package com.example.liftrix.service

import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.UIWidgetData
import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.common.LiftrixResult

/**
 * Service interface for analytics widget data management and preferences persistence.
 * 
 * This service provides a comprehensive abstraction layer for widget operations including:
 * - Widget data retrieval with async loading capabilities
 * - Widget preference management with atomic updates
 * - Widget visibility toggle with immediate feedback
 * - Batch operations for performance optimization
 * 
 * All operations return LiftrixResult for consistent error handling throughout the domain layer.
 * The service integrates with AnalyticsWidgetManager for widget configuration and 
 * WidgetPreferencesRepository for persistence operations.
 * 
 * Key Features:
 * - Async data loading with proper error handling
 * - Preference persistence with atomic updates
 * - Widget visibility management
 * - Performance-optimized batch operations
 * - Seamless integration with existing analytics infrastructure
 */
interface AnalyticsService {
    
    /**
     * Retrieves widget data for a specific user and widget type.
     * 
     * This method handles async data loading, caching, and error recovery.
     * Data is loaded from the analytics engine and formatted for UI display.
     * 
     * @param userId The unique identifier for the user
     * @param widget The specific widget type to retrieve data for
     * @return LiftrixResult containing UIWidgetData with formatted display values or error
     * 
     * Error Cases:
     * - ValidationError: Invalid userId or widget type
     * - NetworkError: Data source unavailable
     * - CalculationError: Widget data calculation failed
     * - NotFoundError: User or widget configuration not found
     * 
     * Example:
     * ```
     * val result = analyticsService.getWidgetData(userId, AnalyticsWidget.TotalVolume)
     * result.fold(
     *     onSuccess = { widgetData -> updateUI(widgetData) },
     *     onFailure = { error -> handleError(error) }
     * )
     * ```
     */
    suspend fun getWidgetData(userId: String, widget: AnalyticsWidget): LiftrixResult<UIWidgetData>
    
    /**
     * Retrieves widget preferences for a specific user.
     * 
     * Returns complete widget preferences including visibility settings, layout configuration,
     * and customization options. Creates default preferences if none exist.
     * 
     * @param userId The unique identifier for the user
     * @return LiftrixResult containing WidgetPreferences or error
     * 
     * Error Cases:
     * - ValidationError: Invalid userId
     * - DatabaseError: Preference retrieval failed
     * - UnknownError: Unexpected error during preference loading
     * 
     * Example:
     * ```
     * val result = analyticsService.getWidgetPreferences(userId)
     * result.fold(
     *     onSuccess = { preferences -> configureWidgets(preferences) },
     *     onFailure = { error -> useDefaultPreferences() }
     * )
     * ```
     */
    suspend fun getWidgetPreferences(userId: String): LiftrixResult<WidgetPreferences>
    
    /**
     * Updates widget preferences for a user with atomic operation.
     * 
     * Validates preferences before persistence and ensures atomic updates.
     * Preferences are persisted to local storage and synchronized with cloud storage.
     * 
     * @param preferences The updated widget preferences to save
     * @return LiftrixResult indicating success or failure
     * 
     * Error Cases:
     * - ValidationError: Invalid preferences data
     * - DatabaseError: Preference save operation failed
     * - UnknownError: Unexpected error during preference update
     * 
     * Example:
     * ```
     * val updatedPreferences = currentPreferences.updateLayout(DashboardLayoutMode.GRID)
     * val result = analyticsService.updateWidgetPreferences(updatedPreferences)
     * result.fold(
     *     onSuccess = { updateUIConfiguration() },
     *     onFailure = { error -> revertPreferences() }
     * )
     * ```
     */
    suspend fun updateWidgetPreferences(preferences: WidgetPreferences): LiftrixResult<Unit>
    
    /**
     * Toggles widget visibility for a specific user and widget.
     * 
     * Provides immediate feedback for widget visibility changes with atomic updates.
     * Ensures at least one widget remains visible to maintain dashboard functionality.
     * 
     * @param userId The unique identifier for the user
     * @param widgetId The widget identifier to toggle visibility for
     * @return LiftrixResult indicating success or failure
     * 
     * Error Cases:
     * - ValidationError: Invalid userId or widgetId
     * - BusinessRuleError: Cannot hide last visible widget
     * - DatabaseError: Visibility toggle operation failed
     * - NotFoundError: Widget or user preferences not found
     * 
     * Example:
     * ```
     * val result = analyticsService.toggleWidgetVisibility(userId, "TotalVolume")
     * result.fold(
     *     onSuccess = { refreshWidget() },
     *     onFailure = { error -> showError(error) }
     * )
     * ```
     */
    suspend fun toggleWidgetVisibility(userId: String, widgetId: String): LiftrixResult<Unit>
    
    /**
     * Resets widget preferences to default values for a user.
     * 
     * Provides a clean slate for widget configuration by resetting all preferences
     * to default values based on user experience level.
     * 
     * @param userId The unique identifier for the user
     * @return LiftrixResult indicating success or failure
     * 
     * Error Cases:
     * - ValidationError: Invalid userId
     * - DatabaseError: Preference reset operation failed
     * - UnknownError: Unexpected error during preference reset
     * 
     * Example:
     * ```
     * val result = analyticsService.resetPreferences(userId)
     * result.fold(
     *     onSuccess = { reloadDashboard() },
     *     onFailure = { error -> showResetError(error) }
     * )
     * ```
     */
    suspend fun resetPreferences(userId: String): LiftrixResult<Unit>
}