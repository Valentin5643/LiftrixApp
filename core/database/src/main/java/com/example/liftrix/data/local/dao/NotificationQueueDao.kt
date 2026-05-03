package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.NotificationQueueEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for notification queue operations with mandatory user scoping.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 * 
 * CRITICAL SECURITY: All queries MUST include userId filtering to prevent data leakage.
 */
@Dao
interface NotificationQueueDao {

    // ========================================
    // Queue Retrieval (User-Scoped)
    // ========================================

    @Query("SELECT * FROM notification_queue WHERE user_id = :userId AND status = 'PENDING' ORDER BY scheduled_for ASC")
    fun observePendingNotifications(userId: String): Flow<List<NotificationQueueEntity>>

    @Query("SELECT * FROM notification_queue WHERE user_id = :userId AND status = 'PENDING' ORDER BY scheduled_for ASC")
    suspend fun getPendingNotifications(userId: String): List<NotificationQueueEntity>

    @Query("""
        SELECT * FROM notification_queue 
        WHERE user_id = :userId AND status = 'PENDING' 
        AND (scheduled_for IS NULL OR scheduled_for <= :currentTime)
        ORDER BY priority DESC, created_at ASC
    """)
    suspend fun getReadyNotifications(userId: String, currentTime: Long): List<NotificationQueueEntity>

    @Query("""
        SELECT * FROM notification_queue 
        WHERE user_id = :userId AND status = 'PENDING' AND batch_key = :batchKey
        ORDER BY created_at ASC
    """)
    suspend fun getNotificationsByBatchKey(userId: String, batchKey: String): List<NotificationQueueEntity>

    @Query("""
        SELECT * FROM notification_queue 
        WHERE user_id = :userId AND status = 'PENDING' AND can_batch = 1
        AND (scheduled_for IS NULL OR scheduled_for <= :currentTime)
        ORDER BY batch_key, created_at ASC
    """)
    suspend fun getBatchableNotifications(userId: String, currentTime: Long): List<NotificationQueueEntity>

    // ========================================
    // Queue Management
    // ========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationQueueEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<NotificationQueueEntity>): List<Long>

    @Update
    suspend fun updateNotification(notification: NotificationQueueEntity): Int

    @Query("""
        UPDATE notification_queue 
        SET status = :status, sent_at = :sentAt
        WHERE user_id = :userId AND id = :notificationId
    """)
    suspend fun updateStatus(
        userId: String,
        notificationId: String,
        status: String,
        sentAt: Long? = null
    ): Int

    @Query("""
        UPDATE notification_queue 
        SET status = 'SENT', sent_at = :sentAt
        WHERE user_id = :userId AND id IN (:notificationIds)
    """)
    suspend fun markNotificationsSent(
        userId: String,
        notificationIds: List<String>,
        sentAt: Long
    ): Int

    @Query("""
        UPDATE notification_queue 
        SET status = 'FAILED', failure_reason = :reason
        WHERE user_id = :userId AND id = :notificationId
    """)
    suspend fun markNotificationFailed(
        userId: String,
        notificationId: String,
        reason: String
    ): Int

    // ========================================
    // Scheduling Operations
    // ========================================

    @Query("""
        UPDATE notification_queue 
        SET scheduled_for = :scheduledFor
        WHERE user_id = :userId AND id = :notificationId
    """)
    suspend fun rescheduleNotification(
        userId: String,
        notificationId: String,
        scheduledFor: Long
    ): Int

    @Query("""
        SELECT * FROM notification_queue 
        WHERE user_id = :userId AND scheduled_for IS NOT NULL 
        AND scheduled_for <= :currentTime AND status = 'PENDING'
        ORDER BY scheduled_for ASC
    """)
    suspend fun getDueScheduledNotifications(userId: String, currentTime: Long): List<NotificationQueueEntity>

    // ========================================
    // Batching Operations
    // ========================================

    @Query("""
        SELECT DISTINCT batch_key FROM notification_queue 
        WHERE user_id = :userId AND status = 'PENDING' AND batch_key IS NOT NULL
        AND can_batch = 1 AND (scheduled_for IS NULL OR scheduled_for <= :currentTime)
    """)
    suspend fun getAvailableBatchKeys(userId: String, currentTime: Long): List<String>

    @Query("""
        SELECT COUNT(*) FROM notification_queue 
        WHERE user_id = :userId AND batch_key = :batchKey AND status = 'PENDING'
    """)
    suspend fun getNotificationCountInBatch(userId: String, batchKey: String): Int

    @Query("""
        UPDATE notification_queue 
        SET batch_key = :newBatchKey
        WHERE user_id = :userId AND id IN (:notificationIds)
    """)
    suspend fun updateBatchKey(
        userId: String,
        notificationIds: List<String>,
        newBatchKey: String
    ): Int

    // ========================================
    // Priority Management
    // ========================================

    @Query("""
        SELECT * FROM notification_queue 
        WHERE user_id = :userId AND status = 'PENDING' AND priority = 'HIGH'
        ORDER BY created_at ASC
    """)
    suspend fun getHighPriorityNotifications(userId: String): List<NotificationQueueEntity>

    @Query("""
        UPDATE notification_queue 
        SET priority = :priority
        WHERE user_id = :userId AND id = :notificationId
    """)
    suspend fun updatePriority(
        userId: String,
        notificationId: String,
        priority: String
    ): Int

    // ========================================
    // Cleanup Operations
    // ========================================

    @Query("""
        UPDATE notification_queue 
        SET status = 'EXPIRED'
        WHERE user_id = :userId AND status = 'PENDING' 
        AND expires_at IS NOT NULL AND expires_at < :currentTime
    """)
    suspend fun markExpiredNotifications(userId: String, currentTime: Long): Int

    @Query("""
        DELETE FROM notification_queue 
        WHERE user_id = :userId AND status IN ('SENT', 'FAILED', 'EXPIRED') 
        AND created_at < :olderThan
    """)
    suspend fun deleteOldNotifications(userId: String, olderThan: Long): Int

    @Query("DELETE FROM notification_queue WHERE user_id = :userId AND id = :notificationId")
    suspend fun deleteNotification(userId: String, notificationId: String): Int

    @Query("DELETE FROM notification_queue WHERE user_id = :userId AND status = :status")
    suspend fun deleteNotificationsByStatus(userId: String, status: String): Int

    // ========================================
    // Analytics and Monitoring
    // ========================================

    @Query("SELECT COUNT(*) FROM notification_queue WHERE user_id = :userId AND status = 'PENDING'")
    suspend fun getPendingNotificationCount(userId: String): Int

    @Query("""
        SELECT status, COUNT(*) as count 
        FROM notification_queue 
        WHERE user_id = :userId 
        GROUP BY status
    """)
    suspend fun getNotificationCountsByStatus(userId: String): Map<@MapColumn(columnName = "status") String, @MapColumn(columnName = "count") Int>

    @Query("""
        SELECT COUNT(*) FROM notification_queue 
        WHERE user_id = :userId AND status = 'SENT' 
        AND sent_at >= :fromTime AND sent_at <= :toTime
    """)
    suspend fun getSentNotificationCount(userId: String, fromTime: Long, toTime: Long): Int

    @Query("""
        SELECT AVG(sent_at - created_at) as avg_processing_time 
        FROM notification_queue 
        WHERE user_id = :userId AND status = 'SENT' 
        AND sent_at IS NOT NULL
    """)
    suspend fun getAverageProcessingTime(userId: String): Long?

    // ========================================
    // Type-Specific Queries
    // ========================================

    @Query("""
        SELECT COUNT(*) FROM notification_queue 
        WHERE user_id = :userId AND type = :type AND status = 'PENDING'
    """)
    suspend fun getPendingNotificationCountByType(userId: String, type: String): Int

    @Query("""
        DELETE FROM notification_queue 
        WHERE user_id = :userId AND type = :type AND status = 'PENDING'
    """)
    suspend fun cancelPendingNotificationsByType(userId: String, type: String): Int
}