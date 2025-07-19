package com.example.liftrix.ui.progress.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.TrendDirection
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.common.components.TrendIndicator

/**
 * Layout types for adaptive behavior based on available space
 */
private enum class LayoutType {
    COMPACT,        // Minimal info, small screens
    COMPACT_PLUS,   // Basic info + trend, medium phones
    MEDIUM,         // Extended info + mini chart, tablets/landscape
    EXPANDED        // Full info + detailed chart, large screens
}

/**
 * UI component for displaying weekly calorie burn trends
 * 
 * Shows weekly calorie patterns with trend analysis and comparison
 * to previous periods. Uses adaptive layout based on available space.
 * 
 * @param windowSizeClass Current window size class for adaptive behavior
 */
@Composable
fun WeeklyCalorieTrendCard(
    weeklyCalories: List<Int>,
    averageCalories: Int,
    trend: TrendDirection? = null,
    trendPercentage: Float? = null,
    isLoading: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass? = null
) {
    // Determine layout type based on available space
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    val layoutType = when {
        windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Expanded -> LayoutType.EXPANDED
        windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Medium -> LayoutType.MEDIUM
        screenWidth > 600.dp -> LayoutType.MEDIUM  // Fallback for tablets
        screenWidth > 450.dp -> LayoutType.COMPACT_PLUS  // Larger phones
        else -> LayoutType.COMPACT
    }
    val currentWeekCalories = weeklyCalories.lastOrNull() ?: 0
    val previousWeekCalories = weeklyCalories.getOrNull(weeklyCalories.size - 2) ?: 0
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !isLoading) { onClick() }
            .semantics {
                contentDescription = "Weekly calorie trend: $currentWeekCalories calories this week, average $averageCalories"
            },
        colors = CardDefaults.cardColors(
            containerColor = LiftrixColors.SurfaceLight,
            contentColor = LiftrixColors.OnSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 16.dp,
                    vertical = when (layoutType) {
                        LayoutType.COMPACT -> 8.dp
                        LayoutType.COMPACT_PLUS -> 12.dp
                        LayoutType.MEDIUM -> 16.dp
                        LayoutType.EXPANDED -> 20.dp
                    }
                )
        ) {
            if (isLoading) {
                WeeklyCalorieTrendLoadingContent(layoutType = layoutType)
            } else {
                WeeklyCalorieTrendContent(
                    weeklyCalories = weeklyCalories,
                    currentWeekCalories = currentWeekCalories,
                    previousWeekCalories = previousWeekCalories,
                    averageCalories = averageCalories,
                    trend = trend,
                    trendPercentage = trendPercentage,
                    layoutType = layoutType
                )
            }
        }
    }
}

@Composable
private fun WeeklyCalorieTrendContent(
    weeklyCalories: List<Int>,
    currentWeekCalories: Int,
    previousWeekCalories: Int,
    averageCalories: Int,
    trend: TrendDirection?,
    trendPercentage: Float?,
    layoutType: LayoutType
) {
    when (layoutType) {
        LayoutType.COMPACT -> CompactLayout(
            weeklyCalories = weeklyCalories,
            currentWeekCalories = currentWeekCalories,
            trend = trend,
            trendPercentage = trendPercentage
        )
        LayoutType.COMPACT_PLUS -> CompactPlusLayout(
            currentWeekCalories = currentWeekCalories,
            previousWeekCalories = previousWeekCalories,
            trend = trend,
            trendPercentage = trendPercentage
        )
        LayoutType.MEDIUM -> MediumLayout(
            weeklyCalories = weeklyCalories,
            currentWeekCalories = currentWeekCalories,
            previousWeekCalories = previousWeekCalories,
            averageCalories = averageCalories,
            trend = trend,
            trendPercentage = trendPercentage
        )
        LayoutType.EXPANDED -> ExpandedLayout(
            weeklyCalories = weeklyCalories,
            currentWeekCalories = currentWeekCalories,
            previousWeekCalories = previousWeekCalories,
            averageCalories = averageCalories,
            trend = trend,
            trendPercentage = trendPercentage
        )
    }
}

@Composable
private fun CompactLayout(
    weeklyCalories: List<Int>,
    currentWeekCalories: Int,
    trend: TrendDirection?,
    trendPercentage: Float?
) {
    // Compact layout without chart - shows just essential info like other cards
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side - Icon + main content
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.TrendingUp,
                contentDescription = "Weekly Calories",
                modifier = Modifier.size(24.dp),
                tint = when(trend) {
                    TrendDirection.UP -> LiftrixColors.Primary
                    TrendDirection.DOWN -> LiftrixColors.Error
                    else -> LiftrixColors.Primary
                }
            )
            
            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Weekly Calories",
                    style = MaterialTheme.typography.labelMedium,
                    color = LiftrixColors.OnSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = "$currentWeekCalories cal",
                    style = MaterialTheme.typography.titleLarge,
                    color = LiftrixColors.OnSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Right side - Trend percentage
        if (trendPercentage != null) {
            val trendText = if (trendPercentage > 0) "+${trendPercentage.toInt()}%" else "${trendPercentage.toInt()}%"
            Text(
                text = trendText,
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    trendPercentage > 5 -> LiftrixColors.Primary
                    trendPercentage < -5 -> LiftrixColors.Error
                    else -> LiftrixColors.OnSurface.copy(alpha = 0.7f)
                },
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CompactPlusLayout(
    currentWeekCalories: Int,
    previousWeekCalories: Int,
    trend: TrendDirection?,
    trendPercentage: Float?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = "Weekly Calories",
                    modifier = Modifier.size(22.dp),
                    tint = when(trend) {
                        TrendDirection.UP -> LiftrixColors.Primary
                        TrendDirection.DOWN -> LiftrixColors.Error
                        else -> LiftrixColors.Primary
                    }
                )
            }
            
            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$currentWeekCalories cal this week",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LiftrixColors.OnSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                val difference = currentWeekCalories - previousWeekCalories
                val differenceText = if (difference > 0) "+$difference" else "$difference"
                
                Text(
                    text = "$differenceText vs last week",
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        difference > 0 -> LiftrixColors.Primary
                        difference < 0 -> LiftrixColors.Error
                        else -> LiftrixColors.OnSurface.copy(alpha = 0.7f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        if (trendPercentage != null) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                val trendText = if (trendPercentage > 0) "+${trendPercentage.toInt()}%" else "${trendPercentage.toInt()}%"
                Text(
                    text = trendText,
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        trendPercentage > 5 -> LiftrixColors.Primary
                        trendPercentage < -5 -> LiftrixColors.Error
                        else -> LiftrixColors.OnSurface.copy(alpha = 0.7f)
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MediumLayout(
    weeklyCalories: List<Int>,
    currentWeekCalories: Int,
    previousWeekCalories: Int,
    averageCalories: Int,
    trend: TrendDirection?,
    trendPercentage: Float?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header with main metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = "Weekly Calories",
                        modifier = Modifier.size(24.dp),
                        tint = when(trend) {
                            TrendDirection.UP -> LiftrixColors.Primary
                            TrendDirection.DOWN -> LiftrixColors.Error
                            else -> LiftrixColors.Primary
                        }
                    )
                }
                
                Column {
                    Text(
                        text = "Weekly Calories",
                        style = MaterialTheme.typography.labelMedium,
                        color = LiftrixColors.OnSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "$currentWeekCalories cal",
                        style = MaterialTheme.typography.headlineSmall,
                        color = LiftrixColors.OnSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            if (trendPercentage != null) {
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    val trendText = if (trendPercentage > 0) "+${trendPercentage.toInt()}%" else "${trendPercentage.toInt()}%"
                    Text(
                        text = trendText,
                        style = MaterialTheme.typography.titleLarge,
                        color = when {
                            trendPercentage > 5 -> LiftrixColors.Primary
                            trendPercentage < -5 -> LiftrixColors.Error
                            else -> LiftrixColors.OnSurface.copy(alpha = 0.7f)
                        },
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "vs last week",
                        style = MaterialTheme.typography.bodySmall,
                        color = LiftrixColors.OnSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
        
        // Secondary metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Average: $averageCalories cal",
                style = MaterialTheme.typography.bodyMedium,
                color = LiftrixColors.OnSurface.copy(alpha = 0.8f)
            )
            
            val difference = currentWeekCalories - previousWeekCalories
            val differenceText = if (difference > 0) "+$difference cal" else "${difference} cal"
            Text(
                text = differenceText,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    difference > 0 -> LiftrixColors.Primary
                    difference < 0 -> LiftrixColors.Error
                    else -> LiftrixColors.OnSurface.copy(alpha = 0.7f)
                },
                fontWeight = FontWeight.Medium
            )
        }
        
        // Mini chart
        if (weeklyCalories.isNotEmpty()) {
            CompactTrendChart(
                values = weeklyCalories,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            )
        }
    }
}

@Composable
private fun ExpandedLayout(
    weeklyCalories: List<Int>,
    currentWeekCalories: Int,
    previousWeekCalories: Int,
    averageCalories: Int,
    trend: TrendDirection?,
    trendPercentage: Float?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = LiftrixColors.Primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = "Weekly Calories",
                        modifier = Modifier.size(28.dp),
                        tint = when(trend) {
                            TrendDirection.UP -> LiftrixColors.Primary
                            TrendDirection.DOWN -> LiftrixColors.Error
                            else -> LiftrixColors.Primary
                        }
                    )
                }
                
                Column {
                    Text(
                        text = "Weekly Calorie Burn",
                        style = MaterialTheme.typography.titleMedium,
                        color = LiftrixColors.OnSurface.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "$currentWeekCalories calories",
                        style = MaterialTheme.typography.headlineMedium,
                        color = LiftrixColors.OnSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            if (trendPercentage != null) {
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    val trendText = if (trendPercentage > 0) "+${trendPercentage.toInt()}%" else "${trendPercentage.toInt()}%"
                    Text(
                        text = trendText,
                        style = MaterialTheme.typography.headlineSmall,
                        color = when {
                            trendPercentage > 5 -> LiftrixColors.Primary
                            trendPercentage < -5 -> LiftrixColors.Error
                            else -> LiftrixColors.OnSurface.copy(alpha = 0.7f)
                        },
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "from last week",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LiftrixColors.OnSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
        
        // Metrics grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetricItem(
                label = "This Week",
                value = "$currentWeekCalories cal",
                modifier = Modifier.weight(1f)
            )
            MetricItem(
                label = "Last Week", 
                value = "$previousWeekCalories cal",
                modifier = Modifier.weight(1f)
            )
            MetricItem(
                label = "Average",
                value = "$averageCalories cal",
                modifier = Modifier.weight(1f)
            )
        }
        
        // Enhanced chart
        if (weeklyCalories.isNotEmpty()) {
            Text(
                text = "7-Day Trend",
                style = MaterialTheme.typography.labelLarge,
                color = LiftrixColors.OnSurface.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium
            )
            CompactTrendChart(
                values = weeklyCalories,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            )
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = LiftrixColors.OnSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = LiftrixColors.OnSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CompactTrendChart(
    values: List<Int>,
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) return
    
    val maxValue = values.maxOrNull() ?: 1
    val minValue = values.minOrNull() ?: 0
    val range = maxValue - minValue
    
    // Modern circular chart container (like fitness apps)
    Box(
        modifier = modifier
            .background(
                color = LiftrixColors.Primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Micro sparkline chart
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxSize()
        ) {
            values.takeLast(7).forEach { value ->
                val normalizedHeight = if (range > 0) {
                    ((value - minValue).toFloat() / range.toFloat() * 0.7f + 0.3f).coerceIn(0.3f, 1f)
                } else {
                    0.5f
                }
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(normalizedHeight)
                        .width(3.dp)
                        .background(
                            color = LiftrixColors.Primary,
                            shape = RoundedCornerShape(1.5.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun MiniBarChart(
    values: List<Int>,
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) return
    
    val maxValue = values.maxOrNull() ?: 1
    val minValue = values.minOrNull() ?: 0
    val range = maxValue - minValue
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEach { value ->
            val normalizedHeight = if (range > 0) {
                ((value - minValue).toFloat() / range.toFloat() * 0.8f + 0.2f).coerceIn(0.2f, 1f)
            } else {
                0.5f
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(normalizedHeight)
                    .background(
                        color = LiftrixColors.Primary.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                    )
            )
        }
    }
}

@Composable
private fun WeeklyCalorieTrendLoadingContent(
    layoutType: LayoutType
) {
    when (layoutType) {
        LayoutType.COMPACT -> CompactLoadingSkeleton()
        LayoutType.COMPACT_PLUS -> CompactPlusLoadingSkeleton()
        LayoutType.MEDIUM -> MediumLoadingSkeleton()
        LayoutType.EXPANDED -> ExpandedLoadingSkeleton()
    }
}

@Composable
private fun CompactLoadingSkeleton() {
    // Simple Row-based loading skeleton to match compact layout without chart
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side - Icon + main content skeleton
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp)
                    )
            )
            
            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(14.dp)
                        .background(
                            color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(20.dp)
                        .background(
                            color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
        }
        
        // Right side - Trend percentage skeleton
        Box(
            modifier = Modifier
                .width(50.dp)
                .height(20.dp)
                .background(
                    color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                )
        )
    }
}

@Composable
private fun CompactPlusLoadingSkeleton() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    )
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(16.dp)
                        .background(
                            color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(14.dp)
                        .background(
                            color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
        }
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(24.dp)
                .background(
                    color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                )
        )
    }
}

@Composable
private fun MediumLoadingSkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(12.dp)
                            .background(
                                color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(20.dp)
                            .background(
                                color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(50.dp)
                        .height(24.dp)
                        .background(
                            color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
                Box(
                    modifier = Modifier
                        .width(70.dp)
                        .height(12.dp)
                        .background(
                            color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(16.dp)
                    .background(
                        color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(16.dp)
                    .background(
                        color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(
                    color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                )
        )
    }
}

@Composable
private fun ExpandedLoadingSkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(140.dp)
                            .height(16.dp)
                            .background(
                                color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                    Box(
                        modifier = Modifier
                            .width(160.dp)
                            .height(24.dp)
                            .background(
                                color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(24.dp)
                        .background(
                            color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(14.dp)
                        .background(
                            color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(3) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(12.dp)
                            .background(
                                color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(16.dp)
                            .background(
                                color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
        }
        
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(14.dp)
                    .background(
                        color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(
                        color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    )
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 200)
@Composable
private fun WeeklyCalorieTrendCardCompactPreview() {
    LiftrixTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text("Compact Layout", style = MaterialTheme.typography.labelMedium)
            WeeklyCalorieTrendCard(
                weeklyCalories = listOf(1200, 1450, 1380, 1520),
                averageCalories = 1387,
                trend = TrendDirection.UP,
                trendPercentage = 12.5f
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 600, heightDp = 300)
@Composable
private fun WeeklyCalorieTrendCardMediumPreview() {
    LiftrixTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text("Medium Layout", style = MaterialTheme.typography.labelMedium)
            WeeklyCalorieTrendCard(
                weeklyCalories = listOf(1200, 1450, 1380, 1520, 1600, 1750, 1680),
                averageCalories = 1507,
                trend = TrendDirection.UP,
                trendPercentage = 15.2f
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 400)
@Composable
private fun WeeklyCalorieTrendCardExpandedPreview() {
    LiftrixTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text("Expanded Layout", style = MaterialTheme.typography.labelMedium)
            WeeklyCalorieTrendCard(
                weeklyCalories = listOf(1200, 1450, 1380, 1520, 1600, 1750, 1680),
                averageCalories = 1507,
                trend = TrendDirection.UP,
                trendPercentage = 15.2f
            )
        }
    }
}