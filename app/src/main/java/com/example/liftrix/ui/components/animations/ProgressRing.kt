package com.example.liftrix.ui.components.animations

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixAnimations
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.PrimaryGradient
import kotlin.math.cos
import kotlin.math.sin

/**
 * Basic progress ring component using Canvas for custom drawing
 * 
 * @param progress Progress value between 0.0 and 1.0
 * @param modifier Modifier for styling the component
 * @param color Color for the progress arc
 * @param backgroundColor Background arc color (optional)
 * @param size Diameter of the progress ring
 * @param strokeWidth Width of the progress stroke
 * @param startAngle Starting angle in degrees (default -90° for top)
 * @param endAngle Ending angle in degrees (default 270° for full circle)
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = LiftrixColors.Primary,
    backgroundColor: Color = color.copy(alpha = 0.1f),
    size: Dp = 120.dp,
    strokeWidth: Dp = 8.dp,
    startAngle: Float = -90f,
    endAngle: Float = 270f
) {
    val density = LocalDensity.current
    val progressClamped = progress.coerceIn(0f, 1f)
    
    Canvas(
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = "Progress: ${(progressClamped * 100).toInt()}%"
            }
    ) {
        drawProgressRing(
            progress = progressClamped,
            color = color,
            backgroundColor = backgroundColor,
            strokeWidth = strokeWidth,
            startAngle = startAngle,
            endAngle = endAngle
        )
    }
}

/**
 * Animated progress ring component with spring physics
 * 
 * @param progress Target progress value between 0.0 and 1.0
 * @param modifier Modifier for styling the component
 * @param color Color for the progress arc
 * @param backgroundColor Background arc color (optional)
 * @param size Diameter of the progress ring
 * @param strokeWidth Width of the progress stroke
 * @param animationSpec Animation specification for progress changes
 * @param startAngle Starting angle in degrees (default -90° for top)
 * @param endAngle Ending angle in degrees (default 270° for full circle)
 */
@Composable
fun AnimatedProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = LiftrixColors.Primary,
    backgroundColor: Color = color.copy(alpha = 0.1f),
    size: Dp = 120.dp,
    strokeWidth: Dp = 8.dp,
    animationSpec: AnimationSpec<Float> = LiftrixAnimations.standardSpring,
    startAngle: Float = -90f,
    endAngle: Float = 270f
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = animationSpec,
        label = "progress_animation"
    )
    
    ProgressRing(
        progress = animatedProgress,
        modifier = modifier,
        color = color,
        backgroundColor = backgroundColor,
        size = size,
        strokeWidth = strokeWidth,
        startAngle = startAngle,
        endAngle = endAngle
    )
}

/**
 * Animated progress ring with gradient color support
 * 
 * @param progress Target progress value between 0.0 and 1.0
 * @param modifier Modifier for styling the component
 * @param progressBrush Brush for the progress arc (supports gradients)
 * @param backgroundColor Background arc color
 * @param size Diameter of the progress ring
 * @param strokeWidth Width of the progress stroke
 * @param animationSpec Animation specification for progress changes
 * @param startAngle Starting angle in degrees (default -90° for top)
 * @param endAngle Ending angle in degrees (default 270° for full circle)
 */
@Composable
fun AnimatedProgressRingWithGradient(
    progress: Float,
    modifier: Modifier = Modifier,
    progressBrush: Brush = PrimaryGradient,
    backgroundColor: Color = LiftrixColors.Primary.copy(alpha = 0.1f),
    size: Dp = 120.dp,
    strokeWidth: Dp = 8.dp,
    animationSpec: AnimationSpec<Float> = LiftrixAnimations.standardSpring,
    startAngle: Float = -90f,
    endAngle: Float = 270f
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = animationSpec,
        label = "progress_animation"
    )
    
    Canvas(
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = "Progress: ${(animatedProgress * 100).toInt()}%"
            }
    ) {
        drawProgressRingWithGradient(
            progress = animatedProgress,
            progressBrush = progressBrush,
            backgroundColor = backgroundColor,
            strokeWidth = strokeWidth,
            startAngle = startAngle,
            endAngle = endAngle
        )
    }
}

/**
 * Multi-layer progress ring for displaying multiple progress values
 * 
 * @param progressLayers List of progress layers with colors and values
 * @param modifier Modifier for styling the component
 * @param size Diameter of the progress ring
 * @param layerSpacing Spacing between progress layers
 * @param animationSpec Animation specification for progress changes
 */
@Composable
fun MultiLayerProgressRing(
    progressLayers: List<ProgressLayer>,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    layerSpacing: Dp = 4.dp,
    animationSpec: AnimationSpec<Float> = LiftrixAnimations.standardSpring
) {
    Canvas(
        modifier = modifier.size(size)
    ) {
        progressLayers.forEachIndexed { index, layer ->
            val layerStrokeWidth = layer.strokeWidth.toPx()
            val layerRadius = (size.toPx() - layerStrokeWidth) / 2f - (index * (layerStrokeWidth + layerSpacing.toPx()))
            
            drawProgressArc(
                progress = layer.progress.coerceIn(0f, 1f),
                center = center,
                radius = layerRadius,
                color = layer.color,
                backgroundColor = layer.backgroundColor,
                strokeWidth = layerStrokeWidth,
                startAngle = layer.startAngle,
                sweepAngle = layer.endAngle - layer.startAngle
            )
        }
    }
}

/**
 * Core drawing function for progress ring
 */
private fun DrawScope.drawProgressRing(
    progress: Float,
    color: Color,
    backgroundColor: Color,
    strokeWidth: Dp,
    startAngle: Float,
    endAngle: Float
) {
    val stroke = Stroke(
        width = strokeWidth.toPx(),
        cap = StrokeCap.Round
    )
    
    val sweepAngle = endAngle - startAngle
    val progressSweep = sweepAngle * progress
    
    val arcSize = Size(
        width = size.width - strokeWidth.toPx(),
        height = size.height - strokeWidth.toPx()
    )
    
    val topLeft = Offset(
        x = strokeWidth.toPx() / 2f,
        y = strokeWidth.toPx() / 2f
    )
    
    // Background arc
    drawArc(
        color = backgroundColor,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        style = stroke,
        size = arcSize,
        topLeft = topLeft
    )
    
    // Progress arc
    if (progress > 0f) {
        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = progressSweep,
            useCenter = false,
            style = stroke,
            size = arcSize,
            topLeft = topLeft
        )
    }
}

/**
 * Drawing function for progress ring with gradient support
 */
private fun DrawScope.drawProgressRingWithGradient(
    progress: Float,
    progressBrush: Brush,
    backgroundColor: Color,
    strokeWidth: Dp,
    startAngle: Float,
    endAngle: Float
) {
    val stroke = Stroke(
        width = strokeWidth.toPx(),
        cap = StrokeCap.Round
    )
    
    val sweepAngle = endAngle - startAngle
    val progressSweep = sweepAngle * progress
    
    val arcSize = Size(
        width = size.width - strokeWidth.toPx(),
        height = size.height - strokeWidth.toPx()
    )
    
    val topLeft = Offset(
        x = strokeWidth.toPx() / 2f,
        y = strokeWidth.toPx() / 2f
    )
    
    // Background arc
    drawArc(
        color = backgroundColor,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        style = stroke,
        size = arcSize,
        topLeft = topLeft
    )
    
    // Progress arc with gradient
    if (progress > 0f) {
        drawArc(
            brush = progressBrush,
            startAngle = startAngle,
            sweepAngle = progressSweep,
            useCenter = false,
            style = stroke,
            size = arcSize,
            topLeft = topLeft
        )
    }
}

/**
 * Helper drawing function for multi-layer progress
 */
private fun DrawScope.drawProgressArc(
    progress: Float,
    center: Offset,
    radius: Float,
    color: Color,
    backgroundColor: Color,
    strokeWidth: Float,
    startAngle: Float,
    sweepAngle: Float
) {
    val stroke = Stroke(
        width = strokeWidth,
        cap = StrokeCap.Round
    )
    
    val arcSize = Size(radius * 2f, radius * 2f)
    val topLeft = Offset(
        x = center.x - radius,
        y = center.y - radius
    )
    
    // Background arc
    drawArc(
        color = backgroundColor,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        style = stroke,
        size = arcSize,
        topLeft = topLeft
    )
    
    // Progress arc
    if (progress > 0f) {
        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = sweepAngle * progress,
            useCenter = false,
            style = stroke,
            size = arcSize,
            topLeft = topLeft
        )
    }
}

/**
 * Data class for progress layer configuration
 */
data class ProgressLayer(
    val progress: Float,
    val color: Color,
    val backgroundColor: Color = color.copy(alpha = 0.1f),
    val strokeWidth: Dp = 8.dp,
    val startAngle: Float = -90f,
    val endAngle: Float = 270f
)

/**
 * Utility function to create common progress ring variants
 */
object ProgressRingDefaults {
    
    @Composable
    fun workoutProgress(
        progress: Float,
        modifier: Modifier = Modifier,
        size: Dp = 80.dp
    ) = AnimatedProgressRing(
        progress = progress,
        modifier = modifier,
        color = LiftrixColors.Primary,
        size = size,
        strokeWidth = 6.dp,
        animationSpec = LiftrixAnimations.standardSpring
    )
    
    @Composable
    fun streakProgress(
        progress: Float,
        modifier: Modifier = Modifier,
        size: Dp = 80.dp
    ) = AnimatedProgressRingWithGradient(
        progress = progress,
        modifier = modifier,
        progressBrush = PrimaryGradient,
        size = size,
        strokeWidth = 6.dp,
        animationSpec = LiftrixAnimations.standardSpring
    )
    
    @Composable
    fun volumeProgress(
        progress: Float,
        modifier: Modifier = Modifier,
        size: Dp = 80.dp
    ) = AnimatedProgressRing(
        progress = progress,
        modifier = modifier,
        color = LiftrixColors.Secondary,
        size = size,
        strokeWidth = 6.dp,
        animationSpec = LiftrixAnimations.gentleSpring
    )
    
    @Composable
    fun durationProgress(
        progress: Float,
        modifier: Modifier = Modifier,
        size: Dp = 80.dp
    ) = AnimatedProgressRing(
        progress = progress,
        modifier = modifier,
        color = LiftrixColors.TiffanyBlue,
        size = size,
        strokeWidth = 6.dp,
        animationSpec = LiftrixAnimations.gentleSpring
    )
}