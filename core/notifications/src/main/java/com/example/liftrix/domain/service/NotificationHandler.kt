package com.example.liftrix.domain.service

import com.google.firebase.messaging.RemoteMessage

/**
 * Service interface for handling various notification scenarios.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 */
interface NotificationHandler {

    /**
     * Shows an in-app notification when the app is in the foreground
     */
    suspend fun showInApp(message: RemoteMessage)

    /**
     * Processes a system notification (when app is in background/killed)
     */
    suspend fun showSystemNotification(
        title: String,
        body: String,
        type: String,
        data: Map<String, String> = emptyMap()
    )

    /**
     * Dismisses notifications of a specific type
     */
    suspend fun dismissNotificationsByType(type: String)

    /**
     * Dismisses all notifications for a user
     */
    suspend fun dismissAllNotifications(userId: String)

    /**
     * Updates a notification's content (for progressive notifications)
     */
    suspend fun updateNotification(
        notificationId: Int,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    )

    /**
     * Creates a notification group/summary for batched notifications
     */
    suspend fun createNotificationGroup(
        groupKey: String,
        summaryTitle: String,
        summaryText: String,
        notifications: List<NotificationInfo>
    )

    /**
     * Schedules a local notification for later delivery
     */
    suspend fun scheduleLocalNotification(
        title: String,
        body: String,
        triggerAt: Long,
        type: String,
        data: Map<String, String> = emptyMap()
    )

    /**
     * Cancels a scheduled notification
     */
    suspend fun cancelScheduledNotification(notificationId: Int)

    data class NotificationInfo(
        val id: Int,
        val title: String,
        val body: String,
        val type: String,
        val timestamp: Long,
        val data: Map<String, String> = emptyMap()
    )
}