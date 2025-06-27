package com.example.liftrix.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Room relationship data class for workout with its exercises
 */
data class WorkoutWithExercises(
    @Embedded val workout: WorkoutEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "workout_id"
    )
    val exercises: List<ExerciseEntity>
)