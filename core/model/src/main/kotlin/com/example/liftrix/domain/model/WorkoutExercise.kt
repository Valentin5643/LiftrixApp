package com.example.liftrix.domain.model

import java.time.Instant

/**
 * Domain model representing an exercise within a workout session
 */
data class WorkoutExercise(
    val id: ExerciseId,
    val exerciseId: ExerciseId,
    val name: String,
    val sets: List<ExerciseSet> = emptyList(),
    val notes: String? = null,
    val orderIndex: Int = 0,
    val restTimerSeconds: Int? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    companion object {
        /**
         * Creates an empty WorkoutExercise for testing or initialization
         */
        fun createEmpty(): WorkoutExercise {
            return WorkoutExercise(
                id = ExerciseId.generate(),
                exerciseId = ExerciseId.generate(),
                name = "Empty Exercise"
            )
        }
    }
    
    /**
     * Checks if this exercise is completed (has at least one completed set)
     */
    fun isCompleted(): Boolean = sets.any { it.isCompleted }
    
    /**
     * Gets the number of completed sets
     */
    fun getCompletedSetsCount(): Int = sets.count { it.isCompleted }
    
    /**
     * Gets total volume for this exercise
     */
    fun getTotalVolume(): Weight? {
        val completedSets = sets.filter { it.isCompleted && it.weight != null && it.reps != null }
        return if (completedSets.isNotEmpty()) {
            completedSets.fold(Weight.ZERO) { acc, set ->
                val volume = set.getVolume()
                if (volume != null) acc + volume else acc
            }
        } else null
    }
    
    /**
     * Gets total reps completed
     */
    fun getTotalRepsCompleted(): Reps {
        return sets.filter { it.isCompleted && it.reps != null }
            .fold(Reps.ZERO) { acc, set -> acc + set.reps!! }
    }
} 