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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Activity card for smaller activity panels with consistent 8pt grid spacing
 * Supports asymmetrical composition for different content types
 */
@Composable
fun ActivityCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null
) {
    LiftrixCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                this.contentDescription = contentDescription ?: "$title: $subtitle"
            },
        onClick = onClick,
        elevation = CardElevations.standard(),
        contentPadding = PaddingValues(CardSpacing.M)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading content with icon and text
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(CardSpacing.M))
                
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(CardSpacing.XXS)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Trailing content
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                trailing?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    
                    if (showChevron) {
                        Spacer(modifier = Modifier.width(CardSpacing.XS))
                    }
                }
                
                if (showChevron && onClick != null) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Compact activity card for minimal space usage
 */
@Composable
fun CompactActivityCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    CompactLiftrixCard(
        modifier = modifier
            .semantics {
                contentDescription = "$title: $subtitle"
            },
        onClick = onClick,
        contentPadding = PaddingValues(CardSpacing.S)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CardSpacing.XS)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CardSpacing.XXS)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Asymmetrical activity card with flexible content arrangement
 */
@Composable
fun AsymmetricalActivityCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    content: @Composable () -> Unit
) {
    LiftrixCard(
        modifier = modifier
            .semantics {
                contentDescription?.let { this.contentDescription = it }
            },
        onClick = onClick,
        elevation = CardElevations.standard(),
        contentPadding = PaddingValues(CardSpacing.M)
    ) {
        content()
    }
}

/**
 * Feature card for highlighting important features or actions
 */
@Composable
fun FeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onClick: (() -> Unit)? = null
) {
    ElevatedLiftrixCard(
        modifier = modifier
            .semantics {
                contentDescription = "$title: $description"
            },
        onClick = onClick,
        contentPadding = PaddingValues(CardSpacing.L)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(CardSpacing.M)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CardSpacing.M),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(CardSpacing.XS)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            actionText?.let {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.width(CardSpacing.XS))
                    
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "View details",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ActivityCardPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.width(300.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActivityCard(
                title = "Recent Workout",
                subtitle = "Push Day - 45 minutes",
                icon = Icons.Default.FitnessCenter,
                trailing = "2h ago",
                onClick = { }
            )
            
            ActivityCard(
                title = "Next Scheduled",
                subtitle = "Pull Day - Tomorrow",
                icon = Icons.Default.FitnessCenter,
                showChevron = false,
                onClick = { }
            )
            
            CompactActivityCard(
                title = "Quick Start",
                subtitle = "15 min",
                icon = Icons.Default.FitnessCenter,
                onClick = { }
            )
            
            FeatureCard(
                title = "New Feature",
                description = "Track your progress with detailed analytics and insights.",
                icon = Icons.Default.FitnessCenter,
                actionText = "Learn More",
                onClick = { }
            )
        }
    }
} 
