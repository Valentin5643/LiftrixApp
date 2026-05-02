package com.example.liftrix.ui.progress.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.ui.theme.LiftrixTokens

/**
 * Drag preview component with Material 3 visual feedback.
 * 
 * Provides sophisticated visual feedback during drag operations with elevation animations,
 * Material 3 shadow effects, smooth transitions, and enhanced visual hierarchy.
 * Maintains consistent Material 3 design language while providing clear drag state feedback.
 * 
 * Features:
 * - Material 3 elevation system with animated shadows
 * - Smooth scale and alpha transitions for drag feedback
 * - Enhanced visual hierarchy with accent borders
 * - Performance-optimized rendering for 60fps during drag
 * - Consistent theming with existing widget components
 * - Accessibility-friendly visual feedback
 * 
 * @param widget The analytics widget being dragged
 * @param widgetData Current data for the widget
 * @param onWidgetClick Click handler for the widget (disabled during drag)
 * @param isLoading Loading state for the widget
 * @param isDragging Whether this preview is currently being dragged
 * @param dragProgress Progress of the drag operation (0.0 to 1.0)
 * @param modifier Modifier for styling the preview
 */
@Composable
fun DragPreview(
    widget: AnalyticsWidget,
    widgetData: WidgetData,
    onWidgetClick: (AnalyticsWidget) -> Unit = {},
    isLoading: Boolean = false,
    isDragging: Boolean = true,
    dragProgress: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    // Animation states for smooth visual feedback
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.05f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "drag_preview_scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isDragging) 0.95f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "drag_preview_alpha"
    )
    
    val elevation by animateFloatAsState(
        targetValue = if (isDragging) LiftrixTokens.Elevation.Level4.value else LiftrixTokens.Elevation.Level1.value,
        animationSpec = tween(durationMillis = 200),
        label = "drag_preview_elevation"
    )
    
    val borderAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.8f else 0.0f,
        animationSpec = tween(durationMillis = 150),
        label = "drag_preview_border"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .alpha(alpha)
            .graphicsLayer {
                shadowElevation = elevation
                shape = RoundedCornerShape(LiftrixTokens.CornerRadius.Medium)
                clip = true
            }
    ) {
        // Main widget content with enhanced elevation
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(LiftrixTokens.CornerRadius.Medium),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = elevation.dp,
                pressedElevation = (elevation + 2f).dp,
                hoveredElevation = (elevation + 1f).dp
            )
        ) {
            // Render the actual widget content
            com.example.liftrix.ui.progress.components.widgets.WidgetFactory.CreateWidget(
                widget = widget,
                data = widgetData,
                onClick = { }, // Disable clicks during drag
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Drag state visual overlay
        if (isDragging) {
            DragStateOverlay(
                dragProgress = dragProgress,
                borderAlpha = borderAlpha,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Visual overlay that provides additional drag state feedback
 */
@Composable
private fun DragStateOverlay(
    dragProgress: Float,
    borderAlpha: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.05f * dragProgress)
                    )
                ),
                shape = RoundedCornerShape(LiftrixTokens.CornerRadius.Medium)
            )
            .border(
                width = (2 * dragProgress).dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = borderAlpha),
                shape = RoundedCornerShape(LiftrixTokens.CornerRadius.Medium)
            )
    )
}

/**
 * Enhanced drag preview for complex widgets with additional visual effects
 */
@Composable
fun EnhancedDragPreview(
    widget: AnalyticsWidget,
    widgetData: WidgetData,
    onWidgetClick: (AnalyticsWidget) -> Unit = {},
    isLoading: Boolean = false,
    isDragging: Boolean = true,
    dragProgress: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    val glowIntensity by animateFloatAsState(
        targetValue = if (isDragging) 0.3f else 0.0f,
        animationSpec = tween(durationMillis = 300),
        label = "drag_glow_intensity"
    )
    
    val rotationZDegrees by animateFloatAsState(
        targetValue = if (isDragging) 2f * dragProgress else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "drag_rotation"
    )
    
    Box(
        modifier = modifier
            .graphicsLayer {
                shadowElevation = LiftrixTokens.Elevation.Level5.value * dragProgress
                rotationZ = rotationZDegrees
                scaleX = 1.0f + (0.1f * dragProgress)
                scaleY = 1.0f + (0.1f * dragProgress)
                alpha = 0.9f + (0.1f * (1f - dragProgress))
            }
    ) {
        // Glow effect background
        if (isDragging && glowIntensity > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = glowIntensity),
                                Color.Transparent
                            ),
                            radius = 100f
                        ),
                        shape = RoundedCornerShape(LiftrixTokens.CornerRadius.Large)
                    )
            )
        }
        
        // Main drag preview
        DragPreview(
            widget = widget,
            widgetData = widgetData,
            onWidgetClick = onWidgetClick,
            isLoading = isLoading,
            isDragging = isDragging,
            dragProgress = dragProgress,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Simplified drag preview for performance-critical scenarios
 */
@Composable
fun SimpleDragPreview(
    widget: AnalyticsWidget,
    widgetData: WidgetData,
    isDragging: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .scale(if (isDragging) 1.02f else 1.0f)
            .alpha(if (isDragging) 0.9f else 1.0f),
        shape = RoundedCornerShape(LiftrixTokens.CornerRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 8.dp else 2.dp
        )
    ) {
        com.example.liftrix.ui.progress.components.widgets.WidgetFactory.CreateWidget(
            widget = widget,
            data = widgetData,
            onClick = { }, // Disabled during drag
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Drag preview variant for accessibility mode with enhanced visual feedback
 */
@Composable
fun AccessibleDragPreview(
    widget: AnalyticsWidget,
    widgetData: WidgetData,
    onWidgetClick: (AnalyticsWidget) -> Unit = {},
    isLoading: Boolean = false,
    isDragging: Boolean = true,
    modifier: Modifier = Modifier
) {
    val highContrastBorder by animateFloatAsState(
        targetValue = if (isDragging) 3f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "accessible_border_width"
    )
    
    Card(
        modifier = modifier
            .scale(if (isDragging) 1.1f else 1.0f)
            .border(
                width = highContrastBorder.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(LiftrixTokens.CornerRadius.Medium)
            ),
        shape = RoundedCornerShape(LiftrixTokens.CornerRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 12.dp else 2.dp
        )
    ) {
        com.example.liftrix.ui.progress.components.widgets.WidgetFactory.CreateWidget(
            widget = widget,
            data = widgetData,
            onClick = { onWidgetClick(widget) },
            modifier = Modifier.fillMaxSize()
        )
    }
}