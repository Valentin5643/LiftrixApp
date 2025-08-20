package com.example.liftrix.ui.auth.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liftrix.ui.theme.LiftrixColorsV2

/**
 * Clean, compact sign up form with only essential elements
 */
@Composable
fun SignUpForm(
    onSignUp: (email: String, password: String, username: String) -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var agreeToTerms by remember { mutableStateOf(false) }
    
    // Validation states
    var usernameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    var isFormValid by remember { mutableStateOf(false) }
    
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Validate form whenever inputs change
    LaunchedEffect(username, email, password, confirmPassword, agreeToTerms) {
        usernameError = validateUsername(username)
        emailError = validateEmail(email)
        passwordError = validatePassword(password)
        confirmPasswordError = validateConfirmPassword(password, confirmPassword)
        
        isFormValid = usernameError == null && emailError == null && 
                     passwordError == null && confirmPasswordError == null &&
                     username.isNotBlank() && email.isNotBlank() && 
                     password.isNotBlank() && confirmPassword.isNotBlank() && agreeToTerms
    }
    
    // Single compact layout for all screen sizes
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Compact headline
        Text(
            text = "Let's Get Started",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 24.sp,
                lineHeight = 32.sp
            ),
            fontWeight = FontWeight.Bold,
            color = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Fill the form to continue",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDarkTheme) LiftrixColorsV2.Dark.TextSecondary else LiftrixColorsV2.Light.TextSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Username field
        Column {
            Text(
                text = "Username",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                placeholder = { 
                    Text(
                        "Choose a username",
                        color = if (isDarkTheme) LiftrixColorsV2.Dark.TextTertiary else LiftrixColorsV2.Light.TextTertiary
                    ) 
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Person icon",
                        tint = LiftrixColorsV2.Teal,
                        modifier = Modifier.size(20.dp)
                    )
                },
                isError = usernameError != null && username.isNotBlank(),
                enabled = !isLoading,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LiftrixColorsV2.Teal,
                    unfocusedBorderColor = if (isDarkTheme) LiftrixColorsV2.Dark.BackgroundTertiary else LiftrixColorsV2.Light.TextTertiary,
                    focusedLabelColor = LiftrixColorsV2.Teal,
                    cursorColor = LiftrixColorsV2.Teal,
                    focusedTextColor = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary,
                    unfocusedTextColor = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Email field
        Column {
            Text(
                text = "Your Email Address",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = { 
                    Text(
                        "user@example.com",
                        color = if (isDarkTheme) LiftrixColorsV2.Dark.TextTertiary else LiftrixColorsV2.Light.TextTertiary
                    ) 
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Email icon",
                        tint = LiftrixColorsV2.Teal,
                        modifier = Modifier.size(20.dp)
                    )
                },
                isError = emailError != null && email.isNotBlank(),
                enabled = !isLoading,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LiftrixColorsV2.Teal,
                    unfocusedBorderColor = if (isDarkTheme) LiftrixColorsV2.Dark.BackgroundTertiary else LiftrixColorsV2.Light.TextTertiary,
                    focusedLabelColor = LiftrixColorsV2.Teal,
                    cursorColor = LiftrixColorsV2.Teal,
                    focusedTextColor = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary,
                    unfocusedTextColor = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Password field
        Column {
            Text(
                text = "Choose a Password",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { 
                    Text(
                        "min. 8 characters",
                        color = if (isDarkTheme) LiftrixColorsV2.Dark.TextTertiary else LiftrixColorsV2.Light.TextTertiary
                    ) 
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Password icon",
                        tint = LiftrixColorsV2.Teal,
                        modifier = Modifier.size(20.dp)
                    )
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
                            tint = LiftrixColorsV2.Teal,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                visualTransformation = if (isPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                isError = passwordError != null && password.isNotBlank(),
                enabled = !isLoading,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LiftrixColorsV2.Teal,
                    unfocusedBorderColor = if (isDarkTheme) LiftrixColorsV2.Dark.BackgroundTertiary else LiftrixColorsV2.Light.TextTertiary,
                    focusedLabelColor = LiftrixColorsV2.Teal,
                    cursorColor = LiftrixColorsV2.Teal,
                    focusedTextColor = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary,
                    unfocusedTextColor = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Confirm Password field
        Column {
            Text(
                text = "Confirm Password",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                placeholder = { 
                    Text(
                        "Re-enter your password",
                        color = if (isDarkTheme) LiftrixColorsV2.Dark.TextTertiary else LiftrixColorsV2.Light.TextTertiary
                    ) 
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Confirm password icon",
                        tint = LiftrixColorsV2.Teal,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible },
                        enabled = !isLoading
                    ) {
                        Icon(
                            imageVector = if (isConfirmPasswordVisible) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (isConfirmPasswordVisible) {
                                "Hide confirm password"
                            } else {
                                "Show confirm password"
                            },
                            tint = LiftrixColorsV2.Teal,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                visualTransformation = if (isConfirmPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                isError = confirmPasswordError != null && confirmPassword.isNotBlank(),
                enabled = !isLoading,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        if (isFormValid && !isLoading) {
                            onSignUp(email, password, username)
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LiftrixColorsV2.Teal,
                    unfocusedBorderColor = if (isDarkTheme) LiftrixColorsV2.Dark.BackgroundTertiary else LiftrixColorsV2.Light.TextTertiary,
                    focusedLabelColor = LiftrixColorsV2.Teal,
                    cursorColor = LiftrixColorsV2.Teal,
                    focusedTextColor = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary,
                    unfocusedTextColor = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Terms checkbox
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = agreeToTerms,
                onCheckedChange = { agreeToTerms = it },
                enabled = !isLoading,
                colors = CheckboxDefaults.colors(
                    checkedColor = LiftrixColorsV2.Teal,
                    uncheckedColor = if (isDarkTheme) LiftrixColorsV2.Dark.TextTertiary else LiftrixColorsV2.Light.TextTertiary,
                    checkmarkColor = if (isDarkTheme) LiftrixColorsV2.Dark.BackgroundPrimary else LiftrixColorsV2.Light.BackgroundPrimary
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "I agree with terms of use",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDarkTheme) LiftrixColorsV2.Dark.TextSecondary else LiftrixColorsV2.Light.TextSecondary
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Sign up button
        Button(
            onClick = {
                keyboardController?.hide()
                if (isFormValid) {
                    onSignUp(email, password, username)
                }
            },
            enabled = isFormValid && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
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
                    modifier = Modifier.size(24.dp),
                    color = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary
                )
            } else {
                Text(
                    text = "Sign up",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDarkTheme) 
                        LiftrixColorsV2.Dark.TextPrimary 
                    else 
                        LiftrixColorsV2.Light.BackgroundPrimary
                )
            }
        }
    }
}

private fun validateUsername(username: String): String? {
    return when {
        username.isBlank() -> null
        username.length < 3 -> "Username must be at least 3 characters"
        username.length > 20 -> "Username must be 20 characters or less"
        !username.matches(Regex("^[a-zA-Z0-9_]+$")) -> "Username can only contain letters, numbers, and underscores"
        else -> null
    }
}

private fun validateEmail(email: String): String? {
    return when {
        email.isBlank() -> null
        !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Please enter a valid email address"
        else -> null
    }
}

private fun validatePassword(password: String): String? {
    return when {
        password.isBlank() -> null
        password.length < 8 -> "Password must be at least 8 characters"
        else -> null
    }
}

private fun validateConfirmPassword(password: String, confirmPassword: String): String? {
    return when {
        confirmPassword.isBlank() -> null
        password != confirmPassword -> "Passwords do not match"
        else -> null
    }
}