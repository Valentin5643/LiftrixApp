package com.example.liftrix.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.repository.workout.WorkoutAnalyticsDataRepository
import com.example.liftrix.domain.repository.workout.WorkoutFeedDataRepository
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.model.CardData
import com.example.liftrix.domain.model.FeedWorkout
import com.example.liftrix.domain.model.RecommendedUser
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutStats
import com.example.liftrix.domain.model.social.FollowStatus
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.SocialRepository
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.domain.usecase.social.FollowAction
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.state.HomeScreenData
import com.example.liftrix.ui.common.state.FeedState
import com.example.liftrix.ui.common.state.RecommendationsState
import com.example.liftrix.ui.common.state.dataOrNull
import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.domain.model.TrendData
import com.example.liftrix.domain.model.IconData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for home screen state management and data loading.
 * 
 * Manages home screen UI state including recent workouts, workout statistics,
 * loading states, and error handling. Integrates with WorkoutRepository for 
 * reactive data updates and AnalyticsService for usage tracking.
 * 
 * @param workoutRepository Repository for workout data operations
 * @param authRepository Repository for authentication state
 * @param analyticsService Service for analytics event tracking
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val workoutFeedDataRepository: WorkoutFeedDataRepository,
    private val workoutAnalyticsDataRepository: WorkoutAnalyticsDataRepository,
    private val authRepository: AuthRepository,
    private val authQueryUseCase: AuthQueryUseCase,
    private val analyticsService: AnalyticsService,
    private val socialRepository: SocialRepository,
    private val socialRelationshipUseCase: com.example.liftrix.domain.usecase.social.SocialRelationshipUseCase
) : ModernBaseViewModel<UiState<HomeScreenData>>(initialState = UiState.Loading) {

    private var currentFeedOffset = 0
    private var currentRecommendationsOffset = 0
    private var showAllUsersInRecentActivity = false // Default to Following (only people you follow)

    init {
        observeUserDataAndLoadHome()
        loadInitialData()
        trackHomeScreenViewed()
    }

    /**
     * Handles events from the UI following BaseViewModel MVI pattern
     */
    fun handleEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.RefreshData -> {
                refreshData()
                trackRefreshPerformed()
            }
            is HomeEvent.LoadMoreWorkouts -> {
                loadMoreWorkouts()
            }
            is HomeEvent.LoadMoreRecommendations -> {
                loadMoreRecommendations()
            }
            is HomeEvent.WorkoutOpened -> {
                trackWorkoutOpened(event.workout)
            }
            is HomeEvent.FeedWorkoutOpened -> {
                trackFeedWorkoutOpened(event.feedWorkout)
            }
            is HomeEvent.FollowUser -> {
                followUser(event.userId)
            }
            is HomeEvent.UnfollowUser -> {
                unfollowUser(event.userId)
            }
            is HomeEvent.RefreshFeed -> {
                loadFeedWorkouts()
            }
            is HomeEvent.ErrorDismissed -> {
                clearError()
            }
            is HomeEvent.FeedErrorDismissed -> {
                updateHomeScreenData { currentData -> 
                    currentData.copy(workoutFeedState = when (val current = currentData.workoutFeedState) {
                        is FeedState.Error -> FeedState.Loading
                        else -> current
                    })
                }
                loadFeedWorkouts()
            }
            is HomeEvent.RecommendationsErrorDismissed -> {
                updateHomeScreenData { currentData -> 
                    currentData.copy(recommendationsState = when (val current = currentData.recommendationsState) {
                        is RecommendationsState.Error -> RecommendationsState.Loading
                        else -> current
                    })
                }
                loadRecommendations()
            }
            is HomeEvent.ToggleFeedFilter -> {
                // Following tab: show only people you follow (includeOthers = false)
                // Explore tab: show all users (includeOthers = true)
                if (event.showFollowing) {
                    // Following selected - only show people you follow
                    showAllUsersInRecentActivity = false
                } else {
                    // Explore selected - show all users
                    showAllUsersInRecentActivity = true
                }
                loadFeedWorkouts()
            }
        }
    }


    private fun clearError() {
        updateState { currentState ->
            when (currentState) {
                is UiState.Error -> UiState.Success(currentState.previousData ?: HomeScreenData())
                else -> currentState
            }
        }
    }
    
    /**
     * Helper method to update HomeScreenData within a Success state
     */
    private fun updateHomeScreenData(transform: (HomeScreenData) -> HomeScreenData) {
        val currentData = (uiState.value as? UiState.Success)?.data ?: HomeScreenData()
        updateState { UiState.Success(transform(currentData)) }
    }

    /**
     * Loads home screen data including recent workouts and statistics
     */
    fun loadHomeData() {
        viewModelScope.launch {
            val userId = authQueryUseCase(waitForAuth = false).fold(
                onSuccess = { it?.value },
                onFailure = { null }
            )
            if (userId == null) {
                return@launch
            }

            val result = workoutRepository.getRecentWorkouts(userId, RECENT_WORKOUTS_LIMIT).first()

            result.onSuccess { recentWorkouts ->
                updateHomeScreenData { it.copy(recentWorkouts = recentWorkouts) }
            }.onFailure { error ->
                logError(error, "loadHomeData")
                Timber.e("Error in loadHomeData: ${error.message}")
            }
        }
    }

    /**
     * Refreshes home screen data manually
     */
    fun refreshData() {
        // Set refreshing state
        updateHomeScreenData { it.copy(isRefreshing = true) }
        
        // Refresh all sections
        loadHomeData()
        loadFeedWorkouts()
        loadRecommendations()
        
        // Clear refreshing state after a delay
        viewModelScope.launch {
            kotlinx.coroutines.delay(500) // Small delay to ensure all loads are initiated
            updateHomeScreenData { it.copy(isRefreshing = false) }
        }
    }

    /**
     * Loads initial data for enhanced home screen architecture
     */
    private fun loadInitialData() {
        loadFeedWorkouts()
        loadRecommendations()
    }

    /**
     * Sets up reactive workout feed data observation
     */
    fun loadFeedWorkouts() {
        // Cancel previous feed observation job
        feedObservationJob?.cancel()
        
        feedObservationJob = viewModelScope.launch {
            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it?.value },
                    onFailure = { null }
                )
                if (userId == null) {
                    Timber.e("Failed to get user ID for feed")
                    return@launch
                }

                updateHomeScreenData { it.copy(workoutFeedState = FeedState.Loading) }
                currentFeedOffset = 0

                // Load feed based on filter (Following vs Explore)
                workoutFeedDataRepository.getRecentActivityFeed(
                    userId = userId,
                    includeOthers = showAllUsersInRecentActivity,
                    limit = FEED_LIMIT
                )
                    .catch { throwable ->
                        // Properly handle cancellation vs actual errors
                        if (throwable !is kotlinx.coroutines.CancellationException) {
                            Timber.e(throwable, "Error in feed workouts flow")
                            updateHomeScreenData { 
                                it.copy(workoutFeedState = FeedState.Error("Failed to load workout feed: ${throwable.message}"))
                            }
                        } else {
                            throw throwable // Rethrow cancellation
                        }
                    }
                    .collect { result ->
                        val startTime = System.currentTimeMillis()
                        result.fold(
                            onSuccess = { workouts ->
                                val loadTime = System.currentTimeMillis() - startTime
                                analyticsService.trackFeedLoadTime(loadTime)
                                
                                workouts.forEachIndexed { index, feedWorkout ->
                                }
                                
                                val hasMore = workouts.size == FEED_LIMIT
                                updateHomeScreenData { it.copy(
                                        workoutFeedState = FeedState.Success(
                                            workouts = workouts,
                                            hasMore = hasMore,
                                            isLoadingMore = false
                                        )
                                    )
                                }
                                currentFeedOffset = workouts.size
                                trackFeedLoaded(workouts.size)
                            },
                            onFailure = { exception ->
                                Timber.e(exception, "Error in feed workouts result")
                                val loadTime = System.currentTimeMillis() - startTime
                                analyticsService.trackFeedLoadTime(loadTime)
                                updateHomeScreenData { 
                                    it.copy(workoutFeedState = FeedState.Error(exception.message ?: "Failed to load workout feed"))
                                }
                            }
                        )
                    }
            } catch (exception: Exception) {
                Timber.e(exception, "Error setting up feed workouts observation")
                updateHomeScreenData { 
                    it.copy(workoutFeedState = FeedState.Error("Failed to setup workout feed"))
                }
            }
        }
    }

    /**
     * Loads more workout feed data for pagination
     */
    fun loadMoreWorkouts() {
        val currentData = uiState.value.dataOrNull() ?: HomeScreenData()
        val currentState = currentData.workoutFeedState
        if (currentState !is FeedState.Success || !currentState.hasMore || currentState.isLoadingMore) {
            return
        }

        // With reactive feeds, we automatically get all available workouts
        // Pagination can be re-implemented later if needed with offset-based queries

        trackMoreWorkoutsLoaded(0) // Track the attempt even if we don't load more
    }

    /**
     * Loads user recommendations for discovery carousel
     */
    fun loadRecommendations() {
        // Cancel previous recommendations observation job
        recommendationsObservationJob?.cancel()
        
        recommendationsObservationJob = viewModelScope.launch {
            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it?.value },
                    onFailure = { null }
                )
                if (userId == null) {
                    Timber.e("Failed to get user ID for recommendations")
                    return@launch
                }

                updateHomeScreenData { it.copy(recommendationsState = RecommendationsState.Loading) }
                currentRecommendationsOffset = 0
                socialRepository.getRecommendedUsers(RECOMMENDATIONS_LIMIT, 0)
                    .catch { exception ->
                        // Properly handle cancellation vs actual errors
                        if (exception !is kotlinx.coroutines.CancellationException) {
                            Timber.e(exception, "Error loading recommendations")
                            updateHomeScreenData { 
                                it.copy(recommendationsState = RecommendationsState.Error(exception.message ?: "Failed to load user recommendations"))
                            }
                        } else {
                            throw exception // Rethrow cancellation
                        }
                    }
                    .collect { users ->
                        val hasMore = users.size == RECOMMENDATIONS_LIMIT
                        updateHomeScreenData { it.copy(
                                recommendationsState = RecommendationsState.Success(
                                    users = users,
                                    hasMore = hasMore,
                                    isLoadingMore = false
                                )
                            )
                        }
                        currentRecommendationsOffset = users.size
                        trackRecommendationsLoaded(users.size)
                    }
            } catch (exception: Exception) {
                Timber.e(exception, "Error in loadRecommendations")
                updateHomeScreenData { 
                    it.copy(recommendationsState = RecommendationsState.Error("Failed to load user recommendations"))
                }
            }
        }
    }

    /**
     * Loads more user recommendations for pagination
     */
    fun loadMoreRecommendations() {
        val currentData = uiState.value.dataOrNull() ?: HomeScreenData()
        val currentState = currentData.recommendationsState
        if (currentState !is RecommendationsState.Success || !currentState.hasMore || currentState.isLoadingMore) {
            return
        }

        viewModelScope.launch {
            try {
                // Track carousel scroll engagement
                analyticsService.trackUserDiscoveryEngagement(
                    action = "carousel_scroll",
                    additionalData = mapOf(
                        "user_count" to currentState.users.size,
                        "timestamp" to System.currentTimeMillis()
                    )
                )
                
                updateHomeScreenData { 
                    it.copy(recommendationsState = currentState.copy(isLoadingMore = true))
                }

                socialRepository.getRecommendedUsers(RECOMMENDATIONS_LIMIT, currentRecommendationsOffset)
                    .catch { exception ->
                        Timber.e(exception, "Error loading more recommendations")
                        updateHomeScreenData { 
                            it.copy(recommendationsState = currentState.copy(isLoadingMore = false))
                        }
                    }
                    .collect { newUsers ->
                        val allUsers = currentState.users + newUsers
                        val hasMore = newUsers.size == RECOMMENDATIONS_LIMIT

                        updateHomeScreenData { it.copy(
                                recommendationsState = RecommendationsState.Success(
                                    users = allUsers,
                                    hasMore = hasMore,
                                    isLoadingMore = false
                                )
                            )
                        }
                        currentRecommendationsOffset = allUsers.size
                        trackMoreRecommendationsLoaded(newUsers.size)
                    }
            } catch (exception: Exception) {
                Timber.e(exception, "Error in loadMoreRecommendations")
                updateHomeScreenData { 
                    it.copy(recommendationsState = currentState.copy(isLoadingMore = false))
                }
            }
        }
    }

    /**
     * Refreshes the entire feed (both workouts and recommendations)
     */
    fun refreshFeed() {
        viewModelScope.launch {
            updateHomeScreenData { it.copy(isRefreshing = true) }
            try {
                // Refresh discovery cache first
                socialRepository.refreshDiscoveryCache()
                
                // Reload data
                loadFeedWorkouts()
                loadRecommendations()
                
                trackFeedRefreshed()
            } catch (exception: Exception) {
                Timber.e(exception, "Error refreshing feed")
            } finally {
                updateHomeScreenData { it.copy(isRefreshing = false) }
            }
        }
    }

    /**
     * Follows a user through social repository
     */
    fun followUser(userId: String) {
        viewModelScope.launch {
            try {
                // Track user discovery engagement
                analyticsService.trackUserDiscoveryEngagement(
                    action = "follow_user",
                    additionalData = mapOf(
                        "target_user_id" to userId,
                        "timestamp" to System.currentTimeMillis()
                    )
                )
                
                socialRelationshipUseCase.followAction(
                    targetUserId = userId,
                    action = FollowAction.FOLLOW,
                    context = "HOME_DISCOVERY"
                ).fold(
                    onSuccess = { followStatus ->
                        // Update recommendations state to reflect follow action
                        val currentData = uiState.value.dataOrNull() ?: HomeScreenData()
                        val currentState = currentData.recommendationsState
                        if (currentState is RecommendationsState.Success) {
                            val shouldRemove = followStatus == FollowStatus.FOLLOWING ||
                                followStatus == FollowStatus.PENDING_SENT ||
                                followStatus == FollowStatus.MUTUAL_FOLLOW
                            val updatedUsers = if (shouldRemove) {
                                currentState.users.filterNot { it.userId == userId }
                            } else {
                                currentState.users.map { user ->
                                    if (user.userId == userId) {
                                        user.withFollowStatus(true)
                                    } else {
                                        user
                                    }
                                }
                            }
                            updateHomeScreenData {
                                it.copy(recommendationsState = currentState.copy(users = updatedUsers))
                            }
                        }
                        socialRepository.refreshDiscoveryCache()
                        trackUserFollowed(userId)
                    },
                    onFailure = { error: Throwable ->
                        Timber.e("Failed to follow user: $userId - ${error.message}")
                        val liftrixError = when (error) {
                            is LiftrixError -> error
                            else -> LiftrixError.BusinessLogicError(
                                code = "FOLLOW_USER_FAILED",
                                errorMessage = "Failed to follow user: ${error.message}",
                                analyticsContext = mapOf(
                                    "operation" to "FOLLOW_USER",
                                    "target_user_id" to userId
                                )
                            )
                        }
                        logError(liftrixError, "followUser")
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Error in followUser")
                logError(
                    com.example.liftrix.domain.model.error.LiftrixError.NetworkError(
                        "Failed to follow user"
                    ),
                    "followUser"
                )
            }
        }
    }

    /**
     * Unfollows a user through social repository
     */
    private fun unfollowUser(userId: String) {
        viewModelScope.launch {
            try {
                socialRelationshipUseCase.followAction(
                    targetUserId = userId,
                    action = FollowAction.UNFOLLOW,
                    context = "HOME_DISCOVERY"
                ).fold(
                    onSuccess = { followStatus ->
                        // Update recommendations state to reflect unfollow action
                        val currentData = uiState.value.dataOrNull() ?: HomeScreenData()
                        val currentState = currentData.recommendationsState
                        if (currentState is RecommendationsState.Success) {
                            val updatedUsers = currentState.users.map { user ->
                                if (user.userId == userId) {
                                    user.withFollowStatus(false)
                                } else {
                                    user
                                }
                            }
                            updateHomeScreenData {
                                it.copy(recommendationsState = currentState.copy(users = updatedUsers))
                            }
                        }
                        trackUserUnfollowed(userId)
                    },
                    onFailure = { error: Throwable ->
                        Timber.e("Failed to unfollow user: $userId - ${error.message}")
                        val liftrixError = when (error) {
                            is LiftrixError -> error
                            else -> LiftrixError.BusinessLogicError(
                                code = "UNFOLLOW_USER_FAILED",
                                errorMessage = "Failed to unfollow user: ${error.message}",
                                analyticsContext = mapOf(
                                    "operation" to "UNFOLLOW_USER",
                                    "target_user_id" to userId
                                )
                            )
                        }
                        logError(liftrixError, "unfollowUser")
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Error in unfollowUser")
                logError(
                    com.example.liftrix.domain.model.error.LiftrixError.NetworkError(
                        "Failed to unfollow user"
                    ),
                    "unfollowUser"
                )
            }
        }
    }

    private var currentObservedUserId: String? = null
    private var workoutObservationJob: kotlinx.coroutines.Job? = null
    private var feedObservationJob: kotlinx.coroutines.Job? = null
    private var recommendationsObservationJob: kotlinx.coroutines.Job? = null

    /**
     * Observes authentication state and loads data when user is available
     */
    private fun observeUserDataAndLoadHome() {
        viewModelScope.launch {
            authRepository.currentUser
                .catch { throwable ->
                    // Properly handle cancellation vs actual errors
                    if (throwable !is kotlinx.coroutines.CancellationException) {
                        Timber.e(throwable, "Error observing auth state")
                        logError(
                            com.example.liftrix.domain.model.error.LiftrixError.AuthenticationError(
                                "Authentication error"
                            ),
                            "observeUserDataAndLoadHome"
                        )
                    } else {
                        throw throwable // Rethrow cancellation to maintain structured concurrency
                    }
                }
                .collect { user ->
                    if (user != null) {
                        // Cancel previous observation job before starting new one
                        if (currentObservedUserId != user.uid) {
                            
                            // Cancel previous observation job if it exists
                            workoutObservationJob?.cancel()
                            workoutObservationJob = null
                            
                            currentObservedUserId = user.uid
                            observeWorkoutDataReactively(user.uid)
                        }
                    } else {
                        // Cancel observation job when user signs out
                        workoutObservationJob?.cancel()
                        workoutObservationJob = null
                        currentObservedUserId = null
                        updateHomeScreenData { 
                            it.copy(recentWorkouts = emptyList()) 
                        }
                    }
                }
        }
    }
    
    /**
     * This ensures Home screen updates automatically when workouts are completed
     * Uses getRecentWorkouts for user's personal workout history
     */
    private fun observeWorkoutDataReactively(userId: String) {
        // Cancel previous observation job before starting new one
        workoutObservationJob?.cancel()
        
        workoutObservationJob = viewModelScope.launch {
            
            workoutRepository.getRecentWorkouts(userId, RECENT_WORKOUTS_LIMIT)
                .catch { throwable ->
                    // Properly handle cancellation vs actual errors
                    if (throwable !is kotlinx.coroutines.CancellationException) {
                        Timber.e(throwable, "Error observing recent workouts")
                        logError(
                            com.example.liftrix.domain.model.error.LiftrixError.DataRetrievalError(
                                "Failed to load recent workouts: ${throwable.message}"
                            ),
                            "observeWorkoutDataReactively"
                        )
                    } else {
                        throw throwable // Rethrow cancellation to maintain structured concurrency
                    }
                }
                .collect { result ->
                    result.fold(
                        onSuccess = { workouts ->
                            
                            updateHomeScreenData {
                                it.copy(recentWorkouts = workouts)
                            }
                            
                        },
                        onFailure = { throwable ->
                            if (throwable !is kotlinx.coroutines.CancellationException) {
                                Timber.e(throwable, "Error in reactive workout observation")
                                logError(
                                    com.example.liftrix.domain.model.error.LiftrixError.DataRetrievalError(
                                        throwable.message ?: "Failed to load workouts"
                                    ),
                                    "observeWorkoutDataReactively"
                                )
                                updateHomeScreenData {
                                    it.copy(recentWorkouts = emptyList())
                                }
                            }
                        }
                    )
                }
        }
    }
    
    /**
     * Toggles between showing all users' workouts or just current user's workouts
     * in the Recent Activity section
     */
    fun toggleRecentActivityFilter() {
        showAllUsersInRecentActivity = !showAllUsersInRecentActivity
        
        // Get current user ID and refresh the feed
        viewModelScope.launch {
            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it?.value },
                    onFailure = { null }
                )
                if (userId != null) {
                    observeWorkoutDataReactively(userId)
                } else {
                    Timber.e("Failed to get user ID")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle recent activity filter")
            }
        }
    }

    /**
     * Tracks home screen viewed event
     */
    private fun trackHomeScreenViewed() {
        viewModelScope.launch {
            try {
                analyticsService.logEvent(
                    "home_screen_viewed",
                    mapOf(
                        "timestamp" to System.currentTimeMillis()
                    )
                )
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track home screen viewed")
            }
        }
    }

    /**
     * Tracks workout opened from home screen
     */
    private fun trackWorkoutOpened(workout: Workout) {
        viewModelScope.launch {
            try {
                analyticsService.logEvent(
                    "home_workout_opened",
                    mapOf(
                        "workout_id" to workout.id.value,
                        "workout_name" to workout.name,
                        "workout_date" to workout.date.toString(),
                        "exercise_count" to workout.exercises.size
                    )
                )
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track workout opened")
            }
        }
    }



    /**
     * Tracks refresh action performed
     */
    private fun trackRefreshPerformed() {
        viewModelScope.launch {
            try {
                analyticsService.logEvent(
                    "home_refresh_performed",
                    mapOf(
                        "timestamp" to System.currentTimeMillis()
                    )
                )
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track refresh performed")
            }
        }
    }

    /**
     * Tracks feed workout opened from home screen
     */
    private fun trackFeedWorkoutOpened(feedWorkout: FeedWorkout) {
        viewModelScope.launch {
            try {
                analyticsService.logEvent(
                    "home_feed_workout_opened",
                    mapOf(
                        "workout_id" to feedWorkout.workout.id.value,
                        "workout_name" to feedWorkout.workout.name,
                        "is_personal" to feedWorkout.isPersonal,
                        "user_id" to (feedWorkout.user?.uid ?: ""),
                        "exercise_count" to feedWorkout.workout.exercises.size
                    )
                )
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track feed workout opened")
            }
        }
    }

    /**
     * Tracks feed loaded event
     */
    private fun trackFeedLoaded(workoutCount: Int) {
        viewModelScope.launch {
            try {
                analyticsService.logEvent(
                    "home_feed_loaded",
                    mapOf(
                        "workout_count" to workoutCount,
                        "timestamp" to System.currentTimeMillis()
                    )
                )
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track feed loaded")
            }
        }
    }

    /**
     * Tracks more workouts loaded event
     */
    private fun trackMoreWorkoutsLoaded(newWorkoutCount: Int) {
        viewModelScope.launch {
            try {
                analyticsService.logEvent(
                    "home_more_workouts_loaded",
                    mapOf(
                        "new_workout_count" to newWorkoutCount,
                        "total_offset" to currentFeedOffset,
                        "timestamp" to System.currentTimeMillis()
                    )
                )
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track more workouts loaded")
            }
        }
    }

    /**
     * Tracks recommendations loaded event
     */
    private fun trackRecommendationsLoaded(userCount: Int) {
        viewModelScope.launch {
            try {
                analyticsService.logEvent(
                    "home_recommendations_loaded",
                    mapOf(
                        "user_count" to userCount,
                        "timestamp" to System.currentTimeMillis()
                    )
                )
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track recommendations loaded")
            }
        }
    }

    /**
     * Tracks more recommendations loaded event
     */
    private fun trackMoreRecommendationsLoaded(newUserCount: Int) {
        viewModelScope.launch {
            try {
                analyticsService.logEvent(
                    "home_more_recommendations_loaded",
                    mapOf(
                        "new_user_count" to newUserCount,
                        "total_offset" to currentRecommendationsOffset,
                        "timestamp" to System.currentTimeMillis()
                    )
                )
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track more recommendations loaded")
            }
        }
    }

    /**
     * Tracks feed refreshed event
     */
    private fun trackFeedRefreshed() {
        viewModelScope.launch {
            try {
                analyticsService.logEvent(
                    "home_feed_refreshed",
                    mapOf(
                        "timestamp" to System.currentTimeMillis()
                    )
                )
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track feed refreshed")
            }
        }
    }

    /**
     * Tracks user followed event
     */
    private fun trackUserFollowed(userId: String) {
        viewModelScope.launch {
            try {
                analyticsService.logEvent(
                    "home_user_followed",
                    mapOf(
                        "followed_user_id" to userId,
                        "timestamp" to System.currentTimeMillis()
                    )
                )
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track user followed")
            }
        }
    }

    /**
     * Tracks user unfollowed event
     */
    private fun trackUserUnfollowed(userId: String) {
        viewModelScope.launch {
            try {
                analyticsService.logEvent(
                    "home_user_unfollowed",
                    mapOf(
                        "unfollowed_user_id" to userId,
                        "timestamp" to System.currentTimeMillis()
                    )
                )
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track user unfollowed")
            }
        }
    }


    private val currentUserIdFlow: StateFlow<String?> =
        authRepository.currentUser
            .map { it?.uid }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    /**
     * Enhanced UI card data for new card components integration
     * Transforms existing workout data into StatCard and ActivityCard format
     */
    private val workoutStatsFlow: StateFlow<WorkoutStats> = currentUserIdFlow
        .filterNotNull()
        .flatMapLatest { userId ->
            flow {
                try {
                    val result = workoutAnalyticsDataRepository.getWorkoutStats(userId)
                    emit(result.getOrElse { WorkoutStats.EMPTY })
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load workout stats")
                    emit(WorkoutStats.EMPTY)
                }
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = WorkoutStats.EMPTY
        )

    val cardData: StateFlow<List<CardData>> = combine(
        currentUserIdFlow,
        uiState.map { it.dataOrNull()?.recentWorkouts ?: emptyList() },
        workoutStatsFlow
    ) { userId, recentWorkouts, stats ->
        
        buildList {
            // Total workouts card
            add(
                CardData.Stats(
                    title = "Total Workouts",
                    value = "${stats.totalWorkouts}",
                    subtitle = "Completed",
                    trend = if (stats.totalWorkouts > 0) TrendData.Positive(5.0f, "progress") else TrendData.Neutral("starting"),
                    icon = IconData.TrendingUp,
                    contentDescription = "Total completed workouts"
                )
            )
            
            // Recent workout activity card
            recentWorkouts.firstOrNull()?.let { recentWorkout ->
                add(
                    CardData.Activity(
                        title = "Recent Workout",
                        subtitle = recentWorkout.name ?: "Unnamed workout",
                        icon = IconData.FitnessCenter,
                        trailing = "Today",
                        contentDescription = "Most recent workout details"
                    )
                )
            } ?: add(
                CardData.Activity(
                    title = "Recent Workout", 
                    subtitle = "No recent workouts",
                    icon = IconData.FitnessCenter,
                    showChevron = false,
                    contentDescription = "No recent workout data"
                )
            )
            
            // Current streak card
            add(
                CardData.Stats(
                    title = "Current Streak",
                    value = "${stats.currentStreak}",
                    subtitle = stats.getStreakDescription(),
                    trend = if (stats.hasSignificantStreak()) TrendData.Positive(10.0f, "strong") else null,
                    icon = IconData.LocalFireDepartment,
                    contentDescription = "Current workout streak"
                )
            )
            
            // Average duration card
            add(
                CardData.Stats(
                    title = "Avg Duration", 
                    value = stats.getFormattedAverageDuration(),
                    subtitle = "Per workout",
                    icon = IconData.Schedule,
                    contentDescription = "Average workout duration"
                )
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    companion object {
        private const val RECENT_WORKOUTS_LIMIT = 7
        private const val FEED_LIMIT = 10
        private const val RECOMMENDATIONS_LIMIT = 15  // Instagram-like discovery with 15 recent public accounts
        private const val MAX_FEED_WORKOUTS = 40
    }
}

// HomeUiState is now defined in ViewModelState.kt as a proper sealed class hierarchy


/**
 * Events that can be triggered from the home screen UI
 * 
 * Supports both legacy events and new social feed interactions
 * following the MVI pattern for reactive state management.
 */
// HomeEvent moved to separate file: ui/home/HomeEvent.kt


 
