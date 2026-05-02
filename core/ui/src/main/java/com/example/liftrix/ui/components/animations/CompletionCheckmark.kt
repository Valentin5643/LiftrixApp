package com.example.liftrix.ui.components.animations

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.example.liftrix.ui.theme.LiftrixAnimations
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.LiftrixTheme
// Removed LiftrixColors.PersianGreen import - using PersianGreen directly

/**
 * Animated completion checkmark with bouncy entrance animation and haptic feedback.
 * 
 * Features:
 * - Path-based checkmark drawing with smooth animation
 * - Spring physics for satisfying bounce effect
 * - Haptic feedback on completion
 * - Accessibility support with content descriptions
 * - Customizable size and colors
 * - Optimized for 60fps performance
 * 
 * @param completed Whether the checkmark should be shown as completed
 * @param onComplete Callback triggered when completion animation starts
 * @param modifier Modifier for styling
 * @param size Size of the checkmark component
 * @param color Color of the checkmark stroke
 * @param backgroundColor Background color (null for transparent)
 * @param strokeWidth Width of the checkmark stroke
 * @param hapticEnabled Whether to provide haptic feedback on completion
 */
@Composable
fun CompletionCheckmark(
    completed: Boolean,
    onComplete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = LiftrixColors.Primary,
    backgroundColor: Color? = null,
    strokeWidth: Dp = 2.dp,
    hapticEnabled: Boolean = true
) {
    val hapticFeedback = LocalHapticFeedback.current
    
    // Track completion state to trigger haptic feedback only once
    var hasTriggeredHaptic by remember { mutableStateOf(false) }
    
    // Animate checkmark path drawing progress
    val pathProgress by animateFloatAsState(
        targetValue = if (completed) 1f else 0f,
        animationSpec = LiftrixAnimations.bouncySpring,
        label = "checkmark_path_progress"
    )
    
    // Animate scale for bouncy entrance effect
    val scale by animateFloatAsState(
        targetValue = if (completed) 1f else 0f,
        animationSpec = LiftrixAnimations.bouncySpring,
        label = "checkmark_scale"
    )
    
    // Trigger haptic feedback and callback when completion starts
    LaunchedEffect(completed) {
        if (completed && !hasTriggeredHaptic) {
            if (hapticEnabled) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            onComplete?.invoke()
            hasTriggeredHaptic = true
        } else if (!completed) {
            hasTriggeredHaptic = false
        }
    }
    
    Canvas(
        modifier = modifier
            .size(size)
            .scale(scale)
            .semantics {
                contentDescription = if (completed) "Completed" else "Not completed"
            }
    ) {
        // Draw background circle if provided
        backgroundColor?.let { bgColor ->
            drawCircle(
                color = bgColor,
                radius = size.toPx() / 2f
            )
        }
        
        // Draw animated checkmark path
        if (pathProgress > 0f) {
            val checkmarkPath = createCheckmarkPath(pathProgress, size.toPx())
            drawPath(
                path = checkmarkPath,
                color = color,
                style = Stroke(
                    width = strokeWidth.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }
    }
}

/**
 * Creates an animated checkmark path based on progress.
 * The checkmark is drawn from left to middle to right with smooth curves.
 * 
 * @param progress Animation progress from 0f to 1f
 * @param canvasSize Size of the canvas in pixels
 * @return Path representing the checkmark at the given progress
 */
private fun createCheckmarkPath(progress: Float, canvasSize: Float): Path {
    val path = Path()
    
    // Define checkmark proportions (relative to canvas size)
    val padding = canvasSize * 0.25f
    val leftX = padding
    val centerX = canvasSize * 0.4f
    val rightX = canvasSize - padding
    val topY = canvasSize * 0.3f
    val centerY = canvasSize * 0.6f
    val bottomY = canvasSize * 0.7f
    
    // Calculate path segments
    val firstSegmentProgress = (progress * 2f).coerceAtMost(1f)
    val secondSegmentProgress = ((progress - 0.5f) * 2f).coerceAtLeast(0f)
    
    if (firstSegmentProgress > 0f) {
        // First segment: left to center (downward stroke)
        path.moveTo(leftX, centerY)
        
        val currentCenterX = leftX + (centerX - leftX) * firstSegmentProgress
        val currentCenterY = centerY + (bottomY - centerY) * firstSegmentProgress
        path.lineTo(currentCenterX, currentCenterY)
        
        if (secondSegmentProgress > 0f) {
            // Second segment: center to right (upward stroke)
            val currentRightX = currentCenterX + (rightX - centerX) * secondSegmentProgress
            val currentRightY = currentCenterY + (topY - bottomY) * secondSegmentProgress
            path.lineTo(currentRightX, currentRightY)
        }
    }
    
    return path
}

/**
 * Alternative checkmark with circular background for enhanced visibility
 */
@Composable
fun CompletionCheckmarkWithBackground(
    completed: Boolean,
    onComplete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    checkmarkColor: Color = Color.White,
    backgroundColor: Color = LiftrixColors.Primary,
    strokeWidth: Dp = 2.5.dp,
    hapticEnabled: Boolean = true
) {
    CompletionCheckmark(
        completed = completed,
        onComplete = onComplete,
        modifier = modifier,
        size = size,
        color = checkmarkColor,
        backgroundColor = backgroundColor,
        strokeWidth = strokeWidth,
        hapticEnabled = hapticEnabled
    )
}

@Preview(name = "Completion Checkmark - Default")
@Composable
private fun CompletionCheckmarkPreview() {
    LiftrixTheme {
        CompletionCheckmark(
            completed = true
        )
    }
}

@Preview(name = "Completion Checkmark - With Background")
@Composable
private fun CompletionCheckmarkWithBackgroundPreview() {
    LiftrixTheme {
        CompletionCheckmarkWithBackground(
            completed = true
        )
    }
}

@Preview(name = "Completion Checkmark - Not Completed")
@Composable
private fun CompletionCheckmarkNotCompletedPreview() {
    LiftrixTheme {
        CompletionCheckmark(
            completed = false
        )
    }
}

@Preview(name = "Completion Checkmark - Large Size")
@Composable
private fun CompletionCheckmarkLargeSizePreview() {
    LiftrixTheme {
        CompletionCheckmark(
            completed = true,
            size = 48.dp,
            strokeWidth = 3.dp
        )
    }
}

@Preview(name = "Completion Checkmark - Custom Colors")
@Composable
private fun CompletionCheckmarkCustomColorsPreview() {
    LiftrixTheme {
        CompletionCheckmark(
            completed = true,
            color = MaterialTheme.colorScheme.error,
            size = 32.dp
        )
    }
} 