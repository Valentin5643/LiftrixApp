package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.WorkoutAnomalyEntity
import com.example.liftrix.domain.model.AnomalyType
import com.example.liftrix.domain.model.UserAnomalyAction
import java.time.Instant

/**
 * DAO for workout anomaly data operations
 */
@Dao
interface WorkoutAnomalyDao {

    /**
     * Inserts a new anomaly
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(anomaly: WorkoutAnomalyEntity)

    /**
     * Updates an existing anomaly
     */
    @Update
    suspend fun update(anomaly: WorkoutAnomalyEntity)

    /**
     * Gets a specific anomaly by ID
     */
    @Query("SELECT * FROM workout_anomalies WHERE id = :anomalyId LIMIT 1")
    suspend fun getAnomaly(anomalyId: String): WorkoutAnomalyEntity?

    /**
     * Gets recent anomalies for a user
     */
    @Query("""
        SELECT * FROM workout_anomalies 
        WHERE user_id = :userId 
        ORDER BY detected_at DESC 
        LIMIT :limit
    """)
    suspend fun getUserAnomalies(userId: String, limit: Int = 50): List<WorkoutAnomalyEntity>

    /**
     * Gets unresolved anomalies for a user
     */
    @Query("""
        SELECT * FROM workout_anomalies 
        WHERE user_id = :userId AND user_action IS NULL 
        ORDER BY detected_at DESC
    """)
    suspend fun getUnresolvedAnomalies(userId: String): List<WorkoutAnomalyEntity>

    /**
     * Gets anomalies for a specific session
     */
    @Query("""
        SELECT * FROM workout_anomalies 
        WHERE session_id = :sessionId 
        ORDER BY detected_at DESC
    """)
    suspend fun getSessionAnomalies(sessionId: String): List<WorkoutAnomalyEntity>

    /**
     * Gets anomalies for a specific exercise and user
     */
    @Query("""
        SELECT * FROM workout_anomalies 
        WHERE user_id = :userId AND exercise_id = :exerciseId 
        ORDER BY detected_at DESC 
        LIMIT :limit
    """)
    suspend fun getExerciseAnomalies(userId: String, exerciseId: String, limit: Int = 20): List<WorkoutAnomalyEntity>

    /**
     * Gets count of confirmed anomalies for a user
     */
    @Query("""
        SELECT COUNT(*) FROM workout_anomalies 
        WHERE user_id = :userId AND user_action = 'CONFIRMED'
    """)
    suspend fun getConfirmedAnomaliesCount(userId: String): Int

    /**
     * Gets count of dismissed anomalies for a user
     */
    @Query("""
        SELECT COUNT(*) FROM workout_anomalies 
        WHERE user_id = :userId AND user_action = 'DISMISSED'
    """)
    suspend fun getDismissedAnomaliesCount(userId: String): Int

    /**
     * Gets count of corrected anomalies for a user
     */
    @Query("""
        SELECT COUNT(*) FROM workout_anomalies 
        WHERE user_id = :userId AND user_action = 'CORRECTED'
    """)
    suspend fun getCorrectedAnomaliesCount(userId: String): Int

    /**
     * Gets all anomalies for analytics
     */
    @Query("""
        SELECT * FROM workout_anomalies 
        ORDER BY detected_at DESC 
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getAllAnomalies(limit: Int = 1000, offset: Int = 0): List<WorkoutAnomalyEntity>

    /**
     * Gets anomalies by type
     */
    @Query("""
        SELECT * FROM workout_anomalies 
        WHERE anomaly_type = :anomalyType 
        ORDER BY detected_at DESC 
        LIMIT :limit
    """)
    suspend fun getAnomaliesByType(anomalyType: AnomalyType, limit: Int = 100): List<WorkoutAnomalyEntity>

    /**
     * Gets high confidence unresolved anomalies
     */
    @Query("""
        SELECT * FROM workout_anomalies 
        WHERE user_action IS NULL AND confidence_score >= :minConfidence 
        ORDER BY confidence_score DESC, detected_at DESC
    """)
    suspend fun getHighConfidenceUnresolvedAnomalies(minConfidence: Float = 0.8f): List<WorkoutAnomalyEntity>

    /**
     * Deletes old resolved anomalies
     */
    @Query("""
        DELETE FROM workout_anomalies 
        WHERE resolved_at IS NOT NULL AND resolved_at < :cutoffTime
    """)
    suspend fun deleteOldResolvedAnomalies(cutoffTime: Instant): Int

    /**
     * Gets anomaly statistics for a user
     */
    @Query("""
        SELECT 
            COUNT(*) as total,
            SUM(CASE WHEN user_action = 'CONFIRMED' THEN 1 ELSE 0 END) as confirmed,
            SUM(CASE WHEN user_action = 'DISMISSED' THEN 1 ELSE 0 END) as dismissed,
            SUM(CASE WHEN user_action = 'CORRECTED' THEN 1 ELSE 0 END) as corrected,
            SUM(CASE WHEN user_action IS NULL THEN 1 ELSE 0 END) as unresolved,
            AVG(confidence_score) as avgConfidence
        FROM workout_anomalies 
        WHERE user_id = :userId
    """)
    suspend fun getUserAnomalyStats(userId: String): UserAnomalyStats?

    /**
     * Gets recent anomaly trend for a user (last 30 days)
     */
    @Query("""
        SELECT COUNT(*) FROM workout_anomalies 
        WHERE user_id = :userId AND detected_at > :cutoffTime
    """)
    suspend fun getRecentAnomalyCount(userId: String, cutoffTime: Instant): Int

    /**
     * Data class for user anomaly statistics
     */
    data class UserAnomalyStats(
        val total: Int,
        val confirmed: Int,
        val dismissed: Int,
        val corrected: Int,
        val unresolved: Int,
        val avgConfidence: Float
    )
}