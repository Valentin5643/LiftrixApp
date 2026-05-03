package com.example.liftrix.domain.service.notifications

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.notifications.NotificationQueue
import com.example.liftrix.domain.model.notifications.QueuedNotification

/**
 * Service interface for building batched notifications
 */
interface BatchNotificationBuilder {
    
    /**
     * Build an inbox style notification from multiple notifications
     */
    suspend fun buildInboxStyleNotification(
        notifications: List<QueuedNotification>,
        userId: String
    ): LiftrixResult<NotificationQueue>
    
    /**
     * Build a summary notification for many notifications
     */
    suspend fun buildSummaryNotification(
        notifications: List<QueuedNotification>, 
        userId: String
    ): LiftrixResult<NotificationQueue>
}