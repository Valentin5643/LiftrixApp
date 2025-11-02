package com.example.liftrix.ui.social

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.PublicUserProfile
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.usecase.social.SocialProfileQueryUseCase
import com.example.liftrix.domain.usecase.social.GetPublicProfileRequest
import com.example.liftrix.domain.usecase.social.SocialRelationshipUseCase
import com.example.liftrix.domain.usecase.social.FollowAction
import com.example.liftrix.domain.model.social.ReportReason
import com.example.liftrix.domain.model.social.FollowStatus
import com.example.liftrix.domain.model.social.ConnectionStatus
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.domain.repository.social.FeedRepository
import com.example.liftrix.domain.repository.social.EngagementRepository
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
    private val socialProfileQueryUseCase: SocialProfileQueryUseCase,
    private val socialRelationshipUseCase: SocialRelationshipUseCase,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val feedRepository: FeedRepository,
    private val engagementRepository: EngagementRepository,
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

    init {
        loadCurrentUserId()
    }

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
            is PublicProfileEvent.BlockUser -> {
                blockUser()
            }
            is PublicProfileEvent.ReportProfile -> {
                reportProfile()
            }
            is PublicProfileEvent.ToggleLike -> {
                toggleLike(event.postId)
            }
            is PublicProfileEvent.ToggleSave -> {
                toggleSave(event.postId)
            }
            is PublicProfileEvent.OpenPostDetail -> {
                // This will be handled by navigation in the UI
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
                socialProfileQueryUseCase.getPublicProfile(
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
                
                // Load user's workout posts
                loadUserPosts(userId)
                
                // Load engagement state
                loadEngagementState()
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
     * Loads workout posts for the user's profile
     */
    private fun loadUserPosts(userId: String) {
        viewModelScope.launch {
            // Get current user ID, or use empty string for anonymous viewing
            val currentUserId = _uiState.value.currentUserId ?: getCurrentUserIdUseCase() ?: ""
            
            val postsFlow = feedRepository.getUserPosts(
                userId = userId,
                viewerId = currentUserId,
                pageSize = 20
            ).cachedIn(viewModelScope)
            
            updateState { currentState ->
                currentState.copy(workoutPosts = postsFlow)
            }
            
            Timber.d("Loading posts for user $userId viewed by $currentUserId")
        }
    }
    
    /**
     * Loads engagement state (liked and saved posts)
     */
    private fun loadEngagementState() {
        // Engagement state will be checked on demand for each post
        // This is more efficient than pre-loading all liked/saved posts
        Timber.d("Engagement state will be checked on demand for each post")
    }
    
    /**
     * Toggles like on a workout post
     */
    private fun toggleLike(postId: String) {
        viewModelScope.launch {
            val currentUserId = _uiState.value.currentUserId ?: return@launch
            val isLiked = _uiState.value.likedPosts.contains(postId)
            
            // Optimistic update
            updateState { currentState ->
                currentState.copy(
                    likedPosts = if (isLiked) {
                        currentState.likedPosts - postId
                    } else {
                        currentState.likedPosts + postId
                    }
                )
            }
            
            // Make API call
            engagementRepository.toggleLike(postId, currentUserId).fold(
                onSuccess = { /* Already updated optimistically */ },
                onFailure = { error ->
                    // Revert on failure
                    updateState { currentState ->
                        currentState.copy(
                            likedPosts = if (isLiked) {
                                currentState.likedPosts + postId
                            } else {
                                currentState.likedPosts - postId
                            }
                        )
                    }
                    Timber.e("Failed to toggle like: ${error.message}")
                }
            )
        }
    }
    
    /**
     * Toggles save on a workout post
     */
    private fun toggleSave(postId: String) {
        viewModelScope.launch {
            val currentUserId = _uiState.value.currentUserId ?: return@launch
            val isSaved = _uiState.value.savedPosts.contains(postId)
            
            // Optimistic update
            updateState { currentState ->
                currentState.copy(
                    savedPosts = if (isSaved) {
                        currentState.savedPosts - postId
                    } else {
                        currentState.savedPosts + postId
                    }
                )
            }
            
            // Make API call
            engagementRepository.toggleSave(postId, currentUserId).fold(
                onSuccess = { /* Already updated optimistically */ },
                onFailure = { error ->
                    // Revert on failure
                    updateState { currentState ->
                        currentState.copy(
                            savedPosts = if (isSaved) {
                                currentState.savedPosts + postId
                            } else {
                                currentState.savedPosts - postId
                            }
                        )
                    }
                    Timber.e("Failed to toggle save: ${error.message}")
                }
            )
        }
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
                    ConnectionStatus.SELF -> {
                        // Cannot follow yourself
                        updateState { currentState ->
                            currentState.copy(isConnectionLoading = false)
                        }
                        return@launch
                    }
                }

                // Execute the follow action
                val result = socialRelationshipUseCase.followAction(
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
            FollowStatus.MUTUAL_FOLLOW -> ConnectionStatus.MUTUAL_FOLLOW
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

    /**
     * Load current user ID
     */
    private fun loadCurrentUserId() {
        viewModelScope.launch {
            val currentUserId = getCurrentUserIdUseCase()
            updateState { it.copy(currentUserId = currentUserId) }
        }
    }

    /**
     * Block the user
     */
    private fun blockUser() {
        val profile = _uiState.value.profile ?: return
        
        viewModelScope.launch {
            try {
                updateState { it.copy(isConnectionLoading = true) }

                val result = socialRelationshipUseCase.blockUser(
                    targetUserId = profile.userId,
                    shouldBlock = true
                )
                
                result.fold(
                    onSuccess = {
                        // Update the connection status to blocked
                        updateState { currentState ->
                            currentState.copy(
                                profile = currentState.profile?.copy(
                                    connectionStatus = ConnectionStatus.BLOCKED
                                ),
                                isConnectionLoading = false
                            )
                        }
                        Timber.d("User blocked successfully: ${profile.userId}")
                    },
                    onFailure = { throwable ->
                        val error = throwable as LiftrixError
                        handleError(error)
                        updateState { it.copy(isConnectionLoading = false) }
                        Timber.e("Failed to block user: ${error.message}")
                    }
                )
            } catch (exception: Exception) {
                val error = LiftrixError.NetworkError(
                    errorMessage = "Failed to block user: ${exception.message}"
                )
                handleError(error)
                updateState { it.copy(isConnectionLoading = false) }
                Timber.e(exception, "Failed to block user")
            }
        }
    }

    /**
     * Report the profile
     */
    private fun reportProfile() {
        val profile = _uiState.value.profile ?: return
        
        viewModelScope.launch {
            try {
                updateState { it.copy(isConnectionLoading = true) }
                
                // For now, we'll use INAPPROPRIATE_CONTENT as the default reason
                // In a real app, you'd show a dialog to let the user select the reason
                val result = socialRelationshipUseCase.reportUser(
                    targetUserId = profile.userId,
                    reason = ReportReason.INAPPROPRIATE_CONTENT,
                    description = "Reported from profile view"
                )
                
                result.fold(
                    onSuccess = {
                        updateState { it.copy(isConnectionLoading = false) }
                        Timber.d("Profile reported successfully: ${profile.userId}")
                        // You might want to show a success message to the user
                    },
                    onFailure = { throwable ->
                        val error = throwable as LiftrixError
                        handleError(error)
                        updateState { it.copy(isConnectionLoading = false) }
                        Timber.e("Failed to report profile: ${error.message}")
                    }
                )
            } catch (exception: Exception) {
                val error = LiftrixError.NetworkError(
                    errorMessage = "Failed to report profile: ${exception.message}"
                )
                handleError(error)
                updateState { it.copy(isConnectionLoading = false) }
                Timber.e(exception, "Failed to report profile")
            }
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
    val isConnectionLoading: Boolean,
    val currentUserId: String? = null,
    val workoutPosts: Flow<PagingData<WorkoutPost>> = flowOf(PagingData.empty()),
    val likedPosts: Set<String> = emptySet(),
    val savedPosts: Set<String> = emptySet()
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
    
    /**
     * Block the user
     */
    object BlockUser : PublicProfileEvent()
    
    /**
     * Report the profile
     */
    object ReportProfile : PublicProfileEvent()
    
    /**
     * Toggle like on a workout post
     */
    data class ToggleLike(val postId: String) : PublicProfileEvent()
    
    /**
     * Toggle save on a workout post
     */
    data class ToggleSave(val postId: String) : PublicProfileEvent()
    
    /**
     * Navigate to post details
     */
    data class OpenPostDetail(val postId: String) : PublicProfileEvent()
}