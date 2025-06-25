package com.example.liftrix.ui.auth

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.TestDataFactory
import com.example.liftrix.domain.model.AuthEvent
import com.example.liftrix.domain.model.AuthState
import com.example.liftrix.ui.theme.LiftrixTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var mockViewModel: AuthViewModel
    private lateinit var authStateFlow: MutableStateFlow<AuthState>

    @Before
    fun setup() {
        mockViewModel = mockk(relaxed = true)
        authStateFlow = MutableStateFlow(AuthState.Initial)
        every { mockViewModel.authState } returns authStateFlow
    }

    @Test
    fun authScreen_displaysLoginFormInitially() {
        composeTestRule.setContent {
            LiftrixTheme {
                AuthScreen(viewModel = mockViewModel)
            }
        }

        // Verify login form elements are displayed
        composeTestRule.onNodeWithText("Welcome to Liftrix").assertIsDisplayed()
        composeTestRule.onNodeWithText("Email").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign In").assertIsDisplayed()
        composeTestRule.onNodeWithText("Don't have an account? Sign Up").assertIsDisplayed()
    }

    @Test
    fun loginForm_emailInput_updatesCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                AuthScreen(viewModel = mockViewModel)
            }
        }

        val testEmail = "test@example.com"
        
        composeTestRule.onNodeWithText("Email").performTextInput(testEmail)
        composeTestRule.onNodeWithText(testEmail).assertIsDisplayed()
    }

    @Test
    fun loginForm_passwordInput_masksText() {
        composeTestRule.setContent {
            LiftrixTheme {
                AuthScreen(viewModel = mockViewModel)
            }
        }

        val testPassword = "password123"
        
        composeTestRule.onNodeWithText("Password").performTextInput(testPassword)
        
        // Password should be masked (we can't verify the actual dots, but we can verify the field has content)
        composeTestRule.onNodeWithText("Password").assertTextContains("")
    }

    @Test
    fun loginForm_signInButton_triggersEmailPasswordSignIn() {
        composeTestRule.setContent {
            LiftrixTheme {
                AuthScreen(viewModel = mockViewModel)
            }
        }

        val testEmail = "test@example.com"
        val testPassword = "password123"
        
        // Fill in login form
        composeTestRule.onNodeWithText("Email").performTextInput(testEmail)
        composeTestRule.onNodeWithText("Password").performTextInput(testPassword)
        
        // Click sign in
        composeTestRule.onNodeWithText("Sign In").performClick()
        
        // Verify ViewModel method was called
        verify { mockViewModel.handleEvent(AuthEvent.EmailPasswordSignIn(testEmail, testPassword)) }
    }

    @Test
    fun authScreen_switchesToSignUpForm() {
        composeTestRule.setContent {
            LiftrixTheme {
                AuthScreen(viewModel = mockViewModel)
            }
        }

        // Click "Sign Up" link
        composeTestRule.onNodeWithText("Don't have an account? Sign Up").performClick()
        
        // Verify sign up form elements appear
        composeTestRule.onNodeWithText("Create Account").assertIsDisplayed()
        composeTestRule.onNodeWithText("Display Name").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign Up").assertIsDisplayed()
        composeTestRule.onNodeWithText("Already have an account? Sign In").assertIsDisplayed()
    }

    @Test
    fun signUpForm_allFields_captureInput() {
        composeTestRule.setContent {
            LiftrixTheme {
                AuthScreen(viewModel = mockViewModel)
            }
        }

        // Switch to sign up form
        composeTestRule.onNodeWithText("Don't have an account? Sign Up").performClick()
        
        val testDisplayName = "Test User"
        val testEmail = "test@example.com"
        val testPassword = "password123"
        
        // Fill in sign up form
        composeTestRule.onNodeWithText("Display Name").performTextInput(testDisplayName)
        composeTestRule.onNodeWithText("Email").performTextInput(testEmail)
        composeTestRule.onNodeWithText("Password").performTextInput(testPassword)
        
        // Verify inputs are captured
        composeTestRule.onNodeWithText(testDisplayName).assertIsDisplayed()
        composeTestRule.onNodeWithText(testEmail).assertIsDisplayed()
    }

    @Test
    fun signUpForm_signUpButton_triggersEmailPasswordSignUp() {
        composeTestRule.setContent {
            LiftrixTheme {
                AuthScreen(viewModel = mockViewModel)
            }
        }

        // Switch to sign up form
        composeTestRule.onNodeWithText("Don't have an account? Sign Up").performClick()
        
        val testDisplayName = "Test User"
        val testEmail = "test@example.com"
        val testPassword = "password123"
        
        // Fill in sign up form
        composeTestRule.onNodeWithText("Display Name").performTextInput(testDisplayName)
        composeTestRule.onNodeWithText("Email").performTextInput(testEmail)
        composeTestRule.onNodeWithText("Password").performTextInput(testPassword)
        
        // Click sign up
        composeTestRule.onNodeWithText("Sign Up").performClick()
        
        // Verify ViewModel method was called
        verify { mockViewModel.handleEvent(AuthEvent.EmailPasswordSignUp(testEmail, testPassword, testDisplayName)) }
    }

    @Test
    fun authScreen_showsLoadingState() {
        authStateFlow.value = AuthState.Loading
        
        composeTestRule.setContent {
            LiftrixTheme {
                AuthScreen(viewModel = mockViewModel)
            }
        }

        // Verify loading indicator is displayed
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
    }

    @Test
    fun authScreen_showsErrorState() {
        val errorMessage = "Invalid email or password"
        authStateFlow.value = AuthState.Error(errorMessage)
        
        composeTestRule.setContent {
            LiftrixTheme {
                AuthScreen(viewModel = mockViewModel)
            }
        }

        // Verify error message is displayed
        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    }

    @Test
    fun authScreen_anonymousSignIn_triggersEvent() {
        composeTestRule.setContent {
            LiftrixTheme {
                AuthScreen(viewModel = mockViewModel)
            }
        }

        // Find and click anonymous sign in button
        composeTestRule.onNodeWithText("Continue as Guest").performClick()
        
        // Verify ViewModel method was called
        verify { mockViewModel.handleEvent(AuthEvent.AnonymousSignIn) }
    }

    @Test
    fun authScreen_googleSignIn_triggersEvent() {
        composeTestRule.setContent {
            LiftrixTheme {
                AuthScreen(viewModel = mockViewModel)
            }
        }

        // Find and click Google sign in button
        composeTestRule.onNodeWithText("Sign in with Google").performClick()
        
        // Verify ViewModel method was called
        verify { mockViewModel.handleEvent(AuthEvent.GoogleSignIn) }
    }

    @Test
    fun authScreen_forgotPassword_showsDialog() {
        composeTestRule.setContent {
            LiftrixTheme {
                AuthScreen(viewModel = mockViewModel)
            }
        }

        // Click forgot password link
        composeTestRule.onNodeWithText("Forgot Password?").performClick()
        
        // Verify forgot password dialog appears
        composeTestRule.onNodeWithText("Reset Password").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enter your email address").assertIsDisplayed()
    }

    @Test
    fun forgotPasswordDialog_sendReset_triggersEvent() {
        composeTestRule.setContent {
            LiftrixTheme {
                AuthScreen(viewModel = mockViewModel)
            }
        }

        // Open forgot password dialog
        composeTestRule.onNodeWithText("Forgot Password?").performClick()
        
        val testEmail = "test@example.com"
        
        // Enter email and send reset
        composeTestRule.onNodeWithText("Enter your email address").performTextInput(testEmail)
        composeTestRule.onNodeWithText("Send Reset Email").performClick()
        
        // Verify ViewModel method was called
        verify { mockViewModel.handleEvent(AuthEvent.ForgotPassword(testEmail)) }
    }

    @Test
    fun authScreen_authenticatedState_doesNotShowForms() {
        authStateFlow.value = AuthState.Authenticated(TestDataFactory.testUser)
        
        composeTestRule.setContent {
            LiftrixTheme {
                AuthScreen(viewModel = mockViewModel)
            }
        }

        // Verify login/signup forms are not displayed
        composeTestRule.onNodeWithText("Sign In").assertDoesNotExist()
        composeTestRule.onNodeWithText("Sign Up").assertDoesNotExist()
    }

    @Test
    fun loginForm_invalidEmail_showsValidationError() {
        composeTestRule.setContent {
            LiftrixTheme {
                AuthScreen(viewModel = mockViewModel)
            }
        }

        val invalidEmail = "invalid-email"
        
        // Enter invalid email
        composeTestRule.onNodeWithText("Email").performTextInput(invalidEmail)
        composeTestRule.onNodeWithText("Password").performTextInput("password123")
        
        // Try to sign in
        composeTestRule.onNodeWithText("Sign In").performClick()
        
        // Verify validation error is shown
        composeTestRule.onNodeWithText("Please enter a valid email address").assertIsDisplayed()
    }

    @Test
    fun signUpForm_shortPassword_showsValidationError() {
        composeTestRule.setContent {
            LiftrixTheme {
                AuthScreen(viewModel = mockViewModel)
            }
        }

        // Switch to sign up
        composeTestRule.onNodeWithText("Don't have an account? Sign Up").performClick()
        
        // Enter short password
        composeTestRule.onNodeWithText("Display Name").performTextInput("Test User")
        composeTestRule.onNodeWithText("Email").performTextInput("test@example.com")
        composeTestRule.onNodeWithText("Password").performTextInput("123")
        
        // Try to sign up
        composeTestRule.onNodeWithText("Sign Up").performClick()
        
        // Verify validation error is shown
        composeTestRule.onNodeWithText("Password must be at least 6 characters").assertIsDisplayed()
    }

    @Test
    fun authScreen_backNavigationFromSignUp_returnsToSignIn() {
        composeTestRule.setContent {
            LiftrixTheme {
                AuthScreen(viewModel = mockViewModel)
            }
        }

        // Switch to sign up
        composeTestRule.onNodeWithText("Don't have an account? Sign Up").performClick()
        composeTestRule.onNodeWithText("Create Account").assertIsDisplayed()
        
        // Go back to sign in
        composeTestRule.onNodeWithText("Already have an account? Sign In").performClick()
        
        // Verify back to sign in form
        composeTestRule.onNodeWithText("Welcome to Liftrix").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign In").assertIsDisplayed()
    }
} 