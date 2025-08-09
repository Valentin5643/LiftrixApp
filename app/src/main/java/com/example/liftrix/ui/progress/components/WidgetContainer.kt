package com.example.liftrix.ui.progress.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import com.example.liftrix.ui.common.WindowSizeClass
import com.example.liftrix.ui.common.rememberWindowSizeClass
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.TrendDirection
import com.example.liftrix.domain.model.analytics.WidgetCategory
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.domain.model.analytics.BasicWidgetData
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.core.extensions.rememberDerivedStateOf
import com.example.liftrix.core.extensions.rememberWidgetDataCache
import com.example.liftrix.ui.accessibility.AccessibilityUtils
import com.example.liftrix.ui.accessibility.TalkBackAnnouncements
import com.example.liftrix.ui.performance.PerformanceTracker
import com.example.liftrix.core.extensions.WidgetPerformanceExt
import com.example.liftrix.BuildConfig

// Import chart types for widget rendering
import com.example.liftrix.ui.progress.components.ProgressChart
import com.example.liftrix.ui.progress.components.ChartData
import com.example.liftrix.ui.progress.components.ChartDataPoint
import com.example.liftrix.ui.progress.components.ChartType
import com.example.liftrix.ui.progress.components.WidgetLayoutMode
import com.example.liftrix.ui.progress.components.widgets.*

// WidgetLayoutMode is now imported from the proper file

/**
 * Container component for organizing analytics dashboard widgets.
 * 
 * Provides flexible layout management with responsive design,
 * collapsible sections, drag-and-drop widget reordering, and 
 * Hevy-inspired organizational patterns. Supports grid, staggered, 
 * list, and sectioned layouts with smooth animations and accessibility compliance.
 * 
 * Enhanced with drag-and-drop functionality for widget reordering with:
 * - Material 3 visual feedback during drag operations
 * - Haptic feedback for drag start, snap-to-grid, and drop operations  
 * - Accessibility support with alternative reorder methods
 * - Performance optimization for 60fps during gesture tracking
 * - Immediate persistence of widget preference changes
 */
@Composable
fun WidgetContainer(
    widgets: List<AnalyticsWidget>,
    configuration: DashboardConfiguration,
    modifier: Modifier = Modifier,
    layoutMode: WidgetLayoutMode = WidgetLayoutMode.SECTIONS,
    onWidgetClick: (AnalyticsWidget) -> Unit = {},
    onWidgetReorder: (from: Int, to: Int) -> Unit = { _, _ -> },
    widgetDataProvider: (AnalyticsWidget) -> WidgetData = { createSampleWidgetData(it) },
    isLoading: Boolean = false,
    enableCollapsibleSections: Boolean = true,
    enableDragAndDrop: Boolean = true,
    windowSizeClass: WindowSizeClass = rememberWindowSizeClass()
) {
    // Performance optimization: memoize expensive calculations
    val columnsCount by rememberDerivedStateOf { 
        windowSizeClass.calculateOptimalColumns(maxColumns = 3)
    }
    
    // Performance optimization: cache widget data to prevent repeated calculations
    val widgetDataCache = rememberWidgetDataCache(widgets, widgetDataProvider)
    
    // Performance tracking for development builds
    PerformanceTracker(
        componentId = "WidgetContainer_${layoutMode.name}",
        enabled = BuildConfig.DEBUG
    )
    
    when (layoutMode) {
        WidgetLayoutMode.GRID -> {
            if (enableDragAndDrop) {
                DragAndDropGrid(
                    widgets = widgets,
                    windowSizeClass = windowSizeClass,
                    onReorder = onWidgetReorder,
                    onWidgetClick = onWidgetClick,
                    widgetDataProvider = widgetDataProvider,
                    isLoading = isLoading,
                    modifier = modifier
                )
            } else {
                GridLayout(
                    widgets = widgets,
                    columnsCount = columnsCount,
                    modifier = modifier,
                    onWidgetClick = onWidgetClick,
                    widgetDataCache = widgetDataCache,
                    isLoading = isLoading,
                    windowSizeClass = windowSizeClass
                )
            }
        }
        
        WidgetLayoutMode.STAGGERED -> {
            if (enableDragAndDrop) {
                // For staggered layout, fall back to grid drag-and-drop for now
                // Future enhancement: implement staggered-specific drag-and-drop
                DragAndDropGrid(
                    widgets = widgets,
                    windowSizeClass = windowSizeClass,
                    onReorder = onWidgetReorder,
                    onWidgetClick = onWidgetClick,
                    widgetDataProvider = widgetDataProvider,
                    isLoading = isLoading,
                    modifier = modifier
                )
            } else {
                StaggeredLayout(
                    widgets = widgets,
                    columnsCount = columnsCount,
                    modifier = modifier,
                    onWidgetClick = onWidgetClick,
                    widgetDataProvider = widgetDataProvider,
                    isLoading = isLoading
                )
            }
        }
        
        WidgetLayoutMode.LIST -> {
            // List layout doesn't support drag-and-drop reordering yet
            // Future enhancement: implement list-specific drag-and-drop
            ListLayout(
                widgets = widgets,
                modifier = modifier,
                onWidgetClick = onWidgetClick,
                widgetDataProvider = widgetDataProvider,
                isLoading = isLoading
            )
        }
        
        WidgetLayoutMode.SECTIONS -> {
            SectionedLayout(
                widgets = widgets,
                configuration = configuration,
                modifier = modifier,
                onWidgetClick = onWidgetClick,
                onWidgetReorder = if (enableDragAndDrop) onWidgetReorder else { _, _ -> },
                widgetDataProvider = widgetDataProvider,
                enableCollapsibleSections = enableCollapsibleSections && windowSizeClass.widthDp.value >= 600,
                enableDragAndDrop = enableDragAndDrop,
                isLoading = isLoading,
                windowSizeClass = windowSizeClass
            )
        }
    }
}

@Composable
private fun GridLayout(
    widgets: List<AnalyticsWidget>,
    columnsCount: Int,
    modifier: Modifier = Modifier,
    onWidgetClick: (AnalyticsWidget) -> Unit,
    widgetDataCache: Map<AnalyticsWidget, WidgetData>,
    isLoading: Boolean,
    windowSizeClass: WindowSizeClass = rememberWindowSizeClass()
) {
    val spacing = when {
        windowSizeClass.widthDp.value < 400 -> 8.dp
        windowSizeClass.widthDp.value < 600 -> 12.dp
        else -> 16.dp
    }
    val contentPadding = PaddingValues(when {
        windowSizeClass.widthDp.value < 400 -> 12.dp
        windowSizeClass.widthDp.value < 600 -> 16.dp
        else -> 20.dp
    })
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(columnsCount),
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        items(widgets.size) { index ->
            val widget = widgets[index]
            val widgetData = widgetDataCache[widget] ?: createSampleWidgetData(widget)
            
            WidgetRenderer(
                widget = widget,
                widgetData = widgetData,
                onClick = { onWidgetClick(widget) },
                isLoading = isLoading
            )
        }
    }
}

@Composable
private fun StaggeredLayout(
    widgets: List<AnalyticsWidget>,
    columnsCount: Int,
    modifier: Modifier = Modifier,
    onWidgetClick: (AnalyticsWidget) -> Unit,
    widgetDataProvider: (AnalyticsWidget) -> WidgetData,
    isLoading: Boolean
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(columnsCount),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp
    ) {
        items(widgets) { widget ->
            val widgetData = widgetDataProvider(widget)
            WidgetRenderer(
                widget = widget,
                widgetData = widgetData,
                onClick = { onWidgetClick(widget) },
                isLoading = isLoading
            )
        }
    }
}

@Composable
private fun ListLayout(
    widgets: List<AnalyticsWidget>,
    modifier: Modifier = Modifier,
    onWidgetClick: (AnalyticsWidget) -> Unit,
    widgetDataProvider: (AnalyticsWidget) -> WidgetData,
    isLoading: Boolean
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        widgets.forEach { widget ->
            val widgetData = widgetDataProvider(widget)
            WidgetRenderer(
                widget = widget,
                widgetData = widgetData,
                onClick = { onWidgetClick(widget) },
                isLoading = isLoading
            )
        }
    }
}

@Composable
private fun SectionedLayout(
    widgets: List<AnalyticsWidget>,
    configuration: DashboardConfiguration,
    modifier: Modifier = Modifier,
    onWidgetClick: (AnalyticsWidget) -> Unit,
    onWidgetReorder: (from: Int, to: Int) -> Unit = { _, _ -> },
    widgetDataProvider: (AnalyticsWidget) -> WidgetData,
    enableCollapsibleSections: Boolean,
    enableDragAndDrop: Boolean = false,
    isLoading: Boolean,
    windowSizeClass: WindowSizeClass = rememberWindowSizeClass()
) {
    // Use the enhanced responsive grid with collapsible sections
    EnhancedResponsiveGrid(
        widgets = widgets,
        windowSizeClass = windowSizeClass,
        modifier = modifier,
        onWidgetClick = onWidgetClick,
        widgetDataProvider = widgetDataProvider,
        isLoading = isLoading,
        enableCollapsibleSections = enableCollapsibleSections,
        useVerticalList = shouldUseVerticalList(windowSizeClass)
    )
}

/**
 * Determines if vertical list layout should be used based on screen size
 * Mobile-optimized: Use vertical list for seamless 2x1 experience
 */
private fun shouldUseVerticalList(windowSizeClass: WindowSizeClass): Boolean {
    return windowSizeClass.widthDp.value < 600
}

// ConfigurationHeader removed to eliminate duplicate widget count display

@Composable
private fun WidgetSection(
    title: String,
    description: String,
    widgets: List<AnalyticsWidget>,
    onWidgetClick: (AnalyticsWidget) -> Unit,
    onWidgetReorder: (from: Int, to: Int) -> Unit = { _, _ -> },
    widgetDataProvider: (AnalyticsWidget) -> WidgetData,
    isCollapsible: Boolean,
    enableDragAndDrop: Boolean = false,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 0f else 180f,
        animationSpec = tween(300),
        label = "section_expansion_rotation"
    )
    
    // Announce section toggle for accessibility
    LaunchedEffect(isExpanded) {
        // TalkBack announcements moved to UI level for proper @Composable context
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(300))
    ) {
        // Section header - only show if title is not empty
        if (title.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics {
                            heading()
                            contentDescription = "$title section header"
                        }
                    )
                    
                    // Section description removed to eliminate duplicate widget count display
                }
                
                if (isCollapsible) {
                    IconButton(
                        onClick = { 
                            isExpanded = !isExpanded
                        },
                        modifier = Modifier.semantics {
                            contentDescription = if (isExpanded) "Collapse $title section" else "Expand $title section"
                            role = Role.Button
                            stateDescription = "$title section is ${if (isExpanded) "expanded" else "collapsed"}"
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(rotationAngle),
                            tint = LiftrixColors.Primary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        // Section content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
        ) {
            if (enableDragAndDrop) {
                // Use DragAndDropGrid for section content when drag-and-drop is enabled
                DragAndDropGrid(
                    widgets = widgets,
                    windowSizeClass = com.example.liftrix.ui.common.rememberWindowSizeClass(),
                    onReorder = onWidgetReorder,
                    onWidgetClick = onWidgetClick,
                    widgetDataProvider = widgetDataProvider,
                    isLoading = isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    widgets.forEach { widget ->
                        val widgetData = widgetDataProvider(widget)
                        WidgetRenderer(
                            widget = widget,
                            widgetData = widgetData,
                            onClick = { onWidgetClick(widget) },
                            isLoading = isLoading
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun WidgetRenderer(
    widget: AnalyticsWidget,
    widgetData: WidgetData,
    onClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    // Render appropriate widget component based on widget type
    when (widget) {
        AnalyticsWidget.TotalVolume -> {
            val basicData = widgetData as? BasicWidgetData
            val metricData = basicData?.let { basic ->
                com.example.liftrix.domain.model.analytics.MetricWidgetData(
                    widgetType = widget,
                    lastUpdated = basic.lastUpdated,
                    isLoading = isLoading,
                    error = null,
                    primaryValue = basic.primaryValue,
                    unit = "kg",
                    secondaryValue = basic.secondaryValue ?: "",
                    trend = basic.trend ?: TrendDirection.STABLE,
                    trendPercentage = 0f,
                    comparisonPeriod = basic.secondaryValue ?: ""
                )
            }
            TotalVolumeWidget(
                data = metricData,
                onRefresh = {},
                onClick = onClick,
                modifier = modifier
            )
        }
        
        AnalyticsWidget.WorkoutFrequency -> {
            val basicData = widgetData as? BasicWidgetData
            val metricData = basicData?.let { basic ->
                com.example.liftrix.domain.model.analytics.MetricWidgetData(
                    widgetType = widget,
                    lastUpdated = basic.lastUpdated,
                    isLoading = isLoading,
                    error = null,
                    primaryValue = basic.primaryValue,
                    unit = "sessions",
                    secondaryValue = basic.secondaryValue ?: "",
                    trend = basic.trend ?: TrendDirection.STABLE,
                    trendPercentage = 0f,
                    comparisonPeriod = basic.secondaryValue ?: ""
                )
            }
            WorkoutFrequencyWidget(
                data = metricData,
                onRefresh = {},
                onClick = onClick,
                modifier = modifier
            )
        }
        
        AnalyticsWidget.AverageDuration -> {
            val basicData = widgetData as? BasicWidgetData
            val metricData = basicData?.let { basic ->
                com.example.liftrix.domain.model.analytics.MetricWidgetData(
                    widgetType = widget,
                    lastUpdated = basic.lastUpdated,
                    isLoading = isLoading,
                    error = null,
                    primaryValue = basic.primaryValue,
                    unit = "min",
                    secondaryValue = basic.secondaryValue ?: "",
                    trend = basic.trend ?: TrendDirection.STABLE,
                    trendPercentage = 0f,
                    comparisonPeriod = basic.secondaryValue ?: ""
                )
            }
            AverageDurationWidget(
                data = metricData,
                onRefresh = {},
                onClick = onClick,
                modifier = modifier
            )
        }
        
        AnalyticsWidget.WorkoutStreak -> {
            val basicData = widgetData as? BasicWidgetData
            val metricData = basicData?.let { basic ->
                com.example.liftrix.domain.model.analytics.MetricWidgetData(
                    widgetType = widget,
                    lastUpdated = basic.lastUpdated,
                    isLoading = isLoading,
                    error = null,
                    primaryValue = basic.primaryValue,
                    unit = "days",
                    secondaryValue = basic.secondaryValue ?: "",
                    trend = basic.trend ?: TrendDirection.STABLE,
                    trendPercentage = 0f,
                    comparisonPeriod = basic.secondaryValue ?: ""
                )
            }
            WorkoutStreakWidget(
                data = metricData,
                onRefresh = {},
                onClick = onClick,
                modifier = modifier
            )
        }
        
        // CaloriesBurned widget removed - using AverageDuration instead
        
        // DailyCalories widget removed - using existing widgets
        
        // WeeklyCalorieTrend widget removed - use VolumeTrends instead
        
        AnalyticsWidget.ProgressChart,
        AnalyticsWidget.OneRMProgression,
        AnalyticsWidget.VolumeLoadProgression -> {
            ProgressChart(
                data = createSampleChartData(widget),
                isLoading = isLoading,
                onClick = onClick,
                modifier = modifier
            )
        }
        
        else -> {
            // Default fallback to CompactMetricWidget
            val basicData = widgetData as? BasicWidgetData
            val metricData = basicData?.let { basic ->
                com.example.liftrix.domain.model.analytics.MetricWidgetData(
                    widgetType = widget,
                    lastUpdated = basic.lastUpdated,
                    isLoading = isLoading,
                    error = null,
                    primaryValue = basic.primaryValue,
                    unit = "",
                    secondaryValue = basic.secondaryValue ?: "",
                    trend = basic.trend ?: TrendDirection.STABLE,
                    trendPercentage = 0f,
                    comparisonPeriod = basic.secondaryValue ?: ""
                )
            }
            CompactMetricWidget(
                widget = widget,
                data = metricData,
                onRefresh = {},
                onClick = onClick,
                modifier = modifier
            )
        }
    }
}

// Helper functions for sample data
private fun createSampleWidgetData(widget: AnalyticsWidget): WidgetData {
    return when (widget) {
        AnalyticsWidget.TotalVolume -> BasicWidgetData(
            widgetType = widget,
            lastUpdated = kotlinx.datetime.Clock.System.now(),
            primaryValue = "2,847 kg",
            secondaryValue = "This week",
            unit = "kg",
            trend = TrendDirection.UP
        )
        AnalyticsWidget.WorkoutFrequency -> BasicWidgetData(
            widgetType = widget,
            lastUpdated = kotlinx.datetime.Clock.System.now(),
            primaryValue = "4 sessions",
            secondaryValue = "Last 7 days",
            unit = "sessions",
            trend = TrendDirection.STABLE
        )
        AnalyticsWidget.WorkoutStreak -> BasicWidgetData(
            widgetType = widget,
            lastUpdated = kotlinx.datetime.Clock.System.now(),
            primaryValue = "12 days",
            secondaryValue = "Current streak",
            unit = "days",
            trend = TrendDirection.UP
        )
        AnalyticsWidget.AverageDuration -> BasicWidgetData(
            widgetType = widget,
            lastUpdated = kotlinx.datetime.Clock.System.now(),
            primaryValue = "67 min",
            secondaryValue = "Average session",
            unit = "min",
            trend = TrendDirection.STABLE
        )
        // CaloriesBurned removed - use AverageDuration sample data
        // DailyCalories removed - using existing widget data
        // WeeklyCalorieTrend removed - use VolumeTrends instead
        else -> BasicWidgetData(
            widgetType = widget,
            lastUpdated = kotlinx.datetime.Clock.System.now(),
            primaryValue = "—",
            secondaryValue = "No data",
            unit = "",
            trend = TrendDirection.UNKNOWN
        )
    }
}

private fun createSampleChartData(widget: AnalyticsWidget): ChartData {
    return ChartData(
        dataPoints = emptyList(), // Sample data would be provided by ViewModel
        title = widget.displayName,
        valueUnit = when (widget) {
            AnalyticsWidget.TotalVolume, AnalyticsWidget.VolumeLoadProgression -> "kg"
            AnalyticsWidget.AverageDuration -> "min"
            else -> ""
        },
        chartType = when (widget) {
            AnalyticsWidget.ProgressChart -> ChartType.RADIAL
            AnalyticsWidget.OneRMProgression -> ChartType.LINE
            else -> ChartType.BAR
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun WidgetContainerPreview() {
    LiftrixTheme {
        WidgetContainer(
            widgets = listOf(
                AnalyticsWidget.WorkoutFrequency,
                AnalyticsWidget.TotalVolume, 
                AnalyticsWidget.WorkoutStreak,
                AnalyticsWidget.AverageDuration,
                AnalyticsWidget.ProgressChart
            ),
            configuration = DashboardConfiguration.Intermediate,
            layoutMode = WidgetLayoutMode.SECTIONS
        )
    }
}