package com.example.liftrix.ui.workout

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.sync.SyncStatus
import com.example.liftrix.ui.workout.templates.WorkoutTemplateSelectionScreen

import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Navigation state for managing screen transitions
private sealed class WorkoutScreenState {
    object WorkoutList : WorkoutScreenState()
    object WorkoutCreation : WorkoutScreenState()
    data class TemplateSelection(val onTemplateSelected: (String) -> Unit) : WorkoutScreenState()
}

/**
 * Legacy workout screen - DEPRECATED
 * 
 * This screen has been replaced by the modern WorkoutTemplatesDashboard + ActiveWorkoutScreen flow.
 * It's kept for backward compatibility but should not be used in new navigation.
 * 
 * Use WorkoutTemplatesDashboard instead for the modern template-focused UI with:
 * - Persistent timer integration
 * - Modern session tracking with ActiveWorkoutScreen
 * - Template management and quick workout creation
 * 
 * @deprecated Use WorkoutTemplatesDashboard and WorkoutFlow navigation instead
 */
@Deprecated(
    message = "Use WorkoutTemplatesDashboard and WorkoutFlow navigation instead",
    replaceWith = ReplaceWith("WorkoutTemplatesDashboard")
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    user: User,
    modifier: Modifier = Modifier,
    onNavigateToCustomWorkout: () -> Unit = {},
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    val uiState: WorkoutUiState by viewModel.uiState.collectAsState()
    val currentUser: User? by viewModel.currentUser.collectAsState()
    val snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
    var screenState: WorkoutScreenState by remember { mutableStateOf(WorkoutScreenState.WorkoutList) }

    // Show error messages
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    when (screenState) {
        is WorkoutScreenState.WorkoutList -> {
            WorkoutListScreen(
                uiState = uiState,
                user = user,
                snackbarHostState = snackbarHostState,
                onNavigateToWorkoutCreation = {
                    // Use modern WorkoutFlow navigation instead of legacy creation screen
                    onNavigateToCustomWorkout()
                },
                onWorkoutAction = { workout: Workout, action: WorkoutAction ->
                    when (action) {
                        WorkoutAction.Start -> {
                            viewModel.startWorkout(workout)
                        }
                        WorkoutAction.Resume -> {
                            viewModel.startWorkout(workout)
                        }
                        WorkoutAction.Complete -> {
                            viewModel.completeWorkout(workout)
                        }
                        WorkoutAction.Cancel -> {
                            // TODO: Implement cancelWorkout in ViewModel
                        }
                    }
                },
                onSyncNow = { viewModel.syncNow() },
                onClearError = { viewModel.clearError() },
                modifier = modifier
            )
        }
        is WorkoutScreenState.WorkoutCreation -> {
            // This state is deprecated - navigation now goes directly to modern WorkoutFlow
            // Fallback to WorkoutList if this state is somehow reached
            screenState = WorkoutScreenState.WorkoutList
        }
        is WorkoutScreenState.TemplateSelection -> {
            val templateSelectionState = screenState as WorkoutScreenState.TemplateSelection
            WorkoutTemplateSelectionScreen(
                onTemplateSelected = { templateId ->
                    templateSelectionState.onTemplateSelected(templateId.value)
                    screenState = WorkoutScreenState.WorkoutCreation
                },
                onNavigateBack = {
                    screenState = WorkoutScreenState.WorkoutList
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutListScreen(
    uiState: WorkoutUiState,
    user: User,
    snackbarHostState: SnackbarHostState,
    onNavigateToWorkoutCreation: () -> Unit,
    onWorkoutAction: (Workout, WorkoutAction) -> Unit,
    onSyncNow: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Workouts",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // Sync status indicator
                    when (uiState.syncStatus) {
                        is SyncStatus.Syncing -> {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(24.dp)
                                    .semantics { contentDescription = "Syncing workouts" },
                                strokeWidth = 2.dp
                            )
                        }
                        is SyncStatus.Error -> {
                            IconButton(
                                onClick = onSyncNow,
                                modifier = Modifier.semantics { 
                                    contentDescription = "Retry sync" 
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry sync",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        else -> {
                            if (uiState.unsyncedCount > 0) {
                                IconButton(
                                    onClick = onSyncNow,
                                    modifier = Modifier.semantics { 
                                        contentDescription = "Sync ${uiState.unsyncedCount} unsynced workouts" 
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Sync workouts",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        WorkoutContent(
            uiState = uiState,
            user = user,
            onWorkoutAction = onWorkoutAction,
            onNavigateToWorkoutCreation = onNavigateToWorkoutCreation,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@Composable
private fun WorkoutContent(
    uiState: WorkoutUiState,
    user: User,
    onWorkoutAction: (Workout, WorkoutAction) -> Unit,
    onNavigateToWorkoutCreation: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        uiState.isLoading -> {
            LoadingContent(modifier = modifier)
        }
        uiState.workouts.isEmpty() -> {
            EmptyWorkoutsContent(
                user = user,
                onNavigateToWorkoutCreation = onNavigateToWorkoutCreation,
                modifier = modifier
            )
        }
        else -> {
            WorkoutList(
                workouts = uiState.workouts,
                syncStatus = uiState.syncStatus,
                onWorkoutAction = onWorkoutAction,
                modifier = modifier
            )
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.semantics { 
                    contentDescription = "Loading workouts" 
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading workouts...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EmptyWorkoutsContent(
    user: User,
    onNavigateToWorkoutCreation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Welcome, ${user.displayName ?: "Athlete"}!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ready to start your fitness journey?",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            
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
                        text = "Create Your First Workout",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Start your fitness journey by creating a simple workout with exercises, sets, and reps.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = onNavigateToWorkoutCreation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { 
                                contentDescription = "Create your first workout" 
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assignment,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Your First Workout")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Quick Start Options",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Use the floating + button to access workout creation options including templates and custom workouts.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tap the floating + button for options",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun WorkoutList(
    workouts: List<Workout>,
    syncStatus: SyncStatus,
    onWorkoutAction: (Workout, WorkoutAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Sync status bar
        if (syncStatus is SyncStatus.Syncing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Syncing workouts in progress" }
            )
        }
        
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = workouts,
                key = { it.id.value }
            ) { workout ->
                WorkoutCard(
                    workout = workout,
                    onAction = { action -> onWorkoutAction(workout, action) }
                )
            }
        }
    }
}

@Composable
private fun WorkoutCard(
    workout: Workout,
    onAction: (WorkoutAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { 
                contentDescription = "Workout: ${workout.name}, ${workout.status.name}" 
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = workout.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = workout.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                WorkoutStatusChip(status = workout.status)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                WorkoutStat(
                    label = "Exercises",
                    value = workout.exercises.size.toString()
                )
                WorkoutStat(
                    label = "Sets",
                    value = "${workout.getCompletedSets()}/${workout.getTotalSets()}"
                )
                WorkoutStat(
                    label = "Volume",
                    value = "${String.format("%.1f", workout.calculateTotalVolume().kilograms)} kg"
                )
            }
            
            // Progress bar
            if (workout.status == WorkoutStatus.IN_PROGRESS || workout.status == WorkoutStatus.PAUSED) {
                Spacer(modifier = Modifier.height(12.dp))
                val progress = (workout.getCompletionPercentage() / 100f).toFloat()
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { 
                            contentDescription = "Workout progress: ${String.format("%.1f", workout.getCompletionPercentage())}%" 
                        }
                )
                Text(
                    text = "${String.format("%.1f", workout.getCompletionPercentage())}% Complete",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            // Action buttons
            if (workout.status != WorkoutStatus.COMPLETED && workout.status != WorkoutStatus.CANCELLED) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (workout.status) {
                        WorkoutStatus.PLANNED -> {
                            OutlinedButton(
                                onClick = { onAction(WorkoutAction.Start) },
                                modifier = Modifier.semantics { 
                                    contentDescription = "Start workout" 
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Start")
                            }
                        }
                        WorkoutStatus.IN_PROGRESS -> {
                            OutlinedButton(
                                onClick = { onAction(WorkoutAction.Complete) },
                                modifier = Modifier.semantics { 
                                    contentDescription = "Complete workout" 
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Complete")
                            }
                        }
                        WorkoutStatus.PAUSED -> {
                            OutlinedButton(
                                onClick = { onAction(WorkoutAction.Resume) },
                                modifier = Modifier.semantics { 
                                    contentDescription = "Resume workout" 
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Resume")
                            }
                        }
                        else -> { /* No actions for completed/cancelled */ }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutStatusChip(
    status: WorkoutStatus,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, contentColor) = when (status) {
        WorkoutStatus.PLANNED -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        WorkoutStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        WorkoutStatus.PAUSED -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        WorkoutStatus.COMPLETED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        WorkoutStatus.CANCELLED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    
    Card(
        modifier = modifier.semantics { 
            contentDescription = "Status: ${status.name}" 
        },
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
            contentColor = contentColor
        )
    ) {
        Text(
            text = status.name.replace("_", " "),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun WorkoutStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.semantics { 
            contentDescription = "$label: $value" 
        }
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

enum class WorkoutAction {
    Start,
    Resume,
    Complete,
    Cancel
} 