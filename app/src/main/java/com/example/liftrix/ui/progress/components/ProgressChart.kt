package com.example.liftrix.ui.progress.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlinx.datetime.LocalDate
import kotlin.math.cos
import kotlin.math.sin

// Chart implementation using Compose Canvas for reliable rendering
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import com.example.liftrix.ui.common.analytics.ChartThemeProvider

/**
 * Chart types supported by the ProgressChart component
 */
enum class ChartType(val displayName: String, val icon: ImageVector) {
    LINE("Line Chart", Icons.Default.ShowChart),
    BAR("Bar Chart", Icons.Default.BarChart),
    RADIAL("Radial Progress", Icons.Default.Analytics)
}

/**
 * Data point for chart visualization
 */
data class ChartDataPoint(
    val date: LocalDate,
    val value: Float,
    val label: String? = null
)

/**
 * Chart data container with metadata
 */
data class ChartData(
    val dataPoints: List<ChartDataPoint>,
    val title: String,
    val valueUnit: String = "",
    val chartType: ChartType = ChartType.LINE,
    val primaryColor: Color = LiftrixColors.Primary,
    val gradientColors: List<Color> = listOf(LiftrixColors.Primary, LiftrixColors.Secondary)
) {
    val maxValue: Float get() = dataPoints.maxOfOrNull { it.value } ?: 0f
    val minValue: Float get() = dataPoints.minOfOrNull { it.value } ?: 0f
    val averageValue: Float get() = if (dataPoints.isNotEmpty()) dataPoints.sumOf { it.value.toDouble() }.toFloat() / dataPoints.size else 0f
    val isEmpty: Boolean get() = dataPoints.isEmpty()
}

/**
 * Progress chart component designed for analytics dashboard integration.
 * 
 * Provides chart visualization with Vico library integration placeholder,
 * loading states, and comprehensive accessibility support. Follows Liftrix
 * design system with Material 3 theming and athletic animations.
 * 
 * Note: This is a comprehensive foundation for Vico integration (UI-003 task).
 * Current implementation includes native Compose charts for immediate functionality.
 */
@Composable
fun ProgressChart(
    data: ChartData,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null
) {
    LiftrixCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                this.contentDescription = contentDescription ?: buildString {
                    append("${data.title} chart")
                    if (!data.isEmpty) {
                        append(" with ${data.dataPoints.size} data points")
                        append(", average value ${data.averageValue.toInt()} ${data.valueUnit}")
                    }
                }
            },
        onClick = onClick,
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Chart header
            ChartHeader(
                title = data.title,
                chartType = data.chartType,
                valueUnit = data.valueUnit,
                isLoading = isLoading
            )
            
            // Chart content area
            if (isLoading) {
                LoadingChartContent()
            } else if (data.isEmpty) {
                EmptyChartContent()
            } else {
                when (data.chartType) {
                    ChartType.LINE -> LineChart(data = data)
                    ChartType.BAR -> BarChart(data = data)
                    ChartType.RADIAL -> RadialChart(data = data)
                }
            }
            
            // Chart footer with statistics
            if (!isLoading && !data.isEmpty) {
                ChartFooter(data = data)
            }
        }
    }
}

@Composable
private fun ChartHeader(
    title: String,
    chartType: ChartType,
    valueUnit: String,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            
            if (!isLoading && valueUnit.isNotEmpty()) {
                Text(
                    text = "Values in $valueUnit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(LiftrixColors.Primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = chartType.icon,
                contentDescription = chartType.displayName,
                modifier = Modifier.size(16.dp),
                tint = LiftrixColors.Primary
            )
        }
    }
}

@Composable
private fun LoadingChartContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = LiftrixColors.Primary,
                strokeWidth = 3.dp
            )
            
            Text(
                text = "Loading chart data...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun EmptyChartContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BarChart,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            
            Text(
                text = "No data available",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Complete more workouts to see your progress",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LineChart(
    data: ChartData,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val normalizedData = remember(data.dataPoints) {
        if (data.dataPoints.isEmpty()) emptyList()
        else {
            val maxValue = data.maxValue
            val minValue = data.minValue
            val range = maxValue - minValue
            if (range == 0f) {
                data.dataPoints.map { 0.5f }
            } else {
                data.dataPoints.map { (it.value - minValue) / range }
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        data.primaryColor.copy(alpha = 0.1f),
                        Color.Transparent
                    )
                )
            )
            .padding(16.dp)
    ) {
        if (normalizedData.isNotEmpty()) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                drawLineChart(
                    data = normalizedData,
                    color = data.primaryColor,
                    strokeWidth = with(density) { 3.dp.toPx() },
                    pointRadius = with(density) { 6.dp.toPx() }
                )
            }
        }
    }
}

@Composable
private fun BarChart(
    data: ChartData,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val normalizedData = remember(data.dataPoints) {
        if (data.dataPoints.isEmpty()) emptyList()
        else {
            val maxValue = data.maxValue
            val minValue = data.minValue
            val range = maxValue - minValue
            if (range == 0f) {
                data.dataPoints.map { 0.5f }
            } else {
                data.dataPoints.map { (it.value - minValue) / range }
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(vertical = 16.dp)
    ) {
        if (normalizedData.isNotEmpty()) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                drawBarChart(
                    data = normalizedData,
                    color = data.primaryColor,
                    cornerRadius = with(density) { 4.dp.toPx() }
                )
            }
        }
    }
}

@Composable
private fun RadialChart(
    data: ChartData,
    modifier: Modifier = Modifier
) {
    val progress = if (data.maxValue > 0) data.averageValue / data.maxValue else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1500),
        label = "radial_progress"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        RadialProgressVisualization(
            progress = animatedProgress,
            data = data
        )
    }
}

@Composable
private fun SimpleLineChartVisualization(
    data: ChartData,
    modifier: Modifier = Modifier
) {
    // Simple placeholder visualization - replace with Vico in UI-003
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "📈 Line Chart\n${data.dataPoints.size} data points",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SimpleBarChartVisualization(
    data: ChartData,
    modifier: Modifier = Modifier
) {
    // Simple bar representation - replace with Vico in UI-003
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        data.dataPoints.take(7).forEach { point ->
            val heightFraction = if (data.maxValue > 0) point.value / data.maxValue else 0f
            val animatedHeight by animateFloatAsState(
                targetValue = heightFraction,
                animationSpec = tween(durationMillis = 800),
                label = "bar_height"
            )
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((200 * animatedHeight).dp)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = data.gradientColors
                        )
                    )
            )
        }
    }
}

@Composable
private fun RadialProgressVisualization(
    progress: Float,
    data: ChartData,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        // Background circle
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        )
        
        // Progress indicator
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier.size(140.dp),
            color = data.primaryColor,
            strokeWidth = 8.dp
        )
        
        // Center text
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = data.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ChartFooter(
    data: ChartData,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatisticItem(
            label = "Max",
            value = "${data.maxValue.toInt()} ${data.valueUnit}",
            modifier = Modifier.weight(1f)
        )
        
        StatisticItem(
            label = "Average",
            value = "${data.averageValue.toInt()} ${data.valueUnit}",
            modifier = Modifier.weight(1f)
        )
        
        StatisticItem(
            label = "Points",
            value = "${data.dataPoints.size}",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatisticItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

private fun DrawScope.drawLineChart(
    data: List<Float>,
    color: Color,
    strokeWidth: Float,
    pointRadius: Float
) {
    if (data.size < 2) return
    
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

private fun DrawScope.drawBarChart(
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

@Preview(showBackground = true)
@Composable
private fun ProgressChartPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sample data
            val sampleData = ChartData(
                dataPoints = listOf(
                    ChartDataPoint(LocalDate(2024, 1, 1), 100f),
                    ChartDataPoint(LocalDate(2024, 1, 2), 150f),
                    ChartDataPoint(LocalDate(2024, 1, 3), 120f),
                    ChartDataPoint(LocalDate(2024, 1, 4), 180f),
                    ChartDataPoint(LocalDate(2024, 1, 5), 200f)
                ),
                title = "Weekly Volume",
                valueUnit = "kg"
            )
            
            ProgressChart(
                data = sampleData.copy(chartType = ChartType.LINE)
            )
            
            ProgressChart(
                data = sampleData.copy(chartType = ChartType.RADIAL, title = "Goal Progress")
            )
        }
    }
}