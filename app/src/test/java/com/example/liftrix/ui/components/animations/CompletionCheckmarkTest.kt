package com.example.liftrix.ui.components.animations

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.LiftrixTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprehensive test suite for CompletionCheckmark component.
 * Tests animation behavior, haptic feedback, accessibility, and visual states.
 */
@RunWith(AndroidJUnit4::class)
class CompletionCheckmarkTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        // Set up test environment
    }

    @Test
    fun completionCheckmark_whenCompleted_isDisplayed() {
        composeTestRule.setContent {
            LiftrixTheme {
                CompletionCheckmark(
                    completed = true
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Completed")
            .assertIsDisplayed()
    }

    @Test
    fun completionCheckmark_whenNotCompleted_showsNotCompletedState() {
        composeTestRule.setContent {
            LiftrixTheme {
                CompletionCheckmark(
                    completed = false
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Not completed")
            .assertIsDisplayed()
    }

    @Test
    fun completionCheckmark_withCustomSize_rendersCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                CompletionCheckmark(
                    completed = true,
                    size = 48.dp
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Completed")
            .assertIsDisplayed()
    }

    @Test
    fun completionCheckmark_withCustomColor_rendersCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                CompletionCheckmark(
                    completed = true,
                    color = Color.Red
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Completed")
            .assertIsDisplayed()
    }

    @Test
    fun completionCheckmark_withBackground_rendersCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                CompletionCheckmark(
                    completed = true,
                    backgroundColor = LiftrixColors.LiftrixTeal
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Completed")
            .assertIsDisplayed()
    }

    @Test
    fun completionCheckmark_withDisabledHaptic_stillFunctions() {
        var callbackTriggered = false
        
        composeTestRule.setContent {
            LiftrixTheme {
                CompletionCheckmark(
                    completed = true,
                    onComplete = { callbackTriggered = true },
                    hapticEnabled = false
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Completed")
            .assertIsDisplayed()

        // Wait for composition and animation
        composeTestRule.waitForIdle()

        assert(callbackTriggered) { "Callback should be triggered even with haptic disabled" }
    }

    @Test
    fun completionCheckmark_transitionFromIncompleteToComplete_triggersCallback() {
        var callbackTriggered = false
        var completed = false

        composeTestRule.setContent {
            LiftrixTheme {
                CompletionCheckmark(
                    completed = completed,
                    onComplete = { callbackTriggered = true }
                )
            }
        }

        // Initially not completed
        composeTestRule
            .onNodeWithContentDescription("Not completed")
            .assertIsDisplayed()

        // Change to completed
        composeTestRule.runOnUiThread {
            completed = true
        }

        composeTestRule.waitForIdle()

        assert(callbackTriggered) { "Callback should be triggered when transitioning to completed" }
    }

    @Test
    fun completionCheckmarkWithBackground_rendersCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                CompletionCheckmarkWithBackground(
                    completed = true
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Completed")
            .assertIsDisplayed()
    }

    @Test
    fun completionCheckmarkWithBackground_withCustomColors_rendersCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                CompletionCheckmarkWithBackground(
                    completed = true,
                    checkmarkColor = Color.White,
                    backgroundColor = Color.Blue
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Completed")
            .assertIsDisplayed()
    }

    @Test
    fun completionCheckmark_animationPerformance_completesWithinReasonableTime() {
        var animationStartTime = 0L
        var callbackTriggered = false

        composeTestRule.setContent {
            LiftrixTheme {
                CompletionCheckmark(
                    completed = true,
                    onComplete = { 
                        callbackTriggered = true
                        animationStartTime = System.currentTimeMillis()
                    }
                )
            }
        }

        composeTestRule.waitForIdle()

        val animationEndTime = System.currentTimeMillis()
        val animationDuration = animationEndTime - animationStartTime

        assert(callbackTriggered) { "Animation callback should be triggered" }
        // Animation should complete within reasonable time (allowing for bouncy spring)
        assert(animationDuration < 2000) { "Animation should complete within 2 seconds" }
    }

    @Test
    fun completionCheckmark_accessibilityProperties_areSetCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                CompletionCheckmark(
                    completed = true
                )
            }
        }

        // Verify accessibility content description is present and correct
        composeTestRule
            .onNodeWithContentDescription("Completed")
            .assertIsDisplayed()
    }

    @Test
    fun completionCheckmark_withLargeSize_maintainsProportions() {
        composeTestRule.setContent {
            LiftrixTheme {
                CompletionCheckmark(
                    completed = true,
                    size = 64.dp,
                    strokeWidth = 4.dp
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Completed")
            .assertIsDisplayed()
    }

    @Test
    fun completionCheckmark_stateReset_fromCompletedToIncomplete() {
        var completed = true

        composeTestRule.setContent {
            LiftrixTheme {
                CompletionCheckmark(
                    completed = completed
                )
            }
        }

        // Initially completed
        composeTestRule
            .onNodeWithContentDescription("Completed")
            .assertIsDisplayed()

        // Reset to incomplete
        composeTestRule.runOnUiThread {
            completed = false
        }

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithContentDescription("Not completed")
            .assertIsDisplayed()
    }

    @Test
    fun completionCheckmark_callbackInvocation_onlyTriggeredOnce() {
        var callbackCount = 0
        var completed = false

        composeTestRule.setContent {
            LiftrixTheme {
                CompletionCheckmark(
                    completed = completed,
                    onComplete = { callbackCount++ }
                )
            }
        }

        // Change to completed
        composeTestRule.runOnUiThread {
            completed = true
        }

        composeTestRule.waitForIdle()

        // Trigger recomposition without changing completed state
        composeTestRule.runOnUiThread {
            // Force recomposition
        }

        composeTestRule.waitForIdle()

        assert(callbackCount == 1) { "Callback should only be triggered once per completion transition" }
    }
} 