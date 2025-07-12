package com.example.liftrix.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.common.AthleticButtonPress
import com.example.liftrix.ui.common.EnhancedAthleticButtonPress
import com.example.liftrix.ui.common.LiftrixAnimationUtils
import com.example.liftrix.ui.common.athleticPressScale
import io.mockk.MockKAnnotations
import kotlinx.coroutines.delay
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests for Liftrix animation system
 * Validates performance, consistency, and athletic feel of spring physics
 */
class AnimationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Test
    fun liftrixSpring_hasCorrectParameters() {
        // Verify the LiftrixSpring configuration matches task requirements
        val spring = LiftrixAnimations.LiftrixSpring
        
        assert(spring.dampingRatio == Spring.DampingRatioMediumBouncy)
        assert(spring.stiffness == Spring.StiffnessMedium)
        assert(spring.visibilityThreshold == 0.01f)
    }

    @Test
    fun athleticMicroSpring_isOptimizedFor60fps() {
        // Verify athletic micro spring is optimized for quick interactions
        val spring = LiftrixAnimations.athleticMicroSpring
        
        assert(spring.dampingRatio == Spring.DampingRatioMediumBouncy)
        assert(spring.stiffness == Spring.StiffnessHigh)
        assert(spring.visibilityThreshold == 0.001f)
    }

    @Test
    fun athleticProgressSpring_providesNaturalFeedback() {
        // Verify progress spring has appropriate bounce for satisfying feedback
        val spring = LiftrixAnimations.athleticProgressSpring
        
        assert(spring.dampingRatio == Spring.DampingRatioLowBouncy)
        assert(spring.stiffness == Spring.StiffnessMedium)
        assert(spring.visibilityThreshold == 0.01f)
    }

    @Test
    fun athleticButtonPress_animatesCorrectly() {
        var isPressed by mutableStateOf(false)
        
        composeTestRule.setContent {
            LiftrixTheme {
                AthleticButtonPress(pressed = isPressed) {
                    Button(
                        onClick = { isPressed = !isPressed },
                        modifier = Modifier.testTag("athletic_button")
                    ) {
                        Text("Athletic Button")
                    }
                }
            }
        }

        // Verify button is displayed
        composeTestRule
            .onNodeWithTag("athletic_button")
            .assertIsDisplayed()

        // Test press interaction
        composeTestRule
            .onNodeWithTag("athletic_button")
            .performClick()
            
        // Button should still be displayed after interaction
        composeTestRule
            .onNodeWithTag("athletic_button")
            .assertIsDisplayed()
    }

    @Test
    fun enhancedAthleticButtonPress_withGlowEffect() {
        var isPressed by mutableStateOf(false)
        
        composeTestRule.setContent {
            LiftrixTheme {
                EnhancedAthleticButtonPress(
                    pressed = isPressed,
                    glowEnabled = true
                ) {
                    Button(
                        onClick = { isPressed = !isPressed },
                        modifier = Modifier.testTag("enhanced_athletic_button")
                    ) {
                        Text("Enhanced Athletic Button")
                    }
                }
            }
        }

        // Verify enhanced button is displayed
        composeTestRule
            .onNodeWithTag("enhanced_athletic_button")
            .assertIsDisplayed()

        // Test press interaction with glow
        composeTestRule
            .onNodeWithTag("enhanced_athletic_button")
            .performClick()
            
        // Button should maintain display with glow effects
        composeTestRule
            .onNodeWithTag("enhanced_athletic_button")
            .assertIsDisplayed()
    }

    @Test
    fun athleticPressScale_modifier_animatesCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                var isPressed by remember { mutableStateOf(false) }
                
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .athleticPressScale()
                        .testTag("athletic_press_scale_box")
                ) {
                    Button(
                        onClick = { isPressed = !isPressed }
                    ) {
                        Text("Press Scale Test")
                    }
                }
            }
        }

        // Verify the component renders correctly
        composeTestRule
            .onNodeWithTag("athletic_press_scale_box")
            .assertIsDisplayed()
    }

    @Test
    fun athleticProgressAnimation_completesSuccessfully() {
        composeTestRule.setContent {
            LiftrixTheme {
                var progress by remember { mutableStateOf(0) }
                
                LaunchedEffect(Unit) {
                    // Simulate progress animation
                    for (i in 0..100 step 10) {
                        progress = i
                        delay(50) // 20fps for test speed
                    }
                }
                
                val animatedProgress = LiftrixAnimationUtils.athleticAnimatedProgress(
                    targetValue = progress,
                    label = "test_progress"
                )
                
                Text(
                    text = "Progress: $animatedProgress%",
                    modifier = Modifier.testTag("progress_text")
                )
            }
        }

        // Verify progress text is displayed
        composeTestRule
            .onNodeWithTag("progress_text")
            .assertIsDisplayed()
    }

    @Test
    fun athleticEntranceAnimation_appearsCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                var isVisible by remember { mutableStateOf(true) }
                
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .athleticEntranceAnimation(visible = isVisible)
                        .testTag("entrance_animation_box")
                ) {
                    Text("Entrance Animation Test")
                }
            }
        }

        // Verify the animated component is displayed
        composeTestRule
            .onNodeWithTag("entrance_animation_box")
            .assertIsDisplayed()
    }

    @Test
    fun athleticTimerPulse_hasCorrectTiming() {
        // Verify athletic timer pulse has appropriate timing for training intensity
        val timerPulse = LiftrixAnimations.athleticTimerPulseSpec
        
        // Should have longer duration for confident rhythm
        assert(timerPulse.animation.durationMillis == 1200)
    }

    @Test
    fun athleticCardEntrance_hasNaturalMotion() {
        // Verify athletic card entrance provides weight-shifting feel
        val cardEntrance = LiftrixAnimations.athleticCardEntranceSpec
        
        // Should have medium duration for natural motion
        assert(cardEntrance.durationMillis == LiftrixAnimations.MEDIUM)
    }

    @Test
    fun athleticCheckmarkAnimation_providesQuickBounce() {
        // Verify checkmark animation has quick bounce for satisfying feedback
        val checkmark = LiftrixAnimations.athleticCheckmarkSpec
        
        assert(checkmark.dampingRatio == Spring.DampingRatioLowBouncy)
        assert(checkmark.stiffness == Spring.StiffnessHigh)
    }

    @Test
    fun athleticModalAnimation_avoidsJarringTransitions() {
        // Verify modal animation provides smooth vertical lift
        val modal = LiftrixAnimations.athleticModalSpec
        
        assert(modal.dampingRatio == Spring.DampingRatioLowBouncy)
        assert(modal.stiffness == Spring.StiffnessMedium)
    }

    @Test
    fun athleticScreenTransition_hasConfidentMotion() {
        // Verify screen transition provides confident horizontal slides
        val screenTransition = LiftrixAnimations.athleticScreenTransitionSpec
        
        assert(screenTransition.dampingRatio == Spring.DampingRatioNoBouncy)
        assert(screenTransition.stiffness == Spring.StiffnessHigh)
    }

    @Test
    fun animationDurations_areOptimizedFor60fps() {
        // Verify animation durations support smooth 60fps performance
        assert(LiftrixAnimations.MICRO == 100) // 6 frames at 60fps
        assert(LiftrixAnimations.FAST == 150) // 9 frames at 60fps
        assert(LiftrixAnimations.STANDARD == 300) // 18 frames at 60fps
        
        // Micro interactions should be very quick
        assert(LiftrixAnimations.MICRO < LiftrixAnimations.FAST)
        assert(LiftrixAnimations.FAST < LiftrixAnimations.STANDARD)
    }

    @Test
    fun visibilityThresholds_providePerformanceOptimization() {
        // Verify visibility thresholds are set for performance optimization
        assert(LiftrixAnimations.LiftrixSpring.visibilityThreshold == 0.01f)
        assert(LiftrixAnimations.athleticMicroSpring.visibilityThreshold == 0.001f)
        assert(LiftrixAnimations.athleticProgressSpring.visibilityThreshold == 0.01f)
        
        // Micro interactions should have tighter thresholds for precision
        assert(LiftrixAnimations.athleticMicroSpring.visibilityThreshold < 
               LiftrixAnimations.LiftrixSpring.visibilityThreshold)
    }
}

/**
 * Composable wrapper for testing theme integration
 */
@Composable
private fun LiftrixTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        content()
    }
} 