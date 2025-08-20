package com.example.liftrix.ui.auth.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import com.example.liftrix.ui.workout.components.TertiaryActionButton
import com.example.liftrix.ui.theme.LiftrixColorsV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sign in form with email/password validation and forgot password functionality
 */
@Composable
fun SignInForm(
    onSignIn: (email: String, password: String) -> Unit,
    onForgotPassword: (email: String) -> Unit = { },
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    onClearError: () -> Unit = { }
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(false) }
    var showForgotPassword by remember { mutableStateOf(false) }
    
    // Validation states
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var isFormValid by remember { mutableStateOf(false) }
    
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isDarkTheme = isSystemInDarkTheme()
    
    // Validate form whenever inputs change
    LaunchedEffect(email, password) {
        emailError = validateEmail(email)
        passwordError = validatePassword(password)
        isFormValid = emailError == null && passwordError == null && 
                     email.isNotBlank() && password.isNotBlank()
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Card container for the form
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) LiftrixColorsV2.Dark.BackgroundSecondary else LiftrixColorsV2.Light.BackgroundSecondary
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Form title
                Text(
                    text = "Welcome Back",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Sign in to your account",
                    style = MaterialTheme.typography.bodyLarge,
                    color = LiftrixColorsV2.Teal
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Error message display
                if (errorMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkTheme) LiftrixColorsV2.Dark.Error.copy(alpha = 0.1f) else LiftrixColorsV2.Light.Error.copy(alpha = 0.1f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Error",
                                tint = if (isDarkTheme) LiftrixColorsV2.Dark.Error else LiftrixColorsV2.Light.Error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDarkTheme) LiftrixColorsV2.Dark.Error else LiftrixColorsV2.Light.Error
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                // Email field
                OutlinedTextField(
                    value = email,
                    onValueChange = { 
                        email = it
                        // Clear error when user starts typing
                        if (errorMessage != null) {
                            onClearError()
                        }
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Email icon",
                                modifier = Modifier.size(18.dp),
                                tint = LiftrixColorsV2.Teal
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Email Address", color = if (isDarkTheme) LiftrixColorsV2.Dark.TextSecondary else LiftrixColorsV2.Light.TextSecondary)
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LiftrixColorsV2.Teal,
                        unfocusedBorderColor = if (isDarkTheme) LiftrixColorsV2.Dark.Outline else LiftrixColorsV2.Light.Outline,
                        focusedTextColor = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary,
                        unfocusedTextColor = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary,
                        cursorColor = LiftrixColorsV2.Teal,
                        focusedContainerColor = if (isDarkTheme) LiftrixColorsV2.Dark.BackgroundSecondary else LiftrixColorsV2.Light.BackgroundSecondary,
                        unfocusedContainerColor = if (isDarkTheme) LiftrixColorsV2.Dark.BackgroundSecondary else LiftrixColorsV2.Light.BackgroundSecondary
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Password field
                OutlinedTextField(
                    value = password,
                    onValueChange = { 
                        password = it
                        // Clear error when user starts typing
                        if (errorMessage != null) {
                            onClearError()
                        }
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Password icon",
                                modifier = Modifier.size(18.dp),
                                tint = LiftrixColorsV2.Teal
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Password", color = if (isDarkTheme) LiftrixColorsV2.Dark.TextSecondary else LiftrixColorsV2.Light.TextSecondary)
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LiftrixColorsV2.Teal,
                        unfocusedBorderColor = if (isDarkTheme) LiftrixColorsV2.Dark.Outline else LiftrixColorsV2.Light.Outline,
                        focusedTextColor = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary,
                        unfocusedTextColor = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary,
                        cursorColor = LiftrixColorsV2.Teal,
                        focusedContainerColor = if (isDarkTheme) LiftrixColorsV2.Dark.BackgroundSecondary else LiftrixColorsV2.Light.BackgroundSecondary,
                        unfocusedContainerColor = if (isDarkTheme) LiftrixColorsV2.Dark.BackgroundSecondary else LiftrixColorsV2.Light.BackgroundSecondary
                    ),
                    visualTransformation = if (isPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { isPasswordVisible = !isPasswordVisible },
                            enabled = !isLoading
                        ) {
                            Icon(
                                imageVector = if (isPasswordVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (isPasswordVisible) {
                                    "Hide password"
                                } else {
                                    "Show password"
                                },
                                tint = if (isDarkTheme) LiftrixColorsV2.Dark.TextTertiary else LiftrixColorsV2.Light.TextTertiary
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            if (isFormValid && !isLoading) {
                                onSignIn(email, password)
                            }
                        }
                    ),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Remember me and Forgot password row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it },
                            enabled = !isLoading,
                            colors = CheckboxDefaults.colors(
                                checkedColor = LiftrixColorsV2.Teal,
                                uncheckedColor = if (isDarkTheme) LiftrixColorsV2.Dark.Outline else LiftrixColorsV2.Light.Outline,
                                checkmarkColor = if (isDarkTheme) LiftrixColorsV2.Dark.BackgroundPrimary else LiftrixColorsV2.Light.BackgroundPrimary
                            )
                        )
                        Text(
                            text = "Remember me next time",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDarkTheme) LiftrixColorsV2.Dark.TextSecondary else LiftrixColorsV2.Light.TextSecondary
                        )
                    }
                    
                    TextButton(
                        onClick = { showForgotPassword = true },
                        enabled = !isLoading
                    ) {
                        Text(
                            text = "Forgot password?",
                            color = LiftrixColorsV2.Teal
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Sign in button
                Button(
                    onClick = {
                        keyboardController?.hide()
                        onSignIn(email, password)
                    },
                    enabled = isFormValid && !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LiftrixColorsV2.Teal,
                        contentColor = if (isDarkTheme) 
                            LiftrixColorsV2.Dark.TextPrimary 
                        else 
                            LiftrixColorsV2.Light.BackgroundPrimary,
                        disabledContainerColor = if (isDarkTheme) 
                            LiftrixColorsV2.Dark.BackgroundTertiary 
                        else 
                            LiftrixColorsV2.Light.BackgroundTertiary,
                        disabledContentColor = if (isDarkTheme)
                            LiftrixColorsV2.Dark.TextDisabled
                        else
                            LiftrixColorsV2.Light.TextDisabled
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = if (isDarkTheme) 
                                LiftrixColorsV2.Dark.TextPrimary 
                            else 
                                LiftrixColorsV2.Light.BackgroundPrimary
                        )
                    } else {
                        Text(
                            text = "Sign In",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
    
    // Forgot password dialog
    if (showForgotPassword) {
        ForgotPasswordDialog(
            initialEmail = email,
            onDismiss = { showForgotPassword = false },
            onSendReset = { resetEmail ->
                onForgotPassword(resetEmail)
                showForgotPassword = false
            }
        )
    }
}

/**
 * Validates email format and requirements
 */
private fun validateEmail(email: String): String? {
    return when {
        email.isBlank() -> null // Don't show error for empty field initially
        !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Please enter a valid email address"
        email.length > 254 -> "Email address is too long"
        else -> null
    }
}

/**
 * Validates password requirements
 */
private fun validatePassword(password: String): String? {
    return when {
        password.isBlank() -> null // Don't show error for empty field initially
        password.length < 6 -> "Password must be at least 6 characters"
        password.length > 128 -> "Password is too long"
        else -> null
    }
} 