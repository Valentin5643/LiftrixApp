package com.example.liftrix.ui.progress.components.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.core.formatting.WeightFormatter
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.ChartSummary
import com.example.liftrix.domain.model.analytics.ChartWidgetData
import com.example.liftrix.domain.model.analytics.DataPoint
import com.example.liftrix.domain.model.analytics.TrendDirection
import com.example.liftrix.domain.repository.FrequencyDataPoint
import com.example.liftrix.domain.repository.VolumeDataPoint
import com.example.liftrix.ui.progress.components.WorkoutFrequencyHeatmap
import com.example.liftrix.ui.progress.components.WorkoutVolumeChart
import com.example.liftrix.ui.progress.components.widgets.FolderStyleWidget
import com.example.liftrix.ui.theme.LiftrixColors
import kotlin.math.max
import kotlin.math.min

/**
 * Chart category widgets for trend visualization and pattern analysis.
 * 
 * All chart widgets follow consistent patterns:
 * - Visual data representation with charts
 * - Summary statistics and trends
 * - Time range indicators
 * - Interactive chart elements
 */

/**
 * Volume chart widget - visual volume progression with folder-style design
 */
@Composable
fun VolumeChartWidget(
    data: ChartWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useFolderStyle: Boolean = true
) {
    if (useFolderStyle) {
        FolderStyleWidget(
            title = "Volume Chart",
            icon = Icons.Default.ShowChart,
            onClick = onClick,
            modifier = modifier,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            iconTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
    } else {
        // Convert ChartWidgetData to VolumeDataPoint list
        val volumeData = data?.let { chartData ->
            chartData.dataPoints.map { point ->
                VolumeDataPoint(
                    date = kotlinx.datetime.LocalDate.fromEpochDays(point.x.toInt()),
                    totalVolume = point.y,
                    exerciseCount = 1 // Default value, could be enhanced
                )
            }
        } ?: emptyList()
        
        // Use the actual WorkoutVolumeChart component
        WorkoutVolumeChart(
            data = volumeData,
            isLoading = data?.isLoading == true,
            weightUnit = WeightUnit.KILOGRAMS, // Default to kg, could be made configurable
            weightFormatter = WeightFormatter(), // Use default formatter
            modifier = modifier
        )
    }
}

/**
 * Duration chart widget - workout duration trends
 */
@Composable
fun DurationChartWidget(
    data: ChartWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseWidget(
        title = "Duration Chart",
        subtitle = data?.timeRange,
        isLoading = data?.isLoading == true,
        error = data?.error?.message,
        onRefresh = onRefresh,
        onClick = onClick,
        modifier = modifier
    ) {
        data?.let { chartData ->
            ChartDisplay(
                chartData = chartData,
                chartColor = MaterialTheme.colorScheme.secondary,
                icon = Icons.Default.Schedule
            )
        }
    }
}

/**
 * Frequency chart widget - workout frequency patterns with folder-style design
 */
@Composable
fun FrequencyChartWidget(
    data: ChartWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useFolderStyle: Boolean = true
) {
    if (useFolderStyle) {
        FolderStyleWidget(
            title = "Frequency Chart",
            icon = Icons.Default.BarChart,
            onClick = onClick,
            modifier = modifier,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            iconTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
    } else {
        // Convert ChartWidgetData to FrequencyDataPoint list
        val frequencyData = data?.let { chartData ->
            chartData.dataPoints.map { point ->
                FrequencyDataPoint(
                    date = kotlinx.datetime.LocalDate.fromEpochDays(point.x.toInt()),
                    workoutCount = point.y.toInt(),
                    intensity = (chartData.dataPoints.maxOfOrNull { it.y }?.let { max -> 
                        if (max > 0f) point.y / max else 0f 
                    } ?: 0f)
                )
            }
        } ?: emptyList()
        
        // Use the actual WorkoutFrequencyHeatmap component
        WorkoutFrequencyHeatmap(
            data = frequencyData,
            isLoading = data?.isLoading == true,
            modifier = modifier
        )
    }
}

/**
 * Volume calendar widget - monthly calendar with daily volume
 */
@Composable
fun VolumeCalendarWidget(
    data: ChartWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseWidget(
        title = "Volume Calendar",
        subtitle = data?.timeRange,
        isLoading = data?.isLoading == true,
        error = data?.error?.message,
        onRefresh = onRefresh,
        onClick = onClick,
        modifier = modifier
    ) {
        data?.let { chartData ->
            CalendarDisplay(
                chartData = chartData,
                primaryColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Progress chart widget - comprehensive progress visualization with folder-style design
 */
@Composable
fun ProgressChartWidget(
    data: ChartWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useFolderStyle: Boolean = true
) {
    if (useFolderStyle) {
        FolderStyleWidget(
            title = "Progress Chart",
            icon = Icons.Default.TrendingUp,
            onClick = onClick,
            modifier = modifier,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            iconTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
    } else {
        BaseWidget(
            title = "Progress Chart",
            subtitle = data?.timeRange,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            onRefresh = onRefresh,
            onClick = onClick,
            modifier = modifier
        ) {
            data?.let { chartData ->
                ChartDisplay(
                    chartData = chartData,
                    chartColor = MaterialTheme.colorScheme.secondary,
                    icon = Icons.Default.ShowChart
                )
            }
        }
    }
}

/**
 * Weekly calorie trend widget - weekly calorie burn patterns
 */
@Composable
fun WeeklyCalorieTrendWidget(
    data: ChartWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseWidget(
        title = "Weekly Calorie Trend",
        subtitle = data?.timeRange,
        isLoading = data?.isLoading == true,
        error = data?.error?.message,
        onRefresh = onRefresh,
        onClick = onClick,
        modifier = modifier
    ) {
        data?.let { chartData ->
            ChartDisplay(
                chartData = chartData,
                chartColor = MaterialTheme.colorScheme.primary,
                icon = Icons.Default.LocalFireDepartment
            )
        }
    }
}

/**
 * Compact chart widget for smaller layouts
 */
@Composable
fun CompactChartWidget(
    widget: AnalyticsWidget,
    data: ChartWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompactBaseWidget(
        title = widget.displayName,
        isLoading = data?.isLoading == true,
        error = data?.error?.message,
        onRefresh = onRefresh,
        onClick = onClick,
        modifier = modifier
    ) {
        data?.let { chartData ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = chartData.summary.getFormattedSummary(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = chartData.timeRange,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                // Mini chart visualization
                MiniChart(
                    dataPoints = chartData.dataPoints.take(7), // Show last 7 points
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp, 24.dp)
                )
            }
        }
    }
}

/**
 * Standard chart display component
 */
@Composable
private fun ChartDisplay(
    chartData: ChartWidgetData,
    chartColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Chart header with icon and summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = chartColor.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = chartData.widgetType.displayName,
                        tint = chartColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chartData.summary.getFormattedSummary(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = "${chartData.yAxisLabel} • ${chartData.dataPoints.size} data points",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        
        // Chart visualization
        LineChart(
            dataPoints = chartData.dataPoints,
            color = chartColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        )
        
        // Chart statistics
        ChartStatistics(
            summary = chartData.summary,
            primaryColor = chartColor
        )
    }
}

/**
 * Calendar display for volume calendar widget
 */
@Composable
private fun CalendarDisplay(
    chartData: ChartWidgetData,
    primaryColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Calendar header
        Text(
            text = "Daily Volume Activity",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // Calendar grid (simplified 7x4 grid)
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Week headers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                    Text(
                        text = day,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.width(24.dp)
                    )
                }
            }
            
            // Calendar days (4 weeks)
            val maxValue = chartData.dataPoints.maxOfOrNull { it.y } ?: 1f
            for (week in 0..3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (day in 0..6) {
                        val dataIndex = week * 7 + day
                        val intensity = if (dataIndex < chartData.dataPoints.size) {
                            (chartData.dataPoints[dataIndex].y / maxValue).coerceIn(0f, 1f)
                        } else 0f
                        
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (intensity > 0f) 
                                        primaryColor.copy(alpha = 0.2f + intensity * 0.8f)
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                )
                        )
                    }
                }
            }
        }
        
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Less",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf(0.2f, 0.4f, 0.6f, 0.8f, 1.0f).forEach { alpha ->
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(primaryColor.copy(alpha = alpha))
                    )
                }
            }
            
            Text(
                text = "More",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Chart statistics display
 */
@Composable
private fun ChartStatistics(
    summary: ChartSummary,
    primaryColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem(
            label = "Average",
            value = "${summary.average.toInt()} ${summary.unit}",
            color = MaterialTheme.colorScheme.onSurface
        )
        
        StatItem(
            label = "Peak",
            value = "${summary.peak.toInt()} ${summary.unit}",
            color = primaryColor
        )
        
        StatItem(
            label = "Change",
            value = summary.trend.getPercentageDescription(summary.changePercentage),
            color = summary.trend.getColor()
        )
    }
}

/**
 * Individual statistic item
 */
@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = color
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

/**
 * Line chart component
 */
@Composable
private fun LineChart(
    dataPoints: List<DataPoint>,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (dataPoints.isEmpty()) return@Canvas
        
        val maxY = dataPoints.maxOfOrNull { it.y } ?: 1f
        val minY = dataPoints.minOfOrNull { it.y } ?: 0f
        val range = max(maxY - minY, 1f)
        
        val stepX = size.width / max(dataPoints.size - 1, 1)
        val path = Path()
        
        dataPoints.forEachIndexed { index, point ->
            val x = index * stepX
            val y = size.height - ((point.y - minY) / range) * size.height
            
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
            
            // Draw data points
            drawCircle(
                color = color,
                radius = 3.dp.toPx(),
                center = Offset(x, y)
            )
        }
        
        // Draw line
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

/**
 * Mini chart for compact displays
 */
@Composable
private fun MiniChart(
    dataPoints: List<DataPoint>,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (dataPoints.isEmpty()) return@Canvas
        
        val maxY = dataPoints.maxOfOrNull { it.y } ?: 1f
        val minY = dataPoints.minOfOrNull { it.y } ?: 0f
        val range = max(maxY - minY, 1f)
        
        val stepX = size.width / max(dataPoints.size - 1, 1)
        val path = Path()
        
        dataPoints.forEachIndexed { index, point ->
            val x = index * stepX
            val y = size.height - ((point.y - minY) / range) * size.height
            
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        // Draw mini line
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

/**
 * Extension function to get trend color
 */
@Composable
private fun TrendDirection.getColor(): Color = when (this) {
    TrendDirection.UP -> MaterialTheme.colorScheme.primary  // Persian Green for positive trends
    TrendDirection.DOWN -> MaterialTheme.colorScheme.error  // Red for negative trends (exception to 5-color rule) 
    TrendDirection.STABLE -> MaterialTheme.colorScheme.onSurfaceVariant  // Neutral color for stable
    TrendDirection.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant  // Neutral color for unknown
}
