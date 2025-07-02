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
    selectedExerciseId: String? = null,
    isCustomExercise: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: WorkoutTemplateCreationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    var templateName by remember { mutableStateOf("") }
    var templateDescription by remember { mutableStateOf("") }
    
    // Handle successful template creation
    LaunchedEffect(uiState) {
        if (uiState is WorkoutTemplateCreationUiState.Success) {
            onNavigateBack()
        }
    }
    
    // Handle selected exercise from navigation
    LaunchedEffect(selectedExerciseId, isCustomExercise) {
        selectedExerciseId?.let { exerciseId ->
            viewModel.addExerciseById(exerciseId, isCustomExercise)
        }
    }
    
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.create_template_title),
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
                            viewModel.createTemplate(
                                name = templateName,
                                description = templateDescription.takeIf { it.isNotBlank() },
                                exercises = uiState.exercises
                            )
                        },
                        enabled = templateName.isNotBlank() && uiState !is WorkoutTemplateCreationUiState.Loading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save template"
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
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
            
            // Exercises List Section
            TemplateExercisesCard(
                exercises = uiState.exercises,
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


/**
 * Card for managing exercises in the template
 */
@Composable
private fun TemplateExercisesCard(
    exercises: List<TemplateExercise>,
    onRemoveExercise: (TemplateExercise) -> Unit,
    onUpdateExercise: (TemplateExercise) -> Unit,
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
                text = "Template Exercises (${exercises.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            if (exercises.isEmpty()) {
                Text(
                    text = "No exercises added yet. Use the exercise selector above to add exercises to your template.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )
            } else {
                exercises.forEach { exercise ->
                    ExerciseListItem(
                        exercise = exercise,
                        onRemove = { onRemoveExercise(exercise) },
                        onUpdate = onUpdateExercise
                    )
                }
            }
        }
    }
}

/**
 * Individual exercise item in the template with inline parameter editing
 */
@Composable
private fun ExerciseListItem(
    exercise: TemplateExercise,
    onRemove: () -> Unit,
    onUpdate: (TemplateExercise) -> Unit,
    modifier: Modifier = Modifier
) {
    var sets by remember { mutableStateOf(exercise.targetSets?.toString() ?: "1") }
    var reps by remember { mutableStateOf(exercise.targetReps?.count?.toString() ?: "") }
    var weight by remember { mutableStateOf(exercise.targetWeight?.kilograms?.toString() ?: "") }
    
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
            
            // Inline editable sets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "1",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(24.dp)
                )
                
                OutlinedTextField(
                    value = weight,
                    onValueChange = { newValue ->
                        if ((newValue.isEmpty() || newValue.toDoubleOrNull() != null) && newValue.length <= 6) {
                            weight = newValue
                            updateExercise(exercise, sets, reps, weight, onUpdate)
                        }
                    },
                    label = { Text("Weight") },
                    placeholder = { Text("50") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                
                OutlinedTextField(
                    value = reps,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() } && newValue.length <= 3) {
                            reps = newValue
                            updateExercise(exercise, sets, reps, weight, onUpdate)
                        }
                    },
                    label = { Text("Reps") },
                    placeholder = { Text("12") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(
                    onClick = { 
                        // Add set functionality would need to be implemented in ViewModel
                        // For now, just increment the sets value
                        val currentSets = sets.toIntOrNull() ?: 1
                        sets = (currentSets + 1).toString()
                        updateExercise(exercise, sets, reps, weight, onUpdate)
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add set",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            // Display target sets summary
            if (sets.toIntOrNull() != null && sets.toInt() > 1) {
                Text(
                    text = "Target: $sets sets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Helper function to update exercise with new parameters
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