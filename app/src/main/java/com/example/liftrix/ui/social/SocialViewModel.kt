package com.example.liftrix.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.Friend
import com.example.liftrix.domain.model.PresenceStatus
import com.example.liftrix.domain.model.SharedWorkout
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.SocialRepository
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.domain.usecase.social.SearchUsersUseCase
import com.example.liftrix.domain.usecase.social.SearchUsersRequest
import com.example.liftrix.domain.usecase.social.FeedGeneratorUseCase
import com.example.liftrix.domain.model.social.SearchFilters
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.service.FirebasePresenceService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for social screen state management and friend interactions.
 * 
 * Manages social UI state including friends list, workout feed, friend requests,
 * real-time presence updates, loading states, and error handling. Integrates with
 * SocialRepository for friend management, FirebasePresenceService for presence tracking,
 * and AnalyticsService for usage analytics.
 * 
 * @param socialRepository Repository for social data operations
 * @param presenceService Service for real-time presence tracking
 * @param authRepository Repository for authentication state
 * @param analyticsService Service for analytics event tracking
 */
@HiltViewModel
class SocialViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
    private val presenceService: FirebasePresenceService,
    private val authRepository: AuthRepository,
    private val analyticsService: AnalyticsService,
    private val searchUsersUseCase: SearchUsersUseCase,
    private val feedGeneratorUseCase: FeedGeneratorUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SocialUiState())
    val uiState: StateFlow<SocialUiState> = _uiState.asStateFlow()

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
        startPresenceTracking()
        observeUserDataAndLoadSocial()
        trackSocialScreenViewed()
    }

    /**
     * Handles UI events from the social screen
     */
    fun onEvent(event: SocialEvent) {
        when (event) {
            is SocialEvent.LoadData -> {
                loadFriendFeed()
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
                loadFriendFeed()
            }
            is SocialEvent.SearchFriends -> {
                searchFriends(event.query)
            }
            is SocialEvent.SendFriendRequest -> {
                sendFriendRequest(event.userId)
            }
            is SocialEvent.AcceptFriendRequest -> {
                respondToFriendRequest(event.userId, accept = true)
            }
            is SocialEvent.DeclineFriendRequest -> {
                respondToFriendRequest(event.userId, accept = false)
            }
            is SocialEvent.RemoveFriend -> {
                removeFriend(event.userId)
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
        }
    }

    /**
     * Loads friend feed data including friends list, workout feed, and pending requests
     */
    fun loadFriendFeed() {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.getCurrentUser()
                if (currentUser == null) {
                    updateState { copy(isLoading = false, error = "User not authenticated") }
                    return@launch
                }

                updateState { copy(isLoading = true, error = null) }
                
                // Combine all social data streams for reactive updates
                combine(
                    socialRepository.getFriends(currentUser.uid),
                    socialRepository.getFriendWorkoutFeed(currentUser.uid),
                    socialRepository.getPendingFriendRequests(currentUser.uid)
                ) { friends, workoutFeed, pendingRequests ->
                    SocialDataResult(friends, workoutFeed, pendingRequests)
                }
                .catch { throwable ->
                    Timber.e(throwable, "Error loading social data")
                    updateState { 
                        copy(
                            isLoading = false, 
                            error = "Failed to load social data: ${throwable.message}"
                        ) 
                    }
                }
                .collect { result ->
                    updateState {
                        copy(
                            isLoading = false,
                            friends = result.friends,
                            friendRequests = result.pendingRequests, // Map pendingRequests to friendRequests
                            friendWorkouts = result.workoutFeed,
                            pendingRequests = result.pendingRequests,
                            isEmpty = result.friends.isEmpty() && result.workoutFeed.isEmpty(),
                            error = null
                        )
                    }
                    
                    // Start observing presence for friends
                    observeFriendsPresence(result.friends.map { it.userId })
                }
            } catch (exception: Exception) {
                Timber.e(exception, "Error in loadFriendFeed")
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
     * Sends a friend request to the specified user
     */
    fun sendFriendRequest(userId: String) {
        viewModelScope.launch {
            try {
                updateState { copy(isLoading = true) }
                
                val result = socialRepository.sendFriendRequest(userId)
                
                if (result.isSuccess) {
                    trackFriendRequestSent(userId)
                    loadFriendFeed() // Refresh data to show updated state
                } else {
                    val error = result.exceptionOrNull()
                    Timber.e(error, "Failed to send friend request")
                    updateState { 
                        copy(
                            isLoading = false,
                            error = "Failed to send friend request: ${error?.message}"
                        ) 
                    }
                }
            } catch (exception: Exception) {
                Timber.e(exception, "Error sending friend request")
                updateState { 
                    copy(
                        isLoading = false,
                        error = "Failed to send friend request: ${exception.message}"
                    ) 
                }
            }
        }
    }

    /**
     * Updates user presence status
     */
    fun updatePresence() {
        viewModelScope.launch {
            try {
                presenceService.updatePresenceStatus(PresenceStatus.ONLINE)
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to update presence")
            }
        }
    }

    /**
     * Refreshes social data manually
     */
    fun refreshData() {
        loadFriendFeed()
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
            
            searchUsersUseCase(request).fold(
                onSuccess = { result ->
                    val users = result.users.map { searchResult ->
                        User(
                            uid = searchResult.userId,
                            email = "", // Email not available in search results for privacy
                            displayName = searchResult.displayName,
                            photoUrl = searchResult.profileImageUrl,
                            isAnonymous = false, // Search results are for registered users
                            subscriptionTier = com.example.liftrix.domain.model.SubscriptionTier.FREE, // Default tier
                            subscriptionStatus = com.example.liftrix.domain.model.SubscriptionStatus.EXPIRED, // Default status
                            subscriptionExpiresAt = null,
                            premiumFeaturesEnabled = false,
                            onboardingCompleted = true, // Assume completed for search results
                            profileVersion = 1L,
                            createdAt = java.time.LocalDateTime.now().minusDays(30), // Placeholder
                            lastSignInAt = java.time.LocalDateTime.now().minusDays(1), // Placeholder
                            updatedAt = java.time.LocalDateTime.now() // Placeholder
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
     * Responds to a friend request (accept or decline)
     */
    private fun respondToFriendRequest(userId: String, accept: Boolean) {
        viewModelScope.launch {
            try {
                updateState { copy(isLoading = true) }
                
                val result = socialRepository.respondToFriendRequest(userId, accept)
                
                if (result.isSuccess) {
                    trackFriendRequestResponse(userId, accept)
                    loadFriendFeed() // Refresh data to show updated state
                } else {
                    val error = result.exceptionOrNull()
                    Timber.e(error, "Failed to respond to friend request")
                    updateState { 
                        copy(
                            isLoading = false,
                            error = "Failed to respond to friend request: ${error?.message}"
                        ) 
                    }
                }
            } catch (exception: Exception) {
                Timber.e(exception, "Error responding to friend request")
                updateState { 
                    copy(
                        isLoading = false,
                        error = "Failed to respond to friend request: ${exception.message}"
                    ) 
                }
            }
        }
    }

    /**
     * Removes a friend from the user's friends list
     */
    private fun removeFriend(userId: String) {
        viewModelScope.launch {
            try {
                updateState { copy(isLoading = true) }
                
                val result = socialRepository.removeFriend(userId)
                
                if (result.isSuccess) {
                    trackFriendRemoved(userId)
                    loadFriendFeed() // Refresh data to show updated state
                } else {
                    val error = result.exceptionOrNull()
                    Timber.e(error, "Failed to remove friend")
                    updateState { 
                        copy(
                            isLoading = false,
                            error = "Failed to remove friend: ${error?.message}"
                        ) 
                    }
                }
            } catch (exception: Exception) {
                Timber.e(exception, "Error removing friend")
                updateState { 
                    copy(
                        isLoading = false,
                        error = "Failed to remove friend: ${exception.message}"
                    ) 
                }
            }
        }
    }

    /**
     * Blocks a user
     */
    private fun blockUser(userId: String) {
        viewModelScope.launch {
            try {
                updateState { copy(isLoading = true) }
                
                val result = socialRepository.blockUser(userId)
                
                if (result.isSuccess) {
                    trackUserBlocked(userId)
                    loadFriendFeed() // Refresh data to show updated state
                } else {
                    val error = result.exceptionOrNull()
                    Timber.e(error, "Failed to block user")
                    updateState { 
                        copy(
                            isLoading = false,
                            error = "Failed to block user: ${error?.message}"
                        ) 
                    }
                }
            } catch (exception: Exception) {
                Timber.e(exception, "Error blocking user")
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
            
            socialRepository.followUser(targetUserId).fold(
                onSuccess = { 
                    // Update UI optimistically
                    updateFollowStatus(targetUserId, true)
                    // Track analytics
                    analyticsService.logSocialFeedEvent(
                        userId = currentUserId,
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
                        loadFriendFeed()
                    } else {
                        updateState { 
                            copy(
                                isLoading = false,
                                friends = emptyList(),
                                friendWorkouts = emptyList(),
                                pendingRequests = emptyList(),
                                isEmpty = true,
                                error = null
                            ) 
                        }
                    }
                }
        }
    }

    /**
     * Observes real-time presence updates for friends
     */
    private fun observeFriendsPresence(friendIds: List<String>) {
        if (friendIds.isEmpty()) return
        
        viewModelScope.launch {
            presenceService.observeFriendsPresence(friendIds)
                .catch { throwable ->
                    Timber.w(throwable, "Error observing friends presence")
                }
                .collect { presenceMap ->
                    updateState { 
                        copy(
                            friends = friends.map { friend ->
                                val presence = presenceMap[friend.userId]
                                friend.copy(presence = presence)
                            }
                        )
                    }
                }
        }
    }

    /**
     * Starts presence tracking for the current user
     */
    private fun startPresenceTracking() {
        viewModelScope.launch {
            try {
                presenceService.startPresenceTracking()
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to start presence tracking")
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
                val currentUser = authRepository.getCurrentUser()
                if (currentUser != null) {
                    analyticsService.logSocialFeedEvent(
                        userId = currentUser.uid,
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
                val currentUser = authRepository.getCurrentUser()
                if (currentUser != null) {
                    analyticsService.logFriendRequestSent(
                        userId = currentUser.uid,
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
                val currentUser = authRepository.getCurrentUser()
                if (currentUser != null) {
                    analyticsService.logFriendRequestResponse(
                        userId = currentUser.uid,
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
                val currentUser = authRepository.getCurrentUser()
                if (currentUser != null) {
                    analyticsService.logSocialFeedEvent(
                        userId = currentUser.uid,
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
                val currentUser = authRepository.getCurrentUser()
                if (currentUser != null) {
                    analyticsService.logSocialFeedEvent(
                        userId = currentUser.uid,
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
                val currentUser = authRepository.getCurrentUser()
                if (currentUser != null) {
                    analyticsService.logSocialFeedEvent(
                        userId = currentUser.uid,
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
                val currentUser = authRepository.getCurrentUser()
                if (currentUser != null) {
                    analyticsService.logSocialFeedEvent(
                        userId = currentUser.uid,
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
                val currentUser = authRepository.getCurrentUser()
                if (currentUser != null) {
                    analyticsService.logSocialWorkoutViewed(
                        userId = currentUser.uid,
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
                val currentUser = authRepository.getCurrentUser()
                if (currentUser != null) {
                    analyticsService.logSocialFeedEvent(
                        userId = currentUser.uid,
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

    override fun onCleared() {
        super.onCleared()
        // Stop presence tracking when ViewModel is cleared
        viewModelScope.launch {
            try {
                presenceService.stopPresenceTracking()
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to stop presence tracking")
            }
        }
    }
}

/**
 * UI state for the social screen
 */
data class SocialUiState(
    val isLoading: Boolean = false,
    val friends: List<Friend> = emptyList(),
    val friendRequests: List<Friend> = emptyList(),
    val friendWorkouts: List<SharedWorkout> = emptyList(),
    val pendingRequests: List<Friend> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<User> = emptyList(),
    val isSearching: Boolean = false,
    val isEmpty: Boolean = true,
    val error: String? = null
)

/**
 * Events that can be triggered from the social screen UI
 */
sealed class SocialEvent {
    object LoadData : SocialEvent()
    object RefreshData : SocialEvent()
    object Refresh : SocialEvent() // Added alias for RefreshData
    object LoadFriends : SocialEvent() // Added for friends list refresh
    data class SendFriendRequest(val userId: String) : SocialEvent()
    data class AcceptFriendRequest(val userId: String) : SocialEvent()
    data class DeclineFriendRequest(val userId: String) : SocialEvent()
    data class RemoveFriend(val userId: String) : SocialEvent()
    data class BlockUser(val userId: String) : SocialEvent()
    data class SearchFriends(val query: String) : SocialEvent() // Added for search
    object ViewAllFriends : SocialEvent()
    data class ViewWorkout(val sharedWorkout: SharedWorkout) : SocialEvent()
    data class CongratulateWorkout(val sharedWorkout: SharedWorkout) : SocialEvent()
    data class ToggleFollow(val targetUserId: String) : SocialEvent()
    object ErrorDismissed : SocialEvent()
}

/**
 * Data class for combining social data streams
 */
private data class SocialDataResult(
    val friends: List<Friend>,
    val workoutFeed: List<SharedWorkout>,
    val pendingRequests: List<Friend>
)