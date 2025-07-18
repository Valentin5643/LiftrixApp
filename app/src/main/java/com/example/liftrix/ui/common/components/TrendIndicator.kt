package com.example.liftrix.ui.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.HelpOutline
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liftrix.domain.model.analytics.TrendDirection
import kotlin.math.abs

/**
 * A composable that displays a trend indicator with arrow and optional percentage change.
 * 
 * Features:
 * - Color-coded trend arrows (green up, red down, gray stable)
 * - Optional percentage change display with proper formatting
 * - Accessibility content descriptions for screen readers
 * - Material 3 design system integration
 * 
 * @param trend The TrendDirection to display
 * @param modifier Modifier for styling and layout
 * @param percentageChange Optional percentage change value for display
 * @param showPercentage Whether to display the percentage text (default false)
 * @param isCompact Whether to use compact display (default false)
 */
@Composable
fun TrendIndicator(
    trend: TrendDirection,
    modifier: Modifier = Modifier,
    percentageChange: Float? = null,
    showPercentage: Boolean = false,
    isCompact: Boolean = false
) {
    val (icon, color, contentDesc) = getTrendPropertiesFromDirection(trend, percentageChange)
    
    Row(
        modifier = modifier.semantics {
            contentDescription = contentDesc
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null, // Content description provided by parent Row
            tint = color,
            modifier = Modifier.size(if (isCompact) 14.dp else 16.dp)
        )
        
        if (showPercentage && percentageChange != null) {
            Text(
                text = formatPercentageChange(percentageChange),
                color = color,
                fontSize = if (isCompact) 10.sp else 12.sp,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/**
 * Overloaded version that accepts percentage change for automatic trend calculation.
 * 
 * @param percentageChange The percentage change value (e.g., 15.5 for +15.5%)
 * @param threshold The threshold for determining stable vs trending (default 1.0%)
 * @param modifier Modifier for styling and layout
 * @param showPercentage Whether to display the percentage text (default true)
 */
@Composable
fun TrendIndicator(
    percentageChange: Float,
    threshold: Float = 1.0f,
    modifier: Modifier = Modifier,
    showPercentage: Boolean = true
) {
    val trendDirection = TrendDirection.fromPercentageChange(percentageChange, threshold)
    
    TrendIndicator(
        trend = trendDirection,
        modifier = modifier,
        percentageChange = percentageChange,
        showPercentage = showPercentage
    )
}

/**
 * Gets the appropriate icon, color, and content description for the trend direction.
 */
@Composable
private fun getTrendPropertiesFromDirection(
    direction: TrendDirection,
    percentageChange: Float?
): Triple<ImageVector, Color, String> {
    return when (direction) {
        TrendDirection.UP -> Triple(
            Icons.Default.TrendingUp,
            MaterialTheme.colorScheme.primary, // Using primary color (teal #20C9B7)
            "Trending up" + if (percentageChange != null) " by ${formatPercentageChange(percentageChange)}" else ""
        )
        TrendDirection.DOWN -> Triple(
            Icons.Default.TrendingDown,
            MaterialTheme.colorScheme.error,
            "Trending down" + if (percentageChange != null) " by ${formatPercentageChange(abs(percentageChange))}" else ""
        )
        TrendDirection.STABLE -> Triple(
            Icons.Default.TrendingFlat,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Stable trend" + if (percentageChange != null) " with ${formatPercentageChange(abs(percentageChange))} change" else ""
        )
        TrendDirection.UNKNOWN -> Triple(
            Icons.Default.HelpOutline,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Unknown trend - insufficient data"
        )
    }
}

/**
 * Formats percentage change for display with appropriate sign and precision.
 */
private fun formatPercentageChange(percentageChange: Float): String {
    val absChange = abs(percentageChange)
    val sign = when {
        percentageChange > 0 -> "+"
        percentageChange < 0 -> "-"
        else -> ""
    }
    
    return when {
        absChange >= 100 -> "${sign}${absChange.toInt()}%"
        absChange >= 10 -> "${sign}${"%.1f".format(absChange)}%"
        else -> "${sign}${"%.1f".format(absChange)}%"
    }
}

/**
 * Overloaded version that accepts Double for compatibility
 */
@Composable
fun TrendIndicator(
    percentageChange: Double,
    threshold: Double = 1.0,
    modifier: Modifier = Modifier,
    showPercentage: Boolean = true
) {
    TrendIndicator(
        percentageChange = percentageChange.toFloat(),
        threshold = threshold.toFloat(),
        modifier = modifier,
        showPercentage = showPercentage
    )
}