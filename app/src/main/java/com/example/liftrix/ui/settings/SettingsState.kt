package com.example.liftrix.ui.settings

import com.example.liftrix.domain.model.SubscriptionStatus
import com.example.liftrix.domain.model.UserSettings
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.WeightUnit

/**
 * Data class representing the UI state for the settings screen.
 * 
 * This state follows the MVI (Model-View-Intent) pattern, providing a single source of truth
 * for the settings screen's UI state. It manages loading states, data, and error conditions
 * in a reactive manner that integrates seamlessly with Compose.
 * 
 * The state is designed to be immutable and predictable, with clear separation between
 * different types of loading states and error conditions.
 * 
 * @property isLoading General loading state for initial screen setup
 * @property userSettings Current user settings data, null if not loaded
 * @property subscriptionStatus Current subscription status, null if not loaded
 * @property error General error message for display
 * @property isUpdatingSettings Loading state for settings updates
 * @property isSigningOut Loading state for logout operation
 * @property showLogoutDialog Whether to show logout confirmation dialog
 * @property expandedCard Currently expanded settings card, null if none expanded
 */
data class SettingsState(
    val isLoading: Boolean = false,
    val userSettings: UserSettings? = null,
    val userProfile: UserProfile? = null,
    val subscriptionStatus: SubscriptionStatus? = null,
    val error: String? = null,
    val isUpdatingSettings: Boolean = false,
    val isSigningOut: Boolean = false,
    val showLogoutDialog: Boolean = false,
    val showImagePickerDialog: Boolean = false,
    val expandedCard: String? = null
) {
    /**
     * Indicates if the screen should show initial loading state.
     * True when loading and no data is available.
     */
    val shouldShowInitialLoading: Boolean
        get() = isLoading && userSettings == null && userProfile == null && subscriptionStatus == null

    /**
     * Indicates if the screen should show error state.
     * True when there's an error and no data is available.
     */
    val shouldShowError: Boolean
        get() = error != null && userSettings == null && userProfile == null && subscriptionStatus == null

    /**
     * Indicates if the screen should show content.
     * True when at least user settings are available.
     */
    val shouldShowContent: Boolean
        get() = userSettings != null && !shouldShowInitialLoading

    /**
     * Indicates if any update operation is in progress.
     * Used to show loading indicators on specific UI elements.
     */
    val isAnyUpdateInProgress: Boolean
        get() = isUpdatingSettings || isSigningOut

    /**
     * Gets the current theme mode for UI theming.
     * Defaults to false (light mode) if settings not loaded.
     */
    val currentThemeMode: Boolean
        get() = userSettings?.darkMode ?: false

    /**
     * Gets the current notification setting.
     * Defaults to true if settings not loaded.
     */
    val currentNotificationSetting: Boolean
        get() = userSettings?.notificationsEnabled ?: true

    /**
     * Gets the current weight unit preference.
     * Defaults to system default if settings not loaded.
     */
    val currentWeightUnit: WeightUnit
        get() = userSettings?.weightUnit ?: WeightUnit.getSystemDefault()

    /**
     * Gets the subscription display name for UI.
     * Defaults to "Free" if subscription not loaded.
     */
    val subscriptionDisplayName: String
        get() = subscriptionStatus?.displayName ?: "Free"

    /**
     * Indicates if the user has premium access.
     * Defaults to false if subscription not loaded.
     */
    val hasPremiumAccess: Boolean
        get() = subscriptionStatus?.providesAccess ?: false

    /**
     * Indicates if the subscription is in trial period.
     * Defaults to false if subscription not loaded.
     */
    val isInTrialPeriod: Boolean
        get() = subscriptionStatus == SubscriptionStatus.TRIAL

    /**
     * Creates a copy of the state with cleared error.
     * Useful for dismissing error states while preserving other state.
     */
    fun clearError(): SettingsState = copy(error = null)

    /**
     * Creates a copy of the state with updated user settings.
     * Ensures proper state transition when settings are updated.
     */
    fun withUpdatedSettings(settings: UserSettings): SettingsState = copy(
        userSettings = settings,
        isUpdatingSettings = false,
        error = null
    )

    /**
     * Creates a copy of the state with updated user profile.
     * Ensures proper state transition when profile is updated.
     */
    fun withUpdatedProfile(profile: UserProfile): SettingsState = copy(
        userProfile = profile,
        error = null
    )

    /**
     * Creates a copy of the state with updated subscription status.
     * Ensures proper state transition when subscription is updated.
     */
    fun withUpdatedSubscription(subscription: SubscriptionStatus): SettingsState = copy(
        subscriptionStatus = subscription,
        error = null
    )

    /**
     * Creates a copy of the state with an error condition.
     * Ensures loading states are cleared when error occurs.
     */
    fun withError(errorMessage: String): SettingsState = copy(
        error = errorMessage,
        isLoading = false,
        isUpdatingSettings = false,
        isSigningOut = false
    )

    /**
     * Creates a copy of the state with logout dialog visibility.
     * Used for showing/hiding logout confirmation dialog.
     */
    fun withLogoutDialog(show: Boolean): SettingsState = copy(
        showLogoutDialog = show
    )

    /**
     * Creates a copy of the state with expanded card.
     * Ensures only one card is expanded at a time.
     */
    fun withExpandedCard(cardId: String?): SettingsState = copy(
        expandedCard = cardId
    )

    /**
     * Creates a copy of the state with image picker dialog visibility.
     * Used for showing/hiding image picker dialog.
     */
    fun withImagePickerDialog(show: Boolean): SettingsState = copy(
        showImagePickerDialog = show
    )
}