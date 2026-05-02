package com.example.liftrix.ui.components.animations

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixAnimations
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.LiftrixTheme
// Removed LiftrixColors.PersianGreen import - using PersianGreen directly
import kotlinx.coroutines.delay

/**
 * Completion feedback utilities for haptic feedback and visual completion effects.
 * Provides standardized feedback patterns for exercise completion throughout the app.
 */
object CompletionFeedback {
    
    /**
     * Provides haptic feedback for exercise completion with appropriate feedback type.
     * Uses LongPress haptic for satisfying tactile confirmation.
     * 
     * @param hapticFeedback HapticFeedback instance from LocalHapticFeedback.current
     */
    fun exerciseComplete(hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    
    /**
     * Provides haptic feedback for set completion with lighter feedback.
     * Uses TextHandleMove for subtle but noticeable confirmation.
     * 
     * @param hapticFeedback HapticFeedback instance from LocalHapticFeedback.current
     */
    fun setComplete(hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    
    /**
     * Provides haptic feedback for workout completion with strong confirmation.
     * Uses LongPress for significant achievement feedback.
     * 
     * @param hapticFeedback HapticFeedback instance from LocalHapticFeedback.current
     */
    fun workoutComplete(hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

/**
 * Wrapper component that provides visual completion feedback with animations.
 * Applies scale and color animations when completion state changes.
 * 
 * @param completed Whether the wrapped content is in completed state
 * @param hapticEnabled Whether to provide haptic feedback on completion
 * @param feedbackType Type of completion feedback (affects haptic strength)
 * @param onComplete Callback triggered when completion animation starts
 * @param modifier Modifier for styling
 * @param content Content to wrap with completion feedback
 */
@Composable
fun CompletionFeedback(
    completed: Boolean,
    hapticEnabled: Boolean = true,
    feedbackType: CompletionFeedbackType = CompletionFeedbackType.SET_COMPLETE,
    onComplete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    var hasTriggeredFeedback by remember { mutableStateOf(false) }
    
    // Animate scale for completion feedback
    val scale by animateFloatAsState(
        targetValue = if (completed) 1.05f else 1f,
        animationSpec = LiftrixAnimations.microInteractionSpec,
        label = "completion_feedback_scale"
    )
    
    // Trigger haptic feedback when completion state changes to true
    LaunchedEffect(completed) {
        if (completed && !hasTriggeredFeedback && hapticEnabled) {
            when (feedbackType) {
                CompletionFeedbackType.SET_COMPLETE -> CompletionFeedback.setComplete(hapticFeedback)
                CompletionFeedbackType.EXERCISE_COMPLETE -> CompletionFeedback.exerciseComplete(hapticFeedback)
                CompletionFeedbackType.WORKOUT_COMPLETE -> CompletionFeedback.workoutComplete(hapticFeedback)
            }
            onComplete?.invoke()
            hasTriggeredFeedback = true
            
            // Reset scale after brief period
            delay(150)
        } else if (!completed) {
            hasTriggeredFeedback = false
        }
    }
    
    Box(
        modifier = modifier
            .scale(scale)
            .semantics {
                contentDescription = if (completed) "Completed with feedback" else "Not completed"
            }
    ) {
        content()
    }
}

/**
 * Types of completion feedback with different haptic intensities
 */
enum class CompletionFeedbackType {
    SET_COMPLETE,       // Light feedback for individual set completion
    EXERCISE_COMPLETE,  // Medium feedback for exercise completion
    WORKOUT_COMPLETE    // Strong feedback for workout completion
}

/**
 * Completion flash effect that shows a brief color overlay on completion.
 * Useful for providing visual confirmation of completion actions.
 * 
 * @param completed Whether to show the completion flash
 * @param flashColor Color of the completion flash
 * @param modifier Modifier for styling
 * @param content Content to overlay with completion flash
 */
@Composable
fun CompletionFlash(
    completed: Boolean,
    flashColor: Color = LiftrixColors.Primary.copy(alpha = 0.3f),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var showFlash by remember { mutableStateOf(false) }
    
    // Animate flash opacity
    val flashAlpha by animateFloatAsState(
        targetValue = if (showFlash) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "completion_flash_alpha"
    )
    
    // Trigger flash effect when completed changes to true
    LaunchedEffect(completed) {
        if (completed) {
            showFlash = true
            delay(200)
            showFlash = false
        }
    }
    
    Box(modifier = modifier) {
        content()
        
        // Flash overlay
        if (flashAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .alpha(flashAlpha)
                    .background(
                        color = flashColor,
                        shape = RoundedCornerShape(8.dp)
                    )
            )
        }
    }
}

/**
 * Completion glow effect that adds a subtle glow around completed items.
 * Provides visual enhancement for completion states.
 * 
 * @param completed Whether to show the completion glow
 * @param glowColor Color of the completion glow
 * @param modifier Modifier for styling
 * @param content Content to apply glow effect to
 */
@Composable
fun CompletionGlow(
    completed: Boolean,
    glowColor: Color = LiftrixColors.Primary.copy(alpha = 0.2f),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Animate glow alpha
    val glowAlpha by animateFloatAsState(
        targetValue = if (completed) 1f else 0f,
        animationSpec = LiftrixAnimations.standardTransitionSpec,
        label = "completion_glow_alpha"
    )
    
    Box(
        modifier = modifier
            .alpha(glowAlpha)
            .background(
                color = glowColor,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(4.dp)
    ) {
        content()
    }
}

@Preview(name = "Completion Feedback - Set Complete")
@Composable
private fun CompletionFeedbackSetPreview() {
    LiftrixTheme {
        CompletionFeedback(
            completed = true,
            feedbackType = CompletionFeedbackType.SET_COMPLETE,
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(16.dp)
            ) {
                CompletionCheckmark(completed = true)
            }
        }
    }
}

@Preview(name = "Completion Flash Effect")
@Composable
private fun CompletionFlashPreview() {
    LiftrixTheme {
        CompletionFlash(
            completed = true,
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(16.dp)
            ) {
                CompletionCheckmark(completed = true)
            }
        }
    }
}

@Preview(name = "Completion Glow Effect")
@Composable
private fun CompletionGlowPreview() {
    LiftrixTheme {
        CompletionGlow(
            completed = true,
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(16.dp)
            ) {
                CompletionCheckmark(completed = true)
            }
        }
    }
} 