package com.example.liftrix.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.liftrix.MainActivity
import com.example.liftrix.R
import com.example.liftrix.service.WorkoutTimerService.TimerState
import javax.inject.Inject

/**
 * Manages notification creation and updates for workout timer states.
 * Handles notification actions for pause/resume/stop/skip operations.
 */
class TimerNotificationManager @Inject constructor(
    private val context: Context,
    private val notificationManager: NotificationManager
) {
    
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "workout_timer_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PAUSE = "com.example.liftrix.PAUSE_TIMER"
        const val ACTION_RESUME = "com.example.liftrix.RESUME_TIMER"
        const val ACTION_STOP = "com.example.liftrix.STOP_TIMER"
        const val ACTION_SKIP_REST = "com.example.liftrix.SKIP_REST"
    }

    init {
        createNotificationChannel()
    }

    /**
     * Creates the notification channel for workout timer notifications.
     * Only creates on Android O+ where channels are required.
     */
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

    /**
     * Creates a notification for the given timer state with appropriate title, text, and actions.
     */
    fun createNotification(timerState: TimerState): android.app.Notification {
        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID).apply {
            val mainIntent = Intent(context, MainActivity::class.java)
            val mainPendingIntent = PendingIntent.getActivity(
                context, 0, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            setContentTitle(getNotificationTitle(timerState))
            setContentText(getNotificationText(timerState))
            setSmallIcon(R.drawable.ic_notification)
            setContentIntent(mainPendingIntent)
            setOngoing(true)
            setSilent(true)
            priority = NotificationCompat.PRIORITY_LOW

            // Add action buttons based on current state
            addActionButtons(this, timerState)
        }.build()
    }

    /**
     * Updates the existing notification with new timer state.
     */
    fun updateNotification(timerState: TimerState) {
        notificationManager.notify(NOTIFICATION_ID, createNotification(timerState))
    }

    /**
     * Removes the notification from the notification tray.
     */
    fun clearNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    /**
     * Adds appropriate action buttons to the notification based on timer state.
     */
    private fun addActionButtons(builder: NotificationCompat.Builder, timerState: TimerState) {
        when (timerState) {
            is TimerState.SessionRunning, is TimerState.RestActive -> {
                builder.addAction(
                    R.drawable.ic_launcher_foreground, "Pause",
                    createActionPendingIntent(ACTION_PAUSE)
                )
            }
            is TimerState.SessionPaused, is TimerState.RestPaused -> {
                builder.addAction(
                    R.drawable.ic_launcher_foreground, "Resume",
                    createActionPendingIntent(ACTION_RESUME)
                )
            }
            else -> Unit
        }

        // Add Skip Rest action for rest timer states
        if (timerState is TimerState.RestActive || timerState is TimerState.RestPaused) {
            builder.addAction(
                R.drawable.ic_launcher_foreground, "Skip Rest",
                createActionPendingIntent(ACTION_SKIP_REST)
            )
        }

        // Always show Stop action
        builder.addAction(
            R.drawable.ic_launcher_foreground, "Stop",
            createActionPendingIntent(ACTION_STOP)
        )
    }

    /**
     * Creates a PendingIntent for notification actions that will be sent to WorkoutTimerService.
     */
    private fun createActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(context, WorkoutTimerService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            context, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Returns the appropriate notification title based on timer state.
     */
    private fun getNotificationTitle(timerState: TimerState): String = when (timerState) {
        is TimerState.Stopped -> "Workout Timer"
        is TimerState.SessionRunning, is TimerState.SessionPaused -> "Workout Session"
        is TimerState.RestActive, is TimerState.RestPaused -> "Rest Timer"
    }

    /**
     * Returns the appropriate notification text based on timer state.
     */
    private fun getNotificationText(timerState: TimerState): String = when (timerState) {
        is TimerState.Stopped -> "Timer stopped"
        is TimerState.SessionRunning -> formatTime(timerState.elapsedSeconds)
        is TimerState.SessionPaused -> "Paused at ${formatTime(timerState.pausedAtSeconds)}"
        is TimerState.RestActive -> "Rest: ${formatTime(timerState.remainingSeconds.toLong())}"
        is TimerState.RestPaused -> "Rest paused: ${formatTime(timerState.remainingSeconds.toLong())}"
    }

    /**
     * Formats time in seconds to HH:MM:SS or MM:SS format.
     */
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
