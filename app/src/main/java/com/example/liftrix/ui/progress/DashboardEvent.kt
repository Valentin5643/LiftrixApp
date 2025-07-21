package com.example.liftrix.ui.progress

import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.ui.common.event.ViewModelEvent

/**
 * Sealed class hierarchy for dashboard events in the MVI pattern.
 * 
 * This event hierarchy handles all user interactions and system events related to
 * the dashboard screen, including widget management, configuration updates, and
 * real-time data synchronization. Events are processed by DashboardViewModel to
 * update the UI state reactively.
 * 
 * Key Features:
 * - Dashboard initialization and data loading
 * - Widget data refresh and management operations
 * - Drag-and-drop widget reordering
 * - Configuration updates with immediate feedback
 * - Real-time update coordination
 * - Error recovery and retry mechanisms
 * - Navigation and export operations
 * 
 * Event Processing:
 * All events are processed asynchronously with proper error handling and state updates.
 * Events maintain consistency and provide immediate user feedback where appropriate.
 * 
 * Usage:
 * ```kotlin
 * // Load dashboard data
 * viewModel.handleEvent(DashboardEvent.LoadDashboard)
 * 
 * // Refresh specific widget
 * viewModel.handleEvent(DashboardEvent.RefreshWidget("TotalVolume"))
 * 
 * // Reorder widgets
 * viewModel.handleEvent(DashboardEvent.ReorderWidgets(fromIndex = 0, toIndex = 2))
 * 
 * // Update configuration
 * viewModel.handleEvent(DashboardEvent.UpdateConfiguration(newConfig))
 * ```
 */
sealed class DashboardEvent : ViewModelEvent {
    
    /**
     * Event for loading initial dashboard data.
     * 
     * Triggers comprehensive dashboard initialization including configuration loading,
     * preferences retrieval, and initial widget data population. Handles both fresh
     * loads and restoration from saved state.
     * 
     * @property forceRefresh Whether to bypass cache and force fresh data loading
     * @property showLoading Whether to show loading indicators during initialization
     * 
     * Example:
     * ```kotlin
     * // Standard dashboard load
     * DashboardEvent.LoadDashboard
     * 
     * // Force refresh all data
     * DashboardEvent.LoadDashboard(forceRefresh = true)
     * ```
     */
    data class LoadDashboard(
        val forceRefresh: Boolean = false,
        val showLoading: Boolean = true
    ) : DashboardEvent()
    
    /**
     * Event for refreshing all dashboard data.
     * 
     * Triggers coordinated refresh of all visible widgets with loading state management.
     * Optimizes network requests and handles partial failures gracefully.
     * 
     * @property includeConfiguration Whether to also refresh configuration data
     * @property showRefreshIndicator Whether to show refresh indicators
     * 
     * Example:
     * ```kotlin
     * // Standard refresh
     * DashboardEvent.RefreshDashboard
     * 
     * // Silent refresh without indicators
     * DashboardEvent.RefreshDashboard(showRefreshIndicator = false)
     * ```
     */
    data class RefreshDashboard(
        val includeConfiguration: Boolean = false,
        val showRefreshIndicator: Boolean = true
    ) : DashboardEvent()
    
    /**
     * Event for refreshing data for a specific widget.
     * 
     * Triggers individual widget data refresh with targeted loading state management.
     * Provides granular control over widget updates without affecting other widgets.
     * 
     * @property widgetId The unique identifier of the widget to refresh
     * @property forceRefresh Whether to bypass cache for this widget
     * 
     * Example:
     * ```kotlin
     * // Refresh specific widget
     * DashboardEvent.RefreshWidget("TotalVolume")
     * 
     * // Force refresh from remote
     * DashboardEvent.RefreshWidget("TotalVolume", forceRefresh = true)
     * ```
     */
    data class RefreshWidget(
        val widgetId: String,
        val forceRefresh: Boolean = false
    ) : DashboardEvent()
    
    /**
     * Event for widget reordering via drag-and-drop.
     * 
     * Handles widget position changes with immediate UI updates and preference persistence.
     * Validates reorder operations and updates dashboard configuration accordingly.
     * 
     * @property fromIndex The original position index of the widget
     * @property toIndex The new position index for the widget
     * @property shouldPersist Whether to persist the reordering changes
     * 
     * Example:
     * ```kotlin
     * // Reorder widget from position 0 to position 2
     * DashboardEvent.ReorderWidgets(fromIndex = 0, toIndex = 2)
     * 
     * // Temporary reorder without persistence
     * DashboardEvent.ReorderWidgets(fromIndex = 0, toIndex = 2, shouldPersist = false)
     * ```
     */
    data class ReorderWidgets(
        val fromIndex: Int,
        val toIndex: Int,
        val shouldPersist: Boolean = true
    ) : DashboardEvent()
    
    /**
     * Event for updating dashboard configuration.
     * 
     * Applies configuration changes with atomic updates and immediate UI reflection.
     * Handles layout mode changes, theme updates, and widget arrangement modifications.
     * 
     * @property configuration The new dashboard configuration to apply
     * @property shouldPersist Whether to persist the configuration changes
     * @property showFeedback Whether to show visual feedback for the update
     * 
     * Example:
     * ```kotlin
     * val newConfig = DashboardConfiguration(
     *     layoutMode = WidgetLayoutMode.GRID,
     *     columnsPortrait = 2,
     *     columnsLandscape = 3
     * )
     * DashboardEvent.UpdateConfiguration(newConfig)
     * ```
     */
    data class UpdateConfiguration(
        val configuration: DashboardConfiguration,
        val shouldPersist: Boolean = true,
        val showFeedback: Boolean = true
    ) : DashboardEvent()
    
    /**
     * Event for toggling widget visibility.
     * 
     * Provides immediate UI feedback for widget visibility changes with persistence.
     * Validates business rules (e.g., minimum visible widgets) before applying changes.
     * 
     * @property widgetId The unique identifier of the widget to toggle
     * @property visible Optional explicit visibility state (null for toggle)
     * @property shouldPersist Whether to persist the visibility change
     * 
     * Example:
     * ```kotlin
     * // Toggle widget visibility
     * DashboardEvent.ToggleWidgetVisibility("WorkoutFrequency")
     * 
     * // Set explicit visibility
     * DashboardEvent.ToggleWidgetVisibility("WorkoutFrequency", visible = true)
     * ```
     */
    data class ToggleWidgetVisibility(
        val widgetId: String,
        val visible: Boolean? = null,
        val shouldPersist: Boolean = true
    ) : DashboardEvent()
    
    /**
     * Event for starting drag operation.
     * 
     * Initiates drag-and-drop state management with visual feedback preparation.
     * Sets up drag state for smooth reordering interactions.
     * 
     * @property widgetId The unique identifier of the widget being dragged
     * @property initialPosition The initial position of the widget
     * 
     * Example:
     * ```kotlin
     * // Start dragging widget at position 1
     * DashboardEvent.StartDrag("TotalVolume", initialPosition = 1)
     * ```
     */
    data class StartDrag(
        val widgetId: String,
        val initialPosition: Int
    ) : DashboardEvent()
    
    /**
     * Event for updating drag position during drag operation.
     * 
     * Provides real-time position updates during drag-and-drop interactions.
     * Updates visual feedback and validates drop positions.
     * 
     * @property currentPosition The current position during drag
     * @property canDrop Whether the current position is valid for dropping
     * 
     * Example:
     * ```kotlin
     * // Update drag to position 3
     * DashboardEvent.UpdateDragPosition(currentPosition = 3, canDrop = true)
     * ```
     */
    data class UpdateDragPosition(
        val currentPosition: Int,
        val canDrop: Boolean = true
    ) : DashboardEvent()
    
    /**
     * Event for ending drag operation.
     * 
     * Completes drag-and-drop interaction with final position confirmation.
     * Applies reordering changes and cleans up drag state.
     * 
     * @property finalPosition The final position where the widget was dropped
     * @property shouldApply Whether to apply the reordering (false for cancellation)
     * 
     * Example:
     * ```kotlin
     * // Complete drag to position 2
     * DashboardEvent.EndDrag(finalPosition = 2)
     * 
     * // Cancel drag operation
     * DashboardEvent.EndDrag(finalPosition = -1, shouldApply = false)
     * ```
     */
    data class EndDrag(
        val finalPosition: Int,
        val shouldApply: Boolean = true
    ) : DashboardEvent()
    
    /**
     * Event for retrying failed operations.
     * 
     * Handles retry logic for failed widget data loading or configuration updates.
     * Implements exponential backoff and maximum retry limits for resilience.
     * 
     * @property widgetId The identifier of the widget to retry (null for all failed)
     * @property operation The specific operation to retry (null for auto-detect)
     * 
     * Example:
     * ```kotlin
     * // Retry specific widget
     * DashboardEvent.RetryOperation("TotalVolume")
     * 
     * // Retry all failed operations
     * DashboardEvent.RetryOperation()
     * ```
     */
    data class RetryOperation(
        val widgetId: String? = null,
        val operation: String? = null
    ) : DashboardEvent()
    
    /**
     * Event for clearing error states.
     * 
     * Clears error states and returns widgets to normal operation mode.
     * Provides user control over error message visibility and recovery.
     * 
     * @property widgetId The identifier of the widget to clear errors for (null for all)
     * @property clearGlobalError Whether to also clear global error state
     * 
     * Example:
     * ```kotlin
     * // Clear specific widget error
     * DashboardEvent.ClearError("TotalVolume")
     * 
     * // Clear all errors
     * DashboardEvent.ClearError()
     * ```
     */
    data class ClearError(
        val widgetId: String? = null,
        val clearGlobalError: Boolean = false
    ) : DashboardEvent()
    
    /**
     * Event for enabling/disabling real-time updates.
     * 
     * Controls real-time data synchronization for the dashboard.
     * Manages network listeners and background refresh operations.
     * 
     * @property enabled Whether real-time updates should be enabled
     * @property applyToAllWidgets Whether to apply to all widgets or just new ones
     * 
     * Example:
     * ```kotlin
     * // Enable real-time updates
     * DashboardEvent.ToggleRealtimeUpdates(enabled = true)
     * 
     * // Disable for all widgets
     * DashboardEvent.ToggleRealtimeUpdates(enabled = false, applyToAllWidgets = true)
     * ```
     */
    data class ToggleRealtimeUpdates(
        val enabled: Boolean,
        val applyToAllWidgets: Boolean = false
    ) : DashboardEvent()
    
    /**
     * Event for navigating to dashboard customization screen.
     * 
     * Triggers navigation to the dashboard customization interface where users
     * can configure widget visibility, layout modes, and advanced preferences.
     * 
     * @property category Optional category to focus on in customization screen
     * 
     * Example:
     * ```kotlin
     * // Open general customization
     * DashboardEvent.NavigateToCustomization
     * 
     * // Open with focus on specific category
     * DashboardEvent.NavigateToCustomization(category = "CHARTS")
     * ```
     */
    data class NavigateToCustomization(
        val category: String? = null
    ) : DashboardEvent()
    
    /**
     * Event for exporting dashboard data.
     * 
     * Initiates export process for dashboard analytics and widget data.
     * Supports multiple export formats and data scoping options.
     * 
     * @property format The export format (PDF, CSV, JSON)
     * @property includeCharts Whether to include chart visualizations
     * @property dateRange Optional date range for data filtering
     * 
     * Example:
     * ```kotlin
     * // Export as PDF with charts
     * DashboardEvent.ExportData(format = "PDF", includeCharts = true)
     * 
     * // Export CSV for specific date range
     * DashboardEvent.ExportData(
     *     format = "CSV", 
     *     dateRange = TimeRange.lastMonth()
     * )
     * ```
     */
    data class ExportData(
        val format: String,
        val includeCharts: Boolean = true,
        val dateRange: TimeRange? = null
    ) : DashboardEvent()
    
    /**
     * Event for exporting raw data.
     * 
     * Initiates export process for raw data without processing or aggregation.
     * Provides access to underlying data for external analysis tools.
     */
    object ExportRawData : DashboardEvent()
    
    /**
     * Event for canceling export operations.
     * 
     * Cancels ongoing export processes and cleans up temporary resources.
     * Provides user control over long-running export operations.
     */
    object CancelExport : DashboardEvent()
    
    /**
     * Event for handling widget click interactions.
     * 
     * Processes widget click events for navigation or detail view actions.
     * Tracks user interactions for analytics and behavior analysis.
     * 
     * @property widget The analytics widget that was clicked
     * @property action The action to perform (view_details, expand, navigate)
     * 
     * Example:
     * ```kotlin
     * // Handle widget click for details
     * DashboardEvent.WidgetClicked(widget, action = "view_details")
     * ```
     */
    data class WidgetClicked(
        val widget: AnalyticsWidget,
        val action: String = "view_details"
    ) : DashboardEvent()
    
    /**
     * Event for time period changes affecting dashboard data.
     * 
     * Updates the time range for dashboard analytics and triggers data refresh
     * for widgets that depend on time-based data filtering.
     * 
     * @property timeRange The new time range to apply
     * @property refreshAffectedWidgets Whether to refresh widgets that use time data
     * 
     * Example:
     * ```kotlin
     * // Change to last 30 days
     * DashboardEvent.TimePeriodChanged(TimeRange.lastMonth())
     * 
     * // Change without refreshing widgets
     * DashboardEvent.TimePeriodChanged(
     *     TimeRange.lastWeek(), 
     *     refreshAffectedWidgets = false
     * )
     * ```
     */
    data class TimePeriodChanged(
        val timeRange: TimeRange,
        val refreshAffectedWidgets: Boolean = true
    ) : DashboardEvent()
    
    /**
     * Event for background data updates from external sources.
     * 
     * Handles data updates from background sync, real-time listeners, or
     * external notifications. Updates affected widgets without user interaction.
     * 
     * @property dataTypes Set of data types that were updated
     * @property sourceInfo Information about the update source
     * 
     * Example:
     * ```kotlin
     * // Background update from workout completion
     * DashboardEvent.BackgroundDataUpdate(
     *     dataTypes = setOf("volume", "frequency"),
     *     sourceInfo = "workout_completed"
     * )
     * ```
     */
    data class BackgroundDataUpdate(
        val dataTypes: Set<String>,
        val sourceInfo: String = "unknown"
    ) : DashboardEvent()
    
    /**
     * Event for network connectivity changes.
     * 
     * Handles online/offline state changes affecting dashboard functionality.
     * Updates UI state and manages offline/online transition behaviors.
     * 
     * @property isOnline Whether network connectivity is available
     * @property shouldRetryFailedOperations Whether to retry operations that failed offline
     * 
     * Example:
     * ```kotlin
     * // Network came online
     * DashboardEvent.NetworkStateChanged(isOnline = true, shouldRetryFailedOperations = true)
     * 
     * // Network went offline
     * DashboardEvent.NetworkStateChanged(isOnline = false)
     * ```
     */
    data class NetworkStateChanged(
        val isOnline: Boolean,
        val shouldRetryFailedOperations: Boolean = false
    ) : DashboardEvent()
    
    /**
     * Event for resetting dashboard preferences to defaults.
     * 
     * Restores default dashboard configuration and widget preferences.
     * Provides confirmation and rollback capabilities for user safety.
     * 
     * @property confirmationRequired Whether to show confirmation dialog
     * @property preserveUserData Whether to preserve user-specific customizations
     * 
     * Example:
     * ```kotlin
     * // Reset with confirmation
     * DashboardEvent.ResetToDefaults()
     * 
     * // Silent reset for troubleshooting
     * DashboardEvent.ResetToDefaults(
     *     confirmationRequired = false,
     *     preserveUserData = false
     * )
     * ```
     */
    data class ResetToDefaults(
        val confirmationRequired: Boolean = true,
        val preserveUserData: Boolean = true
    ) : DashboardEvent()
}

/**
 * Extension functions for event validation and processing.
 */

/**
 * Validates that a RefreshWidget event has a valid widget identifier.
 * 
 * @return true if the widget ID is valid, false otherwise
 */
fun DashboardEvent.RefreshWidget.isValid(): Boolean = widgetId.isNotBlank()

/**
 * Validates that a ReorderWidgets event has valid parameters.
 * 
 * @return true if the indices are valid and different, false otherwise
 */
fun DashboardEvent.ReorderWidgets.isValid(): Boolean = 
    fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex

/**
 * Validates that a ToggleWidgetVisibility event has a valid widget identifier.
 * 
 * @return true if the widget ID is valid, false otherwise
 */
fun DashboardEvent.ToggleWidgetVisibility.isValid(): Boolean = widgetId.isNotBlank()

/**
 * Validates that a StartDrag event has valid parameters.
 * 
 * @return true if the widget ID and initial position are valid, false otherwise
 */
fun DashboardEvent.StartDrag.isValid(): Boolean = 
    widgetId.isNotBlank() && initialPosition >= 0

/**
 * Validates that an UpdateDragPosition event has valid parameters.
 * 
 * @return true if the current position is valid, false otherwise
 */
fun DashboardEvent.UpdateDragPosition.isValid(): Boolean = currentPosition >= 0

/**
 * Validates that an EndDrag event has valid parameters.
 * 
 * @return true if the final position is valid or operation is cancelled, false otherwise
 */
fun DashboardEvent.EndDrag.isValid(): Boolean = 
    !shouldApply || finalPosition >= 0

/**
 * Checks if an event requires network connectivity.
 * 
 * @return true if the event requires network access, false otherwise
 */
fun DashboardEvent.requiresNetwork(): Boolean = when (this) {
    is DashboardEvent.LoadDashboard -> forceRefresh
    is DashboardEvent.RefreshDashboard -> true
    is DashboardEvent.RefreshWidget -> forceRefresh
    is DashboardEvent.UpdateConfiguration -> shouldPersist
    is DashboardEvent.ToggleWidgetVisibility -> shouldPersist
    is DashboardEvent.ExportData -> true
    is DashboardEvent.RetryOperation -> true
    is DashboardEvent.ResetToDefaults -> true
    else -> false
}

/**
 * Checks if an event affects widget data.
 * 
 * @return true if the event changes widget data, false otherwise
 */
fun DashboardEvent.affectsWidgetData(): Boolean = when (this) {
    is DashboardEvent.LoadDashboard -> true
    is DashboardEvent.RefreshDashboard -> true
    is DashboardEvent.RefreshWidget -> true
    is DashboardEvent.BackgroundDataUpdate -> true
    is DashboardEvent.TimePeriodChanged -> refreshAffectedWidgets
    is DashboardEvent.RetryOperation -> true
    else -> false
}

/**
 * Checks if an event affects widget layout or configuration.
 * 
 * @return true if the event changes widget layout, false otherwise
 */
fun DashboardEvent.affectsLayout(): Boolean = when (this) {
    is DashboardEvent.ReorderWidgets -> true
    is DashboardEvent.UpdateConfiguration -> true
    is DashboardEvent.ToggleWidgetVisibility -> true
    is DashboardEvent.ResetToDefaults -> true
    else -> false
}

/**
 * Gets the widget IDs affected by an event.
 * 
 * @return Set of widget IDs that will be affected by this event
 */
fun DashboardEvent.getAffectedWidgets(): Set<String> = when (this) {
    is DashboardEvent.RefreshWidget -> setOf(widgetId)
    is DashboardEvent.ToggleWidgetVisibility -> setOf(widgetId)
    is DashboardEvent.StartDrag -> setOf(widgetId)
    is DashboardEvent.RetryOperation -> if (widgetId != null) setOf(widgetId) else emptySet()
    is DashboardEvent.ClearError -> if (widgetId != null) setOf(widgetId) else emptySet()
    else -> emptySet()
}

/**
 * Checks if an event should trigger immediate UI feedback.
 * 
 * @return true if the event should show immediate feedback, false otherwise
 */
fun DashboardEvent.shouldShowImmediateFeedback(): Boolean = when (this) {
    is DashboardEvent.ReorderWidgets -> true
    is DashboardEvent.ToggleWidgetVisibility -> true
    is DashboardEvent.StartDrag -> true
    is DashboardEvent.UpdateDragPosition -> true
    is DashboardEvent.EndDrag -> true
    is DashboardEvent.UpdateConfiguration -> showFeedback
    is DashboardEvent.ClearError -> true
    else -> false
}