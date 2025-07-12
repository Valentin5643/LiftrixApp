package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.SettingsEntity
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
} 