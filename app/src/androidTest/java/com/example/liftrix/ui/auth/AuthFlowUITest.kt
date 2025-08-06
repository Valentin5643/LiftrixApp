package com.example.liftrix.ui.auth

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.ui.theme.LiftrixTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI Tests for Authentication Flow
 * 
 * Tests critical user authentication journeys including:
 * - Email/password sign-in validation
 * - Error state handling
 * - Navigation flow after successful authentication
 * - Accessibility compliance for auth forms
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AuthFlowUITest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun authScreen_displaysCorrectInitialState() {
        // Given: AuthScreen is displayed
        composeTestRule.setContent {
            LiftrixTheme {
                AuthScreen(
                    onNavigateToHome = { },
                    onNavigateToOnboarding = { }
                )
            }
        }

        // Then: Essential UI elements are visible
        composeTestRule.onNodeWithText("Welcome to Liftrix").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign In").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign Up").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue with Google").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue as Guest").assertIsDisplayed()
    }

    @Test
    fun signInForm_validatesEmailInput() {
        composeTestRule.setContent {
            LiftrixTheme {
                AuthScreen(
                    onNavigateToHome = { },
                    onNavigateToOnboarding = { }
                )
            }
        }

        // Given: User taps Sign In
        composeTestRule.onNodeWithText("Sign In").performClick()

        // When: User enters invalid email
        composeTestRule.onNodeWithText("Email").performTextInput("invalid-email")
        composeTestRule.onNodeWithText("Password").performTextInput("password123")
        composeTestRule.onNodeWithText("Sign In", useUnmergedTree = true).performClick()

        // Then: Validation error is shown
        composeTestRule.onNodeWithText("Please enter a valid email address")
            .assertIsDisplayed()
    }

    @Test
    fun signInForm_handlesNetworkError() {
        composeTestRule.setContent {
            LiftrixTheme {
                AuthScreen(
                    onNavigateToHome = { },
                    onNavigateToOnboarding = { }
                )
            }
        }

        // Given: User enters valid credentials
        composeTestRule.onNodeWithText("Sign In").performClick()
        composeTestRule.onNodeWithText("Email").performTextInput("test@example.com")
        composeTestRule.onNodeWithText("Password").performTextInput("password123")

        // When: Sign in fails due to network error
        composeTestRule.onNodeWithText("Sign In", useUnmergedTree = true).performClick()

        // Then: Error message is displayed (mock network failure scenario)
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Network error. Please try again.")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun authScreen_supportsAccessibility() {
        composeTestRule.setContent {
            LiftrixTheme {
                AuthScreen(
                    onNavigateToHome = { },
                    onNavigateToOnboarding = { }
                )
            }
        }

        // Then: All interactive elements have content descriptions
        composeTestRule.onNodeWithText("Sign In")
            .assertHasClickAction()
            .assert(hasContentDescription())

        composeTestRule.onNodeWithText("Sign Up")
            .assertHasClickAction()
            .assert(hasContentDescription())

        composeTestRule.onNodeWithText("Continue with Google")
            .assertHasClickAction()
            .assert(hasContentDescription())
    }

    @Test
    fun signUpForm_validatesPasswordStrength() {
        composeTestRule.setContent {
            LiftrixTheme {
                AuthScreen(
                    onNavigateToHome = { },
                    onNavigateToOnboarding = { }
                )
            }
        }

        // Given: User taps Sign Up
        composeTestRule.onNodeWithText("Sign Up").performClick()

        // When: User enters weak password
        composeTestRule.onNodeWithText("Email").performTextInput("test@example.com")
        composeTestRule.onNodeWithText("Password").performTextInput("123")
        composeTestRule.onNodeWithText("Confirm Password").performTextInput("123")

        // Then: Password strength validation is shown
        composeTestRule.onNodeWithText("Password must be at least 8 characters")
            .assertIsDisplayed()
    }

    @Test
    fun guestMode_navigatesToOnboarding() {
        var onboardingNavigated = false
        
        composeTestRule.setContent {
            LiftrixTheme {
                AuthScreen(
                    onNavigateToHome = { },
                    onNavigateToOnboarding = { onboardingNavigated = true }
                )
            }
        }

        // When: User selects guest mode
        composeTestRule.onNodeWithText("Continue as Guest").performClick()

        // Then: Navigation to onboarding occurs
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            onboardingNavigated
        }
    }
}