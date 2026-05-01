package com.example.liftrix.domain.model

/**
 * Represents the primary muscle group or exercise category classification
 * Used for exercise organization, workout planning, and analytics filtering
 */
enum class ExerciseCategory(
    val displayName: String,
    val description: String,
    val iconName: String,
    val isCompound: Boolean = false
) {
    /**
     * Chest exercises including bench press, push-ups, flyes
     */
    CHEST(
        displayName = "Chest",
        description = "Pectoral muscle exercises",
        iconName = "chest",
        isCompound = true
    ),
    
    /**
     * Back exercises including rows, pull-ups, deadlifts
     */
    BACK(
        displayName = "Back",
        description = "Latissimus dorsi and rhomboid exercises",
        iconName = "back",
        isCompound = true
    ),
    
    /**
     * Shoulder exercises including overhead press, lateral raises
     */
    SHOULDERS(
        displayName = "Shoulders",
        description = "Deltoid muscle exercises",
        iconName = "shoulders"
    ),
    
    /**
     * Arm exercises including bicep curls, tricep extensions
     */
    ARMS(
        displayName = "Arms",
        description = "Bicep and tricep exercises",
        iconName = "arms"
    ),
    
    /**
     * Bicep-specific exercises
     */
    BICEPS(
        displayName = "Biceps",
        description = "Bicep muscle exercises",
        iconName = "biceps"
    ),
    
    /**
     * Tricep-specific exercises
     */
    TRICEPS(
        displayName = "Triceps",
        description = "Tricep muscle exercises",
        iconName = "triceps"
    ),
    
    /**
     * Leg exercises including squats, lunges, leg press
     */
    LEGS(
        displayName = "Legs",
        description = "Lower body muscle exercises",
        iconName = "legs",
        isCompound = true
    ),
    
    /**
     * Quadricep-specific exercises
     */
    QUADRICEPS(
        displayName = "Quadriceps",
        description = "Front thigh muscle exercises",
        iconName = "quadriceps"
    ),
    
    /**
     * Hamstring-specific exercises
     */
    HAMSTRINGS(
        displayName = "Hamstrings",
        description = "Back thigh muscle exercises",
        iconName = "hamstrings"
    ),
    
    /**
     * Glute-specific exercises
     */
    GLUTES(
        displayName = "Glutes",
        description = "Gluteal muscle exercises",
        iconName = "glutes"
    ),
    
    /**
     * Calf-specific exercises
     */
    CALVES(
        displayName = "Calves",
        description = "Calf muscle exercises",
        iconName = "calves"
    ),
    
    /**
     * Core and abdominal exercises
     */
    CORE(
        displayName = "Core",
        description = "Abdominal and core stability exercises",
        iconName = "core"
    ),
    
    /**
     * Abdominal-specific exercises
     */
    ABS(
        displayName = "Abs",
        description = "Abdominal muscle exercises",
        iconName = "abs"
    ),
    
    /**
     * Cardiovascular exercises
     */
    CARDIO(
        displayName = "Cardio",
        description = "Cardiovascular and endurance exercises",
        iconName = "cardio"
    ),
    
    /**
     * Full body compound exercises
     */
    FULL_BODY(
        displayName = "Full Body",
        description = "Multi-muscle group compound exercises",
        iconName = "full_body",
        isCompound = true
    ),
    
    /**
     * Flexibility and mobility exercises
     */
    FLEXIBILITY(
        displayName = "Flexibility",
        description = "Stretching and mobility exercises",
        iconName = "flexibility"
    ),
    
    /**
     * Other or uncategorized exercises
     */
    OTHER(
        displayName = "Other",
        description = "Miscellaneous exercises",
        iconName = "other"
    );
    
    /**
     * Gets the estimated calorie burn rate per minute for this exercise category
     * Based on average effort for a 70kg person
     */
    fun getCalorieBurnRate(): Double = when (this) {
        CARDIO -> 8.0
        FULL_BODY -> 6.5
        LEGS -> 6.0
        BACK -> 5.5
        CHEST -> 5.0
        SHOULDERS -> 4.5
        CORE, ABS -> 4.0
        ARMS, BICEPS, TRICEPS -> 3.5
        QUADRICEPS, HAMSTRINGS, GLUTES -> 5.5
        CALVES -> 3.0
        FLEXIBILITY -> 2.0
        OTHER -> 4.0
    }
    
    /**
     * Gets the typical rest time between sets for this exercise category
     */
    fun getTypicalRestTime(): Int = when (this) {
        LEGS, BACK, CHEST, FULL_BODY -> 120 // Compound movements need more rest
        SHOULDERS -> 90
        ARMS, BICEPS, TRICEPS, QUADRICEPS, HAMSTRINGS, GLUTES -> 60
        CORE, ABS, CALVES -> 45
        CARDIO -> 30
        FLEXIBILITY -> 15
        OTHER -> 60
    }
    
    /**
     * Gets the recommended number of sets for this exercise category
     */
    fun getRecommendedSets(): IntRange = when (this) {
        LEGS, BACK, CHEST -> 3..5 // Major compound movements
        SHOULDERS, ARMS, BICEPS, TRICEPS -> 2..4
        QUADRICEPS, HAMSTRINGS, GLUTES -> 3..4
        CORE, ABS -> 2..3
        CALVES -> 3..4
        CARDIO -> 1..1 // Usually time-based
        FULL_BODY -> 3..4
        FLEXIBILITY -> 1..2
        OTHER -> 2..4
    }
    
    /**
     * Checks if this category represents a major muscle group
     */
    fun isMajorMuscleGroup(): Boolean = when (this) {
        CHEST, BACK, LEGS, SHOULDERS -> true
        else -> false
    }
    
    /**
     * Gets the muscle group priority for workout planning
     * Higher priority = should be trained earlier in workout
     */
    fun getPriority(): Int = when (this) {
        LEGS -> 1 // Legs first due to high energy demand
        BACK -> 2
        CHEST -> 3
        SHOULDERS -> 4
        ARMS, BICEPS, TRICEPS -> 5
        QUADRICEPS, HAMSTRINGS, GLUTES -> 6
        CORE, ABS -> 7
        CALVES -> 8
        CARDIO -> 9 // Often done last or separately
        FULL_BODY -> 1 // High priority due to compound nature
        FLEXIBILITY -> 10 // Usually done last
        OTHER -> 6
    }
}

/**
 * Extension functions for ExerciseCategory collections
 */
object ExerciseCategoryUtils {
    /**
     * Gets all compound exercise categories
     */
    fun getCompoundCategories(): List<ExerciseCategory> = 
        ExerciseCategory.values().filter { it.isCompound }
    
    /**
     * Gets all isolation exercise categories
     */
    fun getIsolationCategories(): List<ExerciseCategory> = 
        ExerciseCategory.values().filter { !it.isCompound }
    
    /**
     * Gets categories sorted by training priority
     */
    fun getByPriority(): List<ExerciseCategory> = 
        ExerciseCategory.values().sortedBy { it.getPriority() }
    
    /**
     * Gets muscle group categories only (excludes cardio, flexibility, etc.)
     */
    fun getMuscleGroups(): List<ExerciseCategory> = 
        ExerciseCategory.values().filter { 
            it !in listOf(ExerciseCategory.CARDIO, ExerciseCategory.FLEXIBILITY, ExerciseCategory.OTHER)
        }
    
    /**
     * Gets categories suitable for upper body workouts
     */
    fun getUpperBodyCategories(): List<ExerciseCategory> = listOf(
        ExerciseCategory.CHEST, ExerciseCategory.BACK, ExerciseCategory.SHOULDERS, 
        ExerciseCategory.ARMS, ExerciseCategory.BICEPS, ExerciseCategory.TRICEPS
    )
    
    /**
     * Gets categories suitable for lower body workouts
     */
    fun getLowerBodyCategories(): List<ExerciseCategory> = listOf(
        ExerciseCategory.LEGS, ExerciseCategory.QUADRICEPS, ExerciseCategory.HAMSTRINGS, 
        ExerciseCategory.GLUTES, ExerciseCategory.CALVES
    )
    
    /**
     * Gets the opposing muscle group for balanced training
     */
    fun getOpposingGroup(category: ExerciseCategory): ExerciseCategory? = when (category) {
        ExerciseCategory.CHEST -> ExerciseCategory.BACK
        ExerciseCategory.BACK -> ExerciseCategory.CHEST
        ExerciseCategory.BICEPS -> ExerciseCategory.TRICEPS
        ExerciseCategory.TRICEPS -> ExerciseCategory.BICEPS
        ExerciseCategory.QUADRICEPS -> ExerciseCategory.HAMSTRINGS
        ExerciseCategory.HAMSTRINGS -> ExerciseCategory.QUADRICEPS
        else -> null
    }
}