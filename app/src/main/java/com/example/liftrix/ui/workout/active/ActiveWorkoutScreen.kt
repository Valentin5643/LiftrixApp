package com.example.liftrix.ui.workout.active

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftrix.service.TimerServiceManager
import com.example.liftrix.service.WorkoutTimerService
import com.example.liftrix.ui.common.*
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Active workout screen foundation that displays session timer and exercise management.
 * 
 * This screen serves as the foundation for active workout sessions, providing:
 * - Session timer display with start/pause/stop controls
 * - Rest timer integration when active
 * - Exercise addition placeholder
 * - Timer service integration for persistent timing
 * 
 * Features:
 * - Real-time timer display with proper formatting
 * - Session control buttons with proper state management
 * - Material3 design with accessibility support
 * - Integration with WorkoutTimerService via TimerServiceManager
 * - Placeholder for exercise addition and management
 * 
 * @param onNavigateBack Callback for back navigation
 * @param onAddExercise Callback for exercise addition (placeholder)
 * @param modifier Modifier for styling
 * @param timerServiceManager Timer service manager for state management
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    onNavigateBack: () -> Unit,
    onAddExercise: () -> Unit = {},
    modifier: Modifier = Modifier,
    timerServiceManager: TimerServiceManager = hiltViewModel()
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val connectionState by timerServiceManager.connectionState.collectAsStateWithLifecycle()
    val timerState by timerServiceManager.timerState.collectAsStateWithLifecycle()

    // Bind to timer service on screen launch
    LaunchedEffect(Unit) {
        timerServiceManager.bindService()
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Active Workout",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics {
                            contentDescription = "Navigate back"
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Timer Display Section
            item {
                TimerDisplayCard(
                    timerState = timerState,
                    connectionState = connectionState,
                    onStartSession = { timerServiceManager.startSession() },
                    onPauseTimer = { timerServiceManager.pauseTimer() },
                    onResumeTimer = { timerServiceManager.resumeTimer() },
                    onStopTimer = { timerServiceManager.stopTimer() },
                    onSkipRest = { timerServiceManager.skipRest() }
                )
            }
            
            // Session Controls Section
            item {
                SessionControlsCard(
                    timerState = timerState,
                    connectionState = connectionState,
                    onStartSession = { timerServiceManager.startSession() },
                    onPauseTimer = { timerServiceManager.pauseTimer() },
                    onResumeTimer = { timerServiceManager.resumeTimer() },
                    onStopTimer = { timerServiceManager.stopTimer() }
                )
            }
            
            // Exercise Addition Section
            item {
                ExerciseAdditionCard(
                    onAddExercise = onAddExercise
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Card component displaying the session timer and rest timer when active
 */
@Composable
private fun TimerDisplayCard(
    timerState: WorkoutTimerService.TimerServiceState,
    connectionState: TimerServiceManager.ConnectionState,
    onStartSession: () -> Unit,
    onPauseTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    onStopTimer: () -> Unit,
    onSkipRest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Connection Status
            when (connectionState) {
                is TimerServiceManager.ConnectionState.Disconnected -> {
                    Text(
                        text = "Timer Service Disconnected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
                is TimerServiceManager.ConnectionState.Connecting -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Connecting to Timer Service...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is TimerServiceManager.ConnectionState.Error -> {
                    Text(
                        text = "Timer Service Error: ${connectionState.exception.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
                is TimerServiceManager.ConnectionState.Connected -> {
                    // Timer Display
                    when (val state = timerState.timerState) {
                        is WorkoutTimerService.TimerState.Stopped -> {
                            Text(
                                text = "00:00",
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Ready to start workout",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        is WorkoutTimerService.TimerState.SessionRunning -> {
                            Text(
                                text = formatTime(state.elapsedSeconds),
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Session Active",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        is WorkoutTimerService.TimerState.SessionPaused -> {
                            Text(
                                text = formatTime(state.pausedAtSeconds),
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Session Paused",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        is WorkoutTimerService.TimerState.RestActive -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Session: ${formatTime(timerState.sessionDurationSeconds)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = formatTime(state.remainingSeconds.toLong()),
                                    style = MaterialTheme.typography.displayLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "Rest Timer",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                FilledTonalButton(
                                    onClick = onSkipRest,
                                    modifier = Modifier.semantics {
                                        contentDescription = "Skip rest timer"
                                    }
                                ) {
                                    Text("Skip Rest")
                                }
                            }
                        }
                        is WorkoutTimerService.TimerState.RestPaused -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Session: ${formatTime(timerState.sessionDurationSeconds)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = formatTime(state.remainingSeconds.toLong()),
                                    style = MaterialTheme.typography.displayLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "Rest Timer Paused",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card component with session control buttons
 */
@Composable
private fun SessionControlsCard(
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Session Controls",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            val isConnected = connectionState is TimerServiceManager.ConnectionState.Connected
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (val state = timerState.timerState) {
                    is WorkoutTimerService.TimerState.Stopped -> {
                        SessionControlButton(
                            onClick = onStartSession,
                            icon = Icons.Default.PlayArrow,
                            text = "Start",
                            enabled = isConnected,
                            contentDescription = "Start workout session"
                        )
                    }
                    is WorkoutTimerService.TimerState.SessionRunning -> {
                        SessionControlButton(
                            onClick = onPauseTimer,
                            icon = Icons.Default.Pause,
                            text = "Pause",
                            enabled = isConnected,
                            contentDescription = "Pause workout session"
                        )
                        SessionControlButton(
                            onClick = onStopTimer,
                            icon = Icons.Default.Stop,
                            text = "Stop",
                            enabled = isConnected,
                            contentDescription = "Stop workout session"
                        )
                    }
                    is WorkoutTimerService.TimerState.SessionPaused -> {
                        SessionControlButton(
                            onClick = onResumeTimer,
                            icon = Icons.Default.PlayArrow,
                            text = "Resume",
                            enabled = isConnected,
                            contentDescription = "Resume workout session"
                        )
                        SessionControlButton(
                            onClick = onStopTimer,
                            icon = Icons.Default.Stop,
                            text = "Stop",
                            enabled = isConnected,
                            contentDescription = "Stop workout session"
                        )
                    }
                    is WorkoutTimerService.TimerState.RestActive -> {
                        SessionControlButton(
                            onClick = onPauseTimer,
                            icon = Icons.Default.Pause,
                            text = "Pause",
                            enabled = isConnected,
                            contentDescription = "Pause rest timer"
                        )
                        SessionControlButton(
                            onClick = onStopTimer,
                            icon = Icons.Default.Stop,
                            text = "Stop",
                            enabled = isConnected,
                            contentDescription = "Stop workout session"
                        )
                    }
                    is WorkoutTimerService.TimerState.RestPaused -> {
                        SessionControlButton(
                            onClick = onResumeTimer,
                            icon = Icons.Default.PlayArrow,
                            text = "Resume",
                            enabled = isConnected,
                            contentDescription = "Resume rest timer"
                        )
                        SessionControlButton(
                            onClick = onStopTimer,
                            icon = Icons.Default.Stop,
                            text = "Stop",
                            enabled = isConnected,
                            contentDescription = "Stop workout session"
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reusable session control button component
 */
@Composable
private fun SessionControlButton(
    onClick: () -> Unit,
    icon: ImageVector,
    text: String,
    enabled: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
        }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text)
    }
}

/**
 * Card component for exercise addition placeholder
 */
@Composable
private fun ExerciseAdditionCard(
    onAddExercise: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Exercises",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Text(
                text = "No exercises added yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            OutlinedButton(
                onClick = onAddExercise,
                modifier = Modifier.semantics {
                    contentDescription = "Add exercise to workout"
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Exercise")
            }
        }
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
private fun ActiveWorkoutScreenPreview() {
    LiftrixTheme {
        // Preview content would need mock implementations
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "ActiveWorkoutScreen Preview\n(Requires timer service integration)",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}