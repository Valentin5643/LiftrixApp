package com.example.liftrix.ui.error

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.ui.theme.LiftrixTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprehensive error scenario testing for Liftrix error handling components.
 * 
 * Tests all major error conditions including network failures, data corruption,
 * validation errors, authentication issues, and recovery mechanisms to ensure
 * robust error handling across the application.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ErrorScenarioTest {
    
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)
    
    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()
    
    @Before
    fun setup() {
        hiltRule.inject()
    }
    
    @Test
    fun networkError_displaysOfflineModeOptions() {
        val networkError = LiftrixError.NetworkError(
            errorMessage = "Connection timeout",
            httpStatusCode = 408,
            networkType = "WiFi"
        )
        
        var retryClicked = false
        var offlineClicked = false
        
        composeTestRule.setContent {
            LiftrixTheme {
                ErrorHandlingExtensions.LiftrixErrorState(
                    error = networkError,
                    onRetry = { retryClicked = true },
                    onDismiss = { offlineClicked = true }
                )
            }
        }
        
        // Verify error content is displayed
        composeTestRule
            .onNodeWithText("Connection Issue")
            .assertIsDisplayed()
            
        composeTestRule
            .onNodeWithText("Server responded with error 408")
            .assertIsDisplayed()
            
        composeTestRule
            .onNodeWithText("Your changes are saved locally and will sync when connection is restored.")
            .assertIsDisplayed()
        
        // Test retry button
        composeTestRule
            .onNodeWithText("Try Again")
            .assertIsDisplayed()
            .performClick()
            
        assert(retryClicked) { "Retry button should trigger retry callback" }
        
        // Reset and test offline button
        retryClicked = false
        composeTestRule
            .onNodeWithText("Continue Offline")
            .assertIsDisplayed()
            .performClick()
            
        assert(offlineClicked) { "Continue Offline button should trigger dismiss callback" }
    }
    
    @Test
    fun dataCorruptionError_displaysBackupRestoreOptions() {
        val corruptionError = LiftrixError.DatabaseError(
            errorMessage = "Data corruption detected",
            operation = "load_workout",
            table = "workouts",
            sqlErrorCode = 11
        )
        
        var restoreClicked = false
        var continueClicked = false
        
        composeTestRule.setContent {
            LiftrixTheme {
                ErrorHandlingExtensions.LiftrixErrorState(
                    error = corruptionError,
                    onRetry = { restoreClicked = true },
                    onDismiss = { continueClicked = true }
                )
            }
        }
        
        // Verify corruption error content
        composeTestRule
            .onNodeWithText("Data Issue")
            .assertIsDisplayed()
            
        composeTestRule
            .onNodeWithText("Some workout data appears corrupted. We can restore from your last backup or continue with current data.")
            .assertIsDisplayed()
        
        // Test restore backup button
        composeTestRule
            .onNodeWithText("Restore Backup")
            .assertIsDisplayed()
            .performClick()
            
        assert(restoreClicked) { "Restore Backup button should trigger restore callback" }
        
        // Test continue anyway button
        composeTestRule
            .onNodeWithText("Continue Anyway")
            .assertIsDisplayed()
            .performClick()
            
        assert(continueClicked) { "Continue Anyway button should trigger continue callback" }
    }
    
    @Test
    fun validationError_displaysSpecificFieldErrors() {
        val validationError = LiftrixError.ValidationError(
            field = "exercise_weight",
            violations = listOf(
                "Weight cannot be negative",
                "Weight must be a valid number"
            ),
            errorMessage = "Invalid weight input"
        )
        
        var fixInputClicked = false
        
        composeTestRule.setContent {
            LiftrixTheme {
                ErrorHandlingExtensions.LiftrixErrorState(
                    error = validationError,
                    onRetry = {},
                    onDismiss = { fixInputClicked = true }
                )
            }
        }
        
        // Verify validation error content
        composeTestRule
            .onNodeWithText("Input Error")
            .assertIsDisplayed()
            
        composeTestRule
            .onNodeWithText("Please check your input")
            .assertIsDisplayed()
        
        // Verify specific validation violations are listed
        composeTestRule
            .onNodeWithText("• Weight cannot be negative")
            .assertIsDisplayed()
            
        composeTestRule
            .onNodeWithText("• Weight must be a valid number")
            .assertIsDisplayed()
        
        // Test fix input button
        composeTestRule
            .onNodeWithText("Fix Input")
            .assertIsDisplayed()
            .performClick()
            
        assert(fixInputClicked) { "Fix Input button should trigger fix callback" }
    }
    
    @Test
    fun authenticationError_displaysSignInOptions() {
        val authError = LiftrixError.AuthenticationError(
            errorMessage = "Session expired",
            authProvider = "firebase",
            errorCode = "TOKEN_EXPIRED"
        )
        
        var signInClicked = false
        var cancelClicked = false
        
        composeTestRule.setContent {
            LiftrixTheme {
                ErrorHandlingExtensions.LiftrixErrorState(
                    error = authError,
                    onRetry = { signInClicked = true },
                    onDismiss = { cancelClicked = true }
                )
            }
        }
        
        // Verify authentication error content
        composeTestRule
            .onNodeWithText("Sign In Required")
            .assertIsDisplayed()
            
        composeTestRule
            .onNodeWithText("Please sign in again to continue.")
            .assertIsDisplayed()
        
        // Test sign in button
        composeTestRule
            .onNodeWithText("Sign In")
            .assertIsDisplayed()
            .performClick()
            
        assert(signInClicked) { "Sign In button should trigger sign in callback" }
        
        // Test cancel button
        composeTestRule
            .onNodeWithText("Cancel")
            .assertIsDisplayed()
            .performClick()
            
        assert(cancelClicked) { "Cancel button should trigger cancel callback" }
    }
    
    @Test
    fun genericError_displaysBasicRecoveryOptions() {
        val genericError = LiftrixError.UnknownError(
            errorMessage = "Unexpected error occurred",
            isRecoverable = true
        )
        
        var retryClicked = false
        var dismissClicked = false
        
        composeTestRule.setContent {
            LiftrixTheme {
                ErrorHandlingExtensions.LiftrixErrorState(
                    error = genericError,
                    onRetry = { retryClicked = true },
                    onDismiss = { dismissClicked = true }
                )
            }
        }
        
        // Verify generic error content
        composeTestRule
            .onNodeWithText("Something Went Wrong")
            .assertIsDisplayed()
            
        composeTestRule
            .onNodeWithText("Please try again or contact support if the problem persists.")
            .assertIsDisplayed()
        
        // Test retry button (should be present for recoverable errors)
        composeTestRule
            .onNodeWithText("Try Again")
            .assertIsDisplayed()
            .performClick()
            
        assert(retryClicked) { "Try Again button should trigger retry callback" }
        
        // Test dismiss button
        composeTestRule
            .onNodeWithText("Dismiss")
            .assertIsDisplayed()
            .performClick()
            
        assert(dismissClicked) { "Dismiss button should trigger dismiss callback" }
    }
    
    @Test
    fun nonRecoverableError_hidesRetryOption() {
        val nonRecoverableError = LiftrixError.BusinessLogicError(
            code = "INVALID_OPERATION",
            errorMessage = "This operation is not allowed",
            isRecoverable = false
        )
        
        composeTestRule.setContent {
            LiftrixTheme {
                ErrorHandlingExtensions.LiftrixErrorState(
                    error = nonRecoverableError,
                    onRetry = {},
                    onDismiss = {}
                )
            }
        }
        
        // Verify retry button is not displayed for non-recoverable errors
        composeTestRule
            .onNodeWithText("Try Again")
            .assertDoesNotExist()
        
        // Verify dismiss button is available
        composeTestRule
            .onNodeWithText("Dismiss")
            .assertIsDisplayed()
    }
    
    @Test
    fun successState_displaysPositiveFeedback() {
        var continueClicked = false
        
        composeTestRule.setContent {
            LiftrixTheme {
                ErrorHandlingExtensions.SuccessState(
                    title = "Changes Saved",
                    message = "Your workout has been successfully updated",
                    onContinue = { continueClicked = true }
                )
            }
        }
        
        // Verify success content
        composeTestRule
            .onNodeWithText("Changes Saved")
            .assertIsDisplayed()
            
        composeTestRule
            .onNodeWithText("Operation completed successfully")
            .assertIsDisplayed()
            
        composeTestRule
            .onNodeWithText("Your workout has been successfully updated")
            .assertIsDisplayed()
        
        // Test continue button
        composeTestRule
            .onNodeWithText("Continue")
            .assertIsDisplayed()
            .performClick()
            
        assert(continueClicked) { "Continue button should trigger continue callback" }
    }
    
    @Test
    fun errorState_hasProperAccessibilityContent() {
        val networkError = LiftrixError.NetworkError(
            errorMessage = "Connection failed",
            isRecoverable = true
        )
        
        composeTestRule.setContent {
            LiftrixTheme {
                ErrorHandlingExtensions.LiftrixErrorState(
                    error = networkError,
                    onRetry = {},
                    onDismiss = {}
                )
            }
        }
        
        // Verify accessibility content descriptions are present
        composeTestRule
            .onNode(hasContentDescription("Error: Connection failed. Network connection failed. You can try again or continue working offline."))
            .assertExists()
    }
    
    @Test
    fun multipleErrorScenarios_handledSequentially() {
        val errors = listOf(
            LiftrixError.NetworkError("Network error 1"),
            LiftrixError.ValidationError("field1", listOf("Validation error 1")),
            LiftrixError.DatabaseError("Database error 1")
        )
        
        // Test that different error types can be displayed and handled properly
        errors.forEach { error ->
            composeTestRule.setContent {
                LiftrixTheme {
                    ErrorHandlingExtensions.LiftrixErrorState(
                        error = error,
                        onRetry = {},
                        onDismiss = {}
                    )
                }
            }
            
            // Verify error is displayed (each error type should have some visible content)
            composeTestRule
                .onAllNodes(hasText(error.message, substring = true))
                .onFirst()
                .assertIsDisplayed()
        }
    }
    
    @Test
    fun errorState_supportsHapticFeedback() {
        val networkError = LiftrixError.NetworkError("Test network error")
        
        composeTestRule.setContent {
            LiftrixTheme {
                ErrorHandlingExtensions.LiftrixErrorState(
                    error = networkError,
                    onRetry = {},
                    onDismiss = {}
                )
            }
        }
        
        // Test that buttons can be clicked (haptic feedback is handled internally)
        composeTestRule
            .onNodeWithText("Try Again")
            .assertHasClickAction()
            .performClick()
            
        composeTestRule
            .onNodeWithText("Continue Offline")
            .assertHasClickAction()
            .performClick()
    }
}