package com.example.liftrix.domain.model

import java.time.Instant

/**
 * Domain model representing a user-created custom exercise with validation and business rules
 */
data class CustomExercise(
    val id: CustomExerciseId,
    val userId: String,
    val name: String,
    val primaryMuscle: ExerciseCategory,
    val equipment: Equipment,
    val secondaryMuscles: Set<ExerciseCategory> = emptySet(),
    val difficulty: Int? = null,
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(name.isNotBlank()) { "Exercise name cannot be blank" }
        require(name.length <= MAX_NAME_LENGTH) { 
            "Exercise name cannot exceed $MAX_NAME_LENGTH characters: ${name.length}" 
        }
        
        difficulty?.let { diff ->
            require(diff in MIN_DIFFICULTY..MAX_DIFFICULTY) { 
                "Difficulty must be between $MIN_DIFFICULTY and $MAX_DIFFICULTY: $diff" 
            }
        }
        
        notes?.let { note ->
            require(note.length <= MAX_NOTES_LENGTH) { 
                "Notes cannot exceed $MAX_NOTES_LENGTH characters: ${note.length}" 
            }
        }
        
        require(!secondaryMuscles.contains(primaryMuscle)) {
            "Primary muscle group cannot be in secondary muscles: $primaryMuscle"
        }
        
        require(secondaryMuscles.size <= MAX_SECONDARY_MUSCLES) {
            "Cannot have more than $MAX_SECONDARY_MUSCLES secondary muscles: ${secondaryMuscles.size}"
        }
    }
    
    companion object {
        const val MAX_NAME_LENGTH: Int = 100
        const val MAX_NOTES_LENGTH: Int = 500
        const val MIN_DIFFICULTY: Int = 1
        const val MAX_DIFFICULTY: Int = 10
        const val MAX_SECONDARY_MUSCLES: Int = 3
        
        /**
         * Creates a new CustomExercise with validation
         */
        fun create(
            userId: String,
            name: String,
            primaryMuscle: ExerciseCategory,
            equipment: Equipment,
            secondaryMuscles: Set<ExerciseCategory> = emptySet(),
            difficulty: Int? = null,
            notes: String? = null
        ): CustomExercise {
            val now = Instant.now()
            return CustomExercise(
                id = CustomExerciseId.generate(),
                userId = userId,
                name = name.trim(),
                primaryMuscle = primaryMuscle,
                equipment = equipment,
                secondaryMuscles = secondaryMuscles,
                difficulty = difficulty,
                notes = notes?.trim()?.takeIf { it.isNotBlank() },
                createdAt = now,
                updatedAt = now
            )
        }
    }
    
    /**
     * Updates the exercise name with validation
     */
    fun updateName(newName: String): CustomExercise {
        require(newName.isNotBlank()) { "Exercise name cannot be blank" }
        require(newName.trim().length <= MAX_NAME_LENGTH) { 
            "Exercise name cannot exceed $MAX_NAME_LENGTH characters" 
        }
        
        return copy(
            name = newName.trim(),
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Adds a secondary muscle group with validation
     */
    fun addSecondaryMuscle(muscle: ExerciseCategory): CustomExercise {
        require(muscle != primaryMuscle) { 
            "Cannot add primary muscle as secondary: $muscle" 
        }
        require(!secondaryMuscles.contains(muscle)) { 
            "Secondary muscle already exists: $muscle" 
        }
        require(secondaryMuscles.size < MAX_SECONDARY_MUSCLES) { 
            "Cannot add more than $MAX_SECONDARY_MUSCLES secondary muscles" 
        }
        
        return copy(
            secondaryMuscles = secondaryMuscles + muscle,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Removes a secondary muscle group
     */
    fun removeSecondaryMuscle(muscle: ExerciseCategory): CustomExercise {
        require(secondaryMuscles.contains(muscle)) { 
            "Secondary muscle does not exist: $muscle" 
        }
        
        return copy(
            secondaryMuscles = secondaryMuscles - muscle,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Updates the difficulty level with validation
     */
    fun updateDifficulty(newDifficulty: Int?): CustomExercise {
        newDifficulty?.let { diff ->
            require(diff in MIN_DIFFICULTY..MAX_DIFFICULTY) { 
                "Difficulty must be between $MIN_DIFFICULTY and $MAX_DIFFICULTY: $diff" 
            }
        }
        
        return copy(
            difficulty = newDifficulty,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Updates the notes with validation
     */
    fun updateNotes(newNotes: String?): CustomExercise {
        val trimmedNotes = newNotes?.trim()?.takeIf { it.isNotBlank() }
        
        trimmedNotes?.let { notes ->
            require(notes.length <= MAX_NOTES_LENGTH) { 
                "Notes cannot exceed $MAX_NOTES_LENGTH characters: ${notes.length}" 
            }
        }
        
        return copy(
            notes = trimmedNotes,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Updates the equipment type
     */
    fun updateEquipment(newEquipment: Equipment): CustomExercise {
        return copy(
            equipment = newEquipment,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Gets all muscle groups (primary + secondary)
     */
    fun getAllMuscles(): Set<ExerciseCategory> = setOf(primaryMuscle) + secondaryMuscles
    
    /**
     * Checks if this exercise targets a specific muscle group
     */
    fun targetsMuscle(muscle: ExerciseCategory): Boolean = 
        primaryMuscle == muscle || secondaryMuscles.contains(muscle)
    
    /**
     * Checks if this exercise is suitable for specific equipment
     */
    fun isCompatibleWith(userEquipment: Set<Equipment>): Boolean = 
        userEquipment.contains(equipment)
} 