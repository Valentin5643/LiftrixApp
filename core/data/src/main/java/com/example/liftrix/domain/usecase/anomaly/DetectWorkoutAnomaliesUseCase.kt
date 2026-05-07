package com.example.liftrix.domain.usecase.anomaly

import com.example.liftrix.domain.model.AnomalyDetectionSettings
import com.example.liftrix.domain.model.AnomalyType
import com.example.liftrix.domain.model.AnomalyValue
import com.example.liftrix.domain.model.ExerciseHistory
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.UserAnomalyAction
import com.example.liftrix.domain.model.WorkoutAnomaly
import com.example.liftrix.domain.repository.AnomalyDetectionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Detects anomalies in workout data entries to prevent logging errors
 */
@Singleton
class DetectWorkoutAnomaliesUseCase @Inject constructor(
    private val anomalyRepository: AnomalyDetectionRepository
) {

    /**
     * Detects potential anomalies in weight input
     */
    suspend fun detectWeightAnomaly(
        userId: String,
        sessionId: String,
        exerciseId: ExerciseId,
        exerciseName: String,
        currentWeight: Double,
        previousWeight: Double? = null
    ): LiftrixResult<WorkoutAnomaly?> {
        return try {
            val settings = getDetectionSettings(userId)
            val history = getExerciseHistory(userId, exerciseId)

            val anomaly = detectWeightAnomalyInternal(
                userId = userId,
                sessionId = sessionId,
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                currentWeight = currentWeight,
                previousWeight = previousWeight,
                settings = settings,
                history = history
            )

            Result.success(anomaly)
        } catch (e: Exception) {
            Result.failure(LiftrixError.UnknownError("Failed to detect weight anomaly: ${e.message}"))
        }
    }

    /**
     * Detects potential anomalies in reps input
     */
    suspend fun detectRepsAnomaly(
        userId: String,
        sessionId: String,
        exerciseId: ExerciseId,
        exerciseName: String,
        currentReps: Int,
        previousReps: Int? = null
    ): LiftrixResult<WorkoutAnomaly?> {
        return try {
            val settings = getDetectionSettings(userId)
            val history = getExerciseHistory(userId, exerciseId)

            val anomaly = detectRepsAnomalyInternal(
                userId = userId,
                sessionId = sessionId,
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                currentReps = currentReps,
                previousReps = previousReps,
                settings = settings,
                history = history
            )

            Result.success(anomaly)
        } catch (e: Exception) {
            Result.failure(LiftrixError.UnknownError("Failed to detect reps anomaly: ${e.message}"))
        }
    }

    /**
     * Detects potential anomalies in duration input
     */
    suspend fun detectDurationAnomaly(
        userId: String,
        sessionId: String,
        exerciseId: ExerciseId,
        exerciseName: String,
        currentDuration: Long,
        previousDuration: Long? = null
    ): LiftrixResult<WorkoutAnomaly?> {
        return try {
            val settings = getDetectionSettings(userId)
            val history = getExerciseHistory(userId, exerciseId)

            val anomaly = detectDurationAnomalyInternal(
                userId = userId,
                sessionId = sessionId,
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                currentDuration = currentDuration,
                previousDuration = previousDuration,
                settings = settings,
                history = history
            )

            Result.success(anomaly)
        } catch (e: Exception) {
            Result.failure(LiftrixError.UnknownError("Failed to detect duration anomaly: ${e.message}"))
        }
    }

    /**
     * Resolves an anomaly with user action
     */
    suspend fun resolveAnomaly(
        anomalyId: String,
        userAction: UserAnomalyAction,
        correctedValue: AnomalyValue? = null
    ): LiftrixResult<WorkoutAnomaly> {
        return try {
            val result = anomalyRepository.getAnomaly(anomalyId)
            result.fold(
                onSuccess = { anomaly ->
                    val resolvedAnomaly = anomaly.resolve(userAction, correctedValue)
                    
                    // Save the resolved anomaly
                    anomalyRepository.saveAnomaly(resolvedAnomaly)
                    
                    // Update user's detection settings based on feedback
                    updateDetectionSettings(anomaly.userId, userAction)
                    
                    Result.success(resolvedAnomaly)
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Result.failure(LiftrixError.UnknownError("Failed to resolve anomaly: ${e.message}"))
        }
    }

    /**
     * Updates exercise history with new performance data
     */
    suspend fun updateExerciseHistory(
        userId: String,
        exerciseId: ExerciseId,
        weight: Double? = null,
        reps: Int? = null,
        duration: Long? = null
    ): LiftrixResult<Unit> {
        return try {
            val currentHistory = getExerciseHistory(userId, exerciseId)
            val updatedHistory = currentHistory.updateWith(weight, reps, duration)
            anomalyRepository.saveExerciseHistory(updatedHistory)
        } catch (e: Exception) {
            Result.failure(LiftrixError.UnknownError("Failed to update exercise history: ${e.message}"))
        }
    }

    /**
     * Gets user's anomaly detection settings
     */
    suspend fun getDetectionSettings(userId: String): AnomalyDetectionSettings {
        return anomalyRepository.getDetectionSettings(userId).fold(
            onSuccess = { settings -> settings ?: AnomalyDetectionSettings.createDefault(userId) },
            onFailure = { AnomalyDetectionSettings.createDefault(userId) }
        )
    }

    /**
     * Gets exercise performance history for anomaly detection
     */
    suspend fun getExerciseHistory(userId: String, exerciseId: ExerciseId): ExerciseHistory {
        return anomalyRepository.getExerciseHistory(userId, exerciseId).fold(
            onSuccess = { history -> history ?: ExerciseHistory(exerciseId, userId) },
            onFailure = { ExerciseHistory(exerciseId, userId) }
        )
    }

    /**
     * Gets user's anomaly feedback statistics (confirmed vs dismissed)
     */
    suspend fun getUserAnomalyFeedback(userId: String): LiftrixResult<Pair<Int, Int>> {
        return anomalyRepository.getUserAnomalyFeedback(userId)
    }

    private fun detectWeightAnomalyInternal(
        userId: String,
        sessionId: String,
        exerciseId: ExerciseId,
        exerciseName: String,
        currentWeight: Double,
        previousWeight: Double?,
        settings: AnomalyDetectionSettings,
        history: ExerciseHistory
    ): WorkoutAnomaly? {
        // Skip detection for very low weights
        if (currentWeight < settings.minWeightForDetection) return null

        var anomalyType: AnomalyType? = null
        var confidenceScore = 0f
        var previousValue: AnomalyValue? = null

        // Check against previous set in current session
        previousWeight?.let { prev ->
            val spikeRatio = currentWeight / prev

            when {
                spikeRatio >= settings.weightSpikeThreshold -> {
                    anomalyType = AnomalyType.WEIGHT_SPIKE
                    confidenceScore = calculateConfidenceScore(spikeRatio.toFloat(), settings.weightSpikeThreshold)
                    previousValue = AnomalyValue.WeightValue(prev)
                }
            }
        }

        // Check against historical patterns if no immediate anomaly found
        if (anomalyType == null && history.isWeightAnomaly(currentWeight, settings)) {
            val averageWeight = if (history.recentWeights.isNotEmpty()) history.recentWeights.average() else 0.0
            val ratio = if (averageWeight > 0) (currentWeight / averageWeight).toFloat() else 1.0f

            if (ratio >= settings.weightSpikeThreshold) {
                anomalyType = AnomalyType.WEIGHT_SPIKE
                confidenceScore = calculateConfidenceScore(ratio, settings.weightSpikeThreshold)
                previousValue = AnomalyValue.WeightValue(averageWeight)
            }
        }

        return anomalyType?.let { type ->
            WorkoutAnomaly(
                userId = userId,
                sessionId = sessionId,
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                anomalyType = type,
                currentValue = AnomalyValue.WeightValue(currentWeight),
                previousValue = previousValue,
                confidenceScore = confidenceScore
            )
        }
    }

    private fun detectRepsAnomalyInternal(
        userId: String,
        sessionId: String,
        exerciseId: ExerciseId,
        exerciseName: String,
        currentReps: Int,
        previousReps: Int?,
        settings: AnomalyDetectionSettings,
        history: ExerciseHistory
    ): WorkoutAnomaly? {
        // Skip detection for very low reps
        if (currentReps < settings.minRepsForDetection) return null

        var anomalyType: AnomalyType? = null
        var confidenceScore = 0f
        var previousValue: AnomalyValue? = null

        // Check against previous set in current session
        previousReps?.let { prev ->
            val spikeRatio = currentReps.toFloat() / prev

            when {
                spikeRatio >= settings.repsSpikeThreshold -> {
                    anomalyType = AnomalyType.REPS_SPIKE
                    confidenceScore = calculateConfidenceScore(spikeRatio, settings.repsSpikeThreshold)
                    previousValue = AnomalyValue.RepsValue(prev)
                }
            }
        }

        // Check against historical patterns
        if (anomalyType == null && history.isRepsAnomaly(currentReps, settings)) {
            val averageReps = if (history.recentReps.isNotEmpty()) history.recentReps.average() else 0.0
            val ratio = if (averageReps > 0) currentReps / averageReps else 1.0

            if (ratio >= settings.repsSpikeThreshold) {
                anomalyType = AnomalyType.REPS_SPIKE
                confidenceScore = calculateConfidenceScore(ratio.toFloat(), settings.repsSpikeThreshold)
                previousValue = AnomalyValue.RepsValue(averageReps.toInt())
            }
        }

        return anomalyType?.let { type ->
            WorkoutAnomaly(
                userId = userId,
                sessionId = sessionId,
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                anomalyType = type,
                currentValue = AnomalyValue.RepsValue(currentReps),
                previousValue = previousValue,
                confidenceScore = confidenceScore
            )
        }
    }

    private fun detectDurationAnomalyInternal(
        userId: String,
        sessionId: String,
        exerciseId: ExerciseId,
        exerciseName: String,
        currentDuration: Long,
        previousDuration: Long?,
        settings: AnomalyDetectionSettings,
        history: ExerciseHistory
    ): WorkoutAnomaly? {
        // Skip detection for very short durations
        if (currentDuration < settings.minDurationForDetection) return null

        var anomalyType: AnomalyType? = null
        var confidenceScore = 0f
        var previousValue: AnomalyValue? = null

        // Check against previous set in current session
        previousDuration?.let { prev ->
            val spikeRatio = currentDuration.toFloat() / prev

            when {
                spikeRatio >= settings.durationSpikeThreshold -> {
                    anomalyType = AnomalyType.DURATION_SPIKE
                    confidenceScore = calculateConfidenceScore(spikeRatio, settings.durationSpikeThreshold)
                    previousValue = AnomalyValue.DurationValue(prev)
                }
            }
        }

        // Check against historical patterns
        if (anomalyType == null && history.isDurationAnomaly(currentDuration, settings)) {
            val averageDuration = if (history.recentDurations.isNotEmpty()) history.recentDurations.average() else 0.0
            val ratio = if (averageDuration > 0) currentDuration / averageDuration else 1.0

            if (ratio >= settings.durationSpikeThreshold) {
                anomalyType = AnomalyType.DURATION_SPIKE
                confidenceScore = calculateConfidenceScore(ratio.toFloat(), settings.durationSpikeThreshold)
                previousValue = AnomalyValue.DurationValue(averageDuration.toLong())
            }
        }

        return anomalyType?.let { type ->
            WorkoutAnomaly(
                userId = userId,
                sessionId = sessionId,
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                anomalyType = type,
                currentValue = AnomalyValue.DurationValue(currentDuration),
                previousValue = previousValue,
                confidenceScore = confidenceScore
            )
        }
    }

    private fun calculateConfidenceScore(actualRatio: Float, threshold: Float): Float {
        // Higher ratios above threshold give higher confidence scores
        val excessRatio = actualRatio - threshold
        val normalizedScore = (excessRatio / threshold).coerceIn(0f, 1f)
        return (0.6f + normalizedScore * 0.4f).coerceIn(0f, 1f) // Score between 0.6 and 1.0
    }

    private suspend fun updateDetectionSettings(userId: String, userAction: UserAnomalyAction) {
        try {
            val currentSettings = getDetectionSettings(userId)
            
            // Get user's recent anomaly feedback
            getUserAnomalyFeedback(userId).fold(
                onSuccess = { (confirmed, dismissed) ->
                    val adjustedSettings = currentSettings.adjustSensitivity(confirmed, dismissed)
                    
                    if (adjustedSettings != currentSettings) {
                        anomalyRepository.saveDetectionSettings(adjustedSettings)
                    }
                },
                onFailure = {
                    // Can't update settings without feedback data
                }
            )
        } catch (e: Exception) {
            // Log error but don't fail the anomaly resolution
            timber.log.Timber.w(e, "Failed to update detection settings for user $userId")
        }
    }
}