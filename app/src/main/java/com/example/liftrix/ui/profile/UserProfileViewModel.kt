package com.example.liftrix.ui.profile

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.social.PublicUserProfile
import com.example.liftrix.domain.model.social.FollowStatus
import com.example.liftrix.domain.usecase.social.GetPublicProfileUseCase
import com.example.liftrix.domain.usecase.social.GetPublicProfileRequest
import com.example.liftrix.domain.usecase.social.FollowUserUseCase
import com.example.liftrix.domain.usecase.social.FollowAction
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.model.error.LiftrixError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * UserProfileViewModel - Manages state for viewing other users' profiles
 * 
 * Enhanced profile viewing with comprehensive social features:
 * - Privacy-aware profile loading based on follow relationship
 * - Follow/unfollow management with state tracking
 * - Profile tab navigation (workouts, stats, achievements)
 * - Mutual connection detection
 * - Profile sharing capabilities
 * - Block/report functionality
 * - Real-time follow status updates
 * - Error handling with user-friendly messaging
 * 
 * State Management:
 * - UserProfileUiState: Complete profile viewing state
 * - UserProfileEvent: User actions and system events
 * - Reactive flows for real-time UI updates
 * - Follow status synchronization
 * - Privacy filtering based on relationship status
 */
@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val getPublicProfileUseCase: GetPublicProfileUseCase,
    private val followUserUseCase: FollowUserUseCase,
    errorHandler: ErrorHandler
) : BaseViewModel<UserProfileUiState, UserProfileEvent>(
    errorHandler = errorHandler
) {
    
    override val _uiState = MutableStateFlow(UserProfileUiState())
    
    // Current user ID for follow operations
    private val currentUserId = flow {
        try {
            val userId = getCurrentUserIdUseCase()
            emit(userId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get current user ID")
            emit(null)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )
    
    init {
        Timber.d("UserProfileViewModel initialized")
    }
    
    /**
     * Load user profile with privacy filtering
     */
    fun loadUserProfile(userId: String) {
        viewModelScope.launch {
            try {
                updateState { it.copy(isLoading = true, error = null) }
                
                Timber.d("Loading profile for user: $userId")
                
                val result = getPublicProfileUseCase(GetPublicProfileRequest(
                    profileUserId = userId,
                    trackView = true
                ))
                
                if (result.isSuccess) {
                    val profileResult = result.getOrThrow()
                    updateState { 
                        it.copy(
                            isLoading = false,
                            profile = profileResult.profile,
                            canViewDetails = profileResult.canInteract,
                            isOwnProfile = profileResult.isOwnProfile,
                            followStatus = determineFollowStatus(profileResult.profile),
                            mutualConnectionCount = profileResult.profile.mutualConnectionsCount,
                            selectedTab = ProfileTab.WORKOUTS
                        )
                    }
                    Timber.i("Profile loaded successfully for user: $userId")
                } else {
                    val error = result.exceptionOrNull()
                    val liftrixError = LiftrixError.BusinessLogicError(
                        code = "PROFILE_LOAD_FAILED",
                        errorMessage = "Failed to load profile: ${error?.message ?: "Unknown error"}",
                        analyticsContext = mapOf(
                            "operation" to "LOAD_USER_PROFILE",
                            "user_id" to userId
                        )
                    )
                    updateState { 
                        it.copy(
                            isLoading = false,
                            error = liftrixError
                        )
                    }
                    Timber.e(error, "Failed to load profile for user: $userId")
                }
                
            } catch (e: Exception) {
                val liftrixError = LiftrixError.NetworkError(
                    errorMessage = "Unexpected error loading profile: ${e.message}"
                )
                updateState { 
                    it.copy(
                        isLoading = false,
                        error = liftrixError
                    )
                }
                Timber.e(e, "Unexpected error loading profile for user: $userId")
            }
        }
    }
    
    /**
     * Toggle follow status (follow/unfollow/accept/decline)
     */
    fun toggleFollow() {
        val currentState = _uiState.value
        val profile = currentState.profile ?: return
        val followStatus = currentState.followStatus
        
        viewModelScope.launch {
            try {
                updateState { it.copy(isFollowActionLoading = true) }
                
                val action = when (followStatus) {
                    FollowStatus.NONE -> FollowAction.FOLLOW
                    FollowStatus.FOLLOWING -> FollowAction.UNFOLLOW
                    FollowStatus.PENDING_SENT -> FollowAction.CANCEL
                    FollowStatus.PENDING_RECEIVED -> FollowAction.ACCEPT
                    FollowStatus.BLOCKED -> return@launch // Cannot toggle blocked status
                }
                
                Timber.d("Executing follow action: $action for user: ${profile.userId}")
                
                val result = followUserUseCase(
                    targetUserId = profile.userId,
                    action = action,
                    context = "USER_PROFILE_VIEW"
                )
                
                if (result.isSuccess) {
                    val newStatus = result.getOrThrow()
                    updateState { 
                        it.copy(
                            isFollowActionLoading = false,
                            followStatus = newStatus,
                            // Update follow counts if needed
                            profile = it.profile?.copy(
                                followersCount = when (action) {
                                    FollowAction.FOLLOW -> if (newStatus == FollowStatus.FOLLOWING) it.profile.followersCount + 1 else it.profile.followersCount
                                    FollowAction.UNFOLLOW -> it.profile.followersCount - 1
                                    FollowAction.ACCEPT -> it.profile.followersCount + 1
                                    else -> it.profile.followersCount
                                }
                            )
                        )
                    }
                    Timber.i("Follow action successful: $action -> $newStatus")
                } else {
                    val error = result.exceptionOrNull()
                    val liftrixError = LiftrixError.BusinessLogicError(
                        code = "FOLLOW_ACTION_FAILED",
                        errorMessage = "Failed to ${action.name.lowercase()}: ${error?.message ?: "Unknown error"}",
                        analyticsContext = mapOf(
                            "operation" to "FOLLOW_ACTION",
                            "action" to action.name,
                            "target_user_id" to profile.userId
                        )
                    )
                    updateState { 
                        it.copy(
                            isFollowActionLoading = false,
                            error = liftrixError
                        )
                    }
                    Timber.e(error, "Follow action failed: $action")
                }
                
            } catch (e: Exception) {
                val liftrixError = LiftrixError.NetworkError(
                    errorMessage = "Unexpected error during follow action: ${e.message}"
                )
                updateState { 
                    it.copy(
                        isFollowActionLoading = false,
                        error = liftrixError
                    )
                }
                Timber.e(e, "Unexpected error during follow action")
            }
        }
    }
    
    /**
     * Block user functionality
     */
    fun blockUser() {
        val profile = _uiState.value.profile ?: return
        
        viewModelScope.launch {
            try {
                updateState { it.copy(isFollowActionLoading = true) }
                
                val result = followUserUseCase(
                    targetUserId = profile.userId,
                    action = FollowAction.BLOCK,
                    context = "USER_PROFILE_BLOCK"
                )
                
                if (result.isSuccess) {
                    updateState { 
                        it.copy(
                            isFollowActionLoading = false,
                            followStatus = FollowStatus.BLOCKED,
                            canViewDetails = false
                        )
                    }
                    Timber.i("User blocked successfully: ${profile.userId}")
                } else {
                    val error = result.exceptionOrNull()
                    val liftrixError = LiftrixError.BusinessLogicError(
                        code = "BLOCK_USER_FAILED",
                        errorMessage = "Failed to block user: ${error?.message ?: "Unknown error"}",
                        analyticsContext = mapOf(
                            "operation" to "BLOCK_USER",
                            "target_user_id" to profile.userId
                        )
                    )
                    updateState { 
                        it.copy(
                            isFollowActionLoading = false,
                            error = liftrixError
                        )
                    }
                    Timber.e(error, "Failed to block user: ${profile.userId}")
                }
                
            } catch (e: Exception) {
                val liftrixError = LiftrixError.NetworkError(
                    errorMessage = "Unexpected error blocking user: ${e.message}"
                )
                updateState { 
                    it.copy(
                        isFollowActionLoading = false,
                        error = liftrixError
                    )
                }
                Timber.e(e, "Unexpected error blocking user")
            }
        }
    }
    
    /**
     * Select profile tab
     */
    fun selectTab(tab: ProfileTab) {
        updateState { it.copy(selectedTab = tab) }
        Timber.d("Selected profile tab: ${tab.name}")
    }
    
    /**
     * Share profile functionality
     */
    fun shareProfile(profile: PublicUserProfile) {
        viewModelScope.launch {
            try {
                // Generate share URL for the profile
                val shareUrl = "https://liftrix.app/profile/${profile.userId}"
                
                // Create shareable content
                val shareMessage = buildString {
                    append("Check out ${profile.displayName ?: profile.username}'s fitness profile on Liftrix!\n\n")
                    profile.bio?.let { append("$it\n\n") }
                    append("💪 ${profile.totalWorkouts} workouts completed\n")
                    append("🔥 ${profile.currentStreak} day streak\n")
                    if (profile.followersCount > 0) {
                        append("👥 ${profile.followersCount} followers\n")
                    }
                    append("\n$shareUrl")
                }
                
                // Store the share URL in state for QR code generation if needed
                updateState { 
                    it.copy(
                        profileShareUrl = shareUrl,
                        shareMessage = shareMessage
                    )
                }
                
                Timber.i("Profile share prepared for user: ${profile.userId}")
                
                // Trigger platform share (could be handled by UI layer)
                handleEvent(UserProfileEvent.ShareProfile(shareUrl, shareMessage))
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to share profile")
                val liftrixError = LiftrixError.NetworkError(
                    errorMessage = "Failed to share profile: ${e.message}"
                )
                updateState { 
                    it.copy(error = liftrixError)
                }
            }
        }
    }
    
    /**
     * Show more options menu
     */
    fun showMoreOptions() {
        updateState { it.copy(showMoreOptions = true) }
    }
    
    /**
     * Hide more options menu
     */
    fun hideMoreOptions() {
        updateState { it.copy(showMoreOptions = false) }
    }
    
    /**
     * Retry last operation
     */
    fun retryLastOperation() {
        val currentProfile = _uiState.value.profile
        if (currentProfile != null) {
            loadUserProfile(currentProfile.userId)
        }
    }
    
    override fun handleEvent(event: UserProfileEvent) {
        when (event) {
            is UserProfileEvent.LoadProfile -> loadUserProfile(event.userId)
            is UserProfileEvent.ToggleFollow -> toggleFollow()
            is UserProfileEvent.BlockUser -> blockUser()
            is UserProfileEvent.SelectTab -> selectTab(event.tab)
            is UserProfileEvent.ShareProfile -> {
                // Share functionality is already handled within shareProfile method
                // This event could trigger platform-specific sharing UI
                Timber.d("Profile share event received: ${event.shareUrl}")
            }
            is UserProfileEvent.ShowMoreOptions -> showMoreOptions()
            is UserProfileEvent.HideMoreOptions -> hideMoreOptions()
            is UserProfileEvent.RetryLastOperation -> retryLastOperation()
        }
    }
    
    /**
     * Determine follow status from profile connection status
     */
    private fun determineFollowStatus(profile: PublicUserProfile): FollowStatus {
        return when (profile.connectionStatus) {
            com.example.liftrix.domain.model.social.ConnectionStatus.CONNECTED -> FollowStatus.FOLLOWING
            com.example.liftrix.domain.model.social.ConnectionStatus.PENDING_SENT -> FollowStatus.PENDING_SENT
            com.example.liftrix.domain.model.social.ConnectionStatus.PENDING_RECEIVED -> FollowStatus.PENDING_RECEIVED
            com.example.liftrix.domain.model.social.ConnectionStatus.BLOCKED -> FollowStatus.BLOCKED
            com.example.liftrix.domain.model.social.ConnectionStatus.NONE -> FollowStatus.NONE
            com.example.liftrix.domain.model.social.ConnectionStatus.MUTUAL_FOLLOW -> FollowStatus.FOLLOWING
            com.example.liftrix.domain.model.social.ConnectionStatus.GYM_BUDDY -> FollowStatus.FOLLOWING
        }
    }
}

/**
 * UI state for user profile viewing
 */
data class UserProfileUiState(
    val profile: PublicUserProfile? = null,
    val followStatus: FollowStatus = FollowStatus.NONE,
    val canViewDetails: Boolean = false,
    val isOwnProfile: Boolean = false,
    val mutualConnectionCount: Int = 0,
    val selectedTab: ProfileTab = ProfileTab.WORKOUTS,
    val isLoading: Boolean = false,
    val isFollowActionLoading: Boolean = false,
    val error: LiftrixError? = null,
    val showMoreOptions: Boolean = false,
    val profileShareUrl: String? = null,
    val shareMessage: String? = null
)

/**
 * Events for user profile interactions
 */
sealed class UserProfileEvent : ViewModelEvent {
    data class LoadProfile(val userId: String) : UserProfileEvent()
    data object ToggleFollow : UserProfileEvent()
    data object BlockUser : UserProfileEvent()
    data class SelectTab(val tab: ProfileTab) : UserProfileEvent()
    data class ShareProfile(val shareUrl: String, val shareMessage: String) : UserProfileEvent()
    data object ShowMoreOptions : UserProfileEvent()
    data object HideMoreOptions : UserProfileEvent()
    data object RetryLastOperation : UserProfileEvent()
}