package com.example.liftrix.ui.workout.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.liftrix.feature.workout.ui.AccessibilityUtils.ensureMinimumTouchTarget
import com.example.liftrix.feature.workout.ui.AccessibilityUtils.accessibilitySemantics
import com.example.liftrix.ui.accessibility.AccessibilityEnhancements.enhancedAccessibilitySemantics
import com.example.liftrix.ui.icons.LiftrixIcon
import com.example.liftrix.ui.theme.LiftrixAnimations
import com.example.liftrix.ui.theme.LiftrixSpacing
import androidx.compose.runtime.SideEffect
import timber.log.Timber
import com.example.liftrix.ui.performance.PerformanceTracker
import com.example.liftrix.core.extensions.rememberDerivedStateOf

private const val DEBUG_RECOMPOSITION_LOGS = false

/**
 * Unified Workout Card Component
 * 
 * Reusable Material 3 card component with consistent 12dp corners, 2dp elevation, 
 * semantic spacing, and shared visual language for all workout screens.
 * 
 * Features:
 * - 12dp corner radius for modern appearance
 * - 2dp elevation with 4dp pressed state
 * - 12dp internal padding (LiftrixSpacing.cardPadding)
 * - WCAG 2.1 AA accessibility compliance
 * - Semantic color usage from MaterialTheme
 * - Reusable content slots for title, content, and actions
 * 
 * @param title The primary text displayed in the card header
 * @param subtitle Optional secondary text shown below the title
 * @param modifier Modifier for customizing the card's layout and behavior
 * @param onClick Optional click handler. When null, the card is not clickable
 * @param leadingIcon Optional icon displayed to the left of the title for semantic meaning
 * @param actions Composable slot for action buttons displayed at the bottom right
 * @param content Main content area of the card
 */
@Composable
fun UnifiedWorkoutCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = LiftrixAnimations.fastTransitionSpec,
        label = "unifiedCardPressScale"
    )
    
    val cardModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .scale(scale) // Task ANIM-001: Apply press scale animation
            .ensureMinimumTouchTarget()
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Remove default ripple since we have custom animation
                role = Role.Button,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            .enhancedAccessibilitySemantics(
                description = generateCardContentDescription(title, subtitle, true, hasIcon = leadingIcon != null),
                role = Role.Button,
                stateDescription = "Interactive card. Double tap to activate.",
                isEnabled = true
            )
    } else {
        modifier
            .fillMaxWidth()
            .enhancedAccessibilitySemantics(
                description = generateCardContentDescription(title, subtitle, false, hasIcon = leadingIcon != null),
                stateDescription = "Information card",
                isEnabled = true
            )
    }
    
    // Task PERF-001: Add performance tracking for card rendering
    PerformanceTracker(
        componentId = "UnifiedWorkoutCard_$title",
        enabled = true
    )

    Card(
        modifier = cardModifier,
        shape = shape,
        elevation = elevation,
        colors = colors,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = LiftrixSpacing.cardPadding, // 12dp horizontal padding
                vertical = LiftrixSpacing.cardPadding    // 12dp vertical padding
            )
        ) {
            // Header with optional icon and title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
            ) {
                // Leading icon (optional)
                leadingIcon?.let { icon ->
                    LiftrixIcon(
                        icon = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(LiftrixSpacing.touchTarget) // 24dp from tokens
                    )
                }
                
                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Subtitle (optional)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            // Content spacing
            Spacer(modifier = Modifier.height(LiftrixSpacing.elementSpacing)) // 8dp spacing
            
            // Main content
            content()
            
            // Actions row (if actions are provided)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = LiftrixSpacing.elementSpacing), // 8dp top spacing for actions
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions()
            }
        }
    }
}

/**
 * Compact variant of UnifiedWorkoutCard for smaller content areas
 * Uses reduced spacing while maintaining the same visual design language
 */
@Composable
fun CompactUnifiedWorkoutCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    // Task PERF-001: Add performance monitoring for compact variant
    if (DEBUG_RECOMPOSITION_LOGS) {
        SideEffect {
            Timber.d("Recomposition: CompactUnifiedWorkoutCard_$title")
        }
    }
    
    // Task PERF-001: Style calculations for performance
    val shape = RoundedCornerShape(12.dp)
    val elevation = CardDefaults.cardElevation(defaultElevation = 1.dp) // Reduced elevation for compact version
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
    
    // Task ANIM-001: Add press animation to compact variant as well
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = LiftrixAnimations.fastTransitionSpec,
        label = "compactCardPressScale"
    )
    
    val cardModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .scale(scale) // Task ANIM-001: Apply press scale animation
            .ensureMinimumTouchTarget()
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Remove default ripple since we have custom animation
                role = Role.Button,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            .enhancedAccessibilitySemantics(
                description = generateCardContentDescription(title, subtitle, true, isCompact = true, hasIcon = leadingIcon != null),
                role = Role.Button,
                stateDescription = "Compact interactive card. Double tap to activate.",
                isEnabled = true
            )
    } else {
        modifier
            .fillMaxWidth()
            .enhancedAccessibilitySemantics(
                description = generateCardContentDescription(title, subtitle, false, isCompact = true, hasIcon = leadingIcon != null),
                stateDescription = "Compact information card",
                isEnabled = true
            )
    }

    Card(
        modifier = cardModifier,
        shape = shape,
        elevation = elevation,
        colors = colors
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = LiftrixSpacing.elementPaddingLarge, // 12dp horizontal padding
                vertical = LiftrixSpacing.elementSpacing          // 8dp vertical padding (reduced)
            )
        ) {
            // Header with optional icon and title (compact version)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementPaddingSmall)
            ) {
                // Leading icon (optional, smaller for compact)
                leadingIcon?.let { icon ->
                    LiftrixIcon(
                        icon = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        size = 20.dp // Smaller icon for compact version
                    )
                }
                
                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium, // Smaller title for compact version
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Subtitle (optional)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall, // Smaller subtitle for compact version
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp) // Reduced spacing for compact
                )
            }
            
            // Content spacing (reduced for compact)
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(LiftrixSpacing.elementPaddingSmall)) // 4dp spacing
            }
            
            // Main content
            content()
            
            // Actions row (if actions are provided)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = LiftrixSpacing.elementPaddingSmall), // 4dp top spacing (reduced)
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions()
            }
        }
    }
}

/**
 * Enhanced accessibility content description generator for workout cards.
 * 
 * @param title Card title
 * @param subtitle Optional subtitle with additional context
 * @param isClickable Whether the card responds to click interactions
 * @param isCompact Whether this is the compact variant of the card
 * @return Comprehensive content description for screen readers
 */
private fun generateCardContentDescription(
    title: String,
    subtitle: String?,
    isClickable: Boolean,
    isCompact: Boolean = false,
    hasIcon: Boolean = false
): String {
    return buildString {
        if (isCompact) {
            append("Compact ")
        }
        append("workout card")
        if (hasIcon) {
            append(" with icon")
        }
        append(": ")
        append(title)
        
        if (subtitle != null) {
            append(". ")
            append(subtitle)
        }
        
        if (isClickable) {
            append(". Double tap to open")
        }
    }
}

/**
 * Preview functions for development and testing
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnifiedWorkoutCardPreview() {
    MaterialTheme {
        UnifiedWorkoutCard(
            title = "Push Day Workout",
            subtitle = "6 exercises"
        ) {
            Text("Workout description and details go here")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnifiedWorkoutCardWithActionsPreview() {
    MaterialTheme {
        UnifiedWorkoutCard(
            title = "Pull Day Workout",
            subtitle = "5 exercises",
            onClick = { /* Handle card click */ },
            actions = {
                TextButton(onClick = { /* Edit action */ }) {
                    Text("Edit")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { /* Start action */ }) {
                    Text("Start")
                }
            }
        ) {
            Text("Ready to start your workout")
        }
    }
}
