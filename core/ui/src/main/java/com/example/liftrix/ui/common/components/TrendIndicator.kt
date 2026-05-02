package com.example.liftrix.ui.common.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.liftrix.domain.model.analytics.TrendDirection

@Composable
fun TrendIndicator(
    value: Float,
    modifier: Modifier = Modifier,
    positiveIsGood: Boolean = true,
    suffix: String = "%"
) {
    val isPositive = value >= 0f
    val isGood = if (positiveIsGood) isPositive else !isPositive
    val color = if (isGood) Color(0xFF20C9B7) else MaterialTheme.colorScheme.error
    val prefix = if (isPositive) "+" else ""
    Text(
        text = "$prefix${"%.1f".format(value)}$suffix",
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.labelMedium
    )
}

@Composable
fun TrendIndicator(
    trend: TrendDirection,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (label, color) = when (trend) {
        TrendDirection.UP -> "Up" to Color(0xFF20C9B7)
        TrendDirection.DOWN -> "Down" to MaterialTheme.colorScheme.error
        TrendDirection.STABLE -> "Stable" to MaterialTheme.colorScheme.onSurfaceVariant
        TrendDirection.UNKNOWN -> "Unknown" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = if (isCompact) label.take(1) else label,
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.labelMedium
    )
}
