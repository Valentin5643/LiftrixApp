package com.example.liftrix.ui.progress.components.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.Achievement
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.Milestone
import com.example.liftrix.domain.model.analytics.ProgressWidgetData
import com.example.liftrix.ui.progress.components.widgets.FolderStyleWidget
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
 * Strength analytics widget - unified strength analysis combining progress, records, and 1RM
 * Replaces: StrengthProgress, PersonalRecords, OneRMProgression widgets
 */
@Composable
fun StrengthAnalyticsWidget(
    data: ProgressWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useFolderStyle: Boolean = true
) {
    if (useFolderStyle) {
        FolderStyleWidget(
            title = "Strength Analytics",
            icon = Icons.Default.FitnessCenter,
            onClick = onClick,
            modifier = modifier,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            iconTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
    } else {
        BaseWidget(
            title = "Strength Analytics",
            subtitle = "Progress, PRs & 1RM tracking",
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            onRefresh = onRefresh,
            onClick = onClick,
            modifier = modifier
        ) {
            data?.let { progressData ->
                StrengthAnalyticsDisplay(
                    progressData = progressData,
                    primaryColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Personal records widget - displays recent and all-time PRs with folder-style design
 */
@Composable
fun PersonalRecordsWidget(
    data: ProgressWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useFolderStyle: Boolean = true
) {
    if (useFolderStyle) {
        FolderStyleWidget(
            title = "Personal Records",
            icon = Icons.Default.EmojiEvents,
            onClick = onClick,
            modifier = modifier,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            iconTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
    } else {
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
}

/**
 * Volume load progression widget - tracks volume progression with folder-style design
 */
@Composable
fun VolumeLoadProgressionWidget(
    data: ProgressWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useFolderStyle: Boolean = true
) {
    if (useFolderStyle) {
        FolderStyleWidget(
            title = "Volume Progression",
            icon = Icons.Default.Timeline,
            onClick = onClick,
            modifier = modifier,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            iconTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
    } else {
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
}

/**
 * One rep max progression widget - tracks estimated and actual 1RM with folder-style design
 */
@Composable
fun OneRMProgressionWidget(
    data: ProgressWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useFolderStyle: Boolean = true
) {
    if (useFolderStyle) {
        FolderStyleWidget(
            title = "1RM Progression",
            icon = Icons.Default.TrendingUp,
            onClick = onClick,
            modifier = modifier,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            iconTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
    } else {
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
 * Monthly summary widget - comprehensive monthly performance overview with folder-style design
 */
@Composable
fun MonthlySummaryWidget(
    data: ProgressWidgetData?,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useFolderStyle: Boolean = true,
    aspectRatio: Float = 1.1f
) {
    if (useFolderStyle) {
        FolderStyleWidget(
            title = "Monthly Summary",
            icon = Icons.Default.CalendarMonth,
            onClick = onClick,
            modifier = modifier,
            isLoading = data?.isLoading == true,
            error = data?.error?.message,
            iconTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            aspectRatio = aspectRatio
        )
    } else {
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
 * Strength analytics unified display combining progress, personal records, and 1RM tracking
 */
@Composable
private fun StrengthAnalyticsDisplay(
    progressData: ProgressWidgetData,
    primaryColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Strength analytics header with current PR and progress
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
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = "Strength analytics",
                        tint = primaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                // Current strength metric
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
                    text = "Current PR • Target: ${progressData.targetValue.toInt()} ${progressData.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = LiftrixColors.OnSurface.copy(alpha = 0.6f)
                )
            }
            
            // Progress circle indicator
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
        
        // Strength progression bar
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
                    text = "1RM target: $timeToTarget",
                    style = MaterialTheme.typography.bodySmall,
                    color = LiftrixColors.OnSurface.copy(alpha = 0.6f)
                )
            }
        }
        
        // Recent achievements and milestones combined
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Personal records section
            if (progressData.recentAchievements.isNotEmpty()) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Recent PRs",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = LiftrixColors.OnSurface
                    )
                    
                    progressData.recentAchievements.take(2).forEach { achievement ->
                        CompactAchievementItem(
                            achievement = achievement,
                            primaryColor = primaryColor
                        )
                    }
                }
            }
            
            // 1RM estimation section
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "1RM Progress",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = LiftrixColors.OnSurface
                )
                
                OneRMProgressIndicator(
                    currentValue = progressData.currentValue,
                    targetValue = progressData.targetValue,
                    unit = progressData.unit,
                    primaryColor = primaryColor
                )
            }
        }
        
        // Milestones if available
        if (progressData.milestones.isNotEmpty()) {
            MilestonesDisplay(
                milestones = progressData.milestones.take(4),
                primaryColor = primaryColor
            )
        }
    }
}

/**
 * Compact achievement item for strength analytics
 */
@Composable
private fun CompactAchievementItem(
    achievement: Achievement,
    primaryColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = RoundedCornerShape(6.dp),
            color = primaryColor.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Achievement",
                    tint = primaryColor,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = achievement.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = LiftrixColors.OnSurface,
                maxLines = 1
            )
            
            Text(
                text = achievement.value,
                style = MaterialTheme.typography.bodySmall,
                color = LiftrixColors.OnSurface.copy(alpha = 0.6f),
                maxLines = 1
            )
        }
    }
}

/**
 * 1RM progress indicator showing estimated maximum
 */
@Composable
private fun OneRMProgressIndicator(
    currentValue: Float,
    targetValue: Float,
    unit: String,
    primaryColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = primaryColor.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Estimated 1RM (using simple multiplier approximation)
            val estimated1RM = (currentValue * 1.03f).toInt()
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Est. 1RM",
                    style = MaterialTheme.typography.bodySmall,
                    color = LiftrixColors.OnSurface.copy(alpha = 0.7f)
                )
                
                Text(
                    text = "$estimated1RM $unit",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )
            }
            
            // Progress toward 1RM target
            val oneRMProgress = if (targetValue > 0f) (estimated1RM / targetValue).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = oneRMProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = primaryColor,
                trackColor = LiftrixColors.OnSurface.copy(alpha = 0.2f)
            )
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
                        contentDescription = progressData.widgetType.displayName,
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
                        contentDescription = "Personal record",
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
                contentDescription = if (milestone.isAchieved) "Milestone achieved" else "Milestone pending",
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
                    contentDescription = "Achievement",
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
