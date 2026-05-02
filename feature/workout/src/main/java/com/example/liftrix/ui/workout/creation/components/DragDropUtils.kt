package com.example.liftrix.ui.workout.creation.components

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.liftrix.domain.model.TemplateExercise

/**
 * State holder for drag-and-drop functionality in exercise lists.
 * 
 * Manages the dragging state, visual feedback, and reordering logic
 * following Material Design drag-and-drop patterns.
 */
@Composable
fun rememberDragDropState(
    lazyListState: LazyListState = rememberLazyListState(),
    onMove: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> }
): DragDropState {
    val hapticFeedback = LocalHapticFeedback.current
    
    return remember(lazyListState) {
        DragDropState(
            lazyListState = lazyListState,
            onMove = onMove,
            onDragStart = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onDragEnd = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        )
    }
}

/**
 * State class for managing drag-and-drop operations
 */
class DragDropState(
    val lazyListState: LazyListState,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    private val onDragStart: () -> Unit = {},
    private val onDragEnd: () -> Unit = {}
) {
    var draggedDistance by mutableFloatStateOf(0f)
        private set
    
    var draggingItemIndex by mutableIntStateOf(-1)
        private set
    
    var isDragging by mutableStateOf(false)
        private set
    
    var draggedItemOffset by mutableStateOf(Offset.Zero)
        private set
    
    var draggedItemSize by mutableStateOf(IntSize.Zero)
        private set
    
    val currentIndexOfDraggedItem: Int?
        get() = if (isDragging) draggingItemIndex else null
    
    private val draggedItem: LazyListItemInfo?
        get() = currentIndexOfDraggedItem?.let {
            lazyListState.getVisibleItemInfoFor(it)
        }
    
    /**
     * Starts a drag operation for the specified item
     */
    fun startDrag(index: Int, offset: Offset, size: IntSize) {
        draggingItemIndex = index
        draggedItemOffset = offset
        draggedItemSize = size
        isDragging = true
        onDragStart()
    }
    
    /**
     * Updates the drag position and handles item reordering
     */
    fun onDrag(offset: Offset) {
        if (!isDragging) return
        
        draggedDistance += offset.y
        
        val draggedItem = draggedItem ?: return
        val startOffset = draggedItem.offset + draggedDistance
        val endOffset = startOffset + draggedItem.size
        
        val targetItem = lazyListState.layoutInfo.visibleItemsInfo.find { item ->
            draggedItem.index != item.index && 
            startOffset < item.offset + item.size / 2 && 
            endOffset > item.offset + item.size / 2
        }
        
        targetItem?.let { target ->
            val fromIndex = draggingItemIndex
            val toIndex = target.index
            
            if (fromIndex != toIndex) {
                onMove(fromIndex, toIndex)
                draggingItemIndex = toIndex
                draggedDistance = 0f
            }
        }
    }
    
    /**
     * Ends the drag operation and resets state
     */
    fun endDrag() {
        if (!isDragging) return
        
        isDragging = false
        draggingItemIndex = -1
        draggedDistance = 0f
        draggedItemOffset = Offset.Zero
        draggedItemSize = IntSize.Zero
        onDragEnd()
    }
}

/**
 * Extension function to get visible item info for a specific index
 */
private fun LazyListState.getVisibleItemInfoFor(index: Int): LazyListItemInfo? {
    return this.layoutInfo.visibleItemsInfo.getOrNull(
        index - this.layoutInfo.visibleItemsInfo.first().index
    )
}

/**
 * Modifier for draggable items that provides visual feedback during dragging
 */
fun Modifier.dragVisualIndicator(
    dragDropState: DragDropState,
    itemIndex: Int
): Modifier = composed {
    val isDraggedItem = dragDropState.currentIndexOfDraggedItem == itemIndex
    
    this
        .zIndex(if (isDraggedItem) 1f else 0f)
        .graphicsLayer {
            val dragOffset = if (isDraggedItem) dragDropState.draggedDistance else 0f
            translationY = dragOffset
            alpha = if (isDraggedItem) 0.8f else 1f
            scaleX = if (isDraggedItem) 1.02f else 1f
            scaleY = if (isDraggedItem) 1.02f else 1f
        }
        .shadow(
            elevation = if (isDraggedItem) 8.dp else 0.dp,
            clip = false
        )
}

/**
 * Modifier for the drag handle that users interact with to start dragging
 */
fun Modifier.dragHandle(): Modifier = this

/**
 * Data class representing the current drag operation
 */
data class DragOperation(
    val isDragging: Boolean = false,
    val draggedItemIndex: Int = -1,
    val draggedDistance: Float = 0f,
    val targetIndex: Int? = null
) 