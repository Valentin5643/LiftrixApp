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
import androidx.compose.ui.graphics.Color
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
import com.example.liftrix.domain.model.analytics.BasicWidgetData
import com.example.liftrix.domain.model.analytics.ChartWidgetData
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.TrendDirection
import com.example.liftrix.domain.model.analytics.WidgetCategory
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.common.extensions.getWeightUnitSymbolFromPreferences
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.core.extensions.rememberDerivedStateOf
import com.example.liftrix.core.extensions.rememberWidgetDataCache
import com.example.liftrix.ui.accessibility.AccessibilityUtils
import com.example.liftrix.ui.accessibility.TalkBackAnnouncements
import com.example.liftrix.core.extensions.WidgetPerformanceExt
import com.example.liftrix.ui.common.extensions.getWeightUnitFromPreferences
import com.example.liftrix.ui.common.extensions.getWeightUnitSymbolFromPreferences
import com.example.liftrix.domain.progress.ProgressUnitConversionPort
import com.example.liftrix.domain.model.WeightUnit

// Import chart types for widget rendering
import com.example.liftrix.ui.progress.components.ProgressChart
import com.example.liftrix.ui.progress.components.ChartData
import com.example.liftrix.ui.progress.components.ChartDataPoint
import com.example.liftrix.ui.progress.components.ChartType
import com.example.liftrix.ui.progress.components.WidgetLayoutMode
import com.example.liftrix.ui.progress.components.widgets.*

// WidgetLayoutMode is now imported from the proper file


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
    windowSizeClass: WindowSizeClass = rememberWindowSizeClass(),
    coordinatorPreferences: Map<String, Any> = emptyMap(),
    unitConversionService: ProgressUnitConversionPort? = null,
    isDebugBuild: Boolean = false
) {
    // Performance optimization: memoize expensive calculations
    val columnsCount by rememberDerivedStateOf { 
        windowSizeClass.calculateOptimalColumns(maxColumns = 3)
    }
    
    // Performance optimization: cache widget data to prevent repeated calculations
    val widgetDataCache = rememberWidgetDataCache(widgets, widgetDataProvider)
    
    // Performance tracking for development builds
    ProgressWidgetPerformanceMarker(
        componentId = "WidgetContainer_${layoutMode.name}",
        enabled = isDebugBuild
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
                    windowSizeClass = windowSizeClass,
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
                    isLoading = isLoading,
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
                isLoading = isLoading,
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
                enableCollapsibleSections = enableCollapsibleSections && 
                    windowSizeClass.widthDp.value >= 600,
                enableDragAndDrop = enableDragAndDrop,
                isLoading = isLoading,
                windowSizeClass = windowSizeClass,
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
    windowSizeClass: WindowSizeClass = rememberWindowSizeClass(),
    coordinatorPreferences: Map<String, Any> = emptyMap(),
    unitConversionService: ProgressUnitConversionPort? = null
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
            
            // Calculate aspect ratio based on column count
            val aspectRatio = if (columnsCount == 1) 2.0f else 1.1f
            
            WidgetRenderer(
                widget = widget,
                widgetData = widgetData,
                onClick = { onWidgetClick(widget) },
                isLoading = isLoading,
                coordinatorPreferences = coordinatorPreferences,
                unitConversionService = unitConversionService,
                aspectRatio = aspectRatio
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
    isLoading: Boolean,
    coordinatorPreferences: Map<String, Any> = emptyMap(),
    unitConversionService: ProgressUnitConversionPort? = null
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
            
            // Calculate aspect ratio based on column count
            val aspectRatio = if (columnsCount == 1) 2.0f else 1.1f
            
            WidgetRenderer(
                widget = widget,
                widgetData = widgetData,
                onClick = { onWidgetClick(widget) },
                isLoading = isLoading,
                coordinatorPreferences = coordinatorPreferences,
                unitConversionService = unitConversionService,
                aspectRatio = aspectRatio
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
    isLoading: Boolean,
    coordinatorPreferences: Map<String, Any> = emptyMap(),
    unitConversionService: ProgressUnitConversionPort? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        widgets.forEach { widget ->
            val widgetData = widgetDataProvider(widget)
            
            // List layout always uses single column, so use rectangular aspect ratio
            val aspectRatio = 2.0f
            
            WidgetRenderer(
                widget = widget,
                widgetData = widgetData,
                onClick = { onWidgetClick(widget) },
                isLoading = isLoading,
                coordinatorPreferences = coordinatorPreferences,
                unitConversionService = unitConversionService,
                aspectRatio = aspectRatio
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
    windowSizeClass: WindowSizeClass = rememberWindowSizeClass(),
    coordinatorPreferences: Map<String, Any> = emptyMap(),
    unitConversionService: ProgressUnitConversionPort? = null
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
    coordinatorPreferences: Map<String, Any> = emptyMap(),
    unitConversionService: ProgressUnitConversionPort? = null,
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
                            contentDescription = if (isExpanded) "Collapse $title section" else "Expand $title section",
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
                        
                        // Section layout uses single column, so use rectangular aspect ratio
                        val aspectRatio = 2.0f
                        
                        WidgetRenderer(
                            widget = widget,
                            widgetData = widgetData,
                            onClick = { onWidgetClick(widget) },
                            isLoading = isLoading,
                            coordinatorPreferences = coordinatorPreferences,
                            unitConversionService = unitConversionService,
                            aspectRatio = aspectRatio
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
    coordinatorPreferences: Map<String, Any> = emptyMap(),
    unitConversionService: ProgressUnitConversionPort? = null,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 1.1f  // Dynamic aspect ratio based on layout context
) {
    // Get dynamic weight unit symbol from coordinator preferences
    val weightUnitSymbol = coordinatorPreferences.getWeightUnitSymbolFromPreferences()
    // Render appropriate widget component based on widget type
    when (widget) {
        AnalyticsWidget.TotalVolume -> {
            // Handle both BasicWidgetData and ChartWidgetData
            val basicData = widgetData as? BasicWidgetData
            val chartData = widgetData as? ChartWidgetData
            
            val metricData = basicData?.let { basic ->
                com.example.liftrix.domain.model.analytics.MetricWidgetData(
                    widgetType = widget,
                    lastUpdated = basic.lastUpdated,
                    isLoading = isLoading,
                    error = null,
                    primaryValue = basic.primaryValue,
                    unit = weightUnitSymbol,
                    secondaryValue = basic.secondaryValue ?: "",
                    trend = basic.trend ?: TrendDirection.STABLE,
                    trendPercentage = 0f,
                    comparisonPeriod = basic.secondaryValue ?: ""
                )
            } ?: chartData?.let { chart ->
                // Extract metric data from ChartWidgetData
                val totalVolume = chart.summary?.peak?.toInt() ?: 0
                com.example.liftrix.domain.model.analytics.MetricWidgetData(
                    widgetType = widget,
                    lastUpdated = chart.lastUpdated,
                    isLoading = chart.isLoading,
                    error = null,
                    primaryValue = "${totalVolume} ${weightUnitSymbol}",
                    unit = weightUnitSymbol,
                    secondaryValue = chart.timeRange,
                    trend = chart.summary?.trend ?: TrendDirection.STABLE,
                    trendPercentage = chart.summary?.changePercentage ?: 0f,
                    comparisonPeriod = chart.timeRange
                )
            }
            
            // Extract chart data for mini-graph
            val graphData = chartData?.dataPoints?.map { it.y } ?: emptyList()
            
            // Use Enhanced widget with mini-graph
            EnhancedTotalVolumeWidget(
                data = metricData,
                graphData = graphData,
                onRefresh = {},
                onClick = onClick,
                modifier = modifier,
                useFolderStyle = true,
                aspectRatio = aspectRatio
            )
        }
        
        AnalyticsWidget.WorkoutFrequency -> {
            // Handle both BasicWidgetData and ChartWidgetData
            val basicData = widgetData as? BasicWidgetData
            val chartData = widgetData as? ChartWidgetData
            
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
            } ?: chartData?.let { chart ->
                // Extract metric data from ChartWidgetData
                val totalSessions = chart.dataPoints.sumOf { it.y.toDouble() }.toInt()
                com.example.liftrix.domain.model.analytics.MetricWidgetData(
                    widgetType = widget,
                    lastUpdated = chart.lastUpdated,
                    isLoading = chart.isLoading,
                    error = null,
                    primaryValue = "$totalSessions sessions",
                    unit = "sessions",
                    secondaryValue = chart.timeRange,
                    trend = chart.summary.trend,
                    trendPercentage = chart.summary.changePercentage,
                    comparisonPeriod = chart.timeRange
                )
            }
            
            // Extract chart data for mini-graph
            val graphData = chartData?.dataPoints?.map { it.y } ?: emptyList()
            
            // Use Enhanced widget with mini-graph
            EnhancedWorkoutFrequencyWidget(
                data = metricData,
                graphData = graphData,
                onRefresh = {},
                onClick = onClick,
                modifier = modifier,
                useFolderStyle = true,
                aspectRatio = aspectRatio
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
        
        // ProgressWeeklyCalorieTrend widget removed - use VolumeTrends instead
        
        AnalyticsWidget.ProgressChart -> {
            // Handle both BasicWidgetData and ChartWidgetData
            val basicData = widgetData as? BasicWidgetData
            val chartData = widgetData as? ChartWidgetData
            
            val metricData = basicData?.let { basic ->
                com.example.liftrix.domain.model.analytics.MetricWidgetData(
                    widgetType = widget,
                    lastUpdated = basic.lastUpdated,
                    isLoading = isLoading,
                    error = null,
                    primaryValue = basic.primaryValue,
                    unit = weightUnitSymbol,
                    secondaryValue = basic.secondaryValue ?: "",
                    trend = basic.trend ?: TrendDirection.STABLE,
                    trendPercentage = 0f,
                    comparisonPeriod = basic.secondaryValue ?: ""
                )
            } ?: chartData?.let { chart ->
                // Extract metric data from ChartWidgetData
                val averageValue = chart.dataPoints.map { it.y }.average().toInt()
                com.example.liftrix.domain.model.analytics.MetricWidgetData(
                    widgetType = widget,
                    lastUpdated = chart.lastUpdated,
                    isLoading = chart.isLoading,
                    error = null,
                    primaryValue = "$averageValue",
                    unit = chart.summary.unit,
                    secondaryValue = chart.timeRange,
                    trend = chart.summary.trend,
                    trendPercentage = chart.summary.changePercentage,
                    comparisonPeriod = chart.timeRange
                )
            }
            
            // Extract chart data for mini-graph
            val graphData = chartData?.dataPoints?.map { it.y } ?: emptyList()
            
            EnhancedStrengthProgressWidget(
                data = metricData,
                graphData = graphData,
                onRefresh = {},
                onClick = onClick,
                modifier = modifier,
                useFolderStyle = true,
                aspectRatio = aspectRatio
            )
        }
        
        AnalyticsWidget.OneRMProgression -> {
            // Handle both BasicWidgetData and ChartWidgetData
            val basicData = widgetData as? BasicWidgetData
            val chartData = widgetData as? ChartWidgetData
            
            val metricData = basicData?.let { basic ->
                com.example.liftrix.domain.model.analytics.MetricWidgetData(
                    widgetType = widget,
                    lastUpdated = basic.lastUpdated,
                    isLoading = isLoading,
                    error = null,
                    primaryValue = basic.primaryValue,
                    unit = weightUnitSymbol,
                    secondaryValue = basic.secondaryValue ?: "",
                    trend = basic.trend ?: TrendDirection.STABLE,
                    trendPercentage = 0f,
                    comparisonPeriod = basic.secondaryValue ?: ""
                )
            } ?: chartData?.let { chart ->
                // Extract metric data from ChartWidgetData - use peak for 1RM
                val currentOneRM = chart.summary.peak.toInt()
                com.example.liftrix.domain.model.analytics.MetricWidgetData(
                    widgetType = widget,
                    lastUpdated = chart.lastUpdated,
                    isLoading = chart.isLoading,
                    error = null,
                    primaryValue = "$currentOneRM ${weightUnitSymbol}",
                    unit = weightUnitSymbol,
                    secondaryValue = chart.timeRange,
                    trend = chart.summary.trend,
                    trendPercentage = chart.summary.changePercentage,
                    comparisonPeriod = chart.timeRange
                )
            }
            
            // Extract chart data for mini-graph
            val graphData = chartData?.dataPoints?.map { it.y } ?: emptyList()
            
            EnhancedOneRmProgressionWidget(
                data = metricData,
                graphData = graphData,
                onRefresh = {},
                onClick = onClick,
                modifier = modifier,
                useFolderStyle = true,
                aspectRatio = aspectRatio
            )
        }
        
        AnalyticsWidget.VolumeLoadProgression -> {
            // Handle both BasicWidgetData and ChartWidgetData
            val basicData = widgetData as? BasicWidgetData
            val chartData = widgetData as? ChartWidgetData
            
            val metricData = basicData?.let { basic ->
                com.example.liftrix.domain.model.analytics.MetricWidgetData(
                    widgetType = widget,
                    lastUpdated = basic.lastUpdated,
                    isLoading = isLoading,
                    error = null,
                    primaryValue = basic.primaryValue,
                    unit = weightUnitSymbol,
                    secondaryValue = basic.secondaryValue ?: "",
                    trend = basic.trend ?: TrendDirection.STABLE,
                    trendPercentage = 0f,
                    comparisonPeriod = basic.secondaryValue ?: ""
                )
            } ?: chartData?.let { chart ->
                // Extract metric data from ChartWidgetData
                val totalVolume = chart.summary?.peak?.toInt() ?: 0
                com.example.liftrix.domain.model.analytics.MetricWidgetData(
                    widgetType = widget,
                    lastUpdated = chart.lastUpdated,
                    isLoading = chart.isLoading,
                    error = null,
                    primaryValue = "${totalVolume} ${weightUnitSymbol}",
                    unit = weightUnitSymbol,
                    secondaryValue = chart.timeRange,
                    trend = chart.summary?.trend ?: TrendDirection.STABLE,
                    trendPercentage = chart.summary?.changePercentage ?: 0f,
                    comparisonPeriod = chart.timeRange
                )
            }
            
            // Extract chart data for mini-graph
            val graphData = chartData?.dataPoints?.map { it.y } ?: emptyList()
            
            EnhancedVolumeLoadProgressionWidget(
                data = metricData,
                graphData = graphData,
                onRefresh = {},
                onClick = onClick,
                modifier = modifier,
                useFolderStyle = true,
                aspectRatio = aspectRatio
            )
        }
        
        AnalyticsWidget.MuscleGroupDistribution -> {
            val basicData = widgetData as? BasicWidgetData
            val metricData = basicData?.let { basic ->
                com.example.liftrix.domain.model.analytics.MetricWidgetData(
                    widgetType = widget,
                    lastUpdated = basic.lastUpdated,
                    isLoading = isLoading,
                    error = null,
                    primaryValue = basic.primaryValue,
                    unit = "%",
                    secondaryValue = basic.secondaryValue ?: "",
                    trend = basic.trend ?: TrendDirection.STABLE,
                    trendPercentage = 0f,
                    comparisonPeriod = basic.secondaryValue ?: ""
                )
            }
            EnhancedMuscleGroupWidget(
                data = metricData,
                distributionData = generateSampleDistributionData(),
                onRefresh = {},
                onClick = onClick,
                modifier = modifier,
                useFolderStyle = true,
                aspectRatio = aspectRatio
            )
        }
        
        AnalyticsWidget.StrengthProgress -> {
            // Handle both BasicWidgetData and ChartWidgetData
            val basicData = widgetData as? BasicWidgetData
            val chartData = widgetData as? ChartWidgetData
            
            val metricData = basicData?.let { basic ->
                com.example.liftrix.domain.model.analytics.MetricWidgetData(
                    widgetType = widget,
                    lastUpdated = basic.lastUpdated,
                    isLoading = isLoading,
                    error = null,
                    primaryValue = basic.primaryValue,
                    unit = weightUnitSymbol,
                    secondaryValue = basic.secondaryValue ?: "",
                    trend = basic.trend ?: TrendDirection.STABLE,
                    trendPercentage = 0f,
                    comparisonPeriod = basic.secondaryValue ?: ""
                )
            } ?: chartData?.let { chart ->
                // Extract metric data from ChartWidgetData
                val strengthScore = chart.summary.peak.toInt()
                com.example.liftrix.domain.model.analytics.MetricWidgetData(
                    widgetType = widget,
                    lastUpdated = chart.lastUpdated,
                    isLoading = chart.isLoading,
                    error = null,
                    primaryValue = "$strengthScore",
                    unit = chart.summary.unit,
                    secondaryValue = chart.timeRange,
                    trend = chart.summary.trend,
                    trendPercentage = chart.summary.changePercentage,
                    comparisonPeriod = chart.timeRange
                )
            }
            
            // Extract chart data for mini-graph
            val graphData = chartData?.dataPoints?.map { it.y } ?: emptyList()
            
            EnhancedStrengthProgressWidget(
                data = metricData,
                graphData = graphData,
                onRefresh = {},
                onClick = onClick,
                modifier = modifier,
                useFolderStyle = true,
                aspectRatio = aspectRatio
            )
        }
        
        AnalyticsWidget.VolumeAnalytics -> {
            VolumeAnalyticsWidget(
                data = null, // Will be provided by the data provider
                onRefresh = {},
                onClick = onClick,
                modifier = modifier,
                useFolderStyle = true,
                aspectRatio = aspectRatio
            )
        }
        
        AnalyticsWidget.RecoveryMetrics -> {
            RecoveryMetricsWidget(
                data = null, // Will be provided by the data provider
                onRefresh = {},
                onClick = onClick,
                modifier = modifier,
                useFolderStyle = true,
                aspectRatio = aspectRatio
            )
        }
        
        AnalyticsWidget.MonthlySummary -> {
            MonthlySummaryWidget(
                data = null, // Will be provided by the data provider
                onRefresh = {},
                onClick = onClick,
                modifier = modifier,
                useFolderStyle = true,
                aspectRatio = aspectRatio
            )
        }
        
        AnalyticsWidget.StrengthAnalytics -> {
            // Handle both BasicWidgetData and ChartWidgetData - similar to StrengthProgress
            val basicData = widgetData as? BasicWidgetData
            val chartData = widgetData as? ChartWidgetData
            
            val metricData = basicData?.let { basic ->
                com.example.liftrix.domain.model.analytics.MetricWidgetData(
                    widgetType = widget,
                    lastUpdated = basic.lastUpdated,
                    isLoading = isLoading,
                    error = null,
                    primaryValue = basic.primaryValue,
                    unit = weightUnitSymbol,
                    secondaryValue = basic.secondaryValue ?: "",
                    trend = basic.trend ?: TrendDirection.STABLE,
                    trendPercentage = 0f,
                    comparisonPeriod = basic.secondaryValue ?: ""
                )
            } ?: chartData?.let { chart ->
                // Extract metric data from ChartWidgetData
                val strengthScore = chart.summary.peak.toInt()
                com.example.liftrix.domain.model.analytics.MetricWidgetData(
                    widgetType = widget,
                    lastUpdated = chart.lastUpdated,
                    isLoading = chart.isLoading,
                    error = null,
                    primaryValue = "$strengthScore",
                    unit = chart.summary.unit,
                    secondaryValue = chart.timeRange,
                    trend = chart.summary.trend,
                    trendPercentage = chart.summary.changePercentage,
                    comparisonPeriod = chart.timeRange
                )
            }
            
            // Extract chart data for mini-graph
            val graphData = chartData?.dataPoints?.map { it.y } ?: emptyList()
            
            EnhancedStrengthProgressWidget(
                data = metricData,
                graphData = graphData,
                onRefresh = {},
                onClick = onClick,
                modifier = modifier,
                useFolderStyle = true,
                aspectRatio = aspectRatio
            )
        }
        
        AnalyticsWidget.FrequencyChart -> {
            // Handle both BasicWidgetData and ChartWidgetData for FrequencyChart
            val basicData = widgetData as? BasicWidgetData
            val chartData = widgetData as? ChartWidgetData
            
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
            } ?: chartData?.let { chart ->
                // Extract metric data from ChartWidgetData
                val totalSessions = chart.dataPoints.sumOf { it.y.toDouble() }.toInt()
                com.example.liftrix.domain.model.analytics.MetricWidgetData(
                    widgetType = widget,
                    lastUpdated = chart.lastUpdated,
                    isLoading = chart.isLoading,
                    error = null,
                    primaryValue = "$totalSessions sessions",
                    unit = "sessions",
                    secondaryValue = chart.timeRange,
                    trend = chart.summary?.trend ?: TrendDirection.STABLE,
                    trendPercentage = chart.summary?.changePercentage ?: 0f,
                    comparisonPeriod = chart.timeRange
                )
            }
            
            // Extract chart data for mini-graph
            val graphData = chartData?.dataPoints?.map { it.y } ?: emptyList()
            
            // Use Enhanced frequency widget with folder style
            EnhancedWorkoutFrequencyWidget(
                data = metricData,
                graphData = graphData,
                onRefresh = {},
                onClick = onClick,
                modifier = modifier,
                useFolderStyle = true, // Enable folder style for frequency chart
                aspectRatio = aspectRatio
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
            primaryValue = "2,847 ${WeightUnit.getSystemDefault().symbol}",
            secondaryValue = "This week",
            unit = WeightUnit.getSystemDefault().symbol,
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
        // ProgressWeeklyCalorieTrend removed - use VolumeTrends instead
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
            AnalyticsWidget.TotalVolume, AnalyticsWidget.VolumeLoadProgression -> WeightUnit.getSystemDefault().symbol
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

// Helper functions for generating sample graph data for mini-graphs
private fun generateSampleVolumeGraphData(): List<Float> {
    return listOf(2400f, 2650f, 2500f, 2800f, 2750f, 2900f, 2847f)
}

private fun generateSampleFrequencyGraphData(): List<Float> {
    return listOf(3f, 4f, 3f, 5f, 4f, 4f, 3f)
}

private fun generateSampleProgressGraphData(): List<Float> {
    return listOf(100f, 102f, 105f, 103f, 108f, 110f, 112f)
}

private fun generateSample1RMGraphData(): List<Float> {
    return listOf(80f, 82.5f, 82.5f, 85f, 87.5f, 85f, 90f)
}

private fun generateSampleDistributionData(): List<Pair<Float, Color>> {
    return listOf(
        35f to com.example.liftrix.ui.theme.LiftrixColors.Primary,
        25f to com.example.liftrix.ui.theme.LiftrixColors.Secondary,
        20f to com.example.liftrix.ui.theme.LiftrixColors.Primary.copy(alpha = 0.7f),
        15f to com.example.liftrix.ui.theme.LiftrixColors.Secondary.copy(alpha = 0.7f),
        5f to Color.Gray
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
