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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.onboarding.components.PrimaryActionButton
import com.example.liftrix.ui.workout.components.SecondaryActionButton
import kotlinx.coroutines.delay

/**
 * Username availability check states
 */
sealed class UsernameAvailability {
    data object None : UsernameAvailability()
    data object Checking : UsernameAvailability()
    data object Available : UsernameAvailability()
    data class Unavailable(val reason: String) : UsernameAvailability()
    data class Invalid(val reason: String) : UsernameAvailability()
}

/**
 * Username validation and availability checker
 */
object UsernameValidator {
    private val reservedUsernames = setOf(
        "admin", "root", "user", "test", "demo", "api", "support", "help",
        "contact", "info", "mail", "email", "noreply", "no-reply",
        "liftrix", "lift", "fitness", "workout", "gym", "exercise"
    )
    
    fun validateUsername(username: String): UsernameAvailability {
        when {
            username.isBlank() -> return UsernameAvailability.None
            username.length < 3 -> return UsernameAvailability.Invalid("Username must be at least 3 characters")
            username.length > 20 -> return UsernameAvailability.Invalid("Username must be 20 characters or less")
            !username.matches(Regex("^[a-zA-Z0-9_]+$")) -> return UsernameAvailability.Invalid("Username can only contain letters, numbers, and underscores")
            username.startsWith("_") || username.endsWith("_") -> return UsernameAvailability.Invalid("Username cannot start or end with underscore")
            username.contains("__") -> return UsernameAvailability.Invalid("Username cannot contain consecutive underscores")
            reservedUsernames.contains(username.lowercase()) -> return UsernameAvailability.Unavailable("This username is reserved")
        }
        
        return UsernameAvailability.Checking
    }
    
    // Simulated availability check - in real app this would call the API
    suspend fun checkAvailability(username: String): UsernameAvailability {
        delay(800) // Simulate network delay
        
        // Simulate some usernames being taken
        val takenUsernames = setOf("john", "jane", "user123", "test123", "admin123")
        
        return if (takenUsernames.contains(username.lowercase())) {
            UsernameAvailability.Unavailable("Username is already taken")
        } else {
            UsernameAvailability.Available
        }
    }
    
    fun getUsernameSuggestions(baseUsername: String): List<String> {
        val cleanBase = baseUsername.replace(Regex("[^a-zA-Z0-9]"), "").take(15)
        if (cleanBase.length < 3) return emptyList()
        
        return listOf(
            "${cleanBase}_fit",
            "${cleanBase}_gym",
            "${cleanBase}123",
            "${cleanBase}_${(100..999).random()}",
            "${cleanBase}fitness"
        ).take(3)
    }
}

/**
 * Username availability indicator component
 */
@Composable
fun UsernameAvailabilityIndicator(
    availability: UsernameAvailability,
    modifier: Modifier = Modifier
) {
    when (availability) {
        is UsernameAvailability.None -> {
            // Show nothing when no username entered
        }
        is UsernameAvailability.Checking -> {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Checking availability...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        is UsernameAvailability.Available -> {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF66BB6A),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Username is available!",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF66BB6A)
                )
            }
        }
        is UsernameAvailability.Unavailable -> {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Cancel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = availability.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        is UsernameAvailability.Invalid -> {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = availability.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Username suggestions component
 */
@Composable
fun UsernameSuggestions(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (suggestions.isNotEmpty()) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Suggested usernames:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                suggestions.forEach { suggestion ->
                    FilterChip(
                        onClick = { onSuggestionClick(suggestion) },
                        label = { Text(suggestion, style = MaterialTheme.typography.bodySmall) },
                        selected = false,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * Username change screen with real-time availability checking.
 * Part of SPEC-20250116-account-management implementation.
 * 
 * Features:
 * - Real-time username validation
 * - Availability checking with debouncing
 * - Username suggestions for taken usernames
 * - Current username display
 * - Loading states during username update
 * - Error handling with user-friendly messages
 * - Success confirmation
 * - Material 3 design with accessibility support
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsernameChangeScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Local form state
    var newUsername by remember { mutableStateOf("") }
    var availability by remember { mutableStateOf<UsernameAvailability>(UsernameAvailability.None) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var hasStartedEditing by remember { mutableStateOf(false) }
    
    // Debounced availability checking
    LaunchedEffect(newUsername) {
        if (newUsername.isNotEmpty()) {
            val validation = UsernameValidator.validateUsername(newUsername)
            availability = validation
            
            if (validation is UsernameAvailability.Checking) {
                delay(1000) // Debounce for 1 second
                if (newUsername.isNotEmpty()) { // Check if user hasn't cleared the field
                    availability = UsernameValidator.checkAvailability(newUsername)
                    
                    // Generate suggestions if username is taken
                    if (availability is UsernameAvailability.Unavailable) {
                        suggestions = UsernameValidator.getUsernameSuggestions(newUsername)
                    } else {
                        suggestions = emptyList()
                    }
                }
            }
        } else {
            availability = UsernameAvailability.None
            suggestions = emptyList()
        }
    }
    
    val isUsernameValid = availability is UsernameAvailability.Available
    val canSubmit = isUsernameValid && newUsername.isNotEmpty() && !uiState.isUpdatingUsername
    
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
                        text = "Change Username",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        enabled = !uiState.isUpdatingUsername
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
                        contentDescription = "Username change form with availability checking"
                    },
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Current Username Display
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Current Username",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = uiState.accountInfo?.username ?: "Loading...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Username Guidelines
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
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Username Guidelines",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "• 3-20 characters long",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "• Letters, numbers, and underscores only",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "• Cannot start or end with underscore",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "• Must be unique across Liftrix",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                
                // New Username Section
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "New Username",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Choose a unique username that represents you",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        OutlinedTextField(
                            value = newUsername,
                            onValueChange = { value ->
                                // Filter input to only allow valid characters
                                val filteredValue = value.filter { it.isLetterOrDigit() || it == '_' }
                                    .take(20) // Limit to 20 characters
                                newUsername = filteredValue
                                hasStartedEditing = true
                            },
                            label = { Text("New Username *") },
                            placeholder = { Text("Enter your new username") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null
                                )
                            },
                            enabled = !uiState.isUpdatingUsername,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                capitalization = KeyboardCapitalization.None,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    if (canSubmit) {
                                        viewModel.onEvent(
                                            AccountManagementEvent.UpdateUsername(newUsername)
                                        )
                                    }
                                }
                            ),
                            isError = availability is UsernameAvailability.Invalid || availability is UsernameAvailability.Unavailable,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Availability indicator
                        UsernameAvailabilityIndicator(
                            availability = availability,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Username suggestions
                        UsernameSuggestions(
                            suggestions = suggestions,
                            onSuggestionClick = { suggestion ->
                                newUsername = suggestion
                                hasStartedEditing = true
                            },
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
                        enabled = !uiState.isUpdatingUsername,
                        leadingIcon = Icons.Default.Cancel,
                        modifier = Modifier.weight(1f)
                    )
                    
                    PrimaryActionButton(
                        onClick = {
                            keyboardController?.hide()
                            viewModel.onEvent(
                                AccountManagementEvent.UpdateUsername(newUsername)
                            )
                        },
                        text = if (uiState.isUpdatingUsername) "Updating..." else "Update Username",
                        enabled = canSubmit,
                        isLoading = uiState.isUpdatingUsername,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Add bottom padding for keyboard
                Spacer(modifier = Modifier.height(100.dp))
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
            
            // Success handling
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
                            contentDescription = null,
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
 * Preview for UsernameChangeScreen
 */
@Preview(showBackground = true)
@Composable
private fun UsernameChangeScreenPreview() {
    LiftrixTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            UsernameAvailabilityPreview()
        }
    }
}

/**
 * Preview for username availability states
 */
@Composable
private fun UsernameAvailabilityPreview() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Username Availability States", style = MaterialTheme.typography.headlineSmall)
        
        UsernameAvailabilityIndicator(availability = UsernameAvailability.Checking)
        UsernameAvailabilityIndicator(availability = UsernameAvailability.Available)
        UsernameAvailabilityIndicator(availability = UsernameAvailability.Unavailable("Username is already taken"))
        UsernameAvailabilityIndicator(availability = UsernameAvailability.Invalid("Username too short"))
        
        Spacer(modifier = Modifier.height(16.dp))
        
        UsernameSuggestions(
            suggestions = listOf("john_fit", "john_gym", "john123"),
            onSuggestionClick = { }
        )
    }
}