package com.example.liftrix.ui.progress

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.common.ErrorHandler
import timber.log.Timber
import com.example.liftrix.service.ProgressDataService
import com.example.liftrix.ui.common.state.AsyncData
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.state.dataOrNull
import com.example.liftrix.ui.common.state.isFailure
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import com.example.liftrix.ui.progress.components.ChartType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
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
 * @param errorHandler Centralized error handling service
 */
@HiltViewModel
class ProgressChartsViewModel @Inject constructor(
    private val progressDataService: ProgressDataService,
    errorHandler: ErrorHandler
) : BaseViewModel<UiState<ProgressChartsState>, ProgressChartsEvent>(errorHandler) {

    /**
     * Internal mutable state for the progress charts screen.
     * Starts with Loading state until user authentication is determined.
     */
    override val _uiState: MutableStateFlow<UiState<ProgressChartsState>> = 
        MutableStateFlow(UiState.Loading)

    /**
     * Internal state for current time range selection.
     * Separate from UI state to enable independent time range updates.
     */
    private val _currentTimeRange = MutableStateFlow(TimeRange.lastMonth())

    /**
     * Current user ID received from Coordinator.
     * Updated via Coordinator events instead of direct auth repository observation.
     */
    private val _currentUserId = MutableStateFlow<String?>(null)

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
    }

    /**
     * Handles all events from the UI following the MVI pattern.
     * 
     * This method processes user interactions and internal events, updating the state
     * accordingly and triggering appropriate data operations.
     * 
     * @param event The event to process
     */
    override fun handleEvent(event: ProgressChartsEvent) {
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
                handleError(
                    com.example.liftrix.domain.model.error.LiftrixError.UnknownError(
                        errorMessage = "Failed to handle event: ${event::class.simpleName}",
                        analyticsContext = mapOf(
                            "event_type" to (event::class.simpleName ?: "Unknown"),
                            "timestamp" to System.currentTimeMillis().toString()
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
                when (event) {
                    is CoordinatorEvent.UserAuthChanged -> {
                        _currentUserId.value = event.userId
                        Timber.d("Charts: User auth changed to ${event.userId}")
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
                        Timber.d("Charts: Time period changed to ${event.timeRange}")
                    }
                    else -> {
                        // Ignore other coordinator events
                    }
                }
            } catch (exception: Exception) {
                handleError(
                    com.example.liftrix.domain.model.error.LiftrixError.UnknownError(
                        errorMessage = "Failed to handle coordinator event: ${event::class.simpleName}",
                        analyticsContext = mapOf(
                            "event_type" to (event::class.simpleName ?: "Unknown"),
                            "timestamp" to System.currentTimeMillis().toString()
                        )
                    )
                )
            }
        }
    }

    /**
     * Updates the error state in the UI.
     * Overrides BaseViewModel method to provide specific error handling for charts.
     * 
     * @param error The error to display in the UI
     */
    override fun updateErrorState(error: com.example.liftrix.domain.model.error.LiftrixError) {
        // For charts, we maintain current state but log the error
        Timber.e("Chart error: ${error.message}")
        // State is already maintained in _uiState, no wrapping needed
    }

    /**
     * Sets the loading state in the UI.
     * Overrides BaseViewModel method to provide specific loading state for charts.
     */
    override fun setLoadingState() {
        val currentState = _uiState.value
        if (currentState is UiState.Success) {
            _uiState.value = UiState.Success(
                currentState.data.copy(
                    volumeChart = AsyncData.Loading(),
                    durationChart = AsyncData.Loading(),
                    frequencyChart = AsyncData.Loading(),
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
        // Set loading state for all charts
        updateChartStates(
            volumeChart = AsyncData.Loading(),
            durationChart = AsyncData.Loading(),
            frequencyChart = AsyncData.Loading()
        )

        // Launch concurrent data loading with timeout
        viewModelScope.launch {
            try {
                kotlinx.coroutines.withTimeout(30000) { // 30 second timeout
                    launch { loadVolumeChart(userId, timeRange) }
                    launch { loadDurationChart(userId, timeRange) }
                    launch { loadFrequencyChart(userId, timeRange) }
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
        val result = progressDataService.getVolumeData(userId, timeRange)
        
        result.fold(
            onSuccess = { data ->
                val totalVolume = data.sumOf { it.totalVolume.toDouble() }.toFloat()
                timber.log.Timber.d("VolumeDebug ViewModel received ${data.size} points, ${totalVolume}kg total")
                updateChartStates(volumeChart = AsyncData.Success(data))
            },
            onFailure = { error ->
                timber.log.Timber.e("VolumeDebug ViewModel failed: ${error.message}")
                val liftrixError = if (error is LiftrixError) error else LiftrixError.UnknownError(error.message ?: "Unknown error")
                updateChartStates(volumeChart = AsyncData.Failure(liftrixError))
                handleError(liftrixError)
            }
        )
    }

    /**
     * Loads duration chart data from the service.
     * 
     * @param userId The user ID for data scoping
     * @param timeRange The time range for data retrieval
     */
    private suspend fun loadDurationChart(userId: String, timeRange: TimeRange) {
        val result = progressDataService.getDurationData(userId, timeRange)
        
        result.fold(
            onSuccess = { data ->
                updateChartStates(durationChart = AsyncData.Success(data))
            },
            onFailure = { error ->
                val liftrixError = if (error is LiftrixError) error else LiftrixError.UnknownError(error.message ?: "Unknown error")
                updateChartStates(durationChart = AsyncData.Failure(liftrixError))
                handleError(liftrixError)
            }
        )
    }

    /**
     * Loads frequency chart data from the service.
     * 
     * @param userId The user ID for data scoping
     * @param timeRange The time range for data retrieval
     */
    private suspend fun loadFrequencyChart(userId: String, timeRange: TimeRange) {
        val result = progressDataService.getFrequencyData(userId, timeRange)
        
        result.fold(
            onSuccess = { data ->
                updateChartStates(frequencyChart = AsyncData.Success(data))
            },
            onFailure = { error ->
                val liftrixError = if (error is LiftrixError) error else LiftrixError.UnknownError(error.message ?: "Unknown error")
                updateChartStates(frequencyChart = AsyncData.Failure(liftrixError))
                handleError(liftrixError)
            }
        )
    }

    /**
     * Updates the chart states in the UI state.
     * 
     * @param volumeChart Optional new volume chart state
     * @param durationChart Optional new duration chart state
     * @param frequencyChart Optional new frequency chart state
     */
    private fun updateChartStates(
        volumeChart: AsyncData<List<com.example.liftrix.domain.repository.VolumeDataPoint>>? = null,
        durationChart: AsyncData<List<com.example.liftrix.domain.repository.DurationDataPoint>>? = null,
        frequencyChart: AsyncData<List<com.example.liftrix.domain.repository.FrequencyDataPoint>>? = null
    ) {
        val currentState = _uiState.value
        if (currentState is UiState.Success) {
            _uiState.value = UiState.Success(
                currentState.data.copy(
                    volumeChart = volumeChart ?: currentState.data.volumeChart,
                    durationChart = durationChart ?: currentState.data.durationChart,
                    frequencyChart = frequencyChart ?: currentState.data.frequencyChart,
                    lastRefreshTimestamp = System.currentTimeMillis()
                )
            )
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