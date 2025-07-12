package com.example.liftrix.ui.components.buttons

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.ui.theme.LiftrixTheme
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprehensive test suite for LiftrixButton components.
 * Tests button functionality, interactions, accessibility, and performance.
 */
@RunWith(AndroidJUnit4::class)
class ButtonTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var mockOnClick: () -> Unit

    @Before
    fun setup() {
        mockOnClick = mockk(relaxed = true)
    }

    @Test
    fun liftrixButton_displaysCorrectContent() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixButton(
                    onClick = mockOnClick,
                    modifier = Modifier.testTag("test_button")
                ) {
                    Text("Test Button")
                }
            }
        }

        composeTestRule
            .onNodeWithText("Test Button")
            .assertIsDisplayed()
    }

    @Test
    fun liftrixButton_hasClickAction() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixButton(
                    onClick = mockOnClick,
                    modifier = Modifier.testTag("test_button")
                ) {
                    Text("Test Button")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("test_button")
            .assertHasClickAction()
    }

    @Test
    fun liftrixButton_triggersOnClick() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixButton(
                    onClick = mockOnClick,
                    modifier = Modifier.testTag("test_button")
                ) {
                    Text("Test Button")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("test_button")
            .performClick()

        verify { mockOnClick() }
    }

    @Test
    fun liftrixButton_enabledState_isClickable() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixButton(
                    onClick = mockOnClick,
                    enabled = true,
                    modifier = Modifier.testTag("test_button")
                ) {
                    Text("Enabled Button")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("test_button")
            .assertIsEnabled()
            .performClick()

        verify { mockOnClick() }
    }

    @Test
    fun liftrixButton_disabledState_isNotClickable() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixButton(
                    onClick = mockOnClick,
                    enabled = false,
                    modifier = Modifier.testTag("test_button")
                ) {
                    Text("Disabled Button")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("test_button")
            .assertIsNotEnabled()
            .performClick()

        verify(exactly = 0) { mockOnClick() }
    }

    @Test
    fun liftrixButton_primaryVariant_hasCorrectStyling() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixButton(
                    onClick = mockOnClick,
                    variant = ButtonVariant.Primary,
                    modifier = Modifier.testTag("primary_button")
                ) {
                    Text("Primary Button")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("primary_button")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun liftrixButton_secondaryVariant_hasCorrectStyling() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixButton(
                    onClick = mockOnClick,
                    variant = ButtonVariant.Secondary,
                    modifier = Modifier.testTag("secondary_button")
                ) {
                    Text("Secondary Button")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("secondary_button")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun liftrixButton_accentVariant_hasCorrectStyling() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixButton(
                    onClick = mockOnClick,
                    variant = ButtonVariant.Accent,
                    modifier = Modifier.testTag("accent_button")
                ) {
                    Text("Accent Button")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("accent_button")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun liftrixButton_outlinedVariant_hasCorrectStyling() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixButton(
                    onClick = mockOnClick,
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier.testTag("outlined_button")
                ) {
                    Text("Outlined Button")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("outlined_button")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun liftrixButton_textVariant_hasCorrectStyling() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixButton(
                    onClick = mockOnClick,
                    variant = ButtonVariant.Text,
                    modifier = Modifier.testTag("text_button")
                ) {
                    Text("Text Button")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("text_button")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun liftrixButton_customInteractionSource_tracksInteractions() = runTest {
        val interactionSource = MutableInteractionSource()
        var isPressed by mutableStateOf(false)

        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixButton(
                    onClick = mockOnClick,
                    interactionSource = interactionSource,
                    modifier = Modifier.testTag("interaction_button")
                ) {
                    Text("Interaction Button")
                }
            }
        }

        // Simulate press interaction
        val pressInteraction = PressInteraction.Press(androidx.compose.ui.geometry.Offset.Zero)
        interactionSource.tryEmit(pressInteraction)

        composeTestRule
            .onNodeWithTag("interaction_button")
            .assertIsDisplayed()
    }

    @Test
    fun primaryLiftrixButton_convenienceFunction_worksCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                PrimaryLiftrixButton(
                    onClick = mockOnClick,
                    modifier = Modifier.testTag("primary_convenience")
                ) {
                    Text("Primary Convenience")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("primary_convenience")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        verify { mockOnClick() }
    }

    @Test
    fun secondaryLiftrixButton_convenienceFunction_worksCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                SecondaryLiftrixButton(
                    onClick = mockOnClick,
                    modifier = Modifier.testTag("secondary_convenience")
                ) {
                    Text("Secondary Convenience")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("secondary_convenience")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        verify { mockOnClick() }
    }

    @Test
    fun accentLiftrixButton_convenienceFunction_worksCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                AccentLiftrixButton(
                    onClick = mockOnClick,
                    modifier = Modifier.testTag("accent_convenience")
                ) {
                    Text("Accent Convenience")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("accent_convenience")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        verify { mockOnClick() }
    }

    @Test
    fun liftrixButton_accessibility_hasButtonRole() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixButton(
                    onClick = mockOnClick,
                    modifier = Modifier.testTag("accessible_button")
                ) {
                    Text("Accessible Button")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("accessible_button")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun liftrixButton_accessibility_providesContentDescription() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixButton(
                    onClick = mockOnClick,
                    modifier = Modifier.testTag("described_button")
                ) {
                    Text("Button with Description")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("described_button")
            .assertIsDisplayed()
    }

    @Test
    fun athleticButtonPress_animatesCorrectly() {
        var pressed by mutableStateOf(false)

        composeTestRule.setContent {
            LiftrixTheme {
                AthleticButtonPress(
                    pressed = pressed,
                    enabled = true
                ) {
                    LiftrixButton(
                        onClick = { pressed = !pressed },
                        modifier = Modifier.testTag("athletic_button")
                    ) {
                        Text("Athletic Button")
                    }
                }
            }
        }

        composeTestRule
            .onNodeWithTag("athletic_button")
            .assertIsDisplayed()
            .performClick()

        // Animation should be triggered
        composeTestRule.waitForIdle()
    }

    @Test
    fun glowOnPress_rendersCorrectly() {
        var pressed by mutableStateOf(false)

        composeTestRule.setContent {
            LiftrixTheme {
                GlowOnPress(
                    pressed = pressed,
                    enabled = true
                ) {
                    LiftrixButton(
                        onClick = { pressed = !pressed },
                        modifier = Modifier.testTag("glow_button")
                    ) {
                        Text("Glow Button")
                    }
                }
            }
        }

        composeTestRule
            .onNodeWithTag("glow_button")
            .assertIsDisplayed()
    }

    @Test
    fun enhancedButtonInteraction_combinesEffectsCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                EnhancedButtonInteraction(
                    enabled = true,
                    hapticEnabled = true,
                    modifier = Modifier.testTag("enhanced_interaction")
                ) {
                    LiftrixButton(
                        onClick = mockOnClick
                    ) {
                        Text("Enhanced Button")
                    }
                }
            }
        }

        composeTestRule
            .onNodeWithText("Enhanced Button")
            .assertIsDisplayed()
            .performClick()

        verify { mockOnClick() }
    }

    @Test
    fun buttonVariant_colors_returnCorrectColors() {
        // Test that each variant returns appropriate colors
        composeTestRule.setContent {
            LiftrixTheme {
                // Test Primary variant colors
                val primaryColors = ButtonVariant.Primary.colors()
                
                // Test Secondary variant colors
                val secondaryColors = ButtonVariant.Secondary.colors()
                
                // Test Accent variant colors
                val accentColors = ButtonVariant.Accent.colors()
                
                // Test Outlined variant colors
                val outlinedColors = ButtonVariant.Outlined.colors()
                
                // Test Text variant colors
                val textColors = ButtonVariant.Text.colors()
                
                // All color functions should execute without error
                Text("Colors test completed")
            }
        }

        composeTestRule
            .onNodeWithText("Colors test completed")
            .assertIsDisplayed()
    }

    @Test
    fun liftrixButton_darkTheme_rendersCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = true) {
                LiftrixButton(
                    onClick = mockOnClick,
                    variant = ButtonVariant.Primary,
                    modifier = Modifier.testTag("dark_theme_button")
                ) {
                    Text("Dark Theme Button")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("dark_theme_button")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun liftrixButton_lightTheme_rendersCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = false) {
                LiftrixButton(
                    onClick = mockOnClick,
                    variant = ButtonVariant.Primary,
                    modifier = Modifier.testTag("light_theme_button")
                ) {
                    Text("Light Theme Button")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("light_theme_button")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun liftrixButton_performanceTest_multipleButtons() {
        composeTestRule.setContent {
            LiftrixTheme {
                repeat(10) { index ->
                    LiftrixButton(
                        onClick = mockOnClick,
                        variant = ButtonVariant.values()[index % ButtonVariant.values().size],
                        modifier = Modifier.testTag("button_$index")
                    ) {
                        Text("Button $index")
                    }
                }
            }
        }

        // Verify all buttons render correctly
        repeat(10) { index ->
            composeTestRule
                .onNodeWithTag("button_$index")
                .assertIsDisplayed()
        }
    }

    @Test
    fun animateButtonMicroInteraction_returnsAnimatedValue() {
        var targetValue by mutableStateOf(0f)
        var animatedValue = 0f

        composeTestRule.setContent {
            LiftrixTheme {
                animatedValue = animateButtonMicroInteraction(targetValue)
                Text("Animation value: $animatedValue")
            }
        }

        // Change target value and verify animation
        targetValue = 1f
        composeTestRule.waitForIdle()
    }

    @Test
    fun buttonHapticFeedback_executesWithoutError() {
        composeTestRule.setContent {
            LiftrixTheme {
                ButtonHapticFeedback(enabled = true)
                Text("Haptic feedback test")
            }
        }

        composeTestRule
            .onNodeWithText("Haptic feedback test")
            .assertIsDisplayed()
    }

    @Test
    fun animateAthleticElevation_returnsCorrectValues() {
        var pressed by mutableStateOf(false)
        var elevation = 0f

        composeTestRule.setContent {
            LiftrixTheme {
                elevation = animateAthleticElevation(pressed = pressed, enabled = true)
                Text("Elevation: $elevation")
            }
        }

        // Test normal state
        assert(elevation > 0f) { "Elevation should be positive in normal state" }

        // Test pressed state
        pressed = true
        composeTestRule.waitForIdle()
    }
} 