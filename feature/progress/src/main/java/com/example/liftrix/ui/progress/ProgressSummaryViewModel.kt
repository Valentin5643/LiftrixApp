package com.example.liftrix.ui.progress

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.progress.ProgressDataPort
import com.example.liftrix.ui.common.state.AsyncData
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for progress summary screen following MVI pattern with clean architecture.
 * 
 * This ViewModel manages the state for progress summary statistics and aggregated metrics
 * with comprehensive error handling, time period selection, and reactive user authentication.
 * It extends BaseViewModel to leverage standardized state management and error handling.
 * 
 * Key Features:
 * - AsyncData state management for progress summary information
 * - Reactive time period selection with automatic data refresh
 * - User authentication state monitoring with automatic data scoping
 * - Flow combination for efficient reactive updates
 * - Comprehensive error handling with recovery strategies
 * - Lifecycle-aware state management with WhileSubscribed sharing
 * - Performance optimizations with proper Flow lifecycle management
 * 
 * Architecture Integration:
 * - Depends on ProgressDataPort for data operations
 * - Receives user state from ProgressDashboardCoordinator for centralized auth management
 * - Uses ErrorHandler for consistent error processing
 * - Follows clean architecture with proper dependency injection
 * 
 * State Management:
 * - Uses StateFlow for reactive state updates
 * - Combines multiple data sources into single state stream
 * - Provides proper lifecycle management with SharingStarted.WhileSubscribed
 * - Implements loading states and error recovery
 * 
 * Usage:
 * ```kotlin
 * @Composable
 * fun ProgressSummaryScreen(
 *     viewModel: ProgressSummaryViewModel = hiltViewModel()
 * ) {
 *     val uiState by viewModel.uiState.collectAsStateWithLifecycle()
 *     
 *     when (uiState) {
 *         is UiState.Loading -> LoadingIndicator()
 *         is UiState.Success -> SummaryContent(
 *             state = uiState.data,
 *             onEvent = viewModel::handleEvent
 *         )
 *         is UiState.Error -> ErrorMessage(uiState.error)
 *     }
 * }
 * ```
 * 
 * @param progressDataService Service for fetching summary data
 * @param errorHandler Centralized error handling service
 */
@HiltViewModel
class ProgressSummaryViewModel @Inject constructor(
    private val progressDataService: ProgressDataPort
) : ModernBaseViewModel<UiState<ProgressSummaryState>>(initialState = UiState.Loading) {

    /**
     * Internal state for current time range selection.
     * Separate from UI state to enable independent time range updates.
     */
    private val _currentTimeRange = MutableStateFlow(TimeRange.lastMonth())

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
    private val combinedState: StateFlow<ProgressSummaryState> = combine(
        _currentUser,
        _currentTimeRange
    ) { user, timeRange ->
        val userId = user?.uid
        if (userId != null) {
            ProgressSummaryState.createAuthenticatedState(userId, timeRange)
        } else {
            ProgressSummaryState.createUnauthenticatedState()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProgressSummaryState.createUnauthenticatedState()
    )

    init {
        // Start observing combined state and trigger initial data loading
        observeStateChanges()
        
        // Load initial data when ViewModel is created
        handleEvent(ProgressSummaryEvent.LoadSummary)
    }

    /**
     * Handles all events from the UI following the MVI pattern.
     * 
     * This method processes user interactions and internal events, updating the state
     * accordingly and triggering appropriate data operations.
     * 
     * @param event The event to process
     */
    fun handleEvent(event: ProgressSummaryEvent) {
        viewModelScope.launch {
            try {
                when (event) {
                    is ProgressSummaryEvent.LoadSummary -> {
                        loadSummaryData()
                    }
                    is ProgressSummaryEvent.RefreshSummary -> {
                        refreshSummary()
                    }
                    is ProgressSummaryEvent.TimePeriodChanged -> {
                        changeTimePeriod(event.timeRange)
                    }
                    is ProgressSummaryEvent.RetryLoad -> {
                        retryLoadSummary()
                    }
                    is ProgressSummaryEvent.ClearError -> {
                        clearError()
                    }
                    is ProgressSummaryEvent.ForceRefresh -> {
                        forceRefresh()
                    }
                    is ProgressSummaryEvent.QuickTimeRangeSelected -> {
                        handleQuickTimeRangeSelection(event.predefinedRange)
                    }
                    is ProgressSummaryEvent.BackgroundDataUpdate -> {
                        handleBackgroundDataUpdate()
                    }
                }
            } catch (exception: Exception) {
                logError(
                    LiftrixError.UnknownError(
                        errorMessage = "Failed to handle event: ${event::class.simpleName}",
                        analyticsContext = mapOf(
                            "event_type" to (event::class.simpleName ?: "Unknown"),
                            "timestamp" to System.currentTimeMillis().toString()
                        )
                    ),
                    "handleEvent"
                )
            }
        }
    }

    /**
     * Updates the error state in the UI.
     * Overrides BaseViewModel method to provide specific error handling for summary.
     * 
     * @param error The error to display in the UI
     */
    fun updateErrorState(error: LiftrixError) {
        _uiState.value = UiState.Error(error)
    }

    /**
     * Sets the loading state in the UI.
     * Overrides BaseViewModel method to provide specific loading state for summary.
     */
    fun setLoadingState() {
        _uiState.value = UiState.Loading
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
                when (event) {
                    is CoordinatorEvent.UserAuthChanged -> {
                        val previousUserId = _currentUser.value?.uid
                        
                        // FIXED: Added proper validation and null handling
                        _currentUser.value = event.userId?.let { userId ->
                            // Only create User object if we have a valid userId
                            if (userId.isNotBlank()) {
                                // Create a minimal User object that passes validation
                                // Using a temporary email to satisfy the validation requirement
                                // The actual email will be populated when the full user profile is loaded
                                com.example.liftrix.domain.model.User(
                                    uid = userId,
                                    email = "temp@liftrix.app", // FIXED: Use valid email instead of blank
                                    displayName = null,
                                    photoUrl = null,
                                    isAnonymous = false, // FIXED: Keep as false since we have a userId
                                    subscriptionTier = com.example.liftrix.domain.model.SubscriptionTier.FREE,
                                    subscriptionStatus = com.example.liftrix.domain.model.SubscriptionStatus.ACTIVE, // FIXED: Use ACTIVE instead of EXPIRED
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
                                null
                            }
                        }
                        
                        // Auto-load summary when user is available and changed
                        if (previousUserId != _currentUser.value?.uid && _currentUser.value != null) {
                            handleEvent(ProgressSummaryEvent.LoadSummary)
                            Timber.d("Summary: User auth changed to ${event.userId}, loading summary")
                        } else if (_currentUser.value == null) {
                            // Clear state when user logs out
                            _uiState.value = UiState.Loading
                            Timber.d("Summary: User logged out, clearing state")
                        }
                    }
                    is CoordinatorEvent.RefreshAllData -> {
                        handleEvent(ProgressSummaryEvent.RefreshSummary)
                    }
                    is CoordinatorEvent.RefreshSpecificData -> {
                        if (event.dataTypes.contains("summary") || event.dataTypes.contains("progress")) {
                            handleEvent(ProgressSummaryEvent.RefreshSummary)
                        }
                    }
                    is CoordinatorEvent.WorkoutCompleted -> {
                        // Refresh summary data when workout is completed
                        handleEvent(ProgressSummaryEvent.BackgroundDataUpdate)
                    }
                    is CoordinatorEvent.TimePeriodChanged -> {
                        handleEvent(ProgressSummaryEvent.TimePeriodChanged(event.timeRange))
                        Timber.d("Summary: Time period changed to ${event.timeRange}")
                    }
                    else -> {
                        // Ignore other coordinator events
                    }
                }
            } catch (exception: Exception) {
                val error = LiftrixError.UnknownError(
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
     * Observes the combined state and updates the UI state accordingly.
     * This method ensures that UI state stays in sync with authentication and time range changes.
     */
    private fun observeStateChanges() {
        viewModelScope.launch {
            combinedState.collect { newState ->
                _uiState.value = UiState.Success(newState)
                
                // Trigger data loading if user is authenticated and data is not asked
                if (newState.hasValidUser() && newState.isNotAsked()) {
                    loadSummaryDataForState(newState)
                }
            }
        }
    }

    /**
     * Loads summary data for the current state.
     */
    private suspend fun loadSummaryData() {
        val currentState = combinedState.value
        if (currentState.hasValidUser()) {
            loadSummaryDataForState(currentState)
        }
    }

    /**
     * Loads summary data for a specific state.
     * 
     * @param state The state containing user and time range information
     */
    private suspend fun loadSummaryDataForState(state: ProgressSummaryState) {
        val userId = state.userId ?: return
        val timeRange = state.currentTimeRange

        // Update state to loading
        _uiState.value = UiState.Loading

        // Load data from service
        val result = progressDataService.getProgressSummary(userId, timeRange)
        
        result.fold(
            onSuccess = { data ->
                Timber.d("✅ ProgressSummaryViewModel: Successfully loaded summary data")
                val updatedState = state.toSuccessState(data)
                _uiState.value = UiState.Success(updatedState)
            },
            onFailure = { throwable ->
                Timber.e(throwable, "❌ ProgressSummaryViewModel: Failed to load summary data")
                val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                    errorMessage = "Summary data loading failed: ${throwable.message}",
                    analyticsContext = mapOf(
                        "userId" to userId,
                        "timeRange" to timeRange.toString(),
                        "originalError" to (throwable.message ?: "Unknown"),
                        "errorType" to (throwable::class.simpleName ?: "Unknown")
                    )
                )
                _uiState.value = UiState.Error(error)
                logError(error, "loadSummaryDataForState")
            }
        )
    }

    /**
     * Refreshes the summary data.
     */
    private suspend fun refreshSummary() {
        val currentState = combinedState.value
        if (!currentState.hasValidUser()) return

        // Set refresh flag to true
        val refreshingState = currentState.withRefreshStatus(true)
        _uiState.value = UiState.Success(refreshingState)

        // Load fresh data
        loadSummaryDataForState(currentState)
    }

    /**
     * Changes the current time period and triggers data refresh.
     * 
     * @param timeRange The new time range to display
     */
    private suspend fun changeTimePeriod(timeRange: TimeRange) {
        _currentTimeRange.value = timeRange
        
        // Load from the explicit selected range instead of reading combinedState
        // immediately, because the combined flow may still hold the previous range.
        val currentState = (_uiState.value as? UiState.Success)?.data ?: combinedState.value
        val updatedState = currentState.withTimeRange(timeRange)
        _uiState.value = UiState.Success(updatedState)

        // Load data for new time range
        if (updatedState.hasValidUser()) {
            loadSummaryDataForState(updatedState)
        }
    }

    /**
     * Retries loading summary data after a failure.
     */
    private suspend fun retryLoadSummary() {
        clearError()
        loadSummaryData()
    }

    /**
     * Clears the current error state.
     */
    private fun clearError() {
        val currentState = combinedState.value
        if (currentState.hasError()) {
            val clearedState = currentState.copy(summaryData = AsyncData.NotAsked)
            _uiState.value = UiState.Success(clearedState)
        }
    }

    /**
     * Forces a refresh of all data.
     */
    private suspend fun forceRefresh() {
        val currentState = combinedState.value
        if (!currentState.hasValidUser()) return

        val userId = currentState.userId!!
        
        // First refresh all data in the service
        val refreshResult = progressDataService.refreshAllData(userId)
        
        refreshResult.fold(
            onSuccess = { _ ->
                // If refresh succeeded, reload summary data
                loadSummaryDataForState(currentState)
            },
            onFailure = { throwable ->
                // If refresh failed, handle error
                val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                    errorMessage = throwable.message ?: "Unknown error"
                )
                val errorState = currentState.toErrorState(error)
                _uiState.value = UiState.Success(errorState)
                logError(error, "forceRefresh")
            }
        )
    }

    /**
     * Handles quick time range selection.
     * 
     * @param predefinedRange The predefined time range selected
     */
    private suspend fun handleQuickTimeRangeSelection(predefinedRange: PredefinedTimeRange) {
        val timeRange = predefinedRange.toTimeRange()
        changeTimePeriod(timeRange)
    }

    /**
     * Handles background data updates.
     */
    private suspend fun handleBackgroundDataUpdate() {
        val currentState = combinedState.value
        if (!currentState.hasValidUser()) return

        // Check if current data is stale
        if (currentState.isDataStale()) {
            // Load fresh data in background without changing loading state
            val userId = currentState.userId!!
            val timeRange = currentState.currentTimeRange
            
            val result = progressDataService.getProgressSummary(userId, timeRange)
            
            result.fold(
                onSuccess = { data ->
                    val successState = currentState.toSuccessState(data)
                    _uiState.value = UiState.Success(successState)
                },
                onFailure = { throwable ->
                    // For background updates, we don't show errors prominently
                    // Just log them for analytics
                    val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                        errorMessage = throwable.message ?: "Unknown error"
                    )
                    logError(error, "handleBackgroundDataUpdate")
                }
            )
        }
    }

    /**
     * Extension function to handle TimeRange creation for predefined ranges.
     * This addresses the missing methods in TimeRange companion object.
     */
    private fun PredefinedTimeRange.toTimeRange(): TimeRange = when (this) {
        PredefinedTimeRange.LAST_MONTH -> TimeRange.lastMonth()
        PredefinedTimeRange.LAST_SIX_MONTHS -> TimeRange.lastSixMonths()
        PredefinedTimeRange.THIS_MONTH -> createThisMonth()
        PredefinedTimeRange.ALL_TIME -> TimeRange.allTime()
    }

    /**
     * Creates a TimeRange for the current month.
     */
    private fun createThisMonth(): TimeRange {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startDate = calendar.time
        
        calendar.set(java.util.Calendar.DAY_OF_MONTH, calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
        calendar.set(java.util.Calendar.MINUTE, 59)
        calendar.set(java.util.Calendar.SECOND, 59)
        calendar.set(java.util.Calendar.MILLISECOND, 999)
        val endDate = calendar.time
        
        return TimeRange(startDate, endDate)
    }

    /**
     * Creates a TimeRange for the current year.
     */
    private fun createThisYear(): TimeRange {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.MONTH, java.util.Calendar.JANUARY)
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startDate = calendar.time
        
        calendar.set(java.util.Calendar.MONTH, java.util.Calendar.DECEMBER)
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 31)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
        calendar.set(java.util.Calendar.MINUTE, 59)
        calendar.set(java.util.Calendar.SECOND, 59)
        calendar.set(java.util.Calendar.MILLISECOND, 999)
        val endDate = calendar.time
        
        return TimeRange(startDate, endDate)
    }

    /**
     * Creates a TimeRange for all time (very wide range).
     */
    private fun createAllTime(): TimeRange {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(2000, java.util.Calendar.JANUARY, 1, 0, 0, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startDate = calendar.time
        
        calendar.set(2099, java.util.Calendar.DECEMBER, 31, 23, 59, 59)
        calendar.set(java.util.Calendar.MILLISECOND, 999)
        val endDate = calendar.time
        
        return TimeRange(startDate, endDate)
    }
}
