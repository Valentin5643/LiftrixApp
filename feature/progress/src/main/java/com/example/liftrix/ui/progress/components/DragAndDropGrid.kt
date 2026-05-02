package com.example.liftrix.ui.progress.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.ui.accessibility.AccessibilityUtils
import com.example.liftrix.ui.accessibility.TalkBackAnnouncements
import com.example.liftrix.ui.common.WindowSizeClass
import com.example.liftrix.ui.theme.LiftrixTokens
import com.example.liftrix.ui.progress.components.widgets.WidgetFactory
import kotlinx.coroutines.launch
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Drag-and-drop enabled grid for analytics widgets.
 * 
 * Provides comprehensive drag-and-drop functionality using Compose gesture detection
 * with Material 3 visual feedback, haptic feedback, snap-to-grid behavior, and 
 * accessibility support. Maintains 60fps performance during gesture tracking.
 * 
 * Features:
 * - Touch gesture detection with drag threshold
 * - Visual feedback with elevation changes and shadows
 * - Haptic feedback on drag start, snap-to-grid, and drop
 * - Snap-to-grid calculations for precise positioning
 * - Optimistic UI updates with immediate reordering
 * - Accessibility support with alternative reorder methods
 * - Performance-optimized rendering during drag operations
 * 
 * @param widgets List of analytics widgets to display
 * @param windowSizeClass Window size class for responsive column calculation
 * @param onReorder Callback for widget reordering with from/to indices
 * @param onWidgetClick Callback for widget click interactions
 * @param widgetDataProvider Provider function for widget data
 * @param isLoading Loading state for the entire grid
 * @param modifier Modifier for styling the grid
 */
@Composable
fun DragAndDropGrid(
    widgets: List<AnalyticsWidget>,
    windowSizeClass: WindowSizeClass,
    onReorder: (from: Int, to: Int) -> Unit,
    onWidgetClick: (AnalyticsWidget) -> Unit = {},
    widgetDataProvider: (AnalyticsWidget) -> WidgetData = { createDefaultWidgetData(it) },
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Drag state management
    var draggedWidgetIndex by remember { mutableIntStateOf(-1) }
    var draggedWidget by remember { mutableStateOf<AnalyticsWidget?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var dropTargetIndex by remember { mutableIntStateOf(-1) }
    
    // Widget positions for drag calculations
    val widgetPositions = remember { mutableMapOf<Int, Offset>() }
    
    // Animation and feedback
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val dragElevation = remember { Animatable(0f) }
    
    // Responsive column calculation
    val columnsCount = windowSizeClass.calculateOptimalColumns(maxColumns = 3)
    val spacing = windowSizeClass.getResponsiveSpacing(
        compactSpacing = 8.dp,
        mediumSpacing = 12.dp,
        expandedSpacing = 16.dp
    )
    val contentPadding = PaddingValues(windowSizeClass.getResponsivePadding())
    
    // Reset drag state function
    val resetDragState: () -> Unit = {
        isDragging = false
        draggedWidgetIndex = -1
        draggedWidget = null
        dragOffset = Offset.Zero
        dropTargetIndex = -1
    }
    
    // Reset drag state when widgets change
    LaunchedEffect(widgets.size) {
        if (draggedWidgetIndex >= widgets.size) {
            resetDragState()
        }
    }
    
    // Animate elevation changes
    LaunchedEffect(isDragging) {
        dragElevation.animateTo(
            targetValue = if (isDragging) LiftrixTokens.Elevation.Level4.value else 0f,
            animationSpec = tween(durationMillis = 200)
        )
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .run {
                with(AccessibilityUtils) {
                    dragDropGridAccessibilitySemantics(
                        totalWidgets = widgets.size,
                        isDragging = isDragging,
                        draggedWidgetName = draggedWidget?.displayName
                    )
                }
            }
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columnsCount),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 800.dp)
                .pointerInput(widgets.size) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val targetIndex = findWidgetAtPosition(offset, widgetPositions, columnsCount)
                            if (targetIndex >= 0 && targetIndex < widgets.size) {
                                draggedWidgetIndex = targetIndex
                                draggedWidget = widgets[targetIndex]
                                isDragging = true
                                dragOffset = Offset.Zero
                                dropTargetIndex = targetIndex
                                
                                // Haptic feedback on drag start
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDrag = { _, dragAmount ->
                            if (isDragging && draggedWidgetIndex >= 0) {
                                dragOffset += dragAmount
                                
                                // Calculate potential drop target
                                val currentPosition = widgetPositions[draggedWidgetIndex]
                                if (currentPosition != null) {
                                    val targetPosition = currentPosition + dragOffset
                                    val newTargetIndex = findWidgetAtPosition(
                                        targetPosition, 
                                        widgetPositions, 
                                        columnsCount
                                    )
                                    
                                    if (newTargetIndex >= 0 && newTargetIndex < widgets.size && 
                                        newTargetIndex != dropTargetIndex) {
                                        dropTargetIndex = newTargetIndex
                                        // Haptic feedback for snap-to-grid
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            val wasSuccessfulDrop = isDragging && draggedWidgetIndex >= 0 && dropTargetIndex >= 0 && 
                                draggedWidgetIndex != dropTargetIndex
                            
                            if (wasSuccessfulDrop) {
                                // Perform reorder operation
                                onReorder(draggedWidgetIndex, dropTargetIndex)
                                
                                // Haptic feedback on successful drop
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            
                            // Reset drag state
                            resetDragState()
                        }
                    )
                },
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            itemsIndexed(widgets) { index, widget ->
                val widgetData = widgetDataProvider(widget)
                val isBeingDragged = isDragging && index == draggedWidgetIndex
                val isDropTarget = isDragging && index == dropTargetIndex && index != draggedWidgetIndex
                
                // Accessibility announcements for drag operations
                LaunchedEffect(isBeingDragged) {
                    if (isBeingDragged) {
                        // Drag start announcement moved to onDragStart callback
                    }
                }
                
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { coordinates ->
                            widgetPositions[index] = coordinates.positionInParent()
                        }
                        .run {
                            with(AccessibilityUtils) {
                                widgetAccessibilitySemantics(
                                    widget = widget,
                                    widgetData = widgetData,
                                    position = index,
                                    totalWidgets = widgets.size,
                                    isEnabled = true,
                                    isLoading = isLoading,
                                    onMoveUp = if (index > 0) {
                                        { 
                                            onReorder(index, index - 1)
                                            // Note: TalkBack announcements moved to higher level
                                        }
                                    } else null,
                                    onMoveDown = if (index < widgets.size - 1) {
                                        { 
                                            onReorder(index, index + 1)
                                            // Note: TalkBack announcements moved to higher level
                                        }
                                    } else null,
                                    onRefresh = {
                                        // Trigger refresh via callback if available
                                    }
                                )
                            }
                        }
                        .then(
                            if (isBeingDragged) {
                                Modifier
                                    .offset {
                                        IntOffset(
                                            x = dragOffset.x.roundToInt(),
                                            y = dragOffset.y.roundToInt()
                                        )
                                    }
                                    .zIndex(1000f)
                                    .graphicsLayer {
                                        shadowElevation = dragElevation.value
                                        alpha = 0.9f
                                        scaleX = 1.05f
                                        scaleY = 1.05f
                                    }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    // Drop target indicator
                    if (isDropTarget) {
                        DropTargetIndicator(
                            modifier = Modifier.fillMaxSize(),
                            isActive = true
                        )
                    }
                    
                    // Widget renderer with drag preview overlay
                    if (isBeingDragged) {
                        DragPreview(
                            widget = widget,
                            widgetData = widgetData,
                            onWidgetClick = onWidgetClick,
                            isLoading = isLoading
                        )
                    } else {
                        WidgetFactory.CreateWidget(
                            widget = widget,
                            data = widgetData,
                            onClick = { onWidgetClick(widget) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        
        // Placement Preview - Shows where widget will be positioned before releasing drag
        if (isDragging && draggedWidgetIndex >= 0 && dropTargetIndex >= 0 && 
            draggedWidgetIndex != dropTargetIndex && widgets.isNotEmpty()) {
            
            val widgetDataMap = widgets.associate { widget ->
                widget.id to widgetDataProvider(widget)
            }
            
            PlacementPreview(
                widgets = widgets,
                draggedWidgetIndex = draggedWidgetIndex,
                targetIndex = dropTargetIndex,
                isActive = true,
                widgetDataMap = widgetDataMap,
                onWidgetClick = { }, // Disabled during preview
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(500f) // Below dragged widget but above normal grid
            )
        }
    }
    
}

/**
 * Finds the widget index at a given position using grid calculations
 */
private fun findWidgetAtPosition(
    position: Offset,
    widgetPositions: Map<Int, Offset>,
    columnsCount: Int
): Int {
    if (widgetPositions.isEmpty()) return -1
    
    // Find the closest widget position
    var closestIndex = -1
    var closestDistance = Float.MAX_VALUE
    
    widgetPositions.forEach { (index, widgetPosition) ->
        val distance = (position - widgetPosition).getDistanceSquared()
        if (distance < closestDistance) {
            closestDistance = distance
            closestIndex = index
        }
    }
    
    return closestIndex
}

/**
 * Creates default widget data for widgets without specific data
 */
private fun createDefaultWidgetData(widget: AnalyticsWidget): WidgetData {
    return com.example.liftrix.domain.model.analytics.BasicWidgetData(
        widgetType = widget,
        lastUpdated = kotlinx.datetime.Clock.System.now(),
        primaryValue = "0",
        secondaryValue = "Start working out to see data",
        unit = "",
        trend = com.example.liftrix.domain.model.analytics.TrendDirection.STABLE
    )
}

/**
 * Extension function for responsive column calculation
 */
private fun WindowSizeClass.calculateOptimalColumns(maxColumns: Int = 3): Int {
    return when {
        widthDp.value < 400 -> 1
        widthDp.value < 600 -> 2
        else -> maxColumns
    }
}

/**
 * Extension function for responsive spacing calculation
 */
private fun WindowSizeClass.getResponsiveSpacing(
    compactSpacing: androidx.compose.ui.unit.Dp,
    mediumSpacing: androidx.compose.ui.unit.Dp,
    expandedSpacing: androidx.compose.ui.unit.Dp
): androidx.compose.ui.unit.Dp {
    return when {
        widthDp.value < 400 -> compactSpacing
        widthDp.value < 600 -> mediumSpacing
        else -> expandedSpacing
    }
}

/**
 * Extension function for responsive padding calculation
 */
private fun WindowSizeClass.getResponsivePadding(): androidx.compose.ui.unit.Dp {
    return when {
        widthDp.value < 400 -> 12.dp
        widthDp.value < 600 -> 16.dp
        else -> 20.dp
    }
}