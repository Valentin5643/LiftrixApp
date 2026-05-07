package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.Workout

/**
 * Service interface for detecting personal records (PRs) in workouts
 * Compares workout performance against historical bests to identify achievements
 */
interface PRDetectionService {
    
    /**
     * Detects personal records in a completed workout
     * 
     * @param workout The completed workout to analyze
     * @param userId The ID of the user who completed the workout
     * @return LiftrixResult containing list of detected PRs or error
     */
    suspend fun detectPersonalRecords(
        workout: Workout,
        userId: String
    ): LiftrixResult<List<PersonalRecord>>
    
    /**
     * Checks if a specific exercise set represents a personal record
     * 
     * @param exerciseName The name of the exercise
     * @param weight The weight lifted (optional for bodyweight exercises)
     * @param reps The number of repetitions
     * @param userId The ID of the user
     * @return LiftrixResult indicating if it's a PR and previous best
     */
    suspend fun isPersonalRecord(
        exerciseName: String,
        weight: Double?,
        reps: Int,
        userId: String
    ): LiftrixResult<PRComparison>
    
    /**
     * Gets the historical best for an exercise
     * 
     * @param exerciseName The name of the exercise
     * @param userId The ID of the user
     * @param prType The type of PR to check (1RM, VOLUME, REPS)
     * @return LiftrixResult containing the historical best or null if none
     */
    suspend fun getHistoricalBest(
        exerciseName: String,
        userId: String,
        prType: PRType
    ): LiftrixResult<PersonalRecord?>
    
    /**
     * Calculates estimated 1RM from weight and reps
     * 
     * @param weight The weight lifted
     * @param reps The number of repetitions
     * @return The estimated 1 rep max
     */
    fun calculateEstimated1RM(weight: Double, reps: Int): Double
    
    /**
     * Determines the significance of a PR improvement
     * 
     * @param currentValue The new value achieved
     * @param previousBest The previous best value
     * @param prType The type of PR
     * @return The significance level of the improvement
     */
    fun calculatePRSignificance(
        currentValue: Double,
        previousBest: Double,
        prType: PRType
    ): PRSignificance
}

/**
 * Data class representing a personal record
 */
data class PersonalRecord(
    val exerciseName: String,
    val prType: PRType,
    val weight: Double?,
    val reps: Int,
    val estimatedOneRM: Double?,
    val volume: Double?, // weight * reps
    val achievedAt: Long,
    val previousBest: Double?,
    val improvementPercent: Double?
)

/**
 * Data class for comparing current performance to historical bests
 */
data class PRComparison(
    val isPersonalRecord: Boolean,
    val prType: PRType?,
    val currentValue: Double,
    val previousBest: Double?,
    val improvementPercent: Double?,
    val significance: PRSignificance
)

/**
 * Enum representing different types of personal records
 */
enum class PRType(val displayName: String) {
    ONE_RM("1 Rep Max"),
    VOLUME("Volume PR"), // weight * reps
    REPS("Rep PR"), // max reps at given weight
    MAX_WEIGHT("Max Weight") // heaviest weight lifted regardless of reps
}

/**
 * Enum representing the significance of a PR improvement
 */
enum class PRSignificance(val displayName: String, val threshold: Double) {
    MINOR("Small PR", 0.025), // 2.5% improvement
    MODERATE("Good PR", 0.05), // 5% improvement
    MAJOR("Great PR", 0.10), // 10% improvement
    EXCEPTIONAL("Amazing PR", 0.20) // 20% improvement
}