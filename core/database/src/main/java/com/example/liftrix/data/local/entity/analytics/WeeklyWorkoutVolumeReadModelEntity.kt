package com.example.liftrix.data.local.entity.analytics

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "workout_weekly_volume_read_models",
    primaryKeys = ["user_id", "week_start_date"],
    indices = [
        Index(value = ["user_id", "week_start_date"], name = "idx_weekly_volume_user_week")
    ]
)
data class WeeklyWorkoutVolumeReadModelEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "week_start_date")
    val weekStartDate: String,
    @ColumnInfo(name = "week_end_date")
    val weekEndDate: String,
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
