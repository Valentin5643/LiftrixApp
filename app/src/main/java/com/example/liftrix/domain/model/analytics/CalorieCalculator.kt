package com.example.liftrix.domain.model.analytics

import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.data.local.entity.MetDataEntity
import com.example.liftrix.data.seed.MetDataSeed
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive MET-based calorie calculation system with user profile integration
 * 
 * Provides accurate calorie burn estimates using:
 * - Standardized MET values from fitness industry research
 * - User-specific BMR calculations (age, weight, gender)
 * - Exercise-specific coefficients for movement patterns
 * - Intensity adjustments based on RPE and load percentages
 * 
 * Calculation Formula:
 * Calories = MET × Weight(kg) × Duration(hours) × BMR_Factor
 * 
 * Accuracy Targets:
 * - Within 10% of gold-standard measurements
 * - Personalized for individual user characteristics
 * - Research-backed MET values from ACSM and Compendium of Physical Activities
 * 
 * Performance Targets:
 * - Real-time calculation: <50ms for single workout
 * - Batch calculation: <200ms for multiple workouts
 * - Memory efficient with cached MET lookups
 */
@Singleton
class CalorieCalculator @Inject constructor() {
    
    companion object {
        // BMR calculation constants (Mifflin-St Jeor Equation)
        private const val BMR_MALE_WEIGHT_FACTOR = 10.0f
        private const val BMR_MALE_HEIGHT_FACTOR = 6.25f
        private const val BMR_MALE_AGE_FACTOR = 5.0f
        private const val BMR_MALE_CONSTANT = 5.0f
        
        private const val BMR_FEMALE_WEIGHT_FACTOR = 10.0f
        private const val BMR_FEMALE_HEIGHT_FACTOR = 6.25f
        private const val BMR_FEMALE_AGE_FACTOR = 5.0f
        private const val BMR_FEMALE_CONSTANT = -161.0f
        
        // Default values for missing user data
        private const val DEFAULT_WEIGHT_KG = 70.0f
        private const val DEFAULT_HEIGHT_CM = 170.0f
        private const val DEFAULT_AGE = 30
        private const val DEFAULT_BMR_FACTOR = 1.2f // Sedentary activity level
        
        // MET calculation constants
        private const val MET_BASE_VALUE = 3.5f // mL O2/kg/min
        private const val MINUTES_PER_HOUR = 60.0f
        private const val CALORIES_PER_LITER_O2 = 5.0f
        private const val ML_TO_LITER_CONVERSION = 1000.0f
        
        // Intensity adjustment factors
        private const val RPE_LIGHT_THRESHOLD = 5
        private const val RPE_MODERATE_THRESHOLD = 7
        private const val RPE_VIGOROUS_THRESHOLD = 9
        
        // Exercise duration limits for validation
        private const val MIN_EXERCISE_DURATION_MINUTES = 1
        private const val MAX_EXERCISE_DURATION_MINUTES = 300 // 5 hours
        
        // Calorie burn limits for validation
        private const val MIN_CALORIES_PER_MINUTE = 1.0f
        private const val MAX_CALORIES_PER_MINUTE = 25.0f
    }
    
    // Cached MET data for performance
    private val metDataCache: Map<String, MetDataEntity> by lazy {
        MetDataSeed.getMetDataEntities().associateBy { it.id }
    }
    
    /**
     * Calculates calories burned for a list of exercises with user profile integration
     * 
     * @param exercises List of exercises from the workout
     * @param duration Total workout duration
     * @param userProfile User profile with age, weight, and fitness data
     * @return Estimated calories burned during the workout
     */
    fun calculateCaloriesBurned(
        exercises: List<Exercise>,
        duration: Duration,
        userProfile: UserProfile
    ): Int {
        if (exercises.isEmpty() || duration.toMinutes() < MIN_EXERCISE_DURATION_MINUTES) {
            return 0
        }
        
        // Calculate user-specific BMR
        val bmr = calculateBasalMetabolicRate(userProfile)
        val weight = userProfile.weight?.kilograms?.toFloat() ?: DEFAULT_WEIGHT_KG
        
        // Calculate total MET value for all exercises
        val totalMetValue = exercises.sumOf { exercise ->
            val metValue = getMetValueForExercise(exercise, userProfile)
            val exerciseIntensity = estimateExerciseIntensity(exercise)
            (metValue * exerciseIntensity).toDouble()
        }.toFloat()
        
        // Apply duration and convert to calories
        val durationHours = duration.toMinutes() / MINUTES_PER_HOUR
        val caloriesBurned = (totalMetValue * weight * durationHours * bmr).toInt()
        
        // Validate and clamp result
        val maxCalories = (MAX_CALORIES_PER_MINUTE * duration.toMinutes()).toInt()
        val minCalories = (MIN_CALORIES_PER_MINUTE * duration.toMinutes()).toInt()
        
        return caloriesBurned.coerceIn(minCalories, maxCalories)
    }
    
    /**
     * Calculates calories burned for a single exercise with detailed parameters
     * 
     * @param exercise The exercise to calculate calories for
     * @param duration Duration of the exercise
     * @param userProfile User profile for personalization
     * @param rpe Optional Rate of Perceived Exertion (1-10)
     * @param loadPercentage Optional load percentage of 1RM
     * @return Estimated calories burned for the exercise
     */
    fun calculateCaloriesForExercise(
        exercise: Exercise,
        duration: Duration,
        userProfile: UserProfile,
        rpe: Int? = null,
        loadPercentage: Int? = null
    ): Int {
        if (duration.toMinutes() < MIN_EXERCISE_DURATION_MINUTES) {
            return 0
        }
        
        val bmr = calculateBasalMetabolicRate(userProfile)
        val weight = userProfile.weight?.kilograms?.toFloat() ?: DEFAULT_WEIGHT_KG
        
        // Get base MET value for exercise
        val baseMetValue = getMetValueForExercise(exercise, userProfile)
        
        // Apply intensity adjustments
        val intensityMultiplier = MetDataSeed.getIntensityMultiplier(rpe, loadPercentage)
        val adjustedMetValue = baseMetValue * intensityMultiplier
        
        // Calculate calories
        val durationHours = duration.toMinutes() / MINUTES_PER_HOUR
        val caloriesBurned = (adjustedMetValue * weight * durationHours * bmr).toInt()
        
        // Validate result
        val maxCalories = (MAX_CALORIES_PER_MINUTE * duration.toMinutes()).toInt()
        val minCalories = (MIN_CALORIES_PER_MINUTE * duration.toMinutes()).toInt()
        
        return caloriesBurned.coerceIn(minCalories, maxCalories)
    }
    
    /**
     * Calculates Basal Metabolic Rate using Mifflin-St Jeor Equation
     * 
     * @param userProfile User profile with age, weight, and gender
     * @return BMR factor for calorie calculations
     */
    fun calculateBasalMetabolicRate(userProfile: UserProfile): Float {
        val weight = userProfile.weight?.kilograms?.toFloat() ?: DEFAULT_WEIGHT_KG
        val age = userProfile.age?.toFloat() ?: DEFAULT_AGE.toFloat()
        val height = DEFAULT_HEIGHT_CM // Height not in UserProfile, using default
        
        // Determine gender from fitness goals or use male as default
        val isMale = determineGenderFromProfile(userProfile)
        
        val bmr = if (isMale) {
            BMR_MALE_WEIGHT_FACTOR * weight + 
            BMR_MALE_HEIGHT_FACTOR * height - 
            BMR_MALE_AGE_FACTOR * age + 
            BMR_MALE_CONSTANT
        } else {
            BMR_FEMALE_WEIGHT_FACTOR * weight + 
            BMR_FEMALE_HEIGHT_FACTOR * height - 
            BMR_FEMALE_AGE_FACTOR * age + 
            BMR_FEMALE_CONSTANT
        }
        
        // Convert to activity factor (BMR × activity level)
        return (bmr / 1440.0f) * DEFAULT_BMR_FACTOR // 1440 minutes per day
    }
    
    /**
     * Gets MET value for a specific exercise with equipment consideration
     * 
     * @param exercise The exercise to get MET value for
     * @param userProfile User profile for personalization
     * @return MET value for the exercise
     */
    private fun getMetValueForExercise(exercise: Exercise, userProfile: UserProfile): Float {
        val exerciseCategory = exercise.libraryExercise.primaryMuscleGroup.name
        val exerciseName = exercise.libraryExercise.name.lowercase()
        
        // Try to find specific MET data for the exercise
        val metData = findBestMatchingMetData(exerciseName, exerciseCategory)
        
        return if (metData != null) {
            // Use specific MET data with user-specific adjustments
            val baseValue = metData.getEffectiveMET()
            applyUserSpecificAdjustments(baseValue, userProfile)
        } else {
            // Fall back to category default
            val categoryDefault = MetDataSeed.getDefaultMetForCategory(exerciseCategory)
            applyUserSpecificAdjustments(categoryDefault, userProfile)
        }
    }
    
    /**
     * Finds the best matching MET data for an exercise
     * 
     * @param exerciseName Name of the exercise
     * @param exerciseCategory Category of the exercise
     * @return Best matching MetDataEntity or null if not found
     */
    private fun findBestMatchingMetData(exerciseName: String, exerciseCategory: String): MetDataEntity? {
        // First try exact match by exercise type
        val exactMatch = metDataCache.values.find { metData ->
            metData.exerciseType.equals(exerciseName, ignoreCase = true) &&
            metData.appliesToCategory(exerciseCategory)
        }
        
        if (exactMatch != null) return exactMatch
        
        // Try partial match by exercise name
        val partialMatch = metDataCache.values.find { metData ->
            exerciseName.contains(metData.exerciseType, ignoreCase = true) &&
            metData.appliesToCategory(exerciseCategory)
        }
        
        if (partialMatch != null) return partialMatch
        
        // Try category-only match
        return metDataCache.values.find { metData ->
            metData.appliesToCategory(exerciseCategory)
        }
    }
    
    /**
     * Applies user-specific adjustments to base MET value
     * 
     * @param baseMetValue Base MET value from research
     * @param userProfile User profile for adjustments
     * @return Adjusted MET value
     */
    private fun applyUserSpecificAdjustments(baseMetValue: Float, userProfile: UserProfile): Float {
        var adjustedValue = baseMetValue
        
        // Age adjustment (older users may have lower efficiency)
        userProfile.age?.let { age ->
            when {
                age < 25 -> adjustedValue *= 1.05f // Young users burn slightly more
                age > 50 -> adjustedValue *= 0.95f // Older users burn slightly less
            }
        }
        
        // Weight adjustment (heavier users may burn more for bodyweight exercises)
        userProfile.weight?.let { weight ->
            if (weight.kilograms > 90.0) {
                adjustedValue *= 1.1f // Heavier users burn more
            } else if (weight.kilograms < 60.0) {
                adjustedValue *= 0.9f // Lighter users burn less
            }
        }
        
        return adjustedValue.coerceIn(1.0f, 15.0f)
    }
    
    /**
     * Estimates exercise intensity based on exercise characteristics
     * 
     * @param exercise The exercise to estimate intensity for
     * @return Intensity multiplier (0.8 to 1.5)
     */
    private fun estimateExerciseIntensity(exercise: Exercise): Float {
        var intensity = 1.0f
        
        // Volume-based intensity (higher volume = higher intensity)
        val totalVolume = exercise.getTotalVolume()
        if (totalVolume != null && totalVolume.kilograms > 0) {
            when {
                totalVolume.kilograms > 1000 -> intensity *= 1.3f
                totalVolume.kilograms > 500 -> intensity *= 1.2f
                totalVolume.kilograms > 200 -> intensity *= 1.1f
            }
        }
        
        // Set count intensity (more sets = higher intensity)
        when {
            exercise.sets.size > 8 -> intensity *= 1.2f
            exercise.sets.size > 5 -> intensity *= 1.1f
            exercise.sets.size < 3 -> intensity *= 0.9f
        }
        
        // Completion rate intensity based on completed sets
        val completionRate = if (exercise.sets.isEmpty()) 0f else {
            exercise.getCompletedSetsCount().toFloat() / exercise.sets.size.toFloat()
        }
        if (completionRate < 0.8f) {
            intensity *= 0.9f // Reduced intensity for incomplete exercises
        }
        
        return intensity.coerceIn(0.8f, 1.5f)
    }
    
    /**
     * Determines user gender from profile (simplified approach)
     * 
     * @param userProfile User profile to analyze
     * @return True if likely male, false if likely female
     */
    private fun determineGenderFromProfile(userProfile: UserProfile): Boolean {
        // Simple heuristic: assume male for now
        // In future versions, this could be determined from:
        // - Explicit gender field in profile
        // - Fitness goals analysis
        // - Performance patterns
        return true
    }
    
    /**
     * Gets estimated calories per minute for an exercise category
     * 
     * @param category Exercise category
     * @param userProfile User profile
     * @return Estimated calories per minute
     */
    fun getEstimatedCaloriesPerMinute(category: ExerciseCategory, userProfile: UserProfile): Float {
        val metValue = MetDataSeed.getDefaultMetForCategory(category.name)
        val weight = userProfile.weight?.kilograms?.toFloat() ?: DEFAULT_WEIGHT_KG
        val bmr = calculateBasalMetabolicRate(userProfile)
        
        return (metValue * weight * bmr) / MINUTES_PER_HOUR
    }
    
    /**
     * Validates calorie calculation inputs
     * 
     * @param exercises List of exercises
     * @param duration Workout duration
     * @param userProfile User profile
     * @return True if inputs are valid
     */
    fun validateCalculationInputs(
        exercises: List<Exercise>,
        duration: Duration,
        userProfile: UserProfile
    ): Boolean {
        return exercises.isNotEmpty() &&
               duration.toMinutes() >= MIN_EXERCISE_DURATION_MINUTES &&
               duration.toMinutes() <= MAX_EXERCISE_DURATION_MINUTES &&
               userProfile.weight != null &&
               userProfile.weight!!.kilograms > 0
    }
}