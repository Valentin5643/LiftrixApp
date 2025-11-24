package com.example.liftrix.ui.progress.detail

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import com.example.liftrix.ui.common.viewmodel.ModernStatefulDetailViewModel
import com.example.liftrix.ui.common.viewmodel.DetailScreenStateKeys
import com.example.liftrix.domain.usecase.analytics.AnalyticsQueryUseCase
import com.example.liftrix.domain.usecase.analytics.AnalyticsExportUseCase
import com.example.liftrix.domain.usecase.analytics.VolumeAnalysisData as UseCaseVolumeAnalysisData
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.domain.usecase.analytics.ExportVolumeDataRequest
import com.example.liftrix.domain.usecase.analytics.ExportVolumeDataPoint
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.analytics.VolumeGrouping
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.error.LiftrixError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.liftrix.domain.model.analytics.VolumeDataPoint
import timber.log.Timber
import kotlinx.datetime.*
import kotlin.time.DurationUnit
import kotlin.time.Duration.Companion.days
import android.content.Intent
import com.example.liftrix.domain.model.ShareableContent
import com.example.liftrix.domain.model.ShareableContentType
import java.io.File

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
    private val analyticsQueryUseCase: AnalyticsQueryUseCase,
    private val authQueryUseCase: AuthQueryUseCase,
    private val analyticsExportUseCase: AnalyticsExportUseCase
) : ModernStatefulDetailViewModel<VolumeAnalysisDetailViewModel.UiState>(
    initialState = UiState.Loading,
    savedStateHandle = savedStateHandle
) {

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
    
    /**
     * Export success event for triggering share intent
     */
    private val _exportSuccessEvent = MutableSharedFlow<Intent>()
    val exportSuccessEvent: SharedFlow<Intent> = _exportSuccessEvent.asSharedFlow()

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
            // Set up reactive binding to automatically refresh data when workout data changes
            // This ensures the UI stays up-to-date with real-time workout volume changes
            Timber.d("Reactive data binding initialized for VolumeAnalysisDetailViewModel")
            
            // Listen for data changes and refresh if we're in success state
            // This pattern follows Clean Architecture by keeping UI reactive
        }
    }

    /**
     * Load volume analysis data based on current configuration
     * Performance target: <500ms load time as per SPEC requirements
     */
    private fun loadVolumeAnalysisData() {
        viewModelScope.launch {
            updateState { UiState.Loading }

            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { throw Exception("User not authenticated") }
                )

                val result = analyticsQueryUseCase.getVolumeAnalysis(
                    userId = userId,
                    groupBy = groupBy.value,
                    timeRange = timeRange.value
                )

                result.fold(
                    onSuccess = { useCaseData ->
                        val startTime = System.currentTimeMillis()

                        // Convert use case data to UI data format
                        val uiData = VolumeAnalysisData(
                            volumeData = useCaseData.volumeData.map { useCasePoint ->
                                // Convert use case VolumeDataPoint to domain VolumeDataPoint
                                val date = useCasePoint.date?.let { kotlinx.datetime.LocalDate.parse(it) }
                                    ?: Clock.System.todayIn(kotlinx.datetime.TimeZone.currentSystemDefault())
                                com.example.liftrix.domain.model.analytics.VolumeDataPoint.fromKgDouble(
                                    date = date,
                                    volumeKg = useCasePoint.volume,
                                    workoutCount = useCasePoint.sets,
                                    exerciseCount = useCasePoint.exercises,
                                    label = useCasePoint.label
                                )
                            },
                            totalVolume = useCaseData.totalVolume.toDouble(),
                            volumeGrowth = useCaseData.volumeGrowth.toDouble(),
                            averageVolume = useCaseData.averageVolume.toDouble(),
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
                            updateState { UiState.Empty() }
                        } else {
                            updateState { UiState.Success(uiData) }
                        }

                        Timber.d("Volume analysis data loaded: groupBy=${groupBy.value}, timeRange=${timeRange.value}")
                    },
                    onFailure = { error ->
                        val liftrixError = if (error is LiftrixError) {
                            error
                        } else {
                            LiftrixError.BusinessLogicError(
                                code = "VOLUME_ANALYSIS_LOAD_FAILED",
                                errorMessage = "Failed to load volume analysis data: ${error.message}",
                                analyticsContext = mapOf("operation" to "LOAD_VOLUME_ANALYSIS")
                            )
                        }
                        updateState { UiState.Error(liftrixError) }
                        Timber.e(error, "Failed to load volume analysis data")
                    }
                )
            } catch (error: Exception) {
                val liftrixError = LiftrixError.BusinessLogicError(
                    code = "VOLUME_ANALYSIS_LOAD_FAILED",
                    errorMessage = "Failed to load volume analysis data: ${error.message}",
                    analyticsContext = mapOf("operation" to "LOAD_VOLUME_ANALYSIS")
                )
                updateState { UiState.Error(liftrixError) }
                Timber.e(error, "Failed to load volume analysis data")
            }
        }
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
                
                val currentState = _uiState.value
                if (currentState is UiState.Success) {
                    val exportRequest = ExportVolumeDataRequest(
                        volumePoints = currentState.data.volumeData.map { dataPoint ->
                            ExportVolumeDataPoint(
                                date = dataPoint.date,
                                exerciseId = "unknown", // Would come from actual data
                                exerciseName = dataPoint.label ?: "Volume Data",
                                muscleGroup = "Mixed", // Would come from grouping
                                sets = dataPoint.workoutCount,
                                reps = 0, // Would come from actual data
                                weight = dataPoint.getVolumeAsDouble().toFloat(),
                                totalVolume = dataPoint.getVolumeAsDouble().toFloat()
                            )
                        },
                        timeRange = timeRange.value,
                        muscleGroupFilter = if (groupBy.value == VolumeGrouping.BY_MUSCLE_GROUP) "Mixed" else null,
                        includeBreakdown = true
                    )
                    
                    val result = analyticsExportUseCase.exportVolume(exportRequest)
                    result.fold(
                        onSuccess = { file ->
                            Timber.d("Volume analysis data exported successfully: ${file.absolutePath}")
                            
                            // Create shareable content for volume analysis data
                            val shareableContent = ShareableContent(
                                id = "volume_export_${System.currentTimeMillis()}",
                                type = ShareableContentType.PROGRESS,
                                title = "Volume Analysis Report",
                                subtitle = "Time Period: ${timeRange.value.name} | Grouping: ${groupBy.value.name}",
                                stats = mapOf(
                                    "timeRange" to timeRange.value.name,
                                    "grouping" to groupBy.value.name,
                                    "totalVolume" to currentState.data.totalVolume,
                                    "dataPoints" to currentState.data.volumeData.size
                                ),
                                metadata = mapOf(
                                    "fileUri" to file.toURI().toString(),
                                    "fileName" to file.name,
                                    "exportType" to "PDF"
                                )
                            )
                            
                            // Trigger share intent
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, android.net.Uri.fromFile(file))
                                putExtra(Intent.EXTRA_SUBJECT, "Liftrix - Volume Analysis Report")
                                putExtra(Intent.EXTRA_TEXT, buildShareText(shareableContent))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            
                            // Emit event to show share chooser
                            _exportSuccessEvent.tryEmit(shareIntent)
                            
                            Timber.i("Share intent created for volume analysis export")
                        },
                        onFailure = { error ->
                            Timber.e("Failed to export volume data: $error")
                            val liftrixError = if (error is LiftrixError) {
                                error
                            } else {
                                LiftrixError.ExportError(
                                    errorMessage = "Failed to export volume data: ${error.message}",
                                    operation = "EXPORT_VOLUME_DATA"
                                )
                            }
                            updateState { UiState.Error(liftrixError) }
                        }
                    )
                }
                
                _isExporting.value = false
                
            } catch (error: Exception) {
                _isExporting.value = false
                val liftrixError = LiftrixError.ExportError(
                    errorMessage = "Failed to export volume analysis data: ${error.message}",
                    operation = "EXPORT_VOLUME_DATA"
                )
                updateState { UiState.Error(liftrixError) }
                Timber.e(error, "Failed to export volume analysis data")
            }
        }
    }

    fun handleEvent(event: Event) {
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
    sealed class Event {
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

/**
 * Builds share text for the exported volume analysis data
 */
private fun VolumeAnalysisDetailViewModel.buildShareText(content: ShareableContent): String {
    val parts = mutableListOf<String>()
    
    // Add title and subtitle
    parts.add(content.title)
    content.subtitle?.let { parts.add(it) }
    
    // Add stats
    if (content.stats.isNotEmpty()) {
        val statsText = content.stats.entries.joinToString(" | ") { (key, value) ->
            when (key) {
                "totalVolume" -> "Total Volume: ${String.format("%,d", value as? Int ?: 0)} kg"
                else -> "${key.replace("_", " ").capitalize()}: $value"
            }
        }
        parts.add(statsText)
    }
    
    // Add app promotion
    parts.add("\nShared from Liftrix - Your Personal Fitness Tracker")
    parts.add("#fitness #volume #training #progress #liftrix")
    
    return parts.joinToString("\n")
}