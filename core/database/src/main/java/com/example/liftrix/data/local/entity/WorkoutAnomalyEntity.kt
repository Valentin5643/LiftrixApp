package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.liftrix.domain.model.AnomalyType
import com.example.liftrix.domain.model.UserAnomalyAction
import java.time.Instant

/**
 * Room entity for storing workout anomaly data
 */
@Entity(
    tableName = "workout_anomalies",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["session_id"]),
        Index(value = ["exercise_id"]),
        Index(value = ["detected_at"]),
        Index(value = ["user_action"]),
        Index(value = ["anomaly_type"])
    ]
)
data class WorkoutAnomalyEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,

    @ColumnInfo(name = "exercise_name")
    val exerciseName: String,

    @ColumnInfo(name = "anomaly_type")
    val anomalyType: AnomalyType,

    @ColumnInfo(name = "current_value_type")
    val currentValueType: String, // "weight", "reps", "duration"

    @ColumnInfo(name = "current_value_data")
    val currentValueData: String, // JSON representation of AnomalyValue

    @ColumnInfo(name = "previous_value_type")
    val previousValueType: String? = null,

    @ColumnInfo(name = "previous_value_data")
    val previousValueData: String? = null, // JSON representation of AnomalyValue

    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Float,

    @ColumnInfo(name = "detected_at")
    val detectedAt: Instant,

    @ColumnInfo(name = "resolved_at")
    val resolvedAt: Instant? = null,

    @ColumnInfo(name = "user_action")
    val userAction: UserAnomalyAction? = null,

    @ColumnInfo(name = "corrected_value_type")
    val correctedValueType: String? = null,

    @ColumnInfo(name = "corrected_value_data")
    val correctedValueData: String? = null // JSON representation of corrected AnomalyValue
)