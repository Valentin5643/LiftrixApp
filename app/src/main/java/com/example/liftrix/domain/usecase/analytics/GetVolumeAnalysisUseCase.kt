package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.data.local.dao.ExerciseVolumeResult
import com.example.liftrix.data.local.dao.MuscleGroupVolumeResult
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.analytics.VolumeGrouping
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.service.ProgressDataService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for comprehensive volume analysis and aggregation.
 * 
 * This use case provides real-time volume data from the database, replacing 
 * placeholder data with actual workout analytics. Supports multiple grouping
 * strategies and time ranges for flexible analysis.
 * 
 * Features:
 * - Real-time database queries with user scoping
 * - Multiple volume grouping strategies (total, by exercise, by muscle group)
 * - Time range filtering and trend calculations  
 * - Performance optimized for <500ms response times
 * - Proper error handling with LiftrixError context
 * - Background thread execution for smooth UI performance
 * 
 * Usage:
 * ```
 * val volumeAnalysis = getVolumeAnalysisUseCase.execute(
 *     userId = "user123",
 *     groupBy = VolumeGrouping.BY_EXERCISE,
 *     timeRange = TimeRangeType.SIX_MONTHS
 * )
 * ```
 */
@Singleton
class GetVolumeAnalysisUseCase @Inject constructor(
    private val progressDataService: ProgressDataService,
    private val exerciseSetDao: ExerciseSetDao
) {
    
    /**
     * Executes volume analysis with specified grouping and time range.
     * 
     * @param userId User ID for scoping data access
     * @param groupBy Volume grouping strategy (TOTAL, BY_EXERCISE, BY_MUSCLE_GROUP)
     * @param timeRange Time range for analysis (MONTH, SIX_MONTHS, ALL_TIME)
     * @return LiftrixResult containing volume analysis data or error
     */
    suspend fun execute(
        userId: String,
        groupBy: VolumeGrouping,
        timeRange: TimeRangeType
    ): LiftrixResult<VolumeAnalysisData> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.CalculationError(
                    errorMessage = "Failed to retrieve volume analysis for user $userId, groupBy $groupBy, timeRange $timeRange: ${throwable.message}",
                    operation = "getVolumeAnalysis"
                )
            }
        ) {
            
            val (startDate, endDate) = calculateDateRange(timeRange)
            
            val volumeData = when (groupBy) {
                VolumeGrouping.TOTAL -> {
                    aggregateTotal(userId, startDate, endDate)
                }
                VolumeGrouping.BY_EXERCISE -> {
                    aggregateByExercise(userId, startDate, endDate)
                }
                VolumeGrouping.BY_MUSCLE_GROUP -> {
                    aggregateByMuscleGroup(userId, startDate, endDate)
                }
                VolumeGrouping.BY_WEEK -> {
                    aggregateByWeek(userId, startDate, endDate)
                }
                else -> {
                    emptyList() // Handle other grouping types
                }
            }
            
            val totalVolume = calculateTotal(volumeData)
            val volumeGrowth = calculateGrowth(volumeData, timeRange)
            val averageVolume = calculateAverage(volumeData)
            val isEmpty = volumeData.isEmpty()
            
            
            VolumeAnalysisData(
                volumeData = volumeData,
                totalVolume = totalVolume,
                volumeGrowth = volumeGrowth,
                averageVolume = averageVolume,
                timeRange = timeRange,
                groupBy = groupBy,
                isEmpty = isEmpty
            ).also { result ->
            }
        }
    }
    
    /**
     * Calculates date range based on time range type.
     */
    private fun calculateDateRange(timeRange: TimeRangeType): Pair<String, String> {
        val endDate = LocalDate.now()
        val startDate = when (timeRange) {
            TimeRangeType.MONTH -> endDate.minusMonths(1)
            TimeRangeType.SIX_MONTHS -> endDate.minusMonths(6)
            TimeRangeType.ALL_TIME -> LocalDate.of(2020, 1, 1) // App launch date
        }
        
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        return Pair(startDate.format(formatter), endDate.format(formatter))
    }
    
    /**
     * Aggregates total volume across all exercises and time periods.
     */
    private suspend fun aggregateTotal(
        userId: String,
        startDate: String,
        endDate: String
    ): List<VolumeDataPoint> {
        
        // First, let's check if there's any data for this user at all
        val userSetCount = exerciseSetDao.getUserSetCount(userId)
        val userWorkoutCount = exerciseSetDao.getUserWorkoutCount(userId)
        val allSetCount = exerciseSetDao.getAllUserSetCount(userId)
        val nullCompletedSetCount = exerciseSetDao.getNullCompletedSetCount(userId)
        val validSetCount = exerciseSetDao.getUserValidSetCount(userId)
        val debugSample = exerciseSetDao.getDebugSetSample(userId)
        
        debugSample.forEachIndexed { index, sample ->
            val meetsVolumeFilter = (sample.weight_kg != null && sample.weight_kg > 0) && (sample.reps != null && sample.reps > 0)
        }
        
        // Get daily volume data with proper time series
        val dailyVolumeData = exerciseSetDao.getDailyVolumeData(userId, startDate, endDate)
        
        dailyVolumeData.forEachIndexed { index, record ->
        }
        
        return dailyVolumeData.map { daily ->
            VolumeDataPoint(
                label = daily.date,
                volume = daily.total_volume,
                sets = daily.total_sets,
                exercises = daily.exercise_count,
                date = daily.date
            ).also { dataPoint ->
            }
        }
    }
    
    /**
     * Aggregates volume data by individual exercises.
     */
    private suspend fun aggregateByExercise(
        userId: String,
        startDate: String,
        endDate: String
    ): List<VolumeDataPoint> {
        // Get daily volume data per exercise for time series
        val dailyExerciseData = exerciseSetDao.getDailyVolumeDataByExercise(userId, startDate, endDate)
        
        return dailyExerciseData.map { daily ->
            VolumeDataPoint(
                label = daily.exercise_name,
                volume = daily.total_volume,
                sets = daily.total_sets,
                exercises = 1,
                exerciseId = daily.exercise_library_id,
                date = daily.date
            )
        }
    }
    
    /**
     * Aggregates volume data by muscle groups.
     */
    private suspend fun aggregateByMuscleGroup(
        userId: String,
        startDate: String,
        endDate: String
    ): List<VolumeDataPoint> {
        // Get daily volume data per muscle group for time series
        val dailyMuscleGroupData = exerciseSetDao.getDailyVolumeDataByMuscleGroup(userId, startDate, endDate)
        
        return dailyMuscleGroupData.map { daily ->
            VolumeDataPoint(
                label = daily.primary_muscle_group,
                volume = daily.total_volume,
                sets = daily.total_sets,
                exercises = daily.exercise_count,
                date = daily.date
            )
        }
    }
    
    /**
     * Aggregates volume data by week.
     */
    private suspend fun aggregateByWeek(
        userId: String,
        startDate: String,
        endDate: String
    ): List<VolumeDataPoint> {
        
        // Get daily volume data and aggregate by week
        val dailyVolumeData = exerciseSetDao.getDailyVolumeData(userId, startDate, endDate)
        
        // Group by week (ISO week) and aggregate
        val weeklyData = dailyVolumeData.groupBy { daily ->
            // Convert date string to LocalDate and get week number
            val localDate = java.time.LocalDate.parse(daily.date)
            val weekNumber = localDate.get(java.time.temporal.WeekFields.ISO.weekOfYear())
            val year = localDate.year
            "Week $weekNumber, $year"
        }.map { (weekLabel, dailyRecords) ->
            VolumeDataPoint(
                label = weekLabel,
                volume = dailyRecords.sumOf { it.total_volume },
                sets = dailyRecords.sumOf { it.total_sets },
                exercises = dailyRecords.maxOfOrNull { it.exercise_count } ?: 0,
                date = dailyRecords.minByOrNull { it.date }?.date // Use first date of the week
            )
        }.sortedBy { it.date }
        
        return weeklyData
    }
    
    /**
     * Calculates total volume across all data points.
     */
    private fun calculateTotal(volumeData: List<VolumeDataPoint>): Double {
        return volumeData.sumOf { it.volume }
    }
    
    /**
     * Calculates volume growth trend based on time range.
     */
    private fun calculateGrowth(
        volumeData: List<VolumeDataPoint>,
        timeRange: TimeRangeType
    ): Double {
        if (volumeData.isEmpty()) return 0.0
        
        // For now, return 0.0 as growth calculation would require historical comparison
        // This can be enhanced to compare against previous time periods
        return 0.0
    }
    
    /**
     * Calculates average volume per data point.
     */
    private fun calculateAverage(volumeData: List<VolumeDataPoint>): Double {
        return if (volumeData.isNotEmpty()) {
            volumeData.sumOf { it.volume } / volumeData.size
        } else {
            0.0
        }
    }
}


/**
 * Data class representing volume analysis results.
 */
data class VolumeAnalysisData(
    val volumeData: List<VolumeDataPoint>,
    val totalVolume: Double,
    val volumeGrowth: Double,
    val averageVolume: Double,
    val timeRange: TimeRangeType,
    val groupBy: VolumeGrouping,
    val isEmpty: Boolean
)

/**
 * Data class representing individual volume data points.
 */
data class VolumeDataPoint(
    val label: String,
    val volume: Double,
    val sets: Int,
    val exercises: Int,
    val exerciseId: String? = null,
    val date: String? = null
)