package com.example.liftrix.ui.common.sync

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.liftrix.sync.CombinedSyncStatus
import com.example.liftrix.sync.SyncStatus
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import kotlinx.coroutines.delay

/**
 * SyncProgressBar - Background sync progress indication component
 * 
 * Displays sync progress for background operations with:
 * - Determinate progress for known sync operations
 * - Indeterminate progress for real-time sync
 * - Multi-stage sync progress (workouts → analytics → achievements)
 * - Auto-completion celebrations with checkmark animation
 * - Error states with retry suggestions
 * 
 * Design System Integration:
 * - Uses LiftrixColorsV2.Teal for progress indicators
 * - Success states use LiftrixColorsV2.DataViz.Positive
 * - Error states use MaterialTheme.colorScheme.error
 * - Follows LiftrixSpacing semantic tokens
 * - Material 3 LinearProgressIndicator styling
 * - Smooth animations following 300ms timing
 * 
 * Progress Stages:
 * 1. Workouts sync (40% of total progress)
 * 2. Analytics calculations (35% of total progress)
 * 3. Achievement updates (15% of total progress)
 * 4. Profile sync completion (10% of total progress)
 */
@Composable
fun SyncProgressBar(
    combinedStatus: CombinedSyncStatus,
    currentOperation: String? = null,
    progress: Float? = null,
    modifier: Modifier = Modifier,
    showDetailedProgress: Boolean = true,
    autoHide: Boolean = true
) {
    var isVisible by remember(combinedStatus) {
        mutableStateOf(combinedStatus.isAnySync || combinedStatus.hasAnyError)
    }
    
    // Auto-hide after successful completion
    LaunchedEffect(combinedStatus) {
        if (autoHide && combinedStatus.isAllSuccess) {
            delay(2000) // Show success for 2 seconds
            isVisible = false
        } else if (combinedStatus.isAnySync || combinedStatus.hasAnyError) {
            isVisible = true
        }
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        SyncProgressContent(
            combinedStatus = combinedStatus,
            currentOperation = currentOperation,
            progress = progress,
            showDetailedProgress = showDetailedProgress
        )
    }
}

@Composable
private fun SyncProgressContent(
    combinedStatus: CombinedSyncStatus,
    currentOperation: String?,
    progress: Float?,
    showDetailedProgress: Boolean
) {
    val progressData = getProgressData(combinedStatus, currentOperation, progress)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LiftrixSpacing.screenPadding)
            .semantics {
                contentDescription = progressData.accessibilityDescription
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(LiftrixSpacing.medium)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementPaddingMedium)
        ) {
            // Header with icon and title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementPaddingMedium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SyncIcon(
                    icon = progressData.icon,
                    color = progressData.iconColor,
                    isAnimated = progressData.isAnimated
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = progressData.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (progressData.subtitle.isNotBlank()) {
                        Text(
                            text = progressData.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Progress percentage
                if (progressData.showPercentage && progressData.progressValue != null) {
                    Text(
                        text = "${(progressData.progressValue * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = progressData.iconColor,
                        textAlign = TextAlign.End
                    )
                }
            }
            
            // Progress Bar
            if (progressData.showProgressBar) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                ) {
                    if (progressData.progressValue != null) {
                        // Determinate progress
                        LinearProgressIndicator(
                            progress = { progressData.progressValue },
                            modifier = Modifier.fillMaxWidth(),
                            color = progressData.iconColor,
                            trackColor = progressData.iconColor.copy(alpha = 0.2f),
                            strokeCap = StrokeCap.Round
                        )
                    } else {
                        // Indeterminate progress
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = progressData.iconColor,
                            trackColor = progressData.iconColor.copy(alpha = 0.2f),
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }
            
            // Detailed stage progress
            if (showDetailedProgress && progressData.stages.isNotEmpty()) {
                DetailedStageProgress(
                    stages = progressData.stages,
                    currentStageIndex = progressData.currentStageIndex
                )
            }
        }
    }
}

@Composable
private fun SyncIcon(
    icon: ImageVector,
    color: Color,
    isAnimated: Boolean
) {
    if (isAnimated) {
        val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
        val rotationAngle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing)
            ),
            label = "rotation"
        )
        
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(20.dp)
                .rotate(rotationAngle)
        )
    } else {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun DetailedStageProgress(
    stages: List<SyncStage>,
    currentStageIndex: Int
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementPaddingSmall)
    ) {
        stages.forEachIndexed { index, stage ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementPaddingMedium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stage indicator
                val stageColor = when {
                    index < currentStageIndex -> LiftrixColorsV2.DataViz.Positive
                    index == currentStageIndex -> LiftrixColorsV2.Teal
                    else -> MaterialTheme.colorScheme.outline
                }
                
                val stageIcon = when {
                    index < currentStageIndex -> Icons.Default.CheckCircle
                    index == currentStageIndex -> Icons.Default.Sync
                    else -> Icons.Default.CheckCircle
                }
                
                Icon(
                    imageVector = stageIcon,
                    contentDescription = null,
                    tint = stageColor,
                    modifier = Modifier.size(16.dp)
                )
                
                // Stage name
                Text(
                    text = stage.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (index <= currentStageIndex) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f)
                )
                
                // Stage status
                if (index == currentStageIndex && stage.itemCount != null) {
                    Text(
                        text = "${stage.completedItems}/${stage.itemCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Compact sync progress for small spaces
 */
@Composable
fun CompactSyncProgress(
    combinedStatus: CombinedSyncStatus,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = combinedStatus.isAnySync,
        modifier = modifier
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementPaddingSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "compact_sync")
            val rotationAngle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearEasing)
                ),
                label = "compact_rotation"
            )
            
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = null,
                tint = LiftrixColorsV2.Teal,
                modifier = Modifier
                    .size(14.dp)
                    .rotate(rotationAngle)
            )
            
            Text(
                text = "Syncing",
                style = MaterialTheme.typography.labelSmall,
                color = LiftrixColorsV2.Teal
            )
        }
    }
}

/**
 * Helper functions and data classes
 */
@Composable
private fun getProgressData(
    combinedStatus: CombinedSyncStatus,
    currentOperation: String?,
    progress: Float?
): SyncProgressData {
    return when {
        combinedStatus.isAnySync -> SyncProgressData(
            title = currentOperation ?: "Syncing Data",
            subtitle = "Updating your workout data in the cloud",
            icon = Icons.Default.Sync,
            iconColor = LiftrixColorsV2.Teal,
            isAnimated = true,
            showProgressBar = true,
            showPercentage = progress != null,
            progressValue = progress,
            accessibilityDescription = "Sync in progress: ${currentOperation ?: "syncing data"}",
            stages = getSyncStages(),
            currentStageIndex = getCurrentStageIndex(combinedStatus)
        )
        
        combinedStatus.isAllSuccess -> SyncProgressData(
            title = "Sync Complete",
            subtitle = "All data is up to date",
            icon = Icons.Default.CheckCircle,
            iconColor = LiftrixColorsV2.DataViz.Positive,
            isAnimated = false,
            showProgressBar = false,
            showPercentage = false,
            progressValue = null,
            accessibilityDescription = "Sync completed successfully",
            stages = emptyList(),
            currentStageIndex = -1
        )
        
        combinedStatus.hasAnyError -> SyncProgressData(
            title = "Sync Failed",
            subtitle = "Unable to sync some data",
            icon = Icons.Default.Error,
            iconColor = MaterialTheme.colorScheme.error,
            isAnimated = false,
            showProgressBar = false,
            showPercentage = false,
            progressValue = null,
            accessibilityDescription = "Sync failed",
            stages = emptyList(),
            currentStageIndex = -1
        )
        
        else -> SyncProgressData(
            title = "Ready",
            subtitle = "All data is synced",
            icon = Icons.Default.CheckCircle,
            iconColor = LiftrixColorsV2.DataViz.Positive,
            isAnimated = false,
            showProgressBar = false,
            showPercentage = false,
            progressValue = null,
            accessibilityDescription = "All data synced",
            stages = emptyList(),
            currentStageIndex = -1
        )
    }
}

private fun getSyncStages(): List<SyncStage> = listOf(
    SyncStage("Workout Data", null, 0),
    SyncStage("Analytics", null, 0),
    SyncStage("Achievements", null, 0),
    SyncStage("Profile Updates", null, 0)
)

private fun getCurrentStageIndex(combinedStatus: CombinedSyncStatus): Int {
    return when {
        combinedStatus.workoutStatus is SyncStatus.Syncing -> 0
        combinedStatus.analyticsStatus is SyncStatus.Syncing -> 1
        combinedStatus.isAllSuccess -> 3
        else -> 0
    }
}

/**
 * Data classes for progress configuration
 */
private data class SyncProgressData(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconColor: Color,
    val isAnimated: Boolean,
    val showProgressBar: Boolean,
    val showPercentage: Boolean,
    val progressValue: Float?,
    val accessibilityDescription: String,
    val stages: List<SyncStage>,
    val currentStageIndex: Int
)

private data class SyncStage(
    val name: String,
    val itemCount: Int?,
    val completedItems: Int
)