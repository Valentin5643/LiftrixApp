package com.example.liftrix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.liftrix.data.service.FirebaseStorageUrlResolver
import com.example.liftrix.ui.common.LocalFirebaseStorageUrlResolver
import timber.log.Timber

/**
 * Dynamic Profile Image component that resolves storage paths to fresh URLs.
 * 
 * This component eliminates the token invalidation problem by:
 * 1. Accepting storage paths instead of full URLs
 * 2. Resolving to fresh download URLs at runtime using CompositionLocal resolver
 * 3. Handling fallback to initials when images fail to load
 * 4. Providing consistent behavior across all profile contexts
 * 
 * Key Benefits:
 * - Always gets fresh, valid download tokens
 * - Eliminates 403 errors from stale URLs
 * - Consistent fallback behavior
 * - Proper error handling and logging
 * - Clean API without manual dependency injection
 * 
 * The component automatically accesses FirebaseStorageUrlResolver from CompositionLocal,
 * eliminating the need to manually pass the resolver as a parameter.
 */
@Composable
fun DynamicProfileImage(
    storagePath: String?,
    displayName: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackBackgroundColor: Color = MaterialTheme.colorScheme.primary,
    fallbackTextColor: Color = MaterialTheme.colorScheme.onPrimary,
    fallbackTextSize: TextUnit = 24.sp,
    debugContext: String = "Unknown"
) {
    // Access FirebaseStorageUrlResolver from CompositionLocal
    val urlResolver = LocalFirebaseStorageUrlResolver.current
    
    // Resolve storage path to URL dynamically with proper state management
    var imageState by remember(storagePath) { mutableStateOf<ImageState>(ImageState.Loading) }

    // Effect to resolve URL when storage path changes
    LaunchedEffect(storagePath) {
        if (storagePath.isNullOrBlank()) {
            Timber.d("[DYNAMIC_PROFILE_IMAGE] No storage path provided for $debugContext")
            imageState = ImageState.ShowInitials
        } else if (urlResolver == null) {
            Timber.w("[DYNAMIC_PROFILE_IMAGE] No URL resolver available from CompositionLocal for $debugContext")
            imageState = ImageState.ShowInitials
        } else {
            Timber.d("[DYNAMIC_PROFILE_IMAGE] Resolving URL for $debugContext | path=$storagePath")
            imageState = ImageState.Loading
            
            try {
                val resolvedUrl = urlResolver.resolveUrl(storagePath)
                if (resolvedUrl != null) {
                    imageState = ImageState.HasUrl(resolvedUrl)
                    Timber.d("[DYNAMIC_PROFILE_IMAGE] ✅ URL resolved for $debugContext")
                } else {
                    Timber.w("[DYNAMIC_PROFILE_IMAGE] ❌ Failed to resolve URL for $debugContext")
                    imageState = ImageState.ShowInitials
                }
            } catch (e: Exception) {
                Timber.e(e, "[DYNAMIC_PROFILE_IMAGE] Exception resolving URL for $debugContext")
                imageState = ImageState.ShowInitials
            }
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(fallbackBackgroundColor),
        contentAlignment = Alignment.Center
    ) {
        when (val state = imageState) {
            is ImageState.Loading -> {
                // Show loading state
                Text(
                    text = "...",
                    color = fallbackTextColor,
                    fontSize = fallbackTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            
            is ImageState.HasUrl -> {
                // Show image with automatic fallback on error
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(state.url)
                        .crossfade(true)
                        .build(),
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                    onSuccess = { 
                        Timber.d("[DYNAMIC_PROFILE_IMAGE] ✅ Successfully loaded image for $debugContext")
                    },
                    onError = { error ->
                        val httpCode = if (error.result.throwable?.message?.contains("403") == true) "HTTP 403" else "Unknown"
                        Timber.e("[DYNAMIC_PROFILE_IMAGE] ❌ Failed to load image for $debugContext | URL=${state.url} | Error=$httpCode | ${error.result.throwable?.message}")
                        // Automatically fall back to initials on image load error
                        imageState = ImageState.ShowInitials
                    },
                    onLoading = {
                        Timber.d("[DYNAMIC_PROFILE_IMAGE] 🔄 Loading image for $debugContext")
                    }
                )
            }
            
            is ImageState.ShowInitials -> {
                // Show initials fallback
                val initials = displayName.take(2).uppercase()
                Timber.d("[DYNAMIC_PROFILE_IMAGE] 🔤 Showing initials fallback for $debugContext | initials='$initials'")
                Text(
                    text = initials,
                    color = fallbackTextColor,
                    fontSize = fallbackTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Sealed class to represent the different states of the image loading process
 */
private sealed class ImageState {
    object Loading : ImageState()
    data class HasUrl(val url: String) : ImageState()
    object ShowInitials : ImageState()
}

/**
 * Convenience function to determine if a string is a storage path vs a full URL.
 * Storage paths start with a path component, URLs start with http(s)://
 */
fun String?.isStoragePath(): Boolean {
    return this?.let { 
        !it.startsWith("http://") && !it.startsWith("https://") && it.contains("/")
    } ?: false
}

/**
 * Legacy compatibility function - converts old URLs to storage paths if possible.
 * Helps with migration from URL-based to path-based storage.
 */
fun String?.extractStoragePathFromUrl(): String? {
    if (this == null) return null
    
    // Try to extract storage path from Firebase Storage URL
    val pathMatch = Regex("/o/([^?]+)\\?").find(this)
    return pathMatch?.groupValues?.get(1)?.replace("%2F", "/")
}