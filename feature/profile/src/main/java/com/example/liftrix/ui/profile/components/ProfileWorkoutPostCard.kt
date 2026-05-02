package com.example.liftrix.ui.profile.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    onWorkoutClick: () -> Unit = {},
    onBlockUser: () -> Unit = {},
    onReportPost: () -> Unit = {},
    onEditWorkout: () -> Unit = {},
    isOwnPost: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = LiftrixSpacing.small)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onProfileClick)
                .padding(horizontal = LiftrixSpacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileImageDisplay(
                imageUrl = post.authorProfilePhotoUrl,
                displayName = post.authorDisplayName.ifBlank { post.authorUsername },
                userId = post.userId,
                size = 40.dp,
                contentDescription = "Profile picture of ${post.authorDisplayName}"
            )
            Spacer(modifier = Modifier.width(LiftrixSpacing.small))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = post.authorDisplayName.ifBlank { post.authorUsername },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = LiftrixColorsV2.onSurface
                )
                Text(
                    text = formatTimestamp(post.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = LiftrixColorsV2.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(LiftrixSpacing.medium))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onWorkoutClick)
                .padding(horizontal = LiftrixSpacing.medium),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            Metric(value = formatDuration(post.workoutDuration), label = "Duration")
            Spacer(modifier = Modifier.width(32.dp))
            Metric(value = "${post.exercisesCount ?: 0}", label = "Exercises")
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onWorkoutCopyClick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy workout")
            }
        }

        if (post.caption.isNotBlank()) {
            Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
            Text(
                text = post.caption,
                style = MaterialTheme.typography.bodyMedium,
                color = LiftrixColorsV2.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LiftrixSpacing.medium)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LiftrixSpacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.large)) {
                EngagementButton(
                    active = isLiked,
                    count = post.likeCount,
                    activeIcon = Icons.Default.Favorite,
                    inactiveIcon = Icons.Default.FavoriteBorder,
                    contentDescription = if (isLiked) "Unlike" else "Like",
                    onClick = onLikeClick
                )
                EngagementButton(
                    active = false,
                    count = post.commentCount,
                    activeIcon = Icons.Default.ChatBubbleOutline,
                    inactiveIcon = Icons.Default.ChatBubbleOutline,
                    contentDescription = "Comment",
                    onClick = onCommentClick
                )
                EngagementButton(
                    active = false,
                    count = post.shareCount,
                    activeIcon = Icons.Default.Share,
                    inactiveIcon = Icons.Default.Share,
                    contentDescription = "Share",
                    onClick = onShareClick
                )
            }
            IconButton(onClick = onSaveClick) {
                Icon(
                    Icons.Default.Bookmark,
                    contentDescription = if (isSaved) "Unsave" else "Save",
                    tint = if (isSaved) LiftrixColorsV2.primary else LiftrixColorsV2.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 0.5.dp,
            color = LiftrixColorsV2.onSurfaceVariant.copy(alpha = 0.1f)
        )
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = LiftrixColorsV2.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = LiftrixColorsV2.onSurfaceVariant
        )
    }
}

@Composable
private fun EngagementButton(
    active: Boolean,
    count: Int,
    activeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    inactiveIcon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = if (active) activeIcon else inactiveIcon,
            contentDescription = contentDescription,
            tint = if (active) LiftrixColorsV2.primary else LiftrixColorsV2.onSurfaceVariant,
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
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "now"
        diff < 3_600_000 -> "${diff / 60_000}m"
        diff < 86_400_000 -> "${diff / 3_600_000}h"
        diff < 604_800_000 -> "${diff / 86_400_000}d"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun formatDuration(minutes: Int?): String {
    if (minutes == null || minutes == 0) return "0m"
    return if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
}

private fun formatCount(count: Int): String = when {
    count < 1_000 -> count.toString()
    count < 10_000 -> "${count / 1_000}.${(count % 1_000) / 100}k"
    count < 1_000_000 -> "${count / 1_000}k"
    else -> "${count / 1_000_000}.${(count % 1_000_000) / 100_000}M"
}
