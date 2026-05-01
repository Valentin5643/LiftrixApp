package com.example.liftrix.domain.model

import java.time.Duration

/**
 * Domain model representing performance metrics for workouts
 */
data class PerformanceMetrics(
    val totalVolume: Weight,
    val averageIntensity: Double,
    val workoutDuration: Duration,
    val caloriesBurned: Int? = null,
    val personalRecords: Int = 0,
    val strengthScore: Double? = null,
    val enduranceScore: Double? = null
) {
    init {
        require(averageIntensity >= 0.0 && averageIntensity <= 1.0) { 
            "Average intensity must be between 0.0 and 1.0: $averageIntensity" 
        }
        require(!workoutDuration.isNegative) { 
            "Workout duration cannot be negative: $workoutDuration" 
        }
        caloriesBurned?.let { calories ->
            require(calories >= 0) { "Calories burned cannot be negative: $calories" }
        }
        require(personalRecords >= 0) { "Personal records cannot be negative: $personalRecords" }
        strengthScore?.let { score ->
            require(score >= 0.0 && score <= 100.0) { 
                "Strength score must be between 0.0 and 100.0: $score" 
            }
        }
        enduranceScore?.let { score ->
            require(score >= 0.0 && score <= 100.0) { 
                "Endurance score must be between 0.0 and 100.0: $score" 
            }
        }
    }
    
    companion object {
        val EMPTY = PerformanceMetrics(
            totalVolume = Weight.ZERO,
            averageIntensity = 0.0,
            workoutDuration = Duration.ZERO
        )
    }
} 