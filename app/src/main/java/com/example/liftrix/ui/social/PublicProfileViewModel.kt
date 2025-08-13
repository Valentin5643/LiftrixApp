package com.example.liftrix.ui.social


import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.PublicUserProfile
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.usecase.social.GetPublicProfileUseCase
import com.example.liftrix.domain.usecase.social.GetPublicProfileRequest
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for public profile display with connection management
 * 
 * Manages profile loading, connection status updates, and user interactions
 * with privacy-aware data handling based on connection status and user settings.
 */
@HiltViewModel
class PublicProfileViewModel @Inject constructor(
    private val getPublicProfileUseCase: GetPublicProfileUseCase,
    errorHandler: ErrorHandler
) : BaseViewModel<PublicProfileUiState, PublicProfileEvent>(errorHandler) {

    override val _uiState = MutableStateFlow(
        PublicProfileUiState(
            profile = null,
            isLoading = false,
            error = null,
            isConnectionLoading = false
        )
    )

    override fun handleEvent(event: PublicProfileEvent) {
        when (event) {
            is PublicProfileEvent.LoadProfile -> {
                loadProfile(event.userId)
            }
            is PublicProfileEvent.RetryLoad -> {
                retryLoadProfile()
            }
            is PublicProfileEvent.ToggleConnection -> {
                toggleConnection()
            }
            is PublicProfileEvent.RefreshProfile -> {
                refreshProfile()
            }
        }
    }

    /**
     * Loads the public profile for the specified user
     */
    private fun loadProfile(userId: String) {
        if (_uiState.value.profile?.userId == userId && _uiState.value.error == null) {
            // Profile already loaded for this user and no error state
            return
        }

        updateState { currentState ->
            currentState.copy(
                isLoading = true,
                error = null,
                profile = null
            )
        }

        executeUseCase(
            useCase = {
                getPublicProfileUseCase(
                    GetPublicProfileRequest(
                        profileUserId = userId,
                        trackView = true
                    )
                )
            },
            onSuccess = { result ->
                updateState { currentState ->
                    currentState.copy(
                        profile = result.profile,
                        isLoading = false,
                        error = null
                    )
                }
                
                Timber.d("Profile loaded successfully for user: $userId")
            },
            onError = { error ->
                updateState { currentState ->
                    currentState.copy(
                        isLoading = false,
                        error = error,
                        profile = null
                    )
                }
                
                Timber.e("Failed to load profile for user: $userId - ${error.message}")
            },
            showLoading = false // We handle loading state manually
        )
    }

    /**
     * Retries loading the profile after an error
     */
    private fun retryLoadProfile() {
        val currentProfile = _uiState.value.profile
        if (currentProfile != null) {
            loadProfile(currentProfile.userId)
        } else {
            Timber.w("Cannot retry profile load - no previous profile data")
        }
    }

    /**
     * Refreshes the current profile data
     */
    private fun refreshProfile() {
        val currentProfile = _uiState.value.profile
        if (currentProfile != null) {
            // Set refreshing state but keep existing profile
            updateState { currentState ->
                currentState.copy(
                    isLoading = true,
                    error = null
                )
            }
            
            loadProfile(currentProfile.userId)
        }
    }

    /**
     * Toggles connection status with the user
     */
    private fun toggleConnection() {
        val currentProfile = _uiState.value.profile
        if (currentProfile == null) {
            Timber.w("Cannot toggle connection - no profile loaded")
            return
        }

        updateState { currentState ->
            currentState.copy(isConnectionLoading = true)
        }

        viewModelScope.launch {
            try {
                // TODO: Implement connection toggle use case
                // For now, we'll simulate the action
                
                val newConnectionStatus = when (currentProfile.connectionStatus) {
                    com.example.liftrix.domain.model.social.ConnectionStatus.NONE -> 
                        com.example.liftrix.domain.model.social.ConnectionStatus.PENDING_SENT
                    com.example.liftrix.domain.model.social.ConnectionStatus.PENDING_SENT -> 
                        com.example.liftrix.domain.model.social.ConnectionStatus.NONE
                    com.example.liftrix.domain.model.social.ConnectionStatus.PENDING_RECEIVED -> 
                        com.example.liftrix.domain.model.social.ConnectionStatus.CONNECTED
                    com.example.liftrix.domain.model.social.ConnectionStatus.CONNECTED -> 
                        com.example.liftrix.domain.model.social.ConnectionStatus.NONE
                    com.example.liftrix.domain.model.social.ConnectionStatus.MUTUAL_FOLLOW -> 
                        com.example.liftrix.domain.model.social.ConnectionStatus.NONE
                    com.example.liftrix.domain.model.social.ConnectionStatus.GYM_BUDDY -> 
                        com.example.liftrix.domain.model.social.ConnectionStatus.NONE
                    com.example.liftrix.domain.model.social.ConnectionStatus.BLOCKED -> 
                        com.example.liftrix.domain.model.social.ConnectionStatus.BLOCKED
                }

                updateState { currentState ->
                    currentState.copy(
                        profile = currentState.profile?.copy(
                            connectionStatus = newConnectionStatus
                        ),
                        isConnectionLoading = false
                    )
                }

                Timber.d("Connection status updated to: $newConnectionStatus")
                
            } catch (exception: Exception) {
                val error = LiftrixError.NetworkError(
                    errorMessage = "Failed to update connection status"
                )
                
                handleError(error)
                
                updateState { currentState ->
                    currentState.copy(isConnectionLoading = false)
                }
                
                Timber.e(exception, "Failed to toggle connection")
            }
        }
    }

    override fun setLoadingState() {
        updateState { currentState ->
            currentState.copy(isLoading = true)
        }
    }

    override fun updateErrorState(error: LiftrixError) {
        updateState { currentState ->
            currentState.copy(
                error = error,
                isLoading = false,
                isConnectionLoading = false
            )
        }
    }
}

/**
 * UI state for public profile screen
 */
data class PublicProfileUiState(
    val profile: PublicUserProfile?,
    val isLoading: Boolean,
    val error: LiftrixError?,
    val isConnectionLoading: Boolean
) {
    
    /**
     * Whether the profile can be displayed
     */
    val canShowProfile: Boolean
        get() = profile != null && error == null
    
    /**
     * Whether we're in a loading state
     */
    val isLoadingState: Boolean
        get() = isLoading && profile == null
    
    /**
     * Whether connection actions are available
     */
    val canPerformConnectionActions: Boolean
        get() = profile != null && !isConnectionLoading
}

/**
 * Events for public profile screen
 */
sealed class PublicProfileEvent : ViewModelEvent {
    
    /**
     * Load profile for the specified user
     */
    data class LoadProfile(val userId: String) : PublicProfileEvent()
    
    /**
     * Retry loading profile after error
     */
    object RetryLoad : PublicProfileEvent()
    
    /**
     * Toggle connection status (connect/disconnect/accept)
     */
    object ToggleConnection : PublicProfileEvent()
    
    /**
     * Refresh profile data
     */
    object RefreshProfile : PublicProfileEvent()
}