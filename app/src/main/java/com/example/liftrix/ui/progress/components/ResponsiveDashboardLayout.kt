package com.example.liftrix.ui.progress.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.ui.common.WindowSizeClass
import com.example.liftrix.ui.common.rememberWindowSizeClass
import com.example.liftrix.ui.components.layouts.GridSystem

/**
 * Responsive dashboard layout engine that adapts to different screen sizes.
 * 
 * Implements adaptive layout system using WindowSizeClass and Material 3 principles
 * supporting 1-3 column layouts across device sizes from 320dp to 1200dp+ with
 * smooth transitions and optimal content organization. Enhanced with drag-and-drop
 * support for widget reordering.
 * 
 * Features:
 * - Automatic column calculation based on screen width
 * - Material 3 spacing tokens and 8dp grid alignment
 * - Responsive widget sizing with minimum/maximum constraints
 * - Smooth layout transitions during orientation changes
 * - Foldable device support with layout preservation
 * - Drag-and-drop widget reordering with Material 3 visual feedback
 * 
 * @param widgets List of analytics widgets to display
 * @param configuration Dashboard configuration for layout preferences
 * @param layoutMode Layout mode determining organization pattern
 * @param onWidgetClick Callback for widget interactions
 * @param onWidgetReorder Callback for widget reorder operations
 * @param widgetDataProvider Function to provide data for each widget
 * @param isLoading Loading state for the entire dashboard
 * @param enableDragAndDrop Whether to enable drag-and-drop functionality
 * @param modifier Modifier for styling the layout
 */
@Composable
fun ResponsiveDashboardLayout(
    widgets: List<AnalyticsWidget>,
    configuration: DashboardConfiguration,
    modifier: Modifier = Modifier,
    layoutMode: DashboardLayoutMode = DashboardLayoutMode.DEFAULT,
    onWidgetClick: (AnalyticsWidget) -> Unit = {},
    onWidgetReorder: (from: Int, to: Int) -> Unit = { _, _ -> },
    widgetDataProvider: (AnalyticsWidget) -> WidgetData = { createDefaultWidgetData(it) },
    isLoading: Boolean = false,
    enableDragAndDrop: Boolean = layoutMode == DashboardLayoutMode.CUSTOM,
    enableSmoothTransitions: Boolean = true,
    windowSizeClass: WindowSizeClass = rememberWindowSizeClass()
) {
    if (enableSmoothTransitions) {
        // Use animated layout switcher for smooth transitions
        AnimatedLayoutSwitcher(
            widgets = widgets,
            configuration = configuration,
            layoutMode = layoutMode,
            onWidgetClick = onWidgetClick,
            onWidgetReorder = onWidgetReorder,
            widgetDataProvider = widgetDataProvider,
            isLoading = isLoading,
            enableDragAndDrop = enableDragAndDrop,
            windowSizeClass = windowSizeClass,
            modifier = modifier
        )
    } else {
        // Direct layout switching without animations (for performance-critical scenarios)
        when (layoutMode) {
            DashboardLayoutMode.GRID -> {
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
                    AdaptiveWidgetGrid(
                        widgets = widgets,
                        windowSizeClass = windowSizeClass,
                        onWidgetClick = onWidgetClick,
                        widgetDataProvider = widgetDataProvider,
                        isLoading = isLoading,
                        modifier = modifier
                    )
                }
            }
            
            DashboardLayoutMode.SECTIONS -> {
                // Use existing WidgetContainer sectioned layout but with responsive enhancements
                WidgetContainer(
                    widgets = widgets,
                    configuration = configuration,
                    layoutMode = WidgetLayoutMode.SECTIONS,
                    onWidgetClick = onWidgetClick,
                    onWidgetReorder = onWidgetReorder,
                    widgetDataProvider = widgetDataProvider,
                    isLoading = isLoading,
                    enableCollapsibleSections = windowSizeClass.shouldShowCollapsibleSections,
                    enableDragAndDrop = enableDragAndDrop,
                    windowSizeClass = windowSizeClass,
                    modifier = modifier
                )
            }
            
            DashboardLayoutMode.LIST -> {
                WidgetContainer(
                    widgets = widgets,
                    configuration = configuration,
                    layoutMode = WidgetLayoutMode.LIST,
                    onWidgetClick = onWidgetClick,
                    onWidgetReorder = onWidgetReorder,
                    widgetDataProvider = widgetDataProvider,
                    isLoading = isLoading,
                    enableDragAndDrop = false, // List mode uses individual widgets, not sections
                    windowSizeClass = windowSizeClass,
                    modifier = modifier
                )
            }
            
            DashboardLayoutMode.CUSTOM -> {
                // Enhanced custom layout with advanced drag-and-drop and layout persistence
                CustomizableLayoutGrid(
                    widgets = widgets,
                    windowSizeClass = windowSizeClass,
                    onReorder = onWidgetReorder,
                    onWidgetClick = onWidgetClick,
                    widgetDataProvider = widgetDataProvider,
                    isLoading = isLoading,
                    isCustomLayoutMode = true, // Always enable custom mode features
                    onLayoutSave = { customLayout ->
                        // TODO: Implement custom layout persistence
                        // This would typically be handled by a ViewModel or UseCase
                    },
                    modifier = modifier
                )
            }
        }
    }
}

/**
 * Calculates optimal column count with consistent mobile-first approach
 * 
 * Simplified column calculation for seamless mobile experience:
 * - Mobile (< 600dp): 1 column (2x1 layout - one widget per row)
 * - Tablet (600dp-904dp): 2 columns for optimal tablet experience
 * - Desktop (≥ 905dp): 3 columns for large displays
 * 
 * @param windowSizeClass Current window size classification
 * @param widgetCount Number of widgets to display (not used for consistent experience)
 * @param maxColumns Maximum allowed columns (default: 3)
 * @param force2x1Mobile Force 2x1 layout (one per row) for mobile screens
 * @return Optimal number of columns for the layout
 */
fun calculateOptimalColumns(
    windowSizeClass: WindowSizeClass,
    widgetCount: Int,
    maxColumns: Int = 3,
    force2x1Mobile: Boolean = true
): Int {
    return when {
        windowSizeClass.widthDp < 600.dp -> {
            // Mobile: 1 column for 2x1 layout (one widget per row)
            1
        }
        windowSizeClass.widthDp < 905.dp -> {
            // Tablet: 2 columns for optimal readability
            2
        }
        else -> {
            // Desktop: 3 columns maximum
            minOf(3, maxColumns)
        }
    }
}

/**
 * Determines responsive spacing based on window size class and content density
 * 
 * Enhanced spacing calculation following Material 3 principles and 8dp grid:
 * - Very compact (320dp-399dp): 8dp for tight mobile layouts
 * - Compact (400dp-599dp): 8dp for standard mobile layouts  
 * - Medium (600dp-904dp): 12dp for tablet portrait/phone landscape
 * - Expanded (905dp+): 16dp for large displays and desktop
 * 
 * @param windowSizeClass Current window size classification
 * @param useCompactSpacing Whether to use compact spacing for dense layouts
 * @return Spacing value following 8dp grid system
 */
fun calculateResponsiveSpacing(
    windowSizeClass: WindowSizeClass,
    useCompactSpacing: Boolean = false
): Arrangement.HorizontalOrVertical {
    val spacing = when {
        windowSizeClass.widthDp < 400.dp -> GridSystem.spacing2 // 8dp - very compact
        windowSizeClass.widthDp < 600.dp -> {
            if (useCompactSpacing) GridSystem.spacing2 else GridSystem.spacing2 // 8dp - compact
        }
        windowSizeClass.widthDp < 905.dp -> {
            if (useCompactSpacing) GridSystem.spacing2 else GridSystem.spacing3 // 8dp/12dp - medium
        }
        else -> {
            if (useCompactSpacing) GridSystem.spacing3 else GridSystem.spacing4 // 12dp/16dp - expanded
        }
    }
    
    return Arrangement.spacedBy(spacing)
}

/**
 * Calculates responsive content padding following Material 3 principles
 * 
 * Enhanced padding calculation for optimal content spacing:
 * - Very compact (320dp-399dp): 12dp for small phones
 * - Compact (400dp-599dp): 16dp for standard phones
 * - Medium (600dp-904dp): 20dp for tablets
 * - Expanded (905dp+): 24dp for large displays
 * 
 * @param windowSizeClass Current window size classification
 * @param useScreenEdgePadding Whether to include additional screen edge padding
 * @return PaddingValues for content area
 */
fun calculateResponsivePadding(
    windowSizeClass: WindowSizeClass,
    useScreenEdgePadding: Boolean = true
): PaddingValues {
    val basePadding = when {
        windowSizeClass.widthDp < 400.dp -> 12.dp // Very compact
        windowSizeClass.widthDp < 600.dp -> GridSystem.screenPadding // 16dp - compact
        windowSizeClass.widthDp < 905.dp -> 20.dp // Medium
        else -> GridSystem.spacing5 // 24dp - expanded
    }
    
    // Add extra padding for screen edges on larger displays
    val horizontalPadding = if (useScreenEdgePadding && windowSizeClass.widthDp > 905.dp) {
        basePadding + 8.dp
    } else {
        basePadding
    }
    
    return PaddingValues(
        horizontal = horizontalPadding,
        vertical = basePadding
    )
}

/**
 * Creates default widget data for preview and error states
 */
private fun createDefaultWidgetData(widget: AnalyticsWidget): WidgetData {
    return com.example.liftrix.domain.model.analytics.BasicWidgetData(
        widgetType = widget,
        lastUpdated = kotlinx.datetime.Clock.System.now(),
        primaryValue = "—",
        secondaryValue = "No data available",
        unit = "",
        trend = com.example.liftrix.domain.model.analytics.TrendDirection.STABLE
    )
}