package com.example.liftrix.ui.progress.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.ui.common.WindowSizeClass
import com.example.liftrix.ui.common.rememberWindowSizeClass
import com.example.liftrix.ui.components.layouts.GridSystem
import com.example.liftrix.domain.model.analytics.DashboardLayoutMode

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
    enableDragAndDrop: Boolean = false,
    enableSmoothTransitions: Boolean = true,
    windowSizeClass: WindowSizeClass = rememberWindowSizeClass(),
    emptyStateMessage: String = "No widgets to display",
    onAddWidgets: (() -> Unit)? = null
) {
    // Determine optimal layout mode considering widget categories (FR-003)
    val effectiveLayoutMode = if (layoutMode == DashboardLayoutMode.DEFAULT) {
        DashboardLayoutMode.getOptimalModeForWidgets(
            widgets = widgets,
            screenWidthDp = windowSizeClass.widthDp.value.toInt(),
            userPreference = null
        )
    } else {
        layoutMode
    }
    
    // Enable drag-and-drop only for CUSTOM mode
    val effectiveEnableDragAndDrop = enableDragAndDrop || effectiveLayoutMode == DashboardLayoutMode.CUSTOM
    // Handle empty state
    if (widgets.isEmpty() && !isLoading) {
        IntegratedEmptyState(
            message = emptyStateMessage,
            onAddWidgets = onAddWidgets,
            modifier = modifier
        )
        return
    }
    if (enableSmoothTransitions) {
        // Use animated layout switcher for smooth transitions
        AnimatedLayoutSwitcher(
            widgets = widgets,
            configuration = configuration,
            layoutMode = effectiveLayoutMode,
            onWidgetClick = onWidgetClick,
            onWidgetReorder = onWidgetReorder,
            widgetDataProvider = widgetDataProvider,
            isLoading = isLoading,
            enableDragAndDrop = effectiveEnableDragAndDrop,
            windowSizeClass = windowSizeClass,
            modifier = modifier
        )
    } else {
        // Direct layout switching without animations (for performance-critical scenarios)
        when (effectiveLayoutMode) {
            DashboardLayoutMode.GRID -> {
                if (effectiveEnableDragAndDrop) {
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
                    enableDragAndDrop = effectiveEnableDragAndDrop,
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
 * Calculates optimal column count with optimized mobile-first approach
 * 
 * Enhanced column calculation for better mobile experience:
 * - Mobile (< 600dp): 2 columns for compact widget layout
 * - Tablet (600dp-904dp): 3 columns for optimal tablet experience
 * - Desktop (≥ 905dp): 4 columns for large displays
 * 
 * @param windowSizeClass Current window size classification
 * @param widgetCount Number of widgets to display (not used for consistent experience)
 * @param maxColumns Maximum allowed columns (default: 4)
 * @param force2x1Mobile Force 2x1 layout (one per row) for mobile screens
 * @return Optimal number of columns for the layout
 */
fun calculateOptimalColumns(
    windowSizeClass: WindowSizeClass,
    widgetCount: Int,
    maxColumns: Int = 4,
    force2x1Mobile: Boolean = false
): Int {
    return when {
        windowSizeClass.widthDp < 600.dp -> {
            // Mobile: 2 columns for optimized widget layout
            2
        }
        windowSizeClass.widthDp < 768.dp -> {
            // Tablet: 3 columns for optimal readability
            3
        }
        else -> {
            // Desktop: 4 columns maximum
            minOf(4, maxColumns)
        }
    }
}

/**
 * Determines responsive spacing based on window size class and content density
 * 
 * Enhanced spacing calculation for tighter, more symmetrical layout (FR-005):
 * - Very compact (320dp-399dp): 10dp for tight mobile layouts
 * - Compact (400dp-599dp): 10dp for standard mobile layouts  
 * - Medium (600dp-904dp): 12dp for tablet portrait/phone landscape
 * - Expanded (905dp+): 14dp for large displays and desktop
 * 
 * @param windowSizeClass Current window size classification
 * @param useCompactSpacing Whether to use compact spacing for dense layouts
 * @return Spacing value following 8dp grid system
 */
fun calculateResponsiveSpacing(
    windowSizeClass: WindowSizeClass,
    useCompactSpacing: Boolean = false
): Arrangement.HorizontalOrVertical {
    // Optimized spacing for tighter, more symmetrical layout (FR-005)
    val spacing = if (useCompactSpacing) {
        8.dp // Compact mode for high-density layouts
    } else {
        when {
            windowSizeClass.widthDp < 600.dp -> 10.dp // Tighter mobile spacing
            windowSizeClass.widthDp < 768.dp -> 12.dp // Medium spacing
            else -> 14.dp // Comfortable desktop spacing
        }
    }
    
    return Arrangement.spacedBy(spacing)
}

/**
 * Calculates responsive content padding for tighter, more symmetrical layout (FR-005)
 * 
 * Optimized padding calculation for better visual balance:
 * - Very compact (320dp-399dp): 14dp for small phones
 * - Compact (400dp-599dp): 16dp for standard phones
 * - Medium (600dp-904dp): 18dp for tablets
 * - Expanded (905dp+): 20dp for large displays
 * 
 * @param windowSizeClass Current window size classification
 * @param useScreenEdgePadding Whether to include additional screen edge padding
 * @return PaddingValues for content area
 */
fun calculateResponsivePadding(
    windowSizeClass: WindowSizeClass,
    useScreenEdgePadding: Boolean = true
): PaddingValues {
    // Tighter, more symmetric padding calculation (FR-005)
    val basePadding = when {
        windowSizeClass.widthDp < 400.dp -> 14.dp // Slightly increased for better balance
        windowSizeClass.widthDp < 600.dp -> 16.dp // Standard mobile
        windowSizeClass.widthDp < 768.dp -> 18.dp // Tighter tablet padding
        else -> 20.dp // Reduced desktop padding for better symmetry
    }
    
    // Minimal edge padding for better content utilization
    val horizontalPadding = if (useScreenEdgePadding && windowSizeClass.widthDp > 768.dp) {
        basePadding + 4.dp // Reduced from 8dp to 4dp
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
    // Clean zero-state display per FR-004
    val (defaultValue, defaultUnit) = when (widget) {
        com.example.liftrix.domain.model.analytics.AnalyticsWidget.TotalVolume -> "0" to "kg"
        com.example.liftrix.domain.model.analytics.AnalyticsWidget.WorkoutStreak -> "0" to "days"
        com.example.liftrix.domain.model.analytics.AnalyticsWidget.AverageDuration -> "0" to "min"
        com.example.liftrix.domain.model.analytics.AnalyticsWidget.WorkoutFrequency -> "0" to "workouts"
        com.example.liftrix.domain.model.analytics.AnalyticsWidget.StrengthProgress -> "0" to "kg"
        com.example.liftrix.domain.model.analytics.AnalyticsWidget.PersonalRecords -> "0" to "records"
        com.example.liftrix.domain.model.analytics.AnalyticsWidget.OneRMProgression -> "0" to "kg"
        else -> "0" to ""
    }
    
    return com.example.liftrix.domain.model.analytics.BasicWidgetData(
        widgetType = widget,
        lastUpdated = kotlinx.datetime.Clock.System.now(),
        primaryValue = defaultValue,
        secondaryValue = null, // Remove secondary messaging
        unit = defaultUnit,
        trend = com.example.liftrix.domain.model.analytics.TrendDirection.STABLE
    )
}

/**
 * Integrated empty state component for ResponsiveDashboardLayout
 * Provides consistent empty state experience across all layout modes
 */
@Composable
private fun IntegratedEmptyState(
    message: String,
    onAddWidgets: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Dashboard,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            onAddWidgets?.let { action ->
                Button(
                    onClick = action,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Add Widgets")
                }
            }
        }
    }
}