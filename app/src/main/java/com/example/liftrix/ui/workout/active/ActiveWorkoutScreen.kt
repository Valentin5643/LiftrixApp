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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.domain.model.ExerciseSetId
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.service.TimerServiceManager
import com.example.liftrix.service.WorkoutTimerService
import com.example.liftrix.ui.common.*
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.active.components.SaveWorkoutDialog
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Enhanced active workout screen that displays session timer and full workout management.
 * 
 * This screen provides complete workout session functionality including:
 * - Session timer display with start/pause/stop controls
 * - Real-time exercise and set tracking
 * - Exercise addition and management
 * - Set completion and modification
 * - Workout completion flow
 * - Background persistence integration
 * 
 * @param onNavigateBack Callback for back navigation
 * @param onAddExercise Callback for exercise addition
 * @param modifier Modifier for styling
 * @param workoutViewModel ViewModel for workout session management
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    onNavigateBack: () -> Unit,
    onAddExercise: () -> Unit = {},
    navController: NavHostController,
    modifier: Modifier = Modifier,
    workoutViewModel: ActiveWorkoutViewModel = hiltViewModel()
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val workoutState by workoutViewModel.uiState.collectAsStateWithLifecycle()
    
    // State for minimized timer
    var isTimerExpanded by remember { mutableStateOf(false) }
    
    var showSaveWorkoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (workoutState.currentWorkout != null) {
                            workoutState.currentWorkout!!.name
                        } else {
                            "Custom Workout"
                        },
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
                actions = {
                    // Save workout button (with options)
                    if (workoutState.currentWorkout != null && workoutState.currentWorkout!!.exercises.isNotEmpty()) {
                        IconButton(
                            onClick = { showSaveWorkoutDialog = true },
                            modifier = Modifier.semantics {
                                contentDescription = "Save workout with options"
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Done,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
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
            
            // Session Controls Section
            item {
                SessionControlsCard(
                    timerState = workoutState.timerState,
                    connectionState = when {
                        workoutState.connectionError != null -> 
                            TimerServiceManager.ConnectionState.Error(Exception(workoutState.connectionError))
                        workoutState.isTimerServiceConnected -> 
                            TimerServiceManager.ConnectionState.Connected
                        else -> TimerServiceManager.ConnectionState.Disconnected
                    },
                    onStartSession = { workoutViewModel.onEvent(ActiveWorkoutEvent.StartSession) },
                    onPauseTimer = { workoutViewModel.onEvent(ActiveWorkoutEvent.PauseSession) },
                    onResumeTimer = { workoutViewModel.onEvent(ActiveWorkoutEvent.ResumeSession) },
                    onStopTimer = { workoutViewModel.onEvent(ActiveWorkoutEvent.StopSession) }
                )
            }
            
            // Exercise Selection Section
            item {
                ExerciseSelectionCard(
                    onNavigateToExerciseSelection = onAddExercise
                )
            }
            
            // Exercises Section
            item {
                ExercisesCard(
                    workout = workoutState.currentWorkout,
                    onAddExercise = { /* No longer needed - using selector above */ },
                    onRemoveExercise = { exerciseIndex ->
                        workoutViewModel.onEvent(ActiveWorkoutEvent.RemoveExercise(exerciseIndex))
                    },
                    onAddSet = { exerciseIndex ->
                        workoutViewModel.onEvent(ActiveWorkoutEvent.AddSet(exerciseIndex))
                    },
                    onUpdateSet = { exerciseIndex, setIndex, set ->
                        workoutViewModel.onEvent(ActiveWorkoutEvent.UpdateSet(exerciseIndex, setIndex, set))
                    },
                    onRemoveSet = { exerciseIndex, setIndex ->
                        workoutViewModel.onEvent(ActiveWorkoutEvent.RemoveSet(exerciseIndex, setIndex))
                    }
                )
            }
            
            // Workout Summary Section
            if (workoutState.currentWorkout != null) {
                item {
                    WorkoutSummaryCard(
                        workout = workoutState.currentWorkout!!,
                        onCompleteWorkout = { showSaveWorkoutDialog = true }
                    )
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    
    // Loading indicator
    if (workoutState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
    
    // Save Workout Dialog
    SaveWorkoutDialog(
        session = workoutState.currentSession,
        isVisible = showSaveWorkoutDialog,
        onSaveAsWorkout = {
            workoutViewModel.onEvent(ActiveWorkoutEvent.SaveWorkout)
            showSaveWorkoutDialog = false
            onNavigateBack()
        },
        onSaveAsTemplate = { templateName, templateDescription ->
            workoutViewModel.onEvent(ActiveWorkoutEvent.SaveAsTemplate(templateName, templateDescription))
            showSaveWorkoutDialog = false
            onNavigateBack()
        },
        onDismiss = { showSaveWorkoutDialog = false }
    )
    
    // Minimized Timer Component (persistent at bottom)
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        MinimizedTimerComponent(
            timerState = workoutState.timerState,
            connectionState = when {
                workoutState.connectionError != null -> 
                    TimerServiceManager.ConnectionState.Error(Exception(workoutState.connectionError))
                workoutState.isTimerServiceConnected -> 
                    TimerServiceManager.ConnectionState.Connected
                else -> TimerServiceManager.ConnectionState.Disconnected
            },
            isVisible = workoutState.currentSession != null,
            isExpanded = isTimerExpanded,
            onToggleExpanded = { isTimerExpanded = !isTimerExpanded },
            onNavigateToWorkout = { /* Already on workout screen */ },
            onPause = { workoutViewModel.onEvent(ActiveWorkoutEvent.PauseSession) },
            onResume = { workoutViewModel.onEvent(ActiveWorkoutEvent.ResumeSession) },
            onStop = { workoutViewModel.onEvent(ActiveWorkoutEvent.StopSession) }
        )
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
 * Enhanced exercises card with full exercise and set management
 */
@Composable
private fun ExercisesCard(
    workout: com.example.liftrix.domain.model.Workout?,
    onAddExercise: () -> Unit,
    onRemoveExercise: (Int) -> Unit,
    onAddSet: (Int) -> Unit,
    onUpdateSet: (Int, Int, ExerciseSet) -> Unit,
    onRemoveSet: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Exercises",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
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
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (workout?.exercises?.isEmpty() != false) {
                Text(
                    text = "No exercises added yet. Tap 'Add Exercise' to get started!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                workout.exercises.forEachIndexed { exerciseIndex, exercise ->
                    ExerciseItem(
                        exercise = exercise,
                        exerciseIndex = exerciseIndex,
                        onRemoveExercise = onRemoveExercise,
                        onAddSet = onAddSet,
                        onUpdateSet = onUpdateSet,
                        onRemoveSet = onRemoveSet
                    )
                    
                    if (exerciseIndex < workout.exercises.size - 1) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * Individual exercise item with set management
 */
@Composable
private fun ExerciseItem(
    exercise: Exercise,
    exerciseIndex: Int,
    onRemoveExercise: (Int) -> Unit,
    onAddSet: (Int) -> Unit,
    onUpdateSet: (Int, Int, ExerciseSet) -> Unit,
    onRemoveSet: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Exercise header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = exercise.libraryExercise.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(
                    onClick = { onRemoveExercise(exerciseIndex) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove exercise",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Sets
            exercise.sets.forEachIndexed { setIndex, set ->
                SetItem(
                    set = set,
                    setIndex = setIndex,
                    onUpdateSet = { updatedSet ->
                        onUpdateSet(exerciseIndex, setIndex, updatedSet)
                    },
                    onRemoveSet = { onRemoveSet(exerciseIndex, setIndex) }
                )
                
                if (setIndex < exercise.sets.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Add set button
            OutlinedButton(
                onClick = { onAddSet(exerciseIndex) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Set")
            }
        }
    }
}

/**
 * Individual set item with weight/reps input
 */
@Composable
private fun SetItem(
    set: ExerciseSet,
    setIndex: Int,
    onUpdateSet: (ExerciseSet) -> Unit,
    onRemoveSet: () -> Unit,
    modifier: Modifier = Modifier
) {
    var weight by remember { mutableStateOf(set.weight?.kilograms?.toString() ?: "") }
    var reps by remember { mutableStateOf(set.reps?.count?.toString() ?: "") }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${setIndex + 1}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(24.dp)
            )
            
            OutlinedTextField(
                value = weight,
                onValueChange = { 
                    weight = it
                    val weightValue = it.toDoubleOrNull()
                    if (weightValue != null) {
                        onUpdateSet(set.copy(weight = Weight(weightValue)))
                    }
                },
                label = { Text("Weight") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            
            OutlinedTextField(
                value = reps,
                onValueChange = { 
                    reps = it
                    val repsValue = it.toIntOrNull()
                    if (repsValue != null) {
                        onUpdateSet(set.copy(reps = Reps(repsValue)))
                    }
                },
                label = { Text("Reps") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            
            IconButton(
                onClick = { 
                    val updatedSet = set.copy(
                        completedAt = if (set.completedAt == null) java.time.Instant.now() else null
                    )
                    onUpdateSet(updatedSet)
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = if (set.completedAt != null) "Mark incomplete" else "Mark complete",
                    tint = if (set.completedAt != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            IconButton(
                onClick = onRemoveSet,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove set",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Workout summary card with completion option
 */
@Composable
private fun WorkoutSummaryCard(
    workout: com.example.liftrix.domain.model.Workout,
    onCompleteWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Workout Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem(
                    label = "Exercises",
                    value = workout.exercises.size.toString()
                )
                SummaryItem(
                    label = "Sets",
                    value = "${workout.getCompletedSets()}/${workout.getTotalSets()}"
                )
                SummaryItem(
                    label = "Volume",
                    value = "${String.format("%.1f", workout.calculateTotalVolume().kilograms)} kg"
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            FilledTonalButton(
                onClick = onCompleteWorkout,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Complete and save workout"
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Done,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Complete Workout")
            }
        }
    }
}

/**
 * Summary item for workout stats
 */
@Composable
private fun SummaryItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                text = "Enhanced ActiveWorkoutScreen Preview\n(Requires timer and workout services)",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/**
 * Card for exercise selection using navigation to ExerciseSelectionScreen
 */
@Composable
private fun ExerciseSelectionCard(
    onNavigateToExerciseSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Add Exercise",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Button(
                onClick = onNavigateToExerciseSelection,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Exercise"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Exercise")
            }
        }
    }
}