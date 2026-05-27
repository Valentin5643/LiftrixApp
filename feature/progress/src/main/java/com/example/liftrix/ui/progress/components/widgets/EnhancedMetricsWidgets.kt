package com.example.liftrix.ui.progress.components.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.MetricWidgetData
import com.example.liftrix.domain.model.analytics.TrendDirection
import com.example.liftrix.ui.common.components.TrendIndicator
import com.example.liftrix.ui.progress.components.widgets.FolderStyleWidget
import com.example.liftrix.ui.theme.LiftrixColors

/**
 * Enhanced metric widgets that include mini-graph visualizations.
 * These replace the standard metric widgets to provide visual data representations.
 */

/**
 * Enhanced Total Volume Widget with folder-style design
 */
@Composable
fun EnhancedTotalVolumeWidget(
    data: MetricWidgetData?,
    graphData: List<Float>? = null,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useFolderStyle: Boolean = true,
    aspectRatio: Float = 1.1f  // Responsive aspect ratio based on layout context
) {
    if (useFolderStyle) {
        FolderStyleWidget(
            title = "Volume Chart",
            icon = Icons.Default.ShowChart,  // Line chart icon for volume - matches statistics style
            onClick = onClick,
            modifier = modifier,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            iconTint = MaterialTheme.colorScheme.primary,
            aspectRatio = aspectRatio
        )
    } else {
        BaseWidget(
            title = "Total Volume",
            subtitle = data?.comparisonPeriod,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            onRefresh = onRefresh,
            onClick = onClick,
            modifier = modifier
        ) {
            data?.let { widgetData ->
                EnhancedMetricDisplay(
                    primaryValue = widgetData.primaryValue,
                    unit = widgetData.unit,
                    trend = widgetData.trend,
                    trendPercentage = widgetData.trendPercentage,
                    secondaryValue = widgetData.secondaryValue,
                    icon = Icons.Default.Scale,
                    primaryColor = MaterialTheme.colorScheme.primary,
                    graphData = graphData ?: emptyList(),
                    graphType = GraphType.LINE
                )
            }
        }
    }
}

/**
 * Enhanced Workout Frequency Widget with folder-style design
 */
@Composable
fun EnhancedWorkoutFrequencyWidget(
    data: MetricWidgetData?,
    graphData: List<Float>? = null,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useFolderStyle: Boolean = true,
    aspectRatio: Float = 1.1f  // Responsive aspect ratio based on layout context
) {
    if (useFolderStyle) {
        FolderStyleWidget(
            title = "Frequency Chart",
            icon = Icons.Default.BarChart,  // Bar chart icon for frequency - matches statistics style
            onClick = onClick,
            modifier = modifier,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            iconTint = MaterialTheme.colorScheme.primary,
            aspectRatio = aspectRatio
        )
    } else {
        BaseWidget(
            title = "Workout Frequency",
            subtitle = data?.comparisonPeriod,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            onRefresh = onRefresh,
            onClick = onClick,
            modifier = modifier
        ) {
            data?.let { widgetData ->
                EnhancedMetricDisplay(
                    primaryValue = widgetData.primaryValue,
                    unit = widgetData.unit,
                    trend = widgetData.trend,
                    trendPercentage = widgetData.trendPercentage,
                    secondaryValue = widgetData.secondaryValue,
                    icon = Icons.Default.FitnessCenter,
                    primaryColor = MaterialTheme.colorScheme.primary,
                    graphData = graphData ?: emptyList(),
                    graphType = GraphType.BAR
                )
            }
        }
    }
}

/**
 * Enhanced Strength Progress Widget with folder-style design
 */
@Composable
fun EnhancedStrengthProgressWidget(
    data: MetricWidgetData?,
    graphData: List<Float>? = null,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useFolderStyle: Boolean = true,
    aspectRatio: Float = 1.1f  // Responsive aspect ratio based on layout context
) {
    if (useFolderStyle) {
        FolderStyleWidget(
            title = "Strength Progress",
            icon = Icons.Default.FitnessCenter,  // Dumbbell icon for strength - matches statistics style
            onClick = onClick,
            modifier = modifier,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            iconTint = MaterialTheme.colorScheme.primary,
            aspectRatio = aspectRatio
        )
    } else {
        BaseWidget(
            title = "Strength Progress",
            subtitle = data?.comparisonPeriod,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            onRefresh = onRefresh,
            onClick = onClick,
            modifier = modifier
        ) {
            data?.let { widgetData ->
                EnhancedMetricDisplay(
                    primaryValue = widgetData.primaryValue,
                    unit = widgetData.unit,
                    trend = widgetData.trend,
                    trendPercentage = widgetData.trendPercentage,
                    secondaryValue = widgetData.secondaryValue,
                    icon = Icons.Default.TrendingUp,
                    primaryColor = MaterialTheme.colorScheme.primary,
                    graphData = graphData ?: generateSampleProgressData(),
                    graphType = GraphType.LINE
                )
            }
        }
    }
}

/**
 * Enhanced Muscle Group Distribution Widget with folder-style design
 */
@Composable
fun EnhancedMuscleGroupWidget(
    data: MetricWidgetData?,
    distributionData: List<Pair<Float, Color>>? = null,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useFolderStyle: Boolean = true,
    aspectRatio: Float = 1.1f  // Responsive aspect ratio based on layout context
) {
    if (useFolderStyle) {
        FolderStyleWidget(
            title = "Muscle Groups",
            icon = Icons.Default.DonutLarge,  // Donut chart icon for muscle distribution
            onClick = onClick,
            modifier = modifier,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            iconTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            aspectRatio = aspectRatio
        )
    } else {
        BaseWidget(
            title = "Muscle Groups",
            subtitle = "Distribution",
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            onRefresh = onRefresh,
            onClick = onClick,
            modifier = modifier
        ) {
            data?.let { widgetData ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Primary metric display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${widgetData.primaryValue} ${widgetData.unit}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            widgetData.secondaryValue?.let { secondary ->
                                Text(
                                    text = secondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        
                        Icon(
                            imageVector = Icons.Default.DonutLarge,
                            contentDescription = "Distribution",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    // Distribution visualization
                    MiniDistributionChart(
                        segments = distributionData ?: generateSampleDistribution(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Enhanced 1RM Progression Widget with folder-style design
 */
@Composable
fun EnhancedOneRmProgressionWidget(
    data: MetricWidgetData?,
    graphData: List<Float>? = null,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useFolderStyle: Boolean = true,
    aspectRatio: Float = 1.1f  // Responsive aspect ratio based on layout context
) {
    if (useFolderStyle) {
        FolderStyleWidget(
            title = "1RM Progression",
            icon = Icons.Default.TrendingUp,  // Trending up for 1RM progression
            onClick = onClick,
            modifier = modifier,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            iconTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            aspectRatio = aspectRatio
        )
    } else {
        BaseWidget(
            title = "1RM Progression",
            subtitle = data?.comparisonPeriod,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            onRefresh = onRefresh,
            onClick = onClick,
            modifier = modifier
        ) {
            data?.let { widgetData ->
                EnhancedMetricDisplay(
                    primaryValue = widgetData.primaryValue,
                    unit = widgetData.unit,
                    trend = widgetData.trend,
                    trendPercentage = widgetData.trendPercentage,
                    secondaryValue = widgetData.secondaryValue,
                    icon = Icons.Default.ShowChart,
                    primaryColor = MaterialTheme.colorScheme.primary,
                    graphData = graphData ?: generateSample1RMData(),
                    graphType = GraphType.SPARKLINE
                )
            }
        }
    }
}

/**
 * Enhanced Volume Load Progression Widget with folder-style design
 */
@Composable
fun EnhancedVolumeLoadProgressionWidget(
    data: MetricWidgetData?,
    graphData: List<Float>? = null,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useFolderStyle: Boolean = true,
    aspectRatio: Float = 1.1f  // Responsive aspect ratio based on layout context
) {
    if (useFolderStyle) {
        FolderStyleWidget(
            title = "Volume Progression",
            icon = Icons.Default.Timeline,  // Timeline icon for volume progression
            onClick = onClick,
            modifier = modifier,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            iconTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            aspectRatio = aspectRatio
        )
    } else {
        BaseWidget(
            title = "Volume Progression",
            subtitle = data?.comparisonPeriod,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            onRefresh = onRefresh,
            onClick = onClick,
            modifier = modifier
        ) {
            data?.let { widgetData ->
                EnhancedMetricDisplay(
                    primaryValue = widgetData.primaryValue,
                    unit = widgetData.unit,
                    trend = widgetData.trend,
                    trendPercentage = widgetData.trendPercentage,
                    secondaryValue = widgetData.secondaryValue,
                    icon = Icons.Default.Timeline,
                    primaryColor = MaterialTheme.colorScheme.primary,
                    graphData = graphData ?: emptyList(),
                    graphType = GraphType.LINE
                )
            }
        }
    }
}

/**
 * Graph types for mini visualizations
 */
private enum class GraphType {
    LINE, BAR, SPARKLINE, PROGRESS_ARC, DISTRIBUTION
}

/**
 * Enhanced metric display with mini-graph visualization
 */
@Composable
private fun EnhancedMetricDisplay(
    primaryValue: String,
    unit: String,
    trend: TrendDirection,
    trendPercentage: Float,
    secondaryValue: String?,
    icon: ImageVector,
    primaryColor: Color,
    graphData: List<Float>,
    graphType: GraphType
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top row with value and icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Value and secondary info
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = primaryValue,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                secondaryValue?.let { secondary ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = secondary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        TrendIndicator(
                            trend = trend,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
            
            // Icon
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(8.dp),
                color = primaryColor.copy(alpha = 0.1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "Metric",
                        tint = primaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        
        // Mini graph visualization
        when (graphType) {
            GraphType.LINE -> MiniLineGraph(
                dataPoints = graphData,
                lineColor = primaryColor,
                modifier = Modifier.fillMaxWidth()
            )
            GraphType.BAR -> MiniBarChart(
                dataPoints = graphData,
                barColor = primaryColor,
                modifier = Modifier.fillMaxWidth()
            )
            GraphType.SPARKLINE -> MiniSparkline(
                dataPoints = graphData,
                lineColor = primaryColor,
                modifier = Modifier.fillMaxWidth()
            )
            GraphType.PROGRESS_ARC -> {
                val progress = if (graphData.isNotEmpty()) graphData.last() / 100f else 0f
                MiniProgressArc(
                    progress = progress,
                    progressColor = primaryColor,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            GraphType.DISTRIBUTION -> {
                // Handled separately in muscle group widget
            }
        }
    }
}

// Sample data generators for testing/preview

private fun generateSampleVolumeData(): List<Float> {
    return listOf(2400f, 2650f, 2500f, 2800f, 2750f, 2900f, 2847f)
}

private fun generateSampleFrequencyData(): List<Float> {
    return listOf(3f, 4f, 3f, 5f, 4f, 4f, 3f)
}

private fun generateSampleProgressData(): List<Float> {
    return listOf(100f, 102f, 105f, 103f, 108f, 110f, 112f)
}

private fun generateSample1RMData(): List<Float> {
    return listOf(80f, 82.5f, 82.5f, 85f, 87.5f, 87.5f, 90f)
}

private fun generateSampleDistribution(): List<Pair<Float, Color>> {
    return listOf(
        35f to LiftrixColors.Primary,
        25f to LiftrixColors.Secondary,
        20f to LiftrixColors.Primary.copy(alpha = 0.7f),
        15f to LiftrixColors.Secondary.copy(alpha = 0.7f),
        5f to Color.Gray
    )
}
