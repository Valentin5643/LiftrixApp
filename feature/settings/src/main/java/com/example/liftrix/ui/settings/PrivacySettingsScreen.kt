package com.example.liftrix.ui.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.social.SocialPrivacySettings
import com.example.liftrix.domain.model.social.ProfileVisibility
import com.example.liftrix.ui.common.components.LoadingDialog
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.components.actions.UnifiedWorkoutCard

/**
 * Privacy settings screen providing granular control over social features and data visibility.
 * 
 * Features:
 * - Master social toggle for complete social feature control
 * - Profile visibility settings (Public, Followers Only, Private)
 * - Content sharing controls (workouts, achievements, stats)
 * - Discovery settings (suggestions, search visibility)
 * - Notification preferences
 * - Account deactivation and data deletion options
 * 
 * All changes take effect immediately upon user interaction.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    viewModel: PrivacySettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Handle success/error messages
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.handleEvent(PrivacySettingsEvent.ClearMessage)
        }
    }
    
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.handleEvent(PrivacySettingsEvent.ClearMessage)
        }
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Privacy Settings",
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Go back"
                            )
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        when (val privacyState = uiState.privacySettingsState) {
            is UiState.Loading -> {
                LoadingDialog(message = "Loading privacy settings...")
            }
            
            is UiState.Success -> {
                val settings = privacyState.data
                PrivacySettingsContent(
                    settings = settings,
                    onEvent = viewModel::handleEvent,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
            
            is UiState.Error -> {
                ErrorContent(
                    error = privacyState.error.message,
                    onRetry = { viewModel.handleEvent(PrivacySettingsEvent.LoadPrivacySettings) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp)
                )
            }
            
            is UiState.Empty -> {
                EmptyContent(
                    onCreateSettings = { viewModel.handleEvent(PrivacySettingsEvent.CreateDefaultSettings) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp)
                )
            }
            else -> {
                // Fallback for any other UiState types
                Text(
                    text = "Unknown state",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp)
                )
            }
        }
        
        // Confirmation dialogs
        if (uiState.showDisableSocialConfirmation) {
            DisableSocialConfirmationDialog(
                onConfirm = { viewModel.handleEvent(PrivacySettingsEvent.ConfirmDisableSocial) },
                onDismiss = { viewModel.handleEvent(PrivacySettingsEvent.DismissDisableSocialConfirmation) }
            )
        }
        
        if (uiState.showDeleteDataConfirmation) {
            DeleteDataConfirmationDialog(
                onConfirm = { viewModel.handleEvent(PrivacySettingsEvent.ConfirmDeleteData) },
                onDismiss = { viewModel.handleEvent(PrivacySettingsEvent.DismissDeleteDataConfirmation) }
            )
        }
    }
    
    // Loading overlay for operations
    if (uiState.isLoading) {
        LoadingDialog(
            message = when {
                uiState.isUpdatingSettings -> "Updating settings..."
                uiState.isDeletingData -> "Deleting social data..."
                else -> "Please wait..."
            }
        )
    }
}

@Composable
private fun PrivacySettingsContent(
    settings: SocialPrivacySettings,
    onEvent: (PrivacySettingsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Master social toggle
        MasterSocialSection(
            socialEnabled = settings.socialEnabled,
            onToggle = { onEvent(PrivacySettingsEvent.ToggleSocialEnabled(it)) }
        )
        
        if (settings.socialEnabled) {
            // Profile visibility settings
            ProfileVisibilitySection(
                visibility = settings.profileVisibility,
                onVisibilityChange = { onEvent(PrivacySettingsEvent.UpdateProfileVisibility(it)) }
            )
            
            // Content sharing controls
            ContentSharingSection(
                settings = settings,
                onEvent = onEvent
            )
            
            // Discovery settings
            DiscoverySection(
                settings = settings,
                onEvent = onEvent
            )
            
            // Notification settings
            NotificationSection(
                settings = settings,
                onEvent = onEvent
            )
            
            // Blocked users management
            BlockedUsersSection(
                onEvent = onEvent
            )
        }
        
        // Account management
        AccountManagementSection(
            socialEnabled = settings.socialEnabled,
            onEvent = onEvent
        )
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun MasterSocialSection(
    socialEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    UnifiedWorkoutCard(
        title = "Social Features",
        subtitle = if (socialEnabled) "Enabled - Connect with the community" else "Disabled - Keep your workouts private",
        leadingIcon = if (socialEnabled) Icons.Default.Groups else Icons.Default.Lock,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (socialEnabled) {
                    "Social features are enabled. You can connect with other users, share workouts, and participate in the community."
                } else {
                    "Social features are disabled. Your workouts and progress remain completely private."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enable social features",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                
                Switch(
                    checked = socialEnabled,
                    onCheckedChange = onToggle
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileVisibilitySection(
    visibility: ProfileVisibility,
    onVisibilityChange: (ProfileVisibility) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = when (visibility) {
                        ProfileVisibility.PUBLIC -> Icons.Default.Visibility
                        ProfileVisibility.FOLLOWERS -> Icons.Default.Groups
                        ProfileVisibility.PRIVATE -> Icons.Default.VisibilityOff
                    },
                    contentDescription = "Profile visibility",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                
                Text(
                    text = "Profile Visibility",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Text(
                text = "Control who can see your profile and basic information",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = when (visibility) {
                        ProfileVisibility.PUBLIC -> "Public"
                        ProfileVisibility.FOLLOWERS -> "Followers Only"
                        ProfileVisibility.PRIVATE -> "Private"
                    },
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    ProfileVisibility.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = when (option) {
                                            ProfileVisibility.PUBLIC -> "Public"
                                            ProfileVisibility.FOLLOWERS -> "Followers Only"
                                            ProfileVisibility.PRIVATE -> "Private"
                                        },
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = when (option) {
                                            ProfileVisibility.PUBLIC -> "Anyone can see your profile"
                                            ProfileVisibility.FOLLOWERS -> "Only approved followers can see your profile"
                                            ProfileVisibility.PRIVATE -> "Your profile is completely hidden"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onVisibilityChange(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentSharingSection(
    settings: SocialPrivacySettings,
    onEvent: (PrivacySettingsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = "Content sharing",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                
                Text(
                    text = "Content Sharing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Text(
                text = "Choose what content you want to share with other users",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            SettingToggle(
                title = "Share Workouts",
                description = "Allow followers to see your workout sessions",
                checked = settings.workoutSharingEnabled,
                onCheckedChange = { onEvent(PrivacySettingsEvent.ToggleWorkoutSharing(it)) }
            )
            
            SettingToggle(
                title = "Share Achievements",
                description = "Show personal records and milestones",
                checked = settings.showAchievements,
                onCheckedChange = { onEvent(PrivacySettingsEvent.ToggleShowAchievements(it)) }
            )
            
            SettingToggle(
                title = "Share Workout Stats",
                description = "Display workout frequency and progress statistics",
                checked = settings.showWorkoutStats,
                onCheckedChange = { onEvent(PrivacySettingsEvent.ToggleShowWorkoutStats(it)) }
            )
            
            SettingToggle(
                title = "Share Workout Streak",
                description = "Show your current workout consistency streak",
                checked = settings.showWorkoutStreak,
                onCheckedChange = { onEvent(PrivacySettingsEvent.ToggleShowWorkoutStreak(it)) }
            )
        }
    }
}

@Composable
private fun DiscoverySection(
    settings: SocialPrivacySettings,
    onEvent: (PrivacySettingsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = "Discovery settings",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                
                Text(
                    text = "Discovery Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Text(
                text = "Control how other users can find and connect with you",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            SettingToggle(
                title = "Allow Follow Requests",
                description = "Let other users request to follow you",
                checked = settings.allowFollowRequests,
                onCheckedChange = { onEvent(PrivacySettingsEvent.ToggleAllowFollowRequests(it)) }
            )
            
            SettingToggle(
                title = "Appear in Suggestions",
                description = "Be suggested to users with similar interests",
                checked = !settings.hideFromSuggestions,
                onCheckedChange = { onEvent(PrivacySettingsEvent.ToggleHideFromSuggestions(!it)) }
            )
            
            SettingToggle(
                title = "Appear in Search",
                description = "Allow users to find you by searching your username",
                checked = !settings.hideFromSearch,
                onCheckedChange = { onEvent(PrivacySettingsEvent.ToggleHideFromSearch(!it)) }
            )
            
            SettingToggle(
                title = "Enable Gym Buddies",
                description = "Connect with training partners through QR codes",
                checked = settings.gymBuddiesEnabled,
                onCheckedChange = { onEvent(PrivacySettingsEvent.ToggleGymBuddies(it)) }
            )
        }
    }
}

@Composable
private fun NotificationSection(
    settings: SocialPrivacySettings,
    onEvent: (PrivacySettingsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Social notifications",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                
                Text(
                    text = "Social Notifications",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Text(
                text = "Choose which social activities you want to be notified about",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Note: Notification settings could be expanded with specific notification types
            Text(
                text = "Notification preferences will be available in a future update.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BlockedUsersSection(
    onEvent: (PrivacySettingsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
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
                imageVector = Icons.Default.Block,
                contentDescription = "Blocked users",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Blocked Users",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "Manage users you've blocked",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(
                onClick = { onEvent(PrivacySettingsEvent.NavigateToBlockedUsers) }
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "View blocked users",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AccountManagementSection(
    socialEnabled: Boolean,
    onEvent: (PrivacySettingsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PersonRemove,
                    contentDescription = "Account management",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(24.dp)
                )
                
                Text(
                    text = "Account Management",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            
            Text(
                text = "Manage your social data and account settings",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            if (socialEnabled) {
                OutlinedButton(
                    onClick = { onEvent(PrivacySettingsEvent.ShowDisableSocialConfirmation) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Disable Social Features")
                }
            }
            
            OutlinedButton(
                onClick = { onEvent(PrivacySettingsEvent.ShowDeleteDataConfirmation) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (socialEnabled) "Delete All Social Data" else "Delete Social Account Data",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "$title: $description"
            },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Failed to load privacy settings",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun EmptyContent(
    onCreateSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No privacy settings found",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Create default privacy settings to get started with social features",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        Button(onClick = onCreateSettings) {
            Text("Create Settings")
        }
    }
}

@Composable
private fun DisableSocialConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Disable Social Features?")
        },
        text = {
            Text("This will disable all social features and hide your profile from other users. Your workout data will remain private. You can re-enable social features at any time.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Disable")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeleteDataConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Delete Social Data?")
        },
        text = {
            Text("This will permanently delete your social profile, followers, and all social connections. This action cannot be undone. Your workout data will not be affected.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun PrivacySettingsScreenPreview() {
    LiftrixTheme {
        PrivacySettingsScreen(
            onNavigateBack = {}
        )
    }
}

@Preview(showBackground = true, name = "Dark Theme")
@Composable
private fun PrivacySettingsScreenDarkPreview() {
    LiftrixTheme(darkTheme = true) {
        PrivacySettingsScreen(
            onNavigateBack = {}
        )
    }
}

