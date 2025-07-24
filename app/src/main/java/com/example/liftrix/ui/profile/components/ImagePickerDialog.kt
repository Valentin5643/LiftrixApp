package com.example.liftrix.ui.profile.components

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.liftrix.ui.theme.LiftrixTheme
import timber.log.Timber

/**
 * Image picker dialog component for profile picture selection.
 * 
 * Provides a user-friendly interface for selecting profile images with:
 * - Camera capture option with proper permissions handling
 * - Gallery/photo library selection
 * - Modern Material 3 design with accessibility support
 * - Permission request handling with user-friendly messaging
 * - Error handling and fallback options
 * - Proper cleanup and state management
 * 
 * @param isVisible Whether the dialog should be displayed
 * @param onDismiss Callback invoked when dialog is dismissed
 * @param onImageSelected Callback invoked when image is successfully selected
 * @param onError Callback invoked when an error occurs during selection
 */
@Composable
fun ImagePickerDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onImageSelected: (Uri) -> Unit,
    onError: (String) -> Unit = { },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Permission states
    var cameraPermissionGranted by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }
    
    // Camera capture state
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    
    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        cameraPermissionGranted = isGranted
        if (!isGranted) {
            showPermissionRationale = true
            Timber.w("Camera permission denied by user")
            onError("Camera permission is required to take photos")
        }
    }
    
    // Camera launcher for taking photos
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && capturedImageUri != null) {
            Timber.d("Camera capture successful: $capturedImageUri")
            onImageSelected(capturedImageUri!!)
            onDismiss()
        } else {
            Timber.w("Camera capture failed or cancelled")
            onError("Failed to capture photo")
        }
        capturedImageUri = null
    }
    
    // Gallery/photo picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            Timber.d("Gallery selection successful: $uri")
            onImageSelected(uri)
            onDismiss()
        } else {
            Timber.d("Gallery selection cancelled by user")
            // Don't show error for user cancellation
        }
    }
    
    // Create temporary URI for camera capture
    fun createImageUri(): Uri? {
        return try {
            val contentResolver = context.contentResolver
            contentResolver.insert(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "profile_${System.currentTimeMillis()}.jpg")
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to create image URI for camera capture")
            null
        }
    }
    
    // Handle camera action
    fun handleCameraAction() {
        try {
            val imageUri = createImageUri()
            if (imageUri != null) {
                capturedImageUri = imageUri
                cameraLauncher.launch(imageUri)
            } else {
                onError("Failed to prepare camera capture")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error launching camera")
            onError("Failed to open camera")
        }
    }
    
    // Handle gallery action
    fun handleGalleryAction() {
        try {
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } catch (e: Exception) {
            Timber.e(e, "Error launching gallery")
            onError("Failed to open photo gallery")
        }
    }
    
    if (isVisible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Dialog title
                    Text(
                        text = "Choose Profile Picture",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = "Select how you'd like to add your profile picture",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Camera option button
                    OutlinedButton(
                        onClick = {
                            if (cameraPermissionGranted) {
                                handleCameraAction()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .semantics {
                                contentDescription = "Take photo with camera"
                            },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Take Photo",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    // Gallery option button
                    OutlinedButton(
                        onClick = { handleGalleryAction() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .semantics {
                                contentDescription = "Choose from photo library"
                            },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Choose from Library",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Cancel button
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.semantics {
                            contentDescription = "Cancel image selection"
                        }
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    
    // Permission rationale dialog
    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = {
                Text(
                    text = "Camera Permission Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "To take photos for your profile picture, Liftrix needs access to your camera. You can grant this permission in your device settings.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showPermissionRationale = false }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionRationale = false
                        // Try requesting permission again
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                ) {
                    Text("Try Again")
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ImagePickerDialogPreview() {
    LiftrixTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            ImagePickerDialog(
                isVisible = true,
                onDismiss = { },
                onImageSelected = { },
                onError = { }
            )
        }
    }
}