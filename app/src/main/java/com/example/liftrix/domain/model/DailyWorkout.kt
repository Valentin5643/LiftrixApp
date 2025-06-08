package com.example.liftrix.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Domain model representing a daily workout instance with performance tracking
 */
data class DailyWorkout(
    val id: DailyWorkoutId,
    val userId: String,
    val name: String,
    val date: LocalDate,
    val templateId: WorkoutTemplateId? = null,
    val exercises: List<Exercise>,
    val status: WorkoutStatus,
    val startTime: Instant? = null,
    val endTime: Instant? = null,
    val durationMinutes: Int? = null,
    val totalVolumeKg: Double? = null,
    val totalSets: Int? = null,
    val totalReps: Int? = null,
    val notes: String? = null,
    val rating: Int? = null,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(name.isNotBlank()) { "Workout name cannot be blank" }
        require(name.length <= MAX_NAME_LENGTH) { 
            "Workout name cannot exceed $MAX_NAME_LENGTH characters: ${name.length}" 
        }
        require(exercises.size <= MAX_EXERCISES) { 
            "Workout cannot have more than $MAX_EXERCISES exercises: ${exercises.size}" 
        }
        
        notes?.let { note ->
            require(note.length <= MAX_NOTES_LENGTH) { 
                "Notes cannot exceed $MAX_NOTES_LENGTH characters: ${note.length}" 
            }
        }
        
        rating?.let { r ->
            require(r in MIN_RATING..MAX_RATING) { 
                "Rating must be between $MIN_RATING and $MAX_RATING: $r" 
            }
        }
        
        durationMinutes?.let { duration ->
            require(duration >= 0) { "Duration cannot be negative: $duration" }
            require(duration <= MAX_DURATION_MINUTES) { 
                "Duration cannot exceed $MAX_DURATION_MINUTES minutes: $duration" 
            }
        }
        
        totalVolumeKg?.let { volume ->
            require(volume >= 0) { "Total volume cannot be negative: $volume" }
        }
        
        totalSets?.let { sets ->
            require(sets >= 0) { "Total sets cannot be negative: $sets" }
        }
        
        totalReps?.let { reps ->
            require(reps >= 0) { "Total reps cannot be negative: $reps" }
        }
        
        // Validate time consistency
        if (startTime != null && endTime != null) {
            require(endTime.isAfter(startTime)) { 
                "End time must be after start time: start=$startTime, end=$endTime" 
            }
        }
    }
    
    companion object {
        const val MAX_NAME_LENGTH: Int = 100
        const val MAX_NOTES_LENGTH: Int = 2000
        const val MAX_EXERCISES: Int = 20
        const val MAX_DURATION_MINUTES: Int = 480 // 8 hours
        const val MIN_RATING: Int = 1
        const val MAX_RATING: Int = 5
        
        /**
         * Creates a DailyWorkout from a WorkoutTemplate
         */
        fun fromTemplate(template: WorkoutTemplate): DailyWorkout {
            val now = Instant.now()
            val exercises = template.exercises.map { it.toExercise() }
            
            return DailyWorkout(
                id = DailyWorkoutId.generate(),
                userId = template.userId,
                name = template.name,
                date = LocalDate.now(),
                templateId = template.id,
                exercises = exercises,
                status = WorkoutStatus.PLANNED,
                startTime = null,
                endTime = null,
                durationMinutes = template.estimatedDurationMinutes,
                totalVolumeKg = null,
                totalSets = template.getTotalSets(),
                totalReps = null,
                notes = template.description,
                rating = null,
                createdAt = now,
                updatedAt = now
            )
        }
        
        /**
         * Creates a new DailyWorkout from scratch
         */
        fun create(
            userId: String,
            name: String,
            date: LocalDate = LocalDate.now(),
            exercises: List<Exercise> = emptyList(),
            notes: String? = null
        ): DailyWorkout {
            val now = Instant.now()
            return DailyWorkout(
                id = DailyWorkoutId.generate(),
                userId = userId,
                name = name.trim(),
                date = date,
                templateId = null,
                exercises = exercises,
                status = WorkoutStatus.PLANNED,
                startTime = null,
                endTime = null,
                durationMinutes = null,
                totalVolumeKg = null,
                totalSets = exercises.size,
                totalReps = null,
                notes = notes?.trim()?.takeIf { it.isNotBlank() },
                rating = null,
                createdAt = now,
                updatedAt = now
            )
        }
    }
    
    /**
     * Starts the workout
     */
    fun start(): DailyWorkout {
        require(status == WorkoutStatus.PLANNED) { "Can only start a planned workout" }
        
        return copy(
            status = WorkoutStatus.IN_PROGRESS,
            startTime = Instant.now(),
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Completes the workout and calculates final statistics
     */
    fun complete(): DailyWorkout {
        require(status == WorkoutStatus.IN_PROGRESS) { "Can only complete an in-progress workout" }
        
        val now = Instant.now()
        val calculatedDuration = startTime?.let { start ->
            Duration.between(start, now).toMinutes().toInt()
        }
        
        val calculatedVolume = exercises.sumOf { it.calculateTotalVolume().kilograms }
        val calculatedSets = exercises.sumOf { it.sets.size }
        val calculatedReps = exercises.sumOf { it.getTotalRepsCompleted().count }
        
        return copy(
            status = WorkoutStatus.COMPLETED,
            endTime = now,
            durationMinutes = calculatedDuration ?: durationMinutes,
            totalVolumeKg = calculatedVolume,
            totalSets = calculatedSets,
            totalReps = calculatedReps,
            updatedAt = now
        )
    }
    
    /**
     * Pauses the workout
     */
    fun pause(): DailyWorkout {
        require(status == WorkoutStatus.IN_PROGRESS) { "Can only pause an in-progress workout" }
        
        return copy(
            status = WorkoutStatus.PAUSED,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Resumes a paused workout
     */
    fun resume(): DailyWorkout {
        require(status == WorkoutStatus.PAUSED) { "Can only resume a paused workout" }
        
        return copy(
            status = WorkoutStatus.IN_PROGRESS,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Cancels the workout
     */
    fun cancel(): DailyWorkout {
        return copy(
            status = WorkoutStatus.CANCELLED,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Adds an exercise to the workout
     */
    fun addExercise(exercise: Exercise): DailyWorkout {
        require(exercises.size < MAX_EXERCISES) { "Cannot add more exercises, limit reached" }
        require(exercises.none { it.id == exercise.id }) { 
            "Exercise with ID ${exercise.id} already exists" 
        }
        
        return copy(
            exercises = exercises + exercise,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Updates an exercise in the workout
     */
    fun updateExercise(exerciseId: ExerciseId, updatedExercise: Exercise): DailyWorkout {
        val exerciseIndex = exercises.indexOfFirst { it.id == exerciseId }
        require(exerciseIndex != -1) { "Exercise with ID $exerciseId not found" }
        
        val updatedExercises = exercises.toMutableList()
        updatedExercises[exerciseIndex] = updatedExercise.copy(updatedAt = Instant.now())
        
        return copy(
            exercises = updatedExercises,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Removes an exercise from the workout
     */
    fun removeExercise(exerciseId: ExerciseId): DailyWorkout {
        val updatedExercises = exercises.filterNot { it.id == exerciseId }
        require(updatedExercises.size < exercises.size) { 
            "Exercise with ID $exerciseId not found" 
        }
        
        return copy(
            exercises = updatedExercises,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Updates the workout name
     */
    fun updateName(newName: String): DailyWorkout {
        require(newName.isNotBlank()) { "Workout name cannot be blank" }
        require(newName.trim().length <= MAX_NAME_LENGTH) { 
            "Workout name cannot exceed $MAX_NAME_LENGTH characters" 
        }
        
        return copy(
            name = newName.trim(),
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Updates the workout notes
     */
    fun updateNotes(newNotes: String?): DailyWorkout {
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
     * Updates the workout rating
     */
    fun updateRating(newRating: Int?): DailyWorkout {
        newRating?.let { rating ->
            require(rating in MIN_RATING..MAX_RATING) { 
                "Rating must be between $MIN_RATING and $MAX_RATING: $rating" 
            }
        }
        
        return copy(
            rating = newRating,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Calculates the actual duration of the workout
     */
    fun getActualDuration(): Duration? {
        return if (startTime != null && endTime != null) {
            Duration.between(startTime, endTime)
        } else null
    }
    
    /**
     * Calculates workout completion percentage
     */
    fun getCompletionPercentage(): Double {
        val totalSets = exercises.sumOf { it.sets.size }
        val completedSets = exercises.sumOf { it.getCompletedSetsCount() }
        
        return if (totalSets > 0) {
            (completedSets.toDouble() / totalSets) * 100.0
        } else 0.0
    }
    
    /**
     * Checks if the workout is completed
     */
    fun isCompleted(): Boolean = status == WorkoutStatus.COMPLETED
    
    /**
     * Checks if the workout is in progress
     */
    fun isInProgress(): Boolean = status == WorkoutStatus.IN_PROGRESS
    
    /**
     * Gets all unique exercise categories in this workout
     */
    fun getExerciseCategories(): Set<ExerciseCategory> = exercises.map { it.category }.toSet()
    
    /**
     * Converts this daily workout to a template
     */
    fun toTemplate(templateName: String): WorkoutTemplate {
        val templateExercises = exercises.mapIndexed { index, exercise ->
            TemplateExercise.fromExercise(
                exercise = exercise,
                orderIndex = index,
                targetSets = exercise.targetSets,
                targetReps = exercise.targetReps,
                targetWeight = exercise.targetWeight,
                notes = exercise.notes
            )
        }
        
        return WorkoutTemplate.create(
            userId = userId,
            name = templateName,
            description = notes,
            exercises = templateExercises,
            estimatedDurationMinutes = durationMinutes
        )
    }
} 