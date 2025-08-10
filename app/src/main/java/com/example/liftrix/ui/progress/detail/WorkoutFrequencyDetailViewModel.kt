package com.example.liftrix.ui.progress.detail

import androidx.lifecycle.viewModelScope
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.analytics.VolumeDataPoint
import com.example.liftrix.domain.model.error.LiftrixError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber
import kotlinx.datetime.*
import kotlin.time.Duration.Companion.days
import kotlin.random.Random

/**
 * ViewModel for workout frequency detail screen
 * 
 * Manages the state and business logic for the detailed workout frequency analysis view,
 * including frequency patterns, consistency scoring, streak tracking, and rest day analysis.
 * 
 * Features:
 * - Workout frequency tracking and trends
 * - Time range selection (1M, 3M, 6M, 1Y, All)  
 * - Consistency scoring and streak calculations
 * - Weekly pattern analysis
 * - Rest day optimization recommendations
 * - Export functionality for frequency data
 */
@HiltViewModel
class WorkoutFrequencyDetailViewModel @Inject constructor(
    errorHandler: ErrorHandler,
    // TODO: Inject actual use cases when implemented
    // private val getWorkoutFrequencyUseCase: GetWorkoutFrequencyUseCase,
    // private val calculateConsistencyScoreUseCase: CalculateConsistencyScoreUseCase,
    // private val getFrequencyPatternsUseCase: GetFrequencyPatternsUseCase
) : BaseViewModel<WorkoutFrequencyDetailViewModel.UiState, WorkoutFrequencyDetailViewModel.Event>(errorHandler) {

    override val _uiState = MutableStateFlow<UiState>(UiState.Loading)

    /**
     * Current configuration state
     */
    private val _timeRange = MutableStateFlow(TimeRangeType.MONTH)
    val timeRange: StateFlow<TimeRangeType> = _timeRange
    
    private val _consistencyScore = MutableStateFlow(0f)
    val consistencyScore: StateFlow<Float> = _consistencyScore
    
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting

    init {
        loadWorkoutFrequencyData()
    }

    /**
     * Load workout frequency data based on current configuration
     * Performance target: <500ms load time as per SPEC requirements
     */
    private fun loadWorkoutFrequencyData() {
        viewModelScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                _uiState.value = UiState.Loading
                
                // TODO: Replace with actual use case when implemented
                // val result = getWorkoutFrequencyUseCase(
                //     timeRange = _timeRange.value
                // )
                
                // Mock data for development - simulate realistic load time
                kotlinx.coroutines.delay(250)
                val mockData = generateMockWorkoutFrequencyData(_timeRange.value)
                
                val loadTime = System.currentTimeMillis() - startTime
                Timber.d("Workout frequency data loaded in ${loadTime}ms")
                
                // Performance validation - warn if exceeds 500ms target
                if (loadTime > 500) {
                    Timber.w("PERFORMANCE WARNING: Workout frequency load time exceeded 500ms target: ${loadTime}ms")
                } else {
                    Timber.i("PERFORMANCE: Workout frequency load time within target: ${loadTime}ms")
                }
                
                _uiState.value = UiState.Success(mockData)
                
                // Calculate consistency score
                _consistencyScore.value = calculateConsistencyScore(mockData)
                
                Timber.d("Workout frequency data loaded: timeRange=${_timeRange.value}")
                
            } catch (error: Exception) {
                val liftrixError = LiftrixError.DataRetrievalError(
                    errorMessage = "Failed to load workout frequency data"
                )
                _uiState.value = UiState.Error(liftrixError)
                Timber.e(error, "Failed to load workout frequency data")
            }
        }
    }

    /**
     * Update time range for analysis
     */
    fun updateTimeRange(newTimeRange: TimeRangeType) {
        if (_timeRange.value != newTimeRange) {
            _timeRange.value = newTimeRange
            loadWorkoutFrequencyData()
            Timber.d("Time range updated to: $newTimeRange")
        }
    }

    /**
     * Export frequency analysis data
     */
    fun exportData() {
        viewModelScope.launch {
            try {
                _isExporting.value = true
                
                // TODO: Replace with actual export use case
                // val result = exportFrequencyDataUseCase(
                //     timeRange = _timeRange.value
                // )
                
                // Mock export success
                kotlinx.coroutines.delay(1000)
                
                Timber.d("Frequency data exported successfully")
                
            } catch (error: Exception) {
                Timber.e(error, "Failed to export frequency data")
            } finally {
                _isExporting.value = false
            }
        }
    }

    override fun handleEvent(event: Event) {
        when (event) {
            is Event.UpdateTimeRange -> updateTimeRange(event.timeRange)
            is Event.ExportData -> exportData()
            is Event.RefreshData -> loadWorkoutFrequencyData()
        }
    }

    /**
     * Calculate consistency score based on workout frequency data
     */
    private fun calculateConsistencyScore(data: WorkoutFrequencyData): Float {
        if (data.totalWorkouts == 0) return 0f
        
        // Basic consistency calculation based on regular workout patterns
        val expectedWorkoutsPerWeek = 3.5f
        val actualWorkoutsPerWeek = data.averageWorkoutsPerWeek
        val streakBonus = (data.currentStreak / 30f).coerceAtMost(0.2f) // Max 20% bonus for streaks
        
        val baseScore = (actualWorkoutsPerWeek / expectedWorkoutsPerWeek).coerceAtMost(1f)
        return ((baseScore + streakBonus) * 100f).coerceAtMost(100f)
    }

    /**
     * UI state for workout frequency detail screen
     */
    sealed class UiState {
        object Loading : UiState()
        data class Success(val data: WorkoutFrequencyData) : UiState()
        data class Error(val error: LiftrixError) : UiState()
        data class Empty(val message: String = "No workout data available for the selected time range") : UiState()
    }
    
    /**
     * Data class for workout frequency analysis data
     */
    data class WorkoutFrequencyData(
        val frequencyData: List<VolumeDataPoint> = emptyList(), // Reusing VolumeDataPoint for frequency charts
        val totalWorkouts: Int = 0,
        val averageWorkoutsPerWeek: Float = 0f,
        val currentStreak: Int = 0,
        val bestStreak: Int = 0,
        val daysSinceLastWorkout: Int = 0,
        val averageRestDays: Float = 0f,
        val totalRestDays: Int = 0,
        val optimalRestDays: Int = 0,
        val weeklyPattern: List<Pair<String, Int>> = emptyList(), // Day of week to workout count
        val morningWorkouts: Int = 0, // Percentage
        val afternoonWorkouts: Int = 0, // Percentage
        val eveningWorkouts: Int = 0, // Percentage
        val restDayRecommendation: String = "",
        val lastUpdated: Instant = Clock.System.now()
    )

    /**
     * Events for workout frequency detail screen
     */
    sealed class Event : ViewModelEvent {
        data class UpdateTimeRange(val timeRange: TimeRangeType) : Event()
        data object ExportData : Event()
        data object RefreshData : Event()
    }

    /**
     * Mock data generation for development
     */
    private fun generateMockWorkoutFrequencyData(timeRange: TimeRangeType): WorkoutFrequencyData {
        val now = Clock.System.now()
        val daysBack = when (timeRange) {
            TimeRangeType.MONTH -> 30
            TimeRangeType.SIX_MONTHS -> 180
            TimeRangeType.ALL_TIME -> 365 * 2 // 2 years for all-time data
        }

        // Generate frequency data points (workout counts per day/week)
        val frequencyData = (0 until daysBack step 7).map { daysAgo ->
            val date = now.minus(daysAgo.days).toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
            val workoutCount = when {
                Random.nextFloat() < 0.7f -> Random.nextInt(1, 4) // 70% chance of 1-3 workouts
                Random.nextFloat() < 0.2f -> Random.nextInt(4, 6) // 20% chance of 4-5 workouts
                else -> 0 // 10% chance of no workouts
            }.toDouble()
            
            VolumeDataPoint.fromKgDouble(
                date = date,
                volumeKg = workoutCount, // Using volume field to store workout count
                workoutCount = workoutCount.toInt(),
                label = "Workouts"
            )
        }.reversed()

        val totalWorkouts = frequencyData.sumOf { it.workoutCount }
        val averageWorkoutsPerWeek = if (daysBack >= 7) totalWorkouts.toFloat() / (daysBack / 7f) else 0f
        
        // Generate streaks and patterns
        val currentStreak = Random.nextInt(0, 30)
        val bestStreak = currentStreak + Random.nextInt(5, 20)
        val daysSinceLastWorkout = Random.nextInt(0, 5)
        val averageRestDays = 2.5f + Random.nextFloat()
        val totalRestDays = daysBack - totalWorkouts
        val optimalRestDays = (totalWorkouts * 0.4).toInt() // Recommended 40% rest days
        
        // Weekly pattern (day of week preferences)
        val weeklyPattern = listOf(
            "Monday" to (totalWorkouts * 0.18).toInt(),
            "Tuesday" to (totalWorkouts * 0.15).toInt(),
            "Wednesday" to (totalWorkouts * 0.16).toInt(),
            "Thursday" to (totalWorkouts * 0.14).toInt(),
            "Friday" to (totalWorkouts * 0.17).toInt(),
            "Saturday" to (totalWorkouts * 0.12).toInt(),
            "Sunday" to (totalWorkouts * 0.08).toInt()
        )
        
        // Time of day preferences
        val morningWorkouts = Random.nextInt(20, 40)
        val afternoonWorkouts = Random.nextInt(25, 45)
        val eveningWorkouts = 100 - morningWorkouts - afternoonWorkouts
        
        // Rest day recommendation
        val restDayRecommendation = when {
            daysSinceLastWorkout == 0 -> "Great job staying consistent!"
            daysSinceLastWorkout <= 2 -> "Perfect rest period. Consider working out soon."
            daysSinceLastWorkout <= 5 -> "It's been a few days. Time to get back to training!"
            else -> "Extended break detected. Start with a light workout to get back on track."
        }

        return WorkoutFrequencyData(
            frequencyData = frequencyData,
            totalWorkouts = totalWorkouts,
            averageWorkoutsPerWeek = averageWorkoutsPerWeek,
            currentStreak = currentStreak,
            bestStreak = bestStreak,
            daysSinceLastWorkout = daysSinceLastWorkout,
            averageRestDays = averageRestDays,
            totalRestDays = totalRestDays,
            optimalRestDays = optimalRestDays,
            weeklyPattern = weeklyPattern,
            morningWorkouts = morningWorkouts,
            afternoonWorkouts = afternoonWorkouts,
            eveningWorkouts = eveningWorkouts,
            restDayRecommendation = restDayRecommendation,
            lastUpdated = now
        )
    }
}