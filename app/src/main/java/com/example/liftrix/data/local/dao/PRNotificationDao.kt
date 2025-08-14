package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.PRNotificationEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for PR Notification operations
 * Handles cooldown tracking and notification state management
 */
@Dao
interface PRNotificationDao {
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPRNotification(notification: PRNotificationEntity): Long
    
    @Update
    suspend fun updatePRNotification(notification: PRNotificationEntity)
    
    @Query("SELECT EXISTS(SELECT 1 FROM pr_notifications WHERE cooldown_key = :cooldownKey)")
    suspend fun hasSentToday(cooldownKey: String): Boolean
    
    @Query("SELECT * FROM pr_notifications WHERE to_user_id = :userId AND read_at IS NULL ORDER BY sent_at DESC")
    suspend fun getUnreadNotifications(userId: String): List<PRNotificationEntity>
    
    @Query("SELECT * FROM pr_notifications WHERE to_user_id = :userId ORDER BY sent_at DESC LIMIT :limit")
    fun getNotificationsForUser(userId: String, limit: Int = 50): Flow<List<PRNotificationEntity>>
    
    @Query("UPDATE pr_notifications SET read_at = :readAt WHERE id = :notificationId")
    suspend fun markAsRead(notificationId: String, readAt: Long)
    
    @Query("UPDATE pr_notifications SET reacted_with = :reaction WHERE id = :notificationId")
    suspend fun updateReaction(notificationId: String, reaction: String?)
    
    @Query("SELECT COUNT(*) FROM pr_notifications WHERE from_user_id = :userId AND sent_at > :sinceTime")
    suspend fun getSentNotificationCount(userId: String, sinceTime: Long): Int
    
    @Query("SELECT COUNT(*) FROM pr_notifications WHERE to_user_id = :userId AND read_at IS NULL")
    suspend fun getUnreadCount(userId: String): Int
    
    @Query("DELETE FROM pr_notifications WHERE sent_at < :olderThan")
    suspend fun deleteOldNotifications(olderThan: Long)
    
    @Query("""
        SELECT * FROM pr_notifications 
        WHERE from_user_id = :fromUserId AND to_user_id = :toUserId 
        ORDER BY sent_at DESC 
        LIMIT :limit
    """)
    fun getNotificationsBetweenUsers(
        fromUserId: String, 
        toUserId: String, 
        limit: Int = 20
    ): Flow<List<PRNotificationEntity>>
}