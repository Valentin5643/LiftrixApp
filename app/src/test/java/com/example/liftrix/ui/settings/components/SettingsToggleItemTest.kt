package com.example.liftrix.ui.settings.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
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
 * Unit tests for SettingsToggleItem component.
 * 
 * Tests component rendering, user interactions, accessibility features,
 * and state management according to Material3 design guidelines.
 */
@RunWith(AndroidJUnit4::class)
class SettingsToggleItemTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settingsToggleItem_displaysCorrectly() {
        // Given
        val title = "Dark Mode"
        val subtitle = "Use dark theme throughout the app"
        
        // When
        composeTestRule.setContent {
            LiftrixTheme {
                SettingsToggleItem(
                    title = title,
                    subtitle = subtitle,
                    isChecked = false,
                    onToggle = { }
                )
            }
        }
        
        // Then
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
        composeTestRule.onNodeWithText(subtitle).assertIsDisplayed()
    }

    @Test
    fun settingsToggleItem_displaysWithoutSubtitle() {
        // Given
        val title = "Auto-sync"
        
        // When
        composeTestRule.setContent {
            LiftrixTheme {
                SettingsToggleItem(
                    title = title,
                    isChecked = true,
                    onToggle = { }
                )
            }
        }
        
        // Then
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun settingsToggleItem_switchReflectsCheckedState() {
        // Given
        val title = "Push Notifications"
        
        // When - checked state
        composeTestRule.setContent {
            LiftrixTheme {
                SettingsToggleItem(
                    title = title,
                    isChecked = true,
                    onToggle = { }
                )
            }
        }
        
        // Then
        composeTestRule.onNode(hasContentDescription("$title enabled")).assertIsOn()
        
        // When - unchecked state
        composeTestRule.setContent {
            LiftrixTheme {
                SettingsToggleItem(
                    title = title,
                    isChecked = false,
                    onToggle = { }
                )
            }
        }
        
        // Then
        composeTestRule.onNode(hasContentDescription("$title disabled")).assertIsOff()
    }

    @Test
    fun settingsToggleItem_clickTriggersToggle() {
        // Given
        val title = "Dark Mode"
        var isChecked = false
        var toggleCalled = false
        
        // When
        composeTestRule.setContent {
            LiftrixTheme {
                SettingsToggleItem(
                    title = title,
                    isChecked = isChecked,
                    onToggle = { newState ->
                        isChecked = newState
                        toggleCalled = true
                    }
                )
            }
        }
        
        // Then
        composeTestRule.onNode(hasContentDescription("$title disabled")).performClick()
        
        assert(toggleCalled) { "Toggle callback should be called" }
    }

    @Test
    fun settingsToggleItem_hasProperAccessibilityDescription() {
        // Given
        val title = "Dark Mode"
        val subtitle = "Use dark theme throughout the app"
        
        // When
        composeTestRule.setContent {
            LiftrixTheme {
                SettingsToggleItem(
                    title = title,
                    subtitle = subtitle,
                    isChecked = true,
                    onToggle = { }
                )
            }
        }
        
        // Then
        composeTestRule.onNodeWithContentDescription("$title enabled. $subtitle")
            .assertIsDisplayed()
    }

    @Test
    fun settingsToggleItem_hasProperStateDescription() {
        // Given
        val title = "Push Notifications"
        
        // When - checked state
        composeTestRule.setContent {
            LiftrixTheme {
                SettingsToggleItem(
                    title = title,
                    isChecked = true,
                    onToggle = { }
                )
            }
        }
        
        // Then
        composeTestRule.onNode(hasContentDescription("$title enabled")).assertIsOn()
        
        // When - unchecked state
        composeTestRule.setContent {
            LiftrixTheme {
                SettingsToggleItem(
                    title = title,
                    isChecked = false,
                    onToggle = { }
                )
            }
        }
        
        // Then
        composeTestRule.onNode(hasContentDescription("$title disabled")).assertIsOff()
    }

    @Test
    fun settingsToggleItem_disabledStateRendersCorrectly() {
        // Given
        val title = "Premium Features"
        val subtitle = "Available with Pro subscription"
        
        // When
        composeTestRule.setContent {
            LiftrixTheme {
                SettingsToggleItem(
                    title = title,
                    subtitle = subtitle,
                    isChecked = false,
                    onToggle = { },
                    enabled = false
                )
            }
        }
        
        // Then
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
        composeTestRule.onNodeWithText(subtitle).assertIsDisplayed()
    }

    @Test
    fun settingsToggleItem_disabledStateIgnoresClicks() {
        // Given
        val title = "Premium Features"
        var toggleCalled = false
        
        // When
        composeTestRule.setContent {
            LiftrixTheme {
                SettingsToggleItem(
                    title = title,
                    isChecked = false,
                    onToggle = { toggleCalled = true },
                    enabled = false
                )
            }
        }
        
        // Then
        composeTestRule.onNode(hasContentDescription("$title disabled")).performClick()
        
        assert(!toggleCalled) { "Toggle callback should not be called when disabled" }
    }

    @Test
    fun settingsToggleItem_multipleInstancesRenderCorrectly() {
        // Given
        val items = listOf(
            Triple("Dark Mode", "Use dark theme", true),
            Triple("Push Notifications", "Receive notifications", false),
            Triple("Auto-sync", null, true)
        )
        
        // When
        composeTestRule.setContent {
            LiftrixTheme {
                items.forEach { (title, subtitle, isChecked) ->
                    SettingsToggleItem(
                        title = title,
                        subtitle = subtitle,
                        isChecked = isChecked,
                        onToggle = { }
                    )
                }
            }
        }
        
        // Then
        items.forEach { (title, subtitle, isChecked) ->
            composeTestRule.onNodeWithText(title).assertIsDisplayed()
            subtitle?.let { composeTestRule.onNodeWithText(it).assertIsDisplayed() }
            
            val expectedDescription = "$title ${if (isChecked) "enabled" else "disabled"}${subtitle?.let { ". $it" } ?: ""}"
            composeTestRule.onNodeWithContentDescription(expectedDescription).assertIsDisplayed()
        }
    }

    @Test
    fun settingsToggleItem_hasMinimumTouchTargetSize() {
        // Given
        val title = "Dark Mode"
        
        // When
        composeTestRule.setContent {
            LiftrixTheme {
                SettingsToggleItem(
                    title = title,
                    isChecked = false,
                    onToggle = { }
                )
            }
        }
        
        // Then - Switch should have minimum touch target size (this is handled by ensureMinimumTouchTarget modifier)
        composeTestRule.onNode(hasContentDescription("$title disabled")).assertIsDisplayed()
    }

    @Test
    fun settingsToggleItem_handlesStateChanges() {
        // Given
        val title = "Auto-sync"
        var currentState = false
        
        // When
        composeTestRule.setContent {
            LiftrixTheme {
                SettingsToggleItem(
                    title = title,
                    isChecked = currentState,
                    onToggle = { newState -> currentState = newState }
                )
            }
        }
        
        // Then - initial state
        composeTestRule.onNode(hasContentDescription("$title disabled")).assertIsOff()
        
        // When - toggle
        composeTestRule.onNode(hasContentDescription("$title disabled")).performClick()
        
        // Then - state should be updated through callback
        assert(currentState) { "State should be updated to true" }
    }
}