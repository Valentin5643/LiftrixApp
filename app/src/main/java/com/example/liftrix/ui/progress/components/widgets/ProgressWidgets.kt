package com.example.liftrix.ui.progress.components.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.*
import com.example.liftrix.ui.theme.LiftrixColors

/**
 * Progress category widgets for goal tracking and achievement display.
 * 
 * All progress widgets follow consistent patterns:
 * - Current vs target value displays
 * - Progress visualization with percentages
 * - Milestone tracking and achievements
 * - Time-to-target estimations
 */

/**
 * Strength progress widget - tracks personal records and strength gains
 */
@Composable
fun StrengthProgressWidget(
    data: ProgressWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseWidget(
        title = "Strength Progress",
        subtitle = "Personal records",
        isLoading = data?.isLoading == true,
        error = data?.error?.message,
        onRefresh = onRefresh,
        onClick = onClick,
        modifier = modifier
    ) {
        data?.let { progressData ->
            ProgressDisplay(
                progressData = progressData,
                icon = Icons.Default.FitnessCenter,
                primaryColor = MaterialTheme.colorScheme.primary,
                showMilestones = true
            )
        }
    }
}

/**
 * Personal records widget - displays recent and all-time PRs
 */
@Composable
fun PersonalRecordsWidget(
    data: ProgressWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseWidget(
        title = "Personal Records",
        subtitle = "Recent achievements",
        isLoading = data?.isLoading == true,
        error = data?.error?.message,
        onRefresh = onRefresh,
        onClick = onClick,
        modifier = modifier
    ) {
        data?.let { progressData ->
            AchievementDisplay(
                progressData = progressData,
                primaryColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Volume load progression widget - tracks volume progression with intensity analysis
 */
@Composable
fun VolumeLoadProgressionWidget(
    data: ProgressWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseWidget(
        title = "Volume Progression",
        subtitle = "Load progression tracking",
        isLoading = data?.isLoading == true,
        error = data?.error?.message,
        onRefresh = onRefresh,
        onClick = onClick,
        modifier = modifier
    ) {
        data?.let { progressData ->
            ProgressDisplay(
                progressData = progressData,
                icon = Icons.Default.TrendingUp,
                primaryColor = MaterialTheme.colorScheme.secondary,
                showMilestones = false
            )
        }
    }
}

/**
 * One rep max progression widget - tracks estimated and actual 1RM
 */
@Composable
fun OneRMProgressionWidget(
    data: ProgressWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseWidget(
        title = "1RM Progression",
        subtitle = "One rep max tracking",
        isLoading = data?.isLoading == true,
        error = data?.error?.message,
        onRefresh = onRefresh,
        onClick = onClick,
        modifier = modifier
    ) {
        data?.let { progressData ->
            ProgressDisplay(
                progressData = progressData,
                icon = Icons.Default.Psychology,
                primaryColor = MaterialTheme.colorScheme.primary,
                showMilestones = true
            )
        }
    }
}

/**
 * Goal achievement widget - tracks progress toward user-defined goals
 */
@Composable
fun GoalAchievementWidget(
    data: ProgressWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseWidget(
        title = "Goal Achievement",
        subtitle = data?.timeToTarget,
        isLoading = data?.isLoading == true,
        error = data?.error?.message,
        onRefresh = onRefresh,
        onClick = onClick,
        modifier = modifier
    ) {
        data?.let { progressData ->
            GoalProgressDisplay(
                progressData = progressData,
                primaryColor = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

/**
 * Monthly summary widget - comprehensive monthly performance overview
 */
@Composable
fun MonthlySummaryWidget(
    data: ProgressWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseWidget(
        title = "Monthly Summary",
        subtitle = "Performance overview",
        isLoading = data?.isLoading == true,
        error = data?.error?.message,
        onRefresh = onRefresh,
        onClick = onClick,
        modifier = modifier
    ) {
        data?.let { progressData ->
            MonthlySummaryDisplay(
                progressData = progressData,
                primaryColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Compact progress widget for smaller layouts
 */
@Composable
fun CompactProgressWidget(
    widget: AnalyticsWidget,
    data: ProgressWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompactBaseWidget(
        title = widget.displayName,
        isLoading = data?.isLoading == true,
        error = data?.error?.message,
        onRefresh = onRefresh,
        onClick = onClick,
        modifier = modifier
    ) {
        data?.let { progressData ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${progressData.currentValue.toInt()} ${progressData.unit}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = "of ${progressData.targetValue.toInt()} ${progressData.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LiftrixColors.OnSurface.copy(alpha = 0.6f)
                    )
                }
                
                CircularProgressIndicator(
                    progress = progressData.progressPercentage / 100f,
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
            }
        }
    }
}

/**
 * Standard progress display component
 */
@Composable
private fun ProgressDisplay(
    progressData: ProgressWidgetData,
    icon: ImageVector,
    primaryColor: Color,
    showMilestones: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Progress header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = primaryColor.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "${progressData.currentValue.toInt()}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                    
                    Text(
                        text = progressData.unit,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LiftrixColors.OnSurface.copy(alpha = 0.7f)
                    )
                }
                
                Text(
                    text = "Target: ${progressData.targetValue.toInt()} ${progressData.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = LiftrixColors.OnSurface.copy(alpha = 0.6f)
                )
            }
            
            // Circular progress
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = (progressData.progressPercentage / 100f).coerceIn(0f, 1f),
                    modifier = Modifier.size(56.dp),
                    color = primaryColor,
                    strokeWidth = 6.dp,
                    trackColor = LiftrixColors.OnSurface.copy(alpha = 0.1f)
                )
                
                Text(
                    text = "${progressData.progressPercentage.toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = LiftrixColors.OnSurface
                )
            }
        }
        
        // Linear progress bar
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LinearProgressIndicator(
                progress = (progressData.progressPercentage / 100f).coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = primaryColor,
                trackColor = LiftrixColors.OnSurface.copy(alpha = 0.1f)
            )
            
            progressData.timeToTarget?.let { timeToTarget ->
                Text(
                    text = "Estimated time to target: $timeToTarget",
                    style = MaterialTheme.typography.bodySmall,
                    color = LiftrixColors.OnSurface.copy(alpha = 0.6f)
                )
            }
        }
        
        // Milestones
        if (showMilestones && progressData.milestones.isNotEmpty()) {
            MilestonesDisplay(
                milestones = progressData.milestones,
                primaryColor = primaryColor
            )
        }
    }
}

/**
 * Achievement display for personal records
 */
@Composable
private fun AchievementDisplay(
    progressData: ProgressWidgetData,
    primaryColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Progress summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${progressData.currentValue.toInt()} ${progressData.unit}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )
                
                Text(
                    text = "Current PR",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LiftrixColors.OnSurface.copy(alpha = 0.7f)
                )
            }
            
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = primaryColor.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        
        // Recent achievements
        if (progressData.recentAchievements.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Recent Achievements",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = LiftrixColors.OnSurface
                )
                
                progressData.recentAchievements.take(3).forEach { achievement ->
                    AchievementItem(
                        achievement = achievement,
                        primaryColor = primaryColor
                    )
                }
            }
        }
    }
}

/**
 * Goal progress display
 */
@Composable
private fun GoalProgressDisplay(
    progressData: ProgressWidgetData,
    primaryColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Goal progress circle
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = (progressData.progressPercentage / 100f).coerceIn(0f, 1f),
                    modifier = Modifier.size(120.dp),
                    color = primaryColor,
                    strokeWidth = 8.dp,
                    trackColor = LiftrixColors.OnSurface.copy(alpha = 0.1f)
                )
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${progressData.progressPercentage.toInt()}%",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                    
                    Text(
                        text = "Complete",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LiftrixColors.OnSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        // Goal details
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GoalDetailItem(
                label = "Current",
                value = "${progressData.currentValue.toInt()} ${progressData.unit}",
                color = LiftrixColors.OnSurface
            )
            
            GoalDetailItem(
                label = "Target",
                value = "${progressData.targetValue.toInt()} ${progressData.unit}",
                color = primaryColor
            )
            
            val remaining = (progressData.targetValue - progressData.currentValue).toInt()
            GoalDetailItem(
                label = "Remaining",
                value = "$remaining ${progressData.unit}",
                color = LiftrixColors.OnSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Monthly summary display
 */
@Composable
private fun MonthlySummaryDisplay(
    progressData: ProgressWidgetData,
    primaryColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SummaryStatItem(
                label = "This Month",
                value = "${progressData.currentValue.toInt()}",
                unit = progressData.unit,
                color = primaryColor
            )
            
            SummaryStatItem(
                label = "Progress",
                value = "${progressData.progressPercentage.toInt()}%",
                unit = "complete",
                color = LiftrixColors.OnSurface
            )
        }
        
        // Progress bar
        LinearProgressIndicator(
            progress = (progressData.progressPercentage / 100f).coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = primaryColor,
            trackColor = LiftrixColors.OnSurface.copy(alpha = 0.1f)
        )
    }
}

/**
 * Milestones display component
 */
@Composable
private fun MilestonesDisplay(
    milestones: List<Milestone>,
    primaryColor: Color
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Milestones",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = LiftrixColors.OnSurface
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(milestones.take(5)) { milestone ->
                MilestoneItem(
                    milestone = milestone,
                    primaryColor = primaryColor
                )
            }
        }
    }
}

/**
 * Individual milestone item
 */
@Composable
private fun MilestoneItem(
    milestone: Milestone,
    primaryColor: Color
) {
    Surface(
        modifier = Modifier.width(80.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (milestone.isAchieved) 
            primaryColor.copy(alpha = 0.1f) 
        else 
            LiftrixColors.OnSurface.copy(alpha = 0.05f)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (milestone.isAchieved) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (milestone.isAchieved) primaryColor else LiftrixColors.OnSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
            
            Text(
                text = "${milestone.value.toInt()}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (milestone.isAchieved) primaryColor else LiftrixColors.OnSurface
            )
            
            Text(
                text = milestone.label,
                style = MaterialTheme.typography.bodySmall,
                color = LiftrixColors.OnSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Achievement item component
 */
@Composable
private fun AchievementItem(
    achievement: Achievement,
    primaryColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = RoundedCornerShape(8.dp),
            color = primaryColor.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = achievement.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = LiftrixColors.OnSurface
            )
            
            Text(
                text = achievement.value,
                style = MaterialTheme.typography.bodySmall,
                color = LiftrixColors.OnSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Goal detail item
 */
@Composable
private fun GoalDetailItem(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = color
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = LiftrixColors.OnSurface.copy(alpha = 0.6f)
        )
    }
}

/**
 * Summary stat item
 */
@Composable
private fun SummaryStatItem(
    label: String,
    value: String,
    unit: String,
    color: Color
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            
            Text(
                text = unit,
                style = MaterialTheme.typography.bodyMedium,
                color = LiftrixColors.OnSurface.copy(alpha = 0.7f)
            )
        }
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = LiftrixColors.OnSurface.copy(alpha = 0.6f)
        )
    }
}