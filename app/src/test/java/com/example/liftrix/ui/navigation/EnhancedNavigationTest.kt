package com.example.liftrix.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.ui.theme.LiftrixTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test suite for enhanced bottom navigation functionality
 * Verifies navigation styling, interactions, and accessibility
 */
@RunWith(AndroidJUnit4::class)
class EnhancedNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun enhancedBottomNavigation_displaysAllTabs() {
        composeTestRule.setContent {
            LiftrixTheme {
                EnhancedBottomNavigation(
                    currentDestination = null,
                    onNavigate = { }
                )
            }
        }

        // Verify all navigation tabs are displayed
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        composeTestRule.onNodeWithText("Workout").assertIsDisplayed()
        composeTestRule.onNodeWithText("Progress").assertIsDisplayed()
        composeTestRule.onNodeWithText("Coach").assertIsDisplayed()
    }

    @Test
    fun enhancedBottomNavigation_hasProperAccessibility() {
        composeTestRule.setContent {
            LiftrixTheme {
                EnhancedBottomNavigation(
                    currentDestination = null,
                    onNavigate = { }
                )
            }
        }

        // Verify accessibility content descriptions
        composeTestRule.onNodeWithContentDescription("Home tab").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Workout tab").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Progress tab").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Coach tab").assertIsDisplayed()
    }

    @Test
    fun enhancedBottomNavigation_handlesTabSelection() {
        var selectedRoute = ""
        
        composeTestRule.setContent {
            LiftrixTheme {
                EnhancedBottomNavigation(
                    currentDestination = null,
                    onNavigate = { route -> selectedRoute = route }
                )
            }
        }

        // Test navigation to workout tab
        composeTestRule.onNodeWithText("Workout").performClick()
        assert(selectedRoute == "workout")
    }

    @Test
    fun enhancedBottomNavigation_maintainsVisualConsistency() {
        composeTestRule.setContent {
            LiftrixTheme {
                EnhancedBottomNavigation(
                    currentDestination = null,
                    onNavigate = { }
                )
            }
        }

        // Verify visual elements are present
        // This test ensures the enhanced styling doesn't break basic functionality
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        composeTestRule.onNodeWithText("Workout").assertIsDisplayed()
        composeTestRule.onNodeWithText("Progress").assertIsDisplayed()
        composeTestRule.onNodeWithText("Coach").assertIsDisplayed()
    }
} 