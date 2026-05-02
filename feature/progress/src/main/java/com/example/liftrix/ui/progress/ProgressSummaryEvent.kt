package com.example.liftrix.ui.progress

import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.ui.common.event.ViewModelEvent

/**
 * Sealed class hierarchy for ProgressSummaryViewModel events following the MVI pattern.
 * 
 * This event system provides type-safe communication between the UI and ViewModel,
 * enabling clear separation of concerns and predictable state management. Each event
 * represents a specific user action or internal trigger that affects the progress
 * summary state.
 * 
 * Key Features:
 * - Type-safe event handling with sealed class hierarchy
 * - Clear separation between user actions and internal events
 * - Comprehensive event coverage for all summary operations
 * - Integration with existing ViewModelEvent system
 * - Support for both simple and complex event scenarios
 * 
 * Architecture Integration:
 * - Extends ViewModelEvent for consistency with BaseViewModel
 * - Processed by ProgressSummaryViewModel.handleEvent()
 * - Triggers appropriate state updates and data operations
 * - Enables reactive UI updates through state flow
 * 
 * Usage:
 * ```kotlin
 * // From UI components
 * viewModel.handleEvent(ProgressSummaryEvent.LoadSummary)
 * viewModel.handleEvent(ProgressSummaryEvent.RefreshSummary)
 * viewModel.handleEvent(ProgressSummaryEvent.TimePeriodChanged(TimeRange.lastWeek()))
 * ```
 * 
 * Event Processing:
 * - Events are processed sequentially in the ViewModel
 * - Each event triggers appropriate data operations
 * - State updates are handled through BaseViewModel methods
 * - Errors are processed through centralized error handling
 */
sealed class ProgressSummaryEvent : ViewModelEvent {
    
    /**
     * Event to load initial summary data.
     * 
     * Triggered when:
     * - Screen is first displayed
     * - User authentication state changes
     * - ViewModel is initialized
     * 
     * This event initiates the loading of progress summary data for the current
     * user and time range. It sets the loading state and triggers data fetching
     * from the ProgressDataPort.
     * 
     * Processing:
     * - Checks user authentication status
     * - Sets loading state in UI
     * - Calls ProgressDataPort.getProgressSummary()
     * - Updates state with success or error result
     */
    object LoadSummary : ProgressSummaryEvent()
    
    /**
     * Event to refresh summary data.
     * 
     * Triggered when:
     * - User pulls to refresh
     * - Refresh button is pressed
     * - Data needs to be updated after changes
     * - Cache invalidation is required
     * 
     * This event forces a fresh data fetch from the service, bypassing any
     * caching mechanisms. It preserves the current state while indicating
     * that a refresh is in progress.
     * 
     * Processing:
     * - Sets refresh flag to true
     * - Calls ProgressDataPort.getProgressSummary()
     * - Updates state with new data
     * - Clears refresh flag
     */
    object RefreshSummary : ProgressSummaryEvent()
    
    /**
     * Event to change the time period for summary data.
     * 
     * @param timeRange The new time range for data filtering
     * 
     * Triggered when:
     * - User selects different time period in UI
     * - Time range filter is changed
     * - Date range picker updates
     * 
     * This event updates the current time range and triggers a data reload
     * with the new time parameters. It ensures that all summary statistics
     * are recalculated for the new time period.
     * 
     * Processing:
     * - Updates current time range in state
     * - Sets loading state
     * - Calls ProgressDataPort.getProgressSummary() with new range
     * - Updates state with new data
     * 
     * Example usage:
     * ```kotlin
     * viewModel.handleEvent(
     *     ProgressSummaryEvent.TimePeriodChanged(TimeRange.lastWeek())
     * )
     * ```
     */
    data class TimePeriodChanged(val timeRange: TimeRange) : ProgressSummaryEvent()
    
    /**
     * Event to retry loading summary data after a failure.
     * 
     * Triggered when:
     * - User clicks retry button on error screen
     * - Error recovery is initiated
     * - Network connectivity is restored
     * 
     * This event retries the last failed operation, typically data loading.
     * It follows the same flow as LoadSummary but may include additional
     * error recovery logic.
     * 
     * Processing:
     * - Clears current error state
     * - Sets loading state
     * - Retries the last failed operation
     * - Updates state with success or error result
     */
    object RetryLoad : ProgressSummaryEvent()
    
    /**
     * Event to clear the current error state.
     * 
     * Triggered when:
     * - User dismisses error dialog
     * - Error message is acknowledged
     * - UI needs to clear error state
     * 
     * This event clears any error information from the state while preserving
     * other state data like previous successful data or current time range.
     * 
     * Processing:
     * - Clears error from summary data
     * - Preserves previous successful data if available
     * - Updates state to non-error condition
     */
    object ClearError : ProgressSummaryEvent()
    
    /**
     * Event to force refresh all related data.
     * 
     * Triggered when:
     * - User completes a workout
     * - Data synchronization is complete
     * - External data updates are detected
     * 
     * This event triggers a comprehensive refresh of all summary data and
     * related cached information. It's used when external events may have
     * affected the summary statistics.
     * 
     * Processing:
     * - Invalidates all cached data
     * - Calls ProgressDataPort.refreshAllData()
     * - Reloads summary data with current time range
     * - Updates state with fresh data
     */
    object ForceRefresh : ProgressSummaryEvent()
    
    /**
     * Event to update time range to a predefined period.
     * 
     * @param predefinedRange The predefined time range type
     * 
     * Triggered when:
     * - User selects quick time range options
     * - Preset time periods are chosen
     * - Navigation requires specific time ranges
     * 
     * This event provides shortcuts to common time ranges like "Last Week",
     * "Last Month", "Last Year", etc. It's a convenience event that maps
     * to TimePeriodChanged with predefined TimeRange values.
     * 
     * Processing:
     * - Maps predefined range to actual TimeRange
     * - Delegates to TimePeriodChanged event
     * - Updates state with new time range and data
     */
    data class QuickTimeRangeSelected(val predefinedRange: PredefinedTimeRange) : ProgressSummaryEvent()
    
    /**
     * Event for background data updates.
     * 
     * Triggered when:
     * - Data sync completes in background
     * - Real-time updates are received
     * - Cache is updated externally
     * 
     * This event handles background data updates without explicit user action.
     * It's used for maintaining data freshness without disrupting user experience.
     * 
     * Processing:
     * - Checks if current data is stale
     * - Updates data if necessary
     * - Preserves current UI state
     * - Notifies UI of changes if significant
     */
    object BackgroundDataUpdate : ProgressSummaryEvent()
}

/**
 * Enumeration of predefined time ranges for quick selection.
 * 
 * This enum provides standard time range options that are commonly used
 * in progress tracking interfaces. It enables quick time range selection
 * without requiring users to specify exact dates.
 * 
 * Each predefined range maps to a specific TimeRange instance with
 * appropriate start and end dates calculated from the current time.
 */
enum class PredefinedTimeRange {
    /**
     * Last 30 days from today.
     */
    LAST_MONTH,
    
    /**
     * Last 180 days from today.
     */
    LAST_SIX_MONTHS,
    
    /**
     * Current calendar month.
     */
    THIS_MONTH,
    
    /**
     * All time (no date filtering).
     */
    ALL_TIME;
    
    /**
     * Converts the predefined range to an actual TimeRange instance.
     * 
     * @return TimeRange instance representing this predefined range
     */
    fun toTimeRange(): TimeRange = when (this) {
        LAST_MONTH -> TimeRange.lastMonth()
        LAST_SIX_MONTHS -> TimeRange.lastSixMonths()
        THIS_MONTH -> TimeRange.lastMonth()
        ALL_TIME -> TimeRange.allTime()
    }
    
    /**
     * Gets a human-readable display name for the time range.
     * 
     * @return Display name for UI presentation
     */
    fun getDisplayName(): String = when (this) {
        LAST_MONTH -> "Last Month"
        LAST_SIX_MONTHS -> "Last 6 Months"
        THIS_MONTH -> "This Month"
        ALL_TIME -> "All Time"
    }
}

/**
 * Extension functions for ProgressSummaryEvent validation and utilities.
 */

/**
 * Checks if the event requires user authentication.
 * 
 * @return true if the event requires an authenticated user, false otherwise
 */
fun ProgressSummaryEvent.requiresAuthentication(): Boolean = when (this) {
    is ProgressSummaryEvent.LoadSummary,
    is ProgressSummaryEvent.RefreshSummary,
    is ProgressSummaryEvent.TimePeriodChanged,
    is ProgressSummaryEvent.RetryLoad,
    is ProgressSummaryEvent.ForceRefresh,
    is ProgressSummaryEvent.QuickTimeRangeSelected,
    is ProgressSummaryEvent.BackgroundDataUpdate -> true
    is ProgressSummaryEvent.ClearError -> false
}

/**
 * Checks if the event triggers data loading from external sources.
 * 
 * @return true if the event causes data loading, false otherwise
 */
fun ProgressSummaryEvent.triggersDataLoading(): Boolean = when (this) {
    is ProgressSummaryEvent.LoadSummary,
    is ProgressSummaryEvent.RefreshSummary,
    is ProgressSummaryEvent.TimePeriodChanged,
    is ProgressSummaryEvent.RetryLoad,
    is ProgressSummaryEvent.ForceRefresh,
    is ProgressSummaryEvent.QuickTimeRangeSelected,
    is ProgressSummaryEvent.BackgroundDataUpdate -> true
    is ProgressSummaryEvent.ClearError -> false
}

/**
 * Checks if the event is a user-initiated action.
 * 
 * @return true if the event is triggered by user action, false otherwise
 */
fun ProgressSummaryEvent.isUserInitiated(): Boolean = when (this) {
    is ProgressSummaryEvent.LoadSummary,
    is ProgressSummaryEvent.RefreshSummary,
    is ProgressSummaryEvent.TimePeriodChanged,
    is ProgressSummaryEvent.RetryLoad,
    is ProgressSummaryEvent.ClearError,
    is ProgressSummaryEvent.ForceRefresh,
    is ProgressSummaryEvent.QuickTimeRangeSelected -> true
    is ProgressSummaryEvent.BackgroundDataUpdate -> false
}

/**
 * Gets the priority level of the event for processing order.
 * 
 * @return Priority level (higher number = higher priority)
 */
fun ProgressSummaryEvent.getPriority(): Int = when (this) {
    is ProgressSummaryEvent.ClearError -> 10
    is ProgressSummaryEvent.ForceRefresh -> 9
    is ProgressSummaryEvent.RetryLoad -> 8
    is ProgressSummaryEvent.RefreshSummary -> 7
    is ProgressSummaryEvent.TimePeriodChanged -> 6
    is ProgressSummaryEvent.QuickTimeRangeSelected -> 5
    is ProgressSummaryEvent.LoadSummary -> 4
    is ProgressSummaryEvent.BackgroundDataUpdate -> 1
}