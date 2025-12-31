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
import androidx.compose.ui.graphics.Color
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
import com.example.liftrix.ui.onboarding.components.PrimaryActionButton
import com.example.liftrix.ui.workout.components.SecondaryActionButton

/**
 * Password strength levels for UI feedback
 */
enum class PasswordStrength(val label: String, val color: Color) {
    WEAK("Weak", Color(0xFFE57373)),
    FAIR("Fair", Color(0xFFFFB74D)),
    GOOD("Good", Color(0xFF81C784)),
    STRONG("Strong", Color(0xFF66BB6A))
}

/**
 * Password strength analyzer
 */
object PasswordStrengthAnalyzer {
    fun analyzePassword(password: String): PasswordStrength {
        if (password.length < 6) return PasswordStrength.WEAK
        
        var score = 0
        
        // Length bonus
        if (password.length >= 8) score += 1
        if (password.length >= 12) score += 1
        
        // Character variety
        if (password.any { it.isLowerCase() }) score += 1
        if (password.any { it.isUpperCase() }) score += 1
        if (password.any { it.isDigit() }) score += 1
        if (password.any { !it.isLetterOrDigit() }) score += 1
        
        return when (score) {
            0, 1, 2 -> PasswordStrength.WEAK
            3, 4 -> PasswordStrength.FAIR
            5 -> PasswordStrength.GOOD
            else -> PasswordStrength.STRONG
        }
    }
    
    fun getPasswordRequirements(password: String): List<PasswordRequirement> {
        return listOf(
            PasswordRequirement(
                text = "At least 8 characters",
                met = password.length >= 8
            ),
            PasswordRequirement(
                text = "Contains lowercase letter",
                met = password.any { it.isLowerCase() }
            ),
            PasswordRequirement(
                text = "Contains uppercase letter",
                met = password.any { it.isUpperCase() }
            ),
            PasswordRequirement(
                text = "Contains number",
                met = password.any { it.isDigit() }
            ),
            PasswordRequirement(
                text = "Contains special character",
                met = password.any { !it.isLetterOrDigit() }
            )
        )
    }
}

/**
 * Password requirement item for display
 */
data class PasswordRequirement(
    val text: String,
    val met: Boolean
)

/**
 * Password strength indicator component
 */
@Composable
fun PasswordStrengthIndicator(
    password: String,
    modifier: Modifier = Modifier
) {
    val strength = remember(password) { PasswordStrengthAnalyzer.analyzePassword(password) }
    val requirements = remember(password) { PasswordStrengthAnalyzer.getPasswordRequirements(password) }
    
    if (password.isNotEmpty()) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Strength indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Password strength:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = strength.label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = strength.color
                )
            }
            
            // Strength bar
            LinearProgressIndicator(
                progress = { 
                    when (strength) {
                        PasswordStrength.WEAK -> 0.25f
                        PasswordStrength.FAIR -> 0.5f
                        PasswordStrength.GOOD -> 0.75f
                        PasswordStrength.STRONG -> 1.0f
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = strength.color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            // Requirements checklist
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                requirements.forEach { requirement ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (requirement.met) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = requirement.text,
                            tint = if (requirement.met) PasswordStrength.GOOD.color else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = requirement.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (requirement.met) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Password change screen with strength indicator and validation.
 * Part of SPEC-20250116-account-management implementation.
 * 
 * Features:
 * - Real-time password strength analysis
 * - Password requirements checklist
 * - Current password reauthentication
 * - Loading states during password update
 * - Error handling with user-friendly messages
 * - Success confirmation
 * - Material 3 design with accessibility support
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordChangeScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Local form state
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isCurrentPasswordVisible by remember { mutableStateOf(false) }
    var isNewPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var hasStartedEditing by remember { mutableStateOf(false) }
    
    // Validation
    val currentPasswordValid = currentPassword.length >= 6
    val newPasswordStrength = remember(newPassword) { PasswordStrengthAnalyzer.analyzePassword(newPassword) }
    val newPasswordValid = newPassword.length >= 8 && newPasswordStrength != PasswordStrength.WEAK
    val confirmPasswordValid = confirmPassword.isNotEmpty() && confirmPassword == newPassword
    val passwordsMatch = newPassword.isNotEmpty() && confirmPassword.isNotEmpty() && newPassword == confirmPassword
    
    val canSubmit = currentPasswordValid && newPasswordValid && confirmPasswordValid && !uiState.isUpdatingPassword
    
    // Handle success navigation
    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) {
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
                        text = "Change Password",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        enabled = !uiState.isUpdatingPassword
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
                        contentDescription = "Password change form with strength indicator and validation"
                    },
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Security Notice
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
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
                                imageVector = Icons.Default.Security,
                                contentDescription = "Password security",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Password Security",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Choose a strong password that you haven't used elsewhere. We recommend using a password manager to generate and store unique passwords.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                // Current Password Section
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Current Password",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Enter your current password to verify your identity",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        OutlinedTextField(
                            value = currentPassword,
                            onValueChange = { 
                                currentPassword = it
                                hasStartedEditing = true
                            },
                            label = { Text("Current Password *") },
                            placeholder = { Text("Enter your current password") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Current password",
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { isCurrentPasswordVisible = !isCurrentPasswordVisible }
                                ) {
                                    Icon(
                                        imageVector = if (isCurrentPasswordVisible) 
                                            Icons.Default.Visibility 
                                        else 
                                            Icons.Default.VisibilityOff,
                                        contentDescription = if (isCurrentPasswordVisible) 
                                            "Hide password" 
                                        else 
                                            "Show password"
                                    )
                                }
                            },
                            enabled = !uiState.isUpdatingPassword,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            visualTransformation = if (isCurrentPasswordVisible) 
                                VisualTransformation.None 
                            else 
                                PasswordVisualTransformation(),
                            isError = hasStartedEditing && !currentPasswordValid,
                            supportingText = if (hasStartedEditing && !currentPasswordValid) {
                                { Text("Current password is required", color = MaterialTheme.colorScheme.error) }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // New Password Section
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "New Password",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Create a strong password with at least 8 characters",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { 
                                newPassword = it
                                hasStartedEditing = true
                            },
                            label = { Text("New Password *") },
                            placeholder = { Text("Enter your new password") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = "New password",
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { isNewPasswordVisible = !isNewPasswordVisible }
                                ) {
                                    Icon(
                                        imageVector = if (isNewPasswordVisible) 
                                            Icons.Default.Visibility 
                                        else 
                                            Icons.Default.VisibilityOff,
                                        contentDescription = if (isNewPasswordVisible) 
                                            "Hide password" 
                                        else 
                                            "Show password"
                                    )
                                }
                            },
                            enabled = !uiState.isUpdatingPassword,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            visualTransformation = if (isNewPasswordVisible) 
                                VisualTransformation.None 
                            else 
                                PasswordVisualTransformation(),
                            isError = hasStartedEditing && newPassword.isNotEmpty() && !newPasswordValid,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Password strength indicator
                        PasswordStrengthIndicator(
                            password = newPassword,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // Confirm Password Section
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Confirm Password",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { 
                                confirmPassword = it
                                hasStartedEditing = true
                            },
                            label = { Text("Confirm New Password *") },
                            placeholder = { Text("Re-enter your new password") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = "Confirm password",
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }
                                ) {
                                    Icon(
                                        imageVector = if (isConfirmPasswordVisible) 
                                            Icons.Default.Visibility 
                                        else 
                                            Icons.Default.VisibilityOff,
                                        contentDescription = if (isConfirmPasswordVisible) 
                                            "Hide password" 
                                        else 
                                            "Show password"
                                    )
                                }
                            },
                            enabled = !uiState.isUpdatingPassword,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    if (canSubmit) {
                                        viewModel.onEvent(
                                            AccountManagementEvent.UpdatePassword(
                                                currentPassword = currentPassword,
                                                newPassword = newPassword
                                            )
                                        )
                                    }
                                }
                            ),
                            visualTransformation = if (isConfirmPasswordVisible) 
                                VisualTransformation.None 
                            else 
                                PasswordVisualTransformation(),
                            isError = hasStartedEditing && confirmPassword.isNotEmpty() && !passwordsMatch,
                            supportingText = if (hasStartedEditing && confirmPassword.isNotEmpty() && !passwordsMatch) {
                                { Text("Passwords do not match", color = MaterialTheme.colorScheme.error) }
                            } else if (hasStartedEditing && passwordsMatch && confirmPassword.isNotEmpty()) {
                                { 
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Passwords match",
                                            tint = PasswordStrength.GOOD.color,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            "Passwords match", 
                                            color = PasswordStrength.GOOD.color,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
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
                        enabled = !uiState.isUpdatingPassword,
                        leadingIcon = Icons.Default.Cancel,
                        modifier = Modifier.weight(1f)
                    )
                    
                    PrimaryActionButton(
                        onClick = {
                            keyboardController?.hide()
                            viewModel.onEvent(
                                AccountManagementEvent.UpdatePassword(
                                    currentPassword = currentPassword,
                                    newPassword = newPassword
                                )
                            )
                        },
                        text = if (uiState.isUpdatingPassword) "Updating..." else "Update Password",
                        enabled = canSubmit,
                        isLoading = uiState.isUpdatingPassword,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Add bottom padding for keyboard
                Spacer(modifier = Modifier.height(100.dp))
            }
            
            // Error handling (same as EmailChangeScreen)
            uiState.error?.let { errorMessage ->
                LaunchedEffect(errorMessage) {
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
            
            // Success handling (same as EmailChangeScreen)
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
 * Preview for PasswordChangeScreen
 */
@Preview(showBackground = true)
@Composable
private fun PasswordChangeScreenPreview() {
    LiftrixTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            PasswordStrengthIndicatorPreview()
        }
    }
}

/**
 * Preview for password strength indicator
 */
@Composable
private fun PasswordStrengthIndicatorPreview() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Password Strength Examples", style = MaterialTheme.typography.headlineSmall)
        
        listOf(
            "123" to "Weak password",
            "password123" to "Fair password",
            "Password123" to "Good password",
            "MyStr0ng!Pass" to "Strong password"
        ).forEach { (password, label) ->
            Column {
                Text(label, style = MaterialTheme.typography.titleSmall)
                PasswordStrengthIndicator(password = password)
            }
        }
    }
}
