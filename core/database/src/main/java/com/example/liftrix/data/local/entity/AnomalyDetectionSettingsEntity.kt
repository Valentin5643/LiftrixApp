package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Room entity for storing user-specific anomaly detection settings
 */
@Entity(
    tableName = "anomaly_detection_settings",
    indices = [
        Index(value = ["user_id"], unique = true)
    ]
)
data class AnomalyDetectionSettingsEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "weight_spike_threshold")
    val weightSpikeThreshold: Float,

    @ColumnInfo(name = "reps_spike_threshold")
    val repsSpikeThreshold: Float,

    @ColumnInfo(name = "duration_spike_threshold")
    val durationSpikeThreshold: Float,

    @ColumnInfo(name = "min_weight_for_detection")
    val minWeightForDetection: Double,

    @ColumnInfo(name = "min_reps_for_detection")
    val minRepsForDetection: Int,

    @ColumnInfo(name = "min_duration_for_detection")
    val minDurationForDetection: Long,

    @ColumnInfo(name = "learning_enabled")
    val learningEnabled: Boolean,

    @ColumnInfo(name = "last_updated")
    val lastUpdated: Instant
)