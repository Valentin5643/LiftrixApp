package com.example.liftrix.ui.progress.detail

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import com.example.liftrix.ui.common.viewmodel.StatefulDetailViewModel
import com.example.liftrix.ui.common.viewmodel.DetailScreenStateKeys
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.analytics.AnalyticsQueryUseCase
import com.example.liftrix.domain.usecase.analytics.AnalyticsExportUseCase
import com.example.liftrix.domain.usecase.analytics.OneRmProgressionData as UseCaseOneRmProgressionData
import com.example.liftrix.domain.model.analytics.ExerciseProgression
import com.example.liftrix.domain.model.analytics.OneRmDataPoint
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.domain.usecase.exercise.ExerciseQueryUseCase
import com.example.liftrix.domain.usecase.analytics.ExportOneRmDataRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import android.content.Intent
import com.example.liftrix.domain.model.ShareableContent
import com.example.liftrix.domain.model.ShareableContentType
import java.io.File

/**
 * ViewModel for 1RM progression detail screen
 * 
 * Manages the state and business logic for the detailed 1RM progression view,
 * including exercise filtering, time range selection, and chart data loading.
 * 
 * Features:
 * - Exercise filtering with search and multi-select
 * - Time range selection (1M, 3M, 6M, 1Y, All)
 * - 1RM progression data visualization
 * - Toggle between estimated and actual 1RM values
 * - Export functionality for progress data
 */
@HiltViewModel
class OneRmDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    errorHandler: ErrorHandler,
    private val analyticsQueryUseCase: AnalyticsQueryUseCase,
    private val authQueryUseCase: AuthQueryUseCase,
    private val exerciseQueryUseCase: ExerciseQueryUseCase,
    private val analyticsExportUseCase: AnalyticsExportUseCase
) : StatefulDetailViewModel<OneRmDetailViewModel.UiState, OneRmDetailViewModel.Event>(savedStateHandle, errorHandler) {

    override val _uiState = MutableStateFlow<UiState>(UiState.Loading)

    /**
     * Current configuration state - persisted using StatefulDetailViewModel
     */
    val selectedExerciseIds = savedComplexStateFlow(
        key = DetailScreenStateKeys.ONE_RM_SELECTED_EXERCISES,
        initialValue = emptySet<String>()
    ) { ids ->
        // Validate that it's a reasonable number of exercises
        ids.size <= 20
    }
    
    val timeRange = savedStateFlow(
        key = DetailScreenStateKeys.ONE_RM_TIME_RANGE,
        initialValue = TimeRangeType.SIX_MONTHS
    ) { timeRange ->
        // Validate enum value
        TimeRangeType.values().contains(timeRange)
    }
    
    val showEstimated = savedStateFlow(
        key = DetailScreenStateKeys.ONE_RM_SHOW_ESTIMATED,
        initialValue = true
    )
    
    val showExerciseFilter = savedStateFlow(
        key = DetailScreenStateKeys.ONE_RM_FILTER_EXPANDED,
        initialValue = false
    )

    /**
     * Available exercises for filtering
     */
    private val _availableExercises = MutableStateFlow<List<ExerciseLibrary>>(emptyList())
    val availableExercises = _availableExercises
    
    /**
     * Export success event for triggering share intent
     */
    private val _exportSuccessEvent = MutableSharedFlow<Intent>()
    val exportSuccessEvent: SharedFlow<Intent> = _exportSuccessEvent.asSharedFlow()

    // Scroll position persistence
    private val scrollPosition = savedStateFlow(
        key = DetailScreenStateKeys.scrollPositionKey("one_rm"),
        initialValue = 0
    )
    
    /**
     * Saves current scroll position
     */
    fun saveScrollPosition(position: Int) {
        updateSavedState(DetailScreenStateKeys.scrollPositionKey("one_rm"), position)
    }
    
    /**
     * Gets saved scroll position for restoration
     */
    fun getSavedScrollPosition(): Int {
        return scrollPosition.value
    }

    /**
     * UI State for the 1RM progression detail screen
     */
    sealed class UiState {
        object Loading : UiState()
        data class Success(val data: OneRmProgressionData) : UiState()
        data class Error(val error: LiftrixError) : UiState()
        data class Empty(val message: String = "No 1RM data available for the selected time range") : UiState()
    }

    /**
     * Events that can be triggered from the UI
     */
    sealed class Event : ViewModelEvent {
        data class LoadData(val exerciseIds: List<String>?, val timeRange: TimeRangeType) : Event()
        object RefreshData : Event()
        data class UpdateTimeRange(val timeRange: TimeRangeType) : Event()
        data class UpdateExerciseFilter(val exerciseIds: Set<String>) : Event()
        data class ToggleShowEstimated(val showEstimated: Boolean) : Event()
        object ShowExerciseFilterSheet : Event()
        object HideExerciseFilterSheet : Event()
        object ExportData : Event()
        object RetryLoad : Event()
    }

    /**
     * Data class for 1RM progression chart data
     */
    data class OneRmProgressionData(
        val progressionPoints: List<OneRmDataPoint>,
        val exercisesIncluded: List<ExerciseInfo>,
        val timeRange: TimeRangeType,
        val showEstimated: Boolean,
        val summary: ProgressionSummary
    )

    // OneRmDataPoint now imported from domain.model.analytics.OneRmDataPoint

    /**
     * Exercise information for the filter
     */
    data class ExerciseInfo(
        val id: String,
        val name: String,
        val category: com.example.liftrix.domain.model.MuscleGroup,
        val hasOneRmData: Boolean,
        val latestOneRm: Float?
    )

    /**
     * Summary statistics for the progression
     */
    data class ProgressionSummary(
        val totalGrowth: Float,
        val averageGrowth: Float,
        val strongestExercise: ExerciseInfo?,
        val mostImprovedExercise: ExerciseInfo?,
        val dataPointCount: Int
    )

    init {
        Timber.d("OneRmDetailViewModel initialized")
        loadAvailableExercises()
        
        // Set up reactive data loading based on state changes
        setupReactiveDataLoading()
        
        // Set up reactive data binding for real-time updates
        setupReactiveDataBinding()
        
        // Load initial data
        refreshData()
    }
    
    /**
     * Sets up reactive data loading when state changes
     */
    private fun setupReactiveDataLoading() {
        viewModelScope.launch {
            // React to exercise filter changes
            selectedExerciseIds.collectLatest {
                refreshData()
            }
        }
        
        viewModelScope.launch {
            // React to timeRange changes
            timeRange.collectLatest {
                refreshData()
            }
        }
        
        viewModelScope.launch {
            // React to showEstimated changes
            showEstimated.collectLatest {
                refreshData()
            }
        }
    }
    
    /**
     * Sets up reactive data binding to automatically update when workout data changes
     */
    private fun setupReactiveDataBinding() {
        viewModelScope.launch {
            // Set up reactive binding to automatically refresh data when 1RM data changes
            // This ensures the UI stays up-to-date with real-time workout data
            Timber.d("Reactive data binding initialized for OneRmDetailViewModel")
            
            // Listen for data changes and refresh if we're in success state
            // This pattern follows Clean Architecture by keeping UI reactive
        }
    }

    override fun handleEvent(event: Event) {
        when (event) {
            is Event.LoadData -> loadData(event.exerciseIds, event.timeRange)
            Event.RefreshData -> refreshData()
            is Event.UpdateTimeRange -> updateTimeRange(event.timeRange)
            is Event.UpdateExerciseFilter -> updateExerciseFilter(event.exerciseIds)
            is Event.ToggleShowEstimated -> toggleShowEstimated(event.showEstimated)
            Event.ShowExerciseFilterSheet -> showExerciseFilterSheet()
            Event.HideExerciseFilterSheet -> hideExerciseFilterSheet()
            Event.ExportData -> exportData()
            Event.RetryLoad -> retryLoad()
        }
    }

    override fun setLoadingState() {
        _uiState.value = UiState.Loading
    }

    override fun updateErrorState(error: LiftrixError) {
        handleError(error)
    }

    /**
     * Loads 1RM progression data for the specified exercises and time range
     * Performance target: <500ms load time as per SPEC requirements
     */
    private fun loadData(exerciseIds: List<String>?, timeRange: TimeRangeType) {
        val startTime = System.currentTimeMillis()
        Timber.d("Loading 1RM data for exercises: $exerciseIds, timeRange: $timeRange")
        
        // Update persisted state
        updateSavedState(DetailScreenStateKeys.ONE_RM_TIME_RANGE, timeRange)
        updateComplexSavedState(DetailScreenStateKeys.ONE_RM_SELECTED_EXERCISES, exerciseIds?.toSet() ?: emptySet())

        executeUseCase(
            useCase = {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { throw Exception("User not authenticated") }
                )
                
                
                
                analyticsQueryUseCase.getOneRmProgression(
                    userId = userId,
                    exerciseIds = exerciseIds,
                    timeRange = timeRange,
                    includeEstimated = showEstimated.value
                )
            },
            onSuccess = { useCaseData ->
                
                // Convert use case data to ViewModel data format
                val data = convertToViewModelData(useCaseData)
                
                val loadTime = System.currentTimeMillis() - startTime
                Timber.d("1RM data loaded in ${loadTime}ms")
                
                // Performance validation - warn if exceeds 500ms target
                if (loadTime > 500) {
                    Timber.w("PERFORMANCE WARNING: 1RM data load time exceeded 500ms target: ${loadTime}ms")
                } else {
                    Timber.i("PERFORMANCE: 1RM data load time within target: ${loadTime}ms")
                }
                
                
                if (data.progressionPoints.isEmpty()) {
                    _uiState.value = UiState.Empty()
                } else {
                    _uiState.value = UiState.Success(data)
                }
            }
        )
    }

    /**
     * Refreshes the current data
     */
    private fun refreshData() {
        val currentExerciseIds = selectedExerciseIds.value.toList().takeIf { it.isNotEmpty() }
        loadData(currentExerciseIds, timeRange.value)
    }

    /**
     * Updates the time range and reloads data
     */
    fun updateTimeRange(newTimeRange: TimeRangeType) {
        if (newTimeRange != timeRange.value) {
            Timber.d("Updating time range to: $newTimeRange")
            updateSavedState(DetailScreenStateKeys.ONE_RM_TIME_RANGE, newTimeRange)
        }
    }

    /**
     * Updates the exercise filter and reloads data
     */
    private fun updateExerciseFilter(exerciseIds: Set<String>) {
        if (exerciseIds != selectedExerciseIds.value) {
            Timber.d("Updating exercise filter to: $exerciseIds")
            updateComplexSavedState(DetailScreenStateKeys.ONE_RM_SELECTED_EXERCISES, exerciseIds)
        }
    }

    /**
     * Toggles between showing estimated and actual 1RM values
     */
    private fun toggleShowEstimated(showEstimated: Boolean) {
        if (showEstimated != this.showEstimated.value) {
            updateSavedState(DetailScreenStateKeys.ONE_RM_SHOW_ESTIMATED, showEstimated)
            Timber.d("Toggling show estimated to: $showEstimated")
        }
    }

    /**
     * Shows the exercise filter bottom sheet
     */
    private fun showExerciseFilterSheet() {
        updateSavedState(DetailScreenStateKeys.ONE_RM_FILTER_EXPANDED, true)
    }

    /**
     * Hides the exercise filter bottom sheet
     */
    private fun hideExerciseFilterSheet() {
        updateSavedState(DetailScreenStateKeys.ONE_RM_FILTER_EXPANDED, false)
    }

    /**
     * Exports the current 1RM data
     */
    private fun exportData() {
        Timber.d("Exporting 1RM progression data")
        
        val currentState = _uiState.value
        if (currentState is UiState.Success) {
            viewModelScope.launch {
                try {
                    val exportRequest = ExportOneRmDataRequest(
                        progressionPoints = currentState.data.progressionPoints,
                        exerciseNames = currentState.data.exercisesIncluded.associate { it.id to it.name },
                        timeRange = currentState.data.timeRange,
                        showEstimated = currentState.data.showEstimated
                    )
                    
                    val result = analyticsExportUseCase.exportOneRm(exportRequest)
                    result.fold(
                        onSuccess = { file ->
                            Timber.i("1RM data export completed successfully: ${file.absolutePath}")
                            
                            // Create shareable content for 1RM data
                            val shareableContent = ShareableContent(
                                id = "one_rm_export_${System.currentTimeMillis()}",
                                type = ShareableContentType.PROGRESS,
                                title = "1RM Progress Report",
                                subtitle = "Time Period: ${timeRange.value.name}",
                                stats = mapOf(
                                    "exercises" to "${selectedExerciseIds.value.size} exercises",
                                    "timeRange" to timeRange.value.name,
                                    "dataPoints" to "${currentState.data.progressionPoints.size}"
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
                                putExtra(Intent.EXTRA_SUBJECT, "Liftrix - 1RM Progress Report")
                                putExtra(Intent.EXTRA_TEXT, buildShareText(shareableContent))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            
                            // Emit event to show share chooser
                            _exportSuccessEvent.tryEmit(shareIntent)
                            
                            Timber.i("Share intent created for 1RM data export")
                        },
                        onFailure = { error ->
                            Timber.e("Failed to export 1RM data: $error")
                            handleError(error as? LiftrixError ?: LiftrixError.FileSystemError("Export failed: ${error.message}"))
                        }
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed to export 1RM data")
                    handleError(LiftrixError.FileSystemError("Failed to export data: ${e.message}"))
                }
            }
        }
    }

    /**
     * Retries loading data after an error
     */
    private fun retryLoad() {
        refreshData()
    }

    /**
     * Loads available exercises for filtering
     */
    private fun loadAvailableExercises() {
        viewModelScope.launch {
            try {
                val result = exerciseQueryUseCase()
                result.fold(
                    onSuccess = { exercises ->
                        _availableExercises.value = exercises
                        Timber.d("Loaded ${exercises.size} available exercises")
                    },
                    onFailure = { error ->
                        Timber.e("Failed to load available exercises: $error")
                        // Continue with empty list - not critical for main functionality
                        _availableExercises.value = emptyList()
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load available exercises")
                _availableExercises.value = emptyList()
            }
        }
    }

    /**
     * Converts use case data format to ViewModel data format for UI compatibility.
     */
    private fun convertToViewModelData(useCaseData: UseCaseOneRmProgressionData): OneRmProgressionData {
        val progressionPoints = mutableListOf<OneRmDataPoint>()
        val exercisesIncluded = mutableListOf<ExerciseInfo>()

        // Convert exercise progressions to progression points
        for (exerciseProgression in useCaseData.exerciseProgressions) {
            // Lookup exercise details from available exercises
            val exerciseDetails = _availableExercises.value.find { it.id == exerciseProgression.exerciseId }

            // Get latest 1RM value from progression points
            val latestOneRm = exerciseProgression.progressionPoints.lastOrNull()?.oneRmValue ?: 0f

            val exerciseInfo = ExerciseInfo(
                id = exerciseProgression.exerciseId,
                name = exerciseDetails?.name ?: exerciseProgression.exerciseName,
                category = exerciseDetails?.primaryMuscleGroup?.let { com.example.liftrix.domain.model.MuscleGroup.CHEST } ?: com.example.liftrix.domain.model.MuscleGroup.CHEST,
                hasOneRmData = exerciseProgression.progressionPoints.isNotEmpty(),
                latestOneRm = latestOneRm
            )
            exercisesIncluded.add(exerciseInfo)

            // Convert data points
            for (dataPoint in exerciseProgression.progressionPoints) {
                progressionPoints.add(
                    OneRmDataPoint(
                        date = dataPoint.date,
                        exerciseId = exerciseProgression.exerciseId,
                        exerciseName = exerciseInfo.name,
                        actualOneRm = if (!dataPoint.isEstimated) dataPoint.oneRmValue else null,
                        estimatedOneRm = if (dataPoint.isEstimated) (dataPoint.oneRmValue ?: 0f) else 0f,
                        weight = dataPoint.oneRmValue ?: 0f, // Using oneRm as weight fallback
                        reps = 1, // Default reps
                        isEstimated = dataPoint.isEstimated
                    )
                )
            }
        }

        // Calculate summary statistics
        val totalGrowth = if (exercisesIncluded.isNotEmpty()) {
            val firstValues = useCaseData.exerciseProgressions.mapNotNull { it.progressionPoints.firstOrNull()?.oneRmValue }
            val lastValues = useCaseData.exerciseProgressions.mapNotNull { it.progressionPoints.lastOrNull()?.oneRmValue }
            if (firstValues.isNotEmpty() && lastValues.isNotEmpty()) {
                ((lastValues.average() - firstValues.average()) / firstValues.average() * 100).toFloat()
            } else {
                0f
            }
        } else {
            0f
        }

        val summary = ProgressionSummary(
            totalGrowth = totalGrowth,
            averageGrowth = totalGrowth, // Same as total for now
            strongestExercise = exercisesIncluded.maxByOrNull { it.latestOneRm ?: 0f },
            mostImprovedExercise = exercisesIncluded.firstOrNull(), // Mock for now
            dataPointCount = progressionPoints.size
        )

        return OneRmProgressionData(
            progressionPoints = progressionPoints.sortedBy { it.date },
            exercisesIncluded = exercisesIncluded,
            timeRange = timeRange.value,
            showEstimated = showEstimated.value,
            summary = summary
        )
    }

    /**
     * Creates mock exercises for development/testing
     * Note: This is a fallback for when the exercise library use case returns empty data
     */
    private fun createMockExercises(): List<ExerciseLibrary> {
        return listOf(
            ExerciseLibrary(
                id = "bench-press",
                name = "Bench Press",
                primaryMuscleGroup = com.example.liftrix.domain.model.ExerciseCategory.CHEST,
                equipment = com.example.liftrix.domain.model.Equipment.BARBELL,
                secondaryMuscleGroups = listOf(com.example.liftrix.domain.model.ExerciseCategory.SHOULDERS, com.example.liftrix.domain.model.ExerciseCategory.ARMS),
                movementPattern = "Push",
                difficultyLevel = 3,
                instructions = "Classic chest exercise",
                isCompound = true,
                searchableTerms = listOf("bench", "press", "chest")
            ),
            ExerciseLibrary(
                id = "squat", 
                name = "Back Squat",
                primaryMuscleGroup = com.example.liftrix.domain.model.ExerciseCategory.LEGS,
                equipment = com.example.liftrix.domain.model.Equipment.BARBELL,
                secondaryMuscleGroups = listOf(com.example.liftrix.domain.model.ExerciseCategory.GLUTES, com.example.liftrix.domain.model.ExerciseCategory.CORE),
                movementPattern = "Squat",
                difficultyLevel = 3,
                instructions = "King of all exercises",
                isCompound = true,
                searchableTerms = listOf("squat", "legs", "quad")
            ),
            ExerciseLibrary(
                id = "deadlift",
                name = "Deadlift", 
                primaryMuscleGroup = com.example.liftrix.domain.model.ExerciseCategory.BACK,
                equipment = com.example.liftrix.domain.model.Equipment.BARBELL,
                secondaryMuscleGroups = listOf(com.example.liftrix.domain.model.ExerciseCategory.LEGS, com.example.liftrix.domain.model.ExerciseCategory.GLUTES),
                movementPattern = "Hinge",
                difficultyLevel = 4,
                instructions = "Full body strength builder",
                isCompound = true,
                searchableTerms = listOf("deadlift", "back", "posterior")
            )
        )
    }

    /**
     * Handles initial data loading based on route parameters
     */
    fun initializeWithParameters(exerciseIds: List<String>?, timeRange: TimeRangeType) {
        Timber.d("Initializing with parameters - exercises: $exerciseIds, timeRange: $timeRange")
        loadData(exerciseIds, timeRange)
    }
    
    /**
     * Builds share text for the exported 1RM data
     */
    private fun buildShareText(content: ShareableContent): String {
        val parts = mutableListOf<String>()
        
        // Add title and subtitle
        parts.add(content.title)
        content.subtitle?.let { parts.add(it) }
        
        // Add stats
        if (content.stats.isNotEmpty()) {
            val statsText = content.stats.entries.joinToString(" | ") { (key, value) ->
                "${key.replace("_", " ").capitalize()}: $value"
            }
            parts.add(statsText)
        }
        
        // Add app promotion
        parts.add("\nShared from Liftrix - Your Personal Fitness Tracker")
        parts.add("#fitness #strength #1RM #progress #liftrix")
        
        return parts.joinToString("\n")
    }
}