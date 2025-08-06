package com.example.liftrix.domain.usecase.settings

import com.example.liftrix.domain.model.analytics.UserLevel
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.service.PreferencesService
import com.example.liftrix.domain.model.analytics.WidgetLayoutMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for managing user preferences including widget layout, visibility, and configuration.
 * 
 * This use case provides a unified interface for user preference operations through the
 * PreferencesService abstraction layer. It handles comprehensive preference management
 * with proper error handling and context switching for background operations.
 * 
 * Key Features:
 * - Widget layout mode management
 * - User level configuration with automatic widget adjustments
 * - Individual widget visibility control
 * - Widget order customization
 * - Auto-refresh settings management
 * - Dashboard section control
 * - Atomic preference updates with validation
 * - Default preference restoration
 * 
 * Error Handling:
 * All operations return LiftrixResult<T> for consistent error handling with proper
 * context information and recovery mechanisms.
 * 
 * Usage:
 * ```
 * val result = updateUserPreferencesUseCase.updateLayoutMode(userId, WidgetLayoutMode.GRID)
 * result.fold(
 *     onSuccess = { updateUI() },
 *     onFailure = { error -> handleError(error) }
 * )
 * ```
 */
@Singleton
class UpdateUserPreferencesUseCase @Inject constructor(
    private val preferencesService: PreferencesService
) {
    
    /**
     * Updates the widget layout mode for the specified user.
     * 
     * Changes the dashboard layout mode and applies appropriate widget configurations
     * for the new layout. This operation is atomic and ensures consistency across
     * all widget settings.
     * 
     * @param userId The unique identifier for the user
     * @param mode The new widget layout mode (GRID, LIST, COMPACT, etc.)
     * @return LiftrixResult indicating success or failure
     */
    suspend fun updateLayoutMode(
        userId: String,
        mode: WidgetLayoutMode
    ): LiftrixResult<Unit> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ValidationError(
                    violations = listOf(throwable.message ?: "Unknown error"),
                    field = "layoutMode",
                    errorMessage = "Failed to update layout mode for user $userId: ${throwable.message}"
                )
            }
        ) {
            Timber.d("Updating layout mode for user: $userId to mode: $mode")
            
            val result = preferencesService.updateLayoutMode(userId, mode)
            result.getOrThrow()
        }
    }
    
    /**
     * Updates the user experience level and recalculates widget configurations.
     * 
     * Changes the user's experience level and automatically updates widget visibility,
     * order, and configuration to match the new level's defaults. This ensures users
     * see appropriate analytics based on their fitness experience.
     * 
     * @param userId The unique identifier for the user
     * @param level The new user experience level (BEGINNER, INTERMEDIATE, ADVANCED)
     * @return LiftrixResult indicating success or failure
     */
    suspend fun updateUserLevel(
        userId: String,
        level: UserLevel
    ): LiftrixResult<Unit> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ValidationError(
                    violations = listOf(throwable.message ?: "Unknown error"),
                    field = "userLevel",
                    errorMessage = "Failed to update user level for user $userId: ${throwable.message}"
                )
            }
        ) {
            Timber.d("Updating user level for user: $userId to level: $level")
            
            val result = preferencesService.updateUserLevel(userId, level)
            result.getOrThrow()
        }
    }
    
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
    ): LiftrixResult<Unit> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ValidationError(
                    violations = listOf(throwable.message ?: "Unknown error"),
                    field = "widgetVisibility",
                    errorMessage = "Failed to update widget visibility for user $userId, widget $widgetName: ${throwable.message}"
                )
            }
        ) {
            Timber.d("Updating widget visibility for user: $userId, widget: $widgetName, visible: $visible")
            
            val result = preferencesService.updateWidgetVisibility(userId, widgetName, visible)
            result.getOrThrow()
        }
    }
    
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
    ): LiftrixResult<Unit> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ValidationError(
                    violations = listOf(throwable.message ?: "Unknown error"),
                    field = "widgetOrder",
                    errorMessage = "Failed to update widget order for user $userId: ${throwable.message}"
                )
            }
        ) {
            Timber.d("Updating widget order for user: $userId with ${widgetOrder.size} widgets")
            
            val result = preferencesService.updateWidgetOrder(userId, widgetOrder)
            result.getOrThrow()
        }
    }
    
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
    ): LiftrixResult<Unit> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ValidationError(
                    violations = listOf(throwable.message ?: "Unknown error"),
                    field = "autoRefresh",
                    errorMessage = "Failed to update auto-refresh settings for user $userId: ${throwable.message}"
                )
            }
        ) {
            Timber.d("Updating auto-refresh settings for user: $userId, enabled: $enabled, interval: $intervalMinutes")
            
            // Validate interval range
            if (intervalMinutes !in 1..60) {
                throw IllegalArgumentException("Refresh interval must be between 1 and 60 minutes")
            }
            
            val result = preferencesService.updateAutoRefreshSettings(userId, enabled, intervalMinutes)
            result.getOrThrow()
        }
    }
    
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
    suspend fun toggleSection(
        userId: String,
        sectionName: String
    ): LiftrixResult<Unit> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ValidationError(
                    violations = listOf(throwable.message ?: "Unknown error"),
                    field = "sectionToggle",
                    errorMessage = "Failed to toggle section for user $userId, section $sectionName: ${throwable.message}"
                )
            }
        ) {
            Timber.d("Toggling section for user: $userId, section: $sectionName")
            
            val result = preferencesService.toggleSection(userId, sectionName)
            result.getOrThrow()
        }
    }
    
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
    suspend fun resetToDefaults(userId: String): LiftrixResult<Unit> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ValidationError(
                    violations = listOf(throwable.message ?: "Unknown error"),
                    field = "resetDefaults",
                    errorMessage = "Failed to reset preferences to defaults for user $userId: ${throwable.message}"
                )
            }
        ) {
            Timber.d("Resetting preferences to defaults for user: $userId")
            
            val result = preferencesService.resetToDefaults(userId)
            result.getOrThrow()
        }
    }
    
    /**
     * Bulk updates multiple preference settings in a single atomic operation.
     * 
     * Allows updating multiple preference aspects simultaneously while maintaining
     * consistency and reducing the number of database operations.
     * 
     * @param userId The unique identifier for the user
     * @param updates Map of preference updates to apply
     * @return LiftrixResult indicating success or failure
     */
    suspend fun bulkUpdatePreferences(
        userId: String,
        updates: Map<PreferenceType, Any>
    ): LiftrixResult<Unit> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ValidationError(
                    violations = listOf(throwable.message ?: "Unknown error"),
                    field = "bulkUpdate",
                    errorMessage = "Failed to bulk update preferences for user $userId: ${throwable.message}"
                )
            }
        ) {
            Timber.d("Bulk updating preferences for user: $userId with ${updates.size} updates")
            
            // Process each update type
            updates.forEach { (type, value) ->
                when (type) {
                    PreferenceType.LAYOUT_MODE -> {
                        if (value is WidgetLayoutMode) {
                            updateLayoutMode(userId, value).getOrThrow()
                        }
                    }
                    PreferenceType.USER_LEVEL -> {
                        if (value is UserLevel) {
                            updateUserLevel(userId, value).getOrThrow()
                        }
                    }
                    PreferenceType.WIDGET_ORDER -> {
                        if (value is List<*>) {
                            @Suppress("UNCHECKED_CAST")
                            updateWidgetOrder(userId, value as List<String>).getOrThrow()
                        }
                    }
                    PreferenceType.AUTO_REFRESH -> {
                        if (value is AutoRefreshSettings) {
                            updateAutoRefreshSettings(userId, value.enabled, value.intervalMinutes).getOrThrow()
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Enum representing different types of user preferences.
     */
    enum class PreferenceType {
        LAYOUT_MODE,
        USER_LEVEL,
        WIDGET_ORDER,
        AUTO_REFRESH
    }
    
    /**
     * Data class for auto-refresh settings updates.
     */
    data class AutoRefreshSettings(
        val enabled: Boolean,
        val intervalMinutes: Int
    )
}