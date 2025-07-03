package com.example.liftrix.ui.workout.history.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.model.WorkoutSummary
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Individual workout history card component for displaying workout summaries in lists
 * Optimized for efficient display of WorkoutSummary data with Material 3 design
 */
@Composable
fun WorkoutHistoryCard(
    workoutSummary: WorkoutSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Workout ${workoutSummary.name} from ${formatRelativeDate(workoutSummary.date)}"
            },
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
                    .background(getStatusColor(workoutSummary.status))
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Workout details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workoutSummary.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = buildString {
                        append("${workoutSummary.exerciseCount} exercises")
                        if (workoutSummary.completedSets > 0) {
                            append(" • ${workoutSummary.completedSets}/${workoutSummary.totalSets} sets")
                        }
                        if (workoutSummary.duration != null) {
                            append(" • ${workoutSummary.getFormattedDuration()}")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = formatRelativeDate(workoutSummary.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Completion metrics
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${String.format("%.0f", workoutSummary.completionPercentage)}%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "complete",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Gets the appropriate color for workout status indicator
 */
@Composable
private fun getStatusColor(status: WorkoutStatus): Color {
    return when (status) {
        WorkoutStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        WorkoutStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
        WorkoutStatus.PAUSED -> MaterialTheme.colorScheme.outline
        WorkoutStatus.PLANNED -> MaterialTheme.colorScheme.surfaceVariant
        WorkoutStatus.CANCELLED -> MaterialTheme.colorScheme.error
    }
}

/**
 * Formats date relative to current date for user-friendly display
 */
private fun formatRelativeDate(date: LocalDate): String {
    val today = LocalDate.now()
    val daysAgo = ChronoUnit.DAYS.between(date, today)
    
    return when {
        daysAgo == 0L -> "Today"
        daysAgo == 1L -> "Yesterday"
        daysAgo < 7 -> "$daysAgo days ago"
        daysAgo < 30 -> "${daysAgo / 7} weeks ago"
        else -> date.toString() // Fallback to ISO format
    }
} 