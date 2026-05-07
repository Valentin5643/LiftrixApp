package com.example.liftrix.domain.model

import java.time.Instant

fun ExerciseLibrary.toExercise(workoutId: WorkoutId, orderIndex: Int): Exercise {
    return Exercise(
        id = ExerciseId.generate(),
        workoutId = workoutId,
        libraryExercise = this,
        orderIndex = orderIndex,
        targetSets = null,
        targetReps = null,
        targetWeight = null,
        targetTime = null,
        targetDistance = null,
        sets = listOf(
            ExerciseSet(
                id = ExerciseSetId.generate(),
                setNumber = 1,
                reps = Reps.ZERO,
                weight = Weight.ZERO
            )
        ),
        notes = null,
        createdAt = Instant.now()
    )
}
