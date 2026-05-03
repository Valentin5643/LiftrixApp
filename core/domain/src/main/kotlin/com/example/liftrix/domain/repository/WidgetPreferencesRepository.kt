package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing widget preferences and dashboard configuration.
 * 
 * This repository provides operations for persisting and retrieving user widget preferences
 * including visibility settings, layout configurations, and customization options.
 * All operations return LiftrixResult for consistent error handling across the domain layer.
 * 
 * The repository follows the offline-first pattern with local DataStore persistence
 * and optional cloud synchronization for cross-device preference sharing.
 */
interface WidgetPreferencesRepository {
    
    /**
     * Gets widget preferences for a specific user as a reactive Flow.
     * 
     * @param userId The unique identifier for the user
     * @return Flow emitting LiftrixResult with WidgetPreferences or error
     */
    fun getWidgetPreferences(userId: String): Flow<LiftrixResult<WidgetPreferences>>
    
    /**
     * Saves widget preferences for a user.
     * 
     * @param preferences The widget preferences to save
     * @return LiftrixResult indicating success or failure
     */
    suspend fun saveWidgetPreferences(preferences: WidgetPreferences): LiftrixResult<Unit>
    
    /**
     * Updates specific widget visibility setting.
     * 
     * @param userId The user identifier
     * @param widgetName Name of the widget
     * @param visible Whether the widget should be visible
     * @return LiftrixResult indicating success or failure
     */
    suspend fun updateWidgetVisibility(
        userId: String,
        widgetName: String,
        visible: Boolean
    ): LiftrixResult<Unit>
    
    /**
     * Updates widget order for dashboard layout.
     * 
     * @param userId The user identifier
     * @param widgetOrder List of widget names in preferred order
     * @return LiftrixResult indicating success or failure
     */
    suspend fun updateWidgetOrder(
        userId: String,
        widgetOrder: List<String>
    ): LiftrixResult<Unit>
    
    /**
     * Updates dashboard layout mode.
     * 
     * @param userId The user identifier
     * @param layoutMode The new dashboard layout mode
     * @return LiftrixResult indicating success or failure
     */
    suspend fun updateDashboardLayout(
        userId: String,
        layoutMode: com.example.liftrix.domain.model.analytics.DashboardLayoutMode
    ): LiftrixResult<Unit>
    
    /**
     * Updates user experience level and recalculates widget configurations.
     * 
     * @param userId The user identifier
     * @param userLevel The new user experience level
     * @return LiftrixResult indicating success or failure
     */
    suspend fun updateUserLevel(
        userId: String,
        userLevel: com.example.liftrix.domain.model.analytics.UserLevel
    ): LiftrixResult<Unit>
    
    /**
     * Toggles section collapsed state.
     * 
     * @param userId The user identifier
     * @param sectionName Name of the section to toggle
     * @return LiftrixResult indicating success or failure
     */
    suspend fun toggleSection(
        userId: String,
        sectionName: String
    ): LiftrixResult<Unit>
    
    /**
     * Updates widget display size.
     * 
     * @param userId The user identifier
     * @param widgetName Name of the widget
     * @param size New display size
     * @return LiftrixResult indicating success or failure
     */
    suspend fun updateWidgetSize(
        userId: String,
        widgetName: String,
        size: com.example.liftrix.domain.model.analytics.WidgetDisplaySize
    ): LiftrixResult<Unit>
    
    /**
     * Updates auto-refresh settings.
     * 
     * @param userId The user identifier
     * @param enabled Whether auto-refresh should be enabled
     * @param intervalMinutes Refresh interval in minutes (1-60)
     * @return LiftrixResult indicating success or failure
     */
    suspend fun updateAutoRefreshSettings(
        userId: String,
        enabled: Boolean,
        intervalMinutes: Int
    ): LiftrixResult<Unit>
    
    /**
     * Resets widget preferences to default values for a user.
     * 
     * @param userId The user identifier
     * @param userLevel Optional user level to determine defaults
     * @return LiftrixResult indicating success or failure
     */
    suspend fun resetToDefaults(
        userId: String,
        userLevel: com.example.liftrix.domain.model.analytics.UserLevel? = null
    ): LiftrixResult<Unit>
    
    /**
     * Migrates existing widget configurations for a user.
     * 
     * This method handles migration of legacy widget configurations to the new
     * preferences structure, ensuring backward compatibility for existing users.
     * 
     * @param userId The user identifier
     * @param legacyConfiguration Legacy configuration data to migrate
     * @return LiftrixResult indicating success or failure
     */
    suspend fun migrateUserPreferences(
        userId: String,
        legacyConfiguration: Map<String, Any>? = null
    ): LiftrixResult<Unit>
    
    /**
     * Fixes widget preference migration by converting old display names to proper enum names.
     * 
     * This method specifically addresses the issue where widget preferences contain
     * legacy display names like "Progress summary" instead of proper enum names like "ProgressChart".
     * It applies the migration mapping and saves the corrected preferences permanently.
     * 
     * @param userId The user identifier
     * @return LiftrixResult indicating success or failure
     */
    suspend fun fixWidgetPreferenceMigration(userId: String): LiftrixResult<Unit>
    
    /**
     * Exports widget preferences for backup or synchronization.
     * 
     * @param userId The user identifier
     * @return LiftrixResult with serialized preferences data or error
     */
    suspend fun exportPreferences(userId: String): LiftrixResult<String>
    
    /**
     * Imports widget preferences from backup or synchronization.
     * 
     * @param userId The user identifier
     * @param preferencesData Serialized preferences data to import
     * @return LiftrixResult indicating success or failure
     */
    suspend fun importPreferences(
        userId: String,
        preferencesData: String
    ): LiftrixResult<Unit>
    
    /**
     * Clears all widget preferences for a user.
     * 
     * This method is typically used during user logout or account deletion.
     * 
     * @param userId The user identifier
     * @return LiftrixResult indicating success or failure
     */
    suspend fun clearUserPreferences(userId: String): LiftrixResult<Unit>
}