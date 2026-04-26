package com.example.liftrix.ui.workout.active.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.liftrix.ui.components.animations.ProgressRing
import com.example.liftrix.ui.components.cards.ElevatedLiftrixCard
import com.example.liftrix.ui.components.cards.CardSpacing
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Professional timer display with animated progress rings and clear time readouts
 * Designed for athletic confidence and visual hierarchy
 */
@Composable
fun ProfessionalTimerDisplay(
    timerState: WorkoutTimerService.TimerServiceState,
    modifier: Modifier = Modifier,
    showProgressRing: Boolean = true,
    compact: Boolean = false
) {
    val sessionTime = getSessionTimeFromTimerState(timerState)
    val restTime = getRestTimeFromTimerState(timerState)
    val isRestActive = timerState.timerState is WorkoutTimerService.TimerState.RestActive ||
            timerState.timerState is WorkoutTimerService.TimerState.RestPaused
    
    // Calculate progress for session timer (arbitrary max of 3 hours)
    val maxSessionSeconds = 3 * 60 * 60 // 3 hours
    val sessionProgress by animateFloatAsState(
        targetValue = (sessionTime.inWholeSeconds.toFloat() / maxSessionSeconds).coerceAtMost(1f),
        animationSpec = tween(300),
        label = "session_progress"
    )
    
    // Calculate progress for rest timer
    val restProgress by animateFloatAsState(
        targetValue = when (val state = timerState.timerState) {
            is WorkoutTimerService.TimerState.RestActive -> {
                if (state.restTimer.durationSeconds > 0) {
                    state.remainingSeconds.toFloat() / state.restTimer.durationSeconds.toFloat()
                } else 0f
            }
            is WorkoutTimerService.TimerState.RestPaused -> {
                if (state.restTimer.durationSeconds > 0) {
                    state.remainingSeconds.toFloat() / state.restTimer.durationSeconds.toFloat()
                } else 0f
            }
            else -> 0f
        },
        animationSpec = tween(300),
        label = "rest_progress"
    )
    
    ElevatedLiftrixCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = buildString {
                    append("Workout timer: ")
                    append(formatDurationForAccessibility(sessionTime))
                    if (isRestActive) {
                        append(", Rest timer: ")
                        append(formatDurationForAccessibility(restTime))
                    }
                }
            },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            if (compact) CardSpacing.M else CardSpacing.L
        )
    ) {
        if (compact) {
            CompactTimerLayout(
                timerState = timerState,
                sessionTime = sessionTime,
                restTime = restTime,
                isRestActive = isRestActive
            )
        } else {
            FullTimerLayout(
                timerState = timerState,
                sessionTime = sessionTime,
                restTime = restTime,
                isRestActive = isRestActive,
                sessionProgress = sessionProgress,
                restProgress = restProgress,
                showProgressRing = showProgressRing
            )
        }
    }
}

@Composable
private fun FullTimerLayout(
    timerState: WorkoutTimerService.TimerServiceState,
    sessionTime: Duration,
    restTime: Duration,
    isRestActive: Boolean,
    sessionProgress: Float,
    restProgress: Float,
    showProgressRing: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CardSpacing.M)
    ) {
        // Primary timer (session or rest)
        val primaryTimerData = if (isRestActive) {
            TimerDisplayData(
                time = restTime,
                label = "Rest Timer",
                icon = Icons.Default.Timer,
                progress = restProgress
            )
        } else {
            TimerDisplayData(
                time = sessionTime,
                label = "Session Timer",
                icon = getSessionIcon(timerState),
                progress = sessionProgress
            )
        }
        
        // Timer status header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = primaryTimerData.icon,
                contentDescription = primaryTimerData.label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = primaryTimerData.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        // Main timer display with progress ring
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(if (showProgressRing) 160.dp else 120.dp)
        ) {
            if (showProgressRing) {
                ProgressRing(
                    progress = primaryTimerData.progress,
                    modifier = Modifier.size(160.dp),
                    strokeWidth = 12.dp,
                    color = if (isRestActive) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            
            // Time display
            AnimatedContent(
                targetState = formatDuration(primaryTimerData.time),
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(300))
                },
                label = "time_animation"
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
        }
        
        // Secondary timer (if rest is active, show session time)
        if (isRestActive) {
            Spacer(modifier = Modifier.height(CardSpacing.XS))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CardSpacing.M)
            ) {
                Icon(
                    imageVector = getSessionIcon(timerState),
                    contentDescription = "Session timer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Session: ${formatDuration(sessionTime)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Timer state description
        Text(
            text = getTimerStateDescription(timerState),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CompactTimerLayout(
    timerState: WorkoutTimerService.TimerServiceState,
    sessionTime: Duration,
    restTime: Duration,
    isRestActive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Session timer
        TimerSection(
            icon = getSessionIcon(timerState),
            label = "Session",
            time = sessionTime,
            isActive = !isRestActive
        )
        
        // Rest timer (if active)
        if (isRestActive) {
            TimerSection(
                icon = Icons.Default.Timer,
                label = "Rest",
                time = restTime,
                isActive = true
            )
        }
    }
}

@Composable
private fun TimerSection(
    icon: ImageVector,
    label: String,
    time: Duration,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        
        Text(
            text = formatDuration(time),
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            ),
            color = if (isActive) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * Get session icon based on timer state
 */
private fun getSessionIcon(timerState: WorkoutTimerService.TimerServiceState): ImageVector {
    return when (timerState.timerState) {
        is WorkoutTimerService.TimerState.Stopped -> Icons.Default.Stop
        is WorkoutTimerService.TimerState.SessionRunning -> Icons.Default.PlayArrow
        is WorkoutTimerService.TimerState.SessionPaused -> Icons.Default.Pause
        else -> Icons.Default.Timer
    }
}

/**
 * Get timer state description for accessibility and user feedback
 */
private fun getTimerStateDescription(timerState: WorkoutTimerService.TimerServiceState): String {
    return when (val state = timerState.timerState) {
        is WorkoutTimerService.TimerState.Stopped -> "Ready to start workout"
        is WorkoutTimerService.TimerState.SessionRunning -> "Session active"
        is WorkoutTimerService.TimerState.SessionPaused -> "Session paused"
        is WorkoutTimerService.TimerState.RestActive -> "Rest period active"
        is WorkoutTimerService.TimerState.RestPaused -> "Rest period paused"
    }
}

/**
 * Extract session time from timer state
 */
private fun getSessionTimeFromTimerState(timerState: WorkoutTimerService.TimerServiceState): Duration {
    return when (val state = timerState.timerState) {
        is WorkoutTimerService.TimerState.SessionRunning -> state.elapsedSeconds.seconds
        is WorkoutTimerService.TimerState.SessionPaused -> state.pausedAtSeconds.seconds
        is WorkoutTimerService.TimerState.RestActive -> timerState.sessionDurationSeconds.seconds
        is WorkoutTimerService.TimerState.RestPaused -> timerState.sessionDurationSeconds.seconds
        else -> 0.seconds
    }
}

/**
 * Extract rest time from timer state
 */
private fun getRestTimeFromTimerState(timerState: WorkoutTimerService.TimerServiceState): Duration {
    return when (val state = timerState.timerState) {
        is WorkoutTimerService.TimerState.RestActive -> state.remainingSeconds.seconds
        is WorkoutTimerService.TimerState.RestPaused -> state.remainingSeconds.seconds
        else -> 0.seconds
    }
}

/**
 * Format duration for display (MM:SS or HH:MM:SS)
 */
private fun formatDuration(duration: Duration): String {
    val totalSeconds = duration.inWholeSeconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * Format duration for accessibility announcements
 */
private fun formatDurationForAccessibility(duration: Duration): String {
    val totalSeconds = duration.inWholeSeconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return buildString {
        if (hours > 0) {
            append("$hours hour")
            if (hours != 1L) append("s")
            append(" ")
        }
        if (minutes > 0) {
            append("$minutes minute")
            if (minutes != 1L) append("s")
            append(" ")
        }
        append("$seconds second")
        if (seconds != 1L) append("s")
    }
}

/**
 * Data class for timer display information
 */
private data class TimerDisplayData(
    val time: Duration,
    val label: String,
    val icon: ImageVector,
    val progress: Float
)

@Preview(showBackground = true)
@Composable
private fun ProfessionalTimerDisplayPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Active session timer
            ProfessionalTimerDisplay(
                timerState = WorkoutTimerService.TimerServiceState(
                    timerState = WorkoutTimerService.TimerState.SessionRunning(
                        startTime = kotlinx.datetime.Clock.System.now(),
                        elapsedSeconds = 1845 // 30:45
                    ),
                    isRunning = true,
                    sessionDurationSeconds = 1845
                ),
                showProgressRing = true
            )
            
            // Compact timer display
            ProfessionalTimerDisplay(
                timerState = WorkoutTimerService.TimerServiceState(
                    timerState = WorkoutTimerService.TimerState.RestActive(
                        restTimer = com.example.liftrix.domain.model.RestTimer(
                            durationSeconds = 90
                        ),
                        remainingSeconds = 45
                    ),
                    sessionDurationSeconds = 2100
                ),
                compact = true,
                showProgressRing = false
            )
        }
    }
} 
