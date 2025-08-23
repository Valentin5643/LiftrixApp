package com.example.liftrix.ui.profile.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.theme.ProfileColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * WeeklyProgressCard - Displays user's weekly progress with animated green progress bars
 * 
 * Features:
 * - Three progress indicators: Workouts, Volume, Streak
 * - Animated progress bars with 600ms duration
 * - Green color scheme (#4ADE80) for positive progress indication
 * - Percentage display alongside progress bars
 * - Accessibility support with semantic descriptions
 * - Responsive layout with proper spacing
 * 
 * Progress Types:
 * - Workouts: Shows completed/goal workouts (e.g., "3/5")
 * - Volume: Shows total weight lifted with percentage
 * - Streak: Shows current streak days with percentage of goal
 * 
 * Design System Compliance:
 * - Uses ProfileColors.ProgressGreen for consistency
 * - Follows 16dp card radius and semantic spacing
 * - Proper accessibility labels for screen readers
 * - Material 3 typography and color schemes
 * 
 * @param progressData Weekly progress data containing goals and current values
 * @param modifier Optional modifier for customization
 */
@Composable
fun WeeklyProgressCard(
    progressData: WeeklyProgressData,
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
            // Card header with date range
            WeeklyProgressHeader(
                weekStartDate = progressData.weekStartDate,
                weekEndDate = progressData.weekEndDate,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Workouts Progress
            ProgressItem(
                label = "Workouts",
                current = progressData.workoutsCompleted,
                total = progressData.workoutsGoal,
                color = ProfileColors.ProgressGreen,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Volume Progress
            ProgressItem(
                label = "Volume",
                currentText = formatVolume(progressData.totalVolume),
                percentage = calculateVolumePercentage(
                    progressData.totalVolume, 
                    progressData.volumeGoal
                ),
                color = ProfileColors.ProgressGreen,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Streak Progress
            ProgressItem(
                label = "Streak",
                currentText = "${progressData.currentStreak} ${if (progressData.currentStreak == 1) "day" else "days"}",
                percentage = calculateStreakPercentage(
                    progressData.currentStreak, 
                    progressData.streakGoal
                ),
                color = ProfileColors.ProgressGreen,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Header section showing the week date range
 */
@Composable
private fun WeeklyProgressHeader(
    weekStartDate: LocalDate,
    weekEndDate: LocalDate,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "This Week's Progress",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Text(
            text = formatWeekRange(weekStartDate, weekEndDate),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Individual progress item with animated progress bar
 */
@Composable
private fun ProgressItem(
    label: String,
    current: Int? = null,
    total: Int? = null,
    currentText: String? = null,
    percentage: Int? = null,
    color: Color,
    modifier: Modifier = Modifier
) {
    // Calculate actual percentage
    val actualPercentage = percentage ?: if (current != null && total != null && total > 0) {
        ((current.toFloat() / total) * 100).roundToInt()
    } else 0
    
    // Animate progress bar
    val animatedProgress by animateFloatAsState(
        targetValue = (actualPercentage / 100f).coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = 600,
            easing = FastOutSlowInEasing
        ),
        label = "progress_animation_$label"
    )
    
    Column(
        modifier = modifier.semantics {
            contentDescription = "$label progress: $actualPercentage percent"
        }
    ) {
        // Progress label and value row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Progress value text
                Text(
                    text = currentText ?: (if (current != null && total != null) "$current/$total" else ""),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Percentage text
                Text(
                    text = "$actualPercentage%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // Animated progress bar
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .semantics {
                    contentDescription = "$actualPercentage percent complete"
                },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )
    }
}

/**
 * Data class for weekly progress information
 */
data class WeeklyProgressData(
    val workoutsCompleted: Int,
    val workoutsGoal: Int,
    val totalVolume: Float, // in kg or lbs
    val volumeGoal: Float,
    val currentStreak: Int,
    val streakGoal: Int,
    val weekStartDate: LocalDate,
    val weekEndDate: LocalDate
)

/**
 * Formats volume for display (handles kg/lbs conversion)
 */
private fun formatVolume(volume: Float): String {
    return when {
        volume >= 1000f -> "${(volume / 100).roundToInt() / 10.0}K lbs"
        else -> "${volume.roundToInt()} lbs"
    }
}

/**
 * Calculates volume percentage with proper bounds checking
 */
private fun calculateVolumePercentage(current: Float, goal: Float): Int {
    return if (goal > 0) {
        ((current / goal) * 100).roundToInt().coerceIn(0, 100)
    } else 0
}

/**
 * Calculates streak percentage with proper bounds checking
 */
private fun calculateStreakPercentage(current: Int, goal: Int): Int {
    return if (goal > 0) {
        ((current.toFloat() / goal) * 100).roundToInt().coerceIn(0, 100)
    } else 0
}

/**
 * Formats the week date range for display
 */
private fun formatWeekRange(startDate: LocalDate, endDate: LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d")
    return "${startDate.format(formatter)} - ${endDate.format(formatter)}"
}

/**
 * Extension functions for progress data validation
 */
fun WeeklyProgressData.isValid(): Boolean {
    return workoutsGoal > 0 && volumeGoal > 0 && streakGoal > 0
}

fun WeeklyProgressData.getOverallProgress(): Float {
    val workoutProgress = if (workoutsGoal > 0) workoutsCompleted.toFloat() / workoutsGoal else 0f
    val volumeProgress = if (volumeGoal > 0) totalVolume / volumeGoal else 0f
    val streakProgress = if (streakGoal > 0) currentStreak.toFloat() / streakGoal else 0f
    
    return ((workoutProgress + volumeProgress + streakProgress) / 3f).coerceIn(0f, 1f)
}