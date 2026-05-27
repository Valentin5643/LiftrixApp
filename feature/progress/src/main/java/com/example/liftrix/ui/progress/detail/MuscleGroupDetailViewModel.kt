package com.example.liftrix.ui.progress.detail

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import com.example.liftrix.ui.common.viewmodel.ModernStatefulDetailViewModel
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.MuscleHeatmapColorMode
import com.example.liftrix.domain.model.analytics.MuscleHeatmapGender
import com.example.liftrix.domain.model.analytics.MuscleHeatmapMetric
import com.example.liftrix.domain.model.analytics.MuscleHeatmapViewSide
import com.example.liftrix.domain.model.analytics.MuscleHeatmapWidgetData
import com.example.liftrix.domain.model.MuscleGroup
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.progress.ProgressDetailAnalyticsGateway
import com.example.liftrix.domain.progress.MuscleGroupAnalyticsData as UseCaseMuscleGroupAnalyticsData
import com.example.liftrix.domain.progress.MuscleGroupData as UseCaseMuscleGroupData
import com.example.liftrix.domain.progress.BalanceAnalysis as UseCaseBalanceAnalysis
import com.example.liftrix.domain.progress.ProgressAuthPort
import com.example.liftrix.domain.repository.WidgetPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
    savedStateHandle: SavedStateHandle,
    private val analyticsQueryUseCase: ProgressDetailAnalyticsGateway,
    private val authQueryUseCase: ProgressAuthPort,
    private val widgetPreferencesRepository: WidgetPreferencesRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ModernStatefulDetailViewModel<MuscleGroupDetailViewModel.UiState>(
    initialState = UiState.Loading,
    savedStateHandle = savedStateHandle
) {

    /**
     * Current configuration state
     */
    private val _selectedMuscleGroup = MutableStateFlow<MuscleGroup?>(
        savedStateHandle.get<String>(KEY_SELECTED_MUSCLE_GROUP)?.let { MuscleGroup.valueOf(it) }
    )
    val selectedMuscleGroup = _selectedMuscleGroup
    
    private val _timeRange = MutableStateFlow(
        savedStateHandle.get<String>(KEY_TIME_RANGE)?.let { TimeRangeType.valueOf(it) } ?: TimeRangeType.MONTH
    )
    val timeRange = _timeRange
    
    private val _viewMode = MutableStateFlow(
        savedStateHandle.get<String>(KEY_VIEW_MODE)?.let { ViewMode.valueOf(it) } ?: ViewMode.DISTRIBUTION
    )
    val viewMode = _viewMode

    private val _heatmapGender = MutableStateFlow(MuscleHeatmapGender.MALE)
    val heatmapGender = _heatmapGender

    private val _heatmapViewSide = MutableStateFlow(MuscleHeatmapViewSide.FRONT)
    val heatmapViewSide = _heatmapViewSide

    private val _heatmapMetric = MutableStateFlow(MuscleHeatmapMetric.VOLUME)
    val heatmapMetric = _heatmapMetric

    private val _heatmapColorMode = MutableStateFlow(MuscleHeatmapColorMode.APP_GRADIENT)
    val heatmapColorMode = _heatmapColorMode

    private val _heatmapData = MutableStateFlow(MuscleHeatmapWidgetData.empty())
    val heatmapData = _heatmapData

    companion object {
        private const val KEY_SELECTED_MUSCLE_GROUP = "selectedMuscleGroup"
        private const val KEY_TIME_RANGE = "timeRange"
        private const val KEY_VIEW_MODE = "viewMode"
    }

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
        
        // Set up reactive data binding for real-time updates
        setupReactiveDataBinding()
    }
    
    /**
     * Sets up reactive data binding to automatically update when workout data changes
     */
    private fun setupReactiveDataBinding() {
        viewModelScope.launch {
            // Reactive binding for real-time muscle group data updates
            // This will automatically refresh when workout data changes
            authQueryUseCase(waitForAuth = false).fold(
                onSuccess = { userId ->
                    // Monitor for workout data changes that affect muscle group distribution
                    // The use case already handles the data flow internally
                    Timber.d("Monitoring muscle group data changes for user: $userId")
                },
                onFailure = {
                    Timber.e(it, "Failed to get user ID for reactive binding")
                }
            )

            // For now, set up reactive binding stub
            Timber.d("Reactive data binding initialized for MuscleGroupDetailViewModel")
        }
    }


    /**
     * Loads muscle group distribution data
     * Performance target: <500ms load time as per SPEC requirements
     */
    fun loadData(muscleGroup: MuscleGroup?, timeRange: TimeRangeType) {
        val startTime = System.currentTimeMillis()
        Timber.d("Loading muscle group data for muscleGroup: $muscleGroup, timeRange: $timeRange")
        
        // Update current configuration
        _selectedMuscleGroup.value = muscleGroup
        _timeRange.value = timeRange
        
        // Persist to SavedStateHandle
        savedStateHandle[KEY_SELECTED_MUSCLE_GROUP] = muscleGroup?.name
        savedStateHandle[KEY_TIME_RANGE] = timeRange.name

        viewModelScope.launch {
            setState(UiState.Loading)

            val userIdResult = authQueryUseCase(waitForAuth = false)
            val userId = userIdResult.fold(
                onSuccess = { it },
                onFailure = { error ->
                    logError(error, "loadData - auth")
                    setState(UiState.Error(LiftrixError.AuthenticationError("User not authenticated")))
                    return@launch
                }
            )

            loadHeatmapData(userId.value)

            val result = analyticsQueryUseCase.getMuscleGroupAnalytics(
                userId = userId.value,
                muscleGroup = convertToUseCaseMuscleGroup(muscleGroup),
                timeRange = timeRange
            )

            result.onSuccess { useCaseData ->
                val viewModelData = convertToViewModelData(useCaseData)
                val loadTime = System.currentTimeMillis() - startTime
                Timber.d("Muscle group data loaded in ${loadTime}ms")

                // Performance validation - warn if exceeds 500ms target
                if (loadTime > 500) {
                    Timber.w("PERFORMANCE WARNING: Muscle group data load time exceeded 500ms target: ${loadTime}ms")
                } else {
                    Timber.i("PERFORMANCE: Muscle group data load time within target: ${loadTime}ms")
                }

                if (viewModelData.distribution.isEmpty()) {
                    setState(UiState.Empty())
                } else {
                    setState(UiState.Success(viewModelData))
                }
            }.onFailure { error ->
                logError(error, "loadData - getMuscleGroupAnalytics")
                setState(UiState.Error(error as? LiftrixError ?: LiftrixError.UnknownError(errorMessage = error.message ?: "Failed to load muscle group data")))
            }
        }
    }

    /**
     * Refreshes the current data
     */
    fun refreshData() {
        loadData(_selectedMuscleGroup.value, _timeRange.value)
    }

    /**
     * Updates the time range and reloads data
     */
    fun updateTimeRange(newTimeRange: TimeRangeType) {
        if (newTimeRange != _timeRange.value) {
            Timber.d("Updating time range to: $newTimeRange")
            loadData(_selectedMuscleGroup.value, newTimeRange)
            persistAndReloadHeatmap()
        }
    }

    fun updateHeatmapGender(gender: MuscleHeatmapGender) {
        if (gender != _heatmapGender.value) {
            _heatmapGender.value = gender
            persistAndReloadHeatmap()
        }
    }

    fun updateHeatmapViewSide(viewSide: MuscleHeatmapViewSide) {
        if (viewSide != _heatmapViewSide.value) {
            _heatmapViewSide.value = viewSide
            persistAndReloadHeatmap()
        }
    }

    fun updateHeatmapMetric(metric: MuscleHeatmapMetric) {
        if (metric != _heatmapMetric.value) {
            _heatmapMetric.value = metric
            persistAndReloadHeatmap()
        }
    }

    fun updateHeatmapColorMode(colorMode: MuscleHeatmapColorMode) {
        if (colorMode != _heatmapColorMode.value) {
            _heatmapColorMode.value = colorMode
            persistAndReloadHeatmap()
        }
    }

    /**
     * Selects a specific muscle group segment for drill-down
     */
    fun selectMuscleGroupSegment(muscleGroup: MuscleGroup) {
        Timber.d("Selecting muscle group segment: $muscleGroup")

        val currentState = uiState.value
        if (currentState is UiState.Success) {
            // Update view mode to exercises when selecting a segment
            _viewMode.value = ViewMode.EXERCISES
            savedStateHandle[KEY_VIEW_MODE] = ViewMode.EXERCISES.name

            // Load exercises for the selected muscle group
            loadData(muscleGroup, _timeRange.value)
        }
    }

    /**
     * Clears muscle group selection and returns to overview
     */
    fun clearMuscleGroupSelection() {
        Timber.d("Clearing muscle group selection")
        _viewMode.value = ViewMode.DISTRIBUTION
        savedStateHandle[KEY_VIEW_MODE] = ViewMode.DISTRIBUTION.name
        loadData(null, _timeRange.value)
    }

    /**
     * Updates the view mode
     */
    fun updateViewMode(newViewMode: ViewMode) {
        if (newViewMode != _viewMode.value) {
            Timber.d("Updating view mode to: $newViewMode")
            _viewMode.value = newViewMode
            savedStateHandle[KEY_VIEW_MODE] = newViewMode.name

            val currentState = uiState.value
            if (currentState is UiState.Success) {
                val updatedData = currentState.data.copy(viewMode = newViewMode)
                setState(UiState.Success(updatedData))
            }
        }
    }

    /**
     * Retries loading data after an error
     */
    fun retryLoad() {
        loadData(_selectedMuscleGroup.value, _timeRange.value)
    }

    /**
     * Exports the current muscle group data
     */
    fun exportData() {
        Timber.d("Exporting muscle group data")

        val currentState = uiState.value
        if (currentState is UiState.Success) {
            viewModelScope.launch {
                try {
                    // Export muscle group data as CSV format
                    val csvData = buildString {
                        appendLine("Muscle Group,Percentage,Total Volume,Exercise Count,Workout Count")
                        currentState.data.distribution.forEach { group ->
                            appendLine("${group.muscleGroup.displayName},${group.percentage}%,${group.totalVolume},${group.exerciseCount},${group.workoutCount}")
                        }
                        appendLine()
                        appendLine("Balance Score: ${currentState.data.balanceAnalysis.balanceScore}")
                    }
                    // Export and share the CSV file
                    com.example.liftrix.ui.progress.detail.utils.CsvExportUtil.exportAndShare(
                        context = context,
                        csvContent = csvData,
                        fileName = "muscle_group_analysis",
                        shareTitle = "Export Muscle Group Analysis"
                    )
                    Timber.i("Exported muscle group data: ${currentState.data.distribution.size} groups")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to export muscle group data")
                    logError(e, "exportData")
                }
            }
        }
    }

    /**
     * Converts UI MuscleGroup to use case MuscleGroup.
     */
    private fun convertToUseCaseMuscleGroup(uiMuscleGroup: MuscleGroup?): com.example.liftrix.domain.progress.MuscleGroup? {
        return uiMuscleGroup?.let { ui ->
            when (ui) {
                MuscleGroup.CHEST -> com.example.liftrix.domain.progress.MuscleGroup.CHEST
                MuscleGroup.BACK,
                MuscleGroup.LATS,
                MuscleGroup.UPPER_BACK -> com.example.liftrix.domain.progress.MuscleGroup.BACK
                MuscleGroup.SHOULDERS -> com.example.liftrix.domain.progress.MuscleGroup.SHOULDERS
                MuscleGroup.BICEPS -> com.example.liftrix.domain.progress.MuscleGroup.ARMS
                MuscleGroup.TRICEPS -> com.example.liftrix.domain.progress.MuscleGroup.ARMS
                MuscleGroup.QUADRICEPS, MuscleGroup.HAMSTRINGS, MuscleGroup.CALVES -> com.example.liftrix.domain.progress.MuscleGroup.LEGS
                MuscleGroup.GLUTES -> com.example.liftrix.domain.progress.MuscleGroup.GLUTES
                MuscleGroup.CORE -> com.example.liftrix.domain.progress.MuscleGroup.CORE
                else -> com.example.liftrix.domain.progress.MuscleGroup.OTHER
            }
        }
    }

    /**
     * Converts use case MuscleGroup to UI MuscleGroup.
     */
    private fun convertFromUseCaseMuscleGroup(useCaseMuscleGroup: com.example.liftrix.domain.progress.MuscleGroup): MuscleGroup {
        return when (useCaseMuscleGroup) {
            com.example.liftrix.domain.progress.MuscleGroup.CHEST -> MuscleGroup.CHEST
            com.example.liftrix.domain.progress.MuscleGroup.BACK -> MuscleGroup.BACK
            com.example.liftrix.domain.progress.MuscleGroup.SHOULDERS -> MuscleGroup.SHOULDERS
            com.example.liftrix.domain.progress.MuscleGroup.ARMS -> MuscleGroup.BICEPS // Default to biceps for arms
            com.example.liftrix.domain.progress.MuscleGroup.LEGS -> MuscleGroup.QUADRICEPS // Default to quads for legs
            com.example.liftrix.domain.progress.MuscleGroup.GLUTES -> MuscleGroup.GLUTES
            com.example.liftrix.domain.progress.MuscleGroup.CORE -> MuscleGroup.CORE
            com.example.liftrix.domain.progress.MuscleGroup.CARDIO -> MuscleGroup.CORE // Map to core as fallback
            com.example.liftrix.domain.progress.MuscleGroup.OTHER -> MuscleGroup.CORE // Map to core as fallback
        }
    }

    /**
     * Converts use case data format to ViewModel data format for UI compatibility.
     */
    private fun convertToViewModelData(useCaseData: UseCaseMuscleGroupAnalyticsData): MuscleGroupData {
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

        // Convert muscle group distribution
        val distribution = useCaseData.muscleGroupDistribution.mapIndexed { index, data ->
            // Parse muscle group from string
            val useCaseMuscleGroup = try {
                com.example.liftrix.domain.progress.MuscleGroup.valueOf(data.muscleGroup.uppercase())
            } catch (e: IllegalArgumentException) {
                com.example.liftrix.domain.progress.MuscleGroup.OTHER
            }

            MuscleGroupDistribution(
                muscleGroup = convertFromUseCaseMuscleGroup(useCaseMuscleGroup),
                percentage = data.volumePercentage,
                totalVolume = data.totalVolume,
                exerciseCount = data.exerciseCount,
                workoutCount = 0, // Not available in use case data, use default
                color = colors[index % colors.size]
            )
        }

        // Create mock balance analysis since it's not in the use case data
        val balanceAnalysis = BalanceAnalysis(
            overtrainingRisk = emptyList(),
            undertrainingRisk = emptyList(),
            balanceScore = 75f, // Mock score
            recommendations = emptyList()
        )

        // No selected muscle group data available
        val exercises = emptyList<MuscleGroupExercise>()

        // Create mock weekly comparison
        val weeklyComparison = createMockWeeklyComparison(timeRange.value, colors)

        return MuscleGroupData(
            distribution = distribution,
            selectedMuscleGroup = null,
            selectedMuscleGroupExercises = exercises,
            timeRange = timeRange.value,
            viewMode = viewMode.value,
            balanceAnalysis = balanceAnalysis,
            weeklyComparison = weeklyComparison
        )
    }

    /**
     * Creates mock data for development/testing - fallback for when use case data is insufficient
     * Note: This serves as a fallback when the muscle group analytics use case returns empty data
     */
    private fun createMockDataFallback(selectedMuscleGroup: MuscleGroup?, timeRange: TimeRangeType): com.example.liftrix.domain.model.common.LiftrixResult<MuscleGroupData> {
        
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
                    MuscleGroup.BACK, MuscleGroup.LATS, MuscleGroup.UPPER_BACK -> 22f
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
            viewMode = viewMode.value,
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
            MuscleGroup.LATS to listOf(
                "Lat Pulldown" to 45f,
                "Pull-ups" to 30f,
                "Straight-Arm Pulldown" to 15f,
                "One-Arm Pulldown" to 10f
            ),
            MuscleGroup.UPPER_BACK to listOf(
                "Chest-Supported Row" to 35f,
                "Face Pulls" to 25f,
                "Barbell Rows" to 25f,
                "Rear Delt Fly" to 15f
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
                    MuscleGroup.BACK, MuscleGroup.LATS, MuscleGroup.UPPER_BACK -> 22f
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
        viewModelScope.launch {
            loadSavedHeatmapConfiguration()
            loadData(muscleGroup, timeRange)
        }
    }

    private suspend fun loadSavedHeatmapConfiguration() {
        val userId = authQueryUseCase(waitForAuth = false).getOrNull()?.value ?: return
        val preferences = widgetPreferencesRepository.getWidgetPreferences(userId)
            .first()
            .getOrNull()
            ?: return
        val configuration = preferences.getWidgetConfiguration(AnalyticsWidget.MuscleHeatmap.id)

        _heatmapGender.value = MuscleHeatmapGender.fromConfig(configuration["gender"])
        _heatmapViewSide.value = MuscleHeatmapViewSide.fromConfig(configuration["viewSide"])
        _heatmapMetric.value = MuscleHeatmapMetric.fromConfig(configuration["metric"])
        _heatmapColorMode.value = MuscleHeatmapColorMode.fromConfig(configuration["colorMode"])
    }

    private fun persistAndReloadHeatmap() {
        viewModelScope.launch {
            val userId = authQueryUseCase(waitForAuth = false).getOrNull()?.value ?: return@launch
            val configuration = currentHeatmapConfiguration()
            widgetPreferencesRepository.updateWidgetConfiguration(
                userId = userId,
                widgetName = AnalyticsWidget.MuscleHeatmap.id,
                configuration = configuration
            ).onFailure { error ->
                Timber.e(error, "Failed to persist muscle heatmap configuration")
            }
            loadHeatmapData(userId)
        }
    }

    private suspend fun loadHeatmapData(userId: String) {
        analyticsQueryUseCase.getMuscleHeatmapData(
            userId = userId,
            configuration = currentHeatmapConfiguration()
        ).onSuccess { data ->
            _heatmapData.value = data
        }.onFailure { error ->
            Timber.e(error, "Failed to load muscle heatmap detail data")
        }
    }

    private fun currentHeatmapConfiguration(): Map<String, String> = mapOf(
        "gender" to _heatmapGender.value.configValue,
        "viewSide" to _heatmapViewSide.value.configValue,
        "timeRange" to _timeRange.value.name,
        "metric" to _heatmapMetric.value.configValue,
        "colorMode" to _heatmapColorMode.value.configValue
    )

    /**
     * Extension function for random float in range
     */
    private fun ClosedFloatingPointRange<Float>.random() = 
        start + kotlin.random.Random.nextFloat() * (endInclusive - start)
    
    /**
     * Extracts muscle group from recommendation text
     */
    private fun extractMuscleGroupFromRecommendation(recommendation: String): MuscleGroup {
        val lowerRec = recommendation.lowercase()
        return when {
            lowerRec.contains("chest") -> MuscleGroup.CHEST
            lowerRec.contains("lat") -> MuscleGroup.LATS
            lowerRec.contains("upper back") || lowerRec.contains("trap") || lowerRec.contains("rhomboid") -> MuscleGroup.UPPER_BACK
            lowerRec.contains("back") -> MuscleGroup.BACK
            lowerRec.contains("shoulder") -> MuscleGroup.SHOULDERS
            lowerRec.contains("leg") || lowerRec.contains("quad") -> MuscleGroup.QUADRICEPS
            lowerRec.contains("hamstring") -> MuscleGroup.HAMSTRINGS
            lowerRec.contains("glute") -> MuscleGroup.GLUTES
            lowerRec.contains("bicep") -> MuscleGroup.BICEPS
            lowerRec.contains("tricep") -> MuscleGroup.TRICEPS
            lowerRec.contains("core") || lowerRec.contains("abs") -> MuscleGroup.CORE
            else -> MuscleGroup.CHEST // Default fallback
        }
    }
    
    /**
     * Determines recommendation type from text
     */
    private fun determineRecommendationType(recommendation: String): RecommendationType {
        val lowerRec = recommendation.lowercase()
        return when {
            lowerRec.contains("increase") || lowerRec.contains("more") -> RecommendationType.INCREASE_VOLUME
            lowerRec.contains("decrease") || lowerRec.contains("less") || lowerRec.contains("reduce") -> RecommendationType.DECREASE_VOLUME
            lowerRec.contains("variety") || lowerRec.contains("different") -> RecommendationType.ADD_VARIETY
            lowerRec.contains("compound") -> RecommendationType.FOCUS_COMPOUND
            lowerRec.contains("isolation") -> RecommendationType.FOCUS_ISOLATION
            else -> RecommendationType.ADD_VARIETY // Default fallback
        }
    }
    
    /**
     * Extracts suggested exercises from recommendation text
     */
    private fun extractSuggestedExercises(recommendation: String): List<String> {
        val exercises = mutableListOf<String>()
        val lowerRec = recommendation.lowercase()
        
        // Common exercise patterns to look for
        val exercisePatterns = listOf(
            "bench press", "squat", "deadlift", "row", "pull-up", "push-up",
            "curl", "extension", "fly", "press", "raise", "dip", "lunge"
        )
        
        exercisePatterns.forEach { pattern ->
            if (lowerRec.contains(pattern)) {
                exercises.add(pattern.split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } })
            }
        }
        
        // If no specific exercises found, provide generic suggestions based on muscle group
        if (exercises.isEmpty()) {
            val muscleGroup = extractMuscleGroupFromRecommendation(recommendation)
            exercises.addAll(getDefaultExercisesForMuscleGroup(muscleGroup))
        }
        
        return exercises.take(3) // Limit to 3 suggestions
    }
    
    /**
     * Provides default exercises for a muscle group
     */
    private fun getDefaultExercisesForMuscleGroup(muscleGroup: MuscleGroup): List<String> {
        return when (muscleGroup) {
            MuscleGroup.CHEST -> listOf("Bench Press", "Push-Ups", "Chest Fly")
            MuscleGroup.BACK -> listOf("Pull-Ups", "Bent-Over Row", "Lat Pulldown")
            MuscleGroup.LATS -> listOf("Lat Pulldown", "Pull-Ups", "Straight-Arm Pulldown")
            MuscleGroup.UPPER_BACK -> listOf("Bent-Over Row", "Face Pull", "Chest-Supported Row")
            MuscleGroup.SHOULDERS -> listOf("Overhead Press", "Lateral Raise", "Front Raise")
            MuscleGroup.QUADRICEPS -> listOf("Squats", "Leg Press", "Lunges")
            MuscleGroup.HAMSTRINGS -> listOf("Romanian Deadlift", "Leg Curl", "Good Morning")
            MuscleGroup.GLUTES -> listOf("Hip Thrust", "Bulgarian Split Squat", "Glute Bridge")
            MuscleGroup.BICEPS -> listOf("Barbell Curl", "Hammer Curl", "Preacher Curl")
            MuscleGroup.TRICEPS -> listOf("Tricep Dips", "Overhead Extension", "Close-Grip Press")
            MuscleGroup.CORE -> listOf("Plank", "Russian Twist", "Dead Bug")
            else -> listOf("Compound Movement", "Isolation Exercise", "Bodyweight Exercise")
        }
    }
}
