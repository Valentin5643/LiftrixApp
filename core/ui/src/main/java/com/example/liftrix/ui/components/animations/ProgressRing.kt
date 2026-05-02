package com.example.liftrix.ui.components.animations

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.PrimaryGradient

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
    val progressClamped = progress.coerceIn(0f, 1f)
    val sweepAngle = endAngle - startAngle

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Progress: ${(progressClamped * 100).toInt()}%" }
    ) {
        drawArc(
            color = backgroundColor,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = sweepAngle * progressClamped,
            useCenter = false,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun AnimatedProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = LiftrixColors.Primary,
    backgroundColor: Color = color.copy(alpha = 0.1f),
    size: Dp = 120.dp,
    strokeWidth: Dp = 8.dp,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = 300)
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = animationSpec,
        label = "progress_animation"
    )
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Progress: ${(animatedProgress * 100).toInt()}%" }
    ) {
        drawArc(
            color = backgroundColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = animatedProgress * 360f,
            useCenter = false,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun AnimatedProgressRingWithGradient(
    progress: Float,
    modifier: Modifier = Modifier,
    progressBrush: Brush = PrimaryGradient,
    backgroundColor: Color = LiftrixColors.Primary.copy(alpha = 0.1f),
    size: Dp = 120.dp,
    strokeWidth: Dp = 8.dp,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = 300)
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = animationSpec,
        label = "gradient_progress_animation"
    )

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Progress: ${(animatedProgress * 100).toInt()}%" }
    ) {
        drawArc(
            color = backgroundColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            brush = progressBrush,
            startAngle = -90f,
            sweepAngle = animatedProgress * 360f,
            useCenter = false,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )
    }
}

data class ProgressLayer(
    val progress: Float,
    val color: Color,
    val strokeWidth: Dp = 8.dp,
    val backgroundColor: Color = color.copy(alpha = 0.1f)
)

@Composable
fun MultiLayerProgressRing(
    progressLayers: List<ProgressLayer>,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    layerSpacing: Dp = 6.dp,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = 300)
) {
    val animatedProgress = progressLayers.map { layer ->
        val value by animateFloatAsState(
            targetValue = layer.progress.coerceIn(0f, 1f),
            animationSpec = animationSpec,
            label = "layer_progress_animation"
        )
        value
    }

    Canvas(
        modifier = modifier
            .size(size)
            .semantics {
                val averageProgress = if (animatedProgress.isEmpty()) 0f else animatedProgress.average().toFloat()
                contentDescription = "Progress: ${(averageProgress * 100).toInt()}%"
            }
    ) {
        var inset = 0f
        progressLayers.forEachIndexed { index, layer ->
            val strokePx = layer.strokeWidth.toPx()
            val diameter = this.size.minDimension - inset * 2f - strokePx
            if (diameter <= 0f) return@forEachIndexed

            val topLeft = androidx.compose.ui.geometry.Offset(
                x = inset + strokePx / 2f,
                y = inset + strokePx / 2f
            )
            val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)

            drawArc(
                color = layer.backgroundColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            drawArc(
                color = layer.color,
                startAngle = -90f,
                sweepAngle = animatedProgress[index] * 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            inset += strokePx + layerSpacing.toPx()
        }
    }
}

object ProgressRingDefaults {
    @Composable
    fun workoutProgress(
        progress: Float,
        modifier: Modifier = Modifier,
        size: Dp = 120.dp
    ) {
        AnimatedProgressRing(
            progress = progress,
            modifier = modifier,
            color = LiftrixColors.Primary,
            size = size
        )
    }

    @Composable
    fun streakProgress(
        progress: Float,
        modifier: Modifier = Modifier,
        size: Dp = 120.dp
    ) {
        AnimatedProgressRing(
            progress = progress,
            modifier = modifier,
            color = LiftrixColors.Secondary,
            size = size
        )
    }

    @Composable
    fun volumeProgress(
        progress: Float,
        modifier: Modifier = Modifier,
        size: Dp = 120.dp
    ) {
        AnimatedProgressRing(
            progress = progress,
            modifier = modifier,
            color = LiftrixColors.TiffanyBlue,
            size = size
        )
    }

    @Composable
    fun durationProgress(
        progress: Float,
        modifier: Modifier = Modifier,
        size: Dp = 120.dp
    ) {
        AnimatedProgressRing(
            progress = progress,
            modifier = modifier,
            color = LiftrixColors.TiffanyBlue,
            size = size
        )
    }
}
