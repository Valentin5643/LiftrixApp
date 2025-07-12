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
import com.example.liftrix.MainActivity
import com.example.liftrix.R
import com.example.liftrix.domain.model.UnifiedWorkoutSession
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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
        
        // Start observing session state changes
        scope.launch {
            workoutSessionManager.currentSession.collectLatest { session ->
                if (session != null) {
                    val isRunning = session.sessionStatus == UnifiedWorkoutSession.SessionStatus.ACTIVE
                    updateNotification(session, isRunning)
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        
        // Start observing session duration for timer updates
        scope.launch {
            workoutSessionManager.currentSession.collectLatest { session ->
                if (session != null) {
                    val isRunning = session.sessionStatus == UnifiedWorkoutSession.SessionStatus.ACTIVE
                    val duration = session.elapsedTimeSeconds * 1000L
                    updateNotification(session, isRunning, duration)
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
                togglePauseResume()
            }
            ACTION_END_WORKOUT -> {
                endWorkout()
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
            val notification = buildNotification(session, isRunning)
            
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
        val openIntent = Intent(this, MainActivity::class.java).apply {
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
            "Active • $formattedDuration"
        } else {
            "Paused • $formattedDuration"
        }
        
        val contentText = "${session.exercises.size} exercises • $completedExercises completed"
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(session.name)
            .setContentText(contentText)
            .setSubText(statusText)
            .setSmallIcon(R.drawable.ic_fitness_center)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                if (isRunning) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                if (isRunning) "Pause" else "Resume",
                pauseResumePendingIntent
            )
            .addAction(
                R.drawable.ic_stop,
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
    
    private fun togglePauseResume() {
        val session = workoutSessionManager.currentSession.value
        if (session != null) {
            if (session.sessionStatus == UnifiedWorkoutSession.SessionStatus.ACTIVE) {
                workoutSessionManager.pauseSession()
            } else if (session.sessionStatus == UnifiedWorkoutSession.SessionStatus.PAUSED) {
                workoutSessionManager.resumeSession()
            }
        }
    }
    
    private fun endWorkout() {
        // This should probably show a confirmation dialog in the main app
        // For now, just end the session
        workoutSessionManager.completeSession()
    }
    
    private fun openWorkoutActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_active_workout", true)
        }
        startActivity(intent)
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
    }
}