package com.example.liftrix.ui.progress

import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.service.CalorieSummary
import com.example.liftrix.service.DailyCalorieData
import com.example.liftrix.service.WeeklyCalorieTrend
import com.example.liftrix.ui.common.state.AsyncData

/**
 * UI state for calorie tracking screen following MVI pattern.
 * 
 * This state represents the complete calorie tracking screen data including summary statistics,
 * daily calorie data, weekly trends, and current user/time range context. Each data type
 * is wrapped in AsyncData to provide proper loading, success, and error states.
 * 
 * Key Features:
 * - Independent AsyncData state for each calorie data type
 * - User and time range context for data scoping
 * - Refresh timestamp for cache invalidation
 * - Comprehensive data modeling for all calorie-related metrics
 * 
 * State Management:
 * - calorieSummary: Aggregated calorie statistics and trends
 * - dailyCalories: Daily calorie burn data points
 * - weeklyTrend: Weekly trend analysis and moving averages
 * - currentTimeRange: Selected time period for data display
 * - userId: Current user identifier for data scoping
 * - lastRefreshTimestamp: Cache invalidation and freshness tracking
 * 
 * Usage:
 * ```kotlin
 * when (uiState) {
 *     is UiState.Success -> {
 *         val state = uiState.data
 *         CalorieTrackingScreen(
 *             calorieSummary = state.calorieSummary,
 *             dailyCalories = state.dailyCalories,
 *             weeklyTrend = state.weeklyTrend,
 *             onEvent = viewModel::handleEvent
 *         )
 *     }
 *     is UiState.Loading -> LoadingIndicator()
 *     is UiState.Error -> ErrorMessage(uiState.error)
 * }
 * ```
 * 
 * @property calorieSummary AsyncData for calorie summary statistics
 * @property dailyCalories AsyncData for daily calorie burn data
 * @property weeklyTrend AsyncData for weekly calorie trend analysis
 * @property currentTimeRange Current time period selection
 * @property userId Current user identifier (null if not authenticated)
 * @property lastRefreshTimestamp Timestamp of last data refresh
 */
data class CalorieTrackingState(
    val calorieSummary: AsyncData<CalorieSummary> = AsyncData.NotAsked,
    val dailyCalories: AsyncData<List<DailyCalorieData>> = AsyncData.NotAsked,
    val weeklyTrend: AsyncData<WeeklyCalorieTrend> = AsyncData.NotAsked,
    val currentTimeRange: TimeRange = TimeRange.lastMonth(),
    val userId: String? = null,
    val lastRefreshTimestamp: Long = 0L
) {
    
    /**
     * Checks if the current user is valid for data operations.
     * 
     * @return true if user is authenticated and has valid ID
     */
    fun hasValidUser(): Boolean = !userId.isNullOrBlank()
    
    /**
     * Checks if all calorie data is in NotAsked state.
     * Used to determine if initial data loading is required.
     * 
     * @return true if all data is not yet requested
     */
    fun areAllDataNotAsked(): Boolean = 
        calorieSummary is AsyncData.NotAsked &&
        dailyCalories is AsyncData.NotAsked &&
        weeklyTrend is AsyncData.NotAsked
    
    /**
     * Checks if any calorie data is currently loading.
     * 
     * @return true if any data loading is in progress
     */
    fun isAnyDataLoading(): Boolean = 
        calorieSummary is AsyncData.Loading ||
        dailyCalories is AsyncData.Loading ||
        weeklyTrend is AsyncData.Loading
    
    /**
     * Checks if all calorie data has been successfully loaded.
     * 
     * @return true if all data is in Success state
     */
    fun isAllDataLoaded(): Boolean = 
        calorieSummary is AsyncData.Success &&
        dailyCalories is AsyncData.Success &&
        weeklyTrend is AsyncData.Success
    
    /**
     * Checks if any calorie data has failed to load.
     * 
     * @return true if any data is in Failure state
     */
    fun hasAnyDataFailed(): Boolean = 
        calorieSummary is AsyncData.Failure ||
        dailyCalories is AsyncData.Failure ||
        weeklyTrend is AsyncData.Failure
    
    /**
     * Gets all successful calorie data for display.
     * 
     * @return Triple of successful data (summary, daily, weekly) or null if not all successful
     */
    fun getSuccessfulData(): Triple<CalorieSummary, List<DailyCalorieData>, WeeklyCalorieTrend>? {
        return if (isAllDataLoaded()) {
            Triple(
                (calorieSummary as AsyncData.Success).data,
                (dailyCalories as AsyncData.Success).data,
                (weeklyTrend as AsyncData.Success).data
            )
        } else {
            null
        }
    }
    
    /**
     * Gets the first error encountered in data loading.
     * 
     * @return First LiftrixError found or null if no errors
     */
    fun getFirstError(): com.example.liftrix.domain.model.error.LiftrixError? {
        return when {
            calorieSummary is AsyncData.Failure -> calorieSummary.error
            dailyCalories is AsyncData.Failure -> dailyCalories.error
            weeklyTrend is AsyncData.Failure -> weeklyTrend.error
            else -> null
        }
    }
    
    /**
     * Checks if data is fresh based on refresh timestamp.
     * 
     * @param maxAgeMs Maximum age in milliseconds (default: 5 minutes)
     * @return true if data is within the acceptable age threshold
     */
    fun isDataFresh(maxAgeMs: Long = 5 * 60 * 1000): Boolean {
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastRefreshTimestamp) < maxAgeMs
    }
    
    /**
     * Gets display text for current time range.
     * 
     * @return Human-readable time range description
     */
    fun getTimeRangeDisplayText(): String {
        return when {
            currentTimeRange == TimeRange.lastWeek() -> "Last Week"
            currentTimeRange == TimeRange.lastMonth() -> "Last Month"
            currentTimeRange == TimeRange.lastQuarter() -> "Last Quarter"
            currentTimeRange == TimeRange.lastYear() -> "Last Year"
            else -> "Custom Range"
        }
    }
}

/**
 * Factory functions for creating CalorieTrackingState instances.
 */

/**
 * Creates a CalorieTrackingState with loading state for all data types.
 * 
 * @param userId User identifier for data scoping
 * @param timeRange Time range for data retrieval
 * @return CalorieTrackingState with loading states
 */
fun createLoadingCalorieTrackingState(userId: String, timeRange: TimeRange): CalorieTrackingState {
    return CalorieTrackingState(
        calorieSummary = AsyncData.Loading(),
        dailyCalories = AsyncData.Loading(),
        weeklyTrend = AsyncData.Loading(),
        currentTimeRange = timeRange,
        userId = userId,
        lastRefreshTimestamp = System.currentTimeMillis()
    )
}

/**
 * Creates a CalorieTrackingState for unauthenticated user.
 * 
 * @return CalorieTrackingState with no user context
 */
fun createUnauthenticatedCalorieTrackingState(): CalorieTrackingState {
    return CalorieTrackingState(
        calorieSummary = AsyncData.NotAsked,
        dailyCalories = AsyncData.NotAsked,
        weeklyTrend = AsyncData.NotAsked,
        currentTimeRange = TimeRange.lastMonth(),
        userId = null,
        lastRefreshTimestamp = 0L
    )
}

/**
 * Creates a CalorieTrackingState with error state for all data types.
 * 
 * @param error The error to set for all data types
 * @param userId User identifier for data scoping
 * @param timeRange Time range for data retrieval
 * @return CalorieTrackingState with error states
 */
fun createErrorCalorieTrackingState(
    error: com.example.liftrix.domain.model.error.LiftrixError,
    userId: String,
    timeRange: TimeRange
): CalorieTrackingState {
    return CalorieTrackingState(
        calorieSummary = AsyncData.Failure(error),
        dailyCalories = AsyncData.Failure(error),
        weeklyTrend = AsyncData.Failure(error),
        currentTimeRange = timeRange,
        userId = userId,
        lastRefreshTimestamp = System.currentTimeMillis()
    )
}