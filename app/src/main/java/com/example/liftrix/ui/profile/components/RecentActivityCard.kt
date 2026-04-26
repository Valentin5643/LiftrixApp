package com.example.liftrix.ui.profile.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.ui.feed.components.WorkoutPostCard
import com.example.liftrix.ui.theme.LiftrixSpacing

/**
 * RecentActivityCard - Displays user's recent workout posts using existing feed components
 * 
 * Reuses WorkoutPostCard from feed to maintain consistency and avoid duplication.
 * Shows the profile owner's recent workouts that are visible to anyone viewing their profile
 * (based on privacy settings).
 * 
 * Note: This interface matches the usage in UserProfileScreen ModernUserProfileContent.
 */
@Composable
fun RecentActivityCard(
    activities: List<WorkoutPost>,
    onActivityClick: (WorkoutPost) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxVisible: Int = 3
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.cardPadding)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (activities.size > maxVisible) {
                    TextButton(onClick = onSeeAllClick) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "See All",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "See all activities",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Recent workout posts (simplified for profile view)
            if (activities.isNotEmpty()) {
                val visibleActivities = activities.take(maxVisible)
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    visibleActivities.forEach { post ->
                        // Simplified workout display for profile view
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onActivityClick(post) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = post.caption.takeIf { it.isNotBlank() } ?: "Workout completed",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                
                                Text(
                                    text = buildString {
                                        post.exercisesCount?.let { append("$it exercises") }
                                        post.workoutDuration?.let { duration ->
                                            if (isNotEmpty()) append(" • ")
                                            append("${duration}min")
                                        }
                                        if (post.prsCount > 0) {
                                            if (isNotEmpty()) append(" • ")
                                            append("${post.prsCount} PR${if (post.prsCount > 1) "s" else ""}")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No recent workouts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
