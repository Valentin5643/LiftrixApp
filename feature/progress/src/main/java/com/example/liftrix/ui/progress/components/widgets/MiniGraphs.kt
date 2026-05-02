package com.example.liftrix.ui.progress.components.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.ChartColorsV2
import kotlin.math.max
import kotlin.math.min

/**
 * Mini line graph for displaying trend data in widget cards.
 * Shows a simplified visualization of data points with smooth curves.
 */
@Composable
fun MiniLineGraph(
    dataPoints: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
    showFill: Boolean = true
) {
    val primaryColor = lineColor
    val fillGradient = ChartColorsV2.Gradients.getSeriesGradient(primaryColor, isDarkTheme = true)
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {
        if (dataPoints.isEmpty()) {
            // Draw empty state - flat line at middle
            drawEmptyState()
            return@Canvas
        }
        
        if (dataPoints.size == 1) {
            // Draw single point
            drawSinglePoint(dataPoints[0], primaryColor)
            return@Canvas
        }
        
        // Normalize data points
        val normalizedPoints = normalizeDataPoints(dataPoints)
        
        // Draw the graph
        drawMiniLineGraph(
            normalizedPoints = normalizedPoints,
            lineColor = primaryColor,
            fillBrush = fillGradient,
            showFill = showFill
        )
    }
}

/**
 * Mini bar chart for displaying discrete values in widget cards.
 */
@Composable
fun MiniBarChart(
    dataPoints: List<Float>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    maxBars: Int = 7
) {
    val displayPoints = if (dataPoints.size > maxBars) {
        dataPoints.takeLast(maxBars)
    } else {
        dataPoints
    }
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {
        if (displayPoints.isEmpty()) {
            drawEmptyState()
            return@Canvas
        }
        
        val normalizedPoints = normalizeDataPoints(displayPoints)
        drawMiniBarChart(normalizedPoints, barColor)
    }
}

/**
 * Mini progress arc for showing percentage-based metrics.
 */
@Composable
fun MiniProgressArc(
    progress: Float,
    modifier: Modifier = Modifier,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val validProgress = progress.coerceIn(0f, 1f)
    
    Canvas(
        modifier = modifier.size(40.dp)
    ) {
        drawMiniProgressArc(
            progress = validProgress,
            progressColor = progressColor,
            backgroundColor = backgroundColor
        )
    }
}

/**
 * Mini sparkline for showing trend without scale.
 */
@Composable
fun MiniSparkline(
    dataPoints: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    lineWidth: Dp = 2.dp
) {
    val density = LocalDensity.current
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
    ) {
        if (dataPoints.size < 2) {
            drawEmptyState()
            return@Canvas
        }
        
        val normalizedPoints = normalizeDataPoints(dataPoints)
        drawSparkline(normalizedPoints, lineColor, with(density) { lineWidth.toPx() })
    }
}

/**
 * Mini distribution chart for muscle group or exercise distribution.
 */
@Composable
fun MiniDistributionChart(
    segments: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier
) {
    val total = segments.sumOf { it.first.toDouble() }.toFloat()
    if (total == 0f) return
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
    ) {
        drawDistributionBar(segments, total)
    }
}

// Drawing extension functions

private fun DrawScope.drawEmptyState() {
    val centerY = size.height / 2
    val strokeWidth = 1.dp.toPx()
    val dashLength = 5.dp.toPx()
    
    drawLine(
        color = Color.Gray.copy(alpha = 0.3f),
        start = Offset(0f, centerY),
        end = Offset(size.width, centerY),
        strokeWidth = strokeWidth,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength, dashLength))
    )
}

private fun DrawScope.drawSinglePoint(value: Float, color: Color) {
    val centerX = size.width / 2
    val centerY = size.height / 2
    val outerRadius = 8.dp.toPx()
    val innerRadius = 3.dp.toPx()
    
    drawCircle(
        color = color.copy(alpha = 0.3f),
        radius = outerRadius,
        center = Offset(centerX, centerY)
    )
    
    drawCircle(
        color = color,
        radius = innerRadius,
        center = Offset(centerX, centerY)
    )
}

private fun DrawScope.drawMiniLineGraph(
    normalizedPoints: List<Float>,
    lineColor: Color,
    fillBrush: Brush,
    showFill: Boolean
) {
    val padding = 2.dp.toPx()
    val strokeWidth = 2.dp.toPx()
    val availableHeight = size.height - (padding * 2)
    val availableWidth = size.width
    
    val path = Path()
    val fillPath = Path()
    
    normalizedPoints.forEachIndexed { index, value ->
        val x = (index.toFloat() / (normalizedPoints.size - 1)) * availableWidth
        val y = padding + (1f - value) * availableHeight
        
        if (index == 0) {
            path.moveTo(x, y)
            fillPath.moveTo(x, y)
        } else {
            // Create smooth curves using quadratic bezier
            val prevX = ((index - 1).toFloat() / (normalizedPoints.size - 1)) * availableWidth
            val prevY = padding + (1f - normalizedPoints[index - 1]) * availableHeight
            
            val controlX = (prevX + x) / 2
            val controlY = (prevY + y) / 2
            
            path.quadraticBezierTo(controlX, prevY, controlX, controlY)
            path.quadraticBezierTo(controlX, y, x, y)
            
            fillPath.quadraticBezierTo(controlX, prevY, controlX, controlY)
            fillPath.quadraticBezierTo(controlX, y, x, y)
        }
    }
    
    // Draw fill area if enabled
    if (showFill && normalizedPoints.isNotEmpty()) {
        fillPath.lineTo(size.width, size.height)
        fillPath.lineTo(0f, size.height)
        fillPath.close()
        
        drawPath(
            path = fillPath,
            brush = fillBrush
        )
    }
    
    // Draw line
    drawPath(
        path = path,
        color = lineColor,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawMiniBarChart(
    normalizedPoints: List<Float>,
    barColor: Color
) {
    val barCount = normalizedPoints.size
    val barSpacing = 2.dp.toPx()
    val totalSpacing = barSpacing * (barCount - 1)
    val barWidth = (size.width - totalSpacing) / barCount
    val bottomPadding = 4.dp.toPx()
    val maxBarHeight = size.height - bottomPadding
    val cornerRadius = 2.dp.toPx()
    
    normalizedPoints.forEachIndexed { index, value ->
        val x = index * (barWidth + barSpacing)
        val barHeight = value * maxBarHeight
        val y = size.height - barHeight
        
        drawRoundRect(
            color = barColor.copy(alpha = 0.8f + (value * 0.2f)),
            topLeft = Offset(x, y),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius)
        )
    }
}

private fun DrawScope.drawMiniProgressArc(
    progress: Float,
    progressColor: Color,
    backgroundColor: Color
) {
    val strokeWidth = 4.dp.toPx()
    val padding = strokeWidth / 2
    val strokeStyle = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    
    // Draw background arc
    drawArc(
        color = backgroundColor,
        startAngle = 135f,
        sweepAngle = 270f,
        useCenter = false,
        topLeft = Offset(padding, padding),
        size = Size(size.width - strokeWidth, size.height - strokeWidth),
        style = strokeStyle
    )
    
    // Draw progress arc
    if (progress > 0f) {
        drawArc(
            color = progressColor,
            startAngle = 135f,
            sweepAngle = 270f * progress,
            useCenter = false,
            topLeft = Offset(padding, padding),
            size = Size(size.width - strokeWidth, size.height - strokeWidth),
            style = strokeStyle
        )
    }
}

private fun DrawScope.drawSparkline(
    normalizedPoints: List<Float>,
    lineColor: Color,
    lineWidth: Float
) {
    val path = Path()
    val padding = 2.dp.toPx()
    val availableHeight = size.height - (padding * 2)
    
    normalizedPoints.forEachIndexed { index, value ->
        val x = (index.toFloat() / (normalizedPoints.size - 1)) * size.width
        val y = padding + (1f - value) * availableHeight
        
        if (index == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    
    drawPath(
        path = path,
        color = lineColor,
        style = Stroke(width = lineWidth, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawDistributionBar(
    segments: List<Pair<Float, Color>>,
    total: Float
) {
    var currentX = 0f
    
    segments.forEach { (value, color) ->
        val segmentWidth = (value / total) * size.width
        
        drawRect(
            color = color,
            topLeft = Offset(currentX, 0f),
            size = Size(segmentWidth, size.height)
        )
        
        currentX += segmentWidth
    }
}

// Helper functions

private fun normalizeDataPoints(points: List<Float>): List<Float> {
    if (points.isEmpty()) return emptyList()
    
    val min = points.minOrNull() ?: 0f
    val max = points.maxOrNull() ?: 0f
    val range = max - min
    
    return if (range == 0f) {
        // All points are the same
        points.map { 0.5f }
    } else {
        // Normalize to 0-1 range with padding
        points.map { point ->
            val normalized = (point - min) / range
            // Add padding to avoid touching edges
            0.1f + (normalized * 0.8f)
        }
    }
}

/**
 * Generate sample data for preview/testing
 */
fun generateSampleData(size: Int = 7): List<Float> {
    return List(size) { index ->
        (50f + (index * 10f) + (-10..10).random())
    }
}