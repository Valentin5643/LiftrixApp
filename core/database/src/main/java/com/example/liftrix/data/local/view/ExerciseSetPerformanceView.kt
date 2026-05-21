package com.example.liftrix.data.local.view

import androidx.room.ColumnInfo
import androidx.room.DatabaseView

@DatabaseView(
    viewName = "exercise_set_performance_view",
    value = """
        SELECT
            es.id AS set_id,
            es.user_id AS user_id,
            e.id AS exercise_id,
            e.workout_id AS workout_id,
            w.date AS workout_date,
            COALESCE(DATE(es.completed_at / 1000, 'unixepoch'), w.date) AS activity_date,
            e.exercise_library_id AS exercise_library_id,
            el.name AS exercise_name,
            el.primary_muscle_group AS primary_muscle_group,
            el.equipment AS equipment,
            es.weight_kg AS weight_kg,
            es.reps AS reps,
            es.completed_at AS completed_at,
            CASE
                WHEN (el.equipment IS NULL OR el.equipment != 'BODYWEIGHT_ONLY')
                 AND es.weight_kg > 0
                 AND es.reps > 0
                THEN es.weight_kg * es.reps
                ELSE 0
            END AS set_volume,
            CASE
                WHEN (el.equipment IS NULL OR el.equipment != 'BODYWEIGHT_ONLY')
                 AND es.weight_kg > 0
                 AND es.reps BETWEEN 1 AND 10
                THEN es.weight_kg * (1.0 + CAST(es.reps AS REAL) / 30.0)
                ELSE NULL
            END AS estimated_one_rm,
            CASE
                WHEN es.reps > 0 THEN es.reps
                ELSE 0
            END AS rep_count
        FROM exercise_sets es
        JOIN exercises e
            ON es.exercise_id = e.id
           AND es.user_id = e.user_id
        JOIN workouts w
            ON e.workout_id = w.id
           AND e.user_id = w.user_id
        LEFT JOIN exercise_library el
            ON e.exercise_library_id = el.id
        WHERE w.status = 'COMPLETED'
    """
)
data class ExerciseSetPerformanceView(
    @ColumnInfo(name = "set_id")
    val setId: Long,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "exercise_id")
    val exerciseId: Long,
    @ColumnInfo(name = "workout_id")
    val workoutId: String,
    @ColumnInfo(name = "workout_date")
    val workoutDate: String,
    @ColumnInfo(name = "activity_date")
    val activityDate: String,
    @ColumnInfo(name = "exercise_library_id")
    val exerciseLibraryId: String,
    @ColumnInfo(name = "exercise_name")
    val exerciseName: String?,
    @ColumnInfo(name = "primary_muscle_group")
    val primaryMuscleGroup: String?,
    @ColumnInfo(name = "equipment")
    val equipment: String?,
    @ColumnInfo(name = "weight_kg")
    val weightKg: Float?,
    @ColumnInfo(name = "reps")
    val reps: Int?,
    @ColumnInfo(name = "completed_at")
    val completedAt: Long?,
    @ColumnInfo(name = "set_volume")
    val setVolume: Double,
    @ColumnInfo(name = "estimated_one_rm")
    val estimatedOneRm: Double?,
    @ColumnInfo(name = "rep_count")
    val repCount: Int
)
