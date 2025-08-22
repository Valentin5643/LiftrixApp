package com.example.liftrix.ui.settings

import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.ui.common.event.ViewModelEvent

/**
 * Sealed class representing all possible events that can occur in the settings screen.
 * 
 * This follows the MVI (Model-View-Intent) pattern where each user interaction or system
 * event is represented as a discrete event object. Events are processed by the ViewModel
 * to update the UI state reactively.
 * 
 * Events are grouped by functionality and include both user-initiated actions and
 * system-triggered events for comprehensive state management.
 */
sealed class SettingsEvent : ViewModelEvent {
    
    // Data loading events
    /**
     * Triggered when the settings screen is first loaded.
     * Initiates loading of user settings and subscription status.
     */
    object LoadSettings : SettingsEvent()
    
    /**
     * Triggered when user pulls to refresh the settings data.
     * Reloads all settings data from remote sources.
     */
    object RefreshSettings : SettingsEvent()
    
    // Settings modification events
    /**
     * Triggered when user toggles dark mode setting.
     * 
     * @property enabled New dark mode state
     */
    data class UpdateDarkMode(val enabled: Boolean) : SettingsEvent()
    
    /**
     * Triggered when user toggles the theme with system awareness.
     * Properly handles SYSTEM mode by switching to opposite of current display.
     * 
     * @property isSystemInDarkTheme Current system dark theme state
     */
    data class ToggleTheme(val isSystemInDarkTheme: Boolean) : SettingsEvent()
    
    /**
     * Triggered when user toggles notification setting.
     * 
     * @property enabled New notification state
     */
    data class UpdateNotifications(val enabled: Boolean) : SettingsEvent()
    
    /**
     * Triggered when user changes weight unit preference.
     * 
     * @property weightUnit New weight unit preference
     */
    data class UpdateWeightUnit(val weightUnit: WeightUnit) : SettingsEvent()
    
    // Navigation events
    /**
     * Triggered when user taps on profile editing option.
     * Should navigate to profile editing screen.
     */
    object NavigateToProfile : SettingsEvent()
    
    /**
     * Triggered when user taps on subscription management.
     * Should navigate to subscription management screen.
     */
    object NavigateToSubscription : SettingsEvent()
    
    /**
     * Triggered when user taps on privacy settings.
     * Should navigate to privacy settings screen.
     */
    object NavigateToPrivacy : SettingsEvent()
    
    /**
     * Triggered when user taps on help and support.
     * Should navigate to help screen or open support.
     */
    object NavigateToHelp : SettingsEvent()
    
    /**
     * Triggered when user taps on about/app info.
     * Should navigate to about screen with app information.
     */
    object NavigateToAbout : SettingsEvent()
    
    /**
     * Triggered when user taps on anomaly detection settings.
     * Should navigate to anomaly detection settings screen.
     */
    object NavigateToAnomalyDetection : SettingsEvent()
    
    /**
     * Triggered when user taps on anomaly detection dashboard.
     * Should navigate to anomaly detection dashboard screen.
     */
    object NavigateToAnomalyDashboard : SettingsEvent()
    
    /**
     * Triggered when user taps on widget settings/customize dashboard.
     * Should navigate to widget customization screen.
     */
    object NavigateToWidgetSettings : SettingsEvent()
    
    /**
     * Triggered when user taps on notification settings.
     * Should navigate to notification settings screen.
     */
    object NavigateToNotifications : SettingsEvent()
    
    // Account Management Navigation Events (Added for SPEC-20250116-account-management)
    /**
     * Triggered when user taps on email change option.
     * Should navigate to email change screen.
     */
    object NavigateToEmailChange : SettingsEvent()
    
    /**
     * Triggered when user taps on password change option.
     * Should navigate to password change screen.
     */
    object NavigateToPasswordChange : SettingsEvent()
    
    /**
     * Triggered when user taps on username change option.
     * Should navigate to username change screen.
     */
    object NavigateToUsernameChange : SettingsEvent()
    
    /**
     * Triggered when user taps on account deletion option.
     * Should navigate to account deletion flow.
     */
    object NavigateToAccountDeletion : SettingsEvent()
    
    // Authentication events
    /**
     * Triggered when user initiates logout process.
     * Shows logout confirmation dialog.
     */
    object SignOutRequested : SettingsEvent()
    
    /**
     * Triggered when user confirms logout in dialog.
     * Proceeds with actual logout operation.
     */
    object SignOutConfirmed : SettingsEvent()
    
    /**
     * Triggered when user cancels logout in dialog.
     * Dismisses logout confirmation dialog.
     */
    object SignOutCancelled : SettingsEvent()
    
    // UI interaction events
    /**
     * Triggered when user taps on an expandable settings card.
     * Toggles expansion state of the specified card.
     * 
     * @property cardId Unique identifier for the settings card
     */
    data class ToggleCardExpansion(val cardId: String) : SettingsEvent()
    
    /**
     * Triggered when user taps on profile avatar.
     * Should initiate profile image upload process.
     */
    object ProfileAvatarTapped : SettingsEvent()

    /**
     * Triggered when user dismisses the image picker dialog.
     * Hides the image picker dialog.
     */
    object ImagePickerDialogDismissed : SettingsEvent()

    /**
     * Triggered when user selects an image from the picker.
     * Initiates profile image upload process.
     * 
     * @property imageUri URI of the selected image
     */
    data class ProfileImageSelected(val imageUri: android.net.Uri) : SettingsEvent()
    
    // Error handling events
    /**
     * Triggered when user dismisses an error message.
     * Clears the error state and allows retry.
     */
    object ErrorDismissed : SettingsEvent()
    
    /**
     * Triggered when user taps retry after an error.
     * Retries the last failed operation.
     */
    object RetryRequested : SettingsEvent()
    
    // Subscription events
    /**
     * Triggered when user taps on upgrade subscription button.
     * Should initiate subscription upgrade flow.
     */
    object UpgradeSubscription : SettingsEvent()
    
    /**
     * Triggered when user taps on manage subscription button.
     * Should open subscription management in Google Play.
     */
    object ManageSubscription : SettingsEvent()
    
    /**
     * Triggered when subscription purchase is completed.
     * Updates subscription status and refreshes UI.
     */
    object SubscriptionPurchaseCompleted : SettingsEvent()
    
    // Data export events
    /**
     * Triggered when user requests data export.
     * Should initiate data export process.
     */
    object ExportDataRequested : SettingsEvent()
    
    /**
     * Triggered when user wants to navigate to data portability screen.
     * Should navigate to data import/export screen.
     */
    object NavigateToDataPortability : SettingsEvent()
    
    /**
     * Triggered when user requests account deletion.
     * Should show account deletion confirmation.
     */
    object DeleteAccountRequested : SettingsEvent()
    
    // System events
    /**
     * Triggered when the app theme changes externally.
     * Updates UI to reflect system theme changes.
     */
    object SystemThemeChanged : SettingsEvent()
    
    /**
     * Triggered when subscription status changes externally.
     * Refreshes subscription information.
     */
    object SubscriptionStatusChanged : SettingsEvent()
}