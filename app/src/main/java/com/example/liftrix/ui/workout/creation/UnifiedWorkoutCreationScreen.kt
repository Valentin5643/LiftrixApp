package com.example.liftrix.ui.workout.creation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.creation.components.AddExerciseButton
import com.example.liftrix.ui.workout.creation.components.ExerciseCard
import com.example.liftrix.ui.workout.creation.components.ExerciseSelector
import com.example.liftrix.ui.workout.creation.components.WorkoutHeaderForm
import com.example.liftrix.ui.workout.creation.model.SelectedExercise
import com.example.liftrix.ui.workout.creation.model.SetInput
import com.example.liftrix.ui.workout.creation.model.WorkoutCreationEvent
import com.example.liftrix.ui.workout.creation.model.WorkoutCreationState

/**
 * Main screen for redesigned single-screen workout creation flow
 * Orchestrates all components following Material 3 design guidelines and Clean Architecture
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedWorkoutCreationScreen(
    onNavigateBack: () -> Unit,
    onWorkoutCreated: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UnifiedWorkoutCreationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    
    // Handle success message and navigation
    LaunchedEffect(state.showSuccessMessage) {
        if (state.showSuccessMessage) {
            keyboardController?.hide()
            state.successMessage?.let { message ->
                onWorkoutCreated(message)
            }
        }
    }
    
    // Handle error messages
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onEvent(WorkoutCreationEvent.ClearMessages)
        }
    }
    
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Create Workout",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium
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
                            contentDescription = "Back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    keyboardController?.hide()
                    viewModel.onEvent(WorkoutCreationEvent.SaveWorkout)
                },
                icon = {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(2.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save workout"
                        )
                    }
                },
                text = {
                    Text(
                        text = if (state.isSaving) "Saving..." else "Save Workout",
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                expanded = !state.isSaving,
                containerColor = if (state.isFormValid && !state.isSaving) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                },
                contentColor = if (state.isFormValid && !state.isSaving) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                },
                modifier = Modifier.semantics { 
                    contentDescription = if (state.isFormValid) {
                        "Save workout button, enabled"
                    } else {
                        "Save workout button, disabled - complete required fields"
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Main content
            WorkoutCreationContent(
                state = state,
                onEvent = viewModel::onEvent,
                modifier = Modifier.fillMaxSize()
            )
            
            // Exercise selector modal
            ExerciseSelector(
                exercises = state.filteredExercises,
                recentExercises = emptyList(), // TODO: Add recent exercises support
                searchQuery = state.exerciseSearchQuery,
                onSearchQueryChange = { query ->
                    viewModel.onEvent(WorkoutCreationEvent.UpdateExerciseSearchQuery(query))
                },
                selectedEquipment = emptySet(), // TODO: Add equipment filtering support
                onEquipmentSelectionChange = { }, // TODO: Add equipment filtering support
                selectedMuscleGroups = emptySet(), // TODO: Add muscle group filtering support
                onMuscleGroupSelectionChange = { }, // TODO: Add muscle group filtering support
                onExerciseSelected = { exercise ->
                    viewModel.onEvent(WorkoutCreationEvent.SelectExercise(exercise))
                },
                onCreateCustomExercise = { }, // TODO: Add custom exercise creation support
                onDismiss = {
                    viewModel.onEvent(WorkoutCreationEvent.HideExerciseSelector)
                },
                isVisible = state.isExerciseSelectorVisible
            )
            
            // Loading overlay
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.semantics { 
                            contentDescription = "Loading workout data, please wait" 
                        }
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Loading exercises...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Main content area with workout header and exercise list
 */
@Composable
private fun WorkoutCreationContent(
    state: WorkoutCreationState,
    onEvent: (WorkoutCreationEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top spacing
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Workout header form
        item {
            WorkoutHeaderForm(
                workoutName = state.workoutName,
                onWorkoutNameChange = { name ->
                    onEvent(WorkoutCreationEvent.UpdateWorkoutName(name))
                },
                workoutDescription = state.workoutDescription,
                onWorkoutDescriptionChange = { description ->
                    onEvent(WorkoutCreationEvent.UpdateWorkoutDescription(description))
                },
                workoutNameError = state.workoutNameError,
                workoutDescriptionError = state.workoutDescriptionError,
                enabled = !state.isLoading && !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Exercise cards
        if (state.selectedExercises.isNotEmpty()) {
            itemsIndexed(
                items = state.selectedExercises,
                key = { _, exercise -> exercise.libraryExercise.id }
            ) { index, selectedExercise ->
                ExerciseCard(
                    selectedExercise = selectedExercise,
                    onSetUpdate = { setIndex, setInput ->
                        // Update all fields at once with the complete SetInput
                        onEvent(WorkoutCreationEvent.UpdateSetReps(index, setIndex, setInput.reps))
                        onEvent(WorkoutCreationEvent.UpdateSetRpe(index, setIndex, setInput.rpe))
                        onEvent(WorkoutCreationEvent.UpdateSetWeight(index, setIndex, setInput.weight))
                    },
                    onAddSet = {
                        onEvent(WorkoutCreationEvent.AddSetToExercise(index))
                    },
                    onRemoveSet = { setIndex ->
                        onEvent(WorkoutCreationEvent.RemoveSetFromExercise(index, setIndex))
                    },
                    onRemoveExercise = {
                        onEvent(WorkoutCreationEvent.RemoveExercise(index))
                    },
                    enabled = !state.isLoading && !state.isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // Add exercise button or empty state
        item {
            if (state.selectedExercises.isEmpty()) {
                EmptyExerciseState(
                    onAddExercise = {
                        onEvent(WorkoutCreationEvent.ShowExerciseSelector)
                    },
                    enabled = !state.isLoading && !state.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                )
            } else {
                AddExerciseButton(
                    onClick = {
                        onEvent(WorkoutCreationEvent.ShowExerciseSelector)
                    },
                    enabled = !state.isLoading && !state.isSaving &&
                            state.selectedExercises.size < WorkoutCreationState.MAX_EXERCISES_COUNT,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // Bottom spacing for FAB
        item {
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

/**
 * Empty state when no exercises are selected
 */
@Composable
private fun EmptyExerciseState(
    onAddExercise: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.FitnessCenter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
        
        Text(
            text = "No exercises added yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Add exercises to start building your workout",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        AddExerciseButton(
            onClick = onAddExercise,
            enabled = enabled,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun UnifiedWorkoutCreationScreenPreview() {
    LiftrixTheme {
        UnifiedWorkoutCreationScreen(
            onNavigateBack = {},
            onWorkoutCreated = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WorkoutCreationContentWithExercisesPreview() {
    val sampleExercise = ExerciseLibrary(
        id = "1",
        name = "Push-ups",
        primaryMuscleGroup = ExerciseCategory.CHEST,
        equipment = Equipment.BODYWEIGHT_ONLY,
        secondaryMuscleGroups = listOf(ExerciseCategory.SHOULDERS, ExerciseCategory.TRICEPS),
        movementPattern = "Push",
        difficultyLevel = 3,
        instructions = "Classic push-up exercise",
        isCompound = true,
        searchableTerms = listOf("push", "chest", "bodyweight")
    )
    
    val sampleState = WorkoutCreationState(
        workoutName = "Push Day",
        workoutDescription = "Chest, shoulders, and triceps workout",
        selectedExercises = listOf(
            SelectedExercise(
                libraryExercise = sampleExercise,
                sets = listOf(
                    SetInput(reps = "10", rpe = "8", isWeightSupported = false),
                    SetInput(reps = "8", rpe = "9", isWeightSupported = false)
                ),
                orderIndex = 0
            )
        ),
        isFormValid = true
    )
    
    LiftrixTheme {
        WorkoutCreationContent(
            state = sampleState,
            onEvent = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyExerciseStatePreview() {
    LiftrixTheme {
        EmptyExerciseState(
            onAddExercise = {},
            enabled = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        )
    }
} 