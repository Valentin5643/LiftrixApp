package com.example.liftrix.data.local.view

import androidx.room.ColumnInfo
import androidx.room.DatabaseView

@DatabaseView(
    viewName = "completed_workout_metrics_view",
    value = """
        SELECT
            w.id AS workout_id,
            w.user_id AS user_id,
            w.date AS workout_date,
            w.start_time AS start_time,
            w.end_time AS end_time,
            w.exercises_json AS exercises_json,
            CASE
                WHEN w.end_time IS NOT NULL AND w.start_time IS NOT NULL
                THEN MAX(0, (strftime('%s', w.end_time) - strftime('%s', w.start_time)) / 60)
                ELSE 0
            END AS duration_minutes,
            COALESCE(SUM(
                CASE
                    WHEN (el.equipment IS NULL OR el.equipment != 'BODYWEIGHT_ONLY')
                     AND es.weight_kg > 0
                     AND es.reps > 0
                    THEN es.weight_kg * es.reps
                    ELSE 0
                END
            ), 0) AS total_volume,
            COALESCE(SUM(CASE WHEN es.reps > 0 THEN es.reps ELSE 0 END), 0) AS total_reps,
            COUNT(es.id) AS total_sets,
            COUNT(DISTINCT e.exercise_library_id) AS exercise_count,
            w.created_at AS created_at,
            w.updated_at AS updated_at
        FROM workouts w
        LEFT JOIN exercises e
            ON w.id = e.workout_id
           AND w.user_id = e.user_id
        LEFT JOIN exercise_sets es
            ON e.id = es.exercise_id
           AND e.user_id = es.user_id
        LEFT JOIN exercise_library el
            ON e.exercise_library_id = el.id
        WHERE w.status = 'COMPLETED'
        GROUP BY
            w.id,
            w.user_id,
            w.date,
            w.start_time,
            w.end_time,
            w.exercises_json,
            w.created_at,
            w.updated_at
    """
)
data class CompletedWorkoutMetricsView(
    @ColumnInfo(name = "workout_id")
    val workoutId: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "workout_date")
    val workoutDate: String,
    @ColumnInfo(name = "start_time")
    val startTime: String?,
    @ColumnInfo(name = "end_time")
    val endTime: String?,
    @ColumnInfo(name = "exercises_json")
    val exercisesJson: String,
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
    @ColumnInfo(name = "created_at")
    val createdAt: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: String
)
