package com.example.liftrix.ui.workout.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.state.dataOrNull
import com.example.liftrix.ui.common.state.WorkoutTemplateCreationUiState
import com.example.liftrix.ui.workout.components.PrimaryActionButton

/**
 * Screen for creating a new workout template from scratch.
 * 
 * Provides a form-based interface for users to:
 * - Enter template name and description
 * - Set estimated duration and difficulty
 * - Add exercises to the template
 * - Save the template for future use
 */
@Composable
fun WorkoutTemplateCreationScreen(
    onNavigateBack: () -> Unit,
    onNavigateToExerciseSelection: () -> Unit = {},
    editTemplateId: String? = null,
    initialFolderId: String? = null,
    modifier: Modifier = Modifier,
    viewModel: WorkoutTemplateCreationViewModel = hiltViewModel(),
    savedStateHandle: androidx.lifecycle.SavedStateHandle? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val loadedTemplate by viewModel.loadedTemplate.collectAsStateWithLifecycle()
    
    // Debug UI state changes
    LaunchedEffect(uiState) {
        val currentState = uiState
        timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: UI State changed to: ${currentState::class.simpleName}")
        when (currentState) {
            is WorkoutTemplateCreationUiState.Success -> {
                val exercises = currentState.data.exercises
                timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: Success state with ${exercises.size} exercises")
                timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: Exercise names: ${exercises.map { it.name }}")
            }
            is WorkoutTemplateCreationUiState.Loading -> {
                timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: Loading state")
            }
            is WorkoutTemplateCreationUiState.Error -> {
                timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: Error state: ${currentState.error.message}")
            }
            is WorkoutTemplateCreationUiState.Empty -> {
                timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: Empty state")
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
    
    // Note: Success handling is now managed through ViewModel events
    // Template creation success should trigger navigation through the ViewModel
    
    // Load template data when in edit mode
    LaunchedEffect(editTemplateId) {
        editTemplateId?.let { templateId ->
            viewModel.loadTemplateForEditing(templateId)
        }
    }
    
    // Handle initial folder ID for folder-aware template creation
    LaunchedEffect(initialFolderId) {
        if (initialFolderId != null) {
            viewModel.selectFolder(com.example.liftrix.domain.model.FolderId(initialFolderId))
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
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Template Basic Information
                TemplateBasicInfoCard(
                    name = templateName,
                    onNameChange = { templateName = it },
                    description = templateDescription,
                    onDescriptionChange = { templateDescription = it }
                )
            }
            
            // Exercises List Section with drag-and-drop
            val exercises = uiState.dataOrNull()?.exercises ?: emptyList()
            timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: Rendering exercise items with ${exercises.size} exercises")
            timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: Current uiState type: ${uiState::class.simpleName}")
            timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: Exercises: ${exercises.map { "${it.name} (${it.name})" }}")
            timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: UiState dataOrNull result: ${uiState.dataOrNull()}")
            timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: UiState data exercises count: ${uiState.dataOrNull()?.exercises?.size}")
            
            // Debug raw uiState
            when (uiState) {
                is WorkoutTemplateCreationUiState.Success -> {
                    timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: Success state data exercises: ${(uiState as WorkoutTemplateCreationUiState.Success).data.exercises.size}")
                    timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: Success state exercise names: ${(uiState as WorkoutTemplateCreationUiState.Success).data.exercises.map { it.name }}")
                }
                else -> {
                    timber.log.Timber.d("🔥 TEMPLATE-SCREEN-DEBUG: Not in Success state: ${uiState::class.simpleName}")
                }
            }
            
            if (exercises.isNotEmpty()) {
                itemsIndexed(
                    items = exercises,
                    key = { _, exercise -> exercise.exerciseId.value }
                ) { index, exercise ->
                    ExerciseListItem(
                        exercise = exercise,
                        onRemove = { viewModel.removeExercise(exercise) },
                        onUpdate = viewModel::updateExercise,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // Browse Exercises button
            item {
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
            
            // Save Template button at bottom
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SaveTemplateButton(
                    isEditMode = isEditMode,
                    editTemplateId = editTemplateId,
                    templateName = templateName,
                    templateDescription = templateDescription,
                    exercises = uiState.dataOrNull()?.exercises ?: emptyList(),
                    isLoading = uiState is WorkoutTemplateCreationUiState.Loading,
                    onSaveTemplate = { name, description, exercises ->
                        if (isEditMode && editTemplateId != null) {
                            viewModel.updateTemplate(
                                templateId = editTemplateId,
                                name = name,
                                description = description,
                                exercises = exercises
                            )
                        } else {
                            viewModel.createTemplate(
                                name = name,
                                description = description,
                                exercises = exercises
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Loading/Error States
            val currentState = uiState
            when (currentState) {
                is WorkoutTemplateCreationUiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            LiftrixProgressIndicator()
                        }
                    }
                }
                is WorkoutTemplateCreationUiState.Error -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = currentState.error.message,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                else -> { /* No special handling needed */ }
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

/**
 * Save Template button similar to Complete Workout button in active workout screen
 */
@Composable
private fun SaveTemplateButton(
    isEditMode: Boolean,
    editTemplateId: String?,
    templateName: String,
    templateDescription: String,
    exercises: List<TemplateExercise>,
    isLoading: Boolean,
    onSaveTemplate: (String, String?, List<TemplateExercise>) -> Unit,
    modifier: Modifier = Modifier
) {
    PrimaryActionButton(
        text = if (isLoading) {
            if (isEditMode) "Updating..." else "Saving..."
        } else {
            if (isEditMode) "Update Template" else "Save Template"
        },
        onClick = {
            onSaveTemplate(
                templateName,
                templateDescription.takeIf { it.isNotBlank() },
                exercises
            )
        },
        modifier = modifier.height(56.dp),
        enabled = !isLoading && templateName.isNotBlank(),
        leadingIcon = if (isLoading) null else Icons.Default.Check
    )
}


@Preview(showBackground = true)
@Composable
private fun WorkoutTemplateCreationScreenPreview() {
    LiftrixTheme {
        // Preview implementation would go here
    }
}