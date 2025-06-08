package com.example.liftrix.ui.workout.daily

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftrix.R
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.ui.components.LiftrixSnackbarHost
import com.example.liftrix.ui.components.SetInputRow
import com.example.liftrix.ui.workout.creation.ExerciseSelectionScreen

/**
 * Screen for daily workout execution with template vs custom workout distinction
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickWorkoutScreen(
    templateId: String? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DailyWorkoutViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Handle initial workout start
    LaunchedEffect(templateId) {
        if (templateId != null) {
            viewModel.onEvent(DailyWorkoutEvent.StartFromTemplate(templateId))
        } else {
            viewModel.onEvent(DailyWorkoutEvent.StartFromScratch)
        }
    }

    // Show snackbar for errors
    uiState.errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.onEvent(DailyWorkoutEvent.ClearError)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.currentWorkout?.name ?: "Workout",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
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
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (uiState.isWorkoutActive) {
                        IconButton(
                            onClick = {
                                viewModel.onEvent(DailyWorkoutEvent.CompleteWorkout)
                            },
                            modifier = Modifier.semantics { 
                                contentDescription = "Complete workout" 
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Complete workout",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = {
            LiftrixSnackbarHost(hostState = snackbarHostState)
        },
        floatingActionButton = {
            if (uiState.isWorkoutActive && !uiState.showExerciseSelection) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.onEvent(DailyWorkoutEvent.ShowExerciseSelection)
                    },
                    modifier = Modifier.semantics { 
                        contentDescription = "Add exercise to workout" 
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Exercise")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    LoadingContent()
                }
                uiState.isWorkoutCompleted -> {
                    WorkoutCompletedContent(
                        canConvertToTemplate = uiState.canConvertToTemplate,
                        onConvertToTemplate = {
                            viewModel.onEvent(DailyWorkoutEvent.ShowConvertToTemplateDialog)
                        },
                        onStartNewWorkout = {
                            viewModel.onEvent(DailyWorkoutEvent.StartFromScratch)
                        }
                    )
                }
                uiState.isWorkoutActive && uiState.showExerciseSelection -> {
                    ExerciseSelectionScreen(
                        onNavigateBack = {
                            viewModel.onEvent(DailyWorkoutEvent.HideExerciseSelection)
                        },
                        onExerciseSelected = { searchableExercise ->
                            viewModel.onEvent(DailyWorkoutEvent.AddExercise(searchableExercise))
                        },
                        onNavigateToCustomExerciseCreation = {
                            // TODO: Navigate to custom exercise creation
                        }
                    )
                }
                uiState.isWorkoutActive -> {
                    ActiveWorkoutContent(
                        workout = uiState.currentWorkout,
                        isFromTemplate = uiState.sourceTemplate != null,
                        templateName = uiState.sourceTemplate?.name,
                        onUpdateSet = { exerciseId, setIndex, weight, reps ->
                            viewModel.onEvent(DailyWorkoutEvent.UpdateExerciseSet(exerciseId, setIndex, weight, reps))
                        },
                        onCompleteSet = { exerciseId, setIndex ->
                            viewModel.onEvent(DailyWorkoutEvent.CompleteSet(exerciseId, setIndex))
                        },
                        onAddSet = { exerciseId ->
                            viewModel.onEvent(DailyWorkoutEvent.AddSetToExercise(exerciseId))
                        },
                        onRemoveExercise = { exerciseId ->
                            viewModel.onEvent(DailyWorkoutEvent.RemoveExercise(exerciseId))
                        }
                    )
                }
                else -> {
                    EmptyWorkoutContent()
                }
            }

            // Convert to Template Dialog
            if (uiState.showConvertToTemplateDialog) {
                ConvertToTemplateDialog(
                    onConfirm = { templateName ->
                        viewModel.onEvent(DailyWorkoutEvent.ConvertToTemplate(templateName))
                    },
                    onDismiss = {
                        viewModel.onEvent(DailyWorkoutEvent.HideConvertToTemplateDialog)
                    }
                )
            }
        }
    }
}

@Composable
private fun LoadingContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.semantics {
                    contentDescription = "Loading workout"
                }
            )
            Text(
                text = "Starting your workout...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WorkoutCompletedContent(
    canConvertToTemplate: Boolean,
    onConvertToTemplate: () -> Unit,
    onStartNewWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Workout Complete!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Great job on finishing your workout session.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (canConvertToTemplate) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Save as Template",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Turn this custom workout into a reusable template for future sessions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = onConvertToTemplate,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Template")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        OutlinedButton(
            onClick = onStartNewWorkout,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start New Workout")
        }
    }
}

@Composable
private fun ActiveWorkoutContent(
    workout: com.example.liftrix.domain.model.DailyWorkout?,
    isFromTemplate: Boolean,
    templateName: String?,
    onUpdateSet: (com.example.liftrix.domain.model.ExerciseId, Int, Weight, Reps) -> Unit,
    onCompleteSet: (com.example.liftrix.domain.model.ExerciseId, Int) -> Unit,
    onAddSet: (com.example.liftrix.domain.model.ExerciseId) -> Unit,
    onRemoveExercise: (com.example.liftrix.domain.model.ExerciseId) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            WorkoutHeaderCard(
                isFromTemplate = isFromTemplate,
                templateName = templateName
            )
        }
        
        if (workout?.exercises?.isEmpty() == true) {
            item {
                EmptyExercisesCard()
            }
        } else {
            workout?.exercises?.let { exercises ->
                items(exercises, key = { it.id.value }) { exercise ->
                    ExerciseCard(
                        exercise = exercise,
                        onUpdateSet = onUpdateSet,
                        onCompleteSet = onCompleteSet,
                        onAddSet = onAddSet,
                        onRemoveExercise = onRemoveExercise
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutHeaderCard(
    isFromTemplate: Boolean,
    templateName: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isFromTemplate) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isFromTemplate) Icons.Default.Assignment else Icons.Default.Create,
                    contentDescription = null,
                    tint = if (isFromTemplate) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isFromTemplate) "Template Workout" else "Custom Workout",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            
            if (isFromTemplate && templateName != null) {
                Text(
                    text = "Based on: $templateName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Text(
                    text = "Building your workout as you go",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyExercisesCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No exercises yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Tap the + button to add your first exercise",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: Exercise,
    onUpdateSet: (com.example.liftrix.domain.model.ExerciseId, Int, Weight, Reps) -> Unit,
    onCompleteSet: (com.example.liftrix.domain.model.ExerciseId, Int) -> Unit,
    onAddSet: (com.example.liftrix.domain.model.ExerciseId) -> Unit,
    onRemoveExercise: (com.example.liftrix.domain.model.ExerciseId) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Exercise header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = exercise.category.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Exercise completion badge
                val completedSets = exercise.getCompletedSetsCount()
                val totalSets = exercise.sets.size
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (exercise.isCompleted()) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text(
                        text = "$completedSets/$totalSets",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                
                IconButton(
                    onClick = { onRemoveExercise(exercise.id) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove exercise",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Sets
            exercise.sets.forEachIndexed { index, set ->
                SetInputRow(
                    setNumber = set.setNumber,
                    reps = set.reps,
                    weight = set.weight,
                    restTimeSeconds = set.restTimeSeconds,
                    rpe = null, // RPE not implemented in ExerciseSet model yet
                    onRepsChanged = { reps ->
                        onUpdateSet(exercise.id, index, set.weight, reps)
                    },
                    onWeightChanged = { weight ->
                        onUpdateSet(exercise.id, index, weight, set.reps)
                    },
                    onRestTimeChanged = { /* TODO: Implement rest time updates */ },
                    onRpeChanged = { /* TODO: Implement RPE updates */ },
                    onSetCompleted = {
                        onCompleteSet(exercise.id, index)
                    },
                    isCompleted = set.isCompleted,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            // Add set button
            OutlinedButton(
                onClick = { onAddSet(exercise.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Set")
            }
        }
    }
}

@Composable
private fun EmptyWorkoutContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Ready to start your workout?",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConvertToTemplateDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var templateName by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Save as Template",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Give your workout template a name so you can easily find it later.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                OutlinedTextField(
                    value = templateName,
                    onValueChange = { templateName = it },
                    label = { Text("Template name") },
                    placeholder = { Text("e.g., Upper Body Strength") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (templateName.isNotBlank()) {
                        onConfirm(templateName.trim())
                    }
                },
                enabled = templateName.isNotBlank()
            ) {
                Text("Save Template")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        modifier = modifier
    )
}