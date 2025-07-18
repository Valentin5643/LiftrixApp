package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.service.CalorieService
import com.example.liftrix.service.CalorieSummary
import com.example.liftrix.service.DailyCalorieData
import com.example.liftrix.service.WeeklyCalorieTrend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for MET-based calorie calculations and temporal aggregation.
 * 
 * This use case provides a unified interface for accessing calorie-related calculations
 * through the CalorieService abstraction layer. It handles comprehensive calorie data
 * retrieval with proper error handling and context switching for background operations.
 * 
 * Key Features:
 * - MET-based calorie calculations with user context
 * - Daily and weekly calorie data aggregation
 * - Workout-specific calorie burn estimation
 * - Temporal trend analysis and goal tracking
 * - Proper error handling with LiftrixError context
 * - Background thread execution for performance
 * 
 * Error Handling:
 * All operations return LiftrixResult<T> for consistent error handling with proper
 * context information and recovery mechanisms.
 * 
 * Usage:
 * ```
 * val calorieData = calculateCaloriesUseCase.getCalorieSummary(userId)
 * calorieData.fold(
 *     onSuccess = { summary -> updateCalorieWidget(summary) },
 *     onFailure = { error -> handleError(error) }
 * )
 * ```
 */
@Singleton
class CalculateCaloriesUseCase @Inject constructor(
    private val calorieService: CalorieService
) {
    
    /**
     * Retrieves comprehensive calorie summary for the specified user.
     * 
     * Calorie summary includes aggregated data such as total calories burned,
     * average daily calories, workout count, and trend analysis for the current period.
     * 
     * @param userId User identifier for data filtering
     * @return LiftrixResult containing calorie summary or error
     */
    suspend fun getCalorieSummary(userId: String): LiftrixResult<CalorieSummary> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.CalculationError(
                    errorMessage = "Failed to retrieve calorie summary for user $userId: ${throwable.message}",
                    operation = "getCalorieSummary"
                )
            }
        ) {
            Timber.d("Retrieving calorie summary for user: $userId")
            
            val result = calorieService.getCalorieSummary(userId)
            result.getOrThrow()
        }
    }
    
    /**
     * Retrieves daily calorie data for the specified user and time period.
     * 
     * Daily calorie data includes breakdown by workout type, intensity, and duration
     * for each day within the specified time range.
     * 
     * @param userId User identifier for data filtering
     * @param period Time range for data retrieval (LAST_7_DAYS, LAST_30_DAYS, etc.)
     * @return LiftrixResult containing list of daily calorie data or error
     */
    suspend fun getDailyCalories(
        userId: String,
        period: TimeRange
    ): LiftrixResult<List<DailyCalorieData>> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.CalculationError(
                    errorMessage = "Failed to retrieve daily calories for user $userId, period $period: ${throwable.message}",
                    operation = "getDailyCalories"
                )
            }
        ) {
            Timber.d("Retrieving daily calories for user: $userId, period: $period")
            
            val result = calorieService.getDailyCalories(userId, period)
            result.getOrThrow()
        }
    }
    
    /**
     * Retrieves weekly calorie trend data for the specified user.
     * 
     * Weekly trend data includes aggregated weekly calorie burn over the last 12 weeks,
     * with moving averages and trend analysis for long-term progress tracking.
     * 
     * @param userId User identifier for data filtering
     * @return LiftrixResult containing weekly calorie trend or error
     */
    suspend fun getWeeklyTrend(userId: String): LiftrixResult<WeeklyCalorieTrend> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.CalculationError(
                    errorMessage = "Failed to retrieve weekly calorie trend for user $userId: ${throwable.message}",
                    operation = "getWeeklyTrend"
                )
            }
        ) {
            Timber.d("Retrieving weekly calorie trend for user: $userId")
            
            val result = calorieService.getWeeklyTrend(userId)
            result.getOrThrow()
        }
    }
    
    /**
     * Calculates estimated calories burned for a specific workout.
     * 
     * Uses MET-based calculations with user profile integration to provide accurate
     * calorie burn estimates for completed workouts. Factors in exercise type,
     * duration, intensity, and user-specific parameters.
     * 
     * @param workout Workout instance with exercises and timing data
     * @return LiftrixResult containing estimated calories burned or error
     */
    suspend fun calculateWorkoutCalories(workout: Workout): LiftrixResult<Int> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.CalculationError(
                    errorMessage = "Failed to calculate calories for workout ${workout.id}: ${throwable.message}",
                    operation = "calculateWorkoutCalories"
                )
            }
        ) {
            Timber.d("Calculating calories for workout: ${workout.id}")
            
            val result = calorieService.calculateWorkoutCalories(workout)
            result.getOrThrow()
        }
    }
    
    /**
     * Calculates total calories burned for multiple workouts.
     * 
     * Batch calculation of calorie burn for multiple workouts to optimize performance
     * when analyzing workout sequences or calculating totals for time periods.
     * 
     * @param workouts List of workout instances to calculate calories for
     * @return LiftrixResult containing total calories burned or error
     */
    suspend fun calculateMultipleWorkoutCalories(
        workouts: List<Workout>
    ): LiftrixResult<Int> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.CalculationError(
                    errorMessage = "Failed to calculate calories for ${workouts.size} workouts: ${throwable.message}",
                    operation = "calculateMultipleWorkoutCalories"
                )
            }
        ) {
            Timber.d("Calculating calories for ${workouts.size} workouts")
            
            var totalCalories = 0
            
            // Calculate calories for each workout
            workouts.forEach { workout ->
                val workoutCalories = calorieService.calculateWorkoutCalories(workout).getOrThrow()
                totalCalories += workoutCalories
            }
            
            totalCalories
        }
    }
    
    /**
     * Estimates calorie burn for a planned workout based on exercise selection.
     * 
     * Provides calorie burn estimates for workout planning purposes, helping users
     * understand potential calorie expenditure before starting a workout.
     * 
     * @param exercises List of planned exercises with estimated duration
     * @param userId User identifier for personalized calculations
     * @param estimatedDurationMinutes Estimated total workout duration
     * @return LiftrixResult containing estimated calorie burn or error
     */
    suspend fun estimateWorkoutCalories(
        exercises: List<String>,
        userId: String,
        estimatedDurationMinutes: Int
    ): LiftrixResult<Int> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.CalculationError(
                    errorMessage = "Failed to estimate calories for planned workout: ${throwable.message}",
                    operation = "estimateWorkoutCalories"
                )
            }
        ) {
            Timber.d("Estimating calories for planned workout with ${exercises.size} exercises, duration: $estimatedDurationMinutes minutes")
            
            // Estimation based on average MET values for strength training
            val averageStrengthMET = 6.0 // Moderate intensity strength training
            val estimatedCaloriesPerMinute = averageStrengthMET * 3.5 * 70 / 200 // Assuming 70kg user weight
            
            val estimatedCalories = (estimatedCaloriesPerMinute * estimatedDurationMinutes).toInt()
            
            // Adjust based on exercise count complexity
            val complexityMultiplier = when {
                exercises.size <= 3 -> 0.8
                exercises.size <= 6 -> 1.0
                else -> 1.2
            }
            
            (estimatedCalories * complexityMultiplier).toInt()
        }
    }
    
    /**
     * Retrieves calorie burn comparison between different time periods.
     * 
     * Compares calorie burn metrics between current and previous time periods
     * to provide trend analysis and progress tracking capabilities.
     * 
     * @param userId User identifier for data filtering
     * @param currentPeriod Current time period to analyze
     * @param previousPeriod Previous time period for comparison
     * @return LiftrixResult containing comparison data or error
     */
    suspend fun getCalorieComparison(
        userId: String,
        currentPeriod: TimeRange,
        previousPeriod: TimeRange
    ): LiftrixResult<CalorieComparison> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.CalculationError(
                    errorMessage = "Failed to retrieve calorie comparison for user $userId: ${throwable.message}",
                    operation = "getCalorieComparison"
                )
            }
        ) {
            Timber.d("Retrieving calorie comparison for user: $userId, current: $currentPeriod, previous: $previousPeriod")
            
            val currentData = calorieService.getDailyCalories(userId, currentPeriod).getOrThrow()
            val previousData = calorieService.getDailyCalories(userId, previousPeriod).getOrThrow()
            
            val currentTotal = currentData.sumOf { it.totalCalories }
            val previousTotal = previousData.sumOf { it.totalCalories }
            
            val percentageChange = if (previousTotal > 0) {
                ((currentTotal - previousTotal).toFloat() / previousTotal.toFloat()) * 100
            } else {
                0f
            }
            
            CalorieComparison(
                currentPeriodTotal = currentTotal,
                previousPeriodTotal = previousTotal,
                percentageChange = percentageChange,
                currentPeriodAverage = if (currentData.isNotEmpty()) currentTotal / currentData.size else 0,
                previousPeriodAverage = if (previousData.isNotEmpty()) previousTotal / previousData.size else 0,
                isImprovement = percentageChange > 0
            )
        }
    }
    
    /**
     * Data class representing calorie comparison between time periods.
     */
    data class CalorieComparison(
        val currentPeriodTotal: Int,
        val previousPeriodTotal: Int,
        val percentageChange: Float,
        val currentPeriodAverage: Int,
        val previousPeriodAverage: Int,
        val isImprovement: Boolean
    )
}