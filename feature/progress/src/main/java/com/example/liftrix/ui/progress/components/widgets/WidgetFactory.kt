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
import com.example.liftrix.domain.model.analytics.MuscleHeatmapWidgetData
import com.example.liftrix.ui.common.extensions.getWeightUnitSymbolFromPreferences
import com.example.liftrix.ui.progress.components.widgets.heatmap.MuscleHeatmapWidget

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
            
            // CONSOLIDATED WIDGETS - Primary consolidated widgets
            AnalyticsWidget.StrengthAnalytics -> {
                StrengthAnalyticsWidget(
                    data = data as? ProgressWidgetData,
                    onRefresh = onRefresh,
                    onClick = onClick,
                    modifier = modifier,
                    useFolderStyle = true
                )
            }
            
            AnalyticsWidget.VolumeAnalytics -> {
                VolumeAnalyticsWidget(
                    data = data as? AnalyticsWidgetData,
                    onRefresh = onRefresh,
                    onClick = onClick,
                    modifier = modifier,
                    useFolderStyle = true
                )
            }
            
            // CHARTS Category Widgets - Remaining non-consolidated widgets
            AnalyticsWidget.FrequencyChart -> {
                FrequencyChartWidget(
                    data = data as? ChartWidgetData,
                    onRefresh = onRefresh,
                    onClick = onClick,
                    modifier = modifier,
                    useFolderStyle = true
                )
            }
            
            AnalyticsWidget.ProgressChart -> {
                ProgressChartWidget(
                    data = data as? ChartWidgetData,
                    onRefresh = onRefresh,
                    onClick = onClick,
                    modifier = modifier,
                    useFolderStyle = true
                )
            }
            
            // PROGRESS Category Widgets - Remaining non-consolidated widgets
            AnalyticsWidget.MonthlySummary -> {
                MonthlySummaryWidget(
                    data = data as? ProgressWidgetData,
                    onRefresh = onRefresh,
                    onClick = onClick,
                    modifier = modifier,
                    useFolderStyle = true
                )
            }
            
            AnalyticsWidget.RecoveryMetrics -> {
                RecoveryMetricsWidget(
                    data = data as? AnalyticsWidgetData,
                    onRefresh = onRefresh,
                    onClick = onClick,
                    modifier = modifier,
                    useFolderStyle = true
                )
            }
            
            // PerformanceAnalysis renamed to MonthlySummary
            
            // WeeklyTrends renamed to VolumeTrends
            
            AnalyticsWidget.MuscleGroupDistribution -> {
                MuscleGroupDistributionWidget(
                    data = data as? AnalyticsWidgetData,
                    onRefresh = onRefresh,
                    onClick = onClick,
                    modifier = modifier,
                    useFolderStyle = true
                )
            }

            AnalyticsWidget.MuscleHeatmap -> {
                val heatmapData = data as? MuscleHeatmapWidgetData
                MuscleHeatmapWidget(
                    data = heatmapData,
                    onClick = onClick,
                    isLoading = heatmapData?.isLoading == true,
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
        if (widget == AnalyticsWidget.MuscleHeatmap) {
            return MuscleHeatmapWidgetData::class.java
        }

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
        if (widget == AnalyticsWidget.MuscleHeatmap) {
            return data is MuscleHeatmapWidgetData
        }

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
        if (widget == AnalyticsWidget.MuscleHeatmap) {
            return MuscleHeatmapWidgetData.empty().copy(isLoading = true)
        }

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
     * Now supports dynamic weight units from coordinator preferences
     */
    private fun getCleanUnitForWidget(
        widget: AnalyticsWidget,
        coordinatorPreferences: Map<String, Any> = emptyMap()
    ): String {
        val weightUnitSymbol = coordinatorPreferences.getWeightUnitSymbolFromPreferences()
        return when (widget) {
            // Hidden widgets (for compatibility)
            AnalyticsWidget.TotalVolume -> weightUnitSymbol
            AnalyticsWidget.WorkoutStreak -> "days"
            AnalyticsWidget.AverageDuration -> "min"
            AnalyticsWidget.WorkoutFrequency -> "workouts"
            
            // Consolidated widgets - primary widgets
            AnalyticsWidget.StrengthAnalytics -> weightUnitSymbol
            AnalyticsWidget.VolumeAnalytics -> weightUnitSymbol
            
            // Other active widgets
            AnalyticsWidget.MuscleGroupDistribution -> "%"
            AnalyticsWidget.MuscleHeatmap -> ""
            AnalyticsWidget.RecoveryMetrics -> "h"
            AnalyticsWidget.MonthlySummary -> ""
            AnalyticsWidget.FrequencyChart -> "workouts"
            AnalyticsWidget.ProgressChart -> ""
            
            // Deprecated widgets (for compatibility)
            AnalyticsWidget.StrengthProgress -> weightUnitSymbol
            AnalyticsWidget.PersonalRecords -> "records"
            AnalyticsWidget.OneRMProgression -> weightUnitSymbol
            AnalyticsWidget.VolumeLoadProgression -> weightUnitSymbol
            AnalyticsWidget.VolumeTrends -> weightUnitSymbol
            AnalyticsWidget.VolumeChart -> weightUnitSymbol
            
            else -> ""
        }
    }
    
    /**
     * Creates error data for a widget type
     */
    fun createErrorData(widget: AnalyticsWidget, error: com.example.liftrix.domain.model.analytics.WidgetError): WidgetData {
        if (widget == AnalyticsWidget.MuscleHeatmap) {
            return MuscleHeatmapWidgetData.empty().copy(error = error)
        }

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
