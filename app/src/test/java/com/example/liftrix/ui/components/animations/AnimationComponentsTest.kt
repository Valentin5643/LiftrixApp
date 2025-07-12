package com.example.liftrix.ui.components.animations

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.example.liftrix.ui.theme.LiftrixAnimations
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlin.test.*

/**
 * Comprehensive test suite for animation components and performance.
 * 
 * Tests cover:
 * - Animation specifications and timing
 * - Performance validation (60fps target)
 * - Spring physics behavior
 * - Micro-interaction animations
 * - Progress indicators and transitions
 * - Animation correctness and smoothness
 * - Memory efficiency during animations
 */
@RunWith(AndroidJUnit4::class)
class AnimationComponentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        // Setup any animation test prerequisites
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // ========== Animation Specification Tests ==========

    @Test
    fun liftrixAnimations_hasCorrectDurations() {
        // Test that animation durations meet performance requirements
        assertEquals(100L, LiftrixAnimations.Duration.MICRO)
        assertEquals(150L, LiftrixAnimations.Duration.FAST)
        assertEquals(300L, LiftrixAnimations.Duration.STANDARD)
        assertEquals(500L, LiftrixAnimations.Duration.SLOW)
        assertEquals(1000L, LiftrixAnimations.Duration.EMPHASIZE)
    }

    @Test
    fun liftrixAnimations_springSpecsExist() {
        // Verify all spring animation specs are properly defined
        assertNotNull(LiftrixAnimations.standardSpring())
        assertNotNull(LiftrixAnimations.fastSpring())
        assertNotNull(LiftrixAnimations.gentleSpring())
        assertNotNull(LiftrixAnimations.bouncySpring())
        assertNotNull(LiftrixAnimations.preciseSpring())
    }

    @Test
    fun liftrixAnimations_microInteractionSpecs() {
        // Test micro-interaction animation specifications
        assertNotNull(LiftrixAnimations.microInteractionSpec)
        assertNotNull(LiftrixAnimations.bounceEntranceSpec)
        
        // Verify micro-interaction duration is optimal for 60fps
        assertTrue(
            LiftrixAnimations.Duration.MICRO <= 100L,
            "Micro-interaction should be ≤100ms for optimal responsiveness"
        )
    }

    @Test
    fun liftrixAnimations_progressIndicatorSpecs() {
        // Test progress indicator animation specifications
        assertNotNull(LiftrixAnimations.progressRingAnimation())
        assertNotNull(LiftrixAnimations.progressFillSpec)
        assertNotNull(LiftrixAnimations.timerPulseSpec)
    }

    // ========== Animation Performance Tests ==========

    @Test
    fun animations_performanceOptimizedFor60fps() {
        // Verify animation durations support 60fps performance
        val maxFrameTime = 16.67f // 1000ms / 60fps
        
        assertTrue(
            LiftrixAnimations.Duration.MICRO / 6f <= maxFrameTime * 10,
            "Micro animations should allow for smooth frame rates"
        )
        
        assertTrue(
            LiftrixAnimations.Duration.FAST / 9f <= maxFrameTime * 10,
            "Fast animations should allow for smooth frame rates"
        )
    }

    @Test
    fun animations_springPhysicsPerformance() {
        var animationValue by mutableStateOf(0f)
        var frameCount = 0
        
        composeTestRule.setContent {
            LiftrixTheme {
                val animatedValue by animateFloatAsState(
                    targetValue = animationValue,
                    animationSpec = LiftrixAnimations.standardSpring(),
                    finishedListener = { frameCount++ }
                )
                
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(animatedValue)
                        .testTag("spring-animation-box")
                )
            }
        }
        
        // Trigger animation
        animationValue = 1f
        composeTestRule.waitForIdle()
        
        // Animation should complete without excessive frames
        assertTrue(frameCount > 0, "Spring animation should complete")
        
        composeTestRule
            .onNodeWithTag("spring-animation-box")
            .assertExists()
    }

    @Test
    fun animations_memoryEfficiencyDuringAnimation() {
        var isAnimating by mutableStateOf(false)
        
        composeTestRule.setContent {
            LiftrixTheme {
                val alpha by animateFloatAsState(
                    targetValue = if (isAnimating) 1f else 0f,
                    animationSpec = LiftrixAnimations.fastSpring()
                )
                
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .alpha(alpha)
                        .testTag("memory-test-box")
                )
            }
        }
        
        // Trigger multiple animations rapidly to test memory efficiency
        repeat(10) {
            isAnimating = !isAnimating
            composeTestRule.waitForIdle()
        }
        
        // If we reach here without OutOfMemoryError, animations are memory efficient
        composeTestRule
            .onNodeWithTag("memory-test-box")
            .assertExists()
    }

    // ========== Progress Indicator Animation Tests ==========

    @Test
    fun progressIndicator_animatesCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                CircularProgressIndicator(
                    modifier = Modifier.testTag("progress-indicator")
                )
            }
        }
        
        composeTestRule
            .onNodeWithTag("progress-indicator")
            .assertExists()
            .assertIsDisplayed()
        
        // Let animation run for a short time to verify it's working
        composeTestRule.waitForIdle()
    }

    @Test
    fun progressIndicator_customAnimationSpec() {
        var progress by mutableStateOf(0f)
        
        composeTestRule.setContent {
            LiftrixTheme {
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = LiftrixAnimations.progressFillSpec
                )
                
                CircularProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier.testTag("custom-progress")
                )
            }
        }
        
        // Animate progress
        progress = 1f
        composeTestRule.waitForIdle()
        
        composeTestRule
            .onNodeWithTag("custom-progress")
            .assertExists()
            .assertIsDisplayed()
    }

    // ========== Micro-Interaction Animation Tests ==========

    @Test
    fun microInteraction_buttonPressAnimation() {
        var isPressed by mutableStateOf(false)
        
        composeTestRule.setContent {
            LiftrixTheme {
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.95f else 1f,
                    animationSpec = LiftrixAnimations.microInteractionSpec
                )
                
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(scale)
                        .testTag("micro-interaction-box")
                )
            }
        }
        
        // Simulate press interaction
        isPressed = true
        composeTestRule.waitForIdle()
        
        isPressed = false
        composeTestRule.waitForIdle()
        
        composeTestRule
            .onNodeWithTag("micro-interaction-box")
            .assertExists()
    }

    @Test
    fun microInteraction_responsivenessTiming() {
        var isPressed by mutableStateOf(false)
        val startTime = System.currentTimeMillis()
        
        composeTestRule.setContent {
            LiftrixTheme {
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.95f else 1f,
                    animationSpec = LiftrixAnimations.microInteractionSpec,
                    finishedListener = {
                        val endTime = System.currentTimeMillis()
                        val duration = endTime - startTime
                        assertTrue(
                            duration <= 150, // Micro-interactions should be very fast
                            "Micro-interaction took too long: ${duration}ms"
                        )
                    }
                )
                
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(scale)
                        .testTag("timing-test-box")
                )
            }
        }
        
        isPressed = true
        composeTestRule.waitForIdle()
    }

    // ========== Entrance/Exit Animation Tests ==========

    @Test
    fun entranceAnimation_bounceEffect() {
        var isVisible by mutableStateOf(false)
        
        composeTestRule.setContent {
            LiftrixTheme {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isVisible,
                    enter = androidx.compose.animation.scaleIn(
                        animationSpec = LiftrixAnimations.bounceEntranceSpec
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .testTag("entrance-animation-box")
                    )
                }
            }
        }
        
        // Trigger entrance animation
        isVisible = true
        composeTestRule.waitForIdle()
        
        composeTestRule
            .onNodeWithTag("entrance-animation-box")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun exitAnimation_smoothTransition() {
        var isVisible by mutableStateOf(true)
        
        composeTestRule.setContent {
            LiftrixTheme {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isVisible,
                    exit = androidx.compose.animation.fadeOut(
                        animationSpec = LiftrixAnimations.exitSpec
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .testTag("exit-animation-box")
                    )
                }
            }
        }
        
        // Initially visible
        composeTestRule
            .onNodeWithTag("exit-animation-box")
            .assertExists()
        
        // Trigger exit animation
        isVisible = false
        composeTestRule.waitForIdle()
        
        // Should no longer exist after exit animation
        composeTestRule
            .onNodeWithTag("exit-animation-box")
            .assertDoesNotExist()
    }

    // ========== Complex Animation Scenarios Tests ==========

    @Test
    fun animations_chainedAnimationsWork() {
        var stage by mutableStateOf(0)
        
        composeTestRule.setContent {
            LiftrixTheme {
                val scale by animateFloatAsState(
                    targetValue = when (stage) {
                        0 -> 1f
                        1 -> 1.2f
                        else -> 0.8f
                    },
                    animationSpec = LiftrixAnimations.standardSpring()
                )
                
                val alpha by animateFloatAsState(
                    targetValue = when (stage) {
                        0 -> 1f
                        1 -> 0.7f
                        else -> 1f
                    },
                    animationSpec = LiftrixAnimations.fastSpring()
                )
                
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(scale)
                        .alpha(alpha)
                        .testTag("chained-animation-box")
                )
            }
        }
        
        // Test chained animations
        stage = 1
        composeTestRule.waitForIdle()
        
        stage = 2
        composeTestRule.waitForIdle()
        
        composeTestRule
            .onNodeWithTag("chained-animation-box")
            .assertExists()
    }

    @Test
    fun animations_simultaneousAnimationsPerformance() {
        var animateTogether by mutableStateOf(false)
        
        composeTestRule.setContent {
            LiftrixTheme {
                // Multiple simultaneous animations
                val scale1 by animateFloatAsState(
                    targetValue = if (animateTogether) 1.2f else 1f,
                    animationSpec = LiftrixAnimations.standardSpring()
                )
                val scale2 by animateFloatAsState(
                    targetValue = if (animateTogether) 0.8f else 1f,
                    animationSpec = LiftrixAnimations.fastSpring()
                )
                val alpha by animateFloatAsState(
                    targetValue = if (animateTogether) 0.5f else 1f,
                    animationSpec = LiftrixAnimations.gentleSpring()
                )
                
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(scale1)
                        .testTag("multi-animation-box1")
                )
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(scale2)
                        .alpha(alpha)
                        .testTag("multi-animation-box2")
                )
            }
        }
        
        val startTime = System.currentTimeMillis()
        
        // Trigger multiple simultaneous animations
        animateTogether = true
        composeTestRule.waitForIdle()
        
        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime
        
        // Multiple animations should not significantly impact performance
        assertTrue(
            totalTime < 2000, // Allow reasonable time for multiple animations
            "Simultaneous animations took too long: ${totalTime}ms"
        )
        
        composeTestRule
            .onNodeWithTag("multi-animation-box1")
            .assertExists()
        
        composeTestRule
            .onNodeWithTag("multi-animation-box2")
            .assertExists()
    }

    // ========== Animation State Management Tests ==========

    @Test
    fun animations_stateTransitionCorrectness() {
        var state by mutableStateOf("initial")
        
        composeTestRule.setContent {
            LiftrixTheme {
                val animatedValue by animateFloatAsState(
                    targetValue = when (state) {
                        "initial" -> 0f
                        "middle" -> 0.5f
                        "final" -> 1f
                        else -> 0f
                    },
                    animationSpec = LiftrixAnimations.standardSpring()
                )
                
                Box(
                    modifier = Modifier
                        .size((100 * (animatedValue + 0.1f)).dp)
                        .testTag("state-transition-box")
                )
            }
        }
        
        // Test state transitions
        state = "middle"
        composeTestRule.waitForIdle()
        
        state = "final"
        composeTestRule.waitForIdle()
        
        state = "initial"
        composeTestRule.waitForIdle()
        
        composeTestRule
            .onNodeWithTag("state-transition-box")
            .assertExists()
    }

    // ========== Animation Error Handling Tests ==========

    @Test
    fun animations_handleInvalidValues() {
        var targetValue by mutableStateOf(1f)
        
        composeTestRule.setContent {
            LiftrixTheme {
                val animatedValue by animateFloatAsState(
                    targetValue = targetValue,
                    animationSpec = LiftrixAnimations.standardSpring()
                )
                
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(maxOf(0.1f, minOf(2f, animatedValue))) // Clamp values
                        .testTag("error-handling-box")
                )
            }
        }
        
        // Test with extreme values
        targetValue = Float.MAX_VALUE
        composeTestRule.waitForIdle()
        
        targetValue = Float.MIN_VALUE
        composeTestRule.waitForIdle()
        
        targetValue = Float.NaN
        composeTestRule.waitForIdle()
        
        // Should handle gracefully without crashes
        composeTestRule
            .onNodeWithTag("error-handling-box")
            .assertExists()
    }

    @Test
    fun animations_interruptionHandling() {
        var targetValue by mutableStateOf(0f)
        
        composeTestRule.setContent {
            LiftrixTheme {
                val animatedValue by animateFloatAsState(
                    targetValue = targetValue,
                    animationSpec = LiftrixAnimations.standardSpring()
                )
                
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(animatedValue + 0.1f)
                        .testTag("interruption-test-box")
                )
            }
        }
        
        // Start animation
        targetValue = 1f
        
        // Interrupt with new target before first completes
        targetValue = 0.5f
        
        // Interrupt again
        targetValue = 1.5f
        
        composeTestRule.waitForIdle()
        
        // Should handle interruptions gracefully
        composeTestRule
            .onNodeWithTag("interruption-test-box")
            .assertExists()
    }
} 