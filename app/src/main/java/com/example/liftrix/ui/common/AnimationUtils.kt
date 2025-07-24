package com.example.liftrix.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.example.liftrix.ui.theme.LiftrixAnimations

/**
 * Reusable animation utilities for Liftrix
 * Provides common animation patterns with consistent timing and feel
 * Enhanced with athletic motion and weight-shifting physics
 */
object LiftrixAnimationUtils {
    
    /**
     * Creates a micro-interaction scale effect for pressable components
     * Scales down slightly when pressed for tactile feedback
     * 
     * Task ANIM-001: Updated default scale to match task specification (0.98x)
     */
    @Composable
    fun Modifier.pressScale(
        pressedScale: Float = 0.98f, // Task ANIM-001: Updated from 0.95f to 0.98f per specification
        unpressedScale: Float = 1.0f,
        interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
    ): Modifier = composed {
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) pressedScale else unpressedScale,
            animationSpec = LiftrixAnimations.microInteractionSpec,
            label = "pressScale"
        )
        
        this.scale(scale)
    }
    
    /**
     * Athletic press scale with natural weight-shifting feel
     * Uses athletic spring physics for confident motion
     * 
     * Task ANIM-001: Updated to align with task specification scale factor
     */
    @Composable
    fun Modifier.athleticPressScale(
        pressedScale: Float = 0.98f, // Task ANIM-001: Updated from 0.96f to 0.98f for consistency
        unpressedScale: Float = 1.0f,
        interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
    ): Modifier = composed {
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) pressedScale else unpressedScale,
            animationSpec = LiftrixAnimations.LiftrixSpring,
            label = "athleticPressScale"
        )
        
        this.scale(scale)
    }
    
    /**
     * Creates a glow effect that appears on press
     * Uses brand colors for consistent visual feedback
     */
    @Composable
    fun Modifier.pressGlow(
        glowColor: Color = MaterialTheme.colorScheme.primary,
        maxAlpha: Float = 0.3f,
        interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
    ): Modifier = composed {
        val isPressed by interactionSource.collectIsPressedAsState()
        val alpha by animateFloatAsState(
            targetValue = if (isPressed) maxAlpha else 0f,
            animationSpec = LiftrixAnimations.microInteractionSpec,
            label = "pressGlow"
        )
        
        this.graphicsLayer {
            shadowElevation = if (isPressed) 8f else 0f
            ambientShadowColor = glowColor.copy(alpha = alpha)
            spotShadowColor = glowColor.copy(alpha = alpha)
        }
    }
    
    /**
     * Athletic glow effect with enhanced brand color integration
     * Provides stronger visual feedback for athletic confidence
     */
    @Composable
    fun Modifier.athleticPressGlow(
        glowColor: Color = MaterialTheme.colorScheme.primary,
        maxAlpha: Float = 0.4f,
        maxElevation: Float = 12f,
        interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
    ): Modifier = composed {
        val isPressed by interactionSource.collectIsPressedAsState()
        val alpha by animateFloatAsState(
            targetValue = if (isPressed) maxAlpha else 0f,
            animationSpec = LiftrixAnimations.athleticMicroSpring,
            label = "athleticPressGlow"
        )
        val elevation by animateFloatAsState(
            targetValue = if (isPressed) maxElevation else 0f,
            animationSpec = LiftrixAnimations.athleticMicroSpring,
            label = "athleticPressElevation"
        )
        
        this.graphicsLayer {
            shadowElevation = elevation
            ambientShadowColor = glowColor.copy(alpha = alpha)
            spotShadowColor = glowColor.copy(alpha = alpha)
        }
    }
    
    /**
     * Combines press scale and haptic feedback for complete interaction
     * 
     * Task ANIM-001: Updated scale factor to match task specification
     */
    @Composable
    fun Modifier.pressInteraction(
        pressedScale: Float = 0.98f, // Task ANIM-001: Updated from 0.95f to 0.98f
        hapticEnabled: Boolean = true,
        interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
    ): Modifier = composed {
        val hapticFeedback = LocalHapticFeedback.current
        val isPressed by interactionSource.collectIsPressedAsState()
        
        // Trigger haptic feedback on press
        if (isPressed && hapticEnabled) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        
        this.pressScale(pressedScale, interactionSource = interactionSource)
    }
    
    /**
     * Athletic press interaction with enhanced haptic feedback
     * Combines athletic scaling with stronger tactile response
     * 
     * Task ANIM-001: Updated scale factor for consistency
     */
    @Composable
    fun Modifier.athleticPressInteraction(
        pressedScale: Float = 0.98f, // Task ANIM-001: Updated from 0.96f to 0.98f
        hapticEnabled: Boolean = true,
        strongHaptic: Boolean = false,
        interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
    ): Modifier = composed {
        val hapticFeedback = LocalHapticFeedback.current
        val isPressed by interactionSource.collectIsPressedAsState()
        
        // Trigger haptic feedback on press with athletic intensity
        if (isPressed && hapticEnabled) {
            if (strongHaptic) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            } else {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
        
        this.athleticPressScale(pressedScale, interactionSource = interactionSource)
    }
    
    /**
     * Creates a breathing/pulse animation for attention-drawing elements
     */
    @Composable
    fun Modifier.pulseAnimation(
        minScale: Float = 0.95f,
        maxScale: Float = 1.05f,
        enabled: Boolean = true
    ): Modifier = composed {
        if (enabled) {
            val scale by animateFloatAsState(
                targetValue = maxScale,
                animationSpec = LiftrixAnimations.pulseAnimation,
                label = "pulseAnimation"
            )
            this.scale(scale)
        } else {
            this
        }
    }
    
    /**
     * Athletic pulse animation with confident rhythm
     * Matches athletic branding with stronger presence
     */
    @Composable
    fun Modifier.athleticPulseAnimation(
        minScale: Float = 0.92f,
        maxScale: Float = 1.08f,
        enabled: Boolean = true
    ): Modifier = composed {
        if (enabled) {
            val scale by animateFloatAsState(
                targetValue = maxScale,
                animationSpec = LiftrixAnimations.athleticPulseAnimation,
                label = "athleticPulseAnimation"
            )
            this.scale(scale)
        } else {
            this
        }
    }
    
    /**
     * Creates an entrance animation for components appearing
     */
    @Composable
    fun Modifier.entranceAnimation(
        visible: Boolean,
        fromScale: Float = 0.8f,
        toScale: Float = 1.0f
    ): Modifier = composed {
        val scale by animateFloatAsState(
            targetValue = if (visible) toScale else fromScale,
            animationSpec = LiftrixAnimations.bounceEntranceSpec,
            label = "entranceAnimation"
        )
        val alpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = LiftrixAnimations.fastTransitionSpec,
            label = "entranceAlpha"
        )
        
        this
            .scale(scale)
            .graphicsLayer { this.alpha = alpha }
    }
    
    /**
     * Athletic entrance animation with confident motion
     * Natural weight-shifting feel with controlled overshoot
     */
    @Composable
    fun Modifier.athleticEntranceAnimation(
        visible: Boolean,
        fromScale: Float = 0.85f,
        toScale: Float = 1.0f
    ): Modifier = composed {
        val scale by animateFloatAsState(
            targetValue = if (visible) toScale else fromScale,
            animationSpec = LiftrixAnimations.athleticEntranceSpec,
            label = "athleticEntranceAnimation"
        )
        val alpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = LiftrixAnimations.fastTransitionSpec,
            label = "athleticEntranceAlpha"
        )
        
        this
            .scale(scale)
            .graphicsLayer { this.alpha = alpha }
    }
    
    /**
     * Creates a shimmer loading animation
     */
    @Composable
    fun Modifier.shimmerLoading(
        isLoading: Boolean,
        shimmerColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    ): Modifier = composed {
        if (isLoading) {
            val alpha by animateFloatAsState(
                targetValue = 1f,
                animationSpec = LiftrixAnimations.shimmerAnimation,
                label = "shimmerLoading"
            )
            this.graphicsLayer {
                this.alpha = alpha
            }
        } else {
            this
        }
    }
    
    /**
     * Creates a progress ring rotation animation
     */
    @Composable
    fun progressRotation(isAnimating: Boolean): Float {
        return if (isAnimating) {
            val rotation by animateFloatAsState(
                targetValue = 360f,
                animationSpec = LiftrixAnimations.progressRingAnimation,
                label = "progressRotation"
            )
            rotation
        } else {
            0f
        }
    }
    
    /**
     * Athletic progress rotation with weight-shifting feel
     * Smooth completion feedback with natural motion
     */
    @Composable
    fun athleticProgressRotation(isAnimating: Boolean): Float {
        return if (isAnimating) {
            val rotation by animateFloatAsState(
                targetValue = 360f,
                animationSpec = LiftrixAnimations.athleticProgressRingAnimation,
                label = "athleticProgressRotation"
            )
            rotation
        } else {
            0f
        }
    }
    
    /**
     * Creates an animated color transition
     */
    @Composable
    fun animatedBrandColor(
        targetColor: Color,
        label: String = "brandColor"
    ): Color {
        return animateColorAsState(
            targetValue = targetColor,
            animationSpec = tween(
                durationMillis = LiftrixAnimations.STANDARD,
                easing = FastOutSlowInEasing
            ),
            label = label
        ).value
    }
    
    /**
     * Creates an animated integer value for counters and progress
     */
    @Composable
    fun animatedProgress(
        targetValue: Int,
        label: String = "progress"
    ): Int {
        return animateIntAsState(
            targetValue = targetValue,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = label
        ).value
    }
    
    /**
     * Athletic animated progress with weight-shifting feel
     * Uses athletic spring physics for satisfying progress feedback
     */
    @Composable
    fun athleticAnimatedProgress(
        targetValue: Int,
        label: String = "athleticProgress"
    ): Int {
        return animateIntAsState(
            targetValue = targetValue,
            animationSpec = LiftrixAnimations.athleticProgressSpringInt,
            label = label
        ).value
    }
    
    /**
     * Athletic animated float progress for smooth value transitions
     */
    @Composable
    fun athleticAnimatedFloatProgress(
        targetValue: Float,
        label: String = "athleticFloatProgress"
    ): Float {
        return animateFloatAsState(
            targetValue = targetValue,
            animationSpec = LiftrixAnimations.athleticProgressFillSpec,
            label = label
        ).value
    }
}

/**
 * Athletic Button Press Composable
 * Provides weight-shifting feel with spring physics as specified in task example
 */
@Composable
fun AthleticButtonPress(
    pressed: Boolean,
    content: @Composable () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f, // Task ANIM-001: Updated from 0.96f to 0.98f
        animationSpec = LiftrixAnimations.LiftrixSpring,
        label = "athleticButtonPress"
    )
    Box(modifier = Modifier.scale(scale)) { 
        content() 
    }
}

/**
 * Enhanced Athletic Button Press with additional visual feedback
 * Combines scaling with glow effects for complete athletic interaction
 */
@Composable
fun EnhancedAthleticButtonPress(
    pressed: Boolean,
    glowEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f, // Task ANIM-001: Updated from 0.96f to 0.98f
        animationSpec = LiftrixAnimations.LiftrixSpring,
        label = "enhancedAthleticButtonPress"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (pressed && glowEnabled) 0.4f else 0f,
        animationSpec = LiftrixAnimations.athleticMicroSpring,
        label = "enhancedAthleticButtonGlow"
    )
    
    Box(
        modifier = Modifier
            .scale(scale)
            .graphicsLayer {
                if (glowEnabled) {
                    shadowElevation = if (pressed) 12f else 0f
                    ambientShadowColor = Color.White.copy(alpha = glowAlpha)
                    spotShadowColor = Color.White.copy(alpha = glowAlpha)
                }
            }
    ) { 
        content() 
    }
}

/**
 * Extension functions for common animation patterns
 */

/**
 * Simple press scale extension
 */
@Composable
fun Modifier.pressScale(): Modifier = LiftrixAnimationUtils.run {
    pressScale()
}

/**
 * Athletic press scale extension with weight-shifting feel
 */
@Composable
fun Modifier.athleticPressScale(): Modifier = LiftrixAnimationUtils.run {
    athleticPressScale()
}

/**
 * Full press interaction with haptics
 */
@Composable
fun Modifier.pressInteraction(): Modifier = LiftrixAnimationUtils.run {
    pressInteraction()
}

/**
 * Athletic press interaction with enhanced feedback
 */
@Composable
fun Modifier.athleticPressInteraction(): Modifier = LiftrixAnimationUtils.run {
    athleticPressInteraction()
}

/**
 * Entrance animation extension
 */
@Composable
fun Modifier.entranceAnimation(visible: Boolean): Modifier = LiftrixAnimationUtils.run {
    entranceAnimation(visible)
}

/**
 * Athletic entrance animation extension
 */
@Composable
fun Modifier.athleticEntranceAnimation(visible: Boolean): Modifier = LiftrixAnimationUtils.run {
    athleticEntranceAnimation(visible)
} 