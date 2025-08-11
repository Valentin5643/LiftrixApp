package com.example.liftrix.ui.workout.active

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.sp
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.foundation.BorderStroke
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import com.example.liftrix.ui.workout.components.SecondaryActionButton
import com.example.liftrix.ui.workout.components.SaveAsTemplateDialog
import com.example.liftrix.ui.workout.components.SaveQuickWorkoutAsTemplateDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.delay
import androidx.lifecycle.SavedStateHandle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.model.WorkoutSessionId
import com.example.liftrix.ui.common.LoadingScreen
import com.example.liftrix.ui.common.ErrorScreen
import com.example.liftrix.ui.theme.LiftrixTheme
import java.time.Instant

/**
 * 🔥 NEW: Unified active workout screen using single session model
 * 
 * This screen replaces the complex ActiveWorkoutScreen with a simplified version
 * that uses UnifiedWorkoutSession as the single source of truth. No more dual
 * state management or complex synchronization.
 * 
 * Key features:
 * - Single source of truth (UnifiedWorkoutSession)
 * - Session-scoped exercise management
 * - Real-time session updates
 * - Clean Material 3 UI
 * - Simplified state management
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedActiveWorkoutScreen(
    onNavigateBack: () -> Unit,
    onAddExercise: () -> Unit,
    onNavigateToExercise: (String) -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: UnifiedActiveWorkoutViewModel = hiltViewModel(),
    savedStateHandle: SavedStateHandle? = null,
    isBlankWorkout: Boolean = false,
    templateId: String? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val session by viewModel.currentSession.collectAsState()
    
    // 🔥 IMPROVED: Handle screen entry with proper session creation timing
    LaunchedEffect(Unit) {
        timber.log.Timber.d("🔥 GUARD-DEBUG: Screen entry - isBlankWorkout: $isBlankWorkout, templateId: $templateId, session: ${session?.name ?: "null"}")
        
        // Ensure session exists before processing exercises
        if (session == null) {
            when {
                isBlankWorkout -> {
                    timber.log.Timber.d("🔥 GUARD-DEBUG: Starting blank workout...")
                    viewModel.startBlankWorkout()
                }
                templateId != null -> {
                    timber.log.Timber.d("🔥 GUARD-DEBUG: Starting template workout (ID: $templateId)...")
                    viewModel.startTemplateWorkout(templateId)
                }
                else -> {
                    timber.log.Timber.w("🔥 GUARD-DEBUG: No session and no creation parameters - navigating back")
                    onNavigateBack()
                    return@LaunchedEffect
                }
            }
            
            // Wait for session creation with timeout
            var retries = 0
            while (session == null && retries < 20) { // 2 second timeout
                delay(100)
                retries++
            }
            
            if (session == null) {
                timber.log.Timber.e("🔥 GUARD-DEBUG: Session creation timeout - forcing error state")
                onNavigateBack()
            }
        }
    }

    // 🔥 TIMEOUT GUARD: Prevent infinite loading
    LaunchedEffect(uiState) {
        if (uiState is UnifiedActiveWorkoutUiState.Loading) {
            timber.log.Timber.d("🔥 TIMEOUT-DEBUG: Loading state detected, starting 3-second timeout...")
            delay(3000)
            if (uiState is UnifiedActiveWorkoutUiState.Loading) {
                timber.log.Timber.e("🔥 TIMEOUT-DEBUG: Still loading after 3 seconds - forcing error state")
                viewModel.retry() // This should trigger error handling
            }
        }
    }

    // Debug logging for UI state changes
    LaunchedEffect(uiState) {
        timber.log.Timber.d("🔥 SCREEN-DEBUG: UI State changed to: ${uiState::class.simpleName}")
        when (uiState) {
            is UnifiedActiveWorkoutUiState.Loading -> timber.log.Timber.d("🔥 SCREEN-DEBUG: Showing loading screen")
            is UnifiedActiveWorkoutUiState.Success -> timber.log.Timber.d("🔥 SCREEN-DEBUG: Showing success screen with session: ${(uiState as UnifiedActiveWorkoutUiState.Success).session.name}")
            is UnifiedActiveWorkoutUiState.Error -> timber.log.Timber.d("🔥 SCREEN-DEBUG: Showing error screen: ${(uiState as UnifiedActiveWorkoutUiState.Error).message}")
            is UnifiedActiveWorkoutUiState.NoSession -> timber.log.Timber.d("🔥 SCREEN-DEBUG: Showing no session screen")
            is UnifiedActiveWorkoutUiState.WorkoutCompleted -> timber.log.Timber.d("🔥 SCREEN-DEBUG: Workout completed - should navigate away")
        }
    }
    
    // Handle selected exercise from navigation - IMPROVED timing
    val selectedExercise = savedStateHandle?.get<com.example.liftrix.domain.model.ExerciseLibrary>("selected_exercise")
    
    // 🔥 IMPROVED: Process exercises AFTER session is confirmed to exist
    LaunchedEffect(session, selectedExercise) {
        timber.log.Timber.d("🔥 EXERCISE-ADD-DEBUG: LaunchedEffect triggered - session exists: ${session != null}, exercise: ${selectedExercise?.name ?: "null"}")
        
        if (session != null && selectedExercise != null) {
            timber.log.Timber.d("🔥 EXERCISE-ADD-DEBUG: Both session and exercise available - proceeding with addition")
            timber.log.Timber.d("🔥 EXERCISE-ADD-DEBUG: Session: ${session!!.name}, Exercise: ${selectedExercise!!.name}")
            
            try {
                viewModel.addExerciseToSession(selectedExercise!!)
                timber.log.Timber.d("🔥 EXERCISE-ADD-DEBUG: Exercise addition completed successfully")
                
                // Clear the saved state to prevent re-adding
                savedStateHandle?.remove<com.example.liftrix.domain.model.ExerciseLibrary>("selected_exercise")
                timber.log.Timber.d("🔥 EXERCISE-ADD-DEBUG: Cleared exercise from savedStateHandle")
                
            } catch (e: Exception) {
                timber.log.Timber.e(e, "🔥 EXERCISE-ADD-DEBUG: Error adding exercise to session")
            }
        } else {
            if (selectedExercise != null && session == null) {
                timber.log.Timber.w("🔥 EXERCISE-ADD-DEBUG: Exercise selected but no session - will retry when session is created")
            }
        }
    }
    
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = session?.name ?: "Active Workout",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        
                        session?.let { workoutSession ->
                            Text(
                                text = workoutSession.getFormattedDuration(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
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
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    var showMenu by remember { mutableStateOf(false) }
                    
                    IconButton(
                        onClick = { showMenu = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Stop Workout") },
                            onClick = {
                                showMenu = false
                                viewModel.discardWorkout()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        when (uiState) {
            is UnifiedActiveWorkoutUiState.Loading -> {
                LoadingScreen(
                    modifier = Modifier.padding(paddingValues)
                )
            }
            
            is UnifiedActiveWorkoutUiState.NoSession -> {
                // Show message that workout is being prepared
                LoadingScreen(
                    modifier = Modifier.padding(paddingValues)
                )
            }
            
            is UnifiedActiveWorkoutUiState.Error -> {
                val errorState = uiState as UnifiedActiveWorkoutUiState.Error
                ErrorScreen(
                    message = errorState.message,
                    onRetry = { viewModel.retry() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
            
            is UnifiedActiveWorkoutUiState.WorkoutCompleted -> {
                // Navigate to Home immediately when workout is completed
                LaunchedEffect(Unit) {
                    onNavigateToHome()
                }
            }
            
            is UnifiedActiveWorkoutUiState.Success -> {
                val successState = uiState as UnifiedActiveWorkoutUiState.Success
                
                // 🔥 CRITICAL DEBUG: Log UI state and session data
                timber.log.Timber.d("🔥 UI-RENDER-DEBUG: Rendering Success state")
                timber.log.Timber.d("🔥 UI-RENDER-DEBUG: Session exercises count: ${successState.session.exercises.size}")
                timber.log.Timber.d("🔥 UI-RENDER-DEBUG: Session exercises: ${successState.session.exercises.map { it.name }}")
                
                ActiveWorkoutContent(
                    session = successState.session,
                    isCompleting = successState.isCompleting,
                    onNavigateToExercise = onNavigateToExercise,
                    onAddExercise = onAddExercise,
                    onRemoveExercise = { exerciseId ->
                        viewModel.removeExercise(exerciseId)
                    },
                    onAddSet = { exerciseId ->
                        viewModel.addSetToExercise(exerciseId)
                    },
                    onUpdateSet = { exerciseId, setNumber, updatedSet ->
                        viewModel.updateSetInExercise(exerciseId, setNumber, updatedSet)
                    },
                    onCompleteWorkout = {
                        viewModel.completeWorkout()
                    },
                    modifier = Modifier.padding(paddingValues)
                )
                
                // Show template update dialog when needed
                if (successState.showSaveAsTemplateDialog) {
                    SaveAsTemplateDialog(
                        workoutName = successState.session.name,
                        onUpdateTemplate = {
                            viewModel.updateOriginalTemplate()
                        },
                        onSkip = {
                            viewModel.skipTemplateUpdate()
                        },
                        onDismiss = {
                            viewModel.dismissTemplateUpdateDialog()
                        }
                    )
                }
                
                // Show Quick workout save dialog when needed
                if (successState.showSaveQuickWorkoutDialog) {
                    SaveQuickWorkoutAsTemplateDialog(
                        show = true,
                        defaultTemplateName = "${successState.session.name} Template",
                        onSaveAsTemplate = { templateName ->
                            viewModel.saveQuickWorkoutAsTemplate(templateName)
                        },
                        onSkip = {
                            viewModel.skipSaveQuickWorkoutAsTemplate()
                        },
                        onDismiss = {
                            viewModel.dismissSaveQuickWorkoutDialog()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveWorkoutContent(
    session: UnifiedWorkoutSession,
    isCompleting: Boolean,
    onNavigateToExercise: (String) -> Unit,
    onAddExercise: () -> Unit,
    onRemoveExercise: (String) -> Unit,
    onAddSet: (String) -> Unit,
    onUpdateSet: (String, Int, com.example.liftrix.domain.model.SessionSet) -> Unit,
    onCompleteWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Debug logging
    timber.log.Timber.d("🔥 CONTENT-DEBUG: ActiveWorkoutContent rendering")
    timber.log.Timber.d("🔥 CONTENT-DEBUG: session.exercises.size = ${session.exercises.size}")
    timber.log.Timber.d("🔥 CONTENT-DEBUG: session.exercises.isEmpty() = ${session.exercises.isEmpty()}")
    
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Session stats card
        item {
            SessionStatsCard(
                session = session,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Exercise list or empty state
        if (session.exercises.isNotEmpty()) {
            timber.log.Timber.d("🔥 CONTENT-DEBUG: Rendering ${session.exercises.size} exercises")
            
            itemsIndexed(session.exercises) { index, exercise ->
                timber.log.Timber.d("🔥 CONTENT-DEBUG: Rendering ExerciseCard for: ${exercise.name} at index $index")
                
                ExerciseCard(
                    exercise = exercise,
                    isCurrent = index == session.currentExerciseIndex,
                    onExerciseClick = { onNavigateToExercise(exercise.exerciseId.value) },
                    onRemoveExercise = { onRemoveExercise(exercise.exerciseId.value) },
                    onAddSet = { onAddSet(exercise.exerciseId.value) },
                    onUpdateSet = { setNumber, updatedSet ->
                        onUpdateSet(exercise.exerciseId.value, setNumber, updatedSet)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            timber.log.Timber.d("🔥 CONTENT-DEBUG: Showing EmptyExerciseList - no exercises in session")
            
            item {
                EmptyExerciseList(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // Add Exercise button
        item {
            SecondaryActionButton(
                text = "Add Exercise",
                onClick = onAddExercise,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Default.Add
            )
        }
        
        // Complete Workout button at bottom
        item {
            Spacer(modifier = Modifier.height(16.dp))
            CompleteWorkoutButton(
                isCompleting = isCompleting,
                onCompleteWorkout = onCompleteWorkout,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CompleteWorkoutButton(
    isCompleting: Boolean,
    onCompleteWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    PrimaryActionButton(
        text = if (isCompleting) "Completing..." else "Complete Workout",
        onClick = onCompleteWorkout,
        modifier = modifier.height(56.dp),
        enabled = !isCompleting,
        leadingIcon = if (isCompleting) null else Icons.Default.Check
    )
}

@Composable
private fun SessionStatsCard(
    session: UnifiedWorkoutSession,
    modifier: Modifier = Modifier
) {
    val stats = session.getSessionStats()
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Workout Progress",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Text(
                        text = "${stats.completionPercentage.toInt()}% Complete",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                
                // Status indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when (session.sessionStatus) {
                                    UnifiedWorkoutSession.SessionStatus.ACTIVE -> 
                                        MaterialTheme.colorScheme.primary
                                    UnifiedWorkoutSession.SessionStatus.PAUSED -> 
                                        MaterialTheme.colorScheme.outline
                                    UnifiedWorkoutSession.SessionStatus.COMPLETED ->
                                        MaterialTheme.colorScheme.tertiary
                                    UnifiedWorkoutSession.SessionStatus.FAILED_TO_SAVE ->
                                        MaterialTheme.colorScheme.error
                                }
                            )
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = when (session.sessionStatus) {
                            UnifiedWorkoutSession.SessionStatus.ACTIVE -> "Active"
                            UnifiedWorkoutSession.SessionStatus.PAUSED -> "Paused"
                            UnifiedWorkoutSession.SessionStatus.COMPLETED -> "Completed"
                            UnifiedWorkoutSession.SessionStatus.FAILED_TO_SAVE -> "Save Failed"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    value = stats.totalExercises.toString(),
                    label = "Exercises",
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                StatItem(
                    value = "${stats.completedSets}/${stats.totalSets}",
                    label = "Sets",
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                StatItem(
                    value = "${stats.totalVolume.toInt()} kg",
                    label = "Volume",
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = color.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun ExerciseCard(
    exercise: com.example.liftrix.domain.model.SessionExercise,
    isCurrent: Boolean,
    onExerciseClick: () -> Unit,
    onRemoveExercise: () -> Unit,
    onAddSet: () -> Unit,
    onUpdateSet: (Int, com.example.liftrix.domain.model.SessionSet) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isCurrent) 8.dp else 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Exercise header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                
                if (isCurrent) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = "Current exercise",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Sets list - directly visible like modern apps
            exercise.sets.forEachIndexed { index, set ->
                SetInputRow(
                    setNumber = set.setNumber,
                    set = set,
                    onSetUpdated = { updatedSet ->
                        timber.log.Timber.d("🔥 SET-UPDATE: Set ${set.setNumber} updated: reps=${updatedSet.actualReps}, weight=${updatedSet.actualWeight}, completed=${updatedSet.isCompleted()}")
                        onUpdateSet(set.setNumber, updatedSet)
                    }
                )
                if (index < exercise.sets.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Add set button
            Button(
                onClick = {
                    timber.log.Timber.d("🔥 SET-ADD: Adding new set to ${exercise.name}")
                    onAddSet()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
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


@Composable
private fun EmptyExerciseList(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "No exercises yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = "Tap the + button to add your first exercise",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}


@Composable
private fun SetInputRow(
    setNumber: Int,
    set: com.example.liftrix.domain.model.SessionSet,
    onSetUpdated: (com.example.liftrix.domain.model.SessionSet) -> Unit,
    modifier: Modifier = Modifier
) {
    var repsText by remember { mutableStateOf(set.actualReps?.toString() ?: set.targetReps?.toString() ?: "") }
    var weightText by remember { mutableStateOf(set.actualWeight?.kilograms?.toString() ?: set.targetWeight?.kilograms?.toString() ?: "") }
    var isCompleted by remember { mutableStateOf(set.isCompleted()) }
    
    val backgroundColor = if (isCompleted) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Set number
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        if (isCompleted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        },
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = setNumber.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCompleted) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = FontWeight.Medium
                )
            }
            
            // Target/Previous indicator
            Column(
                modifier = Modifier.width(55.dp)
            ) {
                Text(
                    text = "Target",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = "${set.targetReps ?: "-"} × ${set.targetWeight?.kilograms?.toInt() ?: "-"}kg",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
            
            // Reps input
            OutlinedTextField(
                value = repsText,
                onValueChange = { newValue ->
                    repsText = newValue
                    val reps = newValue.toIntOrNull()
                    val weight = weightText.toDoubleOrNull()?.let { 
                        com.example.liftrix.domain.model.Weight(it) 
                    }
                    val updatedSet = set.copy(
                        actualReps = reps,
                        actualWeight = weight
                    )
                    onSetUpdated(updatedSet)
                },
                label = { Text("Reps", style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.width(75.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            
            // Weight input
            OutlinedTextField(
                value = weightText,
                onValueChange = { newValue ->
                    weightText = newValue
                    val reps = repsText.toIntOrNull()
                    val weight = newValue.toDoubleOrNull()?.let { 
                        com.example.liftrix.domain.model.Weight(it) 
                    }
                    val updatedSet = set.copy(
                        actualReps = reps,
                        actualWeight = weight
                    )
                    onSetUpdated(updatedSet)
                },
                label = { Text("Weight", style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.width(85.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
            
            // DEBUG BUTTON - Super visible for testing
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable {
                        isCompleted = !isCompleted
                        val updatedSet = if (isCompleted) {
                            set.copy(
                                actualReps = repsText.toIntOrNull(),
                                actualWeight = weightText.toDoubleOrNull()?.let { 
                                    com.example.liftrix.domain.model.Weight(it) 
                                },
                                completedAt = Instant.now()
                            )
                        } else {
                            set.copy(completedAt = null)
                        }
                        onSetUpdated(updatedSet)
                    }
                    .semantics { 
                        contentDescription = if (isCompleted) "Mark set as incomplete" else "Mark set as complete"
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isCompleted) "✓" else "○",
                    color = if (isCompleted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 20.sp,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

@Preview
@Composable
private fun UnifiedActiveWorkoutScreenPreview() {
    LiftrixTheme {
        // Mock session data
        val mockSession = UnifiedWorkoutSession(
            id = WorkoutSessionId("preview"),
            userId = "user1",
            name = "Upper Body Strength",
            exercises = listOf(),
            sessionStatus = UnifiedWorkoutSession.SessionStatus.ACTIVE,
            startedAt = Instant.now().minusSeconds(1800), // 30 minutes ago
            elapsedTimeSeconds = 1800
        )
        
        ActiveWorkoutContent(
            session = mockSession,
            isCompleting = false,
            onNavigateToExercise = { },
            onAddExercise = { },
            onRemoveExercise = { },
            onAddSet = { },
            onUpdateSet = { _, _, _ -> },
            onCompleteWorkout = { }
        )
    }
}