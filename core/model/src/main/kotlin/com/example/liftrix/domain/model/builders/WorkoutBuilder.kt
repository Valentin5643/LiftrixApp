package com.example.liftrix.domain.model.builders

import com.example.liftrix.domain.model.*
import java.time.Instant
import java.time.LocalDate

/**
 * DSL builder for creating Workout instances with a fluent, readable syntax.
 * 
 * Usage:
 * ```kotlin
 * val workout = workout {
 *     userId = "user123"
 *     name = "Morning Strength"
 *     date = LocalDate.now()
 *     status = WorkoutStatus.PLANNED
 *     exercises {
 *         exercise {
 *             libraryExercise = squatExercise
 *             targetSets = 3
 *             targetReps = RepRange.fixed(8)
 *         }
 *     }
 * }
 * ```
 */
@DslMarker
annotation class WorkoutDsl

/**
 * Creates a new Workout using DSL syntax
 */
@WorkoutDsl
fun workout(block: WorkoutBuilder.() -> Unit): Workout {
    return WorkoutBuilder().apply(block).build()
}

/**
 * Builder class for constructing Workout instances
 */
@WorkoutDsl
class WorkoutBuilder {
    var userId: String? = null
    var id: WorkoutId = WorkoutId.generate()
    var name: String? = null
    var date: LocalDate = LocalDate.now()
    var status: WorkoutStatus = WorkoutStatus.PLANNED
    var startTime: Instant? = null
    var endTime: Instant? = null
    var notes: String? = null
    var templateId: WorkoutId? = null
    var createdAt: Instant = Instant.now()
    var updatedAt: Instant = Instant.now()
    
    private val exercises = mutableListOf<Exercise>()
    
    /**
     * Add exercises using DSL syntax
     */
    fun exercises(block: ExerciseListBuilder.() -> Unit) {
        val builder = ExerciseListBuilder(id)
        builder.block()
        exercises.addAll(builder.build())
    }
    
    /**
     * Add a single exercise
     */
    fun exercise(block: ExerciseBuilder.() -> Unit): Exercise {
        val exercise = ExerciseBuilder(id).apply(block).build()
        exercises.add(exercise)
        return exercise
    }
    
    /**
     * Build the final Workout instance
     */
    fun build(): Workout {
        require(!userId.isNullOrBlank()) { "User ID is required" }
        require(!name.isNullOrBlank()) { "Workout name is required" }
        
        return Workout(
            userId = userId!!,
            id = id,
            name = name!!,
            date = date,
            exercises = exercises.toList(),
            status = status,
            startTime = startTime,
            endTime = endTime,
            notes = notes,
            templateId = templateId,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}

/**
 * Builder for managing a list of exercises within a workout
 */
@WorkoutDsl
class ExerciseListBuilder(private val workoutId: WorkoutId) {
    private val exercises = mutableListOf<Exercise>()
    
    /**
     * Add an exercise using DSL syntax
     */
    fun exercise(block: ExerciseBuilder.() -> Unit) {
        val exercise = ExerciseBuilder(workoutId).apply(block).build()
        exercises.add(exercise)
    }
    
    /**
     * Add an exercise from an existing Exercise instance
     */
    fun exercise(exercise: Exercise) {
        exercises.add(exercise.copy(workoutId = workoutId))
    }
    
    /**
     * Add multiple exercises from existing instances
     */
    fun exercises(vararg exercises: Exercise) {
        this.exercises.addAll(exercises.map { it.copy(workoutId = workoutId) })
    }
    
    fun build(): List<Exercise> = exercises.toList()
}

/**
 * Extension functions for common workout creation patterns
 */

/**
 * Create a planned workout with basic information
 */
fun plannedWorkout(
    userId: String,
    name: String,
    date: LocalDate = LocalDate.now(),
    block: WorkoutBuilder.() -> Unit = {}
): Workout = workout {
    this.userId = userId
    this.name = name
    this.date = date
    this.status = WorkoutStatus.PLANNED
    block()
}

/**
 * Create an active workout session
 */
fun activeWorkout(
    userId: String,
    name: String,
    startTime: Instant = Instant.now(),
    block: WorkoutBuilder.() -> Unit = {}
): Workout = workout {
    this.userId = userId
    this.name = name
    this.status = WorkoutStatus.IN_PROGRESS
    this.startTime = startTime
    block()
}

/**
 * Create a completed workout
 */
fun completedWorkout(
    userId: String,
    name: String,
    startTime: Instant,
    endTime: Instant = Instant.now(),
    block: WorkoutBuilder.() -> Unit = {}
): Workout = workout {
    this.userId = userId
    this.name = name
    this.status = WorkoutStatus.COMPLETED
    this.startTime = startTime
    this.endTime = endTime
    block()
}

/**
 * Copy an existing workout with modifications
 */
fun Workout.copy(block: WorkoutBuilder.() -> Unit): Workout = workout {
    userId = this@copy.userId
    id = WorkoutId.generate() // New ID for copy
    name = this@copy.name
    date = this@copy.date
    status = this@copy.status
    startTime = this@copy.startTime
    endTime = this@copy.endTime
    notes = this@copy.notes
    templateId = this@copy.templateId
    createdAt = Instant.now()
    updatedAt = Instant.now()
    
    // Copy existing exercises
    this@copy.exercises.forEach { existingExercise ->
        exercise {
            libraryExercise = existingExercise.libraryExercise
            orderIndex = existingExercise.orderIndex
            targetSets = existingExercise.targetSets
            targetReps = existingExercise.targetReps
            targetWeight = existingExercise.targetWeight
            targetTime = existingExercise.targetTime
            targetDistance = existingExercise.targetDistance
            notes = existingExercise.notes
            // Sets will be copied by ExerciseBuilder
            sets = existingExercise.sets
        }
    }
    
    // Apply modifications
    block()
}