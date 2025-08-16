package com.example.liftrix.ui.progress.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.WidgetComplexity
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.ui.common.WindowSizeClass

/**
 * Enhanced responsive grid layout optimized for seamless mobile experience:
 * - Mobile (< 600dp): 2x1 layout (one widget per row) organized by widget type sections
 * - Tablet+ (≥ 600dp): 2x2 grid with collapsible sections
 * - Seamless scrolling within widget type categories
 * - No artificial height constraints or fragmented layout partitioning
 * 
 * This component creates a unified, consistent experience across all screen sizes
 * while maintaining logical widget organization by category.
 */
@Composable
fun EnhancedResponsiveGrid(
    widgets: List<AnalyticsWidget>,
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    onWidgetClick: (AnalyticsWidget) -> Unit = {},
    widgetDataProvider: (AnalyticsWidget) -> WidgetData = { createDefaultWidgetData(it) },
    isLoading: Boolean = false,
    enableCollapsibleSections: Boolean = true,
    useVerticalList: Boolean = false
) {
    // Group widgets by category for seamless sectioned experience
    val groupedWidgets = remember(widgets) {
        if (enableCollapsibleSections) {
            // Group by widget category for organized sections
            widgets.groupBy { widget -> 
                formatCategoryName(widget.category.name)
            }.toSortedMap() // Sort categories alphabetically for consistency
        } else {
            mapOf("Analytics" to widgets)
        }
    }
    
    // Mobile optimization: Use 2x1 layout for seamless experience
    val isMobile = windowSizeClass.widthDp < 600.dp
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (isMobile) 12.dp else 16.dp)
    ) {
        // Display widgets organized by type sections
        if (enableCollapsibleSections && groupedWidgets.size > 1) {
            SeamlessSectionsLayout(
                groupedWidgets = groupedWidgets,
                windowSizeClass = windowSizeClass,
                onWidgetClick = onWidgetClick,
                widgetDataProvider = widgetDataProvider,
                isLoading = isLoading,
                isMobile = isMobile
            )
        } else {
            // Unified layout for single category
            if (isMobile) {
                MobileOptimizedLayout(
                    widgets = widgets,
                    windowSizeClass = windowSizeClass,
                    onWidgetClick = onWidgetClick,
                    widgetDataProvider = widgetDataProvider,
                    isLoading = isLoading
                )
            } else {
                ResponsiveGridLayout(
                    widgets = widgets,
                    windowSizeClass = windowSizeClass,
                    onWidgetClick = onWidgetClick,
                    widgetDataProvider = widgetDataProvider,
                    isLoading = isLoading
                )
            }
        }
    }
}

/**
 * Seamless sections layout optimized for mobile with widget type organization
 */
@Composable
private fun SeamlessSectionsLayout(
    groupedWidgets: Map<String, List<AnalyticsWidget>>,
    windowSizeClass: WindowSizeClass,
    onWidgetClick: (AnalyticsWidget) -> Unit,
    widgetDataProvider: (AnalyticsWidget) -> WidgetData,
    isLoading: Boolean,
    isMobile: Boolean
) {
    // Track expanded state for each section (start expanded on mobile for seamless experience)
    val expandedSections = remember { mutableStateMapOf<String, Boolean>() }
    
    // Initialize all sections as expanded by default for seamless scrolling
    LaunchedEffect(groupedWidgets.keys) {
        groupedWidgets.keys.forEach { category ->
            if (category !in expandedSections) {
                expandedSections[category] = true // Always start expanded for seamless experience
            }
        }
    }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(if (isMobile) 8.dp else 12.dp)
    ) {
        groupedWidgets.forEach { (category, widgets) ->
            if (widgets.isNotEmpty()) {
                SeamlessWidgetSection(
                    title = category,
                    widgets = widgets,
                    isExpanded = expandedSections[category] ?: true,
                    onToggle = { expandedSections[category] = it },
                    windowSizeClass = windowSizeClass,
                    onWidgetClick = onWidgetClick,
                    widgetDataProvider = widgetDataProvider,
                    isLoading = isLoading,
                    isMobile = isMobile
                )
            }
        }
    }
}

/**
 * Mobile-optimized 2x1 layout for seamless widget display
 */
@Composable
private fun MobileOptimizedLayout(
    widgets: List<AnalyticsWidget>,
    windowSizeClass: WindowSizeClass,
    onWidgetClick: (AnalyticsWidget) -> Unit,
    widgetDataProvider: (AnalyticsWidget) -> WidgetData,
    isLoading: Boolean
) {
    val spacing = 8.dp
    val contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        widgets.forEach { widget ->
            val widgetData = widgetDataProvider(widget)
            WidgetRenderer(
                widget = widget,
                widgetData = widgetData,
                onClick = { onWidgetClick(widget) },
                isLoading = isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

/**
 * Seamless widget section optimized for mobile 2x1 layout and tablet grid
 */
@Composable
private fun SeamlessWidgetSection(
    title: String,
    widgets: List<AnalyticsWidget>,
    isExpanded: Boolean,
    onToggle: (Boolean) -> Unit,
    windowSizeClass: WindowSizeClass,
    onWidgetClick: (AnalyticsWidget) -> Unit,
    widgetDataProvider: (AnalyticsWidget) -> WidgetData,
    isLoading: Boolean,
    isMobile: Boolean
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(if (isMobile) 6.dp else 8.dp)
    ) {
        // Section header - only show on tablet for collapsible sections, minimal on mobile
        if (!isMobile || widgets.size > 4) {
            SectionHeader(
                title = title,
                isExpanded = isExpanded,
                onToggle = onToggle,
                widgetCount = widgets.size,
                showWidgetCount = !isMobile // Hide count on mobile for cleaner look
            )
        } else {
            // Minimal header for mobile with few widgets
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        
        // Section content - always visible on mobile for seamless experience
        AnimatedVisibility(
            visible = isExpanded || isMobile, // Always show on mobile
            enter = if (isMobile) fadeIn() else expandVertically() + fadeIn(),
            exit = if (isMobile) fadeOut() else shrinkVertically() + fadeOut()
        ) {
            if (isMobile) {
                // Mobile: 2x1 layout (one widget per row)
                MobileWidgetColumn(
                    widgets = widgets,
                    onWidgetClick = onWidgetClick,
                    widgetDataProvider = widgetDataProvider,
                    isLoading = isLoading
                )
            } else {
                // Tablet+: Responsive grid layout
                ResponsiveGridLayout(
                    widgets = widgets,
                    windowSizeClass = windowSizeClass,
                    onWidgetClick = onWidgetClick,
                    widgetDataProvider = widgetDataProvider,
                    isLoading = isLoading
                )
            }
        }
    }
}

/**
 * Mobile-specific widget column for 2x1 seamless layout
 */
@Composable
private fun MobileWidgetColumn(
    widgets: List<AnalyticsWidget>,
    onWidgetClick: (AnalyticsWidget) -> Unit,
    widgetDataProvider: (AnalyticsWidget) -> WidgetData,
    isLoading: Boolean
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        widgets.forEach { widget ->
            val widgetData = widgetDataProvider(widget)
            WidgetRenderer(
                widget = widget,
                widgetData = widgetData,
                onClick = { onWidgetClick(widget) },
                isLoading = isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

/**
 * Responsive grid layout optimized for tablet+ screens (≥600dp)
 * Removes artificial height constraints for seamless scrolling
 * Uses non-scrollable layout when nested inside LazyColumn to prevent crash
 */
@Composable
private fun ResponsiveGridLayout(
    widgets: List<AnalyticsWidget>,
    windowSizeClass: WindowSizeClass,
    onWidgetClick: (AnalyticsWidget) -> Unit,
    widgetDataProvider: (AnalyticsWidget) -> WidgetData,
    isLoading: Boolean,
    isInsideScrollableParent: Boolean = true // Default to true since we're always in LazyColumn
) {
    // Fixed 2 columns for consistent tablet experience
    val columns = if (windowSizeClass.widthDp < 905.dp) 2 else 3
    
    val spacing = when {
        windowSizeClass.widthDp < 905.dp -> 12.dp
        else -> 16.dp
    }
    
    val contentPadding = PaddingValues(
        horizontal = when {
            windowSizeClass.widthDp < 905.dp -> 20.dp
            else -> 24.dp
        },
        vertical = 8.dp
    )
    
    // When inside a scrollable parent (LazyColumn), use non-lazy layout to prevent crash
    if (isInsideScrollableParent) {
        // Use a non-scrollable grid layout when nested
        NonScrollableGrid(
            widgets = widgets,
            columns = columns,
            spacing = spacing,
            contentPadding = contentPadding,
            onWidgetClick = onWidgetClick,
            widgetDataProvider = widgetDataProvider,
            isLoading = isLoading
        )
    } else {
        // Use LazyVerticalGrid when not nested
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            items(
                items = widgets,
                key = { widget -> widget.id }
            ) { widget ->
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

/**
 * Non-scrollable grid layout for use inside scrollable containers
 * Prevents the "infinite height constraints" crash when nested in LazyColumn
 */
@Composable
private fun NonScrollableGrid(
    widgets: List<AnalyticsWidget>,
    columns: Int,
    spacing: androidx.compose.ui.unit.Dp,
    contentPadding: PaddingValues,
    onWidgetClick: (AnalyticsWidget) -> Unit,
    widgetDataProvider: (AnalyticsWidget) -> WidgetData,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        // Group widgets into rows based on column count
        widgets.chunked(columns).forEach { rowWidgets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                rowWidgets.forEach { widget ->
                    val widgetData = widgetDataProvider(widget)
                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        WidgetRenderer(
                            widget = widget,
                            widgetData = widgetData,
                            onClick = { onWidgetClick(widget) },
                            isLoading = isLoading
                        )
                    }
                }
                // Fill remaining columns with spacers if row is incomplete
                repeat(columns - rowWidgets.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Formats category names for better display
 */
private fun formatCategoryName(categoryName: String): String {
    return when (categoryName.uppercase()) {
        "METRICS" -> "Key Metrics"
        "CHARTS" -> "Progress Charts" 
        "PROGRESS" -> "Progress Tracking"
        "ANALYTICS" -> "Analytics Insights"
        "BASIC" -> "Overview"
        "ADVANCED" -> "Advanced Analytics"
        else -> categoryName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}


/**
 * Creates default widget data for error states and previews
 */
private fun createDefaultWidgetData(widget: AnalyticsWidget): WidgetData {
    return com.example.liftrix.domain.model.analytics.BasicWidgetData(
        widgetType = widget,
        lastUpdated = kotlinx.datetime.Clock.System.now(),
        primaryValue = "—",
        secondaryValue = "Loading...",
        unit = "",
        trend = com.example.liftrix.domain.model.analytics.TrendDirection.STABLE
    )
}