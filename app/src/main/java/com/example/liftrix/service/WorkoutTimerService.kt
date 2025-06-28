package com.example.liftrix.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.liftrix.MainActivity
import com.example.liftrix.R
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
        private const val NOTIFICATION_ID = 1001
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
        val restRemainingSeconds: Int = 0
    )

    private val _serviceState = MutableStateFlow(TimerServiceState())
    val serviceState: StateFlow<TimerServiceState> = _serviceState.asStateFlow()

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null
    private var sessionStartTime: Instant? = null
    private var pausedAtSeconds: Long = 0
    private var currentRestTimer: RestTimer? = null

    @Inject
    lateinit var notificationManager: NotificationManager

    inner class TimerBinder : Binder() {
        fun getService(): WorkoutTimerService = this@WorkoutTimerService
    }

    private val binder = TimerBinder()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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
                startSessionTimer()
                updateNotification()
                startForeground(NOTIFICATION_ID, createNotification())
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
            _serviceState.value = TimerServiceState()
            stopForeground(STOP_FOREGROUND_REMOVE)
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

    private fun createNotification() = with(NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)) {
        val mainIntent = Intent(this@WorkoutTimerService, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this@WorkoutTimerService, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setContentTitle(getNotificationTitle())
        setContentText(getNotificationText())
        setSmallIcon(R.drawable.ic_launcher_foreground)
        setContentIntent(mainPendingIntent)
        setOngoing(true)
        setSilent(true)
        priority = NotificationCompat.PRIORITY_LOW

        // Add action buttons based on current state
        val currentTimerState = _serviceState.value.timerState
        when (currentTimerState) {
            is TimerState.SessionRunning, is TimerState.RestActive -> {
                addAction(
                    R.drawable.ic_launcher_foreground, "Pause",
                    createActionPendingIntent(ACTION_PAUSE)
                )
            }
            is TimerState.SessionPaused, is TimerState.RestPaused -> {
                addAction(
                    R.drawable.ic_launcher_foreground, "Resume",
                    createActionPendingIntent(ACTION_RESUME)
                )
            }
            else -> Unit
        }

        if (currentTimerState is TimerState.RestActive || currentTimerState is TimerState.RestPaused) {
            addAction(
                R.drawable.ic_launcher_foreground, "Skip Rest",
                createActionPendingIntent(ACTION_SKIP_REST)
            )
        }

        addAction(
            R.drawable.ic_launcher_foreground, "Stop",
            createActionPendingIntent(ACTION_STOP)
        )

        build()
    }

    private fun createActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, WorkoutTimerService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getNotificationTitle(): String = when (_serviceState.value.timerState) {
        is TimerState.Stopped -> "Workout Timer"
        is TimerState.SessionRunning, is TimerState.SessionPaused -> "Workout Session"
        is TimerState.RestActive, is TimerState.RestPaused -> "Rest Timer"
    }

    private fun getNotificationText(): String = when (val state = _serviceState.value.timerState) {
        is TimerState.Stopped -> "Timer stopped"
        is TimerState.SessionRunning -> formatTime(state.elapsedSeconds)
        is TimerState.SessionPaused -> "Paused at ${formatTime(state.pausedAtSeconds)}"
        is TimerState.RestActive -> "Rest: ${formatTime(state.remainingSeconds.toLong())}"
        is TimerState.RestPaused -> "Rest paused: ${formatTime(state.remainingSeconds.toLong())}"
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