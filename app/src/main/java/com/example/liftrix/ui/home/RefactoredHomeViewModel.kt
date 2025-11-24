package com.example.liftrix.ui.home

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.state.HomeScreenData
import com.example.liftrix.ui.common.state.FeedState
import com.example.liftrix.ui.common.state.RecommendationsState
import com.example.liftrix.ui.common.state.dataOrNull
import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
import com.example.liftrix.ui.home.managers.HomeDataManager
import com.example.liftrix.ui.home.managers.HomeAnalyticsManager
import com.example.liftrix.ui.home.managers.HomeFeedManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Refactored HomeViewModel using manager pattern for clean separation of concerns.
 *
 * This version delegates responsibilities to specialized managers:
 * - HomeDataManager: Data loading and processing
 * - HomeAnalyticsManager: Analytics event tracking
 * - HomeFeedManager: Social feed and recommendations
 *
 * Maintains backward compatibility while following Single Responsibility Principle.
 * Each manager is <200 lines, focused on a single concern, and testable in isolation.
 */
@HiltViewModel
class RefactoredHomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val authQueryUseCase: AuthQueryUseCase,
    private val homeDataManager: HomeDataManager,
    private val homeAnalyticsManager: HomeAnalyticsManager,
    private val homeFeedManager: HomeFeedManager
) : ModernBaseViewModel<UiState<HomeScreenData>>(initialState = UiState.Loading) {
    
    // Feed and recommendations state
    private val _feedState = MutableStateFlow<FeedState>(FeedState.Loading)
    val feedState: StateFlow<FeedState> = _feedState.asStateFlow()
    
    private val _recommendationsState = MutableStateFlow<RecommendationsState>(RecommendationsState.Loading)
    val recommendationsState: StateFlow<RecommendationsState> = _recommendationsState.asStateFlow()
    
    // Feed filter state (true = show all users/Explore, false = show only following)
    private val _showAllUsersInFeed = MutableStateFlow(false) // Default to Following

    init {
        observeUserDataAndLoadHome()
        loadInitialData()
        homeAnalyticsManager.trackHomeScreenViewed(viewModelScope)
    }

    /**
     * Handles events from the UI following MVI pattern
     */
    fun handleEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.RefreshData -> {
                refreshData()
                homeAnalyticsManager.trackRefreshPerformed(viewModelScope)
            }
            is HomeEvent.LoadMoreWorkouts -> {
                loadMoreWorkouts()
            }
            is HomeEvent.LoadMoreRecommendations -> {
                loadMoreRecommendations()
            }
            is HomeEvent.RefreshFeed -> {
                refreshFeed()
            }
            is HomeEvent.FollowUser -> {
                followUser(event.userId)
            }
            is HomeEvent.UnfollowUser -> {
                unfollowUser(event.userId)
            }
            is HomeEvent.WorkoutOpened -> {
                homeAnalyticsManager.trackWorkoutOpened(event.workout, viewModelScope)
            }
            is HomeEvent.FeedWorkoutOpened -> {
                homeAnalyticsManager.trackFeedWorkoutOpened(event.feedWorkout, viewModelScope)
            }
            is HomeEvent.ErrorDismissed -> {
                clearError()
            }
            is HomeEvent.FeedErrorDismissed -> {
                _feedState.value = FeedState.Loading
                loadFeedWorkouts()
            }
            is HomeEvent.RecommendationsErrorDismissed -> {
                _recommendationsState.value = RecommendationsState.Loading
                loadRecommendations()
            }
            is HomeEvent.ToggleFeedFilter -> {
                // Following tab: show only people you follow (includeOthers = false)
                // Explore tab: show all users (includeOthers = true)
                if (event.showFollowing) {
                    // Following selected - only show people you follow
                    _showAllUsersInFeed.value = false
                    Timber.d("🔥 HOME-FILTER: Switched to Following (only people you follow)")
                } else {
                    // Explore selected - show all users
                    _showAllUsersInFeed.value = true
                    Timber.d("🔥 HOME-FILTER: Switched to Explore (all users)")
                }
                loadFeedWorkouts()
            }
        }
    }

    private fun handleError(error: com.example.liftrix.domain.model.error.LiftrixError) {
        val currentData = uiState.value.dataOrNull()
        setState(UiState.Error(error, currentData))
    }

    /**
     * Clears the current error state and restores previous data if available.
     */
    private fun clearError() {
        updateState { currentState ->
            when (currentState) {
                is UiState.Error -> UiState.Success(currentState.previousData ?: HomeScreenData())
                else -> currentState
            }
        }
    }


    /**
     * Loads initial data including home screen data, feed, and recommendations.
     */
    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { "" }
                )
                if (userId.isNotEmpty()) {
                    // Load all data concurrently
                    launch { loadHomeData(userId) }
                    launch { loadFeedWorkouts() }
                    launch { loadRecommendations() }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading initial data")
                handleError(com.example.liftrix.domain.model.error.LiftrixError.DataRetrievalError(e.message ?: "Unknown error"))
            }
        }
    }

    /**
     * Loads home screen data using HomeDataManager.
     */
    private suspend fun loadHomeData(userId: String) {
        homeDataManager.loadHomeData(userId).collect { result ->
            result.fold(
                onSuccess = { homeData ->
                    setState(UiState.Success(homeData))
                },
                onFailure = { error ->
                    handleError(com.example.liftrix.domain.model.error.LiftrixError.DataRetrievalError(error.message ?: "Unknown error"))
                }
            )
        }
    }

    /**
     * Refreshes all data.
     */
    private fun refreshData() {
        viewModelScope.launch {
            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { "" }
                )
                if (userId.isNotEmpty()) {
                    setState(UiState.Loading)
                    // Refresh all data concurrently
                    launch {
                        homeDataManager.refreshHomeData(userId).collect { result ->
                            result.fold(
                                onSuccess = { homeData ->
                                    setState(UiState.Success(homeData))
                                },
                                onFailure = { error -> handleError(com.example.liftrix.domain.model.error.LiftrixError.DataRetrievalError(error.message ?: "Unknown error")) }
                            )
                        }
                    }
                    launch { refreshFeed() }
                    launch { loadRecommendations() }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error refreshing data")
                handleError(com.example.liftrix.domain.model.error.LiftrixError.DataRetrievalError(e.message ?: "Unknown error"))
            }
        }
    }

    /**
     * Loads feed workouts using HomeFeedManager.
     */
    private fun loadFeedWorkouts() {
        viewModelScope.launch {
            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { "" }
                )
                if (userId.isNotEmpty()) {
                    _feedState.value = FeedState.Loading
                    
                    // Load feed with proper filtering based on Following/Explore selection
                    val includeOthers = _showAllUsersInFeed.value
                    
                    
                    // Pass the includeOthers parameter to filter feed content
                    homeFeedManager.loadFeedWorkouts(userId, includeOthers).collect { result ->
                        result.fold(
                            onSuccess = { feedState ->
                                _feedState.value = feedState
                                val workoutCount = if (feedState is FeedState.Success) feedState.workouts.size else 0
                                homeAnalyticsManager.trackFeedLoaded(
                                    workoutCount, 
                                    viewModelScope
                                )
                            },
                            onFailure = { error ->
                                _feedState.value = FeedState.Error(error.message ?: "Unknown error")
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading feed workouts")
                _feedState.value = FeedState.Error(e.message ?: "Error loading feed workouts")
            }
        }
    }

    /**
     * Loads more feed workouts for pagination.
     */
    private fun loadMoreWorkouts() {
        viewModelScope.launch {
            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { "" }
                )
                val currentState = _feedState.value
                val currentWorkouts = if (currentState is FeedState.Success) currentState.workouts else emptyList()

                if (userId.isNotEmpty() && currentState is FeedState.Success && currentState.hasMore && !currentState.isLoadingMore) {
                    _feedState.value = FeedState.Loading
                    
                    val includeOthers = _showAllUsersInFeed.value
                    homeFeedManager.loadMoreFeedWorkouts(userId, currentWorkouts, includeOthers).collect { result ->
                        result.fold(
                            onSuccess = { feedState ->
                                _feedState.value = feedState
                                val newWorkoutsCount = if (feedState is FeedState.Success) feedState.workouts.size - currentWorkouts.size else 0
                                homeAnalyticsManager.trackMoreWorkoutsLoaded(
                                    newWorkoutsCount,
                                    viewModelScope
                                )
                            },
                            onFailure = { error ->
                                _feedState.value = FeedState.Error(error.message ?: "Unknown error")
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading more workouts")
                _feedState.value = FeedState.Error(e.message ?: "Error occurred")
            }
        }
    }

    /**
     * Refreshes the feed using HomeFeedManager.
     */
    private fun refreshFeed() {
        viewModelScope.launch {
            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { "" }
                )
                if (userId.isNotEmpty()) {
                    _feedState.value = FeedState.Loading
                    
                    val includeOthers = _showAllUsersInFeed.value
                    homeFeedManager.refreshFeed(userId, includeOthers).collect { result ->
                        result.fold(
                            onSuccess = { feedState ->
                                _feedState.value = feedState
                                homeAnalyticsManager.trackFeedRefreshed(viewModelScope)
                            },
                            onFailure = { error ->
                                _feedState.value = FeedState.Error(error.message ?: "Unknown error")
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error refreshing feed")
                _feedState.value = FeedState.Error(e.message ?: "Error occurred")
            }
        }
    }

    /**
     * Loads user recommendations using HomeFeedManager.
     */
    private fun loadRecommendations() {
        viewModelScope.launch {
            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { "" }
                )
                if (userId.isNotEmpty()) {
                    _recommendationsState.value = RecommendationsState.Loading
                    
                    homeFeedManager.loadRecommendations(userId).collect { result ->
                        result.fold(
                            onSuccess = { recommendationsState ->
                                _recommendationsState.value = recommendationsState
                                val userCount = if (recommendationsState is RecommendationsState.Success) recommendationsState.users.size else 0
                                homeAnalyticsManager.trackRecommendationsLoaded(
                                    userCount,
                                    viewModelScope
                                )
                            },
                            onFailure = { error ->
                                _recommendationsState.value = RecommendationsState.Error(error.message ?: "Error occurred")
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading recommendations")
                _recommendationsState.value = RecommendationsState.Error(e.message ?: "Error occurred")
            }
        }
    }

    /**
     * Loads more recommendations for pagination.
     */
    private fun loadMoreRecommendations() {
        viewModelScope.launch {
            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { "" }
                )
                val currentState = _recommendationsState.value
                val currentUsers = if (currentState is RecommendationsState.Success) currentState.users else emptyList()

                if (userId.isNotEmpty() && currentState is RecommendationsState.Success && currentState.hasMore && !currentState.isLoadingMore) {
                    _recommendationsState.value = RecommendationsState.Loading
                    
                    homeFeedManager.loadMoreRecommendations(userId, currentUsers).collect { result ->
                        result.fold(
                            onSuccess = { recommendationsState ->
                                _recommendationsState.value = recommendationsState
                                val newUsersCount = if (recommendationsState is RecommendationsState.Success) recommendationsState.users.size - currentUsers.size else 0
                                homeAnalyticsManager.trackMoreRecommendationsLoaded(
                                    newUsersCount,
                                    viewModelScope
                                )
                            },
                            onFailure = { error ->
                                _recommendationsState.value = RecommendationsState.Error(error.message ?: "Error occurred")
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading more recommendations")
                _recommendationsState.value = RecommendationsState.Error(e.message ?: "Error occurred")
            }
        }
    }

    /**
     * Follows a user using HomeFeedManager.
     */
    private fun followUser(userIdToFollow: String) {
        viewModelScope.launch {
            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { "" }
                )
                if (userId.isNotEmpty()) {
                    homeFeedManager.followUser(userId, userIdToFollow).collect { result ->
                        result.fold(
                            onSuccess = {
                                homeAnalyticsManager.trackUserFollowed(userIdToFollow, viewModelScope)
                                // Refresh recommendations to reflect changes
                                loadRecommendations()
                            },
                            onFailure = { error ->
                                Timber.e("Error following user: ${error.message}")
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error following user: $userIdToFollow")
            }
        }
    }

    /**
     * Unfollows a user using HomeFeedManager.
     */
    private fun unfollowUser(userIdToUnfollow: String) {
        viewModelScope.launch {
            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { "" }
                )
                if (userId.isNotEmpty()) {
                    homeFeedManager.unfollowUser(userId, userIdToUnfollow).collect { result ->
                        result.fold(
                            onSuccess = {
                                homeAnalyticsManager.trackUserUnfollowed(userIdToUnfollow, viewModelScope)
                                // Refresh recommendations to reflect changes
                                loadRecommendations()
                            },
                            onFailure = { error ->
                                Timber.e("Error unfollowing user: ${error.message}")
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error unfollowing user: $userIdToUnfollow")
            }
        }
    }

    /**
     * Observes user authentication state and loads home data when authenticated.
     */
    private fun observeUserDataAndLoadHome() {
        viewModelScope.launch {
            try {
                authRepository.currentUser.collect { user ->
                    if (user != null) {
                        viewModelScope.launch {
                            val userId = authQueryUseCase(waitForAuth = false).fold(
                                onSuccess = { it },
                                onFailure = { "" }
                            )
                            if (userId.isNotEmpty()) {
                                loadHomeData(userId)
                            }
                        }
                    } else {
                        setState(UiState.Error(
                            com.example.liftrix.domain.model.error.LiftrixError.AuthenticationError(
                                errorMessage = "User not authenticated"
                            )
                        ))
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error observing user authentication state")
                handleError(com.example.liftrix.domain.model.error.LiftrixError.UnknownError(
                    errorMessage = e.message ?: "Unknown error in observeUserDataAndLoadHome"
                ))
            }
        }
    }
}