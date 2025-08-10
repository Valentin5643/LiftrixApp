package com.example.liftrix.ui.progress.components.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.*
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.common.components.TrendIndicator

/**
 * Metrics category widgets for single-value displays with trends.
 * 
 * All metrics widgets follow consistent patterns:
 * - Primary value display with units
 * - Secondary comparison information
 * - Trend indicators and progress visualization
 * - Consistent Material 3 styling
 */

/**
 * Calories burned widget - displays daily calories with goal progress
 */
@Composable
fun CaloriesBurnedWidget(
    data: MetricWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseWidget(
        title = "Calories Burned",
        subtitle = data?.comparisonPeriod,
        isLoading = data?.isLoading == true,
        error = data?.error?.message,
        onRefresh = onRefresh,
        onClick = onClick,
        modifier = modifier
    ) {
        data?.let { widgetData ->
            MetricDisplay(
                primaryValue = widgetData.primaryValue,
                unit = widgetData.unit,
                trend = widgetData.trend,
                trendPercentage = widgetData.trendPercentage,
                secondaryValue = widgetData.secondaryValue,
                icon = Icons.Default.LocalFireDepartment,
                primaryColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Daily calories widget - today's calorie burn with goal comparison
 */
@Composable
fun DailyCaloriesWidget(
    data: MetricWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseWidget(
        title = "Today's Calories",
        subtitle = "Daily calorie burn",
        isLoading = data?.isLoading == true,
        error = data?.error?.message,
        onRefresh = onRefresh,
        onClick = onClick,
        modifier = modifier
    ) {
        data?.let { widgetData ->
            MetricDisplayWithProgress(
                primaryValue = widgetData.primaryValue,
                unit = widgetData.unit,
                trend = widgetData.trend,
                trendPercentage = widgetData.trendPercentage,
                secondaryValue = widgetData.secondaryValue ?: "0",
                icon = Icons.Default.TrendingUp,
                primaryColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Workout frequency widget - workouts per week/month tracking
 */
@Composable
fun WorkoutFrequencyWidget(
    data: MetricWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            MetricDisplay(
                primaryValue = widgetData.primaryValue,
                unit = widgetData.unit,
                trend = widgetData.trend,
                trendPercentage = widgetData.trendPercentage,
                secondaryValue = widgetData.secondaryValue,
                icon = Icons.Default.FitnessCenter,
                primaryColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Total volume widget - cumulative weight lifted
 */
@Composable
fun TotalVolumeWidget(
    data: MetricWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            MetricDisplay(
                primaryValue = widgetData.primaryValue,
                unit = widgetData.unit,
                trend = widgetData.trend,
                trendPercentage = widgetData.trendPercentage,
                secondaryValue = widgetData.secondaryValue,
                icon = Icons.Default.Scale,
                primaryColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Consistency streak widget - current workout streak
 */
@Composable
fun ConsistencyStreakWidget(
    data: MetricWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseWidget(
        title = "Consistency Streak",
        subtitle = "Current streak",
        isLoading = data?.isLoading == true,
        error = data?.error?.message,
        onRefresh = onRefresh,
        onClick = onClick,
        modifier = modifier
    ) {
        data?.let { widgetData ->
            MetricDisplay(
                primaryValue = widgetData.primaryValue,
                unit = widgetData.unit,
                trend = widgetData.trend,
                trendPercentage = widgetData.trendPercentage,
                secondaryValue = widgetData.secondaryValue,
                icon = Icons.Default.Whatshot,
                primaryColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Average duration widget - average workout time
 */
@Composable
fun AverageDurationWidget(
    data: MetricWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseWidget(
        title = "Average Duration",
        subtitle = data?.comparisonPeriod,
        isLoading = data?.isLoading == true,
        error = data?.error?.message,
        onRefresh = onRefresh,
        onClick = onClick,
        modifier = modifier
    ) {
        data?.let { widgetData ->
            MetricDisplay(
                primaryValue = widgetData.primaryValue,
                unit = widgetData.unit,
                trend = widgetData.trend,
                trendPercentage = widgetData.trendPercentage,
                secondaryValue = widgetData.secondaryValue,
                icon = Icons.Default.Timer,
                primaryColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Workout streak widget - historical streak tracking
 */
@Composable
fun WorkoutStreakWidget(
    data: MetricWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseWidget(
        title = "Workout Streak",
        subtitle = "Days in a row",
        isLoading = data?.isLoading == true,
        error = data?.error?.message,
        onRefresh = onRefresh,
        onClick = onClick,
        modifier = modifier
    ) {
        data?.let { widgetData ->
            MetricDisplay(
                primaryValue = widgetData.primaryValue,
                unit = widgetData.unit,
                trend = widgetData.trend,
                trendPercentage = widgetData.trendPercentage,
                secondaryValue = widgetData.secondaryValue,
                icon = Icons.Default.TrendingUp,
                primaryColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Set completion rate widget - exercise completion tracking
 */
@Composable
fun SetCompletionRateWidget(
    data: MetricWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseWidget(
        title = "Set Completion Rate",
        subtitle = data?.comparisonPeriod,
        isLoading = data?.isLoading == true,
        error = data?.error?.message,
        onRefresh = onRefresh,
        onClick = onClick,
        modifier = modifier
    ) {
        data?.let { widgetData ->
            MetricDisplayWithProgress(
                primaryValue = widgetData.primaryValue,
                unit = widgetData.unit,
                trend = widgetData.trend,
                trendPercentage = widgetData.trendPercentage,
                secondaryValue = widgetData.secondaryValue ?: "0",
                icon = Icons.Default.CheckCircle,
                primaryColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Compact metric widget for smaller layouts
 */
@Composable
fun CompactMetricWidget(
    widget: AnalyticsWidget,
    data: MetricWidgetData?,
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
        data?.let { widgetData ->
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
                
                TrendIndicator(
                    trend = widgetData.trend,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Standard metric display component - Enhanced for mock symmetry
 */
@Composable
private fun MetricDisplay(
    primaryValue: String,
    unit: String,
    trend: TrendDirection,
    trendPercentage: Float,
    secondaryValue: String?,
    icon: ImageVector,
    primaryColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon - Slightly reduced size for better balance
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(12.dp),
            color = primaryColor.copy(alpha = 0.1f)
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        
        // Content with improved spacing and alignment
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Primary value with better alignment
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = primaryValue,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor,
                    maxLines = 1
                )
                
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
            
            // Trend indicator with improved spacing
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrendIndicator(
                    trend = trend,
                    modifier = Modifier.size(14.dp)
                )
                
                Text(
                    text = trend.getPercentageDescription(trendPercentage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }
            
            // Clean secondary value display
            secondaryValue?.let { secondary ->
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Metric display with progress indicator
 */
@Composable
private fun MetricDisplayWithProgress(
    primaryValue: String,
    unit: String,
    trend: TrendDirection,
    trendPercentage: Float,
    secondaryValue: String,
    icon: ImageVector,
    primaryColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Main metric display
        MetricDisplay(
            primaryValue = primaryValue,
            unit = unit,
            trend = trend,
            trendPercentage = trendPercentage,
            secondaryValue = null,
            icon = icon,
            primaryColor = primaryColor
        )
        
        // Progress information
        val progress = try {
            val current = primaryValue.toFloatOrNull() ?: 0f
            val target = secondaryValue.toFloatOrNull() ?: 1f
            (current / target).coerceIn(0f, 1f)
        } catch (e: Exception) {
            0f
        }
        
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Target: $secondaryValue $unit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = primaryColor,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
        }
    }
}