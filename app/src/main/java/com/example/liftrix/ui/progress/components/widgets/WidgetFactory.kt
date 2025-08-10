package com.example.liftrix.ui.progress.components.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.domain.model.analytics.WidgetCategory
import com.example.liftrix.domain.model.analytics.MetricWidgetData
import com.example.liftrix.domain.model.analytics.ChartWidgetData
import com.example.liftrix.domain.model.analytics.ProgressWidgetData
import com.example.liftrix.domain.model.analytics.AnalyticsWidgetData

/**
 * Factory for creating dynamic widget instances based on widget type.
 * 
 * Provides type-safe widget creation with proper data mapping and error handling.
 * All widget components are instantiated through this factory for consistency.
 * 
 * Features:
 * - Dynamic widget creation based on AnalyticsWidget type
 * - Type-safe data casting with error handling
 * - Consistent widget interface across all implementations
 * - Support for loading and error states
 * - Responsive sizing and layout adaptation
 */
object WidgetFactory {
    
    /**
     * Creates a widget composable based on the widget type and data.
     * 
     * @param widget The widget type to create
     * @param data Widget data for the composable
     * @param modifier Modifier for the widget
     * @param onRefresh Callback for refresh actions
     * @param onClick Callback for widget clicks
     * @return Composable widget or error widget if type not supported
     */
    @Composable
    fun CreateWidget(
        widget: AnalyticsWidget,
        data: WidgetData?,
        modifier: Modifier = Modifier,
        onRefresh: () -> Unit = {},
        onClick: () -> Unit = {}
    ) {
        when (widget) {
            // REMOVED WIDGETS - Now shown in Progress Summary only:
            // - WorkoutFrequency
            // - TotalVolume
            // - WorkoutStreak
            // - AverageDuration
            // - VolumeCalendar
            
            // CHARTS Category Widgets
            AnalyticsWidget.VolumeChart -> {
                VolumeChartWidget(
                    data = data as? ChartWidgetData,
                    onRefresh = onRefresh,
                    onClick = onClick,
                    modifier = modifier
                )
            }
            
            // DurationChart renamed to ProgressChart
            
            AnalyticsWidget.FrequencyChart -> {
                FrequencyChartWidget(
                    data = data as? ChartWidgetData,
                    onRefresh = onRefresh,
                    onClick = onClick,
                    modifier = modifier
                )
            }
            
            AnalyticsWidget.ProgressChart -> {
                ProgressChartWidget(
                    data = data as? ChartWidgetData,
                    onRefresh = onRefresh,
                    onClick = onClick,
                    modifier = modifier
                )
            }
            
            // WeeklyCalorieTrend widget removed - use VolumeTrends
            
            // PROGRESS Category Widgets
            AnalyticsWidget.StrengthProgress -> {
                StrengthProgressWidget(
                    data = data as? ProgressWidgetData,
                    onRefresh = onRefresh,
                    onClick = onClick,
                    modifier = modifier
                )
            }
            
            AnalyticsWidget.PersonalRecords -> {
                PersonalRecordsWidget(
                    data = data as? ProgressWidgetData,
                    onRefresh = onRefresh,
                    onClick = onClick,
                    modifier = modifier
                )
            }
            
            AnalyticsWidget.VolumeLoadProgression -> {
                VolumeLoadProgressionWidget(
                    data = data as? ProgressWidgetData,
                    onRefresh = onRefresh,
                    onClick = onClick,
                    modifier = modifier
                )
            }
            
            AnalyticsWidget.OneRMProgression -> {
                OneRMProgressionWidget(
                    data = data as? ProgressWidgetData,
                    onRefresh = onRefresh,
                    onClick = onClick,
                    modifier = modifier
                )
            }
            
            // GoalAchievement widget removed
            
            AnalyticsWidget.MonthlySummary -> {
                MonthlySummaryWidget(
                    data = data as? ProgressWidgetData,
                    onRefresh = onRefresh,
                    onClick = onClick,
                    modifier = modifier
                )
            }
            
            // ANALYTICS Category Widgets
            AnalyticsWidget.VolumeTrends -> {
                VolumeTrendsWidget(
                    data = data as? AnalyticsWidgetData,
                    onRefresh = onRefresh,
                    onClick = onClick,
                    modifier = modifier
                )
            }
            
            AnalyticsWidget.RecoveryMetrics -> {
                RecoveryMetricsWidget(
                    data = data as? AnalyticsWidgetData,
                    onRefresh = onRefresh,
                    onClick = onClick,
                    modifier = modifier
                )
            }
            
            // PerformanceAnalysis renamed to MonthlySummary
            
            // WeeklyTrends renamed to VolumeTrends
            
            AnalyticsWidget.MuscleGroupDistribution -> {
                MuscleGroupDistributionWidget(
                    data = data as? AnalyticsWidgetData,
                    onRefresh = onRefresh,
                    onClick = onClick,
                    modifier = modifier
                )
            }
            
            // RecoveryPatterns renamed to RecoveryMetrics
            
            // TrainingIntensity widget removed
            
            // ExerciseVariety widget removed
            
            // TimeOfDayAnalysis widget removed
            
            // Handle hidden/deprecated widgets - show empty state
            else -> {
                // These widgets are hidden from UI but kept for compatibility
                // Show nothing or empty state
            }
        }
    }
    
    /**
     * Creates a compact version of the widget for smaller layouts
     */
    @Composable
    fun CreateCompactWidget(
        widget: AnalyticsWidget,
        data: WidgetData?,
        modifier: Modifier = Modifier,
        onRefresh: () -> Unit = {},
        onClick: () -> Unit = {}
    ) {
        when (widget.category) {
            WidgetCategory.METRICS -> {
                CompactMetricWidget(
                    widget = widget,
                    data = data as? MetricWidgetData,
                    onRefresh = onRefresh,
                    onClick = onClick,
                    modifier = modifier
                )
            }
            
            WidgetCategory.CHARTS -> {
                CompactChartWidget(
                    widget = widget,
                    data = data as? ChartWidgetData,
                    onRefresh = onRefresh,
                    onClick = onClick,
                    modifier = modifier
                )
            }
            
            WidgetCategory.PROGRESS -> {
                CompactProgressWidget(
                    widget = widget,
                    data = data as? ProgressWidgetData,
                    onRefresh = onRefresh,
                    onClick = onClick,
                    modifier = modifier
                )
            }
            
            WidgetCategory.ANALYTICS -> {
                CompactAnalyticsWidget(
                    widget = widget,
                    data = data as? AnalyticsWidgetData,
                    onRefresh = onRefresh,
                    onClick = onClick,
                    modifier = modifier
                )
            }
        }
    }
    
    /**
     * Gets the appropriate data type for a widget
     */
    fun getExpectedDataType(widget: AnalyticsWidget): Class<out WidgetData> {
        return when (widget.category) {
            WidgetCategory.METRICS -> MetricWidgetData::class.java
            WidgetCategory.CHARTS -> ChartWidgetData::class.java
            WidgetCategory.PROGRESS -> ProgressWidgetData::class.java
            WidgetCategory.ANALYTICS -> AnalyticsWidgetData::class.java
        }
    }
    
    /**
     * Validates if the data type matches the widget requirements
     */
    fun isValidDataType(widget: AnalyticsWidget, data: WidgetData): Boolean {
        return when (widget.category) {
            WidgetCategory.METRICS -> data is MetricWidgetData
            WidgetCategory.CHARTS -> data is ChartWidgetData
            WidgetCategory.PROGRESS -> data is ProgressWidgetData
            WidgetCategory.ANALYTICS -> data is AnalyticsWidgetData
        }
    }
    
    /**
     * Creates loading data for a widget type - Clean zero-state display (FR-004)
     */
    fun createLoadingData(widget: AnalyticsWidget): WidgetData {
        return when (widget.category) {
            WidgetCategory.METRICS -> MetricWidgetData.loading(widget)
            WidgetCategory.CHARTS -> ChartWidgetData.loading(widget)
            WidgetCategory.PROGRESS -> ProgressWidgetData(
                widgetType = widget,
                lastUpdated = kotlinx.datetime.Clock.System.now(),
                currentValue = 0f,
                targetValue = 1f,
                progressPercentage = 0f,
                unit = getCleanUnitForWidget(widget),
                milestones = emptyList(),
                isLoading = true
            )
            WidgetCategory.ANALYTICS -> AnalyticsWidgetData(
                widgetType = widget,
                lastUpdated = kotlinx.datetime.Clock.System.now(),
                insights = emptyList(),
                recommendations = emptyList(),
                metrics = emptyMap(),
                confidence = 0f,
                timeRange = "",
                isLoading = true
            )
        }
    }
    
    /**
     * Gets clean unit for widget - consistent with FR-004 clean zero-state display
     */
    private fun getCleanUnitForWidget(widget: AnalyticsWidget): String {
        return when (widget) {
            // Hidden widgets (for compatibility)
            AnalyticsWidget.TotalVolume -> "kg"
            AnalyticsWidget.WorkoutStreak -> "days"
            AnalyticsWidget.AverageDuration -> "min"
            AnalyticsWidget.WorkoutFrequency -> "workouts"
            
            // Visible widgets
            AnalyticsWidget.StrengthProgress -> "kg"
            AnalyticsWidget.PersonalRecords -> "records"
            AnalyticsWidget.OneRMProgression -> "kg"
            AnalyticsWidget.MuscleGroupDistribution -> "%"
            AnalyticsWidget.VolumeLoadProgression -> "kg"
            AnalyticsWidget.RecoveryMetrics -> "h"
            AnalyticsWidget.VolumeTrends -> "kg"
            AnalyticsWidget.MonthlySummary -> ""
            else -> ""
        }
    }
    
    /**
     * Creates error data for a widget type
     */
    fun createErrorData(widget: AnalyticsWidget, error: com.example.liftrix.domain.model.analytics.WidgetError): WidgetData {
        return when (widget.category) {
            WidgetCategory.METRICS -> MetricWidgetData.error(widget, error)
            WidgetCategory.CHARTS -> ChartWidgetData(
                widgetType = widget,
                lastUpdated = kotlinx.datetime.Clock.System.now(),
                chartType = com.example.liftrix.domain.model.analytics.ChartType.LINE,
                dataPoints = emptyList(),
                xAxisLabel = "",
                yAxisLabel = "",
                timeRange = "",
                summary = com.example.liftrix.domain.model.analytics.ChartSummary.empty(),
                error = error
            )
            WidgetCategory.PROGRESS -> ProgressWidgetData(
                widgetType = widget,
                lastUpdated = kotlinx.datetime.Clock.System.now(),
                currentValue = 0f,
                targetValue = 1f,
                progressPercentage = 0f,
                unit = "",
                milestones = emptyList(),
                error = error
            )
            WidgetCategory.ANALYTICS -> AnalyticsWidgetData(
                widgetType = widget,
                lastUpdated = kotlinx.datetime.Clock.System.now(),
                insights = emptyList(),
                recommendations = emptyList(),
                metrics = emptyMap(),
                confidence = 0f,
                timeRange = "",
                error = error
            )
        }
    }
}