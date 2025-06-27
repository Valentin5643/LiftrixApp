package com.example.liftrix.domain.model

/**
 * Domain model representing an exercise within a workout template
 * Contains target values and ordering information for template exercises
 */
data class TemplateExercise(
    val exerciseId: ExerciseId,
    val name: String,
    val primaryMuscle: ExerciseCategory,
    val equipment: Equipment,
    val targetSets: Int? = null,
    val targetReps: Reps? = null,
    val targetWeight: Weight? = null,
    val restTimeSeconds: Int? = null,
    val notes: String? = null,
    val orderIndex: Int,
    val isCustomExercise: Boolean = false,
    val customExerciseId: CustomExerciseId? = null
) {
    init {
        require(name.isNotBlank()) { "Exercise name cannot be blank" }
        require(name.length <= MAX_NAME_LENGTH) { 
            "Exercise name cannot exceed $MAX_NAME_LENGTH characters: ${name.length}" 
        }
        
        targetSets?.let { sets ->
            require(sets > 0) { "Target sets must be positive: $sets" }
            require(sets <= MAX_SETS) { "Target sets cannot exceed $MAX_SETS: $sets" }
        }
        
        restTimeSeconds?.let { rest ->
            require(rest >= 0) { "Rest time cannot be negative: $rest" }
            require(rest <= MAX_REST_SECONDS) { "Rest time cannot exceed $MAX_REST_SECONDS seconds: $rest" }
        }
        
        notes?.let { note ->
            require(note.length <= MAX_NOTES_LENGTH) { 
                "Notes cannot exceed $MAX_NOTES_LENGTH characters: ${note.length}" 
            }
        }
        
        require(orderIndex >= 0) { "Order index cannot be negative: $orderIndex" }
        
        if (isCustomExercise) {
            require(customExerciseId != null) { 
                "Custom exercise ID must be provided for custom exercises" 
            }
        }
    }
    
    companion object {
        const val MAX_NAME_LENGTH: Int = 100
        const val MAX_NOTES_LENGTH: Int = 500
        const val MAX_SETS: Int = 50
        const val MAX_REST_SECONDS: Int = 1800 // 30 minutes
        
        /**
         * Creates a TemplateExercise from a standard exercise
         */
        fun fromExercise(
            exercise: Exercise,
            orderIndex: Int,
            targetSets: Int? = null,
            targetReps: Reps? = null,
            targetWeight: Weight? = null,
            restTimeSeconds: Int? = null,
            notes: String? = null
        ): TemplateExercise {
            return TemplateExercise(
                exerciseId = exercise.id,
                name = exercise.libraryExercise.name,
                primaryMuscle = exercise.libraryExercise.primaryMuscleGroup,
                equipment = Equipment.BODYWEIGHT_ONLY, // Default, should be determined from exercise
                targetSets = targetSets ?: exercise.targetSets,
                targetReps = targetReps ?: exercise.targetReps?.let { Reps(it) },
                targetWeight = targetWeight ?: exercise.targetWeight,
                restTimeSeconds = restTimeSeconds,
                notes = notes ?: exercise.notes,
                orderIndex = orderIndex,
                isCustomExercise = false,
                customExerciseId = null
            )
        }
        
        /**
         * Creates a TemplateExercise from a custom exercise
         */
        fun fromCustomExercise(
            customExercise: CustomExercise,
            orderIndex: Int,
            targetSets: Int? = null,
            targetReps: Reps? = null,
            targetWeight: Weight? = null,
            restTimeSeconds: Int? = null,
            notes: String? = null
        ): TemplateExercise {
            return TemplateExercise(
                exerciseId = ExerciseId.fromString(customExercise.id.value),
                name = customExercise.name,
                primaryMuscle = customExercise.primaryMuscle,
                equipment = customExercise.equipment,
                targetSets = targetSets,
                targetReps = targetReps,
                targetWeight = targetWeight,
                restTimeSeconds = restTimeSeconds,
                notes = notes ?: customExercise.notes,
                orderIndex = orderIndex,
                isCustomExercise = true,
                customExerciseId = customExercise.id
            )
        }
    }
    
    /**
     * Updates the target sets
     */
    fun updateTargetSets(newTargetSets: Int?): TemplateExercise {
        newTargetSets?.let { sets ->
            require(sets > 0) { "Target sets must be positive: $sets" }
            require(sets <= MAX_SETS) { "Target sets cannot exceed $MAX_SETS: $sets" }
        }
        
        return copy(targetSets = newTargetSets)
    }
    
    /**
     * Updates the target reps
     */
    fun updateTargetReps(newTargetReps: Reps?): TemplateExercise {
        return copy(targetReps = newTargetReps)
    }
    
    /**
     * Updates the target weight
     */
    fun updateTargetWeight(newTargetWeight: Weight?): TemplateExercise {
        return copy(targetWeight = newTargetWeight)
    }
    
    /**
     * Updates the rest time
     */
    fun updateRestTime(newRestTimeSeconds: Int?): TemplateExercise {
        newRestTimeSeconds?.let { rest ->
            require(rest >= 0) { "Rest time cannot be negative: $rest" }
            require(rest <= MAX_REST_SECONDS) { "Rest time cannot exceed $MAX_REST_SECONDS seconds: $rest" }
        }
        
        return copy(restTimeSeconds = newRestTimeSeconds)
    }
    
    /**
     * Updates the notes
     */
    fun updateNotes(newNotes: String?): TemplateExercise {
        val trimmedNotes = newNotes?.trim()?.takeIf { it.isNotBlank() }
        
        trimmedNotes?.let { notes ->
            require(notes.length <= MAX_NOTES_LENGTH) { 
                "Notes cannot exceed $MAX_NOTES_LENGTH characters: ${notes.length}" 
            }
        }
        
        return copy(notes = trimmedNotes)
    }
    
    /**
     * Updates the order index
     */
    fun updateOrderIndex(newOrderIndex: Int): TemplateExercise {
        require(newOrderIndex >= 0) { "Order index cannot be negative: $newOrderIndex" }
        return copy(orderIndex = newOrderIndex)
    }
    
    /**
     * Converts this template exercise to an actual exercise for a workout
     */
    fun toExercise(workoutId: WorkoutId): Exercise {
        val initialSet = ExerciseSet(
            id = ExerciseSetId.generate(),
            setNumber = 1,
            weight = targetWeight ?: Weight.ZERO,
            reps = targetReps ?: Reps.ZERO
        )
        
        return Exercise(
            id = exerciseId,
            workoutId = workoutId,
            libraryExercise = ExerciseLibrary(
                id = exerciseId.value,
                name = name,
                primaryMuscleGroup = primaryMuscle,
                equipment = equipment,
                secondaryMuscleGroups = emptyList(),
                movementPattern = "Unknown",
                difficultyLevel = 1,
                instructions = "",
                isCompound = false,
                searchableTerms = listOf(name.lowercase())
            ),
            orderIndex = orderIndex,
            targetSets = targetSets,
            targetReps = targetReps?.count,
            targetWeight = targetWeight,
            sets = listOf(initialSet),
            notes = notes,
            createdAt = java.time.Instant.now()
        )
    }
    
    /**
     * Checks if this exercise targets a specific muscle group
     */
    fun targetsMuscle(muscle: ExerciseCategory): Boolean = primaryMuscle == muscle
    
    /**
     * Checks if this exercise is compatible with specific equipment
     */
    fun isCompatibleWith(userEquipment: Set<Equipment>): Boolean = 
        userEquipment.contains(equipment)
} 