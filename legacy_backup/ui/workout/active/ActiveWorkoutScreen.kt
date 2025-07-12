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
import androidx.compose.runtime.DisposableEffect
import java.time.LocalDateTime
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
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.active.components.SaveWorkoutDialog
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Simplified active workout screen for workout execution.
 * 
 * This screen provides:
 * - Small timer display in the top bar
 * - Exercise and set tracking
 * - Exercise addition
 * - Set completion
 * - Workout completion flow
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
    workoutViewModel: ActiveWorkoutViewModel = hiltViewModel(),
    isFromTemplate: Boolean = false
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val workoutState by workoutViewModel.uiState.collectAsStateWithLifecycle()
    
    var showSaveWorkoutDialog by remember { mutableStateOf(false) }
    
    // Simple timer state for all workouts
    var startTime by remember { mutableStateOf<LocalDateTime?>(null) }
    var elapsedTime by remember { mutableStateOf("00:00") }
    
    // Start simple timer when screen loads
    LaunchedEffect(Unit) {
        if (startTime == null) {
            startTime = LocalDateTime.now()
        }
    }
    
    // Update elapsed time
    LaunchedEffect(startTime) {
        if (startTime != null) {
            while (true) {
                val now = LocalDateTime.now()
                val elapsed = java.time.Duration.between(startTime, now)
                val minutes = elapsed.toMinutes()
                val seconds = elapsed.minusMinutes(minutes).seconds
                elapsedTime = String.format("%02d:%02d", minutes, seconds)
                kotlinx.coroutines.delay(1000)
            }
        }
    }
    
    // 🔥 ENHANCED: Listen for selected exercise from navigation with improved cleanup
    LaunchedEffect(navController) {
        val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
        savedStateHandle?.let { handle ->
            handle.getLiveData<String>("selected_exercise_id").observeForever { exerciseId ->
                if (exerciseId != null) {
                    val isCustomExercise = handle.get<Boolean>("is_custom_exercise") ?: false
                    timber.log.Timber.i("🔍 NAVIGATION: Received exercise selection - ID: $exerciseId, isCustom: $isCustomExercise")
                    
                    // Add the exercise to the workout with enhanced deduplication
                    workoutViewModel.addExerciseById(exerciseId, isCustomExercise)
                    
                    // 🔥 ENHANCED CLEANUP: Clear the navigation result immediately to prevent re-execution
                    handle.remove<String>("selected_exercise_id")
                    handle.remove<Boolean>("is_custom_exercise")
                    
                    // 🔥 ADDITIONAL CLEANUP: Clear any other potential navigation state
                    handle.remove<String>("selected_exercise_name")
                    handle.remove<Any>("exercise_data")
                }
            }
        }
    }
    
    // 🔥 FIX: Clear navigation state when screen is first created to prevent stale data
    LaunchedEffect(Unit) {
        val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
        savedStateHandle?.let { handle ->
            handle.remove<String>("selected_exercise_id")
            handle.remove<Boolean>("is_custom_exercise")
            handle.remove<String>("selected_exercise_name")
            handle.remove<Any>("exercise_data")
            timber.log.Timber.d("🔥 NAVIGATION-CLEAR: Cleared stale navigation state on screen init")
        }
    }

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
                    // Small timer display for all workouts
                    Text(
                        text = elapsedTime,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    
                    // Save workout button - always visible when exercises exist
                    if (workoutState.currentWorkout != null && workoutState.currentWorkout!!.exercises.isNotEmpty()) {
                        IconButton(
                            onClick = { 
                                if (isFromTemplate) {
                                    // Template workouts complete immediately without dialog
                                    workoutViewModel.onEvent(ActiveWorkoutEvent.SaveWorkout)
                                    onNavigateBack()
                                } else {
                                    // Blank sessions show save dialog
                                    showSaveWorkoutDialog = true
                                }
                            },
                            modifier = Modifier.semantics {
                                contentDescription = if (isFromTemplate) "Complete workout" else "Complete and save workout"
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
            
            // Exercise Selection Section
            item {
                WorkoutExerciseSelectionCard(
                    onNavigateToExerciseSelection = onAddExercise,
                    isFromTemplate = isFromTemplate
                )
            }
            
            // Exercises Section
            item {
                WorkoutExercisesCard(
                    workout = workoutState.currentWorkout,
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
                    },
                    isFromTemplate = isFromTemplate,
                    workoutState = workoutState // DEBUG: Pass state for visual indicators
                )
            }
            
            // Workout Summary Section
            if (workoutState.currentWorkout != null && workoutState.currentWorkout!!.exercises.isNotEmpty()) {
                item {
                    WorkoutSummaryCard(
                        workout = workoutState.currentWorkout!!,
                        onCompleteWorkout = { 
                            // This button only appears for blank sessions (non-template workouts)
                            // Show save dialog to let user choose how to save their workout
                            showSaveWorkoutDialog = true 
                        },
                        isFromTemplate = isFromTemplate
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
    
}




/**
 * Enhanced exercises card with full exercise and set management (no duplicate Add Exercise button)
 */
@Composable
private fun WorkoutExercisesCard(
    workout: com.example.liftrix.domain.model.Workout?,
    onRemoveExercise: (Int) -> Unit,
    onAddSet: (Int) -> Unit,
    onUpdateSet: (Int, Int, ExerciseSet) -> Unit,
    onRemoveSet: (Int, Int) -> Unit,
    isFromTemplate: Boolean = false,
    workoutState: ActiveWorkoutState? = null, // DEBUG: Add state for visual indicators
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFromTemplate) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
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
                    text = if (isFromTemplate) "Workout Exercises (${workout?.exercises?.size ?: 0})" else "Exercises",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                // DEBUG: Visual indicators for debugging
                workoutState?.let { state ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Show loading state
                        if (state.isLoading) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.padding(2.dp)
                            ) {
                                Text(
                                    text = "LOADING",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        
                        // Show pending exercises count
                        if (state.pendingExercises.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                modifier = Modifier.padding(2.dp)
                            ) {
                                Text(
                                    text = "PENDING: ${state.pendingExercises.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        
                        // Show template flag
                        if (state.isWorkoutFromTemplate) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.padding(2.dp)
                            ) {
                                Text(
                                    text = "TEMPLATE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // DEBUG: Show pending exercises details
            workoutState?.let { state ->
                if (state.pendingExercises.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "🔍 DEBUG: Pending Exercises (waiting for workout to load)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            state.pendingExercises.forEachIndexed { index, exercise ->
                                Text(
                                    text = "${index + 1}. ${exercise.libraryExercise.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            
            if (workout?.exercises?.isEmpty() != false) {
                Text(
                    text = if (isFromTemplate) 
                        "No exercises added yet. Use the exercise selector above to add exercises to your workout."
                    else 
                        "No exercises added yet. Tap 'Select Exercise' to get started!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )
            } else {
                workout.exercises.forEachIndexed { exerciseIndex, exercise ->
                    ExerciseItem(
                        exercise = exercise,
                        exerciseIndex = exerciseIndex,
                        onRemoveExercise = onRemoveExercise,
                        onAddSet = onAddSet,
                        onUpdateSet = onUpdateSet,
                        onRemoveSet = onRemoveSet,
                        isFromTemplate = isFromTemplate
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
    isFromTemplate: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFromTemplate) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
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
    isFromTemplate: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFromTemplate) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer
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
                fontWeight = FontWeight.SemiBold,
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
            
            // Only show completion button for non-template workouts
            if (!isFromTemplate) {
                Spacer(modifier = Modifier.height(16.dp))
                
                FilledTonalButton(
                    onClick = {
                        // Validate workout has exercises before completion
                        if (workout.exercises.isNotEmpty()) {
                            onCompleteWorkout()
                        }
                    },
                    enabled = workout.exercises.isNotEmpty(),
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
 * Card for exercise selection using navigation to ExerciseSelectionScreen (template-aligned)
 */
@Composable
private fun WorkoutExerciseSelectionCard(
    onNavigateToExerciseSelection: () -> Unit,
    isFromTemplate: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Add Exercise",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            if (isFromTemplate) {
                Text(
                    text = "Browse exercises by category, equipment, or search to find the perfect exercises for your workout.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            FilledTonalButton(
                onClick = onNavigateToExerciseSelection,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isFromTemplate) "Browse Exercises" else "Select Exercise")
            }
        }
    }
}