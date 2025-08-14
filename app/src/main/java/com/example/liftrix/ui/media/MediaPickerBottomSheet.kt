package com.example.liftrix.ui.media

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.liftrix.domain.model.MediaType
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing

/**
 * Media picker bottom sheet with gallery integration and multi-select capability.
 * Part of content sharing and media management system from SPEC-20250113-content-sharing-media.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPickerBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onMediaSelected: (List<Uri>) -> Unit,
    maxSelection: Int = 10,
    mediaType: MediaType = MediaType.PHOTO,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    
    var selectedMedia by remember { mutableStateOf<Set<GalleryItem>>(emptySet()) }
    var hasPermission by remember { mutableStateOf(false) }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }
    
    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            // Handle camera capture - save bitmap and add to selection
            // Implementation would save bitmap to temporary file and return Uri
        }
    }
    
    // Gallery launcher for multiple selection
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onMediaSelected(uris.take(maxSelection))
            onDismiss()
        }
    }
    
    // Check permission on mount
    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    if (isVisible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            modifier = modifier
        ) {
            MediaPickerContent(
                hasPermission = hasPermission,
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                },
                selectedMedia = selectedMedia,
                onMediaToggle = { item ->
                    selectedMedia = if (selectedMedia.contains(item)) {
                        selectedMedia - item
                    } else if (selectedMedia.size < maxSelection) {
                        selectedMedia + item
                    } else {
                        selectedMedia
                    }
                },
                onDone = {
                    val uris = selectedMedia.map { it.uri }
                    onMediaSelected(uris)
                    onDismiss()
                },
                onDismiss = onDismiss,
                onOpenCamera = {
                    cameraLauncher.launch(null)
                },
                onOpenGallery = {
                    val mimeType = when (mediaType) {
                        MediaType.PHOTO -> "image/*"
                        MediaType.VIDEO -> "video/*"
                    }
                    galleryLauncher.launch(mimeType)
                },
                maxSelection = maxSelection,
                mediaType = mediaType
            )
        }
    }
}

@Composable
private fun MediaPickerContent(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    selectedMedia: Set<GalleryItem>,
    onMediaToggle: (GalleryItem) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    maxSelection: Int,
    mediaType: MediaType
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(LiftrixSpacing.medium)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close"
                )
            }
            
            Text(
                text = "Select Media",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            TextButton(
                onClick = onDone,
                enabled = selectedMedia.isNotEmpty()
            ) {
                Text("Done (${selectedMedia.size}/$maxSelection)")
            }
        }
        
        Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
        
        if (!hasPermission) {
            PermissionRequestCard(
                onRequestPermission = onRequestPermission,
                mediaType = mediaType
            )
        } else {
            MediaGridContent(
                selectedMedia = selectedMedia,
                onMediaToggle = onMediaToggle,
                onOpenCamera = onOpenCamera,
                onOpenGallery = onOpenGallery,
                maxSelection = maxSelection,
                mediaType = mediaType
            )
        }
    }
}

@Composable
private fun PermissionRequestCard(
    onRequestPermission: () -> Unit,
    mediaType: MediaType
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(LiftrixSpacing.medium),
        colors = CardDefaults.cardColors(
            containerColor = LiftrixColorsV2.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (mediaType == MediaType.PHOTO) Icons.Default.Camera else Icons.Default.VideoLibrary,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = LiftrixColorsV2.primary
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
            
            Text(
                text = "Media Access Required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.small))
            
            Text(
                text = "Liftrix needs access to your ${if (mediaType == MediaType.PHOTO) "photos" else "videos"} to let you share content with your workout posts.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.large))
            
            TextButton(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
private fun MediaGridContent(
    selectedMedia: Set<GalleryItem>,
    onMediaToggle: (GalleryItem) -> Unit,
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    maxSelection: Int,
    mediaType: MediaType
) {
    // Load media items from gallery
    val mediaItems by produceState<List<GalleryItem>>(
        initialValue = emptyList(),
        key1 = mediaType
    ) {
        value = loadMediaFromGallery(mediaType)
    }
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Camera option
        item {
            CameraCard(
                onClick = onOpenCamera,
                mediaType = mediaType
            )
        }
        
        // Gallery option
        item {
            GalleryCard(
                onClick = onOpenGallery,
                mediaType = mediaType
            )
        }
        
        // Media items
        items(mediaItems) { item ->
            MediaThumbnail(
                item = item,
                isSelected = selectedMedia.contains(item),
                onToggle = { onMediaToggle(item) },
                selectionNumber = selectedMedia.indexOf(item).takeIf { it >= 0 }?.plus(1),
                canSelect = selectedMedia.contains(item) || selectedMedia.size < maxSelection
            )
        }
    }
}

@Composable
private fun CameraCard(
    onClick: () -> Unit,
    mediaType: MediaType
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = LiftrixColorsV2.primary.copy(alpha = 0.1f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Camera,
                    contentDescription = "Camera",
                    tint = LiftrixColorsV2.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Camera",
                    style = MaterialTheme.typography.labelSmall,
                    color = LiftrixColorsV2.primary
                )
            }
        }
    }
}

@Composable
private fun GalleryCard(
    onClick: () -> Unit,
    mediaType: MediaType
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = LiftrixColorsV2.Dark.Secondary.copy(alpha = 0.1f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (mediaType == MediaType.PHOTO) Icons.Default.Camera else Icons.Default.VideoLibrary,
                    contentDescription = "Gallery",
                    tint = LiftrixColorsV2.Dark.Secondary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Gallery",
                    style = MaterialTheme.typography.labelSmall,
                    color = LiftrixColorsV2.Dark.Secondary
                )
            }
        }
    }
}

@Composable
private fun MediaThumbnail(
    item: GalleryItem,
    isSelected: Boolean,
    onToggle: () -> Unit,
    selectionNumber: Int?,
    canSelect: Boolean
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = canSelect) { onToggle() }
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 3.dp,
                            color = LiftrixColorsV2.primary,
                            shape = RoundedCornerShape(8.dp)
                        )
                    } else {
                        Modifier
                    }
                ),
            contentScale = ContentScale.Crop
        )
        
        // Selection indicator
        if (isSelected && selectionNumber != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .background(
                        color = LiftrixColorsV2.primary,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = selectionNumber.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        } else if (canSelect && !isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .border(
                        width = 2.dp,
                        color = Color.White,
                        shape = CircleShape
                    )
            )
        }
        
        // Video indicator
        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatDuration(item.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
        
        // Disabled overlay
        if (!canSelect && !isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )
        }
    }
}

// Data class for gallery items
data class GalleryItem(
    val uri: Uri,
    val isVideo: Boolean = false,
    val duration: Long = 0, // in milliseconds
    val size: Long = 0 // in bytes
)

// Helper functions
private suspend fun loadMediaFromGallery(mediaType: MediaType): List<GalleryItem> {
    // Implementation would use MediaStore to load gallery items
    // For now, return empty list
    return emptyList()
}

private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / 60000) % 60
    val hours = durationMs / 3600000
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}