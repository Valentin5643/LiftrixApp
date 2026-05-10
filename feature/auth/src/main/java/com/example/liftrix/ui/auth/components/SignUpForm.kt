package com.example.liftrix.ui.auth.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import com.example.liftrix.ui.theme.LiftrixColorsV2

/**
 * Clean, compact sign up form with only essential elements
 */
@Composable
fun SignUpForm(
    onSignUp: (email: String, password: String, username: String) -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
    isDarkThemeOverride: Boolean? = null
) {
    val isDarkTheme = isDarkThemeOverride ?: isSystemInDarkTheme()
    
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
    val textFieldShape = RoundedCornerShape(14.dp)
    val textPrimary = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary
    val textSecondary = if (isDarkTheme) LiftrixColorsV2.Dark.TextSecondary else LiftrixColorsV2.Light.TextSecondary
    val textTertiary = if (isDarkTheme) LiftrixColorsV2.Dark.TextTertiary else LiftrixColorsV2.Light.TextTertiary
    val outlineColor = if (isDarkTheme) LiftrixColorsV2.Dark.OutlineVariant else LiftrixColorsV2.Light.Outline
    val inputContainerColor = Color.Transparent
    
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
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Let's Get Started",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Fill the form to continue",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username", color = textTertiary) },
                    placeholder = { Text("Choose a username", color = textTertiary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Person icon",
                            modifier = Modifier.size(18.dp)
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
                        unfocusedBorderColor = outlineColor,
                        focusedLabelColor = LiftrixColorsV2.Teal,
                        unfocusedLabelColor = textTertiary,
                        cursorColor = LiftrixColorsV2.Teal,
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        focusedContainerColor = inputContainerColor,
                        unfocusedContainerColor = inputContainerColor,
                        focusedLeadingIconColor = LiftrixColorsV2.Teal,
                        unfocusedLeadingIconColor = textTertiary
                    ),
                    shape = textFieldShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email address", color = textTertiary) },
                    placeholder = { Text("name@domain.com", color = textTertiary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Email icon",
                            modifier = Modifier.size(18.dp)
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
                        unfocusedBorderColor = outlineColor,
                        focusedLabelColor = LiftrixColorsV2.Teal,
                        unfocusedLabelColor = textTertiary,
                        cursorColor = LiftrixColorsV2.Teal,
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        focusedContainerColor = inputContainerColor,
                        unfocusedContainerColor = inputContainerColor,
                        focusedLeadingIconColor = LiftrixColorsV2.Teal,
                        unfocusedLeadingIconColor = textTertiary
                    ),
                    shape = textFieldShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password", color = textTertiary) },
                    placeholder = { Text("min. 8 characters", color = textTertiary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Password icon",
                            modifier = Modifier.size(18.dp)
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
                                tint = if (isPasswordVisible) LiftrixColorsV2.Teal else textTertiary,
                                modifier = Modifier.size(18.dp)
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
                        unfocusedBorderColor = outlineColor,
                        focusedLabelColor = LiftrixColorsV2.Teal,
                        unfocusedLabelColor = textTertiary,
                        cursorColor = LiftrixColorsV2.Teal,
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        focusedContainerColor = inputContainerColor,
                        unfocusedContainerColor = inputContainerColor,
                        focusedLeadingIconColor = LiftrixColorsV2.Teal,
                        unfocusedLeadingIconColor = textTertiary,
                        focusedTrailingIconColor = LiftrixColorsV2.Teal,
                        unfocusedTrailingIconColor = textTertiary
                    ),
                    shape = textFieldShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm password", color = textTertiary) },
                    placeholder = { Text("Re-enter your password", color = textTertiary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Confirm password icon",
                            modifier = Modifier.size(18.dp)
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
                                tint = if (isConfirmPasswordVisible) LiftrixColorsV2.Teal else textTertiary,
                                modifier = Modifier.size(18.dp)
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
                        unfocusedBorderColor = outlineColor,
                        focusedLabelColor = LiftrixColorsV2.Teal,
                        unfocusedLabelColor = textTertiary,
                        cursorColor = LiftrixColorsV2.Teal,
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        focusedContainerColor = inputContainerColor,
                        unfocusedContainerColor = inputContainerColor,
                        focusedLeadingIconColor = LiftrixColorsV2.Teal,
                        unfocusedLeadingIconColor = textTertiary,
                        focusedTrailingIconColor = LiftrixColorsV2.Teal,
                        unfocusedTrailingIconColor = textTertiary
                    ),
                    shape = textFieldShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

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
                            uncheckedColor = outlineColor,
                            checkmarkColor = if (isDarkTheme) LiftrixColorsV2.Dark.BackgroundPrimary else LiftrixColorsV2.Light.BackgroundPrimary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "I agree to the terms of use",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

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
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
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
                            color = if (isDarkTheme) LiftrixColorsV2.Dark.TextPrimary else LiftrixColorsV2.Light.TextPrimary
                        )
                    } else {
                        Text(
                            text = "Sign up",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
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
