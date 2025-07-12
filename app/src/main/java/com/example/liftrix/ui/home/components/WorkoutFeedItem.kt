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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.FitnessCenter
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
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.components.cards.CompactLiftrixCard
import com.example.liftrix.ui.components.layouts.GridSystem
import com.example.liftrix.ui.theme.LiftrixColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Modern workout feed item using LiftrixCard system with athletic styling
 * Supports both personal workouts and friends' workouts with enhanced visual hierarchy
 */
@Composable
fun WorkoutFeedItem(
    feedWorkout: FeedWorkout,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LiftrixCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("workout_feed_item"),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentDescription = "Workout: ${feedWorkout.workout.name}${if (!feedWorkout.isPersonal) " by ${feedWorkout.user?.displayName}" else ""}",
        contentPadding = androidx.compose.foundation.layout.PaddingValues(GridSystem.spacing3)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
        ) {
            // Friend workout header (only for friends' workouts)
            if (!feedWorkout.isPersonal && feedWorkout.user != null) {
                FriendWorkoutHeader(
                    user = feedWorkout.user,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Main workout content with enhanced styling
            WorkoutContent(
                workout = feedWorkout.workout,
                isPersonal = feedWorkout.isPersonal
            )
        }
    }
}

/**
 * Enhanced header component for friends' workouts with modern styling
 */
@Composable
private fun FriendWorkoutHeader(
    user: User,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
    ) {
        // Profile image with enhanced styling
        ProfileImage(
            imageUrl = user.photoUrl,
            displayName = user.displayName ?: "Unknown User",
            size = 40.dp
        )
        
        // User name with enhanced typography
        Text(
            text = user.displayName ?: "Unknown User",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Enhanced workout content with athletic styling and improved visual hierarchy
 */
@Composable
private fun WorkoutContent(
    workout: Workout,
    isPersonal: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
    ) {
        // Workout title with enhanced typography
        Text(
            text = workout.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        
        // Workout metadata with enhanced visual design
        WorkoutMetadata(
            workout = workout,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Workout notes with improved styling
        workout.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = GridSystem.spacing2,
                        top = GridSystem.spacing1,
                        end = GridSystem.spacing2,
                        bottom = GridSystem.spacing1
                    )
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(GridSystem.spacing2)
                    )
                    .padding(GridSystem.spacing2)
            )
        }
        
        // Enhanced workout summary for personal workouts
        if (isPersonal) {
            PersonalWorkoutSummary(workout = workout)
        }
    }
}

/**
 * Enhanced workout metadata with modern icon-based design
 */
@Composable
private fun WorkoutMetadata(
    workout: Workout,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Date with icon
        MetadataItem(
            icon = Icons.Default.Schedule,
            text = formatWorkoutDate(workout.date),
            modifier = Modifier.weight(1f, fill = false)
        )
        
        // Duration (if available)
        workout.getDuration()?.let { duration ->
            MetadataItem(
                icon = Icons.Default.Schedule,
                text = formatDuration(duration),
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        
        // Exercise count with icon
        MetadataItem(
            icon = Icons.Default.FitnessCenter,
            text = "${workout.exercises.size} ${if (workout.exercises.size == 1) "exercise" else "exercises"}",
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

/**
 * Individual metadata item with icon and text
 */
@Composable
private fun MetadataItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing1)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Enhanced personal workout summary with modern card design
 */
@Composable
private fun PersonalWorkoutSummary(
    workout: Workout,
    modifier: Modifier = Modifier
) {
    val completionPercentage = workout.getCompletionPercentage()
    val totalSets = workout.exercises.sumOf { it.sets.size }
    val completedSets = workout.exercises.sumOf { exercise ->
        exercise.sets.count { it.isCompleted }
    }
    
    CompactLiftrixCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(GridSystem.spacing2)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Completion status
            Column {
                Text(
                    text = "Progress",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                Text(
                    text = "${String.format("%.0f", completionPercentage)}% Complete",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            // Sets completed
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "Sets",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                Text(
                    text = "$completedSets/$totalSets",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * Enhanced profile image component with modern styling
 */
@Composable
private fun ProfileImage(
    imageUrl: String?,
    displayName: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    // Enhanced initials-based approach with better styling
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
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        if (initials == "?") {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "User avatar",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(size * 0.6f)
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
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