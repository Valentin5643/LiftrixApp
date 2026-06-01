package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.example.liftrix.annotations.UserScoped
import com.example.liftrix.data.local.entity.analytics.CompletedWorkoutMetricReadModelEntity
import com.example.liftrix.data.local.entity.analytics.DailyWorkoutVolumeReadModelEntity
import com.example.liftrix.data.local.entity.analytics.ExercisePrReadModelEntity
import com.example.liftrix.data.local.entity.analytics.MuscleGroupDailyReadModelEntity
import com.example.liftrix.data.local.entity.analytics.WeeklyWorkoutVolumeReadModelEntity

@Dao
interface AnalyticsReadModelDao {

    @Query("""
        SELECT workout_date
        FROM completed_workout_metric_read_models
        WHERE user_id = :userId
        AND workout_id = :workoutId
        LIMIT 1
    """)
    @UserScoped
    suspend fun getReadModelDateForWorkout(userId: String, workoutId: String): String?

    @Query("""
        SELECT DISTINCT e.exercise_library_id
        FROM exercises e
        JOIN workouts w
            ON e.workout_id = w.id
           AND e.user_id = w.user_id
        WHERE w.user_id = :userId
        AND w.id = :workoutId
    """)
    @UserScoped
    suspend fun getExerciseLibraryIdsForWorkout(userId: String, workoutId: String): List<String>

    @Query("""
        DELETE FROM completed_workout_metric_read_models
        WHERE user_id = :userId
        AND workout_id = :workoutId
    """)
    @UserScoped
    suspend fun deleteCompletedWorkoutMetric(userId: String, workoutId: String): Int

    @Query("""
        INSERT OR REPLACE INTO completed_workout_metric_read_models (
            workout_id,
            user_id,
            workout_date,
            duration_minutes,
            total_volume,
            total_reps,
            total_sets,
            exercise_count,
            updated_at
        )
        SELECT
            workout_id,
            user_id,
            workout_date,
            duration_minutes,
            total_volume,
            total_reps,
            total_sets,
            exercise_count,
            :updatedAt
        FROM completed_workout_metrics_view
        WHERE user_id = :userId
        AND workout_id = :workoutId
    """)
    @UserScoped
    suspend fun insertCompletedWorkoutMetricFromSource(
        userId: String,
        workoutId: String,
        updatedAt: Long
    )

    @Query("""
        DELETE FROM workout_daily_volume_read_models
        WHERE user_id = :userId
        AND workout_date = :workoutDate
    """)
    @UserScoped
    suspend fun deleteDailyVolume(userId: String, workoutDate: String): Int

    @Query("""
        INSERT OR REPLACE INTO workout_daily_volume_read_models (
            user_id,
            workout_date,
            total_volume,
            total_reps,
            total_sets,
            workout_count,
            exercise_count,
            total_duration_minutes,
            updated_at
        )
        SELECT
            user_id,
            workout_date,
            SUM(total_volume),
            SUM(total_reps),
            SUM(total_sets),
            COUNT(workout_id),
            SUM(exercise_count),
            SUM(duration_minutes),
            :updatedAt
        FROM completed_workout_metric_read_models
        WHERE user_id = :userId
        AND workout_date = :workoutDate
        GROUP BY user_id, workout_date
    """)
    @UserScoped
    suspend fun insertDailyVolumeFromCompletedMetrics(
        userId: String,
        workoutDate: String,
        updatedAt: Long
    )

    @Query("""
        DELETE FROM workout_weekly_volume_read_models
        WHERE user_id = :userId
        AND week_start_date = :weekStartDate
    """)
    @UserScoped
    suspend fun deleteWeeklyVolume(userId: String, weekStartDate: String): Int

    @Query("""
        INSERT OR REPLACE INTO workout_weekly_volume_read_models (
            user_id,
            week_start_date,
            week_end_date,
            total_volume,
            total_reps,
            total_sets,
            workout_count,
            exercise_count,
            total_duration_minutes,
            updated_at
        )
        SELECT
            user_id,
            :weekStartDate,
            :weekEndDate,
            SUM(total_volume),
            SUM(total_reps),
            SUM(total_sets),
            SUM(workout_count),
            SUM(exercise_count),
            SUM(total_duration_minutes),
            :updatedAt
        FROM workout_daily_volume_read_models
        WHERE user_id = :userId
        AND workout_date BETWEEN :weekStartDate AND :weekEndDate
        GROUP BY user_id
    """)
    @UserScoped
    suspend fun insertWeeklyVolumeFromDaily(
        userId: String,
        weekStartDate: String,
        weekEndDate: String,
        updatedAt: Long
    )

    @Query("""
        DELETE FROM exercise_pr_read_models
        WHERE user_id = :userId
        AND exercise_library_id = :exerciseLibraryId
    """)
    @UserScoped
    suspend fun deleteExercisePr(userId: String, exerciseLibraryId: String): Int

    @Query("""
        INSERT OR REPLACE INTO exercise_pr_read_models (
            user_id,
            exercise_library_id,
            exercise_name,
            primary_muscle_group,
            max_estimated_one_rm,
            max_weight_kg,
            max_reps,
            total_volume,
            total_sets,
            last_pr_at,
            source_workout_id,
            updated_at
        )
        SELECT
            user_id,
            exercise_library_id,
            COALESCE(exercise_name, exercise_library_id),
            COALESCE(primary_muscle_group, 'UNKNOWN'),
            COALESCE(MAX(estimated_one_rm), 0),
            COALESCE(MAX(weight_kg), 0),
            COALESCE(MAX(reps), 0),
            COALESCE(SUM(set_volume), 0),
            COUNT(set_id),
            MAX(completed_at),
            (
                SELECT v2.workout_id
                FROM exercise_set_performance_view v2
                WHERE v2.user_id = :userId
                AND v2.exercise_library_id = :exerciseLibraryId
                AND v2.estimated_one_rm IS NOT NULL
                ORDER BY v2.estimated_one_rm DESC, v2.completed_at DESC
                LIMIT 1
            ),
            :updatedAt
        FROM exercise_set_performance_view
        WHERE user_id = :userId
        AND exercise_library_id = :exerciseLibraryId
        AND estimated_one_rm IS NOT NULL
        GROUP BY user_id, exercise_library_id, exercise_name, primary_muscle_group
    """)
    @UserScoped
    suspend fun insertExercisePrFromSource(
        userId: String,
        exerciseLibraryId: String,
        updatedAt: Long
    )

    @Query("""
        DELETE FROM muscle_group_daily_read_models
        WHERE user_id = :userId
        AND workout_date = :workoutDate
    """)
    @UserScoped
    suspend fun deleteMuscleGroupDailyForDate(userId: String, workoutDate: String): Int

    @Query("""
        INSERT OR REPLACE INTO muscle_group_daily_read_models (
            user_id,
            workout_date,
            primary_muscle_group,
            total_volume,
            total_reps,
            total_sets,
            exercise_count,
            workout_count,
            updated_at
        )
        SELECT
            user_id,
            activity_date,
            COALESCE(primary_muscle_group, 'UNKNOWN'),
            SUM(set_volume),
            SUM(rep_count),
            COUNT(set_id),
            COUNT(DISTINCT exercise_library_id),
            COUNT(DISTINCT workout_id),
            :updatedAt
        FROM exercise_set_performance_view
        WHERE user_id = :userId
        AND activity_date = :workoutDate
        AND rep_count > 0
        GROUP BY user_id, activity_date, COALESCE(primary_muscle_group, 'UNKNOWN')
    """)
    @UserScoped
    suspend fun insertMuscleGroupDailyForDateFromSource(
        userId: String,
        workoutDate: String,
        updatedAt: Long
    )

    @Transaction
    suspend fun refreshWorkoutReadModels(
        userId: String,
        workoutId: String,
        oldWorkoutDate: String? = null,
        oldExerciseLibraryIds: List<String> = emptyList()
    ) {
        val previousDate = oldWorkoutDate ?: getReadModelDateForWorkout(userId, workoutId)
        val updatedAt = System.currentTimeMillis()
        val affectedExerciseIds = (oldExerciseLibraryIds + getExerciseLibraryIdsForWorkout(userId, workoutId)).distinct()

        deleteCompletedWorkoutMetric(userId, workoutId)
        insertCompletedWorkoutMetricFromSource(userId, workoutId, updatedAt)

        val currentDate = getReadModelDateForWorkout(userId, workoutId)
        listOfNotNull(previousDate, currentDate).distinct().forEach { workoutDate ->
            refreshDailyVolume(userId, workoutDate, updatedAt)
            refreshWeeklyVolume(userId, workoutDate, updatedAt)
            refreshMuscleGroupDaily(userId, workoutDate, updatedAt)
        }

        affectedExerciseIds.forEach { exerciseLibraryId ->
            refreshExercisePr(userId, exerciseLibraryId, updatedAt)
        }
    }

    @Transaction
    suspend fun deleteWorkoutReadModels(
        userId: String,
        workoutId: String,
        oldWorkoutDate: String? = null,
        oldExerciseLibraryIds: List<String> = emptyList()
    ) {
        val previousDate = oldWorkoutDate ?: getReadModelDateForWorkout(userId, workoutId)
        val affectedExerciseIds = (oldExerciseLibraryIds + getExerciseLibraryIdsForWorkout(userId, workoutId)).distinct()
        val updatedAt = System.currentTimeMillis()

        deleteCompletedWorkoutMetric(userId, workoutId)
        previousDate?.let { workoutDate ->
            refreshDailyVolume(userId, workoutDate, updatedAt)
            refreshWeeklyVolume(userId, workoutDate, updatedAt)
            refreshMuscleGroupDaily(userId, workoutDate, updatedAt)
        }
        affectedExerciseIds.forEach { exerciseLibraryId ->
            refreshExercisePr(userId, exerciseLibraryId, updatedAt)
        }
    }

    @Query("DELETE FROM completed_workout_metric_read_models WHERE user_id = :userId")
    @UserScoped
    suspend fun deleteAllCompletedWorkoutMetricsForUser(userId: String): Int

    @Query("DELETE FROM workout_daily_volume_read_models WHERE user_id = :userId")
    @UserScoped
    suspend fun deleteAllDailyVolumesForUser(userId: String): Int

    @Query("DELETE FROM workout_weekly_volume_read_models WHERE user_id = :userId")
    @UserScoped
    suspend fun deleteAllWeeklyVolumesForUser(userId: String): Int

    @Query("DELETE FROM exercise_pr_read_models WHERE user_id = :userId")
    @UserScoped
    suspend fun deleteAllExercisePrsForUser(userId: String): Int

    @Query("DELETE FROM muscle_group_daily_read_models WHERE user_id = :userId")
    @UserScoped
    suspend fun deleteAllMuscleGroupDailyForUser(userId: String): Int

    @Transaction
    suspend fun deleteAllReadModelsForUser(userId: String) {
        deleteAllCompletedWorkoutMetricsForUser(userId)
        deleteAllDailyVolumesForUser(userId)
        deleteAllWeeklyVolumesForUser(userId)
        deleteAllExercisePrsForUser(userId)
        deleteAllMuscleGroupDailyForUser(userId)
    }

    @Query("""
        SELECT *
        FROM workout_daily_volume_read_models
        WHERE user_id = :userId
        AND workout_date BETWEEN :startDate AND :endDate
        ORDER BY workout_date
    """)
    @UserScoped
    suspend fun getDailyVolumes(
        userId: String,
        startDate: String,
        endDate: String
    ): List<DailyWorkoutVolumeReadModelEntity>

    @Query("""
        SELECT *
        FROM workout_weekly_volume_read_models
        WHERE user_id = :userId
        AND week_start_date BETWEEN :startDate AND :endDate
        ORDER BY week_start_date
    """)
    @UserScoped
    suspend fun getWeeklyVolumes(
        userId: String,
        startDate: String,
        endDate: String
    ): List<WeeklyWorkoutVolumeReadModelEntity>

    @Query("""
        SELECT *
        FROM completed_workout_metric_read_models
        WHERE user_id = :userId
        AND workout_date BETWEEN :startDate AND :endDate
        ORDER BY workout_date
        LIMIT :limit
    """)
    @UserScoped
    suspend fun getCompletedWorkoutMetrics(
        userId: String,
        startDate: String,
        endDate: String,
        limit: Int = 10000
    ): List<CompletedWorkoutMetricReadModelEntity>

    @Query("""
        SELECT *
        FROM exercise_pr_read_models
        WHERE user_id = :userId
        ORDER BY max_estimated_one_rm DESC
        LIMIT :limit
    """)
    @UserScoped
    suspend fun getExercisePrs(userId: String, limit: Int = 100): List<ExercisePrReadModelEntity>

    @Query("""
        SELECT
            primary_muscle_group AS primary_muscle_group,
            SUM(total_volume) AS total_volume,
            SUM(exercise_count) AS exercise_count,
            SUM(total_sets) AS total_sets
        FROM muscle_group_daily_read_models
        WHERE user_id = :userId
        AND workout_date BETWEEN :startDate AND :endDate
        GROUP BY primary_muscle_group
        ORDER BY total_volume DESC
        LIMIT :limit
    """)
    @UserScoped
    suspend fun getMuscleGroupVolumeSummary(
        userId: String,
        startDate: String,
        endDate: String,
        limit: Int = 50
    ): List<ReadModelMuscleGroupVolumeResult>

    private suspend fun refreshDailyVolume(userId: String, workoutDate: String, updatedAt: Long) {
        deleteDailyVolume(userId, workoutDate)
        insertDailyVolumeFromCompletedMetrics(userId, workoutDate, updatedAt)
    }

    private suspend fun refreshWeeklyVolume(userId: String, workoutDate: String, updatedAt: Long) {
        val weekStartDate = java.time.LocalDate.parse(workoutDate)
            .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val weekEndDate = weekStartDate.plusDays(6)
        deleteWeeklyVolume(userId, weekStartDate.toString())
        insertWeeklyVolumeFromDaily(userId, weekStartDate.toString(), weekEndDate.toString(), updatedAt)
    }

    private suspend fun refreshExercisePr(userId: String, exerciseLibraryId: String, updatedAt: Long) {
        deleteExercisePr(userId, exerciseLibraryId)
        insertExercisePrFromSource(userId, exerciseLibraryId, updatedAt)
    }

    private suspend fun refreshMuscleGroupDaily(userId: String, workoutDate: String, updatedAt: Long) {
        deleteMuscleGroupDailyForDate(userId, workoutDate)
        insertMuscleGroupDailyForDateFromSource(userId, workoutDate, updatedAt)
    }
}

data class ReadModelMuscleGroupVolumeResult(
    val primary_muscle_group: String,
    val total_volume: Double,
    val exercise_count: Int,
    val total_sets: Int
)
