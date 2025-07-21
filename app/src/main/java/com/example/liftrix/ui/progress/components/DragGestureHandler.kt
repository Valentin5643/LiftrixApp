package com.example.liftrix.ui.progress.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Advanced gesture handler for drag-and-drop widget operations.
 * 
 * Manages complex drag gesture state including touch event processing,
 * coordinate-based snap-to-grid calculations, haptic feedback integration,
 * and performance optimization for 60fps rendering during gesture tracking.
 * 
 * Features:
 * - Touch threshold detection to prevent accidental drags
 * - Coordinate-based grid snapping with visual feedback
 * - Performance-optimized state updates for smooth animations
 * - Comprehensive haptic feedback integration
 * - Drag boundaries and collision detection
 * - Spring-based animation for smooth snap-to-grid behavior
 * 
 * @param widgets Current list of widgets for position calculations
 * @param gridColumns Number of columns in the grid layout
 * @param onReorder Callback for executing widget reorder operations
 * @param coroutineScope Coroutine scope for managing animations
 * @param density Density for converting between pixels and dp
 */
@Stable
class DragGestureHandler(
    private val widgets: List<AnalyticsWidget>,
    private val gridColumns: Int,
    private val onReorder: (from: Int, to: Int) -> Unit,
    private val coroutineScope: CoroutineScope,
    private val density: Density
) {
    
    // Drag state properties
    var isDragging by mutableStateOf(false)
        private set
    
    var draggedWidgetIndex by mutableIntStateOf(-1)
        private set
    
    var draggedWidget by mutableStateOf<AnalyticsWidget?>(null)
        private set
    
    var currentDragOffset by mutableStateOf(Offset.Zero)
        private set
    
    var dropTargetIndex by mutableIntStateOf(-1)
        private set
    
    var startDragPosition by mutableStateOf(Offset.Zero)
        private set
    
    // Performance optimization properties
    private var lastSnapPosition by mutableStateOf(Offset.Zero)
    private var dragThreshold by mutableFloatStateOf(with(density) { 8.dp.toPx() })
    private var hasExceededThreshold by mutableStateOf(false)
    
    // Animation properties
    private val snapAnimationX = Animatable(0f)
    private val snapAnimationY = Animatable(0f)
    private val elevationAnimation = Animatable(0f)
    
    // Widget position tracking
    private val widgetPositions = mutableMapOf<Int, Offset>()
    private val widgetSizes = mutableMapOf<Int, Offset>()
    
    // Grid calculation properties
    private var itemWidth by mutableFloatStateOf(0f)
    private var itemHeight by mutableFloatStateOf(0f)
    private var gridSpacing by mutableFloatStateOf(with(density) { 12.dp.toPx() })
    
    /**
     * Handles drag start gesture with threshold detection
     */
    fun onDragStart(startPosition: Offset, hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback) {
        val targetIndex = findWidgetIndexAtPosition(startPosition)
        
        if (targetIndex >= 0 && targetIndex < widgets.size) {
            draggedWidgetIndex = targetIndex
            draggedWidget = widgets[targetIndex]
            startDragPosition = startPosition
            currentDragOffset = Offset.Zero
            dropTargetIndex = targetIndex
            hasExceededThreshold = false
            
            // Start elevation animation
            coroutineScope.launch {
                elevationAnimation.animateTo(
                    targetValue = 12f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
        }
    }
    
    /**
     * Handles drag movement with threshold checking and snap calculations
     */
    fun onDrag(dragAmount: Offset, hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback) {
        if (draggedWidgetIndex < 0) return
        
        currentDragOffset += dragAmount
        
        // Check if drag threshold has been exceeded
        if (!hasExceededThreshold) {
            val totalDragDistance = currentDragOffset.getDistance()
            if (totalDragDistance > dragThreshold) {
                hasExceededThreshold = true
                isDragging = true
                
                // Trigger haptic feedback for drag start
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        
        if (isDragging) {
            updateDropTarget(hapticFeedback)
        }
    }
    
    /**
     * Handles drag end with reorder execution and cleanup
     */
    fun onDragEnd(hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback) {
        if (isDragging && draggedWidgetIndex >= 0 && dropTargetIndex >= 0 && 
            draggedWidgetIndex != dropTargetIndex) {
            
            // Execute reorder operation
            onReorder(draggedWidgetIndex, dropTargetIndex)
            
            // Trigger success haptic feedback
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        
        // Reset drag state with animations
        resetDragState()
    }
    
    /**
     * Updates widget position tracking for accurate drag calculations
     */
    fun updateWidgetPosition(index: Int, position: Offset, size: Offset) {
        widgetPositions[index] = position
        widgetSizes[index] = size
        
        // Update grid metrics for snap calculations
        if (itemWidth == 0f && size.x > 0) {
            itemWidth = size.x
        }
        if (itemHeight == 0f && size.y > 0) {
            itemHeight = size.y
        }
    }
    
    /**
     * Updates grid layout parameters for responsive behavior
     */
    fun updateGridParameters(columns: Int, spacing: Float) {
        if (gridColumns != columns) {
            // Grid layout changed, reset positions
            widgetPositions.clear()
        }
        gridSpacing = spacing
    }
    
    /**
     * Gets current drag elevation for visual feedback
     */
    fun getDragElevation(): Float = elevationAnimation.value
    
    /**
     * Gets snap offset for smooth grid alignment
     */
    fun getSnapOffset(): Offset = Offset(snapAnimationX.value, snapAnimationY.value)
    
    /**
     * Checks if a specific widget is currently being dragged
     */
    fun isWidgetBeingDragged(index: Int): Boolean = 
        isDragging && draggedWidgetIndex == index
    
    /**
     * Checks if a specific widget is a valid drop target
     */
    fun isValidDropTarget(index: Int): Boolean = 
        isDragging && dropTargetIndex == index && index != draggedWidgetIndex
    
    /**
     * Gets the current drag progress for animation purposes
     */
    fun getDragProgress(): Float {
        if (!isDragging || currentDragOffset == Offset.Zero) return 0f
        return (currentDragOffset.getDistance() / (itemWidth + itemHeight)).coerceIn(0f, 1f)
    }
    
    /**
     * Updates drop target based on current drag position
     */
    private fun updateDropTarget(hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback) {
        val currentPosition = widgetPositions[draggedWidgetIndex]
        if (currentPosition == null) return
        
        val draggedPosition = currentPosition + currentDragOffset
        val newTargetIndex = findWidgetIndexAtPosition(draggedPosition)
        
        if (newTargetIndex >= 0 && newTargetIndex < widgets.size && 
            newTargetIndex != dropTargetIndex) {
            
            val previousTarget = dropTargetIndex
            dropTargetIndex = newTargetIndex
            
            // Animate snap to new grid position
            animateSnapToGrid(newTargetIndex)
            
            // Trigger haptic feedback for target change
            if (previousTarget != newTargetIndex) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
    
    /**
     * Animates snap-to-grid behavior for smooth positioning
     */
    private fun animateSnapToGrid(targetIndex: Int) {
        val targetPosition = calculateGridPosition(targetIndex)
        val currentPosition = widgetPositions[draggedWidgetIndex] ?: return
        
        val snapOffset = targetPosition - currentPosition - currentDragOffset
        
        coroutineScope.launch {
            launch {
                snapAnimationX.animateTo(
                    targetValue = snapOffset.x,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            launch {
                snapAnimationY.animateTo(
                    targetValue = snapOffset.y,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }
    }
    
    /**
     * Calculates theoretical grid position for an index
     */
    private fun calculateGridPosition(index: Int): Offset {
        val row = index / gridColumns
        val column = index % gridColumns
        
        return Offset(
            x = column * (itemWidth + gridSpacing),
            y = row * (itemHeight + gridSpacing)
        )
    }
    
    /**
     * Finds widget index at a given position with collision detection
     */
    private fun findWidgetIndexAtPosition(position: Offset): Int {
        if (widgetPositions.isEmpty()) return -1
        
        var closestIndex = -1
        var closestDistance = Float.MAX_VALUE
        
        widgetPositions.forEach { (index, widgetPosition) ->
            val widgetSize = widgetSizes[index] ?: Offset(itemWidth, itemHeight)
            
            // Check if position is within widget bounds
            val isWithinBounds = position.x >= widgetPosition.x && 
                                position.x <= widgetPosition.x + widgetSize.x &&
                                position.y >= widgetPosition.y && 
                                position.y <= widgetPosition.y + widgetSize.y
            
            if (isWithinBounds) {
                return index
            }
            
            // Fallback: find closest widget center
            val widgetCenter = widgetPosition + widgetSize / 2f
            val distance = (position - widgetCenter).getDistanceSquared()
            
            if (distance < closestDistance) {
                closestDistance = distance
                closestIndex = index
            }
        }
        
        return closestIndex
    }
    
    /**
     * Resets all drag state with smooth animations
     */
    private fun resetDragState() {
        coroutineScope.launch {
            // Animate back to original position
            launch {
                snapAnimationX.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            launch {
                snapAnimationY.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            launch {
                elevationAnimation.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }
        
        // Reset state variables
        isDragging = false
        draggedWidgetIndex = -1
        draggedWidget = null
        currentDragOffset = Offset.Zero
        dropTargetIndex = -1
        startDragPosition = Offset.Zero
        hasExceededThreshold = false
        lastSnapPosition = Offset.Zero
    }
}

/**
 * Composable function to create and remember a DragGestureHandler
 */
@Composable
fun rememberDragGestureHandler(
    widgets: List<AnalyticsWidget>,
    gridColumns: Int,
    onReorder: (from: Int, to: Int) -> Unit,
    coroutineScope: CoroutineScope,
    density: Density
): DragGestureHandler {
    val hapticFeedback = LocalHapticFeedback.current
    
    return remember(widgets.size, gridColumns) {
        DragGestureHandler(
            widgets = widgets,
            gridColumns = gridColumns,
            onReorder = onReorder,
            coroutineScope = coroutineScope,
            density = density
        )
    }
}

/**
 * Data class for drag gesture event information
 */
data class DragGestureEvent(
    val type: DragEventType,
    val position: Offset,
    val draggedIndex: Int,
    val targetIndex: Int,
    val dragOffset: Offset
)

/**
 * Enumeration of drag event types
 */
enum class DragEventType {
    DRAG_START,
    DRAG_MOVE,
    DRAG_END,
    DRAG_CANCEL,
    TARGET_CHANGED
}