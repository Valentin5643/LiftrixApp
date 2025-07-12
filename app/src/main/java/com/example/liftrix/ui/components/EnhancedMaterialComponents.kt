package com.example.liftrix.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.common.enhancedTouchTarget
import com.example.liftrix.ui.theme.LiftrixTokens

/**
 * Enhanced Material 3 card with proper accessibility and design tokens.
 * 
 * Provides consistent elevation, corner radius, and accessibility support
 * following Material Design 3 principles and WCAG 2.1 AA guidelines.
 * 
 * @param onClick Optional click action (makes card clickable)
 * @param modifier Modifier for styling
 * @param enabled Whether the card is enabled (for clickable cards)
 * @param elevation Elevation level using design tokens
 * @param contentDescription Accessibility description for clickable cards
 * @param content Card content
 */
@Composable
fun EnhancedCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    elevation: Dp = LiftrixTokens.Elevation.Level1,
    contentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier
            .enhancedTouchTarget()
            .semantics {
                contentDescription?.let {
                    this.contentDescription = it
                }
                role = Role.Button
                if (!enabled) {
                    stateDescription = "Disabled"
                }
            }
    } else {
        modifier
    }
    
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            enabled = enabled,
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            shape = RoundedCornerShape(LiftrixTokens.CornerRadius.Medium),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = MaterialTheme.colorScheme.surface.copy(
                    alpha = LiftrixTokens.Opacity.Disabled
                ),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = LiftrixTokens.Opacity.Disabled
                )
            )
        ) {
            Column(
                modifier = Modifier.padding(LiftrixTokens.Spacing.Large),
                content = content
            )
        }
    } else {
        Card(
            modifier = cardModifier,
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            shape = RoundedCornerShape(LiftrixTokens.CornerRadius.Medium),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Column(
                modifier = Modifier.padding(LiftrixTokens.Spacing.Large),
                content = content
            )
        }
    }
}

/**
 * Enhanced statistics card for displaying workout metrics.
 * 
 * Optimized for performance with stable data classes and proper accessibility.
 * Uses design tokens for consistent spacing and typography.
 * 
 * @param statData Statistics data to display
 * @param modifier Modifier for styling
 * @param onClick Optional click action
 */
@Composable
fun EnhancedStatCard(
    statData: StatCardData,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    EnhancedCard(
        onClick = onClick,
        modifier = modifier,
        contentDescription = "${statData.label}: ${statData.value} ${statData.unit}",
        elevation = LiftrixTokens.Elevation.Level2
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LiftrixTokens.Spacing.Small)
        ) {
            statData.icon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(LiftrixTokens.TouchTarget.IconLarge),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Text(
                text = statData.value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            statData.unit?.let { unit ->
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            
            Text(
                text = statData.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Enhanced action card with icon, title, description, and action button.
 * 
 * Perfect for feature cards, onboarding steps, or informational content
 * with actionable elements. Includes proper accessibility and touch targets.
 * 
 * @param actionCardData Action card data
 * @param modifier Modifier for styling
 */
@Composable
fun EnhancedActionCard(
    actionCardData: ActionCardData,
    modifier: Modifier = Modifier
) {
    EnhancedCard(
        modifier = modifier,
        elevation = LiftrixTokens.Elevation.Level1
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LiftrixTokens.Spacing.Large),
            verticalAlignment = Alignment.Top
        ) {
            actionCardData.icon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(LiftrixTokens.TouchTarget.IconLarge),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LiftrixTokens.Spacing.Medium)
            ) {
                Text(
                    text = actionCardData.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                
                Text(
                    text = actionCardData.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                )
                
                actionCardData.actionButton?.let { buttonData ->
                    Button(
                        onClick = buttonData.onClick,
                        modifier = Modifier
                            .enhancedTouchTarget()
                            .semantics {
                                contentDescription = buttonData.label
                                role = Role.Button
                            },
                        enabled = buttonData.enabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = buttonData.label,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

/**
 * Enhanced chip component with proper touch targets and accessibility.
 * 
 * Provides consistent styling for filter chips, suggestion chips, etc.
 * with guaranteed minimum touch target size.
 * 
 * @param chipData Chip data including label and state
 * @param modifier Modifier for styling
 */
@Composable
fun EnhancedChip(
    chipData: ChipData,
    modifier: Modifier = Modifier
) {
    FilterChip(
        onClick = chipData.onClick,
        label = {
            Text(
                text = chipData.label,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        selected = chipData.selected,
        modifier = modifier
            .enhancedTouchTarget()
            .semantics {
                contentDescription = if (chipData.selected) {
                    "${chipData.label}, selected"
                } else {
                    chipData.label
                }
                role = Role.Button
                stateDescription = if (chipData.selected) "Selected" else "Not selected"
            },
        leadingIcon = chipData.icon?.let { icon ->
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(LiftrixTokens.TouchTarget.IconSmall)
                )
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

/**
 * Stable data classes for component state management.
 * Helps prevent unnecessary recompositions in Compose.
 */
@Stable
data class ActionButtonData(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true
)

@Stable
data class ChipData(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val icon: ImageVector? = null
)

/**
 * Data class for stat card display
 */
@Stable
data class StatCardData(
    val label: String,
    val value: String,
    val unit: String? = null,
    val icon: ImageVector? = null
)

/**
 * Data class for action card display
 */
@Stable
data class ActionCardData(
    val title: String,
    val description: String,
    val icon: ImageVector? = null,
    val actionText: String? = null,
    val onAction: (() -> Unit)? = null,
    val actionButton: ButtonData? = null
)

/**
 * Data class for button configuration
 */
@Stable
data class ButtonData(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true
) 