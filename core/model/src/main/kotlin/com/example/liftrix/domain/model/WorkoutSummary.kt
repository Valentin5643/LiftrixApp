package com.example.liftrix.domain.model

import java.time.Duration
import java.time.LocalDate

/**
 * Lightweight domain model for efficient workout list display
 * Contains essential fields for workout history and summary views
 * Enhanced with analytics support for volume tracking and progress calculations
 */
data class WorkoutSummary(
    val id: WorkoutId,
    val userId: String,
    val name: String,
    val date: LocalDate,
    val duration: Duration?,
    val exerciseCount: Int,
    val completedSets: Int,
    val totalSets: Int,
    val status: WorkoutStatus,
    val completionPercentage: Double,
    val totalVolume: Weight
) {
    init {
        require(userId.isNotBlank()) { "WorkoutSummary must have a valid user ID" }
        require(name.isNotBlank()) { "WorkoutSummary name cannot be blank" }
        require(name.length <= MAX_NAME_LENGTH) { 
            "WorkoutSummary name cannot exceed $MAX_NAME_LENGTH characters: ${name.length}" 
        }
        require(exerciseCount >= 0) { "Exercise count cannot be negative: $exerciseCount" }
        require(completedSets >= 0) { "Completed sets cannot be negative: $completedSets" }
        require(totalSets >= 0) { "Total sets cannot be negative: $totalSets" }
        require(completedSets <= totalSets) { 
            "Completed sets ($completedSets) cannot exceed total sets ($totalSets)" 
        }
        require(completionPercentage >= 0.0 && completionPercentage <= 100.0) { 
            "Completion percentage must be between 0.0 and 100.0: $completionPercentage" 
        }
        require(totalVolume >= Weight.ZERO) { "Total volume cannot be negative: $totalVolume" }
        
        // Validate duration if present
        duration?.let { d ->
            require(!d.isNegative) { "Duration cannot be negative: $d" }
            require(d.toHours() <= MAX_WORKOUT_HOURS) { 
                "Duration cannot exceed $MAX_WORKOUT_HOURS hours: ${d.toHours()}" 
            }
        }
    }
    
    companion object {
        const val MAX_NAME_LENGTH: Int = 100
        const val MAX_WORKOUT_HOURS: Long = 8
    }
    
    /**
     * Checks if the workout is in progress
     */
    fun isInProgress(): Boolean = status == WorkoutStatus.IN_PROGRESS
    
    /**
     * Checks if the workout is completed
     */
    fun isCompleted(): Boolean = status == WorkoutStatus.COMPLETED
    
    /**
     * Checks if the workout is planned/scheduled
     */
    fun isPlanned(): Boolean = status == WorkoutStatus.PLANNED
    
    /**
     * Gets formatted duration string
     */
    fun getFormattedDuration(): String = duration?.let {
        val hours = it.toHours()
        val minutes = it.toMinutes() % 60
        when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${it.seconds}s"
        }
    } ?: "Unknown"
    
    /**
     * Calculates volume efficiency (kg per minute)
     * Used for analytics dashboard volume efficiency widgets
     */
    fun getVolumeEfficiency(): Float = duration?.let { d ->
        val durationMinutes = d.toMinutes()
        if (durationMinutes > 0) {
            (totalVolume.kilograms / durationMinutes).toFloat()
        } else 0.0f
    } ?: 0.0f
    
    /**
     * Estimates calories burned using simplified MET calculation
     * Enhanced integration with analytics calorie tracking system
     */
    fun estimateCaloriesBurned(userWeightKg: Double = 70.0): Int = duration?.let { d ->
        val durationHours = d.toMinutes() / 60.0
        val metValue = 3.5 // Standard MET value for strength training
        val caloriesBurned = (metValue * userWeightKg * durationHours).toInt()
        
        // Apply volume intensity multiplier for more accurate estimation
        val volumeMultiplier = 1.0f + (totalVolume.kilograms / 1000.0f).toFloat().coerceAtMost(0.5f)
        (caloriesBurned * volumeMultiplier).toInt()
    } ?: 0
    
    /**
     * Gets volume intensity factor for calendar color coding (0.0 to 1.0)
     * Can be used with VolumeCalendarData for consistent intensity calculations
     */
    fun getVolumeIntensityFactor(maxVolume: Weight): Float {
        return if (maxVolume.kilograms > 0.0) {
            (totalVolume.kilograms / maxVolume.kilograms).toFloat().coerceIn(0.0f, 1.0f)
        } else 0.0f
    }
    
    /**
     * Checks if workout meets high-volume threshold for analytics
     */
    fun isHighVolumeWorkout(threshold: Weight = Weight.fromKilograms(1000.0)): Boolean {
        return totalVolume >= threshold && isCompleted()
    }
}

/**
 * Converts a full Workout to WorkoutSummary for efficient display
 */
fun Workout.toSummary(): WorkoutSummary = WorkoutSummary(
    id = id,
    userId = userId,
    name = name,
    date = date,
    duration = getDuration(),
    exerciseCount = exercises.size,
    completedSets = getCompletedSets(),
    totalSets = getTotalSets(),
    status = status,
    completionPercentage = getCompletionPercentage(),
    totalVolume = calculateTotalVolume()
)