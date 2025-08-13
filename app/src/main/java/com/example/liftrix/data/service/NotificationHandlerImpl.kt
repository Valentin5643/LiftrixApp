package com.example.liftrix.data.service

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.liftrix.MainActivity
import com.example.liftrix.R
import com.example.liftrix.domain.service.NotificationHandler
import com.example.liftrix.services.NotificationChannelManager
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Implementation of NotificationHandler for managing various notification scenarios.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 * 
 * Handles:
 * - System notifications (when app is in background/killed)
 * - In-app notifications (when app is in foreground)
 * - Scheduled local notifications
 * - Notification groups and batching
 * - Notification dismissal and updates
 */
@Singleton
class NotificationHandlerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationChannelManager: NotificationChannelManager
) : NotificationHandler {

    private val notificationManager = NotificationManagerCompat.from(context)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    // Notification ID management
    private val notificationIdCounter = java.util.concurrent.atomic.AtomicInteger(1000)
    private val activeNotifications = mutableMapOf<String, Int>()

    companion object {
        private const val NOTIFICATION_GROUP_KEY = "LIFTRIX_NOTIFICATION_GROUP"
        private const val GROUP_SUMMARY_ID = 999
    }

    // ========================================
    // In-App Notification Handling
    // ========================================

    override suspend fun showInApp(message: RemoteMessage) {
        Timber.d("Processing in-app notification: ${message.notification?.title}")
        
        // For in-app notifications, we could:
        // 1. Show a banner notification using InAppNotificationManager
        // 2. Update UI state to show notification badges
        // 3. Play subtle notification sounds
        
        // For now, we'll convert the Firebase message to a system notification
        // In a real implementation, this would integrate with the UI layer
        val title = message.notification?.title ?: "Liftrix Notification"
        val body = message.notification?.body ?: ""
        val type = message.data["type"] ?: "default"
        
        showSystemNotification(title, body, type, message.data)
    }

    // ========================================
    // System Notification Handling
    // ========================================

    override suspend fun showSystemNotification(
        title: String,
        body: String,
        type: String,
        data: Map<String, String>
    ) {
        val channelId = getChannelIdForNotificationType(type)
        val notificationId = generateNotificationId(type)
        
        Timber.d("Showing system notification: $title (type: $type, channel: $channelId)")
        
        val intent = createNotificationIntent(type, data)
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(getNotificationPriority(type))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(NOTIFICATION_GROUP_KEY)
        
        // Add action buttons based on notification type
        addNotificationActions(builder, type, data, notificationId)
        
        // Store the notification ID for future reference
        activeNotifications[type] = notificationId
        
        try {
            notificationManager.notify(notificationId, builder.build())
            
            // Create group summary if we have multiple notifications
            createGroupSummaryIfNeeded()
            
            Timber.i("System notification shown successfully: $title")
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to show notification due to security restrictions")
        }
    }

    // ========================================
    // Notification Dismissal
    // ========================================

    override suspend fun dismissNotificationsByType(type: String) {
        activeNotifications[type]?.let { notificationId ->
            notificationManager.cancel(notificationId)
            activeNotifications.remove(type)
            Timber.d("Dismissed notifications of type: $type")
        }
    }

    override suspend fun dismissAllNotifications(userId: String) {
        notificationManager.cancelAll()
        activeNotifications.clear()
        Timber.d("Dismissed all notifications for user: $userId")
    }

    // ========================================
    // Notification Updates
    // ========================================

    override suspend fun updateNotification(
        notificationId: Int,
        title: String,
        body: String,
        data: Map<String, String>
    ) {
        val type = data["type"] ?: "default"
        val channelId = getChannelIdForNotificationType(type)
        
        val intent = createNotificationIntent(type, data)
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(getNotificationPriority(type))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(NOTIFICATION_GROUP_KEY)
        
        try {
            notificationManager.notify(notificationId, builder.build())
            Timber.d("Updated notification $notificationId with new content")
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to update notification due to security restrictions")
        }
    }

    // ========================================
    // Notification Groups
    // ========================================

    override suspend fun createNotificationGroup(
        groupKey: String,
        summaryTitle: String,
        summaryText: String,
        notifications: List<NotificationHandler.NotificationInfo>
    ) {
        val groupBuilder = NotificationCompat.Builder(context, NotificationChannelManager.CHANNEL_DEFAULT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(summaryTitle)
            .setContentText(summaryText)
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setAutoCancel(true)
        
        // Show individual notifications
        notifications.forEach { notificationInfo ->
            showSystemNotification(
                notificationInfo.title,
                notificationInfo.body,
                notificationInfo.type,
                notificationInfo.data
            )
        }
        
        // Show group summary
        try {
            notificationManager.notify(GROUP_SUMMARY_ID, groupBuilder.build())
            Timber.d("Created notification group: $summaryTitle")
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to create notification group due to security restrictions")
        }
    }

    // ========================================
    // Scheduled Notifications
    // ========================================

    override suspend fun scheduleLocalNotification(
        title: String,
        body: String,
        triggerAt: Long,
        type: String,
        data: Map<String, String>
    ) {
        if (alarmManager == null) {
            Timber.w("AlarmManager not available, cannot schedule notification")
            return
        }
        
        val notificationId = generateNotificationId(type)
        val intent = Intent(context, NotificationBroadcastReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("body", body)
            putExtra("type", type)
            putExtra("notification_id", notificationId)
            data.forEach { (key, value) -> putExtra("data_$key", value) }
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
            Timber.d("Scheduled notification: $title at ${java.util.Date(triggerAt)}")
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to schedule notification due to security restrictions")
        }
    }

    override suspend fun cancelScheduledNotification(notificationId: Int) {
        if (alarmManager == null) {
            Timber.w("AlarmManager not available, cannot cancel scheduled notification")
            return
        }
        
        val intent = Intent(context, NotificationBroadcastReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        
        pendingIntent?.let {
            alarmManager.cancel(it)
            Timber.d("Cancelled scheduled notification: $notificationId")
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    private fun getChannelIdForNotificationType(type: String): String {
        return when (type) {
            "follow_request", "social_request" -> NotificationChannelManager.CHANNEL_SOCIAL_REQUESTS
            "like", "comment", "share", "social_engagement" -> NotificationChannelManager.CHANNEL_SOCIAL_ENGAGEMENT
            "mention" -> NotificationChannelManager.CHANNEL_MENTIONS
            "gym_buddy", "buddy_request" -> NotificationChannelManager.CHANNEL_GYM_BUDDY
            "achievement", "personal_record" -> NotificationChannelManager.CHANNEL_ACHIEVEMENT
            "reminder", "workout_reminder" -> NotificationChannelManager.CHANNEL_REMINDER
            "workout_complete" -> NotificationChannelManager.CHANNEL_WORKOUT_COMPLETE
            "error" -> NotificationChannelManager.CHANNEL_ERROR
            "sync" -> NotificationChannelManager.CHANNEL_SYNC
            else -> NotificationChannelManager.CHANNEL_DEFAULT
        }
    }

    private fun getNotificationPriority(type: String): Int {
        return when (type) {
            "follow_request", "social_request", "gym_buddy", "mention", "achievement" -> NotificationCompat.PRIORITY_HIGH
            "like", "comment", "share", "workout_complete" -> NotificationCompat.PRIORITY_DEFAULT
            "reminder", "sync" -> NotificationCompat.PRIORITY_LOW
            "error" -> NotificationCompat.PRIORITY_MAX
            else -> NotificationCompat.PRIORITY_DEFAULT
        }
    }

    private fun generateNotificationId(type: String): Int {
        return notificationIdCounter.incrementAndGet()
    }

    private fun createNotificationIntent(type: String, data: Map<String, String>): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_type", type)
            data.forEach { (key, value) -> putExtra(key, value) }
        }
    }

    private fun addNotificationActions(
        builder: NotificationCompat.Builder,
        type: String,
        data: Map<String, String>,
        notificationId: Int
    ) {
        when (type) {
            "follow_request" -> {
                // Add Accept/Decline actions
                val acceptIntent = createActionIntent("accept_follow", data, notificationId)
                val declineIntent = createActionIntent("decline_follow", data, notificationId)
                
                builder.addAction(android.R.drawable.ic_input_add, "Accept", acceptIntent)
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declineIntent)
            }
            "gym_buddy" -> {
                // Add Join/Decline actions
                val joinIntent = createActionIntent("join_gym_buddy", data, notificationId)
                val declineIntent = createActionIntent("decline_gym_buddy", data, notificationId)
                
                builder.addAction(android.R.drawable.ic_input_add, "Join", joinIntent)
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declineIntent)
            }
        }
    }

    private fun createActionIntent(action: String, data: Map<String, String>, notificationId: Int): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            putExtra("action", action)
            putExtra("notification_id", notificationId)
            data.forEach { (key, value) -> putExtra(key, value) }
        }
        
        return PendingIntent.getBroadcast(
            context,
            Random.nextInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private suspend fun createGroupSummaryIfNeeded() {
        if (activeNotifications.size > 1) {
            val summaryBuilder = NotificationCompat.Builder(context, NotificationChannelManager.CHANNEL_DEFAULT)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Liftrix")
                .setContentText("You have ${activeNotifications.size} notifications")
                .setGroup(NOTIFICATION_GROUP_KEY)
                .setGroupSummary(true)
                .setAutoCancel(true)
            
            try {
                notificationManager.notify(GROUP_SUMMARY_ID, summaryBuilder.build())
            } catch (e: SecurityException) {
                Timber.e(e, "Failed to create group summary due to security restrictions")
            }
        }
    }
}

/**
 * Placeholder broadcast receiver for scheduled notifications.
 * In a full implementation, this would be a proper BroadcastReceiver class.
 */
private class NotificationBroadcastReceiver

/**
 * Placeholder broadcast receiver for notification actions.
 * In a full implementation, this would handle notification action clicks.
 */
private class NotificationActionReceiver