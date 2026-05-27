package com.example.liftrix.ui.progress.components.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.ui.common.rememberFrameTimeMonitor
import com.example.liftrix.ui.common.measureFrameTime
import com.example.liftrix.ui.theme.LiftrixChartStyle
import com.example.liftrix.ui.theme.ChartColorsV2
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.theme.drawBezierLineChart
import kotlinx.datetime.LocalDate
import com.example.liftrix.domain.model.analytics.VolumeDataPoint
import kotlinx.datetime.toJavaLocalDate
import kotlin.math.abs
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

/**
 * ModernVolumeChart - Enhanced volume progression chart with bezier curves and gradients
 *
 * Features:
 * - Smooth bezier curve line rendering with gradients
 * - Personal Record (PR) markers with distinctive styling
 * - Interactive data point selection with haptic feedback
 * - Responsive design with mobile-optimized touch targets
 * - Accessibility-compliant content descriptions
 * - Performance-optimized for 60fps with large datasets
 * - Material 3 design system integration
 */
@Composable
fun ModernVolumeChart(
    data: List<VolumeDataPoint>,
    timeRange: TimeRangeType,
    modifier: Modifier = Modifier,
    onDataPointSelected: ((VolumeDataPoint) -> Unit)? = null,
    showPersonalRecords: Boolean = true,
    animationDuration: Int = 300,
    unit: String = "kg",
    chartTitle: String = "Volume Progress",
    allowPointSelection: Boolean = true,
    useZeroBaseline: Boolean = false,
    maxVisiblePoints: Int = 32,
    fillBrush: Brush? = null
) {
    var selectedPoint by remember { mutableStateOf<VolumeDataPoint?>(null) }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    
    // Frame time monitoring for 60fps validation
    val frameMonitor = rememberFrameTimeMonitor()

    
    // Animation for chart appearance
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animationProgress.animateTo(1f, animationSpec = tween(animationDuration))
    }

    // Calculate chart metrics
    val chartMetrics by remember(data) {
        derivedStateOf {
            if (data.isEmpty()) {
                ChartMetrics.empty()
            } else {
                ChartMetrics.calculate(data)
            }
        }
    }
    val displayMetrics = if (data.isEmpty()) ChartMetrics.forZeroData() else chartMetrics
    val chartScale = remember(displayMetrics, useZeroBaseline) {
        displayMetrics.scaleFor(useZeroBaseline)
    }
    val isDenseData = data.size > maxVisiblePoints
    val showDataPoints = !isDenseData
    val allowSelection = allowPointSelection && showDataPoints
    val resolvedFillBrush = fillBrush ?: ChartColorsV2.Gradients.getPrimaryGradient(isSystemInDarkTheme())

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(LiftrixChartStyle.ChartDimensions.defaultHeight)
            .semantics {
                contentDescription = buildContentDescription(data, timeRange)
            }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Chart metrics only (title removed to avoid duplication)
            ChartMetricsOnly(
                subtitle = timeRange.displayName,
                metrics = chartMetrics,
                unit = unit
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Main chart area
            Box(
                modifier = Modifier.weight(1f)
            ) {
                // Always show chart canvas, even with empty/zero data
                val displayData = if (data.isEmpty()) generateZeroVolumeData() else data
                val textMeasurer = rememberTextMeasurer()
                
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(displayData) {
                            // Only detect taps if we have real data
                            if (allowSelection && data.isNotEmpty()) {
                                detectTapGestures { offset ->
                                    val tappedPoint = findNearestDataPoint(
                                        offset,
                                        data,
                                        androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()),
                                        chartScale,
                                        density.density
                                    )
                                    tappedPoint?.let { point ->
                                        selectedPoint = point
                                        onDataPointSelected?.invoke(point)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                            }
                        }
                ) {
                    measureFrameTime("ModernVolumeChart.draw") {
                        drawModernVolumeChart(
                            data = displayData,
                            metrics = displayMetrics,
                            scale = chartScale,
                            animationProgress = animationProgress.value,
                            selectedPoint = selectedPoint,
                            showPersonalRecords = showPersonalRecords && data.isNotEmpty(),
                            showDataPoints = showDataPoints,
                            fillBrush = resolvedFillBrush,
                            density = density.density,
                            textMeasurer = textMeasurer
                        )
                    }
                }
                
                // Show overlay message for zero data
                if (data.isEmpty()) {
                    ZeroDataOverlay()
                }
            }
            
            // Chart legend and selected point info
            if (selectedPoint != null) {
                Spacer(modifier = Modifier.height(8.dp))
                SelectedPointInfo(
                    point = selectedPoint!!,
                    onDismiss = { selectedPoint = null },
                    unit = unit
                )
            }
        }
    }
}

/**
 * Draw the modernized volume chart with bezier curves, grid, and axes
 */
private fun DrawScope.drawModernVolumeChart(
    data: List<VolumeDataPoint>,
    metrics: ChartMetrics,
    scale: ChartScale,
    animationProgress: Float,
    selectedPoint: VolumeDataPoint?,
    showPersonalRecords: Boolean,
    showDataPoints: Boolean,
    fillBrush: Brush,
    density: Float,
    textMeasurer: TextMeasurer
) {
    if (data.isEmpty()) return
    val plotArea = chartPlotArea(
        width = size.width,
        height = size.height,
        density = density
    )
    
    // Draw grid first (background layer)
    drawGrid(plotArea)
    
    // Draw axes
    drawAxes(plotArea)
    
    val chartConfig = LiftrixChartStyle.LineChartConfig(
        strokeWidth = LiftrixChartStyle.ChartDimensions.strokeWidthMedium.value,
        color = ChartColorsV2.getSeriesColor(0),
        useBezierCurves = true,
        showDataPoints = false,
        showGradientFill = true,
        gradientBrush = fillBrush,
        showGrid = false,
        animationDuration = LiftrixChartStyle.ChartAnimations.defaultDuration
    )
    val points = data.mapIndexed { index, dataPoint ->
        // SINGLE DATA POINT FIX: Handle single point rendering properly
        val x = if (data.size == 1) {
            plotArea.left + plotArea.width * 0.5f // Center single data point
        } else {
            plotArea.left + plotArea.width * index / (data.size - 1).toFloat()
        }
        val normalizedValue = if (scale.range > 0) {
            if (scale.isFlat) {
                0.5
            } else {
                (dataPoint.getVolumeAsDouble() - scale.minValue) / scale.range
            }
        } else {
            0.5 // For single points or zero range, show at mid-height for visibility
        }
        val y = plotArea.bottom - (plotArea.height * normalizedValue.toFloat() * animationProgress)
        x to y.toFloat()
    }
    
    // Draw bezier curve line
    drawBezierLineChart(points, chartConfig)
    
    // Draw interactive data points
    drawInteractiveDataPoints(points, selectedPoint, animationProgress, showDataPoints)
    
    // Draw personal record markers
    if (showPersonalRecords && metrics.personalRecords.isNotEmpty()) {
        drawPersonalRecordMarkers(
            data = data,
            personalRecords = metrics.personalRecords,
            points = points,
            plotArea = plotArea,
            density = density,
            textMeasurer = textMeasurer
        )
    }
    
    // Highlight selected point
    selectedPoint?.let { selected ->
        val selectedIndex = data.indexOf(selected)
        if (selectedIndex >= 0 && selectedIndex < points.size) {
            val (x, y) = points[selectedIndex]
            drawSelectedPointHighlight(x, y, density)
        }
    }
}

/**
 * Draw grid lines for better chart readability (matching 1RM progression style)
 */
private fun DrawScope.drawGrid(plotArea: PlotArea) {
    val gridColor = ChartColorsV2.Infrastructure.getGridColor(true)
    val strokeWidth = 1.dp.toPx()
    
    // Horizontal grid lines
    for (i in 1..4) {
        val y = plotArea.top + plotArea.height * i / 5
        drawLine(
            color = gridColor,
            start = Offset(plotArea.left, y),
            end = Offset(plotArea.right, y),
            strokeWidth = strokeWidth
        )
    }
    
    // Vertical grid lines
    for (i in 1..4) {
        val x = plotArea.left + plotArea.width * i / 5
        drawLine(
            color = gridColor,
            start = Offset(x, plotArea.top),
            end = Offset(x, plotArea.bottom),
            strokeWidth = strokeWidth
        )
    }
}

/**
 * Draw chart axes (matching 1RM progression style)
 */
private fun DrawScope.drawAxes(plotArea: PlotArea) {
    val axisColor = ChartColorsV2.Infrastructure.getAxisColor(true)
    val strokeWidth = 2.dp.toPx()
    
    // Bottom axis
    drawLine(
        color = axisColor,
        start = Offset(plotArea.left, plotArea.bottom),
        end = Offset(plotArea.right, plotArea.bottom),
        strokeWidth = strokeWidth
    )
    
    // Left axis
    drawLine(
        color = axisColor,
        start = Offset(plotArea.left, plotArea.top),
        end = Offset(plotArea.left, plotArea.bottom),
        strokeWidth = strokeWidth
    )
}

/**
 * Draw interactive data points with enhanced styling
 */
private fun DrawScope.drawInteractiveDataPoints(
    points: List<Pair<Float, Float>>,
    selectedPoint: VolumeDataPoint?,
    animationProgress: Float,
    showDataPoints: Boolean
) {
    if (!showDataPoints) return

    points.forEach { (x, y) ->
        val baseRadius = 4.dp.toPx()
        val animatedRadius = baseRadius * animationProgress
        
        // Draw point with V2 primary color
        drawCircle(
            color = ChartColorsV2.getSeriesColor(0),
            radius = animatedRadius,
            center = Offset(x, y)
        )
        
        // Draw inner highlight for better visibility
        if (animationProgress > 0.5f) {
            drawCircle(
                color = Color.White,
                radius = animatedRadius * 0.4f,
                center = Offset(x, y)
            )
        }
    }
}

/**
 * Draw personal record markers on the chart
 */
private fun DrawScope.drawPersonalRecordMarkers(
    data: List<VolumeDataPoint>,
    personalRecords: List<VolumeDataPoint>,
    points: List<Pair<Float, Float>>,
    plotArea: PlotArea,
    density: Float,
    textMeasurer: TextMeasurer
) {
    personalRecords.forEach { pr ->
        val prIndex = data.indexOf(pr)
        if (prIndex >= 0 && prIndex < points.size) {
            val (x, y) = points[prIndex]
            
            // Draw PR marker background
            drawCircle(
                color = ChartColorsV2.Semantic.Excellent.copy(alpha = 0.3f),
                radius = 12.dp.toPx(),
                center = Offset(x, y)
            )
            
            // Draw PR marker
            drawCircle(
                color = ChartColorsV2.Semantic.Excellent,
                radius = 6.dp.toPx(),
                center = Offset(x, y)
            )
            
            // Draw PR label
            val textResult = textMeasurer.measure(
                text = "PR",
                style = TextStyle(
                    color = ChartColorsV2.Semantic.Excellent,
                    fontSize = 14.sp  // Updated to meet 14sp minimum requirement
                )
            )
            drawText(
                textLayoutResult = textResult,
                topLeft = Offset(
                    x - textResult.size.width / 2f,
                    (y - 20.dp.toPx() - textResult.size.height / 2f)
                        .coerceAtLeast(plotArea.top)
                )
            )
        }
    }
}

/**
 * Draw highlight for selected data point
 */
private fun DrawScope.drawSelectedPointHighlight(x: Float, y: Float, density: Float) {
    // Outer ring
    drawCircle(
        color = ChartColorsV2.getSeriesColor(0).copy(alpha = 0.3f),
        radius = 16.dp.toPx(),
        center = Offset(x, y)
    )
    
    // Inner highlight
    drawCircle(
        color = ChartColorsV2.getSeriesColor(0),
        radius = 8.dp.toPx(),
        center = Offset(x, y)
    )
    
    // Center point
    drawCircle(
        color = Color.White,
        radius = 4.dp.toPx(),
        center = Offset(x, y)
    )
}

/**
 * Find nearest data point to tap coordinates
 */
private fun findNearestDataPoint(
    tapOffset: Offset,
    data: List<VolumeDataPoint>,
    canvasSize: androidx.compose.ui.geometry.Size,
    scale: ChartScale,
    density: Float
): VolumeDataPoint? {
    if (data.isEmpty()) return null
    val plotArea = chartPlotArea(
        width = canvasSize.width,
        height = canvasSize.height,
        density = density
    )
    
    var nearestPoint: VolumeDataPoint? = null
    var minDistance = Float.MAX_VALUE
    
    data.forEachIndexed { index, dataPoint ->
        val x = if (data.size == 1) {
            plotArea.left + plotArea.width * 0.5f
        } else {
            plotArea.left + plotArea.width * index / (data.size - 1).toFloat()
        }
        val normalizedValue = if (scale.isFlat) {
            0.5
        } else {
            (dataPoint.getVolumeAsDouble() - scale.minValue) / scale.range
        }
        val y = plotArea.bottom - (plotArea.height * normalizedValue.toFloat())
        
        val distance = kotlin.math.sqrt(
            (tapOffset.x - x).let { it * it } + (tapOffset.y - y).let { it * it }
        )
        
        if (distance < minDistance && distance < (44 * density)) { // 44dp minimum touch target per WCAG 2.1 AA
            minDistance = distance.toFloat()
            nearestPoint = dataPoint
        }
    }
    
    return nearestPoint
}

private data class PlotArea(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float = (right - left).coerceAtLeast(1f)
    val height: Float = (bottom - top).coerceAtLeast(1f)
}

private fun chartPlotArea(
    width: Float,
    height: Float,
    density: Float
): PlotArea {
    val topPadding = 30f * density
    val rightPadding = 4f * density
    val bottomPadding = 2f * density
    return PlotArea(
        left = 0f,
        top = topPadding.coerceAtMost(height * 0.25f),
        right = (width - rightPadding).coerceAtLeast(1f),
        bottom = (height - bottomPadding).coerceAtLeast(1f)
    )
}

/**
 * Chart metrics without title (title shown in widget container)
 */
@Composable
private fun ChartMetricsOnly(
    subtitle: String,
    metrics: ChartMetrics,
    unit: String = "kg"
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        
        if (metrics.isValid) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricChip(
                    label = "Peak",
                    value = "${metrics.maxValue.toInt()}$unit",
                    color = ChartColorsV2.getSeriesColor(0)
                )
                MetricChip(
                    label = "Avg",
                    value = "${metrics.avgValue.toInt()}$unit",
                    color = ChartColorsV2.getSeriesColor(1)
                )
            }
        }
    }
}

/**
 * Small metric chip for displaying key values
 */
@Composable
private fun MetricChip(
    label: String,
    value: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape)
        )
        
        Text(
            text = "$label $value",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}

/**
 * Selected point information display
 */
@Composable
private fun SelectedPointInfo(
    point: VolumeDataPoint,
    onDismiss: () -> Unit,
    unit: String = "kg"
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ChartColorsV2.getSeriesColor(0).copy(alpha = 0.08f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${point.getVolumeAsDouble().toInt()}$unit",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = ChartColorsV2.getSeriesColor(0)
                )
                Text(
                    text = point.date.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            if (point.workoutCount > 1) {
                Text(
                    text = "${point.workoutCount} workouts",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Generate zero-value data points for chart display
 */
private fun generateZeroVolumeData(): List<VolumeDataPoint> {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    return (0..6).map { daysAgo ->
        VolumeDataPoint(
            date = today.minus(DatePeriod(days = 6 - daysAgo)),
            volume = Weight.fromKilograms(0.0),
            workoutCount = 0,
            exerciseCount = 0
        )
    }
}

/**
 * Overlay message for zero data state
 */
@Composable
private fun ZeroDataOverlay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Start working out to see progress",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// VolumeDataPoint moved to domain model: com.example.liftrix.domain.model.analytics.VolumeDataPoint

// TimeRange enum removed - using consolidated TimeRangeType from domain layer
// Import: import com.example.liftrix.domain.model.analytics.TimeRangeType

/**
 * Chart metrics calculation
 */
private data class ChartScale(
    val minValue: Double,
    val range: Double,
    val isFlat: Boolean
)

private data class ChartMetrics(
    val minValue: Double,
    val maxValue: Double,
    val avgValue: Double,
    val range: Double,
    val personalRecords: List<VolumeDataPoint>,
    val isValid: Boolean
) {
    fun scaleFor(useZeroBaseline: Boolean): ChartScale {
        val scaleMin = if (useZeroBaseline) 0.0 else minValue
        val scaleRange = (maxValue - scaleMin).coerceAtLeast(1.0)
        return ChartScale(scaleMin, scaleRange, !useZeroBaseline && maxValue == minValue)
    }

    companion object {
        fun empty() = ChartMetrics(0.0, 0.0, 0.0, 0.0, emptyList(), false)
        
        fun forZeroData() = ChartMetrics(0.0, 10.0, 0.0, 10.0, emptyList(), true)
        
        fun calculate(data: List<VolumeDataPoint>): ChartMetrics {
            if (data.isEmpty()) return empty()
            
            val volumes = data.map { it.getVolumeAsDouble() }
            val minValue = volumes.minOrNull() ?: 0.0
            val maxValue = volumes.maxOrNull() ?: 0.0
            val avgValue = volumes.average()
            // SINGLE DATA POINT FIX: For single data points, create artificial range for proper rendering
            val range = if (data.size == 1 && maxValue > 0) {
                maxValue * 0.2 // 20% range around single point for proper scaling
            } else {
                (maxValue - minValue).coerceAtLeast(1.0) // Prevent division by zero
            }
            
            // Find personal records (local maxima)
            val personalRecords = mutableListOf<VolumeDataPoint>()
            data.forEachIndexed { index, point ->
                val isLocalMaxima = when {
                    index == 0 -> data.getOrNull(1)?.let { point.getVolumeAsDouble() >= it.getVolumeAsDouble() } ?: true
                    index == data.lastIndex -> point.getVolumeAsDouble() >= data[index - 1].getVolumeAsDouble()
                    else -> point.getVolumeAsDouble() >= data[index - 1].getVolumeAsDouble() &&
                           point.getVolumeAsDouble() >= data[index + 1].getVolumeAsDouble()
                }
                if (isLocalMaxima && point.getVolumeAsDouble() > avgValue) {
                    personalRecords.add(point)
                }
            }
            
            return ChartMetrics(minValue, maxValue, avgValue, range, personalRecords, true)
        }
    }
}

/**
 * Build accessibility content description
 */
private fun buildContentDescription(data: List<VolumeDataPoint>, timeRange: TimeRangeType): String {
    return buildString {
        append("Volume progress chart for ${timeRange.displayName}. ")
        if (data.isNotEmpty()) {
            val metrics = ChartMetrics.calculate(data)
            append("${data.size} data points. ")
            append("Range from ${metrics.minValue.toInt()}kg to ${metrics.maxValue.toInt()}kg. ")
            append("Average volume ${metrics.avgValue.toInt()}kg. ")
            if (metrics.personalRecords.isNotEmpty()) {
                append("${metrics.personalRecords.size} personal records marked.")
            }
        } else {
            append("No data available.")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ModernVolumeChartPreview() {
    LiftrixTheme {
        val sampleData = listOf(
            VolumeDataPoint(
                date = LocalDate(2024, 1, 1),
                volume = Weight.fromKilograms(1200.0),
                workoutCount = 1
            ),
            VolumeDataPoint(
                date = LocalDate(2024, 1, 8),
                volume = Weight.fromKilograms(1350.0),
                workoutCount = 2
            ),
            VolumeDataPoint(
                date = LocalDate(2024, 1, 15),
                volume = Weight.fromKilograms(1180.0),
                workoutCount = 1
            ),
            VolumeDataPoint(
                date = LocalDate(2024, 1, 22),
                volume = Weight.fromKilograms(1450.0),
                workoutCount = 1
            ),
            VolumeDataPoint(
                date = LocalDate(2024, 1, 29),
                volume = Weight.fromKilograms(1520.0),
                workoutCount = 2
            )
        )
        
        ModernVolumeChart(
            data = sampleData,
            timeRange = TimeRangeType.MONTH,
            modifier = Modifier.padding(16.dp)
        )
    }
}
