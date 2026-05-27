package com.example.liftrix.domain.progress

import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.MuscleHeatmapWidgetData
import com.example.liftrix.domain.model.analytics.StrengthForecastResult
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import java.io.File

interface ProgressDashboardGateway {
    suspend fun getDashboardConfiguration(userId: String): Flow<LiftrixResult<ProgressDashboardConfiguration>>
    suspend fun getWidgetData(userId: String, widget: AnalyticsWidget): LiftrixResult<ProgressDashboardWidgetData>
    suspend fun refreshWidgetData(userId: String, widget: AnalyticsWidget): LiftrixResult<ProgressDashboardWidgetData>
    suspend fun saveWidgetPreferences(preferences: WidgetPreferences): LiftrixResult<Unit>
}

data class ProgressDashboardConfiguration(
    val preferences: WidgetPreferences,
    val configuration: DashboardConfiguration
)

data class ProgressDashboardWidgetData(
    val widgetType: AnalyticsWidget,
    val data: Map<String, Any>,
    val lastUpdated: Long,
    val isStale: Boolean = false
)

interface ProgressDetailAnalyticsGateway {
    suspend fun getVolumeAnalysis(
        userId: String,
        timeRange: TimeRangeType,
        muscleGroupFilter: String? = null,
        grouping: com.example.liftrix.domain.model.analytics.VolumeGrouping = com.example.liftrix.domain.model.analytics.VolumeGrouping.BY_WEEK
    ): LiftrixResult<VolumeAnalysisData>

    suspend fun getOneRmProgression(
        userId: String,
        exerciseIds: List<String>? = null,
        timeRange: TimeRangeType,
        includeEstimated: Boolean = true
    ): LiftrixResult<OneRmProgressionData>

    suspend fun getStrengthForecast(
        userId: String,
        selectedExerciseId: String? = null,
        historyDays: Int = 30,
        forecastDays: Int = 14
    ): LiftrixResult<StrengthForecastResult>

    suspend fun getWorkoutFrequency(userId: String, timeRange: TimeRangeType): LiftrixResult<WorkoutFrequencyData>

    suspend fun getMuscleGroupAnalytics(
        userId: String,
        timeRange: TimeRangeType,
        muscleGroup: MuscleGroup? = null
    ): LiftrixResult<MuscleGroupAnalyticsData>

    suspend fun getMuscleHeatmapData(
        userId: String,
        configuration: Map<String, String>
    ): LiftrixResult<MuscleHeatmapWidgetData>

    suspend fun getExerciseRanking(
        userId: String,
        timeRange: TimeRangeType,
        metric: com.example.liftrix.domain.model.analytics.RankingMetric = com.example.liftrix.domain.model.analytics.RankingMetric.PERFORMANCE_SCORE
    ): LiftrixResult<ExerciseRankingData>

    suspend fun exportOneRm(request: ExportOneRmDataRequest): LiftrixResult<File>
    suspend fun exportVolume(request: ExportVolumeDataRequest): LiftrixResult<File>
    suspend fun exportFrequency(request: ExportWorkoutFrequencyDataRequest): LiftrixResult<File>
}

data class VolumeAnalysisData(
    val volumeData: List<VolumeAnalysisDataPoint>,
    val totalVolume: Float,
    val volumeGrowth: Float,
    val averageVolume: Float,
    val isEmpty: Boolean = volumeData.isEmpty()
)

data class VolumeAnalysisDataPoint(
    val date: String?,
    val volume: Double,
    val sets: Int,
    val exercises: Int,
    val label: String
)

data class OneRmProgressionData(
    val exerciseProgressions: List<ExerciseProgression>
)

data class ExerciseProgression(
    val exerciseId: String,
    val exerciseName: String,
    val progressionPoints: List<OneRmDataPoint>
)

data class OneRmDataPoint(
    val date: LocalDate,
    val exerciseId: String,
    val exerciseName: String,
    val actualOneRm: Float?,
    val estimatedOneRm: Float?,
    val weight: Float,
    val reps: Int,
    val isEstimated: Boolean,
    val oneRmValue: Float? = actualOneRm ?: estimatedOneRm
)

data class WorkoutFrequencyData(
    val totalWorkouts: Int,
    val consistencyScore: Float,
    val frequencyPoints: List<FrequencyPoint>,
    val totalWorkoutDays: Int,
    val dailyAverage: Float,
    val currentStreak: Int,
    val longestStreak: Int,
    val weeklyDistribution: Map<String, Int>
)

data class FrequencyPoint(
    val date: LocalDate,
    val workoutCount: Int,
    val dayOfWeek: String
)

enum class MuscleGroup {
    CHEST, BACK, SHOULDERS, ARMS, LEGS, GLUTES, CORE, CARDIO, OTHER
}

data class MuscleGroupAnalyticsData(
    val muscleGroupDistribution: List<MuscleGroupDistribution>
)

data class MuscleGroupDistribution(
    val muscleGroup: String,
    val volumePercentage: Float,
    val totalVolume: Float,
    val exerciseCount: Int
)

data class MuscleGroupData(
    val muscleGroup: MuscleGroup,
    val totalVolume: Float,
    val volumePercentage: Float,
    val exerciseCount: Int,
    val topExercises: List<String>,
    val weekOverWeekChange: Float?,
    val balanceScore: Float
)

data class BalanceAnalysis(
    val overtrainedGroups: List<MuscleGroup>,
    val undertrainedGroups: List<MuscleGroup>,
    val balanceScore: Float,
    val recommendations: List<String>
)

data class ExerciseRankingData(
    val rankedExercises: List<RankedExercise>,
    val topPerformer: RankedExercise? = null,
    val mostImproved: RankedExercise? = null,
    val needsAttention: List<RankedExercise> = emptyList(),
    val overallScore: Double = 0.0,
    val timeRange: TimeRangeType = TimeRangeType.MONTH,
    val isEmpty: Boolean = rankedExercises.isEmpty()
)

data class RankedExercise(
    val rank: Int,
    val exerciseId: String,
    val exerciseName: String,
    val performanceScore: Double,
    val totalVolume: Double,
    val workoutDays: Int,
    val totalSets: Int,
    val maxEstimated1RM: Double,
    val plateauStatus: PlateauStatus,
    val trend: PerformanceTrend,
    val recommendations: List<String> = emptyList(),
    val muscleGroup: String = ""
)

enum class PlateauStatus {
    PROGRESSING,
    STABLE,
    STAGNANT,
    DECLINING,
    INSUFFICIENT_DATA
}

enum class PerformanceTrend {
    IMPROVING,
    STABLE,
    DECLINING
}

enum class ProgressExportFormat {
    CSV,
    PDF,
    JSON
}

data class ExportOneRmDataRequest(
    val progressionPoints: List<OneRmDataPoint>,
    val exerciseNames: Map<String, String>,
    val timeRange: TimeRangeType,
    val showEstimated: Boolean,
    val format: ProgressExportFormat = ProgressExportFormat.CSV
)

data class ExportVolumeDataRequest(
    val volumePoints: List<ExportVolumeDataPoint>,
    val timeRange: TimeRangeType,
    val muscleGroupFilter: String? = null,
    val includeBreakdown: Boolean = true,
    val format: ProgressExportFormat = ProgressExportFormat.CSV
)

data class ExportWorkoutFrequencyDataRequest(
    val frequencyPoints: List<WorkoutFrequencyDataPoint>,
    val timeRange: TimeRangeType,
    val includeHeatmap: Boolean = true,
    val includeTrends: Boolean = true,
    val format: ProgressExportFormat = ProgressExportFormat.CSV
)

data class ExportVolumeDataPoint(
    val date: LocalDate,
    val exerciseId: String,
    val exerciseName: String,
    val muscleGroup: String,
    val sets: Int,
    val reps: Int,
    val weight: Float,
    val totalVolume: Float
)

data class WorkoutFrequencyDataPoint(
    val date: LocalDate,
    val dayOfWeek: String,
    val workoutCount: Int,
    val durationMinutes: Int,
    val consistencyScore: Float
)
