package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.example.liftrix.data.local.entity.GymBuddyEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for gym buddy relationship operations with mandatory user scoping.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 * 
 * CRITICAL SECURITY: All queries MUST include userId filtering to prevent data leakage.
 */
@Dao
interface GymBuddyDao {

    // ========================================
    // Gym Buddy Queries (User-Scoped)
    // ========================================

    @Query("SELECT * FROM gym_buddies WHERE user_id = :userId ORDER BY created_at DESC")
    suspend fun getGymBuddies(userId: String): List<GymBuddyEntity>

    @Query("SELECT * FROM gym_buddies WHERE user_id = :userId ORDER BY created_at DESC")
    fun observeGymBuddies(userId: String): Flow<List<GymBuddyEntity>>

    @Query("SELECT * FROM gym_buddies WHERE user_id = :userId AND buddy_id = :buddyId")
    suspend fun getGymBuddy(userId: String, buddyId: String): GymBuddyEntity?

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM gym_buddies 
            WHERE user_id = :userId AND buddy_id = :buddyId
        )
    """)
    suspend fun isGymBuddy(userId: String, buddyId: String): Boolean

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM gym_buddies gb1
            INNER JOIN gym_buddies gb2 
            ON gb1.user_id = gb2.buddy_id AND gb1.buddy_id = gb2.user_id
            WHERE gb1.user_id = :userId AND gb1.buddy_id = :buddyId
        )
    """)
    suspend fun areMutualGymBuddies(userId: String, buddyId: String): Boolean

    // ========================================
    // Gym Buddy Count and Stats
    // ========================================

    @Query("SELECT COUNT(*) FROM gym_buddies WHERE user_id = :userId")
    suspend fun getGymBuddyCount(userId: String): Int

    @Query("SELECT COUNT(*) FROM gym_buddies WHERE user_id = :userId")
    fun observeGymBuddyCount(userId: String): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM gym_buddies 
        WHERE user_id = :userId AND paired_via_qr = 1
    """)
    suspend fun getQrPairedBuddyCount(userId: String): Int

    // ========================================
    // PR Notification Management
    // ========================================

    @Query("""
        SELECT * FROM gym_buddies 
        WHERE user_id = :userId 
        AND (
            last_pr_notification_sent IS NULL 
            OR (strftime('%s', 'now') * 1000) - last_pr_notification_sent > (notification_cooldown_hours * 60 * 60 * 1000)
        )
        ORDER BY created_at DESC
    """)
    suspend fun getBuddiesEligibleForPrNotification(userId: String): List<GymBuddyEntity>

    @Query("""
        UPDATE gym_buddies 
        SET last_pr_notification_sent = :timestamp
        WHERE user_id = :userId AND buddy_id = :buddyId
    """)
    suspend fun updatePrNotificationSent(userId: String, buddyId: String, timestamp: Long): Int

    @Query("""
        UPDATE gym_buddies 
        SET notification_cooldown_hours = :cooldownHours
        WHERE user_id = :userId AND buddy_id = :buddyId
    """)
    suspend fun updateNotificationCooldown(userId: String, buddyId: String, cooldownHours: Int): Int

    // ========================================
    // Gym Buddy Management
    // ========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGymBuddy(gymBuddy: GymBuddyEntity): Long

    @Update
    suspend fun updateGymBuddy(gymBuddy: GymBuddyEntity): Int

    @Query("""
        UPDATE gym_buddies 
        SET buddy_nickname = :nickname
        WHERE user_id = :userId AND buddy_id = :buddyId
    """)
    suspend fun updateBuddyNickname(userId: String, buddyId: String, nickname: String?): Int

    @Query("""
        UPDATE gym_buddies 
        SET pairing_location = :location
        WHERE user_id = :userId AND buddy_id = :buddyId
    """)
    suspend fun updatePairingLocation(userId: String, buddyId: String, location: String?): Int

    // ========================================
    // QR Code Pairing
    // ========================================

    @Query("""
        SELECT * FROM gym_buddies 
        WHERE user_id = :userId AND paired_via_qr = 1
        ORDER BY created_at DESC
    """)
    suspend fun getQrPairedBuddies(userId: String): List<GymBuddyEntity>

    @Query("""
        UPDATE gym_buddies 
        SET paired_via_qr = :pairedViaQr, pairing_location = :location
        WHERE user_id = :userId AND buddy_id = :buddyId
    """)
    suspend fun updateQrPairingInfo(
        userId: String, 
        buddyId: String, 
        pairedViaQr: Boolean, 
        location: String?
    ): Int

    // ========================================
    // Gym Buddy Deletion
    // ========================================

    @Delete
    suspend fun deleteGymBuddy(gymBuddy: GymBuddyEntity): Int

    @Query("DELETE FROM gym_buddies WHERE user_id = :userId AND buddy_id = :buddyId")
    suspend fun deleteGymBuddy(userId: String, buddyId: String): Int

    @Query("DELETE FROM gym_buddies WHERE user_id = :userId")
    suspend fun deleteAllGymBuddiesForUser(userId: String): Int

    // ========================================
    // Sync Management
    // ========================================

    @Query("SELECT * FROM gym_buddies WHERE user_id = :userId AND is_synced = 0")
    suspend fun getUnsyncedGymBuddies(userId: String): List<GymBuddyEntity>

    @Query("""
        UPDATE gym_buddies 
        SET is_synced = :isSynced, sync_version = :version
        WHERE id = :gymBuddyId
    """)
    suspend fun updateSyncStatus(gymBuddyId: String, isSynced: Boolean, version: Int): Int

    // ========================================
    // Analytics Support
    // ========================================

    @Query("""
        SELECT AVG(notification_cooldown_hours) FROM gym_buddies 
        WHERE user_id = :userId
    """)
    suspend fun getAverageCooldownHours(userId: String): Double?

    @Query("""
        SELECT COUNT(*) FROM gym_buddies 
        WHERE user_id = :userId 
        AND last_pr_notification_sent IS NOT NULL
    """)
    suspend fun getNotifiedBuddyCount(userId: String): Int

    // ========== OFFLINE-FIRST ARCHITECTURE METHODS (SPEC-20241228) ==========

    /**
     * Upsert gymbuddy from LOCAL origin (user edit).
     * Sets isDirty=true and lastModified, triggering sync queue.
     */
    suspend fun upsertLocal(gymBuddy: GymBuddyEntity) {
        val entity = gymBuddy.copy(
            isDirty = true,
            lastModified = System.currentTimeMillis()
        )
        _insert(entity)
    }

    /**
     * Upsert gymbuddy from REMOTE origin (Firestore listener/sync).
     * Sets isDirty=false, only applies if remote is newer.
     * Does NOT trigger sync queue.
     */
    @Transaction
    suspend fun upsertFromRemote(gymBuddy: GymBuddyEntity) {
        val local = getGymBuddyForSync(gymBuddy.id, gymBuddy.userId)
        if (local == null || gymBuddy.lastModified > local.lastModified) {
            val entity = gymBuddy.copy(
                isDirty = false,
                isSynced = true,
                syncVersion = System.currentTimeMillis().toInt()
            )
            _insert(entity)
        }
    }

    /**
     * Internal insert for shared logic.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun _insert(entity: GymBuddyEntity)

    /**
     * Get dirty gymbuddy that need upload to Firestore.
     */
    @Query("SELECT * FROM gym_buddies WHERE user_id = :userId AND is_dirty = 1 ORDER BY last_modified ASC")
    suspend fun getDirtyGymBuddies(userId: String): List<GymBuddyEntity>

    /**
     * Mark gymbuddy as clean after successful Firestore upload.
     */
    @Query("UPDATE gym_buddies SET is_dirty = 0, is_synced = 1, sync_version = :syncVersion WHERE id IN (:ids) AND user_id = :userId")
    suspend fun markAsClean(ids: List<String>, userId: String, syncVersion: Long = System.currentTimeMillis()): Int

    /**
     * Get local gymbuddy for remote deduplication.
     */
    @Query("SELECT * FROM gym_buddies WHERE id = :id AND user_id = :userId LIMIT 1")
    suspend fun getGymBuddyForSync(id: String, userId: String): GymBuddyEntity?

    // ========== END OFFLINE-FIRST METHODS ==========
}
