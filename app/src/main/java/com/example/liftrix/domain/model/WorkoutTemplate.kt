package com.example.liftrix.domain.model

import java.time.Instant

/**
 * Domain model representing a reusable workout template with exercise ordering and targeting
 */
data class WorkoutTemplate(
    val id: WorkoutTemplateId,
    val userId: String,
    val name: String,
    val description: String? = null,
    val exercises: List<TemplateExercise>,
    val estimatedDurationMinutes: Int? = null,
    val difficultyLevel: Int? = null,
    val folderId: String?, // 🔥 NULLABLE: Templates can exist without folder (handled by business logic)
    val usageCount: Int = 0,
    val lastUsedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(name.isNotBlank()) { "Template name cannot be blank" }
        require(name.length <= MAX_NAME_LENGTH) { 
            "Template name cannot exceed $MAX_NAME_LENGTH characters: ${name.length}" 
        }
        require(exercises.size <= MAX_EXERCISES) { 
            "Template cannot have more than $MAX_EXERCISES exercises: ${exercises.size}" 
        }
        
        description?.let { desc ->
            require(desc.length <= MAX_DESCRIPTION_LENGTH) { 
                "Description cannot exceed $MAX_DESCRIPTION_LENGTH characters: ${desc.length}" 
            }
        }
        
        estimatedDurationMinutes?.let { duration ->
            require(duration in MIN_DURATION_MINUTES..MAX_DURATION_MINUTES) { 
                "Estimated duration must be between $MIN_DURATION_MINUTES and $MAX_DURATION_MINUTES minutes: $duration" 
            }
        }
        
        difficultyLevel?.let { difficulty ->
            require(difficulty in MIN_DIFFICULTY..MAX_DIFFICULTY) { 
                "Difficulty level must be between $MIN_DIFFICULTY and $MAX_DIFFICULTY: $difficulty" 
            }
        }
        
        // 🔥 REMOVED: folderId can now be null (templates without folders handled by business logic)
        
        require(usageCount >= 0) { "Usage count cannot be negative: $usageCount" }
        
        // Validate exercise order is sequential
        val orderIndices = exercises.map { it.orderIndex }.sorted()
        if (exercises.isNotEmpty()) {
            require(orderIndices == (0 until exercises.size).toList()) { 
                "Exercise order indices must be sequential starting from 0: $orderIndices" 
            }
        }
    }
    
    companion object {
        const val MAX_NAME_LENGTH: Int = 100
        const val MAX_DESCRIPTION_LENGTH: Int = 500
        const val MAX_EXERCISES: Int = 20
        const val MIN_DURATION_MINUTES: Int = 5
        const val MAX_DURATION_MINUTES: Int = 300 // 5 hours
        const val MIN_DIFFICULTY: Int = 1
        const val MAX_DIFFICULTY: Int = 10
        
        /**
         * Creates a new WorkoutTemplate with validation
         */
        fun create(
            userId: String,
            name: String,
            folderId: String?,
            description: String? = null,
            exercises: List<TemplateExercise> = emptyList(),
            estimatedDurationMinutes: Int? = null,
            difficultyLevel: Int? = null
        ): WorkoutTemplate {
            val now = Instant.now()
            return WorkoutTemplate(
                id = WorkoutTemplateId.generate(),
                userId = userId,
                name = name.trim(),
                description = description?.trim()?.takeIf { it.isNotBlank() },
                exercises = exercises,
                estimatedDurationMinutes = estimatedDurationMinutes,
                difficultyLevel = difficultyLevel,
                folderId = folderId,
                usageCount = 0,
                lastUsedAt = null,
                createdAt = now,
                updatedAt = now
            )
        }
    }
    
    /**
     * Adds an exercise to the template
     */
    fun addExercise(exercise: TemplateExercise): WorkoutTemplate {
        require(exercises.size < MAX_EXERCISES) { "Cannot add more exercises, limit reached" }
        require(exercises.none { it.exerciseId == exercise.exerciseId }) { 
            "Exercise with ID ${exercise.exerciseId} already exists" 
        }
        
        val newOrderIndex = exercises.size
        val exerciseWithOrder = exercise.copy(orderIndex = newOrderIndex)
        
        return copy(
            exercises = exercises + exerciseWithOrder,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Removes an exercise from the template
     */
    fun removeExercise(exerciseId: ExerciseId): WorkoutTemplate {
        val exerciseIndex = exercises.indexOfFirst { it.exerciseId == exerciseId }
        require(exerciseIndex != -1) { "Exercise with ID $exerciseId not found" }
        
        val updatedExercises = exercises.filterNot { it.exerciseId == exerciseId }
            .mapIndexed { index, exercise -> exercise.copy(orderIndex = index) }
        
        return copy(
            exercises = updatedExercises,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Reorders exercises in the template
     */
    fun reorderExercises(newOrder: List<ExerciseId>): WorkoutTemplate {
        require(newOrder.size == exercises.size) { 
            "New order must contain all exercises: expected ${exercises.size}, got ${newOrder.size}" 
        }
        require(newOrder.toSet() == exercises.map { it.exerciseId }.toSet()) { 
            "New order must contain exactly the same exercises" 
        }
        
        val reorderedExercises = newOrder.mapIndexed { index, exerciseId ->
            val exercise = exercises.first { it.exerciseId == exerciseId }
            exercise.copy(orderIndex = index)
        }
        
        return copy(
            exercises = reorderedExercises,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Updates the template name
     */
    fun updateName(newName: String): WorkoutTemplate {
        require(newName.isNotBlank()) { "Template name cannot be blank" }
        require(newName.trim().length <= MAX_NAME_LENGTH) { 
            "Template name cannot exceed $MAX_NAME_LENGTH characters" 
        }
        
        return copy(
            name = newName.trim(),
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Updates the template description
     */
    fun updateDescription(newDescription: String?): WorkoutTemplate {
        val trimmedDescription = newDescription?.trim()?.takeIf { it.isNotBlank() }
        
        trimmedDescription?.let { desc ->
            require(desc.length <= MAX_DESCRIPTION_LENGTH) { 
                "Description cannot exceed $MAX_DESCRIPTION_LENGTH characters: ${desc.length}" 
            }
        }
        
        return copy(
            description = trimmedDescription,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Updates the estimated duration
     */
    fun updateEstimatedDuration(newDurationMinutes: Int?): WorkoutTemplate {
        newDurationMinutes?.let { duration ->
            require(duration in MIN_DURATION_MINUTES..MAX_DURATION_MINUTES) { 
                "Estimated duration must be between $MIN_DURATION_MINUTES and $MAX_DURATION_MINUTES minutes: $duration" 
            }
        }
        
        return copy(
            estimatedDurationMinutes = newDurationMinutes,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Updates the difficulty level
     */
    fun updateDifficultyLevel(newDifficultyLevel: Int?): WorkoutTemplate {
        newDifficultyLevel?.let { difficulty ->
            require(difficulty in MIN_DIFFICULTY..MAX_DIFFICULTY) { 
                "Difficulty level must be between $MIN_DIFFICULTY and $MAX_DIFFICULTY: $difficulty" 
            }
        }
        
        return copy(
            difficultyLevel = newDifficultyLevel,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Records usage of this template
     */
    fun recordUsage(): WorkoutTemplate {
        val now = Instant.now()
        return copy(
            usageCount = usageCount + 1,
            lastUsedAt = now,
            updatedAt = now
        )
    }
    
    /**
     * Creates an active workout session from this template
     */
    fun createActiveSession(userId: String, customName: String? = null): UnifiedWorkoutSession {
        return UnifiedWorkoutSession.fromTemplate(userId, this, customName)
    }
    
    /**
     * Gets all unique exercise categories in this template
     */
    fun getExerciseCategories(): Set<ExerciseCategory> = 
        exercises.map { it.primaryMuscle }.toSet()
    
    /**
     * Gets the total number of sets in this template
     */
    fun getTotalSets(): Int = exercises.sumOf { it.targetSets ?: 0 }
    
    /**
     * Checks if this template contains a specific exercise
     */
    fun containsExercise(exerciseId: ExerciseId): Boolean = 
        exercises.any { it.exerciseId == exerciseId }
    
} 