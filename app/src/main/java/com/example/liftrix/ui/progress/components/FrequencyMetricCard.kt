package com.example.liftrix.ui.progress.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.TrendDirection
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlin.math.roundToInt

/**
 * Data class representing daily workout frequency
 */
data class DailyFrequency(
    val date: LocalDate,
    val workoutCount: Int,
    val hasWorkout: Boolean = workoutCount > 0
)

/**
 * Frequency statistics for the card
 */
data class FrequencyStats(
    val currentWeekSessions: Int,
    val targetSessions: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val weeklyAverage: Float,
    val dailyFrequencies: List<DailyFrequency> = emptyList()
) {
    val completionRate: Float 
        get() = if (targetSessions > 0) currentWeekSessions.toFloat() / targetSessions else 0f
    
    val isOnTrack: Boolean
        get() = completionRate >= 0.8f
    
    val trend: TrendDirection
        get() = when {
            completionRate >= 1f -> TrendDirection.UP
            completionRate >= 0.6f -> TrendDirection.STABLE
            else -> TrendDirection.DOWN
        }
}

/**
 * Specialized card component for displaying workout frequency metrics.
 * 
 * Features comprehensive frequency visualization including:
 * - Current week sessions vs target
 * - Weekly completion progress
 * - Daily frequency heatmap (last 7 days)
 * - Streak tracking and motivation
 * - Consistency scoring and insights
 */
@Composable
fun FrequencyMetricCard(
    frequencyStats: FrequencyStats,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val completionProgress by animateFloatAsState(
        targetValue = frequencyStats.completionRate.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1000),
        label = "frequency_progress"
    )
    
    val progressColor by animateColorAsState(
        targetValue = when {
            frequencyStats.completionRate >= 1f -> LiftrixColors.Primary
            frequencyStats.completionRate >= 0.8f -> LiftrixColors.Primary.copy(alpha = 0.8f)
            frequencyStats.completionRate >= 0.5f -> LiftrixColors.Secondary
            else -> LiftrixColors.TiffanyBlue.copy(alpha = 0.7f)
        },
        animationSpec = tween(durationMillis = 800),
        label = "frequency_color"
    )
    
    LiftrixCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = buildFrequencyAccessibilityDescription(frequencyStats)
            },
        onClick = onClick,
        contentPadding = PaddingValues(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with icon and title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Workout Frequency",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = "Weekly consistency tracking",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(LiftrixColors.Secondary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = LiftrixColors.Secondary
                    )
                }
            }
            
            // Main frequency display with compact progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Left side - Main stats
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "${frequencyStats.currentWeekSessions} of ${frequencyStats.targetSessions}",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start
                    )
                    
                    Text(
                        text = "sessions this week",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    
                    // Compact progress bar
                    Spacer(modifier = Modifier.height(8.dp))
                    CompactFrequencyProgressBar(
                        progress = completionProgress,
                        progressColor = progressColor,
                        targetSessions = frequencyStats.targetSessions
                    )
                }
                
                // Right side - Completion badge
                FrequencyCompletionBadge(
                    completionRate = frequencyStats.completionRate,
                    color = progressColor
                )
            }
            
            // Daily frequency heatmap - more compact
            if (frequencyStats.dailyFrequencies.isNotEmpty()) {
                CompactFrequencyHeatmap(
                    dailyFrequencies = frequencyStats.dailyFrequencies
                )
            }
            
            // Streak and insights - combined in one row
            CompactFrequencyInsights(
                stats = frequencyStats
            )
        }
    }
}

@Composable
private fun FrequencyCompletionBadge(
    completionRate: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val badgeText = when {
        completionRate >= 1f -> "Goal Met!"
        completionRate >= 0.8f -> "On Track"
        completionRate >= 0.5f -> "Half Way"
        else -> "Keep Going"
    }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = badgeText,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CompactFrequencyProgressBar(
    progress: Float,
    progressColor: Color,
    targetSessions: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Segmented progress bar (one segment per target session)
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            repeat(targetSessions) { index ->
                val segmentProgress = ((progress * targetSessions) - index).coerceIn(0f, 1f)
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (segmentProgress > 0.5f) {
                                progressColor
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            }
                        )
                )
            }
        }
        
        Text(
            text = "${(progress * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = progressColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FrequencyProgressBar(
    progress: Float,
    progressColor: Color,
    targetSessions: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Weekly Progress",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = "${(progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = progressColor,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        // Segmented progress bar (one segment per target session)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(targetSessions) { index ->
                val segmentProgress = ((progress * targetSessions) - index).coerceIn(0f, 1f)
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (segmentProgress > 0.5f) {
                                progressColor
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun CompactFrequencyHeatmap(
    dailyFrequencies: List<DailyFrequency>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Last 7 Days",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            fontWeight = FontWeight.Medium
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(dailyFrequencies.takeLast(7)) { dailyFreq ->
                CompactFrequencyDayIndicator(
                    dailyFrequency = dailyFreq
                )
            }
        }
    }
}

@Composable
private fun FrequencyHeatmap(
    dailyFrequencies: List<DailyFrequency>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Last 7 Days",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            fontWeight = FontWeight.Medium
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(dailyFrequencies.takeLast(7)) { dailyFreq ->
                FrequencyDayIndicator(
                    dailyFrequency = dailyFreq
                )
            }
        }
    }
}

@Composable
private fun CompactFrequencyDayIndicator(
    dailyFrequency: DailyFrequency,
    modifier: Modifier = Modifier
) {
    val indicatorColor = when {
        dailyFrequency.workoutCount >= 2 -> LiftrixColors.Primary
        dailyFrequency.workoutCount == 1 -> LiftrixColors.Secondary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    }
    
    val icon = when {
        dailyFrequency.workoutCount >= 1 -> Icons.Default.CheckCircle
        else -> Icons.Default.RadioButtonUnchecked
    }
    
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(indicatorColor.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "${dailyFrequency.date.dayOfWeek.name}: ${if (dailyFrequency.hasWorkout) "workout completed" else "rest day"}",
            modifier = Modifier.size(12.dp),
            tint = indicatorColor
        )
    }
}

@Composable
private fun FrequencyDayIndicator(
    dailyFrequency: DailyFrequency,
    modifier: Modifier = Modifier
) {
    val dayOfWeek = dailyFrequency.date.dayOfWeek
    val dayLabel = when (dayOfWeek) {
        DayOfWeek.MONDAY -> "M"
        DayOfWeek.TUESDAY -> "T"
        DayOfWeek.WEDNESDAY -> "W"
        DayOfWeek.THURSDAY -> "T"
        DayOfWeek.FRIDAY -> "F"
        DayOfWeek.SATURDAY -> "S"
        DayOfWeek.SUNDAY -> "S"
    }
    
    val indicatorColor = when {
        dailyFrequency.workoutCount >= 2 -> LiftrixColors.Primary
        dailyFrequency.workoutCount == 1 -> LiftrixColors.Secondary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    }
    
    val icon = when {
        dailyFrequency.workoutCount >= 1 -> Icons.Default.CheckCircle
        else -> Icons.Default.RadioButtonUnchecked
    }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(indicatorColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "${dayOfWeek.name}: ${if (dailyFrequency.hasWorkout) "workout completed" else "rest day"}",
                modifier = Modifier.size(16.dp),
                tint = indicatorColor
            )
        }
        
        Text(
            text = dayLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CompactFrequencyInsights(
    stats: FrequencyStats,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Streak information - compact
        if (stats.currentStreak > 0) {
            Text(
                text = "Streak: ${stats.currentStreak} days",
                style = MaterialTheme.typography.bodyMedium,
                color = LiftrixColors.Primary,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Text(
                text = "Best: ${stats.longestStreak} days",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
        }
        
        // Single top insight
        val insights = getFrequencyInsights(stats)
        if (insights.isNotEmpty()) {
            val (insight, color) = insights.first()
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.1f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = insight,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun FrequencyInsights(
    stats: FrequencyStats,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Streak information
        if (stats.currentStreak > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Current Streak",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    
                    Text(
                        text = "${stats.currentStreak} days",
                        style = MaterialTheme.typography.titleMedium,
                        color = LiftrixColors.Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Best Streak",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    
                    Text(
                        text = "${stats.longestStreak} days",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        
        // Insights and motivational text
        val insights = getFrequencyInsights(stats)
        if (insights.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                insights.take(2).forEach { (insight, color) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(color.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = insight,
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

private fun getFrequencyInsights(stats: FrequencyStats): List<Pair<String, Color>> {
    return buildList {
        // Completion insight
        when {
            stats.completionRate >= 1f -> add("Excellent consistency!" to LiftrixColors.Primary)
            stats.completionRate >= 0.8f -> add("Great progress" to LiftrixColors.Primary)
            stats.completionRate >= 0.5f -> add("Building momentum" to LiftrixColors.Secondary)
            else -> add("Stay consistent" to LiftrixColors.TiffanyBlue)
        }
        
        // Streak insight
        when {
            stats.currentStreak >= 7 -> add("Weekly streak!" to LiftrixColors.Primary)
            stats.currentStreak >= 3 -> add("Good rhythm" to LiftrixColors.Secondary)
            stats.currentStreak == 0 -> add("Fresh start" to LiftrixColors.TiffanyBlue)
        }
        
        // Weekly average insight
        when {
            stats.weeklyAverage >= 5f -> add("High frequency" to LiftrixColors.Primary)
            stats.weeklyAverage >= 3f -> add("Balanced routine" to LiftrixColors.Secondary)
            stats.weeklyAverage >= 1f -> add("Building habit" to LiftrixColors.TiffanyBlue)
        }
    }
}

private fun buildFrequencyAccessibilityDescription(stats: FrequencyStats): String {
    return buildString {
        append("Workout frequency: ${stats.currentWeekSessions} of ${stats.targetSessions} sessions this week")
        
        val completionPercent = (stats.completionRate * 100).roundToInt()
        append(", $completionPercent percent complete")
        
        if (stats.currentStreak > 0) {
            append(", current streak ${stats.currentStreak} days")
        }
        
        when {
            stats.isOnTrack -> append(", on track to meet weekly goal")
            else -> append(", ${stats.targetSessions - stats.currentWeekSessions} more sessions needed")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FrequencyMetricCardPreview() {
    LiftrixTheme {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val sampleDailyFrequencies = (0..6).map { daysBack ->
            val date = today.minus(DatePeriod(days = daysBack))
            DailyFrequency(
                date = date,
                workoutCount = if (daysBack % 2 == 0) 1 else 0
            )
        }.reversed()
        
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FrequencyMetricCard(
                frequencyStats = FrequencyStats(
                    currentWeekSessions = 4,
                    targetSessions = 5,
                    currentStreak = 8,
                    longestStreak = 15,
                    weeklyAverage = 3.8f,
                    dailyFrequencies = sampleDailyFrequencies
                )
            )
            
            FrequencyMetricCard(
                frequencyStats = FrequencyStats(
                    currentWeekSessions = 2,
                    targetSessions = 4,
                    currentStreak = 2,
                    longestStreak = 7,
                    weeklyAverage = 2.1f
                )
            )
        }
    }
}