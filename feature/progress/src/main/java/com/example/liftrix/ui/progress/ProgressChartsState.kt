package com.example.liftrix.ui.progress

import androidx.compose.runtime.Stable
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.analytics.VolumeCalendarData
import com.example.liftrix.domain.repository.VolumeDataPoint
import com.example.liftrix.domain.repository.DurationDataPoint
import com.example.liftrix.domain.repository.FrequencyDataPoint
import com.example.liftrix.ui.common.state.AsyncData
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.state.isLoading
import com.example.liftrix.ui.common.state.isSuccess
import com.example.liftrix.ui.common.state.isFailure
import com.example.liftrix.ui.common.state.isNotAsked

/**
 * Data class representing the complete state of the ProgressChartsViewModel.
 * 
 * This state follows the MVI pattern and contains all data necessary for rendering
 * the progress charts screen, including chart data, time period selection, and
 * loading/error states for individual chart types.
 * 
 * Key Features:
 * - AsyncData pattern for individual chart data with independent loading states
 * - Time period selection state with persistence
 * - User information for scoped data access
 * - Timestamp tracking for data freshness
 * - Comprehensive state checking utilities
 * - Functional programming patterns for state transformations
 * 
 * State Design:
 * - Each chart type (volume, duration, frequency) has independent AsyncData state
 * - Time period selection is separate from chart data state
 * - User authentication state is tracked for data scoping
 * - Last refresh timestamp enables cache invalidation strategies
 * 
 * Usage:
 * ```kotlin
 * // In ViewModel
 * private val _uiState = MutableStateFlow(UiState.Loading<ProgressChartsState>())
 * 
 * // Update specific chart data
 * updateState { currentState ->
 *     currentState.copy(
 *         volumeChart = AsyncData.Success(newVolumeData)
 *     )
 * }
 * 
 * // In Compose UI
 * when (val chartState = state.volumeChart) {
 *     is AsyncData.Loading -> CircularProgressIndicator()
 *     is AsyncData.Success -> VolumeChart(chartState.data)
 *     is AsyncData.Failure -> ErrorMessage(chartState.error)
 * }
 * ```
 * 
 * @param volumeChart AsyncData state for workout volume chart data
 * @param durationChart AsyncData state for workout duration chart data
 * @param frequencyChart AsyncData state for workout frequency chart data
 * @param currentTimeRange The currently selected time range for chart data
 * @param userId The current user ID for data scoping (null if not authenticated)
 * @param lastRefreshTimestamp Timestamp of the last data refresh operation
 */
@Stable
data class ProgressChartsState(
    val volumeChart: AsyncData<List<VolumeDataPoint>> = AsyncData.NotAsked,
    val durationChart: AsyncData<List<DurationDataPoint>> = AsyncData.NotAsked,
    val frequencyChart: AsyncData<List<FrequencyDataPoint>> = AsyncData.NotAsked,
    val volumeCalendar: AsyncData<VolumeCalendarData> = AsyncData.NotAsked,
    val currentTimeRange: TimeRange = TimeRange.lastMonth(),
    val userId: String? = null,
    val lastRefreshTimestamp: Long = 0L
)

/**
 * State checking utility methods for ProgressChartsState.
 * These methods provide convenient ways to check the overall state and individual chart states.
 */

/**
 * Checks if any chart is currently loading data.
 * 
 * @return true if any chart is in loading state, false otherwise
 */
fun ProgressChartsState.isAnyChartLoading(): Boolean = 
    volumeChart.isLoading() || durationChart.isLoading() || frequencyChart.isLoading() || volumeCalendar.isLoading()

/**
 * Checks if all charts have successfully loaded data.
 * 
 * @return true if all charts have data, false otherwise
 */
fun ProgressChartsState.areAllChartsLoaded(): Boolean = 
    volumeChart.isSuccess() && durationChart.isSuccess() && frequencyChart.isSuccess() && volumeCalendar.isSuccess()

/**
 * Checks if any chart has failed to load data.
 * 
 * @return true if any chart has an error, false otherwise
 */
fun ProgressChartsState.hasAnyChartError(): Boolean = 
    volumeChart.isFailure() || durationChart.isFailure() || frequencyChart.isFailure() || volumeCalendar.isFailure()

/**
 * Checks if all charts are in not asked state (initial state).
 * 
 * @return true if all charts are in NotAsked state, false otherwise
 */
fun ProgressChartsState.areAllChartsNotAsked(): Boolean = 
    volumeChart.isNotAsked() && durationChart.isNotAsked() && frequencyChart.isNotAsked() && volumeCalendar.isNotAsked()

/**
 * Checks if the state has a valid user for data operations.
 * 
 * @return true if userId is not null, false otherwise
 */
fun ProgressChartsState.hasValidUser(): Boolean = userId != null

/**
 * Checks if we're waiting for user authentication before we can load charts.
 * 
 * @return true if no userId and charts haven't started loading, false otherwise
 */
fun ProgressChartsState.isWaitingForAuth(): Boolean = 
    userId == null && areAllChartsNotAsked()

/**
 * Checks if we're actively loading chart data (user is authenticated).
 * 
 * @return true if user is authenticated and charts are loading, false otherwise
 */
fun ProgressChartsState.isLoadingChartData(): Boolean = 
    userId != null && isAnyChartLoading()

/**
 * Gets the display name for the current time range.
 * 
 * @return String representation of the current time range
 */
fun ProgressChartsState.getTimeRangeDisplayName(): String = currentTimeRange.type.displayName

/**
 * Gets the short display name for the current time range.
 * 
 * @return Short string representation of the current time range (e.g., "1M", "3M")
 */
fun ProgressChartsState.getTimeRangeShortName(): String = currentTimeRange.type.getShortDisplayName()

/**
 * Checks if the current time range is suitable for real-time updates.
 * 
 * @return true if the time range supports real-time updates, false otherwise
 */
fun ProgressChartsState.isRealTimeCompatible(): Boolean = currentTimeRange.type.isRealTimeCompatible()

/**
 * Gets the recommended update frequency for the current time range.
 * 
 * @return Update frequency in minutes
 */
fun ProgressChartsState.getUpdateFrequencyMinutes(): Int = currentTimeRange.type.getUpdateFrequencyMinutes()

/**
 * Checks if the chart data is fresh based on the current time range update frequency.
 * 
 * @return true if data is fresh, false if it should be refreshed
 */
fun ProgressChartsState.isDataFresh(): Boolean {
    if (lastRefreshTimestamp == 0L) return false
    val updateFrequencyMs = getUpdateFrequencyMinutes() * 60 * 1000L
    return (System.currentTimeMillis() - lastRefreshTimestamp) < updateFrequencyMs
}

/**
 * Gets the age of the data since last refresh in minutes.
 * 
 * @return Age in minutes, or 0 if never refreshed
 */
fun ProgressChartsState.getDataAgeMinutes(): Long {
    if (lastRefreshTimestamp == 0L) return 0L
    return (System.currentTimeMillis() - lastRefreshTimestamp) / (60 * 1000L)
}

/**
 * Factory methods for creating ProgressChartsState instances.
 */

/**
 * Creates an initial loading state for all charts.
 * 
 * @param userId The user ID for data scoping
 * @param timeRange The initial time range selection
 * @return ProgressChartsState with all charts in loading state
 */
fun createLoadingChartsState(userId: String, timeRange: TimeRange = TimeRange.lastMonth()): ProgressChartsState =
    ProgressChartsState(
        volumeChart = AsyncData.Loading(),
        durationChart = AsyncData.Loading(),
        frequencyChart = AsyncData.Loading(),
        volumeCalendar = AsyncData.Loading(),
        currentTimeRange = timeRange,
        userId = userId,
        lastRefreshTimestamp = System.currentTimeMillis()
    )

/**
 * Creates an initial state for unauthenticated users.
 * 
 * @return ProgressChartsState with all charts in not asked state
 */
fun createUnauthenticatedChartsState(): ProgressChartsState =
    ProgressChartsState(
        volumeChart = AsyncData.NotAsked,
        durationChart = AsyncData.NotAsked,
        frequencyChart = AsyncData.NotAsked,
        currentTimeRange = TimeRange.lastMonth(),
        userId = null,
        lastRefreshTimestamp = 0L
    )

/**
 * Extension functions for functional state transformations.
 */

/**
 * Updates the volume chart data while preserving other state.
 * 
 * @param volumeData The new volume chart data
 * @return New state with updated volume chart
 */
fun ProgressChartsState.withVolumeChart(volumeData: AsyncData<List<VolumeDataPoint>>): ProgressChartsState =
    copy(volumeChart = volumeData)

/**
 * Updates the duration chart data while preserving other state.
 * 
 * @param durationData The new duration chart data
 * @return New state with updated duration chart
 */
fun ProgressChartsState.withDurationChart(durationData: AsyncData<List<DurationDataPoint>>): ProgressChartsState =
    copy(durationChart = durationData)

/**
 * Updates the frequency chart data while preserving other state.
 * 
 * @param frequencyData The new frequency chart data
 * @return New state with updated frequency chart
 */
fun ProgressChartsState.withFrequencyChart(frequencyData: AsyncData<List<FrequencyDataPoint>>): ProgressChartsState =
    copy(frequencyChart = frequencyData)

/**
 * Updates the current time range and resets all charts to not asked state.
 * 
 * @param timeRange The new time range selection
 * @return New state with updated time range and reset chart states
 */
fun ProgressChartsState.withTimeRange(timeRange: TimeRange): ProgressChartsState =
    copy(
        currentTimeRange = timeRange,
        volumeChart = AsyncData.NotAsked,
        durationChart = AsyncData.NotAsked,
        frequencyChart = AsyncData.NotAsked,
        lastRefreshTimestamp = 0L
    )

/**
 * Updates the user ID and resets all charts to not asked state.
 * 
 * @param userId The new user ID (null for unauthenticated)
 * @return New state with updated user ID and reset chart states
 */
fun ProgressChartsState.withUserId(userId: String?): ProgressChartsState =
    copy(
        userId = userId,
        volumeChart = if (userId != null) AsyncData.NotAsked else AsyncData.NotAsked,
        durationChart = if (userId != null) AsyncData.NotAsked else AsyncData.NotAsked,
        frequencyChart = if (userId != null) AsyncData.NotAsked else AsyncData.NotAsked,
        lastRefreshTimestamp = 0L
    )

/**
 * Updates the last refresh timestamp.
 * 
 * @param timestamp The new timestamp (defaults to current time)
 * @return New state with updated timestamp
 */
fun ProgressChartsState.withRefreshTimestamp(timestamp: Long = System.currentTimeMillis()): ProgressChartsState =
    copy(lastRefreshTimestamp = timestamp)