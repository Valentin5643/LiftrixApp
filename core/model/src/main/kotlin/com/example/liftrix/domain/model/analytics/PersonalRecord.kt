package com.example.liftrix.domain.model.analytics

import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.WorkoutId
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Personal record domain model for tracking fitness achievements and strength progression
 * 
 * Represents a single personal record achievement with comprehensive tracking:
 * - Exercise-specific record identification and categorization
 * - Weight, reps, and calculated metrics for strength assessment
 * - Temporal tracking with achievement date for progression analysis
 * - Workout context for detailed performance insights
 * - Record type classification for different achievement categories
 * 
 * Used by:
 * - StrengthMetrics for personal record tracking
 * - AnalyticsMapper for PR calculations
 * - UI components for achievement display and motivation
 * - Dashboard widgets for strength progression visualization
 */
@Serializable
data class PersonalRecord(
    val id: String, // Unique identifier for the personal record
    val userId: String, // User who achieved the record
    val exerciseId: ExerciseId, // Exercise for which the record was achieved
    val weight: Weight, // Weight lifted for the record
    val reps: Reps, // Number of repetitions performed
    val achievedDate: LocalDate, // Date when the record was achieved
    @Contextual val workoutId: WorkoutId, // Workout context in which the record was set
    val recordType: PersonalRecordType, // Type/category of the personal record
    val previousRecord: Weight?, // Previous record weight for comparison
    val estimatedOneRepMax: Weight // Calculated/estimated 1RM for this record
) {
    init {
        require(id.isNotBlank()) { "Personal record ID cannot be blank" }
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(weight > Weight.ZERO) { "Weight must be positive: $weight" }
        require(reps > Reps.ZERO) { "Reps must be positive: $reps" }
        require(estimatedOneRepMax >= weight) { "Estimated 1RM must be >= actual weight: $estimatedOneRepMax < $weight" }
        previousRecord?.let { prev ->
            require(weight > prev) { "New record weight must be greater than previous: $weight <= $prev" }
        }
    }
    
    companion object {
        /**
         * Creates a personal record from workout data
         */
        fun create(
            userId: String,
            exerciseId: ExerciseId,
            weight: Weight,
            reps: Reps,
            achievedDate: LocalDate,
            workoutId: WorkoutId,
            recordType: PersonalRecordType,
            previousRecord: Weight? = null
        ): PersonalRecord {
            val estimatedOneRepMax = calculateEstimatedOneRepMax(weight, reps)
            return PersonalRecord(
                id = generateId(),
                userId = userId,
                exerciseId = exerciseId,
                weight = weight,
                reps = reps,
                achievedDate = achievedDate,
                workoutId = workoutId,
                recordType = recordType,
                previousRecord = previousRecord,
                estimatedOneRepMax = estimatedOneRepMax
            )
        }
        
        /**
         * Calculates estimated 1RM using Brzycki formula
         */
        private fun calculateEstimatedOneRepMax(weight: Weight, reps: Reps): Weight {
            return if (reps.count == 1) {
                weight
            } else {
                // Brzycki formula: 1RM = weight × (36 / (37 - reps))
                val multiplier = 36.0 / (37.0 - reps.count)
                Weight(weight.kilograms * multiplier)
            }
        }
        
        /**
         * Generates unique ID for personal record
         */
        private fun generateId(): String = "pr_${java.util.UUID.randomUUID()}"
    }
    
    /**
     * Calculates improvement percentage from previous record
     */
    fun getImprovementPercentage(): Float? = previousRecord?.let { prev ->
        ((weight.kilograms - prev.kilograms) / prev.kilograms).toFloat()
    }
    
    /**
     * Gets improvement amount from previous record
     */
    fun getImprovementAmount(): Weight? = previousRecord?.let { prev ->
        Weight(weight.kilograms - prev.kilograms)
    }
    
    /**
     * Checks if this is a first-time record (no previous record)
     */
    fun isFirstTimeRecord(): Boolean = previousRecord == null
    
    /**
     * Gets record achievement level based on improvement
     */
    fun getAchievementLevel(): AchievementLevel = when {
        isFirstTimeRecord() -> AchievementLevel.FIRST_TIME
        getImprovementPercentage()?.let { it >= 0.2f } == true -> AchievementLevel.MAJOR_BREAKTHROUGH
        getImprovementPercentage()?.let { it >= 0.1f } == true -> AchievementLevel.SIGNIFICANT_IMPROVEMENT
        getImprovementPercentage()?.let { it >= 0.05f } == true -> AchievementLevel.GOOD_PROGRESS
        else -> AchievementLevel.MINOR_IMPROVEMENT
    }
    
    /**
     * Gets motivational message for the achievement
     */
    fun getMotivationalMessage(): String = when (getAchievementLevel()) {
        AchievementLevel.FIRST_TIME -> "Congratulations on your first personal record!"
        AchievementLevel.MAJOR_BREAKTHROUGH -> "Amazing breakthrough! You've made incredible progress!"
        AchievementLevel.SIGNIFICANT_IMPROVEMENT -> "Fantastic improvement! Your hard work is paying off!"
        AchievementLevel.GOOD_PROGRESS -> "Great progress! You're getting stronger!"
        AchievementLevel.MINOR_IMPROVEMENT -> "Nice improvement! Every step forward counts!"
    }
    
    /**
     * Gets formatted improvement string
     */
    fun getFormattedImprovement(): String = when {
        isFirstTimeRecord() -> "First PR!"
        getImprovementAmount() != null -> {
            val improvement = getImprovementAmount()!!
            val percentage = getImprovementPercentage()!!
            "+${improvement.format()} (${formatPercentage(percentage)})"
        }
        else -> "New PR!"
    }
    
    /**
     * Gets days since achievement
     */
    fun getDaysSinceAchievement(): Int {
        val today = kotlinx.datetime.LocalDate.fromEpochDays((kotlinx.datetime.Clock.System.now().epochSeconds / 86400).toInt())
        return (today.toEpochDays() - achievedDate.toEpochDays()).toInt()
    }
    
    /**
     * Checks if this is a recent achievement (within last 30 days)
     */
    fun isRecentAchievement(): Boolean = getDaysSinceAchievement() <= 30
    
    /**
     * Gets volume (weight × reps) for this record
     */
    fun getVolume(): Weight = Weight(weight.kilograms * reps.count)
    
    /**
     * Gets strength score based on estimated 1RM
     */
    fun getStrengthScore(): Float = estimatedOneRepMax.kilograms.toFloat()
    
    /**
     * Formats percentage with proper sign and symbol
     */
    private fun formatPercentage(percentage: Float): String {
        val percent = (percentage * 100).toInt()
        return "+$percent%"
    }
}

/**
 * Enum representing different types of personal records
 */
enum class PersonalRecordType(val displayName: String, val description: String) {
    ONE_REP_MAX("1RM", "One repetition maximum"),
    VOLUME_PR("Volume PR", "Highest weight × reps combination"),
    REPS_PR("Reps PR", "Most repetitions at a given weight"),
    WEIGHT_PR("Weight PR", "Heaviest weight lifted"),
    ENDURANCE_PR("Endurance PR", "Longest duration or highest reps"),
    FREQUENCY_PR("Frequency PR", "Most sets completed");
    
    /**
     * Gets the primary metric that defines this record type
     */
    fun getPrimaryMetric(): String = when (this) {
        ONE_REP_MAX -> "Weight"
        VOLUME_PR -> "Volume"
        REPS_PR -> "Repetitions"
        WEIGHT_PR -> "Weight"
        ENDURANCE_PR -> "Duration/Reps"
        FREQUENCY_PR -> "Sets"
    }
}

/**
 * Enum representing different achievement levels for personal records
 */
enum class AchievementLevel(val displayName: String, val description: String) {
    FIRST_TIME("First Time", "First personal record achieved"),
    MINOR_IMPROVEMENT("Minor Improvement", "Small improvement (<5%)"),
    GOOD_PROGRESS("Good Progress", "Solid improvement (5-10%)"),
    SIGNIFICANT_IMPROVEMENT("Significant Improvement", "Major improvement (10-20%)"),
    MAJOR_BREAKTHROUGH("Major Breakthrough", "Exceptional improvement (20%+)")
}