package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.NotificationHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for notification history operations with mandatory user scoping.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 * 
 * CRITICAL SECURITY: All queries MUST include userId filtering to prevent data leakage.
 */
@Dao
interface NotificationHistoryDao {

    // ========================================
    // History Retrieval (User-Scoped)
    // ========================================

    @Query("SELECT * FROM notification_history WHERE user_id = :userId ORDER BY received_at DESC")
    fun observeNotificationHistory(userId: String): Flow<List<NotificationHistoryEntity>>

    @Query("SELECT * FROM notification_history WHERE user_id = :userId ORDER BY received_at DESC LIMIT :limit")
    suspend fun getRecentNotifications(userId: String, limit: Int = 50): List<NotificationHistoryEntity>

    @Query("""
        SELECT * FROM notification_history 
        WHERE user_id = :userId AND received_at >= :fromTime AND received_at <= :toTime
        ORDER BY received_at DESC
    """)
    suspend fun getNotificationsByDateRange(
        userId: String,
        fromTime: Long,
        toTime: Long
    ): List<NotificationHistoryEntity>

    @Query("SELECT * FROM notification_history WHERE user_id = :userId AND id = :notificationId")
    suspend fun getNotificationById(userId: String, notificationId: String): NotificationHistoryEntity?

    @Query("SELECT * FROM notification_history WHERE user_id = :userId AND type = :type ORDER BY received_at DESC")
    suspend fun getNotificationsByType(userId: String, type: String): List<NotificationHistoryEntity>

    // ========================================
    // Unread Notifications
    // ========================================

    @Query("SELECT * FROM notification_history WHERE user_id = :userId AND is_read = 0 ORDER BY received_at DESC")
    fun observeUnreadNotifications(userId: String): Flow<List<NotificationHistoryEntity>>

    @Query("SELECT * FROM notification_history WHERE user_id = :userId AND is_read = 0 ORDER BY received_at DESC")
    suspend fun getUnreadNotifications(userId: String): List<NotificationHistoryEntity>

    @Query("SELECT COUNT(*) FROM notification_history WHERE user_id = :userId AND is_read = 0")
    fun observeUnreadCount(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM notification_history WHERE user_id = :userId AND is_read = 0")
    suspend fun getUnreadCount(userId: String): Int

    @Query("SELECT COUNT(*) FROM notification_history WHERE user_id = :userId AND is_read = 0 AND type = :type")
    suspend fun getUnreadCountByType(userId: String, type: String): Int

    // ========================================
    // Read Status Management
    // ========================================

    @Query("""
        UPDATE notification_history 
        SET is_read = 1, read_at = :readAt
        WHERE user_id = :userId AND id = :notificationId
    """)
    suspend fun markAsRead(userId: String, notificationId: String, readAt: Long): Int

    @Query("""
        UPDATE notification_history 
        SET is_read = 1, read_at = :readAt
        WHERE user_id = :userId AND id IN (:notificationIds)
    """)
    suspend fun markMultipleAsRead(userId: String, notificationIds: List<String>, readAt: Long): Int

    @Query("""
        UPDATE notification_history 
        SET is_read = 1, read_at = :readAt
        WHERE user_id = :userId AND is_read = 0
    """)
    suspend fun markAllAsRead(userId: String, readAt: Long): Int

    @Query("""
        UPDATE notification_history 
        SET is_read = 1, read_at = :readAt
        WHERE user_id = :userId AND type = :type AND is_read = 0
    """)
    suspend fun markAllAsReadByType(userId: String, type: String, readAt: Long): Int

    @Query("""
        UPDATE notification_history 
        SET is_read = 0, read_at = NULL
        WHERE user_id = :userId AND id = :notificationId
    """)
    suspend fun markAsUnread(userId: String, notificationId: String): Int

    // ========================================
    // Action Tracking
    // ========================================

    @Query("""
        UPDATE notification_history 
        SET action_taken = :action
        WHERE user_id = :userId AND id = :notificationId
    """)
    suspend fun updateActionTaken(userId: String, notificationId: String, action: String): Int

    @Query("SELECT * FROM notification_history WHERE user_id = :userId AND action_taken = :action ORDER BY received_at DESC")
    suspend fun getNotificationsByAction(userId: String, action: String): List<NotificationHistoryEntity>

    // ========================================
    // History Management
    // ========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<NotificationHistoryEntity>): List<Long>

    @Update
    suspend fun updateNotification(notification: NotificationHistoryEntity): Int

    @Query("DELETE FROM notification_history WHERE user_id = :userId AND id = :notificationId")
    suspend fun deleteNotification(userId: String, notificationId: String): Int

    @Query("DELETE FROM notification_history WHERE user_id = :userId AND id IN (:notificationIds)")
    suspend fun deleteMultipleNotifications(userId: String, notificationIds: List<String>): Int

    // ========================================
    // Cleanup Operations
    // ========================================

    @Query("""
        DELETE FROM notification_history 
        WHERE user_id = :userId AND received_at < :olderThan
    """)
    suspend fun deleteNotificationsOlderThan(userId: String, olderThan: Long): Int

    @Query("""
        DELETE FROM notification_history 
        WHERE user_id = :userId AND type = :type AND received_at < :olderThan
    """)
    suspend fun deleteNotificationsByTypeOlderThan(userId: String, type: String, olderThan: Long): Int

    @Query("""
        DELETE FROM notification_history 
        WHERE user_id = :userId AND is_read = 1 AND received_at < :olderThan
    """)
    suspend fun deleteReadNotificationsOlderThan(userId: String, olderThan: Long): Int

    @Query("DELETE FROM notification_history WHERE user_id = :userId")
    suspend fun deleteAllNotificationsForUser(userId: String): Int

    @Query("""
        DELETE FROM notification_history 
        WHERE user_id = :userId AND id NOT IN (
            SELECT id FROM notification_history 
            WHERE user_id = :userId 
            ORDER BY received_at DESC 
            LIMIT :keepCount
        )
    """)
    suspend fun keepOnlyRecentNotifications(userId: String, keepCount: Int): Int

    // ========================================
    // Analytics and Statistics
    // ========================================

    @Query("SELECT COUNT(*) FROM notification_history WHERE user_id = :userId")
    suspend fun getTotalNotificationCount(userId: String): Int

    @Query("""
        SELECT type, COUNT(*) as count 
        FROM notification_history 
        WHERE user_id = :userId 
        GROUP BY type
    """)
    suspend fun getNotificationCountsByType(userId: String): Map<@MapColumn(columnName = "type") String, @MapColumn(columnName = "count") Int>

    @Query("""
        SELECT action_taken, COUNT(*) as count 
        FROM notification_history 
        WHERE user_id = :userId AND action_taken IS NOT NULL
        GROUP BY action_taken
    """)
    suspend fun getActionCountsByType(userId: String): Map<@MapColumn(columnName = "action_taken") String, @MapColumn(columnName = "count") Int>

    @Query("""
        SELECT COUNT(*) FROM notification_history 
        WHERE user_id = :userId AND received_at >= :fromTime AND received_at <= :toTime
    """)
    suspend fun getNotificationCountForPeriod(userId: String, fromTime: Long, toTime: Long): Int

    @Query("""
        SELECT AVG(CASE WHEN read_at IS NOT NULL THEN read_at - received_at ELSE NULL END) 
        FROM notification_history 
        WHERE user_id = :userId AND is_read = 1
    """)
    suspend fun getAverageReadTime(userId: String): Long?

    @Query("""
        SELECT COUNT(CASE WHEN is_read = 1 THEN 1 END) * 100.0 / COUNT(*) as read_rate
        FROM notification_history 
        WHERE user_id = :userId
    """)
    suspend fun getReadRate(userId: String): Double?

    // ========================================
    // Search and Filtering
    // ========================================

    @Query("""
        SELECT * FROM notification_history 
        WHERE user_id = :userId 
        AND (title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%')
        ORDER BY received_at DESC
        LIMIT :limit
    """)
    suspend fun searchNotifications(userId: String, query: String, limit: Int = 50): List<NotificationHistoryEntity>

    @Query("""
        SELECT * FROM notification_history 
        WHERE user_id = :userId AND type IN (:types)
        ORDER BY received_at DESC
        LIMIT :limit
    """)
    suspend fun getNotificationsByTypes(userId: String, types: List<String>, limit: Int = 50): List<NotificationHistoryEntity>

    @Query("""
        SELECT * FROM notification_history 
        WHERE user_id = :userId AND is_read = :isRead
        ORDER BY received_at DESC
        LIMIT :limit
    """)
    suspend fun getNotificationsByReadStatus(userId: String, isRead: Boolean, limit: Int = 50): List<NotificationHistoryEntity>
}