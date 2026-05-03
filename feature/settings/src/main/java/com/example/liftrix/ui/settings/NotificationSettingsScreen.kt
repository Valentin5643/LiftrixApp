package com.example.liftrix.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.ui.settings.components.*
import com.example.liftrix.domain.model.notifications.DeliveryFrequency
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.components.actions.PrimaryActionButton
import com.example.liftrix.ui.components.actions.SecondaryActionButton
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.common.PerformanceOptimizations
import timber.log.Timber

/**
 * Comprehensive notification settings screen with granular controls.
 * 
 * Features:
 * - Master notification toggle with explanation
 * - Category-specific notification controls (Social, Workout, Achievement)
 * - Delivery frequency settings for different notification types
 * - Quiet hours configuration with time pickers
 * - Individual notification type toggles within categories
 * - Notification history access
 * - Muted users management
 * - Sound and vibration preferences
 * - Material3 design with proper theming and accessibility
 * 
 * @param onNavigateBack Callback to navigate back to main settings
 * @param onNavigateToHistory Callback to navigate to notification history
 * @param onNavigateToMutedUsers Callback to navigate to muted users management
 * @param modifier Modifier for styling the screen
 * @param viewModel NotificationSettingsViewModel for state management
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHistory: () -> Unit = {},
    onNavigateToMutedUsers: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Stable callbacks
    val stableOnEvent = remember(viewModel) { viewModel::handleEvent }
    val stableOnNavigateBack = remember(onNavigateBack) { onNavigateBack }
    val stableOnNavigateToHistory = remember(onNavigateToHistory) { onNavigateToHistory }
    val stableOnNavigateToMutedUsers = remember(onNavigateToMutedUsers) { onNavigateToMutedUsers }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Notification settings screen with comprehensive controls"
            }
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = stableOnNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back to Settings"
                    )
                }
            },
            actions = {
                // History access
                IconButton(
                    onClick = stableOnNavigateToHistory
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Notification History"
                    )
                }
            }
        )
        
        // Content
        when {
            uiState.isLoading -> {
                LoadingState(modifier = Modifier.fillMaxSize())
            }
            
            uiState.error != null -> {
                ErrorState(
                    errorMessage = uiState.error ?: "Unknown error",
                    onRetry = { stableOnEvent(NotificationSettingsEvent.RefreshPreferences) },
                    onDismiss = { stableOnEvent(NotificationSettingsEvent.ErrorDismissed) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            else -> {
                NotificationSettingsContent(
                    uiState = uiState,
                    onEvent = stableOnEvent,
                    onNavigateToMutedUsers = stableOnNavigateToMutedUsers,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
    
    // Time Picker Dialog for Quiet Hours
    if (uiState.showQuietHoursStartPicker) {
        QuietHoursTimePicker(
            title = "Quiet Hours Start",
            initialHour = uiState.quietHoursStart,
            onTimeSelected = { hour -> 
                stableOnEvent(NotificationSettingsEvent.UpdateQuietHoursStart(hour))
            },
            onDismiss = { 
                stableOnEvent(NotificationSettingsEvent.DismissQuietHoursStartPicker) 
            }
        )
    }
    
    if (uiState.showQuietHoursEndPicker) {
        QuietHoursTimePicker(
            title = "Quiet Hours End",
            initialHour = uiState.quietHoursEnd,
            onTimeSelected = { hour -> 
                stableOnEvent(NotificationSettingsEvent.UpdateQuietHoursEnd(hour))
            },
            onDismiss = { 
                stableOnEvent(NotificationSettingsEvent.DismissQuietHoursEndPicker) 
            }
        )
    }
    
    // Delivery Frequency Selector Dialog
    if (uiState.showDeliveryFrequencySelector) {
        DeliveryFrequencySelector(
            currentFrequency = uiState.socialDeliveryFrequency,
            onFrequencySelected = { frequency ->
                stableOnEvent(NotificationSettingsEvent.UpdateDeliveryFrequency(frequency))
            },
            onDismiss = {
                stableOnEvent(NotificationSettingsEvent.DismissDeliveryFrequencySelector)
            }
        )
    }
}

/**
 * Main notification settings content with scrollable categories
 */
@Composable
private fun NotificationSettingsContent(
    uiState: NotificationSettingsUiState,
    onEvent: (NotificationSettingsEvent) -> Unit,
    onNavigateToMutedUsers: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Master Toggle Section
        item {
            MasterNotificationToggle(
                enabled = uiState.notificationsEnabled,
                isUpdating = uiState.isUpdatingPreferences,
                onToggle = { enabled ->
                    onEvent(NotificationSettingsEvent.ToggleMasterNotifications(enabled))
                }
            )
        }
        
        // Only show other settings if notifications are enabled
        if (uiState.notificationsEnabled) {
            // Social Notifications Section
            item {
                CategorySection(
                    title = "Social Notifications",
                    subtitle = "Get notified about social activity",
                    enabled = uiState.socialNotifications,
                    expanded = uiState.socialExpanded,
                    isUpdating = uiState.isUpdatingPreferences,
                    onToggle = { enabled ->
                        onEvent(NotificationSettingsEvent.ToggleSocialNotifications(enabled))
                    },
                    onExpand = {
                        onEvent(NotificationSettingsEvent.ToggleSocialExpansion)
                    }
                ) {
                    SocialNotificationSettings(
                        uiState = uiState,
                        onEvent = onEvent
                    )
                }
            }
            
            // Workout Notifications Section
            item {
                CategorySection(
                    title = "Workout Notifications",
                    subtitle = "Reminders and achievement notifications",
                    enabled = uiState.workoutNotifications,
                    expanded = uiState.workoutExpanded,
                    isUpdating = uiState.isUpdatingPreferences,
                    onToggle = { enabled ->
                        onEvent(NotificationSettingsEvent.ToggleWorkoutNotifications(enabled))
                    },
                    onExpand = {
                        onEvent(NotificationSettingsEvent.ToggleWorkoutExpansion)
                    }
                ) {
                    WorkoutNotificationSettings(
                        uiState = uiState,
                        onEvent = onEvent
                    )
                }
            }
            
            // Achievement Notifications Section
            item {
                CategorySection(
                    title = "Achievement Notifications",
                    subtitle = "Personal records and milestones",
                    enabled = uiState.achievementNotifications,
                    expanded = uiState.achievementExpanded,
                    isUpdating = uiState.isUpdatingPreferences,
                    onToggle = { enabled ->
                        onEvent(NotificationSettingsEvent.ToggleAchievementNotifications(enabled))
                    },
                    onExpand = {
                        onEvent(NotificationSettingsEvent.ToggleAchievementExpansion)
                    }
                ) {
                    AchievementNotificationSettings(
                        uiState = uiState,
                        onEvent = onEvent
                    )
                }
            }
            
            // Delivery & Timing Section
            item {
                DeliveryTimingSection(
                    uiState = uiState,
                    onEvent = onEvent
                )
            }
            
            // Sound & Vibration Section
            item {
                SoundVibrationSection(
                    uiState = uiState,
                    onEvent = onEvent
                )
            }
            
            // Muted Users Section
            item {
                MutedUsersSection(
                    mutedUsersCount = uiState.mutedUsersCount,
                    onNavigateToMutedUsers = onNavigateToMutedUsers
                )
            }
        }
    }
}

/**
 * Master notification toggle with explanation
 */
@Composable
private fun MasterNotificationToggle(
    enabled: Boolean,
    isUpdating: Boolean,
    onToggle: (Boolean) -> Unit
) {
    LiftrixCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Push Notifications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (enabled) "Enabled" else "Disabled",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    enabled = !isUpdating
                )
            }
            
            Text(
                text = if (enabled) {
                    "You'll receive notifications for important updates, social activity, and workout reminders. You can customize what you receive below."
                } else {
                    "You won't receive any push notifications. You can still see activity within the app."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Expandable category section with toggle
 */
@Composable
private fun CategorySection(
    title: String,
    subtitle: String,
    enabled: Boolean,
    expanded: Boolean,
    isUpdating: Boolean,
    onToggle: (Boolean) -> Unit,
    onExpand: () -> Unit,
    content: @Composable () -> Unit
) {
    ExpandableSettingsCard(
        title = title,
        subtitle = subtitle,
        isExpanded = expanded,
        onToggle = onExpand,
        headerContent = {
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                enabled = !isUpdating
            )
        }
    ) {
        if (enabled) {
            content()
        } else {
            Text(
                text = "Enable $title to configure specific notification types",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

/**
 * Social notification specific settings
 */
@Composable
private fun SocialNotificationSettings(
    uiState: NotificationSettingsUiState,
    onEvent: (NotificationSettingsEvent) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsToggleItem(
            title = "Gym Buddy PRs",
            subtitle = "Get notified when gym buddies hit personal records",
            isChecked = uiState.gymBuddyPrs,
            onToggle = { enabled ->
                onEvent(NotificationSettingsEvent.ToggleGymBuddyPRs(enabled))
            },
            enabled = !uiState.isUpdatingPreferences
        )
        
        SettingsToggleItem(
            title = "Follow Requests",
            subtitle = "New follower requests",
            isChecked = uiState.followRequests,
            onToggle = { enabled ->
                onEvent(NotificationSettingsEvent.ToggleFollowRequests(enabled))
            },
            enabled = !uiState.isUpdatingPreferences
        )
        
        SettingsToggleItem(
            title = "Post Likes",
            subtitle = "When someone likes your workout posts",
            isChecked = uiState.postLikes,
            onToggle = { enabled ->
                onEvent(NotificationSettingsEvent.TogglePostLikes(enabled))
            },
            enabled = !uiState.isUpdatingPreferences
        )
        
        SettingsToggleItem(
            title = "Post Comments",
            subtitle = "When someone comments on your posts",
            isChecked = uiState.postComments,
            onToggle = { enabled ->
                onEvent(NotificationSettingsEvent.TogglePostComments(enabled))
            },
            enabled = !uiState.isUpdatingPreferences
        )
        
        SettingsToggleItem(
            title = "Mentions",
            subtitle = "When someone mentions you in posts or comments",
            isChecked = uiState.mentions,
            onToggle = { enabled ->
                onEvent(NotificationSettingsEvent.ToggleMentions(enabled))
            },
            enabled = !uiState.isUpdatingPreferences
        )
        
        // Delivery Frequency for Social
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Delivery Frequency",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = uiState.socialDeliveryFrequency.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            SecondaryActionButton(
                text = "Change",
                onClick = {
                    onEvent(NotificationSettingsEvent.ShowDeliveryFrequencySelector)
                }
            )
        }
    }
}

/**
 * Workout notification specific settings
 */
@Composable
private fun WorkoutNotificationSettings(
    uiState: NotificationSettingsUiState,
    onEvent: (NotificationSettingsEvent) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsToggleItem(
            title = "Workout Reminders",
            subtitle = "Gentle reminders when you haven't worked out",
            isChecked = uiState.workoutReminders,
            onToggle = { enabled ->
                onEvent(NotificationSettingsEvent.ToggleWorkoutReminders(enabled))
            },
            enabled = !uiState.isUpdatingPreferences
        )
        
        SettingsToggleItem(
            title = "Rest Day Reminders",
            subtitle = "Reminders to take rest days for recovery",
            isChecked = uiState.restDayReminders,
            onToggle = { enabled ->
                onEvent(NotificationSettingsEvent.ToggleRestDayReminders(enabled))
            },
            enabled = !uiState.isUpdatingPreferences
        )
    }
}

/**
 * Achievement notification specific settings
 */
@Composable
private fun AchievementNotificationSettings(
    uiState: NotificationSettingsUiState,
    onEvent: (NotificationSettingsEvent) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsToggleItem(
            title = "Personal Records",
            subtitle = "When you hit new personal records",
            isChecked = uiState.personalRecords,
            onToggle = { enabled ->
                onEvent(NotificationSettingsEvent.TogglePersonalRecords(enabled))
            },
            enabled = !uiState.isUpdatingPreferences
        )
        
        SettingsToggleItem(
            title = "Milestone Achievements",
            subtitle = "Major milestones and streak achievements",
            isChecked = uiState.milestoneAchievements,
            onToggle = { enabled ->
                onEvent(NotificationSettingsEvent.ToggleMilestoneAchievements(enabled))
            },
            enabled = !uiState.isUpdatingPreferences
        )
    }
}

/**
 * Delivery timing and quiet hours section
 */
@Composable
private fun DeliveryTimingSection(
    uiState: NotificationSettingsUiState,
    onEvent: (NotificationSettingsEvent) -> Unit
) {
    LiftrixCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Delivery & Timing",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            // Quiet Hours Toggle
            SettingsToggleItem(
                title = "Quiet Hours",
                subtitle = "Pause notifications during specified hours",
                isChecked = uiState.quietHoursEnabled,
                onToggle = { enabled ->
                    onEvent(NotificationSettingsEvent.ToggleQuietHours(enabled))
                },
                enabled = !uiState.isUpdatingPreferences
            )
            
            // Quiet Hours Time Selection
            if (uiState.quietHoursEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Start Time
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Start Time",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        SecondaryActionButton(
                            text = "${uiState.quietHoursStart}:00",
                            onClick = {
                                onEvent(NotificationSettingsEvent.ShowQuietHoursStartPicker)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    // End Time
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "End Time",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        SecondaryActionButton(
                            text = "${uiState.quietHoursEnd}:00",
                            onClick = {
                                onEvent(NotificationSettingsEvent.ShowQuietHoursEndPicker)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            // Batching Preference
            SettingsToggleItem(
                title = "Group Similar Notifications",
                subtitle = "Group social notifications together to reduce interruptions",
                isChecked = uiState.batchSocialNotifications,
                onToggle = { enabled ->
                    onEvent(NotificationSettingsEvent.ToggleBatchSocialNotifications(enabled))
                },
                enabled = !uiState.isUpdatingPreferences
            )
        }
    }
}

/**
 * Sound and vibration preferences section
 */
@Composable
private fun SoundVibrationSection(
    uiState: NotificationSettingsUiState,
    onEvent: (NotificationSettingsEvent) -> Unit
) {
    LiftrixCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Sound & Vibration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            SettingsToggleItem(
                title = "Sound",
                subtitle = "Play sounds for notifications",
                isChecked = uiState.notificationSound,
                onToggle = { enabled ->
                    onEvent(NotificationSettingsEvent.ToggleNotificationSound(enabled))
                },
                enabled = !uiState.isUpdatingPreferences
            )
            
            SettingsToggleItem(
                title = "Vibration",
                subtitle = "Vibrate for notifications",
                isChecked = uiState.notificationVibration,
                onToggle = { enabled ->
                    onEvent(NotificationSettingsEvent.ToggleNotificationVibration(enabled))
                },
                enabled = !uiState.isUpdatingPreferences
            )
            
            SettingsToggleItem(
                title = "In-App Notifications",
                subtitle = "Show notifications while using the app",
                isChecked = uiState.showInAppNotifications,
                onToggle = { enabled ->
                    onEvent(NotificationSettingsEvent.ToggleInAppNotifications(enabled))
                },
                enabled = !uiState.isUpdatingPreferences
            )
        }
    }
}

/**
 * Muted users management section
 */
@Composable
private fun MutedUsersSection(
    mutedUsersCount: Int,
    onNavigateToMutedUsers: () -> Unit
) {
    LiftrixCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Text(
                text = "Privacy Controls",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            SettingsNavigationItem(
                title = "Muted Users",
                subtitle = if (mutedUsersCount > 0) {
                    "$mutedUsersCount users muted"
                } else {
                    "No users muted"
                },
                icon = Icons.Default.VolumeOff,
                onClick = onNavigateToMutedUsers
            )
        }
    }
}

/**
 * Quiet hours time picker dialog
 */
@Composable
private fun QuietHoursTimePicker(
    title: String,
    initialHour: Int,
    onTimeSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedHour by remember { mutableIntStateOf(initialHour) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title)
        },
        text = {
            Column {
                Text(
                    text = "Select the hour (24-hour format)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Hour picker (simplified - in production you'd use TimePickerDialog)
                LazyColumn(
                    modifier = Modifier.height(200.dp)
                ) {
                    items((0..23).toList()) { hour ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedHour == hour,
                                onClick = { selectedHour = hour }
                            )
                            Text(
                                text = "${hour}:00",
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            PrimaryActionButton(
                text = "Set",
                onClick = {
                    onTimeSelected(selectedHour)
                    onDismiss()
                }
            )
        },
        dismissButton = {
            SecondaryActionButton(
                text = "Cancel",
                onClick = onDismiss
            )
        }
    )
}

/**
 * Delivery frequency selector dialog
 */
@Composable
private fun DeliveryFrequencySelector(
    currentFrequency: DeliveryFrequency,
    onFrequencySelected: (DeliveryFrequency) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Delivery Frequency")
        },
        text = {
            Column {
                Text(
                    text = "How often would you like to receive social notifications?",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                DeliveryFrequency.values().forEach { frequency ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentFrequency == frequency,
                            onClick = { onFrequencySelected(frequency) }
                        )
                        Column(
                            modifier = Modifier.padding(start = 12.dp)
                        ) {
                            Text(
                                text = frequency.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = frequency.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            PrimaryActionButton(
                text = "Done",
                onClick = onDismiss
            )
        }
    )
}

/**
 * Loading state for notification settings
 */
@Composable
private fun LoadingState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Loading Notification Settings...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Error state for notification settings
 */
@Composable
private fun ErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = "Error",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Error Loading Notification Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SecondaryActionButton(
                text = "Dismiss",
                onClick = onDismiss
            )
            
            PrimaryActionButton(
                text = "Retry",
                onClick = onRetry,
                leadingIcon = Icons.Default.Refresh
            )
        }
    }
}


/**
 * Preview for NotificationSettingsScreen
 */
@Preview(showBackground = true)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationSettingsScreenPreview() {
    LiftrixTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            // Simplified preview content
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "Notifications",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                )
                
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        MasterNotificationToggle(
                            enabled = true,
                            isUpdating = false,
                            onToggle = { }
                        )
                    }
                    
                    item {
                        ExpandableSettingsCard(
                            title = "Social Notifications",
                            subtitle = "Get notified about social activity",
                            isExpanded = true,
                            onToggle = { },
                            headerContent = {
                                Switch(
                                    checked = true,
                                    onCheckedChange = { }
                                )
                            }
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SettingsToggleItem(
                                    title = "Gym Buddy PRs",
                                    subtitle = "Get notified when gym buddies hit personal records",
                                    isChecked = true,
                                    onToggle = { }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
