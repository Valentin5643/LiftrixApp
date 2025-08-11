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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import com.example.liftrix.ui.workout.components.SecondaryActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.example.liftrix.R
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.ui.common.state.WorkoutScreenData
import com.example.liftrix.ui.common.state.WorkoutUiState
import com.example.liftrix.domain.model.Folder
import com.example.liftrix.domain.usecase.folder.GetFoldersUseCase
import com.example.liftrix.domain.usecase.folder.CreateFolderUseCase
import kotlinx.coroutines.flow.firstOrNull
import com.example.liftrix.ui.workout.components.InlineFolderSection
import timber.log.Timber
import com.example.liftrix.ui.workout.components.CreateFolderDialog
import com.example.liftrix.ui.workout.components.FolderEditDialog
import com.example.liftrix.ui.workout.components.QuickCreateFolderButton

/**
 * Main workout screen - simplified entry point with unified visual design.
 * 
 * This screen provides:
 * - Quick workout start (empty workout)
 * - Creating a workout (workout routine design)
 * - Recent workout selection and starting
 * - Folder organization and filtering
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
    onNavigateToWorkoutCreation: (folderId: String?) -> Unit,
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
                onCreateFolder = { folderName -> 
                    viewModel.handleEvent(WorkoutEvent.CreateFolder(folderName))
                },
                viewModel = viewModel,
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
                onCreateWorkout = { onNavigateToWorkoutCreation(null) },
                modifier = modifier
            )
        }
    }
}

@Composable
private fun WorkoutContent(
    screenData: com.example.liftrix.ui.common.state.WorkoutScreenData,
    onNavigateToActiveWorkout: (templateId: String?) -> Unit,
    onNavigateToWorkoutCreation: (folderId: String?) -> Unit,
    onCreateFolder: (String) -> Unit,
    viewModel: WorkoutViewModel,
    modifier: Modifier = Modifier
) {
    // State for folder expansion and creation
    var expandedFolders by remember { mutableStateOf(setOf<String>()) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showEditFolderDialog by remember { mutableStateOf(false) }
    var selectedFolderForEdit by remember { mutableStateOf<Folder?>(null) }
    
    // Drop zone tracking
    val folderPositions = remember { mutableMapOf<String, androidx.compose.ui.geometry.Rect>() }

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
                onCreateWorkout = { onNavigateToWorkoutCreation(null) }
            )
        }
        
        // Recent Workouts Section with Inline Folder Management
        item {
            InlineFolderSectionHeader(
                title = stringResource(R.string.workflow_your_workouts),
                onCreateFolder = { showCreateFolderDialog = true }
            )
        }
        
        // Always show folders, regardless of template count
        // Use real folders from screenData and group templates accordingly
        val foldersWithWorkouts = screenData.folders.map { folder ->
            val workoutsInFolder = screenData.templates.filter { template ->
                template.folderId == folder.id.value
            }
            folder.updateTemplateCount(workoutsInFolder.size) to workoutsInFolder
        }
        
        if (foldersWithWorkouts.isEmpty() && screenData.templates.isEmpty()) {
            // Only show empty state if there are truly no folders AND no templates
            item {
                EmptyWorkoutsCard(
                    onCreateWorkout = { onNavigateToWorkoutCreation(null) }
                )
            }
        } else {
            // Show folders (empty or not) and any templates
            foldersWithWorkouts.forEach { (folder, workouts) ->
                item(key = "folder_${folder.id.value}") {
                    InlineFolderSection(
                        folder = folder,
                        workouts = workouts,
                        isExpanded = expandedFolders.contains(folder.id.value),
                        onToggleExpanded = { toggledFolderId ->
                            expandedFolders = if (expandedFolders.contains(toggledFolderId)) {
                                expandedFolders - toggledFolderId
                            } else {
                                expandedFolders + toggledFolderId
                            }
                        },
                        onStartWorkout = { workout -> 
                            onNavigateToActiveWorkout(workout.id.value) 
                        },
                        onEditWorkout = { /* TODO: Navigate to edit */ },
                        onCreateWorkout = { folderId ->
                            onNavigateToWorkoutCreation(folderId)
                        },
                        onEditFolder = { folderId ->
                            selectedFolderForEdit = screenData.folders.find { it.id.value == folderId }
                            showEditFolderDialog = true
                        },
                        onMoveWorkout = { workout, dropPosition ->
                            // Find which folder the workout was dropped on based on coordinates
                            var targetFolderId: String? = null
                            
                            for ((folderId, folderRect) in folderPositions) {
                                if (folderRect.contains(dropPosition)) {
                                    targetFolderId = folderId
                                    break
                                }
                            }
                            
                            // Only move if dropped on a different folder
                            if (targetFolderId != null && workout.folderId != targetFolderId) {
                                viewModel.handleEvent(
                                    WorkoutEvent.MoveWorkout(workout, targetFolderId)
                                )
                            }
                        },
                        onFolderPositionChanged = { folderId, rect ->
                            folderPositions[folderId] = rect
                        }
                    )
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    
    // Create Folder Dialog
    CreateFolderDialog(
        show = showCreateFolderDialog,
        onDismiss = { showCreateFolderDialog = false },
        onCreateFolder = { folderName: String ->
            onCreateFolder(folderName)
        }
    )
    
    // Folder Edit Dialog
    FolderEditDialog(
        show = showEditFolderDialog,
        folder = selectedFolderForEdit,
        onDismiss = { 
            showEditFolderDialog = false 
            selectedFolderForEdit = null
        },
        onDeleteFolder = { folder ->
            viewModel.handleEvent(WorkoutEvent.DeleteFolder(folder))
            showEditFolderDialog = false
            selectedFolderForEdit = null
        },
        onRenameFolder = { folder, newName ->
            viewModel.handleEvent(WorkoutEvent.RenameFolder(folder, newName))
            showEditFolderDialog = false
            selectedFolderForEdit = null
        },
        onAddWorkoutToFolder = { folder ->
            // Navigate to workout creation with pre-selected folder
            onNavigateToWorkoutCreation(folder.id.value)
            showEditFolderDialog = false
            selectedFolderForEdit = null
        },
        onReorderFolder = { folder, moveUp ->
            // ✅ SIMPLIFIED: ViewModel now handles timing safety
            val currentFolders = screenData.folders
            val currentIndex = currentFolders.indexOfFirst { it.id == folder.id }
            
            if (currentIndex != -1) {
                val newIndex = if (moveUp) {
                    (currentIndex - 1).coerceAtLeast(0)
                } else {
                    (currentIndex + 1).coerceAtMost(currentFolders.size - 1)
                }
                
                if (newIndex != currentIndex) {
                    val mutableFolders = currentFolders.toMutableList()
                    val folderToMove = mutableFolders.removeAt(currentIndex)
                    mutableFolders.add(newIndex, folderToMove)
                    
                    val reorderedIds = mutableFolders.map { it.id }
                    Timber.d("FOLDER-DEBUG: UI TRIGGER - ✅ Requesting reorder of folder '${folder.name}' from index $currentIndex to $newIndex")
                    viewModel.handleEvent(WorkoutEvent.ReorderFolders(reorderedIds))
                }
            }
        }
    )
}

/**
 * Inline folder section header with create folder access
 */
@Composable
private fun InlineFolderSectionHeader(
    title: String,
    onCreateFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        
        QuickCreateFolderButton(
            onClick = onCreateFolder
        )
    }
}


/**
 * Quick actions card for starting and creating workouts
 */
@Composable
private fun QuickActionsCard(
    onStartQuickWorkout: () -> Unit,
    onCreateWorkout: (folderId: String?) -> Unit,
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
                onClick = { onCreateWorkout(null) },
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
    onCreateWorkout: (folderId: String?) -> Unit,
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
                onClick = { onCreateWorkout(null) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Default.Assignment
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
    onCreateWorkout: (folderId: String?) -> Unit,
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
            onClick = { onCreateWorkout(null) },
            leadingIcon = Icons.Default.Assignment
        )
    }
}