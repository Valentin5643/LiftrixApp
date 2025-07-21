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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.repository.VolumeDataPoint
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.core.formatting.WeightFormatter
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.components.cards.StatCard
import com.example.liftrix.ui.components.cards.Trend
import com.example.liftrix.ui.components.layouts.GridSystem
import com.example.liftrix.ui.theme.LiftrixColors
// Chart implementation using Compose Canvas
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import com.example.liftrix.ui.common.analytics.ChartThemeProvider
import kotlin.math.roundToInt
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

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
 * @param weightUnit The user's preferred weight unit for display
 * @param weightFormatter Formatter for weight values
 * @param modifier Modifier for styling the chart container
 */
@Composable
fun WorkoutVolumeChart(
    data: List<VolumeDataPoint>,
    isLoading: Boolean,
    weightUnit: WeightUnit,
    weightFormatter: WeightFormatter,
    modifier: Modifier = Modifier
) {
    // VolumeDebug: Check what the chart receives
    val totalVolume = data.sumOf { it.totalVolume.toDouble() }.toFloat()
    timber.log.Timber.d("VolumeDebug Chart received ${data.size} points, ${totalVolume}kg total, isLoading=$isLoading")
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
                    timber.log.Timber.d("VolumeDebug Chart showing LOADING state")
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
                    timber.log.Timber.d("VolumeDebug Chart showing EMPTY state (zero data)")
                    // Show zero-value chart instead of empty state
                    Column(
                        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing3)
                    ) {
                        // Chart section with zero data
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) {
                            ModernVolumeLineChart(
                                data = getZeroVolumeData(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        // Metrics panel with zero values
                        ModernVolumeMetricsPanel(
                            data = getZeroVolumeData(),
                            weightUnit = weightUnit,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                else -> {
                    timber.log.Timber.d("VolumeDebug Chart showing DATA state with ${data.size} points")
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
                            weightUnit = weightUnit,
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
 * Enhanced volume bar chart with Vico implementation
 */
@Composable
private fun ModernVolumeLineChart(
    data: List<VolumeDataPoint>,
    modifier: Modifier = Modifier
) {
    // Don't return early for empty data - let it render with zero values

    val density = LocalDensity.current
    val normalizedData = remember(data) {
        val maxValue = data.maxOfOrNull { it.totalVolume } ?: 0f
        val minValue = data.minOfOrNull { it.totalVolume } ?: 0f
        val range = maxValue - minValue
        
        timber.log.Timber.d("VolumeDebug Chart normalization: min=${minValue}kg, max=${maxValue}kg, range=${range}kg")
        
        if (range == 0f) {
            // For zero values, show a flat line at the bottom
            timber.log.Timber.d("VolumeDebug Chart using flat line (range=0)")
            data.map { 0.1f }
        } else {
            val normalized = data.map { (it.totalVolume - minValue) / range }
            timber.log.Timber.d("VolumeDebug Chart normalized to: ${normalized}")
            normalized
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
                        LiftrixColors.Primary.copy(alpha = 0.15f),
                        Color.Transparent
                    )
                )
            )
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawWorkoutVolumeLineChart(
                data = normalizedData,
                color = LiftrixColors.Primary,
                strokeWidth = with(density) { 3.dp.toPx() },
                pointRadius = with(density) { 8.dp.toPx() }
            )
        }
    }
}

/**
 * Modern volume metrics panel with enhanced visual hierarchy
 */
@Composable
private fun ModernVolumeMetricsPanel(
    data: List<VolumeDataPoint>,
    weightUnit: WeightUnit,
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
                value = formatVolumeInUserUnit(totalVolume, weightUnit),
                subtitle = "${data.size} workouts",
                modifier = Modifier.weight(1f)
            )
            
            // Average volume
            MetricCard(
                title = "Average",
                value = formatVolumeInUserUnit(averageVolume, weightUnit),
                subtitle = "per workout",
                modifier = Modifier.weight(1f)
            )
            
            // Peak volume
            MetricCard(
                title = "Peak Volume",
                value = formatVolumeInUserUnit(peakVolume, weightUnit),
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

/**
 * Generate zero-value sample data for empty state
 */
private fun getZeroVolumeData(): List<VolumeDataPoint> {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    return listOf(
        VolumeDataPoint(date = today.minus(DatePeriod(days = 6)), totalVolume = 0f, exerciseCount = 0),
        VolumeDataPoint(date = today.minus(DatePeriod(days = 5)), totalVolume = 0f, exerciseCount = 0),
        VolumeDataPoint(date = today.minus(DatePeriod(days = 4)), totalVolume = 0f, exerciseCount = 0),
        VolumeDataPoint(date = today.minus(DatePeriod(days = 3)), totalVolume = 0f, exerciseCount = 0),
        VolumeDataPoint(date = today.minus(DatePeriod(days = 2)), totalVolume = 0f, exerciseCount = 0),
        VolumeDataPoint(date = today.minus(DatePeriod(days = 1)), totalVolume = 0f, exerciseCount = 0),
        VolumeDataPoint(date = today, totalVolume = 0f, exerciseCount = 0)
    )
}

private fun DrawScope.drawWorkoutVolumeLineChart(
    data: List<Float>,
    color: Color,
    strokeWidth: Float,
    pointRadius: Float
) {
    timber.log.Timber.d("VolumeDebug Canvas drawing with ${data.size} points: $data")
    
    if (data.isEmpty()) {
        timber.log.Timber.d("VolumeDebug Canvas skipped - no data")
        return
    }
    
    if (data.size == 1) {
        timber.log.Timber.d("VolumeDebug Canvas drawing single point as centered dot")
        // For single data point, draw a centered dot
        val centerX = size.width / 2f
        val centerY = size.height * (1f - data[0])
        
        drawCircle(
            color = color,
            radius = pointRadius * 1.5f, // Slightly larger for single point visibility
            center = Offset(centerX, centerY)
        )
        return
    }
    
    val spacing = size.width / (data.size - 1).coerceAtLeast(1)
    val path = Path()
    
    // Create line path
    data.forEachIndexed { index, value ->
        val x = index * spacing
        val y = size.height * (1f - value)
        
        if (index == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    
    // Draw line
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round
        )
    )
    
    // Draw points
    data.forEachIndexed { index, value ->
        val x = index * spacing
        val y = size.height * (1f - value)
        
        drawCircle(
            color = color,
            radius = pointRadius,
            center = Offset(x, y)
        )
    }
}

/**
 * Formats volume values in user's preferred unit with appropriate scaling.
 * Shows volumes in thousands (K) for better readability.
 */
private fun formatVolumeInUserUnit(volumeKg: Float, weightUnit: WeightUnit): String {
    // Volume is stored in kg in database, convert to user's preferred unit
    val convertedVolume = when (weightUnit) {
        WeightUnit.KILOGRAMS -> volumeKg
        WeightUnit.POUNDS -> volumeKg / 0.453592f  // Convert kg to lbs: kg ÷ 0.453592
    }
    
    return when {
        convertedVolume >= 1000 -> "${(convertedVolume / 1000).toInt()}K ${weightUnit.symbol}"
        convertedVolume >= 100 -> "${convertedVolume.toInt()} ${weightUnit.symbol}"
        else -> "${"%.1f".format(convertedVolume)} ${weightUnit.symbol}"
    }
} 