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
    val tags: Set<String> = emptySet(),
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
        
        require(tags.size <= MAX_TAGS) { 
            "Template cannot have more than $MAX_TAGS tags: ${tags.size}" 
        }
        
        tags.forEach { tag ->
            require(tag.isNotBlank() && tag.length <= MAX_TAG_LENGTH) { 
                "Tag must be non-blank and not exceed $MAX_TAG_LENGTH characters: '$tag'" 
            }
        }
        
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
        const val MAX_TAGS: Int = 10
        const val MAX_TAG_LENGTH: Int = 30
        
        /**
         * Creates a new WorkoutTemplate with validation
         */
        fun create(
            userId: String,
            name: String,
            description: String? = null,
            exercises: List<TemplateExercise> = emptyList(),
            estimatedDurationMinutes: Int? = null,
            difficultyLevel: Int? = null,
            tags: Set<String> = emptySet()
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
                tags = tags.map { it.trim().lowercase() }.toSet(),
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
     * Adds a tag to the template
     */
    fun addTag(tag: String): WorkoutTemplate {
        val normalizedTag = tag.trim().lowercase()
        require(normalizedTag.isNotBlank()) { "Tag cannot be blank" }
        require(normalizedTag.length <= MAX_TAG_LENGTH) { 
            "Tag cannot exceed $MAX_TAG_LENGTH characters: ${normalizedTag.length}" 
        }
        require(!tags.contains(normalizedTag)) { "Tag already exists: $normalizedTag" }
        require(tags.size < MAX_TAGS) { "Cannot add more than $MAX_TAGS tags" }
        
        return copy(
            tags = tags + normalizedTag,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Removes a tag from the template
     */
    fun removeTag(tag: String): WorkoutTemplate {
        val normalizedTag = tag.trim().lowercase()
        require(tags.contains(normalizedTag)) { "Tag does not exist: $normalizedTag" }
        
        return copy(
            tags = tags - normalizedTag,
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
     * Creates a daily workout from this template
     */
    fun createDailyWorkout(): DailyWorkout {
        return DailyWorkout.fromTemplate(this)
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
    
    /**
     * Checks if this template has a specific tag
     */
    fun hasTag(tag: String): Boolean = tags.contains(tag.trim().lowercase())
} 