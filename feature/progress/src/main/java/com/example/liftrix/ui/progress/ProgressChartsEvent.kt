package com.example.liftrix.ui.progress

import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.ui.progress.components.ChartType

/**
 * Sealed class representing all possible events that can be triggered in the ProgressChartsViewModel.
 * 
 * This event hierarchy follows the MVI pattern and enables type-safe event handling for
 * progress chart interactions including time period changes, data refresh operations,
 * and chart-specific actions.
 * 
 * Key Events:
 * - TimePeriodChanged: User selects a new time period for chart data
 * - RefreshChart: User requests refresh of specific chart type
 * - RefreshAll: User requests refresh of all chart data
 * - LoadInitialData: Initial data loading trigger
 * 
 * Integration with UI:
 * - Events are triggered from Compose UI components
 * - ViewModel processes events and updates state accordingly
 * - Reactive state updates trigger UI recomposition
 * 
 * Usage:
 * ```kotlin
 * // In Compose UI
 * onTimePeriodChanged = { period ->
 *     viewModel.handleEvent(ProgressChartsEvent.TimePeriodChanged(period))
 * }
 * 
 * // In ViewModel
 * override fun handleEvent(event: ProgressChartsEvent) {
 *     when (event) {
 *         is ProgressChartsEvent.TimePeriodChanged -> changeTimePeriod(event.timeRange)
 *         is ProgressChartsEvent.RefreshChart -> refreshChart(event.chartType)
 *         is ProgressChartsEvent.RefreshAll -> refreshAllCharts()
 *         is ProgressChartsEvent.LoadInitialData -> loadInitialData()
 *     }
 * }
 * ```
 */
sealed class ProgressChartsEvent : ViewModelEvent {
    
    /**
     * Event triggered when the user changes the time period for chart data.
     * 
     * This event causes all charts to reload their data for the specified time range.
     * The ViewModel will update the current time period and trigger data refresh
     * for all chart types (volume, duration, frequency).
     * 
     * @param timeRange The new time range for chart data (e.g., WEEK, MONTH, QUARTER, YEAR)
     */
    data class TimePeriodChanged(val timeRange: TimeRange) : ProgressChartsEvent()
    
    /**
     * Event triggered when the user requests refresh of a specific chart type.
     * 
     * This event refreshes only the specified chart type while preserving
     * other chart data and the current time period selection.
     * 
     * @param chartType The type of chart to refresh (VOLUME, DURATION, FREQUENCY)
     */
    data class RefreshChart(val chartType: ChartType) : ProgressChartsEvent()
    
    /**
     * Event triggered when the user requests refresh of all chart data.
     * 
     * This event refreshes all chart types (volume, duration, frequency) with
     * the current time period selection. Typically triggered by pull-to-refresh
     * gesture or explicit refresh button.
     */
    object RefreshAll : ProgressChartsEvent()
    
    /**
     * Event triggered to load initial chart data when the screen is first displayed.
     * 
     * This event is typically called from the ViewModel's initialization block
     * or when the screen becomes visible for the first time.
     */
    object LoadInitialData : ProgressChartsEvent()
    
    /**
     * Event triggered to clear error states for charts.
     * 
     * This event clears error states and returns charts to normal operation mode.
     * Provides user control over error message visibility and recovery.
     */
    object ClearError : ProgressChartsEvent()
}

/**
 * Extension functions for ProgressChartsEvent validation and processing.
 */

/**
 * Checks if the event requires data loading operations.
 * 
 * @return true if the event triggers data loading, false otherwise
 */
fun ProgressChartsEvent.requiresDataLoading(): Boolean = when (this) {
    is ProgressChartsEvent.TimePeriodChanged,
    is ProgressChartsEvent.RefreshChart,
    is ProgressChartsEvent.RefreshAll,
    is ProgressChartsEvent.LoadInitialData -> true
    is ProgressChartsEvent.ClearError -> false
}

/**
 * Checks if the event affects all chart types.
 * 
 * @return true if the event affects all charts, false if it affects only specific charts
 */
fun ProgressChartsEvent.affectsAllCharts(): Boolean = when (this) {
    is ProgressChartsEvent.TimePeriodChanged,
    is ProgressChartsEvent.RefreshAll,
    is ProgressChartsEvent.LoadInitialData -> true
    is ProgressChartsEvent.RefreshChart,
    is ProgressChartsEvent.ClearError -> false
}

/**
 * Gets the chart type affected by this event, if applicable.
 * 
 * @return The chart type if the event is chart-specific, null otherwise
 */
fun ProgressChartsEvent.getAffectedChartType(): ChartType? = when (this) {
    is ProgressChartsEvent.RefreshChart -> chartType
    else -> null
}