package com.example.liftrix.ui.navigation

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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.model.WorkoutSessionId
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * 🔥 NEW: Live session bar that shows persistent workout session status
 * 
 * This component replaces the complex WorkoutNowBar with a simplified version
 * that uses the unified session model. It appears at the bottom of the screen
 * whenever there's a live session and persists across all screens.
 * 
 * Key features:
 * - Always visible when session is live
 * - Shows session status (active/paused)
 * - Real-time duration updates
 * - Simple pause/resume/stop controls
 * - Clean animation entry/exit
 * - Accessibility support
 */
@Composable
fun LiveSessionBar(
    session: UnifiedWorkoutSession?,
    onBarClick: () -> Unit,
    onPauseResume: () -> Unit,
    onStopSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 🔥 FIX: More reactive visibility check that responds to session state changes
    val isVisible = session?.isLive() == true
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300)),
        modifier = modifier
    ) {
        session?.let { liveSession ->
            LiveSessionBarContent(
                session = liveSession,
                onBarClick = onBarClick,
                onPauseResume = onPauseResume,
                onStopSession = onStopSession
            )
        }
    }
}

@Composable
private fun LiveSessionBarContent(
    session: UnifiedWorkoutSession,
    onBarClick: () -> Unit,
    onPauseResume: () -> Unit,
    onStopSession: () -> Unit
) {
    // Real-time duration updates with proper lifecycle management
    var currentDuration by remember(session.id) { mutableStateOf(session.getTotalDurationSeconds()) }
    
    LaunchedEffect(session.id, session.sessionStatus) {
        // Update immediately when session or status changes
        currentDuration = session.getTotalDurationSeconds()
        
        // Only start timer if session is active
        if (session.sessionStatus == UnifiedWorkoutSession.SessionStatus.ACTIVE) {
            while (session.isLive()) {
                currentDuration = session.getTotalDurationSeconds()
                delay(1000) // Update every second
                
                // Break if session is no longer active
                if (session.sessionStatus != UnifiedWorkoutSession.SessionStatus.ACTIVE) {
                    break
                }
            }
        }
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(16.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onBarClick() }
                .semantics { 
                    contentDescription = "Active workout session: ${session.name}. Tap to view details."
                },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side - Session info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Status indicator
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when (session.sessionStatus) {
                                    UnifiedWorkoutSession.SessionStatus.ACTIVE -> 
                                        MaterialTheme.colorScheme.primary
                                    UnifiedWorkoutSession.SessionStatus.PAUSED -> 
                                        MaterialTheme.colorScheme.outline
                                    UnifiedWorkoutSession.SessionStatus.COMPLETED ->
                                        MaterialTheme.colorScheme.tertiary
                                    UnifiedWorkoutSession.SessionStatus.FAILED_TO_SAVE ->
                                        MaterialTheme.colorScheme.error
                                }
                            )
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // Workout icon
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = "Live session",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Session details
                    Column {
                        Text(
                            text = session.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        val sessionStats = session.getSessionStats()
                        
                        Text(
                            text = "${sessionStats.totalExercises} exercises • ${sessionStats.completedSets}/${sessionStats.totalSets} sets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
                
                // Right side - Timer and controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    // Timer
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = formatDuration(currentDuration),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        
                        Text(
                            text = when (session.sessionStatus) {
                                UnifiedWorkoutSession.SessionStatus.ACTIVE -> "Active"
                                UnifiedWorkoutSession.SessionStatus.PAUSED -> "Paused"
                                UnifiedWorkoutSession.SessionStatus.COMPLETED -> "Completed"
                                UnifiedWorkoutSession.SessionStatus.FAILED_TO_SAVE -> "Save Failed"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Control buttons
                    Row {
                        // Pause/Resume button
                        IconButton(
                            onClick = onPauseResume,
                            modifier = Modifier.size(40.dp),
                            enabled = session.sessionStatus != UnifiedWorkoutSession.SessionStatus.COMPLETED
                        ) {
                            Icon(
                                imageVector = when (session.sessionStatus) {
                                    UnifiedWorkoutSession.SessionStatus.ACTIVE -> Icons.Default.Pause
                                    UnifiedWorkoutSession.SessionStatus.PAUSED -> Icons.Default.PlayArrow
                                    UnifiedWorkoutSession.SessionStatus.COMPLETED -> Icons.Default.PlayArrow
                                    UnifiedWorkoutSession.SessionStatus.FAILED_TO_SAVE -> Icons.Default.Error
                                },
                                contentDescription = when (session.sessionStatus) {
                                    UnifiedWorkoutSession.SessionStatus.ACTIVE -> "Pause workout"
                                    UnifiedWorkoutSession.SessionStatus.PAUSED -> "Resume workout"
                                    UnifiedWorkoutSession.SessionStatus.COMPLETED -> "Workout completed"
                                    UnifiedWorkoutSession.SessionStatus.FAILED_TO_SAVE -> "Retry save workout"
                                },
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        // Stop button
                        IconButton(
                            onClick = onStopSession,
                            modifier = Modifier.size(40.dp),
                            enabled = session.sessionStatus != UnifiedWorkoutSession.SessionStatus.COMPLETED
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop workout",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Formats duration in seconds to HH:MM:SS or MM:SS format
 */
@Composable
private fun formatDuration(durationSeconds: Long): String {
    val hours = durationSeconds / 3600
    val minutes = (durationSeconds % 3600) / 60
    val seconds = durationSeconds % 60
    
    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
        else -> String.format("%02d:%02d", minutes, seconds)
    }
}

@Preview
@Composable
private fun LiveSessionBarPreview() {
    LiftrixTheme {
        var sessionStatus by remember { 
            mutableStateOf(UnifiedWorkoutSession.SessionStatus.ACTIVE) 
        }
        
        // Mock session data for preview
        val mockSession = UnifiedWorkoutSession(
            id = WorkoutSessionId("preview"),
            userId = "user1",
            name = "Upper Body Strength",
            exercises = listOf(
                // Mock exercises would go here
            ),
            sessionStatus = sessionStatus,
            startedAt = Instant.now().minusSeconds(1800), // 30 minutes ago
            elapsedTimeSeconds = 1800
        )
        
        Column {
            LiveSessionBar(
                session = mockSession,
                onBarClick = { },
                onPauseResume = { 
                    sessionStatus = when (sessionStatus) {
                        UnifiedWorkoutSession.SessionStatus.ACTIVE -> 
                            UnifiedWorkoutSession.SessionStatus.PAUSED
                        UnifiedWorkoutSession.SessionStatus.PAUSED -> 
                            UnifiedWorkoutSession.SessionStatus.ACTIVE
                        UnifiedWorkoutSession.SessionStatus.COMPLETED -> 
                            UnifiedWorkoutSession.SessionStatus.COMPLETED
                        UnifiedWorkoutSession.SessionStatus.FAILED_TO_SAVE -> 
                            UnifiedWorkoutSession.SessionStatus.FAILED_TO_SAVE
                    }
                },
                onStopSession = { 
                    sessionStatus = UnifiedWorkoutSession.SessionStatus.COMPLETED
                }
            )
        }
    }
}

@Preview
@Composable
private fun LiveSessionBarPausedPreview() {
    LiftrixTheme {
        // Mock paused session
        val mockSession = UnifiedWorkoutSession(
            id = WorkoutSessionId("preview"),
            userId = "user1",
            name = "Full Body Workout",
            exercises = listOf(),
            sessionStatus = UnifiedWorkoutSession.SessionStatus.PAUSED,
            startedAt = Instant.now().minusSeconds(2700), // 45 minutes ago
            elapsedTimeSeconds = 2700
        )
        
        Column {
            LiveSessionBar(
                session = mockSession,
                onBarClick = { },
                onPauseResume = { },
                onStopSession = { }
            )
        }
    }
}
