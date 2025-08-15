package com.example.liftrix.ui.workout.active

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.example.liftrix.domain.model.SessionExercise
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.workout.components.*
import com.example.liftrix.ui.workout.components.SaveQuickWorkoutAsTemplateDialog
import kotlinx.coroutines.delay
import timber.log.Timber

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
    onNavigateBack: () -> Unit,
    onNavigateToExerciseLibrary: (() -> Unit)? = null,
    onNavigateToPostCreation: ((String) -> Unit)? = null,
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
            .background(LiftrixColorsV2.Dark.BackgroundPrimary)
    ) {
        when (uiState) {
            is UnifiedActiveWorkoutUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = LiftrixColorsV2.Teal)
                }
            }
            
            is UnifiedActiveWorkoutUiState.Success -> {
                ActiveWorkoutContent(
                    uiState = uiState as UnifiedActiveWorkoutUiState.Success,
                    viewModel = viewModel,
                    onNavigateBack = onNavigateBack,
                    onNavigateToExerciseLibrary = onNavigateToExerciseLibrary
                )
            }
            
            is UnifiedActiveWorkoutUiState.NoSession -> {
                NoSessionContent(onNavigateBack = onNavigateBack)
            }
            
            is UnifiedActiveWorkoutUiState.WorkoutCompleted -> {
                WorkoutCompletedContent(
                    workoutId = (uiState as UnifiedActiveWorkoutUiState.WorkoutCompleted).workoutId,
                    onNavigateBack = onNavigateBack,
                    onNavigateToPostCreation = onNavigateToPostCreation ?: { _ -> 
                        // Fallback if navigation not provided
                    }
                )
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
    onNavigateBack: () -> Unit,
    onNavigateToExerciseLibrary: (() -> Unit)?
) {
    val session = uiState.session
    val exerciseMenuStates = remember { mutableStateMapOf<String, Boolean>() }
    val exerciseNotes = remember { mutableStateMapOf<String, String>() }
    var showReorderDialog by remember { mutableStateOf(false) }
    
    // Timer state
    var elapsedTime by remember { mutableStateOf(session.elapsedTimeSeconds) }
    LaunchedEffect(session.sessionStatus) {
        while (session.sessionStatus == com.example.liftrix.domain.model.UnifiedWorkoutSession.SessionStatus.ACTIVE) {
            delay(1000)
            elapsedTime++
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Workout name and timer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(LiftrixColorsV2.Dark.BackgroundPrimary)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = session.name,
                style = TextStyle(
                    color = LiftrixColorsV2.Dark.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            
            // Timer
            Text(
                text = formatTime(elapsedTime.toInt()),
                style = TextStyle(
                    color = LiftrixColorsV2.Teal,
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
                containerColor = LiftrixColorsV2.Teal.copy(alpha = 0.1f)
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
                            color = LiftrixColorsV2.Dark.TextTertiary,
                            fontSize = 12.sp
                        )
                    )
                    Text(
                        text = "${session.exercises.size}",
                        style = TextStyle(
                            color = LiftrixColorsV2.Dark.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Sets",
                        style = TextStyle(
                            color = LiftrixColorsV2.Dark.TextTertiary,
                            fontSize = 12.sp
                        )
                    )
                    Text(
                        text = "${session.exercises.sumOf { it.sets.size }}",
                        style = TextStyle(
                            color = LiftrixColorsV2.Dark.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Volume",
                        style = TextStyle(
                            color = LiftrixColorsV2.Dark.TextTertiary,
                            fontSize = 12.sp
                        )
                    )
                    Text(
                        text = "${calculateTotalVolume(session.exercises)} kg",
                        style = TextStyle(
                            color = LiftrixColorsV2.Dark.TextPrimary,
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
                        .background(LiftrixColorsV2.Teal, shape = androidx.compose.foundation.shape.CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Active",
                    style = TextStyle(
                        color = LiftrixColorsV2.Teal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
        
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
                    exerciseSubtitle = null,
                    sets = exercise.sets.map { set ->
                        RedesignedSetData(
                            weight = set.actualWeight?.kilograms?.toString() 
                                ?: set.targetWeight?.kilograms?.toString() ?: "",
                            reps = set.actualReps?.toString() 
                                ?: set.targetReps?.toString() ?: "",
                            previousValue = buildString {
                                val prevWeight = set.targetWeight?.kilograms ?: 0
                                val prevReps = set.targetReps ?: 0
                                append("$prevWeight x $prevReps")
                            },
                            isCompleted = set.completedAt != null
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
                                    com.example.liftrix.domain.model.Weight.fromKilograms(it)
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
                    onMenuClick = { showMenu = true },
                    onNotesClick = { showNotesDialog = true },
                    context = ExerciseCardContext.ACTIVE_WORKOUT
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
                                color = LiftrixColorsV2.Dark.TextPrimary
                            )
                        },
                        text = {
                            OutlinedTextField(
                                value = notesText,
                                onValueChange = { notesText = it },
                                label = { Text("Notes") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = LiftrixColorsV2.Dark.TextPrimary,
                                    unfocusedTextColor = LiftrixColorsV2.Dark.TextPrimary,
                                    focusedBorderColor = LiftrixColorsV2.Teal,
                                    unfocusedBorderColor = LiftrixColorsV2.Dark.Outline
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
                                Text("Save", color = LiftrixColorsV2.Teal)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showNotesDialog = false }) {
                                Text("Cancel", color = LiftrixColorsV2.Dark.TextSecondary)
                            }
                        },
                        containerColor = LiftrixColorsV2.Dark.BackgroundSecondary
                    )
                }
            }
            
            // Add Exercise Button
            item {
                OutlinedButton(
                    onClick = { onNavigateToExerciseLibrary?.invoke() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = LiftrixColorsV2.Teal
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(LiftrixColorsV2.Teal)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
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
                .background(LiftrixColorsV2.Dark.BackgroundPrimary)
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
            ExerciseReorderDialog(
                exercises = session.exercises.map { exercise ->
                    exercise.exerciseId.value to exercise.name
                },
                onDismiss = { showReorderDialog = false },
                onConfirmReorder = { reorderedIds ->
                    // Reorder functionality handled by ViewModel layer
                    // For now, just close the dialog
                    showReorderDialog = false
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
                color = LiftrixColorsV2.Dark.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Start a new workout from the main screen",
            style = TextStyle(
                color = LiftrixColorsV2.Dark.TextSecondary,
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
                color = LiftrixColorsV2.Teal,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Great job on finishing your session",
            style = TextStyle(
                color = LiftrixColorsV2.Dark.TextSecondary,
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
                contentColor = LiftrixColorsV2.Teal
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, LiftrixColorsV2.Teal)
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
                color = LiftrixColorsV2.Dark.Error,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error,
            style = TextStyle(
                color = LiftrixColorsV2.Dark.TextSecondary,
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
                    contentColor = LiftrixColorsV2.Dark.TextSecondary
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
            val weight = set.actualWeight?.kilograms?.toInt() ?: 0
            val reps = set.actualReps ?: 0
            weight * reps
        }
    }
}