package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.FCMTokenEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for FCM token operations with mandatory user scoping.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 * 
 * CRITICAL SECURITY: All queries MUST include userId filtering to prevent data leakage.
 */
@Dao
interface FCMTokenDao {

    // ========================================
    // Token Retrieval (User-Scoped)
    // ========================================

    @Query("SELECT * FROM fcm_tokens WHERE user_id = :userId AND is_active = 1")
    fun observeActiveTokensForUser(userId: String): Flow<List<FCMTokenEntity>>

    @Query("SELECT * FROM fcm_tokens WHERE user_id = :userId AND is_active = 1")
    suspend fun getActiveTokensForUser(userId: String): List<FCMTokenEntity>

    @Query("SELECT * FROM fcm_tokens WHERE user_id = :userId AND device_id = :deviceId")
    suspend fun getTokenForDevice(userId: String, deviceId: String): FCMTokenEntity?

    @Query("SELECT token FROM fcm_tokens WHERE user_id = :userId AND is_active = 1 LIMIT 1")
    suspend fun getActiveTokenForUser(userId: String): String?

    @Query("SELECT EXISTS(SELECT 1 FROM fcm_tokens WHERE user_id = :userId AND device_id = :deviceId AND is_active = 1)")
    suspend fun hasActiveTokenForDevice(userId: String, deviceId: String): Boolean

    // ========================================
    // Token Management
    // ========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToken(token: FCMTokenEntity): Long

    @Update
    suspend fun updateToken(token: FCMTokenEntity): Int

    @Query("""
        UPDATE fcm_tokens 
        SET token = :token, updated_at = :updatedAt, is_synced = 0
        WHERE user_id = :userId AND device_id = :deviceId
    """)
    suspend fun updateTokenForDevice(
        userId: String,
        deviceId: String,
        token: String,
        updatedAt: Long
    ): Int

    @Query("""
        UPDATE fcm_tokens 
        SET last_used = :lastUsed
        WHERE user_id = :userId AND device_id = :deviceId
    """)
    suspend fun updateLastUsed(userId: String, deviceId: String, lastUsed: Long): Int

    @Query("""
        UPDATE fcm_tokens 
        SET is_active = :isActive, updated_at = :updatedAt
        WHERE user_id = :userId AND device_id = :deviceId
    """)
    suspend fun updateActiveStatus(
        userId: String,
        deviceId: String,
        isActive: Boolean,
        updatedAt: Long
    ): Int

    // ========================================
    // Token Cleanup
    // ========================================

    @Query("""
        UPDATE fcm_tokens 
        SET is_active = 0, updated_at = :updatedAt
        WHERE user_id = :userId AND device_id != :currentDeviceId AND is_active = 1
    """)
    suspend fun deactivateOtherDeviceTokens(
        userId: String,
        currentDeviceId: String,
        updatedAt: Long
    ): Int

    @Query("DELETE FROM fcm_tokens WHERE user_id = :userId AND device_id = :deviceId")
    suspend fun deleteTokenForDevice(userId: String, deviceId: String): Int

    @Query("""
        DELETE FROM fcm_tokens 
        WHERE user_id = :userId AND is_active = 0 AND last_used < :olderThan
    """)
    suspend fun deleteInactiveTokensOlderThan(userId: String, olderThan: Long): Int

    // ========================================
    // Sync Management
    // ========================================

    @Query("SELECT * FROM fcm_tokens WHERE user_id = :userId AND is_synced = 0")
    suspend fun getUnsyncedTokensForUser(userId: String): List<FCMTokenEntity>

    @Query("""
        UPDATE fcm_tokens 
        SET is_synced = :isSynced
        WHERE user_id = :userId AND id = :tokenId
    """)
    suspend fun updateSyncStatus(userId: String, tokenId: String, isSynced: Boolean): Int

    // ========================================
    // Analytics Support
    // ========================================

    @Query("SELECT COUNT(*) FROM fcm_tokens WHERE user_id = :userId AND is_active = 1")
    suspend fun getActiveTokenCount(userId: String): Int

    @Query("SELECT platform, COUNT(*) as count FROM fcm_tokens WHERE user_id = :userId AND is_active = 1 GROUP BY platform")
    suspend fun getTokenCountByPlatform(userId: String): Map<@MapColumn(columnName = "platform") String, @MapColumn(columnName = "count") Int>
}