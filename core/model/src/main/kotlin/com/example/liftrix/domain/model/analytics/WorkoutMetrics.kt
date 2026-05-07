package com.example.liftrix.domain.model.analytics

import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.ExerciseCategory
import kotlinx.datetime.LocalDate
import java.time.Duration

/**
 * Enhanced workout metrics for comprehensive analytics and progress tracking
 * 
 * Extends beyond basic workout data to provide:
 * - Advanced volume calculations with intensity weighting
 * - MET-based calorie burn estimates with user profile integration
 * - Performance indicators for training load optimization
 * - Time-based efficiency metrics and trend analysis
 * 
 * This enhanced version complements the existing WorkoutMetrics in Workout.kt
 * by providing analytics-specific calculations and real-time dashboard metrics.
 * 
 * Used by:
 * - Analytics calculation engine for widget data
 * - Dashboard metrics display components  
 * - Progress comparison and trend analysis
 * - Export system for detailed workout summaries
 */
data class WorkoutMetrics(
    val workoutId: String,
    val userId: String,
    val date: LocalDate,
    val totalVolume: Weight,
    val sessionDuration: Duration?,
    val caloriesBurned: Int,
    val exerciseCount: Int,
    val totalSets: Int,
    val completedSets: Int,
    val totalReps: Reps,
    val completionPercentage: Double,
    val averageIntensity: Float, // 0.0 to 1.0 based on RPE/load
    val volumeEfficiency: Float, // Volume per minute
    val categories: Set<ExerciseCategory>,
    val restDuration: Duration? = null,
    val workoutRating: Int? = null // 1-5 subjective rating
) {
    init {
        require(workoutId.isNotBlank()) { "Workout ID cannot be blank" }
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(caloriesBurned >= 0) { "Calories burned cannot be negative: $caloriesBurned" }
        require(exerciseCount >= 0) { "Exercise count cannot be negative: $exerciseCount" }
        require(totalSets >= 0) { "Total sets cannot be negative: $totalSets" }
        require(completedSets >= 0) { "Completed sets cannot be negative: $completedSets" }
        require(completedSets <= totalSets) { "Completed sets cannot exceed total sets: $completedSets > $totalSets" }
        require(completionPercentage in 0.0..100.0) { "Completion percentage must be 0-100: $completionPercentage" }
        require(averageIntensity in 0.0f..1.0f) { "Average intensity must be 0.0-1.0: $averageIntensity" }
        require(volumeEfficiency >= 0.0f) { "Volume efficiency cannot be negative: $volumeEfficiency" }
        workoutRating?.let { rating ->
            require(rating in 1..5) { "Workout rating must be 1-5: $rating" }
        }
    }
    
    companion object {
        const val MIN_DURATION_FOR_EFFICIENCY_CALC = 5 // Minutes
        const val MAX_REALISTIC_CALORIES_PER_HOUR = 1200
        const val AVERAGE_MET_VALUE_STRENGTH_TRAINING = 3.5f
        
        /**
         * Creates metrics from basic workout data with default calculations
         */
        fun fromBasicData(
            workoutId: String,
            userId: String,
            date: LocalDate,
            totalVolume: Weight,
            duration: Duration?,
            exerciseCount: Int,
            totalSets: Int,
            completedSets: Int,
            totalReps: Reps,
            categories: Set<ExerciseCategory>
        ): WorkoutMetrics {
            val completionPercentage = if (totalSets > 0) {
                (completedSets.toDouble() / totalSets) * 100.0
            } else 0.0
            
            val caloriesEstimate = duration?.let { d ->
                estimateCaloriesBurned(totalVolume, d, exerciseCount)
            } ?: 0
            
            val volumeEff = duration?.let { d ->
                calculateVolumeEfficiency(totalVolume, d)
            } ?: 0.0f
            
            return WorkoutMetrics(
                workoutId = workoutId,
                userId = userId,
                date = date,
                totalVolume = totalVolume,
                sessionDuration = duration,
                caloriesBurned = caloriesEstimate,
                exerciseCount = exerciseCount,
                totalSets = totalSets,
                completedSets = completedSets,
                totalReps = totalReps,
                completionPercentage = completionPercentage,
                averageIntensity = 0.5f, // Default moderate intensity
                volumeEfficiency = volumeEff,
                categories = categories
            )
        }
        
        /**
         * Estimates calories burned based on volume and duration
         */
        private fun estimateCaloriesBurned(
            volume: Weight,
            duration: Duration,
            exerciseCount: Int
        ): Int {
            val durationHours = duration.toMinutes() / 60.0
            val baseCaloriesPerHour = AVERAGE_MET_VALUE_STRENGTH_TRAINING * 60 // Assuming 60kg person
            val volumeMultiplier = 1.0f + (volume.kilograms / 1000.0f) // Slight increase for higher volume
            val exerciseVarietyMultiplier = 1.0f + (exerciseCount * 0.05f) // More exercises = higher calorie burn
            
            val estimatedCalories = (baseCaloriesPerHour * durationHours * volumeMultiplier * exerciseVarietyMultiplier).toInt()
            return estimatedCalories.coerceAtMost((MAX_REALISTIC_CALORIES_PER_HOUR * durationHours).toInt())
        }
        
        /**
         * Calculates volume efficiency (kg lifted per minute)
         */
        private fun calculateVolumeEfficiency(volume: Weight, duration: Duration): Float {
            val durationMinutes = duration.toMinutes()
            return if (durationMinutes >= MIN_DURATION_FOR_EFFICIENCY_CALC) {
                (volume.kilograms / durationMinutes).toFloat()
            } else 0.0f
        }
    }
    
    /**
     * Calculates training load score based on volume, intensity, and duration
     */
    fun calculateTrainingLoad(): Float {
        val volumeScore = (totalVolume.kilograms / 100.0).coerceAtMost(10.0) // 0-10 based on volume
        val intensityScore = averageIntensity * 5.0 // 0-5 based on intensity
        val durationScore = sessionDuration?.let { d ->
            (d.toMinutes() / 10.0).coerceAtMost(5.0) // 0-5 based on duration (10min = 1pt)
        } ?: 0.0
        
        return ((volumeScore + intensityScore + durationScore) / 3.0).toFloat()
    }
    
    /**
     * Gets workout quality score (0.0 to 1.0) based on multiple factors
     */
    fun getWorkoutQualityScore(): Float {
        val completionScore = (completionPercentage / 100.0).toFloat()
        val efficiencyScore = (volumeEfficiency / 10.0f).coerceAtMost(1.0f) // Normalize to 0-1
        val intensityScore = averageIntensity
        val varietyScore = (categories.size / 5.0f).coerceAtMost(1.0f) // Up to 5 categories
        
        return (completionScore + efficiencyScore + intensityScore + varietyScore) / 4.0f
    }
    
    /**
     * Checks if workout meets quality thresholds
     */
    fun isHighQualityWorkout(): Boolean {
        return completionPercentage >= 80.0 && 
               averageIntensity >= 0.6f && 
               exerciseCount >= 3 &&
               totalSets >= 8
    }
    
    /**
     * Gets formatted duration string
     */
    fun getFormattedDuration(): String = sessionDuration?.let { duration ->
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${duration.seconds}s"
        }
    } ?: "Unknown"
    
    /**
     * Gets formatted volume per exercise average
     */
    fun getAverageVolumePerExercise(): Weight = if (exerciseCount > 0) {
        Weight(totalVolume.kilograms / exerciseCount)
    } else Weight.ZERO
    
    /**
     * Gets formatted sets per exercise average
     */
    fun getAverageSetsPerExercise(): Float = if (exerciseCount > 0) {
        totalSets.toFloat() / exerciseCount
    } else 0.0f
    
    /**
     * Calculates volume intensity factor for color coding
     */
    fun getVolumeIntensityFactor(): Float {
        // Combine volume and intensity for comprehensive intensity measure
        val normalizedVolume = (totalVolume.kilograms / 500.0).coerceAtMost(1.0).toFloat() // 500kg = max volume factor
        return ((normalizedVolume + averageIntensity) / 2.0f).coerceIn(0.0f, 1.0f)
    }
    
    /**
     * Gets primary muscle groups worked (up to 3)
     */
    fun getPrimaryMuscleGroups(): List<ExerciseCategory> = categories.take(3)
    
    /**
     * Checks if workout focused on specific muscle group
     */
    fun isFocusedWorkout(): Boolean = categories.size <= 2
    
    /**
     * Checks if workout was full-body
     */
    fun isFullBodyWorkout(): Boolean = categories.size >= 4
    
    /**
     * Gets rest-to-work ratio if rest duration available
     */
    fun getRestToWorkRatio(): Float? = sessionDuration?.let { session ->
        restDuration?.let { rest ->
            val workDuration = session.minus(rest)
            if (workDuration.toMinutes() > 0) {
                rest.toMinutes().toFloat() / workDuration.toMinutes().toFloat()
            } else null
        }
    }
    
    /**
     * Estimates one-rep max contribution based on volume and reps
     */
    fun estimateStrengthContribution(): Float {
        // Higher volume with moderate reps suggests strength focus
        val volumeContribution = (totalVolume.kilograms / 200.0).coerceAtMost(1.0).toFloat()
        val repEfficiency = if (totalSets > 0) {
            val avgRepsPerSet = totalReps.count.toFloat() / totalSets
            when {
                avgRepsPerSet <= 5 -> 1.0f    // Strength focus
                avgRepsPerSet <= 8 -> 0.8f    // Power focus  
                avgRepsPerSet <= 12 -> 0.6f   // Hypertrophy focus
                else -> 0.4f                  // Endurance focus
            }
        } else 0.0f
        
        return (volumeContribution * repEfficiency * intensityScore).coerceIn(0.0f, 1.0f)
    }
    
    private val intensityScore: Float get() = averageIntensity
}