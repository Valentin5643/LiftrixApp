package com.example.liftrix.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.model.CardData
import com.example.liftrix.domain.model.FeedWorkout
import com.example.liftrix.domain.model.RecommendedUser
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutStats
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.SocialRepository
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.domain.usecase.GetWorkoutHistoryUseCase
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.components.cards.Trend
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.LocalFireDepartment

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
    private val authRepository: AuthRepository,
    private val analyticsService: AnalyticsService,
    private val socialRepository: SocialRepository,
    private val getWorkoutHistoryUseCase: GetWorkoutHistoryUseCase,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private fun updateState(update: HomeUiState.() -> HomeUiState) {
        _uiState.value = _uiState.value.update()
    }

    private var currentFeedOffset = 0
    private var currentRecommendationsOffset = 0

    init {
        observeUserDataAndLoadHome()
        loadInitialData()
        trackHomeScreenViewed()
    }

    /**
     * Handles UI events from the home screen following MVI pattern
     */
    fun onEvent(event: HomeEvent) {
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
            is HomeEvent.ErrorDismissed -> {
                updateState { copy(errorMessage = null) }
            }
            is HomeEvent.FeedErrorDismissed -> {
                updateState { 
                    copy(workoutFeedState = when (val current = uiState.value.workoutFeedState) {
                        is FeedState.Error -> FeedState.Loading
                        else -> current
                    })
                }
                loadFeedWorkouts()
            }
            is HomeEvent.RecommendationsErrorDismissed -> {
                updateState { 
                    copy(recommendationsState = when (val current = uiState.value.recommendationsState) {
                        is RecommendationsState.Error -> RecommendationsState.Loading
                        else -> current
                    })
                }
                loadRecommendations()
            }
        }
    }

    /**
     * Loads home screen data including recent workouts and statistics
     * 🔥 IMPROVED: Now used for manual refresh, reactive updates handled separately
     */
    fun loadHomeData() {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.getCurrentUser()
                if (currentUser == null) {
                    updateState { copy(isLoading = false, errorMessage = "User not authenticated") }
                    return@launch
                }

                updateState { copy(isLoading = true, errorMessage = null) }
                
                // Load recent workouts for manual refresh
                Timber.d("🔥 HOME-VM-DEBUG: Loading recent workouts for user: ${currentUser.uid}")
                
                workoutRepository.getRecentWorkouts(currentUser.uid, RECENT_WORKOUTS_LIMIT)
                .catch { throwable ->
                    Timber.e(throwable, "Error loading home data")
                    updateState { 
                        copy(
                            isLoading = false, 
                            errorMessage = "Failed to load workout data: ${throwable.message}"
                        ) 
                    }
                }
                .first() // Take only the first emission for manual refresh
                .fold(
                    onSuccess = { recentWorkouts ->
                        Timber.d("🔥 HOME-VM-DEBUG: Successfully loaded ${recentWorkouts.size} recent workouts")
                        recentWorkouts.forEachIndexed { index, workout ->
                            Timber.d("🔥 HOME-VM-DEBUG: Workout[$index] - name: ${workout.name}, status: ${workout.status}, date: ${workout.date}")
                        }
                        
                        updateState {
                            copy(
                                isLoading = false,
                                recentWorkouts = recentWorkouts,
                                errorMessage = null
                            )
                        }
                    },
                    onFailure = { throwable ->
                        Timber.e(throwable, "🔥 HOME-VM-DEBUG: Failed to load recent workouts")
                        updateState {
                            copy(
                                isLoading = false,
                                recentWorkouts = emptyList(),
                                errorMessage = throwable.message
                            )
                        }
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Error in loadHomeData")
                updateState { 
                    copy(
                        isLoading = false, 
                        errorMessage = "Failed to load home data: ${exception.message}"
                    ) 
                }
            }
        }
    }

    /**
     * Refreshes home screen data manually
     */
    fun refreshData() {
        loadHomeData()
    }

    /**
     * Loads initial data for enhanced home screen architecture
     */
    private fun loadInitialData() {
        loadFeedWorkouts()
        loadRecommendations()
    }

    /**
     * Loads workout feed data with pagination support
     */
    fun loadFeedWorkouts() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            try {
                val currentUser = authRepository.getCurrentUser()
                if (currentUser == null) {
                    updateState { 
                        copy(workoutFeedState = FeedState.Error("User not authenticated"))
                    }
                    return@launch
                }

                updateState { copy(workoutFeedState = FeedState.Loading) }
                currentFeedOffset = 0

                val feedResult = workoutRepository.getFeedWorkouts(currentUser.uid, FEED_LIMIT)
                feedResult.fold(
                    onSuccess = { workouts ->
                        val loadTime = System.currentTimeMillis() - startTime
                        analyticsService.trackFeedLoadTime(loadTime)
                        
                        val hasMore = workouts.size == FEED_LIMIT
                        updateState {
                            copy(
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
                        Timber.e(exception, "Error loading feed workouts")
                        val loadTime = System.currentTimeMillis() - startTime
                        analyticsService.trackFeedLoadTime(loadTime)
                        updateState { 
                            copy(workoutFeedState = FeedState.Error(exception.message ?: "Failed to load workout feed"))
                        }
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Error in loadFeedWorkouts")
                val loadTime = System.currentTimeMillis() - startTime
                analyticsService.trackFeedLoadTime(loadTime)
                updateState { 
                    copy(workoutFeedState = FeedState.Error("Failed to load workout feed"))
                }
            }
        }
    }

    /**
     * Loads more workout feed data for pagination
     */
    fun loadMoreWorkouts() {
        val currentState = uiState.value.workoutFeedState
        if (currentState !is FeedState.Success || !currentState.hasMore || currentState.isLoadingMore) {
            return
        }

        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            try {
                val currentUser = authRepository.getCurrentUser()
                if (currentUser == null) return@launch

                updateState { 
                    copy(workoutFeedState = currentState.copy(isLoadingMore = true))
                }

                val moreWorkoutsResult = workoutRepository.getFeedWorkouts(currentUser.uid, FEED_LIMIT)
                moreWorkoutsResult.fold(
                    onSuccess = { newWorkouts ->
                        val loadTime = System.currentTimeMillis() - startTime
                        analyticsService.trackFeedLoadTime(loadTime)
                        
                        val allWorkouts = currentState.workouts + newWorkouts
                        val hasMore = newWorkouts.size == FEED_LIMIT && allWorkouts.size < MAX_FEED_WORKOUTS
                        val showEndMessage = allWorkouts.size >= MAX_FEED_WORKOUTS

                        updateState {
                            copy(
                                workoutFeedState = FeedState.Success(
                                    workouts = allWorkouts,
                                    hasMore = hasMore,
                                    isLoadingMore = false
                                ),
                                showEndOfFeedMessage = showEndMessage
                            )
                        }
                        currentFeedOffset = allWorkouts.size
                        trackMoreWorkoutsLoaded(newWorkouts.size)
                        
                        // Track feed scroll depth for engagement analysis
                        analyticsService.trackFeedScrollDepth(allWorkouts.size)
                    },
                    onFailure = { exception ->
                        Timber.e(exception, "Error loading more workouts")
                        val loadTime = System.currentTimeMillis() - startTime
                        // Track pagination performance with <1s target
                        analyticsService.trackFeedLoadTime(loadTime)
                        updateState { 
                            copy(workoutFeedState = currentState.copy(isLoadingMore = false))
                        }
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Error in loadMoreWorkouts")
                val loadTime = System.currentTimeMillis() - startTime
                analyticsService.trackFeedLoadTime(loadTime)
                updateState { 
                    copy(workoutFeedState = currentState.copy(isLoadingMore = false))
                }
            }
        }
    }

    /**
     * Loads user recommendations for discovery carousel
     */
    fun loadRecommendations() {
        viewModelScope.launch {
            try {
                updateState { copy(recommendationsState = RecommendationsState.Loading) }
                currentRecommendationsOffset = 0

                socialRepository.getRecommendedUsers(RECOMMENDATIONS_LIMIT, 0)
                    .catch { exception ->
                        Timber.e(exception, "Error loading recommendations")
                        updateState { 
                            copy(recommendationsState = RecommendationsState.Error(exception.message ?: "Failed to load user recommendations"))
                        }
                    }
                    .collect { users ->
                        val hasMore = users.size == RECOMMENDATIONS_LIMIT
                        updateState {
                            copy(
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
                updateState { 
                    copy(recommendationsState = RecommendationsState.Error("Failed to load user recommendations"))
                }
            }
        }
    }

    /**
     * Loads more user recommendations for pagination
     */
    fun loadMoreRecommendations() {
        val currentState = uiState.value.recommendationsState
        if (currentState !is RecommendationsState.Success || !currentState.hasMore || currentState.isLoadingMore) {
            return
        }

        viewModelScope.launch {
            try {
                // Track carousel scroll engagement
                analyticsService.trackUserDiscoveryEngagement(
                    action = "carousel_scroll",
                    additionalData = mapOf(
                        "current_user_count" to currentState.users.size,
                        "timestamp" to System.currentTimeMillis()
                    )
                )
                
                updateState { 
                    copy(recommendationsState = currentState.copy(isLoadingMore = true))
                }

                socialRepository.getRecommendedUsers(RECOMMENDATIONS_LIMIT, currentRecommendationsOffset)
                    .catch { exception ->
                        Timber.e(exception, "Error loading more recommendations")
                        updateState { 
                            copy(recommendationsState = currentState.copy(isLoadingMore = false))
                        }
                    }
                    .collect { newUsers ->
                        val allUsers = currentState.users + newUsers
                        val hasMore = newUsers.size == RECOMMENDATIONS_LIMIT

                        updateState {
                            copy(
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
                updateState { 
                    copy(recommendationsState = currentState.copy(isLoadingMore = false))
                }
            }
        }
    }

    /**
     * Refreshes the entire feed (both workouts and recommendations)
     */
    fun refreshFeed() {
        viewModelScope.launch {
            updateState { copy(isRefreshing = true) }
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
                updateState { copy(isRefreshing = false) }
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
                
                socialRepository.followUser(userId).fold(
                    onSuccess = {
                        // Update recommendations state to reflect follow action
                        val currentState = uiState.value.recommendationsState
                        if (currentState is RecommendationsState.Success) {
                            val updatedUsers = currentState.users.map { user ->
                                if (user.userId == userId) {
                                    user.withFollowStatus(true)
                                } else {
                                    user
                                }
                            }
                            updateState {
                                copy(recommendationsState = currentState.copy(users = updatedUsers))
                            }
                        }
                        trackUserFollowed(userId)
                    },
                    onFailure = { exception ->
                        Timber.e(exception, "Failed to follow user: $userId")
                        updateState { 
                            copy(errorMessage = "Failed to follow user: ${exception.message}")
                        }
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Error in followUser")
                updateState { 
                    copy(errorMessage = "Failed to follow user")
                }
            }
        }
    }

    /**
     * Unfollows a user through social repository
     */
    private fun unfollowUser(userId: String) {
        viewModelScope.launch {
            try {
                socialRepository.removeFriend(userId).fold(
                    onSuccess = {
                        // Update recommendations state to reflect unfollow action
                        val currentState = uiState.value.recommendationsState
                        if (currentState is RecommendationsState.Success) {
                            val updatedUsers = currentState.users.map { user ->
                                if (user.userId == userId) {
                                    user.withFollowStatus(false)
                                } else {
                                    user
                                }
                            }
                            updateState {
                                copy(recommendationsState = currentState.copy(users = updatedUsers))
                            }
                        }
                        trackUserUnfollowed(userId)
                    },
                    onFailure = { exception ->
                        Timber.e(exception, "Failed to unfollow user: $userId")
                        updateState { 
                            copy(errorMessage = "Failed to unfollow user: ${exception.message}")
                        }
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Error in unfollowUser")
                updateState { 
                    copy(errorMessage = "Failed to unfollow user")
                }
            }
        }
    }

    /**
     * Observes authentication state and loads data when user is available
     * 🔥 IMPROVED: Now reactively observes workout data changes
     */
    private fun observeUserDataAndLoadHome() {
        viewModelScope.launch {
            authRepository.currentUser
                .catch { throwable ->
                    Timber.e(throwable, "Error observing auth state")
                    updateState { copy(isLoading = false, errorMessage = "Authentication error") }
                }
                .collect { user ->
                    if (user != null) {
                        // Start observing workout data reactively
                        observeWorkoutDataReactively(user.uid)
                    } else {
                        updateState { 
                            copy(
                                isLoading = false,
                                recentWorkouts = emptyList(),
                                errorMessage = null
                            ) 
                        }
                    }
                }
        }
    }
    
    /**
     * 🔥 NEW: Reactively observes workout data changes
     * This ensures Home screen updates automatically when workouts are completed
     */
    private fun observeWorkoutDataReactively(userId: String) {
        viewModelScope.launch {
            // Observe recent workouts reactively
            workoutRepository.getRecentWorkouts(userId, RECENT_WORKOUTS_LIMIT)
                .catch { throwable ->
                    Timber.e(throwable, "Error observing recent workouts")
                    updateState { 
                        copy(
                            isLoading = false,
                            errorMessage = "Failed to load workout data: ${throwable.message}"
                        ) 
                    }
                }
                .collect { result ->
                    result.fold(
                        onSuccess = { recentWorkouts ->
                            Timber.d("🔥 HOME-REACTIVE: Received ${recentWorkouts.size} recent workouts")
                            updateState {
                                copy(
                                    isLoading = false,
                                    recentWorkouts = recentWorkouts,
                                    errorMessage = null
                                )
                            }
                        },
                        onFailure = { throwable ->
                            Timber.e(throwable, "Error in reactive workout observation")
                            updateState {
                                copy(
                                    isLoading = false,
                                    recentWorkouts = emptyList(),
                                    errorMessage = throwable.message
                                )
                            }
                        }
                    )
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

    /**
     * Sets the loading state for the home screen
     */
    private fun setLoadingState() {
        updateState { copy(isLoading = true, errorMessage = null) }
    }

    /**
     * Updates state to reflect error condition
     */
    private fun updateErrorState(error: com.example.liftrix.domain.model.error.LiftrixError) {
        updateState { copy(isLoading = false, errorMessage = error.message) }
    }

    private val currentUserIdFlow: StateFlow<String> = 
        authRepository.currentUser
            .map { it?.uid ?: "" }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = ""
            )

    /**
     * Enhanced UI card data for new card components integration
     * Transforms existing workout data into StatCard and ActivityCard format
     */
    val cardData: StateFlow<List<CardData>> = combine(
        currentUserIdFlow,
        uiState.map { it.recentWorkouts }
    ) { userId, recentWorkouts ->
        val stats = if (userId.isNotEmpty()) {
            try {
                workoutRepository.getWorkoutStats(userId).getOrElse { WorkoutStats.EMPTY }
            } catch (e: Exception) {
                WorkoutStats.EMPTY
            }
        } else {
            WorkoutStats.EMPTY
        }
        
        buildList {
            // Total workouts card
            add(
                CardData.Stats(
                    title = "Total Workouts",
                    value = "${stats.totalWorkouts}",
                    subtitle = "Completed",
                    trend = if (stats.totalWorkouts > 0) Trend.Positive(5.0f, "progress") else Trend.Neutral("starting"),
                    icon = Icons.Default.TrendingUp,
                    contentDescription = "Total completed workouts"
                )
            )
            
            // Recent workout activity card
            recentWorkouts.firstOrNull()?.let { recentWorkout ->
                add(
                    CardData.Activity(
                        title = "Recent Workout",
                        subtitle = recentWorkout.name ?: "Unnamed workout",
                        icon = Icons.Default.FitnessCenter,
                        trailing = "Today",
                        contentDescription = "Most recent workout details"
                    )
                )
            } ?: add(
                CardData.Activity(
                    title = "Recent Workout", 
                    subtitle = "No recent workouts",
                    icon = Icons.Default.FitnessCenter,
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
                    trend = if (stats.hasSignificantStreak()) Trend.Positive(10.0f, "strong") else null,
                    icon = Icons.Default.LocalFireDepartment,
                    contentDescription = "Current workout streak"
                )
            )
            
            // Average duration card
            add(
                CardData.Stats(
                    title = "Avg Duration", 
                    value = stats.getFormattedAverageDuration(),
                    subtitle = "Per workout",
                    icon = Icons.Default.Schedule,
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
        private const val RECOMMENDATIONS_LIMIT = 10
        private const val MAX_FEED_WORKOUTS = 40
    }
}

/**
 * Comprehensive UI state for the enhanced home screen architecture
 * 
 * Manages state for workout feed and user recommendations with proper loading states,
 * pagination support, and error handling following Material 3 design principles.
 */
data class HomeUiState(
    val workoutFeedState: FeedState = FeedState.Loading,
    val recommendationsState: RecommendationsState = RecommendationsState.Loading,
    val showEndOfFeedMessage: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val workoutStats: WorkoutStats? = null,
    // Legacy properties for backward compatibility during transition
    val recentWorkouts: List<Workout> = emptyList(),
    val isLoading: Boolean = false
) {
    /**
     * Indicates if the screen should show empty state
     * Empty when there are no recent workouts and not loading
     */
    val shouldShowEmptyState: Boolean
        get() = recentWorkouts.isEmpty() &&
                !isLoading &&
                !isRefreshing &&
                errorMessage == null

    /**
     * Indicates if the screen should show error state
     * Shows error when there's a global error message
     */
    val shouldShowError: Boolean
        get() = errorMessage != null

    /**
     * Indicates if the screen should show content
     * Shows content when there are workouts and no error
     */
    val shouldShowContent: Boolean
        get() = !shouldShowEmptyState && !shouldShowError

    /**
     * Indicates if any initial loading is happening
     * True when data is being loaded
     */
    val isInitialLoading: Boolean
        get() = isLoading

    /**
     * Gets the global error message for display
     */
    val displayErrorMessage: String?
        get() = errorMessage
}

/**
 * Sealed class representing the state of the workout feed
 * 
 * Supports initial loading, paginated data loading, and error states
 * following reactive programming patterns.
 */
sealed class FeedState {
    /**
     * Initial loading state when no feed data is available
     */
    object Loading : FeedState()
    
    /**
     * Success state containing workout feed data with pagination support
     * 
     * @property workouts List of feed workouts in chronological order
     * @property hasMore True if more workouts can be loaded via pagination
     * @property isLoadingMore True when additional workouts are being loaded
     */
    data class Success(
        val workouts: List<FeedWorkout>,
        val hasMore: Boolean,
        val isLoadingMore: Boolean = false
    ) : FeedState() {
        
        /**
         * Indicates if the feed has data to display
         */
        val hasData: Boolean get() = workouts.isNotEmpty()
        
        /**
         * Gets the count of workouts for analytics and UI display
         */
        val workoutCount: Int get() = workouts.size
    }
    
    /**
     * Error state when feed loading fails
     * 
     * @property message User-friendly error message for display
     */
    data class Error(val message: String) : FeedState()
}

/**
 * Sealed class representing the state of user recommendations
 * 
 * Supports lazy loading with pagination and caching for optimal performance.
 */
sealed class RecommendationsState {
    /**
     * Initial loading state when no recommendation data is available
     */
    object Loading : RecommendationsState()
    
    /**
     * Success state containing user recommendations with pagination support
     * 
     * @property users List of recommended users for discovery carousel
     * @property hasMore True if more recommendations can be loaded
     * @property isLoadingMore True when additional recommendations are being loaded
     */
    data class Success(
        val users: List<RecommendedUser>,
        val hasMore: Boolean,
        val isLoadingMore: Boolean = false
    ) : RecommendationsState() {
        
        /**
         * Indicates if recommendations have data to display
         */
        val hasData: Boolean get() = users.isNotEmpty()
        
        /**
         * Gets the count of recommendations for carousel display
         */
        val userCount: Int get() = users.size
        
        /**
         * Checks if cache is valid for all recommendations
         */
        val isCacheValid: Boolean get() = users.all { it.isCacheValid }
    }
    
    /**
     * Error state when recommendation loading fails
     * 
     * @property message User-friendly error message for display
     */
    data class Error(val message: String) : RecommendationsState()
}

/**
 * Events that can be triggered from the home screen UI
 * 
 * Supports both legacy events and new social feed interactions
 * following the MVI pattern for reactive state management.
 */
sealed class HomeEvent : com.example.liftrix.ui.common.event.ViewModelEvent {
    // Data loading events
    object RefreshData : HomeEvent()
    object LoadMoreWorkouts : HomeEvent()
    object LoadMoreRecommendations : HomeEvent()
    
    // Workout interaction events
    data class WorkoutOpened(val workout: Workout) : HomeEvent()
    data class FeedWorkoutOpened(val feedWorkout: FeedWorkout) : HomeEvent()
    
    // Social interaction events  
    data class FollowUser(val userId: String) : HomeEvent()
    data class UnfollowUser(val userId: String) : HomeEvent()
    

    
    // Error handling events
    object ErrorDismissed : HomeEvent()
    object FeedErrorDismissed : HomeEvent()
    object RecommendationsErrorDismissed : HomeEvent()
}

 