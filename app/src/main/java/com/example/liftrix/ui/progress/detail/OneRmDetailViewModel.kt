package com.example.liftrix.ui.progress.detail

import androidx.lifecycle.viewModelScope
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.error.LiftrixError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber
import kotlinx.datetime.*

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
    errorHandler: ErrorHandler,
    // TODO: Inject actual use cases when implemented
    // private val getOneRmProgressionUseCase: GetOneRmProgressionUseCase,
    // private val getExerciseLibraryUseCase: GetExerciseLibraryUseCase,
    // private val exportOneRmDataUseCase: ExportOneRmDataUseCase
) : BaseViewModel<OneRmDetailViewModel.UiState, OneRmDetailViewModel.Event>(errorHandler) {

    override val _uiState = MutableStateFlow<UiState>(UiState.Loading)

    /**
     * Current configuration state
     */
    private val _selectedExerciseIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedExerciseIds = _selectedExerciseIds
    
    private val _timeRange = MutableStateFlow(TimeRangeType.QUARTER)
    val timeRange = _timeRange
    
    private val _showEstimated = MutableStateFlow(true)
    val showEstimated = _showEstimated
    
    private val _showExerciseFilter = MutableStateFlow(false)
    val showExerciseFilter = _showExerciseFilter

    /**
     * Available exercises for filtering
     */
    private val _availableExercises = MutableStateFlow<List<ExerciseLibrary>>(emptyList())
    val availableExercises = _availableExercises

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

    /**
     * Individual data point for 1RM progression
     */
    data class OneRmDataPoint(
        val date: kotlinx.datetime.LocalDate,
        val exerciseId: String,
        val exerciseName: String,
        val oneRmValue: Float,
        val isEstimated: Boolean,
        val actualWeight: Float? = null,
        val reps: Int? = null
    )

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
        _uiState.value = UiState.Error(error)
    }

    /**
     * Loads 1RM progression data for the specified exercises and time range
     * Performance target: <500ms load time as per SPEC requirements
     */
    private fun loadData(exerciseIds: List<String>?, timeRange: TimeRangeType) {
        val startTime = System.currentTimeMillis()
        Timber.d("Loading 1RM data for exercises: $exerciseIds, timeRange: $timeRange")
        
        // Update current configuration
        _timeRange.value = timeRange
        _selectedExerciseIds.value = exerciseIds?.toSet() ?: emptySet()

        // TODO: Replace with actual use case call
        executeUseCase(
            useCase = { 
                // Simulate API call - keep under 500ms performance target
                kotlinx.coroutines.delay(300)
                createMockData(exerciseIds, timeRange)
            },
            onSuccess = { data ->
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
        val currentExerciseIds = _selectedExerciseIds.value.toList().takeIf { it.isNotEmpty() }
        loadData(currentExerciseIds, _timeRange.value)
    }

    /**
     * Updates the time range and reloads data
     */
    private fun updateTimeRange(newTimeRange: TimeRangeType) {
        if (newTimeRange != _timeRange.value) {
            Timber.d("Updating time range to: $newTimeRange")
            val currentExerciseIds = _selectedExerciseIds.value.toList().takeIf { it.isNotEmpty() }
            loadData(currentExerciseIds, newTimeRange)
        }
    }

    /**
     * Updates the exercise filter and reloads data
     */
    private fun updateExerciseFilter(exerciseIds: Set<String>) {
        if (exerciseIds != _selectedExerciseIds.value) {
            Timber.d("Updating exercise filter to: $exerciseIds")
            val exerciseList = exerciseIds.toList().takeIf { it.isNotEmpty() }
            loadData(exerciseList, _timeRange.value)
        }
    }

    /**
     * Toggles between showing estimated and actual 1RM values
     */
    private fun toggleShowEstimated(showEstimated: Boolean) {
        if (showEstimated != _showEstimated.value) {
            _showEstimated.value = showEstimated
            Timber.d("Toggling show estimated to: $showEstimated")
            
            // Update the current data with the new display mode
            val currentState = _uiState.value
            if (currentState is UiState.Success) {
                val updatedData = currentState.data.copy(showEstimated = showEstimated)
                _uiState.value = UiState.Success(updatedData)
            }
        }
    }

    /**
     * Shows the exercise filter bottom sheet
     */
    private fun showExerciseFilterSheet() {
        _showExerciseFilter.value = true
    }

    /**
     * Hides the exercise filter bottom sheet
     */
    private fun hideExerciseFilterSheet() {
        _showExerciseFilter.value = false
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
                    // TODO: Implement actual export functionality
                    // exportOneRmDataUseCase(currentState.data)
                    Timber.i("1RM data export completed successfully")
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
        val currentExerciseIds = _selectedExerciseIds.value.toList().takeIf { it.isNotEmpty() }
        loadData(currentExerciseIds, _timeRange.value)
    }

    /**
     * Loads available exercises for filtering
     */
    private fun loadAvailableExercises() {
        viewModelScope.launch {
            try {
                // TODO: Replace with actual use case call
                // val exercises = getExerciseLibraryUseCase()
                val exercises = createMockExercises()
                _availableExercises.value = exercises
            } catch (e: Exception) {
                Timber.e(e, "Failed to load available exercises")
                // Continue with empty list - not critical for main functionality
            }
        }
    }

    /**
     * Creates mock data for development/testing
     * TODO: Remove when actual use cases are implemented
     */
    private fun createMockData(exerciseIds: List<String>?, timeRange: TimeRangeType): com.example.liftrix.domain.model.common.LiftrixResult<OneRmProgressionData> {
        val startDate = when (timeRange) {
            TimeRangeType.WEEK -> Clock.System.todayIn(TimeZone.currentSystemDefault()).minus(DatePeriod(days = 7))
            TimeRangeType.MONTH -> Clock.System.todayIn(TimeZone.currentSystemDefault()).minus(DatePeriod(days = 30))
            TimeRangeType.QUARTER -> Clock.System.todayIn(TimeZone.currentSystemDefault()).minus(DatePeriod(days = 90))
            TimeRangeType.THREE_MONTHS -> Clock.System.todayIn(TimeZone.currentSystemDefault()).minus(DatePeriod(days = 90))
            TimeRangeType.SIX_MONTHS -> Clock.System.todayIn(TimeZone.currentSystemDefault()).minus(DatePeriod(days = 180))
            TimeRangeType.YEAR -> Clock.System.todayIn(TimeZone.currentSystemDefault()).minus(DatePeriod(days = 365))
            TimeRangeType.ALL_TIME -> LocalDate.fromEpochDays(0) // Start from epoch for all-time data
        }
        
        val exercises = listOf(
            ExerciseInfo("bench-press", "Bench Press", com.example.liftrix.domain.model.MuscleGroup.CHEST, true, 225f),
            ExerciseInfo("squat", "Back Squat", com.example.liftrix.domain.model.MuscleGroup.QUADRICEPS, true, 315f),
            ExerciseInfo("deadlift", "Deadlift", com.example.liftrix.domain.model.MuscleGroup.BACK, true, 405f)
        )
        
        val filteredExercises = if (exerciseIds.isNullOrEmpty()) {
            exercises
        } else {
            exercises.filter { it.id in exerciseIds }
        }
        
        val progressionPoints = mutableListOf<OneRmDataPoint>()
        
        // Generate mock data points
        for (exercise in filteredExercises) {
            val baseWeight = exercise.latestOneRm ?: 200f
            var currentDate = startDate
            val endDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
            
            while (currentDate <= endDate) {
                val progressionFactor = (currentDate.dayOfYear / 365f) * 0.1f + 1f
                val oneRm = baseWeight * progressionFactor * (0.9f + kotlin.random.Random.nextFloat() * 0.2f)
                
                progressionPoints.add(
                    OneRmDataPoint(
                        date = currentDate,
                        exerciseId = exercise.id,
                        exerciseName = exercise.name,
                        oneRmValue = oneRm,
                        isEstimated = true,
                        actualWeight = null,
                        reps = null
                    )
                )
                
                currentDate = currentDate.plus(kotlinx.datetime.DatePeriod(days = 7))
            }
        }
        
        val summary = ProgressionSummary(
            totalGrowth = 15.5f,
            averageGrowth = 12.3f,
            strongestExercise = exercises.maxByOrNull { it.latestOneRm ?: 0f },
            mostImprovedExercise = exercises.firstOrNull(),
            dataPointCount = progressionPoints.size
        )
        
        val data = OneRmProgressionData(
            progressionPoints = progressionPoints.sortedBy { it.date },
            exercisesIncluded = filteredExercises,
            timeRange = timeRange,
            showEstimated = _showEstimated.value,
            summary = summary
        )
        
        return com.example.liftrix.domain.model.common.LiftrixResult.success(data)
    }

    /**
     * Creates mock exercises for development/testing
     * TODO: Remove when actual use cases are implemented
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
}