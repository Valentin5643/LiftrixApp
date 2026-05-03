package com.example.liftrix.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.Friend
import com.example.liftrix.domain.model.SharedWorkout
import com.example.liftrix.domain.model.User
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.social.FollowRepository
import com.example.liftrix.domain.model.social.FollowRelationship
import com.example.liftrix.domain.model.FriendStatus
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.domain.usecase.social.SocialSearchUseCase
import com.example.liftrix.domain.usecase.social.SearchUsersRequest
import com.example.liftrix.domain.usecase.social.SocialRelationshipUseCase
import com.example.liftrix.domain.usecase.social.FeedGeneratorUseCase
import com.example.liftrix.domain.usecase.social.FollowAction
import com.example.liftrix.domain.model.social.SearchFilters
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.liftrix.domain.model.social.WorkoutPost
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for social screen state management and follow interactions.
 * 
 * Manages social UI state including following/followers lists, workout feed, follow requests,
 * real-time presence updates, loading states, and error handling. Integrates with
 * FollowRepository for follow management, FirebasePresenceService for presence tracking,
 * and AnalyticsService for usage analytics.
 * 
 * @param followRepository Repository for follow/unfollow operations
 * @param authRepository Repository for authentication state
 * @param analyticsService Service for analytics event tracking
 */
@HiltViewModel
class SocialViewModel @Inject constructor(
    private val followRepository: FollowRepository,
    private val authRepository: AuthRepository,
    private val analyticsService: AnalyticsService,
    private val socialSearchUseCase: SocialSearchUseCase,
    private val feedGeneratorUseCase: FeedGeneratorUseCase,
    private val socialRelationshipUseCase: SocialRelationshipUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SocialUiState())
    val uiState: StateFlow<SocialUiState> = _uiState.asStateFlow()
    
    // Navigation events
    private val _navigationEvents = MutableSharedFlow<SocialNavigationEvent>()
    val navigationEvents: SharedFlow<SocialNavigationEvent> = _navigationEvents.asSharedFlow()

    // Add Paging3 support for social feed
    val feedPagingData: Flow<PagingData<WorkoutPost>> = 
        authRepository.currentUser
            .filterNotNull()
            .flatMapLatest { user ->
                feedGeneratorUseCase(userId = user.uid, includeDiscovery = false)
            }
            .cachedIn(viewModelScope)

    companion object {
        private const val FRIEND_FEED_LIMIT = 10
    }

    init {
        observeUserDataAndLoadSocial()
        trackSocialScreenViewed()
        // Load social feed data (following/followers)
        loadSocialFeed()
    }

    /**
     * Handles UI events from the social screen
     */
    fun onEvent(event: SocialEvent) {
        when (event) {
            is SocialEvent.LoadData -> {
                loadSocialFeed()
            }
            is SocialEvent.RefreshData -> {
                refreshData()
                trackRefreshPerformed()
            }
            is SocialEvent.Refresh -> {
                refreshData()
                trackRefreshPerformed()
            }
            is SocialEvent.LoadFriends -> {
                loadSocialFeed()
            }
            // New Following/Followers handlers
            is SocialEvent.LoadFollowing -> {
                loadFollowing()
            }
            is SocialEvent.LoadFollowers -> {
                loadFollowers()
            }
            is SocialEvent.FollowUser -> {
                followUser(event.userId)
            }
            is SocialEvent.UnfollowUser -> {
                unfollowUser(event.userId)
            }
            // Search handlers
            is SocialEvent.SearchFriends -> {
                searchFriends(event.query)
            }
            is SocialEvent.SearchUsers -> {
                searchUsers(event.query)
            }
            // Follow request handlers
            is SocialEvent.SendFriendRequest -> {
                sendFollowRequest(event.userId)
            }
            is SocialEvent.AcceptFriendRequest -> {
                respondToFollowRequest(event.userId, accept = true)
            }
            is SocialEvent.DeclineFriendRequest -> {
                respondToFollowRequest(event.userId, accept = false)
            }
            is SocialEvent.RemoveFriend -> {
                unfollowUser(event.userId)
            }
            is SocialEvent.BlockUser -> {
                blockUser(event.userId)
            }
            is SocialEvent.ViewAllFriends -> {
                trackViewAllFriendsClicked()
            }
            is SocialEvent.ViewWorkout -> {
                trackWorkoutViewed(event.sharedWorkout)
            }
            is SocialEvent.CongratulateWorkout -> {
                congratulateWorkout(event.sharedWorkout)
            }
            is SocialEvent.ErrorDismissed -> {
                updateState { copy(error = null) }
            }
            is SocialEvent.ToggleFollow -> {
                handleFollowAction(event.targetUserId)
            }
            is SocialEvent.NavigateToUserProfile -> {
                // Emit navigation event - will be handled by the screen
                _navigationEvents.tryEmit(SocialNavigationEvent.NavigateToUserProfile(event.userId))
            }
        }
    }

    /**
     * Loads social feed data including following, followers, and workout feed
     */
    fun loadSocialFeed() {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.getCurrentUser()
                if (currentUser == null) {
                    updateState { copy(isLoading = false, error = "User not authenticated") }
                    return@launch
                }

                Timber.d("DEBUG_SOCIAL_FEED: Loading social feed for user: ${currentUser.uid}")
                updateState { copy(isLoading = true, error = null) }
                
                // Load following and followers data
                loadFollowing()
                loadFollowers()
                
                updateState { copy(isLoading = false) }
                
            } catch (exception: Exception) {
                Timber.e(exception, "Error in loadSocialFeed")
                updateState { 
                    copy(
                        isLoading = false, 
                        error = "Failed to load social data: ${exception.message}"
                    ) 
                }
            }
        }
    }

    /**
     * Sends a follow request to the specified user (replaces friend request)
     */
    private fun sendFollowRequest(userId: String) {
        viewModelScope.launch {
            try {
                Timber.d("DEBUG_FOLLOW_REQUEST: Starting follow request for userId: $userId")
                updateState { copy(isLoading = true) }
                
                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId == null) {
                    updateState { copy(isLoading = false, error = "User not authenticated") }
                    return@launch
                }
                
                val result = followRepository.sendFollowRequest(
                    followerId = currentUserId.value,
                    targetUserId = userId,
                    requestSource = "SOCIAL_SCREEN"
                )
                
                result.fold(
                    onSuccess = { followStatus ->
                        Timber.d("DEBUG_FOLLOW_REQUEST: Follow request successful, status: $followStatus")
                        loadSocialFeed() // Refresh data to show updated state
                        analyticsService.logSocialFeedEvent(
                            userId = currentUserId.value,
                            eventType = "follow_request_sent",
                            additionalData = mapOf("target_user_id" to userId)
                        )
                    },
                    onFailure = { error ->
                        Timber.e("DEBUG_FOLLOW_REQUEST: Follow request failed: ${error.message}")
                        updateState { 
                            copy(
                                isLoading = false,
                                error = "Failed to send follow request: ${error.message}"
                            ) 
                        }
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "DEBUG_FOLLOW_REQUEST: Error sending follow request")
                updateState { 
                    copy(
                        isLoading = false,
                        error = "Failed to send follow request: ${exception.message}"
                    ) 
                }
            }
        }
    }

    /**
     * Refreshes social data manually
     */
    fun refreshData() {
        loadSocialFeed()
    }

    /**
     * Searches for users by query string with immediate UI update
     */
    private fun searchFriends(query: String) {
        // Immediately update search query to prevent text input issues
        _uiState.value = _uiState.value.copy(searchQuery = query)
        
        viewModelScope.launch {
            if (query.isBlank()) {
                _uiState.value = _uiState.value.copy(searchResults = emptyList(), isSearching = false)
                return@launch
            }
            
            _uiState.value = _uiState.value.copy(isSearching = true)
            
            val request = SearchUsersRequest(
                query = query,
                filters = SearchFilters(),
                limit = 20,
                useCache = true
            )
            
            socialSearchUseCase.searchUsers(request).fold(
                onSuccess = { result ->
                    val users = result.users.map { searchResult ->
                        User(
                            uid = searchResult.userId,
                            email = "search-result@liftrix.app", // Placeholder email for search results
                            displayName = searchResult.displayName,
                            photoUrl = searchResult.profileImageUrl,
                            isAnonymous = false, // Search results are registered users
                            subscriptionTier = com.example.liftrix.domain.model.SubscriptionTier.FREE,
                            subscriptionStatus = com.example.liftrix.domain.model.SubscriptionStatus.EXPIRED,
                            subscriptionExpiresAt = null,
                            premiumFeaturesEnabled = false,
                            onboardingCompleted = true,
                            profileVersion = 1L,
                            createdAt = java.time.LocalDateTime.now().minusDays(30),
                            lastSignInAt = java.time.LocalDateTime.now().minusDays(1),
                            updatedAt = java.time.LocalDateTime.now()
                        )
                    }
                    _uiState.value = _uiState.value.copy(
                        searchResults = users, 
                        isSearching = false,
                        error = null
                    )
                },
                onFailure = { error ->
                    Timber.e("Search failed: $error")
                    _uiState.value = _uiState.value.copy(
                        searchResults = emptyList(),
                        isSearching = false,
                        error = error.message
                    )
                }
            )
        }
    }

    /**
     * Responds to a follow request (accept or decline)
     */
    private fun respondToFollowRequest(userId: String, accept: Boolean) {
        viewModelScope.launch {
            try {
                Timber.d("DEBUG_FOLLOW_RESPONSE: Responding to follow request - userId: $userId, accept: $accept")
                updateState { copy(isLoading = true) }
                
                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId == null) {
                    updateState { copy(isLoading = false, error = "User not authenticated") }
                    return@launch
                }
                
                val result = if (accept) {
                    followRepository.acceptFollowRequest(
                        targetUserId = currentUserId.value,
                        requesterId = userId
                    )
                } else {
                    followRepository.declineFollowRequest(
                        targetUserId = currentUserId.value,
                        requesterId = userId
                    )
                }
                
                result.fold(
                    onSuccess = { _ ->
                        Timber.d("DEBUG_FOLLOW_RESPONSE: Follow request response successful")
                        loadSocialFeed() // Refresh data to show updated state
                        analyticsService.logSocialFeedEvent(
                            userId = currentUserId.value,
                            eventType = if (accept) "follow_request_accepted" else "follow_request_declined",
                            additionalData = mapOf("requester_user_id" to userId)
                        )
                    },
                    onFailure = { error ->
                        Timber.e("DEBUG_FOLLOW_RESPONSE: Follow request response failed: ${error.message}")
                        updateState { 
                            copy(
                                isLoading = false,
                                error = "Failed to respond to follow request: ${error.message}"
                            ) 
                        }
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "DEBUG_FOLLOW_RESPONSE: Error responding to follow request")
                updateState { 
                    copy(
                        isLoading = false,
                        error = "Failed to respond to follow request: ${exception.message}"
                    ) 
                }
            }
        }
    }

    // removeFriend method removed - use unfollowUser instead

    /**
     * Blocks a user
     */
    private fun blockUser(userId: String) {
        viewModelScope.launch {
            try {
                Timber.d("DEBUG_BLOCK_USER: Starting block action for userId: $userId")
                updateState { copy(isLoading = true) }
                
                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId == null) {
                    updateState { copy(isLoading = false, error = "User not authenticated") }
                    return@launch
                }
                
                val result = followRepository.blockUser(
                    blockerId = currentUserId.value,
                    targetUserId = userId
                )
                
                result.fold(
                    onSuccess = {
                        Timber.d("DEBUG_BLOCK_USER: Block action successful")
                        loadSocialFeed() // Refresh data to show updated state
                        analyticsService.logSocialFeedEvent(
                            userId = currentUserId.value,
                            eventType = "user_blocked",
                            additionalData = mapOf("target_user_id" to userId)
                        )
                    },
                    onFailure = { error ->
                        Timber.e("DEBUG_BLOCK_USER: Block action failed: ${error.message}")
                        updateState { 
                            copy(
                                isLoading = false,
                                error = "Failed to block user: ${error.message}"
                            ) 
                        }
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "DEBUG_BLOCK_USER: Error blocking user")
                updateState { 
                    copy(
                        isLoading = false,
                        error = "Failed to block user: ${exception.message}"
                    ) 
                }
            }
        }
    }

    /**
     * Congratulates a friend on their workout
     */
    private fun congratulateWorkout(sharedWorkout: SharedWorkout) {
        viewModelScope.launch {
            try {
                // Congratulation functionality handled by social interaction service
                trackWorkoutCongratulated(sharedWorkout)
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to congratulate workout")
            }
        }
    }

    /**
     * Handles follow/unfollow actions with optimistic UI updates
     */
    fun handleFollowAction(targetUserId: String) {
        viewModelScope.launch {
            val currentUserId = authRepository.getCurrentUserId() ?: return@launch

            socialRelationshipUseCase.followAction(
                targetUserId = targetUserId,
                action = FollowAction.FOLLOW,
                context = "SOCIAL_FEED"
            ).fold(
                onSuccess = {
                    // Update UI optimistically
                    updateFollowStatus(targetUserId, true)
                    // Track analytics
                    analyticsService.logSocialFeedEvent(
                        userId = currentUserId.value,
                        eventType = "follow_action",
                        additionalData = mapOf(
                            "target_user_id" to targetUserId,
                            "action" to "follow",
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(error = "Failed to update follow status")
                    Timber.e("Follow action failed: $error")
                }
            )
        }
    }

    /**
     * Updates follow status in the UI state optimistically
     */
    private fun updateFollowStatus(targetUserId: String, isNowFollowing: Boolean) {
        _uiState.value = _uiState.value.copy(
            searchResults = _uiState.value.searchResults.map { user ->
                if (user.uid == targetUserId) {
                    // In a real implementation, we'd track follow status
                    // For now, this is a placeholder for optimistic updates
                    user
                } else {
                    user
                }
            }
        )
    }

    /**
     * Observes authentication state and loads data when user is available
     */
    private fun observeUserDataAndLoadSocial() {
        viewModelScope.launch {
            authRepository.currentUser
                .catch { throwable ->
                    Timber.e(throwable, "Error observing auth state")
                    updateState { copy(isLoading = false, error = "Authentication error") }
                }
                .collect { user ->
                    if (user != null) {
                        loadSocialFeed()
                    } else {
                        updateState { 
                            copy(
                                isLoading = false,
                                following = emptyList(),
                                followers = emptyList(),
                                followingCount = 0,
                                followersCount = 0,
                                isEmpty = true,
                                error = null
                            ) 
                        }
                    }
                }
        }
    }

    /**
     * Loads following list (people you follow)
     */
    private fun loadFollowing() {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.getCurrentUser()
                if (currentUser == null) {
                    Timber.e("DEBUG_FOLLOWING: User not authenticated")
                    updateState { copy(isLoading = false, error = "User not authenticated") }
                    return@launch
                }

                Timber.d("DEBUG_FOLLOWING: Starting to load following for user: ${currentUser.uid}")
                updateState { copy(isLoading = true, error = null) }
                
                // Use enriched FollowRepository method with profile data to resolve "Unknown User" issue
                followRepository.observeFollowingWithProfiles(currentUser.uid).collect { followRelationships ->
                    Timber.d("DEBUG_FOLLOWING: Received ${followRelationships.size} following with profiles from FollowRepository")
                    followRelationships.forEachIndexed { index, relationship ->
                        Timber.d("DEBUG_FOLLOWING: Following $index - Name: ${relationship.displayName}, ID: ${relationship.followingId}, Status: ${relationship.status}")
                    }
                    
                    // Convert FollowRelationship to Friend for UI compatibility - now with actual profile data
                    val followingFriends = followRelationships.map { relationship ->
                        Friend(
                            userId = relationship.followingId,
                            displayName = relationship.displayName ?: "Unknown User", // Now populated from database join
                            email = null,
                            status = FriendStatus.ACCEPTED, // Following relationships are accepted
                            presence = null,
                            avatarUrl = relationship.profileImageUrl, // Now populated from database join
                            friendSince = java.time.Instant.ofEpochMilli(relationship.createdAt)
                        )
                    }
                    
                    updateState {
                        copy(
                            isLoading = false,
                            following = followingFriends,
                            followingCount = followingFriends.size,
                            error = null
                        )
                    }
                    Timber.d("DEBUG_FOLLOWING: Updated UI state with ${followingFriends.size} following users")
                }
            } catch (exception: Exception) {
                Timber.e(exception, "DEBUG_FOLLOWING: Error loading following")
                updateState { 
                    copy(
                        isLoading = false, 
                        error = "Failed to load following: ${exception.message}"
                    ) 
                }
            }
        }
    }

    /**
     * Loads followers list (people who follow you)
     */
    private fun loadFollowers() {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.getCurrentUser()
                if (currentUser == null) {
                    Timber.e("DEBUG_FOLLOWERS: User not authenticated")
                    updateState { copy(isLoading = false, error = "User not authenticated") }
                    return@launch
                }

                Timber.d("DEBUG_FOLLOWERS: Starting to load followers for user: ${currentUser.uid}")
                updateState { copy(isLoading = true, error = null) }
                
                // Use enriched FollowRepository method with profile data to resolve "Unknown User" issue
                followRepository.observeFollowersWithProfiles(currentUser.uid).collect { followerRelationships ->
                    Timber.d("DEBUG_FOLLOWERS: Received ${followerRelationships.size} followers with profiles from FollowRepository")
                    followerRelationships.forEachIndexed { index, relationship ->
                        Timber.d("DEBUG_FOLLOWERS: Follower $index - Name: ${relationship.displayName}, ID: ${relationship.followerId}, Status: ${relationship.status}")
                    }
                    
                    // Convert FollowRelationship to Friend for UI compatibility - now with actual profile data
                    val followerFriends = followerRelationships.map { relationship ->
                        Friend(
                            userId = relationship.followerId,
                            displayName = relationship.displayName ?: "Unknown User", // Now populated from database join
                            email = null,
                            status = FriendStatus.ACCEPTED, // Follower relationships are accepted
                            presence = null,
                            avatarUrl = relationship.profileImageUrl, // Now populated from database join
                            friendSince = java.time.Instant.ofEpochMilli(relationship.createdAt)
                        )
                    }
                    
                    updateState {
                        copy(
                            isLoading = false,
                            followers = followerFriends,
                            followersCount = followerFriends.size,
                            error = null
                        )
                    }
                    Timber.d("DEBUG_FOLLOWERS: Updated UI state with ${followerFriends.size} followers")
                }
            } catch (exception: Exception) {
                Timber.e(exception, "DEBUG_FOLLOWERS: Error loading followers")
                updateState { 
                    copy(
                        isLoading = false, 
                        error = "Failed to load followers: ${exception.message}"
                    ) 
                }
            }
        }
    }

    /**
     * Follows a user
     */
    private fun followUser(userId: String) {
        viewModelScope.launch {
            try {
                Timber.d("DEBUG_FOLLOW_ACTION: Starting follow action for userId: $userId")
                updateState { copy(isLoading = true) }
                
                val result = socialRelationshipUseCase.followAction(
                    targetUserId = userId,
                    action = FollowAction.FOLLOW,
                    context = "SOCIAL_SCREEN"
                )
                
                Timber.d("DEBUG_FOLLOW_ACTION: socialRelationshipUseCase.follow result: ${if (result.isSuccess) "SUCCESS" else "FAILURE"}")
                
                result.fold(
                    onSuccess = { followStatus ->
                        Timber.d("DEBUG_FOLLOW_ACTION: Follow successful, status: $followStatus")
                        // Refresh following list
                        loadFollowing()
                        val currentUserId = authRepository.getCurrentUserId()
                        if (currentUserId != null) {
                            analyticsService.logSocialFeedEvent(
                                userId = currentUserId.value,
                                eventType = "user_followed",
                                additionalData = mapOf("target_user_id" to userId)
                            )
                        }
                    },
                    onFailure = { error ->
                        Timber.e("DEBUG_FOLLOW_ACTION: Follow failed with error: ${error.message}")
                        updateState { 
                            copy(
                                isLoading = false, 
                                error = "Failed to follow user: ${error.message}"
                            ) 
                        }
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "DEBUG_FOLLOW_ACTION: Error following user")
                updateState { 
                    copy(
                        isLoading = false, 
                        error = "Failed to follow user: ${exception.message}"
                    ) 
                }
            }
        }
    }

    /**
     * Unfollows a user
     */
    private fun unfollowUser(userId: String) {
        viewModelScope.launch {
            try {
                Timber.d("DEBUG_UNFOLLOW_ACTION: Starting unfollow action for userId: $userId")
                updateState { copy(isLoading = true) }
                
                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId == null) {
                    updateState { copy(isLoading = false, error = "User not authenticated") }
                    return@launch
                }
                
                // Use FollowRepository to unfollow
                val result = followRepository.unfollowUser(
                    followerId = currentUserId.value,
                    targetUserId = userId
                )
                
                result.fold(
                    onSuccess = {
                        Timber.d("DEBUG_UNFOLLOW_ACTION: Unfollow successful")
                        // Refresh following list
                        loadFollowing()
                        analyticsService.logSocialFeedEvent(
                            userId = currentUserId.value,
                            eventType = "user_unfollowed",
                            additionalData = mapOf("target_user_id" to userId)
                        )
                    },
                    onFailure = { error ->
                        Timber.e("DEBUG_UNFOLLOW_ACTION: Unfollow failed with error: ${error.message}")
                        updateState { 
                            copy(
                                isLoading = false, 
                                error = "Failed to unfollow user: ${error.message}"
                            ) 
                        }
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "DEBUG_UNFOLLOW_ACTION: Error unfollowing user")
                updateState { 
                    copy(
                        isLoading = false, 
                        error = "Failed to unfollow user: ${exception.message}"
                    ) 
                }
            }
        }
    }

    /**
     * Searches for users (renamed from searchFriends)
     */
    private fun searchUsers(query: String) {
        viewModelScope.launch {
            try {
                updateState { 
                    copy(
                        searchQuery = query,
                        isSearching = query.isNotEmpty()
                    ) 
                }
                
                if (query.isBlank()) {
                    updateState { copy(searchResults = emptyList(), isSearching = false) }
                    return@launch
                }

                // Use searchUsersUseCase for user search
                val request = SearchUsersRequest(
                    query = query,
                    filters = SearchFilters(),
                    limit = 20,
                    useCache = true
                )
                
                socialSearchUseCase.searchUsers(request).fold(
                    onSuccess = { result ->
                        val users = result.users.map { searchResult ->
                            User(
                                uid = searchResult.userId,
                                email = "search-result@liftrix.app", // Placeholder email for search results
                                displayName = searchResult.displayName,
                                photoUrl = searchResult.profileImageUrl,
                                isAnonymous = false, // Search results are registered users
                                subscriptionTier = com.example.liftrix.domain.model.SubscriptionTier.FREE,
                                subscriptionStatus = com.example.liftrix.domain.model.SubscriptionStatus.EXPIRED,
                                subscriptionExpiresAt = null,
                                premiumFeaturesEnabled = false,
                                onboardingCompleted = true,
                                profileVersion = 1L,
                                createdAt = java.time.LocalDateTime.now().minusDays(30),
                                lastSignInAt = java.time.LocalDateTime.now().minusDays(1),
                                updatedAt = java.time.LocalDateTime.now()
                            )
                        }
                        updateState {
                            copy(
                                searchResults = users,
                                isSearching = false,
                                error = null
                            )
                        }
                    },
                    onFailure = { error ->
                        Timber.e("Search failed: $error")
                        updateState {
                            copy(
                                searchResults = emptyList(),
                                isSearching = false,
                                error = error.message
                            )
                        }
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Error searching users")
                updateState { 
                    copy(
                        isSearching = false, 
                        error = "Search failed: ${exception.message}"
                    ) 
                }
            }
        }
    }

    /**
     * Updates the UI state using the provided transform function
     */
    private fun updateState(transform: SocialUiState.() -> SocialUiState) {
        _uiState.value = _uiState.value.transform()
    }


    // Analytics tracking methods
    
    /**
     * Tracks social screen viewed event
     */
    private fun trackSocialScreenViewed() {
        viewModelScope.launch {
            try {
                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId != null) {
                    analyticsService.logSocialFeedEvent(
                        userId = currentUserId.value,
                        eventType = "social_screen_viewed",
                        additionalData = mapOf(
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track social screen viewed")
            }
        }
    }

    /**
     * Tracks friend request sent event
     */
    private fun trackFriendRequestSent(userId: String) {
        viewModelScope.launch {
            try {
                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId != null) {
                    analyticsService.logFriendRequestSent(
                        userId = currentUserId.value,
                        targetUserId = userId
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track friend request sent")
            }
        }
    }

    /**
     * Tracks friend request response event
     */
    private fun trackFriendRequestResponse(userId: String, accepted: Boolean) {
        viewModelScope.launch {
            try {
                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId != null) {
                    analyticsService.logFriendRequestResponse(
                        userId = currentUserId.value,
                        targetUserId = userId,
                        accepted = accepted
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track friend request response")
            }
        }
    }

    /**
     * Tracks friend removed event
     */
    private fun trackFriendRemoved(userId: String) {
        viewModelScope.launch {
            try {
                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId != null) {
                    analyticsService.logSocialFeedEvent(
                        userId = currentUserId.value,
                        eventType = "friend_removed",
                        additionalData = mapOf(
                            "target_user_id" to userId,
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track friend removed")
            }
        }
    }

    /**
     * Tracks user blocked event
     */
    private fun trackUserBlocked(userId: String) {
        viewModelScope.launch {
            try {
                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId != null) {
                    analyticsService.logSocialFeedEvent(
                        userId = currentUserId.value,
                        eventType = "user_blocked",
                        additionalData = mapOf(
                            "target_user_id" to userId,
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track user blocked")
            }
        }
    }

    /**
     * Tracks refresh performed event
     */
    private fun trackRefreshPerformed() {
        viewModelScope.launch {
            try {
                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId != null) {
                    analyticsService.logSocialFeedEvent(
                        userId = currentUserId.value,
                        eventType = "social_feed_refreshed",
                        additionalData = mapOf(
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track refresh performed")
            }
        }
    }

    /**
     * Tracks view all friends clicked event
     */
    private fun trackViewAllFriendsClicked() {
        viewModelScope.launch {
            try {
                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId != null) {
                    analyticsService.logSocialFeedEvent(
                        userId = currentUserId.value,
                        eventType = "view_all_friends_clicked",
                        additionalData = mapOf(
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track view all friends clicked")
            }
        }
    }

    /**
     * Tracks workout viewed from social feed
     */
    private fun trackWorkoutViewed(sharedWorkout: SharedWorkout) {
        viewModelScope.launch {
            try {
                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId != null) {
                    analyticsService.logSocialWorkoutViewed(
                        userId = currentUserId.value,
                        workoutId = sharedWorkout.id,
                        friendUserId = sharedWorkout.friendUserId,
                        workoutName = sharedWorkout.workoutName
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track workout viewed")
            }
        }
    }

    /**
     * Tracks workout congratulated event
     */
    private fun trackWorkoutCongratulated(sharedWorkout: SharedWorkout) {
        viewModelScope.launch {
            try {
                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId != null) {
                    analyticsService.logSocialFeedEvent(
                        userId = currentUserId.value,
                        eventType = "workout_congratulated",
                        additionalData = mapOf(
                            "workout_id" to sharedWorkout.id,
                            "friend_user_id" to sharedWorkout.friendUserId,
                            "workout_name" to sharedWorkout.workoutName,
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track workout congratulated")
            }
        }
    }

}

/**
 * UI state for the social screen
 */
data class SocialUiState(
    val isLoading: Boolean = false,
    val friends: List<Friend> = emptyList(), // Kept for backward compatibility
    val friendRequests: List<Friend> = emptyList(), // Kept for backward compatibility
    val friendWorkouts: List<SharedWorkout> = emptyList(),
    val pendingRequests: List<Friend> = emptyList(), // Kept for backward compatibility
    // New Following/Followers fields
    val following: List<Friend> = emptyList(),
    val followers: List<Friend> = emptyList(),
    val followingCount: Int = 0,
    val followersCount: Int = 0,
    // Search and UI state
    val searchQuery: String = "",
    val searchResults: List<User> = emptyList(),
    val isSearching: Boolean = false,
    val isEmpty: Boolean = true,
    val error: String? = null
)

/**
 * Events that can be triggered from the social screen UI
 */
sealed class SocialEvent : ViewModelEvent {
    object LoadData : SocialEvent()
    object RefreshData : SocialEvent()
    object Refresh : SocialEvent() // Added alias for RefreshData
    object LoadFriends : SocialEvent() // Added for friends list refresh
    // New Following/Followers events
    object LoadFollowing : SocialEvent()
    object LoadFollowers : SocialEvent()
    data class FollowUser(val userId: String) : SocialEvent()
    data class UnfollowUser(val userId: String) : SocialEvent()
    // Legacy friend request events (kept for compatibility)
    data class SendFriendRequest(val userId: String) : SocialEvent()
    data class AcceptFriendRequest(val userId: String) : SocialEvent()
    data class DeclineFriendRequest(val userId: String) : SocialEvent()
    data class RemoveFriend(val userId: String) : SocialEvent()
    data class BlockUser(val userId: String) : SocialEvent()
    // Search events
    data class SearchFriends(val query: String) : SocialEvent() // Legacy event
    data class SearchUsers(val query: String) : SocialEvent() // New event for user search
    object ViewAllFriends : SocialEvent()
    data class ViewWorkout(val sharedWorkout: SharedWorkout) : SocialEvent()
    data class CongratulateWorkout(val sharedWorkout: SharedWorkout) : SocialEvent()
    data class ToggleFollow(val targetUserId: String) : SocialEvent()
    // Navigation events
    data class NavigateToUserProfile(val userId: String) : SocialEvent()
    object ErrorDismissed : SocialEvent()
}

/**
 * Navigation events for social screen
 */
sealed class SocialNavigationEvent {
    data class NavigateToUserProfile(val userId: String) : SocialNavigationEvent()
}

/**
 * Data class for combining social data streams
 */
private data class SocialDataResult(
    val friends: List<Friend>,
    val workoutFeed: List<SharedWorkout>,
    val pendingRequests: List<Friend>
)
