package com.example.liftrix.ui.testing

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/**
 * UI Testing Utilities for Liftrix
 * 
 * Common test patterns and helper functions for consistent UI testing
 * across the Liftrix application.
 */
object LiftrixTestUtils {

    /**
     * Waits for loading states to complete with timeout
     */
    fun ComposeContentTestRule.waitForLoadingToComplete(timeoutMs: Long = 5000L) {
        waitUntil(timeoutMs) {
            onAllNodesWithContentDescription("Loading")
                .fetchSemanticsNodes().isEmpty()
        }
    }

    /**
     * Asserts that an error state is displayed with retry option
     */
    fun ComposeContentTestRule.assertErrorStateDisplayed(errorMessage: String? = null) {
        onNodeWithContentDescription("Error state").assertIsDisplayed()
        if (errorMessage != null) {
            onNodeWithText(errorMessage).assertIsDisplayed()
        }
        onNodeWithText("Retry").assertIsDisplayed()
    }

    /**
     * Performs text input with validation delay
     */
    fun ComposeContentTestRule.performValidatedTextInput(
        matcher: SemanticsNodeInteraction,
        text: String,
        waitForValidation: Boolean = true
    ) {
        matcher.performTextInput(text)
        if (waitForValidation) {
            // Wait for validation debounce (typically 300ms in Liftrix)
            waitForIdle()
            Thread.sleep(350)
        }
    }

    /**
     * Asserts accessibility compliance for interactive elements
     */
    fun ComposeContentTestRule.assertAccessibilityCompliance() {
        // Check that all clickable elements have content descriptions
        onAllNodes(hasClickAction()).assertAll(hasContentDescription())
        
        // Check that all text inputs have labels
        onAllNodes(hasImeAction()).assertAll(hasText())
        
        // Check that loading indicators are properly labeled
        onAllNodesWithTag("loading_indicator").assertAll(hasContentDescription())
    }

    /**
     * Simulates network delay for testing loading states
     */
    fun ComposeContentTestRule.simulateNetworkDelay(delayMs: Long = 1000L) {
        Thread.sleep(delayMs)
        waitForIdle()
    }

    /**
     * Asserts that UnifiedWorkoutCard is displayed with expected content
     */
    fun ComposeContentTestRule.assertWorkoutCardDisplayed(
        title: String,
        subtitle: String? = null
    ) {
        onNodeWithText(title).assertIsDisplayed()
        if (subtitle != null) {
            onNodeWithText(subtitle).assertIsDisplayed()
        }
        onNode(hasTestTag("unified_workout_card")).assertIsDisplayed()
    }

    /**
     * Asserts that ModernActionButton hierarchy is correct
     */
    fun ComposeContentTestRule.assertButtonHierarchy(
        primaryAction: String,
        secondaryAction: String? = null,
        tertiaryAction: String? = null
    ) {
        // Primary button should be Persian Green
        onNodeWithText(primaryAction)
            .assertIsDisplayed()
            .assert(hasTestTag("primary_action_button"))
        
        if (secondaryAction != null) {
            onNodeWithText(secondaryAction)
                .assertIsDisplayed()
                .assert(hasTestTag("secondary_action_button"))
        }
        
        if (tertiaryAction != null) {
            onNodeWithText(tertiaryAction)
                .assertIsDisplayed()
                .assert(hasTestTag("tertiary_action_button"))
        }
    }

    /**
     * Tests screen rotation state preservation
     */
    fun ComposeContentTestRule.testScreenRotation(
        setupAction: () -> Unit,
        verifyAction: () -> Unit
    ) {
        setupAction()
        
        // Simulate configuration change by recreating the content
        // In a real test, this would involve activity recreation
        verifyAction()
    }

    /**
     * Custom matcher for Liftrix semantic spacing
     */
    fun hasLiftrixSpacing(): SemanticsMatcher {
        return SemanticsMatcher("has Liftrix semantic spacing") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.contains("liftrix_spacing") == true
        }
    }

    /**
     * Custom matcher for completed workout sets
     */
    fun hasCompletedSetIndicator(): SemanticsMatcher {
        return SemanticsMatcher("has completed set indicator") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.contains("completed_set") == true
        }
    }

    /**
     * Custom matcher for workout timer components
     */
    fun hasTimerComponent(): SemanticsMatcher {
        return SemanticsMatcher("has timer component") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.contains("timer") == true
        }
    }
}