package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.AnomalyDetectionSettingsEntity
import com.example.liftrix.data.local.entity.ExerciseHistoryEntity
import com.example.liftrix.data.local.entity.WorkoutAnomalyEntity
import com.example.liftrix.domain.model.AnomalyDetectionSettings
import com.example.liftrix.domain.model.AnomalyValue
import com.example.liftrix.domain.model.ExerciseHistory
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.WorkoutAnomaly
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Mapper for converting between anomaly detection domain models and entities
 */
object AnomalyDetectionMapper {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Converts WorkoutAnomaly domain model to WorkoutAnomalyEntity for database storage
     */
    fun WorkoutAnomaly.toEntity(): WorkoutAnomalyEntity {
        val (currentType, currentData) = currentValue.toTypeAndData()
        val (previousType, previousData) = previousValue?.toTypeAndData() ?: (null to null)
        val (correctedType, correctedData) = correctedValue?.toTypeAndData() ?: (null to null)

        return WorkoutAnomalyEntity(
            id = id,
            userId = userId,
            sessionId = sessionId,
            exerciseId = exerciseId.value,
            exerciseName = exerciseName,
            anomalyType = anomalyType,
            currentValueType = currentType,
            currentValueData = currentData,
            previousValueType = previousType,
            previousValueData = previousData,
            confidenceScore = confidenceScore,
            detectedAt = detectedAt,
            resolvedAt = resolvedAt,
            userAction = userAction,
            correctedValueType = correctedType,
            correctedValueData = correctedData
        )
    }

    /**
     * Converts WorkoutAnomalyEntity from database to WorkoutAnomaly domain model
     */
    fun WorkoutAnomalyEntity.toDomain(): WorkoutAnomaly {
        val previousType = previousValueType
        val previousData = previousValueData
        val correctedType = correctedValueType
        val correctedData = correctedValueData
        return WorkoutAnomaly(
            id = id,
            userId = userId,
            sessionId = sessionId,
            exerciseId = ExerciseId(exerciseId),
            exerciseName = exerciseName,
            anomalyType = anomalyType,
            currentValue = parseAnomalyValue(currentValueType, currentValueData),
            previousValue = if (previousType != null && previousData != null) {
                parseAnomalyValue(previousType, previousData)
            } else null,
            confidenceScore = confidenceScore,
            detectedAt = detectedAt,
            resolvedAt = resolvedAt,
            userAction = userAction,
            correctedValue = if (correctedType != null && correctedData != null) {
                parseAnomalyValue(correctedType, correctedData)
            } else null
        )
    }

    /**
     * Converts AnomalyDetectionSettings domain model to AnomalyDetectionSettingsEntity
     */
    fun AnomalyDetectionSettings.toEntity(): AnomalyDetectionSettingsEntity {
        return AnomalyDetectionSettingsEntity(
            userId = userId,
            weightSpikeThreshold = weightSpikeThreshold,
            repsSpikeThreshold = repsSpikeThreshold,
            durationSpikeThreshold = durationSpikeThreshold,
            minWeightForDetection = minWeightForDetection,
            minRepsForDetection = minRepsForDetection,
            minDurationForDetection = minDurationForDetection,
            learningEnabled = learningEnabled,
            lastUpdated = lastUpdated
        )
    }

    /**
     * Converts AnomalyDetectionSettingsEntity to AnomalyDetectionSettings domain model
     */
    fun AnomalyDetectionSettingsEntity.toDomain(): AnomalyDetectionSettings {
        return AnomalyDetectionSettings(
            userId = userId,
            weightSpikeThreshold = weightSpikeThreshold,
            repsSpikeThreshold = repsSpikeThreshold,
            durationSpikeThreshold = durationSpikeThreshold,
            minWeightForDetection = minWeightForDetection,
            minRepsForDetection = minRepsForDetection,
            minDurationForDetection = minDurationForDetection,
            learningEnabled = learningEnabled,
            lastUpdated = lastUpdated
        )
    }

    /**
     * Converts ExerciseHistory domain model to ExerciseHistoryEntity
     */
    fun ExerciseHistory.toEntity(): ExerciseHistoryEntity {
        return ExerciseHistoryEntity(
            id = "${userId}_${exerciseId.value}",
            userId = userId,
            exerciseId = exerciseId.value,
            recentWeights = json.encodeToString(recentWeights),
            recentReps = json.encodeToString(recentReps),
            recentDurations = json.encodeToString(recentDurations),
            lastPerformed = lastPerformed,
            averageWeight = averageWeight,
            averageReps = averageReps,
            averageDuration = averageDuration,
            maxWeight = maxWeight,
            maxReps = maxReps,
            maxDuration = maxDuration
        )
    }

    /**
     * Converts ExerciseHistoryEntity to ExerciseHistory domain model
     */
    fun ExerciseHistoryEntity.toDomain(): ExerciseHistory {
        return ExerciseHistory(
            exerciseId = ExerciseId(exerciseId),
            userId = userId,
            recentWeights = json.decodeFromString<List<Double>>(recentWeights),
            recentReps = json.decodeFromString<List<Int>>(recentReps),
            recentDurations = json.decodeFromString<List<Long>>(recentDurations),
            lastPerformed = lastPerformed,
            averageWeight = averageWeight,
            averageReps = averageReps,
            averageDuration = averageDuration,
            maxWeight = maxWeight,
            maxReps = maxReps,
            maxDuration = maxDuration
        )
    }

    /**
     * Helper function to convert AnomalyValue to type and JSON data
     */
    private fun AnomalyValue.toTypeAndData(): Pair<String, String> {
        return when (this) {
            is AnomalyValue.WeightValue -> "weight" to json.encodeToString(this)
            is AnomalyValue.RepsValue -> "reps" to json.encodeToString(this)
            is AnomalyValue.DurationValue -> "duration" to json.encodeToString(this)
        }
    }

    /**
     * Helper function to parse type and JSON data back to AnomalyValue
     */
    private fun parseAnomalyValue(type: String, data: String): AnomalyValue {
        return when (type) {
            "weight" -> json.decodeFromString<AnomalyValue.WeightValue>(data)
            "reps" -> json.decodeFromString<AnomalyValue.RepsValue>(data)
            "duration" -> json.decodeFromString<AnomalyValue.DurationValue>(data)
            else -> throw IllegalArgumentException("Unknown anomaly value type: $type")
        }
    }

    /**
     * Extension functions for list conversions
     */
    fun List<WorkoutAnomalyEntity>.toWorkoutAnomalyDomain(): List<WorkoutAnomaly> = map { it.toDomain() }
    fun List<ExerciseHistoryEntity>.toExerciseHistoryDomain(): List<ExerciseHistory> = map { it.toDomain() }
}
