package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.example.liftrix.data.local.entity.WidgetPreferenceEntity
import com.example.liftrix.data.local.entity.DashboardConfigurationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for widget preferences and dashboard configuration operations.
 * 
 * Provides methods to store, retrieve, and manage user dashboard customization
 * preferences including widget visibility, ordering, sizing, and layout settings.
 * All operations are user-scoped for security and multi-user device support.
 */
@Dao
interface WidgetPreferencesDao {
    
    // Widget Preferences Operations
    
    /**
     * Get all widget preferences for a user
     * @param userId User ID to filter by
     * @return Flow of all widget preferences for the user
     */
    @Query("SELECT * FROM widget_preferences WHERE user_id = :userId ORDER BY position ASC")
    fun getWidgetPreferencesForUser(userId: String): Flow<List<WidgetPreferenceEntity>>
    
    /**
     * Get enabled widget preferences for a user in display order
     * @param userId User ID to filter by
     * @return Flow of enabled widget preferences ordered by position
     */
    @Query("SELECT * FROM widget_preferences WHERE user_id = :userId AND is_enabled = 1 ORDER BY position ASC")
    fun getEnabledWidgetPreferencesForUser(userId: String): Flow<List<WidgetPreferenceEntity>>
    
    /**
     * Get a specific widget preference by user and widget type
     * @param userId User ID to filter by
     * @param widgetType Type of widget to retrieve
     * @return Flow of specific widget preference or null if not found
     */
    @Query("SELECT * FROM widget_preferences WHERE user_id = :userId AND widget_type = :widgetType LIMIT 1")
    fun getWidgetPreference(userId: String, widgetType: String): Flow<WidgetPreferenceEntity?>
    
    /**
     * Get widget preferences by enabled status for a user
     * @param userId User ID to filter by
     * @param isEnabled Whether to get enabled or disabled widgets
     * @return Flow of widget preferences matching enabled status
     */
    @Query("SELECT * FROM widget_preferences WHERE user_id = :userId AND is_enabled = :isEnabled ORDER BY position ASC")
    fun getWidgetPreferencesByStatus(userId: String, isEnabled: Boolean): Flow<List<WidgetPreferenceEntity>>
    
    /**
     * Get unsynced widget preferences for background sync
     * @param userId User ID to filter by
     * @return List of unsynced widget preferences
     */
    @Query("SELECT * FROM widget_preferences WHERE user_id = :userId AND is_synced = 0")
    suspend fun getUnsyncedWidgetPreferences(userId: String): List<WidgetPreferenceEntity>
    
    /**
     * Insert or update a widget preference
     * @param preference The widget preference to store
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateWidgetPreference(preference: WidgetPreferenceEntity): Long
    
    /**
     * Insert or update multiple widget preferences
     * @param preferences List of widget preferences to store
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateWidgetPreferences(preferences: List<WidgetPreferenceEntity>): List<Long>
    
    /**
     * Update a widget preference
     * @param preference The widget preference to update
     */
    @Update
    suspend fun updateWidgetPreference(preference: WidgetPreferenceEntity): Int
    
    /**
     * Update widget enabled status
     * @param userId User ID to filter by
     * @param widgetType Type of widget to update
     * @param isEnabled New enabled status
     */
    @Query("UPDATE widget_preferences SET is_enabled = :isEnabled, updated_at = datetime('now'), is_synced = 0 WHERE user_id = :userId AND widget_type = :widgetType")
    suspend fun updateWidgetEnabledStatus(userId: String, widgetType: String, isEnabled: Boolean): Int
    
    /**
     * Update widget position for reordering
     * @param userId User ID to filter by
     * @param widgetType Type of widget to update
     * @param position New position
     */
    @Query("UPDATE widget_preferences SET position = :position, updated_at = datetime('now'), is_synced = 0 WHERE user_id = :userId AND widget_type = :widgetType")
    suspend fun updateWidgetPosition(userId: String, widgetType: String, position: Int): Int
    
    /**
     * Update widget sync status
     * @param userId User ID to filter by
     * @param widgetType Type of widget to update
     * @param isSynced New sync status
     * @param syncVersion New sync version
     */
    @Query("UPDATE widget_preferences SET is_synced = :isSynced, sync_version = :syncVersion WHERE user_id = :userId AND widget_type = :widgetType")
    suspend fun updateWidgetSyncStatus(userId: String, widgetType: String, isSynced: Boolean, syncVersion: Long): Int
    
    /**
     * Mark multiple widget preferences as synced
     * @param userId User ID to filter by
     * @param widgetTypes List of widget types to mark as synced
     * @param syncVersion New sync version
     */
    @Query("UPDATE widget_preferences SET is_synced = 1, sync_version = :syncVersion WHERE user_id = :userId AND widget_type IN (:widgetTypes)")
    suspend fun markWidgetPreferencesAsSynced(userId: String, widgetTypes: List<String>, syncVersion: Long): Int
    
    /**
     * Delete a widget preference
     * @param preference The widget preference to delete
     */
    @Delete
    suspend fun deleteWidgetPreference(preference: WidgetPreferenceEntity): Int
    
    /**
     * Delete all widget preferences for a user
     * @param userId User ID to filter by
     */
    @Query("DELETE FROM widget_preferences WHERE user_id = :userId")
    suspend fun deleteAllWidgetPreferencesForUser(userId: String): Int
    
    // Dashboard Configuration Operations
    
    /**
     * Get dashboard configuration for a user
     * @param userId User ID to filter by
     * @return Flow of dashboard configuration or null if not found
     */
    @Query("SELECT * FROM dashboard_configurations WHERE user_id = :userId LIMIT 1")
    fun getDashboardConfiguration(userId: String): Flow<DashboardConfigurationEntity?>
    
    /**
     * Get dashboard configuration synchronously for a user
     * @param userId User ID to filter by
     * @return Dashboard configuration or null if not found
     */
    @Query("SELECT * FROM dashboard_configurations WHERE user_id = :userId LIMIT 1")
    suspend fun getDashboardConfigurationSync(userId: String): DashboardConfigurationEntity?
    
    /**
     * Get unsynced dashboard configurations for background sync
     * @return List of unsynced dashboard configurations
     */
    @Query("SELECT * FROM dashboard_configurations WHERE is_synced = 0")
    suspend fun getUnsyncedDashboardConfigurations(): List<DashboardConfigurationEntity>
    
    /**
     * Insert or update dashboard configuration
     * @param configuration The dashboard configuration to store
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDashboardConfiguration(configuration: DashboardConfigurationEntity): Long
    
    /**
     * Update dashboard configuration
     * @param configuration The dashboard configuration to update
     */
    @Update
    suspend fun updateDashboardConfiguration(configuration: DashboardConfigurationEntity): Int
    
    /**
     * Update dashboard configuration sync status
     * @param userId User ID to filter by
     * @param isSynced New sync status
     * @param syncVersion New sync version
     */
    @Query("UPDATE dashboard_configurations SET is_synced = :isSynced, sync_version = :syncVersion WHERE user_id = :userId")
    suspend fun updateDashboardConfigurationSyncStatus(userId: String, isSynced: Boolean, syncVersion: Long): Int
    
    /**
     * Delete dashboard configuration for a user
     * @param userId User ID to filter by
     */
    @Query("DELETE FROM dashboard_configurations WHERE user_id = :userId")
    suspend fun deleteDashboardConfiguration(userId: String): Int
    
    // Batch Operations
    
    /**
     * Initialize default widget preferences for a new user
     * @param userId User ID to initialize preferences for
     * @param defaultWidgets List of default widget preferences
     */
    @Transaction
    suspend fun initializeDefaultWidgetPreferences(userId: String, defaultWidgets: List<WidgetPreferenceEntity>) {
        insertOrUpdateWidgetPreferences(defaultWidgets)
    }
    
    /**
     * Reorder widgets by updating positions in batch
     * @param userId User ID to filter by
     * @param widgetTypePositions Map of widget type to new position
     */
    @Transaction
    suspend fun reorderWidgets(userId: String, widgetTypePositions: Map<String, Int>) {
        widgetTypePositions.forEach { (widgetType, position) ->
            updateWidgetPosition(userId, widgetType, position)
        }
    }
    
    /**
     * Check if user has any widget preferences configured
     * @param userId User ID to check
     * @return True if user has widget preferences, false otherwise
     */
    @Query("SELECT COUNT(*) > 0 FROM widget_preferences WHERE user_id = :userId")
    suspend fun hasWidgetPreferences(userId: String): Boolean
    
    /**
     * Check if user has dashboard configuration
     * @param userId User ID to check
     * @return True if user has dashboard configuration, false otherwise
     */
    @Query("SELECT COUNT(*) > 0 FROM dashboard_configurations WHERE user_id = :userId")
    suspend fun hasDashboardConfiguration(userId: String): Boolean
}