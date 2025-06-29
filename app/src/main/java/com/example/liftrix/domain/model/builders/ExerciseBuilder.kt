package com.example.liftrix.domain.model.builders

import com.example.liftrix.domain.model.*
import java.time.Duration
import java.time.Instant

/**
 * DSL builder for creating Exercise instances with a fluent, readable syntax.
 * 
 * Usage:
 * ```kotlin
 * val exercise = exercise {
 *     libraryExercise = squatExercise
 *     targetSets = 3
 *     targetReps = RepRange.fixed(8)
 *     targetWeight = Weight.fromKilograms(80.0)
 *     sets {
 *         set {
 *             reps = 8
 *             weight = Weight.fromKilograms(80.0)
 *             completed = true
 *         }
 *     }
 * }
 * ```
 */

/**
 * Creates a new Exercise using DSL syntax
 */
@WorkoutDsl
fun exercise(workoutId: WorkoutId = WorkoutId.generate(), block: ExerciseBuilder.() -> Unit): Exercise {
    return ExerciseBuilder(workoutId).apply(block).build()
}

/**
 * Builder class for constructing Exercise instances
 */
@WorkoutDsl
class ExerciseBuilder(private val workoutId: WorkoutId) {
    var id: ExerciseId = ExerciseId.generate()
    var libraryExercise: ExerciseLibrary? = null
    var orderIndex: Int = 0
    var targetSets: Int? = null
    var targetReps: Int? = null
    var targetWeight: Weight? = null
    var targetTime: Duration? = null
    var targetDistance: Distance? = null
    var notes: String? = null
    var createdAt: Instant = Instant.now()
    
    private val setsList = mutableListOf<ExerciseSet>()
    
    /**
     * Add sets using DSL syntax
     */
    fun sets(block: SetListBuilder.() -> Unit) {
        val builder = SetListBuilder(id)
        builder.block()
        setsList.addAll(builder.build())
    }
    
    /**
     * Add a single set
     */
    fun set(block: SetBuilder.() -> Unit): ExerciseSet {
        val set = SetBuilder(id).apply(block).build()
        setsList.add(set)
        return set
    }
    
    /**
     * Set the sets directly (for copying existing exercises)
     */
    var sets: List<ExerciseSet>
        get() = setsList.toList()
        set(value) {
            setsList.clear()
            setsList.addAll(value)
        }
    
    /**
     * Build the final Exercise instance
     */
    fun build(): Exercise {
        require(libraryExercise != null) { "Library exercise is required" }
        
        return Exercise(
            id = id,
            workoutId = workoutId,
            libraryExercise = libraryExercise!!,
            orderIndex = orderIndex,
            targetSets = targetSets,
            targetReps = targetReps,
            targetWeight = targetWeight,
            targetTime = targetTime,
            targetDistance = targetDistance,
            sets = setsList.toList(),
            notes = notes,
            createdAt = createdAt
        )
    }
}

/**
 * Builder for managing a list of sets within an exercise
 */
@WorkoutDsl
class SetListBuilder(private val exerciseId: ExerciseId) {
    private val sets = mutableListOf<ExerciseSet>()
    
    /**
     * Add a set using DSL syntax
     */
    fun set(block: SetBuilder.() -> Unit) {
        val set = SetBuilder(exerciseId).apply(block).build()
        sets.add(set)
    }
    
    /**
     * Add a set from an existing ExerciseSet instance
     */
    fun set(set: ExerciseSet) {
        sets.add(set)
    }
    
    /**
     * Add multiple sets with the same parameters
     */
    fun sets(count: Int, block: SetBuilder.() -> Unit) {
        repeat(count) {
            set(block)
        }
    }
    
    fun build(): List<ExerciseSet> = sets.toList()
}

/**
 * DSL builder for creating ExerciseSet instances
 */
@WorkoutDsl
class SetBuilder(private val exerciseId: ExerciseId) {
    var id: ExerciseSetId = ExerciseSetId.generate()
    var orderIndex: Int = 0
    var reps: Int? = null
    var weight: Weight? = null
    var time: Duration? = null
    var distance: Distance? = null
    var restTime: Duration? = null
    var rpe: RPE? = null
    var completed: Boolean = false
    var notes: String? = null
    var createdAt: Instant = Instant.now()
    
    fun build(): ExerciseSet {
        return ExerciseSet(
            id = id,
            setNumber = orderIndex + 1,
            reps = reps?.let { Reps(it) },
            weight = weight,
            time = time,
            distance = distance,
            rpe = rpe,
            completedAt = if (completed) createdAt else null,
            notes = notes
        )
    }
}

/**
 * Extension functions for common exercise creation patterns
 */

/**
 * Create a strength exercise with weight and reps
 */
fun strengthExercise(
    libraryExercise: ExerciseLibrary,
    sets: Int,
    reps: Int,
    weight: Weight? = null,
    workoutId: WorkoutId = WorkoutId.generate()
): Exercise = exercise(workoutId) {
    this.libraryExercise = libraryExercise
    this.targetSets = sets
    this.targetReps = reps
    this.targetWeight = weight
    
    // Create initial sets
    repeat(sets) {
        set {
            this.reps = reps
            this.weight = weight
        }
    }
}

/**
 * Create a cardio exercise with time and distance
 */
fun cardioExercise(
    libraryExercise: ExerciseLibrary,
    time: Duration? = null,
    distance: Distance? = null,
    workoutId: WorkoutId = WorkoutId.generate()
): Exercise = exercise(workoutId) {
    this.libraryExercise = libraryExercise
    this.targetTime = time
    this.targetDistance = distance
    
    // Create initial set for cardio
    set {
        this.time = time
        this.distance = distance
    }
}

/**
 * Create a bodyweight exercise
 */
fun bodyweightExercise(
    libraryExercise: ExerciseLibrary,
    sets: Int,
    reps: Int,
    workoutId: WorkoutId = WorkoutId.generate()
): Exercise = exercise(workoutId) {
    this.libraryExercise = libraryExercise
    this.targetSets = sets
    this.targetReps = reps
    
    // Create initial sets
    repeat(sets) {
        set {
            this.reps = reps
        }
    }
}

/**
 * Create an empty exercise template (for user to fill in)
 */
fun emptyExercise(
    libraryExercise: ExerciseLibrary,
    workoutId: WorkoutId = WorkoutId.generate()
): Exercise = exercise(workoutId) {
    this.libraryExercise = libraryExercise
}

/**
 * Copy an existing exercise with modifications
 */
fun Exercise.copy(block: ExerciseBuilder.() -> Unit): Exercise = exercise(workoutId) {
    id = ExerciseId.generate() // New ID for copy
    libraryExercise = this@copy.libraryExercise
    orderIndex = this@copy.orderIndex
    targetSets = this@copy.targetSets
    targetReps = this@copy.targetReps
    targetWeight = this@copy.targetWeight
    targetTime = this@copy.targetTime
    targetDistance = this@copy.targetDistance
    notes = this@copy.notes
    createdAt = Instant.now()
    
    // Copy existing sets
    this@copy.sets.forEach { existingSet ->
        set {
            reps = existingSet.reps?.count
            weight = existingSet.weight
            time = existingSet.time
            distance = existingSet.distance
            rpe = existingSet.rpe
            completed = existingSet.isCompleted
            notes = existingSet.notes
        }
    }
    
    // Apply modifications
    block()
}