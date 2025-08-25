package com.example.liftrix.domain.model

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Represents a potential anomaly detected during workout logging
 * 
 * This model captures suspicious data entries that may be typos or errors,
 * allowing users to confirm or correct their inputs.
 */
data class WorkoutAnomaly(
    val id: String = generateAnomalyId(),
    val userId: String,
    val sessionId: String,
    val exerciseId: ExerciseId,
    val exerciseName: String,
    val anomalyType: AnomalyType,
    val currentValue: AnomalyValue,
    val previousValue: AnomalyValue? = null,
    val confidenceScore: Float,
    val detectedAt: Instant = Instant.now(),
    val resolvedAt: Instant? = null,
    val userAction: UserAnomalyAction? = null,
    val correctedValue: AnomalyValue? = null
) {
    
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(sessionId.isNotBlank()) { "Session ID cannot be blank" }
        require(exerciseName.isNotBlank()) { "Exercise name cannot be blank" }
        require(confidenceScore in 0.0f..1.0f) { "Confidence score must be between 0 and 1" }
    }

    companion object {
        private fun generateAnomalyId(): String = "anomaly_${System.currentTimeMillis()}_${(100..999).random()}"
        
        const val HIGH_CONFIDENCE_THRESHOLD = 0.8f
        const val MEDIUM_CONFIDENCE_THRESHOLD = 0.6f
    }

    /**
     * Checks if this is a high-confidence anomaly requiring user attention
     */
    fun isHighConfidence(): Boolean = confidenceScore >= HIGH_CONFIDENCE_THRESHOLD

    /**
     * Checks if this anomaly is resolved
     */
    fun isResolved(): Boolean = userAction != null && resolvedAt != null

    /**
     * Resolves the anomaly with user action
     */
    fun resolve(action: UserAnomalyAction, correctedValue: AnomalyValue? = null): WorkoutAnomaly {
        require(!isResolved()) { "Anomaly is already resolved" }
        
        return copy(
            userAction = action,
            correctedValue = if (action == UserAnomalyAction.CORRECTED) correctedValue else null,
            resolvedAt = Instant.now()
        )
    }

    /**
     * Gets a user-friendly description of the anomaly
     */
    fun getDescription(): String {
        return when (anomalyType) {
            AnomalyType.WEIGHT_SPIKE -> {
                val current = currentValue as AnomalyValue.WeightValue
                val previous = previousValue as? AnomalyValue.WeightValue
                val previousText = previous?.let { " (was ${it.value} ${it.unit})" } ?: ""
                "Weight increased significantly to ${current.value} ${current.unit}$previousText"
            }
            AnomalyType.REPS_SPIKE -> {
                val current = currentValue as AnomalyValue.RepsValue
                val previous = previousValue as? AnomalyValue.RepsValue
                val previousText = previous?.let { " (was ${it.value})" } ?: ""
                "Reps increased significantly to ${current.value}$previousText"
            }
            AnomalyType.DURATION_SPIKE -> {
                val current = currentValue as AnomalyValue.DurationValue
                val previous = previousValue as? AnomalyValue.DurationValue
                val previousText = previous?.let { " (was ${it.seconds}s)" } ?: ""
                "Duration increased significantly to ${current.seconds}s$previousText"
            }
            AnomalyType.IMPOSSIBLE_VALUE -> {
                "Value appears to be impossible for this exercise type"
            }
        }
    }

    /**
     * Gets a user-friendly confirmation prompt
     */
    fun getConfirmationPrompt(): String {
        return when (anomalyType) {
            AnomalyType.WEIGHT_SPIKE -> {
                val current = currentValue as AnomalyValue.WeightValue
                "That's a big jump! Did you mean to enter ${current.value} ${current.unit}?"
            }
            AnomalyType.REPS_SPIKE -> {
                val current = currentValue as AnomalyValue.RepsValue
                "Wow, that's a lot of reps! Did you mean to enter ${current.value}?"
            }
            AnomalyType.DURATION_SPIKE -> {
                val current = currentValue as AnomalyValue.DurationValue
                val minutes = current.seconds / 60
                val seconds = current.seconds % 60
                "That's a long time! Did you mean ${minutes}:${seconds.toString().padStart(2, '0')}?"
            }
            AnomalyType.IMPOSSIBLE_VALUE -> {
                "This value seems unusual for this exercise. Please double-check."
            }
        }
    }
}

/**
 * Types of anomalies that can be detected
 */
enum class AnomalyType {
    WEIGHT_SPIKE,      // Sudden increase in weight (e.g., 10x jump)
    REPS_SPIKE,        // Dramatic increase in reps
    DURATION_SPIKE,    // Unusually long exercise duration
    IMPOSSIBLE_VALUE   // Physically impossible or highly unlikely values
}

/**
 * User actions in response to anomaly detection
 */
enum class UserAnomalyAction {
    CONFIRMED,    // User confirmed the value is correct
    CORRECTED,    // User provided a corrected value
    DISMISSED     // User dismissed the warning
}

/**
 * Represents different types of values that can have anomalies
 */
@Serializable
sealed class AnomalyValue {
    @Serializable
    data class WeightValue(val value: Double, val unit: String = "lbs") : AnomalyValue()
    @Serializable
    data class RepsValue(val value: Int) : AnomalyValue()
    @Serializable
    data class DurationValue(val seconds: Long) : AnomalyValue()
}

/**
 * Settings for anomaly detection sensitivity
 */
data class AnomalyDetectionSettings(
    val userId: String,
    val weightSpikeThreshold: Float = 3.0f,    // 3x increase triggers warning
    val repsSpikeThreshold: Float = 2.0f,      // 2x increase triggers warning
    val durationSpikeThreshold: Float = 3.0f,  // 3x increase triggers warning
    val minWeightForDetection: Double = 5.0,   // Don't detect anomalies below 5 lbs
    val minRepsForDetection: Int = 1,          // Don't detect anomalies below 1 rep
    val minDurationForDetection: Long = 5,     // Don't detect anomalies below 5 seconds
    val learningEnabled: Boolean = true,       // Whether to learn from user patterns
    val lastUpdated: Instant = Instant.now()
) {
    
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(weightSpikeThreshold > 1.0f) { "Weight spike threshold must be greater than 1" }
        require(repsSpikeThreshold > 1.0f) { "Reps spike threshold must be greater than 1" }
        require(durationSpikeThreshold > 1.0f) { "Duration spike threshold must be greater than 1" }
    }

    companion object {
        /**
         * Creates default settings for a user
         */
        fun createDefault(userId: String): AnomalyDetectionSettings {
            return AnomalyDetectionSettings(userId = userId)
        }
    }

    /**
     * Adjusts sensitivity based on user feedback patterns
     */
    fun adjustSensitivity(
        confirmedAnomalies: Int,
        dismissedAnomalies: Int
    ): AnomalyDetectionSettings {
        if (!learningEnabled) return this
        
        val totalFeedback = confirmedAnomalies + dismissedAnomalies
        if (totalFeedback < 5) return this // Need enough data to adjust
        
        val confirmationRate = confirmedAnomalies.toFloat() / totalFeedback
        
        // If user dismisses most anomalies, make detection less sensitive
        // If user confirms most anomalies, make detection more sensitive
        val sensitivityAdjustment = when {
            confirmationRate > 0.8f -> 0.9f  // More sensitive
            confirmationRate < 0.3f -> 1.1f  // Less sensitive
            else -> 1.0f // No change
        }
        
        return copy(
            weightSpikeThreshold = (weightSpikeThreshold * sensitivityAdjustment).coerceIn(1.5f, 5.0f),
            repsSpikeThreshold = (repsSpikeThreshold * sensitivityAdjustment).coerceIn(1.5f, 3.0f),
            durationSpikeThreshold = (durationSpikeThreshold * sensitivityAdjustment).coerceIn(2.0f, 5.0f),
            lastUpdated = Instant.now()
        )
    }
}

/**
 * Historical data for anomaly detection pattern learning
 */
data class ExerciseHistory(
    val exerciseId: ExerciseId,
    val userId: String,
    val recentWeights: List<Double> = emptyList(),
    val recentReps: List<Int> = emptyList(),
    val recentDurations: List<Long> = emptyList(),
    val lastPerformed: Instant? = null,
    val averageWeight: Double = 0.0,
    val averageReps: Double = 0.0,
    val averageDuration: Double = 0.0,
    val maxWeight: Double = 0.0,
    val maxReps: Int = 0,
    val maxDuration: Long = 0
) {
    
    companion object {
        const val MAX_HISTORY_SIZE = 20 // Keep last 20 entries for pattern detection
    }

    /**
     * Updates history with new performance data
     */
    fun updateWith(weight: Double?, reps: Int?, duration: Long?): ExerciseHistory {
        val updatedWeights = weight?.let { w ->
            (recentWeights + w).takeLast(MAX_HISTORY_SIZE)
        } ?: recentWeights
        
        val updatedReps = reps?.let { r ->
            (recentReps + r).takeLast(MAX_HISTORY_SIZE)
        } ?: recentReps
        
        val updatedDurations = duration?.let { d ->
            (recentDurations + d).takeLast(MAX_HISTORY_SIZE)
        } ?: recentDurations
        
        return copy(
            recentWeights = updatedWeights,
            recentReps = updatedReps,
            recentDurations = updatedDurations,
            lastPerformed = Instant.now(),
            averageWeight = if (updatedWeights.isNotEmpty()) updatedWeights.average() else 0.0,
            averageReps = if (updatedReps.isNotEmpty()) updatedReps.average() else 0.0,
            averageDuration = if (updatedDurations.isNotEmpty()) updatedDurations.average() else 0.0,
            maxWeight = updatedWeights.maxOrNull() ?: 0.0,
            maxReps = updatedReps.maxOrNull() ?: 0,
            maxDuration = updatedDurations.maxOrNull() ?: 0
        )
    }

    /**
     * Checks if a weight value is an anomaly
     */
    fun isWeightAnomaly(weight: Double, settings: AnomalyDetectionSettings): Boolean {
        if (recentWeights.isEmpty() || weight < settings.minWeightForDetection) return false
        
        val recentAverage = recentWeights.takeLast(5).average()
        val maxRecent = recentWeights.takeLast(5).maxOrNull() ?: 0.0
        
        return weight > recentAverage * settings.weightSpikeThreshold ||
               weight > maxRecent * settings.weightSpikeThreshold
    }

    /**
     * Checks if a reps value is an anomaly
     */
    fun isRepsAnomaly(reps: Int, settings: AnomalyDetectionSettings): Boolean {
        if (recentReps.isEmpty() || reps < settings.minRepsForDetection) return false
        
        val recentAverage = recentReps.takeLast(5).average()
        val maxRecent = recentReps.takeLast(5).maxOrNull() ?: 0
        
        return reps > recentAverage * settings.repsSpikeThreshold ||
               reps > maxRecent * settings.repsSpikeThreshold
    }

    /**
     * Checks if a duration value is an anomaly
     */
    fun isDurationAnomaly(duration: Long, settings: AnomalyDetectionSettings): Boolean {
        if (recentDurations.isEmpty() || duration < settings.minDurationForDetection) return false
        
        val recentAverage = recentDurations.takeLast(5).average()
        val maxRecent = recentDurations.takeLast(5).maxOrNull() ?: 0L
        
        return duration > recentAverage * settings.durationSpikeThreshold ||
               duration > maxRecent * settings.durationSpikeThreshold
    }
}