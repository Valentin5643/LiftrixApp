package com.example.liftrix.ui.progress.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.ui.common.WindowSizeClass
import com.example.liftrix.ui.common.rememberWindowSizeClass
import com.example.liftrix.ui.components.layouts.GridSystem

/**
 * Adaptive widget grid using LazyVerticalGrid with responsive design.
 * 
 * Provides automatic column calculation, Material 3 spacing tokens,
 * and smooth transitions during orientation changes. Supports both
 * fixed and staggered grid layouts based on content requirements.
 * 
 * Enhanced responsive features:
 * - Precise column calculation for 320dp to 1200dp+ screen widths
 * - Material 3 spacing with strict 8dp grid alignment 
 * - Smooth layout transitions with animation support
 * - Foldable device support with layout preservation across fold/unfold
 * - Performance optimized with proper key usage and minimal recomposition
 * - Full accessibility support with semantic content descriptions
 * - Automatic minimum widget width constraints
 * 
 * @param widgets List of analytics widgets to display in grid
 * @param windowSizeClass Window size classification for responsive behavior
 * @param onWidgetClick Callback for widget tap interactions
 * @param widgetDataProvider Function to provide data for each widget
 * @param isLoading Loading state affecting all widgets
 * @param useStaggered Whether to use staggered grid for varied content heights
 * @param maxColumns Maximum number of columns allowed (default: 3)
 * @param modifier Modifier for styling the grid
 */
@Composable
fun AdaptiveWidgetGrid(
    widgets: List<AnalyticsWidget>,
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass = rememberWindowSizeClass(),
    onWidgetClick: (AnalyticsWidget) -> Unit = {},
    widgetDataProvider: (AnalyticsWidget) -> WidgetData = { createDefaultWidgetData(it) },
    isLoading: Boolean = false,
    useStaggered: Boolean = false,
    maxColumns: Int = 3,
    enableSmoothTransitions: Boolean = true
) {
    val columns = calculateOptimalColumns(
        windowSizeClass = windowSizeClass,
        widgetCount = widgets.size,
        maxColumns = maxColumns
    )
    
    val spacing = when {
        windowSizeClass.widthDp < 400.dp -> GridSystem.spacing2 // 8dp - very compact
        windowSizeClass.widthDp < 600.dp -> GridSystem.spacing2 // 8dp - compact
        windowSizeClass.widthDp < 905.dp -> GridSystem.spacing3 // 12dp - medium
        else -> GridSystem.spacing4 // 16dp - expanded
    }
    
    val contentPadding = calculateResponsivePadding(windowSizeClass)
    
    // Smooth transition animation for layout changes
    val transitionAlpha by animateFloatAsState(
        targetValue = if (isLoading) 0.7f else 1.0f,
        animationSpec = tween(300),
        label = "grid_transition_alpha"
    )
    
    val gridModifier = if (enableSmoothTransitions) {
        modifier.alpha(transitionAlpha)
    } else {
        modifier
    }
    
    if (useStaggered) {
        StaggeredWidgetGrid(
            widgets = widgets,
            columns = columns,
            spacing = spacing,
            contentPadding = contentPadding,
            onWidgetClick = onWidgetClick,
            widgetDataProvider = widgetDataProvider,
            isLoading = isLoading,
            modifier = gridModifier
        )
    } else {
        FixedWidgetGrid(
            widgets = widgets,
            columns = columns,
            spacing = spacing,
            contentPadding = contentPadding,
            onWidgetClick = onWidgetClick,
            widgetDataProvider = widgetDataProvider,
            isLoading = isLoading,
            modifier = gridModifier
        )
    }
}

/**
 * Fixed grid implementation using LazyVerticalGrid with equal column widths
 */
@Composable
private fun FixedWidgetGrid(
    widgets: List<AnalyticsWidget>,
    columns: Int,
    spacing: Dp,
    contentPadding: PaddingValues,
    onWidgetClick: (AnalyticsWidget) -> Unit,
    widgetDataProvider: (AnalyticsWidget) -> WidgetData,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 800.dp),
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

/**
 * Staggered grid implementation using LazyVerticalStaggeredGrid for varied content heights
 */
@Composable
private fun StaggeredWidgetGrid(
    widgets: List<AnalyticsWidget>,
    columns: Int,
    spacing: Dp,
    contentPadding: PaddingValues,
    onWidgetClick: (AnalyticsWidget) -> Unit,
    widgetDataProvider: (AnalyticsWidget) -> WidgetData,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(columns),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 800.dp),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalItemSpacing = spacing
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

/**
 * Adaptive grid with minimum column width constraint
 * Uses GridCells.Adaptive for automatic column calculation based on item width
 * 
 * @param widgets List of analytics widgets to display
 * @param minColumnWidth Minimum width for each column (default: 280dp for mobile)
 * @param windowSizeClass Window size classification for responsive adjustments
 * @param onWidgetClick Callback for widget interactions
 * @param widgetDataProvider Function to provide data for each widget
 * @param isLoading Loading state for all widgets
 * @param modifier Modifier for styling the grid
 */
@Composable
fun AdaptiveWidgetGridWithMinWidth(
    widgets: List<AnalyticsWidget>,
    modifier: Modifier = Modifier,
    minColumnWidth: Dp = 280.dp,
    windowSizeClass: WindowSizeClass = rememberWindowSizeClass(),
    onWidgetClick: (AnalyticsWidget) -> Unit = {},
    widgetDataProvider: (AnalyticsWidget) -> WidgetData = { createDefaultWidgetData(it) },
    isLoading: Boolean = false
) {
    // Enhanced minimum width calculation for better responsive behavior
    val adjustedMinWidth = when {
        windowSizeClass.widthDp < 400.dp -> 280.dp // Very compact - fill width
        windowSizeClass.widthDp < 600.dp -> minOf(minColumnWidth, 320.dp) // Compact
        windowSizeClass.widthDp < 905.dp -> minOf(minColumnWidth, 300.dp) // Medium
        else -> minOf(minColumnWidth, windowSizeClass.recommendedMinWidgetWidth) // Expanded
    }
    
    val spacing = when {
        windowSizeClass.widthDp < 400.dp -> GridSystem.spacing2 // 8dp - very compact
        windowSizeClass.widthDp < 600.dp -> GridSystem.spacing2 // 8dp - compact  
        windowSizeClass.widthDp < 905.dp -> GridSystem.spacing3 // 12dp - medium
        else -> GridSystem.spacing4 // 16dp - expanded
    }
    
    val contentPadding = calculateResponsivePadding(windowSizeClass)
    
    LazyVerticalGrid(
        columns = GridCells.Adaptive(adjustedMinWidth),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 800.dp),
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