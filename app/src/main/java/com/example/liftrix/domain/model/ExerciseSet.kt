package com.example.liftrix.domain.model

import java.time.Instant

/**
 * Domain model representing a single set within an exercise
 */
data class ExerciseSet(
    val setNumber: Int,
    val weight: Weight,
    val reps: Reps,
    val isCompleted: Boolean = false,
    val restTimeSeconds: Int? = null,
    val notes: String? = null,
    val completedAt: Instant? = null
) {
    init {
        require(setNumber > 0) { "Set number must be positive: $setNumber" }
        if (restTimeSeconds != null) {
            require(restTimeSeconds >= 0) { "Rest time cannot be negative: $restTimeSeconds" }
            require(restTimeSeconds <= MAX_REST_TIME_SECONDS) { 
                "Rest time cannot exceed $MAX_REST_TIME_SECONDS seconds: $restTimeSeconds" 
            }
        }
        notes?.let { note ->
            require(note.length <= MAX_NOTES_LENGTH) { 
                "Notes cannot exceed $MAX_NOTES_LENGTH characters: ${note.length}" 
            }
        }
    }
    
    companion object {
        const val MAX_REST_TIME_SECONDS: Int = 3600 // 1 hour
        const val MAX_NOTES_LENGTH: Int = 500
    }
    
    /**
     * Calculates the volume (weight × reps) for this set
     */
    fun calculateVolume(): Weight = weight * reps.count.toDouble()
    
    /**
     * Marks this set as completed with current timestamp
     */
    fun markCompleted(): ExerciseSet = copy(
        isCompleted = true,
        completedAt = Instant.now()
    )
    
    /**
     * Updates the weight and reps for this set
     */
    fun updateWeightAndReps(newWeight: Weight, newReps: Reps): ExerciseSet = copy(
        weight = newWeight,
        reps = newReps
    )
} 