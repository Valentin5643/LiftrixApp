package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.example.liftrix.data.local.entity.SettingsEntity
import com.example.liftrix.domain.model.WeightUnit
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for user settings operations
 * 
 * Provides methods to retrieve, insert, and update user settings
 * Uses Flow for reactive data updates and proper conflict resolution
 */
@Dao
interface SettingsDao {
    
    /**
     * Retrieves user settings for a specific user
     * 
     * @param userId The user's unique identifier
     * @return Flow of SettingsEntity or null if not found
     */
    @Query("SELECT * FROM user_settings WHERE user_id = :userId")
    fun getUserSettings(userId: String): Flow<SettingsEntity?>
    
    /**
     * Retrieves user settings synchronously for migration purposes
     * 
     * @param userId The user's unique identifier
     * @return SettingsEntity or null if not found
     */
    @Query("SELECT * FROM user_settings WHERE user_id = :userId")
    suspend fun getUserSettingsSync(userId: String): SettingsEntity?
    
    /**
     * Inserts new user settings or replaces existing ones
     * 
     * @param settings The settings entity to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: SettingsEntity)
    
    /**
     * Updates existing user settings
     * 
     * @param settings The settings entity to update
     */
    @Update
    suspend fun updateSettings(settings: SettingsEntity)
    
    /**
     * Updates dark mode setting for a user
     * 
     * @param userId The user's unique identifier
     * @param darkMode The new dark mode value
     * @param updatedAt The timestamp of the update
     */
    @Query("UPDATE user_settings SET dark_mode = :darkMode, updated_at = :updatedAt WHERE user_id = :userId")
    suspend fun updateDarkMode(userId: String, darkMode: Boolean, updatedAt: java.time.Instant)
    
    /**
     * Updates notification setting for a user
     * 
     * @param userId The user's unique identifier
     * @param enabled The new notification enabled value
     * @param updatedAt The timestamp of the update
     */
    @Query("UPDATE user_settings SET notifications_enabled = :enabled, updated_at = :updatedAt WHERE user_id = :userId")
    suspend fun updateNotifications(userId: String, enabled: Boolean, updatedAt: java.time.Instant)
    
    /**
     * Updates weight unit setting for a user
     * 
     * @param userId The user's unique identifier
     * @param weightUnit The new weight unit preference
     * @param updatedAt The timestamp of the update
     */
    @Query("UPDATE user_settings SET weight_unit = :weightUnit, updated_at = :updatedAt WHERE user_id = :userId")
    suspend fun updateWeightUnit(userId: String, weightUnit: WeightUnit, updatedAt: java.time.Instant)
    
    /**
     * Updates terminology preference for a user
     * 
     * @param userId The user's unique identifier
     * @param preference The new terminology preference
     * @param updatedAt The timestamp of the update
     */
    @Query("UPDATE user_settings SET terminology_preference = :preference, updated_at = :updatedAt WHERE user_id = :userId")
    suspend fun updateTerminologyPreference(userId: String, preference: String, updatedAt: java.time.Instant)
    
    /**
     * Updates migration explanation seen status for a user
     * 
     * @param userId The user's unique identifier
     * @param seen Whether the explanation has been shown
     * @param updatedAt The timestamp of the update
     */
    @Query("UPDATE user_settings SET migration_explanation_seen = :seen, updated_at = :updatedAt WHERE user_id = :userId")
    suspend fun updateMigrationExplanationSeen(userId: String, seen: Boolean, updatedAt: java.time.Instant)
    
    /**
     * Updates migration completed status for a user
     * 
     * @param userId The user's unique identifier
     * @param completed Whether the migration is complete
     * @param updatedAt The timestamp of the update
     */
    @Query("UPDATE user_settings SET migration_completed = :completed, updated_at = :updatedAt WHERE user_id = :userId")
    suspend fun updateMigrationCompleted(userId: String, completed: Boolean, updatedAt: java.time.Instant)
    
    /**
     * Deletes user settings for a specific user
     * 
     * @param userId The user's unique identifier
     */
    @Query("DELETE FROM user_settings WHERE user_id = :userId")
    suspend fun deleteUserSettings(userId: String)
    
    /**
     * Checks if settings exist for a user
     * 
     * @param userId The user's unique identifier
     * @return True if settings exist, false otherwise
     */
    @Query("SELECT COUNT(*) > 0 FROM user_settings WHERE user_id = :userId")
    suspend fun hasSettings(userId: String): Boolean
    
    /**
     * Upserts settings (insert or update) with automatic timestamp
     * Convenience method for the SettingsPersistenceManager
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: SettingsEntity)
    
    // Convenience methods without explicit timestamp for SettingsPersistenceManager
    
    /**
     * Updates dark mode setting with automatic timestamp
     */
    @Query("UPDATE user_settings SET dark_mode = :darkMode, updated_at = datetime('now') WHERE user_id = :userId")
    suspend fun updateDarkMode(userId: String, darkMode: Boolean)
    
    /**
     * Updates notification setting with automatic timestamp
     */
    @Query("UPDATE user_settings SET notifications_enabled = :enabled, updated_at = datetime('now') WHERE user_id = :userId")
    suspend fun updateNotificationsEnabled(userId: String, enabled: Boolean)
    
    /**
     * Updates weight unit setting with automatic timestamp (using string for consistency)
     */
    @Query("UPDATE user_settings SET weight_unit = :weightUnit, updated_at = datetime('now') WHERE user_id = :userId")
    suspend fun updateWeightUnit(userId: String, weightUnit: String)
    
    /**
     * Updates terminology preference with automatic timestamp
     */
    @Query("UPDATE user_settings SET terminology_preference = :preference, updated_at = datetime('now') WHERE user_id = :userId")
    suspend fun updateTerminologyPreference(userId: String, preference: String)
    
    /**
     * Updates migration explanation seen status with automatic timestamp
     */
    @Query("UPDATE user_settings SET migration_explanation_seen = :seen, updated_at = datetime('now') WHERE user_id = :userId")
    suspend fun updateMigrationExplanationSeen(userId: String, seen: Boolean)
    
    /**
     * Updates migration completed status with automatic timestamp
     */
    @Query("UPDATE user_settings SET migration_completed = :completed, updated_at = datetime('now') WHERE user_id = :userId")
    suspend fun updateMigrationCompleted(userId: String, completed: Boolean)
    
    // ========================================
    // Sync Management
    // ========================================
    
    /**
     * Gets unsynced settings for a user
     */
    @Query("SELECT * FROM user_settings WHERE user_id = :userId AND is_synced = 0")
    suspend fun getUnsyncedSettings(userId: String): SettingsEntity?
    
    /**
     * Marks settings as synced
     */
    @Query("UPDATE user_settings SET is_synced = :isSynced, sync_version = :version WHERE user_id = :userId")
    suspend fun markAsSynced(userId: String, isSynced: Boolean, version: Int): Int
    
    /**
     * Gets settings for user (suspending version for sync worker)
     */
    @Query("SELECT * FROM user_settings WHERE user_id = :userId")
    suspend fun getSettingsForUser(userId: String): SettingsEntity?
    
    // ========================================
    // Additional Settings Updates
    // ========================================
    
    /**
     * Updates distance unit setting
     */
    @Query("UPDATE user_settings SET distance_unit = :distanceUnit, updated_at = datetime('now') WHERE user_id = :userId")
    suspend fun updateDistanceUnit(userId: String, distanceUnit: String)
    
    /**
     * Updates privacy settings
     */
    @Query("UPDATE user_settings SET private_profile = :privateProfile, hide_stats = :hideStats, updated_at = datetime('now') WHERE user_id = :userId")
    suspend fun updatePrivacySettings(userId: String, privateProfile: Boolean, hideStats: Boolean)
    
    /**
     * Updates communication settings
     */
    @Query("UPDATE user_settings SET allow_messages = :allowMessages, auto_play_videos = :autoPlayVideos, updated_at = datetime('now') WHERE user_id = :userId")
    suspend fun updateCommunicationSettings(userId: String, allowMessages: Boolean, autoPlayVideos: Boolean)

    // ========== OFFLINE-FIRST ARCHITECTURE METHODS (SPEC-20241228) ==========

    /**
     * Upsert settings from LOCAL origin (user edit).
     * Sets isDirty=true and lastModified, triggering sync queue.
     */
    suspend fun upsertLocal(settings: SettingsEntity) {
        val entity = settings.copy(
            isDirty = true,
            lastModified = System.currentTimeMillis()
        )
        _insert(entity)
    }

    /**
     * Upsert settings from REMOTE origin (Firestore listener/sync).
     * Sets isDirty=false, only applies if remote is newer.
     * Does NOT trigger sync queue.
     */
    @Transaction
    suspend fun upsertFromRemote(settings: SettingsEntity) {
        val local = getSettingsForSync(settings.userId)
        if (local == null || settings.lastModified > local.lastModified) {
            val entity = settings.copy(
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
    suspend fun _insert(entity: SettingsEntity)

    /**
     * Get dirty settings that need upload to Firestore.
     */
    @Query("SELECT * FROM user_settings WHERE user_id = :userId AND is_dirty = 1 ORDER BY last_modified ASC")
    suspend fun getDirtySettings(userId: String): List<SettingsEntity>

    /**
     * Mark settings as clean after successful Firestore upload.
     */
    @Query("UPDATE user_settings SET is_dirty = 0, is_synced = 1, sync_version = :syncVersion WHERE user_id IN (:ids) AND user_id = :userId")
    suspend fun markAsClean(ids: List<String>, userId: String, syncVersion: Long = System.currentTimeMillis()): Int

    /**
     * Get local settings for remote deduplication.
     */
    @Query("SELECT * FROM user_settings WHERE user_id = :userId LIMIT 1")
    suspend fun getSettingsForSync(userId: String): SettingsEntity?

    // ========== END OFFLINE-FIRST METHODS ==========
} 
