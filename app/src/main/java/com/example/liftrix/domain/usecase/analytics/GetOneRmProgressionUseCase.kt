package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.data.local.dao.OneRmResult
import com.example.liftrix.domain.model.analytics.ExerciseProgression
import com.example.liftrix.domain.model.analytics.OneRmDataPoint
import com.example.liftrix.domain.model.analytics.OverallProgressionPoint
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.ProgressStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.*
import timber.log.Timber
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Use case for calculating and tracking 1RM progression over time.
 * 
 * This use case provides real-time 1RM calculations from actual workout data,
 * replacing mock data with database-driven analytics. Uses the Epley formula
 * for estimated 1RM calculations and supports filtering by exercises and time ranges.
 * 
 * Features:
 * - Epley formula for 1RM estimation: weight * (1 + reps/30)
 * - Real-time database queries with user scoping
 * - Exercise filtering and selection
 * - Time range analysis with progression tracking
 * - Performance optimized for <500ms response times
 * - Proper error handling with LiftrixError context
 * - Support for both estimated and actual 1RM values
 * 
 * Usage:
 * ```
 * val oneRmProgression = getOneRmProgressionUseCase.execute(
 *     userId = "user123",
 *     exerciseIds = listOf("bench_press", "squat"),
 *     timeRange = TimeRangeType.SIX_MONTHS,
 *     includeEstimated = true
 * )
 * ```
 */
@Singleton
class GetOneRmProgressionUseCase @Inject constructor(
    private val exerciseSetDao: ExerciseSetDao,
    private val progressStatsRepository: ProgressStatsRepository
) {
    
    /**
     * Executes 1RM progression analysis for specified exercises and time range.
     * 
     * @param userId User ID for scoping data access
     * @param exerciseIds List of exercise library IDs to analyze (null for all exercises)
     * @param timeRange Time range for analysis (MONTH, SIX_MONTHS, ALL_TIME)
     * @param includeEstimated Whether to include estimated 1RM values from Epley formula
     * @return LiftrixResult containing 1RM progression data or error
     */
    suspend fun execute(
        userId: String,
        exerciseIds: List<String>? = null,
        timeRange: TimeRangeType,
        includeEstimated: Boolean = true
    ): LiftrixResult<OneRmProgressionData> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "ONE_RM_PROGRESSION_FAILED", 
                    errorMessage = "Failed to retrieve 1RM progression for user $userId, exercises ${exerciseIds?.size ?: "all"}, timeRange $timeRange: ${throwable.message}",
                    analyticsContext = mapOf(
                        "operation" to "getOneRmProgression",
                        "userId" to userId,
                        "exerciseCount" to (exerciseIds?.size?.toString() ?: "ALL"),
                        "timeRange" to timeRange.name
                    )
                )
            }
        ) {
            // Validate user ID to prevent data leakage
            if (userId.isBlank()) {
                return@liftrixCatching OneRmProgressionData(
                    exerciseProgressions = emptyList(),
                    overallProgression = emptyList(),
                    timeRange = timeRange,
                    isEmpty = true
                )
            }
            
            Timber.d("Retrieving 1RM progression for user: $userId, exercises: ${exerciseIds?.size ?: "all"}, timeRange: $timeRange")
            
            val (startDate, endDate) = calculateDateRange(timeRange)
            
            // Get 1RM data based on whether specific exercises were requested
            val oneRmData = if (exerciseIds.isNullOrEmpty()) {
                // Get all 1RM data for user when no specific exercises requested
                exerciseSetDao.getAllOneRmData(userId, startDate, endDate)
            } else {
                // Get 1RM data for specific exercises
                exerciseSetDao.getOneRmDataForExercises(userId, exerciseIds, startDate, endDate)
            }
            
            val exerciseProgressions = buildExerciseProgressions(oneRmData, includeEstimated)
            val overallProgression = buildOverallProgression(oneRmData, includeEstimated)
            
            OneRmProgressionData(
                exerciseProgressions = exerciseProgressions,
                overallProgression = overallProgression,
                timeRange = timeRange,
                isEmpty = exerciseProgressions.isEmpty()
            )
        }
    }
    
    /**
     * Calculates date range based on time range type.
     */
    private fun calculateDateRange(timeRange: TimeRangeType): Pair<String, String> {
        val endDate = kotlinx.datetime.Clock.System.todayIn(TimeZone.currentSystemDefault())
        val startDate = when (timeRange) {
            TimeRangeType.MONTH -> endDate.minus(DatePeriod(months = 1))
            TimeRangeType.SIX_MONTHS -> endDate.minus(DatePeriod(months = 6))
            TimeRangeType.ALL_TIME -> LocalDate(2020, 1, 1) // App launch date
        }
        
        return Pair(startDate.toString(), endDate.toString())
    }
    
    
    /**
     * Builds individual exercise progressions from raw 1RM data.
     */
    private fun buildExerciseProgressions(
        oneRmData: List<OneRmResult>,
        includeEstimated: Boolean
    ): List<ExerciseProgression> {
        return oneRmData
            .groupBy { it.exercise_library_id }
            .mapNotNull { (exerciseId, exerciseData) ->
                if (exerciseData.isEmpty()) return@mapNotNull null
                
                val dataPoints = exerciseData
                    .sortedBy { it.completed_at }
                    .mapNotNull { result ->
                        // Filter based on includeEstimated flag
                        if (!includeEstimated && result.reps != 1) {
                            null // Skip estimated values when includeEstimated is false
                        } else {
                            OneRmDataPoint(
                                date = Instant.fromEpochMilliseconds(result.completed_at)
                                    .toLocalDateTime(TimeZone.currentSystemDefault())
                                    .date,
                                actualOneRm = if (result.reps == 1) result.weight_kg else null,
                                estimatedOneRm = result.estimated_one_rm.toFloat(),
                                weight = result.weight_kg,
                                reps = result.reps,
                                isEstimated = result.reps != 1
                            )
                        }
                    }
                
                val currentMax = dataPoints.maxOfOrNull { point: OneRmDataPoint ->
                    point.bestOneRm
                } ?: 0f
                
                val previousMax = if (dataPoints.size > 1) {
                    dataPoints.dropLast(dataPoints.size / 2).maxOfOrNull { point: OneRmDataPoint ->
                        point.bestOneRm 
                    } ?: 0f
                } else 0f
                
                val progression = if (previousMax > 0) {
                    ((currentMax - previousMax) / previousMax * 100)
                } else 0f
                
                ExerciseProgression(
                    exerciseId = exerciseId,
                    dataPoints = dataPoints,
                    currentMax = currentMax,
                    progression = progression,
                    bestSet = dataPoints.maxByOrNull { 
                        it.bestOneRm
                    }
                )
            }
    }
    
    /**
     * Builds overall progression trend across all exercises.
     */
    private fun buildOverallProgression(
        oneRmData: List<OneRmResult>,
        includeEstimated: Boolean
    ): List<OverallProgressionPoint> {
        if (oneRmData.isEmpty()) return emptyList()
        
        return oneRmData
            .sortedBy { it.completed_at }
            .groupBy { 
                // Group by week to create progression timeline
                val instant = Instant.fromEpochMilliseconds(it.completed_at)
                val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
                date.minus(DatePeriod(days = date.dayOfWeek.ordinal)) // Start of week
            }
            .map { (weekStart, weekData) ->
                val averageOneRm = weekData.mapNotNull { result ->
                    val value: Float? = if (includeEstimated) result.estimated_one_rm.toFloat() else {
                        if (result.reps == 1) result.weight_kg else null
                    }
                    value
                }.average().toFloat()
                
                val maxOneRm = weekData.mapNotNull { result ->
                    val value: Float? = if (includeEstimated) result.estimated_one_rm.toFloat() else {
                        if (result.reps == 1) result.weight_kg else null
                    }
                    value
                }.maxOrNull() ?: 0f
                
                OverallProgressionPoint(
                    date = weekStart,
                    averageOneRm = averageOneRm,
                    maxOneRm = maxOneRm,
                    exerciseCount = weekData.map { it.exercise_library_id }.distinct().size
                )
            }
    }
}

/**
 * Data class representing complete 1RM progression analysis.
 */
data class OneRmProgressionData(
    val exerciseProgressions: List<ExerciseProgression>,
    val overallProgression: List<OverallProgressionPoint>,
    val timeRange: TimeRangeType,
    val isEmpty: Boolean
)

// ExerciseProgression moved to domain.model.analytics.ExerciseProgression

// OneRmDataPoint moved to domain.model.analytics.OneRmDataPoint

// OverallProgressionPoint moved to domain.model.analytics.OverallProgressionPoint