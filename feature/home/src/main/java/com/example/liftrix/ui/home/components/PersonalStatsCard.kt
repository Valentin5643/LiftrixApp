package com.example.liftrix.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.feature.home.model.HomeWorkoutStats
import java.time.Duration
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.components.cards.CompactLiftrixCard
import com.example.liftrix.ui.components.cards.CardSpacing
import com.example.liftrix.ui.components.layouts.GridSystem

/**
 * Personal stats dashboard card displaying key performance metrics
 * Uses new LiftrixCard system with athletic styling and visual hierarchy
 */
@Composable
fun PersonalStatsCard(
    workoutStats: HomeWorkoutStats?,
    modifier: Modifier = Modifier
) {
    LiftrixCard(
        modifier = modifier.fillMaxWidth(),
        contentDescription = "Personal performance statistics dashboard",
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        contentPadding = PaddingValues(GridSystem.spacing4)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(GridSystem.spacing3)
        ) {
            // Header
            Text(
                text = "Your Performance",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // Weekly Progress Section
            if (workoutStats != null) {
                WeeklyProgressSection(workoutStats = workoutStats)
                Spacer(modifier = Modifier.height(GridSystem.spacing2))
            }
            
            // Stats grid using asymmetrical layout
            if (workoutStats != null) {
                StatsGrid(workoutStats = workoutStats)
            } else {
                EmptyStatsPlaceholder()
            }
        }
    }
}

/**
 * Asymmetrical stats grid with visual hierarchy
 */
@Composable
private fun StatsGrid(
    workoutStats: HomeWorkoutStats,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
    ) {
        // Top row - Primary stats (larger cards)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
        ) {
            StatCard(
                title = "Workouts",
                value = workoutStats.totalWorkouts.toString(),
                icon = Icons.Default.FitnessCenter,
                trend = null,
                modifier = Modifier.weight(1f)
            )
            
            StatCard(
                title = "This Week",
                value = workoutStats.weeklyWorkouts.toString(),
                icon = Icons.Default.Schedule,
                trend = null,
                modifier = Modifier.weight(1f)
            )
        }
        
        // Bottom row - Secondary stats (smaller cards)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
        ) {
            CompactStatCard(
                title = "Streak",
                value = "${workoutStats.currentStreak}d",
                icon = Icons.Default.LocalFireDepartment,
                modifier = Modifier.weight(1f)
            )
            
            CompactStatCard(
                title = "Avg/Week",
                value = String.format("%.1f", workoutStats.averagePerWeek),
                icon = Icons.Default.TrendingUp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Primary stat card with trend indicator
 */
@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    trend: Float?,
    modifier: Modifier = Modifier
) {
    CompactLiftrixCard(
        modifier = modifier.semantics {
            contentDescription = "$title: $value${trend?.let { " with trend $it%" } ?: ""}"
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        contentPadding = PaddingValues(GridSystem.spacing3)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GridSystem.spacing1)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing1)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            
            // Trend indicator
            trend?.let { trendValue ->
                TrendIndicator(
                    trend = trendValue,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Compact stat card for secondary metrics
 */
@Composable
private fun CompactStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    CompactLiftrixCard(
        modifier = modifier.semantics {
            contentDescription = "$title: $value"
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        contentPadding = PaddingValues(GridSystem.spacing2)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing1)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Trend indicator showing performance change
 */
@Composable
private fun TrendIndicator(
    trend: Float,
    modifier: Modifier = Modifier
) {
    val trendColor = when {
        trend > 0 -> MaterialTheme.colorScheme.primary
        trend < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    val trendText = when {
        trend > 0 -> "+${String.format("%.1f", trend)}%"
        trend < 0 -> "${String.format("%.1f", trend)}%"
        else -> "No change"
    }
    
    Text(
        text = trendText,
        style = MaterialTheme.typography.labelSmall,
        color = trendColor,
        modifier = modifier,
        textAlign = TextAlign.Center
    )
}

/**
 * Empty state placeholder when stats are not available
 */
@Composable
private fun EmptyStatsPlaceholder(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
    ) {
        Icon(
            imageVector = Icons.Default.FitnessCenter,
            contentDescription = "No stats yet",
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "Start working out to see your stats!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Weekly progress section showing workout completion percentage
 */
@Composable
private fun WeeklyProgressSection(
    workoutStats: HomeWorkoutStats,
    modifier: Modifier = Modifier
) {
    val weeklyTarget = 3 // Target 3 workouts per week (configurable in future)
    val weeklyWorkouts = workoutStats.weeklyWorkouts
    val progressPercentage = if (weeklyTarget > 0) {
        (weeklyWorkouts.toFloat() / weeklyTarget).coerceAtMost(1.0f)
    } else 0.0f
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Progress ring
        com.example.liftrix.ui.RadialProgressIndicator(
            progress = progressPercentage,
            size = 60.dp,
            centerContent = {
                Text(
                    text = "${(progressPercentage * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        )
        
        // Progress details
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Weekly Progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = "$weeklyWorkouts of $weeklyTarget workouts completed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            val statusText = when {
                progressPercentage >= 1.0f -> "🎯 Goal achieved!"
                progressPercentage >= 0.66f -> "💪 Great progress"
                progressPercentage >= 0.33f -> "👍 Keep going"
                else -> "🚀 Let's get started"
            }
            
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (progressPercentage >= 1.0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PersonalStatsCardPreview() {
    MaterialTheme {
        PersonalStatsCard(
            workoutStats = HomeWorkoutStats(
                totalWorkouts = 42,
                currentStreak = 7,
                weeklyVolume = Duration.ofMinutes(240),
                averageWorkoutDuration = Duration.ofMinutes(45),
                weeklyWorkouts = 4,
                averagePerWeek = 3.5
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PersonalStatsCardEmptyPreview() {
    MaterialTheme {
        PersonalStatsCard(workoutStats = null)
    }
} 
