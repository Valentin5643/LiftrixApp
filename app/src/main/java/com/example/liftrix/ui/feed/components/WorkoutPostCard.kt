package com.example.liftrix.ui.feed.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.liftrix.R
import com.example.liftrix.domain.model.social.MediaItem
import com.example.liftrix.domain.model.social.MediaType
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.ui.components.DynamicProfileImage
import com.example.liftrix.ui.components.isStoragePath
import com.example.liftrix.ui.components.extractStoragePathFromUrl
import com.example.liftrix.data.service.FirebaseStorageUrlResolver
import com.google.firebase.storage.FirebaseStorage
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.theme.rememberWeightUnitManager
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

/**
 * Rich workout post card component with media display and engagement bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutPostCard(
    post: WorkoutPost,
    isLiked: Boolean,
    isSaved: Boolean,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    onProfileClick: () -> Unit,
    onWorkoutCopyClick: () -> Unit,
    onWorkoutClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    onBlockUser: () -> Unit = {},
    onReportPost: () -> Unit = {},
    onEditWorkout: () -> Unit = {},
    isOwnPost: Boolean = false,
    urlResolver: FirebaseStorageUrlResolver = FirebaseStorageUrlResolver(FirebaseStorage.getInstance())
) {
    val hapticFeedback = LocalHapticFeedback.current

    // Removed LiftrixCard wrapper for cleaner, more minimal appearance
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = LiftrixSpacing.small)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LiftrixSpacing.medium)
        ) {
            // Post header with user info
            PostHeader(
                post = post,
                onProfileClick = onProfileClick,
                onBlockUser = onBlockUser,
                onReportPost = onReportPost,
                onEditWorkout = onEditWorkout,
                isOwnPost = isOwnPost,
                urlResolver = urlResolver,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
            
            // Workout metrics - displayed prominently without box
            WorkoutSummaryCard(
                post = post,
                onCopyClick = onWorkoutCopyClick,
                onWorkoutClick = onWorkoutClick,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Post caption - moved after metrics for better hierarchy
            if (post.caption.isNotBlank()) {
                Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
                Text(
                    text = post.caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LiftrixColorsV2.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Media carousel if available
            if (post.mediaItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
                MediaCarousel(
                    mediaItems = post.mediaItems,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.small))
            
            // Engagement bar
            EngagementBar(
                likeCount = post.likeCount,
                commentCount = post.commentCount,
                shareCount = post.shareCount,
                isLiked = isLiked,
                isSaved = isSaved,
                onLikeClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLikeClick()
                },
                onCommentClick = onCommentClick,
                onShareClick = onShareClick,
                onSaveClick = onSaveClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Add subtle divider at the bottom of each post
        Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 0.5.dp,
            color = LiftrixColorsV2.onSurfaceVariant.copy(alpha = 0.1f)
        )
    }
}

@Composable
private fun PostHeader(
    post: WorkoutPost,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    onBlockUser: () -> Unit = {},
    onReportPost: () -> Unit = {},
    onEditWorkout: () -> Unit = {},
    isOwnPost: Boolean = false,
    urlResolver: FirebaseStorageUrlResolver
) {
    var showOptionsMenu by remember { mutableStateOf(false) }
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile section (clickable)
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable { onProfileClick() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile image - using DynamicProfileImage for token-resilient loading
            val userIdentifier = "${post.authorDisplayName ?: post.authorUsername} (${post.userId})"
            val profilePath = if (post.authorProfilePhotoUrl.isStoragePath()) {
                post.authorProfilePhotoUrl
            } else {
                // Extract storage path from URL using utility function
                post.authorProfilePhotoUrl.extractStoragePathFromUrl()
            }
            
            Timber.d("PFP_DEBUG: Feed profile image for $userIdentifier | originalUrl='${post.authorProfilePhotoUrl}' | extractedPath='$profilePath'")
            
            // Use DynamicProfileImage for token-resilient loading
            // Note: DynamicProfileImage now handles both paths and URLs automatically via CompositionLocal
            DynamicProfileImage(
                storagePath = profilePath ?: post.authorProfilePhotoUrl,
                displayName = post.authorDisplayName ?: post.authorUsername ?: "User",
                contentDescription = "Profile picture of ${post.authorDisplayName}",
                modifier = Modifier.size(40.dp),
                debugContext = "Feed-$userIdentifier"
            )
            
            Spacer(modifier = Modifier.width(LiftrixSpacing.small))
            
            Column {
                Text(
                    text = post.authorDisplayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = LiftrixColorsV2.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                
                Text(
                    text = formatTimestamp(post.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = LiftrixColorsV2.onSurfaceVariant
                )
            }
        }
        
        // PR badge if applicable
        if (post.prsCount > 0) {
            Surface(
                color = LiftrixColorsV2.primary,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(
                    text = "PR",
                    style = MaterialTheme.typography.labelSmall,
                    color = LiftrixColorsV2.onPrimary,
                    modifier = Modifier.padding(
                        horizontal = 8.dp,
                        vertical = 4.dp
                    )
                )
            }
        }
        
        // More options menu
        IconButton(onClick = { showOptionsMenu = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = LiftrixColorsV2.onSurfaceVariant
            )
        }
        
        DropdownMenu(
            expanded = showOptionsMenu,
            onDismissRequest = { showOptionsMenu = false }
        ) {
            if (isOwnPost) {
                // Options for user's own posts
                DropdownMenuItem(
                    text = { Text("Edit Workout") },
                    leadingIcon = { 
                        Icon(Icons.Default.Edit, contentDescription = null) 
                    },
                    onClick = {
                        onEditWorkout()
                        showOptionsMenu = false
                    }
                )
            } else {
                // Options for other users' posts
                DropdownMenuItem(
                    text = { Text("Block User") },
                    leadingIcon = { 
                        Icon(Icons.Default.Block, contentDescription = null) 
                    },
                    onClick = {
                        onBlockUser()
                        showOptionsMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Report Post") },
                    leadingIcon = { 
                        Icon(Icons.Default.Flag, contentDescription = null) 
                    },
                    onClick = {
                        onReportPost()
                        showOptionsMenu = false
                    }
                )
            }
        }
    }
}

@Composable
private fun WorkoutSummaryCard(
    post: WorkoutPost,
    onCopyClick: () -> Unit,
    onWorkoutClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        // Workout metrics displayed directly without background box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onWorkoutClick() },
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            // Duration
            WorkoutMetric(
                value = formatDuration(post.workoutDuration),
                label = "Duration",
                modifier = Modifier.padding(end = 32.dp)
            )
            
            // Volume
            val weightUnitManager = rememberWeightUnitManager()
            val volumeText = post.totalVolume?.let { volume ->
                // Debug logging to identify the issue
                Timber.d("UI-MAPPER-DEBUG: WorkoutPost volume display - post.totalVolume=$volume kg, weightUnitManager=${weightUnitManager != null}")
                
                // totalVolume is stored in kilograms (base unit), format for viewer's preferred unit
                if (weightUnitManager != null) {
                    val formattedWeight = weightUnitManager.formatWeightCompact(volume, WeightUnit.KILOGRAMS)
                    Timber.d("UI-MAPPER-DEBUG: Formatted weight via WeightUnitManager: $formattedWeight")
                    formattedWeight
                } else {
                    val fallbackLbs = (volume * 2.20462).toInt()
                    val fallbackText = "$fallbackLbs lbs"
                    Timber.d("UI-MAPPER-DEBUG: WeightUnitManager null, using fallback: $fallbackText")
                    fallbackText
                }
            } ?: "0 lbs".also { 
                Timber.w("UI-MAPPER-DEBUG: post.totalVolume is null! Post ID: ${post.id}, workout ID: ${post.workoutId}")
            }
            
            WorkoutMetric(
                value = volumeText,
                label = "Volume",
                modifier = Modifier.padding(end = 32.dp)
            )
            
            // Exercises count
            WorkoutMetric(
                value = "${post.exercisesCount}",
                label = if (post.exercisesCount == 1) "exercise" else "exercises",
                modifier = Modifier.weight(1f)
            )
            
            // Copy workout button - moved to the right
            IconButton(
                onClick = onCopyClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy workout",
                    tint = LiftrixColorsV2.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        // INT-004: Exercise list with custom exercise indicators
        if (post.exercises.isNotEmpty()) {
            Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
            ExerciseList(
                exercises = post.exercises,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun WorkoutMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Top
    ) {
        // Large value text - prominent like in reference image
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = LiftrixColorsV2.onSurface,
            fontWeight = FontWeight.Bold
        )
        
        // Smaller label text below
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = LiftrixColorsV2.onSurfaceVariant
        )
    }
}

@Composable
private fun MediaCarousel(
    mediaItems: List<MediaItem>,
    modifier: Modifier = Modifier
) {
    // Full-width media display for cleaner look
    if (mediaItems.size == 1) {
        // Single image - show full width
        MediaItem(
            mediaItem = mediaItems.first(),
            modifier = modifier
                .fillMaxWidth()
                .height(300.dp)
        )
    } else {
        // Multiple images - horizontal carousel
        LazyRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.small),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(mediaItems) { mediaItem ->
                MediaItem(
                    mediaItem = mediaItem,
                    modifier = Modifier
                        .width(280.dp)
                        .height(200.dp)
                )
            }
        }
    }
}

@Composable
private fun MediaItem(
    mediaItem: MediaItem,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        AsyncImage(
            model = mediaItem.thumbnailUrl ?: mediaItem.originalUrl,
            contentDescription = "Post media",
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp)),  // Slightly less rounded for modern look
            contentScale = ContentScale.Crop
        )
        
        // Video play indicator
        if (mediaItem.type == MediaType.VIDEO) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
                color = LiftrixColorsV2.surface.copy(alpha = 0.8f),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play video",
                    tint = LiftrixColorsV2.onSurface,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun EngagementBar(
    likeCount: Int,
    commentCount: Int,
    shareCount: Int,
    isLiked: Boolean,
    isSaved: Boolean,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Like, Comment, Share buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.large)
        ) {
            EngagementButton(
                icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                count = likeCount,
                isActive = isLiked,
                onClick = onLikeClick,
                contentDescription = if (isLiked) "Unlike" else "Like"
            )
            
            EngagementButton(
                icon = Icons.Outlined.ChatBubbleOutline,
                count = commentCount,
                isActive = false,
                onClick = onCommentClick,
                contentDescription = "Comment"
            )
            
            EngagementButton(
                icon = Icons.Outlined.Share,
                count = shareCount,
                isActive = false,
                onClick = onShareClick,
                contentDescription = "Share"
            )
        }
        
        // Save button
        IconButton(onClick = onSaveClick) {
            Icon(
                imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = if (isSaved) "Unsave" else "Save",
                tint = if (isSaved) LiftrixColorsV2.primary else LiftrixColorsV2.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EngagementButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    isActive: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) LiftrixColorsV2.primary else LiftrixColorsV2.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        
        if (count > 0) {
            Text(
                text = formatCount(count),
                style = MaterialTheme.typography.labelMedium,
                color = LiftrixColorsV2.onSurfaceVariant
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "now"
        diff < 3600_000 -> "${diff / 60_000}m"
        diff < 86400_000 -> "${diff / 3600_000}h"
        diff < 604800_000 -> "${diff / 86400_000}d"
        else -> {
            val format = SimpleDateFormat("MMM d", Locale.getDefault())
            format.format(Date(timestamp))
        }
    }
}

private fun formatDuration(minutes: Int?): String {
    if (minutes == null || minutes == 0) return "0m"
    
    return when {
        minutes < 60 -> "${minutes}m"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}

private fun formatCount(count: Int): String {
    return when {
        count < 1000 -> count.toString()
        count < 10000 -> "${count / 1000}.${(count % 1000) / 100}k"
        count < 1000000 -> "${count / 1000}k"
        else -> "${count / 1000000}.${(count % 1000000) / 100000}M"
    }
}

// INT-004: Exercise list component with custom exercise indicators
@Composable
private fun ExerciseList(
    exercises: List<com.example.liftrix.domain.model.social.PostExercise>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
    ) {
        Text(
            text = "Exercises",
            style = MaterialTheme.typography.labelMedium,
            color = LiftrixColorsV2.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.small),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(exercises.take(5)) { exercise -> // Limit to 5 exercises for better UX
                ExerciseChip(exercise = exercise)
            }
            
            if (exercises.size > 5) {
                item {
                    ExerciseCountChip(remainingCount = exercises.size - 5)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseChip(
    exercise: com.example.liftrix.domain.model.social.PostExercise,
    modifier: Modifier = Modifier
) {
    AssistChip(
        onClick = { /* Could navigate to exercise details */ },
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Custom exercise indicator
                if (exercise.isCustomExercise) {
                    Text(
                        text = "🔧",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                // PR indicator
                if (exercise.isPR) {
                    Text(
                        text = "💪",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (exercise.isCustomExercise) 
                LiftrixColorsV2.primaryContainer.copy(alpha = 0.3f)
            else 
                LiftrixColorsV2.surfaceVariant,
            labelColor = LiftrixColorsV2.onSurface
        ),
        border = if (exercise.isCustomExercise) 
            BorderStroke(
                width = 1.dp,
                color = LiftrixColorsV2.primary.copy(alpha = 0.5f)
            )
        else 
            null,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseCountChip(
    remainingCount: Int,
    modifier: Modifier = Modifier
) {
    AssistChip(
        onClick = { /* Could expand to show all exercises */ },
        label = {
            Text(
                text = "+$remainingCount more",
                style = MaterialTheme.typography.bodySmall,
                color = LiftrixColorsV2.onSurfaceVariant
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = LiftrixColorsV2.surfaceVariant.copy(alpha = 0.5f),
            labelColor = LiftrixColorsV2.onSurfaceVariant
        ),
        modifier = modifier
    )
}