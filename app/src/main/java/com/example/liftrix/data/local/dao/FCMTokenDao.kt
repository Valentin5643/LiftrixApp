package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
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

    // ========== OFFLINE-FIRST ARCHITECTURE METHODS (SPEC-20241228) ==========

    /**
     * Upsert fcmtoken from LOCAL origin (user edit).
     * Sets isDirty=true and lastModified, triggering sync queue.
     */
    suspend fun upsertLocal(fCMToken: FCMTokenEntity) {
        val entity = fCMToken.copy(
            isDirty = true,
            lastModified = System.currentTimeMillis()
        )
        _insert(entity)
    }

    /**
     * Upsert fcmtoken from REMOTE origin (Firestore listener/sync).
     * Sets isDirty=false, only applies if remote is newer.
     * Does NOT trigger sync queue.
     */
    @Transaction
    suspend fun upsertFromRemote(fCMToken: FCMTokenEntity) {
        val local = getFCMTokenForSync(fCMToken.id, fCMToken.userId)
        if (local == null || fCMToken.lastModified > local.lastModified) {
            val entity = fCMToken.copy(
                isDirty = false,
                isSynced = true
            )
            _insert(entity)
        }
    }

    /**
     * Internal insert for shared logic.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun _insert(entity: FCMTokenEntity)

    /**
     * Get dirty fcmtoken that need upload to Firestore.
     */
    @Query("SELECT * FROM fcm_tokens WHERE user_id = :userId AND is_dirty = 1 ORDER BY last_modified ASC")
    suspend fun getDirtyFCMTokens(userId: String): List<FCMTokenEntity>

    /**
     * Mark fcmtoken as clean after successful Firestore upload.
     */
    @Query("UPDATE fcm_tokens SET is_dirty = 0, is_synced = 1 WHERE id IN (:ids) AND user_id = :userId")
    suspend fun markAsClean(ids: List<String>, userId: String): Int

    /**
     * Get local fcmtoken for remote deduplication.
     */
    @Query("SELECT * FROM fcm_tokens WHERE id = :id AND user_id = :userId LIMIT 1")
    suspend fun getFCMTokenForSync(id: String, userId: String): FCMTokenEntity?

    // ========== END OFFLINE-FIRST METHODS ==========
}
