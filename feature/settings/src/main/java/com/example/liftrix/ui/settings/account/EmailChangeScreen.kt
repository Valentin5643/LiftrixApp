package com.example.liftrix.ui.settings.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.components.actions.PrimaryActionButton
import com.example.liftrix.ui.components.actions.SecondaryActionButton

/**
 * Simple validation result for UI feedback
 */
sealed class ValidationResult {
    data object None : ValidationResult()
    data object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}

/**
 * Email change screen with real-time validation and reauthentication flow.
 * Part of SPEC-20250116-account-management implementation.
 * 
 * Features:
 * - Real-time email format validation
 * - Current password reauthentication requirement
 * - Loading states during email update
 * - Error handling with user-friendly messages
 * - Success confirmation with verification email notice
 * - Material 3 design with accessibility support
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailChangeScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Local form state
    var newEmail by remember { mutableStateOf("") }
    var currentPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var hasStartedEditing by remember { mutableStateOf(false) }
    
    // Real-time validation
    val emailValidation = remember(newEmail, hasStartedEditing) {
        when {
            !hasStartedEditing -> ValidationResult.None
            newEmail.isBlank() -> ValidationResult.Error("Email address is required")
            !android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches() -> 
                ValidationResult.Error("Please enter a valid email address")
            newEmail == uiState.accountInfo?.email -> 
                ValidationResult.Error("This is your current email address")
            else -> ValidationResult.Success
        }
    }
    
    val passwordValidation = remember(currentPassword, hasStartedEditing) {
        when {
            !hasStartedEditing -> ValidationResult.None
            currentPassword.isBlank() -> ValidationResult.Error("Current password is required")
            currentPassword.length < 6 -> ValidationResult.Error("Password is too short")
            else -> ValidationResult.Success
        }
    }
    
    val canSubmit = emailValidation is ValidationResult.Success && 
                   passwordValidation is ValidationResult.Success &&
                   !uiState.isUpdatingEmail
    
    // Handle success navigation
    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) {
            // Auto-dismiss success message and navigate back after 3 seconds
            kotlinx.coroutines.delay(3000)
            viewModel.onEvent(AccountManagementEvent.DismissSuccess)
            onNavigateBack()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Change Email",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        enabled = !uiState.isUpdatingEmail
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
                    .semantics {
                        contentDescription = "Email change form with current email display and validation"
                    },
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Current Email Display
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Current Email",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = uiState.accountInfo?.email ?: "Loading...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        if (uiState.accountInfo?.emailVerified == false) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Email not verified",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Email not verified",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                
                // Instructions
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Email change info",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Email Change Instructions",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "You'll need to verify your new email address after the change. A verification email will be sent to both your old and new email addresses.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                // Form Section
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Update Email Address",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Enter your new email and current password",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // New Email Field
                        OutlinedTextField(
                            value = newEmail,
                            onValueChange = { 
                                newEmail = it
                                hasStartedEditing = true
                                viewModel.onEvent(AccountManagementEvent.ValidateEmail(it))
                            },
                            label = { Text("New Email Address *") },
                            placeholder = { Text("Enter your new email address") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = "New email",
                                )
                            },
                            enabled = !uiState.isUpdatingEmail,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            isError = emailValidation is ValidationResult.Error,
                            supportingText = if (emailValidation is ValidationResult.Error) {
                                { Text(emailValidation.message, color = MaterialTheme.colorScheme.error) }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Current Password Field
                        OutlinedTextField(
                            value = currentPassword,
                            onValueChange = { 
                                currentPassword = it
                                hasStartedEditing = true
                            },
                            label = { Text("Current Password *") },
                            placeholder = { Text("Enter your current password for verification") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Current password",
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { isPasswordVisible = !isPasswordVisible }
                                ) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) 
                                            Icons.Default.Visibility 
                                        else 
                                            Icons.Default.VisibilityOff,
                                        contentDescription = if (isPasswordVisible) 
                                            "Hide password" 
                                        else 
                                            "Show password"
                                    )
                                }
                            },
                            enabled = !uiState.isUpdatingEmail,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    if (canSubmit) {
                                        viewModel.onEvent(
                                            AccountManagementEvent.UpdateEmail(
                                                newEmail = newEmail,
                                                currentPassword = currentPassword
                                            )
                                        )
                                    }
                                }
                            ),
                            visualTransformation = if (isPasswordVisible) 
                                VisualTransformation.None 
                            else 
                                PasswordVisualTransformation(),
                            isError = passwordValidation is ValidationResult.Error,
                            supportingText = if (passwordValidation is ValidationResult.Error) {
                                { Text(passwordValidation.message, color = MaterialTheme.colorScheme.error) }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SecondaryActionButton(
                        text = "Cancel",
                        onClick = onNavigateBack,
                        enabled = !uiState.isUpdatingEmail,
                        leadingIcon = Icons.Default.Cancel,
                        modifier = Modifier.weight(1f)
                    )
                    
                    PrimaryActionButton(
                        onClick = {
                            keyboardController?.hide()
                            viewModel.onEvent(
                                AccountManagementEvent.UpdateEmail(
                                    newEmail = newEmail,
                                    currentPassword = currentPassword
                                )
                            )
                        },
                        text = if (uiState.isUpdatingEmail) "Updating..." else "Update Email",
                        enabled = canSubmit,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Add bottom padding for keyboard
                Spacer(modifier = Modifier.height(100.dp))
            }
            
            // Error Snackbar
            uiState.error?.let { errorMessage ->
                LaunchedEffect(errorMessage) {
                    // Auto-dismiss error after 5 seconds
                    kotlinx.coroutines.delay(5000)
                    viewModel.onEvent(AccountManagementEvent.DismissError)
                }
                
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.onEvent(AccountManagementEvent.DismissError) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss error",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            
            // Success Snackbar
            uiState.successMessage?.let { successMessage ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = successMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.onEvent(AccountManagementEvent.DismissSuccess) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss success",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Preview for EmailChangeScreen
 */
@Preview(showBackground = true)
@Composable
private fun EmailChangeScreenPreview() {
    LiftrixTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            // Note: This is a simplified preview - actual implementation uses ViewModel
            EmailChangeScreenContent(
                currentEmail = "user@example.com",
                isEmailVerified = false,
                onNavigateBack = { },
                onUpdateEmail = { _, _ -> }
            )
        }
    }
}

/**
 * Preview content component for EmailChangeScreen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailChangeScreenContent(
    currentEmail: String,
    isEmailVerified: Boolean,
    onNavigateBack: () -> Unit,
    onUpdateEmail: (String, String) -> Unit
) {
    var newEmail by remember { mutableStateOf("") }
    var currentPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    
    val emailValidation = remember(newEmail) {
        when {
            newEmail.isBlank() -> ValidationResult.None
            !android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches() -> 
                ValidationResult.Error("Please enter a valid email address")
            newEmail == currentEmail -> 
                ValidationResult.Error("This is your current email address")
            else -> ValidationResult.Success
        }
    }
    
    val passwordValidation = remember(currentPassword) {
        when {
            currentPassword.isBlank() -> ValidationResult.None
            currentPassword.length < 6 -> ValidationResult.Error("Password is too short")
            else -> ValidationResult.Success
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Change Email",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Current Email Display
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Current Email",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = currentEmail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (!isEmailVerified) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Email not verified",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Email not verified",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            
            // Form Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Update Email Address",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Enter your new email and current password",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it },
                        label = { Text("New Email Address *") },
                        placeholder = { Text("Enter your new email address") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "New email",
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        isError = emailValidation is ValidationResult.Error,
                        supportingText = if (emailValidation is ValidationResult.Error) {
                            { Text(emailValidation.message, color = MaterialTheme.colorScheme.error) }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("Current Password *") },
                        placeholder = { Text("Enter your current password for verification") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Current password",
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { isPasswordVisible = !isPasswordVisible }
                            ) {
                                Icon(
                                    imageVector = if (isPasswordVisible) 
                                        Icons.Default.Visibility 
                                    else 
                                        Icons.Default.VisibilityOff,
                                    contentDescription = if (isPasswordVisible) 
                                        "Hide password" 
                                    else 
                                        "Show password"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        visualTransformation = if (isPasswordVisible) 
                            VisualTransformation.None 
                        else 
                            PasswordVisualTransformation(),
                        isError = passwordValidation is ValidationResult.Error,
                        supportingText = if (passwordValidation is ValidationResult.Error) {
                            { Text(passwordValidation.message, color = MaterialTheme.colorScheme.error) }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
