package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.analytics.OneRmDataPoint
import com.example.liftrix.domain.model.analytics.RankingMetric
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import com.example.liftrix.domain.model.analytics.VolumeDataPoint
import kotlinx.datetime.minus
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.abs

/**
 * Use case for calculating exercise performance rankings and plateau detection
 * 
 * This use case implements the performance score algorithm as defined in the spec:
 * - Performance Score = (Volume Growth % + 1RM Growth %) / 2
 * - Plateau detection over 3-week windows
 * - Multiple ranking metrics support
 * 
 * Features:
 * - Combined performance score calculation
 * - Individual metric rankings (volume growth, strength growth, etc.)
 * - Plateau detection with 3-week minimum periods
 * - Consistency analysis based on performance variance
 * - Recent trend analysis (last 4 weeks)
 * - User-scoped data access for privacy
 */
class CalculateExerciseRankingUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {

    /**
     * Calculates exercise rankings based on the specified metric
     * 
     * @param request The ranking request with metric, limit, and time range
     * @return LiftrixResult containing ranked exercises or error
     */
    suspend operator fun invoke(request: ExerciseRankingRequest): LiftrixResult<ExerciseRankingResult> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "EXERCISE_RANKING_FAILED",
                    errorMessage = "Failed to calculate exercise rankings: ${throwable.message}",
                    analyticsContext = mapOf(
                        "operation" to "calculateExerciseRanking",
                        "metric" to request.metric.name,
                        "timeRange" to request.timeRange.name
                    )
                )
            }
        ) {
            Timber.d("Calculating exercise rankings for metric: ${request.metric}")
            
            val userId = getCurrentUserIdUseCase.invoke() 
                ?: throw IllegalStateException("User not authenticated")
            val now = Clock.System.now()
            val timeZone = TimeZone.currentSystemDefault()
            
            // Calculate date range
            val endDate = now.toLocalDateTime(timeZone).date
            val startDate = when (request.timeRange) {
                TimeRangeType.MONTH -> endDate.minus(DatePeriod(days = 30))
                TimeRangeType.SIX_MONTHS -> endDate.minus(DatePeriod(days = 180))
                TimeRangeType.ALL_TIME -> LocalDate.fromEpochDays(0) // Start from epoch for all-time data
            }
            
            // Get exercise performance data
            val performanceData = workoutRepository.getExercisePerformanceData(
                userId = userId,
                startDate = startDate,
                endDate = endDate
            ).fold(
                onSuccess = { it },
                onFailure = { error -> throw RuntimeException(error.message) }
            )
            
            if (performanceData.isEmpty()) {
                Timber.d("No performance data found for user $userId in range $startDate to $endDate")
                return@liftrixCatching ExerciseRankingResult(emptyList(), request)
            }
            
            // Calculate rankings based on the requested metric
            val rankings = when (request.metric) {
                RankingMetric.PERFORMANCE_SCORE -> calculatePerformanceRankings(performanceData)
                RankingMetric.VOLUME_GROWTH -> calculateVolumeGrowthRankings(performanceData)
                RankingMetric.STRENGTH_GROWTH -> calculateStrengthGrowthRankings(performanceData)
                RankingMetric.FREQUENCY -> calculateFrequencyRankings(performanceData)
                RankingMetric.CONSISTENCY -> calculateConsistencyRankings(performanceData)
                RankingMetric.RECENT_TREND -> calculateRecentProgressRankings(performanceData)
            }
            
            // Apply limit and return results
            val limitedRankings = rankings.take(request.limit)
            
            Timber.d("Calculated ${limitedRankings.size} exercise rankings")
            ExerciseRankingResult(limitedRankings, request)
        }
    }

    /**
     * Calculates performance score rankings (combined volume + strength growth)
     */
    private fun calculatePerformanceRankings(data: List<ExercisePerformanceData>): List<ExerciseRanking> {
        return data.mapNotNull { exercise ->
            val volumeGrowth = calculateVolumeGrowthPercentage(exercise)
            val strengthGrowth = calculateStrengthGrowthPercentage(exercise)
            
            if (volumeGrowth != null && strengthGrowth != null) {
                val performanceScore = (volumeGrowth + strengthGrowth) / 2f
                val plateauRisk = detectPlateauRisk(exercise)
                
                ExerciseRanking(
                    exerciseId = exercise.exerciseId,
                    exerciseName = exercise.exerciseName,
                    score = performanceScore,
                    metric = RankingMetric.PERFORMANCE_SCORE,
                    trend = determineTrend(performanceScore),
                    plateauRisk = plateauRisk,
                    details = mapOf(
                        "volumeGrowth" to volumeGrowth,
                        "strengthGrowth" to strengthGrowth,
                        "combinedScore" to performanceScore
                    )
                )
            } else null
        }.sortedByDescending { it.score }
    }

    /**
     * Calculates volume growth percentage rankings
     */
    private fun calculateVolumeGrowthRankings(data: List<ExercisePerformanceData>): List<ExerciseRanking> {
        return data.mapNotNull { exercise ->
            val volumeGrowth = calculateVolumeGrowthPercentage(exercise)
            if (volumeGrowth != null) {
                ExerciseRanking(
                    exerciseId = exercise.exerciseId,
                    exerciseName = exercise.exerciseName,
                    score = volumeGrowth,
                    metric = RankingMetric.VOLUME_GROWTH,
                    trend = determineTrend(volumeGrowth),
                    plateauRisk = detectPlateauRisk(exercise),
                    details = mapOf("volumeGrowth" to volumeGrowth)
                )
            } else null
        }.sortedByDescending { it.score }
    }

    /**
     * Calculates strength growth percentage rankings
     */
    private fun calculateStrengthGrowthRankings(data: List<ExercisePerformanceData>): List<ExerciseRanking> {
        return data.mapNotNull { exercise ->
            val strengthGrowth = calculateStrengthGrowthPercentage(exercise)
            if (strengthGrowth != null) {
                ExerciseRanking(
                    exerciseId = exercise.exerciseId,
                    exerciseName = exercise.exerciseName,
                    score = strengthGrowth,
                    metric = RankingMetric.STRENGTH_GROWTH,
                    trend = determineTrend(strengthGrowth),
                    plateauRisk = detectPlateauRisk(exercise),
                    details = mapOf("strengthGrowth" to strengthGrowth)
                )
            } else null
        }.sortedByDescending { it.score }
    }

    /**
     * Calculates frequency rankings (sessions per week)
     */
    private fun calculateFrequencyRankings(data: List<ExercisePerformanceData>): List<ExerciseRanking> {
        return data.map { exercise ->
            val weeksInPeriod = exercise.totalDays / 7f
            val frequency = exercise.sessionCount / weeksInPeriod
            
            ExerciseRanking(
                exerciseId = exercise.exerciseId,
                exerciseName = exercise.exerciseName,
                score = frequency,
                metric = RankingMetric.FREQUENCY,
                trend = ExerciseTrend.STABLE, // Frequency doesn't have a inherent trend
                plateauRisk = false, // Frequency doesn't relate to plateau risk
                details = mapOf(
                    "sessionsPerWeek" to frequency,
                    "totalSessions" to exercise.sessionCount.toFloat()
                )
            )
        }.sortedByDescending { it.score }
    }

    /**
     * Calculates consistency rankings (lower variance is better)
     */
    private fun calculateConsistencyRankings(data: List<ExercisePerformanceData>): List<ExerciseRanking> {
        return data.map { exercise ->
            val consistency = calculateConsistencyScore(exercise)
            
            ExerciseRanking(
                exerciseId = exercise.exerciseId,
                exerciseName = exercise.exerciseName,
                score = consistency,
                metric = RankingMetric.CONSISTENCY,
                trend = ExerciseTrend.STABLE,
                plateauRisk = detectPlateauRisk(exercise),
                details = mapOf("consistencyScore" to consistency)
            )
        }.sortedByDescending { it.score } // Higher consistency score is better
    }

    /**
     * Calculates recent progress rankings (last 4 weeks)
     */
    private fun calculateRecentProgressRankings(data: List<ExercisePerformanceData>): List<ExerciseRanking> {
        return data.mapNotNull { exercise ->
            val recentProgress = calculateRecentProgressScore(exercise)
            if (recentProgress != null) {
                ExerciseRanking(
                    exerciseId = exercise.exerciseId,
                    exerciseName = exercise.exerciseName,
                    score = recentProgress,
                    metric = RankingMetric.RECENT_TREND,
                    trend = determineTrend(recentProgress),
                    plateauRisk = detectPlateauRisk(exercise),
                    details = mapOf("recentProgress" to recentProgress)
                )
            } else null
        }.sortedByDescending { it.score }
    }

    /**
     * Calculates volume growth percentage between first and last data points
     */
    private fun calculateVolumeGrowthPercentage(exercise: ExercisePerformanceData): Float? {
        if (exercise.volumeHistory.size < 2) return null
        
        val sortedHistory = exercise.volumeHistory.sortedBy { it.date }
        val firstVolume = sortedHistory.first().volume
        val lastVolume = sortedHistory.last().volume
        
        return if (firstVolume.kilograms > 0) {
            ((lastVolume.kilograms - firstVolume.kilograms) / firstVolume.kilograms * 100f).toFloat()
        } else null
    }

    /**
     * Calculates strength growth percentage between first and last 1RM estimates
     */
    private fun calculateStrengthGrowthPercentage(exercise: ExercisePerformanceData): Float? {
        if (exercise.oneRmHistory.size < 2) return null
        
        val sortedHistory = exercise.oneRmHistory.sortedBy { it.date }
        val firstOneRm = sortedHistory.first().estimatedOneRm
        val lastOneRm = sortedHistory.last().estimatedOneRm
        
        return if (firstOneRm > 0) {
            ((lastOneRm - firstOneRm) / firstOneRm) * 100f
        } else null
    }

    /**
     * Calculates consistency score based on volume variance (lower variance = higher score)
     */
    private fun calculateConsistencyScore(exercise: ExercisePerformanceData): Float {
        if (exercise.volumeHistory.size < 3) return 50f // Neutral score for insufficient data
        
        val volumes = exercise.volumeHistory.map { it.volume.kilograms.toFloat() }
        val mean = volumes.average().toFloat()
        val variance = volumes.map { (it - mean) * (it - mean) }.average().toFloat()
        val standardDeviation = kotlin.math.sqrt(variance)
        val coefficientOfVariation = if (mean > 0) (standardDeviation / mean) else 1f
        
        // Convert coefficient of variation to consistency score (0-100, where 100 is most consistent)
        return (100f - (coefficientOfVariation * 100f)).coerceIn(0f, 100f)
    }

    /**
     * Calculates recent progress score based on the last 4 weeks of data
     */
    private fun calculateRecentProgressScore(exercise: ExercisePerformanceData): Float? {
        val now = Clock.System.now()
        val timeZone = TimeZone.currentSystemDefault()
        val fourWeeksAgo = now.toLocalDateTime(timeZone).date.minus(DatePeriod(days = 28))
        
        val recentVolumeHistory = exercise.volumeHistory.filter { it.date >= fourWeeksAgo }
        val recentOneRmHistory = exercise.oneRmHistory.filter { it.date >= fourWeeksAgo }
        
        if (recentVolumeHistory.size < 2 && recentOneRmHistory.size < 2) return null
        
        var progressScore = 0f
        var scoreCount = 0
        
        // Recent volume growth
        if (recentVolumeHistory.size >= 2) {
            val sortedVolume = recentVolumeHistory.sortedBy { it.date }
            val firstVolume = sortedVolume.first().volume
            val lastVolume = sortedVolume.last().volume
            if (firstVolume.kilograms > 0) {
                val volumeGrowth = ((lastVolume.kilograms - firstVolume.kilograms) / firstVolume.kilograms * 100f).toFloat()
                progressScore += volumeGrowth
                scoreCount++
            }
        }
        
        // Recent strength growth
        if (recentOneRmHistory.size >= 2) {
            val sortedOneRm = recentOneRmHistory.sortedBy { it.date }
            val firstOneRm = sortedOneRm.first().estimatedOneRm
            val lastOneRm = sortedOneRm.last().estimatedOneRm
            if (firstOneRm > 0) {
                val strengthGrowth = ((lastOneRm - firstOneRm) / firstOneRm) * 100f
                progressScore += strengthGrowth
                scoreCount++
            }
        }
        
        return if (scoreCount > 0) progressScore / scoreCount else null
    }

    /**
     * Detects plateau risk based on 3-week windows of stagnant progress
     */
    private fun detectPlateauRisk(exercise: ExercisePerformanceData): Boolean {
        val now = Clock.System.now()
        val timeZone = TimeZone.currentSystemDefault()
        val threeWeeksAgo = now.toLocalDateTime(timeZone).date.minus(DatePeriod(days = 21))
        
        // Check recent volume and strength data
        val recentVolumeHistory = exercise.volumeHistory.filter { it.date >= threeWeeksAgo }
        val recentOneRmHistory = exercise.oneRmHistory.filter { it.date >= threeWeeksAgo }
        
        var plateauIndicators = 0
        var totalIndicators = 0
        
        // Volume plateau check
        if (recentVolumeHistory.size >= 3) {
            val volumes = recentVolumeHistory.sortedBy { it.date }.map { it.volume.kilograms.toFloat() }
            val volumeVariance = calculateVariancePercentage(volumes)
            if (volumeVariance < PLATEAU_VARIANCE_THRESHOLD) {
                plateauIndicators++
            }
            totalIndicators++
        }
        
        // Strength plateau check
        if (recentOneRmHistory.size >= 3) {
            val oneRms = recentOneRmHistory.sortedBy { it.date }.map { it.estimatedOneRm }
            val oneRmVariance = calculateVariancePercentage(oneRms)
            if (oneRmVariance < PLATEAU_VARIANCE_THRESHOLD) {
                plateauIndicators++
            }
            totalIndicators++
        }
        
        // Consider plateau risk if majority of indicators suggest stagnation
        return totalIndicators > 0 && (plateauIndicators.toFloat() / totalIndicators) >= 0.5f
    }

    /**
     * Calculates variance as percentage of mean
     */
    private fun calculateVariancePercentage(values: List<Float>): Float {
        if (values.size < 2) return 0f
        
        val mean = values.average().toFloat()
        if (mean == 0f) return 0f
        
        val variance = values.map { abs(it - mean) }.average().toFloat()
        return (variance / mean) * 100f
    }

    /**
     * Determines trend based on score value
     */
    private fun determineTrend(score: Float): ExerciseTrend {
        return when {
            score > IMPROVING_THRESHOLD -> ExerciseTrend.IMPROVING
            score < DECLINING_THRESHOLD -> ExerciseTrend.DECLINING
            else -> ExerciseTrend.STABLE
        }
    }

    companion object {
        private const val PLATEAU_VARIANCE_THRESHOLD = 5f // 5% variance threshold for plateau detection
        private const val IMPROVING_THRESHOLD = 10f // 10% growth threshold for improving trend
        private const val DECLINING_THRESHOLD = -5f // -5% threshold for declining trend
    }
}

/**
 * Request data class for exercise ranking calculations
 */
data class ExerciseRankingRequest(
    val metric: RankingMetric,
    val limit: Int = 20,
    val timeRange: TimeRangeType = TimeRangeType.SIX_MONTHS
)

/**
 * Result data class for exercise ranking calculations
 */
data class ExerciseRankingResult(
    val rankings: List<ExerciseRanking>,
    val request: ExerciseRankingRequest
)

/**
 * Individual exercise ranking result
 */
data class ExerciseRanking(
    val exerciseId: String,
    val exerciseName: String,
    val score: Float,
    val metric: RankingMetric,
    val trend: ExerciseTrend,
    val plateauRisk: Boolean,
    val details: Map<String, Float>
)

/**
 * Exercise trend enumeration
 */
enum class ExerciseTrend {
    IMPROVING,
    STABLE, 
    DECLINING
}

/**
 * Exercise performance data for calculations
 */
data class ExercisePerformanceData(
    val exerciseId: String,
    val exerciseName: String,
    val volumeHistory: List<VolumeDataPoint>,
    val oneRmHistory: List<OneRmDataPoint>,
    val sessionCount: Int,
    val totalDays: Int
)

// VolumeDataPoint moved to domain model: com.example.liftrix.domain.model.analytics.VolumeDataPoint
// Note: Use VolumeDataPoint.fromKgFloat() for Float volume conversion

// OneRmDataPoint moved to domain.model.analytics.OneRmDataPoint