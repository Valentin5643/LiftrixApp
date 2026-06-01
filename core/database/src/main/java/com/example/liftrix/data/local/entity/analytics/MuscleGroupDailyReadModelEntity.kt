package com.example.liftrix.data.local.entity.analytics

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "muscle_group_daily_read_models",
    primaryKeys = ["user_id", "workout_date", "primary_muscle_group"],
    indices = [
        Index(value = ["user_id", "workout_date"], name = "idx_muscle_daily_user_date"),
        Index(value = ["user_id", "primary_muscle_group"], name = "idx_muscle_daily_user_group")
    ]
)
data class MuscleGroupDailyReadModelEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "workout_date")
    val workoutDate: String,
    @ColumnInfo(name = "primary_muscle_group")
    val primaryMuscleGroup: String,
    @ColumnInfo(name = "total_volume")
    val totalVolume: Double,
    @ColumnInfo(name = "total_reps")
    val totalReps: Int,
    @ColumnInfo(name = "total_sets")
    val totalSets: Int,
    @ColumnInfo(name = "exercise_count")
    val exerciseCount: Int,
    @ColumnInfo(name = "workout_count")
    val workoutCount: Int,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
