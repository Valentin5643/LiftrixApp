package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Room entity for storing exercise performance history for anomaly detection
 */
@Entity(
    tableName = "exercise_history",
    indices = [
        Index(value = ["user_id", "exercise_id"], unique = true),
        Index(value = ["last_performed"])
    ]
)
data class ExerciseHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // Generated: "${userId}_${exerciseId}"

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,

    @ColumnInfo(name = "recent_weights")
    val recentWeights: String, // JSON array of doubles

    @ColumnInfo(name = "recent_reps")
    val recentReps: String, // JSON array of ints

    @ColumnInfo(name = "recent_durations")
    val recentDurations: String, // JSON array of longs

    @ColumnInfo(name = "last_performed")
    val lastPerformed: Instant? = null,

    @ColumnInfo(name = "average_weight")
    val averageWeight: Double,

    @ColumnInfo(name = "average_reps")
    val averageReps: Double,

    @ColumnInfo(name = "average_duration")
    val averageDuration: Double,

    @ColumnInfo(name = "max_weight")
    val maxWeight: Double,

    @ColumnInfo(name = "max_reps")
    val maxReps: Int,

    @ColumnInfo(name = "max_duration")
    val maxDuration: Long
)