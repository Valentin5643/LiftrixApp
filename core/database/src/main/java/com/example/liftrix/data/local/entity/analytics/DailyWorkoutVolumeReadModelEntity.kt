package com.example.liftrix.data.local.entity.analytics

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "workout_daily_volume_read_models",
    primaryKeys = ["user_id", "workout_date"],
    indices = [
        Index(value = ["user_id", "workout_date"], name = "idx_daily_volume_user_date")
    ]
)
data class DailyWorkoutVolumeReadModelEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "workout_date")
    val workoutDate: String,
    @ColumnInfo(name = "total_volume")
    val totalVolume: Double,
    @ColumnInfo(name = "total_reps")
    val totalReps: Int,
    @ColumnInfo(name = "total_sets")
    val totalSets: Int,
    @ColumnInfo(name = "workout_count")
    val workoutCount: Int,
    @ColumnInfo(name = "exercise_count")
    val exerciseCount: Int,
    @ColumnInfo(name = "total_duration_minutes")
    val totalDurationMinutes: Int,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
