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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
            
            TextButton(onClick = onViewAllWorkouts) {
                Text("View All")
            }
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
 * Quick stat card component
 */
@Composable
private fun QuickStatCard(
    icon: ImageVector,
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.width(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Individual workout history card
 */
@Composable
private fun WorkoutHistoryCard(
    workout: Workout,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
            
            // Workout details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workout.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = buildString {
                        append("${workout.exercises.size} exercises")
                        val completedSets = workout.getCompletedSets()
                        if (completedSets > 0) {
                            append(" • $completedSets sets")
                        }
                        workout.getDuration()?.toMinutes()?.let { duration ->
                            append(" • ${duration}min")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
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
                        style = MaterialTheme.typography.bodySmall,
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
 * Empty state when no workout history exists
 */
@Composable
private fun EmptyWorkoutHistoryState(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "No Workouts Yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Complete your first workout to see your history here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Data class for workout statistics
 */
data class WorkoutStats(
    val workoutsThisWeek: Int = 0,
    val totalMinutesThisWeek: Int = 0,
    val currentStreak: Int = 0,
    val daysSinceLastWorkout: Int? = null,
    val totalWorkouts: Int = 0,
    val totalVolume: Double = 0.0,
    val averageWorkoutDuration: Int = 0
)