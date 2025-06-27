package com.example.liftrix.domain.model

import java.time.Duration
import java.time.Instant

/**
 * Domain model representing a single set within an exercise with flexible metric support
 */
data class ExerciseSet(
    val id: ExerciseSetId,
    val setNumber: Int,
    val reps: Reps? = null,
    val weight: Weight? = null,
    val time: Duration? = null,
    val distance: Distance? = null,
    val rpe: RPE? = null,
    val completedAt: Instant? = null,
    val notes: String? = null
) {
    init {
        require(setNumber > 0) { "Set number must be positive: $setNumber" }
        require(reps == null || reps.count > 0) { "Reps must be positive: $reps" }
        require(hasAtLeastOneMetric()) { "Set must have at least one metric (reps, time, or distance)" }
        
        notes?.let { note ->
            require(note.length <= MAX_NOTES_LENGTH) { 
                "Notes cannot exceed $MAX_NOTES_LENGTH characters: ${note.length}" 
            }
        }
    }
    
    companion object {
        const val MAX_NOTES_LENGTH: Int = 500
    }
    
    /**
     * Checks if at least one metric is present
     */
    private fun hasAtLeastOneMetric(): Boolean = 
        reps != null || time != null || distance != null
    
    /**
     * Checks if the set is completed
     */
    val isCompleted: Boolean = completedAt != null
    
    /**
     * Marks this set as completed with current timestamp
     */
    fun complete(): ExerciseSet = copy(
        completedAt = Instant.now()
    )
    
    /**
     * Marks this set as completed with current timestamp (alias for complete)
     */
    fun markCompleted(): ExerciseSet = complete()
    
    /**
     * Updates weight and reps for this set
     */
    fun updateWeightAndReps(weight: Weight?, reps: Reps?): ExerciseSet = copy(
        weight = weight,
        reps = reps
    )
    
    /**
     * Calculates volume for weight-based exercises (weight × reps)
     */
    fun getVolume(): Weight? {
        return if (weight != null && reps != null) {
            weight * reps.count.toDouble()
        } else {
            null
        }
    }
} 