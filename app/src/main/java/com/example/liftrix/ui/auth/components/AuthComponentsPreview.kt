package com.example.liftrix.ui.auth.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Preview composable showcasing all authentication UI components
 */
@Preview(showBackground = true)
@Composable
fun AuthComponentsPreview() {
    LiftrixTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Authentication Components",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Sign In Form Preview
                Text(
                    text = "Sign In Form",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                SignInForm(
                    onSignIn = { _, _ -> /* Preview only */ },
                    onForgotPassword = { /* Preview only */ },
                    isLoading = false
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Sign Up Form Preview
                Text(
                    text = "Sign Up Form",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                SignUpForm(
                    onSignUp = { _, _, _ -> /* Preview only */ },
                    isLoading = false
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Loading State Preview
                Text(
                    text = "Loading State",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                SignInForm(
                    onSignIn = { _, _ -> /* Preview only */ },
                    isLoading = true
                )
            }
        }
    }
}

/**
 * Preview for individual AuthTextField component
 */
@Preview(showBackground = true)
@Composable
fun AuthTextFieldPreview() {
    LiftrixTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                Text(
                    text = "Auth Text Fields",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Normal text field
                AuthTextField(
                    value = "example@email.com",
                    onValueChange = { },
                    label = "Email",
                    placeholder = "Enter your email"
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Text field with error
                AuthTextField(
                    value = "invalid-email",
                    onValueChange = { },
                    label = "Email",
                    placeholder = "Enter your email",
                    errorMessage = "Please enter a valid email address"
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Text field with supporting text
                AuthTextField(
                    value = "ValidEmail123!",
                    onValueChange = { },
                    label = "Password",
                    placeholder = "Enter your password",
                    supportingText = "Password strength: Strong"
                )
            }
        }
    }
}

/**
 * Features showcased in these components:
 * 
 * 1. **Input Validation**
 *    - Real-time email format validation
 *    - Password strength requirements
 *    - Confirm password matching
 *    - Display name validation
 * 
 * 2. **UX Enhancements**
 *    - Loading states with progress indicators
 *    - Password visibility toggle
 *    - Keyboard navigation (Next/Done actions)
 *    - Form submission on keyboard "Done"
 *    - Error states with clear messaging
 * 
 * 3. **Accessibility**
 *    - Content descriptions for icons
 *    - Proper label associations
 *    - Keyboard navigation support
 *    - Color contrast compliance
 * 
 * 4. **Material 3 Design**
 *    - Consistent color scheme
 *    - Proper elevation and spacing
 *    - Typography scale adherence
 *    - Theme-aware components
 * 
 * 5. **Functionality**
 *    - Email/password authentication
 *    - Password reset dialog
 *    - Google Sign-In integration
 *    - Anonymous login option
 *    - Comprehensive error handling
 */ 