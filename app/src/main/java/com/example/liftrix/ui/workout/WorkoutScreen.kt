package com.example.liftrix.ui.workout

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import com.example.liftrix.ui.workout.components.SecondaryActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.example.liftrix.R
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.ui.common.state.WorkoutScreenData
import com.example.liftrix.ui.common.state.WorkoutUiState

/**
 * Main workout screen - simplified entry point with unified visual design.
 * 
 * This screen provides:
 * - Quick workout start (empty workout)
 * - Creating a workout (workout routine design)
 * - Recent workout selection and starting
 * 
 * @param onNavigateToActiveWorkout Callback for starting active workout
 * @param onNavigateToWorkoutCreation Callback for workout creation
 * @param modifier Modifier for styling
 * @param viewModel ViewModel for workout management
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    onNavigateToActiveWorkout: (templateId: String?) -> Unit,
    onNavigateToWorkoutCreation: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentState = uiState
    
    when (currentState) {
        is WorkoutUiState.Loading -> {
            WorkoutLoadingScreen()
        }
        is WorkoutUiState.Success -> {
            WorkoutContent(
                screenData = currentState.data,
                onNavigateToActiveWorkout = onNavigateToActiveWorkout,
                onNavigateToWorkoutCreation = onNavigateToWorkoutCreation,
                modifier = modifier
            )
        }
        is WorkoutUiState.Error -> {
            WorkoutErrorScreen(
                error = currentState.error,
                onRetry = { viewModel.handleEvent(WorkoutEvent.RefreshData) },
                modifier = modifier
            )
        }
        is WorkoutUiState.Empty -> {
            WorkoutEmptyScreen(
                onCreateWorkout = onNavigateToWorkoutCreation,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun WorkoutContent(
    screenData: com.example.liftrix.ui.common.state.WorkoutScreenData,
    onNavigateToActiveWorkout: (templateId: String?) -> Unit,
    onNavigateToWorkoutCreation: () -> Unit,
    modifier: Modifier = Modifier
) {

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Quick Actions Section
        item {
            QuickActionsCard(
                onStartQuickWorkout = { onNavigateToActiveWorkout(null) },
                onCreateWorkout = onNavigateToWorkoutCreation
            )
        }
        
        // Recent Workouts Section
        item {
            Text(
                text = stringResource(R.string.workflow_your_workouts),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        if (screenData.templates.isEmpty()) {
            item {
                EmptyWorkoutsCard(
                    onCreateWorkout = onNavigateToWorkoutCreation
                )
            }
        } else {
            items(screenData.templates) { workout ->
                WorkoutCard(
                    workout = workout,
                    onStartWorkout = { onNavigateToActiveWorkout(workout.id.value) },
                    onEditWorkout = { /* TODO: Navigate to edit */ }
                )
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Quick actions card for starting and creating workouts
 */
@Composable
private fun QuickActionsCard(
    onStartQuickWorkout: () -> Unit,
    onCreateWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    UnifiedWorkoutCard(
        title = stringResource(R.string.workflow_create_workout),
        subtitle = stringResource(R.string.create_workout_description),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SecondaryActionButton(
                text = stringResource(R.string.workflow_quick_workout),
                onClick = onStartQuickWorkout,
                modifier = Modifier.weight(1f),
                leadingIcon = Icons.Default.PlayArrow
            )
            
            PrimaryActionButton(
                text = stringResource(R.string.workflow_create_new_workout),
                onClick = onCreateWorkout,
                modifier = Modifier.weight(1f),
                leadingIcon = Icons.Default.Assignment
            )
        }
    }
}

/**
 * Empty workouts state card
 */
@Composable
private fun EmptyWorkoutsCard(
    onCreateWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    UnifiedWorkoutCard(
        title = "No Workouts Yet",
        subtitle = "Create your first workout to get started",
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Assignment,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Create your first workout routine to save time on future sessions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            PrimaryActionButton(
                text = stringResource(R.string.workflow_create_new_workout),
                onClick = onCreateWorkout,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Default.Assignment
            )
        }
    }
}

/**
 * Workout card using unified design components
 */
@Composable
private fun WorkoutCard(
    workout: WorkoutTemplate,
    onStartWorkout: () -> Unit,
    onEditWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    UnifiedWorkoutCard(
        title = workout.name,
        subtitle = if (!workout.description.isNullOrBlank()) workout.description else "${workout.exercises.size} exercises",
        modifier = modifier,
        actions = {
            SecondaryActionButton(
                text = "Edit",
                onClick = onEditWorkout,
                leadingIcon = Icons.Default.Edit
            )
            Spacer(modifier = Modifier.width(8.dp))
            PrimaryActionButton(
                text = "Start Workout", 
                onClick = onStartWorkout,
                leadingIcon = Icons.Default.PlayArrow
            )
        }
    ) {
        // Workout stats with modern styling
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            WorkoutStatItem(
                label = "Exercises",
                value = workout.exercises.size.toString()
            )
            WorkoutStatItem(
                label = "Sets",
                value = workout.getTotalSets().toString()
            )
            WorkoutStatItem(
                label = "Est. Time",
                value = workout.estimatedDurationMinutes?.let { "${it} min" } ?: "N/A"
            )
        }
    }
}

/**
 * Workout stat item with updated styling
 */
@Composable
private fun WorkoutStatItem(
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
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WorkoutLoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.CircularProgressIndicator()
    }
}

@Composable
private fun WorkoutErrorScreen(
    error: com.example.liftrix.domain.model.error.LiftrixError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Failed to load workouts",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error.message,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        PrimaryActionButton(
            text = "Retry",
            onClick = onRetry,
            leadingIcon = Icons.Default.Refresh
        )
    }
}

@Composable
private fun WorkoutEmptyScreen(
    onCreateWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No workouts yet",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Create your first workout to get started",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        PrimaryActionButton(
            text = "Create Workout",
            onClick = onCreateWorkout,
            leadingIcon = Icons.Default.Assignment
        )
    }
}