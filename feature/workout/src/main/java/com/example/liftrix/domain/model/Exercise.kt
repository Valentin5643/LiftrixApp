package com.example.liftrix.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

/**
 * Domain model representing an exercise within a workout with flexible metric support
 */
data class Exercise(
    val id: ExerciseId,
    val workoutId: WorkoutId,
    val libraryExercise: ExerciseLibrary,
    val orderIndex: Int,
    val targetSets: Int? = null,
    val targetReps: Int? = null,
    val targetWeight: Weight? = null,
    val targetTime: Duration? = null,
    val targetDistance: Distance? = null,
    val sets: List<ExerciseSet> = emptyList(),
    val notes: String? = null,
    val createdAt: Instant
) {
    init {
        require(orderIndex >= 0) { "Order index must be non-negative: $orderIndex" }
        require(targetSets == null || targetSets > 0) { "Target sets must be positive: $targetSets" }
        require(targetReps == null || targetReps > 0) { "Target reps must be positive: $targetReps" }
        require(sets.size <= MAX_SETS) { "Maximum $MAX_SETS sets per exercise: ${sets.size}" }
        
        notes?.let { note ->
            require(note.length <= MAX_NOTES_LENGTH) { 
                "Notes cannot exceed $MAX_NOTES_LENGTH characters: ${note.length}" 
            }
        }
        
        // Validate set numbers are sequential if sets exist
        if (sets.isNotEmpty()) {
            val setNumbers = sets.map { it.setNumber }.sorted()
            val expectedNumbers = (1..sets.size).toList()
            if (setNumbers != expectedNumbers) {
                // 🔥 SAFETY: Log the validation failure for debugging
                timber.log.Timber.e("🔥 SET-VALIDATION-ERROR: Exercise '${libraryExercise.name}' has non-sequential set numbers: $setNumbers, expected: $expectedNumbers")
                require(setNumbers == expectedNumbers) { 
                    "Set numbers must be sequential starting from 1: $setNumbers (expected: $expectedNumbers)" 
                }
            }
        }
    }
    
    companion object {
        const val MAX_NOTES_LENGTH: Int = 1000
        const val MAX_SETS: Int = 50
        
        /**
         * 🔥 SAFETY: Normalizes set numbers to be sequential starting from 1
         * This prevents crashes when set ordering is broken due to UI manipulation
         */
        fun List<ExerciseSet>.normalized(): List<ExerciseSet> = this
            .sortedBy { it.setNumber }
            .mapIndexed { index, set -> set.copy(setNumber = index + 1) }
            
        /**
         * 🔥 SAFETY: Creates an Exercise with normalized set numbers to prevent validation crashes
         * Use this instead of the constructor when set ordering might be broken
         */
        fun createSafe(
            id: ExerciseId,
            workoutId: WorkoutId,
            libraryExercise: ExerciseLibrary,
            orderIndex: Int,
            targetSets: Int? = null,
            targetReps: Int? = null,
            targetWeight: Weight? = null,
            targetTime: Duration? = null,
            targetDistance: Distance? = null,
            sets: List<ExerciseSet> = emptyList(),
            notes: String? = null,
            createdAt: Instant
        ): Exercise {
            val normalizedSets = sets.normalized()
            return Exercise(
                id = id,
                workoutId = workoutId,
                libraryExercise = libraryExercise,
                orderIndex = orderIndex,
                targetSets = targetSets,
                targetReps = targetReps,
                targetWeight = targetWeight,
                targetTime = targetTime,
                targetDistance = targetDistance,
                sets = normalizedSets,
                notes = notes,
                createdAt = createdAt
            )
        }
    }
    
    /**
     * Exercise type based on library exercise metadata
     */
    val exerciseType: ExerciseType = ExerciseType.fromLibraryExercise(libraryExercise)
    
    /**
     * Exercise capabilities based on type
     */
    val capabilities: ExerciseCapabilities = ExerciseCapabilities.fromExerciseType(exerciseType)
    
    /**
     * Exercise type detection based on equipment and movement patterns
     */
    val isWeightBased: Boolean = capabilities.supportsWeight()
    val isTimeBased: Boolean = capabilities.supportsTime()
    val isDistanceBased: Boolean = capabilities.supportsDistance()
    
    /**
     * Adds a new set to the exercise with validation
     */
    fun addSet(set: ExerciseSet): Exercise {
        validateSetCompatibility(set)
        val newSetNumber = sets.size + 1
        val newSet = set.copy(setNumber = newSetNumber)
        return copy(sets = sets + newSet)
    }
    
    /**
     * Adds a new set with weight and reps (convenience method)
     */
    fun addSet(weight: Weight?, reps: Reps?): Exercise {
        val newSet = ExerciseSet(
            id = ExerciseSetId.generate(),
            setNumber = sets.size + 1,
            reps = reps,
            weight = weight
        )
        return addSet(newSet)
    }
    
    /**
     * Removes a set by set ID
     */
    fun removeSet(setId: ExerciseSetId): Exercise {
        val updatedSets = sets.filter { it.id != setId }
            .mapIndexed { index, set -> set.copy(setNumber = index + 1) }
        return copy(sets = updatedSets)
    }
    
    /**
     * Updates a set by set number (1-indexed)
     */
    fun updateSet(setNumber: Int, updatedSet: ExerciseSet): Exercise {
        require(setNumber > 0) { "Set number must be positive: $setNumber" }
        require(setNumber <= sets.size) { "Set number $setNumber exceeds available sets: ${sets.size}" }
        
        val updatedSets = sets.mapIndexed { index, set ->
            if (set.setNumber == setNumber) updatedSet.copy(setNumber = setNumber) else set
        }
        return copy(sets = updatedSets)
    }
    
    /**
     * Validates set compatibility with exercise type
     */
    fun validateSetCompatibility(set: ExerciseSet) {
        if (!isWeightBased && set.weight != null) {
            throw IllegalArgumentException("Weight not supported for ${libraryExercise.name}")
        }
        if (!isTimeBased && set.time != null) {
            throw IllegalArgumentException("Time not supported for ${libraryExercise.name}")
        }
        if (!isDistanceBased && set.distance != null) {
            throw IllegalArgumentException("Distance not supported for ${libraryExercise.name}")
        }
    }
    
    /**
     * Calculates total volume for weight-based exercises
     */
    fun getTotalVolume(): Weight? {
        if (!isWeightBased) return null
        
        val totalVolumeKg = com.example.liftrix.domain.util.VolumeCalculator.calculateVolumeFromSets(sets)
        return com.example.liftrix.domain.util.VolumeCalculator.toWeightOrNull(totalVolumeKg)
    }
    
    /**
     * Gets the number of completed sets
     */
    fun getCompletedSetsCount(): Int = sets.count { it.isCompleted }
    
    /**
     * Checks if the exercise is completed (all sets are completed)
     */
    fun isCompleted(): Boolean = sets.isNotEmpty() && sets.all { it.isCompleted }
    
    /**
     * Gets the total reps completed across all completed sets
     */
    fun getTotalRepsCompleted(): Reps {
        return sets
            .filter { it.isCompleted }
            .map { it.reps ?: Reps.ZERO }
            .fold(Reps.ZERO) { acc, reps -> acc + reps }
    }
    
    /**
     * Gets the maximum weight used across all sets
     */
    fun getMaxWeight(): Weight? {
        if (!isWeightBased) return null
        
        return sets
            .mapNotNull { it.weight }
            .maxByOrNull { it.kilograms }
    }
    
    /**
     * Creates a template version of this exercise (without performance data)
     */
    fun toTemplate(): Exercise = copy(
        id = ExerciseId.generate(),
        sets = emptyList(),
        notes = null,
        createdAt = Instant.now()
    )
}

// ExerciseCategory moved to its own file - ExerciseCategory.kt