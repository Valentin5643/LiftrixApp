package com.example.liftrix.ui.progress.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.FitnessCenter
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
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.components.cards.StatCard
import com.example.liftrix.ui.components.cards.Trend
import com.example.liftrix.ui.components.layouts.GridSystem
import com.example.liftrix.ui.theme.LiftrixColors
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import kotlin.math.roundToInt

/**
 * Modern workout volume chart component with enhanced data dashboard styling.
 * 
 * Features professional chart design with brand colors, enhanced visual hierarchy,
 * and modern card-based layout. Displays volume trends alongside key metrics in
 * an asymmetrical composition for data dashboard feeling.
 * 
 * Key improvements:
 * - LiftrixCard system with 24dp border radius
 * - Enhanced chart styling with brand colors
 * - Professional data dashboard layout
 * - Improved visual hierarchy and spacing
 * - Modern metrics panel with trend indicators
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
    LiftrixCard(
        modifier = modifier.fillMaxWidth(),
        contentDescription = "Workout volume trend chart with statistics"
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(GridSystem.spacing3)
        ) {
            // Modern header with enhanced typography
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Volume Trend",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Total weight lifted over time",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(GridSystem.iconMedium)
                )
            }
            
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ModernLoadingState()
                    }
                }
                data.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ModernEmptyState()
                    }
                }
                else -> {
                    // Modern asymmetrical layout: Chart + Metrics
                    Column(
                        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing3)
                    ) {
                        // Chart section
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) {
                            ModernVolumeLineChart(
                                data = data,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        // Metrics panel
                        ModernVolumeMetricsPanel(
                            data = data,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modern loading state with enhanced visual design
 */
@Composable
private fun ModernLoadingState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing3)
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = "Loading volume data...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Modern empty state with enhanced visual hierarchy
 */
@Composable
private fun ModernEmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
    ) {
        Icon(
            imageVector = Icons.Default.FitnessCenter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(GridSystem.iconLarge)
        )
        Text(
            text = "No volume data available",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "Complete workouts with weights to see volume trends",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Enhanced volume line chart with modern styling
 */
@Composable
private fun ModernVolumeLineChart(
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
    
    // Create the chart with enhanced styling
    CartesianChartHost(
        rememberCartesianChart(
            rememberLineCartesianLayer()
        ),
        modelProducer,
        modifier = modifier
    )
}

/**
 * Modern volume metrics panel with enhanced visual hierarchy
 */
@Composable
private fun ModernVolumeMetricsPanel(
    data: List<VolumeDataPoint>,
    modifier: Modifier = Modifier
) {
    val totalVolume = data.sumOf { it.totalVolume.toDouble() }.toFloat()
    val averageVolume = if (data.isNotEmpty()) totalVolume / data.size else 0f
    val peakVolume = data.maxOfOrNull { it.totalVolume } ?: 0f
    val trend = calculateVolumeTrend(data)
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
    ) {
        // Key metrics header
        Text(
            text = "Key Metrics",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        
        // Metrics grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing3)
        ) {
            // Total volume
            MetricCard(
                title = "Total Volume",
                value = "${(totalVolume / 1000).roundToInt()}K lbs",
                subtitle = "${data.size} workouts",
                modifier = Modifier.weight(1f)
            )
            
            // Average volume
            MetricCard(
                title = "Average",
                value = "${(averageVolume / 1000).roundToInt()}K lbs",
                subtitle = "per workout",
                modifier = Modifier.weight(1f)
            )
            
            // Peak volume
            MetricCard(
                title = "Peak Volume",
                value = "${(peakVolume / 1000).roundToInt()}K lbs",
                subtitle = "best session",
                trend = trend,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Individual metric card with modern styling
 */
@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trend: VolumeMetricTrend? = null
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(GridSystem.spacing3),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GridSystem.spacing1)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing1)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                trend?.let {
                    Icon(
                        imageVector = when (it) {
                            VolumeMetricTrend.INCREASING -> Icons.Default.TrendingUp
                            VolumeMetricTrend.DECREASING -> Icons.Default.TrendingDown
                            VolumeMetricTrend.STABLE -> Icons.Default.TrendingFlat
                        },
                        contentDescription = null,
                        tint = when (it) {
                            VolumeMetricTrend.INCREASING -> MaterialTheme.colorScheme.primary
                            VolumeMetricTrend.DECREASING -> MaterialTheme.colorScheme.error
                            VolumeMetricTrend.STABLE -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Calculate volume trend from data points
 */
private fun calculateVolumeTrend(data: List<VolumeDataPoint>): VolumeMetricTrend? {
    if (data.size < 2) return null
    
    val recentData = data.takeLast(5)
    val earlierData = data.dropLast(5).takeLast(5)
    
    if (recentData.isEmpty() || earlierData.isEmpty()) return null
    
    val recentAverage = recentData.map { it.totalVolume }.average()
    val earlierAverage = earlierData.map { it.totalVolume }.average()
    
    val changePercent = ((recentAverage - earlierAverage) / earlierAverage) * 100
    
    return when {
        changePercent > 5 -> VolumeMetricTrend.INCREASING
        changePercent < -5 -> VolumeMetricTrend.DECREASING
        else -> VolumeMetricTrend.STABLE
    }
}

/**
 * Volume metric trend enumeration
 */
private enum class VolumeMetricTrend {
    INCREASING,
    DECREASING,
    STABLE
} 