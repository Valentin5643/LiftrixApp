package com.example.liftrix.ui.progress.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.*
import com.example.liftrix.ui.theme.LiftrixTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Goal Progress Card Component
 * 
 * Displays goal progress with Material 3 design following the established
 * AnalyticsWidget patterns for consistent dashboard integration.
 * 
 * Features:
 * - Animated progress indicators with smooth transitions
 * - Achievement status visual feedback
 * - Milestone progress tracking
 * - Accessibility support with TalkBack descriptions
 * - Responsive design adapting to different screen sizes
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalProgressCard(
    goal: Goal,
    milestones: List<Milestone> = emptyList(),
    onGoalClick: (Goal) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val completedMilestones = milestones.count { it.isCompleted }
    val nextMilestone = milestones.filter { !it.isCompleted }.minByOrNull { it.targetDate }
    
    // Animate progress indicator
    val animatedProgress by animateFloatAsState(
        targetValue = goal.progressPercentage,
        animationSpec = tween(durationMillis = 1000),
        label = "progress_animation"
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onGoalClick(goal) }
            .semantics {
                contentDescription = "Goal: ${goal.type.displayName}, " +
                    "${(goal.progressPercentage * 100).toInt()}% complete, " +
                    "${goal.daysRemaining} days remaining"
                role = Role.Button
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header row with goal type and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = getGoalTypeIcon(goal.type),
                        contentDescription = goal.type.displayName,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = goal.type.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                GoalStatusChip(
                    status = goal.achievementStatus,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Progress section
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = goal.getProgressText(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Progress bar
                LinearProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = getProgressColor(goal.achievementStatus),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Combined milestone and deadline info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Combined milestone and deadline in single compact row
                if (milestones.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Milestone icon + count
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Milestones",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        Text(
                            text = "$completedMilestones/${milestones.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // Separator dot
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // Deadline icon + text
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Deadline",
                            tint = getDeadlineColor(goal.daysRemaining),
                            modifier = Modifier.size(14.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        Text(
                            text = getDeadlineText(goal.daysRemaining),
                            style = MaterialTheme.typography.bodySmall,
                            color = getDeadlineColor(goal.daysRemaining)
                        )
                    }
                } else {
                    // Just deadline when no milestones
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Deadline",
                            tint = getDeadlineColor(goal.daysRemaining),
                            modifier = Modifier.size(14.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        Text(
                            text = getDeadlineText(goal.daysRemaining),
                            style = MaterialTheme.typography.bodySmall,
                            color = getDeadlineColor(goal.daysRemaining)
                        )
                    }
                }
            }
            
            // Next milestone preview (if exists) - compact
            nextMilestone?.let { milestone ->
                Spacer(modifier = Modifier.height(6.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = "Next milestone",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        Text(
                            text = "Next: ${milestone.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Status chip showing goal achievement status
 */
@Composable
private fun GoalStatusChip(
    status: AchievementStatus,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, contentColor) = when (status) {
        AchievementStatus.COMPLETED -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        AchievementStatus.NEARLY_COMPLETE -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
        AchievementStatus.ON_TRACK -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
        AchievementStatus.BEHIND -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        AchievementStatus.URGENT -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        AchievementStatus.OVERDUE -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        AchievementStatus.STARTED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Surface(
        modifier = modifier,
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = status.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Gets appropriate icon for goal type
 */
private fun getGoalTypeIcon(type: GoalType): ImageVector {
    return when (type) {
        is GoalType.WeeklyVolume -> Icons.Default.TrendingUp
        is GoalType.MonthlyFrequency -> Icons.Default.Schedule
        is GoalType.StrengthPR -> Icons.Default.TrendingUp
        is GoalType.ConsistencyStreak -> Icons.Default.CheckCircle
    }
}

/**
 * Gets progress bar color based on achievement status
 */
@Composable
private fun getProgressColor(status: AchievementStatus): Color {
    return when (status) {
        AchievementStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        AchievementStatus.NEARLY_COMPLETE -> MaterialTheme.colorScheme.secondary
        AchievementStatus.ON_TRACK -> MaterialTheme.colorScheme.tertiary
        AchievementStatus.BEHIND -> MaterialTheme.colorScheme.error
        AchievementStatus.URGENT -> MaterialTheme.colorScheme.error
        AchievementStatus.OVERDUE -> MaterialTheme.colorScheme.error
        AchievementStatus.STARTED -> MaterialTheme.colorScheme.primary
    }
}

/**
 * Gets deadline text based on days remaining
 */
private fun getDeadlineText(daysRemaining: Int): String {
    return when {
        daysRemaining < 0 -> "${-daysRemaining} days overdue"
        daysRemaining == 0 -> "Due today"
        daysRemaining == 1 -> "Due tomorrow"
        daysRemaining <= 7 -> "$daysRemaining days left"
        daysRemaining <= 30 -> "${daysRemaining / 7} weeks left"
        else -> "${daysRemaining / 30} months left"
    }
}

/**
 * Gets deadline color based on urgency
 */
@Composable
private fun getDeadlineColor(daysRemaining: Int): Color {
    return when {
        daysRemaining < 0 -> MaterialTheme.colorScheme.error
        daysRemaining <= 3 -> MaterialTheme.colorScheme.error
        daysRemaining <= 7 -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

// Preview components
@Preview(showBackground = true)
@Composable
private fun GoalProgressCardPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // On track goal
            GoalProgressCard(
                goal = Goal(
                    id = GoalId.generate(),
                    userId = "user123",
                    type = GoalType.WeeklyVolume,
                    target = 50000,
                    currentProgress = 35000,
                    unit = "lbs",
                    deadline = LocalDate.now().plusDays(14),
                    priority = GoalPriority.NORMAL
                ),
                milestones = listOf(
                    Milestone.create(
                        goalId = GoalId.generate(),
                        title = "Week 1",
                        targetValue = 12500,
                        targetDate = LocalDate.now().minusDays(7),
                        order = 1
                    ).markCompleted(),
                    Milestone.create(
                        goalId = GoalId.generate(),
                        title = "Week 2",
                        targetValue = 25000,
                        targetDate = LocalDate.now(),
                        order = 2
                    ).markCompleted(),
                    Milestone.create(
                        goalId = GoalId.generate(),
                        title = "Week 3",
                        targetValue = 37500,
                        targetDate = LocalDate.now().plusDays(7),
                        order = 3
                    )
                )
            )
            
            // Nearly complete goal
            GoalProgressCard(
                goal = Goal(
                    id = GoalId.generate(),
                    userId = "user123",
                    type = GoalType.ConsistencyStreak,
                    target = 21,
                    currentProgress = 19,
                    unit = "days",
                    deadline = LocalDate.now().plusDays(3),
                    priority = GoalPriority.HIGH
                )
            )
            
            // Overdue goal
            GoalProgressCard(
                goal = Goal(
                    id = GoalId.generate(),
                    userId = "user123",
                    type = GoalType.MonthlyFrequency,
                    target = 16,
                    currentProgress = 12,
                    unit = "sessions",
                    deadline = LocalDate.now().minusDays(2),
                    priority = GoalPriority.NORMAL
                )
            )
        }
    }
}
