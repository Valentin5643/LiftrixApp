package com.example.liftrix.ui.workout.create

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import com.example.liftrix.ui.common.state.WorkoutTemplateCreationUiState
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.workout.components.*
import timber.log.Timber

/**
 * Redesigned Create Template Screen matching reference UI
 */
@Composable
fun RedesignedCreateTemplateScreen(
    onNavigateBack: () -> Unit,
    onNavigateToExerciseSelection: () -> Unit = {},
    editTemplateId: String? = null,
    viewModel: WorkoutTemplateCreationViewModel = hiltViewModel(),
    navBackStackEntry: NavBackStackEntry? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var templateName by remember { mutableStateOf("") }
    var templateDescription by remember { mutableStateOf("") }
    
    // Track exercise menu states
    val exerciseMenuStates = remember { mutableStateMapOf<String, Boolean>() }
    val exerciseNotes = remember { mutableStateMapOf<String, String>() }
    var showReorderDialog by remember { mutableStateOf(false) }
    
    // Handle selected exercise from navigation
    val savedStateHandle = navBackStackEntry?.savedStateHandle
    val selectedExercise = savedStateHandle?.get<com.example.liftrix.domain.model.ExerciseLibrary>("selected_exercise")
    
    Timber.d("🔥 REDESIGNED-TEMPLATE: selectedExercise from savedStateHandle: ${selectedExercise?.name} (ID: ${selectedExercise?.id})")
    
    LaunchedEffect(selectedExercise) {
        selectedExercise?.let { exercise ->
            Timber.d("🔥 REDESIGNED-TEMPLATE: Adding exercise: ${exercise.name} to template")
            viewModel.addExerciseFromLibrary(exercise)
            
            // Clear the saved state to prevent re-adding the same exercise
            savedStateHandle?.remove<com.example.liftrix.domain.model.ExerciseLibrary>("selected_exercise")
            Timber.d("🔥 REDESIGNED-TEMPLATE: selectedExercise cleared from savedStateHandle")
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LiftrixColorsV2.Dark.BackgroundPrimary)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Template Name and Description Section
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Template Name Label
                        Text(
                            text = "Workout Name",
                            style = TextStyle(
                                color = LiftrixColorsV2.Dark.TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        
                        // Template Name Input
                        BasicTextField(
                            value = templateName,
                            onValueChange = { templateName = it },
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
                                    if (templateName.isEmpty()) {
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
                        
                        // Description Label
                        Text(
                            text = "Description (Optional)",
                            style = TextStyle(
                                color = LiftrixColorsV2.Dark.TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        
                        // Description Input
                        BasicTextField(
                            value = templateDescription,
                            onValueChange = { templateDescription = it },
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
                                    if (templateDescription.isEmpty()) {
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
                when (val state = uiState) {
                    is WorkoutTemplateCreationUiState.Success -> {
                        val exercises = state.data.exercises
                        
                        itemsIndexed(
                            items = exercises,
                            key = { _, exercise -> exercise.exerciseId.value }
                        ) { index, exercise ->
                            var showMenu by remember { mutableStateOf(false) }
                            var showNotesDialog by remember { mutableStateOf(false) }
                            
                            RedesignedExerciseCard(
                                exerciseName = exercise.name,
                                exerciseSubtitle = exercise.primaryMuscle.name,
                                sets = List(exercise.targetSets ?: 2) { setIndex ->
                                    RedesignedSetData(
                                        weight = exercise.targetWeight?.kilograms?.toString() ?: "",
                                        reps = exercise.targetReps?.count?.toString() ?: "",
                                        previousValue = "0",
                                        isCompleted = false  // Always false for templates
                                    )
                                },
                                onAddSet = {
                                    val updatedExercise = exercise.copy(
                                        targetSets = (exercise.targetSets ?: 1) + 1
                                    )
                                    viewModel.updateExercise(updatedExercise)
                                },
                                onUpdateSet = { setIndex, setData ->
                                    // In template context, only update target values, ignore completion
                                    val updatedExercise = exercise.copy(
                                        targetWeight = setData.weight.toDoubleOrNull()?.let {
                                            com.example.liftrix.domain.model.Weight.fromKilograms(it)
                                        },
                                        targetReps = setData.reps.toIntOrNull()?.let {
                                            com.example.liftrix.domain.model.Reps(it)
                                        }
                                    )
                                    viewModel.updateExercise(updatedExercise)
                                },
                                onMenuClick = { showMenu = true },
                                onNotesClick = { showNotesDialog = true },
                                context = ExerciseCardContext.TEMPLATE_CREATION
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
                                    onNavigateToExerciseSelection()
                                },
                                onRemove = {
                                    viewModel.removeExercise(exercise)
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
                                            mutableStateOf(exerciseNotes[exercise.exerciseId.value] ?: "")
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
                                                exerciseNotes[exercise.exerciseId.value] = 
                                                    exerciseNotes[exercise.exerciseId.value] ?: ""
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
                    }
                    else -> {
                        // Handle other states
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
                            text = "Browse Exercises",
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
            
            // Save Template Button (Fixed at bottom)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LiftrixColorsV2.Dark.BackgroundPrimary)
                    .padding(16.dp)
            ) {
                RedesignedPrimaryButton(
                    text = "Save Template",
                    onClick = {
                        val exercises = when (val state = uiState) {
                            is WorkoutTemplateCreationUiState.Success -> state.data.exercises
                            else -> emptyList()
                        }
                        
                        if (editTemplateId != null) {
                            viewModel.updateTemplate(
                                templateId = editTemplateId,
                                name = templateName,
                                description = templateDescription.takeIf { it.isNotBlank() },
                                exercises = exercises
                            )
                        } else {
                            viewModel.createTemplate(
                                name = templateName,
                                description = templateDescription.takeIf { it.isNotBlank() },
                                exercises = exercises
                            )
                        }
                    },
                    enabled = templateName.isNotBlank()
                )
            }
        }
        
        // Reorder dialog
        if (showReorderDialog) {
            val exercises = when (val state = uiState) {
                is WorkoutTemplateCreationUiState.Success -> state.data.exercises
                else -> emptyList()
            }
            
            ExerciseReorderDialog(
                exercises = exercises.map { exercise ->
                    exercise.instanceId to exercise.name
                },
                onDismiss = { showReorderDialog = false },
                onConfirmReorder = { reorderedIds ->
                    // Template reorder uses fromIndex/toIndex, need to convert
                    val currentOrder = exercises.map { it.instanceId }
                    val reorderedOrder = reorderedIds
                    
                    // Find what moved and apply the reorder
                    for (i in reorderedOrder.indices) {
                        val currentPos = currentOrder.indexOf(reorderedOrder[i])
                        if (currentPos != i) {
                            viewModel.reorderExercises(currentPos, i)
                            break // Apply one move at a time
                        }
                    }
                }
            )
        }
    }
}