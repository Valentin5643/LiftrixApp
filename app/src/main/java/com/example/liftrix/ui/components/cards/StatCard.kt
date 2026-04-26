package com.example.liftrix.ui.components.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.domain.model.TrendData
import com.example.liftrix.domain.model.IconData

/**
 * UI layer trend data for StatCard - maps from domain TrendData
 */
sealed class Trend {
    data class Positive(val percentage: Float, val label: String = "increase") : Trend()
    data class Negative(val percentage: Float, val label: String = "decrease") : Trend()
    data class Neutral(val label: String = "no change") : Trend()
}

/**
 * Maps domain TrendData to UI Trend
 */
fun TrendData.toUiTrend(): Trend = when (this) {
    is TrendData.Positive -> Trend.Positive(percentage, label)
    is TrendData.Negative -> Trend.Negative(percentage, label)
    is TrendData.Neutral -> Trend.Neutral(label)
}

/**
 * Maps domain IconData to UI ImageVector
 */
fun IconData.toImageVector(): ImageVector = when (this) {
    IconData.FitnessCenter -> Icons.Filled.FitnessCenter
    IconData.TrendingUp -> Icons.Filled.TrendingUp
    IconData.Schedule -> Icons.Filled.Schedule
    IconData.LocalFireDepartment -> Icons.Filled.LocalFireDepartment
    IconData.ChevronRight -> Icons.Filled.ChevronRight
    is IconData.Custom -> Icons.Filled.FitnessCenter // Default fallback
}

/**
 * Large stat display card with optional trend indicator
 * Designed for showcasing key metrics with visual hierarchy
 */
@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trend: Trend? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null
) {
    LiftrixCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                this.contentDescription = contentDescription ?: buildString {
                    append("$title: $value")
                    subtitle?.let { append(", $it") }
                    trend?.let { append(", ${getTrendDescription(it)}") }
                }
            },
        onClick = onClick,
        elevation = CardElevations.medium(),
        contentPadding = PaddingValues(CardSpacing.L)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CardSpacing.XS)
        ) {
            // Header with title and optional icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(CardSpacing.XS))
            
            // Main value display
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // Trend indicator
            trend?.let {
                TrendIndicator(
                    trend = it,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Domain-compatible StatCard that maps domain types to UI types
 */
@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trend: TrendData? = null,
    icon: IconData? = null,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null
) {
    StatCard(
        title = title,
        value = value,
        modifier = modifier,
        subtitle = subtitle,
        trend = trend?.toUiTrend(),
        icon = icon?.toImageVector(),
        onClick = onClick,
        contentDescription = contentDescription
    )
}

/**
 * Compact stat card for smaller displays
 */
@Composable
fun CompactStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    trend: Trend? = null,
    onClick: (() -> Unit)? = null
) {
    CompactLiftrixCard(
        modifier = modifier
            .semantics {
                contentDescription = buildString {
                    append("$title: $value")
                    trend?.let { append(", ${getTrendDescription(it)}") }
                }
            },
        onClick = onClick,
        contentPadding = PaddingValues(CardSpacing.M)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(CardSpacing.XXS),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            trend?.let {
                TrendIndicator(
                    trend = it,
                    compact = true
                )
            }
        }
    }
}

/**
 * Trend indicator component showing increase, decrease, or neutral trend
 */
@Composable
fun TrendIndicator(
    trend: Trend,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val (icon, color, text) = when (trend) {
        is Trend.Positive -> Triple(
            Icons.Default.TrendingUp,
            LiftrixColors.Primary,
            if (compact) "+${trend.percentage.toInt()}%" else "+${trend.percentage.toInt()}% ${trend.label}"
        )
        is Trend.Negative -> Triple(
            Icons.Default.TrendingDown,
            LiftrixColors.TiffanyBlue,
            if (compact) "-${trend.percentage.toInt()}%" else "-${trend.percentage.toInt()}% ${trend.label}"
        )
        is Trend.Neutral -> Triple(
            Icons.Default.TrendingFlat,
            MaterialTheme.colorScheme.onSurfaceVariant,
            trend.label
        )
    }
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = getTrendDescription(trend),
            tint = color,
            modifier = Modifier.size(if (compact) 14.dp else 16.dp)
        )
        
        Spacer(modifier = Modifier.width(CardSpacing.XXS))
        
        Text(
            text = text,
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Get trend description for accessibility
 */
private fun getTrendDescription(trend: Trend): String {
    return when (trend) {
        is Trend.Positive -> "${trend.percentage.toInt()}% increase"
        is Trend.Negative -> "${trend.percentage.toInt()}% decrease"
        is Trend.Neutral -> "no change"
    }
}

@Preview(showBackground = true)
@Composable
private fun StatCardPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.width(200.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCard(
                title = "Total Workouts",
                value = "42",
                subtitle = "This month",
                trend = Trend.Positive(15.2f),
                onClick = { }
            )
            
            StatCard(
                title = "Average Duration",
                value = "45min",
                trend = Trend.Negative(5.8f),
                onClick = { }
            )
            
            CompactStatCard(
                title = "Weekly Streak",
                value = "7",
                trend = Trend.Positive(12.5f),
                onClick = { }
            )
            
            CompactStatCard(
                title = "Rest Days",
                value = "2",
                trend = Trend.Neutral(),
                onClick = { }
            )
        }
    }
} 
