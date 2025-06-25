package com.example.liftrix.domain.model

import java.time.Instant

/**
 * Domain model representing an exercise within a workout
 */
data class Exercise(
    val id: ExerciseId,
    val name: String,
    val category: ExerciseCategory,
    val sets: List<ExerciseSet>,
    val notes: String? = null,
    val targetSets: Int? = null,
    val targetReps: Reps? = null,
    val targetWeight: Weight? = null,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(name.isNotBlank()) { "Exercise name cannot be blank" }
        require(name.length <= MAX_NAME_LENGTH) { 
            "Exercise name cannot exceed $MAX_NAME_LENGTH characters: ${name.length}" 
        }
        require(sets.isNotEmpty()) { "Exercise must have at least one set" }
        require(sets.size <= MAX_SETS) { "Exercise cannot have more than $MAX_SETS sets: ${sets.size}" }
        
        notes?.let { note ->
            require(note.length <= MAX_NOTES_LENGTH) { 
                "Notes cannot exceed $MAX_NOTES_LENGTH characters: ${note.length}" 
            }
        }
        
        targetSets?.let { target ->
            require(target > 0) { "Target sets must be positive: $target" }
            require(target <= MAX_SETS) { "Target sets cannot exceed $MAX_SETS: $target" }
        }
        
        // Validate set numbers are sequential
        val setNumbers = sets.map { it.setNumber }.sorted()
        require(setNumbers == (1..sets.size).toList()) { 
            "Set numbers must be sequential starting from 1: $setNumbers" 
        }
    }
    
    companion object {
        const val MAX_NAME_LENGTH: Int = 100
        const val MAX_NOTES_LENGTH: Int = 1000
        const val MAX_SETS: Int = 50
    }
    
    /**
     * Calculates total volume for all completed sets
     */
    fun calculateTotalVolume(): Weight {
        return sets
            .filter { it.isCompleted }
            .map { it.calculateVolume() }
            .fold(Weight.ZERO) { acc, volume -> acc + volume }
    }
    
    /**
     * Gets the number of completed sets
     */
    fun getCompletedSetsCount(): Int = sets.count { it.isCompleted }
    
    /**
     * Gets the total number of reps completed
     */
    fun getTotalRepsCompleted(): Reps {
        return sets
            .filter { it.isCompleted }
            .map { it.reps }
            .fold(Reps.ZERO) { acc, reps -> acc + reps }
    }
    
    /**
     * Checks if the exercise is completed (all sets are completed)
     */
    fun isCompleted(): Boolean = sets.isNotEmpty() && sets.all { it.isCompleted }
    
    /**
     * Gets the maximum weight used in any completed set
     */
    fun getMaxWeight(): Weight? {
        return sets
            .filter { it.isCompleted }
            .maxByOrNull { it.weight.kilograms }
            ?.weight
    }
    
    /**
     * Adds a new set to the exercise
     */
    fun addSet(weight: Weight, reps: Reps): Exercise {
        val newSetNumber = sets.size + 1
        val newSet = ExerciseSet(
            setNumber = newSetNumber,
            weight = weight,
            reps = reps
        )
        return copy(
            sets = sets + newSet,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Updates a specific set
     */
    fun updateSet(setNumber: Int, updatedSet: ExerciseSet): Exercise {
        require(setNumber in 1..sets.size) { "Invalid set number: $setNumber" }
        
        val updatedSets = sets.map { set ->
            if (set.setNumber == setNumber) updatedSet.copy(setNumber = setNumber)
            else set
        }
        
        return copy(
            sets = updatedSets,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Removes the last set from the exercise
     */
    fun removeLastSet(): Exercise {
        require(sets.size > 1) { "Cannot remove the last remaining set" }
        
        return copy(
            sets = sets.dropLast(1),
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Creates a template version of this exercise with all sets reset
     */
    fun toTemplate(): Exercise {
        val templateSets = sets.map { set ->
            set.copy(
                isCompleted = false,
                completedAt = null,
                restTimeSeconds = null
            )
        }
        
        return copy(
            id = ExerciseId.generate(),
            sets = templateSets,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
}

/**
 * Enum representing different exercise categories
 */
enum class ExerciseCategory(val displayName: String) {
    CHEST("Chest"),
    BACK("Back"),
    SHOULDERS("Shoulders"),
    ARMS("Arms"),
    LEGS("Legs"),
    CORE("Core"),
    CARDIO("Cardio"),
    FULL_BODY("Full Body"),
    OTHER("Other")
} 