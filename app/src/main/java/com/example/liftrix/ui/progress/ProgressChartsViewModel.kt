package com.example.liftrix.ui.progress

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import timber.log.Timber
import com.example.liftrix.service.ProgressDataService
import com.example.liftrix.ui.common.state.AsyncData
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.state.dataOrNull
import com.example.liftrix.ui.common.state.isFailure
import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
import com.example.liftrix.ui.progress.components.ChartType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import java.time.LocalDate as JavaLocalDate
import javax.inject.Inject

/**
 * ViewModel for progress charts screen following MVI pattern with clean architecture.
 * 
 * This ViewModel manages the state for volume, duration, and frequency charts with
 * comprehensive error handling, time period selection, and reactive user authentication.
 * It extends BaseViewModel to leverage standardized state management and error handling.
 * 
 * Key Features:
 * - Independent AsyncData state for each chart type
 * - Reactive time period selection with automatic data refresh
 * - User authentication state monitoring with automatic data scoping
 * - Flow combination for efficient reactive updates
 * - Comprehensive error handling with recovery strategies
 * - Lifecycle-aware state management with WhileSubscribed sharing
 * - Performance optimizations with proper Flow lifecycle management
 * 
 * Architecture Integration:
 * - Depends on ProgressDataService for data operations
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
 * fun ProgressChartsScreen(
 *     viewModel: ProgressChartsViewModel = hiltViewModel()
 * ) {
 *     val uiState by viewModel.uiState.collectAsStateWithLifecycle()
 *     
 *     when (uiState) {
 *         is UiState.Loading -> LoadingIndicator()
 *         is UiState.Success -> ChartsContent(
 *             state = uiState.data,
 *             onEvent = viewModel::handleEvent
 *         )
 *         is UiState.Error -> ErrorMessage(uiState.error)
 *     }
 * }
 * ```
 * 
 * @param progressDataService Service for fetching chart data
 */
@HiltViewModel
class ProgressChartsViewModel @Inject constructor(
    private val progressDataService: ProgressDataService
) : ModernBaseViewModel<UiState<ProgressChartsState>>(initialState = UiState.Loading) {

    /**
     * Internal state for current time range selection.
     * Separate from UI state to enable independent time range updates.
     */
    private val _currentTimeRange = MutableStateFlow(TimeRange.lastMonth())

    /**
     * Current user ID received from Coordinator.
     * Updated via Coordinator events instead of direct auth repository observation.
     * Stabilized to prevent null resets that cause loading loops.
     */
    private val _currentUserId = MutableStateFlow<String?>(null)
    
    /**
     * Track if userId has been set to prevent resetting to null inadvertently.
     * Once we have a valid userId, we don't reset it unless explicitly cleared.
     */
    private var userIdStabilized = false

    /**
     * Combined state flow that reactively updates when user authentication or time range changes.
     * Uses Flow.combine to efficiently handle multiple data sources and automatically
     * trigger data loading when dependencies change.
     */
    private val combinedState: StateFlow<ProgressChartsState> = combine(
        _currentUserId,
        _currentTimeRange
    ) { userId, timeRange ->
        if (userId != null) {
            createLoadingChartsState(userId, timeRange)
        } else {
            createUnauthenticatedChartsState()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = createUnauthenticatedChartsState()
    )

    init {
        
        // Start observing reactive state changes automatically
        observeStateChanges()
        
        // Load initial data when ViewModel is created
        handleEvent(ProgressChartsEvent.LoadInitialData)
        
        // Add userId-anchored stuck check timer
        initializeStuckCheckTimer()
        
    }

    /**
     * Handles all events from the UI following the MVI pattern.
     *
     * This method processes user interactions and internal events, updating the state
     * accordingly and triggering appropriate data operations.
     *
     * @param event The event to process
     */
    fun handleEvent(event: ProgressChartsEvent) {
        viewModelScope.launch {
            try {
                when (event) {
                    is ProgressChartsEvent.TimePeriodChanged -> {
                        changeTimePeriod(event.timeRange)
                    }
                    is ProgressChartsEvent.RefreshChart -> {
                        refreshChart(event.chartType)
                    }
                    is ProgressChartsEvent.RefreshAll -> {
                        refreshAllCharts()
                    }
                    is ProgressChartsEvent.LoadInitialData -> {
                        loadInitialData()
                    }
                    is ProgressChartsEvent.ClearError -> {
                        clearError()
                    }
                }
            } catch (exception: Exception) {
                logError(exception, "handleEvent")
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
                when (event) {
                    is CoordinatorEvent.UserAuthChanged -> {
                        val previousUserId = _currentUserId.value
                        
                        // STABILITY FIX: Only allow userId changes under specific conditions
                        val shouldUpdateUserId = when {
                            // Always allow setting userId if it was null
                            previousUserId == null && event.userId != null -> true
                            // Allow clearing userId only if explicitly requested (logout)
                            previousUserId != null && event.userId == null && !userIdStabilized -> true
                            // Allow changing to different valid userId
                            previousUserId != null && event.userId != null && previousUserId != event.userId -> true
                            // Reject null resets once userId is stabilized
                            previousUserId != null && event.userId == null && userIdStabilized -> false
                            else -> false
                        }
                        
                        if (shouldUpdateUserId) {
                            _currentUserId.value = event.userId
                            
                            if (event.userId != null) {
                                userIdStabilized = true
                                handleEvent(ProgressChartsEvent.RefreshAll)
                            } else {
                                // Only reset stabilization on explicit logout
                                userIdStabilized = false
                            }
                        }
                    }
                    is CoordinatorEvent.RefreshAllData -> {
                        handleEvent(ProgressChartsEvent.RefreshAll)
                    }
                    is CoordinatorEvent.RefreshSpecificData -> {
                        if (event.dataTypes.contains("charts")) {
                            handleEvent(ProgressChartsEvent.RefreshAll)
                        }
                    }
                    is CoordinatorEvent.TimePeriodChanged -> {
                        handleEvent(ProgressChartsEvent.TimePeriodChanged(event.timeRange))
                    }
                    else -> {
                        // Ignore other coordinator events
                    }
                }
            } catch (exception: Exception) {
                logError(exception, "handleCoordinatorEvent")
            }
        }
    }

    /**
     * Sets the loading state in the UI.
     */
    private fun setChartLoadingState() {
        val currentState = _uiState.value
        if (currentState is UiState.Success) {
            _uiState.value = UiState.Success(
                currentState.data.copy(
                    volumeChart = AsyncData.Loading(),
                    durationChart = AsyncData.Loading(),
                    frequencyChart = AsyncData.Loading(),
                    volumeCalendar = AsyncData.Loading(),
                    lastRefreshTimestamp = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Observes the reactive state and updates the mutable UI state accordingly.
     * This method ensures that UI state stays in sync with authentication and time range changes.
     * Uses proper lifecycle management with onEach() and launchIn() to prevent memory leaks.
     */
    private fun observeStateChanges() {
        combinedState
            .onEach { newState ->
                _uiState.value = UiState.Success(newState)
                // Trigger data loading if user is authenticated and charts are not loaded
                if (newState.hasValidUser() && newState.areAllChartsNotAsked()) {
                    viewModelScope.launch {
                        loadAllCharts(newState.userId!!, newState.currentTimeRange)
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Initialize stuck check timer that is anchored to userId availability.
     * Only starts checking for stuck charts 5 seconds AFTER userId is set.
     */
    private fun initializeStuckCheckTimer() {
        viewModelScope.launch {
            _currentUserId.collect { userId ->
                // Start stuck-check timer only after userId is available
                if (userId != null) {
                    launch {
                        kotlinx.coroutines.delay(5000) // 5 second delay after auth completes
                        
                        // Check if charts are still loading after timeout
                        val currentState = _uiState.value
                        if (currentState is UiState.Success) {
                            val currentChartsState = currentState.data
                            val stuckCharts = listOf(
                                "volume" to (currentChartsState.volumeChart is AsyncData.Loading),
                                "duration" to (currentChartsState.durationChart is AsyncData.Loading), 
                                "frequency" to (currentChartsState.frequencyChart is AsyncData.Loading)
                            ).filter { it.second }
                            
                            if (stuckCharts.isNotEmpty()) {
                                Timber.e("Charts genuinely stuck after 5s post-auth timeout: ${stuckCharts.map { it.first }}")
                                
                                // Force error state for truly stuck charts
                                updateChartStates(
                                    volumeChart = if (currentChartsState.volumeChart is AsyncData.Loading) 
                                        AsyncData.Failure(LiftrixError.DatabaseError(
                                            errorMessage = "Chart loading timed out. Database may be unresponsive.",
                                            operation = "chartStuckTimeout"
                                        )) 
                                        else currentChartsState.volumeChart,
                                    durationChart = if (currentChartsState.durationChart is AsyncData.Loading) 
                                        AsyncData.Failure(LiftrixError.DatabaseError(
                                            errorMessage = "Chart loading timed out. Database may be unresponsive.",
                                            operation = "chartStuckTimeout"
                                        ))
                                        else currentChartsState.durationChart,
                                    frequencyChart = if (currentChartsState.frequencyChart is AsyncData.Loading) 
                                        AsyncData.Failure(LiftrixError.DatabaseError(
                                            errorMessage = "Chart loading timed out. Database may be unresponsive.",
                                            operation = "chartStuckTimeout"
                                        ))
                                        else currentChartsState.frequencyChart
                                )
                            }
                        }
                    }
                }
            }
        }
    }


    /**
     * Changes the current time period and triggers data refresh for all charts.
     * 
     * @param timeRange The new time range to display
     */
    private suspend fun changeTimePeriod(timeRange: TimeRange) {
        _currentTimeRange.value = timeRange
        
        // Get current user from combined state
        val currentState = combinedState.value
        if (currentState.hasValidUser()) {
            loadAllCharts(currentState.userId!!, timeRange)
        }
    }

    /**
     * Refreshes data for a specific chart type.
     * 
     * @param chartType The type of chart to refresh
     */
    private suspend fun refreshChart(chartType: ChartType) {
        val currentState = combinedState.value
        if (!currentState.hasValidUser()) return

        val userId = currentState.userId!!
        val timeRange = currentState.currentTimeRange

        when (chartType) {
            ChartType.LINE -> loadVolumeChart(userId, timeRange)  // Assuming LINE maps to Volume
            ChartType.BAR -> loadDurationChart(userId, timeRange)  // Assuming BAR maps to Duration
            ChartType.RADIAL -> loadFrequencyChart(userId, timeRange)  // Assuming RADIAL maps to Frequency
        }
    }

    /**
     * Refreshes all chart data with current time period.
     */
    private suspend fun refreshAllCharts() {
        val currentState = combinedState.value
        if (!currentState.hasValidUser()) return

        loadAllCharts(currentState.userId!!, currentState.currentTimeRange)
    }

    /**
     * Loads initial data when the ViewModel is created.
     */
    private suspend fun loadInitialData() {
        val currentState = combinedState.value
        if (currentState.hasValidUser()) {
            loadAllCharts(currentState.userId!!, currentState.currentTimeRange)
        }
    }

    /**
     * Loads data for all chart types concurrently with timeout handling.
     * 
     * @param userId The user ID for data scoping
     * @param timeRange The time range for data retrieval
     */
    private suspend fun loadAllCharts(userId: String, timeRange: TimeRange) {
        Timber.d("🔍 VOLUME-CALENDAR-DEBUG: loadAllCharts starting - userId=$userId, timeRange=$timeRange")
        
        // Set loading state for all charts
        updateChartStates(
            volumeChart = AsyncData.Loading(),
            durationChart = AsyncData.Loading(),
            frequencyChart = AsyncData.Loading(),
            volumeCalendar = AsyncData.Loading()
        )

        // Launch concurrent data loading with timeout
        viewModelScope.launch {
            try {
                kotlinx.coroutines.withTimeout(30000) { // 30 second timeout
                    launch { loadVolumeChart(userId, timeRange) }
                    launch { loadDurationChart(userId, timeRange) }
                    launch { loadFrequencyChart(userId, timeRange) }
                    launch { 
                        Timber.d("🔍 VOLUME-CALENDAR-DEBUG: Launching loadVolumeCalendar coroutine")
                        loadVolumeCalendar(userId) 
                    }
                }
            } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                Timber.w("Chart loading timeout - setting fallback states")
                updateChartStates(
                    volumeChart = AsyncData.Failure(LiftrixError.UnknownError("Loading timeout - please try again")),
                    durationChart = AsyncData.Failure(LiftrixError.UnknownError("Loading timeout - please try again")),
                    frequencyChart = AsyncData.Failure(LiftrixError.UnknownError("Loading timeout - please try again"))
                )
            }
        }
    }

    /**
     * Loads volume chart data from the service.
     * 
     * @param userId The user ID for data scoping
     * @param timeRange The time range for data retrieval
     */
    private suspend fun loadVolumeChart(userId: String, timeRange: TimeRange) {
        try {
            val result = progressDataService.getVolumeData(userId, timeRange)
            
            result.fold(
                onSuccess = { data ->
                    updateChartStates(volumeChart = AsyncData.Success(data))
                },
                onFailure = { error ->
                    Timber.e("Volume chart fetch failure: ${error.message}")
                    val liftrixError = if (error is LiftrixError) error else LiftrixError.UnknownError(error.message ?: "Unknown error")
                    updateChartStates(volumeChart = AsyncData.Failure(liftrixError))
                    logError(error, "loadVolumeChart")
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Volume chart fetch exception: ${e.message}")
            updateChartStates(volumeChart = AsyncData.Failure(LiftrixError.UnknownError("Volume fetch exception: ${e.message}")))
        }
    }

    /**
     * Loads duration chart data from the service.
     * 
     * @param userId The user ID for data scoping
     * @param timeRange The time range for data retrieval
     */
    private suspend fun loadDurationChart(userId: String, timeRange: TimeRange) {
        try {
            val result = progressDataService.getDurationData(userId, timeRange)
            
            result.fold(
                onSuccess = { data ->
                    updateChartStates(durationChart = AsyncData.Success(data))
                },
                onFailure = { error ->
                    Timber.e("Duration chart fetch failure: ${error.message}")
                    val liftrixError = if (error is LiftrixError) error else LiftrixError.UnknownError(error.message ?: "Unknown error")
                    updateChartStates(durationChart = AsyncData.Failure(liftrixError))
                    logError(error, "loadDurationChart")
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Duration chart fetch exception: ${e.message}")
            updateChartStates(durationChart = AsyncData.Failure(LiftrixError.UnknownError("Duration fetch exception: ${e.message}")))
        }
    }

    /**
     * Loads frequency chart data from the service.
     * 
     * @param userId The user ID for data scoping
     * @param timeRange The time range for data retrieval
     */
    private suspend fun loadFrequencyChart(userId: String, timeRange: TimeRange) {
        try {
            val result = progressDataService.getFrequencyData(userId, timeRange)
            
            result.fold(
                onSuccess = { data ->
                    updateChartStates(frequencyChart = AsyncData.Success(data))
                },
                onFailure = { error ->
                    Timber.e("Frequency chart fetch failure: ${error.message}")
                    val liftrixError = if (error is LiftrixError) error else LiftrixError.UnknownError(error.message ?: "Unknown error")
                    updateChartStates(frequencyChart = AsyncData.Failure(liftrixError))
                    logError(error, "loadFrequencyChart")
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Frequency chart fetch exception: ${e.message}")
            updateChartStates(frequencyChart = AsyncData.Failure(LiftrixError.UnknownError("Frequency fetch exception: ${e.message}")))
        }
    }

    /**
     * Loads volume calendar data from the service.
     * 
     * @param userId The user ID for data scoping
     */
    private suspend fun loadVolumeCalendar(userId: String) {
        try {
            Timber.d("🔍 VOLUME-CALENDAR-DEBUG: Starting loadVolumeCalendar for userId=$userId")
            val result = progressDataService.getVolumeCalendarData(userId)
            
            result.fold(
                onSuccess = { data ->
                    Timber.d("🔍 VOLUME-CALENDAR-DEBUG: Successfully loaded volume calendar with ${data.dailyVolumes.size} days")
                    updateChartStates(volumeCalendar = AsyncData.Success(data))
                },
                onFailure = { error ->
                    Timber.e("🔍 VOLUME-CALENDAR-DEBUG: Volume calendar fetch failure: ${error.message}")
                    val liftrixError = if (error is LiftrixError) error else LiftrixError.UnknownError(error.message ?: "Unknown error")
                    updateChartStates(volumeCalendar = AsyncData.Failure(liftrixError))
                    logError(error, "loadVolumeCalendar")
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Volume calendar fetch exception: ${e.message}")
            updateChartStates(volumeCalendar = AsyncData.Failure(LiftrixError.UnknownError("Volume calendar fetch exception: ${e.message}")))
        }
    }

    /**
     * Updates the chart states in the UI state.
     * 
     * @param volumeChart Optional new volume chart state
     * @param durationChart Optional new duration chart state
     * @param frequencyChart Optional new frequency chart state
     * @param volumeCalendar Optional new volume calendar state
     */
    private fun updateChartStates(
        volumeChart: AsyncData<List<com.example.liftrix.domain.repository.VolumeDataPoint>>? = null,
        durationChart: AsyncData<List<com.example.liftrix.domain.repository.DurationDataPoint>>? = null,
        frequencyChart: AsyncData<List<com.example.liftrix.domain.repository.FrequencyDataPoint>>? = null,
        volumeCalendar: AsyncData<com.example.liftrix.domain.model.analytics.VolumeCalendarData>? = null
    ) {
        val currentState = _uiState.value
        
        when (currentState) {
            is UiState.Success -> {
                val newState = UiState.Success(
                    currentState.data.copy(
                        volumeChart = volumeChart ?: currentState.data.volumeChart,
                        durationChart = durationChart ?: currentState.data.durationChart,
                        frequencyChart = frequencyChart ?: currentState.data.frequencyChart,
                        volumeCalendar = volumeCalendar ?: currentState.data.volumeCalendar,
                        lastRefreshTimestamp = System.currentTimeMillis()
                    )
                )
                _uiState.value = newState
            }
            is UiState.Loading -> {
                // If we're still in Loading state but combinedState has provided initial data, create Success state
                val combinedStateValue = combinedState.value
                val newState = UiState.Success(
                    combinedStateValue.copy(
                        volumeChart = volumeChart ?: combinedStateValue.volumeChart,
                        durationChart = durationChart ?: combinedStateValue.durationChart,
                        frequencyChart = frequencyChart ?: combinedStateValue.frequencyChart,
                        volumeCalendar = volumeCalendar ?: combinedStateValue.volumeCalendar,
                        lastRefreshTimestamp = System.currentTimeMillis()
                    )
                )
                _uiState.value = newState
            }
            else -> {
                Timber.w("Cannot update chart states - currentState is ${currentState?.javaClass?.simpleName}")
            }
        }
    }

    /**
     * Converts Java LocalDate to Kotlin LocalDate for service compatibility.
     * 
     * @param javaDate The Java LocalDate to convert
     * @return Kotlin LocalDate
     */
    private fun JavaLocalDate.toKotlinLocalDate(): kotlinx.datetime.LocalDate =
        kotlinx.datetime.LocalDate(year, monthValue, dayOfMonth)

    /**
     * Converts Kotlin LocalDate to Java LocalDate for service compatibility.
     * 
     * @param kotlinDate The Kotlin LocalDate to convert
     * @return Java LocalDate
     */
    private fun kotlinx.datetime.LocalDate.toJavaLocalDate(): JavaLocalDate =
        JavaLocalDate.of(year, monthNumber, dayOfMonth)
    
    /**
     * Explicit method to reset userId stabilization on user logout.
     * This should only be called when the user explicitly logs out.
     */
    fun resetUserSession() {
        viewModelScope.launch {
            userIdStabilized = false
            _currentUserId.value = null
            
            // Reset all chart states to NotAsked
            _uiState.value = UiState.Success(createUnauthenticatedChartsState())
        }
    }
    
    /**
     * Clears error state from the UI.
     */
    private fun clearError() {
        // Reset any failed charts to NotAsked state to allow retry
        val currentState = _uiState.value
        if (currentState is UiState.Success) {
            _uiState.value = UiState.Success(
                currentState.data.copy(
                    volumeChart = if (currentState.data.volumeChart.isFailure()) AsyncData.NotAsked else currentState.data.volumeChart,
                    durationChart = if (currentState.data.durationChart.isFailure()) AsyncData.NotAsked else currentState.data.durationChart,
                    frequencyChart = if (currentState.data.frequencyChart.isFailure()) AsyncData.NotAsked else currentState.data.frequencyChart
                )
            )
        }
        Timber.d("Error state cleared")
    }
}