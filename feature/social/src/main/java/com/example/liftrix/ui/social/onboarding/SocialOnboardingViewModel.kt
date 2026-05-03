package com.example.liftrix.ui.social.onboarding

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.SocialPrivacySettings
import com.example.liftrix.domain.model.social.ProfileVisibility
import com.example.liftrix.domain.model.social.WorkoutVisibility
import com.example.liftrix.ui.social.onboarding.SocialOnboardingStep
import com.example.liftrix.domain.usecase.social.SocialProfileQueryUseCase
import com.example.liftrix.domain.usecase.social.SocialProfileCommandUseCase
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for social onboarding screen managing multi-step flow state.
 *
 * Handles:
 * - Step navigation and validation
 * - Profile creation with username availability checking
 * - Privacy settings configuration
 * - Form validation and error handling
 *
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SocialOnboardingViewModel @Inject constructor(
    private val socialProfileCommandUseCase: SocialProfileCommandUseCase,
    private val socialProfileQueryUseCase: SocialProfileQueryUseCase
) : ModernBaseViewModel<SocialOnboardingUiState>(
    initialState = SocialOnboardingUiState(
        currentStep = SocialOnboardingStep.PRIVACY_INTRO,
        username = "",
        displayName = "",
        bio = "",
        allowFollowRequests = false,
        workoutSharingEnabled = false,
        gymBuddiesEnabled = false,
        showAchievements = true,
        usernameError = null,
        displayNameError = null,
        isLoading = false,
        isCompleted = false,
        canCreateProfile = false
    )
) {
    init {
        // Debounced username availability checking
        uiState
            .debounce(500) // Wait 500ms after user stops typing
            .distinctUntilChanged { old, new -> old.username == new.username }
            .onEach { state ->
                if (state.username.isNotBlank() && state.username.length >= 3) {
                    checkUsernameAvailability(state.username)
                }
            }
            .launchIn(viewModelScope)
    }

    fun handleEvent(event: SocialOnboardingEvent) {
        when (event) {
            is SocialOnboardingEvent.NavigateNext -> navigateNext()
            is SocialOnboardingEvent.NavigateBack -> navigateBack()
            is SocialOnboardingEvent.UpdateUsername -> updateUsername(event.username)
            is SocialOnboardingEvent.UpdateDisplayName -> updateDisplayName(event.displayName)
            is SocialOnboardingEvent.UpdateBio -> updateBio(event.bio)
            is SocialOnboardingEvent.UpdateAllowFollowRequests -> updateAllowFollowRequests(event.enabled)
            is SocialOnboardingEvent.UpdateWorkoutSharing -> updateWorkoutSharing(event.enabled)
            is SocialOnboardingEvent.UpdateGymBuddies -> updateGymBuddies(event.enabled)
            is SocialOnboardingEvent.UpdateShowAchievements -> updateShowAchievements(event.enabled)
            is SocialOnboardingEvent.CreateProfile -> createProfile()
            is SocialOnboardingEvent.SavePrivacySettings -> savePrivacySettings()
            is SocialOnboardingEvent.CompleteOnboarding -> completeOnboarding()
        }
    }

    fun navigateNext() {
        val currentStep = uiState.value.currentStep
        val nextStep = when (currentStep) {
            SocialOnboardingStep.PRIVACY_INTRO -> SocialOnboardingStep.BENEFITS
            SocialOnboardingStep.BENEFITS -> SocialOnboardingStep.PROFILE_CREATION
            SocialOnboardingStep.PROFILE_CREATION -> SocialOnboardingStep.PRIVACY_SETTINGS
            SocialOnboardingStep.PRIVACY_SETTINGS -> SocialOnboardingStep.COMPLETION
            SocialOnboardingStep.COMPLETION -> return // Already at end
        }

        updateState { it.copy(currentStep = nextStep) }
    }

    fun navigateBack() {
        val currentStep = uiState.value.currentStep
        val previousStep = when (currentStep) {
            SocialOnboardingStep.PRIVACY_INTRO -> return // Already at start
            SocialOnboardingStep.BENEFITS -> SocialOnboardingStep.PRIVACY_INTRO
            SocialOnboardingStep.PROFILE_CREATION -> SocialOnboardingStep.BENEFITS
            SocialOnboardingStep.PRIVACY_SETTINGS -> SocialOnboardingStep.PROFILE_CREATION
            SocialOnboardingStep.COMPLETION -> SocialOnboardingStep.PRIVACY_SETTINGS
        }

        updateState { it.copy(currentStep = previousStep) }
    }

    fun updateUsername(username: String) {
        updateState { state ->
            state.copy(
                username = username,
                usernameError = null,
                canCreateProfile = validateProfileData(
                    username = username,
                    displayName = state.displayName,
                    usernameError = null,
                    displayNameError = state.displayNameError
                )
            )
        }
    }

    fun updateDisplayName(displayName: String) {
        val error = validateDisplayName(displayName)
        updateState { state ->
            state.copy(
                displayName = displayName,
                displayNameError = error,
                canCreateProfile = validateProfileData(
                    username = state.username,
                    displayName = displayName,
                    usernameError = state.usernameError,
                    displayNameError = error
                )
            )
        }
    }

    fun updateBio(bio: String) {
        // Limit bio to 500 characters
        val trimmedBio = if (bio.length > 500) bio.take(500) else bio
        updateState { it.copy(bio = trimmedBio) }
    }

    fun updateAllowFollowRequests(enabled: Boolean) {
        updateState { it.copy(allowFollowRequests = enabled) }
    }

    fun updateWorkoutSharing(enabled: Boolean) {
        updateState { it.copy(workoutSharingEnabled = enabled) }
    }

    fun updateGymBuddies(enabled: Boolean) {
        updateState { it.copy(gymBuddiesEnabled = enabled) }
    }

    fun updateShowAchievements(enabled: Boolean) {
        updateState { it.copy(showAchievements = enabled) }
    }

    private fun checkUsernameAvailability(username: String) {
        viewModelScope.launch {
            val result = socialProfileQueryUseCase.checkUsernameAvailability(username)
            result.fold(
                onSuccess = { isAvailable ->
                    val error = if (!isAvailable) {
                        "Username '$username' is already taken"
                    } else {
                        null
                    }

                    updateState { state ->
                        state.copy(
                            usernameError = error,
                            canCreateProfile = validateProfileData(
                                username = state.username,
                                displayName = state.displayName,
                                usernameError = error,
                                displayNameError = state.displayNameError
                            )
                        )
                    }
                },
                onFailure = { throwable ->
                    Timber.e("Failed to check username availability: ${throwable.message}")
                    // Don't show error to user for availability check failures
                    // They can still attempt to create the profile
                }
            )
        }
    }

    fun createProfile() {
        val state = uiState.value

        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }

            val result = socialProfileCommandUseCase.create(
                username = state.username,
                displayName = state.displayName,
                bio = state.bio.takeIf { it.isNotBlank() }
            )

            updateState { it.copy(isLoading = false) }

            result.fold(
                onSuccess = { profile ->
                    Timber.d("Social profile created successfully")
                    navigateNext()
                },
                onFailure = { throwable ->
                    // Handle specific validation errors
                    when (throwable) {
                        is LiftrixError.ValidationError -> {
                            when (throwable.field) {
                                "username" -> {
                                    updateState { it.copy(usernameError = "Invalid username") }
                                }
                                "displayName" -> {
                                    updateState { it.copy(displayNameError = "Invalid display name") }
                                }
                                else -> {
                                    Timber.e("Profile creation error: ${throwable.message}")
                                }
                            }
                        }
                        is LiftrixError -> {
                            Timber.e("Profile creation error: ${throwable.message}")
                        }
                        else -> {
                            Timber.e("Profile creation error: ${throwable.message}")
                        }
                    }
                }
            )
        }
    }

    fun savePrivacySettings() {
        val state = uiState.value

        val privacySettings = SocialPrivacySettings(
            userId = "", // Will be set by use case
            socialEnabled = true, // User is enabling social features
            profileVisibility = ProfileVisibility.PRIVATE, // Private by default
            allowFollowRequests = state.allowFollowRequests,
            workoutSharingEnabled = state.workoutSharingEnabled,
            gymBuddiesEnabled = state.gymBuddiesEnabled,
            communityParticipation = false, // Disabled by default
            challengeParticipation = false, // Disabled by default
            routineSharingEnabled = false, // Disabled by default
            defaultWorkoutVisibility = WorkoutVisibility.PRIVATE,
            showWorkoutStats = true,
            showAchievements = state.showAchievements,
            showWorkoutStreak = true,
            hideFromSuggestions = true, // Hidden by default until user is comfortable
            hideFromSearch = false,
            notificationSettings = emptyMap(), // Default notification settings
            updatedAt = System.currentTimeMillis()
        )

        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }

            val result = socialProfileCommandUseCase.updatePrivacySettings(privacySettings)
            result.fold(
                onSuccess = {
                    updateState { it.copy(isLoading = false) }
                    Timber.d("Privacy settings saved successfully")
                    navigateNext()
                },
                onFailure = { throwable ->
                    updateState { it.copy(isLoading = false) }
                    val errorMessage = if (throwable is LiftrixError) {
                        throwable.message
                    } else {
                        throwable.message ?: "Unknown error"
                    }
                    Timber.e("Failed to save privacy settings: $errorMessage")
                }
            )
        }
    }

    fun completeOnboarding() {
        updateState { it.copy(isCompleted = true) }
    }

    private fun validateDisplayName(displayName: String): String? {
        return when {
            displayName.isBlank() -> "Display name cannot be empty"
            displayName.length > 50 -> "Display name must be 50 characters or less"
            else -> null
        }
    }

    private fun validateProfileData(
        username: String,
        displayName: String,
        usernameError: String?,
        displayNameError: String?
    ): Boolean {
        return username.length >= 3 &&
                username.length <= 20 &&
                displayName.isNotBlank() &&
                usernameError == null &&
                displayNameError == null
    }
}


/**
 * UI state for social onboarding screen.
 */
data class SocialOnboardingUiState(
    val currentStep: SocialOnboardingStep,
    val username: String,
    val displayName: String,
    val bio: String,
    val allowFollowRequests: Boolean,
    val workoutSharingEnabled: Boolean,
    val gymBuddiesEnabled: Boolean,
    val showAchievements: Boolean,
    val usernameError: String?,
    val displayNameError: String?,
    val isLoading: Boolean,
    val isCompleted: Boolean,
    val canCreateProfile: Boolean
)

/**
 * Events for social onboarding screen.
 */
sealed class SocialOnboardingEvent : ViewModelEvent {
    data object NavigateNext : SocialOnboardingEvent()
    data object NavigateBack : SocialOnboardingEvent()
    data class UpdateUsername(val username: String) : SocialOnboardingEvent()
    data class UpdateDisplayName(val displayName: String) : SocialOnboardingEvent()
    data class UpdateBio(val bio: String) : SocialOnboardingEvent()
    data class UpdateAllowFollowRequests(val enabled: Boolean) : SocialOnboardingEvent()
    data class UpdateWorkoutSharing(val enabled: Boolean) : SocialOnboardingEvent()
    data class UpdateGymBuddies(val enabled: Boolean) : SocialOnboardingEvent()
    data class UpdateShowAchievements(val enabled: Boolean) : SocialOnboardingEvent()
    data object CreateProfile : SocialOnboardingEvent()
    data object SavePrivacySettings : SocialOnboardingEvent()
    data object CompleteOnboarding : SocialOnboardingEvent()
}
