package com.example.liftrix.ui.profile.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.components.isStoragePath
import com.example.liftrix.ui.common.LocalFirebaseStorageUrlResolver
import com.example.liftrix.ui.common.LocalProfileImageCache
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Enhanced profile image display component with comprehensive image loading capabilities.
 * 
 * Features:
 * - Circular image display with proper aspect ratio
 * - Multi-level caching integration via ProfileImageCache
 * - Storage path resolution via CompositionLocal FirebaseStorageUrlResolver
 * - Fallback to user initials when no image is available
 * - Loading state with skeleton shimmer effect
 * - Error handling with graceful fallback
 * - Clickable area for image management actions
 * - Accessibility support with proper semantics
 * - Material 3 design compliance
 * - Responsive sizing for different use cases
 * - Clean API without manual dependency injection
 * 
 * @param imageUrl Optional profile image URL or storage path from Firebase Storage
 * @param displayName User's display name for initials fallback
 * @param userId User ID for cache scoping and analytics
 * @param size Size of the circular image display
 * @param onClick Callback invoked when image is clicked (for upload/change)
 * @param modifier Modifier for styling the component
 * @param contentDescription Custom accessibility description
 */
@Composable
fun ProfileImageDisplay(
    imageUrl: String?,
    displayName: String?,
    userId: String?,
    size: Dp = 60.dp,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val scope = rememberCoroutineScope()
    val urlResolver = LocalFirebaseStorageUrlResolver.current
    val profileImageCache = LocalProfileImageCache.current

    // Create stable key from URL to prevent recomposition loops
    // Using hashCode ensures we only reload when URL content actually changes
    val urlKey = remember(imageUrl) { imageUrl?.hashCode() }

    // Image loading state management with stable keys
    var isLoading by remember(urlKey) { mutableStateOf(!imageUrl.isNullOrBlank()) }
    var loadedImage by remember(urlKey) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var hasError by remember(urlKey) { mutableStateOf(false) }

    // Load image when URL key or userId changes (prevents redundant loads on recomposition)
    LaunchedEffect(urlKey, userId) {
        if (!imageUrl.isNullOrBlank() && userId != null) {
            Timber.d("PFP_DEBUG: ProfileImageDisplay starting load for current user with URL: $imageUrl")
            scope.launch {
                try {
                    isLoading = true
                    hasError = false

                    // Resolve storage path to URL if needed
                    val resolvedUrl = if (imageUrl.isStoragePath()) {
                        if (urlResolver != null) {
                            Timber.d("PFP_DEBUG: Resolving storage path to URL for current user | path=<redacted>")
                            urlResolver.resolveUrl(imageUrl)
                        } else {
                            Timber.w("PFP_DEBUG: Storage path provided but no urlResolver available for current user | path=<redacted>")
                            null
                        }
                    } else {
                        Timber.d("PFP_DEBUG: Using URL directly for current user | url=<redacted>")
                        imageUrl
                    }

                    if (resolvedUrl != null && profileImageCache != null) {
                        // Use ProfileImageCache singleton for optimized loading
                        Timber.d("PFP_DEBUG: Using shared ProfileImageCache for current user | resolvedUrl=<redacted>")

                        val bitmap = profileImageCache.loadImage(resolvedUrl, userId)
                        
                        loadedImage = bitmap
                        hasError = bitmap == null
                        
                        if (bitmap != null) {
                            Timber.d("PFP_DEBUG: âœ… ProfileImageDisplay cache hit for current user | bitmap=${bitmap.width}x${bitmap.height}")
                        } else {
                            Timber.w("PFP_DEBUG: âŒ ProfileImageDisplay cache miss for current user | resolvedUrl=<redacted>")
                        }
                    } else {
                        if (profileImageCache == null) {
                            Timber.e("PFP_DEBUG: âŒ ProfileImageCache not provided via CompositionLocal for current user")
                        }
                        if (resolvedUrl == null) {
                            Timber.w("PFP_DEBUG: âŒ Failed to resolve URL for current user | originalInput=<redacted>")
                        }
                        hasError = true
                        loadedImage = null
                    }
                } catch (e: Exception) {
                    Timber.e("PFP_DEBUG: ðŸ’¥ ProfileImageDisplay failed to load image for current user from URL=<redacted> | Error: ${e.message}", e)
                    hasError = true
                    loadedImage = null
                } finally {
                    isLoading = false
                    Timber.d("PFP_DEBUG: ProfileImageDisplay loading completed for current user | hasError=$hasError | hasImage=${loadedImage != null}")
                }
            }
        } else {
            Timber.d("PFP_DEBUG: ðŸ”¤ ProfileImageDisplay no image URL for current user (imageUrl='$imageUrl'), showing initials fallback")
            isLoading = false
            loadedImage = null
            hasError = false
        }
    }
    
    // Generate user initials for fallback
    val initials = remember(displayName) {
        displayName
            ?.trim()
            ?.split(' ')
            ?.take(2)
            ?.mapNotNull { it.firstOrNull()?.uppercaseChar() }
            ?.joinToString("")
            ?.ifEmpty { "?" }
            ?: "?"
    }
    
    val accessibilityDescription = contentDescription 
        ?: if (loadedImage != null) "Profile picture"
        else "Profile picture placeholder with initials $initials"
    
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (loadedImage != null) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.primaryContainer
            )
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(
                            role = androidx.compose.ui.semantics.Role.Button,
                            onClickLabel = "Change profile picture"
                        ) { onClick() }
                } else Modifier
            )
            .semantics {
                this.contentDescription = accessibilityDescription + if (onClick != null) ", tap to change" else ""
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                // Loading state with subtle shimmer effect
                CircularProgressIndicator(
                    modifier = Modifier.size(size * 0.4f),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
            }
            
            loadedImage != null && !hasError -> {
                // Display loaded image
                val imageBitmap = loadedImage!!
                Image(
                    painter = BitmapPainter(imageBitmap.asImageBitmap()),
                    contentDescription = accessibilityDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            
            else -> {
                // Fallback to initials or person icon
                if (initials != "?" && !displayName.isNullOrBlank()) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = MaterialTheme.typography.headlineSmall.fontSize * (size.value / 60f)
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = accessibilityDescription,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(size * 0.5f)
                    )
                }
            }
        }
        
        // Error indicator (subtle red border)
        if (hasError && !imageUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
            )
        }
    }
}

/**
 * Compact profile image display for use in lists, cards, and dense layouts.
 * 
 * @param imageUrl Optional profile image URL or storage path
 * @param displayName User's display name for initials
 * @param userId User ID for cache scoping
 * @param onClick Optional click handler
 * @param modifier Modifier for styling
 * @param urlResolver Firebase storage URL resolver
 */
@Composable
fun CompactProfileImage(
    imageUrl: String?,
    displayName: String?,
    userId: String?,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    ProfileImageDisplay(
        imageUrl = imageUrl,
        displayName = displayName,
        userId = userId,
        size = 40.dp,
        onClick = onClick,
        modifier = modifier,
        contentDescription = "Compact profile picture"
    )
}

/**
 * Large profile image display for profile screens and detailed views.
 * 
 * @param imageUrl Optional profile image URL or storage path
 * @param displayName User's display name for initials
 * @param userId User ID for cache scoping
 * @param onClick Optional click handler for image management
 * @param modifier Modifier for styling
 * @param urlResolver Firebase storage URL resolver
 */
@Composable
fun LargeProfileImage(
    imageUrl: String?,
    displayName: String?,
    userId: String?,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    ProfileImageDisplay(
        imageUrl = imageUrl,
        displayName = displayName,
        userId = userId,
        size = 120.dp,
        onClick = onClick,
        modifier = modifier,
        contentDescription = "Large profile picture"
    )
}

