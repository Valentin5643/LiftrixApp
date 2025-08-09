package com.example.liftrix.ui.progress.detail.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

/**
 * Data models for interactive charts
 */

/**
 * Represents a single data point in an interactive chart
 */
data class InteractiveChartPoint(
    val x: Float,
    val y: Float,
    val value: Double,
    val label: String,
    val timestamp: Long? = null,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Represents a series of data points with styling
 */
data class ChartDataSeries(
    val id: String,
    val name: String,
    val points: List<InteractiveChartPoint>,
    val color: Color,
    val lineStyle: LineStyle = LineStyle.SOLID
)

/**
 * Line style options for chart series
 */
enum class LineStyle {
    SOLID,
    DASHED,
    DOTTED
}

/**
 * Interactive Charts Components
 * 
 * A comprehensive collection of interactive chart components with enhanced features:
 * - Smooth animations and transitions
 * - Touch interaction with haptic feedback
 * - Dynamic markers and tooltips
 * - Multiple chart types (line, bar, pie)
 * - Responsive design for all screen sizes
 * - Accessibility support
 */

/**
 * Enhanced Interactive Line Chart
 * 
 * Features:
 * - Multi-series support with color coding
 * - Animated line drawing with smooth transitions
 * - Interactive data points with tap detection
 * - Gradient fills and smooth bezier curves
 * - Zoom and pan gestures (optional)
 * - Grid lines and axis labels
 * - Custom markers and tooltips
 */
@Composable
fun EnhancedInteractiveLineChart(
    dataSeries: List<ChartDataSeries>,
    selectedPoint: InteractiveChartPoint?,
    onPointSelected: (InteractiveChartPoint) -> Unit,
    modifier: Modifier = Modifier,
    animationDuration: Int = 1000,
    showGrid: Boolean = true,
    showAxes: Boolean = true,
    enableZoom: Boolean = false,
    markerSize: Dp = 6.dp,
    lineWidth: Dp = 3.dp
) {
    val haptic = LocalHapticFeedback.current
    val animationProgress = remember { Animatable(0f) }
    
    LaunchedEffect(dataSeries) {
        animationProgress.animateTo(
            1f,
            animationSpec = tween(animationDuration, easing = FastOutSlowInEasing)
        )
    }
    
    Canvas(
        modifier = modifier
            .pointerInput(dataSeries) {
                detectTapGestures { offset ->
                    val tappedPoint = findNearestPointInSeries(offset, androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()), dataSeries)
                    tappedPoint?.let { 
                        onPointSelected(it)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
            }
    ) {
        if (dataSeries.isNotEmpty()) {
            if (showGrid) drawEnhancedGrid()
            if (showAxes) drawEnhancedAxes()
            
            dataSeries.forEachIndexed { seriesIndex, series ->
                drawAnimatedLineSeries(
                    series = series,
                    seriesIndex = seriesIndex,
                    animationProgress = animationProgress.value,
                    selectedPoint = selectedPoint,
                    lineWidth = lineWidth,
                    markerSize = markerSize
                )
            }
        }
    }
}

/**
 * Interactive Bar Chart with animations
 */
@Composable
fun InteractiveBarChart(
    data: List<BarChartData>,
    selectedBar: BarChartData?,
    onBarSelected: (BarChartData) -> Unit,
    modifier: Modifier = Modifier,
    animationDuration: Int = 800,
    barSpacing: Dp = 8.dp,
    showLabels: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val animationProgress = remember { Animatable(0f) }
    
    LaunchedEffect(data) {
        animationProgress.animateTo(
            1f,
            animationSpec = tween(animationDuration, easing = FastOutSlowInEasing)
        )
    }
    
    Canvas(
        modifier = modifier
            .pointerInput(data) {
                detectTapGestures { offset ->
                    val tappedBar = findTappedBar(offset, androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()), data, barSpacing.toPx())
                    tappedBar?.let {
                        onBarSelected(it)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
            }
    ) {
        if (data.isNotEmpty()) {
            drawAnimatedBars(
                data = data,
                selectedBar = selectedBar,
                animationProgress = animationProgress.value,
                barSpacing = barSpacing.toPx(),
                showLabels = showLabels
            )
        }
    }
}

/**
 * Interactive Area Chart with gradient fills
 */
@Composable
fun InteractiveAreaChart(
    dataSeries: List<ChartDataSeries>,
    selectedPoint: InteractiveChartPoint?,
    onPointSelected: (InteractiveChartPoint) -> Unit,
    modifier: Modifier = Modifier,
    animationDuration: Int = 1200,
    gradientAlpha: Float = 0.3f
) {
    val haptic = LocalHapticFeedback.current
    val animationProgress = remember { Animatable(0f) }
    
    LaunchedEffect(dataSeries) {
        animationProgress.animateTo(
            1f,
            animationSpec = tween(animationDuration, easing = FastOutSlowInEasing)
        )
    }
    
    Canvas(
        modifier = modifier
            .pointerInput(dataSeries) {
                detectTapGestures { offset ->
                    val tappedPoint = findNearestPointInSeries(offset, androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()), dataSeries)
                    tappedPoint?.let {
                        onPointSelected(it)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
            }
    ) {
        if (dataSeries.isNotEmpty()) {
            drawEnhancedGrid()
            drawEnhancedAxes()
            
            dataSeries.forEachIndexed { seriesIndex, series ->
                drawAnimatedAreaSeries(
                    series = series,
                    seriesIndex = seriesIndex,
                    animationProgress = animationProgress.value,
                    selectedPoint = selectedPoint,
                    gradientAlpha = gradientAlpha
                )
            }
        }
    }
}

/**
 * Additional chart data models
 */
data class BarChartData(
    val id: String,
    val label: String,
    val value: Float,
    val color: Color,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Enhanced drawing functions
 */
private fun DrawScope.drawEnhancedGrid() {
    val gridColor = Color.Gray.copy(alpha = 0.15f)
    val strokeWidth = 0.5.dp.toPx()
    
    // Enhanced grid with more subtle lines
    for (i in 1..10) {
        val y = size.height * i / 11
        val alpha = if (i % 2 == 0) 0.2f else 0.1f
        drawLine(
            color = gridColor.copy(alpha = alpha),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = strokeWidth
        )
    }
    
    for (i in 1..10) {
        val x = size.width * i / 11
        val alpha = if (i % 2 == 0) 0.2f else 0.1f
        drawLine(
            color = gridColor.copy(alpha = alpha),
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = strokeWidth
        )
    }
}

private fun DrawScope.drawEnhancedAxes() {
    val axisColor = Color.Gray.copy(alpha = 0.4f)
    val strokeWidth = 1.5.dp.toPx()
    
    // X axis
    drawLine(
        color = axisColor,
        start = Offset(0f, size.height),
        end = Offset(size.width, size.height),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    
    // Y axis
    drawLine(
        color = axisColor,
        start = Offset(0f, 0f),
        end = Offset(0f, size.height),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawAnimatedLineSeries(
    series: ChartDataSeries,
    seriesIndex: Int,
    animationProgress: Float,
    selectedPoint: InteractiveChartPoint?,
    lineWidth: Dp,
    markerSize: Dp
) {
    if (series.points.size < 2) return
    
    val sortedPoints = series.points.sortedBy { it.x }
    val animatedPointCount = (sortedPoints.size * animationProgress).toInt().coerceAtLeast(2)
    val animatedPoints = sortedPoints.take(animatedPointCount)
    
    // Create smooth path using bezier curves
    val path = Path().apply {
        animatedPoints.forEachIndexed { index, point ->
            val screenX = point.x * size.width
            val screenY = size.height - (point.y * size.height)
            
            when (index) {
                0 -> moveTo(screenX, screenY)
                else -> {
                    val prevPoint = animatedPoints[index - 1]
                    val prevX = prevPoint.x * size.width
                    val prevY = size.height - (prevPoint.y * size.height)
                    
                    // Create smooth bezier curve
                    val controlX1 = prevX + (screenX - prevX) * 0.3f
                    val controlX2 = prevX + (screenX - prevX) * 0.7f
                    
                    cubicTo(controlX1, prevY, controlX2, screenY, screenX, screenY)
                }
            }
        }
    }
    
    // Draw gradient fill area
    val gradientPath = Path().apply {
        addPath(path)
        lineTo(animatedPoints.last().x * size.width, size.height)
        lineTo(animatedPoints.first().x * size.width, size.height)
        close()
    }
    
    val gradient = Brush.verticalGradient(
        colors = listOf(
            series.color.copy(alpha = 0.3f),
            series.color.copy(alpha = 0.05f),
            Color.Transparent
        ),
        startY = 0f,
        endY = size.height
    )
    
    drawPath(
        path = gradientPath,
        brush = gradient
    )
    
    // Draw line with style
    val strokeStyle = when (series.lineStyle) {
        LineStyle.SOLID -> Stroke(
            width = lineWidth.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        LineStyle.DASHED -> Stroke(
            width = lineWidth.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f))
        )
        LineStyle.DOTTED -> Stroke(
            width = lineWidth.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 8f))
        )
    }
    
    drawPath(
        path = path,
        color = series.color,
        style = strokeStyle
    )
    
    // Draw enhanced data points
    animatedPoints.forEach { point ->
        val screenX = point.x * size.width
        val screenY = size.height - (point.y * size.height)
        val isSelected = point == selectedPoint
        val radius = if (isSelected) markerSize.toPx() * 1.5f else markerSize.toPx()
        
        // Selection ring
        if (isSelected) {
            drawCircle(
                color = series.color.copy(alpha = 0.2f),
                radius = radius * 2f,
                center = Offset(screenX, screenY)
            )
        }
        
        // Main point
        drawCircle(
            color = series.color,
            radius = radius * animationProgress,
            center = Offset(screenX, screenY)
        )
        
        // Inner highlight
        if (animationProgress > 0.5f) {
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = radius * 0.4f * animationProgress,
                center = Offset(screenX, screenY)
            )
        }
    }
}

private fun DrawScope.drawAnimatedAreaSeries(
    series: ChartDataSeries,
    seriesIndex: Int,
    animationProgress: Float,
    selectedPoint: InteractiveChartPoint?,
    gradientAlpha: Float
) {
    if (series.points.size < 2) return
    
    val sortedPoints = series.points.sortedBy { it.x }
    val animatedPointCount = (sortedPoints.size * animationProgress).toInt().coerceAtLeast(2)
    val animatedPoints = sortedPoints.take(animatedPointCount)
    
    // Create area path
    val path = Path().apply {
        moveTo(animatedPoints.first().x * size.width, size.height)
        
        animatedPoints.forEach { point ->
            val screenX = point.x * size.width
            val screenY = size.height - (point.y * size.height)
            lineTo(screenX, screenY)
        }
        
        lineTo(animatedPoints.last().x * size.width, size.height)
        close()
    }
    
    // Multi-color gradient
    val gradient = Brush.verticalGradient(
        colors = listOf(
            series.color.copy(alpha = gradientAlpha),
            series.color.copy(alpha = gradientAlpha * 0.5f),
            Color.Transparent
        ),
        startY = 0f,
        endY = size.height
    )
    
    drawPath(
        path = path,
        brush = gradient
    )
    
    // Draw outline
    val outlinePath = Path().apply {
        animatedPoints.forEachIndexed { index, point ->
            val screenX = point.x * size.width
            val screenY = size.height - (point.y * size.height)
            
            if (index == 0) moveTo(screenX, screenY)
            else lineTo(screenX, screenY)
        }
    }
    
    drawPath(
        path = outlinePath,
        color = series.color,
        style = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

private fun DrawScope.drawAnimatedBars(
    data: List<BarChartData>,
    selectedBar: BarChartData?,
    animationProgress: Float,
    barSpacing: Float,
    showLabels: Boolean
) {
    val maxValue = data.maxOfOrNull { it.value } ?: 1f
    val barWidth = (size.width - (data.size - 1) * barSpacing) / data.size
    
    data.forEachIndexed { index, barData ->
        val isSelected = barData == selectedBar
        val barHeight = (barData.value / maxValue) * size.height * animationProgress
        val x = index * (barWidth + barSpacing)
        val y = size.height - barHeight
        
        // Selection glow
        if (isSelected) {
            drawRoundRect(
                color = barData.color.copy(alpha = 0.3f),
                topLeft = Offset(x - 4.dp.toPx(), y - 4.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(
                    barWidth + 8.dp.toPx(), 
                    barHeight + 8.dp.toPx()
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
            )
        }
        
        // Main bar with gradient
        val gradient = Brush.verticalGradient(
            colors = listOf(
                barData.color,
                barData.color.copy(alpha = 0.8f)
            ),
            startY = y,
            endY = size.height
        )
        
        drawRoundRect(
            brush = gradient,
            topLeft = Offset(x, y),
            size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
        )
    }
}

/**
 * Helper functions for interaction detection
 */
private fun findNearestPointInSeries(
    tapOffset: Offset,
    canvasSize: androidx.compose.ui.geometry.Size,
    dataSeries: List<ChartDataSeries>
): InteractiveChartPoint? {
    val touchRadius = 50f
    var nearestPoint: InteractiveChartPoint? = null
    var nearestDistance = Float.MAX_VALUE
    
    dataSeries.forEach { series ->
        series.points.forEach { point ->
            val screenX = point.x * canvasSize.width
            val screenY = canvasSize.height - (point.y * canvasSize.height)
            val distance = sqrt(
                (tapOffset.x - screenX).pow(2) + (tapOffset.y - screenY).pow(2)
            ).toFloat()
            
            if (distance < touchRadius && distance < nearestDistance) {
                nearestDistance = distance
                nearestPoint = point
            }
        }
    }
    
    return nearestPoint
}

private fun findTappedBar(
    tapOffset: Offset,
    canvasSize: androidx.compose.ui.geometry.Size,
    data: List<BarChartData>,
    barSpacing: Float
): BarChartData? {
    val barWidth = (canvasSize.width - (data.size - 1) * barSpacing) / data.size
    
    data.forEachIndexed { index, barData ->
        val x = index * (barWidth + barSpacing)
        val maxValue = data.maxOfOrNull { it.value } ?: 1f
        val barHeight = (barData.value / maxValue) * canvasSize.height
        val y = canvasSize.height - barHeight
        
        if (tapOffset.x >= x && tapOffset.x <= x + barWidth &&
            tapOffset.y >= y && tapOffset.y <= canvasSize.height) {
            return barData
        }
    }
    
    return null
}

/**
 * Chart colors following Liftrix design system
 */
fun getEnhancedChartColor(index: Int): Color {
    val colors = listOf(
        Color(0xFF339989), // Persian Green
        Color(0xFF7DE2D1), // Tiffany Blue
        Color(0xFF4A90A4), // Steel Blue
        Color(0xFF83C5BE), // Powder Blue
        Color(0xFF006D77), // Dark Cyan
        Color(0xFFE29578), // Sandy Brown
        Color(0xFFFDBF50), // Maize
        Color(0xFF264653), // Dark Green
        Color(0xFF2A9D8F), // Medium Green
        Color(0xFFE76F51)  // Coral
    )
    return colors[index % colors.size]
}