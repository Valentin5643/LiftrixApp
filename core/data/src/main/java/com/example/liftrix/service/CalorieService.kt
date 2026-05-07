package com.example.liftrix.service

import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.common.LiftrixResult
import java.util.Date

/**
 * Service interface for calorie tracking and MET-based calculations with proper data aggregation.
 * 
 * Provides a dedicated service layer for handling calorie-related operations with proper error handling
 * and temporal data aggregation. This interface abstracts the CalorieCalculator and MetDataRepository
 * layers to provide consistent calorie tracking patterns for the UI layer.
 * 
 * All operations return LiftrixResult<T> for consistent error handling throughout the application.
 * 
 * Key Responsibilities:
 * - Aggregate calorie data across time periods
 * - Calculate workout-specific calorie burn
 * - Provide daily and weekly calorie trends
 * - Handle MET-based calculations with user context
 */
interface CalorieService {
    
    /**
     * Retrieves comprehensive calorie summary for the specified user.
     * 
     * Provides aggregated calorie data including total calories burned, average daily calories,
     * workout count, and other summary metrics for the current period.
     * 
     * @param userId User identifier for data filtering
     * @return LiftrixResult containing calorie summary or error
     */
    suspend fun getCalorieSummary(userId: String): LiftrixResult<CalorieSummary>
    
    /**
     * Retrieves daily calorie data for the specified user and time period.
     * 
     * Returns a list of daily calorie burn data points within the specified time range,
     * including breakdown by workout type and intensity.
     * 
     * @param userId User identifier for data filtering
     * @param period Time range for data retrieval
     * @return LiftrixResult containing list of daily calorie data or error
     */
    suspend fun getDailyCalories(userId: String, period: TimeRange): LiftrixResult<List<DailyCalorieData>>
    
    /**
     * Retrieves weekly calorie trend data for the specified user.
     * 
     * Provides aggregated weekly calorie burn trends over the last 12 weeks,
     * including moving averages and trend analysis.
     * 
     * @param userId User identifier for data filtering
     * @return LiftrixResult containing weekly calorie trend or error
     */
    suspend fun getWeeklyTrend(userId: String): LiftrixResult<WeeklyCalorieTrend>
    
    /**
     * Calculates estimated calories burned for a specific workout.
     * 
     * Uses MET-based calculations with user profile integration to provide accurate
     * calorie burn estimates for completed workouts.
     * 
     * @param workout Workout instance with exercises and timing data
     * @return LiftrixResult containing estimated calories burned or error
     */
    suspend fun calculateWorkoutCalories(workout: Workout): LiftrixResult<Int>
}

/**
 * Data class representing comprehensive calorie summary statistics.
 * 
 * @property totalCaloriesBurned Total calories burned in the current period
 * @property averageDailyCalories Average daily calorie burn
 * @property totalWorkouts Total number of workouts completed
 * @property averageWorkoutCalories Average calories per workout
 * @property highestDailyCalories Highest single-day calorie burn
 * @property currentWeekCalories Calories burned in current week
 * @property previousWeekCalories Calories burned in previous week
 * @property weeklyTrend Percentage change from previous week
 */
data class CalorieSummary(
    val totalCaloriesBurned: Int,
    val averageDailyCalories: Int,
    val totalWorkouts: Int,
    val averageWorkoutCalories: Int,
    val highestDailyCalories: Int,
    val currentWeekCalories: Int,
    val previousWeekCalories: Int,
    val weeklyTrend: Float
)

/**
 * Data class representing daily calorie burn data.
 * 
 * @property date Date of the calorie data
 * @property totalCalories Total calories burned on this date
 * @property workoutCount Number of workouts completed
 * @property averageIntensity Average workout intensity (MET value)
 * @property topExerciseCategory Most active exercise category
 * @property durationMinutes Total workout duration in minutes
 */
data class DailyCalorieData(
    val date: Date,
    val totalCalories: Int,
    val workoutCount: Int,
    val averageIntensity: Float,
    val topExerciseCategory: String?,
    val durationMinutes: Int
)

/**
 * Data class representing weekly calorie trend analysis.
 * 
 * @property weeklyData List of weekly calorie data points for the last 12 weeks
 * @property movingAverage 4-week moving average of calorie burn
 * @property trendPercentage Overall trend percentage (positive for increasing, negative for decreasing)
 * @property peakWeek Week with highest calorie burn
 * @property lowWeek Week with lowest calorie burn
 * @property consistency Consistency score (0-100) based on variance
 */
data class WeeklyCalorieTrend(
    val weeklyData: List<WeeklyCalorieData>,
    val movingAverage: Float,
    val trendPercentage: Float,
    val peakWeek: WeeklyCalorieData?,
    val lowWeek: WeeklyCalorieData?,
    val consistency: Int
)

/**
 * Data class representing calorie data for a specific week.
 * 
 * @property weekStartDate Start date of the week
 * @property weekEndDate End date of the week
 * @property totalCalories Total calories burned in the week
 * @property workoutCount Number of workouts completed
 * @property averageDailyCalories Average daily calories for the week
 * @property mostActiveDay Day with highest calorie burn
 */
data class WeeklyCalorieData(
    val weekStartDate: Date,
    val weekEndDate: Date,
    val totalCalories: Int,
    val workoutCount: Int,
    val averageDailyCalories: Int,
    val mostActiveDay: Date?
)