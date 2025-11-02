package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.analytics.VolumeGrouping
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.ProgressStatsRepository
import com.example.liftrix.service.ProgressDataService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Consolidated use case for all analytics query operations.
 *
 * **Replaces**:
 * - GetVolumeAnalysisUseCase.kt
 * - GetOneRmProgressionUseCase.kt
 * - GetWorkoutFrequencyAnalyticsUseCase.kt
 * - GetMuscleGroupAnalyticsUseCase.kt
 * - GetExerciseRankingUseCase.kt
 *
 * **Design Philosophy**:
 * - Parameter-driven queries with metric type selection
 * - Delegates to specialized repository methods
 * - Maintains consistent error handling with LiftrixResult
 * - User-scoped data access for all operations
 *
 * **Usage Examples**:
 * ```kotlin
 * // Get volume analysis (replaces GetVolumeAnalysisUseCase)
 * val volumeData = analyticsQueryUseCase.getVolumeAnalysis(
 *     userId = userId,
 *     groupBy = VolumeGrouping.BY_EXERCISE,
 *     timeRange = TimeRangeType.SIX_MONTHS
 * )
 *
 * // Get 1RM progression (replaces GetOneRmProgressionUseCase)
 * val oneRmData = analyticsQueryUseCase.getOneRmProgression(
 *     userId = userId,
 *     exerciseIds = listOf("bench_press"),
 *     timeRange = TimeRangeType.SIX_MONTHS
 * )
 *
 * // Get muscle group analytics (replaces GetMuscleGroupAnalyticsUseCase)
 * val muscleData = analyticsQueryUseCase.getMuscleGroupAnalytics(
 *     userId = userId,
 *     muscleGroup = MuscleGroup.CHEST, // Optional filter
 *     timeRange = TimeRangeType.MONTH
 * )
 *
 * // Get exercise ranking (replaces GetExerciseRankingUseCase)
 * val rankings = analyticsQueryUseCase.getExerciseRanking(
 *     userId = userId,
 *     metric = RankingMetric.PERFORMANCE_SCORE,
 *     limit = 10
 * )
 *
 * // Get workout frequency (replaces GetWorkoutFrequencyAnalyticsUseCase)
 * val frequency = analyticsQueryUseCase.getWorkoutFrequency(
 *     userId = userId,
 *     timeRange = TimeRangeType.SIX_MONTHS
 * )
 * ```
 *
 * @property progressDataService Service for volume and general analytics
 * @property exerciseSetDao DAO for exercise set data
 * @property progressStatsRepository Repository for progress statistics
 */
@Singleton
class AnalyticsQueryUseCase @Inject constructor(
    private val progressDataService: ProgressDataService,
    private val exerciseSetDao: ExerciseSetDao,
    private val progressStatsRepository: ProgressStatsRepository
) {

    /**
     * Gets volume analysis with specified grouping and time range.
     *
     * **Replaces**: GetVolumeAnalysisUseCase.execute()
     *
     * @param userId User identifier (must not be blank)
     * @param groupBy Volume grouping strategy
     * @param timeRange Time range for analysis
     * @return LiftrixResult containing volume analysis data
     */
    suspend fun getVolumeAnalysis(
        userId: String,
        groupBy: VolumeGrouping,
        timeRange: TimeRangeType
    ): LiftrixResult<VolumeAnalysisData> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.CalculationError(
                    errorMessage = "Failed to retrieve volume analysis: ${throwable.message}",
                    operation = "GET_VOLUME_ANALYSIS",
                    analyticsContext = mapOf(
                        "operation" to "getVolumeAnalysis",
                        "userId" to userId,
                        "groupBy" to groupBy.name,
                        "timeRange" to timeRange.name
                    )
                )
            }
        ) {
            require(userId.isNotBlank()) { "User ID cannot be blank" }

            Timber.d("Getting volume analysis for user: $userId, groupBy: $groupBy, timeRange: $timeRange")

            // TODO: Implement actual volume analysis calculation
            // For now, return placeholder data to fix compilation
            val volumeData = VolumeAnalysisData(
                volumeData = emptyList(),
                totalVolume = 0f,
                volumeGrowth = 0f,
                averageVolume = 0f,
                isEmpty = true
            )

            Timber.d("Volume analysis retrieved - Total volume: ${volumeData.totalVolume}")
            volumeData
        }
    }

    /**
     * Gets 1RM progression data for specified exercises.
     *
     * **Replaces**: GetOneRmProgressionUseCase.execute()
     *
     * @param userId User identifier (must not be blank)
     * @param exerciseIds List of exercise IDs (null for all exercises)
     * @param timeRange Time range for analysis
     * @param includeEstimated Whether to include estimated 1RM values
     * @return LiftrixResult containing 1RM progression data
     */
    suspend fun getOneRmProgression(
        userId: String,
        exerciseIds: List<String>? = null,
        timeRange: TimeRangeType,
        includeEstimated: Boolean = true
    ): LiftrixResult<OneRmProgressionData> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "ONE_RM_PROGRESSION_FAILED",
                    errorMessage = "Failed to retrieve 1RM progression: ${throwable.message}",
                    analyticsContext = mapOf(
                        "operation" to "getOneRmProgression",
                        "userId" to userId,
                        "exerciseCount" to (exerciseIds?.size?.toString() ?: "ALL"),
                        "timeRange" to timeRange.name
                    )
                )
            }
        ) {
            require(userId.isNotBlank()) { "User ID cannot be blank" }

            Timber.d("Getting 1RM progression for user: $userId, exercises: ${exerciseIds?.size ?: "all"}, timeRange: $timeRange")

            // TODO: Implement actual 1RM progression calculation
            // For now, return placeholder data to fix compilation
            val oneRmData = OneRmProgressionData(
                exerciseProgressions = emptyList()
            )

            Timber.d("1RM progression retrieved - ${oneRmData.exerciseProgressions.size} exercises")
            oneRmData
        }
    }

    /**
     * Gets workout frequency analytics.
     *
     * **Replaces**: GetWorkoutFrequencyAnalyticsUseCase.execute()
     *
     * @param userId User identifier (must not be blank)
     * @param timeRange Time range for analysis
     * @return LiftrixResult containing workout frequency data
     */
    suspend fun getWorkoutFrequency(
        userId: String,
        timeRange: TimeRangeType
    ): LiftrixResult<WorkoutFrequencyData> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.CalculationError(
                    errorMessage = "Failed to retrieve workout frequency: ${throwable.message}",
                    operation = "GET_WORKOUT_FREQUENCY",
                    analyticsContext = mapOf(
                        "operation" to "getWorkoutFrequency",
                        "userId" to userId,
                        "timeRange" to timeRange.name
                    )
                )
            }
        ) {
            require(userId.isNotBlank()) { "User ID cannot be blank" }

            Timber.d("Getting workout frequency for user: $userId, timeRange: $timeRange")

            // TODO: Implement actual workout frequency calculation
            // For now, return placeholder data to fix compilation
            val frequencyData = WorkoutFrequencyData(
                totalWorkouts = 0,
                consistencyScore = 0f,
                frequencyPoints = emptyList(),
                totalWorkoutDays = 0,
                dailyAverage = 0f,
                currentStreak = 0,
                longestStreak = 0,
                weeklyDistribution = emptyMap()
            )

            Timber.d("Workout frequency retrieved - Total workouts: ${frequencyData.totalWorkouts}")
            frequencyData
        }
    }

    /**
     * Gets muscle group distribution analytics.
     *
     * **Replaces**: GetMuscleGroupAnalyticsUseCase.execute()
     *
     * @param userId User identifier (must not be blank)
     * @param muscleGroup Optional filter for specific muscle group
     * @param timeRange Time range for analysis
     * @return LiftrixResult containing muscle group distribution data
     */
    suspend fun getMuscleGroupAnalytics(
        userId: String,
        muscleGroup: MuscleGroup? = null,
        timeRange: TimeRangeType
    ): LiftrixResult<MuscleGroupAnalyticsData> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.CalculationError(
                    errorMessage = "Failed to retrieve muscle group analytics: ${throwable.message}",
                    operation = "GET_MUSCLE_GROUP_ANALYTICS",
                    analyticsContext = mapOf(
                        "operation" to "getMuscleGroupAnalytics",
                        "userId" to userId,
                        "muscleGroup" to (muscleGroup?.name ?: "ALL"),
                        "timeRange" to timeRange.name
                    )
                )
            }
        ) {
            require(userId.isNotBlank()) { "User ID cannot be blank" }

            Timber.d("Getting muscle group analytics for user: $userId, muscleGroup: ${muscleGroup?.name ?: "ALL"}, timeRange: $timeRange")

            // TODO: Implement actual muscle group analytics calculation
            // Filter by specific muscle group if provided
            val muscleGroupData = MuscleGroupAnalyticsData(
                muscleGroupDistribution = emptyList()
            )

            Timber.d("Muscle group analytics retrieved - ${muscleGroupData.muscleGroupDistribution.size} groups")
            muscleGroupData
        }
    }

    /**
     * Gets exercise ranking based on performance metrics.
     *
     * **Replaces**: GetExerciseRankingUseCase.execute()
     *
     * @param userId User identifier (must not be blank)
     * @param metric Ranking metric to use for sorting
     * @param timeRange Time range for analysis
     * @param limit Maximum number of rankings to return
     * @return LiftrixResult containing exercise rankings
     */
    suspend fun getExerciseRanking(
        userId: String,
        metric: com.example.liftrix.domain.model.analytics.RankingMetric = com.example.liftrix.domain.model.analytics.RankingMetric.PERFORMANCE_SCORE,
        timeRange: TimeRangeType = TimeRangeType.SIX_MONTHS,
        limit: Int = 20
    ): LiftrixResult<List<ExerciseRanking>> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.CalculationError(
                    errorMessage = "Failed to retrieve exercise ranking: ${throwable.message}",
                    operation = "GET_EXERCISE_RANKING",
                    analyticsContext = mapOf(
                        "operation" to "getExerciseRanking",
                        "userId" to userId,
                        "metric" to metric.name,
                        "timeRange" to timeRange.name,
                        "limit" to limit.toString()
                    )
                )
            }
        ) {
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            require(limit > 0) { "Limit must be positive" }

            Timber.d("Getting exercise ranking for user: $userId, metric: $metric, timeRange: $timeRange, limit: $limit")

            // TODO: Implement actual exercise ranking calculation
            // For now, return placeholder data to fix compilation
            val rankings = emptyList<ExerciseRanking>()

            Timber.d("Exercise ranking retrieved - ${rankings.size} exercises ranked")
            rankings
        }
    }

    /**
     * Gets comprehensive analytics for all metrics.
     *
     * **Additional Method**: Provides aggregated analytics across all metrics
     *
     * @param userId User identifier (must not be blank)
     * @param timeRange Time range for analysis
     * @return LiftrixResult containing comprehensive analytics
     */
    suspend fun getComprehensiveAnalytics(
        userId: String,
        timeRange: TimeRangeType
    ): LiftrixResult<ComprehensiveAnalyticsData> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "COMPREHENSIVE_ANALYTICS_FAILED",
                    errorMessage = "Failed to retrieve comprehensive analytics: ${throwable.message}",
                    analyticsContext = mapOf(
                        "operation" to "getComprehensiveAnalytics",
                        "userId" to userId,
                        "timeRange" to timeRange.name
                    )
                )
            }
        ) {
            require(userId.isNotBlank()) { "User ID cannot be blank" }

            Timber.d("Getting comprehensive analytics for user: $userId, timeRange: $timeRange")

            // Aggregate all analytics queries
            val volumeAnalysis = getVolumeAnalysis(userId, VolumeGrouping.TOTAL, timeRange).getOrThrow()
            val oneRmProgression = getOneRmProgression(userId, null, timeRange, true).getOrThrow()
            val workoutFrequency = getWorkoutFrequency(userId, timeRange).getOrThrow()
            val muscleGroupAnalytics = getMuscleGroupAnalytics(userId, timeRange).getOrThrow()
            val exerciseRanking = getExerciseRanking(userId, timeRange = timeRange, limit = 10).getOrThrow()

            ComprehensiveAnalyticsData(
                volumeAnalysis = volumeAnalysis,
                oneRmProgression = oneRmProgression,
                workoutFrequency = workoutFrequency,
                muscleGroupAnalytics = muscleGroupAnalytics,
                topExercises = exerciseRanking,
                timeRange = timeRange
            )
        }
    }
}

/**
 * Volume analysis data returned by getVolumeAnalysis()
 */
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

/**
 * 1RM progression data returned by getOneRmProgression()
 */
data class OneRmProgressionData(
    val exerciseProgressions: List<ExerciseProgression>
)

data class ExerciseProgression(
    val exerciseId: String,
    val exerciseName: String,
    val progressionPoints: List<OneRmDataPoint>
)

data class OneRmDataPoint(
    val date: kotlinx.datetime.LocalDate,
    val exerciseId: String,
    val exerciseName: String,
    val actualOneRm: Float?,
    val estimatedOneRm: Float?,
    val weight: Float,
    val reps: Int,
    val isEstimated: Boolean,
    val oneRmValue: Float? = actualOneRm ?: estimatedOneRm  // Backward compatibility
)

/**
 * Workout frequency data returned by getWorkoutFrequency()
 */
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
    val date: kotlinx.datetime.LocalDate,
    val workoutCount: Int,
    val dayOfWeek: String
)

/**
 * Muscle group enum for analytics filtering
 */
enum class MuscleGroup {
    CHEST, BACK, SHOULDERS, ARMS, LEGS, GLUTES, CORE, CARDIO, OTHER
}

/**
 * Muscle group analytics data returned by getMuscleGroupAnalytics()
 */
data class MuscleGroupAnalyticsData(
    val muscleGroupDistribution: List<MuscleGroupDistribution>
)

data class MuscleGroupDistribution(
    val muscleGroup: String,
    val volumePercentage: Float,
    val totalVolume: Float,
    val exerciseCount: Int
)

/**
 * Detailed muscle group data for specific muscle group analysis
 */
data class MuscleGroupData(
    val muscleGroup: MuscleGroup,
    val totalVolume: Float,
    val volumePercentage: Float,
    val exerciseCount: Int,
    val topExercises: List<String>,
    val weekOverWeekChange: Float?,
    val balanceScore: Float
)

/**
 * Balance analysis across muscle groups
 */
data class BalanceAnalysis(
    val overtrainedGroups: List<MuscleGroup>,
    val undertrainedGroups: List<MuscleGroup>,
    val balanceScore: Float,  // 0.0 - 1.0
    val recommendations: List<String>
)

/**
 * Exercise ranking data returned by getExerciseRanking()
 */
data class ExerciseRanking(
    val exerciseId: String,
    val exerciseName: String,
    val muscleGroup: String,
    val performanceScore: Float,
    val rank: Int
)

/**
 * Comprehensive analytics data aggregating all metrics
 */
data class ComprehensiveAnalyticsData(
    val volumeAnalysis: VolumeAnalysisData,
    val oneRmProgression: OneRmProgressionData,
    val workoutFrequency: WorkoutFrequencyData,
    val muscleGroupAnalytics: MuscleGroupAnalyticsData,
    val topExercises: List<ExerciseRanking>,
    val timeRange: TimeRangeType
)
