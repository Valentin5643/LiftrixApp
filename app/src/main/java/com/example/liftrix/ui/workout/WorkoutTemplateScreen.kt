package com.example.liftrix.ui.workout

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.SavedStateHandle
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.ui.workout.create.WorkoutTemplateCreationViewModel
import com.example.liftrix.ui.workout.creation.components.DragDropExerciseList
import com.example.liftrix.ui.common.state.dataOrNull

/**
 * Template creation screen for building workout templates without timer.
 * 
 * This screen provides:
 * - Exercise selection and management
 * - Set configuration
 * - Template saving functionality
 * - No timer functionality (templates are for planning)
 * 
 * @param onNavigateBack Callback for back navigation
 * @param onAddExercise Callback for exercise addition
 * @param modifier Modifier for styling
 * @param viewModel ViewModel for template creation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutTemplateScreen(
    onNavigateBack: () -> Unit,
    onAddExercise: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: WorkoutTemplateCreationViewModel = hiltViewModel(),
    savedStateHandle: SavedStateHandle? = null
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    var templateName by remember { mutableStateOf("") }
    var templateDescription by remember { mutableStateOf("") }
    
    // Handle selected exercise from navigation
    val selectedExercise = savedStateHandle?.get<com.example.liftrix.domain.model.ExerciseLibrary>("selected_exercise")
    
    // 🔥 CRITICAL DEBUG: Log template savedStateHandle activity
    timber.log.Timber.d("🔥 TEMPLATE-RECEIVE-DEBUG: savedStateHandle available? ${savedStateHandle != null}")
    timber.log.Timber.d("🔥 TEMPLATE-RECEIVE-DEBUG: selectedExercise from savedStateHandle: ${selectedExercise?.name ?: "null"}")
    
    LaunchedEffect(selectedExercise) {
        timber.log.Timber.d("🔥 TEMPLATE-RECEIVE-DEBUG: LaunchedEffect triggered, selectedExercise: ${selectedExercise?.name ?: "null"}")
        
        selectedExercise?.let { exercise ->
            timber.log.Timber.d("🔥 TEMPLATE-RECEIVE-DEBUG: About to call viewModel.addExerciseFromLibrary for: ${exercise.name}")
            timber.log.Timber.d("🔥 TEMPLATE-RECEIVE-DEBUG: Exercise ID: ${exercise.id}")
            
            try {
                viewModel.addExerciseFromLibrary(exercise)
                timber.log.Timber.d("🔥 TEMPLATE-RECEIVE-DEBUG: viewModel.addExerciseFromLibrary completed successfully")
                
                // Clear the saved state to prevent re-adding the same exercise
                savedStateHandle?.remove<com.example.liftrix.domain.model.ExerciseLibrary>("selected_exercise")
                timber.log.Timber.d("🔥 TEMPLATE-RECEIVE-DEBUG: Cleared selected_exercise from savedStateHandle")
                
            } catch (e: Exception) {
                timber.log.Timber.e(e, "🔥 TEMPLATE-RECEIVE-DEBUG: Error in viewModel.addExerciseFromLibrary")
            }
        } ?: run {
            timber.log.Timber.d("🔥 TEMPLATE-RECEIVE-DEBUG: selectedExercise is null, no action taken")
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Create Template",
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
                    // Save template button - always visible, disabled when conditions not met
                    val exercises = uiState.dataOrNull()?.exercises ?: emptyList()
                    val canSave = exercises.isNotEmpty() && templateName.isNotBlank()
                    IconButton(
                        onClick = { 
                            if (canSave) {
                                // Save template without timer
                                viewModel.createTemplate(templateName, templateDescription, exercises)
                                onNavigateBack()
                            }
                        },
                        enabled = canSave,
                        modifier = Modifier.semantics {
                            contentDescription = if (canSave) "Save template" else "Save template (disabled - need name and exercises)"
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = null,
                            tint = if (canSave) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        )
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
            
            // Template Info Section
            item {
                TemplateInfoCard(
                    templateName = templateName,
                    templateDescription = templateDescription,
                    onNameChange = { templateName = it },
                    onDescriptionChange = { templateDescription = it }
                )
            }
            
            // Exercise Selection Section
            item {
                ExerciseSelectionCard(
                    onNavigateToExerciseSelection = onAddExercise
                )
            }
            
            // Exercises Section
            item {
                // 🔥 CRITICAL DEBUG: Log template UI state and exercises
                val exercises = uiState.dataOrNull()?.exercises ?: emptyList()
                timber.log.Timber.d("🔥 TEMPLATE-UI-DEBUG: Rendering template exercises section")
                timber.log.Timber.d("🔥 TEMPLATE-UI-DEBUG: exercises.size = ${exercises.size}")
                timber.log.Timber.d("🔥 TEMPLATE-UI-DEBUG: exercises = ${exercises.map { it.name }}")
                
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
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Card for template name and description input
 */
@Composable
private fun TemplateInfoCard(
    templateName: String,
    templateDescription: String,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Template Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            OutlinedTextField(
                value = templateName,
                onValueChange = onNameChange,
                label = { Text("Template Name") },
                placeholder = { Text("Enter template name") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = templateDescription,
                onValueChange = onDescriptionChange,
                label = { Text("Description (Optional)") },
                placeholder = { Text("Describe your template") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }
    }
}

/**
 * Card for exercise selection
 */
@Composable
private fun ExerciseSelectionCard(
    onNavigateToExerciseSelection: () -> Unit,
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


