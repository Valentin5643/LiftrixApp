package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.example.liftrix.data.local.entity.BlockedUserEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for blocked user operations with mandatory user scoping.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 * 
 * CRITICAL SECURITY: All queries MUST include userId filtering to prevent data leakage.
 */
@Dao
interface BlockedUserDao {

    // ========================================
    // Blocked User Queries (User-Scoped)
    // ========================================

    @Query("SELECT * FROM blocked_users WHERE user_id = :userId ORDER BY blocked_at DESC")
    suspend fun getBlockedUsers(userId: String): List<BlockedUserEntity>

    @Query("SELECT * FROM blocked_users WHERE user_id = :userId ORDER BY blocked_at DESC")
    fun observeBlockedUsers(userId: String): Flow<List<BlockedUserEntity>>

    @Query("SELECT * FROM blocked_users WHERE user_id = :userId AND blocked_user_id = :blockedUserId")
    suspend fun getBlockedUser(userId: String, blockedUserId: String): BlockedUserEntity?

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM blocked_users 
            WHERE user_id = :userId AND blocked_user_id = :targetUserId
        )
    """)
    suspend fun isUserBlocked(userId: String, targetUserId: String): Boolean

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM blocked_users 
            WHERE user_id = :targetUserId AND blocked_user_id = :userId
        )
    """)
    suspend fun isBlockedByUser(userId: String, targetUserId: String): Boolean

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM blocked_users 
            WHERE (user_id = :userId AND blocked_user_id = :targetUserId)
            OR (user_id = :targetUserId AND blocked_user_id = :userId)
        )
    """)
    suspend fun hasBlockRelationship(userId: String, targetUserId: String): Boolean

    // ========================================
    // Blocked User IDs for Privacy Filtering
    // ========================================

    @Query("SELECT blocked_user_id FROM blocked_users WHERE user_id = :userId")
    suspend fun getBlockedUserIds(userId: String): List<String>

    @Query("SELECT blocked_user_id FROM blocked_users WHERE user_id = :userId")
    fun observeBlockedUserIds(userId: String): Flow<List<String>>

    @Query("SELECT user_id FROM blocked_users WHERE blocked_user_id = :userId")
    suspend fun getUsersWhoBlockedMe(userId: String): List<String>

    // ========================================
    // Blocked User Count and Stats
    // ========================================

    @Query("SELECT COUNT(*) FROM blocked_users WHERE user_id = :userId")
    suspend fun getBlockedUserCount(userId: String): Int

    @Query("SELECT COUNT(*) FROM blocked_users WHERE user_id = :userId")
    fun observeBlockedUserCount(userId: String): Flow<Int>

    // ========================================
    // Block Reason Analysis
    // ========================================

    @Query("""
        SELECT reason, COUNT(*) as count 
        FROM blocked_users 
        WHERE user_id = :userId AND reason IS NOT NULL
        GROUP BY reason
        ORDER BY count DESC
    """)
    suspend fun getBlockReasonStats(userId: String): List<BlockReasonStat>

    // ========================================
    // Blocked User Management
    // ========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedUser(blockedUser: BlockedUserEntity): Long

    /**
     * Block a user by creating a blocked user entity
     */
    suspend fun blockUser(userId: String, blockedUserId: String) {
        val blockedUser = BlockedUserEntity(
            id = "${userId}_${blockedUserId}",
            userId = userId,
            blockedUserId = blockedUserId,
            blockedAt = System.currentTimeMillis(),
            reason = null,
            isSynced = false
        )
        insertBlockedUser(blockedUser)
    }

    @Update
    suspend fun updateBlockedUser(blockedUser: BlockedUserEntity): Int

    @Query("""
        UPDATE blocked_users 
        SET reason = :reason
        WHERE user_id = :userId AND blocked_user_id = :blockedUserId
    """)
    suspend fun updateBlockReason(userId: String, blockedUserId: String, reason: String?): Int

    // ========================================
    // Blocked User Deletion
    // ========================================

    @Delete
    suspend fun deleteBlockedUser(blockedUser: BlockedUserEntity): Int

    @Query("DELETE FROM blocked_users WHERE user_id = :userId AND blocked_user_id = :blockedUserId")
    suspend fun unblockUser(userId: String, blockedUserId: String): Int

    @Query("DELETE FROM blocked_users WHERE user_id = :userId")
    suspend fun deleteAllBlockedUsersForUser(userId: String): Int

    // ========================================
    // Batch Block Operations
    // ========================================

    @Query("DELETE FROM blocked_users WHERE user_id = :userId AND blocked_user_id IN (:blockedUserIds)")
    suspend fun unblockUsers(userId: String, blockedUserIds: List<String>): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedUsers(blockedUsers: List<BlockedUserEntity>): List<Long>

    // ========================================
    // Sync Management
    // ========================================

    @Query("SELECT * FROM blocked_users WHERE user_id = :userId AND is_synced = 0")
    suspend fun getUnsyncedBlockedUsers(userId: String): List<BlockedUserEntity>

    @Query("""
        UPDATE blocked_users 
        SET is_synced = :isSynced
        WHERE id = :blockedUserId
    """)
    suspend fun updateSyncStatus(blockedUserId: String, isSynced: Boolean): Int

    // ========================================
    // Analytics Support
    // ========================================

    @Query("""
        SELECT COUNT(*) FROM blocked_users 
        WHERE user_id = :userId 
        AND blocked_at >= :since
    """)
    suspend fun getRecentBlockCount(userId: String, since: Long): Int

    /**
     * Data class for block reason statistics
     */
    data class BlockReasonStat(
        val reason: String,
        val count: Int
    )

    // ========== OFFLINE-FIRST ARCHITECTURE METHODS (SPEC-20241228) ==========

    /**
     * Upsert blockeduser from LOCAL origin (user edit).
     * Sets isDirty=true and lastModified, triggering sync queue.
     */
    suspend fun upsertLocal(blockedUser: BlockedUserEntity) {
        val entity = blockedUser.copy(
            isDirty = true,
            lastModified = System.currentTimeMillis()
        )
        _insert(entity)
    }

    /**
     * Upsert blockeduser from REMOTE origin (Firestore listener/sync).
     * Sets isDirty=false, only applies if remote is newer.
     * Does NOT trigger sync queue.
     */
    @Transaction
    suspend fun upsertFromRemote(blockedUser: BlockedUserEntity) {
        val local = getBlockedUserForSync(blockedUser.id, blockedUser.userId)
        if (local == null || blockedUser.lastModified > local.lastModified) {
            val entity = blockedUser.copy(
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
    suspend fun _insert(entity: BlockedUserEntity)

    /**
     * Get dirty blockeduser that need upload to Firestore.
     */
    @Query("SELECT * FROM blocked_users WHERE user_id = :userId AND is_dirty = 1 ORDER BY last_modified ASC")
    suspend fun getDirtyBlockedUsers(userId: String): List<BlockedUserEntity>

    /**
     * Mark blockeduser as clean after successful Firestore upload.
     */
    @Query("UPDATE blocked_users SET is_dirty = 0, is_synced = 1 WHERE id IN (:ids) AND user_id = :userId")
    suspend fun markAsClean(ids: List<String>, userId: String): Int

    /**
     * Get local blockeduser for remote deduplication.
     */
    @Query("SELECT * FROM blocked_users WHERE id = :id AND user_id = :userId LIMIT 1")
    suspend fun getBlockedUserForSync(id: String, userId: String): BlockedUserEntity?

    // ========== END OFFLINE-FIRST METHODS ==========
}
