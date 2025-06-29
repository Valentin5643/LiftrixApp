package com.example.liftrix.ui.progress.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
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
import com.example.liftrix.domain.repository.VolumeDataPoint
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart


import kotlin.math.roundToInt

/**
 * Modern workout volume chart component with side metrics panel.
 * 
 * Displays a line chart showing volume trends alongside key metrics including
 * total volume, average per workout, peak performance, and trend direction.
 * Uses a modern dashboard layout with chart and metrics side-by-side.
 * 
 * @param data List of volume data points to display
 * @param isLoading Whether the chart is currently loading data
 * @param modifier Modifier for styling the chart container
 */
@Composable
fun WorkoutVolumeChart(
    data: List<VolumeDataPoint>,
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
                text = "Workout Volume Trend",
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
                            VolumeLineChart(
                                data = data,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        // Metrics panel takes 35% of width
                        VolumeMetricsPanel(
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
 * Loading state for the volume chart
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
            text = "Loading volume data...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * Empty state for the volume chart
 */
@Composable
private fun EmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No volume data available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Complete workouts with weights to see volume trends",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * Actual volume line chart using Vico
 */
@Composable
private fun VolumeLineChart(
    data: List<VolumeDataPoint>,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    
    // Transform data to chart entries
    val chartEntries = remember(data) {
        data.map { it.totalVolume }
    }
    
    // Update chart data using LaunchedEffect for suspend function
    LaunchedEffect(chartEntries) {
        modelProducer.runTransaction {
            lineSeries {
                series(chartEntries)
            }
        }
    }
    
    // Create the chart using Vico compose API with default axes
    CartesianChartHost(
        rememberCartesianChart(
            rememberLineCartesianLayer()
        ),
        modelProducer,
        modifier = modifier
    )
}

/**
 * Side metrics panel showing key volume statistics
 */
@Composable
private fun VolumeMetricsPanel(
    data: List<VolumeDataPoint>,
    modifier: Modifier = Modifier
) {
    val metrics = remember(data) { calculateVolumeMetrics(data) }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Total Volume
        MetricItem(
            label = "Total Volume",
            value = "${metrics.totalVolume.roundToInt()} kg",
            subtitle = "${data.size} workouts"
        )
        
        // Average Volume
        MetricItem(
            label = "Avg per Workout",
            value = "${metrics.averageVolume.roundToInt()} kg",
            subtitle = "per session"
        )
        
        // Peak Volume
        MetricItem(
            label = "Peak Volume",
            value = "${metrics.peakVolume.roundToInt()} kg",
            subtitle = metrics.peakDate
        )
        
        // Trend
        MetricItem(
            label = "Trend",
            value = metrics.trendDirection,
            subtitle = "${metrics.trendPercentage}%",
            icon = metrics.trendIcon,
            valueColor = when (metrics.trendDirection) {
                "Increasing" -> MaterialTheme.colorScheme.primary
                "Decreasing" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * Individual metric item component
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
 * Calculate volume metrics from data points
 */
private fun calculateVolumeMetrics(data: List<VolumeDataPoint>): VolumeMetrics {
    if (data.isEmpty()) {
        return VolumeMetrics(
            totalVolume = 0f,
            averageVolume = 0f,
            peakVolume = 0f,
            peakDate = "",
            trendDirection = "No Data",
            trendPercentage = "0",
            trendIcon = Icons.Default.TrendingFlat
        )
    }
    
    val totalVolume = data.sumOf { it.totalVolume.toDouble() }.toFloat()
    val averageVolume = totalVolume / data.size
    val peakDataPoint = data.maxByOrNull { it.totalVolume }
    
    // Calculate trend (compare first half vs second half)
    val trend = if (data.size >= 4) {
        val midPoint = data.size / 2
        val firstHalfAvg = data.take(midPoint).map { it.totalVolume }.average()
        val secondHalfAvg = data.drop(midPoint).map { it.totalVolume }.average()
        val percentChange = ((secondHalfAvg - firstHalfAvg) / firstHalfAvg * 100).roundToInt()
        
        when {
            percentChange > 5 -> Triple("Increasing", percentChange.toString(), Icons.Default.TrendingUp)
            percentChange < -5 -> Triple("Decreasing", percentChange.toString(), Icons.Default.TrendingDown)
            else -> Triple("Stable", percentChange.toString(), Icons.Default.TrendingFlat)
        }
    } else {
        Triple("Stable", "0", Icons.Default.TrendingFlat)
    }
    
    return VolumeMetrics(
        totalVolume = totalVolume,
        averageVolume = averageVolume,
        peakVolume = peakDataPoint?.totalVolume ?: 0f,
        peakDate = peakDataPoint?.date?.toString() ?: "",
        trendDirection = trend.first,
        trendPercentage = trend.second,
        trendIcon = trend.third
    )
}

/**
 * Data class for volume metrics
 */
private data class VolumeMetrics(
    val totalVolume: Float,
    val averageVolume: Float,
    val peakVolume: Float,
    val peakDate: String,
    val trendDirection: String,
    val trendPercentage: String,
    val trendIcon: ImageVector
) 