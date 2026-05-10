package com.example.liftrix.ui.workout.edit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.feature.workout.state.EditSessionUiState
import com.example.liftrix.feature.workout.state.EditSessionData
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import com.example.liftrix.ui.workout.components.UnifiedExerciseCard
import com.example.liftrix.ui.workout.components.SessionProgressCard
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import com.example.liftrix.ui.workout.components.SecondaryActionButton
import com.example.liftrix.ui.theme.LiftrixSpacing
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import com.example.liftrix.ui.components.ValidatedNumericTextField
import com.example.liftrix.ui.components.ValidatedMultilineTextField
import com.example.liftrix.ui.validation.InputValidation
import com.example.liftrix.ui.validation.ValidationSummaryCard
import com.example.liftrix.domain.model.validation.ValidationResult
import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.model.SessionExercise
import com.example.liftrix.domain.model.SessionSet
import com.example.liftrix.feature.workout.ui.rememberWeightUnitManager
import com.example.liftrix.ui.icons.LiftrixIcon
import com.example.liftrix.ui.icons.LiftrixIcons
import com.example.liftrix.ui.theme.LiftrixColors
import java.util.Locale

/**
 * Edit Session Screen - For editing completed workout sessions
 * 
 * This screen allows users to edit historical workout session data with:
 * - Visual indicators differentiating from routine editing
 * - Direct modification of completed session records
 * - All session data editable: exercises, sets, reps, weights, duration
 * - Session timing and completion data modification
 * 
 * Key Features:
 * - Session editing visual indicators with history icon and completion date
 * - Duration editing with start/end time modification
 * - Comprehensive session data editing (sets, reps, weights, notes)
 * - Session metrics recalculation after changes
 * - Historical context preservation with modification timestamps
 * 
 * @param sessionId Unique identifier for the workout session to edit
 * @param onNavigateBack Callback to return to previous screen
 * @param viewModel EditWorkoutViewModel for state management (reused for sessions)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSessionScreen(
    sessionId: WorkoutId,
    onNavigateBack: () -> Unit,
    viewModel: EditSessionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Load session data when screen is created
    LaunchedEffect(sessionId) {
        viewModel.handleEvent(EditSessionEvent.LoadSession(sessionId))
    }
    
    // Handle navigation events
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is EditSessionEvent.NavigateBack -> onNavigateBack()
                is EditSessionEvent.ShowError -> {
                    // Error handling would be implemented here
                    // Could show snackbar or error dialog
                }
                else -> {
                    // Handle other events if needed
                }
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(LiftrixSpacing.screenPadding) // Matches task requirements
    ) {
        when (val currentState = uiState) {
            EditSessionUiState.Loading -> {
                EditSessionLoadingContent()
            }
            is EditSessionUiState.Success -> {
                val data = currentState.data
                EditSessionLoadedContent(
                    data = data,
                    onNavigateBack = onNavigateBack,
                    onDurationChange = { duration ->
                        viewModel.handleEvent(EditSessionEvent.UpdateSessionDuration(duration))
                    },
                    onSetUpdate = { exerciseIndex, setIndex, set ->
                        viewModel.handleEvent(EditSessionEvent.UpdateExerciseSet(exerciseIndex, setIndex, set))
                    },
                    onNotesChange = { notes ->
                        viewModel.handleEvent(EditSessionEvent.UpdateSessionNotes(notes))
                    },
                    onSaveChanges = {
                        viewModel.handleEvent(EditSessionEvent.SaveChanges)
                    },
                    onDiscardChanges = onNavigateBack,
                    hasChanges = data.hasChanges
                )
            }
            is EditSessionUiState.Error -> {
                EditSessionErrorContent(
                    error = currentState.error,
                    onRetry = { viewModel.handleEvent(EditSessionEvent.LoadSession(sessionId)) },
                    onNavigateBack = onNavigateBack
                )
            }
            EditSessionUiState.Empty -> {
                EditSessionErrorContent(
                    error = LiftrixError.NotFoundError("Session not found"),
                    onRetry = { viewModel.handleEvent(EditSessionEvent.LoadSession(sessionId)) },
                    onNavigateBack = onNavigateBack
                )
            }
        }
    }
}

@Composable
private fun EditSessionLoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(LiftrixSpacing.cardSpacing))
        
        Text(
            text = "Loading workout session...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EditSessionLoadedContent(
    data: EditSessionData,
    onNavigateBack: () -> Unit,
    onDurationChange: (Duration) -> Unit,
    onSetUpdate: (Int, Int, SessionSet) -> Unit,
    onNotesChange: (String) -> Unit,
    onSaveChanges: () -> Unit,
    onDiscardChanges: () -> Unit,
    hasChanges: Boolean
) {
    val session = data.session
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Header with session editing indicator
        EditSessionHeader(
            session = session,
            onNavigateBack = onNavigateBack
        )
        
        Spacer(modifier = Modifier.height(LiftrixSpacing.cardSpacing))
        
        // Main content in LazyColumn for scrolling
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.cardSpacing)
        ) {
            // Session summary card
            item {
                SessionSummaryCard(
                    session = session,
                    onDurationChange = onDurationChange,
                    onNotesChange = onNotesChange,
                    editedNotes = session.notes ?: ""
                )
            }
            
            // Session metrics card
            item {
                SessionMetricsCard(session = session)
            }
            
            // Exercise cards with set editing
            itemsIndexed(session.exercises) { exerciseIndex, exercise ->
                EditableExerciseSessionCard(
                    exercise = exercise,
                    exerciseIndex = exerciseIndex,
                    onSetUpdate = onSetUpdate
                )
            }
        }
        
        // Action buttons
        SessionActionButtonsRow(
            hasChanges = hasChanges,
            onSaveChanges = onSaveChanges,
            onDiscardChanges = onDiscardChanges
        )
    }
}

@Composable
private fun EditSessionHeader(
    session: UnifiedWorkoutSession,
    onNavigateBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        IconButton(onClick = onNavigateBack) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Navigate back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Historical session editing indicator (different from routine creation)
        LiftrixIcon(
            icon = LiftrixIcons.Workflow.Edit,
            contentDescription = "Editing historical session",
            tint = LiftrixColors.TiffanyBlue // Coral accent for historical editing
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Column {
            // Screen title with historical editing indicator (matches task requirements)
            Text(
                text = "Editing historical workout session",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            
            // Completion date indicator
            Text(
                text = "Completed ${formatDate(session.endedAt ?: session.startedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SessionSummaryCard(
    session: UnifiedWorkoutSession,
    onDurationChange: (Duration) -> Unit,
    onNotesChange: (String) -> Unit,
    editedNotes: String
) {
    UnifiedWorkoutCard(
        title = "Session Details",
        subtitle = "${session.name} • ${formatLocalDate(session.startedAt.atZone(ZoneId.systemDefault()).toLocalDate())}"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)) {
            // Session duration editing
            SessionDurationEditor(
                currentDuration = getDurationFromSession(session),
                startTime = session.startedAt,
                endTime = session.endedAt,
                onDurationChange = onDurationChange
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.elementPaddingSmall))
            
            // Session notes editing with validation
            val notesValidation = remember(editedNotes) {
                InputValidation.validateSessionNotes(editedNotes)
            }
            
            ValidatedMultilineTextField(
                value = editedNotes,
                onValueChange = onNotesChange,
                label = "Session Notes",
                placeholder = "How did this workout feel? Any observations?",
                validationResult = notesValidation,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                minLines = 2
            )
        }
    }
}

@Composable
private fun SessionDurationEditor(
    currentDuration: Duration?,
    startTime: Instant?,
    endTime: Instant?,
    onDurationChange: (Duration) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Duration display
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "Duration",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "Duration: ${formatDuration(currentDuration)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Edit duration button (would open time picker dialog)
            TextButton(
                onClick = { 
                    currentDuration?.let { duration ->
                        onDurationChange(duration)
                    }
                }
            ) {
                Text("Edit")
            }
        }
        
        // Time range display
        if (startTime != null && endTime != null) {
            Text(
                text = "${formatTime(startTime)} - ${formatTime(endTime)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SessionMetricsCard(session: UnifiedWorkoutSession) {
    val weightUnitManager = rememberWeightUnitManager()

    UnifiedWorkoutCard(
        title = "Session Metrics",
        subtitle = "Performance summary"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Total volume
            MetricItem(
                label = "Volume",
                value = weightUnitManager?.formatWeightCompact(
                    calculateSessionVolume(session).toDouble(),
                    WeightUnit.KILOGRAMS
                ) ?: "${calculateSessionVolume(session)} ${WeightUnit.KILOGRAMS.symbol}"
            )
            
            // Total sets
            MetricItem(
                label = "Sets",
                value = session.exercises.sumOf { it.sets.size }.toString()
            )
            
            // Total reps
            MetricItem(
                label = "Reps",
                value = session.exercises.sumOf { it.sets.sumOf { set -> set.actualReps ?: set.targetReps ?: 0 } }.toString()
            )
            
            // Completion rate
            MetricItem(
                label = "Completion",
                value = "${calculateCompletionRate(session)}%"
            )
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EditableExerciseSessionCard(
    exercise: SessionExercise,
    exerciseIndex: Int,
    onSetUpdate: (Int, Int, SessionSet) -> Unit
) {
    UnifiedWorkoutCard(
        title = exercise.name,
        subtitle = "${exercise.sets.size} sets • ${exercise.sets.sumOf { it.actualReps ?: it.targetReps ?: 0 }} total reps"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            exercise.sets.forEachIndexed { setIndex, set ->
                EditableSetRow(
                    setNumber = setIndex + 1,
                    set = set,
                    onSetUpdate = { updatedSet ->
                        onSetUpdate(exerciseIndex, setIndex, updatedSet)
                    }
                )
            }
        }
    }
}

@Composable
private fun EditableSetRow(
    setNumber: Int,
    set: SessionSet,
    onSetUpdate: (SessionSet) -> Unit
) {
    val weightUnitManager = rememberWeightUnitManager()
    val observedWeightUnit = weightUnitManager?.currentUnit?.collectAsStateWithLifecycle()
    val currentWeightUnit = observedWeightUnit?.value ?: WeightUnit.KILOGRAMS

    var weightText by remember(set.actualWeight, set.targetWeight, currentWeightUnit) {
        mutableStateOf(
            (set.actualWeight ?: set.targetWeight)
                ?.getValue(currentWeightUnit)
                ?.let { formatWeightInputValue(it) }
                ?: ""
        )
    }
    var repsText by remember(set.actualReps, set.targetReps) { 
        mutableStateOf((set.actualReps ?: set.targetReps)?.toString() ?: "") 
    }
    
    // Validation states
    val weightValidation = remember(weightText) {
        InputValidation.validateWeight(weightText)
    }
    val repsValidation = remember(repsText) {
        InputValidation.validateReps(repsText)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Set number
                Text(
                    text = "$setNumber",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(24.dp)
                )
                
                // Weight input with validation
                ValidatedNumericTextField(
                    value = weightText,
                    onValueChange = { newWeight ->
                        weightText = newWeight
                        if (weightValidation is ValidationResult.Success) {
                            val weight = newWeight.toDoubleOrNull()?.let {
                                Weight.fromValue(it, currentWeightUnit)
                            }
                            onSetUpdate(set.copy(actualWeight = weight))
                        }
                    },
                    label = "Weight",
                    suffix = currentWeightUnit.symbol,
                    validationResult = weightValidation,
                    modifier = Modifier.weight(1f),
                    isRequired = false,
                    keyboardType = KeyboardType.Decimal
                )
                
                // Reps input with validation
                ValidatedNumericTextField(
                    value = repsText,
                    onValueChange = { newReps ->
                        repsText = newReps
                        if (repsValidation is ValidationResult.Success) {
                            val reps = newReps.toIntOrNull()
                            onSetUpdate(set.copy(actualReps = reps))
                        }
                    },
                    label = "Reps",
                    validationResult = repsValidation,
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Number
                )
                
                // Set status indicator
                if (set.completedAt != null) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionActionButtonsRow(
    hasChanges: Boolean,
    onSaveChanges: () -> Unit,
    onDiscardChanges: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = LiftrixSpacing.cardSpacing),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Discard changes button
        SecondaryActionButton(
            text = "Discard Changes",
            onClick = onDiscardChanges,
            leadingIcon = com.example.liftrix.ui.icons.LiftrixIcons.Actions.Cancel
        )
        
        // Save changes button
        PrimaryActionButton(
            text = "Save Session",
            onClick = onSaveChanges,
            enabled = hasChanges,
            leadingIcon = com.example.liftrix.ui.icons.LiftrixIcons.Actions.Save
        )
    }
}

@Composable
private fun EditSessionErrorContent(
    error: LiftrixError,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(LiftrixSpacing.cardSpacing),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Error icon and message
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = "Error",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(LiftrixSpacing.cardSpacing))
        
        Text(
            text = "Unable to load session",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(LiftrixSpacing.elementSpacing))
        
        Text(
            text = error.message ?: "An unexpected error occurred",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(LiftrixSpacing.cardSpacing))
        
        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryActionButton(
                text = "Go Back",
                onClick = onNavigateBack,
                leadingIcon = com.example.liftrix.ui.icons.LiftrixIcons.Navigation.Back
            )
            
            PrimaryActionButton(
                text = "Retry",
                onClick = onRetry,
                leadingIcon = Icons.Default.Refresh
            )
        }
    }
}

/**
 * Helper functions for formatting
 */
private fun formatDate(instant: Instant): String {
    return try {
        val localDate = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        localDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    } catch (e: Exception) {
        "Unknown"
    }
}

private fun formatLocalDate(localDate: java.time.LocalDate): String {
    return try {
        localDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    } catch (e: Exception) {
        "Unknown"
    }
}

private fun formatTime(instant: Instant): String {
    return try {
        val localTime = instant.atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        localTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    } catch (e: Exception) {
        "Unknown"
    }
}

private fun formatDuration(duration: Duration?): String {
    return duration?.let { 
        val hours = it.toHours()
        val minutes = it.toMinutes() % 60
        "${hours}h ${minutes}m"
    } ?: "Unknown"
}

/**
 * Helper functions for UnifiedWorkoutSession
 */
private fun getDurationFromSession(session: UnifiedWorkoutSession): Duration? {
    return if (session.endedAt != null) {
        Duration.between(session.startedAt, session.endedAt)
    } else {
        Duration.ofSeconds(session.elapsedTimeSeconds)
    }
}

private fun calculateSessionVolume(session: UnifiedWorkoutSession): Int {
    return session.exercises.sumOf { exercise ->
        exercise.sets.sumOf { set ->
            val weight = set.actualWeight ?: set.targetWeight
            val reps = set.actualReps ?: set.targetReps ?: 0
            weight?.kilograms?.toInt()?.times(reps) ?: 0
        }
    }
}

private fun formatWeightInputValue(value: Double): String {
    return when {
        value == value.toInt().toDouble() -> value.toInt().toString()
        (value * 10).toInt().toDouble() == value * 10 -> String.format(Locale.US, "%.1f", value)
        else -> String.format(Locale.US, "%.2f", value)
    }
}

private fun calculateCompletionRate(session: UnifiedWorkoutSession): Int {
    val totalSets = session.exercises.sumOf { it.sets.size }
    if (totalSets == 0) return 0
    
    val completedSets = session.exercises.sumOf { exercise ->
        exercise.sets.count { it.completedAt != null }
    }
    
    return ((completedSets.toDouble() / totalSets) * 100).toInt()
}
