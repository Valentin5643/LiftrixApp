package com.example.liftrix.ui.progress.detail.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.example.liftrix.domain.model.analytics.OneRmDataPoint
import com.example.liftrix.ui.progress.detail.OneRmDetailViewModel
import com.example.liftrix.ui.theme.ChartColorsV2
import kotlinx.datetime.daysUntil

/**
 * Data class for chart points
 */
private data class ChartPoint(
    val x: Float,
    val y: Float,
    val value: Float,
    val date: String,
    val exerciseId: String,
    val exerciseName: String
)

/**
 * Interactive 1RM progression chart component with enhanced features
 * 
 * Features:
 * - Animated line chart visualization with smooth transitions
 * - Interactive markers with tap detection and haptic feedback
 * - Multiple exercise overlay support with color coding
 * - Estimated vs actual 1RM toggle with visual differentiation
 * - Gradient fills and smooth animations
 * - Touch-based selection with detailed tooltips
 * - Grid lines and axis labels for better readability
 */
@Composable
fun OneRmProgressionChart(
    data: OneRmDetailViewModel.OneRmProgressionData,
    showEstimated: Boolean,
    modifier: Modifier = Modifier,
    animationDuration: Int = 1000
) {
    var selectedPoint by remember { mutableStateOf<ChartPoint?>(null) }
    val haptic = LocalHapticFeedback.current
    
    // Animation for chart appearance
    val animationProgress = remember { Animatable(0f) }
    
    LaunchedEffect(data) {
        animationProgress.animateTo(
            1f,
            animationSpec = tween(animationDuration, easing = FastOutSlowInEasing)
        )
    }
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        if (data.progressionPoints.isEmpty()) {
            EmptyChartState(modifier = Modifier.fillMaxSize())
        } else {
            Column(modifier = Modifier.padding(16.dp)) {
                // Chart header
                ChartHeader(
                    title = "1RM Progression",
                    subtitle = if (showEstimated) "Estimated Values" else "Actual Values",
                    showEstimated = showEstimated
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Interactive chart area
                val chartData = remember(data, showEstimated) {
                    prepareChartData(data, showEstimated)
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                ) {
                    InteractiveLineChart(
                        chartData = chartData,
                        animationProgress = animationProgress.value,
                        selectedPoint = selectedPoint,
                        onPointSelected = { point ->
                            selectedPoint = point
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Selected point tooltip
                    selectedPoint?.let { point ->
                        ChartTooltip(
                            point = point,
                            onDismiss = { selectedPoint = null },
                            modifier = Modifier.align(Alignment.TopEnd)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Chart legend
                ChartLegend(
                    exercises = data.exercisesIncluded,
                    showEstimated = showEstimated
                )
            }
        }
    }
}

/**
 * Chart header with title and subtitle
 */
@Composable
private fun ChartHeader(
    title: String,
    subtitle: String,
    showEstimated: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = if (showEstimated) "EST" else "ACT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * Interactive line chart with animations and tap detection
 */
@Composable
private fun InteractiveLineChart(
    chartData: List<ChartPoint>,
    animationProgress: Float,
    selectedPoint: ChartPoint?,
    onPointSelected: (ChartPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.pointerInput(chartData) {
            detectTapGestures { offset ->
                val tappedPoint = findNearestPoint(offset, androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()), chartData)
                tappedPoint?.let { onPointSelected(it) }
            }
        }
    ) {
        if (chartData.isNotEmpty()) {
            drawGrid()
            drawAxes()
            drawChartLines(chartData, animationProgress, selectedPoint)
            drawDataPoints(chartData, animationProgress, selectedPoint)
        }
    }
}

/**
 * Draw grid lines for better chart readability
 */
private fun DrawScope.drawGrid() {
    val gridColor = ChartColorsV2.Infrastructure.getGridColor(true)
    val strokeWidth = 1.dp.toPx()
    
    // Horizontal grid lines
    for (i in 1..4) {
        val y = size.height * i / 5
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = strokeWidth
        )
    }
    
    // Vertical grid lines
    for (i in 1..4) {
        val x = size.width * i / 5
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = strokeWidth
        )
    }
}

/**
 * Draw chart axes
 */
private fun DrawScope.drawAxes() {
    val axisColor = Color.Gray.copy(alpha = 0.5f)
    val strokeWidth = 2.dp.toPx()
    
    // Bottom axis
    drawLine(
        color = axisColor,
        start = Offset(0f, size.height),
        end = Offset(size.width, size.height),
        strokeWidth = strokeWidth
    )
    
    // Left axis
    drawLine(
        color = axisColor,
        start = Offset(0f, 0f),
        end = Offset(0f, size.height),
        strokeWidth = strokeWidth
    )
}

/**
 * Draw animated chart lines with gradient fills
 */
private fun DrawScope.drawChartLines(
    chartData: List<ChartPoint>,
    animationProgress: Float,
    selectedPoint: ChartPoint?
) {
    val groupedData = chartData.groupBy { it.exerciseId }
    val drawSeriesFill = groupedData.size == 1
    
    groupedData.entries.forEachIndexed { exerciseIndex, (exerciseId, points) ->
        val sortedPoints = points.sortedBy { it.x }
        val color = getChartColor(exerciseIndex)
        val strokeWidth = 3.dp.toPx()
        
        if (sortedPoints.size > 1) {
            val animatedPoints = sortedPoints.take((sortedPoints.size * animationProgress).toInt().coerceAtLeast(2))
            
            // Create path for line
            val path = Path().apply {
                animatedPoints.forEachIndexed { index, point ->
                    val screenX = point.x * size.width
                    val screenY = size.height - (point.y * size.height)
                    
                    if (index == 0) {
                        moveTo(screenX, screenY)
                    } else {
                        lineTo(screenX, screenY)
                    }
                }
            }
            
            // Draw gradient fill
            val gradient = Brush.verticalGradient(
                colors = listOf(
                    color.copy(alpha = 0.14f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = size.height
            )
            
            if (drawSeriesFill) {
                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(animatedPoints.last().x * size.width, size.height)
                    lineTo(animatedPoints.first().x * size.width, size.height)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = gradient
                )
            }
            
            // Draw line
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

/**
 * Draw interactive data points with selection indicators
 */
private fun DrawScope.drawDataPoints(
    chartData: List<ChartPoint>,
    animationProgress: Float,
    selectedPoint: ChartPoint?
) {
    val groupedData = chartData.groupBy { it.exerciseId }
    
    groupedData.entries.forEachIndexed { exerciseIndex, (exerciseId, points) ->
        val color = getChartColor(exerciseIndex)
        
        points.forEach { point ->
            val screenX = point.x * size.width
            val screenY = size.height - (point.y * size.height)
            val isSelected = point == selectedPoint
            val baseRadius = 4.dp.toPx()
            val selectedRadius = 8.dp.toPx()
            val currentRadius = if (isSelected) selectedRadius else baseRadius
            
            // Draw outer ring for selection
            if (isSelected) {
                drawCircle(
                    color = color.copy(alpha = 0.3f),
                    radius = selectedRadius * 1.5f,
                    center = Offset(screenX, screenY)
                )
            }
            
            // Draw point with animation scale
            val animatedRadius = currentRadius * animationProgress
            drawCircle(
                color = color,
                radius = animatedRadius,
                center = Offset(screenX, screenY)
            )
            
            // Draw inner highlight
            if (animationProgress > 0.5f) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.8f),
                    radius = animatedRadius * 0.4f,
                    center = Offset(screenX, screenY)
                )
            }
        }
    }
}

/**
 * Chart tooltip for selected points
 */
@Composable
private fun ChartTooltip(
    point: ChartPoint,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.inverseSurface,
        shape = MaterialTheme.shapes.small,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = point.exerciseName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = "${point.value.toInt()} kg",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.inverseOnSurface
            )
            
            Text(
                text = point.date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Chart legend with exercise colors
 */
@Composable
private fun ChartLegend(
    exercises: List<OneRmDetailViewModel.ExerciseInfo>,
    showEstimated: Boolean
) {
    if (exercises.size > 1) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(exercises) { exercise ->
                val index = exercises.indexOf(exercise)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = getChartColor(index),
                                shape = CircleShape
                            )
                    )
                    
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Empty state for when no chart data is available
 */
@Composable
private fun EmptyChartState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "📈",
                style = MaterialTheme.typography.headlineLarge
            )
            
            Text(
                text = "No Progression Data",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "Complete workouts to track 1RM progression",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Prepare chart data from ViewModel data
 */
private fun prepareChartData(
    data: OneRmDetailViewModel.OneRmProgressionData,
    showEstimated: Boolean
): List<ChartPoint> {
    if (data.progressionPoints.isEmpty()) return emptyList()

    val sortedPoints = data.progressionPoints.sortedWith(
        compareBy<OneRmDataPoint> { it.date }
            .thenBy { it.exerciseId.orEmpty() }
    )
    val allValues = sortedPoints.mapNotNull { it.chartValue(showEstimated) }
    val minValue = allValues.minOrNull() ?: 0f
    val maxValue = allValues.maxOrNull() ?: 0f
    val valueRange = maxValue - minValue
    val minDate = sortedPoints.minOf { it.date }
    val maxDate = sortedPoints.maxOf { it.date }
    val dateRangeDays = minDate.daysUntil(maxDate)

    return sortedPoints.mapNotNull { point ->
        val value = point.chartValue(showEstimated) ?: return@mapNotNull null
        val normalizedX = if (dateRangeDays > 0) {
            minDate.daysUntil(point.date).toFloat() / dateRangeDays.toFloat()
        } else {
            0.5f
        }
        val normalizedY = if (valueRange > 0f) {
            val verticalPadding = 0.08f
            verticalPadding + ((value - minValue) / valueRange) * (1f - verticalPadding * 2f)
        } else {
            0.5f
        }
        
        ChartPoint(
            x = normalizedX,
            y = normalizedY,
            value = value,
            date = point.date.toString(),
            exerciseId = point.exerciseId ?: "",
            exerciseName = point.exerciseName ?: ""
        )
    }
}

private fun OneRmDataPoint.chartValue(showEstimated: Boolean): Float? {
    return if (showEstimated) {
        bestOneRm
    } else {
        actualOneRm ?: bestOneRm
    }.takeIf { it > 0f }
}

/**
 * Find the nearest chart point to a tap location
 */
private fun findNearestPoint(
    tapOffset: Offset,
    canvasSize: androidx.compose.ui.geometry.Size,
    chartData: List<ChartPoint>
): ChartPoint? {
    val touchRadius = 40f // Increase touch target for better UX
    
    return chartData.minByOrNull { point ->
        val screenX = point.x * canvasSize.width
        val screenY = canvasSize.height - (point.y * canvasSize.height)
        val distance = kotlin.math.sqrt(
            (tapOffset.x - screenX) * (tapOffset.x - screenX) +
            (tapOffset.y - screenY) * (tapOffset.y - screenY)
        )
        distance
    }?.takeIf { point ->
        val screenX = point.x * canvasSize.width
        val screenY = canvasSize.height - (point.y * canvasSize.height)
        val distance = kotlin.math.sqrt(
            (tapOffset.x - screenX) * (tapOffset.x - screenX) +
            (tapOffset.y - screenY) * (tapOffset.y - screenY)
        )
        distance <= touchRadius
    }
}

/**
 * Gets chart color for exercise by index
 */
private fun getChartColor(index: Int): Color {
    return ChartColorsV2.getSeriesColor(index)
}
