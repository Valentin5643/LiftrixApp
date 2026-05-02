package com.example.liftrix.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * Foreground service for managing workout and rest timers during active workout sessions.
 * Provides persistent timing functionality with notification support for timer state visibility.
 */
@AndroidEntryPoint
class WorkoutTimerService : Service() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "workout_timer_channel"
        private const val LONG_WORKOUT_CHANNEL_ID = "long_workout_reminder_channel"
        private const val NOTIFICATION_ID = 1001
        private const val LONG_WORKOUT_NOTIFICATION_ID = 1002
        private const val ACTION_PAUSE = "com.example.liftrix.PAUSE_TIMER"
        private const val ACTION_RESUME = "com.example.liftrix.RESUME_TIMER"
        private const val ACTION_STOP = "com.example.liftrix.STOP_TIMER"
        private const val ACTION_SKIP_REST = "com.example.liftrix.SKIP_REST"
        private const val ACTION_CONFIRM_ACTIVE = "com.example.liftrix.CONFIRM_ACTIVE"
        private const val ACTION_END_WORKOUT = "com.example.liftrix.END_WORKOUT"
        
        // 2 hours in seconds
        private const val LONG_WORKOUT_THRESHOLD_SECONDS = 2 * 60 * 60L
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
        val restRemainingSeconds: Int = 0
    )

    private val _serviceState = MutableStateFlow(TimerServiceState())
    val serviceState: StateFlow<TimerServiceState> = _serviceState.asStateFlow()

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null
    private var sessionStartTime: Instant? = null
    private var pausedAtSeconds: Long = 0
    private var currentRestTimer: RestTimer? = null
    private var longWorkoutNotificationShown = false

    @Inject
    lateinit var notificationManager: NotificationManager

    inner class TimerBinder : Binder() {
        fun getService(): WorkoutTimerService = this@WorkoutTimerService
    }

    private val binder = TimerBinder()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        createLongWorkoutNotificationChannel()
        Timber.d("WorkoutTimerService created")
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handleIntent(intent)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
        serviceScope.coroutineContext[Job]?.cancel()
        Timber.d("WorkoutTimerService destroyed")
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_STOP -> stopService()
            ACTION_SKIP_REST -> skipRest()
            ACTION_CONFIRM_ACTIVE -> confirmWorkoutActive()
            ACTION_END_WORKOUT -> endWorkoutFromNotification()
        }
    }

    /**
     * Starts the workout session timer
     */
    fun startSession(): Result<Unit> {
        return try {
            if (_serviceState.value.timerState is TimerState.Stopped) {
                sessionStartTime = Clock.System.now()
                pausedAtSeconds = 0
                longWorkoutNotificationShown = false
                startSessionTimer()
                updateNotification()
                
                // For Android 14+ (API 34+), specify the service type when starting foreground
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID, 
                        createNotification(), 
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, createNotification())
                }
                
                Timber.d("Session timer started")
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Session already running"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to start session")
            Result.failure(e)
        }
    }

    /**
     * Pauses the current timer (session or rest)
     */
    fun pauseTimer(): Result<Unit> {
        return try {
            when (val currentState = _serviceState.value.timerState) {
                is TimerState.SessionRunning -> {
                    pausedAtSeconds = currentState.elapsedSeconds
                    timerJob?.cancel()
                    _serviceState.value = _serviceState.value.copy(
                        timerState = TimerState.SessionPaused(currentState.startTime, pausedAtSeconds),
                        isRunning = false
                    )
                    updateNotification()
                    Timber.d("Session timer paused at $pausedAtSeconds seconds")
                    Result.success(Unit)
                }
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
     * Resumes the paused timer
     */
    fun resumeTimer(): Result<Unit> {
        return try {
            when (val currentState = _serviceState.value.timerState) {
                is TimerState.SessionPaused -> {
                    sessionStartTime = Clock.System.now().minus(pausedAtSeconds.seconds)
                    startSessionTimer()
                    updateNotification()
                    Timber.d("Session timer resumed from $pausedAtSeconds seconds")
                    Result.success(Unit)
                }
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
                    sessionStartTime?.let { startTime ->
                        _serviceState.value = _serviceState.value.copy(
                            timerState = TimerState.SessionRunning(startTime, pausedAtSeconds),
                            isRunning = true,
                            restRemainingSeconds = 0
                        )
                        startSessionTimer()
                        updateNotification()
                        Timber.d("Rest timer skipped")
                        Result.success(Unit)
                    } ?: Result.failure(IllegalStateException("No session to return to"))
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
     * Stops all timers and ends the service
     */
    fun stopTimer(): Result<Unit> {
        return try {
            timerJob?.cancel()
            sessionStartTime = null
            pausedAtSeconds = 0
            currentRestTimer = null
            longWorkoutNotificationShown = false
            _serviceState.value = TimerServiceState()
            stopForeground(STOP_FOREGROUND_REMOVE)
            // Clear any long workout notifications
            notificationManager.cancel(LONG_WORKOUT_NOTIFICATION_ID)
            Timber.d("Timer stopped")
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

    private fun startSessionTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (true) {
                sessionStartTime?.let { startTime ->
                    val elapsed = (Clock.System.now() - startTime).inWholeSeconds + pausedAtSeconds
                    _serviceState.value = _serviceState.value.copy(
                        timerState = TimerState.SessionRunning(startTime, elapsed),
                        isRunning = true,
                        sessionDurationSeconds = elapsed
                    )
                    updateNotification()
                    
                    // Check if we should show the long workout notification
                    checkForLongWorkoutNotification(elapsed)
                }
                delay(1000)
            }
        }
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

            // Rest timer completed - return to session timer
            sessionStartTime?.let { startTime ->
                _serviceState.value = _serviceState.value.copy(
                    timerState = TimerState.SessionRunning(startTime, pausedAtSeconds),
                    isRunning = true,
                    restRemainingSeconds = 0
                )
                startSessionTimer()
                updateNotification()
                Timber.d("Rest timer completed, returning to session")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Workout Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification for workout and rest timers"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createLongWorkoutNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                LONG_WORKOUT_CHANNEL_ID,
                "Long Workout Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications asking if you're still working out after 2+ hours"
                setShowBadge(true)
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

        setContentTitle(getNotificationTitle())
        setContentText(getNotificationText())
        setSmallIcon(android.R.drawable.ic_dialog_info)
        setContentIntent(mainPendingIntent)
        setOngoing(true)
        setSilent(true)
        priority = NotificationCompat.PRIORITY_LOW

        // Add action buttons based on current state
        _serviceState.value.timerState.let { currentTimerState ->
            getNotificationActions(currentTimerState).forEach { (iconRes, text, action) ->
                addAction(iconRes, text, createActionPendingIntent(action))
            }
        }
    }.build()

    private fun getNotificationActions(timerState: TimerState): List<NotificationAction> = buildList {
        when (timerState) {
            is TimerState.SessionRunning, is TimerState.RestActive -> 
                add(NotificationAction(android.R.drawable.ic_media_pause, "Pause", ACTION_PAUSE))
            is TimerState.SessionPaused, is TimerState.RestPaused -> 
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

    private fun getNotificationTitle(): String = _serviceState.value.timerState.let { state ->
        when (state) {
            is TimerState.Stopped -> "Workout Timer"
            is TimerState.SessionRunning, is TimerState.SessionPaused -> "Workout Session"
            is TimerState.RestActive, is TimerState.RestPaused -> "Rest Timer"
        }
    }

    private fun getNotificationText(): String = _serviceState.value.timerState.let { state ->
        when (state) {
            is TimerState.Stopped -> "Timer stopped"
            is TimerState.SessionRunning -> formatTime(state.elapsedSeconds)
            is TimerState.SessionPaused -> "Paused at ${formatTime(state.pausedAtSeconds)}"
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

    /**
     * Checks if we should show the long workout notification (after 2 hours)
     */
    private fun checkForLongWorkoutNotification(elapsedSeconds: Long) {
        if (!longWorkoutNotificationShown && elapsedSeconds >= LONG_WORKOUT_THRESHOLD_SECONDS) {
            showLongWorkoutNotification(elapsedSeconds)
            longWorkoutNotificationShown = true
        }
    }

    /**
     * Shows a notification asking if the user is still working out
     */
    private fun showLongWorkoutNotification(elapsedSeconds: Long) {
        val mainPendingIntent = launchIntent()
            .let { intent ->
                PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

        val confirmActivePendingIntent = Intent(this, WorkoutTimerService::class.java).apply {
            action = ACTION_CONFIRM_ACTIVE
        }.let { intent ->
            PendingIntent.getService(
                this, ACTION_CONFIRM_ACTIVE.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val endWorkoutPendingIntent = Intent(this, WorkoutTimerService::class.java).apply {
            action = ACTION_END_WORKOUT
        }.let { intent ->
            PendingIntent.getService(
                this, ACTION_END_WORKOUT.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(this, LONG_WORKOUT_CHANNEL_ID).apply {
            setContentTitle("Long Workout Detected")
            setContentText("You've been working out for ${formatTime(elapsedSeconds)}. Are you still active?")
            setSmallIcon(android.R.drawable.ic_dialog_info)
            setContentIntent(mainPendingIntent)
            setAutoCancel(true)
            priority = NotificationCompat.PRIORITY_DEFAULT
            
            addAction(
                android.R.drawable.ic_menu_send,
                "Yes, I'm active",
                confirmActivePendingIntent
            )
            addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "End workout",
                endWorkoutPendingIntent
            )
        }.build()

        notificationManager.notify(LONG_WORKOUT_NOTIFICATION_ID, notification)
        Timber.d("Showed long workout notification after ${formatTime(elapsedSeconds)}")
    }

    /**
     * User confirmed they're still active - dismiss the notification
     */
    private fun confirmWorkoutActive() {
        notificationManager.cancel(LONG_WORKOUT_NOTIFICATION_ID)
        Timber.d("User confirmed workout is still active")
    }

    /**
     * User wants to end the workout from the notification
     */
    private fun endWorkoutFromNotification() {
        notificationManager.cancel(LONG_WORKOUT_NOTIFICATION_ID)
        stopTimer()
        Timber.d("Workout ended from long workout notification")
    }
}
