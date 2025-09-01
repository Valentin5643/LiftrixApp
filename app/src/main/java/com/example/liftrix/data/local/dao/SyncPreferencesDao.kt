package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.example.liftrix.data.local.entity.SyncPreferencesEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Sync Preferences operations with mandatory user scoping.
 * Part of Phase 2: Sync Infrastructure Enhancement from SPEC-20250901-todo-implementation.
 * 
 * CRITICAL SECURITY: All queries MUST include userId filtering to prevent data leakage.
 */
@Dao
interface SyncPreferencesDao {
    
    /**
     * Inserts or updates sync preferences for a user.
     * Uses REPLACE strategy for efficient upsert operation.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSyncPreferences(preferences: SyncPreferencesEntity)
    
    /**
     * Upserts sync preferences using Room's @Upsert annotation.
     */
    @Upsert
    suspend fun upsertSyncPreferences(preferences: SyncPreferencesEntity)
    
    /**
     * Gets sync preferences for a specific user.
     * @param userId The user ID to get preferences for (mandatory user scoping)
     * @return SyncPreferencesEntity or null if not found
     */
    @Query("SELECT * FROM sync_preferences WHERE user_id = :userId")
    suspend fun getSyncPreferences(userId: String): SyncPreferencesEntity?
    
    /**
     * Observes sync preferences changes for a specific user.
     * @param userId The user ID to observe (mandatory user scoping)
     * @return Flow of SyncPreferencesEntity or null if not found
     */
    @Query("SELECT * FROM sync_preferences WHERE user_id = :userId")
    fun observeSyncPreferences(userId: String): Flow<SyncPreferencesEntity?>
    
    /**
     * Gets auto sync enabled status for a specific user.
     * @param userId The user ID to check (mandatory user scoping)
     * @return Boolean or null if preferences not found
     */
    @Query("SELECT auto_sync_enabled FROM sync_preferences WHERE user_id = :userId")
    suspend fun getAutoSyncEnabled(userId: String): Boolean?
    
    /**
     * Observes auto sync enabled status for a specific user.
     * @param userId The user ID to observe (mandatory user scoping)
     * @return Flow of Boolean or null if preferences not found
     */
    @Query("SELECT auto_sync_enabled FROM sync_preferences WHERE user_id = :userId")
    fun observeAutoSyncEnabled(userId: String): Flow<Boolean?>
    
    /**
     * Gets last sync timestamp for a specific user.
     * @param userId The user ID to check (mandatory user scoping)
     * @return Last sync timestamp or null if not found
     */
    @Query("SELECT last_sync_timestamp FROM sync_preferences WHERE user_id = :userId")
    suspend fun getLastSyncTimestamp(userId: String): Long?
    
    /**
     * Updates auto sync enabled status for a specific user.
     * @param userId The user ID to update (mandatory user scoping)
     * @param enabled Whether auto sync should be enabled
     */
    @Query("UPDATE sync_preferences SET auto_sync_enabled = :enabled, last_modified = :timestamp WHERE user_id = :userId")
    suspend fun updateAutoSyncEnabled(userId: String, enabled: Boolean, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Updates last sync timestamp for a specific user.
     * @param userId The user ID to update (mandatory user scoping)
     * @param timestamp The new last sync timestamp
     */
    @Query("UPDATE sync_preferences SET last_sync_timestamp = :timestamp, last_modified = :lastModified WHERE user_id = :userId")
    suspend fun updateLastSyncTimestamp(userId: String, timestamp: Long, lastModified: Long = System.currentTimeMillis())
    
    /**
     * Updates sync interval for a specific user.
     * @param userId The user ID to update (mandatory user scoping)
     * @param intervalMinutes The sync interval in minutes
     */
    @Query("UPDATE sync_preferences SET sync_interval_minutes = :intervalMinutes, last_modified = :timestamp WHERE user_id = :userId")
    suspend fun updateSyncInterval(userId: String, intervalMinutes: Long, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Gets all users who have auto sync enabled.
     * Used for scheduling sync operations.
     * @return List of user IDs with auto sync enabled
     */
    @Query("SELECT user_id FROM sync_preferences WHERE auto_sync_enabled = 1")
    suspend fun getUsersWithAutoSyncEnabled(): List<String>
    
    /**
     * Gets preferences that need to be synced.
     * @return List of unsynced preference entries
     */
    @Query("SELECT * FROM sync_preferences WHERE is_synced = 0 ORDER BY last_modified ASC")
    suspend fun getUnsyncedPreferences(): List<SyncPreferencesEntity>
    
    /**
     * Marks preferences as synced for a specific user.
     * @param userId The user ID to mark as synced (mandatory user scoping)
     * @param syncVersion The sync version to set
     */
    @Query("UPDATE sync_preferences SET is_synced = 1, sync_version = :syncVersion WHERE user_id = :userId")
    suspend fun markAsSynced(userId: String, syncVersion: Long)
    
    /**
     * Deletes sync preferences for a specific user.
     * @param userId The user ID to delete preferences for (mandatory user scoping)
     */
    @Query("DELETE FROM sync_preferences WHERE user_id = :userId")
    suspend fun deleteSyncPreferences(userId: String)
    
    /**
     * Gets users due for sync based on their interval settings.
     * @param currentTimestamp Current timestamp to compare against
     * @return List of user IDs due for sync
     */
    @Query("""
        SELECT user_id FROM sync_preferences 
        WHERE auto_sync_enabled = 1 
        AND (last_sync_timestamp IS NULL OR 
             (last_sync_timestamp + (sync_interval_minutes * 60 * 1000)) <= :currentTimestamp)
    """)
    suspend fun getUsersDueForSync(currentTimestamp: Long): List<String>
    
    /**
     * Gets sync preferences count for monitoring.
     * @return Total number of sync preference entries
     */
    @Query("SELECT COUNT(*) FROM sync_preferences")
    suspend fun getPreferencesCount(): Int
}