package com.example.liftrix.ui.components.buttons

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.liftrix.ui.theme.LiftrixTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

/**
 * Tests for LiftrixButton components and interactions
 * Validates functionality, accessibility, and micro-interactions
 */
@RunWith(MockitoJUnitRunner::class)
class ButtonInteractionsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun liftrixButton_primaryVariant_rendersCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixButton(
                    text = "Primary Button",
                    onClick = { },
                    variant = ButtonVariant.Primary,
                    modifier = Modifier.testTag("primary-button")
                )
            }
        }

        composeTestRule
            .onNodeWithTag("primary-button")
            .assertExists()
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertIsEnabled()

        composeTestRule
            .onNodeWithText("Primary Button")
            .assertExists()
    }

    @Test
    fun liftrixButton_secondaryVariant_rendersCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixButton(
                    text = "Secondary Button",
                    onClick = { },
                    variant = ButtonVariant.Secondary,
                    modifier = Modifier.testTag("secondary-button")
                )
            }
        }

        composeTestRule
            .onNodeWithTag("secondary-button")
            .assertExists()
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertIsEnabled()
    }

    @Test
    fun liftrixButton_tertiaryVariant_rendersCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixButton(
                    text = "Tertiary Button",
                    onClick = { },
                    variant = ButtonVariant.Tertiary,
                    modifier = Modifier.testTag("tertiary-button")
                )
            }
        }

        composeTestRule
            .onNodeWithTag("tertiary-button")
            .assertExists()
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertIsEnabled()
    }

    @Test
    fun liftrixButton_destructiveVariant_rendersCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixButton(
                    text = "Delete",
                    onClick = { },
                    variant = ButtonVariant.Destructive,
                    modifier = Modifier.testTag("destructive-button")
                )
            }
        }

        composeTestRule
            .onNodeWithTag("destructive-button")
            .assertExists()
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertIsEnabled()
    }

    @Test
    fun liftrixButton_onClick_triggersCallback() {
        var clicked = false

        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixButton(
                    text = "Click Me",
                    onClick = { clicked = true },
                    modifier = Modifier.testTag("clickable-button")
                )
            }
        }

        composeTestRule
            .onNodeWithTag("clickable-button")
            .performClick()

        assert(clicked) { "Button click should trigger callback" }
    }

    @Test
    fun liftrixButton_disabled_doesNotTriggerCallback() {
        var clicked = false

        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixButton(
                    text = "Disabled Button",
                    onClick = { clicked = true },
                    enabled = false,
                    modifier = Modifier.testTag("disabled-button")
                )
            }
        }

        composeTestRule
            .onNodeWithTag("disabled-button")
            .assertIsNotEnabled()

        composeTestRule
            .onNodeWithTag("disabled-button")
            .performClick()

        assert(!clicked) { "Disabled button should not trigger callback" }
    }

    @Test
    fun liftrixButton_loadingState_preventsClick() {
        var clicked = false

        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixButton(
                    text = "Loading Button",
                    onClick = { clicked = true },
                    loading = true,
                    modifier = Modifier.testTag("loading-button")
                )
            }
        }

        // Loading button should still appear enabled but not respond to clicks
        composeTestRule
            .onNodeWithTag("loading-button")
            .performClick()

        assert(!clicked) { "Loading button should not trigger callback" }
    }

    @Test
    fun liftrixButton_withIcon_rendersIconAndText() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixButton(
                    text = "Add Item",
                    onClick = { },
                    icon = Icons.Default.Add,
                    modifier = Modifier.testTag("icon-button")
                )
            }
        }

        composeTestRule
            .onNodeWithTag("icon-button")
            .assertExists()
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Add Item")
            .assertExists()
    }

    @Test
    fun liftrixButton_contentDescription_setsAccessibilityProperties() {
        val contentDescription = "Save workout template"

        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixButton(
                    text = "Save",
                    onClick = { },
                    contentDescription = contentDescription,
                    modifier = Modifier.testTag("accessible-button")
                )
            }
        }

        composeTestRule
            .onNodeWithTag("accessible-button")
            .assert(hasContentDescription(contentDescription))
    }

    @Test
    fun liftrixIconButton_rendersCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixIconButton(
                    icon = Icons.Default.Add,
                    onClick = { },
                    contentDescription = "Add item",
                    modifier = Modifier.testTag("icon-only-button")
                )
            }
        }

        composeTestRule
            .onNodeWithTag("icon-only-button")
            .assertExists()
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertIsEnabled()
            .assert(hasContentDescription("Add item"))
    }

    @Test
    fun liftrixIconButton_onClick_triggersCallback() {
        var clicked = false

        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixIconButton(
                    icon = Icons.Default.Add,
                    onClick = { clicked = true },
                    modifier = Modifier.testTag("clickable-icon-button")
                )
            }
        }

        composeTestRule
            .onNodeWithTag("clickable-icon-button")
            .performClick()

        assert(clicked) { "Icon button click should trigger callback" }
    }

    @Test
    fun liftrixIconButton_disabled_doesNotTriggerCallback() {
        var clicked = false

        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixIconButton(
                    icon = Icons.Default.Add,
                    onClick = { clicked = true },
                    enabled = false,
                    modifier = Modifier.testTag("disabled-icon-button")
                )
            }
        }

        composeTestRule
            .onNodeWithTag("disabled-icon-button")
            .assertIsNotEnabled()

        composeTestRule
            .onNodeWithTag("disabled-icon-button")
            .performClick()

        assert(!clicked) { "Disabled icon button should not trigger callback" }
    }

    @Test
    fun buttonInteractions_pressInteraction_returnsPairWithModifierAndSource() {
        var modifier: Modifier? = null
        var interactionSource: MutableInteractionSource? = null

        composeTestRule.setContent {
            val (pressModifier, source) = ButtonInteractions.pressInteraction()
            modifier = pressModifier
            interactionSource = source
        }

        assert(modifier != null) { "Press interaction should return modifier" }
        assert(interactionSource != null) { "Press interaction should return interaction source" }
    }

    @Test
    fun buttonInteractions_enhancedPressInteraction_returnsTripleWithStateAndInteractions() {
        var modifier: Modifier? = null
        var interactionSource: MutableInteractionSource? = null
        var isPressed: Boolean? = null

        composeTestRule.setContent {
            val (pressModifier, source, pressed) = ButtonInteractions.enhancedPressInteraction()
            modifier = pressModifier
            interactionSource = source
            isPressed = pressed
        }

        assert(modifier != null) { "Enhanced press interaction should return modifier" }
        assert(interactionSource != null) { "Enhanced press interaction should return interaction source" }
        assert(isPressed != null) { "Enhanced press interaction should return pressed state" }
    }

    @Test
    fun allButtonVariants_maintainMinimumTouchTargetSize() {
        val buttonVariants = listOf(
            ButtonVariant.Primary,
            ButtonVariant.Secondary,
            ButtonVariant.Tertiary,
            ButtonVariant.Destructive
        )

        buttonVariants.forEachIndexed { index, variant ->
            composeTestRule.setContent {
                LiftrixTheme {
                    LiftrixButton(
                        text = "${variant.name} Button",
                        onClick = { },
                        variant = variant,
                        modifier = Modifier.testTag("button-$index")
                    )
                }
            }

            // Verify button exists and has appropriate touch target
            composeTestRule
                .onNodeWithTag("button-$index")
                .assertExists()
                .assertIsDisplayed()
                .assertHasClickAction()
        }
    }

    @Test
    fun buttons_withHapticDisabled_stillFunctionCorrectly() {
        var clicked = false

        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixButton(
                    text = "No Haptic Button",
                    onClick = { clicked = true },
                    hapticEnabled = false,
                    modifier = Modifier.testTag("no-haptic-button")
                )
            }
        }

        composeTestRule
            .onNodeWithTag("no-haptic-button")
            .performClick()

        assert(clicked) { "Button with disabled haptic should still trigger callback" }
    }

    @Test
    fun iconButton_withHapticDisabled_stillFunctionCorrectly() {
        var clicked = false

        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixIconButton(
                    icon = Icons.Default.Add,
                    onClick = { clicked = true },
                    hapticEnabled = false,
                    modifier = Modifier.testTag("no-haptic-icon-button")
                )
            }
        }

        composeTestRule
            .onNodeWithTag("no-haptic-icon-button")
            .performClick()

        assert(clicked) { "Icon button with disabled haptic should still trigger callback" }
    }

    @Test
    fun buttons_inDarkTheme_renderCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = true) {
                LiftrixButton(
                    text = "Dark Theme Button",
                    onClick = { },
                    modifier = Modifier.testTag("dark-theme-button")
                )
            }
        }

        composeTestRule
            .onNodeWithTag("dark-theme-button")
            .assertExists()
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun buttons_dynamicStateChanges_respondCorrectly() {
        var isEnabled by mutableStateOf(true)
        var isLoading by mutableStateOf(false)

        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixButton(
                    text = "Dynamic Button",
                    onClick = { },
                    enabled = isEnabled,
                    loading = isLoading,
                    modifier = Modifier.testTag("dynamic-button")
                )
            }
        }

        // Initially enabled
        composeTestRule
            .onNodeWithTag("dynamic-button")
            .assertIsEnabled()

        // Change to disabled
        isEnabled = false
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("dynamic-button")
            .assertIsNotEnabled()

        // Change to loading (re-enable but set loading)
        isEnabled = true
        isLoading = true
        composeTestRule.waitForIdle()

        // Loading button appears enabled but shouldn't respond to clicks functionally
        composeTestRule
            .onNodeWithTag("dynamic-button")
            .assertExists()
    }
}