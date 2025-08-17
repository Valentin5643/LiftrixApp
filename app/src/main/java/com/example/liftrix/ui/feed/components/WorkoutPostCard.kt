package com.example.liftrix.ui.feed.components

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
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.components.cards.LiftrixCard
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
    modifier: Modifier = Modifier,
    onBlockUser: () -> Unit = {},
    onReportPost: () -> Unit = {},
    isOwnPost: Boolean = false
) {
    val hapticFeedback = LocalHapticFeedback.current

    LiftrixCard(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.medium)
        ) {
            // Post header with user info
            PostHeader(
                post = post,
                onProfileClick = onProfileClick,
                onBlockUser = onBlockUser,
                onReportPost = onReportPost,
                isOwnPost = isOwnPost,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.small))
            
            // Post caption
            if (post.caption.isNotBlank()) {
                Text(
                    text = post.caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LiftrixColorsV2.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(LiftrixSpacing.small))
            }
            
            // Workout summary card
            WorkoutSummaryCard(
                post = post,
                onCopyClick = onWorkoutCopyClick,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Media carousel if available
            if (post.mediaItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(LiftrixSpacing.small))
                MediaCarousel(
                    mediaItems = post.mediaItems,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
            
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
    }
}

@Composable
private fun PostHeader(
    post: WorkoutPost,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    onBlockUser: () -> Unit = {},
    onReportPost: () -> Unit = {},
    isOwnPost: Boolean = false
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
            // Profile image
            AsyncImage(
                model = post.authorProfilePhotoUrl ?: "",
                contentDescription = "Profile picture of ${post.authorDisplayName}",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
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
        
        // More options menu (only for other users' posts)
        if (!isOwnPost) {
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
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    WorkoutStat(
                        label = "Duration",
                        value = formatDuration(post.workoutDuration),
                        icon = Icons.Outlined.Schedule
                    )
                    
                    WorkoutStat(
                        label = "Volume",
                        value = "${post.totalVolume?.toInt() ?: 0} lbs",
                        icon = Icons.Outlined.FitnessCenter
                    )
                    
                    WorkoutStat(
                        label = "Exercises",
                        value = "${post.exercisesCount}",
                        icon = Icons.Outlined.List
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(LiftrixSpacing.medium))
            
            // Copy workout button
            IconButton(
                onClick = onCopyClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = LiftrixColorsV2.primary.copy(alpha = 0.1f),
                    contentColor = LiftrixColorsV2.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy workout"
                )
            }
        }
    }
}

@Composable
private fun WorkoutStat(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LiftrixColorsV2.primary,
            modifier = Modifier.size(16.dp)
        )
        
        Spacer(modifier = Modifier.height(2.dp))
        
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = LiftrixColorsV2.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = LiftrixColorsV2.onSurfaceVariant
        )
    }
}

@Composable
private fun MediaCarousel(
    mediaItems: List<MediaItem>,
    modifier: Modifier = Modifier
) {
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
                .clip(RoundedCornerShape(12.dp)),
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