package com.example.liftrix.data.local.entity.analytics

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "exercise_pr_read_models",
    primaryKeys = ["user_id", "exercise_library_id"],
    indices = [
        Index(value = ["user_id", "max_estimated_one_rm"], name = "idx_exercise_pr_user_one_rm"),
        Index(value = ["user_id", "primary_muscle_group"], name = "idx_exercise_pr_user_muscle")
    ]
)
data class ExercisePrReadModelEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "exercise_library_id")
    val exerciseLibraryId: String,
    @ColumnInfo(name = "exercise_name")
    val exerciseName: String,
    @ColumnInfo(name = "primary_muscle_group")
    val primaryMuscleGroup: String,
    @ColumnInfo(name = "max_estimated_one_rm")
    val maxEstimatedOneRm: Double,
    @ColumnInfo(name = "max_weight_kg")
    val maxWeightKg: Double,
    @ColumnInfo(name = "max_reps")
    val maxReps: Int,
    @ColumnInfo(name = "total_volume")
    val totalVolume: Double,
    @ColumnInfo(name = "total_sets")
    val totalSets: Int,
    @ColumnInfo(name = "last_pr_at")
    val lastPrAt: Long?,
    @ColumnInfo(name = "source_workout_id")
    val sourceWorkoutId: String?,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
