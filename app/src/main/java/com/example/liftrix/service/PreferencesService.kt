package com.example.liftrix.service

import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.analytics.UserLevel
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.analytics.WidgetLayoutMode

/**
 * Service interface for managing user preferences with widget layout and visibility management.
 * 
 * This service provides a high-level abstraction for user preference operations, handling
 * the coordination between widget preferences and user settings. It encapsulates business
 * logic for preference validation, default restoration, and atomic updates.
 * 
 * Key Features:
 * - Atomic preference updates with validation
 * - Widget layout mode management
 * - User level change propagation
 * - Thread-safe preference operations
 * - Default preference restoration
 * 
 * Error Handling:
 * All operations return LiftrixResult<T> for consistent error handling with proper
 * context information and recovery mechanisms.
 * 
 * Performance:
 * - Background processing for CPU-intensive operations
 * - Efficient preference synchronization
 * - Minimal database access patterns
 */
interface PreferencesService {
    
    /**
     * Retrieves user preferences for the specified user.
     * 
     * Returns the complete user preferences including widget visibility settings,
     * layout mode, user level, and customization options. If no preferences exist,
     * creates and returns default preferences based on user level.
     * 
     * @param userId The unique identifier for the user
     * @return LiftrixResult containing UserPreferences or error
     */
    suspend fun getUserPreferences(userId: String): LiftrixResult<WidgetPreferences>
    
    /**
     * Updates the widget layout mode for the specified user.
     * 
     * Changes the dashboard layout mode and applies appropriate widget configurations
     * for the new layout. This operation is atomic and ensures consistency across
     * all widget settings.
     * 
     * @param userId The unique identifier for the user
     * @param mode The new widget layout mode
     * @return LiftrixResult indicating success or failure
     */
    suspend fun updateLayoutMode(userId: String, mode: WidgetLayoutMode): LiftrixResult<Unit>
    
    /**
     * Updates the user experience level and recalculates widget configurations.
     * 
     * Changes the user's experience level and automatically updates widget visibility,
     * order, and configuration to match the new level's defaults. This ensures users
     * see appropriate analytics based on their fitness experience.
     * 
     * @param userId The unique identifier for the user
     * @param level The new user experience level
     * @return LiftrixResult indicating success or failure
     */
    suspend fun updateUserLevel(userId: String, level: UserLevel): LiftrixResult<Unit>
    
    /**
     * Resets all user preferences to default values.
     * 
     * Restores the user's preferences to default values based on their current
     * user level. This operation is useful for recovering from configuration
     * issues or providing a fresh start for users.
     * 
     * @param userId The unique identifier for the user
     * @return LiftrixResult indicating success or failure
     */
    suspend fun resetToDefaults(userId: String): LiftrixResult<Unit>
    
    /**
     * Updates widget visibility for a specific widget.
     * 
     * Toggles or sets the visibility of a specific widget while maintaining
     * consistency with widget order and layout constraints. Ensures at least
     * one widget remains visible.
     * 
     * @param userId The unique identifier for the user
     * @param widgetName The name of the widget to update
     * @param visible Whether the widget should be visible
     * @return LiftrixResult indicating success or failure
     */
    suspend fun updateWidgetVisibility(
        userId: String, 
        widgetName: String, 
        visible: Boolean
    ): LiftrixResult<Unit>
    
    /**
     * Updates the order of widgets in the dashboard.
     * 
     * Reorders widgets according to the provided list while maintaining
     * visibility constraints and ensuring all visible widgets are included.
     * 
     * @param userId The unique identifier for the user
     * @param widgetOrder List of widget names in the desired order
     * @return LiftrixResult indicating success or failure
     */
    suspend fun updateWidgetOrder(
        userId: String, 
        widgetOrder: List<String>
    ): LiftrixResult<Unit>
    
    /**
     * Updates auto-refresh settings for widgets.
     * 
     * Configures automatic refresh behavior for widgets including enable/disable
     * state and refresh interval. Validates interval within acceptable range.
     * 
     * @param userId The unique identifier for the user
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
     * Toggles the collapsed state of a dashboard section.
     * 
     * Manages section visibility in sectioned layout modes, allowing users
     * to collapse or expand sections for better organization.
     * 
     * @param userId The unique identifier for the user
     * @param sectionName The name of the section to toggle
     * @return LiftrixResult indicating success or failure
     */
    suspend fun toggleSection(userId: String, sectionName: String): LiftrixResult<Unit>
    
    /**
     * Saves complete widget preferences for a user.
     * 
     * @param preferences The complete widget preferences to save
     * @return LiftrixResult indicating success or failure
     */
    suspend fun saveWidgetPreferences(preferences: WidgetPreferences): LiftrixResult<Unit>
}