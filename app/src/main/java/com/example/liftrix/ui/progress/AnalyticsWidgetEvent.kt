package com.example.liftrix.ui.progress

import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.ui.common.event.ViewModelEvent

/**
 * Sealed class hierarchy for analytics widget events in the MVI pattern.
 * 
 * This event hierarchy handles all user interactions and system events related to
 * analytics widgets, including widget data loading, configuration updates, and
 * visibility management. Events are processed by AnalyticsWidgetViewModel to
 * update the UI state reactively.
 * 
 * Key Features:
 * - Widget data loading and refresh operations
 * - Configuration updates with immediate UI feedback
 * - Widget visibility toggle with persistence
 * - Bulk operations for performance optimization
 * - Error recovery and retry mechanisms
 * 
 * Usage:
 * ```kotlin
 * // Load specific widget data
 * viewModel.handleEvent(AnalyticsWidgetEvent.LoadWidget("TotalVolume"))
 * 
 * // Toggle widget visibility
 * viewModel.handleEvent(AnalyticsWidgetEvent.ToggleVisibility("WorkoutFrequency"))
 * 
 * // Update dashboard configuration
 * viewModel.handleEvent(AnalyticsWidgetEvent.UpdateConfiguration(newConfig))
 * 
 * // Refresh all widgets
 * viewModel.handleEvent(AnalyticsWidgetEvent.RefreshAllWidgets)
 * ```
 */
sealed class AnalyticsWidgetEvent : ViewModelEvent {
    
    /**
     * Event for loading data for a specific widget.
     * 
     * Triggers asynchronous loading of widget data from the analytics service.
     * Updates the widget state with loading indicators and handles errors gracefully.
     * 
     * @property widgetId The unique identifier of the widget to load
     * @property forceRefresh Whether to bypass cache and force fresh data loading
     * 
     * Example:
     * ```kotlin
     * // Load widget with cache
     * AnalyticsWidgetEvent.LoadWidget("TotalVolume")
     * 
     * // Force refresh from remote
     * AnalyticsWidgetEvent.LoadWidget("TotalVolume", forceRefresh = true)
     * ```
     */
    data class LoadWidget(
        val widgetId: String,
        val forceRefresh: Boolean = false
    ) : AnalyticsWidgetEvent()
    
    /**
     * Event for toggling widget visibility.
     * 
     * Provides immediate UI feedback for widget visibility changes with persistence.
     * Validates business rules (e.g., minimum visible widgets) before applying changes.
     * 
     * @property widgetId The unique identifier of the widget to toggle
     * @property visible Optional explicit visibility state (null for toggle)
     * 
     * Example:
     * ```kotlin
     * // Toggle current visibility
     * AnalyticsWidgetEvent.ToggleVisibility("WorkoutFrequency")
     * 
     * // Set explicit visibility
     * AnalyticsWidgetEvent.ToggleVisibility("WorkoutFrequency", visible = true)
     * ```
     */
    data class ToggleVisibility(
        val widgetId: String,
        val visible: Boolean? = null
    ) : AnalyticsWidgetEvent()
    
    /**
     * Event for updating dashboard configuration.
     * 
     * Applies configuration changes with atomic updates and immediate UI reflection.
     * Handles layout changes, theme updates, and widget arrangement modifications.
     * 
     * @property configuration The new dashboard configuration to apply
     * @property shouldPersist Whether to persist the configuration changes
     * 
     * Example:
     * ```kotlin
     * val newConfig = DashboardConfiguration(
     *     layoutMode = WidgetLayoutMode.GRID,
     *     widgetOrder = listOf("TotalVolume", "WorkoutFrequency")
     * )
     * AnalyticsWidgetEvent.UpdateConfiguration(newConfig)
     * ```
     */
    data class UpdateConfiguration(
        val configuration: DashboardConfiguration,
        val shouldPersist: Boolean = true
    ) : AnalyticsWidgetEvent()
    
    /**
     * Event for refreshing all widget data.
     * 
     * Triggers parallel refresh of all visible widgets with coordinated loading states.
     * Optimizes network requests and handles partial failures gracefully.
     * 
     * @property showLoadingStates Whether to show loading indicators during refresh
     * @property retryFailedWidgets Whether to retry previously failed widgets
     * 
     * Example:
     * ```kotlin
     * // Standard refresh with loading states
     * AnalyticsWidgetEvent.RefreshAllWidgets
     * 
     * // Silent refresh without loading indicators
     * AnalyticsWidgetEvent.RefreshAllWidgets(showLoadingStates = false)
     * ```
     */
    data class RefreshAllWidgets(
        val showLoadingStates: Boolean = true,
        val retryFailedWidgets: Boolean = true
    ) : AnalyticsWidgetEvent()
    
    /**
     * Event for reordering widgets in the dashboard.
     * 
     * Handles drag-and-drop reordering with immediate UI updates and persistence.
     * Validates new widget positions and updates configuration accordingly.
     * 
     * @property widgetId The identifier of the widget being moved
     * @property newPosition The new position index for the widget
     * @property shouldPersist Whether to persist the reordering changes
     * 
     * Example:
     * ```kotlin
     * // Move widget to new position
     * AnalyticsWidgetEvent.ReorderWidget("TotalVolume", newPosition = 2)
     * ```
     */
    data class ReorderWidget(
        val widgetId: String,
        val newPosition: Int,
        val shouldPersist: Boolean = true
    ) : AnalyticsWidgetEvent()
    
    /**
     * Event for resetting widget preferences to defaults.
     * 
     * Restores default widget configuration based on user experience level.
     * Provides confirmation dialog and rollback capability for user safety.
     * 
     * @property confirmationRequired Whether to show confirmation dialog
     * @property preserveCustomizations Whether to preserve user customizations
     * 
     * Example:
     * ```kotlin
     * // Reset with confirmation
     * AnalyticsWidgetEvent.ResetPreferences()
     * 
     * // Silent reset for system operations
     * AnalyticsWidgetEvent.ResetPreferences(confirmationRequired = false)
     * ```
     */
    data class ResetPreferences(
        val confirmationRequired: Boolean = true,
        val preserveCustomizations: Boolean = false
    ) : AnalyticsWidgetEvent()
    
    /**
     * Event for retrying failed widget operations.
     * 
     * Handles retry logic for failed widget data loading or configuration updates.
     * Implements exponential backoff and maximum retry limits for resilience.
     * 
     * @property widgetId The identifier of the widget to retry (null for all failed)
     * @property operation The specific operation to retry (null for last failed operation)
     * 
     * Example:
     * ```kotlin
     * // Retry specific widget
     * AnalyticsWidgetEvent.RetryOperation("TotalVolume")
     * 
     * // Retry all failed widgets
     * AnalyticsWidgetEvent.RetryOperation()
     * ```
     */
    data class RetryOperation(
        val widgetId: String? = null,
        val operation: String? = null
    ) : AnalyticsWidgetEvent()
    
    /**
     * Event for dismissing error states.
     * 
     * Clears error states and returns widgets to normal operation mode.
     * Provides user control over error message visibility and recovery.
     * 
     * @property widgetId The identifier of the widget to dismiss errors for (null for all)
     * @property shouldClearHistory Whether to clear error history
     * 
     * Example:
     * ```kotlin
     * // Dismiss specific widget errors
     * AnalyticsWidgetEvent.DismissError("TotalVolume")
     * 
     * // Dismiss all widget errors
     * AnalyticsWidgetEvent.DismissError()
     * ```
     */
    data class DismissError(
        val widgetId: String? = null,
        val shouldClearHistory: Boolean = false
    ) : AnalyticsWidgetEvent()
    
    /**
     * Event for widget interaction tracking.
     * 
     * Captures user interactions with widgets for analytics and behavior analysis.
     * Helps improve widget design and user experience through data-driven insights.
     * 
     * @property widgetId The identifier of the interacted widget
     * @property interactionType The type of interaction (tap, long_press, etc.)
     * @property metadata Additional interaction metadata
     * 
     * Example:
     * ```kotlin
     * // Track widget tap
     * AnalyticsWidgetEvent.TrackInteraction(
     *     widgetId = "TotalVolume",
     *     interactionType = "tap",
     *     metadata = mapOf("value" to "1250kg")
     * )
     * ```
     */
    data class TrackInteraction(
        val widgetId: String,
        val interactionType: String,
        val metadata: Map<String, Any> = emptyMap()
    ) : AnalyticsWidgetEvent()
    
    /**
     * Event for drag-and-drop widget reordering.
     * 
     * Handles drag-and-drop reordering operations with from/to indices for
     * immediate UI updates and preference persistence. Used by the drag-and-drop
     * grid component to communicate reorder operations to the ViewModel.
     * 
     * @property fromIndex The original index position of the widget
     * @property toIndex The new index position for the widget
     * @property shouldPersist Whether to persist the reordering changes
     * 
     * Example:
     * ```kotlin
     * // Reorder widget from position 0 to position 2
     * AnalyticsWidgetEvent.WidgetReordered(fromIndex = 0, toIndex = 2)
     * ```
     */
    data class WidgetReordered(
        val fromIndex: Int,
        val toIndex: Int,
        val shouldPersist: Boolean = true
    ) : AnalyticsWidgetEvent()
    
    /**
     * Event for widget click handling.
     * 
     * Handles widget click events and navigation or detail view actions.
     * 
     * @property widget The analytics widget that was clicked
     * 
     * Example:
     * ```kotlin
     * // Handle widget click
     * AnalyticsWidgetEvent.WidgetClicked(analyticsWidget)
     * ```
     */
    data class WidgetClicked(
        val widget: AnalyticsWidget
    ) : AnalyticsWidgetEvent()
    
    /**
     * Event for clearing widget error states.
     * 
     * Clears error states for specific widgets or all widgets.
     * 
     * @property widgetId The identifier of the widget to clear errors for (null for all)
     * 
     * Example:
     * ```kotlin
     * // Clear specific widget errors
     * AnalyticsWidgetEvent.ClearError("TotalVolume")
     * 
     * // Clear all widget errors
     * AnalyticsWidgetEvent.ClearError()
     * ```
     */
    data class ClearError(
        val widgetId: String? = null
    ) : AnalyticsWidgetEvent()
    
    /**
     * Event for forcing advanced user level with all widgets enabled.
     * 
     * Sets user to advanced level and enables all 23 available widgets.
     * Used for troubleshooting when widgets aren't appearing properly.
     * 
     * Example:
     * ```kotlin
     * // Force show all widgets
     * AnalyticsWidgetEvent.ForceAllWidgets()
     * ```
     */
    data class ForceAllWidgets(
        val dummy: Boolean = true
    ) : AnalyticsWidgetEvent()
    
    /**
     * Event for navigating to dashboard customization screen.
     * 
     * Triggers navigation to the enhanced dashboard customization interface
     * where users can configure widget visibility, layout modes, and preferences.
     * 
     * Example:
     * ```kotlin
     * // Navigate to customization screen
     * AnalyticsWidgetEvent.NavigateToDashboardCustomization
     * ```
     */
    data object NavigateToDashboardCustomization : AnalyticsWidgetEvent()
}

/**
 * Extension functions for event validation and processing.
 */

/**
 * Validates that a LoadWidget event has a valid widget identifier.
 * 
 * @return true if the widget ID is valid, false otherwise
 */
fun AnalyticsWidgetEvent.LoadWidget.isValid(): Boolean = widgetId.isNotBlank()

/**
 * Validates that a ToggleVisibility event has a valid widget identifier.
 * 
 * @return true if the widget ID is valid, false otherwise
 */
fun AnalyticsWidgetEvent.ToggleVisibility.isValid(): Boolean = widgetId.isNotBlank()

/**
 * Validates that a ReorderWidget event has valid parameters.
 * 
 * @return true if the widget ID and position are valid, false otherwise
 */
fun AnalyticsWidgetEvent.ReorderWidget.isValid(): Boolean = 
    widgetId.isNotBlank() && newPosition >= 0

/**
 * Validates that a WidgetReordered event has valid parameters.
 * 
 * @return true if the from/to indices are valid, false otherwise
 */
fun AnalyticsWidgetEvent.WidgetReordered.isValid(): Boolean = 
    fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex

/**
 * Validates that a TrackInteraction event has valid parameters.
 * 
 * @return true if the widget ID and interaction type are valid, false otherwise
 */
fun AnalyticsWidgetEvent.TrackInteraction.isValid(): Boolean = 
    widgetId.isNotBlank() && interactionType.isNotBlank()

/**
 * Checks if an event requires network connectivity.
 * 
 * @return true if the event requires network access, false otherwise
 */
fun AnalyticsWidgetEvent.requiresNetwork(): Boolean = when (this) {
    is AnalyticsWidgetEvent.LoadWidget -> forceRefresh
    is AnalyticsWidgetEvent.RefreshAllWidgets -> true
    is AnalyticsWidgetEvent.UpdateConfiguration -> shouldPersist
    is AnalyticsWidgetEvent.ResetPreferences -> true
    is AnalyticsWidgetEvent.RetryOperation -> true
    else -> false
}

/**
 * Checks if an event affects widget visibility.
 * 
 * @return true if the event changes widget visibility, false otherwise
 */
fun AnalyticsWidgetEvent.affectsVisibility(): Boolean = when (this) {
    is AnalyticsWidgetEvent.ToggleVisibility -> true
    is AnalyticsWidgetEvent.UpdateConfiguration -> true
    is AnalyticsWidgetEvent.ResetPreferences -> true
    else -> false
}

/**
 * Gets the widget IDs affected by an event.
 * 
 * @return Set of widget IDs that will be affected by this event
 */
fun AnalyticsWidgetEvent.getAffectedWidgets(): Set<String> = when (this) {
    is AnalyticsWidgetEvent.LoadWidget -> setOf(widgetId)
    is AnalyticsWidgetEvent.ToggleVisibility -> setOf(widgetId)
    is AnalyticsWidgetEvent.ReorderWidget -> setOf(widgetId)
    is AnalyticsWidgetEvent.TrackInteraction -> setOf(widgetId)
    is AnalyticsWidgetEvent.DismissError -> if (widgetId != null) setOf(widgetId) else emptySet()
    is AnalyticsWidgetEvent.RetryOperation -> if (widgetId != null) setOf(widgetId) else emptySet()
    else -> emptySet()
}