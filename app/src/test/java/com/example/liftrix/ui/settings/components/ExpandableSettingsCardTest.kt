package com.example.liftrix.ui.settings.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
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
 * Unit tests for ExpandableSettingsCard component.
 * Tests expand/collapse behavior, accessibility, and user interactions.
 */
@RunWith(AndroidJUnit4::class)
class ExpandableSettingsCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun expandableSettingsCard_initiallyCollapsed_doesNotShowContent() {
        // Given
        val testContent = "Test content"
        
        // When
        composeTestRule.setContent {
            LiftrixTheme {
                ExpandableSettingsCard(
                    title = "Test Settings",
                    isExpanded = false,
                    onToggle = { }
                ) {
                    androidx.compose.material3.Text(testContent)
                }
            }
        }

        // Then
        composeTestRule.onNodeWithText("Test Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText(testContent).assertIsNotDisplayed()
        composeTestRule.onNodeWithContentDescription("Expand Test Settings").assertIsDisplayed()
    }

    @Test
    fun expandableSettingsCard_initiallyExpanded_showsContent() {
        // Given
        val testContent = "Test content"
        
        // When
        composeTestRule.setContent {
            LiftrixTheme {
                ExpandableSettingsCard(
                    title = "Test Settings",
                    isExpanded = true,
                    onToggle = { }
                ) {
                    androidx.compose.material3.Text(testContent)
                }
            }
        }

        // Then
        composeTestRule.onNodeWithText("Test Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText(testContent).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Collapse Test Settings").assertIsDisplayed()
    }

    @Test
    fun expandableSettingsCard_clickToggle_invokesOnToggle() {
        // Given
        var toggleCount = 0
        
        // When
        composeTestRule.setContent {
            LiftrixTheme {
                ExpandableSettingsCard(
                    title = "Test Settings",
                    isExpanded = false,
                    onToggle = { toggleCount++ }
                ) {
                    androidx.compose.material3.Text("Test content")
                }
            }
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Expand Test Settings").performClick()
        assert(toggleCount == 1)
    }

    @Test
    fun expandableSettingsCard_clickCard_invokesOnToggle() {
        // Given
        var toggleCount = 0
        
        // When
        composeTestRule.setContent {
            LiftrixTheme {
                ExpandableSettingsCard(
                    title = "Test Settings",
                    isExpanded = false,
                    onToggle = { toggleCount++ }
                ) {
                    androidx.compose.material3.Text("Test content")
                }
            }
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Test Settings settings collapsed").performClick()
        assert(toggleCount == 1)
    }

    @Test
    fun expandableSettingsCard_accessibilityDescriptions_areCorrect() {
        // When collapsed
        composeTestRule.setContent {
            LiftrixTheme {
                ExpandableSettingsCard(
                    title = "Test Settings",
                    isExpanded = false,
                    onToggle = { }
                ) {
                    androidx.compose.material3.Text("Test content")
                }
            }
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Test Settings settings collapsed").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Expand Test Settings").assertIsDisplayed()

        // When expanded
        composeTestRule.setContent {
            LiftrixTheme {
                ExpandableSettingsCard(
                    title = "Test Settings",
                    isExpanded = true,
                    onToggle = { }
                ) {
                    androidx.compose.material3.Text("Test content")
                }
            }
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Test Settings settings expanded").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Collapse Test Settings").assertIsDisplayed()
    }

    @Test
    fun expandableSettingsCard_titleDisplayed_correctly() {
        // Given
        val testTitle = "General Settings"
        
        // When
        composeTestRule.setContent {
            LiftrixTheme {
                ExpandableSettingsCard(
                    title = testTitle,
                    isExpanded = false,
                    onToggle = { }
                ) {
                    androidx.compose.material3.Text("Test content")
                }
            }
        }

        // Then
        composeTestRule.onNodeWithText(testTitle).assertIsDisplayed()
    }

    @Test
    fun expandableSettingsCard_contentDisplayed_whenExpanded() {
        // Given
        val testContent = "Dark Mode, Notifications, Language"
        
        // When
        composeTestRule.setContent {
            LiftrixTheme {
                ExpandableSettingsCard(
                    title = "Test Settings",
                    isExpanded = true,
                    onToggle = { }
                ) {
                    androidx.compose.material3.Text(testContent)
                }
            }
        }

        // Then
        composeTestRule.onNodeWithText(testContent).assertIsDisplayed()
    }

    @Test
    fun expandableSettingsCard_contentHidden_whenCollapsed() {
        // Given
        val testContent = "Dark Mode, Notifications, Language"
        
        // When
        composeTestRule.setContent {
            LiftrixTheme {
                ExpandableSettingsCard(
                    title = "Test Settings",
                    isExpanded = false,
                    onToggle = { }
                ) {
                    androidx.compose.material3.Text(testContent)
                }
            }
        }

        // Then
        composeTestRule.onNodeWithText(testContent).assertIsNotDisplayed()
    }
}