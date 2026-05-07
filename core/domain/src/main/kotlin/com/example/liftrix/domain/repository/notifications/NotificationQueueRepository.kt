package com.example.liftrix.domain.repository.notifications

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.notifications.QueuedNotification
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing the notification queue
 */
interface NotificationQueueRepository {
    
    /**
     * Get all pending notifications for a user
     */
    suspend fun getPending(userId: String): Flow<LiftrixResult<List<QueuedNotification>>>
    
    /**
     * Mark a notification as sent
     */
    suspend fun markSent(notificationId: String): Flow<LiftrixResult<Unit>>
    
    /**
     * Queue a new notification
     */
    suspend fun queueNotification(notification: QueuedNotification): Flow<LiftrixResult<Unit>>
    
    /**
     * Remove expired notifications
     */
    suspend fun cleanupExpired(): Flow<LiftrixResult<Int>>
}