package com.example.liftrix.ui.social.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.liftrix.ui.common.AccessibleIcon
import com.example.liftrix.ui.theme.LiftrixTokens
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.SharedWorkout
import com.example.liftrix.ui.theme.LiftrixTheme
import java.time.Duration
import java.time.Instant

/**
 * Individual friend workout display card with quick action buttons and Material 3 design
 * Designed for horizontal scrolling in social feed with fixed width
 */
@Composable
fun FriendWorkoutCard(
    sharedWorkout: SharedWorkout,
    onViewWorkout: (SharedWorkout) -> Unit,
    onCongratulate: (SharedWorkout) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = { onViewWorkout(sharedWorkout) },
        modifier = modifier
            .width(200.dp)
            .semantics {
                contentDescription = "Friend workout: ${sharedWorkout.friendDisplayName} completed ${sharedWorkout.workoutName}, ${sharedWorkout.getFormattedTimeShared()}"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FriendHeader(
                friendDisplayName = sharedWorkout.friendDisplayName,
                timeShared = sharedWorkout.getFormattedTimeShared()
            )
            
            WorkoutSummary(
                workoutName = sharedWorkout.workoutName,
                duration = sharedWorkout.duration,
                exerciseCount = sharedWorkout.exerciseCount
            )
            
            QuickActions(
                onCongratulate = { onCongratulate(sharedWorkout) }
            )
        }
    }
}

/**
 * Header section displaying friend avatar, name, and time since shared
 */
@Composable
private fun FriendHeader(
    friendDisplayName: String,
    timeShared: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FriendAvatar(
            displayName = friendDisplayName,
            modifier = Modifier.size(32.dp)
        )
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = friendDisplayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Text(
                text = timeShared,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Simple circular avatar with friend initials or fallback icon
 */
@Composable
private fun FriendAvatar(
    displayName: String,
    modifier: Modifier = Modifier
) {
    val initials = displayName
        .trim()
        .split(' ')
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }
    
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        if (initials == "?") {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Friend avatar",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Workout summary section displaying name, duration, and exercise count
 */
@Composable
private fun WorkoutSummary(
    workoutName: String,
    duration: Duration,
    exerciseCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = workoutName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WorkoutStatItem(
                icon = Icons.Default.Schedule,
                value = formatDuration(duration),
                label = "Duration",
                modifier = Modifier.weight(1f)
            )
            
            WorkoutStatItem(
                icon = Icons.Default.FitnessCenter,
                value = exerciseCount.toString(),
                label = "Exercises",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Individual stat item with icon and value for compact display
 */
@Composable
private fun WorkoutStatItem(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccessibleIcon(
            imageVector = icon,
            contentDescription = "$label: $value",
            iconSize = LiftrixTokens.TouchTarget.IconSmall,
            tint = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Quick action buttons for social interactions
 */
@Composable
private fun QuickActions(
    onCongratulate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        IconButton(
            onClick = onCongratulate,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FavoriteBorder,
                contentDescription = "Congratulate friend",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Formats duration to human-readable string optimized for compact display
 */
private fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutesPart()
    
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "< 1m"
    }
}

@Preview(showBackground = true)
@Composable
private fun FriendWorkoutCardPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Recent workout
            FriendWorkoutCard(
                sharedWorkout = SharedWorkout(
                    id = "shared1",
                    friendUserId = "friend123",
                    friendDisplayName = "Alex Johnson",
                    workoutName = "Push Day",
                    completedAt = Instant.now().minusSeconds(3600),
                    duration = Duration.ofMinutes(65),
                    exerciseCount = 5,
                    sharedAt = Instant.now().minusSeconds(300)
                ),
                onViewWorkout = { },
                onCongratulate = { }
            )
            
            // Longer workout name
            FriendWorkoutCard(
                sharedWorkout = SharedWorkout(
                    id = "shared2",
                    friendUserId = "friend456",
                    friendDisplayName = "Sarah Martinez",
                    workoutName = "Full Body Strength Training Session",
                    completedAt = Instant.now().minusSeconds(7200),
                    duration = Duration.ofMinutes(45),
                    exerciseCount = 8,
                    sharedAt = Instant.now().minusSeconds(1800)
                ),
                onViewWorkout = { },
                onCongratulate = { }
            )
            
            // Short name and workout
            FriendWorkoutCard(
                sharedWorkout = SharedWorkout(
                    id = "shared3",
                    friendUserId = "friend789",
                    friendDisplayName = "Jo",
                    workoutName = "Cardio",
                    completedAt = Instant.now().minusSeconds(1800),
                    duration = Duration.ofMinutes(20),
                    exerciseCount = 3,
                    sharedAt = Instant.now().minusSeconds(60)
                ),
                onViewWorkout = { },
                onCongratulate = { }
            )
        }
    }
}
