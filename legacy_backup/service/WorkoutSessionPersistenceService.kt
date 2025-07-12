package com.example.liftrix.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.liftrix.R
import com.example.liftrix.domain.repository.ActiveWorkoutSessionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

/**
 * Background service for persisting active workout sessions.
 * Ensures session data is regularly saved even when app is backgrounded.
 */
@AndroidEntryPoint
class WorkoutSessionPersistenceService : Service() {

    @Inject
    lateinit var sessionRepository: ActiveWorkoutSessionRepository

    private var serviceJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Timber.d("WorkoutSessionPersistenceService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PERSISTENCE -> {
                startPersistenceMonitoring()
            }
            ACTION_STOP_PERSISTENCE -> {
                stopPersistenceMonitoring()
                stopSelf()
            }
            ACTION_MANUAL_SAVE -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                if (sessionId != null) {
                    performManualSave(sessionId)
                }
            }
        }
        
        return START_STICKY // Restart service if killed
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopPersistenceMonitoring()
        Timber.d("WorkoutSessionPersistenceService destroyed")
    }

    /**
     * Starts the background persistence monitoring
     */
    private fun startPersistenceMonitoring() {
        if (serviceJob?.isActive == true) {
            Timber.d("Persistence monitoring already active")
            return
        }

        serviceJob = serviceScope.launch {
            Timber.i("Starting workout session persistence monitoring")
            
            // Start foreground service with notification
            val notification = createPersistenceNotification()
            startForeground(NOTIFICATION_ID, notification)
            
            while (true) {
                try {
                    performAutomaticSave()
                    delay(AUTO_SAVE_INTERVAL_MS)
                } catch (exception: Exception) {
                    Timber.e(exception, "Error in persistence monitoring loop")
                    delay(ERROR_RETRY_INTERVAL_MS)
                }
            }
        }
    }

    /**
     * Stops the background persistence monitoring
     */
    private fun stopPersistenceMonitoring() {
        serviceJob?.cancel()
        serviceJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        Timber.i("Stopped workout session persistence monitoring")
    }

    /**
     * Performs automatic save of sessions that need it
     */
    private suspend fun performAutomaticSave() {
        try {
            val cutoffTime = Instant.now().minusSeconds(AUTO_SAVE_INTERVAL_MS / 1000)
            val sessionsNeedingSave = sessionRepository.getSessionsNeedingAutoSave(cutoffTime)
                .getOrNull() ?: emptyList()
            
            if (sessionsNeedingSave.isEmpty()) {
                Timber.v("No sessions need auto-save")
                return
            }
            
            Timber.d("Auto-saving ${sessionsNeedingSave.size} sessions")
            
            for (session in sessionsNeedingSave) {
                try {
                    sessionRepository.saveSessionChanges(session)
                    sessionRepository.updateAutoSaveTimestamp(session.id)
                    Timber.v("Auto-saved session: ${session.id}")
                } catch (exception: Exception) {
                    Timber.e(exception, "Failed to auto-save session: ${session.id}")
                }
            }
            
            // Update notification with last save time
            updatePersistenceNotification(sessionsNeedingSave.size)
            
        } catch (exception: Exception) {
            Timber.e(exception, "Error in automatic save")
        }
    }

    /**
     * Performs manual save of a specific session
     */
    private fun performManualSave(sessionId: String) {
        serviceScope.launch {
            try {
                val session = sessionRepository.getSessionById(
                    com.example.liftrix.domain.model.WorkoutSessionId(sessionId)
                ).getOrNull()
                
                if (session != null) {
                    sessionRepository.saveSessionChanges(session)
                    sessionRepository.updateAutoSaveTimestamp(session.id)
                    Timber.i("Manual save completed for session: $sessionId")
                } else {
                    Timber.w("Session not found for manual save: $sessionId")
                }
            } catch (exception: Exception) {
                Timber.e(exception, "Error in manual save for session: $sessionId")
            }
        }
    }

    /**
     * Creates the notification channel for persistence notifications
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Workout Session Persistence",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps workout sessions saved in the background"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Creates the persistent notification
     */
    private fun createPersistenceNotification(): Notification {
        val intent = Intent(this, WorkoutSessionPersistenceService::class.java).apply {
            action = ACTION_STOP_PERSISTENCE
        }
        
        val stopPendingIntent = PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Workout Session Active")
            .setContentText("Automatically saving workout progress")
            .setSmallIcon(android.R.drawable.ic_menu_save) // Use system icon
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel, // Use system icon
                "Stop",
                stopPendingIntent
            )
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * Updates the persistent notification with current status
     */
    private fun updatePersistenceNotification(sessionsSaved: Int) {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Workout Session Active")
            .setContentText("Last saved: ${java.time.LocalTime.now().toString().substring(0, 5)} (${sessionsSaved} sessions)")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "workout_session_persistence"
        private const val NOTIFICATION_ID = 2001
        
        private const val AUTO_SAVE_INTERVAL_MS = 30_000L // 30 seconds
        private const val ERROR_RETRY_INTERVAL_MS = 10_000L // 10 seconds
        
        const val ACTION_START_PERSISTENCE = "com.example.liftrix.START_PERSISTENCE"
        const val ACTION_STOP_PERSISTENCE = "com.example.liftrix.STOP_PERSISTENCE"
        const val ACTION_MANUAL_SAVE = "com.example.liftrix.MANUAL_SAVE"
        const val EXTRA_SESSION_ID = "session_id"

        /**
         * Starts the persistence service
         */
        fun startPersistence(context: Context) {
            val intent = Intent(context, WorkoutSessionPersistenceService::class.java).apply {
                action = ACTION_START_PERSISTENCE
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            
            Timber.i("Workout session persistence service start requested")
        }

        /**
         * Stops the persistence service
         */
        fun stopPersistence(context: Context) {
            val intent = Intent(context, WorkoutSessionPersistenceService::class.java).apply {
                action = ACTION_STOP_PERSISTENCE
            }
            context.startService(intent)
            
            Timber.i("Workout session persistence service stop requested")
        }

        /**
         * Triggers manual save for a specific session
         */
        fun manualSave(context: Context, sessionId: String) {
            val intent = Intent(context, WorkoutSessionPersistenceService::class.java).apply {
                action = ACTION_MANUAL_SAVE
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
            context.startService(intent)
            
            Timber.d("Manual save requested for session: $sessionId")
        }
    }
}