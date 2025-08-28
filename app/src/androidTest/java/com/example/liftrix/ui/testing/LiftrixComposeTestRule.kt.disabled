package com.example.liftrix.ui.testing

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.liftrix.ui.theme.LiftrixTheme
import dagger.hilt.android.testing.HiltAndroidRule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

/**
 * Custom Compose Test Rule for Liftrix UI Tests
 * 
 * Provides standardized setup for all Compose UI tests including:
 * - Hilt dependency injection setup
 * - LiftrixTheme application
 * - Common test utilities and extensions
 * - Performance monitoring integration
 * - Accessibility testing helpers
 */
class LiftrixComposeTestRule {
    
    private val hiltRule = HiltAndroidRule(this)
    private val composeRule = createComposeRule()
    
    val testRule: TestRule = RuleChain
        .outerRule(hiltRule)
        .around(composeRule)
    
    /**
     * Sets content with LiftrixTheme applied
     */
    fun setThemedContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            LiftrixTheme {
                content()
            }
        }
    }
    
    /**
     * Waits for UI to be idle with extended timeout for animations
     */
    fun waitForAnimationsToComplete() {
        composeRule.waitForIdle()
        // Additional wait for Liftrix's 150ms interaction animations
        Thread.sleep(200)
    }
    
    /**
     * Provides access to the underlying ComposeContentTestRule
     */
    fun compose(): ComposeContentTestRule = composeRule
    
    /**
     * Injects Hilt dependencies for the test
     */
    fun inject() {
        hiltRule.inject()
    }
}

/**
 * Extension function to create LiftrixComposeTestRule easily
 */
fun createLiftrixComposeRule(): LiftrixComposeTestRule {
    return LiftrixComposeTestRule()
}