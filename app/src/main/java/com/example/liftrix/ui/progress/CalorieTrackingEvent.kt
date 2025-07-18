package com.example.liftrix.ui.progress

import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.ui.common.event.ViewModelEvent

/**
 * Sealed class hierarchy for calorie tracking events following MVI pattern.
 * 
 * This sealed class defines all possible user interactions and internal events
 * for the calorie tracking screen. Each event represents a specific action that
 * can trigger state changes in the CalorieTrackingViewModel.
 * 
 * Key Features:
 * - Type-safe event handling with sealed class hierarchy
 * - Comprehensive coverage of all calorie tracking operations
 * - Clear separation between user actions and internal events
 * - Parameter validation and data encapsulation
 * 
 * Event Categories:
 * - Data Loading: Initial load and refresh operations
 * - Time Period: Time range selection and filtering
 * - Workout Analysis: Individual workout calorie calculations
 * - Data Management: Cache management and refresh operations
 * 
 * Usage:
 * ```kotlin
 * // In Compose UI
 * Button(onClick = { onEvent(CalorieTrackingEvent.LoadSummary) }) {
 *     Text("Load Summary")
 * }
 * 
 * // In ViewModel
 * override fun handleEvent(event: CalorieTrackingEvent) {
 *     when (event) {
 *         is CalorieTrackingEvent.LoadSummary -> loadCalorieSummary()
 *         is CalorieTrackingEvent.TimePeriodChanged -> changeTimePeriod(event.timeRange)
 *         // ... other events
 *     }
 * }
 * ```
 * 
 * Architecture Integration:
 * - Implements ViewModelEvent interface for consistency
 * - Used with BaseViewModel event handling system
 * - Integrates with CalorieService for data operations
 * - Follows established MVI patterns from other ViewModels
 */
sealed class CalorieTrackingEvent : ViewModelEvent {
    
    /**
     * Event for loading initial calorie summary data.
     * 
     * Triggers loading of aggregated calorie statistics including total calories burned,
     * average daily calories, workout count, and weekly trends.
     * 
     * UI Actions:
     * - Screen initialization
     * - Pull-to-refresh on summary section
     * - Retry after error state
     * 
     * State Changes:
     * - Sets calorieSummary to Loading state
     * - Updates to Success or Failure based on service response
     * - Updates lastRefreshTimestamp
     */
    object LoadSummary : CalorieTrackingEvent()
    
    /**
     * Event for loading daily calorie data for the current time period.
     * 
     * @property timeRange Time range for daily data retrieval
     * 
     * Triggers loading of daily calorie burn data points including workout counts,
     * average intensity, and exercise category breakdown.
     * 
     * UI Actions:
     * - Time period selection change
     * - Daily chart refresh
     * - Drill-down from summary to daily view
     * 
     * State Changes:
     * - Sets dailyCalories to Loading state
     * - Updates currentTimeRange if different
     * - Updates to Success or Failure based on service response
     */
    data class LoadDailyCalories(val timeRange: TimeRange) : CalorieTrackingEvent()
    
    /**
     * Event for loading weekly calorie trend data.
     * 
     * Triggers loading of weekly trend analysis including moving averages,
     * trend percentages, peak/low weeks, and consistency scores.
     * 
     * UI Actions:
     * - Weekly trend chart refresh
     * - Trend analysis screen navigation
     * - Long-term progress review
     * 
     * State Changes:
     * - Sets weeklyTrend to Loading state
     * - Updates to Success or Failure based on service response
     * - Updates lastRefreshTimestamp
     */
    object LoadWeeklyTrend : CalorieTrackingEvent()
    
    /**
     * Event for changing the time period selection.
     * 
     * @property timeRange New time range for data display
     * 
     * Triggers time period change and refresh of relevant data types.
     * This event causes automatic refresh of daily calories while maintaining
     * summary and weekly trend data if still valid.
     * 
     * UI Actions:
     * - Time period dropdown selection
     * - Quick time range buttons (week, month, 3 months, year)
     * - Date range picker confirmation
     * 
     * State Changes:
     * - Updates currentTimeRange
     * - Triggers LoadDailyCalories for new time range
     * - Preserves other data if still valid
     */
    data class TimePeriodChanged(val timeRange: TimeRange) : CalorieTrackingEvent()
    
    /**
     * Event for calculating calories burned for a specific workout.
     * 
     * @property workout Workout instance with exercises and timing data
     * 
     * Triggers MET-based calorie calculation for the specified workout.
     * This is typically used for real-time calorie display during or after workouts.
     * 
     * UI Actions:
     * - Workout completion callback
     * - Individual workout analysis
     * - Real-time calorie display during workout
     * 
     * State Changes:
     * - May trigger partial summary refresh
     * - Updates individual workout calorie displays
     * - Integrates with daily calorie data if same day
     */
    data class CalculateWorkoutCalories(val workout: Workout) : CalorieTrackingEvent()
    
    /**
     * Event for refreshing all calorie data.
     * 
     * Triggers complete refresh of all calorie data types including summary,
     * daily calories, and weekly trends. This event is typically used when
     * the user wants to ensure all data is up-to-date.
     * 
     * UI Actions:
     * - Pull-to-refresh on entire screen
     * - Refresh button in toolbar
     * - Automatic refresh after app resume
     * 
     * State Changes:
     * - Sets all data types to Loading state
     * - Updates all data types based on service responses
     * - Updates lastRefreshTimestamp
     */
    object RefreshAllData : CalorieTrackingEvent()
    
    /**
     * Event for loading initial data when screen is first displayed.
     * 
     * Triggers loading of all necessary data for initial screen display.
     * This event is typically called in ViewModel init or when user
     * authentication state changes.
     * 
     * UI Actions:
     * - Screen navigation
     * - ViewModel initialization
     * - User authentication completion
     * 
     * State Changes:
     * - Sets appropriate data types to Loading state
     * - Loads data based on user authentication status
     * - Updates lastRefreshTimestamp
     */
    object LoadInitialData : CalorieTrackingEvent()
    
    /**
     * Event for retrying failed data operations.
     * 
     * Triggers retry of the last failed operation or all failed operations.
     * This event is used when the user wants to retry after encountering errors.
     * 
     * UI Actions:
     * - Retry button click in error states
     * - Automatic retry after network reconnection
     * - Error state action buttons
     * 
     * State Changes:
     * - Retries failed operations
     * - Updates failed states to Loading then Success/Failure
     * - Updates lastRefreshTimestamp
     */
    object RetryFailedOperations : CalorieTrackingEvent()
    
    /**
     * Event for clearing cached calorie data.
     * 
     * Triggers clearing of cached calorie data to force fresh data loading.
     * This event is useful for troubleshooting or when data inconsistencies
     * are suspected.
     * 
     * UI Actions:
     * - Developer/debug menu options
     * - Settings menu clear cache
     * - Long-press debug actions
     * 
     * State Changes:
     * - Resets all data types to NotAsked state
     * - Clears cache in service layer
     * - Triggers fresh data loading
     */
    object ClearCachedData : CalorieTrackingEvent()
    
    /**
     * Event for refreshing calorie data.
     * 
     * Triggers refresh of calorie summary and related data.
     * This event is used when the user wants to update calorie information.
     * 
     * State Changes:
     * - Sets calorie data to Loading state
     * - Updates to Success or Failure based on service response
     * - Updates lastRefreshTimestamp
     */
    object RefreshCalories : CalorieTrackingEvent()
    
    /**
     * Event for clearing error states.
     * 
     * Clears error states and returns to normal operation mode.
     * Provides user control over error message visibility and recovery.
     */
    object ClearError : CalorieTrackingEvent()
    
    /**
     * Event for navigating to calorie goal settings.
     * 
     * Triggers navigation to the calorie goal configuration screen.
     * This allows users to set and adjust their calorie burn goals.
     */
    object NavigateToCalorieGoalSettings : CalorieTrackingEvent()
    
    /**
     * Event for navigating to detailed calorie analytics.
     * 
     * Triggers navigation to the detailed calorie analytics screen.
     * This provides more comprehensive calorie tracking and analysis.
     */
    object NavigateToDetailedCalorieAnalytics : CalorieTrackingEvent()
    
    /**
     * Event for navigating to calorie history.
     * 
     * Triggers navigation to the calorie history screen.
     * This shows historical calorie burn data and trends.
     */
    object NavigateToCalorieHistory : CalorieTrackingEvent()
}

/**
 * Extension functions for CalorieTrackingEvent validation and processing.
 */

/**
 * Checks if the event requires user authentication.
 * 
 * @return true if the event requires authenticated user, false otherwise
 */
fun CalorieTrackingEvent.requiresAuthentication(): Boolean = when (this) {
    is CalorieTrackingEvent.LoadSummary,
    is CalorieTrackingEvent.LoadDailyCalories,
    is CalorieTrackingEvent.LoadWeeklyTrend,
    is CalorieTrackingEvent.CalculateWorkoutCalories,
    is CalorieTrackingEvent.RefreshAllData,
    is CalorieTrackingEvent.LoadInitialData,
    is CalorieTrackingEvent.RetryFailedOperations,
    is CalorieTrackingEvent.RefreshCalories -> true
    
    is CalorieTrackingEvent.TimePeriodChanged,
    is CalorieTrackingEvent.ClearCachedData,
    is CalorieTrackingEvent.ClearError,
    is CalorieTrackingEvent.NavigateToCalorieGoalSettings,
    is CalorieTrackingEvent.NavigateToDetailedCalorieAnalytics,
    is CalorieTrackingEvent.NavigateToCalorieHistory -> false
}

/**
 * Checks if the event involves data loading operations.
 * 
 * @return true if the event triggers data loading, false otherwise
 */
fun CalorieTrackingEvent.isDataLoadingEvent(): Boolean = when (this) {
    is CalorieTrackingEvent.LoadSummary,
    is CalorieTrackingEvent.LoadDailyCalories,
    is CalorieTrackingEvent.LoadWeeklyTrend,
    is CalorieTrackingEvent.RefreshAllData,
    is CalorieTrackingEvent.LoadInitialData,
    is CalorieTrackingEvent.RetryFailedOperations,
    is CalorieTrackingEvent.RefreshCalories -> true
    
    is CalorieTrackingEvent.TimePeriodChanged,
    is CalorieTrackingEvent.CalculateWorkoutCalories,
    is CalorieTrackingEvent.ClearCachedData,
    is CalorieTrackingEvent.ClearError,
    is CalorieTrackingEvent.NavigateToCalorieGoalSettings,
    is CalorieTrackingEvent.NavigateToDetailedCalorieAnalytics,
    is CalorieTrackingEvent.NavigateToCalorieHistory -> false
}

/**
 * Checks if the event affects time-based data filtering.
 * 
 * @return true if the event changes time range context, false otherwise
 */
fun CalorieTrackingEvent.affectsTimeRange(): Boolean = when (this) {
    is CalorieTrackingEvent.TimePeriodChanged,
    is CalorieTrackingEvent.LoadDailyCalories -> true
    
    else -> false
}

/**
 * Gets the time range associated with the event, if applicable.
 * 
 * @return TimeRange associated with the event or null if not applicable
 */
fun CalorieTrackingEvent.getTimeRange(): TimeRange? = when (this) {
    is CalorieTrackingEvent.LoadDailyCalories -> timeRange
    is CalorieTrackingEvent.TimePeriodChanged -> timeRange
    else -> null
}

/**
 * Checks if the event can be executed concurrently with other events.
 * 
 * @return true if the event can run concurrently, false if it should be sequential
 */
fun CalorieTrackingEvent.canExecuteConcurrently(): Boolean = when (this) {
    is CalorieTrackingEvent.LoadSummary,
    is CalorieTrackingEvent.LoadDailyCalories,
    is CalorieTrackingEvent.LoadWeeklyTrend,
    is CalorieTrackingEvent.CalculateWorkoutCalories -> true
    
    is CalorieTrackingEvent.TimePeriodChanged,
    is CalorieTrackingEvent.RefreshAllData,
    is CalorieTrackingEvent.LoadInitialData,
    is CalorieTrackingEvent.RetryFailedOperations,
    is CalorieTrackingEvent.ClearCachedData,
    is CalorieTrackingEvent.RefreshCalories,
    is CalorieTrackingEvent.ClearError,
    is CalorieTrackingEvent.NavigateToCalorieGoalSettings,
    is CalorieTrackingEvent.NavigateToDetailedCalorieAnalytics,
    is CalorieTrackingEvent.NavigateToCalorieHistory -> false
}

/**
 * Gets a human-readable description of the event for logging and debugging.
 * 
 * @return String description of the event
 */
fun CalorieTrackingEvent.getDescription(): String = when (this) {
    is CalorieTrackingEvent.LoadSummary -> "Load calorie summary data"
    is CalorieTrackingEvent.LoadDailyCalories -> "Load daily calorie data for ${timeRange.getDisplayName()}"
    is CalorieTrackingEvent.LoadWeeklyTrend -> "Load weekly calorie trend data"
    is CalorieTrackingEvent.TimePeriodChanged -> "Change time period to ${timeRange.getDisplayName()}"
    is CalorieTrackingEvent.CalculateWorkoutCalories -> "Calculate calories for workout ${workout.id}"
    is CalorieTrackingEvent.RefreshAllData -> "Refresh all calorie data"
    is CalorieTrackingEvent.LoadInitialData -> "Load initial calorie data"
    is CalorieTrackingEvent.RetryFailedOperations -> "Retry failed operations"
    is CalorieTrackingEvent.ClearCachedData -> "Clear cached calorie data"
    is CalorieTrackingEvent.RefreshCalories -> "Refresh calorie data"
    is CalorieTrackingEvent.ClearError -> "Clear error state"
    is CalorieTrackingEvent.NavigateToCalorieGoalSettings -> "Navigate to calorie goal settings"
    is CalorieTrackingEvent.NavigateToDetailedCalorieAnalytics -> "Navigate to detailed calorie analytics"
    is CalorieTrackingEvent.NavigateToCalorieHistory -> "Navigate to calorie history"
}

/**
 * Extension function for TimeRange to get display name.
 * This is a placeholder - actual implementation should be in TimeRange class.
 */
private fun TimeRange.getDisplayName(): String = "Time Range" // Placeholder implementation