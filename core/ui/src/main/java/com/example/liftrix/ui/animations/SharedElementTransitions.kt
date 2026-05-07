package com.example.liftrix.ui.animations

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixAnimations

/**
 * SharedElementTransitions for Liftrix Screen Navigation
 * 
 * Provides smooth 300ms screen transitions with shared element animations
 * following athletic design principles and maintaining 60fps performance.
 * 
 * Features:
 * - 300ms transition timing using LiftrixAnimations.STANDARD
 * - Athletic spring physics with weight-shifting feel
 * - Shared element continuity between screens
 * - 60fps performance optimization
 * - Accessibility-compliant animations
 */

/**
 * Main shared element transition composable for screen navigation
 * 
 * @param visible Whether the content should be visible
 * @param label Unique label for the animation (for performance tracking)
 * @param modifier Modifier for customizing the animation container
 * @param content The screen content to animate
 */
@Composable
fun SharedElementTransition(
    visible: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = tween(
                durationMillis = LiftrixAnimations.STANDARD,
                easing = FastOutSlowInEasing
            ),
            initialOffsetY = { it / 3 } // Slide in from bottom third
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = LiftrixAnimations.STANDARD,
                easing = FastOutSlowInEasing
            )
        ),
        exit = slideOutVertically(
            animationSpec = tween(
                durationMillis = LiftrixAnimations.STANDARD,
                easing = FastOutSlowInEasing
            ),
            targetOffsetY = { -it / 3 } // Slide out to top third
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = LiftrixAnimations.STANDARD,
                easing = FastOutSlowInEasing
            )
        ),
        modifier = modifier,
        label = "sharedElementTransition_$label",
        content = content
    )
}

/**
 * Athletic shared element transition with enhanced spring physics
 * Provides weight-shifting feel with natural motion
 * 
 * @param visible Whether the content should be visible
 * @param label Unique label for the animation
 * @param modifier Modifier for customizing the animation container
 * @param content The screen content to animate
 */
@Composable
fun AthleticSharedElementTransition(
    visible: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh
            ),
            initialOffsetY = { it / 4 } // Gentler slide for athletic feel
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = LiftrixAnimations.STANDARD,
                easing = FastOutSlowInEasing
            )
        ) + scaleIn(
            animationSpec = LiftrixAnimations.athleticEntranceSpring,
            initialScale = 0.92f // Athletic entrance scale
        ),
        exit = slideOutVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh
            ),
            targetOffsetY = { -it / 4 }
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = LiftrixAnimations.FAST,
                easing = FastOutSlowInEasing
            )
        ) + scaleOut(
            animationSpec = LiftrixAnimations.exitSpec,
            targetScale = 1.08f // Athletic exit overshoot
        ),
        modifier = modifier,
        label = "athleticSharedElementTransition_$label",
        content = content
    )
}

/**
 * Horizontal shared element transition for left/right navigation
 * 
 * @param visible Whether the content should be visible
 * @param label Unique label for the animation
 * @param isForward Whether this is a forward navigation (true) or back navigation (false)
 * @param modifier Modifier for customizing the animation container
 * @param content The screen content to animate
 */
@Composable
fun HorizontalSharedElementTransition(
    visible: Boolean,
    label: String,
    isForward: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    val density = LocalDensity.current
    val slideDistance = with(density) { 300.dp.toPx().toInt() }
    
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            animationSpec = tween(
                durationMillis = LiftrixAnimations.STANDARD,
                easing = FastOutSlowInEasing
            ),
            initialOffsetX = { if (isForward) slideDistance else -slideDistance }
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = LiftrixAnimations.STANDARD,
                easing = FastOutSlowInEasing
            )
        ),
        exit = slideOutHorizontally(
            animationSpec = tween(
                durationMillis = LiftrixAnimations.STANDARD,
                easing = FastOutSlowInEasing
            ),
            targetOffsetX = { if (isForward) -slideDistance else slideDistance }
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = LiftrixAnimations.STANDARD,
                easing = FastOutSlowInEasing
            )
        ),
        modifier = modifier,
        label = "horizontalSharedElementTransition_$label",
        content = content
    )
}

/**
 * Modal shared element transition for bottom sheet and dialog appearances
 * 
 * @param visible Whether the content should be visible
 * @param label Unique label for the animation
 * @param modifier Modifier for customizing the animation container
 * @param content The modal content to animate
 */
@Composable
fun ModalSharedElementTransition(
    visible: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            initialOffsetY = { it } // Slide in from bottom
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = LiftrixAnimations.STANDARD,
                easing = FastOutSlowInEasing
            )
        ) + scaleIn(
            animationSpec = LiftrixAnimations.athleticModalSpec,
            initialScale = 0.9f
        ),
        exit = slideOutVertically(
            animationSpec = tween(
                durationMillis = LiftrixAnimations.FAST,
                easing = FastOutSlowInEasing
            ),
            targetOffsetY = { it }
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = LiftrixAnimations.FAST,
                easing = FastOutSlowInEasing
            )
        ) + scaleOut(
            animationSpec = LiftrixAnimations.exitSpec,
            targetScale = 0.9f
        ),
        modifier = modifier,
        label = "modalSharedElementTransition_$label",
        content = content
    )
}

/**
 * Shared element card transition for workout cards
 * Provides smooth continuity when navigating from card to detail screen
 * 
 * @param visible Whether the card should be visible
 * @param label Unique label for the animation
 * @param startBounds Initial bounds of the card element
 * @param endBounds Final bounds of the card element
 * @param modifier Modifier for customizing the animation container
 * @param content The card content to animate
 */
@Composable
fun SharedElementCardTransition(
    visible: Boolean,
    label: String,
    startBounds: Dp = 0.dp,
    endBounds: Dp = 0.dp,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    val density = LocalDensity.current
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = LiftrixAnimations.STANDARD,
                easing = FastOutSlowInEasing
            )
        ) + scaleIn(
            animationSpec = tween(
                durationMillis = LiftrixAnimations.STANDARD,
                easing = FastOutSlowInEasing
            ),
            initialScale = 0.85f
        ) + expandVertically(
            animationSpec = tween(
                durationMillis = LiftrixAnimations.STANDARD,
                easing = FastOutSlowInEasing
            ),
            expandFrom = Alignment.Top
        ),
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = LiftrixAnimations.FAST,
                easing = FastOutSlowInEasing
            )
        ) + scaleOut(
            animationSpec = LiftrixAnimations.exitSpec,
            targetScale = 0.95f
        ) + shrinkVertically(
            animationSpec = tween(
                durationMillis = LiftrixAnimations.FAST,
                easing = FastOutSlowInEasing
            ),
            shrinkTowards = Alignment.Top
        ),
        modifier = modifier,
        label = "sharedElementCardTransition_$label",
        content = content
    )
}

/**
 * Screen transition wrapper that provides consistent navigation animations
 * 
 * @param transitionState Current transition state
 * @param transitionType Type of transition to apply
 * @param label Unique label for the animation
 * @param modifier Modifier for customizing the wrapper
 * @param content Screen content to wrap with transition
 */
@Composable
fun ScreenTransitionWrapper(
    transitionState: Boolean,
    transitionType: TransitionType = TransitionType.VERTICAL,
    label: String,
    modifier: Modifier = Modifier.fillMaxSize(),
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    when (transitionType) {
        TransitionType.VERTICAL -> SharedElementTransition(
            visible = transitionState,
            label = label,
            modifier = modifier,
            content = content
        )
        TransitionType.HORIZONTAL_FORWARD -> HorizontalSharedElementTransition(
            visible = transitionState,
            label = label,
            isForward = true,
            modifier = modifier,
            content = content
        )
        TransitionType.HORIZONTAL_BACK -> HorizontalSharedElementTransition(
            visible = transitionState,
            label = label,
            isForward = false,
            modifier = modifier,
            content = content
        )
        TransitionType.MODAL -> ModalSharedElementTransition(
            visible = transitionState,
            label = label,
            modifier = modifier,
            content = content
        )
        TransitionType.ATHLETIC -> AthleticSharedElementTransition(
            visible = transitionState,
            label = label,
            modifier = modifier,
            content = content
        )
        TransitionType.CARD -> SharedElementCardTransition(
            visible = transitionState,
            label = label,
            modifier = modifier,
            content = content
        )
    }
}

/**
 * Transition types for different navigation scenarios
 */
enum class TransitionType {
    VERTICAL,           // Default vertical slide transition
    HORIZONTAL_FORWARD, // Horizontal slide for forward navigation
    HORIZONTAL_BACK,    // Horizontal slide for back navigation
    MODAL,             // Bottom sheet or dialog transitions
    ATHLETIC,          // Enhanced athletic transitions with spring physics
    CARD              // Card to detail screen transitions
}

/**
 * Athletic background transition for screen changes
 * Provides subtle background animation during navigation
 */
@Composable
fun AthleticBackgroundTransition(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = LiftrixAnimations.STANDARD,
            easing = FastOutSlowInEasing
        ),
        label = "athleticBackgroundTransition"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.98f,
        animationSpec = LiftrixAnimations.athleticScreenTransitionSpec,
        label = "athleticBackgroundScale"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
    ) {
        content()
    }
}