package com.example.liftrix.ui.profile.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.theme.ProfileColors

/**
 * AchievementProgressCard - Displays user achievements with progress indicators
 * 
 * Features:
 * - Achievement list with progress bars showing completion (1/5, 4/5 style)
 * - Emoji icons for visual appeal and categorization
 * - Animated progress bars with smooth transitions
 * - "See All" button for expanded achievement view
 * - Completion status indication with color coding
 * - Accessibility support with semantic descriptions
 * 
 * Achievement Display Format:
 * - Icon (emoji) + Title + Progress Bar + Completion Ratio
 * - Green progress bars for completed/in-progress achievements
 * - Different opacity for completed vs in-progress items
 * 
 * Design System Compliance:
 * - Uses ProfileColors for consistent theming
 * - Follows 16dp card radius and semantic spacing
 * - Material 3 typography and interactions
 * - WCAG 2.1 AA accessibility compliance
 * 
 * @param achievements List of achievement progress items to display
 * @param onSeeAllClick Callback when "See All" button is clicked
 * @param modifier Optional modifier for customization
 */
@Composable
fun AchievementProgressCard(
    achievements: List<AchievementProgressItem>,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
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
            // Card header with "See All" button
            AchievementCardHeader(
                onSeeAllClick = onSeeAllClick,
                totalAchievements = achievements.size,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (achievements.isEmpty()) {
                // Empty state
                AchievementEmptyState(
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Display top 3 achievements
                achievements.take(3).forEachIndexed { index, achievement ->
                    AchievementProgressRow(
                        achievement = achievement,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Add spacing between items (except for the last one)
                    if (index < achievements.take(3).size - 1) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

/**
 * Achievement card header with title and "See All" button
 */
@Composable
private fun AchievementCardHeader(
    onSeeAllClick: () -> Unit,
    totalAchievements: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Achievements",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            if (totalAchievements > 0) {
                Text(
                    text = "$totalAchievements unlocked",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        TextButton(
            onClick = onSeeAllClick,
            modifier = Modifier.semantics {
                contentDescription = "View all achievements"
            }
        ) {
            Text(
                text = "See All",
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Individual achievement progress row
 */
@Composable
private fun AchievementProgressRow(
    achievement: AchievementProgressItem,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = achievement.progressPercentage / 100f,
        animationSpec = tween(
            durationMillis = 800,
            easing = FastOutSlowInEasing
        ),
        label = "achievement_progress_${achievement.id}"
    )
    
    Row(
        modifier = modifier.semantics {
            contentDescription = "${achievement.title}: ${achievement.currentProgress} of ${achievement.maxProgress} completed"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Achievement icon (emoji)
        AchievementIcon(
            icon = achievement.icon,
            isCompleted = achievement.isCompleted,
            modifier = Modifier.size(32.dp)
        )
        
        // Title and progress section
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Achievement title with completion indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (achievement.isCompleted) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                
                // Completion ratio (e.g., "4/5")
                Text(
                    text = "${achievement.currentProgress}/${achievement.maxProgress}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (achievement.isCompleted) 
                        ProfileColors.ProgressGreen 
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Progress bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .semantics {
                            contentDescription = "${achievement.progressPercentage}% complete"
                        },
                    color = if (achievement.isCompleted) 
                        ProfileColors.ProgressGreen 
                        else ProfileColors.ProgressGreen.copy(alpha = 0.8f),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )
            }
            
            // Next milestone hint (if available)
            achievement.nextMilestone?.let { milestone ->
                Text(
                    text = "Next: $milestone",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics {
                        contentDescription = "Next milestone: $milestone"
                    }
                )
            }
        }
    }
}

/**
 * Achievement icon with completion indicator
 */
@Composable
private fun AchievementIcon(
    icon: String,
    isCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = icon,
            fontSize = 24.sp,
            modifier = Modifier.semantics {
                contentDescription = if (isCompleted) "Completed achievement" else "Achievement in progress"
            }
        )
    }
}

/**
 * Empty state when no achievements are available
 */
@Composable
private fun AchievementEmptyState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🏆",
            fontSize = 32.sp
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "No achievements yet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "Complete workouts to unlock achievements",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Data class for achievement progress information
 */
data class AchievementProgressItem(
    val id: String,
    val title: String,
    val icon: String, // Emoji or icon identifier
    val currentProgress: Int,
    val maxProgress: Int,
    val progressPercentage: Float,
    val category: AchievementCategory,
    val nextMilestone: String? = null,
    val isCompleted: Boolean
) {
    companion object {
        /**
         * Creates a sample achievement for testing/preview
         */
        fun createSample(
            id: String,
            title: String,
            icon: String,
            current: Int,
            max: Int,
            nextMilestone: String? = null
        ): AchievementProgressItem {
            val percentage = ((current.toFloat() / max) * 100).coerceIn(0f, 100f)
            return AchievementProgressItem(
                id = id,
                title = title,
                icon = icon,
                currentProgress = current,
                maxProgress = max,
                progressPercentage = percentage,
                category = AchievementCategory.WORKOUT_COUNT,
                nextMilestone = nextMilestone,
                isCompleted = current >= max
            )
        }
    }
}

/**
 * Categories for achievements
 */
enum class AchievementCategory(val displayName: String, val defaultIcon: String) {
    FIRST_TIME_EVENTS("First Time", "🎉"),
    WORKOUT_COUNT("Workout Count", "💪"),
    VOLUME_MILESTONES("Volume", "🏋️"),
    STREAK_ACHIEVEMENTS("Streaks", "🔥"),
    PERSONAL_RECORDS("Personal Records", "🏆"),
    SOCIAL_ACHIEVEMENTS("Social", "👥"),
    SPECIAL_EVENTS("Special", "⭐")
}

/**
 * Extension functions for achievement data
 */
fun List<AchievementProgressItem>.getCompletedCount(): Int = count { it.isCompleted }

fun List<AchievementProgressItem>.getInProgressCount(): Int = count { !it.isCompleted && it.currentProgress > 0 }

fun List<AchievementProgressItem>.getAverageProgress(): Float {
    if (isEmpty()) return 0f
    return map { it.progressPercentage }.average().toFloat()
}

fun AchievementProgressItem.getRemainingProgress(): Int = maxProgress - currentProgress