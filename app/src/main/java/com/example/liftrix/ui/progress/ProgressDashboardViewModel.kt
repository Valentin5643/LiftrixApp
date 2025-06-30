package com.example.liftrix.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.DurationDataPoint
import com.example.liftrix.domain.repository.FrequencyDataPoint
import com.example.liftrix.domain.repository.ProgressStatsRepository
import com.example.liftrix.domain.repository.ProgressSummary
import com.example.liftrix.domain.repository.VolumeDataPoint
import com.example.liftrix.ui.common.loadDataWithAuth
import com.example.liftrix.ui.common.updateState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import timber.log.Timber
import javax.inject.Inject
import kotlin.random.Random

/**
 * ViewModel for progress dashboard state management following MVI pattern.
 * 
 * Manages complex state for multiple chart types with time period filtering,
 * data loading coordination, and authentication integration. Provides reactive
 * state updates for volume charts, duration charts, frequency heatmaps, and
 * progress summary statistics.
 * 
 * @param progressStatsRepository Repository for progress data aggregation
 * @param authRepository Repository for authentication state and user management
 */
@HiltViewModel
class ProgressDashboardViewModel @Inject constructor(
    private val progressStatsRepository: ProgressStatsRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    // Enable mock data for development - set to false for production
    private val useMockData = true

    private val _uiState = MutableStateFlow(
        if (useMockData) {
            ProgressDashboardUiState(
                volumeData = generateMockVolumeData(),
                durationData = generateMockDurationData(),
                frequencyData = generateMockFrequencyData(),
                summaryData = generateMockSummaryData()
            )
        } else {
            ProgressDashboardUiState()
        }
    )
    val uiState: StateFlow<ProgressDashboardUiState> = _uiState.asStateFlow()

    init {
        if (!useMockData) {
            // Load real data from repository
            loadVolumeData()
            loadDurationData() 
            loadFrequencyData()
            loadSummaryData()
        }
        observeAuthState()
    }

    /**
     * Handles events from the progress dashboard UI
     */
    fun onEvent(event: ProgressDashboardEvent) {
        when (event) {
            is ProgressDashboardEvent.TimePeriodChanged -> {
                handleTimePeriodChange(event.timePeriod)
            }
            is ProgressDashboardEvent.RefreshData -> {
                refreshAllData()
            }
            is ProgressDashboardEvent.ClearError -> {
                updateState { copy(error = null) }
            }
            is ProgressDashboardEvent.LoadVolumeChart -> {
                loadVolumeData()
            }
            is ProgressDashboardEvent.LoadDurationChart -> {
                loadDurationData()
            }
            is ProgressDashboardEvent.LoadFrequencyChart -> {
                loadFrequencyData()
            }
            is ProgressDashboardEvent.LoadSummaryStats -> {
                loadSummaryData()
            }
        }
    }

    /**
     * Observe authentication state to reload data when user changes
     */
    private fun observeAuthState() {
        if (!useMockData) {
            viewModelScope.launch {
                authRepository.currentUser.collect { user ->
                    if (user != null) {
                        loadInitialData()
                    } else {
                        updateState {
                            copy(
                                volumeData = emptyList(),
                                durationData = emptyList(),
                                frequencyData = emptyList(),
                                summaryData = ProgressSummary(0, 0f, 0, 0, 0, 0f, 0),
                                isVolumeLoading = false,
                                isDurationLoading = false,
                                isFrequencyLoading = false,
                                isSummaryLoading = false,
                                error = null
                            )
                        }
                    }
                }
            }
        }
        // When using mock data, skip auth state observation to preserve mock data
    }

    /**
     * Load initial data for all charts
     */
    private fun loadInitialData() {
        refreshAllData()
    }

    /**
     * Handle time period selection change
     */
    private fun handleTimePeriodChange(timePeriod: TimePeriod) {
        updateState { copy(selectedTimePeriod = timePeriod) }
        if (!useMockData) {
            refreshAllData()
        }
        // When using mock data, only update the period selection
        Timber.d("Time period changed to: $timePeriod")
    }

    /**
     * Refresh all chart data
     */
    private fun refreshAllData() {
        if (!useMockData) {
            loadVolumeData()
            loadDurationData()
            loadFrequencyData()
            loadSummaryData()
        }
        // When using mock data, skip refresh to preserve mock data
    }

    /**
     * Load workout volume chart data
     */
    private fun loadVolumeData() {
        val (startDate, endDate) = getDateRangeForPeriod(_uiState.value.selectedTimePeriod)
        
        loadDataWithAuth(
            authRepository = authRepository,
            uiState = _uiState,
            dataLoader = progressStatsRepository::getWorkoutVolumeData,
            updateLoading = { copy(isVolumeLoading = it) },
            updateData = { copy(volumeData = it) },
            updateError = { copy(error = it) },
            dataType = "volume data",
            startDate = startDate,
            endDate = endDate
        )
    }

    /**
     * Load workout duration chart data
     */
    private fun loadDurationData() {
        val (startDate, endDate) = getDateRangeForPeriod(_uiState.value.selectedTimePeriod)
        
        loadDataWithAuth(
            authRepository = authRepository,
            uiState = _uiState,
            dataLoader = progressStatsRepository::getWorkoutDurationData,
            updateLoading = { copy(isDurationLoading = it) },
            updateData = { copy(durationData = it) },
            updateError = { copy(error = it) },
            dataType = "duration data",
            startDate = startDate,
            endDate = endDate
        )
    }

    /**
     * Load workout frequency heatmap data
     */
    private fun loadFrequencyData() {
        val (startDate, endDate) = getDateRangeForPeriod(_uiState.value.selectedTimePeriod)
        
        loadDataWithAuth(
            authRepository = authRepository,
            uiState = _uiState,
            dataLoader = progressStatsRepository::getWorkoutFrequencyData,
            updateLoading = { copy(isFrequencyLoading = it) },
            updateData = { copy(frequencyData = it) },
            updateError = { copy(error = it) },
            dataType = "frequency data",
            startDate = startDate,
            endDate = endDate
        )
    }

    /**
     * Load progress summary statistics
     */
    private fun loadSummaryData() {
        val (startDate, endDate) = getDateRangeForPeriod(_uiState.value.selectedTimePeriod)
        
        loadDataWithAuth(
            authRepository = authRepository,
            uiState = _uiState,
            dataLoader = progressStatsRepository::getProgressSummary,
            updateLoading = { copy(isSummaryLoading = it) },
            updateData = { copy(summaryData = it) },
            updateError = { copy(error = it) },
            dataType = "summary data",
            startDate = startDate,
            endDate = endDate
        )
    }

    /**
     * Calculate start and end dates for the selected time period
     */
    private fun getDateRangeForPeriod(timePeriod: TimePeriod): Pair<LocalDate, LocalDate> {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        
        return when (timePeriod) {
            TimePeriod.WEEK -> {
                val startDate = today.minus(DatePeriod(days = 7))
                Pair(startDate, today)
            }
            TimePeriod.MONTH -> {
                val startDate = today.minus(DatePeriod(months = 1))
                Pair(startDate, today)
            }
            TimePeriod.QUARTER -> {
                val startDate = today.minus(DatePeriod(months = 3))
                Pair(startDate, today)
            }
            TimePeriod.YEAR -> {
                val startDate = today.minus(DatePeriod(years = 1))
                Pair(startDate, today)
            }
        }
    }

    /**
     * Update state using a lambda function
     */
    private fun updateState(update: ProgressDashboardUiState.() -> ProgressDashboardUiState) {
        _uiState.updateState(update)
    }

    /**
     * Generate mock volume data for development and testing
     */
    private fun generateMockVolumeData(): List<VolumeDataPoint> {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        return (0..13).map { daysBack ->
            val date = today.minus(DatePeriod(days = daysBack))
            val volume = Random.nextInt(1000, 3001).toFloat() + (daysBack * 50) // Trending upward
            VolumeDataPoint(
                date = date,
                totalVolume = volume,
                exerciseCount = Random.nextInt(3, 9)
            )
        }.reversed() // Show oldest to newest
    }

    /**
     * Generate mock duration data for development and testing
     */
    private fun generateMockDurationData(): List<DurationDataPoint> {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        return (0..13).map { daysBack ->
            val date = today.minus(DatePeriod(days = daysBack))
            val duration = Random.nextInt(45, 91) // 45-90 minute workouts
            DurationDataPoint(
                date = date,
                durationMinutes = duration,
                workoutCount = 1
            )
        }.reversed() // Show oldest to newest
    }

    /**
     * Generate mock frequency data for development and testing
     */
    private fun generateMockFrequencyData(): List<FrequencyDataPoint> {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        return (0..83).mapNotNull { daysBack -> // Last 12 weeks
            val date = today.minus(DatePeriod(days = daysBack))
            // Simulate some workout days (about 3-4 per week)
            if (Random.nextInt(0, 7) < 4) { // 4/7 chance of workout
                val workoutCount = if (Random.nextInt(0, 10) < 8) 1 else 2 // Mostly 1 workout, sometimes 2
                FrequencyDataPoint(
                    date = date,
                    workoutCount = workoutCount,
                    intensity = Random.nextFloat() * (1.0f - 0.3f) + 0.3f
                )
            } else null
        }.reversed() // Show oldest to newest
    }

    /**
     * Generate mock summary data for development and testing
     */
    private fun generateMockSummaryData(): ProgressSummary {
        return ProgressSummary(
            totalWorkouts = 28,
            totalVolume = 45000f,
            averageDuration = 67,
            currentStreak = 5,
            longestStreak = 12,
            averageWorkoutsPerWeek = 3.5f,
            totalActiveTime = 1876 // 28 workouts * 67 minutes average
        )
    }
}

/**
 * UI state for progress dashboard
 * 
 * @param selectedTimePeriod Currently selected time period for filtering
 * @param volumeData Workout volume chart data points
 * @param durationData Workout duration chart data points
 * @param frequencyData Workout frequency heatmap data points
 * @param summaryData Progress summary statistics
 * @param isVolumeLoading Whether volume chart is loading
 * @param isDurationLoading Whether duration chart is loading
 * @param isFrequencyLoading Whether frequency chart is loading
 * @param isSummaryLoading Whether summary stats are loading
 * @param error Error message to display, null if no error
 */
data class ProgressDashboardUiState(
    val selectedTimePeriod: TimePeriod = TimePeriod.MONTH,
    val volumeData: List<VolumeDataPoint> = emptyList(),
    val durationData: List<DurationDataPoint> = emptyList(),
    val frequencyData: List<FrequencyDataPoint> = emptyList(),
    val summaryData: ProgressSummary = ProgressSummary(0, 0f, 0, 0, 0, 0f, 0),
    val isVolumeLoading: Boolean = false,
    val isDurationLoading: Boolean = false,
    val isFrequencyLoading: Boolean = false,
    val isSummaryLoading: Boolean = false,
    val error: String? = null
) {
    /**
     * Convenience property to check if any chart is loading
     */
    val isAnyChartLoading: Boolean
        get() = isVolumeLoading || isDurationLoading || isFrequencyLoading || isSummaryLoading

    /**
     * Convenience property to check if all charts have data
     */
    val hasAllData: Boolean
        get() = volumeData.isNotEmpty() && durationData.isNotEmpty() && 
               frequencyData.isNotEmpty() && summaryData.totalWorkouts > 0

    /**
     * Convenience property to check if we're in an empty state
     */
    val isEmpty: Boolean
        get() = volumeData.isEmpty() && durationData.isEmpty() && 
               frequencyData.isEmpty() && summaryData.totalWorkouts == 0 && !isAnyChartLoading
}

/**
 * Time period options for progress dashboard filtering
 */
enum class TimePeriod(val displayName: String, val days: Int) {
    WEEK("Week", 7),
    MONTH("Month", 30),
    QUARTER("Quarter", 90),
    YEAR("Year", 365)
}

/**
 * Events that can be triggered from the progress dashboard UI
 */
sealed class ProgressDashboardEvent {
    /**
     * Time period selection changed
     */
    data class TimePeriodChanged(val timePeriod: TimePeriod) : ProgressDashboardEvent()
    
    /**
     * Refresh all dashboard data
     */
    data object RefreshData : ProgressDashboardEvent()
    
    /**
     * Load volume chart data specifically
     */
    data object LoadVolumeChart : ProgressDashboardEvent()
    
    /**
     * Load duration chart data specifically
     */
    data object LoadDurationChart : ProgressDashboardEvent()
    
    /**
     * Load frequency chart data specifically
     */
    data object LoadFrequencyChart : ProgressDashboardEvent()
    
    /**
     * Load summary statistics specifically
     */
    data object LoadSummaryStats : ProgressDashboardEvent()
    
    /**
     * Clear any error state
     */
    data object ClearError : ProgressDashboardEvent()
}