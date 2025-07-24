package com.example.liftrix.domain.model

import kotlinx.serialization.Serializable

/**
 * Domain model representing an exercise within a workout template
 * Contains target values and ordering information for template exercises
 */
@Serializable
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
                equipment = exercise.libraryExercise.equipment,
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
        // Create appropriate number of sets based on targetSets property
        val initialSets = (1..(targetSets ?: 1)).map { setNumber ->
            ExerciseSet(
                id = ExerciseSetId.generate(),
                setNumber = setNumber,
                weight = targetWeight ?: Weight.ZERO,
                reps = targetReps ?: Reps.ZERO
            )
        }
        
        return Exercise(
            id = exerciseId,
            workoutId = workoutId,
            libraryExercise = ExerciseLibrary(
                id = exerciseId.value,
                name = name,
                primaryMuscleGroup = primaryMuscle,
                equipment = equipment,
                secondaryMuscleGroups = inferSecondaryMuscleGroups(name, primaryMuscle),
                movementPattern = inferMovementPattern(name, equipment),
                difficultyLevel = inferDifficultyLevel(name, equipment),
                instructions = notes ?: inferInstructions(name, equipment),
                isCompound = inferIsCompound(name, primaryMuscle),
                searchableTerms = listOf(name.lowercase()) + inferSearchTerms(name)
            ),
            orderIndex = orderIndex,
            targetSets = targetSets,
            targetReps = targetReps?.count,
            targetWeight = targetWeight,
            sets = initialSets,
            notes = notes,
            createdAt = java.time.Instant.now()
        )
    }
    
    /**
     * Infers secondary muscle groups based on exercise name and primary muscle
     */
    private fun inferSecondaryMuscleGroups(exerciseName: String, primary: ExerciseCategory): List<ExerciseCategory> {
        val name = exerciseName.lowercase()
        return when {
            name.contains("bench") && primary == ExerciseCategory.CHEST -> listOf(ExerciseCategory.SHOULDERS, ExerciseCategory.TRICEPS)
            name.contains("squat") && primary == ExerciseCategory.LEGS -> listOf(ExerciseCategory.GLUTES, ExerciseCategory.CORE)
            name.contains("deadlift") && primary == ExerciseCategory.BACK -> listOf(ExerciseCategory.LEGS, ExerciseCategory.GLUTES)
            name.contains("pull") && primary == ExerciseCategory.BACK -> listOf(ExerciseCategory.BICEPS)
            name.contains("press") && primary == ExerciseCategory.SHOULDERS -> listOf(ExerciseCategory.TRICEPS)
            name.contains("row") && primary == ExerciseCategory.BACK -> listOf(ExerciseCategory.BICEPS)
            else -> emptyList()
        }
    }
    
    /**
     * Infers movement pattern based on exercise name and equipment
     */
    private fun inferMovementPattern(exerciseName: String, equipment: Equipment): String {
        val name = exerciseName.lowercase()
        return when {
            name.contains("squat") -> "Squat"
            name.contains("deadlift") -> "Hinge"
            name.contains("bench") || name.contains("press") -> "Push"
            name.contains("pull") || name.contains("row") -> "Pull"
            name.contains("curl") -> "Isolation"
            name.contains("lunge") -> "Lunge"
            name.contains("plank") || name.contains("hold") -> "Hold"
            name.contains("run") || name.contains("walk") -> "Cardio"
            equipment == Equipment.TREADMILL || equipment == Equipment.EXERCISE_BIKE -> "Cardio"
            else -> "Compound"
        }
    }
    
    /**
     * Infers difficulty level based on exercise complexity
     */
    private fun inferDifficultyLevel(exerciseName: String, equipment: Equipment): Int {
        val name = exerciseName.lowercase()
        return when {
            name.contains("deadlift") || name.contains("snatch") || name.contains("clean") -> 5
            name.contains("squat") || name.contains("bench") -> 4
            name.contains("press") || name.contains("row") -> 3
            name.contains("curl") || name.contains("extension") -> 2
            equipment == Equipment.BODYWEIGHT_ONLY -> 2
            else -> 3
        }
    }
    
    /**
     * Infers basic instructions based on exercise type
     */
    private fun inferInstructions(exerciseName: String, equipment: Equipment): String {
        val name = exerciseName.lowercase()
        return when {
            name.contains("squat") -> "Stand with feet shoulder-width apart, lower your body as if sitting back into a chair, then return to standing."
            name.contains("bench") -> "Lie on bench, grip bar slightly wider than shoulders, lower to chest, then press up."
            name.contains("deadlift") -> "Stand with feet hip-width apart, bend at hips and knees to grip bar, stand up by driving through heels."
            name.contains("curl") -> "Hold weight with arms extended, curl by flexing biceps, then lower with control."
            name.contains("press") -> "Press weight overhead in a controlled motion, then lower with control."
            name.contains("row") -> "Pull weight towards body by squeezing shoulder blades together, then lower with control."
            else -> "Exercise from workout template. Follow proper form and breathing technique."
        }
    }
    
    /**
     * Infers if exercise is compound based on name and primary muscle
     */
    private fun inferIsCompound(exerciseName: String, primary: ExerciseCategory): Boolean {
        val name = exerciseName.lowercase()
        return when {
            name.contains("squat") || name.contains("deadlift") || name.contains("bench") -> true
            name.contains("press") && !name.contains("leg press") -> true
            name.contains("row") && !name.contains("cable") -> true
            name.contains("pull-up") || name.contains("pullup") -> true
            name.contains("curl") || name.contains("extension") || name.contains("fly") -> false
            name.contains("isolation") -> false
            else -> true // Default to compound for template exercises
        }
    }
    
    /**
     * Infers additional search terms based on exercise name
     */
    private fun inferSearchTerms(exerciseName: String): List<String> {
        val name = exerciseName.lowercase()
        val terms = mutableListOf<String>()
        
        // Add muscle group terms
        when {
            name.contains("bench") || name.contains("chest") -> terms.addAll(listOf("chest", "pecs"))
            name.contains("squat") || name.contains("leg") -> terms.addAll(listOf("legs", "quads", "glutes"))
            name.contains("back") || name.contains("row") -> terms.addAll(listOf("back", "lats"))
            name.contains("shoulder") || name.contains("press") -> terms.addAll(listOf("shoulders", "delts"))
        }
        
        // Add movement terms
        when {
            name.contains("press") -> terms.add("pressing")
            name.contains("pull") -> terms.add("pulling")
            name.contains("curl") -> terms.add("curling")
        }
        
        return terms.distinct()
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