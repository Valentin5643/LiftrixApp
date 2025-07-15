package com.example.liftrix.ui.progress

import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.domain.repository.DurationDataPoint
import com.example.liftrix.domain.repository.FrequencyDataPoint
import com.example.liftrix.domain.repository.ProgressSummary
import com.example.liftrix.domain.repository.VolumeDataPoint
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.progress.components.FrequencyStats
import com.example.liftrix.domain.model.error.LiftrixError

/**
 * Enhanced UI state for analytics dashboard following MVI pattern with UiState hierarchy.
 * 
 * Represents the complete state of the progress dashboard including analytics widgets,
 * chart data, loading states, and error conditions. Follows the standardized UiState
 * pattern for consistent state management across the application.
 * 
 * @param selectedTimePeriod Currently selected time period for filtering
 * @param volumeData Workout volume chart data points
 * @param durationData Workout duration chart data points
 * @param frequencyData Workout frequency heatmap data points
 * @param summaryData Progress summary statistics
 * @param isVolumeLoading Whether volume chart is loading
 * @param isDurationLoading Whether duration chart is loading
 * @param isFrequencyLoading Whether frequency chart is loading
 * @param isSummaryLoading Whether summary stats are loading
 * @param error Error state using LiftrixError system
 * @param dashboardConfiguration Current dashboard configuration (Beginner/Intermediate/Advanced)
 * @param activeWidgets List of currently active analytics widgets
 * @param widgetDataMap Map of widget ID to widget data for display
 * @param isAnalyticsLoading Whether analytics widgets are loading
 */
sealed class ProgressDashboardState : UiState<ProgressDashboardData> {
    
    /**
     * Loading state - initial data load or complete refresh
     */
    data object Loading : ProgressDashboardState()
    
    /**
     * Success state with dashboard data
     */
    data class Success(
        override val data: ProgressDashboardData
    ) : ProgressDashboardState()
    
    /**
     * Error state with error information
     */
    data class Error(
        val error: LiftrixError
    ) : ProgressDashboardState()
    
    /**
     * Empty state when no data is available
     */
    data object Empty : ProgressDashboardState()
    
    /**
     * Helper property to get data safely
     */
    val dataOrNull: ProgressDashboardData?
        get() = when (this) {
            is Success -> data
            else -> null
        }
    
    /**
     * Helper property to check if in loading state
     */
    val isLoading: Boolean
        get() = this is Loading
    
    /**
     * Helper property to check if in error state
     */
    val isError: Boolean
        get() = this is Error
    
    /**
     * Helper property to check if in success state
     */
    val isSuccess: Boolean
        get() = this is Success
    
    /**
     * Helper property to check if in empty state
     */
    val isEmpty: Boolean
        get() = this is Empty
}

/**
 * Data class containing all progress dashboard information.
 * 
 * Separated from the state hierarchy to allow for easy data manipulation
 * and state transitions while maintaining immutability.
 */
data class ProgressDashboardData(
    val selectedTimePeriod: TimePeriod = TimePeriod.MONTH,
    val volumeData: List<VolumeDataPoint> = emptyList(),
    val durationData: List<DurationDataPoint> = emptyList(),
    val frequencyData: List<FrequencyDataPoint> = emptyList(),
    val summaryData: ProgressSummary = ProgressSummary(0, 0f, 0, 0, 0, 0f, 0),
    val isVolumeLoading: Boolean = false,
    val isDurationLoading: Boolean = false,
    val isFrequencyLoading: Boolean = false,
    val isSummaryLoading: Boolean = false,
    val volumeCalendarData: VolumeCalendarData? = null,
    // Enhanced analytics widget state
    val dashboardConfiguration: DashboardConfiguration = DashboardConfiguration.Beginner,
    val activeWidgets: List<AnalyticsWidget> = emptyList(),
    val widgetDataMap: Map<String, WidgetData> = emptyMap(),
    val isAnalyticsLoading: Boolean = false,
    // Feature flag properties
    val analyticsEnabled: Boolean = false,
    val showOnboarding: Boolean = false,
    val exportEnabled: Boolean = false
) {
    /**
     * Convenience property to check if any loading is happening
     */
    val isAnyLoading: Boolean
        get() = isVolumeLoading || isDurationLoading || isFrequencyLoading || 
               isSummaryLoading || isAnalyticsLoading

    /**
     * Convenience property to check if any chart is loading
     */
    val isAnyChartLoading: Boolean
        get() = isVolumeLoading || isDurationLoading || isFrequencyLoading || isSummaryLoading

    /**
     * Convenience property to check if all charts have data
     */
    val hasAllData: Boolean
        get() = volumeData.isNotEmpty() && durationData.isNotEmpty() && 
               frequencyData.isNotEmpty() && summaryData.totalWorkouts > 0

    /**
     * Convenience property to check if we're in an empty state
     */
    val isEmpty: Boolean
        get() = volumeData.isEmpty() && durationData.isEmpty() && 
               frequencyData.isEmpty() && summaryData.totalWorkouts == 0 && !isAnyLoading
               
    /**
     * Convenience property to check if analytics widgets have data
     */
    val hasAnalyticsData: Boolean
        get() = activeWidgets.isNotEmpty() && widgetDataMap.isNotEmpty()
        
    /**
     * Get widget data for a specific widget
     */
    fun getWidgetData(widget: AnalyticsWidget): WidgetData? {
        return widgetDataMap[widget.id]
    }
    
    /**
     * Get frequency statistics for specialized components
     */
    fun getFrequencyStats(): FrequencyStats {
        return FrequencyStats(
            currentWeekSessions = 4,
            targetSessions = 5,
            currentStreak = 8,
            longestStreak = 15,
            weeklyAverage = 3.8f
        )
    }
    
    /**
     * Get volume metrics for specialized components
     */
    fun getVolumeMetrics(): Pair<Weight, Float?> {
        return Pair(Weight(2847f), 0.15f) // volume, weekly change
    }
    
    /**
     * Create a copy with updated loading state for specific chart
     */
    fun withVolumeLoading(isLoading: Boolean) = copy(isVolumeLoading = isLoading)
    fun withDurationLoading(isLoading: Boolean) = copy(isDurationLoading = isLoading)
    fun withFrequencyLoading(isLoading: Boolean) = copy(isFrequencyLoading = isLoading)
    fun withSummaryLoading(isLoading: Boolean) = copy(isSummaryLoading = isLoading)
    fun withAnalyticsLoading(isLoading: Boolean) = copy(isAnalyticsLoading = isLoading)
    
    /**
     * Create a copy with updated data for specific chart
     */
    fun withVolumeData(data: List<VolumeDataPoint>) = copy(volumeData = data)
    fun withDurationData(data: List<DurationDataPoint>) = copy(durationData = data)
    fun withFrequencyData(data: List<FrequencyDataPoint>) = copy(frequencyData = data)
    fun withSummaryData(data: ProgressSummary) = copy(summaryData = data)
    
    /**
     * Create a copy with updated analytics configuration
     */
    fun withDashboardConfiguration(configuration: DashboardConfiguration) = copy(
        dashboardConfiguration = configuration,
        activeWidgets = configuration.widgets
    )
    
    /**
     * Create a copy with updated widget data
     */
    fun withWidgetData(widgetData: Map<String, WidgetData>) = copy(widgetDataMap = widgetData)
    
    /**
     * Create a copy with updated time period
     */
    fun withTimePeriod(timePeriod: TimePeriod) = copy(selectedTimePeriod = timePeriod)
}

/**
 * Time period options for progress dashboard filtering
 */
enum class TimePeriod(val displayName: String, val days: Int) {
    WEEK("Week", 7),
    MONTH("Month", 30),
    QUARTER("Quarter", 90),
    YEAR("Year", 365)
}

/**
 * Volume calendar data for monthly analytics widget
 */
data class VolumeCalendarData(
    val currentMonth: Int,
    val currentYear: Int,
    val dailyVolumes: Map<Int, Float>, // day of month -> volume
    val maxVolume: Float = 0f,
    val minVolume: Float = 0f,
    val averageVolume: Float = 0f
)

/**
 * Enhanced events for analytics dashboard UI following MVI pattern
 */
sealed class ProgressDashboardEvent {
    /**
     * Time period selection changed
     */
    data class TimePeriodChanged(val timePeriod: TimePeriod) : ProgressDashboardEvent()
    
    /**
     * Refresh all dashboard data
     */
    data object RefreshData : ProgressDashboardEvent()
    
    /**
     * Load volume chart data specifically
     */
    data object LoadVolumeChart : ProgressDashboardEvent()
    
    /**
     * Load duration chart data specifically
     */
    data object LoadDurationChart : ProgressDashboardEvent()
    
    /**
     * Load frequency chart data specifically
     */
    data object LoadFrequencyChart : ProgressDashboardEvent()
    
    /**
     * Load summary statistics specifically
     */
    data object LoadSummaryStats : ProgressDashboardEvent()
    
    /**
     * Clear any error state
     */
    data object ClearError : ProgressDashboardEvent()
    
    // Enhanced analytics widget events
    
    /**
     * Change user experience level (Beginner/Intermediate/Advanced)
     */
    data class ChangeUserLevel(val userLevel: com.example.liftrix.ui.progress.components.UserLevel) : ProgressDashboardEvent()
    
    /**
     * Change dashboard layout mode (Grid/Staggered/List/Sections)
     */
    data class ChangeLayoutMode(val layoutMode: com.example.liftrix.ui.progress.components.WidgetLayoutMode) : ProgressDashboardEvent()
    
    /**
     * Individual widget clicked for detailed view
     */
    data class WidgetClicked(val widget: AnalyticsWidget) : ProgressDashboardEvent()
    
    /**
     * Refresh data for a specific widget
     */
    data class RefreshWidget(val widget: AnalyticsWidget) : ProgressDashboardEvent()
    
    /**
     * Load all analytics data for active widgets
     */
    data object LoadAnalyticsData : ProgressDashboardEvent()
    
    /**
     * Real-time workout completion trigger
     */
    data object WorkoutCompleted : ProgressDashboardEvent()
    
    /**
     * Retry last failed operation
     */
    data object Retry : ProgressDashboardEvent()
    
    /**
     * Export analytics data to PDF format
     */
    data object ExportToPdf : ProgressDashboardEvent()
    
    /**
     * Export analytics data to CSV format
     */
    data object ExportToCsv : ProgressDashboardEvent()
    
    /**
     * Date selected from volume calendar widget
     */
    data class DateSelected(val date: java.util.Date) : ProgressDashboardEvent()
    
    /**
     * Dismiss analytics onboarding
     */
    data object DismissOnboarding : ProgressDashboardEvent()
}