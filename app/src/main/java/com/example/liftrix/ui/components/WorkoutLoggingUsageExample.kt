package com.example.liftrix.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.usecase.exercise.SearchableExercise
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Example usage of workout logging components with ViewModel
 * This shows how to wire the stateless components to your ViewModel state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLoggingScreenExample(
    viewModel: WorkoutLoggingViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Exercise Selector
        SearchableExerciseSelector(
            exercises = uiState.availableExercises,
            searchQuery = uiState.exerciseSearchQuery,
            selectedExercise = uiState.selectedExercise,
            isExpanded = uiState.isExerciseSelectorExpanded,
            onSearchQueryChanged = viewModel::onExerciseSearchQueryChanged,
            onExerciseSelected = viewModel::onExerciseSelected,
            onExpandedChanged = viewModel::onExerciseSelectorExpandedChanged,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Show sets input when exercise is selected
        uiState.selectedExercise?.let { exercise ->
            Text(
                text = "Exercise: ${exercise.name}",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Sets List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(uiState.sets) { index, setData ->
                    SetInputRow(
                        setNumber = index + 1,
                        reps = setData.reps,
                        weight = setData.weight,
                        restTimeSeconds = setData.restTimeSeconds,
                        rpe = setData.rpe,
                        onRepsChanged = { reps ->
                            viewModel.onSetRepsChanged(index, reps)
                        },
                        onWeightChanged = { weight ->
                            viewModel.onSetWeightChanged(index, weight)
                        },
                        onRestTimeChanged = { restTime ->
                            viewModel.onSetRestTimeChanged(index, restTime)
                        },
                        onRpeChanged = { rpe ->
                            viewModel.onSetRpeChanged(index, rpe)
                        },
                        onSetCompleted = {
                            viewModel.onSetCompleted(index)
                        },
                        isCompleted = setData.isCompleted,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Add Set Button
            Button(
                onClick = viewModel::onAddSet,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text("Add Set")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Muscle Engagement Selector
            MuscleEngagementSelector(
                selectedEngagement = uiState.muscleEngagement,
                onEngagementSelected = viewModel::onMuscleEngagementSelected,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Save Button
            SaveButton(
                isLoading = uiState.isSaving,
                isEnabled = uiState.canSave,
                onSaveClicked = {
                    keyboardController?.hide()
                    viewModel.onSaveWorkout()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Show message when no exercise is selected
        if (uiState.selectedExercise == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select an exercise to start logging your workout",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Example UI state for workout logging
 */
data class WorkoutLoggingUiState(
    val availableExercises: List<SearchableExercise> = emptyList(),
    val exerciseSearchQuery: String = "",
    val selectedExercise: SearchableExercise? = null,
    val isExerciseSelectorExpanded: Boolean = false,
    val sets: List<SetInputData> = emptyList(),
    val muscleEngagement: Int? = null,
    val isSaving: Boolean = false,
    val canSave: Boolean = false
)

/**
 * Data class representing set input state
 */
data class SetInputData(
    val reps: Reps = Reps(0),
    val weight: Weight = Weight(0.0),
    val restTimeSeconds: Int? = null,
    val rpe: Int? = null,
    val isCompleted: Boolean = false
)

/**
 * Example ViewModel interface showing the required methods
 * Implement this interface in your actual ViewModel
 */
interface WorkoutLoggingViewModel {
    val uiState: StateFlow<WorkoutLoggingUiState>
    
    fun onExerciseSearchQueryChanged(query: String)
    fun onExerciseSelected(exercise: SearchableExercise?)
    fun onExerciseSelectorExpandedChanged(expanded: Boolean)
    fun onSetRepsChanged(setIndex: Int, reps: Reps)
    fun onSetWeightChanged(setIndex: Int, weight: Weight)
    fun onSetRestTimeChanged(setIndex: Int, restTime: Int?)
    fun onSetRpeChanged(setIndex: Int, rpe: Int?)
    fun onSetCompleted(setIndex: Int)
    fun onAddSet()
    fun onMuscleEngagementSelected(engagement: Int)
    fun onSaveWorkout()
}

/**
 * Simple example usage of workout logging components
 */
@Composable
fun WorkoutLoggingUsageExample(
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedExercise by remember { mutableStateOf<SearchableExercise?>(null) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    
    val sampleExercises = remember {
        listOf(
            SearchableExercise.LibraryExercise(
                ExerciseLibrary(
                    id = "1",
                    name = "Bench Press",
                    primaryMuscleGroup = ExerciseCategory.CHEST,
                    equipment = Equipment.BARBELL,
                    secondaryMuscleGroups = listOf(ExerciseCategory.SHOULDERS, ExerciseCategory.ARMS),
                    movementPattern = "Horizontal Push",
                    difficultyLevel = 5,
                    instructions = "Lie on bench, press weight up",
                    isCompound = true,
                    searchableTerms = listOf("press", "chest", "barbell")
                )
            )
        )
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        SearchableExerciseSelector(
            exercises = sampleExercises,
            searchQuery = searchQuery,
            selectedExercise = selectedExercise,
            isExpanded = isSearchExpanded,
            onSearchQueryChanged = { searchQuery = it },
            onExerciseSelected = { selectedExercise = it },
            onExpandedChanged = { isSearchExpanded = it }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun WorkoutLoggingUsageExamplePreview() {
    LiftrixTheme {
        WorkoutLoggingUsageExample()
    }
} 