package com.example.liftrix.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.ui.theme.LiftrixTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Home screen composable displaying recent workouts with empty state handling
 */
@Composable
fun HomeScreen(
    workouts: List<Workout>,
    isLoading: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onWorkoutClick: (WorkoutId) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            isLoading && workouts.isEmpty() -> {
                LoadingState(
                    modifier = Modifier.fillMaxSize()
                )
            }
            workouts.isEmpty() -> {
                EmptyState(
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        WelcomeHeader(
                            isRefreshing = isRefreshing,
                            onRefresh = onRefresh
                        )
                    }
                    
                    items(
                        items = workouts,
                        key = { it.id.value }
                    ) { workout ->
                        WorkoutCard(
                            workout = workout,
                            onClick = { onWorkoutClick(workout.id) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Welcome header section with refresh functionality
 */
@Composable
private fun WelcomeHeader(
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Welcome back!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "Here are your recent workouts",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh workouts",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Workout card displaying workout information
 */
@Composable
private fun WorkoutCard(
    workout: Workout,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
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
            // Header with name and date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = workout.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = workout.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                
                WorkoutStatusChip(status = workout.status)
            }
            
            // Workout stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                WorkoutStat(
                    icon = Icons.Default.FitnessCenter,
                    value = "${workout.exercises.size}",
                    label = "Exercises",
                    modifier = Modifier.weight(1f)
                )
                
                WorkoutStat(
                    icon = Icons.Default.TrendingUp,
                    value = "${workout.getTotalSets()}",
                    label = "Sets",
                    modifier = Modifier.weight(1f)
                )
                
                workout.getDuration()?.let { duration ->
                    WorkoutStat(
                        icon = Icons.Default.Schedule,
                        value = formatDuration(duration),
                        label = "Duration",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // Progress bar for completion
            if (workout.status == WorkoutStatus.IN_PROGRESS || workout.status == WorkoutStatus.COMPLETED) {
                val progress = (workout.getCompletionPercentage() / 100.0).toFloat()
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            }
        }
    }
}

/**
 * Workout status chip
 */
@Composable
private fun WorkoutStatusChip(
    status: WorkoutStatus,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor) = when (status) {
        WorkoutStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        WorkoutStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        WorkoutStatus.PAUSED -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        WorkoutStatus.PLANNED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        WorkoutStatus.CANCELLED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = backgroundColor
    ) {
        Text(
            text = status.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Individual workout statistic
 */
@Composable
private fun WorkoutStat(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * Empty state when no workouts are available
 */
@Composable
private fun EmptyState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.FitnessCenter,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "No workouts yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Start your fitness journey by creating your first workout",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Loading state with shimmer placeholder
 */
@Composable
private fun LoadingState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Loading workouts...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

// Preview composables
@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    LiftrixTheme {
        HomeScreen(
            workouts = listOf(
                createSampleWorkout("Push Day", WorkoutStatus.COMPLETED),
                createSampleWorkout("Pull Day", WorkoutStatus.IN_PROGRESS),
                createSampleWorkout("Leg Day", WorkoutStatus.PLANNED)
            ),
            isLoading = false,
            isRefreshing = false,
            onRefresh = {},
            onWorkoutClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenEmptyPreview() {
    LiftrixTheme {
        HomeScreen(
            workouts = emptyList(),
            isLoading = false,
            isRefreshing = false,
            onRefresh = {},
            onWorkoutClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenLoadingPreview() {
    LiftrixTheme {
        HomeScreen(
            workouts = emptyList(),
            isLoading = true,
            isRefreshing = false,
            onRefresh = {},
            onWorkoutClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Dark Theme")
@Composable
private fun HomeScreenDarkPreview() {
    LiftrixTheme(darkTheme = true) {
        HomeScreen(
            workouts = listOf(
                createSampleWorkout("Push Day", WorkoutStatus.COMPLETED),
                createSampleWorkout("Pull Day", WorkoutStatus.IN_PROGRESS)
            ),
            isLoading = false,
            isRefreshing = false,
            onRefresh = {},
            onWorkoutClick = {}
        )
    }
}

// Helper function for preview data
private fun createSampleWorkout(name: String, status: WorkoutStatus): Workout {
    val now = Instant.now()
    return Workout(
        userId = "sample-user",
        id = WorkoutId.generate(),
        name = name,
        date = LocalDate.now(),
        exercises = emptyList(),
        status = status,
        startTime = now.minusSeconds(3600),
        endTime = if (status == WorkoutStatus.COMPLETED) now else null,
        notes = null,
        templateId = null,
        createdAt = now.minusSeconds(7200),
        updatedAt = now
    )
} 