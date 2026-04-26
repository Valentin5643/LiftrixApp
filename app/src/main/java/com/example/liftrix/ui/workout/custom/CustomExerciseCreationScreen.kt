package com.example.liftrix.ui.workout.custom

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseType
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
 * Custom Exercise Creation Screen
 * 
 * Modern creation interface with multiple sections:
 * - Basic Info (name, description, type)
 * - Exercise Details (muscles, equipment, difficulty)
 * - Media (images, video URL)
 * - Instructions (step-by-step)
 * - Organization (tags, categories, notes)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomExerciseCreationScreen(
    onNavigateBack: () -> Unit,
    onExerciseCreated: (String) -> Unit, // Exercise ID
    viewModel: CustomExerciseCreationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    
    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        viewModel.handleEvent(CustomExerciseCreationEvent.AddImages(uris))
    }

    // Handle navigation events
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CustomExerciseNavigationEvent.NavigateBack -> onNavigateBack()
                is CustomExerciseNavigationEvent.ExerciseCreated -> onExerciseCreated(event.exerciseId)
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
                    modifier = Modifier.padding(LiftrixSpacing.medium)
                ) {
                    PrimaryActionButton(
                        text = when (val currentState = uiState) {
                            is UiState.Success -> {
                                when {
                                    currentState.data.isCreated -> "✓ Exercise Created!"
                                    currentState.data.isCreating -> "Creating Exercise..."
                                    else -> "Create Exercise"
                                }
                            }
                            else -> "Create Exercise"
                        },
                        onClick = { 
                            viewModel.handleEvent(CustomExerciseCreationEvent.CreateExercise)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = when (val currentState = uiState) {
                            is UiState.Success -> currentState.data.canCreate()
                            else -> false
                        },
                        leadingIcon = when (val currentState = uiState) {
                            is UiState.Success -> {
                                when {
                                    currentState.data.isCreated -> Icons.Default.CheckCircle
                                    currentState.data.isCreating -> null
                                    else -> Icons.Default.Add
                                }
                            }
                            else -> Icons.Default.Add
                        }
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
                    CircularProgressIndicator(color = LiftrixColorsV2.Teal)
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
                            imageVector = Icons.Default.Info,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = state.error.message ?: "An error occurred",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        SecondaryActionButton(
                            text = "Try Again",
                            onClick = { viewModel.handleEvent(CustomExerciseCreationEvent.Retry) }
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
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create exercise",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.showAction && state.actionText != null) {
                            SecondaryActionButton(
                                text = state.actionText!!,
                                onClick = { viewModel.handleEvent(CustomExerciseCreationEvent.ResetForm) }
                            )
                        }
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
                    // Success message when exercise is created
                    if (state.data.isCreated) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(LiftrixSpacing.medium),
                                    horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.small),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Exercise created",
                                        tint = LiftrixColorsV2.Teal,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "Exercise Created Successfully!",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        if (state.data.createdExerciseName != null) {
                                            Text(
                                                text = "\"${state.data.createdExerciseName}\" has been added to your custom exercises.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
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
                    
                    
                    // Bottom spacing for floating action button
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
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
}

/**
 * Reusable section container with title and icon
 */
@Composable
private fun CreationSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    contentDescription = title,
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
