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
 * UI component for displaying today's calorie burn compared to daily goal
 * 
 * Shows current day's calorie burn with goal progress visualization.
 * Includes circular progress indicator and goal comparison text.
 */
@Composable
fun DailyCaloriesCard(
    caloriesInToday: Int,
    dailyGoal: Int = 400,
    workoutCount: Int = 0,
    trend: TrendDirection? = null,
    isLoading: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val progress = if (dailyGoal > 0) (caloriesInToday.toFloat() / dailyGoal.toFloat()).coerceIn(0f, 1f) else 0f
    val isGoalReached = caloriesInToday >= dailyGoal
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !isLoading) { onClick() }
            .semantics {
                contentDescription = "Today's calories: $caloriesInToday of $dailyGoal calorie goal"
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
                DailyCaloriesLoadingContent()
            } else {
                DailyCaloriesContent(
                    caloriesInToday = caloriesInToday,
                    dailyGoal = dailyGoal,
                    workoutCount = workoutCount,
                    progress = progress,
                    isGoalReached = isGoalReached,
                    trend = trend
                )
            }
        }
    }
}

@Composable
private fun DailyCaloriesContent(
    caloriesInToday: Int,
    dailyGoal: Int,
    workoutCount: Int,
    progress: Float,
    isGoalReached: Boolean,
    trend: TrendDirection?
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
                    text = "Today's Calories",
                    style = MaterialTheme.typography.titleMedium,
                    color = LiftrixColors.OnSurface,
                    fontWeight = FontWeight.SemiBold
                )
                
                Text(
                    text = if (workoutCount > 0) "$workoutCount workout${if (workoutCount > 1) "s" else ""}" else "No workouts",
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
        
        // Progress circle and stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular progress
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(60.dp)
            ) {
                CircularProgressIndicator(
                    progress = progress,
                    modifier = Modifier.size(60.dp),
                    color = if (isGoalReached) LiftrixColors.Primary else LiftrixColors.Primary,
                    strokeWidth = 6.dp,
                    trackColor = LiftrixColors.OnSurface.copy(alpha = 0.1f)
                )
                
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = LiftrixColors.OnSurface,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Stats
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = caloriesInToday.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (isGoalReached) LiftrixColors.Primary else LiftrixColors.Primary,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "of $dailyGoal goal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LiftrixColors.OnSurface.copy(alpha = 0.7f)
                )
                
                val remainingCalories = dailyGoal - caloriesInToday
                if (remainingCalories > 0) {
                    Text(
                        text = "$remainingCalories to goal",
                        style = MaterialTheme.typography.bodySmall,
                        color = LiftrixColors.OnSurface.copy(alpha = 0.6f)
                    )
                } else {
                    Text(
                        text = "Goal reached! 🎉",
                        style = MaterialTheme.typography.bodySmall,
                        color = LiftrixColors.Primary
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyCaloriesLoadingContent() {
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
                        .width(120.dp)
                        .height(20.dp)
                        .background(
                            color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
                
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(16.dp)
                        .background(
                            color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
        }
        
        // Progress and stats skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Progress circle skeleton
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(
                        color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(40.dp)
                    )
            )
            
            // Stats skeleton
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(32.dp)
                        .background(
                            color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
                
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
                        .width(70.dp)
                        .height(14.dp)
                        .background(
                            color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DailyCaloriesCardPreview() {
    LiftrixTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            DailyCaloriesCard(
                caloriesInToday = 347,
                dailyGoal = 400,
                workoutCount = 2,
                trend = TrendDirection.UP
            )
            
            DailyCaloriesCard(
                caloriesInToday = 450,
                dailyGoal = 400,
                workoutCount = 1,
                trend = TrendDirection.STABLE
            )
            
            DailyCaloriesCard(
                caloriesInToday = 0,
                dailyGoal = 400,
                workoutCount = 0,
                isLoading = true
            )
        }
    }
}