package com.example.liftrix.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.domain.usecase.settings.EnhancedSignOutUseCase
import com.example.liftrix.domain.usecase.settings.GetSubscriptionStatusUseCase
import com.example.liftrix.domain.usecase.settings.GetUserSettingsUseCase
import com.example.liftrix.domain.usecase.settings.UpdateSettingsUseCase
import com.example.liftrix.domain.usecase.settings.UpdateWeightUnitPreferenceUseCase
import com.example.liftrix.domain.usecase.profile.UploadProfileImageUseCase
import com.example.liftrix.domain.usecase.GetProfileUseCase
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.ui.theme.ThemeManager
import com.example.liftrix.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the settings screen following MVI (Model-View-Intent) pattern.
 * 
 * This ViewModel manages the state and business logic for the settings screen,
 * integrating with multiple use cases to provide a cohesive user experience.
 * It handles user settings, subscription status, and authentication state
 * while providing reactive updates to the UI.
 * 
 * The ViewModel follows Clean Architecture principles by delegating business
 * logic to use cases and maintaining a clear separation of concerns.
 * 
 * @property getUserSettingsUseCase Use case for retrieving user settings
 * @property updateSettingsUseCase Use case for updating user settings
 * @property getSubscriptionStatusUseCase Use case for retrieving subscription status
 * @property enhancedSignOutUseCase Use case for comprehensive logout process
 * @property authRepository Repository for authentication operations
 * @property analyticsService Service for tracking analytics events
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getUserSettingsUseCase: GetUserSettingsUseCase,
    private val getProfileUseCase: GetProfileUseCase,
    private val updateSettingsUseCase: UpdateSettingsUseCase,
    private val updateWeightUnitPreferenceUseCase: UpdateWeightUnitPreferenceUseCase,
    private val getSubscriptionStatusUseCase: GetSubscriptionStatusUseCase,
    private val enhancedSignOutUseCase: EnhancedSignOutUseCase,
    private val uploadProfileImageUseCase: UploadProfileImageUseCase,
    private val authRepository: AuthRepository,
    private val analyticsService: AnalyticsService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsState())
    val uiState: StateFlow<SettingsState> = _uiState.asStateFlow()

    private var currentUserId: String? = null
    private var lastFailedEvent: SettingsEvent? = null
    
    // Theme manager for immediate theme updates
    private val themeManager: ThemeManager by lazy { ThemeManager.getInstance(context) }

    init {
        observeAuthenticationState()
        trackSettingsScreenViewed()
    }

    /**
     * Handles all events from the settings screen UI.
     * This is the main entry point for user interactions and system events.
     * 
     * @param event The event to process
     */
    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.LoadSettings -> loadSettings()
            is SettingsEvent.RefreshSettings -> refreshSettings()
            is SettingsEvent.UpdateDarkMode -> updateDarkMode(event.enabled)
            is SettingsEvent.UpdateNotifications -> updateNotifications(event.enabled)
            is SettingsEvent.UpdateWeightUnit -> updateWeightUnit(event.weightUnit)
            is SettingsEvent.NavigateToProfile -> handleProfileNavigation()
            is SettingsEvent.NavigateToSubscription -> handleSubscriptionNavigation()
            is SettingsEvent.NavigateToPrivacy -> handlePrivacyNavigation()
            is SettingsEvent.NavigateToHelp -> handleHelpNavigation()
            is SettingsEvent.NavigateToAbout -> handleAboutNavigation()
            is SettingsEvent.NavigateToAnomalyDetection -> handleAnomalyDetectionNavigation()
            is SettingsEvent.NavigateToAnomalyDashboard -> handleAnomalyDashboardNavigation()
            is SettingsEvent.NavigateToWidgetSettings -> handleWidgetSettingsNavigation()
            is SettingsEvent.SignOutRequested -> requestSignOut()
            is SettingsEvent.SignOutConfirmed -> confirmSignOut()
            is SettingsEvent.SignOutCancelled -> cancelSignOut()
            is SettingsEvent.ToggleCardExpansion -> toggleCardExpansion(event.cardId)
            is SettingsEvent.ProfileAvatarTapped -> handleProfileAvatarTapped()
            is SettingsEvent.ImagePickerDialogDismissed -> dismissImagePickerDialog()
            is SettingsEvent.ProfileImageSelected -> handleProfileImageSelected(event.imageUri)
            is SettingsEvent.ErrorDismissed -> dismissError()
            is SettingsEvent.RetryRequested -> retryLastFailedOperation()
            is SettingsEvent.UpgradeSubscription -> handleSubscriptionUpgrade()
            is SettingsEvent.ManageSubscription -> handleSubscriptionManagement()
            is SettingsEvent.SubscriptionPurchaseCompleted -> handleSubscriptionPurchaseCompleted()
            is SettingsEvent.ExportDataRequested -> handleDataExport()
            is SettingsEvent.DeleteAccountRequested -> handleAccountDeletion()
            is SettingsEvent.SystemThemeChanged -> handleSystemThemeChanged()
            is SettingsEvent.SubscriptionStatusChanged -> handleSubscriptionStatusChanged()
        }
    }

    /**
     * Loads user settings, profile, and subscription status.
     * This is the primary data loading method for the settings screen.
     */
    private fun loadSettings() {
        val userId = currentUserId ?: return
        
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            
            try {
                // Combine settings, profile, and subscription data loading
                combine(
                    getUserSettingsUseCase(userId),
                    getProfileUseCase(userId).map { Result.success(it) }.catch { emit(Result.failure(it)) },
                    getSubscriptionStatusUseCase(userId)
                ) { settingsResult, profileResult, subscriptionResult ->
                    Triple(settingsResult, profileResult, subscriptionResult)
                }
                .catch { exception ->
                    Timber.e(exception, "Error loading settings for user: $userId")
                    updateState { 
                        withError("Failed to load settings: ${exception.message}")
                    }
                }
                .collect { (settingsResult, profileResult, subscriptionResult) ->
                    processSettingsResults(settingsResult, profileResult, subscriptionResult)
                }
            } catch (exception: Exception) {
                Timber.e(exception, "Exception in loadSettings")
                updateState { 
                    withError("Failed to load settings")
                }
            }
        }
    }

    /**
     * Refreshes settings data from remote sources.
     */
    private fun refreshSettings() {
        trackSettingsRefreshed()
        loadSettings()
    }

    /**
     * Updates the dark mode setting.
     * 
     * @param enabled New dark mode state
     */
    private fun updateDarkMode(enabled: Boolean) {
        val userId = currentUserId ?: return
        
        viewModelScope.launch {
            updateState { copy(isUpdatingSettings = true) }
            
            try {
                Timber.d("Updating dark mode for user $userId to: $enabled")
                
                // Update theme manager immediately for instant UI response
                val themeMode = if (enabled) ThemeMode.DARK else ThemeMode.LIGHT
                themeManager.switchTheme(themeMode)
                
                val result = updateSettingsUseCase.updateDarkMode(userId, enabled)
                
                result.fold(
                    onSuccess = {
                        Timber.d("Dark mode updated successfully")
                        trackSettingChanged("dark_mode", enabled)
                        // State will be updated through the reactive flow
                    },
                    onFailure = { exception ->
                        Timber.e(exception, "Failed to update dark mode")
                        // Revert theme manager change if settings update failed
                        val revertMode = if (enabled) ThemeMode.LIGHT else ThemeMode.DARK
                        themeManager.switchTheme(revertMode)
                        
                        updateState { 
                            withError("Failed to update dark mode: ${exception.message}")
                        }
                        lastFailedEvent = SettingsEvent.UpdateDarkMode(enabled)
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Exception updating dark mode")
                // Revert theme manager change if exception occurred
                val revertMode = if (enabled) ThemeMode.LIGHT else ThemeMode.DARK
                themeManager.switchTheme(revertMode)
                
                updateState { 
                    withError("Failed to update dark mode")
                }
                lastFailedEvent = SettingsEvent.UpdateDarkMode(enabled)
            }
        }
    }

    /**
     * Updates the notification setting.
     * 
     * @param enabled New notification state
     */
    private fun updateNotifications(enabled: Boolean) {
        val userId = currentUserId ?: return
        
        viewModelScope.launch {
            updateState { copy(isUpdatingSettings = true) }
            
            try {
                Timber.d("Updating notifications for user $userId to: $enabled")
                
                val result = updateSettingsUseCase.updateNotifications(userId, enabled)
                
                result.fold(
                    onSuccess = {
                        Timber.d("Notifications updated successfully")
                        trackSettingChanged("notifications", enabled)
                        // State will be updated through the reactive flow
                    },
                    onFailure = { exception ->
                        Timber.e(exception, "Failed to update notifications")
                        updateState { 
                            withError("Failed to update notifications: ${exception.message}")
                        }
                        lastFailedEvent = SettingsEvent.UpdateNotifications(enabled)
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Exception updating notifications")
                updateState { 
                    withError("Failed to update notifications")
                }
                lastFailedEvent = SettingsEvent.UpdateNotifications(enabled)
            }
        }
    }

    /**
     * Updates the user's weight unit preference.
     */
    private fun updateWeightUnit(weightUnit: WeightUnit) {
        val userId = currentUserId ?: return
        
        viewModelScope.launch {
            updateState { copy(isUpdatingSettings = true) }
            
            try {
                Timber.d("Updating weight unit for user $userId to: ${weightUnit.symbol}")
                
                val result = updateWeightUnitPreferenceUseCase(userId, weightUnit)
                
                result.fold(
                    onSuccess = {
                        Timber.d("Weight unit updated successfully")
                        trackSettingChanged("weight_unit", weightUnit.symbol)
                        // State will be updated through the reactive flow
                    },
                    onFailure = { exception ->
                        Timber.e(exception, "Failed to update weight unit")
                        updateState { 
                            withError("Failed to update weight unit: ${exception.message}")
                        }
                        lastFailedEvent = SettingsEvent.UpdateWeightUnit(weightUnit)
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Exception updating weight unit")
                updateState { 
                    withError("Failed to update weight unit")
                }
                lastFailedEvent = SettingsEvent.UpdateWeightUnit(weightUnit)
            }
        }
    }

    /**
     * Handles profile navigation event.
     */
    private fun handleProfileNavigation() {
        trackNavigationEvent("profile")
        // Navigation will be handled by the UI layer
    }

    /**
     * Handles subscription navigation event.
     */
    private fun handleSubscriptionNavigation() {
        trackNavigationEvent("subscription")
        // Navigation will be handled by the UI layer
    }

    /**
     * Handles privacy navigation event.
     */
    private fun handlePrivacyNavigation() {
        trackNavigationEvent("privacy")
        // Navigation will be handled by the UI layer
    }

    /**
     * Handles help navigation event.
     */
    private fun handleHelpNavigation() {
        trackNavigationEvent("help")
        // Navigation will be handled by the UI layer
    }

    /**
     * Handles about navigation event.
     */
    private fun handleAboutNavigation() {
        trackNavigationEvent("about")
        // Navigation will be handled by the UI layer
    }

    /**
     * Handles anomaly detection settings navigation event.
     */
    private fun handleAnomalyDetectionNavigation() {
        trackNavigationEvent("anomaly_detection")
        // Navigation will be handled by the UI layer
    }

    /**
     * Handles anomaly detection dashboard navigation event.
     */
    private fun handleAnomalyDashboardNavigation() {
        trackNavigationEvent("anomaly_dashboard")
        // Navigation will be handled by the UI layer
    }

    /**
     * Handles navigation to widget settings screen.
     */
    private fun handleWidgetSettingsNavigation() {
        trackNavigationEvent("widget_settings")
        // Navigation will be handled by the UI layer
    }

    /**
     * Requests sign out by showing confirmation dialog.
     */
    private fun requestSignOut() {
        trackSignOutRequested()
        updateState { withLogoutDialog(true) }
    }

    /**
     * Confirms sign out and proceeds with logout process.
     */
    private fun confirmSignOut() {
        updateState { 
            copy(
                isSigningOut = true,
                showLogoutDialog = false
            )
        }
        
        viewModelScope.launch {
            try {
                Timber.d("Confirming sign out")
                
                val result = enhancedSignOutUseCase()
                
                result.fold(
                    onSuccess = {
                        Timber.d("Sign out completed successfully")
                        trackSignOutCompleted()
                        // Authentication state observer will handle navigation
                    },
                    onFailure = { exception ->
                        Timber.e(exception, "Failed to sign out")
                        updateState { 
                            withError("Failed to sign out: ${exception.message}")
                        }
                        lastFailedEvent = SettingsEvent.SignOutConfirmed
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Exception during sign out")
                updateState { 
                    withError("Failed to sign out")
                }
                lastFailedEvent = SettingsEvent.SignOutConfirmed
            }
        }
    }

    /**
     * Cancels sign out and dismisses confirmation dialog.
     */
    private fun cancelSignOut() {
        trackSignOutCancelled()
        updateState { withLogoutDialog(false) }
    }

    /**
     * Toggles the expansion state of a settings card.
     * 
     * @param cardId The ID of the card to toggle
     */
    private fun toggleCardExpansion(cardId: String) {
        val currentExpanded = _uiState.value.expandedCard
        val newExpanded = if (currentExpanded == cardId) null else cardId
        
        updateState { withExpandedCard(newExpanded) }
        trackCardExpansion(cardId, newExpanded != null)
    }

    /**
     * Handles profile avatar tap event.
     */
    private fun handleProfileAvatarTapped() {
        trackProfileAvatarTapped()
        _uiState.value = _uiState.value.withImagePickerDialog(true)
    }

    /**
     * Dismisses the image picker dialog.
     */
    private fun dismissImagePickerDialog() {
        _uiState.value = _uiState.value.withImagePickerDialog(false)
    }

    /**
     * Handles profile image selection and initiates upload.
     */
    private fun handleProfileImageSelected(imageUri: android.net.Uri) {
        viewModelScope.launch {
            try {
                // Dismiss dialog first
                _uiState.value = _uiState.value.withImagePickerDialog(false)
                
                // Get current user ID from auth
                val currentUser = authRepository.getCurrentUser()
                if (currentUser == null) {
                    Timber.w("Cannot upload profile image - user not authenticated")
                    _uiState.value = _uiState.value.withError("Please sign in to upload profile image")
                    return@launch
                }
                
                Timber.d("Starting profile image upload for user: ${currentUser.uid}")
                
                // Show loading state (could be enhanced with specific image upload loading state)
                _uiState.value = _uiState.value.copy(isUpdatingSettings = true)
                
                // Upload image using the use case
                val result = uploadProfileImageUseCase(
                    userId = currentUser.uid,
                    imageUri = imageUri,
                    cropRect = null // No cropping for now
                )
                
                if (result.isSuccess) {
                    val imageUrl = result.getOrThrow()
                    Timber.i("Profile image upload successful: $imageUrl")
                    
                    // Clear loading state
                    _uiState.value = _uiState.value.copy(isUpdatingSettings = false)
                    
                    // Track successful upload
                    analyticsService.logEvent(
                        "profile_image_uploaded",
                        mapOf(
                            "source" to "settings_screen",
                            "success" to true
                        )
                    )
                    
                } else {
                    val error = result.exceptionOrNull()
                    val errorMessage = error?.message ?: "Failed to upload profile image"
                    Timber.e(error, "Profile image upload failed")
                    
                    _uiState.value = _uiState.value.copy(
                        isUpdatingSettings = false,
                        error = errorMessage
                    )
                    
                    // Track failed upload
                    analyticsService.logEvent(
                        "profile_image_upload_failed",
                        mapOf(
                            "source" to "settings_screen",
                            "error" to errorMessage
                        )
                    )
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error during profile image upload")
                _uiState.value = _uiState.value.copy(
                    isUpdatingSettings = false,
                    error = "Unexpected error: ${e.message}"
                )
            }
        }
    }

    /**
     * Dismisses the current error state.
     */
    private fun dismissError() {
        updateState { clearError() }
    }

    /**
     * Retries the last failed operation.
     */
    private fun retryLastFailedOperation() {
        lastFailedEvent?.let { event ->
            Timber.d("Retrying last failed operation: $event")
            onEvent(event)
            lastFailedEvent = null
        }
    }

    /**
     * Handles subscription upgrade event.
     */
    private fun handleSubscriptionUpgrade() {
        trackSubscriptionAction("upgrade_requested")
        // Subscription upgrade logic will be handled by the UI layer
    }

    /**
     * Handles subscription management event.
     */
    private fun handleSubscriptionManagement() {
        trackSubscriptionAction("manage_requested")
        // Subscription management logic will be handled by the UI layer
    }

    /**
     * Handles subscription purchase completion.
     */
    private fun handleSubscriptionPurchaseCompleted() {
        trackSubscriptionAction("purchase_completed")
        refreshSettings()
    }

    /**
     * Handles data export request.
     */
    private fun handleDataExport() {
        trackDataAction("export_requested")
        // Data export logic will be handled by the UI layer
    }

    /**
     * Handles account deletion request.
     */
    private fun handleAccountDeletion() {
        trackDataAction("delete_requested")
        // Account deletion logic will be handled by the UI layer
    }

    /**
     * Handles system theme change event.
     */
    private fun handleSystemThemeChanged() {
        trackSystemEvent("theme_changed")
        // Theme change will be handled by the UI layer
    }

    /**
     * Handles subscription status change event.
     */
    private fun handleSubscriptionStatusChanged() {
        trackSystemEvent("subscription_status_changed")
        refreshSettings()
    }

    /**
     * Observes authentication state and loads data when user is available.
     */
    private fun observeAuthenticationState() {
        viewModelScope.launch {
            authRepository.currentUser
                .distinctUntilChanged()
                .map { it?.uid }
                .catch { exception ->
                    Timber.e(exception, "Error observing authentication state")
                    updateState { withError("Authentication error") }
                }
                .collect { userId ->
                    currentUserId = userId
                    if (userId != null) {
                        loadSettings()
                    } else {
                        updateState { 
                            SettingsState(error = "User not authenticated")
                        }
                    }
                }
        }
    }

    /**
     * Processes the results from settings, profile, and subscription loading.
     */
    private fun processSettingsResults(
        settingsResult: Result<com.example.liftrix.domain.model.UserSettings>,
        profileResult: Result<com.example.liftrix.domain.model.UserProfile?>,
        subscriptionResult: Result<com.example.liftrix.domain.model.SubscriptionStatus>
    ) {
        val settings = settingsResult.getOrNull()
        val profile = profileResult.getOrNull()
        val subscription = subscriptionResult.getOrNull()
        
        // Sync theme manager with user settings
        settings?.let { userSettings ->
            val themeMode = if (userSettings.darkMode) ThemeMode.DARK else ThemeMode.LIGHT
            themeManager.switchTheme(themeMode)
        }
        
        val errorMessage = when {
            settingsResult.isFailure && profileResult.isFailure && subscriptionResult.isFailure -> {
                "Failed to load settings, profile, and subscription data"
            }
            settingsResult.isFailure && subscriptionResult.isFailure -> {
                "Failed to load settings and subscription data"
            }
            settingsResult.isFailure && profileResult.isFailure -> {
                "Failed to load settings and profile data"
            }
            profileResult.isFailure && subscriptionResult.isFailure -> {
                "Failed to load profile and subscription data"
            }
            settingsResult.isFailure -> {
                "Failed to load settings data"
            }
            profileResult.isFailure -> {
                "Failed to load profile data"
            }
            subscriptionResult.isFailure -> {
                "Failed to load subscription data"
            }
            else -> null
        }
        
        updateState {
            copy(
                isLoading = false,
                userSettings = settings,
                userProfile = profile,
                subscriptionStatus = subscription,
                error = errorMessage,
                isUpdatingSettings = false
            )
        }
    }

    /**
     * Updates the UI state safely.
     */
    private fun updateState(update: SettingsState.() -> SettingsState) {
        _uiState.value = _uiState.value.update()
    }

    // Analytics tracking methods
    private fun trackSettingsScreenViewed() {
        viewModelScope.launch {
            try {
                analyticsService.logEvent(
                    "settings_screen_viewed",
                    mapOf("timestamp" to System.currentTimeMillis())
                )
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track settings screen viewed")
            }
        }
    }

    private fun trackSettingsRefreshed() {
        viewModelScope.launch {
            try {
                analyticsService.logEvent(
                    "settings_refreshed",
                    mapOf("timestamp" to System.currentTimeMillis())
                )
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track settings refreshed")
            }
        }
    }

    private fun trackSettingChanged(settingName: String, value: Any) {
        viewModelScope.launch {
            try {
                analyticsService.logEvent(
                    "setting_changed",
                    mapOf(
                        "setting_name" to settingName,
                        "new_value" to value,
                        "timestamp" to System.currentTimeMillis()
                    )
                )
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track setting changed")
            }
        }
    }

    private fun trackNavigationEvent(destination: String) {
        viewModelScope.launch {
            try {
                analyticsService.logEvent(
                    "settings_navigation",
                    mapOf(
                        "destination" to destination,
                        "timestamp" to System.currentTimeMillis()
                    )
                )
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track navigation event")
            }
        }
    }

    private fun trackSignOutRequested() {
        viewModelScope.launch {
            try {
                analyticsService.logEvent(
                    "settings_sign_out_requested",
                    mapOf("timestamp" to System.currentTimeMillis())
                )
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track sign out requested")
            }
        }
    }

    private fun trackSignOutCompleted() {
        viewModelScope.launch {
            try {
                analyticsService.logEvent(
                    "settings_sign_out_completed",
                    mapOf("timestamp" to System.currentTimeMillis())
                )
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track sign out completed")
            }
        }
    }

    private fun trackSignOutCancelled() {
        viewModelScope.launch {
            try {
                analyticsService.logEvent(
                    "settings_sign_out_cancelled",
                    mapOf("timestamp" to System.currentTimeMillis())
                )
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track sign out cancelled")
            }
        }
    }

    private fun trackCardExpansion(cardId: String, expanded: Boolean) {
        viewModelScope.launch {
            try {
                analyticsService.logEvent(
                    "settings_card_toggled",
                    mapOf(
                        "card_id" to cardId,
                        "expanded" to expanded,
                        "timestamp" to System.currentTimeMillis()
                    )
                )
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track card expansion")
            }
        }
    }

    private fun trackProfileAvatarTapped() {
        viewModelScope.launch {
            try {
                analyticsService.logEvent(
                    "settings_profile_avatar_tapped",
                    mapOf("timestamp" to System.currentTimeMillis())
                )
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track profile avatar tapped")
            }
        }
    }

    private fun trackSubscriptionAction(action: String) {
        viewModelScope.launch {
            try {
                analyticsService.logEvent(
                    "settings_subscription_action",
                    mapOf(
                        "action" to action,
                        "timestamp" to System.currentTimeMillis()
                    )
                )
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track subscription action")
            }
        }
    }

    private fun trackDataAction(action: String) {
        viewModelScope.launch {
            try {
                analyticsService.logEvent(
                    "settings_data_action",
                    mapOf(
                        "action" to action,
                        "timestamp" to System.currentTimeMillis()
                    )
                )
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track data action")
            }
        }
    }

    private fun trackSystemEvent(event: String) {
        viewModelScope.launch {
            try {
                analyticsService.logEvent(
                    "settings_system_event",
                    mapOf(
                        "event" to event,
                        "timestamp" to System.currentTimeMillis()
                    )
                )
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track system event")
            }
        }
    }
    
    /**
     * Gets the current authenticated user ID for security validation.
     * 
     * @return Current user ID if authenticated, null otherwise
     */
    suspend fun getCurrentUserId(): String? {
        return authRepository.getCurrentUserId()
    }
}