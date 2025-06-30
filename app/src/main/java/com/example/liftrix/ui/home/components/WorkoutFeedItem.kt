package com.example.liftrix.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.FeedWorkout
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.ui.theme.LiftrixColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Individual workout card component for displaying workout details in the social feed
 * Supports both personal workouts and friends' workouts with conditional rendering
 */
@Composable
fun WorkoutFeedItem(
    feedWorkout: FeedWorkout,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("workout_feed_item"),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Friend workout header (only for friends' workouts)
            if (!feedWorkout.isPersonal && feedWorkout.user != null) {
                FriendWorkoutHeader(
                    user = feedWorkout.user,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            
            // Main workout content
            WorkoutContent(
                workout = feedWorkout.workout,
                isPersonal = feedWorkout.isPersonal
            )
        }
    }
}

/**
 * Header component for friends' workouts showing user profile information
 */
@Composable
private fun FriendWorkoutHeader(
    user: User,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile image
        ProfileImage(
            imageUrl = user.photoUrl,
            displayName = user.displayName ?: "Unknown User",
            size = 32.dp
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // User name
        Text(
            text = user.displayName ?: "Unknown User",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Core workout content component displaying workout details
 */
@Composable
private fun WorkoutContent(
    workout: Workout,
    isPersonal: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Workout title
        Text(
            text = workout.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Workout metadata (date, duration, exercises)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date
            Text(
                text = formatWorkoutDate(workout.date),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Duration (if available)
            workout.getDuration()?.let { duration ->
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Exercise count
            Text(
                text = "•",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "${workout.exercises.size} ${if (workout.exercises.size == 1) "exercise" else "exercises"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Workout notes (if available)
        workout.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Workout summary for personal workouts
        if (isPersonal) {
            Spacer(modifier = Modifier.height(8.dp))
            
            PersonalWorkoutSummary(workout = workout)
        }
    }
}

/**
 * Summary section for personal workouts with additional details
 */
@Composable
private fun PersonalWorkoutSummary(
    workout: Workout,
    modifier: Modifier = Modifier
) {
    val completedSets = workout.getCompletedSets()
    val totalVolume = workout.calculateTotalVolume()
    
    if (completedSets > 0 || totalVolume.kilograms > 0.0) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (completedSets > 0) {
                Text(
                    text = "$completedSets ${if (completedSets == 1) "set" else "sets"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = LiftrixColors.Primary,
                    fontWeight = FontWeight.Medium
                )
            }
            
            if (totalVolume.kilograms > 0.0) {
                if (completedSets > 0) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    text = "${totalVolume.kilograms.toInt()} kg",
                    style = MaterialTheme.typography.bodySmall,
                    color = LiftrixColors.Primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Profile image component with URL loading capability and initials fallback
 * Reused from DiscoveryCarousel pattern
 */
@Composable
private fun ProfileImage(
    imageUrl: String?,
    displayName: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    // For now, using initials-based approach similar to FriendAvatar
    // TODO: Add Coil image loading library for actual URL support in future iteration
    val initials = displayName
        .trim()
        .split(' ')
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }
    
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(LiftrixColors.Primary),
        contentAlignment = Alignment.Center
    ) {
        if (initials == "?") {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "User avatar",
                tint = LiftrixColors.OnPrimary,
                modifier = Modifier.size(size * 0.5f)
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelMedium,
                color = LiftrixColors.OnPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Formats workout date for display in feed
 */
private fun formatWorkoutDate(date: LocalDate): String {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    
    return when (date) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> {
            // Show day of week for current week, otherwise show full date
            val daysAgo = date.until(today).days
            if (daysAgo <= 7) {
                date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            } else {
                date.format(DateTimeFormatter.ofPattern("MMM d"))
            }
        }
    }
}

/**
 * Formats workout duration for display
 */
private fun formatDuration(duration: java.time.Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "< 1m"
    }
}