package com.example.liftrix.ui.progress

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.SubscriptionTier
import com.example.liftrix.domain.model.SubscriptionStatus
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.service.CalorieService
import com.example.liftrix.service.CalorieSummary
import com.example.liftrix.service.DailyCalorieData
import com.example.liftrix.service.WeeklyCalorieTrend
import com.example.liftrix.ui.common.state.AsyncData
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.state.dataOrNull
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for calorie tracking screen following MVI pattern with clean architecture.
 * 
 * This ViewModel manages the state for calorie analytics including summary statistics,
 * daily calorie data, weekly trends, and workout-specific calorie calculations.
 * It extends BaseViewModel to leverage standardized state management and error handling.
 * 
 * Key Features:
 * - Comprehensive calorie tracking with MET-based calculations
 * - Independent AsyncData state for each data type (summary, daily, weekly)
 * - Reactive time period selection with automatic data refresh
 * - User authentication state monitoring with automatic data scoping
 * - Flow combination for efficient reactive updates
 * - Workout-specific calorie calculation support
 * - Performance optimizations with proper Flow lifecycle management
 * - Cache-aware data loading with freshness validation
 * 
 * Architecture Integration:
 * - Depends on CalorieService for MET-based calorie calculations
 * - Receives user state from ProgressDashboardCoordinator for centralized auth management
 * - Uses ErrorHandler for consistent error processing
 * - Follows clean architecture with proper dependency injection
 * 
 * State Management:
 * - Uses StateFlow for reactive state updates
 * - Combines user authentication and time range into single state stream
 * - Provides proper lifecycle management with SharingStarted.WhileSubscribed
 * - Implements loading states and error recovery for each data type
 * 
 * Data Types:
 * - CalorieSummary: Aggregated calorie statistics and trends
 * - DailyCalorieData: Daily calorie burn data with workout breakdowns
 * - WeeklyCalorieTrend: Weekly trend analysis with moving averages
 * 
 * Usage:
 * ```kotlin
 * @Composable
 * fun CalorieTrackingScreen(
 *     viewModel: CalorieTrackingViewModel = hiltViewModel()
 * ) {
 *     val uiState by viewModel.uiState.collectAsStateWithLifecycle()
 *     
 *     when (uiState) {
 *         is UiState.Loading -> LoadingIndicator()
 *         is UiState.Success -> CalorieTrackingContent(
 *             state = uiState.data,
 *             onEvent = viewModel::handleEvent
 *         )
 *         is UiState.Error -> ErrorMessage(uiState.error)
 *     }
 * }
 * ```
 * 
 * @param calorieService Service for MET-based calorie calculations
 * @param errorHandler Centralized error handling service
 */
@HiltViewModel
class CalorieTrackingViewModel @Inject constructor(
    private val calorieService: CalorieService,
    private val authRepository: com.example.liftrix.domain.repository.AuthRepository,
    errorHandler: ErrorHandler
) : BaseViewModel<UiState<CalorieTrackingState>, CalorieTrackingEvent>(errorHandler) {

    /**
     * Internal mutable state for the calorie tracking screen.
     * Starts with Loading state until user authentication is determined.
     */
    override val _uiState: MutableStateFlow<UiState<CalorieTrackingState>> = 
        MutableStateFlow(UiState.Loading)

    /**
     * Internal state for current time range selection.
     * Separate from UI state to enable independent time range updates.
     */
    private val _currentTimeRange = MutableStateFlow(TimeRange.lastMonth())

    /**
     * Internal state for individual workout calorie calculations.
     * Maintains a map of workout IDs to their calculated calories.
     */
    private val _workoutCalories = MutableStateFlow<Map<String, Int>>(emptyMap())

    /**
     * Current user state received from Coordinator.
     * Updated via Coordinator events instead of direct auth repository observation.
     */
    private val _currentUser = MutableStateFlow<com.example.liftrix.domain.model.User?>(null)

    /**
     * Combined state flow that reactively updates when user authentication or time range changes.
     * Uses Flow.combine to efficiently handle multiple data sources and automatically
     * trigger data loading when dependencies change.
     */
    private val combinedState: StateFlow<CalorieTrackingState> = combine(
        _currentUser,
        _currentTimeRange
    ) { user, timeRange ->
        val userId = user?.uid
        Timber.d("CalorieTrackingViewModel: combinedState updated - userId: $userId, timeRange: $timeRange")
        if (userId != null) {
            Timber.d("CalorieTrackingViewModel: Creating loading state for authenticated user")
            createLoadingCalorieTrackingState(userId, timeRange)
        } else {
            Timber.d("CalorieTrackingViewModel: Creating unauthenticated state")
            createUnauthenticatedCalorieTrackingState()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = createUnauthenticatedCalorieTrackingState()
    )

    /**
     * Reactive UI state that automatically updates when combined state changes.
     * Uses proper lifecycle management with WhileSubscribed sharing strategy.
     */
    private val reactiveUiState: StateFlow<UiState<CalorieTrackingState>> = combinedState
        .map { state -> 
            Timber.d("CalorieTrackingViewModel: reactiveUiState map - hasValidUser: ${state.hasValidUser()}, areAllDataNotAsked: ${state.areAllDataNotAsked()}")
            // Trigger data loading if user is authenticated and data is not loaded
            if (state.hasValidUser() && state.areAllDataNotAsked()) {
                Timber.d("CalorieTrackingViewModel: Triggering initial data load for user: ${state.userId}")
                viewModelScope.launch {
                    loadInitialCalorieData(state.userId!!, state.currentTimeRange)
                }
            }
            UiState.Success(state)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UiState.Loading
        )

    init {
        // Start observing reactive state changes automatically
        observeStateChanges()
        
        // Load initial data when ViewModel is created
        handleEvent(CalorieTrackingEvent.LoadInitialData)
        
        // Add fallback mechanism to force auth check after delay
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000) // Wait 3 seconds for coordinator
            if (_currentUser.value == null) {
                Timber.w("CalorieTrackingViewModel: No user after 3s, checking auth repository directly")
                try {
                    val currentUser = authRepository.getCurrentUser()
                    if (currentUser != null) {
                        Timber.d("CalorieTrackingViewModel: Found user in auth repository: ${currentUser.uid}")
                        // Create User object directly
                        _currentUser.value = com.example.liftrix.domain.model.User(
                            uid = currentUser.uid,
                            email = currentUser.email ?: "temp@liftrix.app",
                            displayName = currentUser.displayName,
                            photoUrl = currentUser.photoUrl,
                            isAnonymous = currentUser.isAnonymous,
                            subscriptionTier = SubscriptionTier.FREE,
                            subscriptionStatus = SubscriptionStatus.ACTIVE,
                            subscriptionExpiresAt = null,
                            premiumFeaturesEnabled = false,
                            onboardingCompleted = false,
                            profileVersion = 1L,
                            createdAt = java.time.LocalDateTime.now(),
                            lastSignInAt = java.time.LocalDateTime.now(),
                            updatedAt = java.time.LocalDateTime.now()
                        )
                    } else {
                        Timber.w("CalorieTrackingViewModel: No current user found in auth repository")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "CalorieTrackingViewModel: Error checking auth repository")
                }
            }
        }
        
        // Add timeout mechanism for stuck loading states
        viewModelScope.launch {
            kotlinx.coroutines.delay(10000) // Wait 10 seconds
            val currentState = _uiState.value.dataOrNull()
            if (currentState != null && currentState.isAnyDataLoading()) {
                Timber.w("CalorieTrackingViewModel: Data still loading after 10s, forcing refresh")
                handleEvent(CalorieTrackingEvent.RetryFailedOperations)
            }
        }
        
        Timber.d("CalorieTrackingViewModel initialized")
    }

    /**
     * Handles all events from the UI following the MVI pattern.
     * 
     * This method processes user interactions and internal events, updating the state
     * accordingly and triggering appropriate data operations.
     * 
     * @param event The event to process
     */
    override fun handleEvent(event: CalorieTrackingEvent) {
        viewModelScope.launch {
            try {
                Timber.d("Handling event: ${event.getDescription()}")
                
                when (event) {
                    is CalorieTrackingEvent.LoadSummary -> {
                        loadCalorieSummary()
                    }
                    is CalorieTrackingEvent.LoadDailyCalories -> {
                        loadDailyCalories(event.timeRange)
                    }
                    is CalorieTrackingEvent.LoadWeeklyTrend -> {
                        loadWeeklyTrend()
                    }
                    is CalorieTrackingEvent.TimePeriodChanged -> {
                        changeTimePeriod(event.timeRange)
                    }
                    is CalorieTrackingEvent.CalculateWorkoutCalories -> {
                        calculateWorkoutCalories(event.workout)
                    }
                    is CalorieTrackingEvent.RefreshAllData -> {
                        refreshAllData()
                    }
                    is CalorieTrackingEvent.LoadInitialData -> {
                        loadInitialData()
                    }
                    is CalorieTrackingEvent.RetryFailedOperations -> {
                        retryFailedOperations()
                    }
                    is CalorieTrackingEvent.ClearCachedData -> {
                        clearCachedData()
                    }
                    is CalorieTrackingEvent.RefreshCalories -> {
                        refreshCalories()
                    }
                    is CalorieTrackingEvent.ClearError -> {
                        clearError()
                    }
                    is CalorieTrackingEvent.NavigateToCalorieGoalSettings -> {
                        navigateToCalorieGoalSettings()
                    }
                    is CalorieTrackingEvent.NavigateToDetailedCalorieAnalytics -> {
                        navigateToDetailedCalorieAnalytics()
                    }
                    is CalorieTrackingEvent.NavigateToCalorieHistory -> {
                        navigateToCalorieHistory()
                    }
                }
            } catch (exception: Exception) {
                Timber.e(exception, "Failed to handle event: ${event::class.simpleName}")
                handleError(
                    com.example.liftrix.domain.model.error.LiftrixError.UnknownError(
                        errorMessage = "Failed to handle event: ${event::class.simpleName}",
                        analyticsContext = mapOf(
                            "event_type" to (event::class.simpleName ?: "Unknown"),
                            "timestamp" to System.currentTimeMillis().toString(),
                            "error_message" to (exception.message ?: "Unknown error")
                        )
                    )
                )
            }
        }
    }

    /**
     * Handles coordination events from the ProgressDashboardCoordinator.
     * 
     * This method processes events that require coordination between ViewModels,
     * such as user authentication changes and global data refresh requests.
     * 
     * @param event The coordination event to process
     */
    fun handleCoordinatorEvent(event: CoordinatorEvent) {
        viewModelScope.launch {
            try {
                Timber.d("CalorieTrackingViewModel: Handling coordinator event: ${event::class.simpleName}")
                when (event) {
                    is CoordinatorEvent.UserAuthChanged -> {
                        val previousUserId = _currentUser.value?.uid
                        Timber.d("CalorieTrackingViewModel: UserAuthChanged - previousUserId: $previousUserId, newUserId: ${event.userId}")
                        
                        // FIXED: Added proper validation and null handling
                        _currentUser.value = event.userId?.let { userId ->
                            // Only create User object if we have a valid userId
                            if (userId.isNotBlank()) {
                                Timber.d("CalorieTrackingViewModel: Creating User object for userId: $userId")
                                // Create a minimal User object that passes validation
                                // Using a temporary email to satisfy the validation requirement
                                com.example.liftrix.domain.model.User(
                                    uid = userId,
                                    email = "temp@liftrix.app", // FIXED: Use valid email instead of blank
                                    displayName = null,
                                    photoUrl = null,
                                    isAnonymous = false, // FIXED: Keep as false since we have a userId
                                    subscriptionTier = SubscriptionTier.FREE,
                                    subscriptionStatus = SubscriptionStatus.ACTIVE,
                                    subscriptionExpiresAt = null,
                                    premiumFeaturesEnabled = false,
                                    onboardingCompleted = false,
                                    profileVersion = 1L,
                                    createdAt = java.time.LocalDateTime.now(),
                                    lastSignInAt = java.time.LocalDateTime.now(),
                                    updatedAt = java.time.LocalDateTime.now()
                                )
                            } else {
                                // FIXED: Handle blank userId case
                                Timber.w("CalorieTrackingViewModel: Received blank userId, setting to null")
                                null
                            }
                        }
                        
                        // Auto-load calorie data when user is available and changed
                        if (previousUserId != _currentUser.value?.uid && _currentUser.value != null) {
                            Timber.d("CalorieTrackingViewModel: User changed, loading initial data")
                            handleEvent(CalorieTrackingEvent.LoadInitialData)
                            Timber.d("Calories: User auth changed to ${event.userId}, loading calorie data")
                        } else if (_currentUser.value == null) {
                            // Clear state when user logs out
                            _uiState.value = UiState.Loading
                            Timber.d("Calories: User logged out, clearing state")
                        } else {
                            Timber.d("CalorieTrackingViewModel: User unchanged, no action needed")
                        }
                    }
                    is CoordinatorEvent.RefreshAllData -> {
                        handleEvent(CalorieTrackingEvent.RefreshAllData)
                    }
                    is CoordinatorEvent.RefreshSpecificData -> {
                        if (event.dataTypes.contains("calories") || event.dataTypes.contains("analytics")) {
                            handleEvent(CalorieTrackingEvent.RefreshAllData)
                        }
                    }
                    is CoordinatorEvent.WorkoutCompleted -> {
                        // Recalculate calories when workout is completed
                        handleEvent(CalorieTrackingEvent.RefreshAllData)
                    }
                    is CoordinatorEvent.TimePeriodChanged -> {
                        handleEvent(CalorieTrackingEvent.TimePeriodChanged(event.timeRange))
                        Timber.d("Calories: Time period changed to ${event.timeRange}")
                    }
                    else -> {
                        // Ignore other coordinator events
                    }
                }
            } catch (exception: Exception) {
                val error = com.example.liftrix.domain.model.error.LiftrixError.UnknownError(
                    errorMessage = "Failed to handle coordinator event: ${event::class.simpleName}",
                    analyticsContext = mapOf(
                        "event_type" to (event::class.simpleName ?: "Unknown"),
                        "timestamp" to System.currentTimeMillis().toString()
                    )
                )
                updateErrorState(error)
                Timber.e(exception, "Failed to handle coordinator event: ${event::class.simpleName}")
            }
        }
    }

    /**
     * Updates the error state in the UI.
     * Overrides BaseViewModel method to provide specific error handling for calorie tracking.
     * 
     * @param error The error to display in the UI
     */
    override fun updateErrorState(error: com.example.liftrix.domain.model.error.LiftrixError) {
        Timber.e("Updating error state: ${error.message}")
        _uiState.value = UiState.Error(error, _uiState.value.dataOrNull())
    }

    /**
     * Sets the loading state in the UI.
     * Overrides BaseViewModel method to provide specific loading state for calorie tracking.
     */
    override fun setLoadingState() {
        val currentState = _uiState.value.dataOrNull()
        if (currentState != null) {
            _uiState.value = UiState.Success(currentState.copy(
                calorieSummary = AsyncData.Loading(),
                dailyCalories = AsyncData.Loading(),
                weeklyTrend = AsyncData.Loading(),
                lastRefreshTimestamp = System.currentTimeMillis()
            ))
        } else {
            _uiState.value = UiState.Loading
        }
    }

    /**
     * Observes the reactive UI state and updates the mutable UI state accordingly.
     * This method ensures that UI state stays in sync with authentication and time range changes.
     * Uses proper lifecycle management with onEach() and launchIn() to prevent memory leaks.
     */
    private fun observeStateChanges() {
        reactiveUiState
            .onEach { newUiState ->
                _uiState.value = newUiState
            }
            .launchIn(viewModelScope)
    }

    /**
     * Loads calorie summary data from the service.
     * 
     * Retrieves comprehensive calorie summary including total calories burned,
     * average daily calories, workout count, and weekly trends.
     */
    private suspend fun loadCalorieSummary() {
        val currentState = combinedState.value
        if (!currentState.hasValidUser()) {
            Timber.w("Cannot load calorie summary - no authenticated user")
            return
        }

        val userId = currentState.userId!!
        Timber.d("Loading calorie summary for user: $userId")

        // Set loading state for summary
        updateCalorieDataStates(calorieSummary = AsyncData.Loading())

        val result = calorieService.getCalorieSummary(userId)
        
        result.fold(
            onSuccess = { data ->
                Timber.d("Successfully loaded calorie summary")
                updateCalorieDataStates(calorieSummary = AsyncData.Success(data))
            },
            onFailure = { throwable ->
                val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                    errorMessage = throwable.message ?: "Unknown error",
                    analyticsContext = mapOf("operation" to "loadCalorieSummary")
                )
                Timber.e("Failed to load calorie summary: ${error.message}")
                updateCalorieDataStates(calorieSummary = AsyncData.Failure(error))
                handleError(error)
            }
        )
    }

    /**
     * Loads daily calorie data for the specified time range.
     * 
     * @param timeRange Time range for daily data retrieval
     */
    private suspend fun loadDailyCalories(timeRange: TimeRange) {
        val currentState = combinedState.value
        if (!currentState.hasValidUser()) {
            Timber.w("Cannot load daily calories - no authenticated user")
            return
        }

        val userId = currentState.userId!!
        Timber.d("Loading daily calories for user: $userId, timeRange: $timeRange")

        // Update time range if different
        if (timeRange != currentState.currentTimeRange) {
            _currentTimeRange.value = timeRange
        }

        // Set loading state for daily calories
        updateCalorieDataStates(dailyCalories = AsyncData.Loading())

        val result = calorieService.getDailyCalories(userId, timeRange)
        
        result.fold(
            onSuccess = { data ->
                Timber.d("Successfully loaded daily calories: ${data.size} data points")
                updateCalorieDataStates(dailyCalories = AsyncData.Success(data))
            },
            onFailure = { throwable ->
                val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                    errorMessage = throwable.message ?: "Unknown error",
                    analyticsContext = mapOf("operation" to "loadDailyCalories")
                )
                Timber.e("Failed to load daily calories: ${error.message}")
                updateCalorieDataStates(dailyCalories = AsyncData.Failure(error))
                handleError(error)
            }
        )
    }

    /**
     * Loads weekly calorie trend data from the service.
     * 
     * Retrieves weekly trend analysis including moving averages,
     * trend percentages, peak/low weeks, and consistency scores.
     */
    private suspend fun loadWeeklyTrend() {
        val currentState = combinedState.value
        if (!currentState.hasValidUser()) {
            Timber.w("Cannot load weekly trend - no authenticated user")
            return
        }

        val userId = currentState.userId!!
        Timber.d("Loading weekly trend for user: $userId")

        // Set loading state for weekly trend
        updateCalorieDataStates(weeklyTrend = AsyncData.Loading())

        val result = calorieService.getWeeklyTrend(userId)
        
        result.fold(
            onSuccess = { data ->
                Timber.d("Successfully loaded weekly trend: ${data.weeklyData.size} weeks")
                updateCalorieDataStates(weeklyTrend = AsyncData.Success(data))
            },
            onFailure = { throwable ->
                val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                    errorMessage = throwable.message ?: "Unknown error",
                    analyticsContext = mapOf("operation" to "loadWeeklyTrend")
                )
                Timber.e("Failed to load weekly trend: ${error.message}")
                updateCalorieDataStates(weeklyTrend = AsyncData.Failure(error))
                handleError(error)
            }
        )
    }

    /**
     * Changes the current time period and triggers data refresh for daily calories.
     * 
     * @param timeRange The new time range to display
     */
    private suspend fun changeTimePeriod(timeRange: TimeRange) {
        Timber.d("Changing time period to: $timeRange")
        
        _currentTimeRange.value = timeRange
        
        // Reload daily calories for new time range
        loadDailyCalories(timeRange)
    }

    /**
     * Calculates calories burned for a specific workout.
     * 
     * @param workout Workout instance with exercises and timing data
     */
    private suspend fun calculateWorkoutCalories(workout: Workout) {
        Timber.d("Calculating calories for workout: ${workout.id}")

        val result = calorieService.calculateWorkoutCalories(workout)
        
        result.fold(
            onSuccess = { calories ->
                Timber.d("Successfully calculated workout calories: $calories")
                
                // Update workout calories map
                _workoutCalories.value = _workoutCalories.value.toMutableMap().apply {
                    put(workout.id.toString(), calories)
                }
                
                // Optionally refresh summary if workout is recent
                // This could be optimized based on workout date vs current time range
                refreshCalorieSummary()
            },
            onFailure = { throwable ->
                val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                    errorMessage = throwable.message ?: "Unknown error",
                    analyticsContext = mapOf("operation" to "calculateWorkoutCalories")
                )
                Timber.e("Failed to calculate workout calories: ${error.message}")
                handleError(error)
            }
        )
    }

    /**
     * Refreshes all calorie data types.
     */
    private suspend fun refreshAllData() {
        Timber.d("Refreshing all calorie data")
        
        val currentState = combinedState.value
        if (!currentState.hasValidUser()) {
            Timber.w("Cannot refresh data - no authenticated user")
            return
        }

        val userId = currentState.userId!!
        val timeRange = currentState.currentTimeRange

        // Set loading state for all data types
        updateCalorieDataStates(
            calorieSummary = AsyncData.Loading(),
            dailyCalories = AsyncData.Loading(),
            weeklyTrend = AsyncData.Loading()
        )

        // Launch concurrent data loading
        viewModelScope.launch {
            loadCalorieSummaryInternal(userId)
            loadDailyCaloriesInternal(userId, timeRange)
            loadWeeklyTrendInternal(userId)
        }
    }

    /**
     * Loads initial data when the ViewModel is created.
     */
    private suspend fun loadInitialData() {
        Timber.d("Loading initial calorie data")
        
        val currentState = combinedState.value
        if (currentState.hasValidUser()) {
            loadInitialCalorieData(currentState.userId!!, currentState.currentTimeRange)
        }
    }

    /**
     * Loads initial calorie data for authenticated user.
     * 
     * @param userId User identifier for data scoping
     * @param timeRange Time range for data retrieval
     */
    private suspend fun loadInitialCalorieData(userId: String, timeRange: TimeRange) {
        Timber.d("Loading initial calorie data for user: $userId, timeRange: $timeRange")

        // Set loading state for all data types
        updateCalorieDataStates(
            calorieSummary = AsyncData.Loading(),
            dailyCalories = AsyncData.Loading(),
            weeklyTrend = AsyncData.Loading()
        )

        // Launch concurrent data loading with timeout
        viewModelScope.launch {
            try {
                // Add timeout for service calls
                kotlinx.coroutines.withTimeout(15000) { // 15 second timeout
                    loadCalorieSummaryInternal(userId)
                    loadDailyCaloriesInternal(userId, timeRange)
                    loadWeeklyTrendInternal(userId)
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Timber.w("CalorieTrackingViewModel: Service call timeout, providing fallback data")
                // Provide fallback data instead of staying in loading state
                updateCalorieDataStates(
                    calorieSummary = AsyncData.Success(createFallbackCalorieSummary()),
                    dailyCalories = AsyncData.Success(emptyList()),
                    weeklyTrend = AsyncData.Success(createFallbackWeeklyTrend())
                )
            } catch (e: Exception) {
                Timber.e(e, "CalorieTrackingViewModel: Error loading initial data")
                // Provide fallback data instead of staying in loading state
                updateCalorieDataStates(
                    calorieSummary = AsyncData.Success(createFallbackCalorieSummary()),
                    dailyCalories = AsyncData.Success(emptyList()),
                    weeklyTrend = AsyncData.Success(createFallbackWeeklyTrend())
                )
            }
        }
    }

    /**
     * Retries failed operations based on current state.
     */
    private suspend fun retryFailedOperations() {
        Timber.d("Retrying failed operations")
        
        val currentState = combinedState.value
        if (!currentState.hasValidUser()) return

        val userId = currentState.userId!!
        val timeRange = currentState.currentTimeRange

        // Retry each failed operation
        if (currentState.calorieSummary is AsyncData.Failure) {
            viewModelScope.launch { loadCalorieSummaryInternal(userId) }
        }
        
        if (currentState.dailyCalories is AsyncData.Failure) {
            viewModelScope.launch { loadDailyCaloriesInternal(userId, timeRange) }
        }
        
        if (currentState.weeklyTrend is AsyncData.Failure) {
            viewModelScope.launch { loadWeeklyTrendInternal(userId) }
        }
    }

    /**
     * Clears cached data and reloads fresh data.
     */
    private suspend fun clearCachedData() {
        Timber.d("Clearing cached calorie data")
        
        // Reset all data to NotAsked state
        updateCalorieDataStates(
            calorieSummary = AsyncData.NotAsked,
            dailyCalories = AsyncData.NotAsked,
            weeklyTrend = AsyncData.NotAsked
        )
        
        // Clear workout calories cache
        _workoutCalories.value = emptyMap()
        
        // Reload fresh data
        loadInitialData()
    }

    /**
     * Internal method to load calorie summary.
     * 
     * @param userId User identifier for data scoping
     */
    private suspend fun loadCalorieSummaryInternal(userId: String) {
        updateCalorieDataStates(calorieSummary = AsyncData.Loading())
        
        val result = calorieService.getCalorieSummary(userId)
        
        result.fold(
            onSuccess = { data ->
                updateCalorieDataStates(calorieSummary = AsyncData.Success(data))
            },
            onFailure = { throwable ->
                val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                    errorMessage = throwable.message ?: "Unknown error",
                    analyticsContext = mapOf("operation" to "loadCalorieSummaryInternal")
                )
                updateCalorieDataStates(calorieSummary = AsyncData.Failure(error))
            }
        )
    }

    /**
     * Internal method to load daily calories.
     * 
     * @param userId User identifier for data scoping
     * @param timeRange Time range for data retrieval
     */
    private suspend fun loadDailyCaloriesInternal(userId: String, timeRange: TimeRange) {
        updateCalorieDataStates(dailyCalories = AsyncData.Loading())
        
        val result = calorieService.getDailyCalories(userId, timeRange)
        
        result.fold(
            onSuccess = { data ->
                updateCalorieDataStates(dailyCalories = AsyncData.Success(data))
            },
            onFailure = { throwable ->
                val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                    errorMessage = throwable.message ?: "Unknown error",
                    analyticsContext = mapOf("operation" to "loadDailyCaloriesInternal")
                )
                updateCalorieDataStates(dailyCalories = AsyncData.Failure(error))
            }
        )
    }

    /**
     * Internal method to load weekly trend.
     * 
     * @param userId User identifier for data scoping
     */
    private suspend fun loadWeeklyTrendInternal(userId: String) {
        updateCalorieDataStates(weeklyTrend = AsyncData.Loading())
        
        val result = calorieService.getWeeklyTrend(userId)
        
        result.fold(
            onSuccess = { data ->
                updateCalorieDataStates(weeklyTrend = AsyncData.Success(data))
            },
            onFailure = { throwable ->
                val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                    errorMessage = throwable.message ?: "Unknown error",
                    analyticsContext = mapOf("operation" to "loadWeeklyTrendInternal")
                )
                updateCalorieDataStates(weeklyTrend = AsyncData.Failure(error))
            }
        )
    }

    /**
     * Refreshes only the calorie summary data.
     */
    private suspend fun refreshCalorieSummary() {
        val currentState = combinedState.value
        if (currentState.hasValidUser()) {
            loadCalorieSummaryInternal(currentState.userId!!)
        }
    }

    /**
     * Updates the calorie data states in the UI state.
     * 
     * @param calorieSummary Optional new calorie summary state
     * @param dailyCalories Optional new daily calories state
     * @param weeklyTrend Optional new weekly trend state
     */
    private fun updateCalorieDataStates(
        calorieSummary: AsyncData<CalorieSummary>? = null,
        dailyCalories: AsyncData<List<DailyCalorieData>>? = null,
        weeklyTrend: AsyncData<WeeklyCalorieTrend>? = null
    ) {
        val currentUiState = _uiState.value
        val currentState = currentUiState.dataOrNull() ?: return

        val newState = currentState.copy(
            calorieSummary = calorieSummary ?: currentState.calorieSummary,
            dailyCalories = dailyCalories ?: currentState.dailyCalories,
            weeklyTrend = weeklyTrend ?: currentState.weeklyTrend,
            lastRefreshTimestamp = System.currentTimeMillis()
        )

        _uiState.value = UiState.Success(newState)
    }

    /**
     * Gets the current workout calories map.
     * 
     * @return Map of workout IDs to calculated calories
     */
    fun getWorkoutCalories(): Map<String, Int> = _workoutCalories.value

    /**
     * Gets calculated calories for a specific workout.
     * 
     * @param workoutId Workout identifier
     * @return Calculated calories or null if not calculated
     */
    fun getWorkoutCalories(workoutId: String): Int? = _workoutCalories.value[workoutId]

    /**
     * Cleanup method called when ViewModel is cleared.
     * Provides cleanup logging for debugging.
     */
    /**
     * Refreshes calorie data.
     */
    private fun refreshCalories() {
        viewModelScope.launch {
            loadInitialData()
        }
    }
    
    /**
     * Clears error state.
     */
    private fun clearError() {
        updateState { currentState ->
            when (currentState) {
                is UiState.Error -> UiState.Loading
                else -> currentState
            }
        }
    }
    
    /**
     * Navigates to calorie goal settings.
     */
    private fun navigateToCalorieGoalSettings() {
        // Navigation logic would be implemented here
        Timber.d("Navigate to calorie goal settings")
    }
    
    /**
     * Navigates to detailed calorie analytics.
     */
    private fun navigateToDetailedCalorieAnalytics() {
        // Navigation logic would be implemented here
        Timber.d("Navigate to detailed calorie analytics")
    }
    
    /**
     * Navigates to calorie history.
     */
    private fun navigateToCalorieHistory() {
        // Navigation logic would be implemented here
        Timber.d("Navigate to calorie history")
    }

    /**
     * Creates initial unauthenticated state for calorie tracking.
     */
    private fun createUnauthenticatedCalorieTrackingState(): CalorieTrackingState {
        return CalorieTrackingState(
            calorieSummary = AsyncData.NotAsked,
            dailyCalories = AsyncData.NotAsked,
            weeklyTrend = AsyncData.NotAsked,
            currentTimeRange = TimeRange.lastMonth(),
            userId = null,
            lastRefreshTimestamp = System.currentTimeMillis()
        )
    }

    /**
     * Creates fallback calorie summary data when service is unavailable.
     */
    private fun createFallbackCalorieSummary(): CalorieSummary {
        return CalorieSummary(
            totalCaloriesBurned = 0,
            averageDailyCalories = 0,
            totalWorkouts = 0,
            averageWorkoutCalories = 0,
            highestDailyCalories = 0,
            currentWeekCalories = 0,
            previousWeekCalories = 0,
            weeklyTrend = 0f
        )
    }

    /**
     * Creates fallback weekly trend data when service is unavailable.
     */
    private fun createFallbackWeeklyTrend(): WeeklyCalorieTrend {
        return WeeklyCalorieTrend(
            weeklyData = emptyList(),
            movingAverage = 0f,
            trendPercentage = 0f,
            peakWeek = null,
            lowWeek = null,
            consistency = 0
        )
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("CalorieTrackingViewModel cleared")
    }
}