package com.example.liftrix.ui.settings.account

import androidx.compose.foundation.BorderStroke
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

/**
 * Account deletion flow steps
 */
enum class DeletionStep {
    WARNING,           // Show warning about data loss
    CONFIRMATION,      // Require typing confirmation text
    AUTHENTICATION,   // Require password verification
    PROCESSING,       // Show deletion in progress
    COMPLETED         // Show deletion completed
}

/**
 * Data loss warning item
 */
data class DataLossItem(
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

/**
 * Account deletion flow with multi-step confirmation process.
 * Part of SPEC-20250116-account-management implementation.
 * 
 * Features:
 * - Multi-step confirmation process
 * - Clear data loss warnings
 * - Text confirmation requirement
 * - Password authentication
 * - Loading states during deletion
 * - Irreversible action warnings
 * - Material 3 design with accessibility support
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDeletionFlow(
    onNavigateBack: () -> Unit,
    onDeletionCompleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Local state for deletion flow
    var currentStep by remember { mutableStateOf(DeletionStep.WARNING) }
    var confirmationText by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var hasReadWarnings by remember { mutableStateOf(false) }
    
    // Required confirmation text
    val requiredConfirmation = "DELETE MY ACCOUNT"
    val isConfirmationValid = confirmationText == requiredConfirmation
    val isPasswordValid = password.length >= 6
    
    // Handle deletion completion
    LaunchedEffect(uiState.accountDeleted) {
        if (uiState.accountDeleted) {
            currentStep = DeletionStep.COMPLETED
            kotlinx.coroutines.delay(2000)
            onDeletionCompleted()
        }
    }
    
    // Handle deletion processing
    LaunchedEffect(uiState.isDeletingAccount) {
        if (uiState.isDeletingAccount) {
            currentStep = DeletionStep.PROCESSING
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentStep) {
                            DeletionStep.WARNING -> "Delete Account"
                            DeletionStep.CONFIRMATION -> "Confirm Deletion"
                            DeletionStep.AUTHENTICATION -> "Verify Identity"
                            DeletionStep.PROCESSING -> "Deleting Account"
                            DeletionStep.COMPLETED -> "Account Deleted"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (currentStep) {
                            DeletionStep.PROCESSING, DeletionStep.COMPLETED -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                },
                navigationIcon = {
                    if (currentStep != DeletionStep.PROCESSING && currentStep != DeletionStep.COMPLETED) {
                        IconButton(
                            onClick = {
                                if (currentStep == DeletionStep.WARNING) {
                                    onNavigateBack()
                                } else {
                                    currentStep = when (currentStep) {
                                        DeletionStep.CONFIRMATION -> DeletionStep.WARNING
                                        DeletionStep.AUTHENTICATION -> DeletionStep.CONFIRMATION
                                        else -> DeletionStep.WARNING
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
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
            when (currentStep) {
                DeletionStep.WARNING -> {
                    WarningStep(
                        hasReadWarnings = hasReadWarnings,
                        onWarningsRead = { hasReadWarnings = it },
                        onContinue = { currentStep = DeletionStep.CONFIRMATION },
                        onCancel = onNavigateBack,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                DeletionStep.CONFIRMATION -> {
                    ConfirmationStep(
                        confirmationText = confirmationText,
                        onConfirmationTextChange = { confirmationText = it },
                        requiredText = requiredConfirmation,
                        isValid = isConfirmationValid,
                        onContinue = { currentStep = DeletionStep.AUTHENTICATION },
                        onBack = { currentStep = DeletionStep.WARNING },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                DeletionStep.AUTHENTICATION -> {
                    AuthenticationStep(
                        password = password,
                        onPasswordChange = { password = it },
                        isPasswordVisible = isPasswordVisible,
                        onPasswordVisibilityToggle = { isPasswordVisible = !isPasswordVisible },
                        isValid = isPasswordValid,
                        isLoading = uiState.isDeletingAccount,
                        onConfirm = {
                            keyboardController?.hide()
                            viewModel.onEvent(AccountManagementEvent.DeleteAccount(password))
                        },
                        onBack = { currentStep = DeletionStep.CONFIRMATION },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                DeletionStep.PROCESSING -> {
                    ProcessingStep(
                        modifier = Modifier.fillMaxSize()
                    )
                }
                DeletionStep.COMPLETED -> {
                    CompletedStep(
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            // Error handling
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
                            contentDescription = null,
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
        }
    }
}

/**
 * Step 1: Warning about data loss
 */
@Composable
private fun WarningStep(
    hasReadWarnings: Boolean,
    onWarningsRead: (Boolean) -> Unit,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    val dataLossItems = listOf(
        DataLossItem(
            title = "All Workout Data",
            description = "Your workout history, routines, and progress tracking will be permanently lost",
            icon = Icons.Default.FitnessCenter
        ),
        DataLossItem(
            title = "Social Connections",
            description = "Your friends, followers, and all social interactions will be removed",
            icon = Icons.Default.People
        ),
        DataLossItem(
            title = "Personal Records",
            description = "All your personal records, achievements, and milestone data will be deleted",
            icon = Icons.Default.EmojiEvents
        ),
        DataLossItem(
            title = "Account Settings",
            description = "Your preferences, settings, and profile information will be permanently removed",
            icon = Icons.Default.Settings
        )
    )
    
    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(16.dp)
            .semantics {
                contentDescription = "Account deletion warning with data loss information"
            },
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Warning header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
            ),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.error)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "This Action Cannot Be Undone",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Deleting your account will permanently remove all your data from Liftrix. This includes all the information listed below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        // Data loss items
        Text(
            text = "What will be permanently deleted:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        
        dataLossItems.forEach { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // Alternative options
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
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Consider These Alternatives",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "• Change your password if you're worried about account security",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "• Update your privacy settings to control data sharing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "• Export your workout data before deletion if needed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // Acknowledgment checkbox
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = hasReadWarnings,
                onCheckedChange = onWarningsRead,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.error
                )
            )
            Text(
                text = "I understand that this action is permanent and cannot be undone",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Cancel,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Keep Account")
            }
            
            Button(
                onClick = onContinue,
                enabled = hasReadWarnings,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Continue")
            }
        }
        
        Spacer(modifier = Modifier.height(100.dp))
    }
}

/**
 * Step 2: Text confirmation
 */
@Composable
private fun ConfirmationStep(
    confirmationText: String,
    onConfirmationTextChange: (String) -> Unit,
    requiredText: String,
    isValid: Boolean,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .semantics {
                contentDescription = "Text confirmation step for account deletion"
            },
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Confirmation instruction
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "Type the confirmation text",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "To confirm that you want to delete your account, please type the following text exactly as shown:",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Required text display
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.error)
        ) {
            Text(
                text = requiredText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            )
        }
        
        // Text input
        OutlinedTextField(
            value = confirmationText,
            onValueChange = onConfirmationTextChange,
            label = { Text("Type the confirmation text") },
            placeholder = { Text("Enter the text exactly as shown above") },
            isError = confirmationText.isNotEmpty() && !isValid,
            supportingText = {
                if (confirmationText.isNotEmpty() && !isValid) {
                    Text(
                        "Text must match exactly: $requiredText",
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (isValid) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF66BB6A),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Text matches correctly",
                            color = Color(0xFF66BB6A)
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (isValid) onContinue()
                }
            ),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back")
            }
            
            Button(
                onClick = onContinue,
                enabled = isValid,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Continue")
            }
        }
    }
}

/**
 * Step 3: Password authentication
 */
@Composable
private fun AuthenticationStep(
    password: String,
    onPasswordChange: (String) -> Unit,
    isPasswordVisible: Boolean,
    onPasswordVisibilityToggle: () -> Unit,
    isValid: Boolean,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    
    Column(
        modifier = modifier
            .padding(16.dp)
            .semantics {
                contentDescription = "Password authentication step for account deletion"
            },
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Authentication instruction
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "Verify Your Identity",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Enter your current password to confirm that you are authorized to delete this account.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Password input
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Current Password") },
            placeholder = { Text("Enter your account password") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null
                )
            },
            trailingIcon = {
                IconButton(onClick = onPasswordVisibilityToggle) {
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
            visualTransformation = if (isPasswordVisible) 
                VisualTransformation.None 
            else 
                PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    if (isValid && !isLoading) onConfirm()
                }
            ),
            enabled = !isLoading,
            isError = password.isNotEmpty() && !isValid,
            supportingText = if (password.isNotEmpty() && !isValid) {
                { Text("Password must be at least 6 characters", color = MaterialTheme.colorScheme.error) }
            } else null,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Final warning
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.error)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Final Warning",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Once you click 'Delete Account', your account and all data will be permanently deleted. This action cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                enabled = !isLoading,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back")
            }
            
            Button(
                onClick = {
                    keyboardController?.hide()
                    onConfirm()
                },
                enabled = isValid && !isLoading,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isLoading) "Deleting..." else "Delete Account")
            }
        }
    }
}

/**
 * Step 4: Processing deletion
 */
@Composable
private fun ProcessingStep(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .semantics {
                contentDescription = "Account deletion in progress"
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            strokeWidth = 4.dp,
            color = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Deleting Your Account",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Please wait while we permanently delete your account and all associated data. This may take a few moments.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Step 5: Deletion completed
 */
@Composable
private fun CompletedStep(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .semantics {
                contentDescription = "Account deletion completed successfully"
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF66BB6A),
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Account Deleted",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Your account and all associated data have been permanently deleted. Thank you for using Liftrix.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Preview for Account Deletion Flow
 */
@Preview(showBackground = true)
@Composable
private fun AccountDeletionFlowPreview() {
    LiftrixTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            WarningStep(
                hasReadWarnings = false,
                onWarningsRead = { },
                onContinue = { },
                onCancel = { }
            )
        }
    }
}