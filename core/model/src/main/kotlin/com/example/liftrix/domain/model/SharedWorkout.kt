package com.example.liftrix.domain.model

import java.time.Duration
import java.time.Instant

/**
 * Domain model representing a shared workout activity for social feed display
 */
data class SharedWorkout(
    val id: String,
    val friendUserId: String,
    val friendDisplayName: String,
    val workoutName: String,
    val completedAt: Instant,
    val duration: Duration,
    val exerciseCount: Int,
    val sharedAt: Instant
) {
    init {
        require(id.isNotBlank()) { "Shared workout ID cannot be blank" }
        require(friendUserId.isNotBlank()) { "Friend user ID cannot be blank" }
        require(friendDisplayName.isNotBlank()) { "Friend display name cannot be blank" }
        require(workoutName.isNotBlank()) { "Workout name cannot be blank" }
        require(exerciseCount >= 0) { "Exercise count cannot be negative: $exerciseCount" }
        require(!duration.isNegative) { "Workout duration cannot be negative: $duration" }
        require(completedAt.isBefore(sharedAt) || completedAt == sharedAt) { 
            "Workout must be completed before or at the time it was shared: completed=$completedAt, shared=$sharedAt" 
        }
        require(sharedAt.isBefore(Instant.now().plusSeconds(60))) { 
            "Shared time cannot be in the future: $sharedAt" 
        }
        
        require(workoutName.length <= MAX_WORKOUT_NAME_LENGTH) { 
            "Workout name cannot exceed $MAX_WORKOUT_NAME_LENGTH characters: ${workoutName.length}" 
        }
        require(friendDisplayName.length <= MAX_DISPLAY_NAME_LENGTH) { 
            "Friend display name cannot exceed $MAX_DISPLAY_NAME_LENGTH characters: ${friendDisplayName.length}" 
        }
    }
    
    companion object {
        const val MAX_WORKOUT_NAME_LENGTH: Int = 100
        const val MAX_DISPLAY_NAME_LENGTH: Int = 50
    }
    
    /**
     * Gets the formatted duration for display purposes
     */
    fun getFormattedDuration(): String {
        if (duration.isZero) return "0m"
        
        val hours = duration.toHours()
        val minutes = duration.toMinutesPart()
        
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }
    
    /**
     * Gets the time since the workout was shared
     */
    fun getTimeSinceShared(): Duration = Duration.between(sharedAt, Instant.now())
    
    /**
     * Gets a formatted string representation of when the workout was shared
     */
    fun getFormattedTimeShared(): String {
        val timeSince = getTimeSinceShared()
        
        return when {
            timeSince.toMinutes() < 1 -> "Just now"
            timeSince.toMinutes() < 60 -> "${timeSince.toMinutes()}m ago"
            timeSince.toHours() < 24 -> "${timeSince.toHours()}h ago"
            else -> "${timeSince.toDays()}d ago"
        }
    }
    
    /**
     * Gets the exercise count description for display purposes
     */
    fun getExerciseCountDescription(): String {
        return when (exerciseCount) {
            0 -> "No exercises"
            1 -> "1 exercise"
            else -> "$exerciseCount exercises"
        }
    }
    
    /**
     * Checks if this is a recent share (within the last hour)
     */
    fun isRecentShare(): Boolean = getTimeSinceShared().toHours() < 1
    
    /**
     * Creates a summary description of the workout for display
     */
    fun getSummaryDescription(): String {
        return "${friendDisplayName} completed \"$workoutName\" • ${getFormattedDuration()} • ${getExerciseCountDescription()}"
    }
}