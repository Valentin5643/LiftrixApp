package com.example.liftrix.ui.progress

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.liftrix.ui.theme.LiftrixTheme
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for ProgressScreen placeholder composable.
 * 
 * Tests verify that the placeholder screen displays correctly with proper
 * content and follows Material3 design guidelines.
 */
class ProgressScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun progressScreen_displaysTitle() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressScreen()
            }
        }

        // Assert
        composeTestRule
            .onNodeWithText("Progress")
            .assertIsDisplayed()
    }

    @Test
    fun progressScreen_displaysComingSoonMessage() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressScreen()
            }
        }

        // Assert
        composeTestRule
            .onNodeWithText("Charts and analytics coming soon")
            .assertIsDisplayed()
    }

    @Test
    fun progressScreen_displaysAllContent() {
        // Arrange & Act
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressScreen()
            }
        }

        // Assert - Verify both title and subtitle are displayed
        composeTestRule
            .onNodeWithText("Progress")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Charts and analytics coming soon")
            .assertIsDisplayed()
    }
} 