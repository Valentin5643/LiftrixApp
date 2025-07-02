package com.example.liftrix.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liftrix.service.TimerServiceManager
import com.example.liftrix.service.WorkoutTimerService
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Minimized timer component that provides persistent workout timer display across all screens.
 * 
 * Key features:
 * - Small, unobtrusive corner placement
 * - Always visible during active workout sessions
 * - Stop/Resume controls in minimized UI
 * - Expandable to show detailed information
 * - Click to navigate to active workout screen
 * 
 * Design follows modern fitness app patterns with clean, accessible controls.
 * 
 * @param timerState Current timer service state
 * @param connectionState Timer service connection status
 * @param isVisible Whether the component should be visible
 * @param isExpanded Whether the detailed view is shown
 * @param onToggleExpanded Callback to toggle expanded state
 * @param onNavigateToWorkout Callback to navigate to active workout screen
 * @param onPause Callback to pause the timer
 * @param onResume Callback to resume the timer  
 * @param onStop Callback to stop the timer
 * @param modifier Modifier for styling
 */
@Composable
fun MinimizedTimerComponent(
    timerState: WorkoutTimerService.TimerServiceState,
    connectionState: TimerServiceManager.ConnectionState,
    isVisible: Boolean,
    isExpanded: Boolean = false,
    onToggleExpanded: () -> Unit = {},
    onNavigateToWorkout: () -> Unit = {},
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onStop: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300)),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Workout timer: ${getTimerDescription(timerState.timerState)}"
                },
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = getTimerBackgroundColor(timerState.timerState)
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isExpanded) 16.dp else 0.dp,
                bottomEnd = if (isExpanded) 16.dp else 0.dp
            )
        ) {
            Column {
                // Minimized view (always visible)
                MinimizedTimerContent(
                    timerState = timerState,
                    connectionState = connectionState,
                    isExpanded = isExpanded,
                    onToggleExpanded = onToggleExpanded,
                    onNavigateToWorkout = onNavigateToWorkout,
                    onPause = onPause,
                    onResume = onResume,
                    onStop = onStop
                )
                
                // Expanded view (conditional)
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(animationSpec = tween(300)),
                    exit = shrinkVertically(animationSpec = tween(300))
                ) {
                    ExpandedTimerContent(
                        timerState = timerState,
                        onNavigateToWorkout = onNavigateToWorkout
                    )
                }
            }
        }
    }
}

/**
 * Minimized timer content with essential information and controls
 */
@Composable
private fun MinimizedTimerContent(
    timerState: WorkoutTimerService.TimerServiceState,
    connectionState: TimerServiceManager.ConnectionState,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onNavigateToWorkout: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onNavigateToWorkout() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Timer display
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Status indicator
            Surface(
                modifier = Modifier.size(8.dp),
                shape = RoundedCornerShape(4.dp),
                color = getTimerIndicatorColor(timerState.timerState)
            ) {}
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = getTimerDisplayText(timerState),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = getTimerTextColor(timerState.timerState)
                )
                
                if (connectionState is TimerServiceManager.ConnectionState.Connected) {
                    Text(
                        text = getTimerStatusText(timerState.timerState),
                        style = MaterialTheme.typography.bodySmall,
                        color = getTimerTextColor(timerState.timerState).copy(alpha = 0.8f)
                    )
                } else {
                    Text(
                        text = "Connecting...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        
        // Controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play/Pause button
            when (timerState.timerState) {
                is WorkoutTimerService.TimerState.SessionRunning,
                is WorkoutTimerService.TimerState.RestActive -> {
                    IconButton(
                        onClick = onPause,
                        modifier = Modifier
                            .size(32.dp)
                            .semantics { contentDescription = "Pause timer" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = null,
                            tint = getTimerTextColor(timerState.timerState),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                is WorkoutTimerService.TimerState.SessionPaused,
                is WorkoutTimerService.TimerState.RestPaused -> {
                    IconButton(
                        onClick = onResume,
                        modifier = Modifier
                            .size(32.dp)
                            .semantics { contentDescription = "Resume timer" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = getTimerTextColor(timerState.timerState),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                else -> {}
            }
            
            // Stop button
            if (timerState.timerState !is WorkoutTimerService.TimerState.Stopped) {
                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .size(32.dp)
                        .semantics { contentDescription = "Stop timer" }
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        tint = getTimerTextColor(timerState.timerState),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            // Expand/collapse button
            IconButton(
                onClick = onToggleExpanded,
                modifier = Modifier
                    .size(32.dp)
                    .semantics { 
                        contentDescription = if (isExpanded) "Collapse timer details" else "Expand timer details"
                    }
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = getTimerTextColor(timerState.timerState),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Expanded timer content with detailed session information
 */
@Composable
private fun ExpandedTimerContent(
    timerState: WorkoutTimerService.TimerServiceState,
    onNavigateToWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Session details
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Session Duration",
                    style = MaterialTheme.typography.bodySmall,
                    color = getTimerTextColor(timerState.timerState).copy(alpha = 0.8f)
                )
                Text(
                    text = formatTime(timerState.sessionDurationSeconds),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = getTimerTextColor(timerState.timerState)
                )
            }
            
            when (val state = timerState.timerState) {
                is WorkoutTimerService.TimerState.RestActive,
                is WorkoutTimerService.TimerState.RestPaused -> {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Rest Remaining",
                            style = MaterialTheme.typography.bodySmall,
                            color = getTimerTextColor(timerState.timerState).copy(alpha = 0.8f)
                        )
                        Text(
                            text = formatTime(timerState.restRemainingSeconds.toLong()),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                else -> {}
            }
        }
        
        // Navigate to workout button
        Text(
            text = "Tap to view full workout",
            style = MaterialTheme.typography.bodySmall,
            color = getTimerTextColor(timerState.timerState).copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clickable { onNavigateToWorkout() }
        )
    }
}

/**
 * Gets the appropriate background color based on timer state
 */
@Composable
private fun getTimerBackgroundColor(timerState: WorkoutTimerService.TimerState): Color {
    return when (timerState) {
        is WorkoutTimerService.TimerState.SessionRunning -> MaterialTheme.colorScheme.primaryContainer
        is WorkoutTimerService.TimerState.SessionPaused -> MaterialTheme.colorScheme.tertiaryContainer
        is WorkoutTimerService.TimerState.RestActive -> MaterialTheme.colorScheme.secondaryContainer
        is WorkoutTimerService.TimerState.RestPaused -> MaterialTheme.colorScheme.tertiaryContainer
        is WorkoutTimerService.TimerState.Stopped -> MaterialTheme.colorScheme.surfaceVariant
    }
}

/**
 * Gets the appropriate text color based on timer state
 */
@Composable
private fun getTimerTextColor(timerState: WorkoutTimerService.TimerState): Color {
    return when (timerState) {
        is WorkoutTimerService.TimerState.SessionRunning -> MaterialTheme.colorScheme.onPrimaryContainer
        is WorkoutTimerService.TimerState.SessionPaused -> MaterialTheme.colorScheme.onTertiaryContainer
        is WorkoutTimerService.TimerState.RestActive -> MaterialTheme.colorScheme.onSecondaryContainer
        is WorkoutTimerService.TimerState.RestPaused -> MaterialTheme.colorScheme.onTertiaryContainer
        is WorkoutTimerService.TimerState.Stopped -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * Gets the appropriate indicator color based on timer state
 */
@Composable
private fun getTimerIndicatorColor(timerState: WorkoutTimerService.TimerState): Color {
    return when (timerState) {
        is WorkoutTimerService.TimerState.SessionRunning -> MaterialTheme.colorScheme.primary
        is WorkoutTimerService.TimerState.SessionPaused -> MaterialTheme.colorScheme.tertiary
        is WorkoutTimerService.TimerState.RestActive -> MaterialTheme.colorScheme.secondary
        is WorkoutTimerService.TimerState.RestPaused -> MaterialTheme.colorScheme.tertiary
        is WorkoutTimerService.TimerState.Stopped -> MaterialTheme.colorScheme.outline
    }
}

/**
 * Gets the main timer display text
 */
private fun getTimerDisplayText(timerState: WorkoutTimerService.TimerServiceState): String {
    return when (val state = timerState.timerState) {
        is WorkoutTimerService.TimerState.Stopped -> "00:00"
        is WorkoutTimerService.TimerState.SessionRunning -> formatTime(state.elapsedSeconds)
        is WorkoutTimerService.TimerState.SessionPaused -> formatTime(state.pausedAtSeconds)
        is WorkoutTimerService.TimerState.RestActive -> formatTime(state.remainingSeconds.toLong())
        is WorkoutTimerService.TimerState.RestPaused -> formatTime(state.remainingSeconds.toLong())
    }
}

/**
 * Gets the timer status text
 */
private fun getTimerStatusText(timerState: WorkoutTimerService.TimerState): String {
    return when (timerState) {
        is WorkoutTimerService.TimerState.Stopped -> "Ready"
        is WorkoutTimerService.TimerState.SessionRunning -> "Active"
        is WorkoutTimerService.TimerState.SessionPaused -> "Paused"
        is WorkoutTimerService.TimerState.RestActive -> "Rest"
        is WorkoutTimerService.TimerState.RestPaused -> "Rest Paused"
    }
}

/**
 * Gets the timer description for accessibility
 */
private fun getTimerDescription(timerState: WorkoutTimerService.TimerState): String {
    return when (timerState) {
        is WorkoutTimerService.TimerState.Stopped -> "Timer stopped"
        is WorkoutTimerService.TimerState.SessionRunning -> "Session active for ${formatTime(timerState.elapsedSeconds)}"
        is WorkoutTimerService.TimerState.SessionPaused -> "Session paused at ${formatTime(timerState.pausedAtSeconds)}"
        is WorkoutTimerService.TimerState.RestActive -> "Rest timer active with ${formatTime(timerState.remainingSeconds.toLong())} remaining"
        is WorkoutTimerService.TimerState.RestPaused -> "Rest timer paused with ${formatTime(timerState.remainingSeconds.toLong())} remaining"
    }
}

/**
 * Formats time in seconds to MM:SS or HH:MM:SS format
 */
private fun formatTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}

@Preview(showBackground = true)
@Composable
private fun MinimizedTimerComponentPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Active session preview
            MinimizedTimerComponent(
                timerState = WorkoutTimerService.TimerServiceState(
                    timerState = WorkoutTimerService.TimerState.SessionRunning(
                        startTime = kotlinx.datetime.Clock.System.now(),
                        elapsedSeconds = 1845 // 30:45
                    ),
                    isRunning = true,
                    sessionDurationSeconds = 1845
                ),
                connectionState = TimerServiceManager.ConnectionState.Connected,
                isVisible = true,
                isExpanded = false
            )
            
            // Rest timer preview
            MinimizedTimerComponent(
                timerState = WorkoutTimerService.TimerServiceState(
                    timerState = WorkoutTimerService.TimerState.RestActive(
                        restTimer = com.example.liftrix.domain.model.RestTimer(
                            durationSeconds = 90,
                            isEnabled = true
                        ),
                        remainingSeconds = 45
                    ),
                    isRunning = true,
                    sessionDurationSeconds = 1200,
                    restRemainingSeconds = 45
                ),
                connectionState = TimerServiceManager.ConnectionState.Connected,
                isVisible = true,
                isExpanded = true
            )
            
            // Paused session preview
            MinimizedTimerComponent(
                timerState = WorkoutTimerService.TimerServiceState(
                    timerState = WorkoutTimerService.TimerState.SessionPaused(
                        startTime = kotlinx.datetime.Clock.System.now(),
                        pausedAtSeconds = 900 // 15:00
                    ),
                    isRunning = false,
                    sessionDurationSeconds = 900
                ),
                connectionState = TimerServiceManager.ConnectionState.Connected,
                isVisible = true,
                isExpanded = false
            )
        }
    }
}