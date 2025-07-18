package com.example.liftrix.ui.progress.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.TrendDirection
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.common.components.TrendIndicator

/**
 * UI component for displaying weekly calorie burn trends
 * 
 * Shows weekly calorie patterns with trend analysis and comparison
 * to previous periods. Includes mini chart visualization.
 */
@Composable
fun WeeklyCalorieTrendCard(
    weeklyCalories: List<Int>,
    averageCalories: Int,
    trend: TrendDirection? = null,
    trendPercentage: Float? = null,
    isLoading: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
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
                .padding(20.dp)
        ) {
            if (isLoading) {
                WeeklyCalorieTrendLoadingContent()
            } else {
                WeeklyCalorieTrendContent(
                    weeklyCalories = weeklyCalories,
                    currentWeekCalories = currentWeekCalories,
                    previousWeekCalories = previousWeekCalories,
                    averageCalories = averageCalories,
                    trend = trend,
                    trendPercentage = trendPercentage
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
    trendPercentage: Float?
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Weekly Calorie Trend",
                    style = MaterialTheme.typography.titleMedium,
                    color = LiftrixColors.OnSurface,
                    fontWeight = FontWeight.SemiBold
                )
                
                Text(
                    text = "4-week trend analysis",
                    style = MaterialTheme.typography.bodySmall,
                    color = LiftrixColors.OnSurface.copy(alpha = 0.6f)
                )
            }
            
            trend?.let { trendDirection ->
                TrendIndicator(
                    trend = trendDirection,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        // Current week vs previous
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "This Week",
                    style = MaterialTheme.typography.labelMedium,
                    color = LiftrixColors.OnSurface.copy(alpha = 0.7f)
                )
                
                Text(
                    text = "$currentWeekCalories cal",
                    style = MaterialTheme.typography.headlineMedium,
                    color = LiftrixColors.Primary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "vs Last Week",
                    style = MaterialTheme.typography.labelMedium,
                    color = LiftrixColors.OnSurface.copy(alpha = 0.7f)
                )
                
                val difference = currentWeekCalories - previousWeekCalories
                val differenceText = if (difference > 0) "+$difference" else "$difference"
                val differenceColor = when {
                    difference > 0 -> LiftrixColors.Primary
                    difference < 0 -> LiftrixColors.Error
                    else -> LiftrixColors.OnSurface.copy(alpha = 0.7f)
                }
                
                Text(
                    text = "$differenceText cal",
                    style = MaterialTheme.typography.titleMedium,
                    color = differenceColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // Mini chart visualization
        if (weeklyCalories.isNotEmpty()) {
            MiniBarChart(
                values = weeklyCalories,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            )
        }
        
        // Average and trend percentage
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "4-week average",
                    style = MaterialTheme.typography.bodySmall,
                    color = LiftrixColors.OnSurface.copy(alpha = 0.6f)
                )
                
                Text(
                    text = "$averageCalories cal/week",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LiftrixColors.OnSurface,
                    fontWeight = FontWeight.Medium
                )
            }
            
            trendPercentage?.let { percentage ->
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Trend",
                        style = MaterialTheme.typography.bodySmall,
                        color = LiftrixColors.OnSurface.copy(alpha = 0.6f)
                    )
                    
                    val trendText = if (percentage > 0) "+${percentage.toInt()}%" else "${percentage.toInt()}%"
                    val trendColor = when {
                        percentage > 5 -> LiftrixColors.Primary
                        percentage < -5 -> LiftrixColors.Error
                        else -> LiftrixColors.OnSurface.copy(alpha = 0.7f)
                    }
                    
                    Text(
                        text = trendText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = trendColor,
                        fontWeight = FontWeight.Medium
                    )
                }
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
private fun WeeklyCalorieTrendLoadingContent() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(20.dp)
                        .background(
                            color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
                
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(16.dp)
                        .background(
                            color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
        }
        
        // Stats skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(16.dp)
                        .background(
                            color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
                
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(32.dp)
                        .background(
                            color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
            
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(70.dp)
                        .height(16.dp)
                        .background(
                            color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
                
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(24.dp)
                        .background(
                            color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
        }
        
        // Chart skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(
                    color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WeeklyCalorieTrendCardPreview() {
    LiftrixTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            WeeklyCalorieTrendCard(
                weeklyCalories = listOf(1200, 1450, 1380, 1520),
                averageCalories = 1387,
                trend = TrendDirection.UP,
                trendPercentage = 12.5f
            )
            
            WeeklyCalorieTrendCard(
                weeklyCalories = listOf(1200, 1450, 1380, 1520),
                averageCalories = 1387,
                trend = TrendDirection.DOWN,
                trendPercentage = -8.2f
            )
            
            WeeklyCalorieTrendCard(
                weeklyCalories = emptyList(),
                averageCalories = 0,
                isLoading = true
            )
        }
    }
}