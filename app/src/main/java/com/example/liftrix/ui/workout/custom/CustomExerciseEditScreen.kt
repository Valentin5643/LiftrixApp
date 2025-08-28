package com.example.liftrix.ui.workout.custom

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseType
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.ui.common.state.UiState
import androidx.compose.material3.MaterialTheme
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import com.example.liftrix.ui.workout.components.SecondaryActionButton
import com.example.liftrix.ui.workout.custom.components.ExerciseImageGallery
import com.example.liftrix.ui.workout.custom.components.ExerciseTypeSelector
import timber.log.Timber

/**
 * Custom Exercise Edit Screen
 * 
 * Screen for editing existing custom exercises with the same sectioned interface as creation:
 * - Pre-populated form fields with current exercise data
 * - Same validation and error handling as creation
 * - Ability to modify all aspects of the exercise
 * - Preserves existing images while allowing additions/removals
 * - Shows save progress and handles update conflicts
 * 
 * Key differences from creation screen:
 * - Title shows "Edit Exercise" instead of "Create Exercise"
 * - Form is pre-populated with existing exercise data
 * - Save button shows "Save Changes" instead of "Create Exercise"
 * - Handles loading existing exercise data on screen entry
 * - Shows confirmation dialog for destructive changes
 * 
 * @param exerciseId The ID of the custom exercise to edit
 * @param onNavigateBack Callback when user navigates back
 * @param onExerciseUpdated Callback when exercise is successfully updated
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomExerciseEditScreen(
    exerciseId: String,
    onNavigateBack: () -> Unit,
    onExerciseUpdated: (String) -> Unit, // Exercise ID
    viewModel: CustomExerciseEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    
    // Load exercise data when screen opens
    LaunchedEffect(exerciseId) {
        viewModel.loadExercise(exerciseId)
    }
    
    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        viewModel.handleEvent(CustomExerciseEditEvent.AddImages(uris))
    }

    // Handle navigation events
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CustomExerciseEditEvent.NavigateBack -> {
                    val currentUiState = uiState as? UiState.Success
                    if (currentUiState?.data?.hasChanges() == true) {
                        showUnsavedChangesDialog = true
                    } else {
                        onNavigateBack()
                    }
                }
                is CustomExerciseEditEvent.ExerciseUpdated -> onExerciseUpdated(event.exerciseId)
                is CustomExerciseEditEvent.ExerciseDeleted -> onNavigateBack()
                else -> {
                    // Handle other events that are not navigation events
                    // This branch ensures exhaustive when expression
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            Surface(
                color = LiftrixColorsV2.Dark.BackgroundSecondary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(LiftrixSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
                ) {
                    PrimaryActionButton(
                        text = "Save Changes",
                        onClick = { 
                            viewModel.handleEvent(CustomExerciseEditEvent.SaveExercise)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = when (uiState) {
                            is UiState.Success -> (uiState as UiState.Success).data.canSave() && (uiState as UiState.Success).data.hasChanges()
                            else -> false
                        },
                        leadingIcon = Icons.Default.Save
                    )
                }
            }
        },
        containerColor = LiftrixColorsV2.Dark.BackgroundPrimary
    ) { paddingValues ->
        
        when (val state = uiState) {
            UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
                    ) {
                        CircularProgressIndicator(color = LiftrixColorsV2.Teal)
                        Text(
                            text = "Loading exercise...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
            
            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = state.error.message ?: "Failed to load exercise",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        SecondaryActionButton(
                            text = "Try Again",
                            onClick = { viewModel.loadExercise(exerciseId) }
                        )
                        SecondaryActionButton(
                            text = "Go Back",
                            onClick = onNavigateBack
                        )
                    }
                }
            }
            
            is UiState.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SecondaryActionButton(
                            text = "Go Back",
                            onClick = onNavigateBack
                        )
                    }
                }
            }
            
            is UiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium),
                    contentPadding = PaddingValues(LiftrixSpacing.medium)
                ) {
                    // Changes indicator (if has unsaved changes)
                    if (state.data.hasChanges()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "You have unsaved changes",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                    
                    // Basic Info Section
                    item {
                        CreationSection(
                            title = "Basic Information",
                            icon = Icons.Default.Info
                        ) {
                            BasicInfoSection(
                                formState = state.data,
                                onEvent = viewModel::handleEvent,
                                focusManager = focusManager
                            )
                        }
                    }
                    
                    // Exercise Details Section
                    item {
                        CreationSection(
                            title = "Exercise Details",
                            icon = Icons.Default.FitnessCenter
                        ) {
                            ExerciseDetailsSection(
                                formState = state.data,
                                onEvent = viewModel::handleEvent
                            )
                        }
                    }
                    
                    // Media Section
                    item {
                        CreationSection(
                            title = "Media",
                            icon = Icons.Default.Camera
                        ) {
                            MediaSection(
                                formState = state.data,
                                onEvent = viewModel::handleEvent,
                                onAddImages = {
                                    imagePickerLauncher.launch("image/*")
                                }
                            )
                        }
                    }
                    
                    
                    // Metadata Section (creation date, last modified, etc.)
                    item {
                        MetadataSection(
                            formState = state.data
                        )
                    }
                }
            }
            else -> {
                // Fallback for any unexpected state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = LiftrixColorsV2.Teal)
                }
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "Delete Exercise",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Are you sure you want to delete this custom exercise?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "This action cannot be undone and will remove the exercise from all your workouts and templates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.handleEvent(CustomExerciseEditEvent.DeleteExercise)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Unsaved changes dialog
    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            title = {
                Text(
                    text = "Unsaved Changes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "You have unsaved changes. Are you sure you want to leave without saving?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnsavedChangesDialog = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = "Leave",
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUnsavedChangesDialog = false }
                ) {
                    Text("Stay")
                }
            }
        )
    }
}

/**
 * Metadata section showing exercise creation and modification info.
 */
@Composable
private fun MetadataSection(
    formState: CustomExerciseEditFormState,
    modifier: Modifier = Modifier
) {
    CreationSection(
        title = "Exercise Info",
        icon = Icons.Default.Info,
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (formState.createdAt != null) {
                MetadataRow(
                    label = "Created",
                    value = formState.createdAt!!
                )
            }
            if (formState.lastModified != null) {
                MetadataRow(
                    label = "Last Modified",
                    value = formState.lastModified!!
                )
            }
            if (formState.usageCount != null) {
                MetadataRow(
                    label = "Used in Workouts",
                    value = "${formState.usageCount} times"
                )
            }
        }
    }
}

/**
 * Individual metadata row component.
 */
@Composable
private fun MetadataRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Extended form state for editing that includes additional metadata and change tracking.
 */
data class CustomExerciseEditFormState(
    // All fields from creation form
    val name: String = "",
    val nameError: String? = null,
    val description: String = "",
    val descriptionError: String? = null,
    val exerciseType: ExerciseType = ExerciseType.WEIGHT_BASED,
    val primaryMuscle: ExerciseCategory? = null,
    val secondaryMuscles: Set<ExerciseCategory> = emptySet(),
    val equipment: Equipment? = null,
    val difficulty: Int? = null,
    val mainImageUri: Uri? = null,
    val additionalImageUris: List<Uri> = emptyList(),
    val videoUrl: String = "",
    val videoUrlError: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    
    // Edit-specific fields
    val exerciseId: String = "",
    val originalState: CustomExerciseFormState? = null,
    val createdAt: String? = null,
    val lastModified: String? = null,
    val usageCount: Int? = null,
    val isDeleting: Boolean = false
) {
    /**
     * Checks if the form has the minimum required fields to save
     */
    fun canSave(): Boolean {
        return name.isNotBlank() &&
                nameError == null &&
                primaryMuscle != null &&
                equipment != null &&
                descriptionError == null &&
                videoUrlError == null &&
                !isSaving &&
                !isDeleting
    }
    
    /**
     * Checks if the form has unsaved changes compared to original state
     */
    fun hasChanges(): Boolean {
        if (originalState == null) return false
        
        return name != originalState.name ||
                description != originalState.description ||
                exerciseType != originalState.exerciseType ||
                primaryMuscle != originalState.primaryMuscle ||
                secondaryMuscles != originalState.secondaryMuscles ||
                equipment != originalState.equipment ||
                difficulty != originalState.difficulty ||
                mainImageUri != originalState.mainImageUri ||
                additionalImageUris != originalState.additionalImageUris ||
                videoUrl != originalState.videoUrl
    }
    
    /**
     * Legacy property for compatibility - delegates to hasChanges()
     */
    val hasUnsavedChanges: Boolean get() = hasChanges()
}

/**
 * Events for custom exercise editing
 */
sealed class CustomExerciseEditEvent : ViewModelEvent {
    // Navigation
    object NavigateBack : CustomExerciseEditEvent()
    data class ExerciseUpdated(val exerciseId: String) : CustomExerciseEditEvent()
    object ExerciseDeleted : CustomExerciseEditEvent()
    
    // Form Updates - Same as creation events but adapted for editing
    data class UpdateName(val name: String) : CustomExerciseEditEvent()
    data class UpdateDescription(val description: String) : CustomExerciseEditEvent()
    data class UpdateExerciseType(val type: ExerciseType) : CustomExerciseEditEvent()
    data class UpdatePrimaryMuscle(val muscle: ExerciseCategory) : CustomExerciseEditEvent()
    data class AddSecondaryMuscle(val muscle: ExerciseCategory) : CustomExerciseEditEvent()
    data class RemoveSecondaryMuscle(val muscle: ExerciseCategory) : CustomExerciseEditEvent()
    data class UpdateEquipment(val equipment: Equipment) : CustomExerciseEditEvent()
    data class UpdateDifficulty(val difficulty: Int?) : CustomExerciseEditEvent()
    data class SetMainImage(val uri: Uri) : CustomExerciseEditEvent()
    data class AddImages(val uris: List<Uri>) : CustomExerciseEditEvent()
    data class RemoveImage(val uri: Uri) : CustomExerciseEditEvent()
    data class UpdateVideoUrl(val url: String) : CustomExerciseEditEvent()
    
    // Actions
    object SaveExercise : CustomExerciseEditEvent()
    object DeleteExercise : CustomExerciseEditEvent()
}

/**
 * Reusable section container with title and icon - copied from CreationScreen
 */
@Composable
private fun CreationSection(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = LiftrixColorsV2.Dark.BackgroundSecondary
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.medium)
        ) {
            // Section header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = LiftrixSpacing.medium)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = LiftrixColorsV2.Teal,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(LiftrixSpacing.small))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
            
            // Section content
            content()
        }
    }
}