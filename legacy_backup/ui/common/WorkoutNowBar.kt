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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liftrix.domain.model.ActiveWorkoutSession
import com.example.liftrix.service.LiveWorkoutSessionManager
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlinx.coroutines.delay

@Composable
fun WorkoutNowBar(
    sessionState: LiveWorkoutSessionManager.LiveSessionState,
    sessionDuration: Long,
    onBarClick: () -> Unit,
    onPauseResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = sessionState is LiveWorkoutSessionManager.LiveSessionState.ActiveSession,
        enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300)),
        modifier = modifier
    ) {
        if (sessionState is LiveWorkoutSessionManager.LiveSessionState.ActiveSession) {
            WorkoutNowBarContent(
                session = sessionState.session,
                isRunning = sessionState.isRunning,
                duration = sessionDuration,
                onBarClick = onBarClick,
                onPauseResume = onPauseResume
            )
        }
    }
}

@Composable
private fun WorkoutNowBarContent(
    session: ActiveWorkoutSession,
    isRunning: Boolean,
    duration: Long,
    onBarClick: () -> Unit,
    onPauseResume: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onBarClick() }
                .semantics { contentDescription = "Active workout session: ${session.name}" },
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
                                if (isRunning) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // Workout icon
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
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
                        
                        val completedExercises = session.exercises.count { exercise ->
                            exercise.sets.any { it.completedAt != null }
                        }
                        
                        Text(
                            text = "${session.exercises.size} exercises • $completedExercises completed",
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
                            text = formatDuration(duration),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        
                        Text(
                            text = if (isRunning) "Active" else "Paused",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Pause/Resume button
                    IconButton(
                        onClick = onPauseResume,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isRunning) "Pause workout" else "Resume workout",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun formatDuration(durationMillis: Long): String {
    val seconds = (durationMillis / 1000) % 60
    val minutes = (durationMillis / (1000 * 60)) % 60
    val hours = (durationMillis / (1000 * 60 * 60))
    
    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
        else -> String.format("%02d:%02d", minutes, seconds)
    }
}

@Preview
@Composable
private fun WorkoutNowBarPreview() {
    LiftrixTheme {
        var isRunning by remember { mutableStateOf(true) }
        var duration by remember { mutableStateOf(0L) }
        
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                if (isRunning) {
                    duration += 1000
                }
            }
        }
        
        // Mock session data for preview
        val mockSession = ActiveWorkoutSession(
            id = com.example.liftrix.domain.model.WorkoutSessionId("preview"),
            userId = "user1",
            name = "Upper Body Strength",
            exercises = listOf(
                // Mock exercises would go here
            ),
            sessionState = com.example.liftrix.domain.model.ActiveWorkoutSession.SessionState.ACTIVE,
            startedAt = java.time.Instant.now()
        )
        
        val sessionState = LiveWorkoutSessionManager.LiveSessionState.ActiveSession(
            session = mockSession,
            isRunning = isRunning,
            startTime = System.currentTimeMillis()
        )
        
        Column {
            WorkoutNowBar(
                sessionState = sessionState,
                sessionDuration = duration,
                onBarClick = { },
                onPauseResume = { isRunning = !isRunning }
            )
        }
    }
}