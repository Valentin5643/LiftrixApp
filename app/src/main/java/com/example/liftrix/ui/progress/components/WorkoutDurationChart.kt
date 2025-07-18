package com.example.liftrix.ui.progress.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.repository.DurationDataPoint
// Chart implementation using Compose Canvas
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import com.example.liftrix.ui.common.analytics.ChartThemeProvider
import com.example.liftrix.ui.theme.LiftrixColors
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.math.roundToInt

/**
 * Modern workout duration chart component with side metrics panel.
 * 
 * Displays a bar chart showing duration trends alongside key metrics including
 * average duration, total workout time, longest session, and consistency score.
 * Uses a modern dashboard layout with chart and metrics side-by-side.
 * 
 * @param data List of duration data points to display
 * @param isLoading Whether the chart is currently loading data
 * @param modifier Modifier for styling the chart container
 */
@Composable
fun WorkoutDurationChart(
    data: List<DurationDataPoint>,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Workout Duration",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingState()
                    }
                }
                data.isEmpty() -> {
                    // Show zero-value chart instead of empty state
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Chart takes 65% of width
                        Box(
                            modifier = Modifier
                                .weight(0.65f)
                                .height(200.dp)
                        ) {
                            DurationBarChart(
                                data = getZeroDurationData(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        // Metrics panel takes 35% of width
                        DurationMetricsPanel(
                            data = getZeroDurationData(),
                            modifier = Modifier.weight(0.35f)
                        )
                    }
                }
                else -> {
                    // Modern layout: Chart + Metrics side by side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Chart takes 65% of width
                        Box(
                            modifier = Modifier
                                .weight(0.65f)
                                .height(200.dp)
                        ) {
                            DurationBarChart(
                                data = data,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        // Metrics panel takes 35% of width
                        DurationMetricsPanel(
                            data = data,
                            modifier = Modifier.weight(0.35f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Loading state for the duration chart
 */
@Composable
private fun LoadingState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Loading duration data...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * Empty state for the duration chart
 */
@Composable
private fun EmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No duration data available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Complete timed workouts to see duration trends",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * Actual duration line chart using Vico
 */
@Composable
private fun DurationBarChart(
    data: List<DurationDataPoint>,
    modifier: Modifier = Modifier
) {
    // Don't return early for empty data - let it render with zero values

    val density = LocalDensity.current
    val normalizedData = remember(data) {
        val maxValue = data.maxOfOrNull { it.durationMinutes } ?: 0
        val minValue = data.minOfOrNull { it.durationMinutes } ?: 0
        val range = maxValue - minValue
        if (range == 0) {
            // For zero values, show small bars at the bottom
            data.map { 0.1f }
        } else {
            data.map { (it.durationMinutes - minValue).toFloat() / range.toFloat() }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        LiftrixColors.Secondary.copy(alpha = 0.1f),
                        Color.Transparent
                    )
                )
            )
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawDurationBarChart(
                data = normalizedData,
                color = LiftrixColors.Secondary,
                cornerRadius = with(density) { 6.dp.toPx() }
            )
        }
    }
}

/**
 * Side metrics panel showing key duration statistics
 */
@Composable
private fun DurationMetricsPanel(
    data: List<DurationDataPoint>,
    modifier: Modifier = Modifier
) {
    val metrics = remember(data) { calculateDurationMetrics(data) }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Average Duration
        MetricItem(
            label = "Avg Duration",
            value = "${metrics.averageDuration} min",
            subtitle = "per workout",
            icon = Icons.Default.AccessTime
        )
        
        // Total Time
        MetricItem(
            label = "Total Time",
            value = formatTotalTime(metrics.totalTime),
            subtitle = "${data.size} sessions"
        )
        
        // Longest Session
        MetricItem(
            label = "Longest Session",
            value = "${metrics.longestSession} min",
            subtitle = metrics.longestDate,
            icon = Icons.Default.Timer
        )
        
        // Consistency
        MetricItem(
            label = "Consistency",
            value = "${metrics.consistencyScore}%",
            subtitle = metrics.consistencyLabel,
            valueColor = when {
                metrics.consistencyScore >= 80 -> MaterialTheme.colorScheme.primary
                metrics.consistencyScore >= 60 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.error
            }
        )
    }
}

/**
 * Individual metric item component for duration
 */
@Composable
private fun MetricItem(
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = valueColor,
                    modifier = Modifier.padding(end = 2.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = valueColor
            )
        }
        
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * Calculate duration metrics from data points
 */
private fun calculateDurationMetrics(data: List<DurationDataPoint>): DurationMetrics {
    if (data.isEmpty()) {
        return DurationMetrics(
            averageDuration = 0,
            totalTime = 0,
            longestSession = 0,
            longestDate = "",
            consistencyScore = 0,
            consistencyLabel = "No Data"
        )
    }
    
    val totalTime = data.sumOf { it.durationMinutes }
    val averageDuration = totalTime / data.size
    val longestDataPoint = data.maxByOrNull { it.durationMinutes }
    
    // Calculate consistency (how close durations are to average)
    val variance = data.map { (it.durationMinutes - averageDuration).let { diff -> diff * diff } }.average()
    val standardDeviation = kotlin.math.sqrt(variance)
    val consistencyScore = ((1 - (standardDeviation / averageDuration.coerceAtLeast(1))) * 100)
        .coerceIn(0.0, 100.0).roundToInt()
    
    val consistencyLabel = when {
        consistencyScore >= 80 -> "Very Consistent"
        consistencyScore >= 60 -> "Fairly Consistent"
        consistencyScore >= 40 -> "Somewhat Varied"
        else -> "Highly Varied"
    }
    
    return DurationMetrics(
        averageDuration = averageDuration,
        totalTime = totalTime,
        longestSession = longestDataPoint?.durationMinutes ?: 0,
        longestDate = longestDataPoint?.date?.toString() ?: "",
        consistencyScore = consistencyScore,
        consistencyLabel = consistencyLabel
    )
}

/**
 * Format total time into hours and minutes
 */
private fun formatTotalTime(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

/**
 * Data class for duration metrics
 */
private data class DurationMetrics(
    val averageDuration: Int,
    val totalTime: Int,
    val longestSession: Int,
    val longestDate: String,
    val consistencyScore: Int,
    val consistencyLabel: String
)

/**
 * Generate zero-value sample data for empty state
 */
private fun getZeroDurationData(): List<DurationDataPoint> {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    return listOf(
        DurationDataPoint(date = today.minus(DatePeriod(days = 6)), durationMinutes = 0, workoutCount = 0),
        DurationDataPoint(date = today.minus(DatePeriod(days = 5)), durationMinutes = 0, workoutCount = 0),
        DurationDataPoint(date = today.minus(DatePeriod(days = 4)), durationMinutes = 0, workoutCount = 0),
        DurationDataPoint(date = today.minus(DatePeriod(days = 3)), durationMinutes = 0, workoutCount = 0),
        DurationDataPoint(date = today.minus(DatePeriod(days = 2)), durationMinutes = 0, workoutCount = 0),
        DurationDataPoint(date = today.minus(DatePeriod(days = 1)), durationMinutes = 0, workoutCount = 0),
        DurationDataPoint(date = today, durationMinutes = 0, workoutCount = 0)
    )
}

private fun DrawScope.drawDurationBarChart(
    data: List<Float>,
    color: Color,
    cornerRadius: Float
) {
    if (data.isEmpty()) return
    
    val barWidth = size.width / data.size * 0.7f
    val spacing = size.width / data.size
    
    data.forEachIndexed { index, value ->
        val x = index * spacing + spacing * 0.15f
        val barHeight = size.height * value
        val y = size.height - barHeight
        
        drawRoundRect(
            color = color,
            topLeft = Offset(x, y),
            size = Size(barWidth, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
        )
    }
} 