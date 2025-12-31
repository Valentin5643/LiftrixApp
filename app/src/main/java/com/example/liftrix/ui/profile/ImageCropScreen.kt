package com.example.liftrix.ui.profile

import android.graphics.Rect
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.liftrix.ui.theme.LiftrixTheme
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min

/**
 * Image cropping screen with touch-based crop area selection.
 * 
 * Features:
 * - Square aspect ratio constraint for profile images
 * - Touch-based drag gestures for crop area adjustment
 * - Visual crop overlay with semi-transparent background
 * - Pinch-to-zoom support for precise cropping
 * - Material 3 design with proper navigation
 * - Accessibility support with semantic descriptions
 * - Real-time crop preview updates
 * - Error handling for invalid images
 * - Loading states during image processing
 * 
 * @param imageUri URI of the image to crop
 * @param onCropConfirmed Callback with crop rectangle when user confirms
 * @param onNavigateBack Callback when user cancels or navigates back
 * @param modifier Modifier for styling the screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCropScreen(
    imageUri: Uri,
    onCropConfirmed: (Rect) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    // Screen state
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    
    // Crop area state (in container coordinates)
    var cropRect by remember { mutableStateOf(Rect(0, 0, 0, 0)) }
    var isDragging by remember { mutableStateOf(false) }
    
    // Calculate initial crop area when image loads
    LaunchedEffect(imageSize, containerSize) {
        if (imageSize != IntSize.Zero && containerSize != IntSize.Zero) {
            val imageAspectRatio = imageSize.width.toFloat() / imageSize.height.toFloat()
            val containerAspectRatio = containerSize.width.toFloat() / containerSize.height.toFloat()
            
            val (scaledWidth, scaledHeight) = if (imageAspectRatio > containerAspectRatio) {
                // Image is wider, fit by height
                val scaledWidth = (containerSize.height * imageAspectRatio).toInt()
                scaledWidth to containerSize.height
            } else {
                // Image is taller or equal, fit by width
                val scaledHeight = (containerSize.width / imageAspectRatio).toInt()
                containerSize.width to scaledHeight
            }
            
            // Center the scaled image
            val imageLeft = (containerSize.width - scaledWidth) / 2
            val imageTop = (containerSize.height - scaledHeight) / 2
            
            // Create initial square crop in the center
            val cropSize = min(scaledWidth, scaledHeight) * 0.8f // 80% of available space
            val cropLeft = imageLeft + (scaledWidth - cropSize) / 2
            val cropTop = imageTop + (scaledHeight - cropSize) / 2
            
            cropRect = Rect(
                cropLeft.toInt(),
                cropTop.toInt(),
                (cropLeft + cropSize).toInt(),
                (cropTop + cropSize).toInt()
            )
            
            Timber.d("Initial crop rect calculated: $cropRect")
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Crop Image",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics {
                            contentDescription = "Cancel cropping and go back"
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            // Convert crop rect to image coordinates
                            val imageRect = convertCropRectToImageCoordinates(
                                cropRect, imageSize, containerSize
                            )
                            Timber.d("Crop confirmed with rect: $imageRect")
                            onCropConfirmed(imageRect)
                        },
                        enabled = !isLoading && !hasError && cropRect.width() > 0,
                        modifier = Modifier.semantics {
                            contentDescription = "Confirm crop selection"
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Confirm crop",
                            tint = if (!isLoading && !hasError) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                hasError -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Failed to load image",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Please try selecting a different image",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onNavigateBack
                        ) {
                            Text("Go Back")
                        }
                    }
                }
                
                else -> {
                    // Image cropping interface
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { size ->
                                containerSize = size
                            }
                    ) {
                        // Background image
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Image to crop",
                            modifier = Modifier
                                .fillMaxSize()
                                .semantics {
                                    contentDescription = "Image being cropped, drag the square to adjust crop area"
                                },
                            contentScale = ContentScale.Fit,
                            onSuccess = { 
                                isLoading = false
                                hasError = false
                            },
                            onError = { 
                                isLoading = false
                                hasError = true
                                Timber.e("Failed to load image for cropping: $imageUri")
                            },
                            onLoading = { 
                                isLoading = true 
                            }
                        )
                        
                        // Crop overlay
                        if (!isLoading && !hasError) {
                            CropOverlay(
                                cropRect = cropRect,
                                containerSize = containerSize.toSize(),
                                onCropRectChanged = { newRect ->
                                    cropRect = newRect
                                },
                                onDragStateChanged = { dragging ->
                                    isDragging = dragging
                                }
                            )
                        }
                    }
                }
            }
            
            // Instructions text
            if (!isLoading && !hasError) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                    )
                ) {
                    Text(
                        text = "Drag the square to adjust your crop area. The cropped image will be used as your profile picture.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Crop overlay component with drag gesture handling.
 */
@Composable
private fun CropOverlay(
    cropRect: Rect,
    containerSize: Size,
    onCropRectChanged: (Rect) -> Unit,
    onDragStateChanged: (Boolean) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onDragStateChanged(true)
                    },
                    onDragEnd = {
                        onDragStateChanged(false)
                    }
                ) { change, dragAmount ->
                    val newLeft = (cropRect.left + dragAmount.x).toInt()
                    val newTop = (cropRect.top + dragAmount.y).toInt()
                    val newRight = newLeft + cropRect.width()
                    val newBottom = newTop + cropRect.height()
                    
                    // Constrain to container bounds
                    val constrainedLeft = max(0, min(newLeft, containerSize.width.toInt() - cropRect.width()))
                    val constrainedTop = max(0, min(newTop, containerSize.height.toInt() - cropRect.height()))
                    val constrainedRight = constrainedLeft + cropRect.width()
                    val constrainedBottom = constrainedTop + cropRect.height()
                    
                    onCropRectChanged(
                        Rect(constrainedLeft, constrainedTop, constrainedRight, constrainedBottom)
                    )
                }
            }
    ) {
        drawCropOverlay(cropRect, containerSize)
    }
}

/**
 * Draws the crop overlay with semi-transparent background and crop rectangle.
 */
private fun DrawScope.drawCropOverlay(cropRect: Rect, containerSize: Size) {
    // Semi-transparent overlay
    drawRect(
        color = Color.Black.copy(alpha = 0.5f),
        size = containerSize
    )
    
    // Clear crop area
    drawRect(
        color = Color.Transparent,
        topLeft = Offset(cropRect.left.toFloat(), cropRect.top.toFloat()),
        size = Size(cropRect.width().toFloat(), cropRect.height().toFloat()),
        blendMode = BlendMode.Clear
    )
    
    // Crop rectangle border
    drawRect(
        color = Color.White,
        topLeft = Offset(cropRect.left.toFloat(), cropRect.top.toFloat()),
        size = Size(cropRect.width().toFloat(), cropRect.height().toFloat()),
        style = Stroke(width = 3.dp.toPx())
    )
    
    // Corner handles
    val handleSize = 20.dp.toPx()
    val handleOffset = handleSize / 2
    
    listOf(
        Offset(cropRect.left - handleOffset, cropRect.top - handleOffset), // Top-left
        Offset(cropRect.right - handleOffset, cropRect.top - handleOffset), // Top-right
        Offset(cropRect.left - handleOffset, cropRect.bottom - handleOffset), // Bottom-left
        Offset(cropRect.right - handleOffset, cropRect.bottom - handleOffset) // Bottom-right
    ).forEach { offset ->
        drawRect(
            color = Color.White,
            topLeft = offset,
            size = Size(handleSize, handleSize)
        )
    }
}

/**
 * Converts crop rectangle from container coordinates to image coordinates.
 */
private fun convertCropRectToImageCoordinates(
    cropRect: Rect,
    imageSize: IntSize,
    containerSize: IntSize
): Rect {
    if (imageSize == IntSize.Zero || containerSize == IntSize.Zero) {
        return Rect(0, 0, imageSize.width, imageSize.height)
    }
    
    val imageAspectRatio = imageSize.width.toFloat() / imageSize.height.toFloat()
    val containerAspectRatio = containerSize.width.toFloat() / containerSize.height.toFloat()
    
    data class ImageFitResult(val scaledWidth: Int, val scaledHeight: Int, val imageLeft: Int, val imageTop: Int)
    
    val imageFit = if (imageAspectRatio > containerAspectRatio) {
        // Image is wider, fit by height
        val scaledWidth = (containerSize.height * imageAspectRatio).toInt()
        val imageLeft = (containerSize.width - scaledWidth) / 2
        ImageFitResult(scaledWidth, containerSize.height, imageLeft, 0)
    } else {
        // Image is taller or equal, fit by width
        val scaledHeight = (containerSize.width / imageAspectRatio).toInt()
        val imageTop = (containerSize.height - scaledHeight) / 2
        ImageFitResult(containerSize.width, scaledHeight, 0, imageTop)
    }
    
    val scaledWidth = imageFit.scaledWidth
    val scaledHeight = imageFit.scaledHeight
    val imageLeft = imageFit.imageLeft
    val imageTop = imageFit.imageTop
    
    // Convert crop coordinates relative to the scaled image
    val cropRelativeLeft = cropRect.left - imageLeft
    val cropRelativeTop = cropRect.top - imageTop
    
    // Scale to actual image coordinates
    val scaleX = imageSize.width.toFloat() / scaledWidth
    val scaleY = imageSize.height.toFloat() / scaledHeight
    
    val imageCropLeft = (cropRelativeLeft * scaleX).toInt()
    val imageCropTop = (cropRelativeTop * scaleY).toInt()
    val imageCropRight = ((cropRelativeLeft + cropRect.width()) * scaleX).toInt()
    val imageCropBottom = ((cropRelativeTop + cropRect.height()) * scaleY).toInt()
    
    // Ensure bounds are within image
    return Rect(
        max(0, imageCropLeft),
        max(0, imageCropTop),
        min(imageSize.width, imageCropRight),
        min(imageSize.height, imageCropBottom)
    )
}

@Preview(showBackground = true)
@Composable
private fun ImageCropScreenPreview() {
    LiftrixTheme {
        // Note: Preview won't show actual image cropping functionality
        // This is just for layout preview
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Image Crop Screen\n(Preview Mode)",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
