package com.example.liftrix.ui.feed

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File
import com.example.liftrix.R
import com.example.liftrix.domain.model.social.MediaUploadRequest
import com.example.liftrix.domain.model.social.PostVisibility
import com.example.liftrix.ui.common.components.LoadingIndicator
import com.example.liftrix.ui.feed.components.MediaPickerDialog
import com.example.liftrix.ui.feed.components.PrivacySettingsDialog
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing

/**
 * Multi-step post creation screen with media picker and privacy settings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCreationScreen(
    workoutId: String,
    navController: NavController,
    viewModel: PostCreationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    var showMediaPicker by remember { mutableStateOf(false) }
    var showPrivacySettings by remember { mutableStateOf(false) }
    
    // Create temporary file for camera capture
    val tempCameraUri = remember {
        val tempFile = File(context.cacheDir, "temp_camera_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }
    
    // Media picker launcher
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onEvent(PostCreationEvent.AddMedia(uris))
        }
    }
    
    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            viewModel.onEvent(PostCreationEvent.AddMedia(listOf(tempCameraUri)))
        }
    }
    
    LaunchedEffect(workoutId) {
        viewModel.onEvent(PostCreationEvent.LoadWorkout(workoutId))
    }
    
    // Handle navigation after successful post creation
    LaunchedEffect(uiState.isPostCreated) {
        if (uiState.isPostCreated) {
            navController.navigateUp()
        }
    }

    Scaffold(
        topBar = {
            PostCreationTopBar(
                onNavigateBack = { navController.navigateUp() },
                onShare = { 
                    viewModel.onEvent(PostCreationEvent.CreatePost)
                },
                isLoading = uiState.isCreatingPost,
                canShare = uiState.canCreatePost
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(LiftrixSpacing.medium)
            ) {
                // Post content section
                PostContentSection(
                    uiState = uiState,
                    onCaptionChange = { caption ->
                        viewModel.onEvent(PostCreationEvent.UpdateCaption(caption))
                    },
                    onAddMediaClick = { showMediaPicker = true },
                    onRemoveMedia = { index ->
                        viewModel.onEvent(PostCreationEvent.RemoveMedia(index))
                    },
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
                
                // Privacy and settings section
                PrivacySection(
                    visibility = uiState.visibility,
                    onPrivacyClick = { showPrivacySettings = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    
    // Media picker dialog
    if (showMediaPicker) {
        MediaPickerDialog(
            onDismiss = { showMediaPicker = false },
            onCameraClick = {
                cameraLauncher.launch(tempCameraUri)
                showMediaPicker = false
            },
            onGalleryClick = {
                mediaPickerLauncher.launch("image/*")
                showMediaPicker = false
            }
        )
    }
    
    // Privacy settings dialog
    if (showPrivacySettings) {
        PrivacySettingsDialog(
            currentVisibility = uiState.visibility,
            onVisibilitySelected = { visibility ->
                viewModel.onEvent(PostCreationEvent.UpdateVisibility(visibility))
                showPrivacySettings = false
            },
            onDismiss = { showPrivacySettings = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostCreationTopBar(
    onNavigateBack: () -> Unit,
    onShare: () -> Unit,
    isLoading: Boolean,
    canShare: Boolean,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.create_post_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close"
                )
            }
        },
        actions = {
            Button(
                onClick = onShare,
                enabled = canShare && !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LiftrixColorsV2.primary
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = LiftrixColorsV2.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = stringResource(R.string.post_share_button),
                        color = LiftrixColorsV2.onPrimary
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = LiftrixColorsV2.surface
        ),
        modifier = modifier
    )
}

@Composable
private fun PostContentSection(
    uiState: PostCreationUiState,
    onCaptionChange: (String) -> Unit,
    onAddMediaClick: () -> Unit,
    onRemoveMedia: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Workout summary card
        uiState.workoutSummary?.let { workout ->
            WorkoutSummaryDisplay(
                workout = workout,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
        }
        
        // Caption input
        OutlinedTextField(
            value = uiState.caption,
            onValueChange = onCaptionChange,
            placeholder = {
                Text(stringResource(R.string.post_caption_placeholder))
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences
            ),
            maxLines = 6,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LiftrixColorsV2.primary,
                unfocusedBorderColor = LiftrixColorsV2.outline
            )
        )
        
        Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
        
        // Media section
        MediaSection(
            mediaRequests = uiState.mediaRequests,
            onAddMediaClick = onAddMediaClick,
            onRemoveMedia = onRemoveMedia,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Error message
        uiState.error?.let { error ->
            Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
            
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(LiftrixSpacing.medium)
                )
            }
        }
    }
}

@Composable
private fun WorkoutSummaryDisplay(
    workout: WorkoutSummary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = LiftrixColorsV2.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.medium)
        ) {
            Text(
                text = workout.name,
                style = MaterialTheme.typography.titleMedium,
                color = LiftrixColorsV2.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.small))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                WorkoutStatItem(
                    label = "Duration",
                    value = "${workout.durationMinutes}m"
                )
                
                WorkoutStatItem(
                    label = "Volume",
                    value = "${workout.totalVolume.toInt()} lbs"
                )
                
                WorkoutStatItem(
                    label = "Exercises",
                    value = "${workout.exerciseCount}"
                )
                
                if (workout.prsCount > 0) {
                    WorkoutStatItem(
                        label = "PRs",
                        value = "${workout.prsCount}"
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutStatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = LiftrixColorsV2.primary,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = LiftrixColorsV2.onSurfaceVariant
        )
    }
}

@Composable
private fun MediaSection(
    mediaRequests: List<MediaUploadRequest>,
    onAddMediaClick: () -> Unit,
    onRemoveMedia: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.post_media_section_title),
                style = MaterialTheme.typography.titleSmall,
                color = LiftrixColorsV2.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            
            TextButton(
                onClick = onAddMediaClick,
                enabled = mediaRequests.size < 5 // Max 5 media items
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                
                Spacer(modifier = Modifier.width(4.dp))
                
                Text(stringResource(R.string.post_add_media_button))
            }
        }
        
        if (mediaRequests.isNotEmpty()) {
            Spacer(modifier = Modifier.height(LiftrixSpacing.small))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
            ) {
                items(mediaRequests.withIndex().toList()) { (index, request) ->
                    MediaPreviewItem(
                        mediaRequest = request,
                        onRemove = { onRemoveMedia(index) },
                        modifier = Modifier.size(80.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaPreviewItem(
    mediaRequest: MediaUploadRequest,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        AsyncImage(
            model = mediaRequest.uri,
            contentDescription = "Media preview",
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
        ) {
            Surface(
                color = LiftrixColorsV2.surface.copy(alpha = 0.8f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove media",
                    tint = LiftrixColorsV2.onSurface,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                )
            }
        }
    }
}

@Composable
private fun PrivacySection(
    visibility: PostVisibility,
    onPrivacyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = LiftrixColorsV2.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.post_privacy_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = LiftrixColorsV2.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                
                Text(
                    text = getVisibilityDescription(visibility),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LiftrixColorsV2.onSurfaceVariant
                )
            }
            
            IconButton(onClick = onPrivacyClick) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Edit privacy settings",
                    tint = LiftrixColorsV2.primary
                )
            }
        }
    }
}

@Composable
private fun getVisibilityDescription(visibility: PostVisibility): String {
    return when (visibility) {
        PostVisibility.PUBLIC -> stringResource(R.string.visibility_public_description)
        PostVisibility.FOLLOWERS -> stringResource(R.string.visibility_followers_description)
        PostVisibility.PRIVATE -> stringResource(R.string.visibility_private_description)
    }
}

/**
 * Simple workout summary for display in post creation
 */
data class WorkoutSummary(
    val id: String,
    val name: String,
    val durationMinutes: Int,
    val totalVolume: Double,
    val exerciseCount: Int,
    val prsCount: Int
)