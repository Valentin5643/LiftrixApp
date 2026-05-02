package com.example.liftrix.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Domain model representing a complete workout session
 */
data class Workout(
    val userId: String,
    val id: WorkoutId,
    val name: String,
    val date: LocalDate,
    val exercises: List<Exercise>,
    val status: WorkoutStatus,
    val startTime: Instant?,
    val endTime: Instant?,
    val notes: String? = null,
    val templateId: WorkoutId? = null,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(userId.isNotBlank()) { "Workout must have a valid user ID" }
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
        
        // Validate time consistency
        if (startTime != null && endTime != null) {
            require(endTime.isAfter(startTime)) { 
                "End time must be after start time: start=$startTime, end=$endTime" 
            }
            // Note: Duration validation removed to allow long workouts (some users forget to end them)
        }
        
        // Validate exercise IDs are unique
        val exerciseIds = exercises.map { it.id.value }
        require(exerciseIds.size == exerciseIds.distinct().size) { 
            "Exercise IDs must be unique within a workout" 
        }
    }
    
    companion object {
        const val MAX_NAME_LENGTH: Int = 100
        const val MAX_NOTES_LENGTH: Int = 2000
        const val MAX_EXERCISES: Int = 20
        // Note: MAX_WORKOUT_HOURS removed - no duration limit enforced
    }
    
    /**
     * Calculates the total duration of the workout
     */
    fun getDuration(): Duration? {
        return if (startTime != null && endTime != null) {
            Duration.between(startTime, endTime)
        } else null
    }
    
    /**
     * Gets the effective timestamp for sorting and display.
     * Falls back to createdAt when updatedAt is missing or zero (sync migration case).
     */
    fun getEffectiveTimestamp(): Instant {
        return if (updatedAt.epochSecond > 0) updatedAt else createdAt
    }
    
    /**
     * Calculates total volume across all exercises using centralized VolumeCalculator
     */
    fun calculateTotalVolume(): Weight {
        val totalVolumeKg = exercises.sumOf { exercise ->
            com.example.liftrix.domain.util.VolumeCalculator.calculateVolumeFromSets(exercise.sets)
        }
        return Weight(totalVolumeKg)
    }
    
    /**
     * Gets the total number of sets across all exercises
     */
    fun getTotalSets(): Int = exercises.sumOf { it.sets.size }
    
    /**
     * Gets the total number of completed sets
     */
    fun getCompletedSets(): Int = exercises.sumOf { it.getCompletedSetsCount() }
    
    /**
     * Gets the total number of reps completed
     */
    fun getTotalRepsCompleted(): Reps {
        return exercises
            .map { it.getTotalRepsCompleted() }
            .fold(Reps.ZERO) { acc, reps -> acc + reps }
    }
    
    /**
     * Calculates workout completion percentage
     */
    fun getCompletionPercentage(): Double {
        val totalSets = getTotalSets()
        return if (totalSets > 0) {
            (getCompletedSets().toDouble() / totalSets) * 100.0
        } else 0.0
    }
    
    /**
     * Checks if the workout is completed (all exercises completed)
     */
    fun isCompleted(): Boolean = exercises.isNotEmpty() && exercises.all { it.isCompleted() }
    
    /**
     * Gets all unique exercise categories in this workout
     */
    fun getExerciseCategories(): Set<ExerciseCategory> = exercises.map { it.libraryExercise.primaryMuscleGroup }.toSet()
    
    /**
     * Starts the workout by setting start time and status
     */
    fun start(): Workout = copy(
        status = WorkoutStatus.IN_PROGRESS,
        startTime = Instant.now(),
        updatedAt = Instant.now()
    )
    
    /**
     * Completes the workout by setting end time and status
     */
    fun complete(): Workout = copy(
        status = WorkoutStatus.COMPLETED,
        endTime = Instant.now(),
        updatedAt = Instant.now()
    )
    
    /**
     * Pauses the workout
     */
    fun pause(): Workout = copy(
        status = WorkoutStatus.PAUSED,
        updatedAt = Instant.now()
    )
    
    /**
     * Resumes a paused workout
     */
    fun resume(): Workout {
        require(status == WorkoutStatus.PAUSED) { "Can only resume a paused workout" }
        return copy(
            status = WorkoutStatus.IN_PROGRESS,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Cancels the workout
     */
    fun cancel(): Workout = copy(
        status = WorkoutStatus.CANCELLED,
        updatedAt = Instant.now()
    )
    
    /**
     * Adds an exercise to the workout
     */
    fun addExercise(exercise: Exercise): Workout {
        require(exercises.size < MAX_EXERCISES) { "Cannot add more exercises, limit reached" }
        require(exercises.none { it.id == exercise.id }) { "Exercise with ID ${exercise.id} already exists" }
        
        return copy(
            exercises = exercises + exercise,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Updates an exercise in the workout
     */
    fun updateExercise(exerciseId: ExerciseId, updatedExercise: Exercise): Workout {
        val exerciseIndex = exercises.indexOfFirst { it.id == exerciseId }
        require(exerciseIndex != -1) { "Exercise with ID $exerciseId not found" }
        
        val updatedExercises = exercises.toMutableList()
        updatedExercises[exerciseIndex] = updatedExercise
        
        return copy(
            exercises = updatedExercises,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Removes an exercise from the workout
     */
    fun removeExercise(exerciseId: ExerciseId): Workout {
        require(exercises.any { it.id == exerciseId }) { "Exercise with ID $exerciseId not found" }
        require(exercises.size > 1) { "Cannot remove the last exercise from workout" }
        
        return copy(
            exercises = exercises.filter { it.id != exerciseId },
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Reorders exercises in the workout
     */
    fun reorderExercises(newOrder: List<ExerciseId>): Workout {
        require(newOrder.size == exercises.size) { "New order must contain all exercises" }
        require(newOrder.toSet() == exercises.map { it.id }.toSet()) { "New order must contain exactly the same exercises" }
        
        val reorderedExercises = newOrder.mapNotNull { id ->
            exercises.find { it.id == id }
        }
        
        return copy(
            exercises = reorderedExercises,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Creates a copy of this workout as a template
     */
    fun toTemplate(name: String): Workout = copy(
        id = WorkoutId.generate(),
        name = name,
        status = WorkoutStatus.PLANNED,
        startTime = null,
        endTime = null,
        exercises = exercises.map { it.toTemplate() },
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )
    
    /**
     * Gets workout metrics for analytics
     */
    fun getMetrics(): WorkoutMetrics = WorkoutMetrics(
        totalVolume = calculateTotalVolume(),
        totalSets = getTotalSets(),
        completedSets = getCompletedSets(),
        totalReps = getTotalRepsCompleted(),
        duration = getDuration(),
        completionPercentage = getCompletionPercentage(),
        exerciseCount = exercises.size,
        categories = getExerciseCategories()
    )
}


/**
 * Workout metrics data class
 */
data class WorkoutMetrics(
    val totalVolume: Weight,
    val totalSets: Int,
    val completedSets: Int,
    val totalReps: Reps,
    val duration: Duration?,
    val completionPercentage: Double,
    val exerciseCount: Int,
    val categories: Set<ExerciseCategory>
) 