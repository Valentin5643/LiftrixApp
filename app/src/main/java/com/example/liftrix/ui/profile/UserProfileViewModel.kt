package com.example.liftrix.ui.profile

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.social.PublicUserProfile
import com.example.liftrix.domain.model.social.FollowStatus
import com.example.liftrix.domain.usecase.social.GetPublicProfileUseCase
import com.example.liftrix.domain.usecase.social.GetPublicProfileRequest
import com.example.liftrix.domain.usecase.social.FollowUserUseCase
import com.example.liftrix.domain.usecase.social.FollowAction
import com.example.liftrix.domain.usecase.social.ReportUserUseCase
import com.example.liftrix.domain.model.social.ReportReason
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.share.PlatformShareAdapter
import com.example.liftrix.domain.model.ShareableContent
import com.example.liftrix.domain.model.SocialPlatform
import com.example.liftrix.ui.profile.components.StatType
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
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
    private val reportUserUseCase: ReportUserUseCase,
    private val platformShareAdapter: PlatformShareAdapter,
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
     * Load user profile with privacy filtering and additional modern profile data
     */
    fun loadUserProfile(userId: String, forceRefresh: Boolean = false) {
        // Skip loading if we already have the profile and it's not a force refresh
        if (!forceRefresh && _uiState.value.profile?.userId == userId && !_uiState.value.isLoading) {
            Timber.d("Profile already loaded for user: $userId, skipping reload")
            return
        }
        
        viewModelScope.launch {
            try {
                // Preserve existing profile data during loading to prevent UI flashing
                val existingProfile = if (_uiState.value.profile?.userId == userId) {
                    _uiState.value.profile
                } else {
                    null
                }
                
                updateState { 
                    it.copy(
                        isLoading = true, 
                        error = null,
                        // Keep existing profile data during reload to prevent flashing
                        profile = existingProfile ?: it.profile
                    ) 
                }
                
                Timber.d("Loading profile for user: $userId (forceRefresh: $forceRefresh)")
                
                // Load profile data
                val profileResult = getPublicProfileUseCase(GetPublicProfileRequest(
                    profileUserId = userId,
                    trackView = true
                ))
                if (profileResult.isSuccess) {
                    val result = profileResult.getOrThrow()
                    updateState { 
                        it.copy(
                            isLoading = false,
                            profile = result.profile,
                            canViewDetails = result.canInteract,
                            isOwnProfile = result.isOwnProfile,
                            followStatus = determineFollowStatus(result.profile),
                            mutualConnectionCount = result.profile.mutualConnectionsCount,
                        )
                    }
                    
                    Timber.i("Profile loaded successfully for user: $userId")
                } else {
                    val error = profileResult.exceptionOrNull()
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
                    FollowStatus.MUTUAL_FOLLOW -> FollowAction.UNFOLLOW
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
     * Share profile functionality using the enhanced PlatformShareAdapter
     */
    fun shareProfile(profile: PublicUserProfile) {
        viewModelScope.launch {
            try {
                // Generate share URL for the profile
                val shareUrl = "https://liftrix.app/profile/${profile.userId}"
                
                // Create enhanced shareable content
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
                
                // Create shareable content with enhanced features
                val shareableContent = ShareableContent(
                    id = "profile_${profile.userId}",
                    type = com.example.liftrix.domain.model.ShareableContentType.PROGRESS,
                    title = "${profile.displayName ?: profile.username}'s Fitness Profile",
                    subtitle = profile.bio ?: "Check out this amazing fitness journey!",
                    imageUrl = profile.profileImageUrl, // Include profile image if available
                    metadata = mapOf(
                        "shareUrl" to shareUrl,
                        "shareMessage" to shareMessage,
                        "hashtags" to listOf("#Liftrix", "#Fitness", "#Workout")
                    )
                )
                
                // Store the share URL in state for QR code generation if needed
                updateState { 
                    it.copy(
                        profileShareUrl = shareUrl,
                        shareMessage = shareMessage,
                        shareableContent = shareableContent
                    )
                }
                
                Timber.i("Profile share prepared for user: ${profile.userId}")
                
                // Trigger enhanced platform share
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
     * Share profile to specific platform using enhanced sharing
     */
    fun shareProfileToPlatform(profile: PublicUserProfile, platform: SocialPlatform) {
        viewModelScope.launch {
            try {
                // Get or create shareable content
                val content = uiState.value.shareableContent ?: run {
                    shareProfile(profile) // This will create the content
                    uiState.value.shareableContent
                } ?: return@launch
                
                // Use the enhanced platform share adapter
                platformShareAdapter.shareContent(platform, content)
                    .fold(
                        onSuccess = { intent ->
                            Timber.i("Successfully created share intent for $platform")
                            updateState { 
                                it.copy(shareMessage = "Profile share intent created for $platform successfully!")
                            }
                        },
                        onFailure = { error ->
                            Timber.e("Failed to share to $platform: $error")
                            updateState {
                                it.copy(error = error as? LiftrixError ?: LiftrixError.NetworkError(
                                    errorMessage = "Failed to share to $platform"
                                ))
                            }
                        }
                    )
                    
            } catch (e: Exception) {
                Timber.e(e, "Failed to share profile to platform: $platform")
                updateState {
                    it.copy(error = LiftrixError.NetworkError(
                        errorMessage = "Failed to share profile: ${e.message}"
                    ))
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
     * Clear share message after sharing
     */
    fun clearShareMessage() {
        updateState { it.copy(shareMessage = null) }
    }
    
    /**
     * Report user with reason and description
     */
    fun reportUser(reason: ReportReason, description: String?) {
        val profile = _uiState.value.profile ?: return
        
        viewModelScope.launch {
            try {
                updateState { it.copy(isLoading = true) }
                
                val result = reportUserUseCase(
                    targetUserId = profile.userId,
                    reason = reason,
                    description = description ?: "Reported from profile view"
                )
                
                result.fold(
                    onSuccess = {
                        updateState { it.copy(isLoading = false) }
                        Timber.i("User reported successfully: ${profile.userId}")
                        // Could show a success message to the user
                    },
                    onFailure = { error ->
                        val liftrixError = error as? LiftrixError ?: LiftrixError.BusinessLogicError(
                            code = "REPORT_FAILED",
                            errorMessage = "Failed to report user: ${error.message}",
                            analyticsContext = mapOf(
                                "operation" to "REPORT_USER",
                                "user_id" to profile.userId
                            )
                        )
                        updateState { 
                            it.copy(
                                isLoading = false,
                                error = liftrixError
                            )
                        }
                        Timber.e(error, "Failed to report user: ${profile.userId}")
                    }
                )
            } catch (e: Exception) {
                val liftrixError = LiftrixError.NetworkError(
                    errorMessage = "Unexpected error reporting user: ${e.message}"
                )
                updateState { 
                    it.copy(
                        isLoading = false,
                        error = liftrixError
                    )
                }
                Timber.e(e, "Unexpected error reporting user")
            }
        }
    }
    
    /**
     * Retry last operation
     */
    fun retryLastOperation() {
        val currentProfile = _uiState.value.profile
        if (currentProfile != null) {
            loadUserProfile(currentProfile.userId, forceRefresh = true)
        }
    }
    
    
    /**
     * Refresh profile data manually
     */
    fun refreshProfile() {
        val currentProfile = _uiState.value.profile ?: return
        
        viewModelScope.launch {
            updateState { it.copy(isRefreshing = true) }
            loadUserProfile(currentProfile.userId, forceRefresh = true)
            updateState { it.copy(isRefreshing = false) }
        }
    }
    
    /**
     * Handle stats item clicks - triggers navigation events
     */
    fun handleStatsClick(statType: com.example.liftrix.ui.profile.components.StatType) {
        val profile = _uiState.value.profile ?: return
        
        when (statType) {
            com.example.liftrix.ui.profile.components.StatType.WORKOUTS -> {
                Timber.d("Navigate to workouts list for user: ${profile.userId}")
                handleEvent(UserProfileEvent.NavigateToWorkoutsList(profile.userId))
            }
            com.example.liftrix.ui.profile.components.StatType.FOLLOWERS -> {
                Timber.d("Navigate to followers list for user: ${profile.userId}")
                handleEvent(UserProfileEvent.NavigateToFollowersList(profile.userId))
            }
            com.example.liftrix.ui.profile.components.StatType.FOLLOWING -> {
                Timber.d("Navigate to following list for user: ${profile.userId}")
                handleEvent(UserProfileEvent.NavigateToFollowingList(profile.userId))
            }
        }
    }
    
    /**
     * Handle workout post clicks from recent activities
     */
    fun handleActivityClick(workoutPost: com.example.liftrix.domain.model.social.WorkoutPost) {
        Timber.d("Workout post clicked: ${workoutPost.id}")
        // TODO: Navigate to workout detail screen
        handleEvent(UserProfileEvent.WorkoutPostClicked(workoutPost))
    }
    
    /**
     * Show all activities
     */
    fun showAllActivities() {
        updateState { it.copy(showAllActivities = true) }
        handleEvent(UserProfileEvent.ShowAllActivities)
    }
    
    /**
     * Show all achievements
     */
    fun showAllAchievements() {
        updateState { it.copy(expandedAchievements = true) }
        handleEvent(UserProfileEvent.ShowAllAchievements)
    }
    
    override fun handleEvent(event: UserProfileEvent) {
        when (event) {
            // Existing events
            is UserProfileEvent.LoadProfile -> loadUserProfile(event.userId)
            is UserProfileEvent.ToggleFollow -> toggleFollow()
            is UserProfileEvent.BlockUser -> blockUser()
            is UserProfileEvent.ShareProfile -> {
                // Share functionality is already handled within shareProfile method
                // This event could trigger platform-specific sharing UI
                Timber.d("Profile share event received: ${event.shareUrl}")
            }
            is UserProfileEvent.ShareToPlatform -> {
                uiState.value.profile?.let { profile ->
                    shareProfileToPlatform(profile, event.platform)
                }
            }
            is UserProfileEvent.ShowMoreOptions -> showMoreOptions()
            is UserProfileEvent.HideMoreOptions -> hideMoreOptions()
            is UserProfileEvent.RetryLastOperation -> retryLastOperation()
            
            // New modern profile events
            is UserProfileEvent.RefreshProfile -> refreshProfile()
            is UserProfileEvent.WorkoutPostClicked -> {
                // Navigation should be handled by the UI layer
                Timber.d("Workout post clicked: ${event.workoutPost.id}")
            }
            is UserProfileEvent.ShowAllActivities -> {
                // Navigation should be handled by the UI layer
                // State is already updated in showAllActivities() method
                Timber.d("ShowAllActivities event received - navigation should be handled by UI")
            }
            is UserProfileEvent.ShowAllAchievements -> {
                // Navigation should be handled by the UI layer  
                // State is already updated in showAllAchievements() method
                Timber.d("ShowAllAchievements event received - navigation should be handled by UI")
            }
            is UserProfileEvent.StatsClicked -> {
                // Stats click handling - avoid recursion
                val profile = _uiState.value.profile ?: return
                
                when (event.statType) {
                    com.example.liftrix.ui.profile.components.StatType.WORKOUTS -> {
                        Timber.d("Navigate to workouts list for user: ${profile.userId}")
                        // Navigation should be handled by UI layer
                    }
                    com.example.liftrix.ui.profile.components.StatType.FOLLOWERS -> {
                        Timber.d("Navigate to followers list for user: ${profile.userId}")
                        // Navigation should be handled by UI layer
                    }
                    com.example.liftrix.ui.profile.components.StatType.FOLLOWING -> {
                        Timber.d("Navigate to following list for user: ${profile.userId}")
                        // Navigation should be handled by UI layer
                    }
                }
            }
            // Navigation events
            is UserProfileEvent.NavigateToFollowersList -> {
                Timber.d("Navigation event: Navigate to followers list for user ${event.userId}")
                // Navigation is handled by the screen composable
            }
            is UserProfileEvent.NavigateToFollowingList -> {
                Timber.d("Navigation event: Navigate to following list for user ${event.userId}")
                // Navigation is handled by the screen composable
            }
            is UserProfileEvent.NavigateToWorkoutsList -> {
                Timber.d("Navigation event: Navigate to workouts list for user ${event.userId}")
                // Navigation is handled by the screen composable
            }
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
            com.example.liftrix.domain.model.social.ConnectionStatus.GYM_BUDDY -> FollowStatus.FOLLOWING
            com.example.liftrix.domain.model.social.ConnectionStatus.MUTUAL_FOLLOW -> FollowStatus.FOLLOWING
            com.example.liftrix.domain.model.social.ConnectionStatus.SELF -> FollowStatus.NONE // Own profile, no follow status
        }
    }
}


/**
 * Enhanced UI state for modern profile viewing
 * 
 * Includes new fields for weekly progress, achievement progress, recent activities,
 * and enhanced layout preferences following the modernization specification.
 */
data class UserProfileUiState(
    // Existing profile fields
    val profile: PublicUserProfile? = null,
    val followStatus: FollowStatus = FollowStatus.NONE,
    val canViewDetails: Boolean = false,
    val isOwnProfile: Boolean = false,
    val mutualConnectionCount: Int = 0,
    val isLoading: Boolean = false,
    val isFollowActionLoading: Boolean = false,
    val error: LiftrixError? = null,
    val showMoreOptions: Boolean = false,
    val profileShareUrl: String? = null,
    val shareMessage: String? = null,
    val shareableContent: ShareableContent? = null,
    
    // New modern profile UI fields (simplified)
    val isRefreshing: Boolean = false,
    
    val showAllActivities: Boolean = false,
    val expandedAchievements: Boolean = false,
    
    // Loading states for new components
    val isLoadingProgress: Boolean = false,
    val isLoadingActivities: Boolean = false,
    val isLoadingAchievements: Boolean = false
)

/**
 * Enhanced events for modern profile interactions
 */
sealed class UserProfileEvent : ViewModelEvent {
    // Existing events
    data class LoadProfile(val userId: String) : UserProfileEvent()
    data object ToggleFollow : UserProfileEvent()
    data object BlockUser : UserProfileEvent()
    data class ShareProfile(val shareUrl: String, val shareMessage: String) : UserProfileEvent()
    data class ShareToPlatform(val platform: SocialPlatform) : UserProfileEvent()
    data object ShowMoreOptions : UserProfileEvent()
    data object HideMoreOptions : UserProfileEvent()
    data object RetryLastOperation : UserProfileEvent()
    
    // New events for modern profile UI
    data object RefreshProfile : UserProfileEvent()
    data class WorkoutPostClicked(val workoutPost: com.example.liftrix.domain.model.social.WorkoutPost) : UserProfileEvent()
    data object ShowAllActivities : UserProfileEvent()
    data object ShowAllAchievements : UserProfileEvent()
    data class StatsClicked(val statType: com.example.liftrix.ui.profile.components.StatType) : UserProfileEvent()
    
    // Navigation events
    data class NavigateToFollowersList(val userId: String) : UserProfileEvent()
    data class NavigateToFollowingList(val userId: String) : UserProfileEvent()
    data class NavigateToWorkoutsList(val userId: String) : UserProfileEvent()
}


