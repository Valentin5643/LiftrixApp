package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.domain.model.analytics.CalorieCalculator
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.analytics.TrendDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for calorie analytics data preparation for UI consumption
 * 
 * Provides calorie-related analytics data in formats optimized for UI display,
 * including daily calories, weekly trends, and goal comparisons.
 * 
 * Features:
 * - Daily calorie burn calculations using CalorieCalculator
 * - Weekly calorie trend analysis
 * - Monthly calorie summaries
 * - Goal tracking and comparison
 * - Optimized data formatting for chart display
 */
@Singleton
class CalorieAnalyticsUseCase @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val calorieCalculator: CalorieCalculator
) {
    
    /**
     * Data class representing daily calorie information
     */
    data class DailyCalorieData(
        val date: LocalDate,
        val caloriesBurned: Int,
        val workoutCount: Int,
        val avgCaloriesPerWorkout: Int
    )
    
    /**
     * Data class representing weekly calorie trends
     */
    data class WeeklyCalorieTrend(
        val weekStartDate: LocalDate,
        val totalCalories: Int,
        val averageDailyCalories: Int,
        val workoutDays: Int,
        val trendDirection: TrendDirection
    )
    
    /**
     * Data class representing calorie summary metrics
     */
    data class CalorieSummary(
        val totalCaloriesThisWeek: Int,
        val totalCaloriesThisMonth: Int,
        val averageDailyCalories: Int,
        val highestDayCalories: Int,
        val currentStreak: Int,
        val goalProgress: Float // 0.0 to 1.0+
    )
    
    
    /**
     * Gets today's calorie data
     */
    suspend fun getTodaysCalories(userId: String): LiftrixResult<DailyCalorieData> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.CalculationError(
                    errorMessage = "Failed to calculate today's calories: ${throwable.message}",
                    operation = "getTodaysCalories"
                )
            }
        ) {
            val today = LocalDate.now()
            val todayWorkouts = workoutDao.getWorkoutsByDateRange(
                userId = userId,
                startDate = today.toString(),
                endDate = today.toString()
            )
            
            val totalCalories = todayWorkouts.sumOf { workout ->
                try {
                    calorieCalculator.calculateWorkoutCalories(
                        exercisesJson = workout.exercisesJson,
                        durationMinutes = workout.endTime?.let { end ->
                            workout.startTime?.let { start ->
                                java.time.Duration.between(start, end).toMinutes()
                            }
                        } ?: 60,
                        userId = userId
                    )
                } catch (e: Exception) {
                    Timber.w(e, "Failed to calculate calories for workout ${workout.id}")
                    0
                }
            }
            
            DailyCalorieData(
                date = today,
                caloriesBurned = totalCalories,
                workoutCount = todayWorkouts.size,
                avgCaloriesPerWorkout = if (todayWorkouts.isNotEmpty()) {
                    totalCalories / todayWorkouts.size
                } else 0
            )
        }
    }
    
    /**
     * Gets weekly calorie trend data for charts
     */
    suspend fun getWeeklyCalorieTrend(userId: String, weeksBack: Int = 4): LiftrixResult<List<WeeklyCalorieTrend>> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.CalculationError(
                    errorMessage = "Failed to calculate weekly calorie trends: ${throwable.message}",
                    operation = "getWeeklyCalorieTrend"
                )
            }
        ) {
            val trends = mutableListOf<WeeklyCalorieTrend>()
            val today = LocalDate.now()
            
            repeat(weeksBack) { weekOffset ->
                val weekStart = today.minusWeeks(weekOffset.toLong()).with(java.time.DayOfWeek.MONDAY)
                val weekEnd = weekStart.plusDays(6)
                
                val weekWorkouts = workoutDao.getWorkoutsByDateRange(
                    userId = userId,
                    startDate = weekStart.toString(),
                    endDate = weekEnd.toString()
                )
                
                val weeklyCalories = weekWorkouts.sumOf { workout ->
                    try {
                        calorieCalculator.calculateWorkoutCalories(
                            exercisesJson = workout.exercisesJson,
                            durationMinutes = workout.endTime?.let { end ->
                                workout.startTime?.let { start ->
                                    java.time.Duration.between(start, end).toMinutes()
                                }
                            } ?: 60,
                            userId = userId
                        )
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to calculate calories for workout ${workout.id}")
                        0
                    }
                }
                
                val workoutDays = weekWorkouts.map { it.date }.distinct().size
                val avgDailyCalories = if (workoutDays > 0) weeklyCalories / workoutDays else 0
                
                val trendDirection = if (trends.isNotEmpty()) {
                    val previousWeekCalories = trends.last().totalCalories
                    when {
                        weeklyCalories > previousWeekCalories * 1.1 -> TrendDirection.UP
                        weeklyCalories < previousWeekCalories * 0.9 -> TrendDirection.DOWN
                        else -> TrendDirection.STABLE
                    }
                } else TrendDirection.STABLE
                
                trends.add(
                    WeeklyCalorieTrend(
                        weekStartDate = weekStart,
                        totalCalories = weeklyCalories,
                        averageDailyCalories = avgDailyCalories,
                        workoutDays = workoutDays,
                        trendDirection = trendDirection
                    )
                )
            }
            
            trends.reversed() // Return chronological order (oldest first)
        }
    }
    
    /**
     * Gets comprehensive calorie summary for dashboard display
     */
    suspend fun getCalorieSummary(userId: String, dailyCalorieGoal: Int = 400): LiftrixResult<CalorieSummary> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.CalculationError(
                    errorMessage = "Failed to calculate calorie summary: ${throwable.message}",
                    operation = "getCalorieSummary"
                )
            }
        ) {
            val today = LocalDate.now()
            val weekStart = today.with(java.time.DayOfWeek.MONDAY)
            val monthStart = today.withDayOfMonth(1)
            
            // This week's calories
            val thisWeekWorkouts = workoutDao.getWorkoutsByDateRange(
                userId = userId,
                startDate = weekStart.toString(),
                endDate = today.toString()
            )
            
            val thisWeekCalories = thisWeekWorkouts.sumOf { workout ->
                try {
                    calorieCalculator.calculateWorkoutCalories(
                        exercisesJson = workout.exercisesJson,
                        durationMinutes = workout.endTime?.let { end ->
                            workout.startTime?.let { start ->
                                java.time.Duration.between(start, end).toMinutes()
                            }
                        } ?: 60,
                        userId = userId
                    )
                } catch (e: Exception) {
                    0
                }
            }
            
            // This month's calories
            val thisMonthWorkouts = workoutDao.getWorkoutsByDateRange(
                userId = userId,
                startDate = monthStart.toString(),
                endDate = today.toString()
            )
            
            val thisMonthCalories = thisMonthWorkouts.sumOf { workout ->
                try {
                    calorieCalculator.calculateWorkoutCalories(
                        exercisesJson = workout.exercisesJson,
                        durationMinutes = workout.endTime?.let { end ->
                            workout.startTime?.let { start ->
                                java.time.Duration.between(start, end).toMinutes()
                            }
                        } ?: 60,
                        userId = userId
                    )
                } catch (e: Exception) {
                    0
                }
            }
            
            // Calculate daily averages and goals
            val daysInMonth = java.time.temporal.ChronoUnit.DAYS.between(monthStart, today).toInt() + 1
            val avgDailyCalories = if (daysInMonth > 0) thisMonthCalories / daysInMonth else 0
            
            // Today's calories for goal progress
            val todaysCalories = getTodaysCalories(userId).getOrNull()?.caloriesBurned ?: 0
            val goalProgress = if (dailyCalorieGoal > 0) {
                todaysCalories.toFloat() / dailyCalorieGoal.toFloat()
            } else 0f
            
            // Calculate highest day
            val dailyCaloriesThisMonth = thisMonthWorkouts.groupBy { it.date }
                .mapValues { (_, workouts) ->
                    workouts.sumOf { workout ->
                        try {
                            calorieCalculator.calculateWorkoutCalories(
                                exercisesJson = workout.exercisesJson,
                                durationMinutes = workout.endTime?.let { end ->
                                    workout.startTime?.let { start ->
                                        java.time.Duration.between(start, end).toMinutes()
                                    }
                                } ?: 60,
                                userId = userId
                            )
                        } catch (e: Exception) {
                            0
                        }
                    }
                }
            
            val highestDayCalories = dailyCaloriesThisMonth.values.maxOrNull() ?: 0
            
            // Calculate current streak (days meeting goal)
            var currentStreak = 0
            var checkDate = today
            while (currentStreak < 30) { // Max 30 days lookback
                val dayWorkouts = workoutDao.getWorkoutsByDateRange(
                    userId = userId,
                    startDate = checkDate.toString(),
                    endDate = checkDate.toString()
                )
                
                val dayCalories = dayWorkouts.sumOf { workout ->
                    try {
                        calorieCalculator.calculateWorkoutCalories(
                            exercisesJson = workout.exercisesJson,
                            durationMinutes = workout.endTime?.let { end ->
                                workout.startTime?.let { start ->
                                    java.time.Duration.between(start, end).toMinutes()
                                }
                            } ?: 60,
                            userId = userId
                        )
                    } catch (e: Exception) {
                        0
                    }
                }
                
                if (dayCalories >= dailyCalorieGoal) {
                    currentStreak++
                    checkDate = checkDate.minusDays(1)
                } else {
                    break
                }
            }
            
            CalorieSummary(
                totalCaloriesThisWeek = thisWeekCalories,
                totalCaloriesThisMonth = thisMonthCalories,
                averageDailyCalories = avgDailyCalories,
                highestDayCalories = highestDayCalories,
                currentStreak = currentStreak,
                goalProgress = goalProgress
            )
        }
    }
}