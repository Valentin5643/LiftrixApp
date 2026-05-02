package com.example.liftrix.ui.profile

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.social.PublicUserProfile
import com.example.liftrix.domain.model.social.FollowStatus
import com.example.liftrix.domain.usecase.social.SocialProfileQueryUseCase
import com.example.liftrix.domain.usecase.social.GetPublicProfileRequest
import com.example.liftrix.domain.usecase.social.SocialRelationshipUseCase
import com.example.liftrix.domain.usecase.social.FollowAction
import com.example.liftrix.domain.model.social.ReportReason
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.domain.usecase.social.PostEngagementUseCase
import com.example.liftrix.domain.usecase.social.RecordShareUseCase
import com.example.liftrix.domain.usecase.social.CopyWorkoutFromPostUseCase
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
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
 * - Profile tab navigation (workouts and stats)
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
    private val authQueryUseCase: AuthQueryUseCase,
    private val socialProfileQueryUseCase: SocialProfileQueryUseCase,
    private val socialRelationshipUseCase: SocialRelationshipUseCase,
    private val platformShareAdapter: PlatformShareAdapter,
    private val postEngagementUseCase: PostEngagementUseCase,
    private val recordShareUseCase: RecordShareUseCase,
    private val copyWorkoutFromPostUseCase: CopyWorkoutFromPostUseCase
) : ModernBaseViewModel<UserProfileUiState>(initialState = UserProfileUiState()) {
    
    // Current user ID for follow operations
    private val currentUserId = flow {
        try {
            val userId = authQueryUseCase(waitForAuth = false).fold(
                onSuccess = { it },
                onFailure = { null }
            )
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
                val profileResult = socialProfileQueryUseCase.getPublicProfile(GetPublicProfileRequest(
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

                val result = socialRelationshipUseCase.followAction(
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

                val result = socialRelationshipUseCase.blockUser(
                    targetUserId = profile.userId,
                    shouldBlock = true
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

                val result = socialRelationshipUseCase.reportUser(
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
        viewModelScope.launch {
            try {
                Timber.d("Workout post clicked: ${workoutPost.id}")
                
                // Update state to show loading for activity interaction
                updateState { 
                    it.copy(
                        isLoadingActivities = true,
                        error = null
                    )
                }
                
                // Handle optimistic navigation with proper state management
                val currentUserId = currentUserId.value
                if (currentUserId != null) {
                    // Handle engagement activity interaction
                    handleEvent(UserProfileEvent.WorkoutPostClicked(workoutPost))
                    
                    // Update engagement metrics optimistically for immediate UI feedback
                    updateState { state ->
                        val updatedProfile = state.profile?.let { profile ->
                            // Update engagement count if this is an interactive post
                            profile.copy(
                                totalWorkouts = profile.totalWorkouts // Keep current value for now
                            )
                        }
                        state.copy(
                            profile = updatedProfile,
                            isLoadingActivities = false
                        )
                    }
                    
                    Timber.i("Workout post interaction handled successfully: ${workoutPost.id}")
                } else {
                    updateState { 
                        it.copy(
                            isLoadingActivities = false,
                            error = LiftrixError.BusinessLogicError(
                                code = "WORKOUT_POST_INTERACTION_FAILED",
                                errorMessage = "Unable to interact with workout post - user not authenticated",
                                analyticsContext = mapOf(
                                    "operation" to "WORKOUT_POST_INTERACTION",
                                    "post_id" to workoutPost.id
                                )
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error handling workout post click: ${workoutPost.id}")
                updateState { 
                    it.copy(
                        isLoadingActivities = false,
                        error = LiftrixError.NetworkError(
                            errorMessage = "Failed to interact with workout post: ${e.message}"
                        )
                    )
                }
            }
        }
    }
    
    /**
     * Show all activities
     */
    fun showAllActivities() {
        updateState { it.copy(showAllActivities = true) }
        handleEvent(UserProfileEvent.ShowAllActivities)
    }
    
    // ==========================================
    // Social Engagement Methods
    // ==========================================
    
    /**
     * Toggle like status for a post with optimistic updates
     */
    fun toggleLike(postId: String) {
        viewModelScope.launch {
            try {
                // Optimistic update
                val wasLiked = _uiState.value.likedPosts.contains(postId)
                val newLikedState = !wasLiked
                
                updateState { state ->
                    state.copy(
                        likedPosts = if (newLikedState) {
                            state.likedPosts + postId
                        } else {
                            state.likedPosts - postId
                        },
                        engagementLoadingPosts = state.engagementLoadingPosts + postId,
                        // Update stats optimistically
                        postEngagementStats = state.postEngagementStats.mapValues { (id, stats) ->
                            if (id == postId) {
                                stats.copy(
                                    isLikedByViewer = newLikedState,
                                    likeCount = if (newLikedState) stats.likeCount + 1 else (stats.likeCount - 1).coerceAtLeast(0)
                                )
                            } else stats
                        }
                    )
                }
                
                Timber.d("Optimistic like update for post $postId: $newLikedState")

                // Execute like toggle
                val result = postEngagementUseCase.toggleLike(postId)
                
                result.fold(
                    onSuccess = { actualLikeState ->
                        updateState { state ->
                            state.copy(
                                engagementLoadingPosts = state.engagementLoadingPosts - postId,
                                likedPosts = if (actualLikeState) {
                                    state.likedPosts + postId
                                } else {
                                    state.likedPosts - postId
                                }
                            )
                        }
                        Timber.i("Like toggled successfully for post $postId: $actualLikeState")
                    },
                    onFailure = { error ->
                        // Revert optimistic update on failure
                        updateState { state ->
                            state.copy(
                                likedPosts = if (wasLiked) {
                                    state.likedPosts + postId
                                } else {
                                    state.likedPosts - postId
                                },
                                engagementLoadingPosts = state.engagementLoadingPosts - postId,
                                // Revert stats
                                postEngagementStats = state.postEngagementStats.mapValues { (id, stats) ->
                                    if (id == postId) {
                                        stats.copy(
                                            isLikedByViewer = wasLiked,
                                            likeCount = if (wasLiked) stats.likeCount + 1 else (stats.likeCount - 1).coerceAtLeast(0)
                                        )
                                    } else stats
                                },
                                error = error as? LiftrixError ?: LiftrixError.NetworkError(
                                    errorMessage = "Failed to toggle like: ${error.message}"
                                )
                            )
                        }
                        Timber.e(error, "Failed to toggle like for post: $postId")
                    }
                )
                
            } catch (e: Exception) {
                updateState { state ->
                    state.copy(
                        engagementLoadingPosts = state.engagementLoadingPosts - postId,
                        error = LiftrixError.NetworkError(
                            errorMessage = "Unexpected error toggling like: ${e.message}"
                        )
                    )
                }
                Timber.e(e, "Unexpected error toggling like for post: $postId")
            }
        }
    }
    
    /**
     * Toggle save status for a post with optimistic updates
     */
    fun toggleSave(postId: String) {
        viewModelScope.launch {
            try {
                // Optimistic update
                val wasSaved = _uiState.value.savedPosts.contains(postId)
                val newSavedState = !wasSaved
                
                updateState { state ->
                    state.copy(
                        savedPosts = if (newSavedState) {
                            state.savedPosts + postId
                        } else {
                            state.savedPosts - postId
                        },
                        engagementLoadingPosts = state.engagementLoadingPosts + postId,
                        // Update stats optimistically
                        postEngagementStats = state.postEngagementStats.mapValues { (id, stats) ->
                            if (id == postId) {
                                stats.copy(
                                    isSavedByViewer = newSavedState,
                                    saveCount = if (newSavedState) stats.saveCount + 1 else (stats.saveCount - 1).coerceAtLeast(0)
                                )
                            } else stats
                        }
                    )
                }
                
                Timber.d("Optimistic save update for post $postId: $newSavedState")

                // Execute save toggle
                val result = postEngagementUseCase.toggleSave(postId)
                
                result.fold(
                    onSuccess = { actualSaveState ->
                        updateState { state ->
                            state.copy(
                                engagementLoadingPosts = state.engagementLoadingPosts - postId,
                                savedPosts = if (actualSaveState) {
                                    state.savedPosts + postId
                                } else {
                                    state.savedPosts - postId
                                }
                            )
                        }
                        Timber.i("Save toggled successfully for post $postId: $actualSaveState")
                    },
                    onFailure = { error ->
                        // Revert optimistic update on failure
                        updateState { state ->
                            state.copy(
                                savedPosts = if (wasSaved) {
                                    state.savedPosts + postId
                                } else {
                                    state.savedPosts - postId
                                },
                                engagementLoadingPosts = state.engagementLoadingPosts - postId,
                                // Revert stats
                                postEngagementStats = state.postEngagementStats.mapValues { (id, stats) ->
                                    if (id == postId) {
                                        stats.copy(
                                            isSavedByViewer = wasSaved,
                                            saveCount = if (wasSaved) stats.saveCount + 1 else (stats.saveCount - 1).coerceAtLeast(0)
                                        )
                                    } else stats
                                },
                                error = error as? LiftrixError ?: LiftrixError.NetworkError(
                                    errorMessage = "Failed to toggle save: ${error.message}"
                                )
                            )
                        }
                        Timber.e(error, "Failed to toggle save for post: $postId")
                    }
                )
                
            } catch (e: Exception) {
                updateState { state ->
                    state.copy(
                        engagementLoadingPosts = state.engagementLoadingPosts - postId,
                        error = LiftrixError.NetworkError(
                            errorMessage = "Unexpected error toggling save: ${e.message}"
                        )
                    )
                }
                Timber.e(e, "Unexpected error toggling save for post: $postId")
            }
        }
    }
    
    /**
     * Create a comment on a post
     */
    fun createComment(postId: String, content: String) {
        viewModelScope.launch {
            try {
                updateState { it.copy(engagementLoadingPosts = it.engagementLoadingPosts + postId) }

                val result = postEngagementUseCase.createComment(postId, content)
                
                result.fold(
                    onSuccess = { comment ->
                        updateState { state ->
                            state.copy(
                                engagementLoadingPosts = state.engagementLoadingPosts - postId,
                                // Update comment count optimistically
                                postEngagementStats = state.postEngagementStats.mapValues { (id, stats) ->
                                    if (id == postId) {
                                        stats.copy(commentCount = stats.commentCount + 1)
                                    } else stats
                                }
                            )
                        }
                        Timber.i("Comment created successfully for post $postId: ${comment.id}")
                    },
                    onFailure = { error ->
                        updateState { state ->
                            state.copy(
                                engagementLoadingPosts = state.engagementLoadingPosts - postId,
                                error = error as? LiftrixError ?: LiftrixError.NetworkError(
                                    errorMessage = "Failed to create comment: ${error.message}"
                                )
                            )
                        }
                        Timber.e(error, "Failed to create comment for post: $postId")
                    }
                )
                
            } catch (e: Exception) {
                updateState { state ->
                    state.copy(
                        engagementLoadingPosts = state.engagementLoadingPosts - postId,
                        error = LiftrixError.NetworkError(
                            errorMessage = "Unexpected error creating comment: ${e.message}"
                        )
                    )
                }
                Timber.e(e, "Unexpected error creating comment for post: $postId")
            }
        }
    }
    
    /**
     * Share a post and record the share action
     */
    fun sharePost(postId: String, shareMethod: String) {
        viewModelScope.launch {
            try {
                updateState { it.copy(engagementLoadingPosts = it.engagementLoadingPosts + postId) }
                
                val result = recordShareUseCase(postId, shareMethod)
                
                result.fold(
                    onSuccess = {
                        updateState { state ->
                            state.copy(
                                engagementLoadingPosts = state.engagementLoadingPosts - postId,
                                // Update share count optimistically
                                postEngagementStats = state.postEngagementStats.mapValues { (id, stats) ->
                                    if (id == postId) {
                                        stats.copy(shareCount = stats.shareCount + 1)
                                    } else stats
                                }
                            )
                        }
                        Timber.i("Share recorded successfully for post $postId via $shareMethod")
                    },
                    onFailure = { error ->
                        updateState { state ->
                            state.copy(
                                engagementLoadingPosts = state.engagementLoadingPosts - postId,
                                error = error as? LiftrixError ?: LiftrixError.NetworkError(
                                    errorMessage = "Failed to record share: ${error.message}"
                                )
                            )
                        }
                        Timber.e(error, "Failed to record share for post: $postId")
                    }
                )
                
            } catch (e: Exception) {
                updateState { state ->
                    state.copy(
                        engagementLoadingPosts = state.engagementLoadingPosts - postId,
                        error = LiftrixError.NetworkError(
                            errorMessage = "Unexpected error recording share: ${e.message}"
                        )
                    )
                }
                Timber.e(e, "Unexpected error recording share for post: $postId")
            }
        }
    }
    
    /**
     * Copy workout from a post to user's templates
     */
    fun copyWorkout(postId: String) {
        viewModelScope.launch {
            try {
                updateState { it.copy(engagementLoadingPosts = it.engagementLoadingPosts + postId) }
                
                val result = copyWorkoutFromPostUseCase(postId)
                
                result.fold(
                    onSuccess = { templateId ->
                        updateState { state ->
                            state.copy(
                                engagementLoadingPosts = state.engagementLoadingPosts - postId
                            )
                        }
                        Timber.i("Workout copied successfully from post $postId to template $templateId")
                        // Could show success message or navigate to template
                    },
                    onFailure = { error ->
                        updateState { state ->
                            state.copy(
                                engagementLoadingPosts = state.engagementLoadingPosts - postId,
                                error = error as? LiftrixError ?: LiftrixError.NetworkError(
                                    errorMessage = "Failed to copy workout: ${error.message}"
                                )
                            )
                        }
                        Timber.e(error, "Failed to copy workout from post: $postId")
                    }
                )
                
            } catch (e: Exception) {
                updateState { state ->
                    state.copy(
                        engagementLoadingPosts = state.engagementLoadingPosts - postId,
                        error = LiftrixError.NetworkError(
                            errorMessage = "Unexpected error copying workout: ${e.message}"
                        )
                    )
                }
                Timber.e(e, "Unexpected error copying workout from post: $postId")
            }
        }
    }
    
    /**
     * Load engagement status for a post
     */
    fun loadEngagementStatus(postId: String) {
        // Skip if already loading or loaded
        if (_uiState.value.engagementLoadingPosts.contains(postId) || 
            _uiState.value.postEngagementStats.containsKey(postId)) {
            return
        }
        
        viewModelScope.launch {
            try {
                updateState { it.copy(engagementLoadingPosts = it.engagementLoadingPosts + postId) }
                
                val result = postEngagementUseCase.getEngagementStatus(postId)
                
                result.fold(
                    onSuccess = { engagementStatus ->
                        updateState { state ->
                            state.copy(
                                engagementLoadingPosts = state.engagementLoadingPosts - postId,
                                likedPosts = if (engagementStatus.isLiked) {
                                    state.likedPosts + postId
                                } else {
                                    state.likedPosts - postId
                                },
                                savedPosts = if (engagementStatus.isSaved) {
                                    state.savedPosts + postId
                                } else {
                                    state.savedPosts - postId
                                },
                                postEngagementStats = state.postEngagementStats + (postId to engagementStatus.stats)
                            )
                        }
                        Timber.d("Engagement status loaded for post $postId")
                    },
                    onFailure = { error ->
                        updateState { state ->
                            state.copy(
                                engagementLoadingPosts = state.engagementLoadingPosts - postId,
                                error = error as? LiftrixError ?: LiftrixError.NetworkError(
                                    errorMessage = "Failed to load engagement status: ${error.message}"
                                )
                            )
                        }
                        Timber.e(error, "Failed to load engagement status for post: $postId")
                    }
                )
                
            } catch (e: Exception) {
                updateState { state ->
                    state.copy(
                        engagementLoadingPosts = state.engagementLoadingPosts - postId,
                        error = LiftrixError.NetworkError(
                            errorMessage = "Unexpected error loading engagement status: ${e.message}"
                        )
                    )
                }
                Timber.e(e, "Unexpected error loading engagement status for post: $postId")
            }
        }
    }
    
    fun handleEvent(event: UserProfileEvent) {
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
            
            // Social engagement events
            is UserProfileEvent.ToggleLike -> toggleLike(event.postId)
            is UserProfileEvent.ToggleSave -> toggleSave(event.postId)
            is UserProfileEvent.CreateComment -> createComment(event.postId, event.content)
            is UserProfileEvent.SharePost -> sharePost(event.postId, event.shareMethod)
            is UserProfileEvent.CopyWorkout -> copyWorkout(event.postId)
            is UserProfileEvent.LoadEngagementStatus -> loadEngagementStatus(event.postId)
            
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
 * Includes new fields for weekly progress, recent activities,
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
    // Loading states for new components
    val isLoadingProgress: Boolean = false,
    val isLoadingActivities: Boolean = false,
    
    // Social engagement states
    val likedPosts: Set<String> = emptySet(),
    val savedPosts: Set<String> = emptySet(),
    val engagementLoadingPosts: Set<String> = emptySet(),
    val postEngagementStats: Map<String, com.example.liftrix.domain.model.social.PostEngagementStats> = emptyMap()
)

/**
 * Enhanced events for modern profile interactions
 */
sealed class UserProfileEvent {
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
    data class StatsClicked(val statType: com.example.liftrix.ui.profile.components.StatType) : UserProfileEvent()
    
    // Social engagement events
    data class ToggleLike(val postId: String) : UserProfileEvent()
    data class ToggleSave(val postId: String) : UserProfileEvent()
    data class CreateComment(val postId: String, val content: String) : UserProfileEvent()
    data class SharePost(val postId: String, val shareMethod: String) : UserProfileEvent()
    data class CopyWorkout(val postId: String) : UserProfileEvent()
    data class LoadEngagementStatus(val postId: String) : UserProfileEvent()
    
    // Navigation events
    data class NavigateToFollowersList(val userId: String) : UserProfileEvent()
    data class NavigateToFollowingList(val userId: String) : UserProfileEvent()
    data class NavigateToWorkoutsList(val userId: String) : UserProfileEvent()
}
