package com.example.liftrix.ui.social


import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.PublicUserProfile
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.usecase.social.GetPublicProfileUseCase
import com.example.liftrix.domain.usecase.social.GetPublicProfileRequest
import com.example.liftrix.domain.usecase.social.FollowUserUseCase
import com.example.liftrix.domain.usecase.social.FollowAction
import com.example.liftrix.domain.model.social.FollowStatus
import com.example.liftrix.domain.model.social.ConnectionStatus
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
    private val followUserUseCase: FollowUserUseCase,
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
                // Determine the follow action based on current connection status
                val followAction = when (currentProfile.connectionStatus) {
                    ConnectionStatus.NONE -> FollowAction.FOLLOW
                    ConnectionStatus.PENDING_SENT -> FollowAction.CANCEL
                    ConnectionStatus.PENDING_RECEIVED -> FollowAction.ACCEPT
                    ConnectionStatus.CONNECTED -> FollowAction.UNFOLLOW
                    ConnectionStatus.MUTUAL_FOLLOW -> FollowAction.UNFOLLOW
                    ConnectionStatus.GYM_BUDDY -> FollowAction.UNFOLLOW
                    ConnectionStatus.BLOCKED -> {
                        // Cannot toggle blocked status from this screen
                        updateState { currentState ->
                            currentState.copy(isConnectionLoading = false)
                        }
                        return@launch
                    }
                }

                // Execute the follow action
                val result = followUserUseCase(
                    targetUserId = currentProfile.userId,
                    action = followAction,
                    context = "PUBLIC_PROFILE"
                )

                result.fold(
                    onSuccess = { followStatus ->
                        // Convert FollowStatus to ConnectionStatus
                        val newConnectionStatus = mapFollowStatusToConnectionStatus(followStatus)

                        updateState { currentState ->
                            currentState.copy(
                                profile = currentState.profile?.copy(
                                    connectionStatus = newConnectionStatus
                                ),
                                isConnectionLoading = false
                            )
                        }

                        Timber.d("Connection status updated to: $newConnectionStatus")
                    },
                    onFailure = { throwable ->
                        val error = LiftrixError.BusinessLogicError(
                            code = "CONNECTION_UPDATE_FAILED",
                            errorMessage = "Failed to update connection: ${throwable.message}",
                            analyticsContext = mapOf(
                                "operation" to "TOGGLE_CONNECTION",
                                "target_user_id" to currentProfile.userId
                            )
                        )
                        handleError(error)
                        
                        updateState { currentState ->
                            currentState.copy(isConnectionLoading = false)
                        }
                        
                        Timber.e(error, "Failed to toggle connection")
                    }
                )
                
            } catch (exception: Exception) {
                val error = LiftrixError.NetworkError(
                    errorMessage = "Failed to update connection status: ${exception.message}"
                )
                
                handleError(error)
                
                updateState { currentState ->
                    currentState.copy(isConnectionLoading = false)
                }
                
                Timber.e(exception, "Failed to toggle connection")
            }
        }
    }

    /**
     * Maps FollowStatus to ConnectionStatus for UI display
     */
    private fun mapFollowStatusToConnectionStatus(followStatus: FollowStatus): ConnectionStatus {
        return when (followStatus) {
            FollowStatus.NONE -> ConnectionStatus.NONE
            FollowStatus.PENDING_SENT -> ConnectionStatus.PENDING_SENT
            FollowStatus.PENDING_RECEIVED -> ConnectionStatus.PENDING_RECEIVED
            FollowStatus.FOLLOWING -> ConnectionStatus.CONNECTED
            // Note: MUTUAL_FOLLOWING doesn't exist in FollowStatus enum
            // Using FOLLOWING -> CONNECTED mapping as the primary status
            FollowStatus.BLOCKED -> ConnectionStatus.BLOCKED
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