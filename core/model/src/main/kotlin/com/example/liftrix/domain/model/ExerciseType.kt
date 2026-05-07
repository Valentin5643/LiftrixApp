package com.example.liftrix.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ExerciseType(val displayName: String) {
    WEIGHT_BASED("Weight based"),
    TIME_BASED("Time based"),
    DISTANCE_BASED("Distance based"),
    BODYWEIGHT("Bodyweight"),
    CARDIO("Cardio"),
    STRENGTH("Strength"),
    HYBRID("Hybrid"),
    FLEXIBILITY("Flexibility"),
    MOBILITY("Mobility"),
    BALANCE("Balance"),
    PLYOMETRIC("Plyometric"),
    OLYMPIC_LIFT("Olympic lift"),
    POWERLIFTING("Powerlifting"),
    ISOLATION("Isolation"),
    COMPOUND("Compound"),
    OTHER("Other");

    companion object {
        fun fromLibraryExercise(exercise: ExerciseLibrary): ExerciseType {
            return when {
                exercise.equipment == Equipment.BODYWEIGHT_ONLY -> BODYWEIGHT
                exercise.equipment in listOf(
                    Equipment.BARBELL,
                    Equipment.DUMBBELLS,
                    Equipment.KETTLEBELLS,
                    Equipment.CABLE_MACHINE
                ) -> WEIGHT_BASED
                exercise.primaryMuscleGroup == ExerciseCategory.CARDIO -> CARDIO
                exercise.movementPattern.contains("hold", ignoreCase = true) ||
                    exercise.movementPattern.contains("static", ignoreCase = true) ||
                    exercise.movementPattern.contains("plank", ignoreCase = true) -> TIME_BASED
                exercise.movementPattern.contains("running", ignoreCase = true) ||
                    exercise.movementPattern.contains("cycling", ignoreCase = true) ||
                    exercise.movementPattern.contains("walking", ignoreCase = true) ||
                    exercise.movementPattern.contains("rowing", ignoreCase = true) -> DISTANCE_BASED
                else -> HYBRID
            }
        }
    }
}
