package com.example.liftrix.ui.workout.completion

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest

import com.example.liftrix.domain.model.social.PostVisibility
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.common.state.UiState
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

/**
 * Screen for creating workout posts with media attachments.
 * 
 * Features:
 * - Rich text caption input
 * - Multi-image selection (up to 10 photos)
 * - Privacy controls with clear descriptions
 * - Progress indication during upload
 * - Error handling with user-friendly messages
 * - Responsive design for different screen sizes
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCreationScreen(
    workoutId: String,
    onNavigateBack: () -> Unit,
    onPostCreated: (String) -> Unit,
    showTopBar: Boolean = true,
    viewModel: PostCreationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var caption by remember { mutableStateOf("") }
    var selectedMedia by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var privacy by remember { mutableStateOf(PostVisibility.FOLLOWERS) }
    val context = LocalContext.current
    
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        selectedMedia = (selectedMedia + uris).take(10) // Max 10 images
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text("Share Workout") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        SharePostButton(
                            isLoading = uiState is UiState.Loading,
                            onClick = {
                                viewModel.handleEvent(
                                    PostCreationEvent.CreatePost(
                                        workoutId = workoutId,
                                        caption = caption,
                                        mediaUris = selectedMedia,
                                        privacy = privacy
                                    )
                                )
                            }
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            if (!showTopBar) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SharePostButton(
                        isLoading = uiState is UiState.Loading,
                        onClick = {
                            viewModel.handleEvent(
                                PostCreationEvent.CreatePost(
                                    workoutId = workoutId,
                                    caption = caption,
                                    mediaUris = selectedMedia,
                                    privacy = privacy
                                )
                            )
                        }
                    )
                }
            }

            // Loading/Progress State
            if (uiState is UiState.Loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = LiftrixColorsV2.primary
                )
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = LiftrixColorsV2.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = LiftrixColorsV2.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Creating your post...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Caption Input
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Write a caption...") },
                    placeholder = { Text("Share your workout thoughts, achievements, or tips!") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .heightIn(min = 120.dp, max = 200.dp),
                    maxLines = 8,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LiftrixColorsV2.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }

            // Media Selection
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Photos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "${selectedMedia.size}/10",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    if (selectedMedia.isEmpty()) {
                        // Empty state
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(LiftrixColorsV2.surfaceVariant)
                                .clickable { mediaPickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Add photos",
                                    modifier = Modifier.size(32.dp),
                                    tint = LiftrixColorsV2.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Add Photos (up to 10)",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Show off your workout setup!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        // Media grid
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(selectedMedia) { uri ->
                                MediaThumbnail(
                                    uri = uri,
                                    onRemove = { selectedMedia = selectedMedia - uri }
                                )
                            }
                            
                            if (selectedMedia.size < 10) {
                                item {
                                    AddMediaButton(
                                        onClick = { mediaPickerLauncher.launch("image/*") }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Privacy Controls
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Who can see this post?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    PostVisibility.values().forEach { privacyOption ->
                        PrivacyOption(
                            visibility = privacyOption,
                            isSelected = privacy == privacyOption,
                            onSelect = { privacy = privacyOption }
                        )
                    }
                }
            }

            // Error State
            if (uiState is UiState.Error) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Failed to create post. Please try again.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Bottom spacing
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Handle success state
    LaunchedEffect(uiState) {
        val currentState = uiState  // Capture for smart cast
        when (currentState) {
            is UiState.Success -> {
                val data = currentState.data
                if (data is PostCreationUiState.Success) {
                    Toast.makeText(context, data.message, Toast.LENGTH_SHORT).show()
                    onPostCreated(data.createdPostId)
                }
            }
            is UiState.Error -> {
                Toast.makeText(
                    context,
                    "Failed to share workout. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            else -> {}
        }
    }
}

@Composable
private fun SharePostButton(
    isLoading: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Text(
                text = "Share",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MediaThumbnail(
    uri: Uri,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(100.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(uri)
                .crossfade(true)
                .build(),
            contentDescription = "Selected photo",
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 8.dp, y = (-8).dp)
        ) {
            Icon(
                Icons.Default.Cancel,
                contentDescription = "Remove photo",
                tint = Color.White,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                        CircleShape
                    )
                    .padding(4.dp)
            )
        }
    }
}

@Composable
private fun AddMediaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .size(100.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = LiftrixColorsV2.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add more photos",
                modifier = Modifier.size(32.dp),
                tint = LiftrixColorsV2.primary
            )
        }
    }
}

@Composable
private fun PrivacyOption(
    visibility: PostVisibility,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = LiftrixColorsV2.primary
            )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = when (visibility) {
                    PostVisibility.PUBLIC -> "Public"
                    PostVisibility.FOLLOWERS -> "Followers"
                    PostVisibility.PRIVATE -> "Only me"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = when (visibility) {
                    PostVisibility.PUBLIC -> "Anyone can see this post"
                    PostVisibility.FOLLOWERS -> "Only your followers can see this post"
                    PostVisibility.PRIVATE -> "Only you can see this post"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
