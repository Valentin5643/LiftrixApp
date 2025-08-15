package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.analytics.AnalyticsTimeRange
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for retrieving workout frequency analytics data
 * 
 * Provides comprehensive frequency analysis including:
 * - Daily workout patterns
 * - Weekly workout distribution
 * - Consistency scoring
 * - Monthly trends
 * - Workout streaks
 * 
 * This use case aggregates workout data over specified time ranges and
 * calculates various frequency metrics for the progress dashboard.
 */
@Singleton
class GetWorkoutFrequencyAnalyticsUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository
) {
    
    /**
     * Execute the use case to retrieve workout frequency analytics
     * 
     * @param userId The ID of the user to get analytics for
     * @param timeRange The time range for the analysis
     * @return Flow of workout frequency data wrapped in LiftrixResult
     */
    suspend fun execute(
        userId: String,
        timeRange: TimeRangeType
    ): Flow<LiftrixResult<WorkoutFrequencyData>> = flow {
        Timber.d("Getting workout frequency analytics for user: $userId, timeRange: $timeRange")
        
        emit(liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DataRetrievalError(
                    errorMessage = "Failed to retrieve workout frequency analytics: ${throwable.message}"
                )
            }
        ) {
            val domainTimeRange = TimeRange.fromType(timeRange)
            val startDate = domainTimeRange.startDate
            val endDate = domainTimeRange.endDate
            
            // Get workouts within the time range
            val workouts = workoutRepository.getWorkoutsInDateRange(
                userId = userId,
                startDate = startDate,
                endDate = endDate
            )
            
            // Calculate frequency metrics
            val frequencyPoints = calculateFrequencyPoints(workouts, startDate, endDate)
            val dailyAverage = calculateDailyAverage(workouts, startDate, endDate)
            val weeklyDistribution = calculateWeeklyDistribution(workouts)
            val consistencyScore = calculateConsistencyScore(workouts, startDate, endDate)
            val currentStreak = calculateCurrentStreak(workouts)
            val longestStreak = calculateLongestStreak(workouts)
            val totalWorkoutDays = workouts.map { it.date }.distinct().size
            
            WorkoutFrequencyData(
                frequencyPoints = frequencyPoints,
                dailyAverage = dailyAverage,
                weeklyDistribution = weeklyDistribution,
                consistencyScore = consistencyScore,
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                totalWorkoutDays = totalWorkoutDays,
                timeRange = timeRange
            )
        })
    }.catch { throwable ->
        Timber.e(throwable, "Error in GetWorkoutFrequencyAnalyticsUseCase")
        emit(LiftrixResult.failure(
            LiftrixError.DataRetrievalError(
                errorMessage = "Failed to retrieve workout frequency analytics: ${throwable.message}"
            )
        ))
    }
    
    /**
     * Calculate frequency data points for charting
     */
    private fun calculateFrequencyPoints(
        workouts: List<WorkoutData>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<WorkoutFrequencyDataPoint> {
        val workoutsByDate = workouts.groupBy { it.date }
        val points = mutableListOf<WorkoutFrequencyDataPoint>()
        
        var currentDate = startDate
        while (currentDate <= endDate) {
            val dayWorkouts = workoutsByDate[currentDate] ?: emptyList()
            points.add(
                WorkoutFrequencyDataPoint(
                    date = currentDate,
                    dayOfWeek = currentDate.dayOfWeek.name,
                    workoutCount = dayWorkouts.size,
                    durationMinutes = dayWorkouts.sumOf { it.durationMinutes },
                    consistencyScore = 0f // Will be calculated separately
                )
            )
            currentDate = currentDate.plus(DatePeriod(days = 1))
        }
        
        return points
    }
    
    /**
     * Calculate daily average workouts
     */
    private fun calculateDailyAverage(
        workouts: List<WorkoutData>,
        startDate: LocalDate,
        endDate: LocalDate
    ): Float {
        val totalDays = startDate.daysUntil(endDate) + 1
        return if (totalDays > 0) {
            workouts.size.toFloat() / totalDays
        } else {
            0f
        }
    }
    
    /**
     * Calculate weekly distribution of workouts
     */
    private fun calculateWeeklyDistribution(workouts: List<WorkoutData>): Map<DayOfWeek, Int> {
        return workouts.groupBy { it.date.dayOfWeek }
            .mapValues { it.value.size }
            .withDefault { 0 }
    }
    
    /**
     * Calculate consistency score (0-1 scale)
     */
    private fun calculateConsistencyScore(
        workouts: List<WorkoutData>,
        startDate: LocalDate,
        endDate: LocalDate
    ): Float {
        val totalDays = startDate.daysUntil(endDate) + 1
        val workoutDays = workouts.map { it.date }.distinct().size
        
        // Score based on workout frequency (target: 3-5 days per week)
        val weeksInRange = totalDays / 7.0
        val averageWorkoutsPerWeek = if (weeksInRange > 0) workoutDays / weeksInRange else 0.0
        
        return when {
            averageWorkoutsPerWeek >= 3 && averageWorkoutsPerWeek <= 5 -> 1.0f
            averageWorkoutsPerWeek >= 2 && averageWorkoutsPerWeek < 3 -> 0.7f
            averageWorkoutsPerWeek >= 5 && averageWorkoutsPerWeek <= 6 -> 0.85f
            averageWorkoutsPerWeek >= 1 && averageWorkoutsPerWeek < 2 -> 0.4f
            averageWorkoutsPerWeek > 6 -> 0.6f // Too many, risk of overtraining
            else -> 0.2f
        }
    }
    
    /**
     * Calculate current workout streak
     */
    private fun calculateCurrentStreak(workouts: List<WorkoutData>): Int {
        if (workouts.isEmpty()) return 0
        
        val sortedDates = workouts.map { it.date }.distinct().sorted()
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        var streak = 0
        var currentDate = today
        
        // Check if there was a workout today or yesterday (allow 1 day gap)
        if (sortedDates.lastOrNull()?.let { it >= today.minus(DatePeriod(days = 1)) } != true) {
            return 0
        }
        
        // Count backwards from today
        for (date in sortedDates.reversed()) {
            val daysDiff = currentDate.minus(date).days
            if (daysDiff <= 1) {
                streak++
                currentDate = date
            } else {
                break
            }
        }
        
        return streak
    }
    
    /**
     * Calculate longest workout streak
     */
    private fun calculateLongestStreak(workouts: List<WorkoutData>): Int {
        if (workouts.isEmpty()) return 0
        
        val sortedDates = workouts.map { it.date }.distinct().sorted()
        var longestStreak = 1
        var currentStreak = 1
        
        for (i in 1 until sortedDates.size) {
            val daysDiff = sortedDates[i].minus(sortedDates[i - 1]).days
            if (daysDiff == 1) {
                currentStreak++
                longestStreak = maxOf(longestStreak, currentStreak)
            } else {
                currentStreak = 1
            }
        }
        
        return longestStreak
    }
    
    /**
     * Helper function to calculate days between two dates
     */
    private fun LocalDate.daysUntil(other: LocalDate): Int {
        return other.toEpochDays() - this.toEpochDays()
    }
}

/**
 * Data class representing workout frequency analytics
 */
data class WorkoutFrequencyData(
    val frequencyPoints: List<WorkoutFrequencyDataPoint>,
    val dailyAverage: Float,
    val weeklyDistribution: Map<DayOfWeek, Int>,
    val consistencyScore: Float,
    val currentStreak: Int,
    val longestStreak: Int,
    val totalWorkoutDays: Int,
    val timeRange: TimeRangeType
)

/**
 * Simplified workout data for frequency calculations
 */
data class WorkoutData(
    val id: String,
    val date: LocalDate,
    val durationMinutes: Int,
    val exerciseCount: Int
)