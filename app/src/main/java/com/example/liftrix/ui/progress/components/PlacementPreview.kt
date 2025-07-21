package com.example.liftrix.ui.progress.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.ui.theme.LiftrixTokens

/**
 * Enhanced placement preview component that shows users where their widget will be positioned
 * before they release the drag operation.
 * 
 * This component addresses the user's request to "get a preview before letting go of the widgets"
 * by showing a live preview of the final dashboard layout with the widget in its target position.
 * 
 * Features:
 * - Live layout preview showing final widget arrangement
 * - Animated preview indicators with Material 3 design
 * - Clear visual distinction between preview and actual widgets
 * - Accessibility support with descriptive labels
 * - Smooth transitions for preview state changes
 * - Performance-optimized rendering for real-time updates
 * 
 * @param widgets List of all widgets in their current order
 * @param draggedWidgetIndex Index of the widget being dragged
 * @param targetIndex Target index where the widget will be placed
 * @param isActive Whether the placement preview is currently active
 * @param widgetDataMap Map of widget IDs to their data
 * @param onWidgetClick Click handler for widgets (disabled during preview)
 * @param modifier Modifier for styling the preview container
 */
@Composable
fun PlacementPreview(
    widgets: List<AnalyticsWidget>,
    draggedWidgetIndex: Int,
    targetIndex: Int,
    isActive: Boolean = true,
    widgetDataMap: Map<String, WidgetData> = emptyMap(),
    onWidgetClick: (AnalyticsWidget) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Calculate the preview layout
    val previewWidgets = remember(widgets, draggedWidgetIndex, targetIndex) {
        calculatePreviewLayout(widgets, draggedWidgetIndex, targetIndex)
    }
    
    // Animation states for smooth preview transitions
    val previewAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.9f else 0.0f,
        animationSpec = tween(durationMillis = 300),
        label = "placement_preview_alpha"
    )
    
    val previewScale by animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.95f,
        animationSpec = tween(durationMillis = 250),
        label = "placement_preview_scale"
    )
    
    val overlayAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.8f else 0.0f,
        animationSpec = tween(durationMillis = 200),
        label = "placement_overlay_alpha"
    )
    
    if (isActive && draggedWidgetIndex >= 0 && targetIndex >= 0 && previewWidgets.isNotEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .alpha(previewAlpha)
                .scale(previewScale)
                .semantics {
                    contentDescription = "Widget placement preview - shows where widget will be positioned"
                }
        ) {
            // Semi-transparent overlay background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha),
                        shape = RoundedCornerShape(LiftrixTokens.CornerRadius.Large)
                    )
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(LiftrixTokens.CornerRadius.Large)
                    )
            )
            
            // Preview header with instruction
            PreviewHeader(
                draggedWidget = widgets.getOrNull(draggedWidgetIndex),
                targetIndex = targetIndex,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            )
            
            // Widget grid preview
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 80.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                itemsIndexed(previewWidgets) { index, previewItem ->
                    PlacementPreviewWidget(
                        widget = previewItem.widget,
                        widgetData = widgetDataMap[previewItem.widget.id],
                        isPreviewTarget = previewItem.isPreviewTarget,
                        isOriginalPosition = previewItem.isOriginalPosition,
                        previewIndex = index,
                        onWidgetClick = { }, // Disabled during preview
                        modifier = Modifier.height(120.dp)
                    )
                }
            }
        }
    }
}

/**
 * Header component showing preview instructions and context
 */
@Composable
private fun PreviewHeader(
    draggedWidget: AnalyticsWidget?,
    targetIndex: Int,
    modifier: Modifier = Modifier
) {
    if (draggedWidget != null) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(LiftrixTokens.CornerRadius.Medium)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SwapVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                
                Text(
                    text = "Preview: ${draggedWidget.displayName} → Position ${targetIndex + 1}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * Individual widget preview component with visual state indicators
 */
@Composable
private fun PlacementPreviewWidget(
    widget: AnalyticsWidget,
    widgetData: WidgetData?,
    isPreviewTarget: Boolean,
    isOriginalPosition: Boolean,
    previewIndex: Int,
    onWidgetClick: (AnalyticsWidget) -> Unit,
    modifier: Modifier = Modifier
) {
    // Animation states for different preview states
    val targetAlpha by animateFloatAsState(
        targetValue = when {
            isPreviewTarget -> 1.0f
            isOriginalPosition -> 0.3f
            else -> 0.8f
        },
        animationSpec = tween(durationMillis = 200),
        label = "preview_widget_alpha"
    )
    
    val targetScale by animateFloatAsState(
        targetValue = if (isPreviewTarget) 1.05f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "preview_widget_scale"
    )
    
    val borderColor by animateColorAsState(
        targetValue = when {
            isPreviewTarget -> MaterialTheme.colorScheme.primary
            isOriginalPosition -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 200),
        label = "preview_widget_border_color"
    )
    
    Card(
        modifier = modifier
            .scale(targetScale)
            .alpha(targetAlpha)
            .border(
                width = if (isPreviewTarget) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(LiftrixTokens.CornerRadius.Medium)
            )
            .semantics {
                contentDescription = when {
                    isPreviewTarget -> "Preview position for ${widget.displayName}"
                    isOriginalPosition -> "Original position of ${widget.displayName}"
                    else -> widget.displayName
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = when {
                isPreviewTarget -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                isOriginalPosition -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPreviewTarget) 6.dp else 2.dp
        ),
        shape = RoundedCornerShape(LiftrixTokens.CornerRadius.Medium)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Background effect for preview target
            if (isPreviewTarget) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    Color.Transparent
                                ),
                                radius = 100f
                            )
                        )
                )
            }
            
            // Widget content or simplified preview
            if (widgetData != null) {
                // Render simplified version of the widget
                com.example.liftrix.ui.progress.components.widgets.WidgetFactory.CreateWidget(
                    widget = widget,
                    data = widgetData,
                    onClick = { },
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.7f) // Make it more transparent for preview
                )
            } else {
                // Fallback display with widget name
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = widget.displayName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.sp,
                            fontWeight = if (isPreviewTarget) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                    
                    if (isPreviewTarget) {
                        Text(
                            text = "NEW POSITION",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            
            // Position indicator
            if (isPreviewTarget || isOriginalPosition) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .background(
                            color = when {
                                isPreviewTarget -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                            },
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${previewIndex + 1}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (isPreviewTarget) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

/**
 * Data class representing a widget in the preview layout
 */
private data class PreviewWidgetItem(
    val widget: AnalyticsWidget,
    val isPreviewTarget: Boolean = false,
    val isOriginalPosition: Boolean = false
)

/**
 * Calculates the preview layout showing where the widget will be placed
 */
private fun calculatePreviewLayout(
    widgets: List<AnalyticsWidget>,
    draggedWidgetIndex: Int,
    targetIndex: Int
): List<PreviewWidgetItem> {
    if (draggedWidgetIndex < 0 || targetIndex < 0 || widgets.isEmpty()) {
        return emptyList()
    }
    
    val workingList = widgets.toMutableList()
    val draggedWidget = workingList.getOrNull(draggedWidgetIndex) ?: return emptyList()
    
    // Remove the dragged widget from its original position
    workingList.removeAt(draggedWidgetIndex)
    
    // Insert it at the target position
    val safeTargetIndex = targetIndex.coerceIn(0, workingList.size)
    workingList.add(safeTargetIndex, draggedWidget)
    
    // Create preview items with state indicators
    return workingList.mapIndexed { index, widget ->
        PreviewWidgetItem(
            widget = widget,
            isPreviewTarget = widget == draggedWidget && index == safeTargetIndex,
            isOriginalPosition = false // We don't show original position in the preview
        )
    }
}


/**
 * Extension function to safely get an item from a list
 */
private fun <T> List<T>.getOrNull(index: Int): T? {
    return if (index in 0 until size) get(index) else null
}

/**
 * Remember function for Compose
 */
@Composable
private fun <T> remember(vararg keys: Any?, calculation: () -> T): T {
    return androidx.compose.runtime.remember(*keys, calculation = calculation)
}