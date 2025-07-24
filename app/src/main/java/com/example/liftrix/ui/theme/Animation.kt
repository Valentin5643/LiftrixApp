package com.example.liftrix.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.example.liftrix.ui.common.PerformanceOptimizations

/**
 * Centralized animation specifications for Liftrix
 * Designed for 60fps performance with spring physics and micro-interactions
 * Athletic-inspired motion with natural weight-shifting feel
 */
object LiftrixAnimations {
    
    // Standard Animation Durations (in milliseconds)
    const val INSTANT = 0
    const val MICRO = 100
    const val FAST = 150
    const val STANDARD = 300
    const val MEDIUM = 500
    const val SLOW = 800
    const val VERY_SLOW = 1200
    
    // Athletic Spring Physics Configurations
    
    /**
     * Primary athletic spring for weight-shifting feel
     * Optimized for natural motion with medium bounce and precise control
     */
    val LiftrixSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
        visibilityThreshold = 0.01f
    )
    
    /**
     * Athletic micro-interaction spring for button presses
     * Quick response with natural feel, optimized for 60fps
     */
    val athleticMicroSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh,
        visibilityThreshold = 0.001f
    )
    
    /**
     * Athletic progress spring for smooth value transitions
     * Slightly bouncy for satisfying progress feedback
     */
    val athleticProgressSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
        visibilityThreshold = 0.01f
    )
    
    /**
     * Athletic progress spring for integer values (counters, progress)
     * Slightly bouncy for satisfying progress feedback
     */
    val athleticProgressSpringInt: SpringSpec<Int> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
        visibilityThreshold = 1
    )
    
    /**
     * Athletic entrance spring for component appearances
     * Confident motion with controlled overshoot
     */
    val athleticEntranceSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessHigh,
        visibilityThreshold = 0.01f
    )
    
    // Standard Spring Animation Specifications
    
    /**
     * Standard spring animation for general UI interactions
     * Provides natural motion feel with moderate bounce
     */
    val standardSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
    
    /**
     * Fast spring animation for quick interactions
     * High stiffness for snappy responses
     */
    val fastSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh
    )
    
    /**
     * Gentle spring animation for smooth transitions
     * Low stiffness for fluid motion
     */
    val gentleSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )
    
    /**
     * Bouncy spring animation for playful interactions
     * High bounce for engaging feedback
     */
    val bouncySpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium
    )
    
    /**
     * Precise spring animation for controlled movements
     * No bounce for exact positioning
     */
    val preciseSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )
    
    // Tween Animation Specifications
    
    /**
     * Micro-interaction animation for button presses and small feedback
     * Very fast with smooth easing, optimized for athletic feel
     */
    val microInteractionSpec: TweenSpec<Float> = tween(
        durationMillis = MICRO,
        easing = FastOutSlowInEasing
    )
    
    /**
     * Fast transition animation for quick UI changes
     */
    val fastTransitionSpec: TweenSpec<Float> = tween(
        durationMillis = FAST,
        easing = FastOutSlowInEasing
    )
    
    /**
     * Fast transition animation for color changes
     */
    val fastColorTransitionSpec: TweenSpec<Color> = tween(
        durationMillis = FAST,
        easing = FastOutSlowInEasing
    )
    
    /**
     * Standard transition animation for general UI transitions
     */
    val standardTransitionSpec: TweenSpec<Float> = tween(
        durationMillis = STANDARD,
        easing = FastOutSlowInEasing
    )
    
    /**
     * Standard transition animation for color changes
     */
    val standardColorTransitionSpec: TweenSpec<Color> = tween(
        durationMillis = STANDARD,
        easing = FastOutSlowInEasing
    )
    
    /**
     * Athletic entrance animation with controlled overshoot
     * Provides confident, athletic appearance with precise timing
     */
    val athleticEntranceSpec: AnimationSpec<Float> = keyframes {
        durationMillis = STANDARD
        0f at 0
        1.08f at (STANDARD * 0.6).toInt() with FastOutSlowInEasing
        0.96f at (STANDARD * 0.8).toInt() with FastOutSlowInEasing
        1f at STANDARD
    }
    
    /**
     * Entrance animation specification for components appearing
     */
    val bounceEntranceSpec: AnimationSpec<Float> = keyframes {
        durationMillis = STANDARD
        0f at 0
        1.1f at (STANDARD * 0.6).toInt() with FastOutSlowInEasing
        0.9f at (STANDARD * 0.8).toInt() with FastOutSlowInEasing
        1f at STANDARD
    }
    
    /**
     * Exit animation specification for components disappearing
     */
    val exitSpec: TweenSpec<Float> = tween(
        durationMillis = FAST,
        easing = FastOutSlowInEasing
    )
    
    // Progress and Loading Animations
    
    /**
     * Progress ring animation for loading indicators
     * Infinite rotation with linear timing, 60fps optimized
     */
    val progressRingAnimation: InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = tween(
            durationMillis = SLOW,
            easing = LinearEasing
        ),
        repeatMode = RepeatMode.Restart
    )
    
    /**
     * Athletic progress ring animation with weight-shifting feel
     * Smooth completion feedback with natural motion
     */
    val athleticProgressRingAnimation: InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = tween(
            durationMillis = 1000,
            easing = FastOutSlowInEasing
        ),
        repeatMode = RepeatMode.Restart
    )
    
    /**
     * Pulse animation for breathing effects
     * Smooth in-out pulse with athletic timing
     */
    val pulseAnimation: InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = tween(
            durationMillis = MEDIUM,
            easing = FastOutSlowInEasing
        ),
        repeatMode = RepeatMode.Reverse
    )
    
    /**
     * Athletic pulse animation for active states
     * Confident rhythm matching athletic branding
     */
    val athleticPulseAnimation: InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = tween(
            durationMillis = 800,
            easing = FastOutSlowInEasing
        ),
        repeatMode = RepeatMode.Reverse
    )
    
    /**
     * Shimmer animation for loading placeholders
     * Fast, smooth shimmer effect
     */
    val shimmerAnimation: InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = tween(
            durationMillis = FAST,
            easing = LinearEasing
        ),
        repeatMode = RepeatMode.Restart
    )
    
    /**
     * Skeleton loading shimmer animation - Task ANIM-001
     * Smooth 1200ms cycle with teal accent color for skeleton loading states
     * Optimized for natural breathing effect and 60fps performance
     */
    val skeletonShimmerAnimation: InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = tween(
            durationMillis = 1200, // Task specification: smooth skeleton loading
            easing = LinearEasing
        ),
        repeatMode = RepeatMode.Restart
    )
    
    // Card and Surface Animations
    
    /**
     * Athletic card appearance animation with confident motion
     * Natural weight-shifting feel with controlled overshoot
     */
    val athleticCardEntranceSpec: AnimationSpec<Float> = keyframes {
        durationMillis = MEDIUM
        0f at 0
        0.85f at (MEDIUM * 0.3).toInt()
        1.06f at (MEDIUM * 0.7).toInt() with FastOutSlowInEasing
        1f at MEDIUM
    }
    
    /**
     * Card appearance animation with scale and fade
     */
    val cardEntranceSpec: AnimationSpec<Float> = keyframes {
        durationMillis = MEDIUM
        0f at 0
        0.8f at (MEDIUM * 0.3).toInt()
        1.05f at (MEDIUM * 0.7).toInt() with FastOutSlowInEasing
        1f at MEDIUM
    }
    
    /**
     * Athletic card press animation with natural feedback
     * Optimized for tactile feel and 60fps performance
     */
    val athleticCardPressSpec: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh,
        visibilityThreshold = 0.001f
    )
    
    /**
     * Card press animation for interactive feedback
     */
    val cardPressSpec: TweenSpec<Float> = tween(
        durationMillis = MICRO,
        easing = FastOutSlowInEasing
    )
    
    /**
     * Surface elevation animation for depth changes
     */
    val elevationChangeSpec: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh
    )
    
    // Navigation Animations
    
    /**
     * Athletic screen transition with confident motion
     * Smooth horizontal slides with natural feel
     */
    val athleticScreenTransitionSpec: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
        visibilityThreshold = 0.01f
    )
    
    /**
     * Screen transition animation for navigation
     */
    val screenTransitionSpec: TweenSpec<Float> = tween(
        durationMillis = STANDARD,
        easing = FastOutSlowInEasing
    )
    
    /**
     * Athletic modal animation with vertical lift
     * Natural motion avoiding jarring transitions
     */
    val athleticModalSpec: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
        visibilityThreshold = 0.01f
    )
    
    /**
     * Bottom navigation selection animation
     */
    val navigationSelectionSpec: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )
    
    /**
     * Navigation selection spring animation
     */
    val navigationSelectionSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )
    
    // Exercise and Workout Specific Animations
    
    /**
     * Athletic exercise completion with satisfying feedback
     * Weight-shifting feel with confident overshoot
     */
    val athleticExerciseCompletionSpec: AnimationSpec<Float> = keyframes {
        durationMillis = MEDIUM
        0f at 0
        1.15f at (MEDIUM * 0.5).toInt() with FastOutSlowInEasing
        0.92f at (MEDIUM * 0.8).toInt()
        1f at MEDIUM
    }
    
    /**
     * Exercise completion animation with satisfying feedback
     */
    val exerciseCompletionSpec: AnimationSpec<Float> = keyframes {
        durationMillis = MEDIUM
        0f at 0
        1.2f at (MEDIUM * 0.5).toInt() with FastOutSlowInEasing
        0.95f at (MEDIUM * 0.8).toInt()
        1f at MEDIUM
    }
    
    /**
     * Athletic checkmark animation with quick bounce
     * Natural feedback with haptic-optimized timing
     */
    val athleticCheckmarkSpec: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessHigh,
        visibilityThreshold = 0.01f
    )
    
    /**
     * Set completion checkmark animation
     */
    val checkmarkAnimationSpec: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh
    )
    
    /**
     * Athletic progress fill with smooth completion feedback
     * Natural motion with weight-shifting feel
     */
    val athleticProgressFillSpec: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
        visibilityThreshold = 0.005f
    )
    
    /**
     * Progress ring fill animation for workout progress
     */
    val progressFillSpec: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium
    )
    
    /**
     * Athletic timer pulse with confident rhythm
     * Matches athletic branding and training intensity
     */
    val athleticTimerPulseSpec: InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = tween(
            durationMillis = 1200,
            easing = FastOutSlowInEasing
        ),
        repeatMode = RepeatMode.Reverse
    )
    
    /**
     * Timer pulse animation for active workout timer
     */
    val timerPulseSpec: InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = tween(
            durationMillis = 1000,
            easing = FastOutSlowInEasing
        ),
        repeatMode = RepeatMode.Reverse
    )
    
    // Drag and Drop Animations
    
    /**
     * Drag start animation for lift-off effect
     */
    val dragStartSpec: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )
    
    /**
     * Drag drop animation for settling into place
     */
    val dragDropSpec: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
    
    // Search and Input Animations
    
    /**
     * Search result appearance animation
     */
    val searchResultSpec: TweenSpec<Float> = tween(
        durationMillis = FAST,
        easing = FastOutSlowInEasing
    )
    
    /**
     * Input field focus animation
     */
    val inputFocusSpec: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )
}

/**
 * Animation utilities for common patterns with performance optimization
 * Enhanced with 60fps monitoring and memory-efficient animation handling
 */
object AnimationUtils {
    
    /**
     * Scale animation for press interactions with performance monitoring
     * Returns to original size after press, optimized for 60fps
     */
    fun pressScaleAnimation(): AnimationSpec<Float> = LiftrixAnimations.microInteractionSpec
    
    /**
     * Athletic press scale animation with weight-shifting feel
     * Optimized for consistent 60fps performance
     */
    fun athleticPressScaleAnimation(): SpringSpec<Float> = LiftrixAnimations.athleticMicroSpring
    
    /**
     * Fade in animation for appearing content
     * Memory-optimized for reduced allocation overhead
     */
    fun fadeInAnimation(): AnimationSpec<Float> = LiftrixAnimations.fastTransitionSpec
    
    /**
     * Fade out animation for disappearing content
     * Optimized for smooth 60fps transitions
     */
    fun fadeOutAnimation(): AnimationSpec<Float> = LiftrixAnimations.exitSpec
    
    /**
     * Slide in animation for new content
     * Performance-optimized for consistent frame rates
     */
    fun slideInAnimation(): AnimationSpec<Float> = LiftrixAnimations.standardTransitionSpec
    
    /**
     * Athletic slide in animation with natural motion
     * Optimized spring physics for 60fps consistency
     */
    fun athleticSlideInAnimation(): SpringSpec<Float> = LiftrixAnimations.athleticEntranceSpring
    
    /**
     * Scale up animation for emphasis
     * Memory-efficient implementation for reduced GC pressure
     */
    fun scaleUpAnimation(): AnimationSpec<Float> = LiftrixAnimations.bouncySpring
    
    /**
     * Athletic scale up animation with confident motion
     * Performance-optimized for athletic branding feel
     */
    fun athleticScaleUpAnimation(): SpringSpec<Float> = LiftrixAnimations.athleticEntranceSpring
    
    /**
     * Rotation animation for icons and indicators
     * Optimized for smooth 60fps rotation performance
     */
    fun rotationAnimation(): AnimationSpec<Float> = LiftrixAnimations.standardSpring
    
    /**
     * Performance-monitored animation wrapper
     * Tracks animation performance and provides 60fps monitoring
     */
    @Composable
    fun PerformanceMonitoredAnimation(
        key: String,
        content: @Composable () -> Unit
    ) {
        PerformanceOptimizations.AnimationPerformanceMonitor.MonitorAnimation(
            key = key,
            content = content
        )
    }
    
    /**
     * Memory-efficient animation state management
     * Reduces recomposition overhead for complex animations
     */
    @Composable
    fun <T> rememberOptimizedAnimationState(
        initialValue: T,
        key: String
    ): androidx.compose.runtime.MutableState<T> {
        return remember(key) {
            PerformanceOptimizations.MemoryEfficientComponents.optimizedMutableStateOf(initialValue)
        }
    }
    
    /**
     * Batch animation performance tracking
     * Efficiently tracks multiple animations with minimal overhead
     */
    @Composable
    fun TrackAnimationPerformance(
        animationKey: String,
        content: @Composable () -> Unit
    ) {
        PerformanceOptimizations.MemoryEfficientComponents.TrackRecomposition(
            key = "animation_$animationKey",
            content = content
        )
    }
} 