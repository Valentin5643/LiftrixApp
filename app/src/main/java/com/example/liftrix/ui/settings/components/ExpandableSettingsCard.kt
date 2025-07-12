package com.example.liftrix.ui.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.common.AccessibilityUtils.accessibilitySemantics
import com.example.liftrix.ui.common.AccessibilityUtils.ensureMinimumTouchTarget
import com.example.liftrix.ui.common.PerformanceOptimizations
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Expandable settings card component with accordion behavior and Material3 design.
 * 
 * Provides smooth expand/collapse animations with accessibility support and proper
 * touch target sizes following WCAG 2.1 AA guidelines.
 * 
 * @param title The title text displayed in the card header
 * @param isExpanded Whether the card is currently expanded
 * @param onToggle Callback invoked when the card is tapped to toggle expansion
 * @param modifier Modifier to be applied to the card
 * @param content The content displayed when the card is expanded
 */
@Composable
fun ExpandableSettingsCard(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Performance monitoring for animation
    PerformanceOptimizations.AnimationPerformanceMonitor.MonitorAnimation(
        key = "ExpandableSettingsCard_$title"
    ) {
        // Stable callback to prevent unnecessary recompositions
        val stableOnToggle = remember(onToggle) { onToggle }
        
        // Optimized animation specs for 60fps performance
        val optimizedAnimationSpec = remember {
            tween<androidx.compose.ui.unit.IntSize>(
                durationMillis = 167, // Optimized for 60fps (10 frames at 60fps)
                easing = FastOutSlowInEasing
            )
        }
        
        Card(
            modifier = modifier
                .fillMaxWidth()
                .accessibilitySemantics(
                    description = if (isExpanded) "$title settings expanded" else "$title settings collapsed",
                    role = Role.Button,
                    stateDescription = if (isExpanded) "Expanded" else "Collapsed"
                ),
            onClick = stableOnToggle,
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            // Track recomposition for performance analysis
            PerformanceOptimizations.MemoryEfficientComponents.TrackRecomposition(
                key = "ExpandableCardContent_$title"
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Card Header with Title and Expand/Collapse Icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        IconButton(
                            onClick = stableOnToggle,
                            modifier = Modifier
                                .ensureMinimumTouchTarget()
                                .accessibilitySemantics(
                                    description = if (isExpanded) "Collapse $title" else "Expand $title",
                                    role = Role.Button
                                )
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    // Expandable Content with Optimized Animation
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandVertically(animationSpec = optimizedAnimationSpec),
                        exit = shrinkVertically(animationSpec = optimizedAnimationSpec)
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            content()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Preview for ExpandableSettingsCard in collapsed state
 */
@Preview(showBackground = true)
@Composable
private fun ExpandableSettingsCardCollapsedPreview() {
    LiftrixTheme {
        ExpandableSettingsCard(
            title = "General Settings",
            isExpanded = false,
            onToggle = { }
        ) {
            Text(
                text = "Sample content that would be shown when expanded",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Preview for ExpandableSettingsCard in expanded state
 */
@Preview(showBackground = true)
@Composable
private fun ExpandableSettingsCardExpandedPreview() {
    LiftrixTheme {
        ExpandableSettingsCard(
            title = "General Settings",
            isExpanded = true,
            onToggle = { }
        ) {
            Column {
                Text(
                    text = "Dark Mode",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Language",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Preview for multiple ExpandableSettingsCard instances showing accordion behavior
 */
@Preview(showBackground = true)
@Composable
private fun ExpandableSettingsCardAccordionPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExpandableSettingsCard(
                title = "General Settings",
                isExpanded = true,
                onToggle = { }
            ) {
                Text(
                    text = "Dark Mode, Notifications, Language",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            ExpandableSettingsCard(
                title = "Manage Subscription",
                isExpanded = false,
                onToggle = { }
            ) {
                Text(
                    text = "Premium features, Billing info",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            ExpandableSettingsCard(
                title = "Security",
                isExpanded = false,
                onToggle = { }
            ) {
                Text(
                    text = "Privacy settings, Data export",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}