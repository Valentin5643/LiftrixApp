package com.example.liftrix.domain.model

import java.time.Duration
import java.time.LocalDate

/**
 * Lightweight domain model for efficient workout list display
 * Contains essential fields for workout history and summary views
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
    val completionPercentage: Double
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
    completionPercentage = getCompletionPercentage()
)