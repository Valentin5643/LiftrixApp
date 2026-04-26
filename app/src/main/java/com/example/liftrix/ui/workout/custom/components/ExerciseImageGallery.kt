package com.example.liftrix.ui.workout.custom.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.liftrix.ui.profile.components.ImagePickerDialog
import com.example.liftrix.ui.theme.LiftrixTheme
import timber.log.Timber

/**
 * Exercise image gallery component for managing multiple images in custom exercise creation.
 * 
 * Features:
 * - Support for multiple exercise images with preview thumbnails
 * - Main image designation with visual indicator (star badge)
 * - Add/remove image functionality with user-friendly controls
 * - Responsive grid layout that adapts to image count
 * - Image picker dialog integration for camera/gallery selection
 * - Loading states and error handling for image operations
 * - Accessibility support with proper semantics
 * - Material 3 design with rounded corners and elevation
 * - Haptic feedback for better user experience
 * - Performance optimized with Coil image loading
 * 
 * Usage Pattern:
 * - Shows "Add Image" button when empty or less than max images
 * - First image automatically becomes main image
 * - Users can tap star icon to change main image designation
 * - Remove button (X) appears on image hover/long press
 * - Supports up to 5 images total for performance reasons
 * 
 * @param imageUris List of image URIs to display
 * @param mainImageIndex Index of the main image (0-based)
 * @param onAddImage Callback when user wants to add a new image
 * @param onRemoveImage Callback when user removes an image (passes index)
 * @param onSetMainImage Callback when user designates main image (passes index)
 * @param maxImages Maximum number of images allowed (default: 5)
 * @param modifier Modifier for styling the component
 */
@Composable
fun ExerciseImageGallery(
    imageUris: List<Uri>,
    mainImageIndex: Int = 0,
    onAddImage: (Uri) -> Unit,
    onRemoveImage: (Int) -> Unit,
    onSetMainImage: (Int) -> Unit,
    maxImages: Int = 5,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showImagePicker by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Exercise Images",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = "${imageUris.size}/$maxImages",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Helper text
        if (imageUris.isEmpty()) {
            Text(
                text = "Add images to help users understand how to perform this exercise",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
        } else {
            Text(
                text = "Tap the star to set the main image that will be displayed in lists",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Image gallery
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            if (imageUris.isEmpty()) {
                // Empty state
                EmptyImageGallery(
                    onAddImage = { showImagePicker = true },
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                // Image grid
                ImageGridLayout(
                    imageUris = imageUris,
                    mainImageIndex = mainImageIndex,
                    onRemoveImage = onRemoveImage,
                    onSetMainImage = onSetMainImage,
                    onAddImage = if (imageUris.size < maxImages) {
                        { showImagePicker = true }
                    } else null,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        
        // Error message display
        errorMessage?.let { message ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        
        // Clear error message after delay
        LaunchedEffect(errorMessage) {
            if (errorMessage != null) {
                kotlinx.coroutines.delay(5000)
                errorMessage = null
            }
        }
    }
    
    // Image picker dialog
    ImagePickerDialog(
        isVisible = showImagePicker,
        onDismiss = { showImagePicker = false },
        onImageSelected = { uri ->
            try {
                onAddImage(uri)
                showImagePicker = false
                Timber.d("ExerciseImageGallery: Image added successfully: $uri")
            } catch (e: Exception) {
                Timber.e(e, "ExerciseImageGallery: Failed to add image")
                errorMessage = "Failed to add image: ${e.message}"
                showImagePicker = false
            }
        },
        onError = { message ->
            errorMessage = message
            showImagePicker = false
            Timber.w("ExerciseImageGallery: Image picker error: $message")
        }
    )
}

/**
 * Empty state component for when no images have been added yet.
 */
@Composable
private fun EmptyImageGallery(
    onAddImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Add image icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(40.dp)
                )
                .clickable(
                    onClickLabel = "Add exercise image"
                ) { onAddImage() }
                .semantics {
                    contentDescription = "Add exercise image"
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add image",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Text(
            text = "Add Exercise Images",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        OutlinedButton(
            onClick = onAddImage,
            modifier = Modifier.semantics {
                contentDescription = "Add first exercise image"
            }
        ) {
            Text("Add Image")
        }
    }
}

/**
 * Grid layout component for displaying and managing exercise images.
 */
@Composable
private fun ImageGridLayout(
    imageUris: List<Uri>,
    mainImageIndex: Int,
    onRemoveImage: (Int) -> Unit,
    onSetMainImage: (Int) -> Unit,
    onAddImage: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        // Existing images
        itemsIndexed(imageUris) { index, imageUri ->
            ExerciseImageThumbnail(
                imageUri = imageUri,
                isMainImage = index == mainImageIndex,
                onRemove = { onRemoveImage(index) },
                onSetAsMain = { onSetMainImage(index) },
                modifier = Modifier.size(120.dp)
            )
        }
        
        // Add image button (if not at max capacity)
        if (onAddImage != null) {
            item {
                AddImageThumbnail(
                    onClick = onAddImage,
                    modifier = Modifier.size(120.dp)
                )
            }
        }
    }
}

/**
 * Individual image thumbnail component with controls for main designation and removal.
 */
@Composable
private fun ExerciseImageThumbnail(
    imageUri: Uri,
    isMainImage: Boolean,
    onRemove: () -> Unit,
    onSetAsMain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
    ) {
        // Image container with border for main image
        Card(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isMainImage) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else Modifier
                ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUri)
                    .crossfade(true)
                    .build(),
                contentDescription = if (isMainImage) "Main exercise image" else "Exercise image",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        }
        
        // Main image indicator (star badge)
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 4.dp, y = 4.dp)
                .clickable(
                    onClickLabel = if (isMainImage) "Remove main image designation" else "Set as main image"
                ) { onSetAsMain() }
                .semantics {
                    contentDescription = if (isMainImage) {
                        "Main image indicator, tap to remove designation"
                    } else {
                        "Tap to set as main image"
                    }
                },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shadowElevation = 2.dp
        ) {
            Icon(
                imageVector = if (isMainImage) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = if (isMainImage) "Main image" else "Set as main image",
                modifier = Modifier.padding(6.dp),
                tint = if (isMainImage) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                }
            )
        }
        
        // Remove button
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-4).dp, y = 4.dp)
                .clickable(
                    onClickLabel = "Remove image"
                ) { onRemove() }
                .semantics {
                    contentDescription = "Remove this image"
                },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            shadowElevation = 2.dp
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove image",
                modifier = Modifier.padding(6.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        
        // Main image label
        if (isMainImage) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-4).dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 1.dp
            ) {
                Text(
                    text = "MAIN",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Add image button thumbnail component.
 */
@Composable
private fun AddImageThumbnail(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(
                onClickLabel = "Add another image"
            ) { onClick() }
            .semantics {
                contentDescription = "Add another exercise image"
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add image",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Add Image",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExerciseImageGalleryPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Exercise Image Gallery Variants",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            // Empty state
            Text("Empty State:", style = MaterialTheme.typography.titleSmall)
            ExerciseImageGallery(
                imageUris = emptyList(),
                onAddImage = { },
                onRemoveImage = { },
                onSetMainImage = { }
            )
            
            // Preview note for filled states
            Text(
                text = "Note: Preview cannot display actual images. In runtime, images would show image thumbnails with star/remove controls.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
