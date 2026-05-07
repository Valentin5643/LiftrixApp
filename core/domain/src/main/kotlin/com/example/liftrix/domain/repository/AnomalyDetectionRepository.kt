package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.AnomalyDetectionSettings
import com.example.liftrix.domain.model.ExerciseHistory
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.WorkoutAnomaly

/**
 * Repository interface for anomaly detection and exercise history management
 */
interface AnomalyDetectionRepository {

    /**
     * Gets anomaly detection settings for a user
     */
    suspend fun getDetectionSettings(userId: String): LiftrixResult<AnomalyDetectionSettings?>

    /**
     * Saves anomaly detection settings for a user
     */
    suspend fun saveDetectionSettings(settings: AnomalyDetectionSettings): LiftrixResult<Unit>

    /**
     * Gets exercise performance history for a user and exercise
     */
    suspend fun getExerciseHistory(userId: String, exerciseId: ExerciseId): LiftrixResult<ExerciseHistory?>

    /**
     * Saves exercise performance history
     */
    suspend fun saveExerciseHistory(history: ExerciseHistory): LiftrixResult<Unit>

    /**
     * Saves a detected anomaly
     */
    suspend fun saveAnomaly(anomaly: WorkoutAnomaly): LiftrixResult<Unit>

    /**
     * Gets a specific anomaly by ID
     */
    suspend fun getAnomaly(anomalyId: String): LiftrixResult<WorkoutAnomaly>

    /**
     * Gets recent anomalies for a user
     */
    suspend fun getUserAnomalies(userId: String, limit: Int = 50): LiftrixResult<List<WorkoutAnomaly>>

    /**
     * Gets unresolved anomalies for a user
     */
    suspend fun getUnresolvedAnomalies(userId: String): LiftrixResult<List<WorkoutAnomaly>>

    /**
     * Gets user's anomaly feedback statistics (confirmed vs dismissed)
     */
    suspend fun getUserAnomalyFeedback(userId: String): LiftrixResult<Pair<Int, Int>>

    /**
     * Gets all anomalies for analytics and model improvement
     */
    suspend fun getAllAnomalies(limit: Int = 1000, offset: Int = 0): LiftrixResult<List<WorkoutAnomaly>>

    /**
     * Cleans up old resolved anomalies
     */
    suspend fun cleanupOldAnomalies(daysToKeep: Int = 30): LiftrixResult<Int>
}