package com.example.liftrix.ui.settings

import com.example.liftrix.domain.model.social.SocialPrivacySettings
import com.example.liftrix.domain.model.social.ProfileVisibility
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.repository.social.SocialPrivacySettingsRepository
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.domain.usecase.social.SocialProfileCommandUseCase
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
import com.example.liftrix.domain.model.error.LiftrixError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for privacy settings screen managing social privacy preferences.
 *
 * Handles:
 * - Loading and displaying current privacy settings
 * - Updating privacy settings with immediate effect
 * - Master social toggle with confirmation dialogs
 * - Account management actions (disable social, delete data)
 * - Real-time updates when settings change
 *
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 */
@HiltViewModel
class PrivacySettingsViewModel @Inject constructor(
    private val privacySettingsRepository: SocialPrivacySettingsRepository,
    private val socialProfileCommandUseCase: SocialProfileCommandUseCase,
    private val authQueryUseCase: AuthQueryUseCase
) : ModernBaseViewModel<PrivacySettingsUiState>(
    initialState = PrivacySettingsUiState(
        privacySettingsState = UiState.Loading,
        isLoading = false,
        isUpdatingSettings = false,
        isDeletingData = false,
        successMessage = null,
        errorMessage = null,
        showDisableSocialConfirmation = false,
        showDeleteDataConfirmation = false
    )
) {

    init {
        loadPrivacySettings()
        observePrivacySettingsChanges()
    }

    fun handleEvent(event: PrivacySettingsEvent) {
        when (event) {
            is PrivacySettingsEvent.LoadPrivacySettings -> loadPrivacySettings()
            is PrivacySettingsEvent.CreateDefaultSettings -> createDefaultSettings()
            is PrivacySettingsEvent.ToggleSocialEnabled -> toggleSocialEnabled(event.enabled)
            is PrivacySettingsEvent.UpdateProfileVisibility -> updateProfileVisibility(event.visibility)
            is PrivacySettingsEvent.ToggleAllowFollowRequests -> toggleAllowFollowRequests(event.enabled)
            is PrivacySettingsEvent.ToggleWorkoutSharing -> toggleWorkoutSharing(event.enabled)
            is PrivacySettingsEvent.ToggleShowAchievements -> toggleShowAchievements(event.enabled)
            is PrivacySettingsEvent.ToggleShowWorkoutStats -> toggleShowWorkoutStats(event.enabled)
            is PrivacySettingsEvent.ToggleShowWorkoutStreak -> toggleShowWorkoutStreak(event.enabled)
            is PrivacySettingsEvent.ToggleHideFromSuggestions -> toggleHideFromSuggestions(event.hide)
            is PrivacySettingsEvent.ToggleHideFromSearch -> toggleHideFromSearch(event.hide)
            is PrivacySettingsEvent.ToggleGymBuddies -> toggleGymBuddies(event.enabled)
            is PrivacySettingsEvent.NavigateToBlockedUsers -> { /* Navigation handled by parent */ }
            is PrivacySettingsEvent.ShowDisableSocialConfirmation -> showDisableSocialConfirmation()
            is PrivacySettingsEvent.DismissDisableSocialConfirmation -> dismissDisableSocialConfirmation()
            is PrivacySettingsEvent.ConfirmDisableSocial -> confirmDisableSocial()
            is PrivacySettingsEvent.ShowDeleteDataConfirmation -> showDeleteDataConfirmation()
            is PrivacySettingsEvent.DismissDeleteDataConfirmation -> dismissDeleteDataConfirmation()
            is PrivacySettingsEvent.ConfirmDeleteData -> confirmDeleteData()
            is PrivacySettingsEvent.ClearMessage -> clearMessages()
        }
    }

    private fun loadPrivacySettings() {
        viewModelScope.launch {
            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it.value },
                    onFailure = { throw IllegalStateException("User not authenticated") }
                )
                val result = privacySettingsRepository.getPrivacySettings(userId)
                result.fold(
                    onSuccess = { settings ->
                        val newState: UiState<SocialPrivacySettings> = if (settings != null) {
                            UiState.Success(settings)
                        } else {
                            UiState.Empty()
                        }
                        updateState {
                            it.copy(privacySettingsState = newState)
                        }
                    },
                    onFailure = { throwable ->
                        val liftrixError = if (throwable is LiftrixError) {
                            throwable
                        } else {
                            LiftrixError.UnknownError(
                                errorMessage = throwable.message ?: "Failed to load privacy settings"
                            )
                        }
                        val errorState: UiState<SocialPrivacySettings> = UiState.Error(liftrixError)
                        updateState {
                            it.copy(
                                privacySettingsState = errorState,
                                isLoading = false,
                                errorMessage = liftrixError.message
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                logError(e, "loadPrivacySettings")
                val errorState: UiState<SocialPrivacySettings> = UiState.Error(
                    LiftrixError.UnknownError(
                        errorMessage = e.message ?: "Failed to load privacy settings",
                        analyticsContext = mapOf("operation" to "LOAD_PRIVACY_SETTINGS")
                    )
                )
                updateState {
                    it.copy(
                        privacySettingsState = errorState,
                        isLoading = false,
                        errorMessage = e.message
                    )
                }
            }
        }
    }

    private fun observePrivacySettingsChanges() {
        viewModelScope.launch {
            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it.value },
                    onFailure = { throw IllegalStateException("User not authenticated") }
                )
                privacySettingsRepository.observePrivacySettings(userId)
                    .onEach { settings ->
                        val newState: UiState<SocialPrivacySettings> = if (settings != null) {
                            UiState.Success(settings)
                        } else {
                            UiState.Empty()
                        }
                        updateState {
                            it.copy(privacySettingsState = newState)
                        }
                    }
                    .launchIn(viewModelScope)
            } catch (e: Exception) {
                logError(e, "observePrivacySettingsChanges")
            }
        }
    }

    private fun createDefaultSettings() {
        viewModelScope.launch {
            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it.value },
                    onFailure = { throw IllegalStateException("User not authenticated") }
                )
                val result = privacySettingsRepository.createPrivacySettings(userId)
                result.fold(
                    onSuccess = { settings ->
                        val newState: UiState<SocialPrivacySettings> = UiState.Success(settings)
                        updateState {
                            it.copy(
                                privacySettingsState = newState,
                                successMessage = "Privacy settings created successfully"
                            )
                        }
                    },
                    onFailure = { throwable ->
                        val errorMessage = if (throwable is LiftrixError) {
                            throwable.message
                        } else {
                            throwable.message ?: "Failed to create default settings"
                        }
                        updateState {
                            it.copy(
                                isLoading = false,
                                errorMessage = errorMessage
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                logError(e, "createDefaultSettings")
                updateState {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message
                    )
                }
            }
        }
    }

    private fun toggleSocialEnabled(enabled: Boolean) {
        if (!enabled) {
            // Show confirmation dialog before disabling social features
            showDisableSocialConfirmation()
            return
        }

        updateSetting { it.copy(socialEnabled = enabled) }
    }

    private fun updateProfileVisibility(visibility: ProfileVisibility) {
        updateSetting { it.copy(profileVisibility = visibility) }
    }

    private fun toggleAllowFollowRequests(enabled: Boolean) {
        updateSetting { it.copy(allowFollowRequests = enabled) }
    }

    private fun toggleWorkoutSharing(enabled: Boolean) {
        updateSetting { it.copy(workoutSharingEnabled = enabled) }
    }

    private fun toggleShowAchievements(enabled: Boolean) {
        updateSetting { it.copy(showAchievements = enabled) }
    }

    private fun toggleShowWorkoutStats(enabled: Boolean) {
        updateSetting { it.copy(showWorkoutStats = enabled) }
    }

    private fun toggleShowWorkoutStreak(enabled: Boolean) {
        updateSetting { it.copy(showWorkoutStreak = enabled) }
    }

    private fun toggleHideFromSuggestions(hide: Boolean) {
        updateSetting { it.copy(hideFromSuggestions = hide) }
    }

    private fun toggleHideFromSearch(hide: Boolean) {
        updateSetting { it.copy(hideFromSearch = hide) }
    }

    private fun toggleGymBuddies(enabled: Boolean) {
        updateSetting { it.copy(gymBuddiesEnabled = enabled) }
    }

    private fun updateSetting(transform: (SocialPrivacySettings) -> SocialPrivacySettings) {
        val currentState = uiState.value.privacySettingsState
        if (currentState !is UiState.Success) {
            Timber.w("Cannot update privacy settings - current state is not success")
            return
        }

        val currentSettings = currentState.data
        val updatedSettings = transform(currentSettings).copy(
            updatedAt = System.currentTimeMillis()
        )

        updateState { it.copy(isUpdatingSettings = true) }

        viewModelScope.launch {
            try {
                socialProfileCommandUseCase.updatePrivacySettings(updatedSettings)
                updateState {
                    it.copy(
                        isUpdatingSettings = false,
                        successMessage = "Settings updated successfully"
                    )
                }
            } catch (e: Exception) {
                logError(e, "updateSetting")
                updateState {
                    it.copy(
                        isUpdatingSettings = false,
                        errorMessage = "Failed to update settings: ${e.message}"
                    )
                }
            }
        }
    }

    private fun showDisableSocialConfirmation() {
        updateState { it.copy(showDisableSocialConfirmation = true) }
    }

    private fun dismissDisableSocialConfirmation() {
        updateState { it.copy(showDisableSocialConfirmation = false) }
    }

    private fun confirmDisableSocial() {
        updateState { it.copy(showDisableSocialConfirmation = false) }
        updateSetting { it.copy(socialEnabled = false) }
    }

    private fun showDeleteDataConfirmation() {
        updateState { it.copy(showDeleteDataConfirmation = true) }
    }

    private fun dismissDeleteDataConfirmation() {
        updateState { it.copy(showDeleteDataConfirmation = false) }
    }

    private fun confirmDeleteData() {
        updateState {
            it.copy(
                showDeleteDataConfirmation = false,
                isDeletingData = true
            )
        }

        viewModelScope.launch {
            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it.value },
                    onFailure = { throw IllegalStateException("User not authenticated") }
                )
                privacySettingsRepository.deletePrivacySettings(userId)
                val newState: UiState<SocialPrivacySettings> = UiState.Empty()
                updateState {
                    it.copy(
                        isDeletingData = false,
                        privacySettingsState = newState,
                        successMessage = "All social data has been deleted"
                    )
                }
            } catch (e: Exception) {
                logError(e, "confirmDeleteData")
                updateState {
                    it.copy(
                        isDeletingData = false,
                        errorMessage = "Failed to delete social data: ${e.message}"
                    )
                }
            }
        }
    }

    private fun clearMessages() {
        updateState {
            it.copy(
                successMessage = null,
                errorMessage = null
            )
        }
    }
}

/**
 * UI state for privacy settings screen.
 */
data class PrivacySettingsUiState(
    val privacySettingsState: UiState<SocialPrivacySettings>,
    val isLoading: Boolean,
    val isUpdatingSettings: Boolean,
    val isDeletingData: Boolean,
    val successMessage: String?,
    val errorMessage: String?,
    val showDisableSocialConfirmation: Boolean,
    val showDeleteDataConfirmation: Boolean
)

/**
 * Events for privacy settings screen.
 */
sealed class PrivacySettingsEvent : ViewModelEvent {
    data object LoadPrivacySettings : PrivacySettingsEvent()
    data object CreateDefaultSettings : PrivacySettingsEvent()
    data class ToggleSocialEnabled(val enabled: Boolean) : PrivacySettingsEvent()
    data class UpdateProfileVisibility(val visibility: ProfileVisibility) : PrivacySettingsEvent()
    data class ToggleAllowFollowRequests(val enabled: Boolean) : PrivacySettingsEvent()
    data class ToggleWorkoutSharing(val enabled: Boolean) : PrivacySettingsEvent()
    data class ToggleShowAchievements(val enabled: Boolean) : PrivacySettingsEvent()
    data class ToggleShowWorkoutStats(val enabled: Boolean) : PrivacySettingsEvent()
    data class ToggleShowWorkoutStreak(val enabled: Boolean) : PrivacySettingsEvent()
    data class ToggleHideFromSuggestions(val hide: Boolean) : PrivacySettingsEvent()
    data class ToggleHideFromSearch(val hide: Boolean) : PrivacySettingsEvent()
    data class ToggleGymBuddies(val enabled: Boolean) : PrivacySettingsEvent()
    data object NavigateToBlockedUsers : PrivacySettingsEvent()
    data object ShowDisableSocialConfirmation : PrivacySettingsEvent()
    data object DismissDisableSocialConfirmation : PrivacySettingsEvent()
    data object ConfirmDisableSocial : PrivacySettingsEvent()
    data object ShowDeleteDataConfirmation : PrivacySettingsEvent()
    data object DismissDeleteDataConfirmation : PrivacySettingsEvent()
    data object ConfirmDeleteData : PrivacySettingsEvent()
    data object ClearMessage : PrivacySettingsEvent()
}
