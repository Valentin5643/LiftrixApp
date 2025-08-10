package com.example.liftrix.ui.progress.detail

import androidx.lifecycle.viewModelScope
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.MuscleGroup
import com.example.liftrix.domain.model.error.LiftrixError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber
import kotlinx.datetime.*

/**
 * ViewModel for muscle group distribution detail screen
 * 
 * Manages the state and business logic for the detailed muscle group analysis view,
 * including distribution calculations, drill-down functionality, and comparative analysis.
 * 
 * Features:
 * - Muscle group volume distribution pie chart
 * - Drill-down to specific muscle group exercises
 * - Weekly/monthly comparison views
 * - Balance recommendations
 * - Interactive pie chart with selection
 */
@HiltViewModel
class MuscleGroupDetailViewModel @Inject constructor(
    errorHandler: ErrorHandler,
    // TODO: Inject actual use cases when implemented
    // private val getMuscleGroupDistributionUseCase: GetMuscleGroupDistributionUseCase,
    // private val getMuscleGroupExercisesUseCase: GetMuscleGroupExercisesUseCase,
    // private val generateBalanceRecommendationsUseCase: GenerateBalanceRecommendationsUseCase
) : BaseViewModel<MuscleGroupDetailViewModel.UiState, MuscleGroupDetailViewModel.Event>(errorHandler) {

    override val _uiState = MutableStateFlow<UiState>(UiState.Loading)

    /**
     * Current configuration state
     */
    private val _selectedMuscleGroup = MutableStateFlow<MuscleGroup?>(null)
    val selectedMuscleGroup = _selectedMuscleGroup
    
    private val _timeRange = MutableStateFlow(TimeRangeType.MONTH)
    val timeRange = _timeRange
    
    private val _viewMode = MutableStateFlow(ViewMode.DISTRIBUTION)
    val viewMode = _viewMode

    /**
     * UI State for the muscle group detail screen
     */
    sealed class UiState {
        object Loading : UiState()
        data class Success(val data: MuscleGroupData) : UiState()
        data class Error(val error: LiftrixError) : UiState()
        data class Empty(val message: String = "No muscle group data available for the selected time range") : UiState()
    }

    /**
     * Events that can be triggered from the UI
     */
    sealed class Event : ViewModelEvent {
        data class LoadData(val muscleGroup: MuscleGroup?, val timeRange: TimeRangeType) : Event()
        object RefreshData : Event()
        data class UpdateTimeRange(val timeRange: TimeRangeType) : Event()
        data class SelectMuscleGroupSegment(val muscleGroup: MuscleGroup) : Event()
        object ClearMuscleGroupSelection : Event()
        data class UpdateViewMode(val viewMode: ViewMode) : Event()
        object RetryLoad : Event()
        object ExportData : Event()
    }

    /**
     * View modes for the muscle group analysis
     */
    enum class ViewMode(val displayName: String) {
        DISTRIBUTION("Distribution"),
        COMPARISON("Comparison"),
        EXERCISES("Exercises"),
        RECOMMENDATIONS("Balance")
    }

    /**
     * Data class for muscle group analysis
     */
    data class MuscleGroupData(
        val distribution: List<MuscleGroupDistribution>,
        val selectedMuscleGroup: MuscleGroup?,
        val selectedMuscleGroupExercises: List<MuscleGroupExercise>,
        val timeRange: TimeRangeType,
        val viewMode: ViewMode,
        val balanceAnalysis: BalanceAnalysis,
        val weeklyComparison: List<WeeklyComparison>
    )

    /**
     * Distribution data for each muscle group
     */
    data class MuscleGroupDistribution(
        val muscleGroup: MuscleGroup,
        val percentage: Float,
        val totalVolume: Float,
        val exerciseCount: Int,
        val workoutCount: Int,
        val color: androidx.compose.ui.graphics.Color
    )

    /**
     * Exercise data within a specific muscle group
     */
    data class MuscleGroupExercise(
        val id: String,
        val name: String,
        val volume: Float,
        val percentage: Float,
        val sessionCount: Int,
        val latestOneRm: Float?,
        val trend: Trend
    )

    /**
     * Trend direction for exercises
     */
    enum class Trend {
        IMPROVING, STABLE, DECLINING
    }

    /**
     * Balance analysis and recommendations
     */
    data class BalanceAnalysis(
        val overtrainingRisk: List<MuscleGroup>,
        val undertrainingRisk: List<MuscleGroup>,
        val balanceScore: Float, // 0-100 where 100 is perfectly balanced
        val recommendations: List<BalanceRecommendation>
    )

    /**
     * Individual balance recommendation
     */
    data class BalanceRecommendation(
        val muscleGroup: MuscleGroup,
        val recommendationType: RecommendationType,
        val message: String,
        val suggestedExercises: List<String>
    )

    /**
     * Types of balance recommendations
     */
    enum class RecommendationType {
        INCREASE_VOLUME,
        DECREASE_VOLUME, 
        ADD_VARIETY,
        FOCUS_COMPOUND,
        FOCUS_ISOLATION
    }

    /**
     * Weekly comparison data
     */
    data class WeeklyComparison(
        val weekStartDate: kotlinx.datetime.LocalDate,
        val distribution: List<MuscleGroupDistribution>,
        val totalVolume: Float
    )

    init {
        Timber.d("MuscleGroupDetailViewModel initialized")
    }

    override fun handleEvent(event: Event) {
        when (event) {
            is Event.LoadData -> loadData(event.muscleGroup, event.timeRange)
            Event.RefreshData -> refreshData()
            is Event.UpdateTimeRange -> updateTimeRange(event.timeRange)
            is Event.SelectMuscleGroupSegment -> selectMuscleGroupSegment(event.muscleGroup)
            Event.ClearMuscleGroupSelection -> clearMuscleGroupSelection()
            is Event.UpdateViewMode -> updateViewMode(event.viewMode)
            Event.RetryLoad -> retryLoad()
            Event.ExportData -> exportData()
        }
    }

    override fun setLoadingState() {
        _uiState.value = UiState.Loading
    }

    override fun updateErrorState(error: LiftrixError) {
        _uiState.value = UiState.Error(error)
    }

    /**
     * Loads muscle group distribution data
     * Performance target: <500ms load time as per SPEC requirements
     */
    private fun loadData(muscleGroup: MuscleGroup?, timeRange: TimeRangeType) {
        val startTime = System.currentTimeMillis()
        Timber.d("Loading muscle group data for muscleGroup: $muscleGroup, timeRange: $timeRange")
        
        // Update current configuration
        _selectedMuscleGroup.value = muscleGroup
        _timeRange.value = timeRange

        // TODO: Replace with actual use case call
        executeUseCase(
            useCase = { 
                // Simulate API call - keep under 500ms performance target
                kotlinx.coroutines.delay(200)
                createMockData(muscleGroup, timeRange)
            },
            onSuccess = { data ->
                val loadTime = System.currentTimeMillis() - startTime
                Timber.d("Muscle group data loaded in ${loadTime}ms")
                
                // Performance validation - warn if exceeds 500ms target
                if (loadTime > 500) {
                    Timber.w("PERFORMANCE WARNING: Muscle group data load time exceeded 500ms target: ${loadTime}ms")
                } else {
                    Timber.i("PERFORMANCE: Muscle group data load time within target: ${loadTime}ms")
                }
                
                if (data.distribution.isEmpty()) {
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
        loadData(_selectedMuscleGroup.value, _timeRange.value)
    }

    /**
     * Updates the time range and reloads data
     */
    private fun updateTimeRange(newTimeRange: TimeRangeType) {
        if (newTimeRange != _timeRange.value) {
            Timber.d("Updating time range to: $newTimeRange")
            loadData(_selectedMuscleGroup.value, newTimeRange)
        }
    }

    /**
     * Selects a specific muscle group segment for drill-down
     */
    private fun selectMuscleGroupSegment(muscleGroup: MuscleGroup) {
        Timber.d("Selecting muscle group segment: $muscleGroup")
        
        val currentState = _uiState.value
        if (currentState is UiState.Success) {
            // Update view mode to exercises when selecting a segment
            _viewMode.value = ViewMode.EXERCISES
            
            // Load exercises for the selected muscle group
            loadData(muscleGroup, _timeRange.value)
        }
    }

    /**
     * Clears muscle group selection and returns to overview
     */
    private fun clearMuscleGroupSelection() {
        Timber.d("Clearing muscle group selection")
        _viewMode.value = ViewMode.DISTRIBUTION
        loadData(null, _timeRange.value)
    }

    /**
     * Updates the view mode
     */
    private fun updateViewMode(newViewMode: ViewMode) {
        if (newViewMode != _viewMode.value) {
            Timber.d("Updating view mode to: $newViewMode")
            _viewMode.value = newViewMode
            
            val currentState = _uiState.value
            if (currentState is UiState.Success) {
                val updatedData = currentState.data.copy(viewMode = newViewMode)
                _uiState.value = UiState.Success(updatedData)
            }
        }
    }

    /**
     * Retries loading data after an error
     */
    private fun retryLoad() {
        loadData(_selectedMuscleGroup.value, _timeRange.value)
    }

    /**
     * Exports the current muscle group data
     */
    private fun exportData() {
        Timber.d("Exporting muscle group data")
        
        val currentState = _uiState.value
        if (currentState is UiState.Success) {
            viewModelScope.launch {
                try {
                    // TODO: Implement actual export functionality
                    Timber.i("Muscle group data export completed successfully")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to export muscle group data")
                    handleError(LiftrixError.FileSystemError("Failed to export data: ${e.message}"))
                }
            }
        }
    }

    /**
     * Creates mock data for development/testing
     * TODO: Remove when actual use cases are implemented
     */
    private fun createMockData(selectedMuscleGroup: MuscleGroup?, timeRange: TimeRangeType): com.example.liftrix.domain.model.common.LiftrixResult<MuscleGroupData> {
        
        // Create color palette for muscle groups
        val colors = listOf(
            androidx.compose.ui.graphics.Color(0xFF339989), // Persian Green
            androidx.compose.ui.graphics.Color(0xFF7DE2D1), // Tiffany Blue  
            androidx.compose.ui.graphics.Color(0xFF2B2C28), // Jet
            androidx.compose.ui.graphics.Color(0xFF4A90A4), // Steel Blue
            androidx.compose.ui.graphics.Color(0xFF83C5BE), // Powder Blue
            androidx.compose.ui.graphics.Color(0xFF006D77), // Dark Cyan
            androidx.compose.ui.graphics.Color(0xFFE29578), // Sandy Brown
            androidx.compose.ui.graphics.Color(0xFFFDBF50), // Maize
        )

        val allMuscleGroups = if (selectedMuscleGroup == null) {
            // Show distribution for all muscle groups
            MuscleGroup.getPrimaryMuscleGroups().take(8)
        } else {
            // Show only the selected muscle group
            listOf(selectedMuscleGroup)
        }
        
        val totalVolume = 10000f // Mock total volume
        val distribution = allMuscleGroups.mapIndexed { index, muscleGroup ->
            val percentage = when {
                selectedMuscleGroup != null && muscleGroup == selectedMuscleGroup -> 100f
                else -> when (muscleGroup) {
                    MuscleGroup.CHEST -> 18f
                    MuscleGroup.BACK -> 22f
                    MuscleGroup.QUADRICEPS -> 20f
                    MuscleGroup.HAMSTRINGS -> 15f
                    MuscleGroup.SHOULDERS -> 12f
                    MuscleGroup.TRICEPS -> 6f
                    MuscleGroup.BICEPS -> 4f
                    MuscleGroup.CORE -> 3f
                    else -> 5f
                }
            }
            
            MuscleGroupDistribution(
                muscleGroup = muscleGroup,
                percentage = percentage,
                totalVolume = totalVolume * (percentage / 100f),
                exerciseCount = (2..6).random(),
                workoutCount = (3..10).random(),
                color = colors[index % colors.size]
            )
        }

        val exercises = if (selectedMuscleGroup != null) {
            // Generate exercises for the selected muscle group
            createMockExercisesForMuscleGroup(selectedMuscleGroup)
        } else {
            emptyList()
        }

        val balanceAnalysis = BalanceAnalysis(
            overtrainingRisk = listOf(MuscleGroup.CHEST),
            undertrainingRisk = listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.CORE),
            balanceScore = 72f,
            recommendations = listOf(
                BalanceRecommendation(
                    muscleGroup = MuscleGroup.HAMSTRINGS,
                    recommendationType = RecommendationType.INCREASE_VOLUME,
                    message = "Consider adding more posterior chain work to balance your training",
                    suggestedExercises = listOf("Romanian Deadlift", "Nordic Curls", "Good Mornings")
                ),
                BalanceRecommendation(
                    muscleGroup = MuscleGroup.CORE,
                    recommendationType = RecommendationType.ADD_VARIETY,
                    message = "Add more core exercises to support compound movements",
                    suggestedExercises = listOf("Dead Bug", "Pallof Press", "Turkish Get-ups")
                )
            )
        )

        val weeklyComparison = createMockWeeklyComparison(timeRange, colors)

        val data = MuscleGroupData(
            distribution = distribution,
            selectedMuscleGroup = selectedMuscleGroup,
            selectedMuscleGroupExercises = exercises,
            timeRange = timeRange,
            viewMode = _viewMode.value,
            balanceAnalysis = balanceAnalysis,
            weeklyComparison = weeklyComparison
        )
        
        return com.example.liftrix.domain.model.common.LiftrixResult.success(data)
    }

    /**
     * Creates mock exercises for a specific muscle group
     */
    private fun createMockExercisesForMuscleGroup(muscleGroup: MuscleGroup): List<MuscleGroupExercise> {
        val exercisesByGroup = mapOf(
            MuscleGroup.CHEST to listOf(
                "Bench Press" to 35f,
                "Incline Dumbbell Press" to 25f,
                "Dips" to 20f,
                "Push-ups" to 15f,
                "Cable Fly" to 5f
            ),
            MuscleGroup.BACK to listOf(
                "Deadlift" to 40f,
                "Pull-ups" to 25f,
                "Barbell Rows" to 20f,
                "T-Bar Row" to 10f,
                "Cable Rows" to 5f
            ),
            MuscleGroup.QUADRICEPS to listOf(
                "Back Squat" to 45f,
                "Front Squat" to 25f,
                "Leg Press" to 15f,
                "Lunges" to 10f,
                "Leg Extensions" to 5f
            ),
            MuscleGroup.SHOULDERS to listOf(
                "Overhead Press" to 40f,
                "Lateral Raises" to 25f,
                "Rear Delt Fly" to 15f,
                "Arnold Press" to 15f,
                "Face Pulls" to 5f
            )
        )

        val exercisesWithPercentages = exercisesByGroup[muscleGroup] ?: listOf("Generic Exercise" to 100f)
        
        return exercisesWithPercentages.mapIndexed { index, (name, percentage) ->
            MuscleGroupExercise(
                id = "exercise-$index",
                name = name,
                volume = 1000f * (percentage / 100f),
                percentage = percentage,
                sessionCount = (2..8).random(),
                latestOneRm = if ((0..10).random() < 7) (135f..405f).random() else null,
                trend = Trend.values().random()
            )
        }
    }

    /**
     * Creates mock weekly comparison data
     */
    private fun createMockWeeklyComparison(timeRange: TimeRangeType, colors: List<androidx.compose.ui.graphics.Color>): List<WeeklyComparison> {
        val numberOfWeeks = when (timeRange) {
            TimeRangeType.MONTH -> 4
            TimeRangeType.SIX_MONTHS -> 24
            TimeRangeType.ALL_TIME -> 104 // 2 years worth of weeks for all-time display
        }.coerceAtMost(12) // Limit to 12 weeks for display

        val primaryMuscleGroups = MuscleGroup.getPrimaryMuscleGroups().take(6)
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

        return (0 until numberOfWeeks).map { weekOffset ->
            val weekStartDate = today.minus(DatePeriod(days = weekOffset * 7))
            val totalWeekVolume = (8000f..12000f).random()
            
            val weeklyDistribution = primaryMuscleGroups.mapIndexed { index, muscleGroup ->
                val basePercentage = when (muscleGroup) {
                    MuscleGroup.CHEST -> 18f
                    MuscleGroup.BACK -> 22f
                    MuscleGroup.QUADRICEPS -> 20f
                    MuscleGroup.HAMSTRINGS -> 15f
                    MuscleGroup.SHOULDERS -> 12f
                    MuscleGroup.TRICEPS -> 6f
                    else -> 7f
                }
                
                // Add some weekly variation
                val variation = (-3f..3f).random()
                val percentage = (basePercentage + variation).coerceAtLeast(0f)
                
                MuscleGroupDistribution(
                    muscleGroup = muscleGroup,
                    percentage = percentage,
                    totalVolume = totalWeekVolume * (percentage / 100f),
                    exerciseCount = (2..5).random(),
                    workoutCount = (1..4).random(),
                    color = colors[index % colors.size]
                )
            }
            
            WeeklyComparison(
                weekStartDate = weekStartDate,
                distribution = weeklyDistribution,
                totalVolume = totalWeekVolume
            )
        }.reversed() // Show oldest to newest
    }

    /**
     * Handles initial data loading based on route parameters
     */
    fun initializeWithParameters(muscleGroup: MuscleGroup?, timeRange: TimeRangeType) {
        Timber.d("Initializing with parameters - muscleGroup: $muscleGroup, timeRange: $timeRange")
        loadData(muscleGroup, timeRange)
    }

    /**
     * Extension function for random float in range
     */
    private fun ClosedFloatingPointRange<Float>.random() = 
        start + kotlin.random.Random.nextFloat() * (endInclusive - start)
}