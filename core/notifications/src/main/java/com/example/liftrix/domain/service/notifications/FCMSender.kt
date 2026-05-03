package com.example.liftrix.domain.service.notifications

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.notifications.NotificationQueue
import com.example.liftrix.domain.model.notifications.QueuedNotification

/**
 * Service interface for sending FCM notifications
 */
interface FCMSender {
    
    /**
     * Send a single notification
     */
    suspend fun sendSingle(notification: QueuedNotification): LiftrixResult<Unit>
    
    /**
     * Send a notification to a specific user
     */
    suspend fun sendToUser(userId: String, notification: NotificationQueue): LiftrixResult<Unit>
}