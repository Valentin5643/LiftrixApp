package com.example.liftrix.ui.settings

import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.SocialPrivacySettings
import com.example.liftrix.domain.model.social.ProfileVisibility
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.repository.social.SocialPrivacySettingsRepository
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.usecase.social.SocialProfileCommandUseCase
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    private val authQueryUseCase: AuthQueryUseCase,
    errorHandler: ErrorHandler
) : BaseViewModel<PrivacySettingsUiState, PrivacySettingsEvent>(errorHandler) {

    override val _uiState = MutableStateFlow(
        PrivacySettingsUiState(
            privacySettingsState = UiState.Loading,
            isLoading = false,
            isUpdatingSettings = false,
            isDeletingData = false,
            successMessage = null,
            errorMessage = null,
            showDisableSocialConfirmation = false,
            showDeleteDataConfirmation = false
        )
    )

    init {
        loadPrivacySettings()
        observePrivacySettingsChanges()
    }

    override fun handleEvent(event: PrivacySettingsEvent) {
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

    override fun setLoadingState() {
        updateState { it.copy(isLoading = true) }
    }

    override fun updateErrorState(error: LiftrixError) {
        updateState { 
            it.copy(
                isLoading = false,
                isUpdatingSettings = false,
                isDeletingData = false,
                errorMessage = error.message
            )
        }
    }

    private fun loadPrivacySettings() {
        executeUseCase(
            useCase = {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { throw IllegalStateException("User not authenticated") }
                )
                privacySettingsRepository.getPrivacySettings(userId)
            },
            onSuccess = { settings ->
                updateState { 
                    it.copy(
                        privacySettingsState = if (settings != null) {
                            UiState.Success(settings)
                        } else {
                            UiState.Empty()
                        }
                    )
                }
            },
            onError = { error ->
                updateState { 
                    it.copy(privacySettingsState = UiState.Error(error))
                }
                handleError(error)
            }
        )
    }

    private fun observePrivacySettingsChanges() {
        executeUseCase(
            useCase = {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { throw IllegalStateException("User not authenticated") }
                )
                privacySettingsRepository.observePrivacySettings(userId)
                    .onEach { settings ->
                        updateState { 
                            it.copy(
                                privacySettingsState = if (settings != null) {
                                    UiState.Success(settings)
                                } else {
                                    UiState.Empty()
                                }
                            )
                        }
                    }
                    .launchIn(viewModelScope)
                Result.success(Unit)
            },
            showLoading = false
        )
    }

    private fun createDefaultSettings() {
        executeUseCase(
            useCase = {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { throw IllegalStateException("User not authenticated") }
                )
                privacySettingsRepository.createPrivacySettings(userId)
            },
            onSuccess = { settings ->
                updateState { 
                    it.copy(
                        privacySettingsState = UiState.Success(settings),
                        successMessage = "Privacy settings created successfully"
                    )
                }
            },
            onError = { error ->
                handleError(error)
            }
        )
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
        val currentState = _uiState.value.privacySettingsState
        if (currentState !is UiState.Success) {
            Timber.w("Cannot update privacy settings - current state is not success")
            return
        }

        val currentSettings = currentState.data
        val updatedSettings = transform(currentSettings).copy(
            updatedAt = System.currentTimeMillis()
        )

        updateState { it.copy(isUpdatingSettings = true) }

        executeUseCase(
            useCase = { socialProfileCommandUseCase.updatePrivacySettings(updatedSettings) },
            onSuccess = { _ ->
                updateState { 
                    it.copy(
                        isUpdatingSettings = false,
                        successMessage = "Settings updated successfully"
                    )
                }
            },
            onError = { error ->
                updateState { 
                    it.copy(
                        isUpdatingSettings = false,
                        errorMessage = "Failed to update settings: ${error.message}"
                    )
                }
                handleError(error)
            },
            showLoading = false
        )
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

        executeUseCase(
            useCase = {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { throw IllegalStateException("User not authenticated") }
                )
                privacySettingsRepository.deletePrivacySettings(userId)
            },
            onSuccess = { _ ->
                updateState { 
                    it.copy(
                        isDeletingData = false,
                        privacySettingsState = UiState.Empty(),
                        successMessage = "All social data has been deleted"
                    )
                }
            },
            onError = { error ->
                updateState { 
                    it.copy(
                        isDeletingData = false,
                        errorMessage = "Failed to delete social data: ${error.message}"
                    )
                }
                handleError(error)
            },
            showLoading = false
        )
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