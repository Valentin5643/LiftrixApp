package com.example.liftrix.ui.progress.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.repository.DurationDataPoint
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart

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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState()
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
 * Actual duration bar chart using Vico
 */
@Composable
private fun DurationBarChart(
    data: List<DurationDataPoint>,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    
    // Transform data to chart entries
    val chartEntries = remember(data) {
        data.map { it.durationMinutes.toFloat() }
    }
    
    // Update chart data using LaunchedEffect for suspend function
    LaunchedEffect(chartEntries) {
        modelProducer.runTransaction {
            columnSeries {
                series(chartEntries)
            }
        }
    }
    
    // Create the chart using Vico compose API with proper axes
    CartesianChartHost(
        rememberCartesianChart(
            rememberColumnCartesianLayer()
        ),
        modelProducer,
        modifier = modifier
    )
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