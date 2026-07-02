package com.example.liftrix.ui.workout.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import com.example.liftrix.ui.accessibility.AccessibilityEnhancements.enhancedAccessibilitySemantics
import com.example.liftrix.ui.accessibility.AccessibilityEnhancements.ensureWcagTouchTarget
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.theme.LiftrixColorsV2

/**
 * Modern Action Button System
 * 
 * Consistent button hierarchy following Material 3 design principles with Liftrix styling.
 * Provides three distinct button variants with clear visual hierarchy:
 * 
 * 1. PrimaryActionButton - Filled tonal style for primary actions
 * 2. SecondaryActionButton - Outlined style for secondary actions  
 * 3. TertiaryActionButton - Text-only style for tertiary actions
 * 
 * All buttons feature:
 * - 20dp corner radius (pill shape) for modern appearance
 * - Minimum 48dp height for WCAG 2.1 AA accessibility compliance
 * - Haptic feedback on all interactions
 * - Persian Green and Tiffany Blue color hierarchy from 5-color palette
 * - Semantic accessibility support
 */

/**
 * Primary Action Button
 * 
 * Filled tonal button for primary actions like "Save", "Create", "Start Workout".
 * Uses Persian Green (#339989) with filled tonal style for brand consistency.
 * 
 * @param text The button text label
 * @param onClick Callback invoked when the button is clicked
 * @param modifier Modifier for customizing the button's layout and behavior
 * @param enabled Whether the button is enabled and interactive
 * @param leadingIcon Optional icon displayed to the left of the text
 */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val hapticFeedback = LocalHapticFeedback.current
    val shape = RoundedCornerShape(20.dp)
    
    Button(
        onClick = {
            if (enabled) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
        },
        enabled = enabled,
        modifier = modifier
            .heightIn(min = LiftrixSpacing.touchTarget)
            .ensureWcagTouchTarget()
            .enhancedAccessibilitySemantics(
                description = generateButtonContentDescription(text, "Primary", enabled, hasIcon = leadingIcon != null),
                role = Role.Button,
                stateDescription = if (enabled) "Primary action available" else "Primary action disabled",
                isEnabled = enabled
            ),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = LiftrixColorsV2.Teal,
            contentColor = Color.White,
            disabledContainerColor = LiftrixColorsV2.Teal.copy(alpha = 0.38f),
            disabledContentColor = Color.White.copy(alpha = 0.38f)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            leadingIcon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/**
 * Secondary Action Button
 * 
 * Outlined button for secondary actions like "Edit", "Cancel", "View Details".
 * Uses Tiffany Blue (#7DE2D1) with outlined style for supporting actions.
 * 
 * @param text The button text label
 * @param onClick Callback invoked when the button is clicked
 * @param modifier Modifier for customizing the button's layout and behavior
 * @param enabled Whether the button is enabled and interactive
 * @param leadingIcon Optional icon displayed to the left of the text
 */
@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val hapticFeedback = LocalHapticFeedback.current
    val shape = RoundedCornerShape(20.dp)
    
    OutlinedButton(
        onClick = {
            if (enabled) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
        },
        enabled = enabled,
        modifier = modifier
            .heightIn(min = LiftrixSpacing.touchTarget)
            .ensureWcagTouchTarget()
            .enhancedAccessibilitySemantics(
                description = generateButtonContentDescription(text, "Secondary", enabled, hasIcon = leadingIcon != null),
                role = Role.Button,
                stateDescription = if (enabled) "Secondary action available" else "Secondary action disabled",
                isEnabled = enabled
            ),
        shape = shape,
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) LiftrixColorsV2.Teal 
                   else LiftrixColorsV2.Teal.copy(alpha = 0.38f)
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = LiftrixColorsV2.Teal,
            disabledContentColor = LiftrixColorsV2.Teal.copy(alpha = 0.38f)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            leadingIcon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/**
 * Tertiary Action Button
 * 
 * Text-only button for tertiary actions like "Skip", "Learn More", "Advanced Options".
 * Uses Persian Green with reduced alpha for minimal visual impact.
 * 
 * @param text The button text label
 * @param onClick Callback invoked when the button is clicked
 * @param modifier Modifier for customizing the button's layout and behavior
 * @param enabled Whether the button is enabled and interactive
 * @param leadingIcon Optional icon displayed to the left of the text
 */
@Composable
fun TertiaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val hapticFeedback = LocalHapticFeedback.current
    val shape = RoundedCornerShape(20.dp)
    
    TextButton(
        onClick = {
            if (enabled) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
        },
        enabled = enabled,
        modifier = modifier
            .heightIn(min = LiftrixSpacing.touchTarget)
            .ensureWcagTouchTarget()
            .enhancedAccessibilitySemantics(
                description = generateButtonContentDescription(text, "Tertiary", enabled, hasIcon = leadingIcon != null),
                role = Role.Button,
                stateDescription = if (enabled) "Tertiary action available" else "Tertiary action disabled",
                isEnabled = enabled
            ),
        shape = shape,
        colors = ButtonDefaults.textButtonColors(
            contentColor = LiftrixColorsV2.Teal.copy(alpha = 0.8f),
            disabledContentColor = LiftrixColorsV2.Teal.copy(alpha = 0.38f)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            leadingIcon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/**
 * Enhanced accessibility content description generator for action buttons.
 * 
 * @param text Button text label
 * @param buttonType Type of button (Primary, Secondary, Tertiary)
 * @param enabled Whether the button is enabled
 * @param hasIcon Whether the button includes an icon
 * @return Comprehensive content description for screen readers
 */
private fun generateButtonContentDescription(
    text: String,
    buttonType: String,
    enabled: Boolean,
    hasIcon: Boolean = false
): String {
    return buildString {
        append(text)
        append(" ")
        append(buttonType.lowercase())
        append(" button")
        
        if (hasIcon) {
            append(" with icon")
        }
        
        if (!enabled) {
            append(", disabled")
        }
        
        append(". Double tap to activate")
    }
}

/**
 * Preview functions for development and testing
 * These previews demonstrate proper usage and visual hierarchy
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernActionButtonPreview() {
    MaterialTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            PrimaryActionButton(
                text = "Start Workout",
                onClick = { /* Primary action */ }
            )
            
            SecondaryActionButton(
                text = "Edit Workout",
                onClick = { /* Secondary action */ }
            )
            
            TertiaryActionButton(
                text = "Skip for Now",
                onClick = { /* Tertiary action */ }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernActionButtonStatesPreview() {
    MaterialTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            // Enabled state
            androidx.compose.material3.Text(
                text = "Enabled State",
                style = MaterialTheme.typography.titleMedium
            )
            
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                PrimaryActionButton(
                    text = "Primary",
                    onClick = { /* Primary action */ }
                )
                
                SecondaryActionButton(
                    text = "Secondary",
                    onClick = { /* Secondary action */ }
                )
                
                TertiaryActionButton(
                    text = "Tertiary",
                    onClick = { /* Tertiary action */ }
                )
            }
            
            // Disabled state
            androidx.compose.material3.Text(
                text = "Disabled State",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                PrimaryActionButton(
                    text = "Primary",
                    onClick = { /* Primary action */ },
                    enabled = false
                )
                
                SecondaryActionButton(
                    text = "Secondary", 
                    onClick = { /* Secondary action */ },
                    enabled = false
                )
                
                TertiaryActionButton(
                    text = "Tertiary",
                    onClick = { /* Tertiary action */ },
                    enabled = false
                )
            }
        }
    }
}
