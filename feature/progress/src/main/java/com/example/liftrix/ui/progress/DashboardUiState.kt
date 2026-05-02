package com.example.liftrix.ui.progress

import androidx.compose.runtime.Stable
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.progress.ProgressDashboardWidgetData

/**
 * UI state data class for the dashboard screen following MVI pattern.
 * 
 * This state represents the complete dashboard state including widget data,
 * configuration, loading states, and error information. It provides reactive
 * state management for the dashboard UI with comprehensive state handling.
 * 
 * Key Features:
 * - Widget data collection with individual loading/error states
 * - Dashboard configuration for layout and preferences
 * - Real-time update management with refresh states
 * - Drag-and-drop state for widget reordering
 * - Comprehensive error handling per widget and globally
 * - Performance optimizations with state diffing
 * 
 * State Management:
 * - Immutable state with copy operations for updates
 * - Individual widget state tracking for granular updates
 * - Loading state management for smooth UX
 * - Error state preservation with recovery options
 * 
 * Usage:
 * ```kotlin
 * @Composable
 * fun DashboardScreen(
 *     uiState: UiState<DashboardState>
 * ) {
 *     when (uiState) {
 *         is UiState.Loading -> LoadingIndicator()
 *         is UiState.Success -> DashboardContent(uiState.data)
 *         is UiState.Error -> ErrorDisplay(uiState.error)
 *     }
 * }
 * ```
 */
@Stable
data class DashboardUiState(
    /**
     * Current dashboard configuration including layout mode, widget order, and display preferences.
     * Null during initial loading phase.
     */
    val configuration: DashboardConfiguration? = null,
    
    /**
     * User widget preferences including visibility, positions, and customizations.
     * Null during initial loading phase.
     */
    val preferences: WidgetPreferences? = null,
    
    /**
     * Map of widget data indexed by widget ID.
     * Contains successfully loaded widget data ready for display.
     */
    val widgetData: Map<String, ProgressDashboardWidgetData> = emptyMap(),
    
    /**
     * Map of widget loading states indexed by widget ID.
     * True indicates widget is currently loading data.
     */
    val widgetLoadingStates: Map<String, Boolean> = emptyMap(),
    
    /**
     * Map of widget error states indexed by widget ID.
     * Contains error information for widgets that failed to load.
     */
    val widgetErrors: Map<String, LiftrixError> = emptyMap(),
    
    /**
     * Global loading state for dashboard initialization.
     * True during initial dashboard setup and configuration loading.
     */
    val isLoading: Boolean = true,
    
    /**
     * Global refresh state for coordinated refresh operations.
     * True during pull-to-refresh or background refresh operations.
     */
    val isRefreshing: Boolean = false,
    
    /**
     * Configuration update state for settings changes.
     * True during dashboard configuration updates or preference changes.
     */
    val isConfiguring: Boolean = false,
    
    /**
     * Drag-and-drop state for widget reordering operations.
     * Contains state information for drag-and-drop interactions.
     */
    val dragDropState: DragDropState = DragDropState(),
    
    /**
     * Global error state for dashboard-level errors.
     * Contains errors that affect the entire dashboard rather than individual widgets.
     */
    val globalError: LiftrixError? = null,
    
    /**
     * List of available widgets for the current user.
     * Filtered based on user permissions and experience level.
     */
    val availableWidgets: List<AnalyticsWidget> = emptyList(),
    
    /**
     * List of currently active/visible widgets in display order.
     * Derived from preferences and used for UI rendering.
     */
    val activeWidgets: List<String> = emptyList(),
    
    /**
     * Real-time update enablement state.
     * Controls whether the dashboard should listen for real-time data updates.
     */
    val realtimeUpdatesEnabled: Boolean = true,
    
    /**
     * Last successful data update timestamp.
     * Used for staleness detection and cache validation.
     */
    val lastUpdated: Long = 0L,
    
    /**
     * Network connectivity state affecting data operations.
     * Used to show offline indicators and disable network-dependent features.
     */
    val isOnline: Boolean = true
) {
    
    /**
     * Checks if the dashboard has valid configuration data.
     * 
     * @return true if configuration and preferences are loaded
     */
    fun hasValidConfiguration(): Boolean = configuration != null && preferences != null
    
    /**
     * Checks if any widgets are currently loading.
     * 
     * @return true if any widget is in loading state
     */
    fun hasLoadingWidgets(): Boolean = widgetLoadingStates.values.any { it }
    
    /**
     * Checks if any widgets have errors.
     * 
     * @return true if any widget has an error state
     */
    fun hasWidgetErrors(): Boolean = widgetErrors.isNotEmpty()
    
    /**
     * Gets the number of successfully loaded widgets.
     * 
     * @return count of widgets with loaded data
     */
    fun getLoadedWidgetCount(): Int = widgetData.size
    
    /**
     * Gets the total number of widgets that should be displayed.
     * 
     * @return count of active widgets
     */
    fun getTotalWidgetCount(): Int = activeWidgets.size
    
    /**
     * Checks if all active widgets have been loaded.
     * 
     * @return true if all active widgets have data or errors
     */
    fun allWidgetsProcessed(): Boolean {
        val processedWidgets = widgetData.keys + widgetErrors.keys
        return activeWidgets.all { it in processedWidgets }
    }
    
    /**
     * Checks if the dashboard is in a busy state.
     * 
     * @return true if loading, refreshing, or configuring
     */
    fun isBusy(): Boolean = isLoading || isRefreshing || isConfiguring
    
    /**
     * Gets widgets that need to be loaded or refreshed.
     * 
     * @return list of widget IDs that need data loading
     */
    fun getWidgetsNeedingData(): List<String> {
        return activeWidgets.filter { widgetId ->
            widgetId !in widgetData && widgetId !in widgetLoadingStates
        }
    }
    
    /**
     * Checks if data is stale and needs refresh.
     * 
     * @param staleThresholdMs staleness threshold in milliseconds
     * @return true if data is older than threshold
     */
    fun isDataStale(staleThresholdMs: Long = 5 * 60 * 1000): Boolean {
        if (lastUpdated == 0L) return true
        return System.currentTimeMillis() - lastUpdated > staleThresholdMs
    }
}

/**
 * State for drag-and-drop widget reordering operations.
 * 
 * Tracks the current state of drag-and-drop interactions including
 * dragged widget information, drop targets, and visual feedback states.
 */
@Stable
data class DragDropState(
    /**
     * Widget currently being dragged, null if no drag operation active.
     */
    val draggedWidget: String? = null,
    
    /**
     * Original position of the dragged widget for potential cancellation.
     */
    val originalPosition: Int = -1,
    
    /**
     * Current position during drag operation for real-time feedback.
     */
    val currentPosition: Int = -1,
    
    /**
     * Whether a drag operation is currently active.
     */
    val isDragging: Boolean = false,
    
    /**
     * Valid drop target positions for the current drag operation.
     */
    val validDropTargets: Set<Int> = emptySet(),
    
    /**
     * Whether the current drag position is valid for dropping.
     */
    val canDrop: Boolean = false
) {
    
    /**
     * Checks if drag-and-drop is active.
     * 
     * @return true if a widget is currently being dragged
     */
    fun isActive(): Boolean = isDragging && draggedWidget != null
    
    /**
     * Checks if the drag operation has moved from original position.
     * 
     * @return true if current position differs from original
     */
    fun hasMoved(): Boolean = originalPosition != currentPosition && currentPosition >= 0
}

/**
 * Extension functions for DashboardUiState transformations and updates.
 */

/**
 * Updates widget data for a specific widget.
 * 
 * @param widgetId the ID of the widget to update
 * @param data the new widget data
 * @return updated state with new widget data
 */
fun DashboardUiState.withWidgetData(
    widgetId: String, 
    data: ProgressDashboardWidgetData
): DashboardUiState = copy(
    widgetData = widgetData + (widgetId to data),
    widgetLoadingStates = widgetLoadingStates - widgetId,
    widgetErrors = widgetErrors - widgetId,
    lastUpdated = System.currentTimeMillis()
)

/**
 * Updates loading state for a specific widget.
 * 
 * @param widgetId the ID of the widget to update
 * @param isLoading the new loading state
 * @return updated state with new loading state
 */
fun DashboardUiState.withWidgetLoading(
    widgetId: String, 
    isLoading: Boolean
): DashboardUiState = copy(
    widgetLoadingStates = if (isLoading) {
        widgetLoadingStates + (widgetId to true)
    } else {
        widgetLoadingStates - widgetId
    }
)

/**
 * Updates error state for a specific widget.
 * 
 * @param widgetId the ID of the widget to update
 * @param error the error to set, null to clear error
 * @return updated state with new error state
 */
fun DashboardUiState.withWidgetError(
    widgetId: String, 
    error: LiftrixError?
): DashboardUiState = copy(
    widgetErrors = if (error != null) {
        widgetErrors + (widgetId to error)
    } else {
        widgetErrors - widgetId
    },
    widgetLoadingStates = widgetLoadingStates - widgetId
)

/**
 * Updates dashboard configuration.
 * 
 * @param configuration the new dashboard configuration
 * @return updated state with new configuration
 */
fun DashboardUiState.withConfiguration(
    configuration: DashboardConfiguration
): DashboardUiState = copy(configuration = configuration)

/**
 * Updates widget preferences.
 * 
 * @param preferences the new widget preferences
 * @return updated state with new preferences
 */
fun DashboardUiState.withPreferences(
    preferences: WidgetPreferences
): DashboardUiState = copy(
    preferences = preferences,
    activeWidgets = preferences.visibleWidgets.toList()
)

/**
 * Updates the refreshing state.
 * 
 * @param refreshing the new refreshing state
 * @return updated state with new refreshing state
 */
fun DashboardUiState.withRefreshing(refreshing: Boolean): DashboardUiState = 
    copy(isRefreshing = refreshing)

/**
 * Updates the loading state.
 * 
 * @param loading the new loading state
 * @return updated state with new loading state
 */
fun DashboardUiState.withLoading(loading: Boolean): DashboardUiState = 
    copy(isLoading = loading)

/**
 * Updates the configuring state.
 * 
 * @param configuring the new configuring state
 * @return updated state with new configuring state
 */
fun DashboardUiState.withConfiguring(configuring: Boolean): DashboardUiState = 
    copy(isConfiguring = configuring)

/**
 * Updates the drag-and-drop state.
 * 
 * @param dragDropState the new drag-and-drop state
 * @return updated state with new drag-and-drop state
 */
fun DashboardUiState.withDragDropState(dragDropState: DragDropState): DashboardUiState = 
    copy(dragDropState = dragDropState)

/**
 * Updates the global error state.
 * 
 * @param error the new global error, null to clear
 * @return updated state with new global error
 */
fun DashboardUiState.withGlobalError(error: LiftrixError?): DashboardUiState = 
    copy(globalError = error)

/**
 * Updates the available widgets list.
 * 
 * @param widgets the new list of available widgets
 * @return updated state with new available widgets
 */
fun DashboardUiState.withAvailableWidgets(widgets: List<AnalyticsWidget>): DashboardUiState = 
    copy(availableWidgets = widgets)

/**
 * Updates the active widgets from preferences.
 * 
 * @param preferences the widget preferences containing visible widgets
 * @return updated state with new active widgets
 */
fun DashboardUiState.withActiveWidgets(preferences: WidgetPreferences): DashboardUiState = 
    copy(activeWidgets = preferences.visibleWidgets.toList())

/**
 * Updates the active widgets from resolved AnalyticsWidget objects.
 * 
 * This method sets the active widgets using properly resolved AnalyticsWidget objects
 * from ProgressWidgetResolverPort, ensuring correct widget counts for all user levels.
 * 
 * @param widgets the resolved AnalyticsWidget objects
 * @return updated state with new active widgets from resolved widgets
 */
fun DashboardUiState.withResolvedWidgets(widgets: List<com.example.liftrix.domain.model.analytics.AnalyticsWidget>): DashboardUiState = 
    copy(activeWidgets = widgets.map { it.id })

/**
 * Clears errors for specific widget or all widgets.
 * 
 * @param widgetId the ID of the widget to clear errors for, null for all
 * @return updated state with cleared errors
 */
fun DashboardUiState.withClearedErrors(widgetId: String? = null): DashboardUiState = copy(
    widgetErrors = if (widgetId != null) {
        widgetErrors - widgetId
    } else {
        emptyMap()
    },
    globalError = if (widgetId == null) null else globalError
)

/**
 * Updates the online/offline state.
 * 
 * @param online the new online state
 * @return updated state with new online state
 */
fun DashboardUiState.withOnlineState(online: Boolean): DashboardUiState = 
    copy(isOnline = online)