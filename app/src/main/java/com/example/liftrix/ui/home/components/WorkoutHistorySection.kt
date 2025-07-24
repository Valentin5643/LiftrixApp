package com.example.liftrix.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import com.example.liftrix.ui.workout.components.TertiaryActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutStats
import com.example.liftrix.domain.model.WorkoutStatus
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Section component displaying workout history and recent activity.
 * This replaces template management in the Home tab.
 */
@Composable
fun WorkoutHistorySection(
    recentWorkouts: List<Workout>,
    workoutStats: WorkoutStats,
    onViewAllWorkouts: () -> Unit,
    onWorkoutClick: (Workout) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Workouts",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            
            TertiaryActionButton(
                text = "View All",
                onClick = onViewAllWorkouts
            )
        }
        
        // Quick Stats Row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                QuickStatCard(
                    icon = Icons.Default.FitnessCenter,
                    label = "This Week",
                    value = "${workoutStats.workoutsThisWeek}",
                    subtitle = "workouts"
                )
            }
            item {
                QuickStatCard(
                    icon = Icons.Default.Timer,
                    label = "Total Time",
                    value = "${workoutStats.totalMinutesThisWeek}",
                    subtitle = "minutes"
                )
            }
            item {
                QuickStatCard(
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    label = "Current Streak",
                    value = "${workoutStats.currentStreak}",
                    subtitle = "days"
                )
            }
            item {
                QuickStatCard(
                    icon = Icons.Default.CalendarMonth,
                    label = "Last Workout",
                    value = workoutStats.daysSinceLastWorkout?.let { days ->
                        if (days == 0) "Today" else "${days}d ago"
                    } ?: "None",
                    subtitle = ""
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Recent Workouts List
        if (recentWorkouts.isEmpty()) {
            EmptyWorkoutHistoryState(
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.height(400.dp), // Fixed height to prevent layout issues
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recentWorkouts) { workout ->
                    WorkoutHistoryCard(
                        workout = workout,
                        onClick = { onWorkoutClick(workout) }
                    )
                }
            }
        }
    }
}

/**
 * Quick stat card component using UnifiedWorkoutCard
 */
@Composable
private fun QuickStatCard(
    icon: ImageVector,
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    UnifiedWorkoutCard(
        title = value,
        subtitle = if (subtitle.isNotEmpty()) "$label $subtitle" else label,
        modifier = modifier.width(120.dp),
        leadingIcon = icon
    ) {
        // Empty content since title and subtitle handle the display
    }
}

/**
 * Individual workout history card using UnifiedWorkoutCard
 */
@Composable
private fun WorkoutHistoryCard(
    workout: Workout,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subtitle = buildString {
        append("${workout.exercises.size} exercises")
        val completedSets = workout.getCompletedSets()
        if (completedSets > 0) {
            append(" • $completedSets sets")
        }
        workout.getDuration()?.toMinutes()?.let { duration ->
            append(" • ${duration}min")
        }
    }
    
    UnifiedWorkoutCard(
        title = workout.name,
        subtitle = subtitle,
        modifier = modifier,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator  
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .let { mod ->
                        when (workout.status) {
                            WorkoutStatus.COMPLETED -> mod.then(
                                Modifier.fillMaxWidth()
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            WorkoutStatus.IN_PROGRESS -> mod.then(
                                Modifier.fillMaxWidth()
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            WorkoutStatus.PLANNED -> mod.then(
                                Modifier.fillMaxWidth()
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            else -> mod
                        }
                    }
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Time info
            Column(modifier = Modifier.weight(1f)) {
                workout.endTime?.let { endTime ->
                    val endDate = endTime.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    val daysAgo = java.time.temporal.ChronoUnit.DAYS.between(endDate, java.time.LocalDate.now())
                    val timeText = when {
                        daysAgo == 0L -> "Today"
                        daysAgo == 1L -> "Yesterday"
                        daysAgo < 7 -> "$daysAgo days ago"
                        else -> endTime.atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"))
                    }
                    
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Workout metrics
            Column(
                horizontalAlignment = Alignment.End
            ) {
                workout.calculateTotalVolume()?.let { volume ->
                    Text(
                        text = "${String.format("%.0f", volume)}kg",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "volume",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Empty state when no workout history exists using UnifiedWorkoutCard
 */
@Composable
private fun EmptyWorkoutHistoryState(
    modifier: Modifier = Modifier
) {
    UnifiedWorkoutCard(
        title = "No Workouts Yet",
        subtitle = "Complete your first workout to see your history here.",
        modifier = modifier,
        leadingIcon = Icons.Default.FitnessCenter
    ) {
        // Empty content since title and subtitle handle the message
    }
}

// WorkoutStats is now imported from domain model