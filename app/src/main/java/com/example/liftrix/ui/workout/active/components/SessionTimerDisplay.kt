package com.example.liftrix.ui.workout.active.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.service.WorkoutTimerService
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Session timer display component that shows the current workout session duration.
 * 
 * Features:
 * - Animated time display with smooth transitions
 * - Visual state indicators (running, paused, stopped)
 * - Material3 design with proper contrast and accessibility
 * - Monospace font for consistent timer display
 * - Semantic content descriptions for screen readers
 * 
 * @param timerState Current timer service state
 * @param formattedTime Pre-formatted time string (HH:MM:SS or MM:SS)
 * @param modifier Modifier for styling
 */
@Composable
fun SessionTimerDisplay(
    timerState: WorkoutTimerService.TimerServiceState,
    formattedTime: String,
    modifier: Modifier = Modifier
) {
    val (statusIcon, statusText, containerColor) = when (val state = timerState.timerState) {
        is WorkoutTimerService.TimerState.Stopped -> Triple(
            Icons.Filled.Stop,
            "Ready to start",
            MaterialTheme.colorScheme.surfaceVariant
        )
        is WorkoutTimerService.TimerState.SessionRunning -> Triple(
            Icons.Filled.PlayArrow,
            "Session active",
            MaterialTheme.colorScheme.primaryContainer
        )
        is WorkoutTimerService.TimerState.SessionPaused -> Triple(
            Icons.Filled.Pause,
            "Session paused",
            MaterialTheme.colorScheme.secondaryContainer
        )
        else -> Triple(
            Icons.Filled.AccessTime,
            "Session timer",
            MaterialTheme.colorScheme.surfaceVariant
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Animated timer display
            AnimatedContent(
                targetState = formattedTime,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "session_timer_animation",
                modifier = Modifier.semantics {
                    contentDescription = "Session time: $formattedTime"
                }
            ) { timeText ->
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Session label
            Text(
                text = "Session Duration",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Utility function to format time in seconds to display format
 */
fun formatSessionTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}

/**
 * Utility function to extract session time from timer state
 */
fun getSessionTimeFromState(timerState: WorkoutTimerService.TimerServiceState): String {
    return when (val state = timerState.timerState) {
        is WorkoutTimerService.TimerState.SessionRunning -> formatSessionTime(state.elapsedSeconds)
        is WorkoutTimerService.TimerState.SessionPaused -> formatSessionTime(state.pausedAtSeconds)
        else -> "00:00"
    }
}

@Preview(showBackground = true)
@Composable
private fun SessionTimerDisplayPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stopped state
            SessionTimerDisplay(
                timerState = WorkoutTimerService.TimerServiceState(
                    timerState = WorkoutTimerService.TimerState.Stopped
                ),
                formattedTime = "00:00"
            )
            
            // Running state
            SessionTimerDisplay(
                timerState = WorkoutTimerService.TimerServiceState(
                    timerState = WorkoutTimerService.TimerState.SessionRunning(
                        startTime = kotlinx.datetime.Clock.System.now(),
                        elapsedSeconds = 1845 // 30:45
                    ),
                    isRunning = true,
                    sessionDurationSeconds = 1845
                ),
                formattedTime = "30:45"
            )
            
            // Paused state
            SessionTimerDisplay(
                timerState = WorkoutTimerService.TimerServiceState(
                    timerState = WorkoutTimerService.TimerState.SessionPaused(
                        startTime = kotlinx.datetime.Clock.System.now(),
                        pausedAtSeconds = 3725 // 1:02:05
                    ),
                    sessionDurationSeconds = 3725
                ),
                formattedTime = "1:02:05"
            )
        }
    }
} 