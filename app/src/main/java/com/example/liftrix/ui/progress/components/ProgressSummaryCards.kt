package com.example.liftrix.ui.progress.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.repository.ProgressSummary
import com.example.liftrix.ui.components.animations.AnimatedProgressRing
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.components.cards.StatCard
import com.example.liftrix.ui.components.cards.CompactStatCard
import com.example.liftrix.ui.components.cards.Trend
import com.example.liftrix.ui.components.layouts.GridSystem
import com.example.liftrix.ui.theme.LiftrixColors
import kotlin.math.min

/**
 * Modern progress summary cards component with enhanced visual hierarchy.
 * 
 * Displays essential workout metrics using the new LiftrixCard system with
 * animated progress rings, brand colors, and professional data dashboard styling.
 * Features asymmetrical composition and responsive grid layout.
 * 
 * Key improvements:
 * - LiftrixCard system with 24dp border radius
 * - Animated progress rings with brand colors
 * - Enhanced visual hierarchy with proper spacing
 * - Professional data dashboard styling
 * - Responsive grid layout for different screen sizes
 * 
 * @param summaryData Progress summary data to display
 * @param isLoading Whether the summary data is currently loading
 * @param modifier Modifier for styling the component container
 */
@Composable
fun ProgressSummaryCards(
    summaryData: ProgressSummary,
    isLoading: Boolean,
    weightUnit: com.example.liftrix.domain.model.WeightUnit,
    weightFormatter: com.example.liftrix.core.formatting.WeightFormatter,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(GridSystem.gapMedium)
    ) {
        // Header with enhanced typography
        Text(
            text = "Progress Summary",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = GridSystem.spacing1)
        )
        
        if (isLoading) {
            LoadingState()
        } else {
            // Asymmetrical composition with large stat card and compact cards
            Column(
                verticalArrangement = Arrangement.spacedBy(GridSystem.gapMedium)
            ) {
                // Primary stat card with animated progress ring
                PrimaryStatCard(summaryData = summaryData)
                
                // Grid of compact stat cards
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.height(200.dp),
                    horizontalArrangement = Arrangement.spacedBy(GridSystem.gapMedium),
                    verticalArrangement = Arrangement.spacedBy(GridSystem.gapMedium)
                ) {
                    items(getCompactStats(summaryData, weightUnit, weightFormatter)) { stat ->
                        CompactStatCard(
                            title = stat.title,
                            value = stat.value,
                            trend = stat.trend,
                            onClick = { /* Handle stat click */ }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Primary stat card with animated progress ring and enhanced visual hierarchy
 */
@Composable
private fun PrimaryStatCard(summaryData: ProgressSummary) {
    val workoutProgress = calculateWorkoutProgress(summaryData.totalWorkouts)
    val streakProgress = calculateStreakProgress(summaryData.currentStreak, summaryData.longestStreak)
    
    LiftrixCard(
        modifier = Modifier.fillMaxWidth(),
        contentDescription = "Primary workout statistics with progress visualization"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Progress visualization
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedProgressRing(
                        progress = workoutProgress,
                        size = 80.dp,
                        strokeWidth = 6.dp,
                        color = LiftrixColors.Primary
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${summaryData.totalWorkouts}",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "workouts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Statistics column
            Column(
                modifier = Modifier.weight(1f).padding(start = GridSystem.spacing4),
                verticalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
            ) {
                StatRow(
                    icon = Icons.Default.LocalFireDepartment,
                    label = "Current Streak",
                    value = "${summaryData.currentStreak} days",
                    progress = streakProgress
                )
                
                StatRow(
                    icon = Icons.Default.AccessTime,
                    label = "Avg Duration",
                    value = "${summaryData.averageDuration} min",
                    progress = calculateDurationProgress(summaryData.averageDuration)
                )
                
                StatRow(
                    icon = Icons.Default.EmojiEvents,
                    label = "Weekly Average",
                    value = String.format("%.1f", summaryData.averageWorkoutsPerWeek),
                    progress = summaryData.averageWorkoutsPerWeek / 7f
                )
            }
        }
    }
}

/**
 * Individual stat row with icon and mini progress indicator
 */
@Composable
private fun StatRow(
    icon: ImageVector,
    label: String,
    value: String,
    progress: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // Mini progress ring
        AnimatedProgressRing(
            progress = progress,
            size = 24.dp,
            strokeWidth = 2.dp,
            color = LiftrixColors.Primary
        )
    }
}

/**
 * Modern loading state with enhanced visual design
 */
@Composable
private fun LoadingState() {
    LiftrixCard(
        modifier = Modifier.fillMaxWidth(),
        contentDescription = "Loading progress summary"
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(GridSystem.spacing3)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Loading progress summary...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Generate compact stat items for grid display
 */
private fun getCompactStats(
    summaryData: ProgressSummary,
    weightUnit: com.example.liftrix.domain.model.WeightUnit,
    weightFormatter: com.example.liftrix.core.formatting.WeightFormatter
): List<CompactStatItem> {
    // Convert total volume from kg to user's preferred unit
    val totalVolumeInKg = summaryData.totalVolume.toDouble()
    val totalVolumeInUserUnit = weightUnit.convertFromKilograms(totalVolumeInKg)
    val formattedVolume = "${(totalVolumeInUserUnit / 1000).toInt()}K ${weightUnit.symbol}"
    
    return listOf(
        CompactStatItem(
            title = "Total Volume",
            value = formattedVolume,
            trend = Trend.Positive(12.5f, "increase")
        ),
        CompactStatItem(
            title = "Active Time",
            value = "${(summaryData.totalActiveTime / 60).toInt()}h",
            trend = Trend.Positive(8.3f, "increase")
        ),
        CompactStatItem(
            title = "Best Streak",
            value = "${summaryData.longestStreak} days",
            trend = if (summaryData.currentStreak == summaryData.longestStreak) {
                Trend.Positive(0f, "current best")
            } else {
                Trend.Neutral("personal record")
            }
        ),
        CompactStatItem(
            title = "Consistency",
            value = "${((summaryData.averageWorkoutsPerWeek / 7f) * 100).toInt()}%",
            trend = Trend.Positive(15.2f, "improvement")
        )
    )
}

/**
 * Data class for compact stat items
 */
private data class CompactStatItem(
    val title: String,
    val value: String,
    val trend: Trend? = null
)

/**
 * Calculate workout progress based on total workouts (0-50 workouts = 0-1.0 progress)
 */
private fun calculateWorkoutProgress(totalWorkouts: Int): Float {
    return min(totalWorkouts.toFloat() / 50f, 1f)
}

/**
 * Calculate streak progress based on current and longest streak
 */
private fun calculateStreakProgress(currentStreak: Int, longestStreak: Int): Float {
    return if (longestStreak > 0) {
        min(currentStreak.toFloat() / longestStreak.toFloat(), 1f)
    } else {
        0f
    }
}

/**
 * Calculate duration progress based on average duration (target: 60 minutes)
 */
private fun calculateDurationProgress(averageDuration: Int): Float {
    return min(averageDuration.toFloat() / 60f, 1f)
} 