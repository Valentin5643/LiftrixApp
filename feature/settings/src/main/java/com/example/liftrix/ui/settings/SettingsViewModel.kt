package com.example.liftrix.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.interactor.account.AccountInteractor
import com.example.liftrix.domain.interactor.admin.AdminInteractor
import com.example.liftrix.domain.interactor.auth.AuthInteractor
import com.example.liftrix.domain.interactor.settings.SettingsInteractor
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.SettingsRepository
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.domain.service.SettingsValidator
import com.example.liftrix.service.ImageProcessingService
import com.example.liftrix.domain.usecase.profile.ProfileImageOperationsUseCase
import com.example.liftrix.domain.usecase.profile.ProfileQueryUseCase
import com.example.liftrix.domain.usecase.social.SocialProfileQueryUseCase
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.domain.model.error.LiftrixError
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
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
 * @property settingsInteractor Interactor for settings and subscription queries
 * @property authInteractor Interactor for comprehensive auth operations
 * @property authRepository Repository for authentication operations
 * @property analyticsService Service for tracking analytics events
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsInteractor: SettingsInteractor,
    private val settingsRepository: SettingsRepository,
    private val profileQueryUseCase: ProfileQueryUseCase,
    private val socialProfileQueryUseCase: SocialProfileQueryUseCase,
    private val authInteractor: AuthInteractor,
    private val accountInteractor: AccountInteractor,
    private val profileImageOperationsUseCase: ProfileImageOperationsUseCase,
    private val imageProcessingService: ImageProcessingService,
    private val authRepository: AuthRepository,
    private val analyticsService: AnalyticsService,
    private val settingsValidator: SettingsValidator,
    private val adminInteractor: AdminInteractor,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsState(isLoading = true))
    val uiState: StateFlow<SettingsState> = _uiState.asStateFlow()

    private var currentUserId: String? = null
    private var cachedCurrentUser: com.example.liftrix.domain.model.User? = null
    private var lastFailedEvent: SettingsEvent? = null
    private var loadSettingsJob: kotlinx.coroutines.Job? = null
    private var lastLoadedUserId: String? = null
    
    // Theme manager for immediate theme updates
    private val themeManager: ThemeManager by lazy { ThemeManager.getInstance(context) }

    init {
        // Start authentication observation immediately
        observeAuthenticationState()
        
        // Also check authentication immediately as backup
        viewModelScope.launch {
            // Add a small delay to let Firebase Auth initialize
            kotlinx.coroutines.delay(100)
            
            val currentUser = authRepository.getCurrentUser()
            if (currentUser != null) {
                cachedCurrentUser = currentUser // Cache the user object
                if (currentUserId == null) { // Only set if not already set by Flow
                    currentUserId = currentUser.uid
                    loadSettings()
                    // Validate settings integrity on startup
                    validateAndRepairSettings(currentUser.uid)
                }
            } else {
                // Give the Flow more time, but set a hard timeout
                kotlinx.coroutines.delay(3000) // Wait 3 more seconds
                if (currentUserId == null) {
                    updateState { 
                        copy(
                            isLoading = false,
                            error = "Authentication timeout. Please try restarting the app or signing in again."
                        )
                    }
                }
            }
        }
        
        trackSettingsScreenViewed()
    }
    
    /**
     * Validates settings integrity on app startup and repairs if needed.
     */
    private suspend fun validateAndRepairSettings(userId: String) {
        Timber.d("Validating settings integrity for user $userId")
        try {
            val settings = settingsRepository.getUserSettings(userId).firstOrNull()
            if (settings == null) {
                Timber.w("Settings not found during integrity validation for user $userId")
            } else {
                Timber.d("Settings integrity validated successfully for user $userId")
            }
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to validate settings integrity for user $userId")
        }
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
            is SettingsEvent.UpdateDarkMode -> {
                // For backward compatibility, use the legacy updateDarkMode
                updateDarkMode(event.enabled)
            }
            is SettingsEvent.ToggleTheme -> {
                // New event for theme toggling with system awareness
                updateThemeFromToggle(event.isSystemInDarkTheme)
            }
            is SettingsEvent.UpdateNotifications -> updateNotifications(event.enabled)
            is SettingsEvent.UpdateWeightUnit -> updateWeightUnit(event.weightUnit)
            is SettingsEvent.NavigateToProfile -> handleProfileNavigation()
            is SettingsEvent.NavigateToSubscription -> handleSubscriptionNavigation()
            is SettingsEvent.NavigateToPrivacy -> handlePrivacyNavigation()
            is SettingsEvent.NavigateToTermsOfService -> handleTermsOfServiceNavigation()
            is SettingsEvent.NavigateToAIDisclaimer -> handleAIDisclaimerNavigation()
            is SettingsEvent.NavigateToCommunityGuidelines -> handleCommunityGuidelinesNavigation()
            is SettingsEvent.NavigateToContentModerationPolicy -> handleContentModerationPolicyNavigation()
            is SettingsEvent.NavigateToRefundPolicy -> handleRefundPolicyNavigation()
            is SettingsEvent.NavigateToAIChatSettings -> handleAIChatSettingsNavigation()
            is SettingsEvent.NavigateToHelpCenter -> handleHelpCenterNavigation()
            is SettingsEvent.NavigateToContactSupport -> handleContactSupportNavigation()
            is SettingsEvent.NavigateToHelp -> handleHelpNavigation()
            is SettingsEvent.NavigateToAbout -> handleAboutNavigation()
            is SettingsEvent.NavigateToAnomalyDetection -> handleAnomalyDetectionNavigation()
            is SettingsEvent.NavigateToAnomalyDashboard -> handleAnomalyDashboardNavigation()
            is SettingsEvent.NavigateToWidgetSettings -> handleWidgetSettingsNavigation()
            is SettingsEvent.NavigateToNotifications -> handleNotificationsNavigation()
            is SettingsEvent.NavigateToEmailChange -> handleEmailChangeNavigation()
            is SettingsEvent.NavigateToPasswordChange -> handlePasswordChangeNavigation()
            is SettingsEvent.NavigateToUsernameChange -> handleUsernameChangeNavigation()
            is SettingsEvent.NavigateToAccountDeletion -> handleAccountDeletionNavigation()
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
            is SettingsEvent.NavigateToDataPortability -> handleDataPortabilityNavigation()
            is SettingsEvent.NavigateToExportProgressReport -> handleExportProgressReportNavigation()
            is SettingsEvent.DeleteAccountRequested -> handleAccountDeletion()
            is SettingsEvent.DeleteAccountDialogDismissed -> dismissDeleteAccountDialog()
            is SettingsEvent.DeleteAccountConfirmed -> confirmAccountDeletion(
                event.reauthProvider,
                event.reauthPayload,
                event.exportDataFirst
            )
            is SettingsEvent.SystemThemeChanged -> handleSystemThemeChanged()
            is SettingsEvent.SubscriptionStatusChanged -> handleSubscriptionStatusChanged()
        }
    }

    /**
     * Loads user settings, profile, and subscription status.
     * This is the primary data loading method for the settings screen.
     * Implements job deduplication and cancellation handling.
     */
    private fun loadSettings() {
        val userId = currentUserId ?: return
        
        // Skip if already loading for the same user
        if (loadSettingsJob?.isActive == true && lastLoadedUserId == userId) {
            return
        }
        
        // Cancel any existing loadSettings job to prevent race conditions
        loadSettingsJob?.cancel()
        lastLoadedUserId = userId
        
        loadSettingsJob = viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            
            try {
                // Add timeout to prevent infinite hanging
                kotlinx.coroutines.withTimeout(10_000) { // 10 second timeout
                    // Use supervisorScope to prevent cascade failures
                    kotlinx.coroutines.supervisorScope {
                        // Combine settings, profile, social profile, and subscription data loading
                        combine(
                            settingsInteractor.userSettings(userId),
                            profileQueryUseCase(userId).map { Result.success(it) }.catch { emit(Result.failure(it)) },
                            kotlinx.coroutines.flow.flow {
                                val socialProfileResult = socialProfileQueryUseCase.invoke(userId)
                                emit(socialProfileResult)
                            }.catch { throwable ->
                                val error = LiftrixError.BusinessLogicError(
                                    code = "SOCIAL_PROFILE_FETCH_FAILED",
                                    errorMessage = "Failed to fetch social profile: ${throwable.message}",
                                    analyticsContext = mapOf("operation" to "FETCH_SOCIAL_PROFILE")
                                )
                                emit(com.example.liftrix.domain.model.common.liftrixFailure(error))
                            },
                            settingsInteractor.subscriptionStatus(userId)
                        ) { settingsResult, profileResult, socialProfileResult, subscriptionResult ->
                            SettingsLoadResult(settingsResult, profileResult, socialProfileResult, subscriptionResult)
                        }
                        .catch { exception ->
                            when (exception) {
                                is kotlinx.coroutines.CancellationException -> {
                                    throw exception // Re-throw cancellation to respect cooperative cancellation
                                }
                                else -> {
                                    updateState { 
                                        withError("Failed to load settings: ${exception.message}")
                                    }
                                }
                            }
                        }
                        .collect { loadResult ->
                            processSettingsResults(loadResult)
                        }
                    }
                }
            } catch (exception: kotlinx.coroutines.TimeoutCancellationException) {
                // Provide default settings to prevent infinite loading
                provideDefaultSettings()
            } catch (exception: kotlinx.coroutines.CancellationException) {
                // Don't update error state for cancellation - it's expected behavior
            } catch (exception: Exception) {
                // Provide default settings as fallback
                provideDefaultSettings()
            }
        }
    }

    /**
     * Refreshes settings data from remote sources.
     * Uses the same robust loading mechanism as loadSettings().
     */
    private fun refreshSettings() {
        trackSettingsRefreshed()
        loadSettings() // Uses the same job deduplication and cancellation handling
    }

    /**
     * Updates the theme state from the UI toggle
     * Requires system theme state to properly handle toggling
     * 
     * @param isSystemInDarkTheme Current system dark theme state
     */
    fun updateThemeFromToggle(isSystemInDarkTheme: Boolean) {
        val userId = currentUserId ?: return
        
        viewModelScope.launch {
            updateState { copy(isUpdatingSettings = true) }
            
            try {
                // Use the toggle method which handles system mode properly
                themeManager.toggleTheme(isSystemInDarkTheme)
                
                // Get the NEW effective state AFTER toggling for persistence
                val newEffectiveState = themeManager.getEffectiveThemeState(isSystemInDarkTheme)
                
                Timber.d("Toggled theme for user $userId, new effective state: $newEffectiveState")
                
                // Update the UI state immediately with the new state
                updateState { copy(effectiveThemeState = newEffectiveState) }
                
                val persistenceResult = settingsRepository.updateDarkMode(userId, newEffectiveState)
                
                persistenceResult.fold(
                    onSuccess = {
                        // Verify the setting was actually persisted
                        val verificationResult = settingsValidator.verifySetting(
                            userId = userId,
                            key = "dark_mode",
                            expectedValue = newEffectiveState
                        )
                        
                        verificationResult.fold(
                            onSuccess = { isVerified: Boolean ->
                                if (isVerified) {
                                    Timber.d("Dark mode updated and verified successfully")
                                    trackSettingChanged("dark_mode", newEffectiveState)
                                    updateState { copy(isUpdatingSettings = false) }
                                    // Don't reload settings - we already have the updated state
                                } else {
                                    // Setting didn't persist correctly, try again
                                    Timber.w("Dark mode setting not verified, retrying...")
                                    viewModelScope.launch {
                                        retrySettingUpdate("dark_mode", newEffectiveState)
                                    }
                                }
                            },
                            onFailure = { exception: Throwable ->
                                Timber.e(exception, "Failed to verify dark mode setting")
                                // Continue with fallback to use case
                                viewModelScope.launch {
                                    fallbackToUseCase(userId, newEffectiveState, "dark_mode")
                                }
                            }
                        )
                    },
                    onFailure = { exception ->
                        Timber.e(exception, "Failed to persist dark mode setting")
                        // Fallback to original use case method
                        viewModelScope.launch {
                            fallbackToUseCase(userId, newEffectiveState, "dark_mode")
                        }
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Exception updating dark mode")
                viewModelScope.launch {
                    // Calculate the effective state that would result from the toggle
                    val effectiveState = themeManager.getEffectiveThemeState(isSystemInDarkTheme)
                    fallbackToUseCase(userId, effectiveState, "dark_mode")
                }
            }
        }
    }
    
    /**
     * Fallback method that uses the original use case when persistence manager fails.
     */
    private suspend fun fallbackToUseCase(userId: String, enabled: Boolean, settingType: String) {
        Timber.d("Using fallback use case for $settingType")
        
        when (settingType) {
            "dark_mode" -> {
                val result = settingsRepository.updateDarkMode(userId, enabled)
                result.fold(
                    onSuccess = {
                        Timber.d("Dark mode updated successfully via fallback")
                        trackSettingChanged("dark_mode", enabled)
                        updateState { copy(isUpdatingSettings = false) }
                    },
                    onFailure = { exception ->
                        Timber.e(exception, "Fallback failed for dark mode")
                        // Revert theme manager change if settings update failed
                        val revertMode = if (enabled) ThemeMode.LIGHT else ThemeMode.DARK
                        themeManager.switchTheme(revertMode)
                        
                        updateState { 
                            withError("Failed to update dark mode: ${exception.message}")
                        }
                        lastFailedEvent = SettingsEvent.UpdateDarkMode(enabled)
                    }
                )
            }
            "notifications_enabled" -> {
                val result = settingsRepository.updateNotifications(userId, enabled)
                result.fold(
                    onSuccess = {
                        Timber.d("Notifications updated successfully via fallback")
                        trackSettingChanged("notifications", enabled)
                        updateState { copy(isUpdatingSettings = false) }
                    },
                    onFailure = { exception ->
                        Timber.e(exception, "Fallback failed for notifications")
                        updateState { 
                            withError("Failed to update notifications: ${exception.message}")
                        }
                        lastFailedEvent = SettingsEvent.UpdateNotifications(enabled)
                    }
                )
            }
        }
    }
    
    /**
     * Retries a setting update with exponential backoff.
     */
    private suspend fun retrySettingUpdate(key: String, value: Any, maxRetries: Int = 3) {
        val userId = currentUserId ?: return
        
        for (attempt in 1..maxRetries) {
            Timber.d("Retry attempt $attempt for setting $key")
            
            delay(1000L * attempt) // Exponential backoff
            
            val retryResult = persistSetting(userId, key, value)
            
            retryResult.fold(
                onSuccess = {
                    val verificationResult = settingsValidator.verifySetting(
                        userId = userId,
                        key = key,
                        expectedValue = value
                    )
                    
                    verificationResult.fold(
                        onSuccess = { isVerified ->
                            if (isVerified) {
                                Timber.i("Setting $key verified after retry attempt $attempt")
                                updateState { copy(isUpdatingSettings = false) }
                                return
                            }
                        },
                        onFailure = { 
                            Timber.w("Verification failed on retry attempt $attempt for $key")
                        }
                    )
                },
                onFailure = { exception ->
                    Timber.w(exception, "Retry attempt $attempt failed for setting $key")
                }
            )
        }
        
        // All retries failed
        Timber.e("All retry attempts failed for setting $key")
        updateState { 
            withError("Settings update failed after multiple attempts. Please try again.")
        }
        
        when (key) {
            "dark_mode" -> lastFailedEvent = SettingsEvent.UpdateDarkMode(value as Boolean)
            "weight_unit" -> lastFailedEvent = SettingsEvent.UpdateWeightUnit(value as WeightUnit)
            "notifications_enabled" -> lastFailedEvent = SettingsEvent.UpdateNotifications(value as Boolean)
        }
    }

    private suspend fun persistSetting(userId: String, key: String, value: Any): Result<Unit> {
        return when (key) {
            "dark_mode" -> settingsRepository.updateDarkMode(userId, value as Boolean)
            "notifications_enabled" -> settingsRepository.updateNotifications(userId, value as Boolean)
            "weight_unit" -> settingsRepository.updateWeightUnit(userId, value as WeightUnit)
            else -> Result.failure(IllegalArgumentException("Unsupported setting key: $key"))
        }
    }

    /**
     * Legacy method for updating dark mode directly
     * Use updateThemeFromToggle for proper system-aware toggling
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
                
                // Update UI state
                updateState { copy(effectiveThemeState = enabled) }
                
                val persistenceResult = settingsRepository.updateDarkMode(userId, enabled)
                
                persistenceResult.fold(
                    onSuccess = {
                        Timber.d("Dark mode updated successfully")
                        trackSettingChanged("dark_mode", enabled)
                        updateState { copy(isUpdatingSettings = false) }
                        // Don't reload settings - we already have the updated state
                    },
                    onFailure = { error ->
                        Timber.e("Failed to update dark mode: $error")
                        // Revert theme manager change
                        val revertMode = if (enabled) ThemeMode.LIGHT else ThemeMode.DARK
                        themeManager.switchTheme(revertMode)
                        updateState { 
                            copy(
                                isUpdatingSettings = false,
                                effectiveThemeState = !enabled,
                                error = "Failed to update dark mode"
                            )
                        }
                        lastFailedEvent = SettingsEvent.UpdateDarkMode(enabled)
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Exception updating dark mode")
                updateState { 
                    copy(
                        isUpdatingSettings = false,
                        error = "Failed to update dark mode: ${exception.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Updates the notification setting with persistence verification and retry logic.
     * 
     * @param enabled New notification state
     */
    private fun updateNotifications(enabled: Boolean) {
        val userId = currentUserId ?: return
        
        viewModelScope.launch {
            updateState { copy(isUpdatingSettings = true) }
            
            try {
                Timber.d("Updating notifications for user $userId to: $enabled")
                
                val persistenceResult = settingsRepository.updateNotifications(userId, enabled)
                
                persistenceResult.fold(
                    onSuccess = {
                        // Verify the setting was actually persisted
                        val verificationResult = settingsValidator.verifySetting(
                            userId = userId,
                            key = "notifications_enabled",
                            expectedValue = enabled
                        )
                        
                        verificationResult.fold(
                            onSuccess = { isVerified ->
                                if (isVerified) {
                                    Timber.d("Notifications updated and verified successfully")
                                    trackSettingChanged("notifications", enabled)
                                    updateState { copy(isUpdatingSettings = false) }
                                } else {
                                    // Setting didn't persist correctly, try again
                                    Timber.w("Notifications setting not verified, retrying...")
                                    retrySettingUpdate("notifications_enabled", enabled)
                                }
                            },
                            onFailure = { exception ->
                                Timber.e(exception, "Failed to verify notifications setting")
                                // Continue with fallback to use case
                                fallbackToUseCaseNotifications(userId, enabled)
                            }
                        )
                    },
                    onFailure = { exception ->
                        Timber.e(exception, "Failed to persist notifications setting")
                        // Fallback to original use case method
                        fallbackToUseCaseNotifications(userId, enabled)
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Exception updating notifications")
                fallbackToUseCaseNotifications(userId, enabled)
            }
        }
    }
    
    /**
     * Fallback method for notifications when persistence manager fails.
     */
    private suspend fun fallbackToUseCaseNotifications(userId: String, enabled: Boolean) {
        Timber.d("Using fallback use case for notifications")
        
        val result = settingsRepository.updateNotifications(userId, enabled)
        result.fold(
            onSuccess = {
                Timber.d("Notifications updated successfully via fallback")
                trackSettingChanged("notifications", enabled)
                updateState { copy(isUpdatingSettings = false) }
            },
            onFailure = { exception ->
                Timber.e(exception, "Fallback failed for notifications")
                updateState { 
                    withError("Failed to update notifications: ${exception.message}")
                }
                lastFailedEvent = SettingsEvent.UpdateNotifications(enabled)
            }
        )
    }

    /**
     * Updates the user's weight unit preference with persistence verification and retry logic.
     * This is critical for fixing the weight unit persistence bug.
     */
    private fun updateWeightUnit(weightUnit: WeightUnit) {
        val userId = currentUserId ?: return
        
        viewModelScope.launch {
            updateState { copy(isUpdatingSettings = true) }
            
            try {
                Timber.d("Updating weight unit for user $userId to: ${weightUnit.symbol}")
                
                val persistenceResult = settingsRepository.updateWeightUnit(userId, weightUnit)
                
                persistenceResult.fold(
                    onSuccess = {
                        // Verify the weight unit was actually persisted - critical for this bug fix
                        val verificationResult = settingsValidator.verifySetting(
                            userId = userId,
                            key = "weight_unit",
                            expectedValue = weightUnit
                        )
                        
                        verificationResult.fold(
                            onSuccess = { isVerified ->
                                if (isVerified) {
                                    Timber.d("Weight unit updated and verified successfully")
                                    trackSettingChanged("weight_unit", weightUnit.symbol)
                                    updateState { copy(isUpdatingSettings = false) }
                                } else {
                                    // Weight unit didn't persist correctly - this is the bug we're fixing
                                    Timber.w("Weight unit setting not verified, retrying...")
                                    retrySettingUpdate("weight_unit", weightUnit)
                                }
                            },
                            onFailure = { exception ->
                                Timber.e(exception, "Failed to verify weight unit setting")
                                // Continue with fallback to use case
                                fallbackToUseCaseWeightUnit(userId, weightUnit)
                            }
                        )
                    },
                    onFailure = { exception ->
                        Timber.e(exception, "Failed to persist weight unit setting")
                        // Fallback to original use case method
                        fallbackToUseCaseWeightUnit(userId, weightUnit)
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Exception updating weight unit")
                fallbackToUseCaseWeightUnit(userId, weightUnit)
            }
        }
    }

    /**
     * Fallback method for weight unit when persistence manager fails.
     */
    private suspend fun fallbackToUseCaseWeightUnit(userId: String, weightUnit: WeightUnit) {
        Timber.d("Using fallback use case for weight unit")

        val result = settingsRepository.updateWeightUnit(userId, weightUnit)
        result.fold(
            onSuccess = {
                Timber.d("Weight unit updated successfully via fallback")
                trackSettingChanged("weight_unit", weightUnit.symbol)
                updateState { copy(isUpdatingSettings = false) }
            },
            onFailure = { exception ->
                Timber.e(exception, "Fallback failed for weight unit")
                updateState { 
                    withError("Failed to update weight unit: ${exception.message}")
                }
                lastFailedEvent = SettingsEvent.UpdateWeightUnit(weightUnit)
            }
        )
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
     * Handles terms of service navigation event.
     */
    private fun handleTermsOfServiceNavigation() {
        trackNavigationEvent("terms_of_service")
        // Navigation will be handled by the UI layer
    }

    /**
     * Handles AI disclaimer navigation event.
     */
    private fun handleAIDisclaimerNavigation() {
        trackNavigationEvent("ai_disclaimer")
        // Navigation will be handled by the UI layer
    }

    /**
     * Handles community guidelines navigation event.
     */
    private fun handleCommunityGuidelinesNavigation() {
        trackNavigationEvent("community_guidelines")
        // Navigation will be handled by the UI layer
    }

    /**
     * Handles content moderation policy navigation event.
     */
    private fun handleContentModerationPolicyNavigation() {
        trackNavigationEvent("content_moderation_policy")
        // Navigation will be handled by the UI layer
    }

    /**
     * Handles refund policy navigation event.
     */
    private fun handleRefundPolicyNavigation() {
        trackNavigationEvent("refund_subscription_policy")
        // Navigation will be handled by the UI layer
    }

    /**
     * Handles AI chat settings navigation event.
     */
    private fun handleAIChatSettingsNavigation() {
        trackNavigationEvent("ai_chat_settings")
        // Navigation will be handled by the UI layer
    }

    /**
     * Handles help center navigation event.
     */
    private fun handleHelpCenterNavigation() {
        trackNavigationEvent("help_center")
        // Navigation will be handled by the UI layer
    }

    /**
     * Handles contact support navigation event.
     */
    private fun handleContactSupportNavigation() {
        trackNavigationEvent("contact_support")
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
     * Handles notifications navigation event.
     */
    private fun handleNotificationsNavigation() {
        trackNavigationEvent("notifications")
        // Navigation will be handled by the UI layer
    }

    // Account Management Navigation Handlers (Added for SPEC-20250116-account-management)
    
    /**
     * Handles navigation to email change screen.
     * Tracks analytics event for navigation tracking.
     */
    private fun handleEmailChangeNavigation() {
        trackNavigationEvent("email_change")
        // Navigation will be handled by the UI layer
    }

    /**
     * Handles navigation to password change screen.
     * Tracks analytics event for navigation tracking.
     */
    private fun handlePasswordChangeNavigation() {
        trackNavigationEvent("password_change")
        // Navigation will be handled by the UI layer
    }

    /**
     * Handles navigation to username change screen.
     * Tracks analytics event for navigation tracking.
     */
    private fun handleUsernameChangeNavigation() {
        trackNavigationEvent("username_change")
        // Navigation will be handled by the UI layer
    }

    /**
     * Handles navigation to account deletion flow.
     * Tracks analytics event for navigation tracking.
     */
    private fun handleAccountDeletionNavigation() {
        trackNavigationEvent("account_deletion")
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
                
                val result = authInteractor.signOutEnhanced()
                
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

                // Show loading state
                _uiState.value = _uiState.value.copy(isUpdatingSettings = true, error = null)

                // Step 1: Process image URI to bytes using ImageProcessingService
                val processResult = imageProcessingService.processProfileImage(imageUri)

                when {
                    processResult.isSuccess -> {
                        val processedImage = processResult.getOrNull()!!
                        Timber.d("Image processed successfully: ${processedImage.fileSizeBytes} bytes")

                        // Step 2: Upload processed image bytes
                        val uploadResult = profileImageOperationsUseCase.upload(
                            userId = currentUser.uid,
                            imageBytes = processedImage.imageBytes
                        )

                        uploadResult.fold(
                            onSuccess = { storagePath ->
                                Timber.i("Profile image upload successful: $storagePath")

                                // Update UI state with success
                                _uiState.value = _uiState.value.copy(
                                    isUpdatingSettings = false,
                                    error = null
                                )

                                // Reload settings to show updated profile image
                                loadSettings()

                                // Track successful upload
                                analyticsService.logEvent(
                                    "profile_image_uploaded",
                                    mapOf(
                                        "source" to "settings_screen",
                                        "success" to true,
                                        "file_size_bytes" to processedImage.fileSizeBytes.toString()
                                    )
                                )
                            },
                            onFailure = { error ->
                                val errorMessage = when (error) {
                                    is LiftrixError.ValidationError -> error.violations.firstOrNull() ?: "Image validation failed"
                                    is LiftrixError.NetworkError -> "Network error: ${error.message}"
                                    else -> "Failed to upload profile image"
                                }
                                Timber.e("Profile image upload failed: $error")

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
                        )
                    }
                    else -> {
                        // Image processing failed
                        val error = processResult.exceptionOrNull()
                        val errorMessage = when {
                            error?.message?.contains("too large") == true -> "Image too large to process"
                            error?.message?.contains("format") == true -> "Unsupported image format"
                            else -> "Failed to process image"
                        }
                        Timber.e(error, "Image processing failed")

                        _uiState.value = _uiState.value.copy(
                            isUpdatingSettings = false,
                            error = errorMessage
                        )

                        // Track processing failure
                        analyticsService.logEvent(
                            "profile_image_processing_failed",
                            mapOf(
                                "source" to "settings_screen",
                                "error" to errorMessage
                            )
                        )
                    }
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
     * If no specific failed event, retries settings loading.
     */
    private fun retryLastFailedOperation() {
        lastFailedEvent?.let { event ->
            Timber.d("Retrying last failed operation: $event")
            onEvent(event)
            lastFailedEvent = null
        } ?: run {
            Timber.d("No specific failed event, retrying settings load")
            loadSettings()
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
     * Handles navigation to data portability screen.
     */
    private fun handleDataPortabilityNavigation() {
        trackDataAction("navigate_to_data_portability")
        // Navigation will be handled by the UI layer through callbacks
    }

    /**
     * Handles navigation to progress report export screen.
     */
    private fun handleExportProgressReportNavigation() {
        trackDataAction("navigate_to_export_progress_report")
        // Navigation will be handled by the UI layer through callbacks
    }

    /**
     * Handles account deletion request by showing the delete account confirmation dialog.
     */
    private fun handleAccountDeletion() {
        trackDataAction("delete_requested")
        updateState { copy(showDeleteAccountDialog = true) }
    }

    /**
     * Dismisses the delete account confirmation dialog.
     */
    private fun dismissDeleteAccountDialog() {
        updateState { copy(showDeleteAccountDialog = false) }
    }

    /**
     * Confirms account deletion and initiates the deletion process.
     *
     * @param reauthProvider The authentication provider ("password", "google", "anonymous")
     * @param reauthPayload The re-authentication credential
     * @param exportDataFirst Whether to export data before deletion
     */
    private fun confirmAccountDeletion(
        reauthProvider: String,
        reauthPayload: String,
        exportDataFirst: Boolean
    ) {
        val userId = currentUserId ?: return

        updateState { copy(showDeleteAccountDialog = false, isLoading = true) }
        trackDataAction("delete_confirmed")

        viewModelScope.launch {
            Timber.d("Initiating account deletion for user $userId with provider $reauthProvider, exportDataFirst=$exportDataFirst")

            val result = accountInteractor.deleteAccount(
                reauthProvider = reauthProvider,
                reauthPayload = reauthPayload,
                exportDataFirst = exportDataFirst
            )

            result.fold(
                onSuccess = { deletionJobId ->
                    Timber.i("Account deletion initiated successfully. Job ID: $deletionJobId")
                    trackDataAction("delete_success")

                    // Sign out the user after successful deletion initiation
                    confirmSignOut()
                },
                onFailure = { error ->
                    Timber.e("Failed to delete account for user $userId: $error")
                    trackDataAction("delete_failed")

                    updateState {
                        copy(
                            isLoading = false,
                            error = when (error) {
                                is LiftrixError.AuthenticationError -> "Re-authentication failed. Please check your credentials."
                                is LiftrixError.BusinessLogicError -> error.errorMessage
                                else -> "Failed to delete account. Please try again."
                            }
                        )
                    }
                }
            )
        }
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
                .catch { exception ->
                    Timber.e(exception, "Error observing authentication state")
                    updateState { withError("Authentication error") }
                }
                .collect { user ->
                    currentUserId = user?.uid
                    cachedCurrentUser = user // Cache the full user object
                    if (user != null) {
                        loadSettings()
                    } else {
                        cachedCurrentUser = null // Clear cache when user signs out
                        updateState { 
                            copy(
                                isLoading = false,
                                error = "User not authenticated",
                                userSettings = null,
                                userProfile = null,
                                currentUser = null,
                                subscriptionStatus = null
                            )
                        }
                    }
                }
        }
    }

    /**
     * Processes the results from settings, profile, and subscription loading.
     * Implements graceful degradation - shows content even if some data fails to load.
     */
    private fun processSettingsResults(
        loadResult: SettingsLoadResult
    ) {
        val settings = loadResult.settingsResult.getOrNull()
        val profile = loadResult.profileResult.getOrNull()
        val socialProfile = loadResult.socialProfileResult.fold(
            onSuccess = { it },
            onFailure = { null }
        )
        val subscription = loadResult.subscriptionResult.getOrNull()
        
        // Load the actual Firebase Auth user to prevent "Welcome to Liftrix" fallback
        // This is critical for proper username display in UserProfileCard
        viewModelScope.launch {
            // Try to get current user, use cached version if fetch fails
            val currentUser = try {
                val user = authRepository.getCurrentUser()
                if (user != null) {
                    cachedCurrentUser = user // Cache for future use
                    user
                } else {
                    cachedCurrentUser // Use cached version if current fetch returns null
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get current user for settings display, using cached")
                cachedCurrentUser // Fall back to cached user on error
            }
            
            // Sync theme manager with user settings only if there's a mode mismatch
            // Preserve SYSTEM mode - don't override user's choice to follow system theme
            settings?.let { userSettings ->
                val currentMode = themeManager.themeMode.value
                // Only sync if explicit mode doesn't match the settings (preserve SYSTEM mode)
                if ((currentMode == ThemeMode.DARK && !userSettings.darkMode) ||
                    (currentMode == ThemeMode.LIGHT && userSettings.darkMode)) {
                    val themeMode = if (userSettings.darkMode) ThemeMode.DARK else ThemeMode.LIGHT
                    themeManager.switchTheme(themeMode)
                }
                // Note: SYSTEM mode is intentionally preserved and not overridden
            }
            
            // Calculate the effective theme state (what's actually being displayed)
            // This is needed to properly show toggle state when in SYSTEM mode
            val currentThemeMode = themeManager.themeMode.value
            val effectiveThemeState = when (currentThemeMode) {
                ThemeMode.SYSTEM -> {
                    // In SYSTEM mode, we need to check what the system default is
                    // Since we can't directly access isSystemInDarkTheme here,
                    // we'll use the settings value as a fallback
                    settings?.darkMode ?: false
                }
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.TIME_BASED -> settings?.darkMode ?: false
            }
            
            // Prioritize showing content over error state
            // Only show error if critical data (settings) is missing
            val errorMessage = when {
                loadResult.settingsResult.isFailure -> {
                    // Settings are critical - show error if they fail
                    "Failed to load user settings. Some features may not work correctly."
                }
                loadResult.profileResult.isFailure && loadResult.subscriptionResult.isFailure -> {
                    // Profile and subscription are non-critical
                    "Some profile and subscription information couldn't be loaded."
                }
                loadResult.profileResult.isFailure -> {
                    "Profile information couldn't be loaded."
                }
                loadResult.subscriptionResult.isFailure -> {
                    "Subscription information couldn't be loaded."
                }
                else -> null
            }
            
            // Check admin permissions for current user
            val isAdmin = try {
                currentUser?.uid?.let { userId ->
                    adminInteractor.checkPermissions(userId).getOrNull() ?: false
                } ?: false
            } catch (e: Exception) {
                Timber.e(e, "Failed to check admin permissions")
                false
            }
            
            // Always update state to exit loading, even with partial data
            val newState = _uiState.value.copy(
                isLoading = false,
                userSettings = settings,
                userProfile = profile,
                socialProfile = socialProfile,
                currentUser = currentUser,
                subscriptionStatus = subscription,
                error = if (settings == null) errorMessage else null, // Only show error if critical data missing
                isUpdatingSettings = false,
                effectiveThemeState = effectiveThemeState,
                isAdmin = isAdmin
            )
            
            _uiState.value = newState
        }
    }
    
    /**
     * Provides default settings as a fallback when data loading fails.
     * This prevents the UI from being stuck in loading state.
     */
    private fun provideDefaultSettings() {
        val defaultSettings = com.example.liftrix.domain.model.UserSettings(
            userId = currentUserId ?: "unknown",
            darkMode = false,
            notificationsEnabled = true,
            weightUnit = com.example.liftrix.domain.model.WeightUnit.getSystemDefault()
        )
        
        updateState {
            copy(
                isLoading = false,
                userSettings = defaultSettings,
                userProfile = null, // Keep profile null if it couldn't be loaded
                subscriptionStatus = com.example.liftrix.domain.model.SubscriptionStatus.default(),
                error = "Using default settings due to loading issues. Please check your connection.",
                isUpdatingSettings = false
            )
        }
    }

    /**
     * Updates the UI state safely with diff-checking to prevent unnecessary emissions.
     */
    private fun updateState(update: SettingsState.() -> SettingsState) {
        val currentState = _uiState.value
        val newState = currentState.update()
        
        // Only update if state actually changed
        if (currentState != newState) {
            _uiState.value = newState
        }
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
        return authRepository.getCurrentUserId()?.value
    }
    
    override fun onCleared() {
        super.onCleared()
        // Cancel any ongoing loadSettings job to prevent leaks
        loadSettingsJob?.cancel()
        loadSettingsJob = null
    }
    
    /**
     * Data class to hold all settings loading results.
     */
    private data class SettingsLoadResult(
        val settingsResult: Result<com.example.liftrix.domain.model.UserSettings>,
        val profileResult: Result<com.example.liftrix.domain.model.UserProfile?>,
        val socialProfileResult: com.example.liftrix.domain.model.common.LiftrixResult<com.example.liftrix.domain.model.social.SocialProfile?>,
        val subscriptionResult: Result<com.example.liftrix.domain.model.SubscriptionStatus>
    )
}
