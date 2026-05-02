package com.example.liftrix.domain.model

/**
 * Represents the capabilities and field requirements for an exercise type
 */
data class ExerciseCapabilities(
    val supportsWeight: Boolean,
    val supportsTime: Boolean,
    val supportsDistance: Boolean,
    val requiredFields: Set<ExerciseField>
) {
    companion object {
        /**
         * Determines exercise capabilities based on exercise type
         */
        fun fromExerciseType(exerciseType: ExerciseType): ExerciseCapabilities {
            return when (exerciseType) {
                ExerciseType.WEIGHT_BASED -> ExerciseCapabilities(
                    supportsWeight = true,
                    supportsTime = false,
                    supportsDistance = false,
                    requiredFields = setOf(ExerciseField.REPS, ExerciseField.WEIGHT)
                )
                
                ExerciseType.TIME_BASED -> ExerciseCapabilities(
                    supportsWeight = false,
                    supportsTime = true,
                    supportsDistance = false,
                    requiredFields = setOf(ExerciseField.TIME)
                )
                
                ExerciseType.DISTANCE_BASED -> ExerciseCapabilities(
                    supportsWeight = false,
                    supportsTime = true,
                    supportsDistance = true,
                    requiredFields = setOf(ExerciseField.DISTANCE)
                )
                
                ExerciseType.BODYWEIGHT -> ExerciseCapabilities(
                    supportsWeight = false,
                    supportsTime = true,
                    supportsDistance = false,
                    requiredFields = setOf(ExerciseField.REPS)
                )
                
                ExerciseType.CARDIO -> ExerciseCapabilities(
                    supportsWeight = false,
                    supportsTime = true,
                    supportsDistance = true,
                    requiredFields = emptySet() // Either time or distance required
                )
                
                ExerciseType.STRENGTH -> ExerciseCapabilities(
                    supportsWeight = true,
                    supportsTime = false,
                    supportsDistance = false,
                    requiredFields = setOf(ExerciseField.REPS, ExerciseField.WEIGHT)
                )
                
                ExerciseType.HYBRID -> ExerciseCapabilities(
                    supportsWeight = true,
                    supportsTime = true,
                    supportsDistance = true,
                    requiredFields = emptySet() // At least one metric required
                )

                ExerciseType.FLEXIBILITY -> ExerciseCapabilities(
                    supportsWeight = false,
                    supportsTime = true,
                    supportsDistance = false,
                    requiredFields = setOf(ExerciseField.TIME) // Hold time for stretches
                )
                
                ExerciseType.BALANCE -> ExerciseCapabilities(
                    supportsWeight = false,
                    supportsTime = true,
                    supportsDistance = false,
                    requiredFields = setOf(ExerciseField.TIME) // Balance hold duration
                )
                
                ExerciseType.PLYOMETRIC -> ExerciseCapabilities(
                    supportsWeight = false,
                    supportsTime = false,
                    supportsDistance = true,
                    requiredFields = setOf(ExerciseField.REPS) // Explosive movements counted by reps
                )
                
                ExerciseType.OLYMPIC_LIFT -> ExerciseCapabilities(
                    supportsWeight = true,
                    supportsTime = false,
                    supportsDistance = false,
                    requiredFields = setOf(ExerciseField.REPS, ExerciseField.WEIGHT) // Technical lifts with weight
                )
                
                ExerciseType.POWERLIFTING -> ExerciseCapabilities(
                    supportsWeight = true,
                    supportsTime = false,
                    supportsDistance = false,
                    requiredFields = setOf(ExerciseField.REPS, ExerciseField.WEIGHT) // Heavy compound lifts
                )
                
                ExerciseType.ISOLATION -> ExerciseCapabilities(
                    supportsWeight = true,
                    supportsTime = false,
                    supportsDistance = false,
                    requiredFields = setOf(ExerciseField.REPS, ExerciseField.WEIGHT) // Single muscle focus
                )
                
                ExerciseType.COMPOUND -> ExerciseCapabilities(
                    supportsWeight = true,
                    supportsTime = false,
                    supportsDistance = false,
                    requiredFields = setOf(ExerciseField.REPS, ExerciseField.WEIGHT) // Multi-muscle movements
                )
            }
        }
        
        /**
         * Creates capabilities directly from ExerciseLibrary
         */
        fun fromLibraryExercise(exercise: ExerciseLibrary): ExerciseCapabilities {
            val exerciseType = ExerciseType.fromLibraryExercise(exercise)
            return fromExerciseType(exerciseType)
        }
    }
    
    /**
     * Checks if weight field should be displayed/enabled
     */
    fun supportsWeight(): Boolean = supportsWeight
    
    /**
     * Checks if time field should be displayed/enabled
     */
    fun supportsTime(): Boolean = supportsTime
    
    /**
     * Checks if distance field should be displayed/enabled
     */
    fun supportsDistance(): Boolean = supportsDistance
    
    
    /**
     * Checks if a specific field is required
     */
    fun isFieldRequired(field: ExerciseField): Boolean = field in requiredFields
}

/**
 * Enum representing different exercise input fields
 */
enum class ExerciseField {
    REPS,
    WEIGHT,
    TIME,
    DISTANCE,
    RPE
}