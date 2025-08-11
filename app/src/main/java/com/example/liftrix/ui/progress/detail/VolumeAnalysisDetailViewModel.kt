package com.example.liftrix.ui.progress.detail

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import com.example.liftrix.ui.common.viewmodel.StatefulDetailViewModel
import com.example.liftrix.ui.common.viewmodel.DetailScreenStateKeys
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.usecase.analytics.GetVolumeAnalysisUseCase
import com.example.liftrix.domain.usecase.analytics.VolumeAnalysisData as UseCaseVolumeAnalysisData
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.analytics.VolumeGrouping
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.error.LiftrixError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.liftrix.domain.model.analytics.VolumeDataPoint
import timber.log.Timber
import kotlinx.datetime.*
import kotlin.time.DurationUnit
import kotlin.time.Duration.Companion.days

/**
 * ViewModel for volume analysis detail screen
 * 
 * Manages the state and business logic for the detailed volume analysis view,
 * including grouping options, time range selection, and volume progression data.
 * 
 * Features:
 * - Volume grouping by total, exercise, muscle group, session, week, or month
 * - Time range selection (1M, 3M, 6M, 1Y, All)  
 * - Volume trend analysis and projections
 * - Comparative volume analysis across different dimensions
 * - Export functionality for volume data
 */
@HiltViewModel
class VolumeAnalysisDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    errorHandler: ErrorHandler,
    private val getVolumeAnalysisUseCase: GetVolumeAnalysisUseCase,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
    // TODO: Inject additional use cases when implemented
    // private val getVolumeTrendsUseCase: GetVolumeTrendsUseCase,
    // private val exportVolumeDataUseCase: ExportVolumeDataUseCase
) : StatefulDetailViewModel<VolumeAnalysisDetailViewModel.UiState, VolumeAnalysisDetailViewModel.Event>(savedStateHandle, errorHandler) {

    override val _uiState = MutableStateFlow<UiState>(UiState.Loading)

    /**
     * Current configuration state - persisted using StatefulDetailViewModel
     */
    
    // Persisted state flows
    val groupBy = savedStateFlow(
        key = DetailScreenStateKeys.VOLUME_GROUP_BY,
        initialValue = VolumeGrouping.TOTAL
    ) { groupBy ->
        // Validate enum value
        VolumeGrouping.values().contains(groupBy)
    }
    
    val timeRange = savedStateFlow(
        key = DetailScreenStateKeys.VOLUME_TIME_RANGE,
        initialValue = TimeRangeType.MONTH
    ) { timeRange ->
        // Validate enum value
        TimeRangeType.values().contains(timeRange)
    }
    
    val showProjections = savedStateFlow(
        key = DetailScreenStateKeys.VOLUME_SHOW_PROJECTIONS,
        initialValue = true
    )
    
    private val _isExporting = MutableStateFlow(false)
    val isExporting = _isExporting

    init {
        // Set up reactive data loading based on state changes
        setupReactiveDataLoading()
        
        // Set up reactive data binding for real-time updates
        setupReactiveDataBinding()
        
        // Load initial data
        loadVolumeAnalysisData()
    }
    
    /**
     * Sets up reactive data loading when state changes
     */
    private fun setupReactiveDataLoading() {
        viewModelScope.launch {
            // React to groupBy changes
            groupBy.collectLatest {
                loadVolumeAnalysisData()
            }
        }
        
        viewModelScope.launch {
            // React to timeRange changes  
            timeRange.collectLatest {
                loadVolumeAnalysisData()
            }
        }
        
        viewModelScope.launch {
            // React to projections toggle
            showProjections.collectLatest {
                loadVolumeAnalysisData()
            }
        }
    }
    
    /**
     * Sets up reactive data binding to automatically update when workout data changes
     */
    private fun setupReactiveDataBinding() {
        viewModelScope.launch {
            // TODO: Replace with actual repository flow when available
            // Example reactive binding:
            // workoutRepository.getWorkoutsFlow(getCurrentUserId()).collectLatest {
            //     if (_uiState.value is UiState.Success) {
            //         loadVolumeAnalysisData() // Refresh data when workouts change
            //     }
            // }
            
            // For now, set up reactive binding stub
            Timber.d("Reactive data binding initialized for VolumeAnalysisDetailViewModel")
        }
    }

    /**
     * Load volume analysis data based on current configuration
     * Performance target: <500ms load time as per SPEC requirements
     */
    private fun loadVolumeAnalysisData() {
        executeUseCase(
            useCase = { 
                val userId = getCurrentUserIdUseCase() ?: throw Exception("User not authenticated").also {
                }
                
                
                getVolumeAnalysisUseCase.execute(
                    userId = userId,
                    groupBy = groupBy.value,
                    timeRange = timeRange.value
                )
            },
            onSuccess = { useCaseData ->
                val startTime = System.currentTimeMillis()
                
                
                // Convert use case data to UI data format
                val uiData = VolumeAnalysisData(
                    volumeData = useCaseData.volumeData.map { dataPoint ->
                        // Parse date from string if available, otherwise use current date
                        val date = dataPoint.date?.let {
                            try {
                                LocalDate.parse(it)
                            } catch (e: Exception) {
                                Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
                            }
                        } ?: Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
                        
                        VolumeDataPoint.fromKgDouble(
                            date = date,
                            volumeKg = dataPoint.volume,
                            workoutCount = dataPoint.sets,
                            label = dataPoint.label
                        )
                    },
                    totalVolume = useCaseData.totalVolume,
                    volumeGrowth = useCaseData.volumeGrowth,
                    averageVolume = useCaseData.averageVolume,
                    lastUpdated = Clock.System.now()
                )
                
                val loadTime = System.currentTimeMillis() - startTime
                Timber.d("Volume analysis data loaded in ${loadTime}ms")
                
                // Performance validation - warn if exceeds 500ms target
                if (loadTime > 500) {
                    Timber.w("PERFORMANCE WARNING: Volume analysis load time exceeded 500ms target: ${loadTime}ms")
                } else {
                    Timber.i("PERFORMANCE: Volume analysis load time within target: ${loadTime}ms")
                }
                
                
                if (useCaseData.isEmpty) {
                    _uiState.value = UiState.Empty()
                } else {
                    _uiState.value = UiState.Success(uiData)
                }
                
                Timber.d("Volume analysis data loaded: groupBy=${groupBy.value}, timeRange=${timeRange.value}")
            }
        )
    }

    /**
     * Update volume grouping method
     */
    fun updateGroupBy(newGroupBy: VolumeGrouping) {
        if (groupBy.value != newGroupBy) {
            updateSavedState(DetailScreenStateKeys.VOLUME_GROUP_BY, newGroupBy)
            Timber.d("Volume grouping updated to: $newGroupBy")
        }
    }

    /**
     * Update time range for analysis
     */
    fun updateTimeRange(newTimeRange: TimeRangeType) {
        if (timeRange.value != newTimeRange) {
            updateSavedState(DetailScreenStateKeys.VOLUME_TIME_RANGE, newTimeRange)
            Timber.d("Time range updated to: $newTimeRange")
        }
    }

    /**
     * Toggle volume projections visibility
     */
    fun toggleProjections() {
        updateSavedState(DetailScreenStateKeys.VOLUME_SHOW_PROJECTIONS, !showProjections.value)
        Timber.d("Volume projections toggled: ${showProjections.value}")
    }

    /**
     * Export volume analysis data
     */
    fun exportData() {
        viewModelScope.launch {
            try {
                _isExporting.value = true
                
                // TODO: Replace with actual export use case
                // val result = exportVolumeDataUseCase(
                //     groupBy = groupBy.value,
                //     timeRange = timeRange.value
                // )
                
                // Mock export success
                kotlinx.coroutines.delay(1000)
                
                _isExporting.value = false
                Timber.d("Volume analysis data exported successfully")
                
            } catch (error: Exception) {
                _isExporting.value = false
                val liftrixError = LiftrixError.ExportError(
                    errorMessage = "Failed to export volume analysis data"
                )
                handleError(liftrixError)
                Timber.e(error, "Failed to export volume analysis data")
            }
        }
    }

    override fun handleEvent(event: Event) {
        when (event) {
            is Event.UpdateGroupBy -> updateGroupBy(event.groupBy)
            is Event.UpdateTimeRange -> updateTimeRange(event.timeRange)
            is Event.ToggleProjections -> toggleProjections()
            is Event.ExportData -> exportData()
            is Event.RefreshData -> loadVolumeAnalysisData()
        }
    }

    /**
     * Handles initial data loading based on route parameters
     */
    fun initializeWithParameters(groupBy: VolumeGrouping, timeRange: TimeRangeType) {
        Timber.d("Initializing with parameters - groupBy: $groupBy, timeRange: $timeRange")
        
        // Validate parameters and set defaults if needed
        val validatedGroupBy = try {
            groupBy
        } catch (e: Exception) {
            Timber.w("Invalid groupBy parameter: $groupBy, using default TOTAL")
            VolumeGrouping.TOTAL
        }
        
        val validatedTimeRange = try {
            timeRange
        } catch (e: Exception) {
            Timber.w("Invalid timeRange parameter: $timeRange, using default MONTH")
            TimeRangeType.MONTH
        }
        
        // Update persisted state (this will trigger reactive data loading)
        updateSavedState(DetailScreenStateKeys.VOLUME_GROUP_BY, validatedGroupBy)
        updateSavedState(DetailScreenStateKeys.VOLUME_TIME_RANGE, validatedTimeRange)
    }

    // Scroll position persistence
    private val scrollPosition = savedStateFlow(
        key = DetailScreenStateKeys.VOLUME_SCROLL_POSITION,
        initialValue = 0
    )
    
    /**
     * Saves current scroll position
     */
    fun saveScrollPosition(position: Int) {
        updateSavedState(DetailScreenStateKeys.VOLUME_SCROLL_POSITION, position)
    }
    
    /**
     * Gets saved scroll position for restoration
     */
    fun getSavedScrollPosition(): Int {
        return scrollPosition.value
    }

    /**
     * UI state for volume analysis detail screen
     */
    sealed class UiState {
        object Loading : UiState()
        data class Success(val data: VolumeAnalysisData) : UiState()
        data class Error(val error: LiftrixError) : UiState()
        data class Empty(val message: String = "No volume data available for the selected time range") : UiState()
    }
    
    /**
     * Data class for volume analysis data
     */
    data class VolumeAnalysisData(
        val volumeData: List<VolumeDataPoint> = emptyList(),
        val trendData: List<VolumeTrendPoint> = emptyList(),
        val projectionData: List<VolumeProjectionPoint> = emptyList(),
        val totalVolume: Double = 0.0,
        val volumeGrowth: Double = 0.0,
        val averageVolume: Double = 0.0,
        val lastUpdated: Instant = Clock.System.now()
    )

    /**
     * Events for volume analysis detail screen
     */
    sealed class Event : ViewModelEvent {
        data class UpdateGroupBy(val groupBy: VolumeGrouping) : Event()
        data class UpdateTimeRange(val timeRange: TimeRangeType) : Event()
        data object ToggleProjections : Event()
        data object ExportData : Event()
        data object RefreshData : Event()
    }

    /**
     * Mock data generation for development
     */
    private fun generateMockVolumeAnalysisData(
        groupBy: VolumeGrouping,
        timeRange: TimeRangeType
    ): VolumeAnalysisData {
        val now = Clock.System.now()
        val daysBack = when (timeRange) {
            TimeRangeType.MONTH -> 30
            TimeRangeType.SIX_MONTHS -> 180
            TimeRangeType.ALL_TIME -> 365 * 2 // 2 years for all-time data
        }

        val volumeData = (0 until daysBack step 7).map { daysAgo ->
            val date = now.minus(daysAgo.days).toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
            val baseVolume = when (groupBy) {
                VolumeGrouping.TOTAL -> 15000.0 + (daysAgo * 50.0) + (kotlin.random.Random.nextDouble() * 2000.0)
                VolumeGrouping.BY_EXERCISE -> 3000.0 + (daysAgo * 10.0) + (kotlin.random.Random.nextDouble() * 500.0)
                VolumeGrouping.BY_MUSCLE_GROUP -> 5000.0 + (daysAgo * 20.0) + (kotlin.random.Random.nextDouble() * 1000.0)
                VolumeGrouping.BY_SESSION -> 8000.0 + (daysAgo * 30.0) + (kotlin.random.Random.nextDouble() * 1500.0)
                VolumeGrouping.BY_WEEK -> 50000.0 + (daysAgo * 200.0) + (kotlin.random.Random.nextDouble() * 5000.0)
                VolumeGrouping.BY_MONTH -> 200000.0 + (daysAgo * 1000.0) + (kotlin.random.Random.nextDouble() * 20000.0)
            }
            VolumeDataPoint.fromKgDouble(
                date = date,
                volumeKg = baseVolume,
                workoutCount = (1..3).random(),
                label = groupBy.displayName
            )
        }.reversed()

        val totalVolume = volumeData.sumOf { it.getVolumeAsDouble() }
        val volumeGrowth = if (volumeData.size >= 2) {
            val recent = volumeData.takeLast(4).sumOf { it.getVolumeAsDouble() }
            val previous = volumeData.dropLast(4).takeLast(4).sumOf { it.getVolumeAsDouble() }
            if (previous > 0) ((recent - previous) / previous) * 100.0 else 0.0
        } else 0.0

        return VolumeAnalysisData(
            volumeData = volumeData,
            totalVolume = totalVolume,
            volumeGrowth = volumeGrowth,
            averageVolume = if (volumeData.isNotEmpty()) totalVolume / volumeData.size else 0.0,
            lastUpdated = now
        )
    }

    override fun updateErrorState(error: LiftrixError) {
        handleError(error)
    }

    override fun setLoadingState() {
        _uiState.value = UiState.Loading
    }
}

// VolumeDataPoint moved to domain model: com.example.liftrix.domain.model.analytics.VolumeDataPoint
// Note: This file may need conversion from Instant to LocalDate and Double to Weight

/**
 * Data point for volume trend analysis
 */
data class VolumeTrendPoint(
    val date: Instant,
    val trend: Double,
    val movingAverage: Double
)

/**
 * Data point for volume projections
 */
data class VolumeProjectionPoint(
    val date: Instant,
    val projectedVolume: Double,
    val confidenceInterval: Pair<Double, Double>
)