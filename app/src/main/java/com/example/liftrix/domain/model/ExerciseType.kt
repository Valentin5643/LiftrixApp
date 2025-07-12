package com.example.liftrix.domain.model

/**
 * Enum representing different exercise types based on their primary characteristics
 */
enum class ExerciseType {
    WEIGHT_BASED,    // Requires weight tracking (barbell, dumbbells, etc.)
    TIME_BASED,      // Primarily time-focused (planks, wall sits, etc.)
    DISTANCE_BASED,  // Primarily distance-focused (running, cycling, etc.)
    BODYWEIGHT,      // Bodyweight only exercises (push-ups, pull-ups, etc.)
    CARDIO,          // Cardio exercises (may combine time and distance)
    STRENGTH,        // General strength training exercises
    HYBRID,          // Exercises that can use multiple metrics
    FLEXIBILITY,     // Flexibility and stretching exercises
    BALANCE,         // Balance and stability exercises
    PLYOMETRIC,      // Explosive power exercises
    OLYMPIC_LIFT,    // Olympic lifting movements
    POWERLIFTING,    // Powerlifting movements
    ISOLATION,       // Isolation exercises targeting specific muscles
    COMPOUND;        // Compound movements working multiple muscle groups
    
    companion object {
        /**
         * Determines exercise type based on ExerciseLibrary metadata
         */
        fun fromLibraryExercise(exercise: ExerciseLibrary): ExerciseType {
            return when {
                // Bodyweight exercises
                exercise.equipment == Equipment.BODYWEIGHT_ONLY -> BODYWEIGHT
                
                // Weight-based exercises
                exercise.equipment in listOf(
                    Equipment.BARBELL, 
                    Equipment.DUMBBELLS, 
                    Equipment.KETTLEBELLS,
                    Equipment.CABLE_MACHINE
                ) -> WEIGHT_BASED
                
                // Cardio exercises
                exercise.primaryMuscleGroup == ExerciseCategory.CARDIO -> CARDIO
                
                // Time-based exercises (static holds)
                exercise.movementPattern.contains("hold", ignoreCase = true) ||
                exercise.movementPattern.contains("static", ignoreCase = true) ||
                exercise.movementPattern.contains("plank", ignoreCase = true) -> TIME_BASED
                
                // Distance-based exercises
                exercise.movementPattern.contains("running", ignoreCase = true) ||
                exercise.movementPattern.contains("cycling", ignoreCase = true) ||
                exercise.movementPattern.contains("walking", ignoreCase = true) ||
                exercise.movementPattern.contains("rowing", ignoreCase = true) -> DISTANCE_BASED
                
                // Default to hybrid for exercises that don't fit clear categories
                else -> HYBRID
            }
        }
    }
}