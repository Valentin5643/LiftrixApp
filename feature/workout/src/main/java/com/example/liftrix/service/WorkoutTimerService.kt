package com.example.liftrix.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.liftrix.domain.model.RestTimer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import timber.log.Timber
import java.lang.ref.WeakReference
import javax.inject.Inject

/**
 * Bound service for the short-lived rest countdown shown during an active workout.
 * Active-session elapsed time and notification actions are owned by
 * UnifiedWorkoutSession and WorkoutForegroundService.
 */
@AndroidEntryPoint
class WorkoutTimerService : Service() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "workout_rest_timer_channel"
        private const val NOTIFICATION_ID = 1003
        private const val ACTION_PAUSE = "com.example.liftrix.PAUSE_TIMER"
        private const val ACTION_RESUME = "com.example.liftrix.RESUME_TIMER"
        private const val ACTION_STOP = "com.example.liftrix.STOP_TIMER"
        private const val ACTION_SKIP_REST = "com.example.liftrix.SKIP_REST"
    }

    sealed class TimerState {
        data object Stopped : TimerState()
        data class SessionRunning(val startTime: Instant, val elapsedSeconds: Long) : TimerState()
        data class SessionPaused(val startTime: Instant, val pausedAtSeconds: Long) : TimerState()
        data class RestActive(val restTimer: RestTimer, val remainingSeconds: Int) : TimerState()
        data class RestPaused(val restTimer: RestTimer, val remainingSeconds: Int) : TimerState()
    }

    data class TimerServiceState(
        val timerState: TimerState = TimerState.Stopped,
        val isRunning: Boolean = false,
        val sessionDurationSeconds: Long = 0,
        val restRemainingSeconds: Int = 0,
        val restCompletionSignal: Long? = null
    )

    private val _serviceState = MutableStateFlow(TimerServiceState())
    val serviceState: StateFlow<TimerServiceState> = _serviceState.asStateFlow()

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null
    private var currentRestTimer: RestTimer? = null
    private var restCompletionSignalCounter = 0L

    @Inject
    lateinit var notificationManager: NotificationManager

    class TimerBinder(service: WorkoutTimerService) : Binder() {
        private var serviceReference: WeakReference<WorkoutTimerService>? = WeakReference(service)

        fun getService(): WorkoutTimerService? = serviceReference?.get()

        fun clearServiceReference() {
            serviceReference?.clear()
            serviceReference = null
        }
    }

    private val binder = TimerBinder(this)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Timber.d("WorkoutTimerService created")
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handleIntent(intent)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopTimer()
        serviceScope.coroutineContext[Job]?.cancel()
        binder.clearServiceReference()
        super.onDestroy()
        Timber.d("WorkoutTimerService destroyed")
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_STOP -> stopService()
            ACTION_SKIP_REST -> skipRest()
        }
    }

    /**
     * Pauses the active rest countdown.
     */
    fun pauseTimer(): Result<Unit> {
        return try {
            when (val currentState = _serviceState.value.timerState) {
                is TimerState.RestActive -> {
                    timerJob?.cancel()
                    _serviceState.value = _serviceState.value.copy(
                        timerState = TimerState.RestPaused(currentState.restTimer, currentState.remainingSeconds),
                        isRunning = false
                    )
                    updateNotification()
                    Timber.d("Rest timer paused with ${currentState.remainingSeconds} seconds remaining")
                    Result.success(Unit)
                }
                else -> {
                    Result.failure(IllegalStateException("No active timer to pause"))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to pause timer")
            Result.failure(e)
        }
    }

    /**
     * Resumes the paused rest countdown.
     */
    fun resumeTimer(): Result<Unit> {
        return try {
            when (val currentState = _serviceState.value.timerState) {
                is TimerState.RestPaused -> {
                    startRestTimer(currentState.restTimer, currentState.remainingSeconds)
                    updateNotification()
                    Timber.d("Rest timer resumed with ${currentState.remainingSeconds} seconds remaining")
                    Result.success(Unit)
                }
                else -> {
                    Result.failure(IllegalStateException("No paused timer to resume"))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to resume timer")
            Result.failure(e)
        }
    }

    /**
     * Starts a rest timer with the specified configuration
     */
    fun startRestTimer(restTimer: RestTimer): Result<Unit> {
        return try {
            if (!restTimer.isEnabled) {
                Timber.d("Rest timer is disabled, skipping")
                return Result.success(Unit)
            }

            currentRestTimer = restTimer
            startRestTimer(restTimer, restTimer.durationSeconds)
            updateNotification()
            Timber.d("Rest timer started for ${restTimer.durationSeconds} seconds")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start rest timer")
            Result.failure(e)
        }
    }

    /**
     * Skips the current rest timer
     */
    fun skipRest(): Result<Unit> {
        return try {
            when (_serviceState.value.timerState) {
                is TimerState.RestActive, is TimerState.RestPaused -> {
                    timerJob?.cancel()
                    completeRestTimer(emitCompletionSignal = false).also { result ->
                        if (result.isSuccess) {
                            Timber.d("Rest timer skipped")
                        }
                    }
                }
                else -> {
                    Result.failure(IllegalStateException("No rest timer to skip"))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to skip rest timer")
            Result.failure(e)
        }
    }

    /**
     * Adjusts the active or paused rest timer by the provided number of seconds.
     */
    fun adjustRestBySeconds(deltaSeconds: Int): Result<Unit> {
        return try {
            when (val currentState = _serviceState.value.timerState) {
                is TimerState.RestActive -> {
                    val adjustedSeconds = (currentState.remainingSeconds + deltaSeconds)
                        .coerceIn(0, RestTimer.MAX_DURATION_SECONDS)
                    timerJob?.cancel()

                    val result = if (adjustedSeconds == 0) {
                        completeRestTimer(emitCompletionSignal = true)
                    } else {
                        startRestTimer(currentState.restTimer, adjustedSeconds)
                        updateNotification()
                        Result.success(Unit)
                    }
                    Timber.d("Adjusted active rest timer by $deltaSeconds seconds to $adjustedSeconds")
                    result
                }
                is TimerState.RestPaused -> {
                    val adjustedSeconds = (currentState.remainingSeconds + deltaSeconds)
                        .coerceIn(0, RestTimer.MAX_DURATION_SECONDS)

                    val result = if (adjustedSeconds == 0) {
                        completeRestTimer(emitCompletionSignal = true)
                    } else {
                        _serviceState.value = _serviceState.value.copy(
                            timerState = TimerState.RestPaused(currentState.restTimer, adjustedSeconds),
                            isRunning = false,
                            restRemainingSeconds = adjustedSeconds
                        )
                        updateNotification()
                        Result.success(Unit)
                    }
                    Timber.d("Adjusted paused rest timer by $deltaSeconds seconds to $adjustedSeconds")
                    result
                }
                else -> {
                    Result.failure(IllegalStateException("No rest timer to adjust"))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to adjust rest timer")
            Result.failure(e)
        }
    }

    /**
     * Stops the rest timer and clears its notification.
     */
    fun stopTimer(): Result<Unit> {
        return try {
            timerJob?.cancel()
            currentRestTimer = null
            restCompletionSignalCounter = 0L
            _serviceState.value = TimerServiceState()
            notificationManager.cancel(NOTIFICATION_ID)
            Timber.d("Rest timer stopped")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop timer")
            Result.failure(e)
        }
    }

    private fun stopService() {
        stopTimer()
        stopSelf()
    }

    private fun startRestTimer(restTimer: RestTimer, initialSeconds: Int) {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            var remainingSeconds = initialSeconds
            
            while (remainingSeconds > 0) {
                _serviceState.value = _serviceState.value.copy(
                    timerState = TimerState.RestActive(restTimer, remainingSeconds),
                    isRunning = true,
                    restRemainingSeconds = remainingSeconds
                )
                updateNotification()
                delay(1000)
                remainingSeconds--
            }

            completeRestTimer(emitCompletionSignal = true)
            Timber.d("Rest timer completed")
        }
    }

    private fun completeRestTimer(emitCompletionSignal: Boolean): Result<Unit> {
        val completionSignal = if (emitCompletionSignal) {
            restCompletionSignalCounter += 1
            restCompletionSignalCounter
        } else {
            _serviceState.value.restCompletionSignal
        }

        _serviceState.value = _serviceState.value.copy(
            timerState = TimerState.Stopped,
            isRunning = false,
            restRemainingSeconds = 0,
            restCompletionSignal = completionSignal
        )
        currentRestTimer = null
        notificationManager.cancel(NOTIFICATION_ID)
        return Result.success(Unit)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Workout Rest Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Countdown notifications for rest periods between sets"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun launchIntent(): Intent =
        packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(packageName)
            }

    private fun createNotification() = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).apply {
        val mainPendingIntent = launchIntent()
            .let { intent ->
                PendingIntent.getActivity(
                    this@WorkoutTimerService, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

        setContentTitle("Rest Timer")
        setContentText(getNotificationText())
        setSmallIcon(android.R.drawable.ic_dialog_info)
        setContentIntent(mainPendingIntent)
        setOngoing(true)
        setSilent(true)
        priority = NotificationCompat.PRIORITY_LOW

        // These actions affect only the rest countdown. Session actions belong to
        // WorkoutForegroundService.
        _serviceState.value.timerState.let { currentTimerState ->
            getNotificationActions(currentTimerState).forEach { (iconRes, text, action) ->
                addAction(iconRes, text, createActionPendingIntent(action))
            }
        }
    }.build()

    private fun getNotificationActions(timerState: TimerState): List<NotificationAction> = buildList {
        when (timerState) {
            is TimerState.RestActive ->
                add(NotificationAction(android.R.drawable.ic_media_pause, "Pause", ACTION_PAUSE))
            is TimerState.RestPaused ->
                add(NotificationAction(android.R.drawable.ic_media_play, "Resume", ACTION_RESUME))
            else -> Unit
        }

        if (timerState is TimerState.RestActive || timerState is TimerState.RestPaused) {
            add(NotificationAction(android.R.drawable.ic_media_ff, "Skip Rest", ACTION_SKIP_REST))
        }

        add(NotificationAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", ACTION_STOP))
    }

    private data class NotificationAction(val iconRes: Int, val text: String, val action: String)

    private fun createActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, WorkoutTimerService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getNotificationText(): String = _serviceState.value.timerState.let { state ->
        when (state) {
            is TimerState.Stopped -> "Rest complete"
            is TimerState.SessionRunning, is TimerState.SessionPaused -> "Rest timer unavailable"
            is TimerState.RestActive -> "Rest: ${formatTime(state.remainingSeconds.toLong())}"
            is TimerState.RestPaused -> "Rest paused: ${formatTime(state.remainingSeconds.toLong())}"
        }
    }

    private fun updateNotification() {
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun formatTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%d:%02d".format(minutes, secs)
        }
    }

}
