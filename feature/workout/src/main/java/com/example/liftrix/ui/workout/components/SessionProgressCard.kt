package com.example.liftrix.ui.workout.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.feature.workout.ui.AccessibilityUtils.accessibilitySemantics
import com.example.liftrix.ui.theme.LiftrixSpacing
import kotlin.math.cos
import kotlin.math.sin

/**
 * Session Progress Card Component
 * 
 * Modern card component displaying active workout session progress including elapsed time,
 * exercise completion status, and session controls. Built on UnifiedWorkoutCard for consistent
 * visual design with specialized functionality for active workout sessions.
 * 
 * Features:
 * - Large, prominent timer display with modern typography
 * - Circular progress indicator showing exercise completion
 * - Session control buttons (pause/resume/stop) with proper hierarchy
 * - Workout name and progress summary
 * - Real-time updates optimized for 60fps performance
 * - Accessibility support with proper time announcements
 * 
 * @param workoutName Name of the current workout session
 * @param elapsedTime Elapsed session time in seconds
 * @param completedExercises Number of completed exercises
 * @param totalExercises Total number of exercises in the workout
 * @param isPaused Whether the session is currently paused
 * @param onPauseResume Callback for pause/resume action
 * @param onEndSession Callback for ending the session
 * @param modifier Modifier for customizing the card's layout and behavior
 */
@Composable
fun SessionProgressCard(
    workoutName: String,
    elapsedTime: Long, // in seconds
    completedExercises: Int,
    totalExercises: Int,
    isPaused: Boolean = false,
    onPauseResume: () -> Unit,
    onEndSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    UnifiedWorkoutCard(
        title = workoutName,
        subtitle = "Active Session",
        modifier = modifier,
        actions = {
            SessionControlButtons(
                isPaused = isPaused,
                onPauseResume = onPauseResume,
                onEndSession = onEndSession
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.cardContentSpacing)
        ) {
            // Main timer display with progress indicator
            SessionTimerDisplay(
                elapsedTime = elapsedTime,
                isPaused = isPaused,
                progress = if (totalExercises > 0) completedExercises.toFloat() / totalExercises else 0f
            )
            
            // Progress summary
            SessionProgressSummary(
                completedExercises = completedExercises,
                totalExercises = totalExercises
            )
            
            // Session status indicator
            SessionStatusChip(
                isPaused = isPaused,
                completedExercises = completedExercises,
                totalExercises = totalExercises
            )
        }
    }
}

/**
 * Large timer display with circular progress indicator
 */
@Composable
private fun SessionTimerDisplay(
    elapsedTime: Long,
    isPaused: Boolean,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(180.dp),
        contentAlignment = Alignment.Center
    ) {
        // Circular progress background and foreground
        CircularProgressIndicator(
            progress = 1f,
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 6.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
        
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 6.dp,
            color = MaterialTheme.colorScheme.primary,
            strokeCap = StrokeCap.Round
        )
        
        // Timer text in center
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = formatElapsedTime(elapsedTime),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.accessibilitySemantics(
                    description = "Elapsed time: ${formatElapsedTimeForAccessibility(elapsedTime)}"
                )
            )
            
            if (isPaused) {
                Text(
                    text = "PAUSED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.accessibilitySemantics(
                        description = "Session is paused"
                    )
                )
            }
        }
    }
}

/**
 * Progress summary showing completed vs total exercises
 */
@Composable
private fun SessionProgressSummary(
    completedExercises: Int,
    totalExercises: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(LiftrixSpacing.elementPaddingLarge),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
            // Exercises progress
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Exercises",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$completedExercises/$totalExercises",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.accessibilitySemantics(
                        description = "$completedExercises of $totalExercises exercises completed"
                    )
                )
            }
            
            // Progress percentage
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Progress",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val progressPercent = if (totalExercises > 0) (completedExercises * 100) / totalExercises else 0
                Text(
                    text = "$progressPercent%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.accessibilitySemantics(
                        description = "$progressPercent percent complete"
                    )
                )
            }
            
            // Linear progress bar
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Completion",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = if (totalExercises > 0) completedExercises.toFloat() / totalExercises else 0f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            }
        }
}

/**
 * Status chip showing current session state
 */
@Composable
private fun SessionStatusChip(
    isPaused: Boolean,
    completedExercises: Int,
    totalExercises: Int,
    modifier: Modifier = Modifier
) {
    val (statusText, statusColor) = when {
        isPaused -> "Paused" to MaterialTheme.colorScheme.error
        completedExercises >= totalExercises -> "Complete" to MaterialTheme.colorScheme.primary
        completedExercises > 0 -> "In Progress" to MaterialTheme.colorScheme.primary
        else -> "Starting" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    AssistChip(
        onClick = { /* Status chip is informational only */ },
        label = {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor
            )
        },
        leadingIcon = {
            when {
                isPaused -> Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = "Paused",
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )
                completedExercises >= totalExercises -> Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Complete",
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )
                else -> Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Active",
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        },
        modifier = modifier.accessibilitySemantics(
            description = "Session status: $statusText"
        )
    )
}

/**
 * Session control buttons with proper hierarchy
 */
@Composable
private fun SessionControlButtons(
    isPaused: Boolean,
    onPauseResume: () -> Unit,
    onEndSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.buttonSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pause/Resume button - primary action
        PrimaryActionButton(
            text = if (isPaused) "Resume" else "Pause",
            onClick = onPauseResume
        )
        
        // End session button - secondary action (destructive)
        SecondaryActionButton(
            text = "End",
            onClick = onEndSession
        )
    }
}

/**
 * Helper function to format elapsed time for display (MM:SS or HH:MM:SS)
 */
private fun formatElapsedTime(elapsedSeconds: Long): String {
    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60
    
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

/**
 * Helper function to format elapsed time for accessibility (spoken format)
 */
private fun formatElapsedTimeForAccessibility(elapsedSeconds: Long): String {
    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60
    
    val parts = mutableListOf<String>()
    
    if (hours > 0) {
        parts.add("$hours ${if (hours == 1L) "hour" else "hours"}")
    }
    
    if (minutes > 0) {
        parts.add("$minutes ${if (minutes == 1L) "minute" else "minutes"}")
    }
    
    if (seconds > 0 || parts.isEmpty()) {
        parts.add("$seconds ${if (seconds == 1L) "second" else "seconds"}")
    }
    
    return parts.joinToString(", ")
}

/**
 * Compact version of SessionProgressCard for smaller spaces
 */
@Composable
fun CompactSessionProgressCard(
    workoutName: String,
    elapsedTime: Long,
    completedExercises: Int,
    totalExercises: Int,
    isPaused: Boolean = false,
    onPauseResume: () -> Unit,
    onEndSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompactUnifiedWorkoutCard(
        title = workoutName,
        subtitle = formatElapsedTime(elapsedTime),
        modifier = modifier,
        actions = {
            // Compact control buttons
            IconButton(
                onClick = onPauseResume,
                modifier = Modifier.accessibilitySemantics(
                    description = if (isPaused) "Resume session" else "Pause session"
                )
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (isPaused) "Resume" else "Pause",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            IconButton(
                onClick = onEndSession,
                modifier = Modifier.accessibilitySemantics(
                    description = "End session"
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "End",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Progress indicator
            CircularProgressIndicator(
                progress = if (totalExercises > 0) completedExercises.toFloat() / totalExercises else 0f,
                modifier = Modifier.size(32.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary
            )
            
            // Exercise count
            Text(
                text = "$completedExercises/$totalExercises exercises",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.accessibilitySemantics(
                    description = "$completedExercises of $totalExercises exercises completed"
                )
            )
            
            // Status indicator
            if (isPaused) {
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            text = "Paused",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.error
                    )
                )
            }
        }
    }
}