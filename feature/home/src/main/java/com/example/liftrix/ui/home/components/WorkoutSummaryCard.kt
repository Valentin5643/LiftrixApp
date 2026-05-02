package com.example.liftrix.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.feature.home.model.HomeWorkout
import com.example.liftrix.feature.home.model.HomeWorkoutStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Reusable card component displaying workout summary information
 * Shows workout title, date, duration, and exercise count with Material 3 design
 */
@Composable
fun WorkoutSummaryCard(
    workout: HomeWorkout,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Workout ${workout.name} from ${workout.date}, ${workout.exerciseCount} exercises"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WorkoutHeader(
                name = workout.name,
                date = workout.date
            )
            
            WorkoutStats(
                duration = workout.getDuration(),
                exerciseCount = workout.exerciseCount
            )
        }
    }
}

/**
 * Header section displaying workout name and date
 */
@Composable
private fun WorkoutHeader(
    name: String,
    date: LocalDate,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Text(
            text = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * Stats section displaying workout duration and exercise count
 */
@Composable
private fun WorkoutStats(
    duration: Duration?,
    exerciseCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WorkoutStatItem(
            icon = Icons.Default.Schedule,
            value = duration?.let { formatDuration(it) } ?: "Not started",
            label = "Duration",
            modifier = Modifier.weight(1f)
        )
        
        WorkoutStatItem(
            icon = Icons.Default.FitnessCenter,
            value = exerciseCount.toString(),
            label = if (exerciseCount == 1) "Exercise" else "Exercises",
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Individual stat item with icon, value, and label
 */
@Composable
private fun WorkoutStatItem(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Formats duration to human-readable string
 */
private fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutesPart()
    
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "< 1m"
    }
}

@Preview(showBackground = true)
@Composable
private fun WorkoutSummaryCardPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Completed workout
            WorkoutSummaryCard(
                workout = HomeWorkout(
                    userId = "user123",
                    id = "workout1",
                    name = "Push Day",
                    date = LocalDate.now(),
                    exerciseCount = 0,
                    totalSets = 0,
                    completedSetCount = 0,
                    totalVolumeKg = 0.0,
                    status = HomeWorkoutStatus.COMPLETED,
                    startTime = Instant.now().minusSeconds(3600),
                    endTime = Instant.now()
                ),
                onClick = { }
            )
            
            // Workout without duration
            WorkoutSummaryCard(
                workout = HomeWorkout(
                    userId = "user123",
                    id = "workout2",
                    name = "Full Body Strength Training Session",
                    date = LocalDate.now().minusDays(1),
                    exerciseCount = 0,
                    totalSets = 0,
                    completedSetCount = 0,
                    totalVolumeKg = 0.0,
                    status = HomeWorkoutStatus.PLANNED,
                    startTime = null,
                    endTime = null
                ),
                onClick = { }
            )
        }
    }
} 
