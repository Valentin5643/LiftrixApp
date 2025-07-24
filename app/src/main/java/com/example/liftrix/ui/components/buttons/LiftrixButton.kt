package com.example.liftrix.ui.components.buttons

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixAnimations
import com.example.liftrix.ui.theme.LiftrixColors

/**
 * Enhanced button variants with athletic branding and micro-interactions.
 * Each variant uses specific brand colors and styling for different use cases.
 */
enum class ButtonVariant {
    /** Primary actions using Teal brand color - for main CTAs and important actions */
    Primary,
    
    /** Secondary actions using Indigo brand color - for secondary CTAs and info actions */
    Secondary,
    
    /** Accent actions using Coral brand color - for accent elements and warnings */
    Accent,
    
    /** Outlined variant with brand colors - for secondary actions with less emphasis */
    Outlined,
    
    /** Text variant with brand colors - for tertiary actions and navigation */
    Text
}

/**
 * Enhanced button component with athletic branding, micro-interactions, and haptic feedback.
 * 
 * @deprecated This legacy button system is deprecated. Use ModernActionButton hierarchy instead:
 * - Use PrimaryActionButton for primary actions
 * - Use SecondaryActionButton for secondary actions  
 * - Use TertiaryActionButton for tertiary actions
 * 
 * @param onClick Callback triggered when button is clicked
 * @param modifier Modifier for styling and positioning
 * @param variant Button variant determining colors and style
 * @param enabled Whether the button is enabled for interaction
 * @param interactionSource MutableInteractionSource for tracking interaction states
 * @param content Button content as a composable lambda
 */
@Deprecated(
    message = "Use ModernActionButton hierarchy instead (PrimaryActionButton, SecondaryActionButton, TertiaryActionButton)",
    replaceWith = ReplaceWith("PrimaryActionButton"),
    level = DeprecationLevel.WARNING
)
@Composable
fun LiftrixButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
) {
    // Athletic micro-interaction: scale animation on press
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = LiftrixAnimations.athleticMicroSpring,
        label = "button_scale"
    )
    
    // Haptic feedback for tactile response
    val hapticFeedback = LocalHapticFeedback.current
    
    // Enhanced onClick with haptic feedback
    val enhancedOnClick = {
        if (enabled) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        }
    }
    
    // Athletic button shape with rounded corners
    val buttonShape = RoundedCornerShape(16.dp)
    
    // Apply micro-scaling animation
    val scaledModifier = modifier.scale(scale)
    
    // Render appropriate button variant
    when (variant) {
        ButtonVariant.Primary -> {
            Button(
                onClick = enhancedOnClick,
                modifier = scaledModifier,
                enabled = enabled,
                shape = buttonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LiftrixColors.Primary,
                    contentColor = LiftrixColors.OnPrimary,
                    disabledContainerColor = LiftrixColors.Primary.copy(alpha = 0.38f),
                    disabledContentColor = LiftrixColors.OnPrimary.copy(alpha = 0.38f)
                ),
                interactionSource = interactionSource,
                content = content
            )
        }
        
        ButtonVariant.Secondary -> {
            Button(
                onClick = enhancedOnClick,
                modifier = scaledModifier,
                enabled = enabled,
                shape = buttonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LiftrixColors.Secondary,
                    contentColor = LiftrixColors.OnSecondary,
                    disabledContainerColor = LiftrixColors.Secondary.copy(alpha = 0.38f),
                    disabledContentColor = LiftrixColors.OnSecondary.copy(alpha = 0.38f)
                ),
                interactionSource = interactionSource,
                content = content
            )
        }
        
        ButtonVariant.Accent -> {
            Button(
                onClick = enhancedOnClick,
                modifier = scaledModifier,
                enabled = enabled,
                shape = buttonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LiftrixColors.TiffanyBlue,
                    contentColor = LiftrixColors.Night,
                    disabledContainerColor = LiftrixColors.TiffanyBlue.copy(alpha = 0.38f),
                    disabledContentColor = LiftrixColors.Night.copy(alpha = 0.38f)
                ),
                interactionSource = interactionSource,
                content = content
            )
        }
        
        ButtonVariant.Outlined -> {
            OutlinedButton(
                onClick = enhancedOnClick,
                modifier = scaledModifier,
                enabled = enabled,
                shape = buttonShape,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = LiftrixColors.Primary,
                    disabledContentColor = LiftrixColors.Primary.copy(alpha = 0.38f)
                ),
                interactionSource = interactionSource,
                content = content
            )
        }
        
        ButtonVariant.Text -> {
            TextButton(
                onClick = enhancedOnClick,
                modifier = scaledModifier,
                enabled = enabled,
                shape = buttonShape,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = LiftrixColors.Primary,
                    disabledContentColor = LiftrixColors.Primary.copy(alpha = 0.38f)
                ),
                interactionSource = interactionSource,
                content = content
            )
        }
    }
}

/**
 * Extension function to get button colors for a specific variant.
 * Useful for custom button implementations that need variant-specific colors.
 * 
 * @return ButtonColors configured for the specified variant
 */
@Composable
fun ButtonVariant.colors(): ButtonColors {
    return when (this) {
        ButtonVariant.Primary -> ButtonDefaults.buttonColors(
            containerColor = LiftrixColors.Primary,
            contentColor = LiftrixColors.OnPrimary,
            disabledContainerColor = LiftrixColors.Primary.copy(alpha = 0.38f),
            disabledContentColor = LiftrixColors.OnPrimary.copy(alpha = 0.38f)
        )
        
        ButtonVariant.Secondary -> ButtonDefaults.buttonColors(
            containerColor = LiftrixColors.Secondary,
            contentColor = LiftrixColors.OnSecondary,
            disabledContainerColor = LiftrixColors.Secondary.copy(alpha = 0.38f),
            disabledContentColor = LiftrixColors.OnSecondary.copy(alpha = 0.38f)
        )
        
        ButtonVariant.Accent -> ButtonDefaults.buttonColors(
            containerColor = LiftrixColors.TiffanyBlue,
            contentColor = LiftrixColors.Night,
            disabledContainerColor = LiftrixColors.TiffanyBlue.copy(alpha = 0.38f),
            disabledContentColor = LiftrixColors.Night.copy(alpha = 0.38f)
        )
        
        ButtonVariant.Outlined -> ButtonDefaults.outlinedButtonColors(
            contentColor = LiftrixColors.Primary,
            disabledContentColor = LiftrixColors.Primary.copy(alpha = 0.38f)
        )
        
        ButtonVariant.Text -> ButtonDefaults.textButtonColors(
            contentColor = LiftrixColors.Primary,
            disabledContentColor = LiftrixColors.Primary.copy(alpha = 0.38f)
        )
    }
}

/**
 * Convenience function for creating a primary action button with consistent styling.
 * 
 * @deprecated Use PrimaryActionButton instead for unified design system compliance
 */
@Deprecated(
    message = "Use PrimaryActionButton instead for unified design system compliance",
    replaceWith = ReplaceWith("PrimaryActionButton"),
    level = DeprecationLevel.WARNING
)
@Composable
fun PrimaryLiftrixButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    LiftrixButton(
        onClick = onClick,
        modifier = modifier,
        variant = ButtonVariant.Primary,
        enabled = enabled,
        content = content
    )
}

/**
 * Convenience function for creating a secondary action button with consistent styling.
 * 
 * @deprecated Use SecondaryActionButton instead for unified design system compliance
 */
@Deprecated(
    message = "Use SecondaryActionButton instead for unified design system compliance",
    replaceWith = ReplaceWith("SecondaryActionButton"),
    level = DeprecationLevel.WARNING
)
@Composable
fun SecondaryLiftrixButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    LiftrixButton(
        onClick = onClick,
        modifier = modifier,
        variant = ButtonVariant.Secondary,
        enabled = enabled,
        content = content
    )
}

/**
 * Convenience function for creating an accent action button with consistent styling.
 * 
 * @deprecated Use TertiaryActionButton instead for unified design system compliance
 */
@Deprecated(
    message = "Use TertiaryActionButton instead for unified design system compliance",
    replaceWith = ReplaceWith("TertiaryActionButton"),
    level = DeprecationLevel.WARNING
)
@Composable
fun AccentLiftrixButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    LiftrixButton(
        onClick = onClick,
        modifier = modifier,
        variant = ButtonVariant.Accent,
        enabled = enabled,
        content = content
    )
}