package com.example.liftrix.di.feature.progress

import com.example.liftrix.domain.model.AnomalyDetectionSettings
import com.example.liftrix.domain.model.AnomalyValue
import com.example.liftrix.domain.model.ExerciseHistory
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.UserAnomalyAction
import com.example.liftrix.domain.model.WorkoutAnomaly
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.DashboardLayoutMode
import com.example.liftrix.domain.model.analytics.RankingMetric
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.analytics.UserLevel
import com.example.liftrix.domain.model.analytics.VolumeCalendarData
import com.example.liftrix.domain.model.analytics.VolumeGrouping
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.domain.model.analytics.WidgetLayoutMode
import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.workout.WorkoutAnalyticsDataRepository
import com.example.liftrix.domain.progress.BalanceAnalysis
import com.example.liftrix.domain.progress.ExportOneRmDataRequest
import com.example.liftrix.domain.progress.ExportVolumeDataPoint
import com.example.liftrix.domain.progress.ExportVolumeDataRequest
import com.example.liftrix.domain.progress.ExportWorkoutFrequencyDataRequest
import com.example.liftrix.domain.progress.ExerciseProgression
import com.example.liftrix.domain.progress.ExerciseRankingData
import com.example.liftrix.domain.progress.FrequencyPoint
import com.example.liftrix.domain.progress.MuscleGroup
import com.example.liftrix.domain.progress.MuscleGroupAnalyticsData
import com.example.liftrix.domain.progress.MuscleGroupData
import com.example.liftrix.domain.progress.MuscleGroupDistribution
import com.example.liftrix.domain.progress.OneRmDataPoint
import com.example.liftrix.domain.progress.OneRmProgressionData
import com.example.liftrix.domain.progress.PlateauStatus
import com.example.liftrix.domain.progress.PerformanceTrend
import com.example.liftrix.domain.progress.ProgressAnalyticsServicePort
import com.example.liftrix.domain.progress.ProgressAnomalyPort
import com.example.liftrix.domain.progress.ProgressAuthPort
import com.example.liftrix.domain.progress.ProgressCaloriePort
import com.example.liftrix.domain.progress.ProgressCalorieSummary
import com.example.liftrix.domain.progress.ProgressDailyCalorieData
import com.example.liftrix.domain.progress.ProgressDashboardGateway
import com.example.liftrix.domain.progress.ProgressDashboardConfiguration
import com.example.liftrix.domain.progress.ProgressDashboardWidgetData
import com.example.liftrix.domain.progress.ProgressDataPort
import com.example.liftrix.domain.progress.ProgressDetailAnalyticsGateway
import com.example.liftrix.domain.progress.ProgressExerciseCatalogPort
import com.example.liftrix.domain.progress.ProgressExerciseOption
import com.example.liftrix.domain.progress.ProgressFeatureFlagPort
import com.example.liftrix.domain.progress.ProgressPreferencesPort
import com.example.liftrix.domain.progress.ProgressSessionSummary
import com.example.liftrix.domain.progress.ProgressSessionSummaryPort
import com.example.liftrix.domain.progress.ProgressSettingsPort
import com.example.liftrix.domain.progress.ProgressUnitConversionPort
import com.example.liftrix.domain.progress.ProgressWeeklyCalorieData
import com.example.liftrix.domain.progress.ProgressWeeklyCalorieTrend
import com.example.liftrix.domain.progress.ProgressWidgetResolverPort
import com.example.liftrix.domain.progress.RankedExercise
import com.example.liftrix.domain.progress.VolumeAnalysisData
import com.example.liftrix.domain.progress.VolumeAnalysisDataPoint
import com.example.liftrix.domain.progress.WorkoutFrequencyData
import com.example.liftrix.domain.progress.WorkoutFrequencyDataPoint
import com.example.liftrix.domain.repository.DurationDataPoint
import com.example.liftrix.domain.repository.FrequencyDataPoint
import com.example.liftrix.domain.repository.ProgressSummary
import com.example.liftrix.domain.repository.VolumeDataPoint
import com.example.liftrix.domain.usecase.analytics.AnalyticsExportUseCase
import com.example.liftrix.domain.usecase.analytics.AnalyticsQueryUseCase
import com.example.liftrix.domain.usecase.analytics.GetDashboardConfigurationUseCase
import com.example.liftrix.domain.usecase.analytics.GetWidgetDataUseCase
import com.example.liftrix.domain.usecase.analytics.WidgetPreferencesUseCase
import com.example.liftrix.domain.usecase.anomaly.DetectWorkoutAnomaliesUseCase
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.domain.usecase.exercise.ExerciseQueryUseCase
import com.example.liftrix.domain.usecase.settings.SettingsQueryUseCase
import com.example.liftrix.service.AnalyticsService
import com.example.liftrix.service.CalorieService
import com.example.liftrix.service.FeatureFlagService
import com.example.liftrix.service.PreferencesService
import com.example.liftrix.service.ProgressDataService
import com.example.liftrix.service.UnifiedWorkoutSessionManager
import com.example.liftrix.service.WidgetResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import java.io.File
import javax.inject.Inject
import kotlin.math.abs

class AppProgressCaloriePort @Inject constructor(
    private val delegate: CalorieService
) : ProgressCaloriePort {
    override suspend fun getCalorieSummary(userId: String): LiftrixResult<ProgressCalorieSummary> =
        delegate.getCalorieSummary(userId).map { it.toProgress() }

    override suspend fun getDailyCalories(userId: String, period: TimeRange): LiftrixResult<List<ProgressDailyCalorieData>> =
        delegate.getDailyCalories(userId, period).map { values -> values.map { it.toProgress() } }

    override suspend fun getWeeklyTrend(userId: String): LiftrixResult<ProgressWeeklyCalorieTrend> =
        delegate.getWeeklyTrend(userId).map { it.toProgress() }

}

class AppProgressDataPort @Inject constructor(
    private val delegate: ProgressDataService
) : ProgressDataPort {
    override suspend fun getVolumeData(userId: String, timeRange: TimeRange): LiftrixResult<List<VolumeDataPoint>> = delegate.getVolumeData(userId, timeRange)
    override suspend fun getDurationData(userId: String, timeRange: TimeRange): LiftrixResult<List<DurationDataPoint>> = delegate.getDurationData(userId, timeRange)
    override suspend fun getFrequencyData(userId: String, timeRange: TimeRange): LiftrixResult<List<FrequencyDataPoint>> = delegate.getFrequencyData(userId, timeRange)
    override suspend fun getVolumeCalendarData(userId: String): LiftrixResult<VolumeCalendarData> = delegate.getVolumeCalendarData(userId)
    override suspend fun getProgressSummary(userId: String, timeRange: TimeRange): LiftrixResult<ProgressSummary> = delegate.getProgressSummary(userId, timeRange)
    override suspend fun refreshAllData(userId: String): LiftrixResult<Unit> = delegate.refreshAllData(userId)
}

class AppProgressAnalyticsServicePort @Inject constructor(
    private val delegate: AnalyticsService
) : ProgressAnalyticsServicePort {
    override suspend fun getWidgetData(userId: String, widget: AnalyticsWidget): LiftrixResult<WidgetData> = delegate.getWidgetData(userId, widget)
    override suspend fun getWidgetPreferences(userId: String): LiftrixResult<WidgetPreferences> = delegate.getWidgetPreferences(userId)
    override suspend fun updateWidgetPreferences(preferences: WidgetPreferences): LiftrixResult<Unit> = delegate.updateWidgetPreferences(preferences)
    override suspend fun toggleWidgetVisibility(userId: String, widgetId: String): LiftrixResult<Unit> = delegate.toggleWidgetVisibility(userId, widgetId)
    override suspend fun resetPreferences(userId: String): LiftrixResult<Unit> = delegate.resetPreferences(userId)
}

class AppProgressPreferencesPort @Inject constructor(
    private val delegate: PreferencesService
) : ProgressPreferencesPort {
    override suspend fun getUserPreferences(userId: String): LiftrixResult<WidgetPreferences> = delegate.getUserPreferences(userId)
    override suspend fun updateLayoutMode(userId: String, mode: WidgetLayoutMode): LiftrixResult<Unit> = delegate.updateLayoutMode(userId, mode)
    override suspend fun updateUserLevel(userId: String, level: UserLevel): LiftrixResult<Unit> = delegate.updateUserLevel(userId, level)
    override suspend fun resetToDefaults(userId: String): LiftrixResult<Unit> = delegate.resetToDefaults(userId)
    override suspend fun updateWidgetVisibility(userId: String, widgetName: String, visible: Boolean): LiftrixResult<Unit> = delegate.updateWidgetVisibility(userId, widgetName, visible)
    override suspend fun updateWidgetOrder(userId: String, widgetOrder: List<String>): LiftrixResult<Unit> = delegate.updateWidgetOrder(userId, widgetOrder)
    override suspend fun updateAutoRefreshSettings(userId: String, enabled: Boolean, intervalMinutes: Int): LiftrixResult<Unit> = delegate.updateAutoRefreshSettings(userId, enabled, intervalMinutes)
    override suspend fun toggleSection(userId: String, sectionName: String): LiftrixResult<Unit> = delegate.toggleSection(userId, sectionName)
    override suspend fun saveWidgetPreferences(preferences: WidgetPreferences): LiftrixResult<Unit> = delegate.saveWidgetPreferences(preferences)
}

class AppProgressFeatureFlagPort @Inject constructor(
    private val delegate: FeatureFlagService
) : ProgressFeatureFlagPort {
    override suspend fun isFeatureEnabled(featureKey: String): LiftrixResult<Boolean> = delegate.isFeatureEnabled(featureKey)
    override suspend fun getABTestVariant(testKey: String): LiftrixResult<String> = delegate.getABTestVariant(testKey)
    override suspend fun getAllFeatureFlags(): LiftrixResult<Map<String, Boolean>> = delegate.getAllFeatureFlags()
    override suspend fun refreshRemoteConfig(): LiftrixResult<Unit> = delegate.refreshRemoteConfig()
}

class AppProgressWidgetResolverPort @Inject constructor(
    private val delegate: WidgetResolver
) : ProgressWidgetResolverPort {
    override fun resolveWidgets(userLevel: UserLevel, layoutMode: DashboardLayoutMode, preferences: WidgetPreferences?): List<AnalyticsWidget> =
        delegate.resolveWidgets(userLevel, layoutMode, preferences)

    override fun resolveStandardWidgets(userLevel: UserLevel): List<AnalyticsWidget> = delegate.resolveStandardWidgets(userLevel)
    override fun resolveWidgetsFromPreferences(preferences: WidgetPreferences?, userLevel: UserLevel): List<AnalyticsWidget> = delegate.resolveWidgetsFromPreferences(preferences, userLevel)
    override fun createDefaultPreferences(userId: String, userLevel: UserLevel, layoutMode: DashboardLayoutMode): WidgetPreferences = delegate.createDefaultPreferences(userId, userLevel, layoutMode)
    override fun forceCleanupDeprecatedWidgets(preferences: WidgetPreferences): WidgetPreferences = delegate.forceCleanupDeprecatedWidgets(preferences)
}

class AppProgressSessionSummaryPort @Inject constructor(
    sessionManager: UnifiedWorkoutSessionManager
) : ProgressSessionSummaryPort {
    override val currentSessionSummary: Flow<ProgressSessionSummary?> = sessionManager.currentSession.map { session ->
        session?.let { ProgressSessionSummary(sessionActive = it.isActive(), sessionId = it.id.value) }
    }
}

class AppProgressAuthPort @Inject constructor(
    private val delegate: AuthQueryUseCase
) : ProgressAuthPort {
    override suspend fun invoke(waitForAuth: Boolean): LiftrixResult<com.example.liftrix.domain.model.UserId> =
        delegate(waitForAuth).map { userId -> com.example.liftrix.domain.model.UserId(userId.value) }
}

class AppProgressExerciseCatalogPort @Inject constructor(
    private val delegate: ExerciseQueryUseCase
) : ProgressExerciseCatalogPort {
    override suspend fun invoke(): LiftrixResult<List<ProgressExerciseOption>> =
        delegate().map { exercises ->
            exercises.map { exercise ->
                ProgressExerciseOption(
                    id = exercise.id,
                    name = exercise.name,
                    primaryMuscleGroup = exercise.primaryMuscleGroup,
                    instructions = exercise.instructions
                )
            }
        }
}

class AppProgressSettingsPort @Inject constructor(
    private val delegate: SettingsQueryUseCase
) : ProgressSettingsPort {
    override fun getWeightUnitPreference(userId: String): Flow<com.example.liftrix.domain.model.WeightUnit> =
        delegate.getWeightUnitPreference(userId)
}

class AppProgressUnitConversionPort @Inject constructor(
    private val delegate: com.example.liftrix.domain.service.UnitConversionService
) : ProgressUnitConversionPort {
    override suspend fun getCurrentWeightUnit(userId: String): com.example.liftrix.domain.model.WeightUnit =
        delegate.getCurrentWeightUnit(userId)
}

class AppProgressDashboardGateway @Inject constructor(
    private val getWidgetDataUseCase: GetWidgetDataUseCase,
    private val getDashboardConfigurationUseCase: GetDashboardConfigurationUseCase,
    private val widgetPreferencesUseCase: WidgetPreferencesUseCase
) : ProgressDashboardGateway {
    override suspend fun getDashboardConfiguration(userId: String): Flow<LiftrixResult<ProgressDashboardConfiguration>> =
        getDashboardConfigurationUseCase.execute(
            GetDashboardConfigurationUseCase.GetConfigurationParams(userId = userId)
        ).map { result ->
            result.map { ProgressDashboardConfiguration(it.preferences, it.configuration) }
        }

    override suspend fun getWidgetData(userId: String, widget: AnalyticsWidget): LiftrixResult<ProgressDashboardWidgetData> =
        getWidgetDataUseCase.getWidgetData(userId, widget).map { it.toProgress() }

    override suspend fun refreshWidgetData(userId: String, widget: AnalyticsWidget): LiftrixResult<ProgressDashboardWidgetData> =
        getWidgetDataUseCase.refreshWidgetData(userId, widget).map { it.toProgress() }

    override suspend fun saveWidgetPreferences(preferences: WidgetPreferences): LiftrixResult<Unit> =
        widgetPreferencesUseCase.save(preferences)
}

private fun GetWidgetDataUseCase.WidgetData.toProgress() = ProgressDashboardWidgetData(widgetType, data, lastUpdated, isStale)

class AppProgressDetailAnalyticsGateway @Inject constructor(
    private val query: AnalyticsQueryUseCase,
    private val export: AnalyticsExportUseCase,
    private val workoutAnalyticsDataRepository: WorkoutAnalyticsDataRepository
) : ProgressDetailAnalyticsGateway {
    override suspend fun getVolumeAnalysis(userId: String, timeRange: TimeRangeType, muscleGroupFilter: String?, grouping: VolumeGrouping): LiftrixResult<VolumeAnalysisData> =
        query.getVolumeAnalysis(userId, grouping, timeRange).map { it.toProgress() }

    override suspend fun getOneRmProgression(userId: String, exerciseIds: List<String>?, timeRange: TimeRangeType, includeEstimated: Boolean): LiftrixResult<OneRmProgressionData> =
        query.getOneRmProgression(userId, exerciseIds, timeRange, includeEstimated).map { it.toProgress() }

    override suspend fun getWorkoutFrequency(userId: String, timeRange: TimeRangeType): LiftrixResult<WorkoutFrequencyData> =
        query.getWorkoutFrequency(userId, timeRange).map { it.toProgress() }

    override suspend fun getMuscleGroupAnalytics(userId: String, timeRange: TimeRangeType, muscleGroup: MuscleGroup?): LiftrixResult<MuscleGroupAnalyticsData> =
        query.getMuscleGroupAnalytics(userId, muscleGroup?.toUseCase(), timeRange).map { it.toProgress() }

    override suspend fun getExerciseRanking(userId: String, timeRange: TimeRangeType, metric: RankingMetric): LiftrixResult<ExerciseRankingData> =
        getExercisePerformanceData(userId, timeRange).map { exercises -> exercises.toProgressRanking(metric, timeRange) }

    override suspend fun exportOneRm(request: ExportOneRmDataRequest): LiftrixResult<File> =
        export.exportOneRm(request.toUseCase())

    override suspend fun exportVolume(request: ExportVolumeDataRequest): LiftrixResult<File> =
        export.exportVolume(request.toUseCase())

    override suspend fun exportFrequency(request: ExportWorkoutFrequencyDataRequest): LiftrixResult<File> =
        export.exportFrequency(request.toUseCase())

    private suspend fun getExercisePerformanceData(
        userId: String,
        timeRange: TimeRangeType
    ): LiftrixResult<List<com.example.liftrix.domain.usecase.analytics.ExercisePerformanceData>> {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val startDate = when (timeRange) {
            TimeRangeType.MONTH -> today.minus(DatePeriod(months = 1))
            TimeRangeType.SIX_MONTHS -> today.minus(DatePeriod(months = 6))
            TimeRangeType.ALL_TIME -> kotlinx.datetime.LocalDate(2020, 1, 1)
        }
        return workoutAnalyticsDataRepository.getExercisePerformanceData(userId, startDate, today)
    }
}

class AppProgressAnomalyPort @Inject constructor(
    private val delegate: DetectWorkoutAnomaliesUseCase
) : ProgressAnomalyPort {
    override suspend fun detectWeightAnomaly(userId: String, sessionId: String, exerciseId: ExerciseId, exerciseName: String, currentWeight: Double, previousWeight: Double?): LiftrixResult<WorkoutAnomaly?> =
        delegate.detectWeightAnomaly(userId, sessionId, exerciseId, exerciseName, currentWeight, previousWeight)

    override suspend fun detectRepsAnomaly(userId: String, sessionId: String, exerciseId: ExerciseId, exerciseName: String, currentReps: Int, previousReps: Int?): LiftrixResult<WorkoutAnomaly?> =
        delegate.detectRepsAnomaly(userId, sessionId, exerciseId, exerciseName, currentReps, previousReps)

    override suspend fun detectDurationAnomaly(userId: String, sessionId: String, exerciseId: ExerciseId, exerciseName: String, currentDuration: Long, previousDuration: Long?): LiftrixResult<WorkoutAnomaly?> =
        delegate.detectDurationAnomaly(userId, sessionId, exerciseId, exerciseName, currentDuration, previousDuration)

    override suspend fun resolveAnomaly(anomalyId: String, userAction: UserAnomalyAction, correctedValue: AnomalyValue?): LiftrixResult<WorkoutAnomaly> =
        delegate.resolveAnomaly(anomalyId, userAction, correctedValue)

    override suspend fun updateExerciseHistory(userId: String, exerciseId: ExerciseId, weight: Double?, reps: Int?, duration: Long?): LiftrixResult<Unit> =
        delegate.updateExerciseHistory(userId, exerciseId, weight, reps, duration)
    override suspend fun getDetectionSettings(userId: String): AnomalyDetectionSettings = delegate.getDetectionSettings(userId)
    override suspend fun getExerciseHistory(userId: String, exerciseId: ExerciseId): ExerciseHistory = delegate.getExerciseHistory(userId, exerciseId)
    override suspend fun getUserAnomalyFeedback(userId: String): LiftrixResult<Pair<Int, Int>> = delegate.getUserAnomalyFeedback(userId)
}

private fun com.example.liftrix.service.CalorieSummary.toProgress() = ProgressCalorieSummary(
    totalCaloriesBurned, averageDailyCalories, totalWorkouts, averageWorkoutCalories, highestDailyCalories, currentWeekCalories, previousWeekCalories, weeklyTrend
)

private fun com.example.liftrix.service.DailyCalorieData.toProgress() = ProgressDailyCalorieData(
    date, totalCalories, workoutCount, averageIntensity, topExerciseCategory, durationMinutes
)

private fun com.example.liftrix.service.WeeklyCalorieTrend.toProgress() = ProgressWeeklyCalorieTrend(
    weeklyData.map { it.toProgress() },
    movingAverage,
    trendPercentage,
    peakWeek?.toProgress(),
    lowWeek?.toProgress(),
    consistency
)

private fun com.example.liftrix.service.WeeklyCalorieData.toProgress() = ProgressWeeklyCalorieData(
    weekStartDate, weekEndDate, totalCalories, workoutCount, averageDailyCalories, mostActiveDay
)

private fun com.example.liftrix.domain.usecase.analytics.VolumeAnalysisData.toProgress() = VolumeAnalysisData(
    volumeData.map { VolumeAnalysisDataPoint(it.date, it.volume, it.sets, it.exercises, it.label) },
    totalVolume,
    volumeGrowth,
    averageVolume,
    isEmpty
)

private fun com.example.liftrix.domain.usecase.analytics.OneRmProgressionData.toProgress() = OneRmProgressionData(
    exerciseProgressions.map { ExerciseProgression(it.exerciseId, it.exerciseName, it.progressionPoints.map { point -> point.toProgress() }) }
)

private fun com.example.liftrix.domain.usecase.analytics.OneRmDataPoint.toProgress() = OneRmDataPoint(
    date, exerciseId, exerciseName, actualOneRm, estimatedOneRm, weight, reps, isEstimated, oneRmValue
)

private fun com.example.liftrix.domain.usecase.analytics.WorkoutFrequencyData.toProgress() = WorkoutFrequencyData(
    totalWorkouts, consistencyScore, frequencyPoints.map { FrequencyPoint(it.date, it.workoutCount, it.dayOfWeek) }, totalWorkoutDays, dailyAverage, currentStreak, longestStreak, weeklyDistribution
)

private fun com.example.liftrix.domain.usecase.analytics.MuscleGroupAnalyticsData.toProgress() = MuscleGroupAnalyticsData(
    muscleGroupDistribution.map { MuscleGroupDistribution(it.muscleGroup, it.volumePercentage, it.totalVolume, it.exerciseCount) }
)

private fun List<com.example.liftrix.domain.usecase.analytics.ExercisePerformanceData>.toProgressRanking(
    metric: RankingMetric,
    timeRange: TimeRangeType
): ExerciseRankingData {
    val ranked = sortedWith(
        compareByDescending<com.example.liftrix.domain.usecase.analytics.ExercisePerformanceData> { it.rankingScore(metric) }
            .thenBy { it.exerciseName }
    ).mapIndexed { index, exercise -> exercise.toProgressRankedExercise(index + 1) }

    return ExerciseRankingData(
        rankedExercises = ranked,
        topPerformer = ranked.minByOrNull { it.rank },
        mostImproved = ranked.maxByOrNull { it.performanceScore },
        needsAttention = ranked.filter { it.plateauStatus == PlateauStatus.STAGNANT || it.plateauStatus == PlateauStatus.DECLINING },
        overallScore = if (ranked.isEmpty()) 0.0 else ranked.map { it.performanceScore }.average(),
        timeRange = timeRange,
        isEmpty = ranked.isEmpty()
    )
}

private fun com.example.liftrix.domain.usecase.analytics.ExercisePerformanceData.toProgressRankedExercise(rank: Int): RankedExercise {
    val plateauStatus = detectPlateauStatus()
    return RankedExercise(
        rank = rank,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        performanceScore = performanceScore,
        totalVolume = totalVolume,
        workoutDays = workoutDays,
        totalSets = totalSets,
        maxEstimated1RM = maxEstimated1RM,
        plateauStatus = plateauStatus,
        trend = determineTrend(),
        recommendations = recommendationsFor(plateauStatus),
        muscleGroup = muscleGroup
    )
}

private fun com.example.liftrix.domain.usecase.analytics.ExercisePerformanceData.rankingScore(metric: RankingMetric): Double =
    when (metric) {
        RankingMetric.PERFORMANCE_SCORE -> performanceScore
        RankingMetric.VOLUME_GROWTH -> volumeGrowthPercentage() ?: Double.NEGATIVE_INFINITY
        RankingMetric.STRENGTH_GROWTH -> strengthGrowthPercentage() ?: Double.NEGATIVE_INFINITY
        RankingMetric.FREQUENCY -> workoutDays.toDouble()
        RankingMetric.CONSISTENCY -> -(volumeVariancePercentage() ?: Double.POSITIVE_INFINITY)
        RankingMetric.RECENT_TREND -> recentTrendScore() ?: Double.NEGATIVE_INFINITY
    }

private fun com.example.liftrix.domain.usecase.analytics.ExercisePerformanceData.volumeGrowthPercentage(): Double? {
    if (volumeHistory.size < 2) return null
    val sorted = volumeHistory.sortedBy { it.date }
    val first = sorted.first().volume
    val last = sorted.last().volume
    return if (first > 0.0) ((last - first) / first) * 100.0 else null
}

private fun com.example.liftrix.domain.usecase.analytics.ExercisePerformanceData.strengthGrowthPercentage(): Double? {
    if (oneRmHistory.size < 2) return null
    val sorted = oneRmHistory.sortedBy { it.date }
    val first = sorted.first().oneRm
    val last = sorted.last().oneRm
    return if (first > 0.0) ((last - first) / first) * 100.0 else null
}

private fun com.example.liftrix.domain.usecase.analytics.ExercisePerformanceData.volumeVariancePercentage(): Double? {
    val values = volumeHistory.map { it.volume }.filter { it > 0.0 }
    if (values.isEmpty()) return null
    val mean = values.average()
    if (mean == 0.0) return null
    return values.map { abs(it - mean) }.average() / mean * 100.0
}

private fun com.example.liftrix.domain.usecase.analytics.ExercisePerformanceData.recentTrendScore(): Double? {
    val values = volumeHistory.sortedBy { it.date }.map { it.volume }.filter { it > 0.0 }
    if (values.size < 2) return null
    val midpoint = values.size / 2
    val earlier = values.take(midpoint).average()
    val recent = values.drop(midpoint).average()
    return if (earlier > 0.0) ((recent - earlier) / earlier) * 100.0 else null
}

private fun com.example.liftrix.domain.usecase.analytics.ExercisePerformanceData.detectPlateauStatus(): PlateauStatus =
    when {
        workoutDays < 3 -> PlateauStatus.INSUFFICIENT_DATA
        performanceScore < -5.0 -> PlateauStatus.DECLINING
        totalVolume < 1000.0 && maxEstimated1RM < 50.0 -> PlateauStatus.STAGNANT
        performanceScore > 10.0 -> PlateauStatus.PROGRESSING
        else -> PlateauStatus.STABLE
    }

private fun com.example.liftrix.domain.usecase.analytics.ExercisePerformanceData.determineTrend(): PerformanceTrend =
    when {
        performanceScore > 10.0 -> PerformanceTrend.IMPROVING
        performanceScore < -5.0 -> PerformanceTrend.DECLINING
        else -> PerformanceTrend.STABLE
    }

private fun com.example.liftrix.domain.usecase.analytics.ExercisePerformanceData.recommendationsFor(status: PlateauStatus): List<String> =
    when (status) {
        PlateauStatus.INSUFFICIENT_DATA -> listOf("Perform this exercise more frequently to track progress")
        PlateauStatus.DECLINING -> listOf("Focus on form and consider reducing weight")
        PlateauStatus.STAGNANT -> listOf("Try increasing intensity or changing rep ranges")
        PlateauStatus.STABLE -> listOf("Maintain current programming - steady progress")
        PlateauStatus.PROGRESSING -> listOf("Excellent progress! Continue your approach")
    }

private fun com.example.liftrix.domain.usecase.analytics.ExerciseRankingData.toProgress() = ExerciseRankingData(
    rankedExercises = rankedExercises.map { it.toProgress() },
    topPerformer = topPerformer?.toProgress(),
    mostImproved = mostImproved?.toProgress(),
    needsAttention = needsAttention.map { it.toProgress() },
    overallScore = overallScore,
    timeRange = timeRange,
    isEmpty = isEmpty
)

private fun com.example.liftrix.domain.usecase.analytics.RankedExercise.toProgress() = RankedExercise(
    rank, exerciseId, exerciseName, performanceScore, totalVolume, workoutDays, totalSets, maxEstimated1RM, plateauStatus.toProgress(), trend.toProgress(), recommendations, muscleGroup
)

private fun com.example.liftrix.domain.usecase.analytics.PlateauStatus.toProgress() = PlateauStatus.valueOf(name)
private fun com.example.liftrix.domain.usecase.analytics.PerformanceTrend.toProgress() = PerformanceTrend.valueOf(name)
private fun MuscleGroup.toUseCase() = com.example.liftrix.domain.usecase.analytics.MuscleGroup.valueOf(name)

private fun ExportOneRmDataRequest.toUseCase() = com.example.liftrix.domain.usecase.analytics.ExportOneRmDataRequest(
    progressionPoints.map {
        com.example.liftrix.domain.model.analytics.OneRmDataPoint(
            date = it.date,
            exerciseId = it.exerciseId,
            exerciseName = it.exerciseName,
            actualOneRm = it.actualOneRm,
            estimatedOneRm = requireNotNull(it.estimatedOneRm ?: it.oneRmValue ?: it.actualOneRm) {
                "1RM export point must include an estimated, actual, or derived 1RM value"
            },
            weight = it.weight,
            reps = it.reps,
            isEstimated = it.isEstimated
        )
    },
    exerciseNames,
    timeRange,
    showEstimated
)

private fun ExportVolumeDataRequest.toUseCase() = com.example.liftrix.domain.usecase.analytics.ExportVolumeDataRequest(
    volumePoints.map { it.toUseCase() },
    timeRange,
    muscleGroupFilter,
    includeBreakdown
)

private fun ExportVolumeDataPoint.toUseCase() = com.example.liftrix.domain.usecase.analytics.ExportVolumeDataPoint(
    date, exerciseId, exerciseName, muscleGroup, sets, reps, weight, totalVolume
)

private fun ExportWorkoutFrequencyDataRequest.toUseCase() = com.example.liftrix.domain.usecase.analytics.ExportWorkoutFrequencyDataRequest(
    frequencyPoints.map { it.toUseCase() },
    timeRange,
    includeHeatmap,
    includeTrends
)

private fun WorkoutFrequencyDataPoint.toUseCase() = com.example.liftrix.domain.usecase.analytics.WorkoutFrequencyDataPoint(
    date, dayOfWeek, workoutCount, durationMinutes, consistencyScore
)
