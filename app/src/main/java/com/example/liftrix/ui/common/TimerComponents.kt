package com.example.liftrix.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liftrix.service.TimerServiceManager
import com.example.liftrix.service.WorkoutTimerService

@Composable
fun SessionControlsCard(
    timerState: WorkoutTimerService.TimerServiceState,
    connectionState: TimerServiceManager.ConnectionState,
    onStartSession: () -> Unit,
    onPauseTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    onStopTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Session Controls",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (timerState.timerState) {
                    is WorkoutTimerService.TimerState.Stopped -> {
                        SessionControlButton(
                            text = "Start Session",
                            onClick = onStartSession,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    is WorkoutTimerService.TimerState.SessionRunning -> {
                        SessionControlButton(
                            text = "Pause",
                            onClick = onPauseTimer,
                            modifier = Modifier.weight(1f)
                        )
                        SessionControlButton(
                            text = "Stop",
                            onClick = onStopTimer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    is WorkoutTimerService.TimerState.SessionPaused -> {
                        SessionControlButton(
                            text = "Resume",
                            onClick = onResumeTimer,
                            modifier = Modifier.weight(1f)
                        )
                        SessionControlButton(
                            text = "Stop",
                            onClick = onStopTimer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    else -> {
                        SessionControlButton(
                            text = "Stop",
                            onClick = onStopTimer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            // Connection status indicator
            Text(
                text = "Status: ${connectionState::class.simpleName}",
                style = MaterialTheme.typography.bodySmall,
                color = when (connectionState) {
                    is TimerServiceManager.ConnectionState.Connected -> MaterialTheme.colorScheme.primary
                    is TimerServiceManager.ConnectionState.Disconnected -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
fun ExerciseAdditionCard(
    onAddExercise: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Add Exercise",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onAddExercise,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add New Exercise")
            }
        }
    }
}

@Composable
fun TimerDisplay(
    elapsedTime: Long,
    modifier: Modifier = Modifier
) {
    Text(
        text = formatDurationTime(elapsedTime),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

@Composable
fun RestTimerDisplay(
    timeRemaining: Long,
    totalTime: Long,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Rest Timer",
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = formatDurationTime(timeRemaining),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        LinearProgressIndicator(
            progress = if (totalTime > 0) (totalTime - timeRemaining).toFloat() / totalTime else 0f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )
    }
}

@Composable
fun SessionControlButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
    ) {
        Text(text)
    }
}

fun formatDurationTime(timeInSeconds: Long): String {
    val hours = timeInSeconds / 3600
    val minutes = (timeInSeconds % 3600) / 60
    val seconds = timeInSeconds % 60
    
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}