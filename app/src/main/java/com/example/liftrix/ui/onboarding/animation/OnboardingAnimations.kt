package com.example.liftrix.ui.onboarding.animation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.with
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Comprehensive animation system for onboarding flow following Material Design motion principles.
 * Optimized for performance with <300ms transitions, <100ms validation feedback, and smooth 60fps experience.
 */
object OnboardingAnimations {
    
    // Material Design easing curves optimized for onboarding
    val EmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val StandardEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    
    // Optimized animation durations for smooth experience
    const val DURATION_VERY_FAST = 50
    const val DURATION_FAST = 100
    const val DURATION_MEDIUM = 200
    const val DURATION_SLOW = 300
    const val DURATION_EXTRA_SLOW = 500
    
    // Performance-optimized animation specs
    val fastTween = tween<Float>(DURATION_FAST, easing = EmphasizedEasing)
    val mediumTween = tween<Float>(DURATION_MEDIUM, easing = EmphasizedEasing)
    val slowTween = tween<Float>(DURATION_SLOW, easing = EmphasizedEasing)
    val bounceSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
    val smoothSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
    
    /**
     * Navigation slide transition with optimized performance for forward navigation.
     * Uses hardware-accelerated transforms and reduced overdraw.
     */
    fun slideInForward(): EnterTransition = slideInHorizontally(
        animationSpec = tween(DURATION_MEDIUM, easing = EmphasizedDecelerateEasing),
        initialOffsetX = { fullWidth -> fullWidth }
    ) + fadeIn(
        animationSpec = tween(DURATION_MEDIUM, delayMillis = 50, easing = LinearEasing)
    )
    
    /**
     * Navigation slide transition optimized for backward navigation.
     */
    fun slideInBackward(): EnterTransition = slideInHorizontally(
        animationSpec = tween(DURATION_MEDIUM, easing = EmphasizedDecelerateEasing),
        initialOffsetX = { fullWidth -> -fullWidth / 3 }
    ) + fadeIn(
        animationSpec = tween(DURATION_MEDIUM, easing = LinearEasing)
    )
    
    /**
     * Exit transition for forward navigation with reduced GPU usage.
     */
    fun slideOutForward(): ExitTransition = slideOutHorizontally(
        animationSpec = tween(DURATION_MEDIUM, easing = EmphasizedAccelerateEasing),
        targetOffsetX = { fullWidth -> -fullWidth / 3 }
    ) + fadeOut(
        animationSpec = tween(DURATION_MEDIUM, delayMillis = 50, easing = LinearEasing)
    )
    
    /**
     * Exit transition for backward navigation.
     */
    fun slideOutBackward(): ExitTransition = slideOutHorizontally(
        animationSpec = tween(DURATION_MEDIUM, easing = EmphasizedAccelerateEasing),
        targetOffsetX = { fullWidth -> fullWidth }
    ) + fadeOut(
        animationSpec = tween(DURATION_MEDIUM, easing = LinearEasing)
    )
    
    /**
     * Optimized validation feedback animation for <100ms response time.
     */
    @Composable
    fun validationFeedbackEnter(): EnterTransition = expandVertically(
        animationSpec = tween(DURATION_FAST, easing = EmphasizedDecelerateEasing),
        expandFrom = androidx.compose.ui.Alignment.Top
    ) + fadeIn(
        animationSpec = tween(DURATION_FAST, easing = LinearEasing)
    ) + scaleIn(
        animationSpec = tween(DURATION_FAST, easing = EmphasizedDecelerateEasing),
        initialScale = 0.8f,
        transformOrigin = TransformOrigin(0f, 0f)
    )
    
    /**
     * Validation feedback exit animation.
     */
    @Composable
    fun validationFeedbackExit(): ExitTransition = shrinkVertically(
        animationSpec = tween(DURATION_FAST, easing = EmphasizedAccelerateEasing),
        shrinkTowards = androidx.compose.ui.Alignment.Top
    ) + fadeOut(
        animationSpec = tween(DURATION_FAST, easing = LinearEasing)
    ) + scaleOut(
        animationSpec = tween(DURATION_FAST, easing = EmphasizedAccelerateEasing),
        targetScale = 0.8f,
        transformOrigin = TransformOrigin(0f, 0f)
    )
    
    /**
     * Progress indicator animation with optimized spring physics.
     */
    @Composable
    fun animateProgress(
        targetProgress: Float,
        durationMillis: Int = DURATION_MEDIUM
    ): Float {
        val animatedProgress by animateFloatAsState(
            targetValue = targetProgress,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "progress_animation"
        )
        return animatedProgress
    }
    
    /**
     * Step completion animation with bounce effect.
     */
    @Composable
    fun animateStepCompletion(
        isCompleted: Boolean,
        durationMillis: Int = DURATION_MEDIUM
    ): Float {
        val scale by animateFloatAsState(
            targetValue = if (isCompleted) 1.1f else 1.0f,
            animationSpec = if (isCompleted) {
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            } else {
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            },
            label = "step_completion_animation"
        )
        return scale
    }
    
    /**
     * Content fade transition for dynamic content changes.
     */
    @OptIn(ExperimentalAnimationApi::class)
    fun contentFadeTransition(
        durationMillis: Int = DURATION_FAST
    ): ContentTransform = fadeIn(
        animationSpec = tween(durationMillis, delayMillis = durationMillis / 2, easing = LinearEasing)
    ) with fadeOut(
        animationSpec = tween(durationMillis / 2, easing = LinearEasing)
    )
    
    /**
     * Loading state animation with subtle fade.
     */
    @Composable
    fun loadingAnimation(): EnterTransition = fadeIn(
        animationSpec = tween(DURATION_FAST, easing = LinearEasing)
    )
    
    /**
     * Error shake animation for validation feedback.
     */
    @Composable
    fun shakeAnimation(trigger: Boolean): Modifier {
        val shakeOffset by animateFloatAsState(
            targetValue = if (trigger) 1f else 0f,
            animationSpec = tween(DURATION_FAST, easing = EmphasizedEasing),
            label = "shake_animation"
        )
        
        return Modifier.graphicsLayer {
            translationX = shakeOffset * 8f * kotlin.math.sin(shakeOffset * kotlin.math.PI * 4).toFloat()
        }
    }
    
    /**
     * Success pulse animation for positive feedback.
     */
    @Composable
    fun successPulseAnimation(trigger: Boolean): Modifier {
        val scale by animateFloatAsState(
            targetValue = if (trigger) 1.05f else 1.0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "success_pulse_animation"
        )
        
        return Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    }
}

/**
 * Performance optimization utilities for animations.
 */
object AnimationPerformanceUtils {
    
    /**
     * Stable wrapper for animation parameters to prevent unnecessary recompositions.
     */
    @Stable
    data class AnimationConfig(
        val duration: Int,
        val easing: Easing,
        val delayMillis: Int = 0
    )
    
    /**
     * Optimized derivedStateOf wrapper for animation calculations.
     */
    @Composable
    fun rememberAnimationState(
        vararg keys: Any?,
        calculation: () -> Float
    ): Float {
        val state by remember(*keys) {
            derivedStateOf(calculation)
        }
        return state
    }
    
    /**
     * Memory-efficient animation configuration caching.
     */
    val animationConfigs = mapOf(
        "fast" to AnimationConfig(OnboardingAnimations.DURATION_FAST, OnboardingAnimations.EmphasizedEasing),
        "medium" to AnimationConfig(OnboardingAnimations.DURATION_MEDIUM, OnboardingAnimations.EmphasizedEasing),
        "slow" to AnimationConfig(OnboardingAnimations.DURATION_SLOW, OnboardingAnimations.EmphasizedEasing),
        "validation" to AnimationConfig(OnboardingAnimations.DURATION_FAST, OnboardingAnimations.EmphasizedDecelerateEasing),
        "progress" to AnimationConfig(OnboardingAnimations.DURATION_MEDIUM, OnboardingAnimations.StandardEasing)
    )
    
    /**
     * Performance metrics for animation monitoring.
     */
    @Stable
    data class AnimationMetrics(
        val frameDrops: Int = 0,
        val averageDuration: Long = 0L,
        val memoryUsage: Long = 0L
    )
    
    /**
     * Battery-optimized animation configuration based on system state.
     */
    @Composable
    fun getBatteryOptimizedDuration(baseDuration: Int, isPowerSaveMode: Boolean = false): Int {
        return if (isPowerSaveMode) {
            (baseDuration * 0.7f).toInt() // Reduce animation duration by 30% in power save mode
        } else {
            baseDuration
        }
    }
}

/**
 * Specialized animation components for onboarding flow.
 */
object OnboardingAnimationComponents {
    
    /**
     * Animated validation feedback container.
     */
    @Composable
    fun AnimatedValidationFeedback(
        isVisible: Boolean,
        content: @Composable () -> Unit
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = OnboardingAnimations.validationFeedbackEnter(),
            exit = OnboardingAnimations.validationFeedbackExit()
        ) {
            content()
        }
    }
    
    /**
     * Animated step indicator with completion effects.
     */
    @Composable
    fun AnimatedStepIndicator(
        isCompleted: Boolean,
        isCurrent: Boolean,
        content: @Composable (scale: Float) -> Unit
    ) {
        val scale = OnboardingAnimations.animateStepCompletion(isCompleted)
        content(scale)
    }
    
    /**
     * Animated progress with smooth transitions.
     */
    @Composable
    fun AnimatedProgress(
        targetProgress: Float,
        content: @Composable (progress: Float) -> Unit
    ) {
        val animatedProgress = OnboardingAnimations.animateProgress(targetProgress)
        content(animatedProgress)
    }
}

/**
 * Extension function for optimized recomposition prevention.
 */
@Composable
inline fun <T> T.rememberStable(): T = remember { this }