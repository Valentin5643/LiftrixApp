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
import com.example.liftrix.domain.usecase.auth.GetAuthenticatedUserIdUseCase
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.domain.usecase.GetWorkoutHistoryUseCase
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.state.HomeScreenData
import com.example.liftrix.ui.common.state.FeedState
import com.example.liftrix.ui.common.state.RecommendationsState
import com.example.liftrix.ui.common.state.dataOrNull
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
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
    private val authRepository: AuthRepository,
    private val getAuthenticatedUserIdUseCase: GetAuthenticatedUserIdUseCase,
    private val analyticsService: AnalyticsService,
    private val socialRepository: SocialRepository,
    private val getWorkoutHistoryUseCase: GetWorkoutHistoryUseCase,
    errorHandler: ErrorHandler
) : BaseViewModel<UiState<HomeScreenData>, HomeEvent>(errorHandler) {

    override val _uiState = MutableStateFlow<UiState<HomeScreenData>>(UiState.Loading)


    private var currentFeedOffset = 0
    private var currentRecommendationsOffset = 0

    init {
        observeUserDataAndLoadHome()
        loadInitialData()
        trackHomeScreenViewed()
    }

    /**
     * Handles events from the UI following BaseViewModel MVI pattern
     */
    override fun handleEvent(event: HomeEvent) {
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
        }
    }

    /**
     * Override to handle loading state updates
     */
    override fun setLoadingState() {
        setState(UiState.Loading)
    }

    /**
     * Override to handle error state updates
     */
    override fun updateErrorState(error: com.example.liftrix.domain.model.error.LiftrixError) {
        val currentData = (_uiState.value as? UiState.Success)?.data ?: HomeScreenData()
        setState(UiState.Error(error, currentData))
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
        val currentData = (_uiState.value as? UiState.Success)?.data ?: HomeScreenData()
        setState(UiState.Success(transform(currentData)))
    }

    /**
     * Loads home screen data including recent workouts and statistics
     * 🔥 IMPROVED: Now used for manual refresh, reactive updates handled separately
     */
    fun loadHomeData() {
        executeUseCase(
            useCase = {
                val userId = getAuthenticatedUserIdUseCase()
                val result = workoutRepository.getRecentWorkouts(userId, RECENT_WORKOUTS_LIMIT).first()
                result
            },
            onSuccess = { recentWorkouts ->
                Timber.d("🔥 HOME-VM-DEBUG: Successfully loaded ${recentWorkouts.size} recent workouts")
                recentWorkouts.forEachIndexed { index, workout ->
                    Timber.d("🔥 HOME-VM-DEBUG: Workout[$index] - name: ${workout.name}, status: ${workout.status}, date: ${workout.date}")
                }
                
                updateHomeScreenData { it.copy(recentWorkouts = recentWorkouts) }
            },
            onError = { error ->
                Timber.e("Error in loadHomeData: ${error.message}")
            }
        )
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
                val userId = getAuthenticatedUserIdUseCase()

                updateHomeScreenData { it.copy(workoutFeedState = FeedState.Loading) }
                currentFeedOffset = 0

                val feedResult = workoutRepository.getFeedWorkouts(userId, FEED_LIMIT)
                feedResult.fold(
                    onSuccess = { workouts ->
                        val loadTime = System.currentTimeMillis() - startTime
                        analyticsService.trackFeedLoadTime(loadTime)
                        
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
                        Timber.e(exception, "Error loading feed workouts")
                        val loadTime = System.currentTimeMillis() - startTime
                        analyticsService.trackFeedLoadTime(loadTime)
                        updateHomeScreenData { 
                            it.copy(workoutFeedState = FeedState.Error(exception.message ?: "Failed to load workout feed"))
                        }
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Error in loadFeedWorkouts")
                val loadTime = System.currentTimeMillis() - startTime
                analyticsService.trackFeedLoadTime(loadTime)
                updateHomeScreenData { 
                    it.copy(workoutFeedState = FeedState.Error("Failed to load workout feed"))
                }
            }
        }
    }

    /**
     * Loads more workout feed data for pagination
     */
    fun loadMoreWorkouts() {
        val currentData = _uiState.value.dataOrNull() ?: HomeScreenData()
        val currentState = currentData.workoutFeedState
        if (currentState !is FeedState.Success || !currentState.hasMore || currentState.isLoadingMore) {
            return
        }

        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            try {
                val userId = getAuthenticatedUserIdUseCase()

                updateHomeScreenData { currentData ->
                    val updatedFeedState = when (val feedState = currentData.workoutFeedState) {
                        is FeedState.Success -> feedState.copy(isLoadingMore = true)
                        else -> feedState
                    }
                    currentData.copy(workoutFeedState = updatedFeedState)
                }

                val moreWorkoutsResult = workoutRepository.getFeedWorkouts(userId, FEED_LIMIT)
                moreWorkoutsResult.fold(
                    onSuccess = { newWorkouts ->
                        val loadTime = System.currentTimeMillis() - startTime
                        analyticsService.trackFeedLoadTime(loadTime)
                        
                        val currentData = (_uiState.value as? UiState.Success)?.data ?: HomeScreenData()
                        val existingWorkouts = when (val feedState = currentData.workoutFeedState) {
                            is FeedState.Success -> feedState.workouts
                            else -> emptyList()
                        }
                        val allWorkouts = existingWorkouts + newWorkouts
                        val hasMore = newWorkouts.size == FEED_LIMIT && allWorkouts.size < MAX_FEED_WORKOUTS
                        val showEndMessage = allWorkouts.size >= MAX_FEED_WORKOUTS
                        
                        updateHomeScreenData { 
                            it.copy(
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
                        updateHomeScreenData { currentData ->
                            val updatedFeedState = when (val feedState = currentData.workoutFeedState) {
                                is FeedState.Success -> feedState.copy(isLoadingMore = false)
                                else -> feedState
                            }
                            currentData.copy(workoutFeedState = updatedFeedState)
                        }
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Error in loadMoreWorkouts")
                val loadTime = System.currentTimeMillis() - startTime
                analyticsService.trackFeedLoadTime(loadTime)
                updateHomeScreenData { currentData ->
                    val updatedFeedState = when (val feedState = currentData.workoutFeedState) {
                        is FeedState.Success -> feedState.copy(isLoadingMore = false)
                        else -> feedState
                    }
                    currentData.copy(workoutFeedState = updatedFeedState)
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
                updateHomeScreenData { it.copy(recommendationsState = RecommendationsState.Loading) }
                currentRecommendationsOffset = 0

                socialRepository.getRecommendedUsers(RECOMMENDATIONS_LIMIT, 0)
                    .catch { exception ->
                        Timber.e(exception, "Error loading recommendations")
                        updateHomeScreenData { 
                            it.copy(recommendationsState = RecommendationsState.Error(exception.message ?: "Failed to load user recommendations"))
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
                
                socialRepository.followUser(userId).fold(
                    onSuccess = {
                        // Update recommendations state to reflect follow action
                        val currentData = uiState.value.dataOrNull() ?: HomeScreenData()
                        val currentState = currentData.recommendationsState
                        if (currentState is RecommendationsState.Success) {
                            val updatedUsers = currentState.users.map { user ->
                                if (user.userId == userId) {
                                    user.withFollowStatus(true)
                                } else {
                                    user
                                }
                            }
                            updateHomeScreenData {
                                it.copy(recommendationsState = currentState.copy(users = updatedUsers))
                            }
                        }
                        trackUserFollowed(userId)
                    },
                    onFailure = { exception ->
                        Timber.e(exception, "Failed to follow user: $userId")
                        updateErrorState(com.example.liftrix.domain.model.error.LiftrixError.NetworkError(
                            "Failed to follow user: ${exception.message}"
                        ))
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Error in followUser")
                updateErrorState(com.example.liftrix.domain.model.error.LiftrixError.NetworkError(
                    "Failed to follow user"
                ))
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
                    onFailure = { exception ->
                        Timber.e(exception, "Failed to unfollow user: $userId")
                        updateErrorState(com.example.liftrix.domain.model.error.LiftrixError.NetworkError(
                            "Failed to unfollow user: ${exception.message}"
                        ))
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Error in unfollowUser")
                updateErrorState(com.example.liftrix.domain.model.error.LiftrixError.NetworkError(
                    "Failed to unfollow user"
                ))
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
                    updateErrorState(com.example.liftrix.domain.model.error.LiftrixError.AuthenticationError(
                        "Authentication error"
                    ))
                }
                .collect { user ->
                    if (user != null) {
                        // Start observing workout data reactively
                        observeWorkoutDataReactively(user.uid)
                    } else {
                        updateHomeScreenData { 
                            it.copy(recentWorkouts = emptyList()) 
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
                    updateErrorState(com.example.liftrix.domain.model.error.LiftrixError.DataRetrievalError(
                        "Failed to load workout data: ${throwable.message}"
                    ))
                }
                .collect { result ->
                    result.fold(
                        onSuccess = { recentWorkouts ->
                            Timber.d("🔥 HOME-REACTIVE: Received ${recentWorkouts.size} recent workouts")
                            updateHomeScreenData {
                                it.copy(recentWorkouts = recentWorkouts)
                            }
                        },
                        onFailure = { throwable ->
                            Timber.e(throwable, "Error in reactive workout observation")
                            updateErrorState(com.example.liftrix.domain.model.error.LiftrixError.DataRetrievalError(
                                throwable.message ?: "Failed to load workouts"
                            ))
                            updateHomeScreenData {
                                it.copy(recentWorkouts = emptyList())
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
    private val workoutStatsFlow: StateFlow<WorkoutStats> = currentUserIdFlow
        .filter { it.isNotEmpty() }
        .flatMapLatest { userId ->
            flow {
                try {
                    val result = workoutRepository.getWorkoutStats(userId)
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
        private const val RECOMMENDATIONS_LIMIT = 10
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


 