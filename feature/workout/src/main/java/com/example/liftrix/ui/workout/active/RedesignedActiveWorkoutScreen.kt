package com.example.liftrix.ui.workout.active

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.domain.model.SessionExercise
import com.example.liftrix.domain.service.WeightUnitManager
import com.example.liftrix.feature.workout.R
import com.example.liftrix.feature.workout.ui.rememberWeightUnitManager
import androidx.compose.material3.MaterialTheme
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.components.animations.CompletionFeedback
import com.example.liftrix.ui.components.animations.CompletionFeedbackType
import com.example.liftrix.ui.workout.active.components.RestTimerDisplay
import com.example.liftrix.ui.workout.active.components.getRestTimeFromState
import com.example.liftrix.ui.workout.components.*
import com.example.liftrix.ui.workout.components.SaveQuickWorkoutAsTemplateDialog
import com.example.liftrix.ui.workout.components.ExerciseCardContext
import com.example.liftrix.ui.workout.plate.PlateCalculatorBottomSheet
import kotlinx.coroutines.delay
import timber.log.Timber
import java.util.Locale

/**
 * Redesigned Active Workout Screen matching reference UI
 * Key differences from template/edit screens:
 * - Shows duration timer
 * - Shows workout progress
 * - No workout name in header
 * - Complete workout button at bottom
 */
@Composable
fun RedesignedActiveWorkoutScreen(
    viewModel: UnifiedActiveWorkoutViewModel = hiltViewModel(),
    navController: NavController,
    onNavigateBack: () -> Unit,
    onNavigateToExerciseLibrary: (() -> Unit)? = null,
    onNavigateToPostCreation: ((String) -> Unit)? = null,
    onNavigateToPostWorkoutSummary: ((String) -> Unit)? = null,
    isBlankWorkout: Boolean = false,
    templateId: String? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentSession by viewModel.currentSession.collectAsStateWithLifecycle()
    
    // Initialize session on first composition based on parameters
    LaunchedEffect(Unit) {
        
        // Ensure session exists before processing
        if (currentSession == null) {
            when {
                isBlankWorkout -> {
                    viewModel.startBlankWorkout()
                }
                templateId != null -> {
                    viewModel.startTemplateWorkout(templateId)
                }
                else -> {
                    // Don't navigate back immediately - the ViewModel will set NoSession state
                }
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (uiState) {
            is UnifiedActiveWorkoutUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            
            is UnifiedActiveWorkoutUiState.Success -> {
                ActiveWorkoutContent(
                    uiState = uiState as UnifiedActiveWorkoutUiState.Success,
                    viewModel = viewModel,
                    navController = navController,
                    onNavigateBack = onNavigateBack,
                    onNavigateToExerciseLibrary = onNavigateToExerciseLibrary
                )
            }
            
            is UnifiedActiveWorkoutUiState.NoSession -> {
                NoSessionContent(onNavigateBack = onNavigateBack)
            }
            
            is UnifiedActiveWorkoutUiState.WorkoutCompleted -> {
                val workoutId = (uiState as UnifiedActiveWorkoutUiState.WorkoutCompleted).workoutId
                
                // Navigate to the enhanced post-workout summary screen if available
                if (onNavigateToPostWorkoutSummary != null) {
                    LaunchedEffect(workoutId) {
                        onNavigateToPostWorkoutSummary(workoutId)
                        // Clear the saved workout ID after navigation
                        viewModel.clearSavedWorkoutId()
                    }
                } else {
                    // Fallback to simple completion screen
                    WorkoutCompletedContent(
                        workoutId = workoutId,
                        onNavigateBack = onNavigateBack,
                        onNavigateToPostCreation = onNavigateToPostCreation ?: { _ -> 
                            // Fallback if navigation not provided
                        }
                    )
                }
            }
            
            is UnifiedActiveWorkoutUiState.Error -> {
                ErrorContent(
                    error = (uiState as UnifiedActiveWorkoutUiState.Error).message,
                    onRetry = viewModel::retry,
                    onNavigateBack = onNavigateBack
                )
            }
        }
    }
}

@Composable
private fun ActiveWorkoutContent(
    uiState: UnifiedActiveWorkoutUiState.Success,
    viewModel: UnifiedActiveWorkoutViewModel,
    navController: NavController,
    onNavigateBack: () -> Unit,
    onNavigateToExerciseLibrary: (() -> Unit)?
) {
    val session = uiState.session
    val weightUnitManager = rememberWeightUnitManager()
    val observedWeightUnit = weightUnitManager?.currentUnit?.collectAsStateWithLifecycle()
    val currentWeightUnit = observedWeightUnit?.value ?: WeightUnit.KILOGRAMS
    val exerciseMenuStates = remember { mutableStateMapOf<String, Boolean>() }
    val exerciseNotes = remember { mutableStateMapOf<String, String>() }
    var showReorderDialog by remember { mutableStateOf(false) }
    val timerState by viewModel.timerState.collectAsStateWithLifecycle()
    var lastRestCompletionSignal by remember { mutableStateOf<Long?>(null) }
    var showRestCompleteFeedback by remember { mutableStateOf(false) }
    var showPlateCalculator by remember { mutableStateOf(false) }
    
    // Anomaly detection states
    var showAnomalyDialog by remember { mutableStateOf(false) }
    var anomalyDetails by remember { mutableStateOf<Triple<String, String, String?>?>(null) } // value, type, previousValue
    var anomalyExerciseName by remember { mutableStateOf("") }
    var anomalySetIndex by remember { mutableStateOf(0) }
    var anomalyExerciseId by remember { mutableStateOf("") }
    
    // Timer state
    var elapsedTime by remember { mutableStateOf(session.elapsedTimeSeconds) }
    LaunchedEffect(session.sessionStatus) {
        while (session.sessionStatus == com.example.liftrix.domain.model.UnifiedWorkoutSession.SessionStatus.ACTIVE) {
            delay(1000)
            elapsedTime++
        }
    }

    LaunchedEffect(timerState.restCompletionSignal) {
        val completionSignal = timerState.restCompletionSignal
        if (completionSignal != null && completionSignal != lastRestCompletionSignal) {
            lastRestCompletionSignal = completionSignal
            showRestCompleteFeedback = true
            delay(1200)
            showRestCompleteFeedback = false
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Workout name and timer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = session.name,
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = { showPlateCalculator = true },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = "Open plate calculator",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            
            // Timer
            Text(
                text = formatTime(elapsedTime.toInt()),
                style = TextStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
        
        // Progress card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Exercises",
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    )
                    Text(
                        text = "${session.exercises.size}",
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Sets",
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    )
                    Text(
                        text = "${session.exercises.sumOf { it.sets.size }}",
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Volume",
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    )
                    Text(
                        text = weightUnitManager?.formatWeightCompact(
                            calculateTotalVolume(session.exercises).toDouble(),
                            WeightUnit.KILOGRAMS
                        ) ?: "${calculateTotalVolume(session.exercises)} kg",
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
            
            // Active indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, shape = androidx.compose.foundation.shape.CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Active",
                    style = TextStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }

        CompletionFeedback(
            completed = showRestCompleteFeedback,
            feedbackType = CompletionFeedbackType.SET_COMPLETE,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            AnimatedVisibility(visible = showRestCompleteFeedback) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Rest complete",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        RestTimerDisplay(
            timerState = timerState,
            formattedTime = getRestTimeFromState(timerState),
            onPause = viewModel::pauseRestTimer,
            onResume = viewModel::resumeRestTimer,
            onSkip = viewModel::skipRestTimer,
            onAddTime = { viewModel.adjustRestTimerBy(15) },
            onSubtractTime = { viewModel.adjustRestTimerBy(-15) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        // Exercises list
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(
                items = session.exercises,
                key = { _, exercise -> exercise.exerciseId.value }
            ) { index, exercise ->
                var showMenu by remember { mutableStateOf(false) }
                var showNotesDialog by remember { mutableStateOf(false) }
                
                RedesignedExerciseCard(
                    exerciseName = exercise.name,
                    exerciseSubtitle = exercise.primaryMuscle.name,
                    leadingIconResId = muscleGroupIconResId(exercise.primaryMuscle),
                    leadingIconContentDescription = muscleGroupIconContentDescription(exercise.primaryMuscle),
                    sets = exercise.sets.mapIndexed { setIndex, set ->
                        RedesignedSetData(
                            weight = set.actualWeight?.kilograms
                                ?.let { formatWeightInputValue(it, currentWeightUnit, weightUnitManager) }
                                ?: set.targetWeight?.kilograms
                                    ?.let { formatWeightInputValue(it, currentWeightUnit, weightUnitManager) }
                                ?: "",
                            reps = set.actualReps?.toString() 
                                ?: set.targetReps?.toString() ?: "",
                            previousValue = viewModel.getPreviousValueForSet(
                                exerciseId = exercise.exerciseId.value,
                                setNumber = setIndex + 1
                            )?.let { previousValue ->
                                weightUnitManager?.formatWeightText(previousValue) ?: previousValue
                            },
                            isCompleted = set.completedAt != null,
                            setId = "${exercise.exerciseId.value}_${setIndex}",
                            hasWeightAnomaly = false,
                            hasRepsAnomaly = false
                        )
                    },
                    onAddSet = {
                        // Fix: Use addSetToExercise instead of updateSetInExercise
                        // The ViewModel will properly create and add the new set
                        viewModel.addSetToExercise(exercise.exerciseId.value)
                    },
                    onUpdateSet = { setIndex, setData ->
                        if (setIndex < exercise.sets.size) {
                            val updatedSet = exercise.sets[setIndex].copy(
                                actualWeight = setData.weight.toDoubleOrNull()?.let {
                                    Weight.fromValue(it, currentWeightUnit)
                                },
                                actualReps = setData.reps.toIntOrNull(),
                                completedAt = if (setData.isCompleted) {
                                    java.time.Instant.now()
                                } else null
                            )
                            viewModel.updateSetInExercise(
                                exercise.exerciseId.value,
                                setIndex + 1,
                                updatedSet
                            )
                        }
                    },
                    onRemoveSet = { setIndex ->
                        viewModel.removeSetFromExercise(exercise.exerciseId.value, setIndex + 1)
                    },
                    onMenuClick = { showMenu = true },
                    onNotesClick = { showNotesDialog = true },
                    context = ExerciseCardContext.ACTIVE_WORKOUT,
                    weightUnit = currentWeightUnit,
                    onAnomalyDetected = { value, setIdx ->
                        // Prepare anomaly dialog data
                        anomalyDetails = Triple(
                            value,
                            if (value.toDoubleOrNull() != null) "weight" else "reps",
                            exercise.sets.getOrNull(setIdx)?.let { set ->
                                buildString {
                                    val prevWeight = set.targetWeight?.kilograms ?: 0
                                    val displayPrevWeight = weightUnitManager?.formatWeightCompact(
                                        prevWeight.toDouble(),
                                        WeightUnit.KILOGRAMS
                                    ) ?: "$prevWeight kg"
                                    val prevReps = set.targetReps ?: 0
                                    append("$displayPrevWeight x $prevReps")
                                }
                            }
                        )
                        anomalyExerciseName = exercise.name
                        anomalySetIndex = setIdx
                        anomalyExerciseId = exercise.exerciseId.value
                        showAnomalyDialog = true
                    }
                )
                
                // Exercise options menu
                ExerciseOptionsMenu(
                    expanded = showMenu,
                    onDismiss = { showMenu = false },
                    onReorder = {
                        showReorderDialog = true
                    },
                    onChangeExercise = {
                        // Navigate to exercise selection
                        onNavigateToExerciseLibrary?.invoke()
                    },
                    onRemove = {
                        viewModel.removeExercise(exercise.exerciseId.value)
                    }
                )
                
                // Notes dialog
                if (showNotesDialog) {
                    var notesText by remember { 
                        mutableStateOf(exerciseNotes[exercise.exerciseId.value] ?: "")
                    }
                    AlertDialog(
                        onDismissRequest = { showNotesDialog = false },
                        title = {
                            Text(
                                "Exercise Notes",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        text = {
                            OutlinedTextField(
                                value = notesText,
                                onValueChange = { notesText = it },
                                label = { Text("Notes") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    exerciseNotes[exercise.exerciseId.value] = notesText
                                    showNotesDialog = false
                                }
                            ) {
                                Text("Save", color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showNotesDialog = false }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
            
            // Add Exercise Button
            item {
                OutlinedButton(
                    onClick = { onNavigateToExerciseLibrary?.invoke() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add exercise",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add Exercise",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
            
            // Bottom padding
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
        
        // Complete Workout Button (Fixed at bottom)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            RedesignedPrimaryButton(
                text = if (uiState.isCompleting) "Completing..." else "Complete Workout",
                onClick = { viewModel.completeWorkout() },
                enabled = !uiState.isCompleting && session.exercises.isNotEmpty()
            )
        }
        
        // Reorder dialog
        if (showReorderDialog) {
            val exercisePairs = session.exercises.map { exercise ->
                exercise.exerciseId.value to exercise.name
            }
            ExerciseReorderDialog(
                exercises = exercisePairs,
                onDismiss = { showReorderDialog = false },
                onConfirmReorder = { reorderedIds ->
                    // Reorder functionality handled by ViewModel layer
                    // For now, just close the dialog
                    showReorderDialog = false
                }
            )
        }
        
        // Anomaly Detection Dialog
        if (showAnomalyDialog && anomalyDetails != null) {
            AnomalyNudgeDialog(
                anomalyValue = anomalyDetails!!.first,
                anomalyType = anomalyDetails!!.second,
                previousValue = anomalyDetails!!.third,
                exerciseName = anomalyExerciseName,
                onConfirm = {
                    // User confirms the value is correct
                    showAnomalyDialog = false
                    anomalyDetails = null
                },
                onCorrect = { correctedValue ->
                    val exercise = session.exercises.find { it.exerciseId.value == anomalyExerciseId }
                    if (exercise != null && anomalySetIndex < exercise.sets.size) {
                        val set = exercise.sets[anomalySetIndex]
                        val updatedSet = if (anomalyDetails!!.second == "weight") {
                            set.copy(
                                actualWeight = correctedValue.toDoubleOrNull()?.let {
                                    com.example.liftrix.domain.model.Weight.fromKilograms(it)
                                }
                            )
                        } else {
                            set.copy(actualReps = correctedValue.toIntOrNull())
                        }
                        viewModel.updateSetInExercise(
                            anomalyExerciseId,
                            anomalySetIndex + 1,
                            updatedSet
                        )
                    }
                    showAnomalyDialog = false
                    anomalyDetails = null
                },
                onDismiss = {
                    showAnomalyDialog = false
                    anomalyDetails = null
                }
            )
        }
        
        // Save Quick Workout Dialog - CRITICAL FIX
        if (uiState.showSaveQuickWorkoutDialog) {
            SaveQuickWorkoutAsTemplateDialog(
                show = true,
                defaultTemplateName = "${session.name} Template",
                onSaveAsTemplate = { templateName ->
                    viewModel.saveQuickWorkoutAsTemplate(templateName)
                },
                onSkip = {
                    viewModel.skipSaveQuickWorkoutAsTemplate()
                },
                onDismiss = {
                    viewModel.dismissSaveQuickWorkoutDialog()
                }
            )
        }

        PlateCalculatorBottomSheet(
            isVisible = showPlateCalculator,
            weightUnit = currentWeightUnit,
            onDismiss = { showPlateCalculator = false }
        )
    }
}

@Composable
private fun NoSessionContent(onNavigateBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No Active Session",
            style = TextStyle(
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Start a new workout from the main screen",
            style = TextStyle(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        )
        Spacer(modifier = Modifier.height(24.dp))
        RedesignedPrimaryButton(
            text = "Go Back",
            onClick = onNavigateBack,
            modifier = Modifier.fillMaxWidth(0.6f)
        )
    }
}

@Composable
private fun WorkoutCompletedContent(
    workoutId: String,
    onNavigateBack: () -> Unit,
    onNavigateToPostCreation: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Workout Completed!",
            style = TextStyle(
                color = MaterialTheme.colorScheme.primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Great job on finishing your session",
            style = TextStyle(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        // Share Workout Button
        RedesignedPrimaryButton(
            text = "Share Workout",
            onClick = { onNavigateToPostCreation(workoutId) },
            modifier = Modifier.fillMaxWidth(0.8f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Back to Home Button
        OutlinedButton(
            onClick = onNavigateBack,
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = "Back to Home",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Error",
            style = TextStyle(
                color = MaterialTheme.colorScheme.error,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error,
            style = TextStyle(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onNavigateBack,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("Go Back")
            }
            RedesignedPrimaryButton(
                text = "Retry",
                onClick = onRetry,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// Helper functions
private fun formatTime(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}

private fun calculateTotalVolume(exercises: List<SessionExercise>): Int {
    return exercises.sumOf { exercise ->
        exercise.sets.sumOf { set ->
            set.getVolume().toInt()
        }
    }
}

private fun muscleGroupIconResId(muscleGroup: ExerciseCategory): Int? {
    return when (muscleGroup) {
        ExerciseCategory.SHOULDERS -> R.drawable.muscle_shoulders
        ExerciseCategory.CHEST -> R.drawable.muscle_chest
        ExerciseCategory.BACK -> R.drawable.muscle_back
        ExerciseCategory.BICEPS -> R.drawable.muscle_biceps
        ExerciseCategory.TRICEPS -> R.drawable.muscle_triceps
        ExerciseCategory.LEGS,
        ExerciseCategory.QUADRICEPS -> R.drawable.muscle_legs
        ExerciseCategory.GLUTES -> R.drawable.muscle_glutes
        ExerciseCategory.ABS,
        ExerciseCategory.CORE -> R.drawable.muscle_core
        ExerciseCategory.CALVES -> R.drawable.muscle_calves
        else -> null
    }
}

private fun muscleGroupIconContentDescription(muscleGroup: ExerciseCategory): String {
    return when (muscleGroup) {
        ExerciseCategory.SHOULDERS -> "Shoulders muscle icon"
        ExerciseCategory.CHEST -> "Chest muscle icon"
        ExerciseCategory.BACK -> "Back muscle icon"
        ExerciseCategory.BICEPS -> "Biceps muscle icon"
        ExerciseCategory.TRICEPS -> "Triceps muscle icon"
        ExerciseCategory.LEGS,
        ExerciseCategory.QUADRICEPS -> "Legs muscle icon"
        ExerciseCategory.GLUTES -> "Glutes muscle icon"
        ExerciseCategory.ABS,
        ExerciseCategory.CORE -> "Core muscle icon"
        ExerciseCategory.CALVES -> "Calves muscle icon"
        else -> "Exercise"
    }
}

private fun formatWeightInputValue(
    kilograms: Double,
    targetUnit: WeightUnit,
    weightUnitManager: WeightUnitManager?
): String {
    val displayValue = weightUnitManager?.convertForDisplay(kilograms, WeightUnit.KILOGRAMS)
        ?: targetUnit.convertFromKilograms(kilograms)
    return when {
        displayValue == displayValue.toInt().toDouble() -> displayValue.toInt().toString()
        (displayValue * 10).toInt().toDouble() == displayValue * 10 -> {
            String.format(Locale.US, "%.1f", displayValue)
        }
        else -> String.format(Locale.US, "%.2f", displayValue)
    }
}
