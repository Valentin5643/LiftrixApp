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
import com.example.liftrix.domain.model.*
import com.example.liftrix.ui.settings.components.*
import com.example.liftrix.ui.profile.components.ImagePickerDialog
import com.example.liftrix.ui.common.FeedItemShimmer
import com.example.liftrix.ui.common.ContextualColorOverlay
import com.example.liftrix.ui.common.ColorContext
import com.example.liftrix.ui.common.PerformanceOptimizations
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.components.cards.ElevatedLiftrixCard
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import com.example.liftrix.ui.workout.components.SecondaryActionButton
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.icons.LiftrixIcons
import com.example.liftrix.ui.icons.CustomActionIcons
import timber.log.Timber
import java.time.LocalDateTime

/**
 * Main settings screen with expandable categories, user profile, and navigation integration.
 * 
 * Features:
 * - User profile card with avatar and edit functionality
 * - Expandable settings categories with accordion behavior
 * - Material3 design with proper theming and accessibility
 * - MVI pattern integration with SettingsViewModel
 * - Loading, error, and success state handling
 * - Navigation integration with proper callbacks
 * - Logout confirmation dialog
 * - Analytics tracking for user interactions
 * - Account management navigation (SPEC-20250116-account-management)
 * 
 * @param onNavigateBack Callback to navigate back to previous screen
 * @param onNavigateToProfile Callback to navigate to profile editing screen
 * @param onNavigateToAuth Callback to navigate to authentication screen after logout
 * @param onNavigateToEmailChange Callback to navigate to email change screen
 * @param onNavigateToPasswordChange Callback to navigate to password change screen
 * @param onNavigateToUsernameChange Callback to navigate to username change screen
 * @param onNavigateToAccountDeletion Callback to navigate to account deletion flow
 * @param onNavigateToHelpCenter Callback to navigate to help center screen
 * @param onNavigateToContactSupport Callback to navigate to contact support screen
 * @param onNavigateToAbout Callback to navigate to about screen
 * @param onNavigateToPrivacyPolicy Callback to navigate to privacy policy screen
 * @param onNavigateToTermsOfService Callback to navigate to terms of service screen
 * @param modifier Modifier for styling the screen
 * @param viewModel SettingsViewModel for state management (injectable for testing)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToAnomalyDetection: (() -> Unit)? = null,
    onNavigateToAnomalyDashboard: (() -> Unit)? = null,
    onNavigateToWidgetSettings: (() -> Unit)? = null,
    onNavigateToNotifications: (() -> Unit)? = null,
    onNavigateToEmailChange: (() -> Unit)? = null,
    onNavigateToPasswordChange: (() -> Unit)? = null,
    onNavigateToUsernameChange: (() -> Unit)? = null,
    onNavigateToAccountDeletion: (() -> Unit)? = null,
    onNavigateToHelpCenter: (() -> Unit)? = null,
    onNavigateToContactSupport: (() -> Unit)? = null,
    onNavigateToAbout: (() -> Unit)? = null,
    onNavigateToPrivacyPolicy: (() -> Unit)? = null,
    onNavigateToTermsOfService: (() -> Unit)? = null,
    onNavigateToDataPortability: (() -> Unit)? = null,
    onNavigateToAIChatSettings: (() -> Unit)? = null,
    onNavigateToAdminBanManagement: (() -> Unit)? = null,
    onNavigateToUpgradeToPremium: (() -> Unit)? = null,
    onNavigateToSyncSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    // Performance monitoring for settings screen
    PerformanceOptimizations.AnimationPerformanceMonitor.MonitorAnimation(
        key = "SettingsScreen"
    ) {
        val uiState by viewModel.uiState.collectAsState()
        
        
        // Stable callbacks to prevent unnecessary recompositions
        val stableOnEvent = remember(viewModel) { viewModel::onEvent }
        val stableOnNavigateBack = remember(onNavigateBack) { onNavigateBack }
        val stableOnNavigateToProfile = remember(onNavigateToProfile) { onNavigateToProfile }
        val stableOnNavigateToAuth = remember(onNavigateToAuth) { onNavigateToAuth }
        val stableOnNavigateToAnomalyDetection = remember(onNavigateToAnomalyDetection) { onNavigateToAnomalyDetection }
        val stableOnNavigateToAnomalyDashboard = remember(onNavigateToAnomalyDashboard) { onNavigateToAnomalyDashboard }
        val stableOnNavigateToWidgetSettings = remember(onNavigateToWidgetSettings) { onNavigateToWidgetSettings }
        val stableOnNavigateToNotifications = remember(onNavigateToNotifications) { onNavigateToNotifications }
        val stableOnNavigateToEmailChange = remember(onNavigateToEmailChange) { onNavigateToEmailChange }
        val stableOnNavigateToPasswordChange = remember(onNavigateToPasswordChange) { onNavigateToPasswordChange }
        val stableOnNavigateToUsernameChange = remember(onNavigateToUsernameChange) { onNavigateToUsernameChange }
        val stableOnNavigateToAccountDeletion = remember(onNavigateToAccountDeletion) { onNavigateToAccountDeletion }
        val stableOnNavigateToHelpCenter = remember(onNavigateToHelpCenter) { onNavigateToHelpCenter }
        val stableOnNavigateToContactSupport = remember(onNavigateToContactSupport) { onNavigateToContactSupport }
        val stableOnNavigateToAbout = remember(onNavigateToAbout) { onNavigateToAbout }
        val stableOnNavigateToPrivacyPolicy = remember(onNavigateToPrivacyPolicy) { onNavigateToPrivacyPolicy }
        val stableOnNavigateToTermsOfService = remember(onNavigateToTermsOfService) { onNavigateToTermsOfService }
        val stableOnNavigateToDataPortability = remember(onNavigateToDataPortability) { onNavigateToDataPortability }
        val stableOnNavigateToAIChatSettings = remember(onNavigateToAIChatSettings) { onNavigateToAIChatSettings }
        val stableOnNavigateToSyncSettings = remember(onNavigateToSyncSettings) { onNavigateToSyncSettings }
        
        // Optimized LaunchedEffect with stable key
        LaunchedEffect(uiState.isSigningOut, uiState.error) {
            if (uiState.isSigningOut && uiState.error == null) {
                // Navigate to auth screen after successful logout
                stableOnNavigateToAuth()
            }
        }
        
        // Handle navigation events from ViewModel
        LaunchedEffect(viewModel) {
            // For now, navigation is handled through callbacks in the UI composables
            // The ViewModel only tracks analytics for navigation events
        }
    
        Column(
            modifier = modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Settings screen with user profile and preferences"
                }
        ) {
            // Settings Content with performance tracking (TopAppBar now handled by MainNavigationContainer)
            PerformanceOptimizations.MemoryEfficientComponents.TrackRecomposition(
                key = "SettingsContent"
            ) {
                when {
                    uiState.shouldShowError -> {
                        ErrorState(
                            errorMessage = uiState.error ?: "Something went wrong",
                            onRetry = { stableOnEvent(SettingsEvent.RefreshSettings) },
                            onDismiss = { stableOnEvent(SettingsEvent.ErrorDismissed) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    uiState.shouldShowInitialLoading -> {
                        LoadingState(
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    uiState.shouldShowContent -> {
                        SettingsContent(
                            uiState = uiState,
                            onEvent = stableOnEvent,
                            onNavigateToProfile = stableOnNavigateToProfile,
                            onNavigateToAnomalyDetection = stableOnNavigateToAnomalyDetection,
                            onNavigateToAnomalyDashboard = stableOnNavigateToAnomalyDashboard,
                            onNavigateToWidgetSettings = stableOnNavigateToWidgetSettings,
                            onNavigateToNotifications = stableOnNavigateToNotifications,
                            onNavigateToAIChatSettings = stableOnNavigateToAIChatSettings,
                            onNavigateToSyncSettings = stableOnNavigateToSyncSettings,
                            onNavigateToEmailChange = stableOnNavigateToEmailChange,
                            onNavigateToPasswordChange = stableOnNavigateToPasswordChange,
                            onNavigateToUsernameChange = stableOnNavigateToUsernameChange,
                            onNavigateToAccountDeletion = stableOnNavigateToAccountDeletion,
                            onNavigateToPrivacyPolicy = stableOnNavigateToPrivacyPolicy,
                            onNavigateToDataPortability = stableOnNavigateToDataPortability,
                            onNavigateToHelpCenter = stableOnNavigateToHelpCenter,
                            onNavigateToAbout = stableOnNavigateToAbout,
                            onNavigateToAdminBanManagement = onNavigateToAdminBanManagement,
                            onNavigateToUpgradeToPremium = onNavigateToUpgradeToPremium,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    else -> {
                        LoadingState(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    
        // Logout Confirmation Dialog with stable callbacks
        LogoutConfirmationDialog(
            isVisible = uiState.showLogoutDialog,
            onDismiss = { stableOnEvent(SettingsEvent.SignOutCancelled) },
            onConfirm = { stableOnEvent(SettingsEvent.SignOutConfirmed) }
        )
        
        // Image Picker Dialog with stable callbacks
        ImagePickerDialog(
            isVisible = uiState.showImagePickerDialog,
            onDismiss = { stableOnEvent(SettingsEvent.ImagePickerDialogDismissed) },
            onImageSelected = { uri -> stableOnEvent(SettingsEvent.ProfileImageSelected(uri)) },
            onError = { error ->
                Timber.e("Image picker error in settings: $error")
                // Could enhance this with proper error display in the future
            }
        )
    }
}

/**
 * Main settings content with scrollable list of categories and user profile.
 * 
 * @param uiState Current settings UI state
 * @param onEvent Event handler for settings actions
 * @param onNavigateToProfile Callback to navigate to profile editing
 * @param onNavigateToEmailChange Callback to navigate to email change screen
 * @param onNavigateToPasswordChange Callback to navigate to password change screen
 * @param onNavigateToUsernameChange Callback to navigate to username change screen
 * @param onNavigateToAccountDeletion Callback to navigate to account deletion flow
 * @param modifier Modifier for styling the content
 */
@Composable
private fun SettingsContent(
    uiState: SettingsState,
    onEvent: (SettingsEvent) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAnomalyDetection: (() -> Unit)? = null,
    onNavigateToAnomalyDashboard: (() -> Unit)? = null,
    onNavigateToWidgetSettings: (() -> Unit)? = null,
    onNavigateToNotifications: (() -> Unit)? = null,
    onNavigateToAIChatSettings: (() -> Unit)? = null,
    onNavigateToSyncSettings: (() -> Unit)? = null,
    onNavigateToEmailChange: (() -> Unit)? = null,
    onNavigateToPasswordChange: (() -> Unit)? = null,
    onNavigateToUsernameChange: (() -> Unit)? = null,
    onNavigateToAccountDeletion: (() -> Unit)? = null,
    onNavigateToPrivacyPolicy: (() -> Unit)? = null,
    onNavigateToDataPortability: (() -> Unit)? = null,
    onNavigateToHelpCenter: (() -> Unit)? = null,
    onNavigateToAbout: (() -> Unit)? = null,
    onNavigateToAdminBanManagement: (() -> Unit)? = null,
    onNavigateToUpgradeToPremium: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Stable callbacks and data to prevent unnecessary recompositions
    val stableOnEvent = remember(onEvent) { onEvent }
    val stableOnNavigateToProfile = remember(onNavigateToProfile) { onNavigateToProfile }
    val stableOnNavigateToNotifications = remember(onNavigateToNotifications) { onNavigateToNotifications }
    val stableOnNavigateToPrivacyPolicy = remember(onNavigateToPrivacyPolicy) { onNavigateToPrivacyPolicy }
    val stableOnNavigateToDataPortability = remember(onNavigateToDataPortability) { onNavigateToDataPortability }
    val stableOnNavigateToHelpCenter = remember(onNavigateToHelpCenter) { onNavigateToHelpCenter }
    val stableOnNavigateToAbout = remember(onNavigateToAbout) { onNavigateToAbout }
    val stableSettingsCategories = remember { settingsCategories }
    
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // User Profile Card
        item {
            UserProfileCard(
                user = uiState.currentUser,
                socialProfile = uiState.socialProfile,
                userProfile = uiState.userProfile,
                onEditProfile = stableOnNavigateToProfile,
                onAvatarClick = { stableOnEvent(SettingsEvent.ProfileAvatarTapped) }
            )
        }
        
        // Settings Categories with performance optimization
        items(
            items = stableSettingsCategories,
            key = { category -> category.id }
        ) { category ->
            ExpandableSettingsCard(
                title = category.title,
                isExpanded = uiState.expandedCard == category.id,
                onToggle = { stableOnEvent(SettingsEvent.ToggleCardExpansion(category.id)) }
            ) {
                SettingsCategoryContent(
                    category = category,
                    uiState = uiState,
                    onEvent = stableOnEvent,
                    onNavigateToAnomalyDetection = onNavigateToAnomalyDetection,
                    onNavigateToAnomalyDashboard = onNavigateToAnomalyDashboard,
                    onNavigateToWidgetSettings = onNavigateToWidgetSettings,
                    onNavigateToNotifications = stableOnNavigateToNotifications,
                    onNavigateToAIChatSettings = onNavigateToAIChatSettings,
                    onNavigateToSyncSettings = onNavigateToSyncSettings,
                    onNavigateToEmailChange = onNavigateToEmailChange,
                    onNavigateToPasswordChange = onNavigateToPasswordChange,
                    onNavigateToUsernameChange = onNavigateToUsernameChange,
                    onNavigateToAccountDeletion = onNavigateToAccountDeletion,
                    onNavigateToPrivacyPolicy = stableOnNavigateToPrivacyPolicy,
                    onNavigateToDataPortability = stableOnNavigateToDataPortability,
                    onNavigateToAdminBanManagement = onNavigateToAdminBanManagement,
                    onNavigateToHelpCenter = stableOnNavigateToHelpCenter,
                    onNavigateToAbout = stableOnNavigateToAbout,
                    onNavigateToUpgradeToPremium = onNavigateToUpgradeToPremium
                )
            }
        }
        
        // Sign Out Section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            
            UnifiedWorkoutCard(
                title = "Sign Out",
                subtitle = "Sign out of your account",
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    SecondaryActionButton(
                        text = "Sign Out",
                        leadingIcon = CustomActionIcons.SignOut,
                        onClick = { stableOnEvent(SettingsEvent.SignOutRequested) }
                    )
                }
            }
        }
    }
}

/**
 * Content for individual settings categories based on category type.
 * 
 * @param category The settings category to display
 * @param uiState Current settings UI state
 * @param onEvent Event handler for settings actions
 * @param onNavigateToEmailChange Callback to navigate to email change screen
 * @param onNavigateToPasswordChange Callback to navigate to password change screen
 * @param onNavigateToUsernameChange Callback to navigate to username change screen
 * @param onNavigateToAccountDeletion Callback to navigate to account deletion flow
 */
@Composable
private fun SettingsCategoryContent(
    category: SettingsCategory,
    uiState: SettingsState,
    onEvent: (SettingsEvent) -> Unit,
    onNavigateToAnomalyDetection: (() -> Unit)? = null,
    onNavigateToAnomalyDashboard: (() -> Unit)? = null,
    onNavigateToWidgetSettings: (() -> Unit)? = null,
    onNavigateToNotifications: (() -> Unit)? = null,
    onNavigateToAIChatSettings: (() -> Unit)? = null,
    onNavigateToSyncSettings: (() -> Unit)? = null,
    onNavigateToEmailChange: (() -> Unit)? = null,
    onNavigateToPasswordChange: (() -> Unit)? = null,
    onNavigateToUsernameChange: (() -> Unit)? = null,
    onNavigateToAccountDeletion: (() -> Unit)? = null,
    onNavigateToPrivacyPolicy: (() -> Unit)? = null,
    onNavigateToDataPortability: (() -> Unit)? = null,
    onNavigateToAdminBanManagement: (() -> Unit)? = null,
    onNavigateToHelpCenter: (() -> Unit)? = null,
    onNavigateToAbout: (() -> Unit)? = null,
    onNavigateToUpgradeToPremium: (() -> Unit)? = null
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (category.id) {
            "general" -> {
                GeneralSettings(
                    uiState = uiState,
                    onEvent = onEvent,
                    onNavigateToAnomalyDetection = onNavigateToAnomalyDetection,
                    onNavigateToAnomalyDashboard = onNavigateToAnomalyDashboard,
                    onNavigateToWidgetSettings = onNavigateToWidgetSettings,
                    onNavigateToNotifications = onNavigateToNotifications,
                    onNavigateToAIChatSettings = onNavigateToAIChatSettings,
                    onNavigateToSyncSettings = onNavigateToSyncSettings
                )
            }
            
            "subscription" -> {
                SubscriptionSettings(
                    uiState = uiState,
                    onEvent = onEvent,
                    onNavigateToUpgradeToPremium = onNavigateToUpgradeToPremium
                )
            }
            
            "privacy" -> {
                PrivacySettings(
                    uiState = uiState,
                    onEvent = onEvent,
                    onNavigateToEmailChange = onNavigateToEmailChange,
                    onNavigateToPasswordChange = onNavigateToPasswordChange,
                    onNavigateToUsernameChange = onNavigateToUsernameChange,
                    onNavigateToAccountDeletion = onNavigateToAccountDeletion,
                    onNavigateToPrivacyPolicy = onNavigateToPrivacyPolicy,
                    onNavigateToDataPortability = onNavigateToDataPortability,
                    onNavigateToAdminBanManagement = onNavigateToAdminBanManagement
                )
            }
            
            "support" -> {
                SupportSettings(
                    uiState = uiState,
                    onEvent = onEvent,
                    onNavigateToHelpCenter = onNavigateToHelpCenter,
                    onNavigateToAbout = onNavigateToAbout
                )
            }
        }
    }
}

/**
 * General settings category content with toggles and preferences.
 */
@Composable
private fun GeneralSettings(
    uiState: SettingsState,
    onEvent: (SettingsEvent) -> Unit,
    onNavigateToAnomalyDetection: (() -> Unit)? = null,
    onNavigateToAnomalyDashboard: (() -> Unit)? = null,
    onNavigateToWidgetSettings: (() -> Unit)? = null,
    onNavigateToNotifications: (() -> Unit)? = null,
    onNavigateToAIChatSettings: (() -> Unit)? = null,
    onNavigateToSyncSettings: (() -> Unit)? = null
) {
    // Get system theme state for proper toggle handling
    val isSystemInDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    
    // Stable callback to prevent unnecessary recompositions
    val stableOnEvent = remember(onEvent) { onEvent }
    val stableOnNavigateToNotifications = remember(onNavigateToNotifications) { onNavigateToNotifications }
    val stableOnNavigateToAIChatSettings = remember(onNavigateToAIChatSettings) { onNavigateToAIChatSettings }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Use the new ToggleTheme event for system-aware toggling
        val onDarkModeToggle = remember(stableOnEvent, isSystemInDarkTheme) { { _: Boolean ->
            stableOnEvent(SettingsEvent.ToggleTheme(isSystemInDarkTheme))
        }}
        
        val onNotificationsToggle = remember(stableOnEvent) { { enabled: Boolean ->
            stableOnEvent(SettingsEvent.UpdateNotifications(enabled))
        }}
        
        val onWeightUnitToggle = remember(stableOnEvent) { { useMetric: Boolean ->
            val newUnit = if (useMetric) WeightUnit.KILOGRAMS else WeightUnit.POUNDS
            stableOnEvent(SettingsEvent.UpdateWeightUnit(newUnit))
        }}
        
        
        SettingsToggleItem(
            title = "Dark Mode",
            subtitle = "Use dark theme throughout the app",
            isChecked = uiState.currentThemeMode,
            onToggle = onDarkModeToggle,
            enabled = !uiState.isUpdatingSettings
        )
        
        SettingsNavigationItem(
            title = "Notifications",
            subtitle = if (uiState.currentNotificationSetting) "Enabled" else "Disabled",
            icon = LiftrixIcons.State.Info,
            onClick = { 
                // Trigger analytics event
                stableOnEvent(SettingsEvent.NavigateToNotifications)
                // Navigate to notification settings
                stableOnNavigateToNotifications?.invoke()
            }
        )
        
        SettingsNavigationItem(
            title = "AI Chat Settings",
            subtitle = "Configure AI assistant language, behavior, and data management",
            icon = LiftrixIcons.Workflow.Settings,
            onClick = { 
                // Navigate to AI chat settings
                stableOnNavigateToAIChatSettings?.invoke()
            }
        )
        
        SettingsToggleItem(
            title = "Metric System (kg)",
            subtitle = "Use kilograms instead of pounds for weight display",
            isChecked = uiState.currentWeightUnit == WeightUnit.KILOGRAMS,
            onToggle = onWeightUnitToggle,
            enabled = !uiState.isUpdatingSettings
        )
        
        
        SettingsNavigationItem(
            title = "Anomaly Detection",
            subtitle = "Configure error detection and sensitivity",
            icon = LiftrixIcons.State.Warning,
            onClick = { 
                // Trigger analytics event
                stableOnEvent(SettingsEvent.NavigateToAnomalyDetection)
                // Navigate to anomaly detection settings
                onNavigateToAnomalyDetection?.invoke()
            }
        )
        
        SettingsNavigationItem(
            title = "Anomaly Dashboard",
            subtitle = "View and manage detected anomalies",
            icon = LiftrixIcons.Fitness.Statistics,
            onClick = { 
                // Trigger analytics event
                stableOnEvent(SettingsEvent.NavigateToAnomalyDashboard)
                // Navigate to anomaly detection dashboard
                onNavigateToAnomalyDashboard?.invoke()
            }
        )
        
        SettingsNavigationItem(
            title = "Customize Dashboard",
            subtitle = "Personalize your dashboard widgets and layout",
            icon = LiftrixIcons.Workflow.Settings,
            onClick = { 
                // Trigger analytics event
                stableOnEvent(SettingsEvent.NavigateToWidgetSettings)
                // Navigate to widget settings
                onNavigateToWidgetSettings?.invoke()
            }
        )
        
        SettingsNavigationItem(
            title = "Data Sync Settings",
            subtitle = "Manage sync preferences and troubleshoot sync issues",
            icon = Icons.Default.Sync,
            onClick = { 
                // Navigate to sync settings
                onNavigateToSyncSettings?.invoke()
            }
        )
    }
}

/**
 * Subscription settings category content.
 */
@Composable
private fun SubscriptionSettings(
    uiState: SettingsState,
    onEvent: (SettingsEvent) -> Unit,
    onNavigateToUpgradeToPremium: (() -> Unit)? = null
) {
    // Stable callback to prevent unnecessary recompositions
    val stableOnEvent = remember(onEvent) { onEvent }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Subscription Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Current Plan",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = uiState.subscriptionDisplayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (uiState.hasPremiumAccess) {
                SecondaryActionButton(
                    text = "Manage",
                    onClick = { stableOnEvent(SettingsEvent.ManageSubscription) },
                    leadingIcon = CustomActionIcons.Settings
                )
            } else {
                PrimaryActionButton(
                    text = "Upgrade",
                    onClick = { 
                        stableOnEvent(SettingsEvent.UpgradeSubscription)
                        onNavigateToUpgradeToPremium?.invoke()
                    },
                    leadingIcon = Icons.Default.Star
                )
            }
        }
        
        if (!uiState.hasPremiumAccess) {
            SettingsNavigationItem(
                title = "Upgrade to Premium",
                subtitle = "Unlock advanced features and analytics",
                icon = Icons.Default.Star,
                onClick = { 
                    stableOnEvent(SettingsEvent.UpgradeSubscription)
                    onNavigateToUpgradeToPremium?.invoke()
                }
            )
        }
    }
}

/**
 * Privacy settings category content with account management options.
 * Updated for SPEC-20250116-account-management implementation.
 */
@Composable
private fun PrivacySettings(
    uiState: SettingsState,
    onEvent: (SettingsEvent) -> Unit,
    onNavigateToEmailChange: (() -> Unit)? = null,
    onNavigateToPasswordChange: (() -> Unit)? = null,
    onNavigateToUsernameChange: (() -> Unit)? = null,
    onNavigateToAccountDeletion: (() -> Unit)? = null,
    onNavigateToPrivacyPolicy: (() -> Unit)? = null,
    onNavigateToDataPortability: (() -> Unit)? = null,
    onNavigateToAdminBanManagement: (() -> Unit)? = null
) {
    // Stable callbacks to prevent unnecessary recompositions
    val stableOnEvent = remember(onEvent) { onEvent }
    val stableOnNavigateToEmailChange = remember(onNavigateToEmailChange) { onNavigateToEmailChange }
    val stableOnNavigateToPasswordChange = remember(onNavigateToPasswordChange) { onNavigateToPasswordChange }
    val stableOnNavigateToUsernameChange = remember(onNavigateToUsernameChange) { onNavigateToUsernameChange }
    val stableOnNavigateToAccountDeletion = remember(onNavigateToAccountDeletion) { onNavigateToAccountDeletion }
    val stableOnNavigateToPrivacyPolicy = remember(onNavigateToPrivacyPolicy) { onNavigateToPrivacyPolicy }
    val stableOnNavigateToDataPortability = remember(onNavigateToDataPortability) { onNavigateToDataPortability }
    val stableOnNavigateToAdminBanManagement = remember(onNavigateToAdminBanManagement) { onNavigateToAdminBanManagement }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Account Management Section
        Text(
            text = "Account Management",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        
        SettingsNavigationItem(
            title = "Change Email",
            subtitle = "Update your email address",
            icon = LiftrixIcons.Workflow.Profile,
            onClick = { 
                stableOnEvent(SettingsEvent.NavigateToEmailChange)
                stableOnNavigateToEmailChange?.invoke()
            }
        )
        
        SettingsNavigationItem(
            title = "Change Password",
            subtitle = "Update your account password",
            icon = LiftrixIcons.Workflow.Settings,
            onClick = { 
                stableOnEvent(SettingsEvent.NavigateToPasswordChange)
                stableOnNavigateToPasswordChange?.invoke()
            }
        )
        
        SettingsNavigationItem(
            title = "Change Username",
            subtitle = "Update your username",
            icon = LiftrixIcons.Workflow.Profile,
            onClick = { 
                stableOnEvent(SettingsEvent.NavigateToUsernameChange)
                stableOnNavigateToUsernameChange?.invoke()
            }
        )
        
        // Privacy & Data Section
        Text(
            text = "Privacy & Data",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
        )
        
        SettingsNavigationItem(
            title = "Privacy Policy",
            subtitle = "View our privacy policy",
            icon = LiftrixIcons.State.Info,
            onClick = { 
                stableOnEvent(SettingsEvent.NavigateToPrivacy)
                stableOnNavigateToPrivacyPolicy?.invoke()
            }
        )
        
        SettingsNavigationItem(
            title = "Export Data",
            subtitle = "Download your workout data",
            icon = LiftrixIcons.Actions.Share,
            onClick = { 
                stableOnEvent(SettingsEvent.NavigateToDataPortability)
                stableOnNavigateToDataPortability?.invoke()
            }
        )
        
        // Admin Section (Only visible to admin users)
        if (uiState.isAdmin && onNavigateToAdminBanManagement != null) {
            Text(
                text = "Administration",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
            
            SettingsNavigationItem(
                title = "User Management",
                subtitle = "Ban and manage users",
                icon = LiftrixIcons.Workflow.Settings,
                onClick = { 
                    stableOnNavigateToAdminBanManagement?.invoke()
                }
            )
        }
        
        // Danger Zone Section
        Text(
            text = "Danger Zone",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
        )
        
        SettingsNavigationItem(
            title = "Delete Account",
            subtitle = "Permanently delete your account",
            icon = Icons.Default.Delete,
            onClick = { 
                stableOnEvent(SettingsEvent.NavigateToAccountDeletion)
                stableOnNavigateToAccountDeletion?.invoke()
            }
        )
    }
}

/**
 * Support settings category content.
 */
@Composable
private fun SupportSettings(
    uiState: SettingsState,
    onEvent: (SettingsEvent) -> Unit,
    onNavigateToHelpCenter: (() -> Unit)? = null,
    onNavigateToAbout: (() -> Unit)? = null
) {
    // Stable callbacks to prevent unnecessary recompositions
    val stableOnEvent = remember(onEvent) { onEvent }
    val stableOnNavigateToHelpCenter = remember(onNavigateToHelpCenter) { onNavigateToHelpCenter }
    val stableOnNavigateToAbout = remember(onNavigateToAbout) { onNavigateToAbout }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsNavigationItem(
            title = "Help & Support",
            subtitle = "Get help with using the app",
            icon = Icons.Default.Help,
            onClick = { 
                stableOnEvent(SettingsEvent.NavigateToHelp)
                stableOnNavigateToHelpCenter?.invoke()
            }
        )
        
        SettingsNavigationItem(
            title = "About",
            subtitle = "App version and information",
            icon = LiftrixIcons.State.Info,
            onClick = { 
                stableOnEvent(SettingsEvent.NavigateToAbout)
                stableOnNavigateToAbout?.invoke()
            }
        )
    }
}

/**
 * Error state component for settings screen.
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
            imageVector = LiftrixIcons.State.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Error Loading Settings",
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
                onClick = onDismiss,
                leadingIcon = CustomActionIcons.Close
            )
            
            PrimaryActionButton(
                text = "Retry",
                onClick = onRetry,
                leadingIcon = CustomActionIcons.Refresh
            )
        }
    }
}

/**
 * Loading state component for settings screen.
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
            text = "Loading Settings...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Data class representing a settings category.
 */
private data class SettingsCategory(
    val id: String,
    val title: String,
    val description: String
)

/**
 * List of settings categories for the accordion layout.
 */
private val settingsCategories = listOf(
    SettingsCategory(
        id = "general",
        title = "General",
        description = "App preferences and display settings"
    ),
    SettingsCategory(
        id = "subscription",
        title = "Manage Subscription",
        description = "Subscription status and premium features"
    ),
    SettingsCategory(
        id = "privacy",
        title = "Privacy & Security",
        description = "Data privacy and account security"
    ),
    SettingsCategory(
        id = "support",
        title = "Help & Support",
        description = "Get help and app information"
    )
)

/**
 * Preview for SettingsScreen with sample data
 */
@Preview(showBackground = true)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenPreview() {
    LiftrixTheme {
        // Note: This is a simplified preview - actual implementation would use ViewModel
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { }) {
                            Icon(
                                imageVector = LiftrixIcons.Navigation.Back,
                                contentDescription = "Back"
                            )
                        }
                    }
                )
                
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        UserProfileCard(
                            user = User(
                                uid = "user123",
                                email = "john.doe@example.com",
                                displayName = "John Doe",
                                photoUrl = null,
                                isAnonymous = false,
                                subscriptionTier = SubscriptionTier.FREE,
                                subscriptionStatus = SubscriptionStatus.ACTIVE,
                                subscriptionExpiresAt = null,
                                premiumFeaturesEnabled = false,
                                onboardingCompleted = true,
                                profileVersion = 1,
                                createdAt = LocalDateTime.now().minusDays(30),
                                lastSignInAt = LocalDateTime.now().minusHours(2),
                                updatedAt = LocalDateTime.now().minusHours(2)
                            ),
                            onEditProfile = { },
                            onAvatarClick = { }
                        )
                    }
                    
                    items(settingsCategories) { category ->
                        ExpandableSettingsCard(
                            title = category.title,
                            isExpanded = category.id == "general",
                            onToggle = { }
                        ) {
                            if (category.id == "general") {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    SettingsToggleItem(
                                        title = "Dark Mode",
                                        subtitle = "Use dark theme throughout the app",
                                        isChecked = false,
                                        onToggle = { }
                                    )
                                    
                                    SettingsToggleItem(
                                        title = "Push Notifications",
                                        subtitle = "Receive notifications for workout reminders",
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
}