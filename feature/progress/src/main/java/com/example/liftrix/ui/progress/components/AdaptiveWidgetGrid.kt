package com.example.liftrix.ui.progress.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.app.ActivityManager
import android.content.Context
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.ui.common.WindowSizeClass
import com.example.liftrix.ui.common.rememberWindowSizeClass
import com.example.liftrix.ui.components.layouts.GridSystem
import timber.log.Timber

/**
 * Memory-aware adaptive widget grid with automatic degradation under memory pressure.
 * 
 * Provides automatic column calculation, Material 3 spacing tokens,
 * and smooth transitions during orientation changes. Supports both
 * fixed and staggered grid layouts based on content requirements.
 * 
 * Enhanced features:
 * - Memory pressure handling with automatic widget count limits
 * - Dynamic widget virtualization for performance
 * - Automatic degradation under low memory conditions
 * - Precise column calculation for 320dp to 1200dp+ screen widths
 * - Material 3 spacing with strict 12dp grid per specification
 * - Smooth layout transitions with animation support
 * - Performance optimized with proper key usage and minimal recomposition
 * - Full accessibility support with semantic content descriptions
 * 
 * @param widgets List of analytics widgets to display in grid
 * @param windowSizeClass Window size classification for responsive behavior
 * @param onWidgetClick Callback for widget tap interactions
 * @param widgetDataProvider Function to provide data for each widget
 * @param isLoading Loading state affecting all widgets
 * @param useStaggered Whether to use staggered grid for varied content heights
 * @param maxColumns Maximum number of columns allowed (default: 3)
 * @param maxWidgetsUnderMemoryPressure Maximum widgets to show when memory is low (default: 10)
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
    enableSmoothTransitions: Boolean = true,
    maxWidgetsUnderMemoryPressure: Int = 10
) {
    // Monitor memory pressure and limit widgets if needed
    val context = LocalContext.current
    val isMemoryConstrained = remember { isLowMemory(context) }
    
    // Apply widget limits under memory pressure
    val displayWidgets = if (isMemoryConstrained && widgets.size > maxWidgetsUnderMemoryPressure) {
        Timber.w("Memory pressure detected: limiting widgets from ${widgets.size} to $maxWidgetsUnderMemoryPressure")
        widgets.take(maxWidgetsUnderMemoryPressure)
    } else {
        widgets
    }
    
    // Register memory pressure callbacks
    DisposableEffect(context) {
        val memoryCallback = registerMemoryPressureCallback(context) {
            Timber.w("Memory pressure callback triggered - consider reducing widget count")
        }
        onDispose {
            unregisterMemoryPressureCallback(context, memoryCallback)
        }
    }
    
    val columns = calculateOptimalColumns(
        windowSizeClass = windowSizeClass,
        widgetCount = displayWidgets.size,
        maxColumns = maxColumns
    )
    
    val spacing = 12.dp
    
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
            widgets = displayWidgets,
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
            widgets = displayWidgets,
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
        // Use Start arrangement to ensure odd widgets are left-aligned
        horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        items(
            count = widgets.size,
            key = { index -> widgets[index].id },
            contentType = { index -> widgets[index].category.name },
            span = { index ->
                val widget = widgets[index]
                // CHARTS category widgets should span full width
                if (widget.category == com.example.liftrix.domain.model.analytics.WidgetCategory.CHARTS) {
                    androidx.compose.foundation.lazy.grid.GridItemSpan(columns)
                } else {
                    androidx.compose.foundation.lazy.grid.GridItemSpan(1)
                }
            }
        ) { index ->
            val widget = widgets[index]
            val widgetData = widgetDataProvider(widget)
            
            // Use FullWidthWidgetCard for CHARTS widgets, regular WidgetRenderer for others
            if (widget.category == com.example.liftrix.domain.model.analytics.WidgetCategory.CHARTS) {
                FullWidthWidgetCard(
                    widget = widget,
                    widgetData = widgetData,
                    onClick = { onWidgetClick(widget) },
                    isLoading = isLoading
                )
            } else {
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
            key = { widget -> widget.id },
            contentType = { widget -> widget.category.name }
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
        windowSizeClass.widthDp < 768.dp -> minOf(minColumnWidth, 300.dp) // Medium
        else -> minOf(minColumnWidth, windowSizeClass.recommendedMinWidgetWidth) // Expanded
    }
    
    val spacing = 12.dp
    
    val contentPadding = calculateResponsivePadding(windowSizeClass)
    
    LazyVerticalGrid(
        columns = GridCells.Adaptive(adjustedMinWidth),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 800.dp),
        contentPadding = contentPadding,
        // Use Start arrangement to ensure odd widgets are left-aligned
        horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        items(
            count = widgets.size,
            key = { index -> widgets[index].id },
            contentType = { index -> widgets[index].category.name },
            span = { index ->
                val widget = widgets[index]
                // CHARTS category widgets should span full width
                if (widget.category == com.example.liftrix.domain.model.analytics.WidgetCategory.CHARTS) {
                    androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan)
                } else {
                    androidx.compose.foundation.lazy.grid.GridItemSpan(1)
                }
            }
        ) { index ->
            val widget = widgets[index]
            val widgetData = widgetDataProvider(widget)
            
            // Use FullWidthWidgetCard for CHARTS widgets, regular WidgetRenderer for others
            if (widget.category == com.example.liftrix.domain.model.analytics.WidgetCategory.CHARTS) {
                FullWidthWidgetCard(
                    widget = widget,
                    widgetData = widgetData,
                    onClick = { onWidgetClick(widget) },
                    isLoading = isLoading
                )
            } else {
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

/**
 * Checks if the device is currently under memory pressure
 */
private fun isLowMemory(context: Context): Boolean {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    
    // Consider low memory if available memory is less than 10% of total or below threshold
    val lowMemoryThreshold = memoryInfo.threshold
    val availableMemory = memoryInfo.availMem
    val totalMemory = memoryInfo.totalMem
    val memoryPercentage = (availableMemory.toFloat() / totalMemory.toFloat()) * 100
    
    return memoryInfo.lowMemory || availableMemory < lowMemoryThreshold || memoryPercentage < 10f
}

/**
 * Registers a callback for memory pressure notifications
 */
private fun registerMemoryPressureCallback(
    context: Context,
    onMemoryPressure: () -> Unit
): android.content.ComponentCallbacks2 {
    val callback = object : android.content.ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
            // Not used
        }
        
        override fun onLowMemory() {
            Timber.w("onLowMemory callback triggered")
            onMemoryPressure()
        }
        
        override fun onTrimMemory(level: Int) {
            when (level) {
                android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
                android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
                android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                    Timber.w("onTrimMemory callback triggered with level: $level")
                    onMemoryPressure()
                }
            }
        }
    }
    
    context.registerComponentCallbacks(callback)
    return callback
}

/**
 * Unregisters a memory pressure callback
 */
private fun unregisterMemoryPressureCallback(
    context: Context,
    callback: android.content.ComponentCallbacks2
) {
    context.unregisterComponentCallbacks(callback)
}
