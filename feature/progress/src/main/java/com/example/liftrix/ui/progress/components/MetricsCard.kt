package com.example.liftrix.ui.progress.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.TrendDirection
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.components.cards.Trend
import com.example.liftrix.ui.theme.LiftrixAnimations
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.PrimaryGradient
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Material 3 metrics card component for analytics dashboard widgets.
 * 
 * Displays individual analytics metrics with optional trend indicators,
 * progress visualization, and loading states. Follows Liftrix design 
 * system with accessibility compliance and athletic micro-interactions.
 */
@Composable
fun MetricsCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trend: TrendDirection? = null,
    progress: Float? = null,
    isLoading: Boolean = false,
    gradient: Brush? = null,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val isCompact = screenWidth < 400
    
    LiftrixCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                this.contentDescription = contentDescription ?: buildString {
                    append(title)
                    append(": ")
                    append(value)
                    subtitle?.let { append(", $it") }
                    trend?.let { trendDir ->
                        append(", ")
                        append(when (trendDir) {
                            TrendDirection.UP -> "trending up"
                            TrendDirection.DOWN -> "trending down"
                            TrendDirection.STABLE -> "stable"
                            TrendDirection.UNKNOWN -> "unknown trend"
                        })
                    }
                }
            },
        onClick = onClick,
        contentPadding = if (isCompact) 
            PaddingValues(horizontal = 12.dp, vertical = 16.dp) 
        else 
            PaddingValues(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Gradient background if provided
            gradient?.let { gradientBrush ->
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                )
            }
            
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                LoadingContent(isCompact = isCompact)
            }
            
            AnimatedVisibility(
                visible = !isLoading,
                enter = fadeIn(animationSpec = tween(300)) + scaleIn(
                    animationSpec = tween(300),
                    initialScale = 0.95f
                ),
                exit = fadeOut(animationSpec = tween(300)) + scaleOut(
                    animationSpec = tween(300),
                    targetScale = 0.95f
                )
            ) {
                MetricsContent(
                    title = title,
                    value = value,
                    subtitle = subtitle,
                    icon = icon,
                    trend = trend,
                    progress = progress,
                    isCompact = isCompact
                )
            }
        }
    }
}

@Composable
private fun LoadingContent(
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(if (isCompact) 20.dp else 24.dp),
            color = LiftrixColors.Primary,
            strokeWidth = 2.dp
        )
        
        Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))
        
        Text(
            text = "Loading...",
            style = if (isCompact) 
                MaterialTheme.typography.bodySmall 
            else 
                MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun MetricsContent(
    title: String,
    value: String,
    subtitle: String?,
    icon: ImageVector?,
    trend: TrendDirection?,
    progress: Float?,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp)
    ) {
        // Header with title and icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = if (isCompact)
                    MaterialTheme.typography.titleSmall
                else
                    MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            icon?.let { iconVector ->
                Icon(
                    imageVector = iconVector,
                    contentDescription = title,
                    modifier = Modifier.size(if (isCompact) 16.dp else 20.dp),
                    tint = LiftrixColors.Primary.copy(alpha = 0.7f)
                )
            }
        }
        
        // Main value display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = value,
                style = if (isCompact)
                    MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                else
                    MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f)
            )
            
            // Trend indicator
            trend?.let { trendDirection ->
                TrendIndicator(
                    trend = trendDirection,
                    isCompact = isCompact
                )
            }
        }
        
        // Progress indicator
        progress?.let { progressValue ->
            ProgressIndicator(
                progress = progressValue,
                isCompact = isCompact
            )
        }
        
        // Subtitle
        subtitle?.let { subtitleText ->
            Text(
                text = subtitleText,
                style = if (isCompact)
                    MaterialTheme.typography.bodySmall
                else
                    MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TrendIndicator(
    trend: TrendDirection,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    val (icon, color) = when (trend) {
        TrendDirection.UP -> Icons.Default.TrendingUp to LiftrixColors.Primary
        TrendDirection.DOWN -> Icons.Default.TrendingDown to LiftrixColors.TiffanyBlue
        TrendDirection.STABLE -> Icons.Default.TrendingFlat to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        TrendDirection.UNKNOWN -> Icons.Default.TrendingFlat to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    }
    
    Box(
        modifier = modifier
            .size(if (isCompact) 28.dp else 32.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = when (trend) {
                TrendDirection.UP -> "Trending up"
                TrendDirection.DOWN -> "Trending down"
                TrendDirection.STABLE -> "Stable trend"
                TrendDirection.UNKNOWN -> "Unknown trend"
            },
            modifier = Modifier.size(if (isCompact) 14.dp else 16.dp),
            tint = color
        )
    }
}

@Composable
private fun ProgressIndicator(
    progress: Float,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1000),
        label = "progress_animation"
    )
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Progress",
                style = if (isCompact)
                    MaterialTheme.typography.labelSmall
                else
                    MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            
            Text(
                text = "${(animatedProgress * 100).toInt()}%",
                style = if (isCompact)
                    MaterialTheme.typography.labelSmall
                else
                    MaterialTheme.typography.labelMedium,
                color = LiftrixColors.Primary,
                fontWeight = FontWeight.Medium
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isCompact) 4.dp else 6.dp)
                .clip(RoundedCornerShape(if (isCompact) 2.dp else 3.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(if (isCompact) 4.dp else 6.dp)
                    .clip(RoundedCornerShape(if (isCompact) 2.dp else 3.dp))
                    .background(
                        brush = PrimaryGradient
                    )
            )
        }
    }
}

/**
 * Compact variant of MetricsCard for dashboard grids
 */
@Composable
fun CompactMetricsCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trend: TrendDirection? = null,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null
) {
    MetricsCard(
        title = title,
        value = value,
        modifier = modifier,
        icon = icon,
        trend = trend,
        onClick = onClick,
        contentDescription = contentDescription
    )
}

@Preview(showBackground = true)
@Composable
private fun MetricsCardPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MetricsCard(
                title = "Total Volume",
                value = "2,847 kg",
                subtitle = "This week",
                trend = TrendDirection.UP,
                progress = 0.75f
            )
            
            MetricsCard(
                title = "Workout Frequency",
                value = "4 sessions",
                subtitle = "Last 7 days",
                trend = TrendDirection.STABLE,
                isLoading = false
            )
            
            MetricsCard(
                title = "Loading Example",
                value = "",
                isLoading = true
            )
        }
    }
}
