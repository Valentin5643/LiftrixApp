package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.example.liftrix.data.local.entity.PRNotificationPreferencesEntity

/**
 * DAO for PR Notification Preferences with mandatory user scoping.
 * 
 * CRITICAL SECURITY: All queries MUST include userId filtering to prevent data leakage.
 */
@Dao
interface PRNotificationPreferencesDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePreferences(preferences: PRNotificationPreferencesEntity)
    
    @Update
    suspend fun updatePreferences(preferences: PRNotificationPreferencesEntity)
    
    @Query("SELECT * FROM pr_notification_preferences WHERE user_id = :userId")
    suspend fun getPreferencesForUser(userId: String): PRNotificationPreferencesEntity?
    
    @Query("DELETE FROM pr_notification_preferences WHERE user_id = :userId")
    suspend fun deletePreferencesForUser(userId: String)

    // ========== OFFLINE-FIRST ARCHITECTURE METHODS (SPEC-20241228) ==========

    /**
     * Upsert prnotificationpreferences from LOCAL origin (user edit).
     * Sets isDirty=true and lastModified, triggering sync queue.
     */
    suspend fun upsertLocal(pRNotificationPreferences: PRNotificationPreferencesEntity) {
        val entity = pRNotificationPreferences.copy(
            isDirty = true,
            lastModified = System.currentTimeMillis()
        )
        _insert(entity)
    }

    /**
     * Upsert prnotificationpreferences from REMOTE origin (Firestore listener/sync).
     * Sets isDirty=false, only applies if remote is newer.
     * Does NOT trigger sync queue.
     */
    @Transaction
    suspend fun upsertFromRemote(pRNotificationPreferences: PRNotificationPreferencesEntity) {
        val local = getPRNotificationPreferencesForSync(pRNotificationPreferences.userId)
        if (local == null || pRNotificationPreferences.lastModified > local.lastModified) {
            val entity = pRNotificationPreferences.copy(
                isDirty = false,
                isSynced = true,
                syncVersion = System.currentTimeMillis()
            )
            _insert(entity)
        }
    }

    /**
     * Internal insert for shared logic.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun _insert(entity: PRNotificationPreferencesEntity)

    /**
     * Get dirty prnotificationpreferences that need upload to Firestore.
     */
    @Query("SELECT * FROM pr_notification_preferences WHERE user_id = :userId AND is_dirty = 1 ORDER BY last_modified ASC")
    suspend fun getDirtyPRNotificationPreferences(userId: String): List<PRNotificationPreferencesEntity>

    /**
     * Mark prnotificationpreferences as clean after successful Firestore upload.
     */
    @Query("UPDATE pr_notification_preferences SET is_dirty = 0, is_synced = 1, sync_version = :syncVersion WHERE user_id IN (:ids) AND user_id = :userId")
    suspend fun markAsClean(ids: List<String>, userId: String, syncVersion: Long = System.currentTimeMillis()): Int

    /**
     * Get local prnotificationpreferences for remote deduplication.
     */
    @Query("SELECT * FROM pr_notification_preferences WHERE user_id = :userId LIMIT 1")
    suspend fun getPRNotificationPreferencesForSync(userId: String): PRNotificationPreferencesEntity?

    // ========== END OFFLINE-FIRST METHODS ==========
}
