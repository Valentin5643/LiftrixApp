package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.data.local.dao.ExerciseLibraryDao
import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.analytics.VolumeCalendarData
import com.example.liftrix.domain.model.analytics.VolumeGrouping
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.ProgressStatsRepository
import com.example.liftrix.domain.repository.DashboardData
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.service.AnalyticsEngine
import com.example.liftrix.service.ProgressDataService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
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
    private val exerciseLibraryDao: ExerciseLibraryDao,
    private val progressStatsRepository: ProgressStatsRepository,
    private val workoutRepository: WorkoutRepository,
    private val analyticsEngine: AnalyticsEngine,
    private val widgetCalculatorFactory: com.example.liftrix.domain.service.widget.WidgetCalculatorFactory
) {
    // ========================================
    // 🚀 CRITICAL-002 Phase 3: Analytics Caching Layer
    // ========================================

    /**
     * Cache entry with TTL support for analytics data.
     */
    private data class CacheEntry<T>(
        val data: T,
        val timestamp: Long,
        val ttlMs: Long
    ) {
        fun isValid(): Boolean = System.currentTimeMillis() - timestamp < ttlMs
    }

    // Cache storage with thread-safe concurrent access
    private val analyticsCache = ConcurrentHashMap<String, CacheEntry<Any>>()

    // Cache TTLs by data type (in milliseconds)
    private object CacheTTL {
        const val VOLUME_ANALYSIS = 10 * 60 * 1000L      // 10 minutes
        const val ONE_RM_PROGRESSION = 15 * 60 * 1000L  // 15 minutes (more stable)
        const val WORKOUT_FREQUENCY = 10 * 60 * 1000L   // 10 minutes
        const val MUSCLE_GROUP = 10 * 60 * 1000L        // 10 minutes
        const val EXERCISE_RANKING = 15 * 60 * 1000L    // 15 minutes (more stable)
    }

    /**
     * Get cached data or null if not present/expired.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> getCached(key: String): T? {
        val entry = analyticsCache[key] ?: return null
        return if (entry.isValid()) {
            Timber.d("[ANALYTICS-CACHE] Cache hit for key: $key")
            entry.data as? T
        } else {
            analyticsCache.remove(key)
            Timber.d("[ANALYTICS-CACHE] Cache expired for key: $key")
            null
        }
    }

    /**
     * Store data in cache with specified TTL.
     */
    private fun <T : Any> putCache(key: String, data: T, ttlMs: Long) {
        analyticsCache[key] = CacheEntry(data, System.currentTimeMillis(), ttlMs)
        Timber.d("[ANALYTICS-CACHE] Cached data for key: $key (TTL: ${ttlMs / 1000}s)")
    }

    /**
     * Generate cache key for analytics queries.
     */
    private fun cacheKey(userId: String, type: String, vararg params: Any?): String {
        val paramString = params.filterNotNull().joinToString("_")
        return "$userId:$type:$paramString"
    }

    /**
     * Invalidate all cache entries for a user.
     * Call this when workout data changes.
     */
    fun invalidateCacheForUser(userId: String) {
        val keysToRemove = analyticsCache.keys.filter { it.startsWith("$userId:") }
        keysToRemove.forEach { analyticsCache.remove(it) }
        Timber.d("[ANALYTICS-CACHE] Invalidated ${keysToRemove.size} cache entries for user: $userId")
    }

    /**
     * Clear all cached analytics data.
     */
    fun clearAllCache() {
        val size = analyticsCache.size
        analyticsCache.clear()
        Timber.d("[ANALYTICS-CACHE] Cleared all $size cache entries")
    }

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

            // 🚀 CRITICAL-002 Phase 3: Check cache first
            val cacheKey = cacheKey(userId, "VOLUME", groupBy.name, timeRange.name)
            getCached<VolumeAnalysisData>(cacheKey)?.let { return@liftrixCatching it }

            Timber.d("Getting volume analysis for user: $userId, groupBy: $groupBy, timeRange: $timeRange")

            // Convert TimeRangeType to TimeRange
            val timeRangeObj = timeRange.toTimeRange()

            // Fetch volume data from service
            val volumeResult = progressDataService.getVolumeData(userId, timeRangeObj)
            val rawVolumeData = volumeResult.getOrThrow()

            if (rawVolumeData.isEmpty()) {
                Timber.d("No volume data found for user: $userId")
                return@liftrixCatching VolumeAnalysisData(
                    volumeData = emptyList(),
                    totalVolume = 0f,
                    volumeGrowth = 0f,
                    averageVolume = 0f,
                    isEmpty = true
                )
            }

            // Convert repository VolumeDataPoint to VolumeAnalysisDataPoint
            val convertedData = rawVolumeData.map { dataPoint ->
                VolumeAnalysisDataPoint(
                    date = dataPoint.date.toString(),
                    volume = dataPoint.totalVolume.toDouble(),
                    sets = 0, // Not available from repository
                    exercises = dataPoint.exerciseCount,
                    label = dataPoint.date.toString()
                )
            }

            // Calculate aggregates
            val totalVolume = rawVolumeData.sumOf { it.totalVolume.toDouble() }.toFloat()
            val averageVolume = if (rawVolumeData.isNotEmpty()) {
                totalVolume / rawVolumeData.size
            } else 0f

            // Calculate growth (compare first half to second half)
            val volumeGrowth = if (rawVolumeData.size >= 2) {
                val midPoint = rawVolumeData.size / 2
                val firstHalf = rawVolumeData.take(midPoint).sumOf { it.totalVolume.toDouble() }.toFloat()
                val secondHalf = rawVolumeData.drop(midPoint).sumOf { it.totalVolume.toDouble() }.toFloat()
                if (firstHalf > 0) {
                    ((secondHalf - firstHalf) / firstHalf) * 100f
                } else 0f
            } else 0f

            val volumeData = VolumeAnalysisData(
                volumeData = convertedData,
                totalVolume = totalVolume,
                volumeGrowth = volumeGrowth,
                averageVolume = averageVolume,
                isEmpty = convertedData.isEmpty()
            )

            // 🚀 CRITICAL-002 Phase 3: Store in cache
            putCache(cacheKey, volumeData, CacheTTL.VOLUME_ANALYSIS)

            Timber.d("Volume analysis retrieved - Total volume: ${volumeData.totalVolume}, data points: ${convertedData.size}")
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

            // 🚀 CRITICAL-002 Phase 3: Check cache first
            val exerciseKey = exerciseIds?.sorted()?.joinToString(",") ?: "ALL"
            val cacheKey = cacheKey(userId, "ONE_RM", exerciseKey, timeRange.name, includeEstimated)
            getCached<OneRmProgressionData>(cacheKey)?.let { return@liftrixCatching it }

            Timber.d("Getting 1RM progression for user: $userId, exercises: ${exerciseIds?.size ?: "all"}, timeRange: $timeRange")

            // Step 1: Get date range
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val startDate = when (timeRange) {
                TimeRangeType.MONTH -> today.minus(DatePeriod(months = 1))
                TimeRangeType.SIX_MONTHS -> today.minus(DatePeriod(months = 6))
                TimeRangeType.ALL_TIME -> LocalDate(2020, 1, 1)
            }

            // Step 2: Query DAO for 1RM data
            val oneRmResults = if (exerciseIds.isNullOrEmpty()) {
                exerciseSetDao.getAllOneRmData(userId, startDate.toString(), today.toString())
            } else {
                exerciseSetDao.getOneRmDataForExercises(
                    userId, exerciseIds, startDate.toString(), today.toString()
                )
            }

            if (oneRmResults.isEmpty()) {
                Timber.d("No 1RM data found for user: $userId")
                return@liftrixCatching OneRmProgressionData(exerciseProgressions = emptyList())
            }

            // Step 3: Group by exercise and fetch names
            val exerciseIdList = oneRmResults.map { it.exercise_library_id }.distinct()
            val exerciseMap = exerciseLibraryDao.getExercisesByIds(exerciseIdList)
                .associateBy { it.id }

            // Step 4: Build progressions
            val progressions = oneRmResults
                .groupBy { it.exercise_library_id }
                .map { (exerciseId, results) ->
                    var runningBestOneRm = 0f
                    var runningBestWeight = 0f
                    var runningBestReps = 0
                    ExerciseProgression(
                        exerciseId = exerciseId,
                        exerciseName = exerciseMap[exerciseId]?.name ?: "Unknown Exercise",
                        progressionPoints = results
                            .groupBy { result ->
                                Instant.fromEpochMilliseconds(result.completed_at)
                                    .toLocalDateTime(TimeZone.currentSystemDefault()).date
                            }
                            .map { (date, dayResults) ->
                                val bestSetForDay = dayResults.maxBy { it.estimated_one_rm }
                                date to bestSetForDay
                            }
                            .sortedBy { it.first }
                            .map { (date, result) ->
                                val dayOneRm = result.estimated_one_rm.toFloat()
                                if (dayOneRm >= runningBestOneRm) {
                                    runningBestOneRm = dayOneRm
                                    runningBestWeight = result.weight_kg
                                    runningBestReps = result.reps
                                }
                                OneRmDataPoint(
                                    date = date,
                                    exerciseId = exerciseId,
                                    exerciseName = exerciseMap[exerciseId]?.name ?: "",
                                    actualOneRm = null,
                                    estimatedOneRm = runningBestOneRm,
                                    weight = runningBestWeight,
                                    reps = runningBestReps,
                                    isEstimated = true
                                )
                            }
                    )
                }

            val oneRmData = OneRmProgressionData(exerciseProgressions = progressions)

            // 🚀 CRITICAL-002 Phase 3: Store in cache
            putCache(cacheKey, oneRmData, CacheTTL.ONE_RM_PROGRESSION)

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

            // 🚀 CRITICAL-002 Phase 3: Check cache first
            val cacheKey = cacheKey(userId, "FREQUENCY", timeRange.name)
            getCached<WorkoutFrequencyData>(cacheKey)?.let { return@liftrixCatching it }

            Timber.d("Getting workout frequency for user: $userId, timeRange: $timeRange")

            // Convert TimeRangeType to TimeRange
            val timeRangeObj = timeRange.toTimeRange()

            // Fetch frequency data and summary from service
            val frequencyResult = progressDataService.getFrequencyData(userId, timeRangeObj)
            val summaryResult = progressDataService.getProgressSummary(userId, timeRangeObj)

            val rawFrequencyData = frequencyResult.getOrThrow()
            val progressSummary = summaryResult.getOrThrow()

            if (rawFrequencyData.isEmpty()) {
                Timber.d("No frequency data found for user: $userId")
                return@liftrixCatching WorkoutFrequencyData(
                    totalWorkouts = 0,
                    consistencyScore = 0f,
                    frequencyPoints = emptyList(),
                    totalWorkoutDays = 0,
                    dailyAverage = 0f,
                    currentStreak = 0,
                    longestStreak = 0,
                    weeklyDistribution = emptyMap()
                )
            }

            // Convert repository FrequencyDataPoint to FrequencyPoint
            val convertedPoints = rawFrequencyData.map { dataPoint ->
                FrequencyPoint(
                    date = dataPoint.date,
                    workoutCount = dataPoint.workoutCount,
                    dayOfWeek = dataPoint.date.dayOfWeek.name
                )
            }

            // Calculate weekly distribution
            val weeklyDistribution = convertedPoints
                .groupBy { it.dayOfWeek }
                .mapValues { (_, points) -> points.sumOf { it.workoutCount } }

            // Calculate daily average from time range
            val daysInRange = timeRangeObj.getDurationInDays().coerceAtLeast(1)
            val dailyAverage = progressSummary.totalWorkouts.toFloat() / daysInRange

            // Calculate consistency score (0-100)
            val consistencyScore = if (daysInRange > 0) {
                (rawFrequencyData.count { it.workoutCount > 0 }.toFloat() / daysInRange * 100f).coerceIn(0f, 100f)
            } else 0f

            val frequencyData = WorkoutFrequencyData(
                totalWorkouts = progressSummary.totalWorkouts,
                consistencyScore = consistencyScore,
                frequencyPoints = convertedPoints,
                totalWorkoutDays = rawFrequencyData.count { it.workoutCount > 0 },
                dailyAverage = dailyAverage,
                currentStreak = progressSummary.currentStreak,
                longestStreak = progressSummary.longestStreak,
                weeklyDistribution = weeklyDistribution
            )

            // 🚀 CRITICAL-002 Phase 3: Store in cache
            putCache(cacheKey, frequencyData, CacheTTL.WORKOUT_FREQUENCY)

            Timber.d("Workout frequency retrieved - Total workouts: ${frequencyData.totalWorkouts}, consistency: ${frequencyData.consistencyScore}%")
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

            // 🚀 CRITICAL-002 Phase 3: Check cache first
            val cacheKey = cacheKey(userId, "MUSCLE_GROUP", muscleGroup?.name ?: "ALL", timeRange.name)
            getCached<MuscleGroupAnalyticsData>(cacheKey)?.let { return@liftrixCatching it }

            Timber.d("Getting muscle group analytics for user: $userId, muscleGroup: ${muscleGroup?.name ?: "ALL"}, timeRange: $timeRange")

            // Step 1: Get date range
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val startDate = when (timeRange) {
                TimeRangeType.MONTH -> today.minus(DatePeriod(months = 1))
                TimeRangeType.SIX_MONTHS -> today.minus(DatePeriod(months = 6))
                TimeRangeType.ALL_TIME -> LocalDate(2020, 1, 1)
            }

            // Step 2: Query DAO for muscle group volume data
            val muscleGroupResults = exerciseSetDao.getVolumeDataByMuscleGroup(
                userId, startDate.toString(), today.toString()
            )

            if (muscleGroupResults.isEmpty()) {
                Timber.d("No muscle group data found for user: $userId")
                return@liftrixCatching MuscleGroupAnalyticsData(muscleGroupDistribution = emptyList())
            }

            // Step 3: Calculate percentages
            val grandTotal = muscleGroupResults.sumOf { it.total_volume }

            val distribution = muscleGroupResults
                .filter { muscleGroup == null || it.primary_muscle_group == muscleGroup.name }
                .map { result ->
                    MuscleGroupDistribution(
                        muscleGroup = result.primary_muscle_group,
                        volumePercentage = if (grandTotal > 0)
                            ((result.total_volume / grandTotal) * 100).toFloat()
                        else 0f,
                        totalVolume = result.total_volume.toFloat(),
                        exerciseCount = result.exercise_count
                    )
                }

            val muscleGroupData = MuscleGroupAnalyticsData(muscleGroupDistribution = distribution)

            // 🚀 CRITICAL-002 Phase 3: Store in cache
            putCache(cacheKey, muscleGroupData, CacheTTL.MUSCLE_GROUP)

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

            // 🚀 CRITICAL-002 Phase 3: Check cache first
            val cacheKey = cacheKey(userId, "EXERCISE_RANKING", metric.name, timeRange.name, limit.toString())
            getCached<List<ExerciseRanking>>(cacheKey)?.let { return@liftrixCatching it }

            Timber.d("Getting exercise ranking for user: $userId, metric: $metric, timeRange: $timeRange, limit: $limit")

            // Step 1: Get date range
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val startDate = when (timeRange) {
                TimeRangeType.MONTH -> today.minus(DatePeriod(months = 1))
                TimeRangeType.SIX_MONTHS -> today.minus(DatePeriod(months = 6))
                TimeRangeType.ALL_TIME -> LocalDate(2020, 1, 1)
            }

            // Step 2: Query DAO for exercise rankings
            val rankingResults = exerciseSetDao.getExerciseRankings(
                userId, startDate.toString(), today.toString(), limit
            )

            if (rankingResults.isEmpty()) {
                Timber.d("No ranking data found for user: $userId")
                return@liftrixCatching emptyList<ExerciseRanking>()
            }

            // Step 3: Fetch muscle groups
            val exerciseIdList = rankingResults.map { it.exercise_library_id }
            val exerciseMap = exerciseLibraryDao.getExercisesByIds(exerciseIdList)
                .associateBy { it.id }

            // Step 4: Map to domain model with ranks
            val rankings = rankingResults.mapIndexed { index, result ->
                ExerciseRanking(
                    exerciseId = result.exercise_library_id,
                    exerciseName = result.exercise_name,
                    muscleGroup = exerciseMap[result.exercise_library_id]
                        ?.primaryMuscleGroup?.name ?: "",
                    performanceScore = result.performance_score.toFloat(),
                    rank = index + 1
                )
            }

            // 🚀 CRITICAL-002 Phase 3: Store in cache
            putCache(cacheKey, rankings, CacheTTL.EXERCISE_RANKING)

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
            val muscleGroupAnalytics = getMuscleGroupAnalytics(userId, null, timeRange).getOrThrow()
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

    /**
     * Gets widget data for dashboard display.
     *
     * **Replaces**: GetWidgetDataUseCase.getWidgetData()
     *
     * Retrieves widget-specific analytics data optimized for UI display,
     * including charts, summaries, and trend indicators.
     *
     * @param userId User identifier (must not be blank)
     * @param widgetType Type of widget requesting data
     * @return LiftrixResult containing widget data or error
     */
    suspend fun getWidgetData(
        userId: String,
        widgetType: AnalyticsWidget
    ): LiftrixResult<WidgetData> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DataRetrievalError(
                    errorMessage = "Failed to retrieve widget data for user $userId, widget $widgetType: ${throwable.message}",
                    operation = "getWidgetData",
                    retryable = true
                )
            }
        ) {
            require(userId.isNotBlank()) { "User ID cannot be blank" }

            Timber.d("Retrieving widget data for user: $userId, widgetType: $widgetType")

            // Use widget calculator factory to delegate calculation logic
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val startDate = today.minus(DatePeriod(days = 30)) // Last 30 days by default
            val endDate = today

            // Get appropriate calculator for widget type and calculate data
            val calculator = widgetCalculatorFactory.getCalculator(widgetType)
            val data = calculator.calculate(userId, startDate, endDate)

            WidgetData(
                widgetType = widgetType,
                data = data,
                lastUpdated = System.currentTimeMillis(),
                isStale = false
            )
        }
    }

    /**
     * Retrieves data for multiple widgets efficiently with parallel fetching.
     *
     * **Replaces**: GetWidgetDataUseCase.getMultipleWidgetData()
     *
     * Batch retrieval of widget data to optimize performance when loading
     * multiple widgets simultaneously on the dashboard.
     *
     * @param userId User identifier for data filtering
     * @param widgetTypes List of widget types to retrieve data for
     * @return LiftrixResult containing map of widget data or error
     */
    suspend fun getMultipleWidgetData(
        userId: String,
        widgetTypes: List<AnalyticsWidget>
    ): LiftrixResult<Map<AnalyticsWidget, WidgetData>> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DataRetrievalError(
                    errorMessage = "Failed to retrieve multiple widget data for user $userId: ${throwable.message}",
                    operation = "getMultipleWidgetData",
                    retryable = true
                )
            }
        ) {
            require(userId.isNotBlank()) { "User ID cannot be blank" }

            Timber.d("Retrieving multiple widget data for user: $userId, widgets: $widgetTypes")

            // Retrieve data for all widgets in parallel for optimal performance (PERF-001 fix)
            val deferredResults = widgetTypes.map { widgetType ->
                async {
                    widgetType to getWidgetData(userId, widgetType).getOrThrow()
                }
            }

            deferredResults.awaitAll().toMap()
        }
    }

    /**
     * Generates monthly volume calendar data for analytics dashboard.
     *
     * **Replaces**: GenerateVolumeCalendarUseCase.invoke()
     *
     * Provides volume calendar generation for monthly workout volume visualization
     * with color-coded intensity, historical volume pattern analysis, and calendar
     * widget data for dashboard display.
     *
     * @param userId User identifier (must not be blank)
     * @param year The year for calendar generation
     * @param month The month for calendar generation
     * @return LiftrixResult containing VolumeCalendarData or error
     */
    suspend fun generateVolumeCalendar(
        userId: String,
        year: Int,
        month: Month
    ): LiftrixResult<VolumeCalendarData> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.CalculationError(
                    errorMessage = "Failed to generate volume calendar: ${throwable.message}",
                    operation = "GENERATE_VOLUME_CALENDAR",
                    analyticsContext = mapOf(
                        "operation" to "generateVolumeCalendar",
                        "userId" to userId,
                        "year" to year.toString(),
                        "month" to month.name
                    )
                )
            }
        ) {
            require(userId.isNotBlank()) { "User ID cannot be blank" }

            // Validate year range
            require(year in 2020..2040) { "Year must be between 2020 and 2040" }

            // Validate future date constraint
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            require(
                year < today.year ||
                (year == today.year && month.ordinal <= today.month.ordinal)
            ) { "Cannot generate calendar for future months" }

            Timber.d("Generating volume calendar for user: $userId, year: $year, month: $month")

            // Delegate to analytics engine for calendar generation
            val calendarResult = analyticsEngine.generateVolumeCalendar(
                userId = userId,
                year = year,
                month = month
            )

            val calendarData = calendarResult.getOrThrow()
            Timber.d("Successfully generated volume calendar with ${calendarData.dailyVolumes.size} days of data")

            calendarData
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

/**
 * Exercise performance data for detailed exercise analytics (consolidation of old GetExerciseRankingUseCase).
 * Contains aggregated performance metrics for a single exercise over a time period.
 */
data class ExercisePerformanceData(
    val exerciseId: String,
    val exerciseName: String,
    val muscleGroup: String = "",
    val totalVolume: Double = 0.0,
    val totalSets: Int = 0,
    val workoutDays: Int = 0,
    val maxEstimated1RM: Double = 0.0,
    val performanceScore: Double = 0.0,
    val volumeHistory: List<PerformanceDataPoint> = emptyList(),
    val oneRmHistory: List<PerformanceDataPoint> = emptyList()
)

/**
 * Individual data point in exercise performance history.
 */
data class PerformanceDataPoint(
    val date: kotlinx.datetime.LocalDate,
    val volume: Double = 0.0,
    val weight: Double = 0.0,
    val reps: Int = 0,
    val oneRm: Double = 0.0  // For 1RM progression tracking
)

/**
 * Workout data for frequency analytics (consolidation of old GetWorkoutFrequencyAnalyticsUseCase).
 * Simplified workout representation for frequency calculations and heatmap generation.
 */
data class WorkoutData(
    val id: String,
    val date: kotlinx.datetime.LocalDate,
    val durationMinutes: Int,
    val exerciseCount: Int
)

/**
 * Detailed ranked exercise data with plateau detection and recommendations.
 * Consolidation of old GetExerciseRankingUseCase output.
 */
data class ExerciseRankingData(
    val rankedExercises: List<RankedExercise>,
    val topPerformer: RankedExercise? = null,
    val mostImproved: RankedExercise? = null,
    val needsAttention: List<RankedExercise> = emptyList(),
    val overallScore: Double = 0.0,
    val timeRange: TimeRangeType = TimeRangeType.MONTH,
    val isEmpty: Boolean = rankedExercises.isEmpty()
)

/**
 * Individual ranked exercise with full performance metrics.
 */
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

/**
 * Plateau detection status for exercises
 */
enum class PlateauStatus {
    PROGRESSING,
    STABLE,
    STAGNANT,
    DECLINING,
    INSUFFICIENT_DATA
}

/**
 * Performance trend direction for exercises
 */
enum class PerformanceTrend {
    IMPROVING,
    STABLE,
    DECLINING
}

/**
 * Widget data returned by getWidgetData() and getMultipleWidgetData().
 *
 * Contains widget-specific analytics data optimized for dashboard display.
 *
 * @property widgetType Type of widget (volume, duration, frequency, etc.)
 * @property data Raw analytics data for the widget
 * @property lastUpdated Timestamp of last data update
 * @property isStale Whether the data should be refreshed
 */
data class WidgetData(
    val widgetType: AnalyticsWidget,
    val data: Map<String, Any>,
    val lastUpdated: Long,
    val isStale: Boolean = false
)

/**
 * Extension function to convert TimeRangeType to TimeRange for service calls
 */
private fun TimeRangeType.toTimeRange(): TimeRange {
    return when (this) {
        TimeRangeType.MONTH -> TimeRange.lastMonth()
        TimeRangeType.SIX_MONTHS -> TimeRange.lastSixMonths()
        TimeRangeType.ALL_TIME -> TimeRange.allTime()
    }
}
