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
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixColors

sealed class Trend {
    data class Positive(val percentage: Float, val label: String = "increase") : Trend()
    data class Negative(val percentage: Float, val label: String = "decrease") : Trend()
    data class Neutral(val label: String = "no change") : Trend()
}

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
                this.contentDescription = contentDescription ?: "$title: $value"
            },
        onClick = onClick,
        elevation = CardElevations.medium(),
        contentPadding = PaddingValues(CardSpacing.L)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CardSpacing.XS)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
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
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            trend?.let { TrendIndicator(trend = it, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
fun CompactStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    trend: Trend? = null,
    onClick: (() -> Unit)? = null
) {
    CompactLiftrixCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(CardSpacing.M)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(CardSpacing.XXS),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            trend?.let { TrendIndicator(trend = it, compact = true) }
        }
    }
}

@Composable
fun TrendIndicator(
    trend: Trend,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val (icon, color, text) = when (trend) {
        is Trend.Positive -> Triple(Icons.Default.TrendingUp, LiftrixColors.Primary, "+${trend.percentage.toInt()}%")
        is Trend.Negative -> Triple(Icons.Default.TrendingDown, LiftrixColors.TiffanyBlue, "-${trend.percentage.toInt()}%")
        is Trend.Neutral -> Triple(Icons.Default.TrendingFlat, MaterialTheme.colorScheme.onSurfaceVariant, trend.label)
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(if (compact) 14.dp else 16.dp))
        Spacer(modifier = Modifier.width(CardSpacing.XXS))
        Text(text = text, color = color, style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
    }
}

