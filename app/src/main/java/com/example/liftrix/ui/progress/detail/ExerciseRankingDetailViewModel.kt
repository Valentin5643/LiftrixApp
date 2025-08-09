package com.example.liftrix.ui.progress.detail

import androidx.lifecycle.viewModelScope
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.model.analytics.RankingMetric
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.error.LiftrixError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber
import kotlinx.datetime.*

/**
 * ViewModel for exercise ranking detail screen
 * 
 * Manages the state and business logic for the detailed exercise performance rankings,
 * including ranking metrics, performance score calculations, and exercise insights.
 * 
 * Features:
 * - Exercise performance ranking with multiple metrics
 * - Performance score calculation: (Volume Growth % + 1RM Growth %) / 2
 * - Plateau detection across 3-week windows
 * - Customizable ranking limits and sorting
 * - Exercise performance insights and recommendations
 * - Export functionality for ranking data
 */
@HiltViewModel
class ExerciseRankingDetailViewModel @Inject constructor(
    errorHandler: ErrorHandler,
    // TODO: Inject actual use cases when implemented
    // private val calculateExerciseRankingUseCase: CalculateExerciseRankingUseCase,
    // private val getExercisePerformanceUseCase: GetExercisePerformanceUseCase,
    // private val exportRankingDataUseCase: ExportRankingDataUseCase
) : BaseViewModel<ExerciseRankingDetailViewModel.UiState, ExerciseRankingDetailViewModel.Event>(errorHandler) {

    override val _uiState = MutableStateFlow<UiState>(UiState())

    /**
     * Current configuration state
     */
    private val _sortBy = MutableStateFlow(RankingMetric.PERFORMANCE_SCORE)
    val sortBy = _sortBy
    
    private val _limit = MutableStateFlow(20)
    val limit = _limit
    
    private val _showPlateaus = MutableStateFlow(true)
    val showPlateaus = _showPlateaus
    
    private val _isExporting = MutableStateFlow(false)
    val isExporting = _isExporting

    init {
        loadExerciseRankings()
    }

    /**
     * Load exercise rankings based on current configuration
     */
    private fun loadExerciseRankings() {
        viewModelScope.launch {
            try {
                // TODO: Replace with actual use case when implemented
                // val result = calculateExerciseRankingUseCase(
                //     sortBy = _sortBy.value,
                //     limit = _limit.value,
                //     includePlayeaus = _showPlateaus.value
                // )
                
                // Mock data for development (TODO: Replace with actual use case)
                val mockData = createMockRankingData(
                    sortBy = _sortBy.value,
                    limit = _limit.value
                )
                
                _uiState.value = UiState(
                    rankings = mockData,
                    plateauExercises = mockData.filter { it.isPlateau }.map { it.exerciseName },
                    averagePerformanceScore = mockData.map { it.score }.average(),
                    topPerformer = mockData.maxByOrNull { it.score }
                )
                
                Timber.d("Exercise rankings loaded: sortBy=${_sortBy.value}, limit=${_limit.value}")
                
            } catch (error: Exception) {
                val liftrixError = LiftrixError.DataRetrievalError(
                    errorMessage = "Failed to load exercise rankings: ${error.message}",
                    operation = "loadExerciseRankings"
                )
                // Since we're using data class pattern, we can't use UiState.Error
                // Just keep the current state with empty rankings
                _uiState.value = UiState()
                Timber.e(error, "Failed to load exercise rankings")
            }
        }
    }

    /**
     * Update ranking metric
     */
    fun updateSortBy(newSortBy: RankingMetric) {
        if (_sortBy.value != newSortBy) {
            _sortBy.value = newSortBy
            loadExerciseRankings()
            Timber.d("Ranking metric updated to: $newSortBy")
        }
    }

    /**
     * Update ranking limit
     */
    fun updateLimit(newLimit: Int) {
        if (_limit.value != newLimit && newLimit > 0 && newLimit <= 50) {
            _limit.value = newLimit
            loadExerciseRankings()
            Timber.d("Ranking limit updated to: $newLimit")
        }
    }

    /**
     * Toggle plateau detection visibility
     */
    fun togglePlateaus() {
        _showPlateaus.value = !_showPlateaus.value
        loadExerciseRankings()
        Timber.d("Plateau detection toggled: ${_showPlateaus.value}")
    }

    /**
     * Export exercise ranking data
     */
    fun exportData() {
        viewModelScope.launch {
            try {
                _isExporting.value = true
                
                // TODO: Replace with actual export use case
                // val result = exportRankingDataUseCase(
                //     sortBy = _sortBy.value,
                //     limit = _limit.value
                // )
                
                // Mock export success
                kotlinx.coroutines.delay(1000)
                
                _isExporting.value = false
                Timber.d("Exercise ranking data exported successfully")
                
            } catch (error: Exception) {
                _isExporting.value = false
                val liftrixError = LiftrixError.ExportError(
                    errorMessage = "Failed to export exercise ranking data: ${error.message}",
                    operation = "exportExerciseRankings",
                    format = "csv"
                )
                handleError(liftrixError)
                Timber.e(error, "Failed to export exercise ranking data")
            }
        }
    }

    override fun handleEvent(event: Event) {
        when (event) {
            is Event.UpdateSortBy -> updateSortBy(event.sortBy)
            is Event.UpdateLimit -> updateLimit(event.limit)
            is Event.TogglePlateaus -> togglePlateaus()
            is Event.ExportData -> exportData()
            is Event.RefreshData -> loadExerciseRankings()
        }
    }

    /**
     * Handles initial data loading based on route parameters
     */
    fun initializeWithParameters(sortBy: com.example.liftrix.domain.model.analytics.RankingMetric, limit: Int) {
        Timber.d("Initializing with parameters - sortBy: $sortBy, limit: $limit")
        
        // Validate parameters and set defaults if needed
        val validatedSortBy = try {
            sortBy
        } catch (e: Exception) {
            Timber.w("Invalid sortBy parameter: $sortBy, using default PERFORMANCE_SCORE")
            com.example.liftrix.domain.model.analytics.RankingMetric.PERFORMANCE_SCORE
        }
        
        val validatedLimit = when {
            limit <= 0 -> {
                Timber.w("Invalid limit parameter: $limit, using default 20")
                20
            }
            limit > 100 -> {
                Timber.w("Limit parameter too high: $limit, capping at 100")
                100
            }
            else -> limit
        }
        
        _sortBy.value = validatedSortBy
        _limit.value = validatedLimit
        loadExerciseRankings()
    }

    /**
     * Creates mock ranking data for development
     */
    private fun createMockRankingData(
        sortBy: RankingMetric,
        limit: Int
    ): List<ExerciseRankingEntry> {
        val exerciseNames = listOf(
            "Bench Press", "Squat", "Deadlift", "Overhead Press", "Barbell Row",
            "Incline Bench Press", "Front Squat", "Romanian Deadlift", "Pull-ups", "Dips"
        )

        return exerciseNames.take(limit).mapIndexed { index, name ->
            ExerciseRankingEntry(
                exerciseId = "exercise_${index + 1}",
                exerciseName = name,
                rank = index + 1,
                score = 1.0 - (index * 0.08) - (kotlin.random.Random.nextDouble() * 0.1),
                volumeGrowth = 0.15 - (index * 0.01),
                strengthGrowth = 0.12 - (index * 0.008),
                frequency = 3.0 - (index * 0.1),
                consistency = 0.8 - (index * 0.02),
                recentTrend = if (index % 3 == 0) 0.05 else if (index % 3 == 1) 0.0 else -0.03,
                isPlateau = index >= 8
            )
        }
    }

    /**
     * UI state for exercise ranking detail screen
     */
    data class UiState(
        val rankings: List<ExerciseRankingEntry> = emptyList(),
        val plateauExercises: List<String> = emptyList(),
        val averagePerformanceScore: Double = 0.0,
        val topPerformer: ExerciseRankingEntry? = null,
        val lastUpdated: Instant = Clock.System.now()
    )

    /**
     * Events for exercise ranking detail screen
     */
    sealed class Event : ViewModelEvent {
        data class UpdateSortBy(val sortBy: RankingMetric) : Event()
        data class UpdateLimit(val limit: Int) : Event()
        data object TogglePlateaus : Event()
        data object ExportData : Event()
        data object RefreshData : Event()
    }

    /**
     * Mock data generation for development
     */
    private fun generateMockExerciseRankingData(
        sortBy: RankingMetric,
        limit: Int
    ): UiState {
        val exerciseNames = listOf(
            "Bench Press", "Squat", "Deadlift", "Overhead Press", "Barbell Row",
            "Incline Bench Press", "Front Squat", "Romanian Deadlift", "Pull-ups", "Dips",
            "Lateral Raises", "Barbell Curls", "Close-Grip Bench Press", "Bulgarian Split Squats",
            "Face Pulls", "Tricep Extensions", "Leg Press", "Lat Pulldowns", "Chest Flyes", "Leg Curls"
        )

        val rankings = exerciseNames.take(limit).mapIndexed { index, name ->
            val baseScore = when (sortBy) {
                RankingMetric.PERFORMANCE_SCORE -> 100.0 - (index * 3.5) + (kotlin.random.Random.nextDouble() * 10.0)
                RankingMetric.VOLUME_GROWTH -> 50.0 - (index * 1.8) + (kotlin.random.Random.nextDouble() * 15.0)
                RankingMetric.STRENGTH_GROWTH -> 40.0 - (index * 1.5) + (kotlin.random.Random.nextDouble() * 12.0)
                RankingMetric.FREQUENCY -> 4.0 - (index * 0.1) + (kotlin.random.Random.nextDouble() * 1.0)
                RankingMetric.CONSISTENCY -> 95.0 - (index * 2.0) + (kotlin.random.Random.nextDouble() * 8.0)
                RankingMetric.RECENT_TREND -> 30.0 - (index * 1.2) + (kotlin.random.Random.nextDouble() * 10.0)
            }

            ExerciseRankingEntry(
                exerciseId = "exercise_${index + 1}",
                exerciseName = name,
                rank = index + 1,
                score = baseScore,
                volumeGrowth = 20.0 + (kotlin.random.Random.nextDouble() * 30.0),
                strengthGrowth = 15.0 + (kotlin.random.Random.nextDouble() * 25.0),
                frequency = 2.0 + (kotlin.random.Random.nextDouble() * 2.0),
                consistency = 80.0 + (kotlin.random.Random.nextDouble() * 20.0),
                recentTrend = -5.0 + (kotlin.random.Random.nextDouble() * 40.0),
                isPlateau = kotlin.random.Random.nextBoolean() && index < 5
            )
        }.sortedByDescending { 
            when (sortBy) {
                RankingMetric.CONSISTENCY -> if (sortBy.isHigherBetter()) it.score else -it.score
                else -> if (sortBy.isHigherBetter()) it.score else -it.score
            }
        }

        val plateauExercises = rankings.filter { it.isPlateau }.map { it.exerciseName }
        val averageScore = rankings.map { it.score }.average()
        val topPerformer = rankings.firstOrNull()

        return UiState(
            rankings = rankings,
            plateauExercises = plateauExercises,
            averagePerformanceScore = averageScore,
            topPerformer = topPerformer,
            lastUpdated = Clock.System.now()
        )
    }
}

/**
 * Exercise ranking entry with performance metrics
 */
data class ExerciseRankingEntry(
    val exerciseId: String,
    val exerciseName: String,
    val rank: Int,
    val score: Double,
    val volumeGrowth: Double,
    val strengthGrowth: Double,
    val frequency: Double,
    val consistency: Double,
    val recentTrend: Double,
    val isPlateau: Boolean
)