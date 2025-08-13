package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.NotificationMuteEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for notification mute operations with mandatory user scoping.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 * 
 * CRITICAL SECURITY: All queries MUST include userId filtering to prevent data leakage.
 */
@Dao
interface NotificationMuteDao {

    // ========================================
    // Mute Retrieval (User-Scoped)
    // ========================================

    @Query("SELECT * FROM notification_mutes WHERE user_id = :userId ORDER BY created_at DESC")
    fun observeMutesForUser(userId: String): Flow<List<NotificationMuteEntity>>

    @Query("SELECT * FROM notification_mutes WHERE user_id = :userId ORDER BY created_at DESC")
    suspend fun getMutesForUser(userId: String): List<NotificationMuteEntity>

    @Query("""
        SELECT * FROM notification_mutes 
        WHERE user_id = :userId AND (muted_until IS NULL OR muted_until > :currentTime)
        ORDER BY created_at DESC
    """)
    suspend fun getActiveMutesForUser(userId: String, currentTime: Long): List<NotificationMuteEntity>

    @Query("""
        SELECT * FROM notification_mutes 
        WHERE user_id = :userId AND mute_type = 'USER' AND muted_user_id = :mutedUserId
        AND (muted_until IS NULL OR muted_until > :currentTime)
        LIMIT 1
    """)
    suspend fun getUserMute(userId: String, mutedUserId: String, currentTime: Long): NotificationMuteEntity?

    @Query("""
        SELECT * FROM notification_mutes 
        WHERE user_id = :userId AND mute_type = 'CATEGORY' AND muted_category = :category
        AND (muted_until IS NULL OR muted_until > :currentTime)
        LIMIT 1
    """)
    suspend fun getCategoryMute(userId: String, category: String, currentTime: Long): NotificationMuteEntity?

    @Query("""
        SELECT * FROM notification_mutes 
        WHERE user_id = :userId AND mute_type = 'ALL'
        AND (muted_until IS NULL OR muted_until > :currentTime)
        LIMIT 1
    """)
    suspend fun getAllNotificationsMute(userId: String, currentTime: Long): NotificationMuteEntity?

    // ========================================
    // Mute Checks
    // ========================================

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM notification_mutes 
            WHERE user_id = :userId AND mute_type = 'USER' AND muted_user_id = :mutedUserId
            AND (muted_until IS NULL OR muted_until > :currentTime)
        )
    """)
    suspend fun isUserMuted(userId: String, mutedUserId: String, currentTime: Long = System.currentTimeMillis()): Boolean

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM notification_mutes 
            WHERE user_id = :userId AND mute_type = 'CATEGORY' AND muted_category = :category
            AND (muted_until IS NULL OR muted_until > :currentTime)
        )
    """)
    suspend fun isCategoryMuted(userId: String, category: String, currentTime: Long): Boolean

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM notification_mutes 
            WHERE user_id = :userId AND mute_type = 'ALL'
            AND (muted_until IS NULL OR muted_until > :currentTime)
        )
    """)
    suspend fun areAllNotificationsMuted(userId: String, currentTime: Long): Boolean

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM notification_mutes 
            WHERE user_id = :userId 
            AND ((mute_type = 'USER' AND muted_user_id = :fromUserId)
                OR (mute_type = 'CATEGORY' AND muted_category = :category)
                OR mute_type = 'ALL')
            AND (muted_until IS NULL OR muted_until > :currentTime)
        )
    """)
    suspend fun isNotificationMuted(
        userId: String,
        fromUserId: String?,
        category: String?,
        currentTime: Long
    ): Boolean

    // ========================================
    // Mute Management
    // ========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMute(mute: NotificationMuteEntity): Long

    @Update
    suspend fun updateMute(mute: NotificationMuteEntity): Int

    @Query("""
        UPDATE notification_mutes 
        SET muted_until = :mutedUntil
        WHERE user_id = :userId AND id = :muteId
    """)
    suspend fun updateMuteDuration(userId: String, muteId: String, mutedUntil: Long?): Int

    // ========================================
    // User Mutes
    // ========================================

    suspend fun muteUser(
        userId: String,
        mutedUserId: String,
        mutedUntil: Long? = null,
        createdAt: Long = System.currentTimeMillis()
    ): Long {
        val mute = NotificationMuteEntity(
            id = "${userId}_user_${mutedUserId}_${createdAt}",
            userId = userId,
            muteType = "USER",
            mutedUserId = mutedUserId,
            mutedCategory = null,
            mutedUntil = mutedUntil,
            createdAt = createdAt
        )
        return insertMute(mute)
    }

    @Query("""
        DELETE FROM notification_mutes 
        WHERE user_id = :userId AND mute_type = 'USER' AND muted_user_id = :mutedUserId
    """)
    suspend fun unmuteUser(userId: String, mutedUserId: String): Int

    @Query("""
        SELECT DISTINCT muted_user_id FROM notification_mutes 
        WHERE user_id = :userId AND mute_type = 'USER' 
        AND (muted_until IS NULL OR muted_until > :currentTime)
    """)
    suspend fun getMutedUserIds(userId: String, currentTime: Long): List<String>

    // ========================================
    // Category Mutes
    // ========================================

    suspend fun muteCategory(
        userId: String,
        category: String,
        mutedUntil: Long? = null,
        createdAt: Long = System.currentTimeMillis()
    ): Long {
        val mute = NotificationMuteEntity(
            id = "${userId}_category_${category}_${createdAt}",
            userId = userId,
            muteType = "CATEGORY",
            mutedUserId = null,
            mutedCategory = category,
            mutedUntil = mutedUntil,
            createdAt = createdAt
        )
        return insertMute(mute)
    }

    @Query("""
        DELETE FROM notification_mutes 
        WHERE user_id = :userId AND mute_type = 'CATEGORY' AND muted_category = :category
    """)
    suspend fun unmuteCategory(userId: String, category: String): Int

    @Query("""
        SELECT DISTINCT muted_category FROM notification_mutes 
        WHERE user_id = :userId AND mute_type = 'CATEGORY' 
        AND (muted_until IS NULL OR muted_until > :currentTime)
    """)
    suspend fun getMutedCategories(userId: String, currentTime: Long): List<String>

    // ========================================
    // All Notifications Mute
    // ========================================

    suspend fun muteAllNotifications(
        userId: String,
        mutedUntil: Long? = null,
        createdAt: Long = System.currentTimeMillis()
    ): Long {
        val mute = NotificationMuteEntity(
            id = "${userId}_all_${createdAt}",
            userId = userId,
            muteType = "ALL",
            mutedUserId = null,
            mutedCategory = null,
            mutedUntil = mutedUntil,
            createdAt = createdAt
        )
        return insertMute(mute)
    }

    @Query("""
        DELETE FROM notification_mutes 
        WHERE user_id = :userId AND mute_type = 'ALL'
    """)
    suspend fun unmuteAllNotifications(userId: String): Int

    // ========================================
    // Temporary Mutes
    // ========================================

    suspend fun muteUserTemporarily(
        userId: String,
        mutedUserId: String,
        durationMillis: Long,
        createdAt: Long = System.currentTimeMillis()
    ): Long {
        return muteUser(userId, mutedUserId, createdAt + durationMillis, createdAt)
    }

    suspend fun muteCategoryTemporarily(
        userId: String,
        category: String,
        durationMillis: Long,
        createdAt: Long = System.currentTimeMillis()
    ): Long {
        return muteCategory(userId, category, createdAt + durationMillis, createdAt)
    }

    suspend fun muteAllNotificationsTemporarily(
        userId: String,
        durationMillis: Long,
        createdAt: Long = System.currentTimeMillis()
    ): Long {
        return muteAllNotifications(userId, createdAt + durationMillis, createdAt)
    }

    // ========================================
    // Cleanup Operations
    // ========================================

    @Query("""
        DELETE FROM notification_mutes 
        WHERE user_id = :userId AND muted_until IS NOT NULL AND muted_until < :currentTime
    """)
    suspend fun deleteExpiredMutes(userId: String, currentTime: Long): Int

    @Query("DELETE FROM notification_mutes WHERE user_id = :userId AND id = :muteId")
    suspend fun deleteMute(userId: String, muteId: String): Int

    @Query("DELETE FROM notification_mutes WHERE user_id = :userId")
    suspend fun deleteAllMutesForUser(userId: String): Int

    // ========================================
    // Analytics
    // ========================================

    @Query("SELECT COUNT(*) FROM notification_mutes WHERE user_id = :userId")
    suspend fun getTotalMuteCount(userId: String): Int

    @Query("""
        SELECT mute_type, COUNT(*) as count 
        FROM notification_mutes 
        WHERE user_id = :userId AND (muted_until IS NULL OR muted_until > :currentTime)
        GROUP BY mute_type
    """)
    suspend fun getActiveMuteCountsByType(userId: String, currentTime: Long): Map<@MapColumn(columnName = "mute_type") String, @MapColumn(columnName = "count") Int>

    // ========================================
    // Additional Methods for Repository
    // ========================================

    @Query("""
        SELECT COUNT(*) FROM notification_mutes 
        WHERE user_id = :userId AND mute_type = 'USER' 
        AND (muted_until IS NULL OR muted_until > :currentTime)
    """)
    suspend fun getMutedUsersCount(userId: String, currentTime: Long = System.currentTimeMillis()): Int

    @Query("""
        SELECT DISTINCT muted_user_id FROM notification_mutes 
        WHERE user_id = :userId AND mute_type = 'USER' 
        AND muted_user_id IS NOT NULL
        AND (muted_until IS NULL OR muted_until > :currentTime)
        ORDER BY created_at DESC
    """)
    suspend fun getMutedUsers(userId: String, currentTime: Long = System.currentTimeMillis()): List<String>

    @Query("""
        DELETE FROM notification_mutes 
        WHERE user_id = :userId AND mute_type = 'USER' AND muted_user_id = :mutedUserId
    """)
    suspend fun deleteUserMute(userId: String, mutedUserId: String): Int
}