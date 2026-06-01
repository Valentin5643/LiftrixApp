package com.example.liftrix.data.local.entity.analytics

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "completed_workout_metric_read_models",
    primaryKeys = ["workout_id", "user_id"],
    indices = [
        Index(value = ["user_id", "workout_date"], name = "idx_completed_workout_metrics_user_date")
    ]
)
data class CompletedWorkoutMetricReadModelEntity(
    @ColumnInfo(name = "workout_id")
    val workoutId: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "workout_date")
    val workoutDate: String,
    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Int,
    @ColumnInfo(name = "total_volume")
    val totalVolume: Double,
    @ColumnInfo(name = "total_reps")
    val totalReps: Int,
    @ColumnInfo(name = "total_sets")
    val totalSets: Int,
    @ColumnInfo(name = "exercise_count")
    val exerciseCount: Int,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
