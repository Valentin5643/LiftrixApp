package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.analytics.WorkoutMetrics
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.usecase.analytics.ExercisePerformanceData
import com.example.liftrix.domain.usecase.analytics.ExerciseRanking

/**
 * Centralizes analytics calculations previously duplicated across:
 * - CalculateCaloriesUseCase
 * - CalculateExerciseRankingUseCase
 * - CalculateWorkoutMetricsUseCase
 *
 * This service provides core calculation logic that can be reused across multiple
 * use cases, ensuring consistency and reducing duplication.
 *
 * **Design Philosophy**:
 * - Pure calculation logic without repository dependencies
 * - Stateless operations for thread safety
 * - Comprehensive error handling with LiftrixResult
 * - Domain-level service following Clean Architecture principles
 *
 * **Performance Targets**:
 * - Single workout calorie calculation: <50ms
 * - Exercise ranking calculation: <200ms for 50 exercises
 * - Workout metrics calculation: <100ms
 * - One rep max calculation: <1ms
 */
interface AnalyticsCalculationService {

    /**
     * Calculates calories burned for a specific workout using MET-based algorithms.
     *
     * Uses the following formula:
     * Calories = MET × weight(kg) × duration(hours)
     *
     * @param workout Workout instance with exercises and timing data
     * @param userWeightKg User's weight in kilograms for personalized calculation
     * @return LiftrixResult containing estimated calories burned or error
     */
    suspend fun calculateCaloriesBurned(
        workout: Workout,
        userWeightKg: Double
    ): LiftrixResult<Int>

    /**
     * Calculates total calories burned for multiple workouts.
     *
     * Batch calculation optimized for performance when analyzing workout sequences
     * or calculating totals for time periods.
     *
     * @param workouts List of workout instances
     * @param userWeightKg User's weight in kilograms
     * @return LiftrixResult containing total calories burned or error
     */
    suspend fun calculateMultipleWorkoutCalories(
        workouts: List<Workout>,
        userWeightKg: Double
    ): LiftrixResult<Int>

    /**
     * Estimates calorie burn for a planned workout based on exercise selection.
     *
     * Provides calorie burn estimates for workout planning purposes using average
     * MET values for strength training exercises.
     *
     * @param exerciseCount Number of planned exercises
     * @param estimatedDurationMinutes Estimated total workout duration
     * @param userWeightKg User's weight in kilograms
     * @return LiftrixResult containing estimated calorie burn or error
     */
    suspend fun estimateWorkoutCalories(
        exerciseCount: Int,
        estimatedDurationMinutes: Int,
        userWeightKg: Double
    ): LiftrixResult<Int>

    /**
     * Calculates exercise rankings based on performance data.
     *
     * Implements the performance score algorithm:
     * Performance Score = (Volume Growth % + 1RM Growth %) / 2
     *
     * @param performanceData List of exercise performance data
     * @param limit Maximum number of rankings to return
     * @return LiftrixResult containing ranked exercises or error
     */
    suspend fun calculateExerciseRanking(
        performanceData: List<ExercisePerformanceData>,
        limit: Int = 20
    ): LiftrixResult<List<ExerciseRanking>>

    /**
     * Calculates comprehensive workout metrics.
     *
     * Provides detailed analytics including:
     * - Volume efficiency and intensity
     * - Training load assessment
     * - Quality metrics
     *
     * @param workout Workout instance to analyze
     * @return LiftrixResult containing WorkoutMetrics or error
     */
    suspend fun calculateWorkoutMetrics(
        workout: Workout
    ): LiftrixResult<WorkoutMetrics>

    /**
     * Calculates estimated one-rep maximum (1RM) using the Epley formula.
     *
     * Formula: 1RM = weight × (1 + reps/30)
     *
     * @param weight Weight lifted in kilograms
     * @param reps Number of repetitions performed
     * @return Estimated one-rep max in kilograms
     */
    fun calculateOneRepMax(weight: Double, reps: Int): Double

    /**
     * Calculates volume for a specific exercise (sets × reps × weight).
     *
     * @param sets Number of sets
     * @param reps Number of repetitions per set
     * @param weight Weight in kilograms
     * @return Total volume in kilograms
     */
    fun calculateVolume(sets: Int, reps: Int, weight: Double): Double

    /**
     * Calculates performance score combining volume and strength growth.
     *
     * @param volumeGrowthPercent Percentage growth in volume
     * @param strengthGrowthPercent Percentage growth in 1RM
     * @return Combined performance score
     */
    fun calculatePerformanceScore(
        volumeGrowthPercent: Float,
        strengthGrowthPercent: Float
    ): Float
}
