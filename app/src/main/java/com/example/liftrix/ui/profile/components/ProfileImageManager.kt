package com.example.liftrix.ui.profile.components

import android.graphics.Rect
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.liftrix.domain.usecase.profile.DeleteProfileImageUseCase
import com.example.liftrix.domain.usecase.profile.UploadProfileImageUseCase
import com.example.liftrix.ui.navigation.LiftrixRoute
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Complete profile image management component orchestrating the full workflow.
 * 
 * Features:
 * - Integrated ProfileImageDisplay with click handling
 * - Image picker dialog for camera/gallery selection
 * - Navigation to crop screen for image editing
 * - Upload progress tracking with visual feedback
 * - Error handling with user-friendly messages
 * - Delete functionality with confirmation dialog
 * - State management for the entire image workflow
 * - Accessibility support throughout the process
 * - Material 3 design consistency
 * 
 * Workflow:
 * 1. User clicks on profile image
 * 2. Image picker dialog appears (camera/gallery)
 * 3. Selected image navigates to crop screen
 * 4. Cropped image uploads with progress indicator
 * 5. Success/error feedback displayed to user
 * 
 * @param currentImageUrl Current profile image URL (null if no image)
 * @param displayName User's display name for initials fallback
 * @param userId User ID for upload operations
 * @param uploadUseCase Use case for uploading profile images
 * @param deleteUseCase Use case for deleting profile images
 * @param navController Navigation controller for crop screen navigation
 * @param size Size of the profile image display
 * @param onImageUpdated Callback when image is successfully updated
 * @param onError Callback when an error occurs
 * @param modifier Modifier for styling the component
 */
@Composable
fun ProfileImageManager(
    currentImageUrl: String?,
    displayName: String?,
    userId: String,
    uploadUseCase: UploadProfileImageUseCase,
    deleteUseCase: DeleteProfileImageUseCase,
    navController: NavController,
    size: Dp = 120.dp,
    onImageUpdated: (String?) -> Unit = { },
    onError: (String) -> Unit = { },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // UI state management
    var showImagePicker by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    
    // Temporary state for crop workflow
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var cropRect by remember { mutableStateOf<Rect?>(null) }
    
    // Handle crop completion from navigation
    LaunchedEffect(selectedImageUri, cropRect) {
        if (selectedImageUri != null && cropRect != null) {
            scope.launch {
                try {
                    isUploading = true
                    uploadProgress = 0f
                    errorMessage = null
                    successMessage = null
                    
                    Timber.d("Starting image upload for user: $userId")
                    
                    // Simulate upload progress (in real implementation, this would come from the use case)
                    uploadProgress = 0.3f
                    
                    val result = uploadUseCase(
                        userId = userId,
                        imageUri = selectedImageUri!!,
                        cropRect = cropRect
                    )
                    
                    uploadProgress = 1f
                    
                    if (result.isSuccess) {
                        val imageUrl = result.getOrThrow()
                        successMessage = "Profile picture updated successfully!"
                        onImageUpdated(imageUrl)
                        Timber.i("Profile image upload successful: $imageUrl")
                    } else {
                        val error = result.exceptionOrNull()
                        val message = error?.message ?: "Failed to upload image"
                        errorMessage = message
                        onError(message)
                        Timber.e(error, "Profile image upload failed")
                    }
                } catch (e: Exception) {
                    val message = "Unexpected error during upload: ${e.message}"
                    errorMessage = message
                    onError(message)
                    Timber.e(e, "Unexpected error during profile image upload")
                } finally {
                    isUploading = false
                    // Clear temporary state
                    selectedImageUri = null
                    cropRect = null
                }
            }
        }
    }
    
    // Handle image deletion
    fun handleImageDelete() {
        scope.launch {
            try {
                isUploading = true
                errorMessage = null
                successMessage = null
                
                Timber.d("Starting image deletion for user: $userId")
                
                val result = deleteUseCase(userId)
                
                if (result.isSuccess) {
                    successMessage = "Profile picture removed successfully!"
                    onImageUpdated(null)
                    Timber.i("Profile image deletion successful")
                } else {
                    val error = result.exceptionOrNull()
                    val message = error?.message ?: "Failed to remove image"
                    errorMessage = message
                    onError(message)
                    Timber.e(error, "Profile image deletion failed")
                }
            } catch (e: Exception) {
                val message = "Unexpected error during deletion: ${e.message}"
                errorMessage = message
                onError(message)
                Timber.e(e, "Unexpected error during profile image deletion")
            } finally {
                isUploading = false
                showDeleteConfirmation = false
            }
        }
    }
    
    // Navigate to crop screen
    fun navigateToCropScreen(imageUri: Uri) {
        selectedImageUri = imageUri
        navController.navigate(
            LiftrixRoute.ImageCrop(imageUri = imageUri.toString())
        )
    }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Profile image display with upload overlay
        Box(
            contentAlignment = Alignment.Center
        ) {
            ProfileImageDisplay(
                imageUrl = currentImageUrl,
                displayName = displayName,
                userId = userId,
                size = size,
                onClick = if (!isUploading) {
                    { showImagePicker = true }
                } else null, // Disable clicking during upload
                modifier = Modifier.semantics {
                    contentDescription = if (isUploading) {
                        "Profile picture uploading, please wait"
                    } else {
                        "Profile picture, tap to change"
                    }
                }
            )
            
            // Upload progress overlay
            if (isUploading) {
                Box(
                    modifier = Modifier
                        .size(size)
                        .semantics {
                            contentDescription = "Upload in progress: ${(uploadProgress * 100).toInt()}%"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = uploadProgress,
                        modifier = Modifier.size(size * 0.8f),
                        strokeWidth = 4.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    
                    if (uploadProgress > 0f) {
                        Text(
                            text = "${(uploadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        
        // Action buttons
        if (!isUploading) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Change picture button
                OutlinedButton(
                    onClick = { showImagePicker = true },
                    modifier = Modifier.semantics {
                        contentDescription = "Change profile picture"
                    }
                ) {
                    Text("Change Picture")
                }
                
                // Remove picture button (only show if there's a current image)
                if (!currentImageUrl.isNullOrBlank()) {
                    TextButton(
                        onClick = { showDeleteConfirmation = true },
                        modifier = Modifier.semantics {
                            contentDescription = "Remove current profile picture"
                        }
                    ) {
                        Text(
                            text = "Remove",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        
        // Status messages
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
        
        successMessage?.let { message ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        
        // Clear messages after delay
        LaunchedEffect(errorMessage) {
            if (errorMessage != null) {
                kotlinx.coroutines.delay(5000)
                errorMessage = null
            }
        }
        
        LaunchedEffect(successMessage) {
            if (successMessage != null) {
                kotlinx.coroutines.delay(3000)
                successMessage = null
            }
        }
    }
    
    // Image picker dialog
    ImagePickerDialog(
        isVisible = showImagePicker,
        onDismiss = { showImagePicker = false },
        onImageSelected = { uri ->
            navigateToCropScreen(uri)
            showImagePicker = false
        },
        onError = { message ->
            errorMessage = message
            showImagePicker = false
        }
    )
    
    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Text(
                    text = "Remove Profile Picture",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove your profile picture? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { handleImageDelete() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = "Remove",
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Simplified profile image manager for contexts where navigation is not available.
 */
@Composable
fun SimpleProfileImageDisplay(
    currentImageUrl: String?,
    displayName: String?,
    userId: String?,
    size: Dp = 60.dp,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    ProfileImageDisplay(
        imageUrl = currentImageUrl,
        displayName = displayName,
        userId = userId,
        size = size,
        onClick = onClick,
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
private fun ProfileImageManagerPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Profile Image Manager",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            // Note: Preview shows UI structure but cannot demonstrate full functionality
            // due to dependency requirements (use cases, navigation controller)
            
            SimpleProfileImageDisplay(
                currentImageUrl = null,
                displayName = "John Doe",
                userId = "user123",
                size = 120.dp,
                onClick = { }
            )
            
            Text(
                text = "Preview Mode - Full functionality requires runtime dependencies",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}