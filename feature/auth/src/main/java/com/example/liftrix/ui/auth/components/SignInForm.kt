package com.example.liftrix.ui.auth.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

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
    onClearError: () -> Unit = { },
    isDarkThemeOverride: Boolean? = null
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
    
    val isDarkTheme = isDarkThemeOverride ?: isSystemInDarkTheme()
    val textFieldShape = RoundedCornerShape(14.dp)
    val textPrimary = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary
    val textSecondary = if (isDarkTheme) LiftrixColorsV2.Dark.TextSecondary else LiftrixColorsV2.Light.TextSecondary
    val textTertiary = if (isDarkTheme) LiftrixColorsV2.Dark.TextTertiary else LiftrixColorsV2.Light.TextTertiary
    val outlineColor = if (isDarkTheme) LiftrixColorsV2.Dark.OutlineVariant else LiftrixColorsV2.Light.Outline
    val inputContainerColor = Color.Transparent
    
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
                containerColor = if (isDarkTheme) LiftrixColorsV2.Dark.BackgroundSecondary else LiftrixColorsV2.Light.BackgroundElevated
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkTheme) 2.dp else 5.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (isDarkTheme) LiftrixColorsV2.Dark.OutlineVariant else LiftrixColorsV2.Light.Outline
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Form title
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Welcome Back",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Sign in to continue",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LiftrixColorsV2.Teal
                    )
                }
                
                // Error message display
                if (errorMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkTheme) LiftrixColorsV2.Dark.Error.copy(alpha = 0.1f) else LiftrixColorsV2.Light.Error.copy(alpha = 0.1f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        shape = RoundedCornerShape(12.dp)
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
                    label = { Text("Email address", color = textTertiary) },
                    placeholder = { Text("name@domain.com", color = textTertiary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Email icon",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    enabled = !isLoading,
                    isError = emailError != null && email.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LiftrixColorsV2.Teal,
                        unfocusedBorderColor = outlineColor,
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        cursorColor = LiftrixColorsV2.Teal,
                        focusedContainerColor = inputContainerColor,
                        unfocusedContainerColor = inputContainerColor,
                        focusedLabelColor = LiftrixColorsV2.Teal,
                        unfocusedLabelColor = textTertiary,
                        focusedLeadingIconColor = LiftrixColorsV2.Teal,
                        unfocusedLeadingIconColor = textTertiary
                    ),
                    shape = textFieldShape,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    singleLine = true
                )
                
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
                    label = { Text("Password", color = textTertiary) },
                    placeholder = { Text("Enter your password", color = textTertiary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Password icon",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    enabled = !isLoading,
                    isError = passwordError != null && password.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LiftrixColorsV2.Teal,
                        unfocusedBorderColor = outlineColor,
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        cursorColor = LiftrixColorsV2.Teal,
                        focusedContainerColor = inputContainerColor,
                        unfocusedContainerColor = inputContainerColor,
                        focusedLabelColor = LiftrixColorsV2.Teal,
                        unfocusedLabelColor = textTertiary,
                        focusedLeadingIconColor = LiftrixColorsV2.Teal,
                        unfocusedLeadingIconColor = textTertiary,
                        focusedTrailingIconColor = LiftrixColorsV2.Teal,
                        unfocusedTrailingIconColor = textTertiary
                    ),
                    shape = textFieldShape,
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
                                tint = if (isPasswordVisible) LiftrixColorsV2.Teal else textTertiary
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
                
                // Remember me checkbox
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it },
                            enabled = !isLoading,
                            colors = CheckboxDefaults.colors(
                                checkedColor = LiftrixColorsV2.Teal,
                                uncheckedColor = outlineColor,
                                checkmarkColor = if (isDarkTheme) LiftrixColorsV2.Dark.BackgroundPrimary else LiftrixColorsV2.Light.BackgroundPrimary
                            )
                        )
                        Text(
                            text = "Remember me",
                            style = MaterialTheme.typography.bodyMedium,
                            color = textSecondary
                        )
                    }
                    TextButton(
                        onClick = { showForgotPassword = true },
                        enabled = !isLoading,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "Forgot password?",
                            color = LiftrixColorsV2.Teal,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                    }
                }
                
                // Sign in button
                Button(
                    onClick = {
                        keyboardController?.hide()
                        onSignIn(email, password)
                    },
                    enabled = isFormValid && !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
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
                    ),
                    shape = RoundedCornerShape(14.dp)
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
