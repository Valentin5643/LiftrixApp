package com.example.liftrix.ui.workout.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.SavedStateHandle
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import com.example.liftrix.ui.workout.components.SecondaryActionButton
import com.example.liftrix.ui.workout.components.TertiaryActionButton
import com.example.liftrix.ui.workout.creation.components.DragDropExerciseList
import com.example.liftrix.ui.common.state.dataOrNull
import com.example.liftrix.ui.components.ValidatedRequiredTextField
import com.example.liftrix.ui.components.ValidatedMultilineTextField
import com.example.liftrix.ui.validation.InputValidation
import com.example.liftrix.ui.validation.ValidationSummaryCard
import com.example.liftrix.domain.model.validation.ValidationResult

/**
 * Creating a workout screen for building workout routines.
 * 
 * This screen provides:
 * - Workout details input (name, description)
 * - Exercise selection and management
 * - Set configuration
 * - Workout saving functionality
 * - Modern unified card-based design
 * 
 * @param onNavigateBack Callback for back navigation
 * @param onAddExercise Callback for exercise addition
 * @param modifier Modifier for styling
 * @param viewModel ViewModel for workout creation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateWorkoutScreen(
    onNavigateBack: () -> Unit,
    onAddExercise: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: WorkoutTemplateCreationViewModel = hiltViewModel(),
    savedStateHandle: SavedStateHandle? = null
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    var workoutName by remember { mutableStateOf("") }
    var workoutDescription by remember { mutableStateOf("") }
    
    // Validation states
    val workoutNameValidation by remember(workoutName) {
        derivedStateOf { InputValidation.validateWorkoutName(workoutName) }
    }
    val workoutDescriptionValidation by remember(workoutDescription) {
        derivedStateOf { InputValidation.validateWorkoutDescription(workoutDescription) }
    }
    val formValidation by remember(workoutName, workoutDescription) {
        derivedStateOf { 
            InputValidation.validateWorkoutCreationForm(workoutName, workoutDescription)
        }
    }
    
    // Handle selected exercise from navigation
    val selectedExercise = savedStateHandle?.get<ExerciseLibrary>("selected_exercise")
    
    LaunchedEffect(selectedExercise) {
        selectedExercise?.let { exercise ->
            try {
                viewModel.addExerciseFromLibrary(exercise)
                
                // Clear the saved state to prevent re-adding the same exercise
                savedStateHandle?.remove<ExerciseLibrary>("selected_exercise")
                
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Error adding exercise from library")
            }
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Creating a workout",
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
                    // Save workout button - modern tertiary style for header actions
                    val exercises = uiState.dataOrNull()?.exercises ?: emptyList()
                    val canSave = exercises.isNotEmpty() && workoutName.isNotBlank()
                    TertiaryActionButton(
                        text = "Cancel",
                        onClick = onNavigateBack
                    )
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
                .padding(horizontal = LiftrixSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.cardSpacing)
        ) {
            item {
                Spacer(modifier = Modifier.height(LiftrixSpacing.elementSpacing))
            }
            
            // Workout Details Section - Using UnifiedWorkoutCard
            item {
                UnifiedWorkoutCard(
                    title = "Workout Details",
                    subtitle = "Name and description"
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.formFieldSpacing)
                    ) {
                        ValidatedRequiredTextField(
                            value = workoutName,
                            onValueChange = { workoutName = it },
                            label = "Workout Name",
                            placeholder = "Enter workout name",
                            validationResult = workoutNameValidation,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        ValidatedMultilineTextField(
                            value = workoutDescription,
                            onValueChange = { workoutDescription = it },
                            label = "Description (Optional)",
                            placeholder = "Describe your workout",
                            validationResult = workoutDescriptionValidation,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                }
            }
            
            // Exercise Selection Section - Using UnifiedWorkoutCard
            item {
                UnifiedWorkoutCard(
                    title = "Add Exercises",
                    subtitle = "Browse exercises by category, equipment, or search"
                ) {
                    Text(
                        text = "Find the perfect exercises for your workout routine. You can search by name, filter by muscle group, or browse by equipment type.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(LiftrixSpacing.elementSpacing))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PrimaryActionButton(
                            text = "Browse Exercises",
                            onClick = onAddExercise,
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = com.example.liftrix.ui.icons.LiftrixIcons.Actions.Search
                        )
                    }
                }
            }
            
            // Exercises List Section
            val exercises = uiState.dataOrNull()?.exercises ?: emptyList()
            if (exercises.isNotEmpty()) {
                item {
                    UnifiedWorkoutCard(
                        title = "Selected Exercises",
                        subtitle = "${exercises.size} exercise${if (exercises.size == 1) "" else "s"}"
                    ) {
                        DragDropExerciseList(
                            exercises = exercises,
                            onReorder = { fromIndex, toIndex ->
                                viewModel.reorderExercises(fromIndex, toIndex)
                            },
                            onUpdateExercise = { updatedExercise ->
                                viewModel.updateExercise(updatedExercise)
                            },
                            onRemoveExercise = { exercise ->
                                viewModel.removeExercise(exercise)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            // Validation Summary (if there are errors)
            if (formValidation is ValidationResult.Error) {
                item {
                    ValidationSummaryCard(
                        validations = listOf(workoutNameValidation, workoutDescriptionValidation),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // Action Buttons Section
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.buttonSpacing)
                ) {
                    // Primary save action
                    val exercises = uiState.dataOrNull()?.exercises ?: emptyList()
                    val canSave = exercises.isNotEmpty() && formValidation is ValidationResult.Success
                    PrimaryActionButton(
                        text = "Save Workout",
                        onClick = { 
                            if (canSave) {
                                viewModel.createWorkout(workoutName, workoutDescription, exercises)
                                onNavigateBack()
                            }
                        },
                        enabled = canSave,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = com.example.liftrix.ui.icons.LiftrixIcons.Actions.Save
                    )
                    
                    // Secondary actions row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.buttonSpacing)
                    ) {
                        SecondaryActionButton(
                            text = "Preview",
                            onClick = { 
                                // TODO: Implement workout preview functionality
                            },
                            enabled = exercises.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            leadingIcon = com.example.liftrix.ui.icons.LiftrixIcons.Workflow.Progress
                        )
                        
                        TertiaryActionButton(
                            text = "Clear All",
                            onClick = {
                                // Clear form and reset state
                                workoutName = ""
                                workoutDescription = ""
                                viewModel.resetToEditing()
                            },
                            enabled = workoutName.isNotBlank() || workoutDescription.isNotBlank() || exercises.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            leadingIcon = com.example.liftrix.ui.icons.LiftrixIcons.Actions.Remove
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(LiftrixSpacing.screenContentSpacing))
            }
        }
    }
}