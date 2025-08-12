package com.example.liftrix.ui.workout.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.ui.common.state.EditWorkoutUiState
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.workout.components.*
import timber.log.Timber
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Redesigned Edit Workout Screen matching reference UI
 */
@Composable
fun RedesignedEditWorkoutScreen(
    workoutId: WorkoutId,
    onNavigateBack: () -> Unit,
    onNavigateToExerciseSelection: () -> Unit = {},
    onNavigateToExerciseSelectionWithReplacement: (Int) -> Unit = {},
    viewModel: EditWorkoutViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Load workout data
    LaunchedEffect(workoutId) {
        viewModel.loadWorkout(workoutId)
    }
    
    // Handle navigation events
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is EditWorkoutEvent.NavigateBack -> onNavigateBack()
                else -> {}
            }
        }
    }
    
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LiftrixColorsV2.Dark.BackgroundPrimary)
    ) {
        when (val state = uiState) {
            is EditWorkoutUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = LiftrixColorsV2.Teal)
                }
            }
            
            is EditWorkoutUiState.Success -> {
                EditWorkoutContent(
                    data = state.data,
                    viewModel = viewModel,
                    onNavigateBack = onNavigateBack,
                    onNavigateToExerciseSelection = onNavigateToExerciseSelection,
                    onNavigateToExerciseSelectionWithReplacement = onNavigateToExerciseSelectionWithReplacement
                )
            }
            
            is EditWorkoutUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Error loading workout",
                        style = TextStyle(
                            color = LiftrixColorsV2.Dark.Error,
                            fontSize = 16.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    RedesignedPrimaryButton(
                        text = "Retry",
                        onClick = { viewModel.loadWorkout(workoutId) }
                    )
                }
            }
            
            else -> {}
        }
    }
}

@Composable
private fun EditWorkoutContent(
    data: com.example.liftrix.ui.common.state.EditWorkoutData,
    viewModel: EditWorkoutViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToExerciseSelection: () -> Unit,
    onNavigateToExerciseSelectionWithReplacement: (Int) -> Unit
) {
    var workoutName by remember(data) { mutableStateOf(data.editedName) }
    var workoutDescription by remember(data) { mutableStateOf(data.editedDescription) }
    
    // Track exercise states
    val exerciseMenuStates = remember { mutableStateMapOf<String, Boolean>() }
    val exerciseNotes = remember { mutableStateMapOf<String, String>() }
    var showReorderDialog by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        
        // Icon indicator for edit mode
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(LiftrixColorsV2.Dark.BackgroundPrimary)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Editing mode",
                tint = LiftrixColorsV2.Teal,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Editing mode",
                style = TextStyle(
                    color = LiftrixColorsV2.Teal,
                    fontSize = 12.sp
                )
            )
        }
        
        // Content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Workout Details Section
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Text(
                        text = "Workout Details",
                        style = TextStyle(
                            color = LiftrixColorsV2.Dark.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    
                    data.lastModified?.let { lastModified ->
                        Text(
                            text = "Last modified ${
                                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                                    .format(lastModified.atZone(java.time.ZoneId.systemDefault()))
                            }",
                            style = TextStyle(
                                color = LiftrixColorsV2.Dark.TextTertiary,
                                fontSize = 12.sp
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Workout Name
                    Text(
                        text = "Workout Name *",
                        style = TextStyle(
                            color = LiftrixColorsV2.Dark.TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    
                    BasicTextField(
                        value = workoutName,
                        onValueChange = { 
                            workoutName = it
                            viewModel.updateName(it)
                        },
                        textStyle = TextStyle(
                            color = LiftrixColorsV2.Dark.TextPrimary,
                            fontSize = 16.sp
                        ),
                        cursorBrush = SolidColor(LiftrixColorsV2.Teal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                LiftrixColorsV2.Dark.BackgroundSecondary,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(16.dp),
                        decorationBox = { innerTextField ->
                            Box {
                                if (workoutName.isEmpty()) {
                                    Text(
                                        text = "Enter workout name",
                                        style = TextStyle(
                                            color = LiftrixColorsV2.Dark.TextTertiary,
                                            fontSize = 16.sp
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    
                    // Description
                    Text(
                        text = "Description (optional)",
                        style = TextStyle(
                            color = LiftrixColorsV2.Dark.TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    
                    BasicTextField(
                        value = workoutDescription,
                        onValueChange = { 
                            workoutDescription = it
                            viewModel.updateDescription(it)
                        },
                        textStyle = TextStyle(
                            color = LiftrixColorsV2.Dark.TextPrimary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        ),
                        cursorBrush = SolidColor(LiftrixColorsV2.Teal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(
                                LiftrixColorsV2.Dark.BackgroundSecondary,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(16.dp),
                        decorationBox = { innerTextField ->
                            Box {
                                if (workoutDescription.isEmpty()) {
                                    Text(
                                        text = "Add workout description",
                                        style = TextStyle(
                                            color = LiftrixColorsV2.Dark.TextTertiary,
                                            fontSize = 14.sp
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }
            
            // Exercises Section
            itemsIndexed(
                items = data.editedExercises,
                key = { _, exercise -> exercise.id.value }
            ) { index, exercise ->
                var showMenu by remember { mutableStateOf(false) }
                var showNotesDialog by remember { mutableStateOf(false) }
                
                RedesignedExerciseCard(
                    exerciseName = exercise.libraryExercise.name,
                    exerciseSubtitle = exercise.libraryExercise.primaryMuscleGroup.name,
                    sets = exercise.sets.map { set ->
                        RedesignedSetData(
                            weight = set.weight?.kilograms?.toString() ?: "",
                            reps = set.reps?.count?.toString() ?: "",
                            previousValue = "${set.weight?.kilograms ?: 0} x ${set.reps?.count ?: 0}",
                            isCompleted = set.completedAt != null
                        )
                    },
                    onAddSet = {
                        // Add a new set to the exercise
                        val newSet = com.example.liftrix.domain.model.ExerciseSet(
                            id = com.example.liftrix.domain.model.ExerciseSetId(java.util.UUID.randomUUID().toString()),
                            setNumber = exercise.sets.size + 1,
                            reps = null,
                            weight = null,
                            completedAt = null
                        )
                        val updatedExercise = exercise.copy(
                            sets = exercise.sets + newSet
                        )
                        viewModel.updateExercise(index, updatedExercise)
                    },
                    onUpdateSet = { setIndex, setData ->
                        val updatedSets = exercise.sets.toMutableList()
                        if (setIndex < updatedSets.size) {
                            updatedSets[setIndex] = updatedSets[setIndex].copy(
                                weight = setData.weight.toDoubleOrNull()?.let {
                                    com.example.liftrix.domain.model.Weight.fromKilograms(it)
                                },
                                reps = setData.reps.toIntOrNull()?.let { com.example.liftrix.domain.model.Reps(it) },
                                completedAt = if (setData.isCompleted) java.time.Instant.now() else null
                            )
                            val updatedExercise = exercise.copy(sets = updatedSets)
                            viewModel.updateExercise(index, updatedExercise)
                        }
                    },
                    onMenuClick = { showMenu = true },
                    onNotesClick = { showNotesDialog = true }
                )
                
                // Exercise options menu
                ExerciseOptionsMenu(
                    expanded = showMenu,
                    onDismiss = { showMenu = false },
                    onReorder = {
                        showReorderDialog = true
                    },
                    onChangeExercise = {
                        // Navigate to exercise selection with replacement context
                        onNavigateToExerciseSelectionWithReplacement(index)
                    },
                    onRemove = {
                        viewModel.removeExercise(index)
                    }
                )
                
                // Notes dialog
                if (showNotesDialog) {
                    AlertDialog(
                        onDismissRequest = { showNotesDialog = false },
                        title = {
                            Text(
                                "Exercise Notes",
                                color = LiftrixColorsV2.Dark.TextPrimary
                            )
                        },
                        text = {
                            var notes by remember { 
                                mutableStateOf(exerciseNotes[exercise.id.value] ?: "")
                            }
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
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
                                    exerciseNotes[exercise.id.value] = 
                                        exerciseNotes[exercise.id.value] ?: ""
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
                    onClick = onNavigateToExerciseSelection,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = LiftrixColorsV2.Teal
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = SolidColor(LiftrixColorsV2.Teal)
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
        
        // Save Changes Button (Fixed at bottom)
        if (data.hasChanges) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LiftrixColorsV2.Dark.BackgroundPrimary)
                    .padding(16.dp)
            ) {
                RedesignedPrimaryButton(
                    text = "Save Changes",
                    onClick = {
                        viewModel.saveChanges()
                    },
                    enabled = workoutName.isNotBlank()
                )
            }
        }
        
        // Reorder dialog
        if (showReorderDialog) {
            ExerciseReorderDialog(
                exercises = data.editedExercises.map { exercise ->
                    exercise.id.value to exercise.libraryExercise.name
                },
                onDismiss = { showReorderDialog = false },
                onConfirmReorder = { reorderedIds ->
                    val exerciseIds = reorderedIds.map { id ->
                        com.example.liftrix.domain.model.ExerciseId(id)
                    }
                    viewModel.reorderExercises(exerciseIds)
                }
            )
        }
    }
}