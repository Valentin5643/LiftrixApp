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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.analytics.TrendDirection
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlin.math.abs

/**
 * Specialized card component for displaying workout volume metrics.
 * 
 * Features enhanced visualization for volume data including:
 * - Current volume with unit formatting
 * - Week-over-week change with visual indicators  
 * - Goal progress with gradient progress bar
 * - Volume intensity color coding
 * - Accessibility-compliant descriptions
 */
@Composable
fun VolumeMetricCard(
    totalVolume: Weight,
    modifier: Modifier = Modifier,
    weeklyChange: Float? = null,
    goalVolume: Weight? = null,
    previousWeekVolume: Weight? = null,
    isLoading: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val progress: Float? = if (goalVolume != null && goalVolume.value > 0) {
        (totalVolume.value / goalVolume.value).toFloat().coerceIn(0f, 1f)
    } else null
    
    val trend = when {
        weeklyChange == null -> null
        weeklyChange > 0.05f -> TrendDirection.UP
        weeklyChange < -0.05f -> TrendDirection.DOWN
        else -> TrendDirection.STABLE
    }
    
    val animatedProgress by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = tween(durationMillis = 1200),
        label = "volume_progress"
    )
    
    val progressColor by animateColorAsState(
        targetValue = when {
            progress == null -> LiftrixColors.Primary.copy(alpha = 0.3f)
            progress >= 1f -> LiftrixColors.Primary
            progress >= 0.7f -> LiftrixColors.Primary.copy(alpha = 0.8f)
            progress >= 0.4f -> LiftrixColors.Secondary.copy(alpha = 0.8f)
            else -> LiftrixColors.Accent.copy(alpha = 0.6f)
        },
        animationSpec = tween(durationMillis = 800),
        label = "progress_color"
    )
    
    LiftrixCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = buildVolumeAccessibilityDescription(
                    totalVolume = totalVolume,
                    weeklyChange = weeklyChange,
                    goalVolume = goalVolume,
                    progress = progress
                )
            },
        onClick = onClick,
        contentPadding = PaddingValues(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        text = "Total Volume",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                    
                    previousWeekVolume?.let { prevVolume ->
                        Text(
                            text = "Previous: ${prevVolume.displayValue}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(LiftrixColors.Primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = LiftrixColors.Primary
                    )
                }
            }
            
            // Main volume display with trend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = totalVolume.displayValue,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start
                    )
                    
                    Text(
                        text = "This week",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                // Weekly change indicator
                weeklyChange?.let { change ->
                    VolumeChangeIndicator(
                        change = change,
                        trend = trend ?: TrendDirection.STABLE
                    )
                }
            }
            
            // Goal progress section
            goalVolume?.let { goal ->
                VolumeProgressSection(
                    currentVolume = totalVolume,
                    goalVolume = goal,
                    progress = animatedProgress,
                    progressColor = progressColor
                )
            }
            
            // Volume insights
            VolumeInsights(
                totalVolume = totalVolume,
                weeklyChange = weeklyChange,
                progress = progress
            )
        }
    }
}

@Composable
private fun VolumeChangeIndicator(
    change: Float,
    trend: TrendDirection,
    modifier: Modifier = Modifier
) {
    val changePercent = abs(change * 100).toInt()
    val changeText = when (trend) {
        TrendDirection.UP -> "+$changePercent%"
        TrendDirection.DOWN -> "-$changePercent%"
        TrendDirection.STABLE -> "±$changePercent%"
    }
    
    val indicatorColor = when (trend) {
        TrendDirection.UP -> LiftrixColors.Primary
        TrendDirection.DOWN -> LiftrixColors.Accent
        TrendDirection.STABLE -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(indicatorColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (trend) {
                    TrendDirection.UP -> Icons.Default.TrendingUp
                    TrendDirection.DOWN -> Icons.Default.TrendingUp // Will be rotated
                    TrendDirection.STABLE -> Icons.Default.TrendingUp
                },
                contentDescription = null,
                modifier = Modifier
                    .size(12.dp)
                    .let { mod ->
                        when (trend) {
                            TrendDirection.DOWN -> mod
                            else -> mod
                        }
                    },
                tint = indicatorColor
            )
        }
        
        Text(
            text = changeText,
            style = MaterialTheme.typography.labelMedium,
            color = indicatorColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun VolumeProgressSection(
    currentVolume: Weight,
    goalVolume: Weight,
    progress: Float,
    progressColor: Color,
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
                text = "Weekly Goal",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = "${goalVolume.displayValue} target",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        
        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(progressColor, progressColor.copy(alpha = 0.8f))
                        )
                    )
            )
        }
        
        // Progress text
        Text(
            text = "${(progress * 100).toInt()}% of weekly goal",
            style = MaterialTheme.typography.bodySmall,
            color = progressColor,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun VolumeInsights(
    totalVolume: Weight,
    weeklyChange: Float?,
    progress: Float?,
    modifier: Modifier = Modifier
) {
    val insights = buildList {
        // Volume category insight
        when {
            totalVolume.value >= 10000 -> add("Elite volume level" to LiftrixColors.Primary)
            totalVolume.value >= 5000 -> add("High volume training" to LiftrixColors.Primary)
            totalVolume.value >= 2000 -> add("Moderate volume" to LiftrixColors.Secondary)
            else -> add("Building volume" to LiftrixColors.Accent)
        }
        
        // Change insight
        weeklyChange?.let { change ->
            when {
                change > 0.2f -> add("Significant increase" to LiftrixColors.Primary)
                change > 0.05f -> add("Steady progress" to LiftrixColors.Primary)
                change < -0.1f -> add("Consider deload" to LiftrixColors.Accent)
                else -> add("Consistent training" to LiftrixColors.Secondary)
            }
        }
        
        // Goal insight
        progress?.let { prog ->
            when {
                prog >= 1f -> add("Goal achieved!" to LiftrixColors.Primary)
                prog >= 0.8f -> add("Nearly there" to LiftrixColors.Primary)
                prog >= 0.5f -> add("Good progress" to LiftrixColors.Secondary)
                else -> add("Keep pushing" to LiftrixColors.Accent)
            }
        }
    }
    
    if (insights.isNotEmpty()) {
        Row(
            modifier = modifier.fillMaxWidth(),
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

private fun buildVolumeAccessibilityDescription(
    totalVolume: Weight,
    weeklyChange: Float?,
    goalVolume: Weight?,
    progress: Float?
): String {
    return buildString {
        append("Total volume: ${totalVolume.displayValue} this week")
        
        weeklyChange?.let { change ->
            val changePercent = abs(change * 100).toInt()
            when {
                change > 0.05f -> append(", up $changePercent percent from last week")
                change < -0.05f -> append(", down $changePercent percent from last week")
                else -> append(", similar to last week")
            }
        }
        
        goalVolume?.let { goal ->
            progress?.let { prog ->
                val progressPercent = (prog * 100).toInt()
                append(", $progressPercent percent of ${goal.displayValue} weekly goal")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VolumeMetricCardPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            VolumeMetricCard(
                totalVolume = Weight(2847.0),
                weeklyChange = 0.15f,
                goalVolume = Weight(3500.0),
                previousWeekVolume = Weight(2475.0)
            )
            
            VolumeMetricCard(
                totalVolume = Weight(1250.0),
                weeklyChange = -0.08f,
                goalVolume = Weight(2000.0)
            )
            
            VolumeMetricCard(
                totalVolume = Weight(5420.0),
                weeklyChange = 0.02f
            )
        }
    }
}