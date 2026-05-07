package com.example.liftrix.ui.components.buttons

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixAnimations
import com.example.liftrix.ui.theme.LiftrixColors

/**
 * Athletic button press animation with micro-scaling effect.
 * Provides natural weight-shifting feel using spring physics.
 * 
 * @param pressed Whether the button is currently pressed
 * @param enabled Whether the button is enabled for interaction
 * @param content Button content to be animated
 */
@Composable
fun AthleticButtonPress(
    pressed: Boolean,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = LiftrixAnimations.athleticMicroSpring,
        label = "athletic_button_press"
    )
    
    Box(modifier = Modifier.scale(scale)) {
        content()
    }
}

/**
 * Glow-on-press effect for enhanced button feedback.
 * Creates a subtle brand color glow when button is pressed.
 * 
 * @param pressed Whether the button is currently pressed
 * @param enabled Whether the button is enabled for interaction
 * @param glowColor Color of the glow effect
 * @param modifier Modifier for styling
 * @param content Button content to be enhanced with glow
 */
@Composable
fun GlowOnPress(
    pressed: Boolean,
    enabled: Boolean = true,
    glowColor: Color = LiftrixColors.Primary,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val glowAlpha by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.2f else 0f,
        animationSpec = LiftrixAnimations.microInteractionSpec,
        label = "glow_alpha"
    )
    
    Box(modifier = modifier) {
        // Glow effect background
        if (glowAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = glowAlpha),
                                Color.Transparent
                            ),
                            radius = 120f
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
            )
        }
        
        content()
    }
}

/**
 * Enhanced button interaction wrapper that combines athletic micro-scaling,
 * glow effects, and haptic feedback for comprehensive button enhancement.
 * 
 * @param interactionSource MutableInteractionSource for tracking interaction states
 * @param enabled Whether the button is enabled for interaction
 * @param glowColor Color of the glow effect
 * @param hapticEnabled Whether to provide haptic feedback
 * @param modifier Modifier for styling
 * @param content Button content to be enhanced
 */
@Composable
fun EnhancedButtonInteraction(
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
    glowColor: Color = LiftrixColors.Primary,
    hapticEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current
    
    // Trigger haptic feedback on press
    if (isPressed && enabled && hapticEnabled) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    
    GlowOnPress(
        pressed = isPressed,
        enabled = enabled,
        glowColor = glowColor,
        modifier = modifier
    ) {
        AthleticButtonPress(
            pressed = isPressed,
            enabled = enabled
        ) {
            content()
        }
    }
}

/**
 * Micro-interaction animation for button state changes.
 * Provides subtle visual feedback for button interactions.
 * 
 * @param targetValue Target value for the animation
 * @param label Animation label for debugging
 * @return Animated float value
 */
@Composable
fun animateButtonMicroInteraction(
    targetValue: Float,
    label: String = "button_micro_interaction"
): Float {
    val animatedValue by animateFloatAsState(
        targetValue = targetValue,
        animationSpec = LiftrixAnimations.athleticMicroSpring,
        label = label
    )
    return animatedValue
}

/**
 * Color animation for button state changes.
 * Provides smooth color transitions for button interactions.
 * 
 * @param targetColor Target color for the animation
 * @param label Animation label for debugging
 * @return Animated color value
 */
@Composable
fun animateButtonColor(
    targetColor: Color,
    label: String = "button_color_animation"
): Color {
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = LiftrixAnimations.fastColorTransitionSpec,
        label = label
    )
    return animatedColor
}

/**
 * Haptic feedback utility for button interactions.
 * Provides consistent haptic feedback across all button components.
 * 
 * @param enabled Whether haptic feedback is enabled
 * @param feedbackType Type of haptic feedback to provide
 */
@Composable
fun ButtonHapticFeedback(
    enabled: Boolean = true,
    feedbackType: HapticFeedbackType = HapticFeedbackType.LongPress
) {
    val hapticFeedback = LocalHapticFeedback.current
    
    if (enabled) {
        hapticFeedback.performHapticFeedback(feedbackType)
    }
}

/**
 * Athletic button elevation animation for enhanced depth perception.
 * Creates subtle elevation changes during button interactions.
 * 
 * @param pressed Whether the button is currently pressed
 * @param enabled Whether the button is enabled for interaction
 * @return Animated elevation value in dp
 */
@Composable
fun animateAthleticElevation(
    pressed: Boolean,
    enabled: Boolean = true
): Float {
    val elevation by animateFloatAsState(
        targetValue = if (pressed && enabled) 2f else 6f,
        animationSpec = LiftrixAnimations.athleticMicroSpring,
        label = "athletic_elevation"
    )
    return elevation
}

/**
 * Brand color glow variants for different button types.
 * Provides consistent glow colors based on button variants.
 */
object ButtonGlowColors {
    val Primary = LiftrixColors.Primary
    val Secondary = LiftrixColors.Secondary
    val Accent = LiftrixColors.TiffanyBlue
    val Success = LiftrixColors.Primary
    val Warning = LiftrixColors.TiffanyBlue
    val Error = LiftrixColors.Error
}

/**
 * Athletic button interaction constants for consistent behavior.
 */
object AthleticButtonConstants {
    const val PRESS_SCALE = 0.96f
    const val NORMAL_SCALE = 1f
    const val GLOW_ALPHA_PRESSED = 0.2f
    const val GLOW_ALPHA_NORMAL = 0f
    const val ELEVATION_PRESSED = 2f
    const val ELEVATION_NORMAL = 6f
    const val CORNER_RADIUS = 16f
    const val GLOW_RADIUS = 120f
    const val GLOW_CORNER_RADIUS = 20f
} 