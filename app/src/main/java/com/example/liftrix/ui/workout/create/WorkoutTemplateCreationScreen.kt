package com.example.liftrix.ui.workout.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.example.liftrix.domain.model.ExerciseLibrary
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftrix.R
import com.example.liftrix.domain.model.TemplateExercise
import com.example.liftrix.ui.common.LiftrixProgressIndicator
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.creation.components.DragDropExerciseList

/**
 * Screen for creating a new workout template from scratch.
 * 
 * Provides a form-based interface for users to:
 * - Enter template name and description
 * - Set estimated duration and difficulty
 * - Add exercises to the template
 * - Save the template for future use
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutTemplateCreationScreen(
    onNavigateBack: () -> Unit,
    onNavigateToExerciseSelection: () -> Unit = {},
    editTemplateId: String? = null,
    modifier: Modifier = Modifier,
    viewModel: WorkoutTemplateCreationViewModel = hiltViewModel(),
    savedStateHandle: androidx.lifecycle.SavedStateHandle? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val loadedTemplate by viewModel.loadedTemplate.collectAsStateWithLifecycle()
    
    // Debug UI state changes
    LaunchedEffect(uiState) {
        timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: UI State changed to: ${uiState::class.simpleName}")
        when (uiState) {
            is WorkoutTemplateCreationUiState.Editing -> {
                timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: Editing state with ${uiState.exercises.size} exercises")
                timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: Exercise names: ${uiState.exercises.map { it.name }}")
            }
            is WorkoutTemplateCreationUiState.Loading -> {
                timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: Loading state")
            }
            else -> {
                timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: Other state: ${uiState::class.simpleName}")
            }
        }
    }
    
    var templateName by remember { mutableStateOf("") }
    var templateDescription by remember { mutableStateOf("") }
    
    val isEditMode = editTemplateId != null
    
    // Initialize form fields when template is loaded
    LaunchedEffect(loadedTemplate) {
        loadedTemplate?.let { template ->
            templateName = template.name
            templateDescription = template.description ?: ""
        }
    }
    
    // Handle successful template creation
    LaunchedEffect(uiState) {
        if (uiState is WorkoutTemplateCreationUiState.Success) {
            onNavigateBack()
        }
    }
    
    // Load template data when in edit mode
    LaunchedEffect(editTemplateId) {
        editTemplateId?.let { templateId ->
            viewModel.loadTemplateForEditing(templateId)
        }
    }
    
    // Handle selected exercise from navigation
    val selectedExercise = savedStateHandle?.get<com.example.liftrix.domain.model.ExerciseLibrary>("selected_exercise")
    
    timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: selectedExercise from savedStateHandle: ${selectedExercise?.name} (ID: ${selectedExercise?.id})")
    timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: savedStateHandle is null? ${savedStateHandle == null}")
    
    LaunchedEffect(selectedExercise) {
        timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: LaunchedEffect triggered for selectedExercise: ${selectedExercise?.name}")
        
        selectedExercise?.let { exercise ->
            timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: About to call addExerciseFromLibrary for: ${exercise.name} (ID: ${exercise.id})")
            viewModel.addExerciseFromLibrary(exercise)
            timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: addExerciseFromLibrary call completed")
            
            // Clear the saved state to prevent re-adding the same exercise
            savedStateHandle?.remove<com.example.liftrix.domain.model.ExerciseLibrary>("selected_exercise")
            timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: selectedExercise cleared from savedStateHandle")
        } ?: run {
            timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: selectedExercise is null, no action taken")
        }
    }
    
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) "Edit Template" else stringResource(R.string.create_template_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (isEditMode && editTemplateId != null) {
                                viewModel.updateTemplate(
                                    templateId = editTemplateId,
                                    name = templateName,
                                    description = templateDescription.takeIf { it.isNotBlank() },
                                    exercises = uiState.exercises
                                )
                            } else {
                                viewModel.createTemplate(
                                    name = templateName,
                                    description = templateDescription.takeIf { it.isNotBlank() },
                                    exercises = uiState.exercises
                                )
                            }
                        },
                        enabled = templateName.isNotBlank() && uiState !is WorkoutTemplateCreationUiState.Loading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = if (isEditMode) "Update template" else "Save template"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Template Basic Information
            TemplateBasicInfoCard(
                name = templateName,
                onNameChange = { templateName = it },
                description = templateDescription,
                onDescriptionChange = { templateDescription = it }
            )
            
            // Exercise Selector Section
            TemplateExerciseAddCard(
                onNavigateToExerciseSelection = onNavigateToExerciseSelection
            )
            
            // Exercises List Section with drag-and-drop (dynamic size based on exercises)
            timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: Rendering DragDropExerciseList with ${uiState.exercises.size} exercises")
            timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: Current uiState type: ${uiState::class.simpleName}")
            timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: Exercises: ${uiState.exercises.map { "${it.name} (${it.name})" }}")
            
            DragDropExerciseList(
                exercises = uiState.exercises,
                onReorder = viewModel::reorderExercises,
                onRemoveExercise = viewModel::removeExercise,
                onUpdateExercise = viewModel::updateExercise
            )
            
            // Loading/Error States
            when (val state = uiState) {
                is WorkoutTemplateCreationUiState.Loading -> {
                    LiftrixProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                is WorkoutTemplateCreationUiState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = state.error,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                else -> { /* No special handling needed */ }
            }
        }
    }
}

/**
 * Card for navigating to exercise selection screen
 */
@Composable
private fun TemplateExerciseAddCard(
    onNavigateToExerciseSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
            
            Text(
                text = "Browse exercises by category, equipment, or search to find the perfect exercises for your template.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            FilledTonalButton(
                onClick = onNavigateToExerciseSelection,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Browse Exercises")
            }
        }
    }
}

/**
 * Card for basic template information (name and description)
 */
@Composable
private fun TemplateBasicInfoCard(
    name: String,
    onNameChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Template Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Template Name") },
                placeholder = { Text("Enter template name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                label = { Text("Description (Optional)") },
                placeholder = { Text("Describe this workout template") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
        }
    }
}


// TemplateExercisesCard removed - replaced by DragDropExerciseList

/**
 * Individual exercise item in the template with actual set input rows
 */
@Composable
private fun ExerciseListItem(
    exercise: TemplateExercise,
    onRemove: () -> Unit,
    onUpdate: (TemplateExercise) -> Unit,
    modifier: Modifier = Modifier
) {
    // Track individual sets instead of just a target number
    var setInputs by remember { 
        mutableStateOf(
            if (exercise.targetSets != null && exercise.targetSets!! > 0) {
                List(exercise.targetSets!!) { index ->
                    SetInput(
                        setNumber = index + 1,
                        weight = exercise.targetWeight?.kilograms?.toString() ?: "",
                        reps = exercise.targetReps?.count?.toString() ?: ""
                    )
                }
            } else {
                listOf(SetInput(setNumber = 1, weight = "", reps = ""))
            }
        )
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Exercise header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove exercise",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            // Display all set input rows
            setInputs.forEachIndexed { index, setInput ->
                TemplateSetInputRow(
                    setInput = setInput,
                    onUpdateSet = { updatedSet ->
                        setInputs = setInputs.toMutableList().apply {
                            set(index, updatedSet)
                        }
                        updateExerciseFromSets(exercise, setInputs, onUpdate)
                    },
                    onRemoveSet = if (setInputs.size > 1) {
                        {
                            setInputs = setInputs.toMutableList().apply {
                                removeAt(index)
                                // Renumber remaining sets
                                forEachIndexed { i, set -> set(i, this[i].copy(setNumber = i + 1)) }
                            }
                            updateExerciseFromSets(exercise, setInputs, onUpdate)
                        }
                    } else null
                )
                
                if (index < setInputs.size - 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Add set button
            OutlinedButton(
                onClick = { 
                    setInputs = setInputs + SetInput(
                        setNumber = setInputs.size + 1,
                        weight = setInputs.lastOrNull()?.weight ?: "",
                        reps = setInputs.lastOrNull()?.reps ?: ""
                    )
                    updateExerciseFromSets(exercise, setInputs, onUpdate)
                },
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
 * Data class to track individual set inputs
 */
private data class SetInput(
    val setNumber: Int,
    val weight: String,
    val reps: String
)

/**
 * Individual set input row for template exercises
 */
@Composable
private fun TemplateSetInputRow(
    setInput: SetInput,
    onUpdateSet: (SetInput) -> Unit,
    onRemoveSet: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${setInput.setNumber}",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(24.dp)
        )
        
        OutlinedTextField(
            value = setInput.weight,
            onValueChange = { newValue ->
                if ((newValue.isEmpty() || newValue.toDoubleOrNull() != null) && newValue.length <= 6) {
                    onUpdateSet(setInput.copy(weight = newValue))
                }
            },
            label = { Text("Weight") },
            placeholder = { Text("50") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        
        OutlinedTextField(
            value = setInput.reps,
            onValueChange = { newValue ->
                if (newValue.all { it.isDigit() } && newValue.length <= 3) {
                    onUpdateSet(setInput.copy(reps = newValue))
                }
            },
            label = { Text("Reps") },
            placeholder = { Text("12") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        
        if (onRemoveSet != null) {
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
        } else {
            // Placeholder to maintain alignment when there's only one set
            Spacer(modifier = Modifier.size(40.dp))
        }
    }
}

/**
 * Helper function to update exercise from set inputs
 */
private fun updateExerciseFromSets(
    exercise: TemplateExercise,
    setInputs: List<SetInput>,
    onUpdate: (TemplateExercise) -> Unit
) {
    // Use the first set's values as the target values for the template
    val firstSet = setInputs.firstOrNull()
    val updatedExercise = exercise.copy(
        targetSets = setInputs.size,
        targetReps = firstSet?.reps?.toIntOrNull()?.let { com.example.liftrix.domain.model.Reps(it) },
        targetWeight = firstSet?.weight?.toDoubleOrNull()?.let { com.example.liftrix.domain.model.Weight.fromKilograms(it) }
    )
    onUpdate(updatedExercise)
}

/**
 * Helper function to update exercise with new parameters (legacy)
 */
private fun updateExercise(
    exercise: TemplateExercise,
    sets: String,
    reps: String,
    weight: String,
    onUpdate: (TemplateExercise) -> Unit
) {
    val updatedExercise = exercise.copy(
        targetSets = sets.toIntOrNull(),
        targetReps = reps.toIntOrNull()?.let { com.example.liftrix.domain.model.Reps(it) },
        targetWeight = weight.toDoubleOrNull()?.let { com.example.liftrix.domain.model.Weight.fromKilograms(it) }
    )
    onUpdate(updatedExercise)
}


@Preview(showBackground = true)
@Composable
private fun WorkoutTemplateCreationScreenPreview() {
    LiftrixTheme {
        // Preview implementation would go here
    }
}