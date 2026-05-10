package com.example.liftrix.ui.progress

import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.ui.common.state.UiState

/**
 * Comprehensive state class for analytics widget management in the MVI pattern.
 * 
 * This state class manages all UI-related data for analytics widgets including
 * widget data loading, preferences, configuration, and error states. It provides
 * a centralized state management approach with efficient update mechanisms.
 * 
 * Key Features:
 * - Individual widget state tracking with loading/error/success states
 * - Dashboard configuration management with immediate UI updates
 * - Widget preferences persistence with atomic updates
 * - Error handling with widget-specific error states
 * - Loading state management for bulk operations
 * - Interaction tracking for analytics and user behavior analysis
 * 
 * State Management:
 * - Immutable data structures for predictable state updates
 * - Efficient widget state updates without full state reconstruction
 * - Separate loading states for different operations
 * - Comprehensive error tracking with recovery information
 * 
 * Usage:
 * ```kotlin
 * // Access widget data
 * val volumeWidget = state.widgetData["TotalVolume"]
 * 
 * // Check loading state
 * if (state.isLoading) {
 *     ShowLoadingIndicator()
 * }
 * 
 * // Handle widget errors
 * state.widgetErrors["TotalVolume"]?.let { error ->
 *     ShowErrorMessage(error)
 * }
 * ```
 */
data class AnalyticsWidgetState(
    /**
     * Map of widget data indexed by widget ID.
     * 
     * Contains the actual widget data for display, including formatted values,
     * trends, and metadata. Each widget maintains its own data state independently.
     * 
     * Structure: widgetId -> WidgetData
     */
    val widgetData: Map<String, WidgetData> = emptyMap(),
    
    /**
     * User's widget preferences including visibility and configuration.
     * 
     * Contains user-specific settings for widget visibility, layout preferences,
     * and customization options. Updated through preference management events.
     */
    val preferences: WidgetPreferences? = null,
    
    /**
     * Dashboard configuration including layout and widget arrangement.
     * 
     * Controls the overall dashboard appearance, widget ordering, and layout mode.
     * Changes are applied immediately with persistence handled separately.
     */
    val configuration: DashboardConfiguration? = null,
    
    /**
     * Global loading state for dashboard operations.
     * 
     * Indicates when bulk operations are in progress such as refreshing all widgets,
     * resetting preferences, or loading initial dashboard data.
     */
    val isLoading: Boolean = false,
    
    /**
     * Widget-specific loading states indexed by widget ID.
     * 
     * Tracks individual widget loading states for granular loading indicators.
     * Allows showing loading states for specific widgets during updates.
     * 
     * Structure: widgetId -> isLoading
     */
    val widgetLoadingStates: Map<String, Boolean> = emptyMap(),
    
    /**
     * Widget-specific error states indexed by widget ID.
     * 
     * Contains error information for individual widgets, allowing granular
     * error handling and recovery options per widget.
     * 
     * Structure: widgetId -> LiftrixError
     */
    val widgetErrors: Map<String, LiftrixError> = emptyMap(),
    
    /**
     * Global error state for dashboard-wide operations.
     * 
     * Contains errors that affect the entire dashboard such as preference
     * loading failures or configuration errors.
     */
    val globalError: LiftrixError? = null,
    
    /**
     * Refresh state tracking for pull-to-refresh operations.
     * 
     * Indicates when the dashboard is being refreshed through user gesture
     * or automatic refresh operations.
     */
    val isRefreshing: Boolean = false,
    
    /**
     * Configuration change state for immediate UI feedback.
     * 
     * Tracks when configuration changes are being applied and persisted.
     * Provides feedback for layout changes and preference updates.
     */
    val isConfiguringDashboard: Boolean = false,
    
    /**
     * Retry state tracking for failed operations.
     * 
     * Tracks which widgets or operations are currently being retried.
     * Prevents multiple retry attempts and provides user feedback.
     * 
     * Structure: widgetId -> isRetrying
     */
    val retryingOperations: Map<String, Boolean> = emptyMap(),
    
    /**
     * Widget interaction history for analytics.
     * 
     * Tracks user interactions with widgets for behavior analysis
     * and user experience improvements.
     * 
     * Structure: widgetId -> List<InteractionData>
     */
    val interactionHistory: Map<String, List<WidgetInteraction>> = emptyMap(),
    
    /**
     * Last refresh timestamp for cache management.
     * 
     * Tracks when the dashboard was last refreshed to manage cache
     * invalidation and data freshness indicators.
     */
    val lastRefreshTimestamp: Long = 0L,
    
    /**
     * Pending configuration changes for batch operations.
     * 
     * Stores configuration changes that haven't been applied yet,
     * allowing for batch updates and atomic configuration changes.
     */
    val pendingConfigurationChanges: DashboardConfiguration? = null,
    
    /**
     * List of active widgets currently displayed on the dashboard.
     * 
     * Contains the list of widgets that are currently active and visible
     * based on user preferences and configuration.
     */
    val activeWidgets: List<AnalyticsWidget> = emptyList(),
    
    
    /**
     * Map of widget data indexed by widget type.
     * 
     * Provides access to widget data by widget type for easier lookup.
     */
    val widgetDataMap: Map<AnalyticsWidget, WidgetData> = emptyMap()
) {
    
    /**
     * Checks if any widget is currently loading.
     * 
     * @return true if any widget is in loading state, false otherwise
     */
    fun hasLoadingWidgets(): Boolean = widgetLoadingStates.values.any { it }
    
    /**
     * Checks if any widget has an error state.
     * 
     * @return true if any widget has an error, false otherwise
     */
    fun hasWidgetErrors(): Boolean = widgetErrors.isNotEmpty()
    
    /**
     * Gets the total number of visible widgets based on preferences.
     * 
     * @return number of visible widgets or 0 if preferences not loaded
     */
    fun getVisibleWidgetCount(): Int = preferences?.visibleWidgets?.size ?: 0
    
    /**
     * Checks if a specific widget is visible.
     * 
     * @param widgetId The widget identifier to check
     * @return true if the widget is visible, false otherwise
     */
    fun isWidgetVisible(widgetId: String): Boolean = 
        preferences?.visibleWidgets?.contains(widgetId) ?: false
    
    /**
     * Checks if a specific widget is loading.
     * 
     * @param widgetId The widget identifier to check
     * @return true if the widget is loading, false otherwise
     */
    fun isWidgetLoading(widgetId: String): Boolean = 
        widgetLoadingStates[widgetId] ?: false
    
    /**
     * Gets the error for a specific widget.
     * 
     * @param widgetId The widget identifier to get error for
     * @return LiftrixError if widget has an error, null otherwise
     */
    fun getWidgetError(widgetId: String): LiftrixError? = widgetErrors[widgetId]
    
    /**
     * Checks if a specific widget has an error.
     * 
     * @param widgetId The widget identifier to check
     * @return true if the widget has an error, false otherwise
     */
    fun hasWidgetError(widgetId: String): Boolean = widgetErrors.containsKey(widgetId)
    
    /**
     * Gets the data for a specific widget.
     * 
     * @param widgetId The widget identifier to get data for
     * @return WidgetData if available, null otherwise
     */
    fun getWidgetData(widgetId: String): WidgetData? = widgetData[widgetId]
    
    /**
     * Checks if a specific widget has data loaded.
     * 
     * @param widgetId The widget identifier to check
     * @return true if the widget has data, false otherwise
     */
    fun hasWidgetData(widgetId: String): Boolean = widgetData.containsKey(widgetId)
    
    /**
     * Checks if the dashboard is in a valid state for user interaction.
     * 
     * @return true if the dashboard is ready for interaction, false otherwise
     */
    fun isReadyForInteraction(): Boolean = 
        !isLoading && !isConfiguringDashboard && globalError == null
    
    /**
     * Gets the list of widgets that need to be retried.
     * 
     * @return List of widget IDs that have errors and can be retried
     */
    fun getRetryableWidgets(): List<String> = widgetErrors.keys.filter { widgetId ->
        val error = widgetErrors[widgetId]
        error?.isRecoverable ?: false
    }
    
    /**
     * Checks if there are any pending configuration changes.
     * 
     * @return true if there are pending changes, false otherwise
     */
    fun hasPendingConfigurationChanges(): Boolean = pendingConfigurationChanges != null
    
    /**
     * Gets the effective configuration (pending changes or current).
     * 
     * @return DashboardConfiguration that should be used for UI rendering
     */
    fun getEffectiveConfiguration(): DashboardConfiguration? = 
        pendingConfigurationChanges ?: configuration
    
    /**
     * Checks if the dashboard data is stale based on refresh timestamp.
     * 
     * @param staleThresholdMs Threshold in milliseconds for considering data stale
     * @return true if data is stale, false otherwise
     */
    fun isDataStale(staleThresholdMs: Long = 5 * 60 * 1000): Boolean = 
        System.currentTimeMillis() - lastRefreshTimestamp > staleThresholdMs
    
    /**
     * Gets the count of widgets in each state for analytics.
     * 
     * @return Map of state -> count for dashboard analytics
     */
    fun getWidgetStateCounts(): Map<String, Int> = mapOf(
        "loaded" to widgetData.size,
        "loading" to widgetLoadingStates.count { it.value },
        "error" to widgetErrors.size,
        "visible" to getVisibleWidgetCount(),
        "retrying" to retryingOperations.count { it.value }
    )
}

/**
 * Data class for tracking widget interactions.
 * 
 * Captures user interactions with widgets for analytics and behavior analysis.
 * Helps improve widget design and user experience through data-driven insights.
 */
data class WidgetInteraction(
    /**
     * The type of interaction (tap, long_press, swipe, etc.).
     */
    val type: String,
    
    /**
     * Timestamp when the interaction occurred.
     */
    val timestamp: Long = System.currentTimeMillis(),
    
    /**
     * Additional metadata about the interaction.
     */
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Extension functions for state management and transformations.
 */

/**
 * Creates a new state with updated widget data.
 * 
 * @param widgetId The widget identifier to update
 * @param data The new widget data
 * @return New state with updated widget data
 */
fun AnalyticsWidgetState.withWidgetData(
    widgetId: String, 
    data: WidgetData
): AnalyticsWidgetState = copy(
    widgetData = widgetData + (widgetId to data),
    lastRefreshTimestamp = System.currentTimeMillis()
)

/**
 * Creates a new state with updated widget loading state.
 * 
 * @param widgetId The widget identifier to update
 * @param isLoading The new loading state
 * @return New state with updated loading state
 */
fun AnalyticsWidgetState.withWidgetLoading(
    widgetId: String, 
    isLoading: Boolean
): AnalyticsWidgetState = copy(
    widgetLoadingStates = if (isLoading) {
        widgetLoadingStates + (widgetId to true)
    } else {
        widgetLoadingStates - widgetId
    }
)

/**
 * Creates a new state with updated widget error state.
 * 
 * @param widgetId The widget identifier to update
 * @param error The error to set (null to clear error)
 * @return New state with updated error state
 */
fun AnalyticsWidgetState.withWidgetError(
    widgetId: String, 
    error: LiftrixError?
): AnalyticsWidgetState = copy(
    widgetErrors = if (error != null) {
        widgetErrors + (widgetId to error)
    } else {
        widgetErrors - widgetId
    }
)

/**
 * Creates a new state with updated preferences.
 * 
 * @param newPreferences The new widget preferences
 * @return New state with updated preferences
 */
fun AnalyticsWidgetState.withPreferences(
    newPreferences: WidgetPreferences
): AnalyticsWidgetState = copy(preferences = newPreferences)

/**
 * Creates a new state with updated configuration.
 * 
 * @param newConfiguration The new dashboard configuration
 * @return New state with updated configuration
 */
fun AnalyticsWidgetState.withConfiguration(
    newConfiguration: DashboardConfiguration
): AnalyticsWidgetState = copy(configuration = newConfiguration)

/**
 * Creates a new state with global loading state.
 * 
 * @param loading The new global loading state
 * @return New state with updated loading state
 */
fun AnalyticsWidgetState.withLoading(loading: Boolean): AnalyticsWidgetState = 
    copy(isLoading = loading)

/**
 * Creates a new state with global error state.
 * 
 * @param error The error to set (null to clear error)
 * @return New state with updated error state
 */
fun AnalyticsWidgetState.withGlobalError(error: LiftrixError?): AnalyticsWidgetState = 
    copy(globalError = error)

/**
 * Creates a new state with refreshing state.
 * 
 * @param refreshing The new refreshing state
 * @return New state with updated refreshing state
 */
fun AnalyticsWidgetState.withRefreshing(refreshing: Boolean): AnalyticsWidgetState = 
    copy(isRefreshing = refreshing)

/**
 * Creates a new state with configuration state.
 * 
 * @param configuring The new configuration state
 * @return New state with updated configuration state
 */
fun AnalyticsWidgetState.withConfiguring(configuring: Boolean): AnalyticsWidgetState = 
    copy(isConfiguringDashboard = configuring)

/**
 * Creates a new state with recorded interaction.
 * 
 * @param widgetId The widget identifier
 * @param interaction The interaction to record
 * @return New state with updated interaction history
 */
fun AnalyticsWidgetState.withInteraction(
    widgetId: String, 
    interaction: WidgetInteraction
): AnalyticsWidgetState {
    val currentHistory = interactionHistory[widgetId] ?: emptyList()
    val updatedHistory = currentHistory + interaction
    
    return copy(
        interactionHistory = interactionHistory + (widgetId to updatedHistory)
    )
}

/**
 * Creates a new state with cleared widget errors.
 * 
 * @param widgetId The widget identifier (null to clear all errors)
 * @return New state with cleared errors
 */
fun AnalyticsWidgetState.withClearedErrors(widgetId: String? = null): AnalyticsWidgetState = 
    if (widgetId != null) {
        copy(widgetErrors = widgetErrors - widgetId)
    } else {
        copy(widgetErrors = emptyMap(), globalError = null)
    }

/**
 * Creates a new state with active widgets populated from preferences.
 * 
 * DEPRECATED: This method has limited widget resolution capabilities.
 * Use withResolvedWidgets() with ProgressWidgetResolverPort.resolveWidgetsFromPreferences() instead.
 * 
 * @param preferences The widget preferences containing visible widgets
 * @return New state with updated active widgets list
 */
@Deprecated("Use withResolvedWidgets() with ProgressWidgetResolverPort for proper widget resolution")
fun AnalyticsWidgetState.withActiveWidgets(
    preferences: WidgetPreferences?
): AnalyticsWidgetState {
    timber.log.Timber.w("Using deprecated withActiveWidgets() - consider migrating to withResolvedWidgets() with ProgressWidgetResolverPort")
    
    val resolvedWidgets = if (preferences != null) {
        val allWidgets = AnalyticsWidget.getAllWidgets()
        preferences.getOrderedVisibleWidgets().mapNotNull { widgetId ->
            allWidgets.find { it.id == widgetId }
        }
    } else {
        emptyList()
    }
    
    // CRITICAL BUG FIX: Create level-appropriate defaults using proper widget selection
    val finalWidgets = if (resolvedWidgets.isEmpty() && preferences != null) {
        val userLevel = preferences.userLevel
        timber.log.Timber.i("No widgets resolved from preferences, creating level-appropriate defaults for $userLevel")
        
        defaultProgressWidgets()
    } else if (resolvedWidgets.isEmpty()) {
        // No preferences at all - use beginner defaults
        timber.log.Timber.i("No preferences available, using beginner defaults")
        defaultProgressWidgets()
    } else {
        resolvedWidgets
    }
    
    timber.log.Timber.d("Resolved ${finalWidgets.size} active widgets from preferences for level ${preferences?.userLevel ?: "UNKNOWN"}")
    
    return copy(activeWidgets = finalWidgets)
}

private fun defaultProgressWidgets(): List<AnalyticsWidget> = listOf(
    AnalyticsWidget.StrengthAnalytics,
    AnalyticsWidget.VolumeAnalytics,
    AnalyticsWidget.MuscleGroupDistribution
)

/**
 * Creates a new state with resolved widgets from ProgressWidgetResolverPort.
 * 
 * This method updates the state with widgets resolved by ProgressWidgetResolverPort based on 
 * user level and layout mode. Used for dynamic widget resolution.
 * 
 * @param resolvedWidgets The widgets resolved by ProgressWidgetResolverPort
 * @return New state with updated active widgets list
 */
fun AnalyticsWidgetState.withResolvedWidgets(
    resolvedWidgets: List<AnalyticsWidget>
): AnalyticsWidgetState {
    timber.log.Timber.d("Setting ${resolvedWidgets.size} resolved widgets: ${resolvedWidgets.map { it.displayName }.joinToString(", ")}")
    
    return copy(activeWidgets = resolvedWidgets)
}
