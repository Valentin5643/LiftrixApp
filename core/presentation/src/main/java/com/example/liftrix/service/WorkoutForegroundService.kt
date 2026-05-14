package com.example.liftrix.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.example.liftrix.domain.model.UnifiedWorkoutSession
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class WorkoutForegroundService : Service() {
    
    @Inject
    lateinit var workoutSessionManager: UnifiedWorkoutSessionManager
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var notificationManager: NotificationManager? = null
    
    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService()
        createNotificationChannel()
        
        scope.launch {
            workoutSessionManager.currentSession.collectLatest { session ->
                if (session == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collectLatest
                }

                while (true) {
                    val currentSession = workoutSessionManager.currentSession.value ?: break
                    val isRunning = currentSession.sessionStatus == UnifiedWorkoutSession.SessionStatus.ACTIVE
                    val duration = currentSession.getTotalDurationSeconds() * 1000L
                    updateNotification(currentSession, isRunning, duration)
                    delay(NOTIFICATION_TICK_MILLIS)
                }
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FOREGROUND -> {
                startForegroundService()
            }
            ACTION_STOP_FOREGROUND -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_PAUSE_RESUME -> {
                scope.launch {
                    togglePauseResume()
                }
            }
            ACTION_END_WORKOUT -> {
                scope.launch {
                    endWorkout()
                }
            }
            ACTION_VIEW_WORKOUT -> {
                openWorkoutActivity()
            }
        }
        
        return START_STICKY // Restart service if killed by system
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        Timber.d("WorkoutForegroundService destroyed")
    }
    
    private fun startForegroundService() {
        val session = workoutSessionManager.currentSession.value
        if (session != null) {
            val isRunning = session.sessionStatus == UnifiedWorkoutSession.SessionStatus.ACTIVE
            val notification = buildNotification(
                session = session,
                isRunning = isRunning,
                duration = session.getTotalDurationSeconds() * 1000L
            )
            
            // For Android 14+ (API 34+), specify the service type when starting foreground
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID, 
                    notification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            
            Timber.d("Foreground service started for session: ${session.name}")
        }
    }
    
    private fun updateNotification(session: UnifiedWorkoutSession, isRunning: Boolean, duration: Long = 0L) {
        val notification = buildNotification(session, isRunning, duration)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }
    
    private fun buildNotification(
        session: UnifiedWorkoutSession, 
        isRunning: Boolean, 
        duration: Long = 0L
    ): Notification {
        val openIntent = launchIntent().apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_active_workout", true)
        }
        
        val openPendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            openIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Pause/Resume action
        val pauseResumeIntent = Intent(this, WorkoutForegroundService::class.java).apply {
            action = ACTION_PAUSE_RESUME
        }
        val pauseResumePendingIntent = PendingIntent.getService(
            this, 
            1, 
            pauseResumeIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // End workout action
        val endWorkoutIntent = Intent(this, WorkoutForegroundService::class.java).apply {
            action = ACTION_END_WORKOUT
        }
        val endWorkoutPendingIntent = PendingIntent.getService(
            this, 
            2, 
            endWorkoutIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Format duration
        val formattedDuration = formatDuration(duration)
        
        // Calculate completed exercises
        val completedExercises = session.exercises.count { exercise ->
            exercise.sets.any { it.completedAt != null }
        }
        
        val statusText = if (isRunning) {
            "Active - $formattedDuration"
        } else {
            "Paused - $formattedDuration"
        }

        val contentText = "${session.exercises.size} exercises - $completedExercises completed"
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(session.name)
            .setContentText(contentText)
            .setSubText(statusText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                if (isRunning) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isRunning) "Pause" else "Resume",
                pauseResumePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "End Workout",
                endWorkoutPendingIntent
            )
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .build()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Workout Sessions",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification for active workout sessions"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    private suspend fun togglePauseResume() {
        val session = currentSessionForAction()
        if (session != null) {
            if (session.sessionStatus == UnifiedWorkoutSession.SessionStatus.ACTIVE) {
                workoutSessionManager.pauseSession()
            } else if (session.sessionStatus == UnifiedWorkoutSession.SessionStatus.PAUSED) {
                workoutSessionManager.resumeSession()
            }
        } else {
            Timber.w("Cannot toggle pause/resume from notification - no recovered session")
        }
    }
    
    private suspend fun endWorkout() {
        val session = currentSessionForAction()
        if (session != null) {
            workoutSessionManager.completeSession()
        } else {
            Timber.w("Cannot end workout from notification - no recovered session")
        }
    }

    private suspend fun currentSessionForAction(): UnifiedWorkoutSession? {
        return workoutSessionManager.currentSession.value
            ?: withTimeoutOrNull(ACTION_SESSION_WAIT_MILLIS) {
                workoutSessionManager.currentSession.filterNotNull().first()
            }
    }
    
    private fun openWorkoutActivity() {
        val intent = launchIntent().apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_active_workout", true)
        }
        startActivity(intent)
    }

    private fun launchIntent(): Intent =
        packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(packageName)
            }
    
    private fun formatDuration(durationMillis: Long): String {
        val seconds = (durationMillis / 1000) % 60
        val minutes = (durationMillis / (1000 * 60)) % 60
        val hours = (durationMillis / (1000 * 60 * 60))
        
        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
            else -> String.format("%02d:%02d", minutes, seconds)
        }
    }
    
    companion object {
        const val ACTION_START_FOREGROUND = "com.example.liftrix.START_FOREGROUND"
        const val ACTION_STOP_FOREGROUND = "com.example.liftrix.STOP_FOREGROUND"
        const val ACTION_PAUSE_RESUME = "com.example.liftrix.PAUSE_RESUME"
        const val ACTION_END_WORKOUT = "com.example.liftrix.END_WORKOUT"
        const val ACTION_VIEW_WORKOUT = "com.example.liftrix.VIEW_WORKOUT"
        
        private const val CHANNEL_ID = "workout_session_channel"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_TICK_MILLIS = 1_000L
        private const val ACTION_SESSION_WAIT_MILLIS = 1_500L
    }
}
