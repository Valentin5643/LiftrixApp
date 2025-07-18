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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

// Import chart types for widget rendering
import com.example.liftrix.ui.progress.components.ChartData
import com.example.liftrix.ui.progress.components.ChartType
import com.example.liftrix.ui.progress.components.WidgetLayoutMode

// WidgetLayoutMode is now imported from the proper file

/**
 * Container component for organizing analytics dashboard widgets.
 * 
 * Provides flexible layout management with responsive design,
 * collapsible sections, and Hevy-inspired organizational patterns.
 * Supports grid, staggered, list, and sectioned layouts with
 * smooth animations and accessibility compliance.
 */
@Composable
fun WidgetContainer(
    widgets: List<AnalyticsWidget>,
    configuration: DashboardConfiguration,
    modifier: Modifier = Modifier,
    layoutMode: WidgetLayoutMode = WidgetLayoutMode.SECTIONS,
    onWidgetClick: (AnalyticsWidget) -> Unit = {},
    widgetDataProvider: (AnalyticsWidget) -> WidgetData = { createSampleWidgetData(it) },
    isLoading: Boolean = false,
    enableCollapsibleSections: Boolean = true
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val columnsCount = when {
        screenWidth >= 600 -> 3 // Tablet landscape
        screenWidth >= 400 -> 2 // Phone landscape / large phone
        else -> 1 // Phone portrait
    }
    
    when (layoutMode) {
        WidgetLayoutMode.GRID -> {
            GridLayout(
                widgets = widgets,
                columnsCount = columnsCount,
                modifier = modifier,
                onWidgetClick = onWidgetClick,
                widgetDataProvider = widgetDataProvider,
                isLoading = isLoading
            )
        }
        
        WidgetLayoutMode.STAGGERED -> {
            StaggeredLayout(
                widgets = widgets,
                columnsCount = columnsCount,
                modifier = modifier,
                onWidgetClick = onWidgetClick,
                widgetDataProvider = widgetDataProvider,
                isLoading = isLoading
            )
        }
        
        WidgetLayoutMode.LIST -> {
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
                widgetDataProvider = widgetDataProvider,
                enableCollapsibleSections = enableCollapsibleSections,
                isLoading = isLoading
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
    widgetDataProvider: (AnalyticsWidget) -> WidgetData,
    isLoading: Boolean
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columnsCount),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
private fun SectionedLayout(
    widgets: List<AnalyticsWidget>,
    configuration: DashboardConfiguration,
    modifier: Modifier = Modifier,
    onWidgetClick: (AnalyticsWidget) -> Unit,
    widgetDataProvider: (AnalyticsWidget) -> WidgetData,
    enableCollapsibleSections: Boolean,
    isLoading: Boolean
) {
    val widgetsByCategory = widgets.groupBy { it.category }
    
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Configuration header
        item {
            ConfigurationHeader(
                configuration = configuration,
                totalWidgets = widgets.size
            )
        }
        
        // Essential widgets section (always visible)
        val essentialWidgets = widgets.filter { it.priority.configurationLevel == 1 }
        if (essentialWidgets.isNotEmpty()) {
            item {
                WidgetSection(
                    title = "Essential Metrics",
                    description = "Core analytics for tracking your progress",
                    widgets = essentialWidgets,
                    onWidgetClick = onWidgetClick,
                    widgetDataProvider = widgetDataProvider,
                    isCollapsible = false,
                    isLoading = isLoading
                )
            }
        }
        
        // Intermediate widgets section
        val intermediateWidgets = widgets.filter { it.priority.configurationLevel == 2 }
        if (intermediateWidgets.isNotEmpty()) {
            item {
                WidgetSection(
                    title = "Enhanced Insights",
                    description = "Advanced metrics for optimization",
                    widgets = intermediateWidgets,
                    onWidgetClick = onWidgetClick,
                    widgetDataProvider = widgetDataProvider,
                    isCollapsible = enableCollapsibleSections,
                    isLoading = isLoading
                )
            }
        }
        
        // Advanced widgets section
        val advancedWidgets = widgets.filter { it.priority.configurationLevel == 3 }
        if (advancedWidgets.isNotEmpty()) {
            item {
                WidgetSection(
                    title = "Advanced Analytics",
                    description = "Professional-level metrics and insights",
                    widgets = advancedWidgets,
                    onWidgetClick = onWidgetClick,
                    widgetDataProvider = widgetDataProvider,
                    isCollapsible = enableCollapsibleSections,
                    isLoading = isLoading
                )
            }
        }
    }
}

@Composable
private fun ConfigurationHeader(
    configuration: DashboardConfiguration,
    totalWidgets: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "${configuration.name} Dashboard",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = "${configuration.description} • $totalWidgets widgets",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun WidgetSection(
    title: String,
    description: String,
    widgets: List<AnalyticsWidget>,
    onWidgetClick: (AnalyticsWidget) -> Unit,
    widgetDataProvider: (AnalyticsWidget) -> WidgetData,
    isCollapsible: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 0f else 180f,
        animationSpec = tween(300),
        label = "section_expansion_rotation"
    )
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(300))
    ) {
        // Section header
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
                    fontWeight = FontWeight.SemiBold
                )
                
                Text(
                    text = "$description • ${widgets.size} widgets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            if (isCollapsible) {
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.semantics {
                        contentDescription = if (isExpanded) "Collapse $title section" else "Expand $title section"
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
        
        // Section content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
        ) {
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

@Composable
private fun WidgetRenderer(
    widget: AnalyticsWidget,
    widgetData: WidgetData,
    onClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    // Render appropriate widget component based on widget type
    when (widget) {
        AnalyticsWidget.TotalVolume,
        AnalyticsWidget.WorkoutFrequency,
        AnalyticsWidget.AverageDuration,
        AnalyticsWidget.ConsistencyStreak -> {
            val basicData = widgetData as? BasicWidgetData
            MetricsCard(
                title = widget.displayName,
                value = basicData?.value ?: "—",
                subtitle = basicData?.subtitle ?: "No data",
                trend = basicData?.trend,
                isLoading = isLoading,
                onClick = onClick,
                modifier = modifier
            )
        }
        
        AnalyticsWidget.CALORIES_BURNED -> {
            val basicData = widgetData as? BasicWidgetData
            CaloriesBurnedCard(
                caloriesBurned = basicData?.value?.replace(" cal", "")?.replace(",", "")?.toIntOrNull() ?: 0,
                subtitle = basicData?.subtitle ?: "Today",
                trend = basicData?.trend,
                isLoading = isLoading,
                onClick = onClick,
                modifier = modifier
            )
        }
        
        AnalyticsWidget.DAILY_CALORIES -> {
            val basicData = widgetData as? BasicWidgetData
            val caloriesValue = basicData?.value?.replace(" cal", "")?.replace(",", "")?.toIntOrNull() ?: 0
            DailyCaloriesCard(
                caloriesInToday = caloriesValue,
                dailyGoal = 400, // TODO: Get from user preferences
                workoutCount = 1, // TODO: Get from actual data
                trend = basicData?.trend,
                isLoading = isLoading,
                onClick = onClick,
                modifier = modifier
            )
        }
        
        AnalyticsWidget.WEEKLY_CALORIE_TREND -> {
            val basicData = widgetData as? BasicWidgetData
            WeeklyCalorieTrendCard(
                weeklyCalories = listOf(1200, 1450, 1380, 1520), // TODO: Get from actual data
                averageCalories = 1387, // TODO: Calculate from actual data
                trend = basicData?.trend,
                trendPercentage = 12.5f, // TODO: Calculate from actual data
                isLoading = isLoading,
                onClick = onClick,
                modifier = modifier
            )
        }
        
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
            // Default fallback to MetricsCard
            val basicData = widgetData as? BasicWidgetData
            MetricsCard(
                title = widget.displayName,
                value = basicData?.value ?: "—",
                subtitle = basicData?.subtitle ?: "No data",
                trend = basicData?.trend,
                isLoading = isLoading,
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
            value = "2,847 kg",
            subtitle = "This week",
            trend = TrendDirection.UP
        )
        AnalyticsWidget.WorkoutFrequency -> BasicWidgetData(
            widgetType = widget,
            lastUpdated = kotlinx.datetime.Clock.System.now(),
            value = "4 sessions",
            subtitle = "Last 7 days",
            trend = TrendDirection.STABLE
        )
        AnalyticsWidget.ConsistencyStreak -> BasicWidgetData(
            widgetType = widget,
            lastUpdated = kotlinx.datetime.Clock.System.now(),
            value = "12 days",
            subtitle = "Current streak",
            trend = TrendDirection.UP
        )
        AnalyticsWidget.AverageDuration -> BasicWidgetData(
            widgetType = widget,
            lastUpdated = kotlinx.datetime.Clock.System.now(),
            value = "67 min",
            subtitle = "Average session",
            trend = TrendDirection.STABLE
        )
        AnalyticsWidget.CALORIES_BURNED -> BasicWidgetData(
            widgetType = widget,
            lastUpdated = kotlinx.datetime.Clock.System.now(),
            value = "347 cal",
            subtitle = "Today",
            trend = TrendDirection.UP
        )
        AnalyticsWidget.DAILY_CALORIES -> BasicWidgetData(
            widgetType = widget,
            lastUpdated = kotlinx.datetime.Clock.System.now(),
            value = "347 cal",
            subtitle = "of 400 goal",
            trend = TrendDirection.UP
        )
        AnalyticsWidget.WEEKLY_CALORIE_TREND -> BasicWidgetData(
            widgetType = widget,
            lastUpdated = kotlinx.datetime.Clock.System.now(),
            value = "1,520 cal",
            subtitle = "This week",
            trend = TrendDirection.UP
        )
        else -> BasicWidgetData(
            widgetType = widget,
            lastUpdated = kotlinx.datetime.Clock.System.now(),
            value = "—",
            subtitle = "No data",
            trend = null
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
            widgets = DashboardConfiguration.Intermediate.widgets,
            configuration = DashboardConfiguration.Intermediate,
            layoutMode = WidgetLayoutMode.SECTIONS
        )
    }
}