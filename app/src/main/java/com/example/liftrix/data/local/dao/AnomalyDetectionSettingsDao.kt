package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.AnomalyDetectionSettingsEntity

/**
 * DAO for anomaly detection settings operations
 */
@Dao
interface AnomalyDetectionSettingsDao {

    /**
     * Gets anomaly detection settings for a user
     */
    @Query("SELECT * FROM anomaly_detection_settings WHERE user_id = :userId LIMIT 1")
    suspend fun getDetectionSettings(userId: String): AnomalyDetectionSettingsEntity?

    /**
     * Inserts or updates detection settings
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: AnomalyDetectionSettingsEntity)

    /**
     * Updates existing detection settings
     */
    @Update
    suspend fun update(settings: AnomalyDetectionSettingsEntity)

    /**
     * Deletes detection settings for a user
     */
    @Query("DELETE FROM anomaly_detection_settings WHERE user_id = :userId")
    suspend fun deleteByUserId(userId: String)

    /**
     * Gets all users with learning enabled
     */
    @Query("SELECT * FROM anomaly_detection_settings WHERE learning_enabled = 1")
    suspend fun getUsersWithLearningEnabled(): List<AnomalyDetectionSettingsEntity>

    /**
     * Gets count of users with custom settings (non-default)
     */
    @Query("""
        SELECT COUNT(*) FROM anomaly_detection_settings 
        WHERE weight_spike_threshold != 3.0 
           OR weight_drop_threshold != 0.3 
           OR reps_spike_threshold != 2.0 
           OR reps_drop_threshold != 0.5
    """)
    suspend fun getCustomSettingsCount(): Int
}