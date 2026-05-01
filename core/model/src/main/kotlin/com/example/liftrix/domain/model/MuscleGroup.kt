package com.example.liftrix.domain.model

/**
 * Enum representing different muscle groups for exercise categorization
 * Used for workout planning, exercise selection, and progress tracking
 */
enum class MuscleGroup(
    val displayName: String,
    val description: String
) {
    // Upper Body - Pushing
    CHEST("Chest", "Pectorals and supporting muscles"),
    SHOULDERS("Shoulders", "Deltoids and shoulder complex"),
    TRICEPS("Triceps", "Triceps brachii and arm extensors"),
    
    // Upper Body - Pulling  
    BACK("Back", "Latissimus dorsi, rhomboids, and upper back"),
    BICEPS("Biceps", "Biceps brachii and arm flexors"),
    FOREARMS("Forearms", "Forearm flexors and extensors"),
    
    // Lower Body
    QUADRICEPS("Quadriceps", "Quadriceps femoris and knee extensors"),
    HAMSTRINGS("Hamstrings", "Hamstring complex and knee flexors"),
    GLUTES("Glutes", "Gluteal muscles and hip extensors"),
    CALVES("Calves", "Gastrocnemius, soleus, and calf muscles"),
    
    // Core and Stabilizers
    CORE("Core", "Abdominals, obliques, and core stabilizers"),
    LOWER_BACK("Lower Back", "Erector spinae and lower back muscles"),
    
    // Full Body / Compound
    FULL_BODY("Full Body", "Multiple muscle groups working together"),
    
    // Specialized
    CARDIO("Cardio", "Cardiovascular and endurance training"),
    FLEXIBILITY("Flexibility", "Stretching and mobility work");
    
    companion object {
        /**
         * Gets muscle groups by training type
         */
        fun getByTrainingType(type: String): List<MuscleGroup> {
            return when (type.lowercase()) {
                "push" -> listOf(CHEST, SHOULDERS, TRICEPS)
                "pull" -> listOf(BACK, BICEPS, FOREARMS)
                "legs" -> listOf(QUADRICEPS, HAMSTRINGS, GLUTES, CALVES)
                "upper" -> listOf(CHEST, SHOULDERS, TRICEPS, BACK, BICEPS, FOREARMS)
                "lower" -> listOf(QUADRICEPS, HAMSTRINGS, GLUTES, CALVES, LOWER_BACK)
                "core" -> listOf(CORE, LOWER_BACK)
                else -> values().toList()
            }
        }
        
        /**
         * Gets primary muscle groups (excluding specialized categories)
         */
        fun getPrimaryMuscleGroups(): List<MuscleGroup> {
            return listOf(
                CHEST, SHOULDERS, TRICEPS, BACK, BICEPS, FOREARMS,
                QUADRICEPS, HAMSTRINGS, GLUTES, CALVES, CORE, LOWER_BACK
            )
        }
        
        /**
         * Gets compound movement muscle groups
         */
        fun getCompoundMuscleGroups(): List<MuscleGroup> {
            return listOf(CHEST, BACK, SHOULDERS, QUADRICEPS, HAMSTRINGS, GLUTES)
        }
        
        /**
         * Gets isolation movement muscle groups  
         */
        fun getIsolationMuscleGroups(): List<MuscleGroup> {
            return listOf(BICEPS, TRICEPS, FOREARMS, CALVES, CORE)
        }
    }
    
    /**
     * Checks if this muscle group is considered a major compound movement target
     */
    fun isCompound(): Boolean = this in getCompoundMuscleGroups()
    
    /**
     * Checks if this muscle group is typically trained with isolation exercises
     */
    fun isIsolation(): Boolean = this in getIsolationMuscleGroups()
    
    /**
     * Gets synergistic muscle groups that work together with this one
     */
    fun getSynergists(): List<MuscleGroup> {
        return when (this) {
            CHEST -> listOf(SHOULDERS, TRICEPS)
            BACK -> listOf(BICEPS, FOREARMS)
            SHOULDERS -> listOf(TRICEPS, CHEST)
            QUADRICEPS -> listOf(GLUTES, CALVES)
            HAMSTRINGS -> listOf(GLUTES, LOWER_BACK)
            GLUTES -> listOf(QUADRICEPS, HAMSTRINGS)
            else -> emptyList()
        }
    }
    
    /**
     * Gets antagonistic muscle groups (opposing muscles)
     */
    fun getAntagonists(): List<MuscleGroup> {
        return when (this) {
            CHEST -> listOf(BACK)
            BACK -> listOf(CHEST)
            BICEPS -> listOf(TRICEPS)
            TRICEPS -> listOf(BICEPS)
            QUADRICEPS -> listOf(HAMSTRINGS)
            HAMSTRINGS -> listOf(QUADRICEPS)
            else -> emptyList()
        }
    }
} 