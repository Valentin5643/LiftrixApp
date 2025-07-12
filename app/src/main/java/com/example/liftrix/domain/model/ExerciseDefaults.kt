package com.example.liftrix.domain.model

import kotlinx.serialization.Serializable

/**
 * Domain model representing intelligent default values for exercise parameters
 * Used by the smart defaults system to suggest optimal sets, reps, weight, and rest time
 */
@Serializable
data class ExerciseDefaults(
    val sets: Int,
    val reps: Reps,
    val weight: Weight? = null,
    val restTimeSeconds: Int,
    val source: DefaultSource
) {
    init {
        require(sets in MIN_SETS..MAX_SETS) { 
            "Sets must be between $MIN_SETS and $MAX_SETS: $sets" 
        }
        require(restTimeSeconds in MIN_REST_SECONDS..MAX_REST_SECONDS) { 
            "Rest time must be between $MIN_REST_SECONDS and $MAX_REST_SECONDS seconds: $restTimeSeconds" 
        }
    }
    
    companion object {
        const val MIN_SETS = 1
        const val MAX_SETS = 10
        const val MIN_REST_SECONDS = 30
        const val MAX_REST_SECONDS = 300 // 5 minutes
        
        /**
         * Creates fallback defaults for when no history or exercise type data is available
         */
        fun createFallback(): ExerciseDefaults {
            return ExerciseDefaults(
                sets = 3,
                reps = Reps(10),
                weight = null,
                restTimeSeconds = 90,
                source = DefaultSource.FALLBACK
            )
        }
        
        /**
         * Creates defaults based on exercise type characteristics
         */
        fun fromExerciseType(
            exerciseType: ExerciseType,
            primaryMuscle: ExerciseCategory,
            isCompound: Boolean = false
        ): ExerciseDefaults {
            return when (exerciseType) {
                ExerciseType.WEIGHT_BASED -> createWeightBasedDefaults(primaryMuscle, isCompound)
                ExerciseType.BODYWEIGHT -> createBodyweightDefaults(primaryMuscle)
                ExerciseType.TIME_BASED -> createTimeBasedDefaults()
                ExerciseType.DISTANCE_BASED -> createDistanceBasedDefaults()
                ExerciseType.CARDIO -> createCardioDefaults()
                else -> createStrengthDefaults(primaryMuscle, isCompound)
            }
        }
        
        private fun createWeightBasedDefaults(
            primaryMuscle: ExerciseCategory,
            isCompound: Boolean
        ): ExerciseDefaults {
            return if (isCompound) {
                // Compound movements: lower reps, longer rest
                ExerciseDefaults(
                    sets = 4,
                    reps = Reps(6),
                    weight = null,
                    restTimeSeconds = 120,
                    source = DefaultSource.EXERCISE_TYPE
                )
            } else {
                // Isolation movements: moderate reps, standard rest
                ExerciseDefaults(
                    sets = 3,
                    reps = Reps(10),
                    weight = null,
                    restTimeSeconds = when (primaryMuscle) {
                        ExerciseCategory.ARMS, ExerciseCategory.BICEPS, ExerciseCategory.TRICEPS -> 60
                        ExerciseCategory.SHOULDERS -> 75
                        else -> 90
                    },
                    source = DefaultSource.EXERCISE_TYPE
                )
            }
        }
        
        private fun createBodyweightDefaults(primaryMuscle: ExerciseCategory): ExerciseDefaults {
            return ExerciseDefaults(
                sets = 3,
                reps = when (primaryMuscle) {
                    ExerciseCategory.CORE -> Reps(20)
                    ExerciseCategory.LEGS -> Reps(15)
                    else -> Reps(12)
                },
                weight = null,
                restTimeSeconds = 60,
                source = DefaultSource.EXERCISE_TYPE
            )
        }
        
        private fun createTimeBasedDefaults(): ExerciseDefaults {
            return ExerciseDefaults(
                sets = 3,
                reps = Reps(30), // 30 seconds per set
                weight = null,
                restTimeSeconds = 60,
                source = DefaultSource.EXERCISE_TYPE
            )
        }
        
        private fun createDistanceBasedDefaults(): ExerciseDefaults {
            return ExerciseDefaults(
                sets = 1,
                reps = Reps(1), // Distance is the primary metric
                weight = null,
                restTimeSeconds = 180,
                source = DefaultSource.EXERCISE_TYPE
            )
        }
        
        private fun createCardioDefaults(): ExerciseDefaults {
            return ExerciseDefaults(
                sets = 1,
                reps = Reps(20), // 20 minutes
                weight = null,
                restTimeSeconds = 60,
                source = DefaultSource.EXERCISE_TYPE
            )
        }
        
        private fun createStrengthDefaults(
            primaryMuscle: ExerciseCategory,
            isCompound: Boolean
        ): ExerciseDefaults {
            return if (isCompound) {
                ExerciseDefaults(
                    sets = 4,
                    reps = Reps(8),
                    weight = null,
                    restTimeSeconds = 120,
                    source = DefaultSource.EXERCISE_TYPE
                )
            } else {
                ExerciseDefaults(
                    sets = 3,
                    reps = Reps(12),
                    weight = null,
                    restTimeSeconds = 90,
                    source = DefaultSource.EXERCISE_TYPE
                )
            }
        }
    }
    
    /**
     * Creates a TemplateExercise with these defaults applied
     */
    fun applyToTemplateExercise(templateExercise: TemplateExercise): TemplateExercise {
        return templateExercise.copy(
            targetSets = sets,
            targetReps = reps,
            targetWeight = weight,
            restTimeSeconds = restTimeSeconds
        )
    }
    
    /**
     * Checks if these defaults are based on user history
     */
    fun isFromHistory(): Boolean = source == DefaultSource.HISTORY
    
    /**
     * Checks if these defaults are exercise type-based
     */
    fun isFromExerciseType(): Boolean = source == DefaultSource.EXERCISE_TYPE
    
    /**
     * Gets a human-readable description of the default source
     */
    fun getSourceDescription(): String = when (source) {
        DefaultSource.HISTORY -> "Based on your workout history"
        DefaultSource.EXERCISE_TYPE -> "Based on exercise type"
        DefaultSource.FALLBACK -> "Standard defaults"
    }
}

/**
 * Enum representing the source of exercise defaults
 */
@Serializable
enum class DefaultSource(val displayName: String) {
    HISTORY("Personal History"),
    EXERCISE_TYPE("Exercise Type"),
    FALLBACK("Standard Defaults")
} 