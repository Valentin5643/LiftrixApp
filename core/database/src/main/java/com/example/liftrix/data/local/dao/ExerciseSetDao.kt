package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.liftrix.data.local.entity.ExerciseSetEntity
import com.example.liftrix.annotations.UserScoped
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseSetDao {
    
    suspend fun getSetsByExercise(exerciseId: Long, userId: String): List<ExerciseSetEntity> {
        return getSetsForExercise(exerciseId, userId)
    }

    @Query("""
        SELECT es.* FROM exercise_sets es
        JOIN exercises e ON es.exercise_id = e.id
        JOIN workouts w ON e.workout_id = w.id
        WHERE e.id = :exerciseId
        AND w.user_id = :userId
        ORDER BY es.set_number ASC
    """)
    @UserScoped
    suspend fun getSetsForExercise(exerciseId: Long, userId: String): List<ExerciseSetEntity>
    
    @Query("""
        SELECT es.* FROM exercise_sets es
        JOIN exercises e ON es.exercise_id = e.id
        JOIN workouts w ON e.workout_id = w.id
        WHERE e.id = :exerciseId
        AND w.user_id = :userId
        ORDER BY es.set_number ASC
    """)
    @UserScoped
    fun getSetsByExerciseFlow(exerciseId: Long, userId: String): Flow<List<ExerciseSetEntity>>
    
    @Query("""
        SELECT es.* FROM exercise_sets es
        JOIN exercises e ON es.exercise_id = e.id
        JOIN workouts w ON e.workout_id = w.id
        WHERE es.id = :setId
        AND w.user_id = :userId
    """)
    @UserScoped
    suspend fun getSetById(setId: Long, userId: String): ExerciseSetEntity?
    
    @Query("""
        SELECT es.* FROM exercise_sets es 
        JOIN exercises e ON es.exercise_id = e.id 
        JOIN workouts w ON e.workout_id = w.id 
        WHERE w.user_id = :userId 
        AND e.exercise_library_id = :exerciseLibraryId 
        AND es.completed_at IS NOT NULL
        ORDER BY es.completed_at DESC 
        LIMIT :limit
    """)
    @UserScoped
    suspend fun getExerciseHistory(userId: String, exerciseLibraryId: String, limit: Int): List<ExerciseSetEntity>
    
    @Query("""
        SELECT es.* FROM exercise_sets es
        JOIN exercises e ON es.exercise_id = e.id
        JOIN workouts w ON e.workout_id = w.id
        WHERE w.user_id = :userId
        AND es.completed_at IS NOT NULL
        ORDER BY es.completed_at DESC
        LIMIT :limit
    """)
    @UserScoped
    suspend fun getRecentCompletedSets(userId: String, limit: Int): List<ExerciseSetEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(set: ExerciseSetEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(sets: List<ExerciseSetEntity>): List<Long>
    
    @Update
    suspend fun updateSet(set: ExerciseSetEntity): Int
    
    @Update
    suspend fun updateSets(sets: List<ExerciseSetEntity>): Int
    
    @Delete
    suspend fun deleteSet(set: ExerciseSetEntity): Int
    
    @Query("""
        DELETE FROM exercise_sets
        WHERE id = :setId
        AND exercise_id IN (
            SELECT e.id FROM exercises e
            JOIN workouts w ON e.workout_id = w.id
            WHERE w.user_id = :userId
        )
    """)
    @UserScoped
    suspend fun deleteSetById(setId: Long, userId: String): Int
    
    @Query("""
        DELETE FROM exercise_sets
        WHERE exercise_id = :exerciseId
        AND exercise_id IN (
            SELECT e.id FROM exercises e
            JOIN workouts w ON e.workout_id = w.id
            WHERE w.user_id = :userId
        )
    """)
    @UserScoped
    suspend fun deleteSetsForExercise(exerciseId: Long, userId: String): Int
    
    @Query("""
        DELETE FROM exercise_sets
        WHERE exercise_id IN (
            SELECT e.id FROM exercises e
            JOIN workouts w ON e.workout_id = w.id
            WHERE w.id = :workoutId
            AND w.user_id = :userId
        )
    """)
    @UserScoped
    suspend fun deleteSetsForWorkout(workoutId: String, userId: String): Int
    
    @Query("""
        SELECT COUNT(*) FROM exercise_sets
        WHERE exercise_id = :exerciseId
        AND exercise_id IN (
            SELECT e.id FROM exercises e
            JOIN workouts w ON e.workout_id = w.id
            WHERE w.user_id = :userId
        )
    """)
    @UserScoped
    suspend fun getSetCountForExercise(exerciseId: Long, userId: String): Int
    
    @Query("""
        SELECT COUNT(*) FROM exercise_sets
        WHERE exercise_id = :exerciseId
        AND completed_at IS NOT NULL
        AND exercise_id IN (
            SELECT e.id FROM exercises e
            JOIN workouts w ON e.workout_id = w.id
            WHERE w.user_id = :userId
        )
    """)
    @UserScoped
    suspend fun getCompletedSetCountForExercise(exerciseId: Long, userId: String): Int
    
    @Query("""
        UPDATE exercise_sets
        SET completed_at = :completedAt
        WHERE id = :setId
        AND exercise_id IN (
            SELECT e.id FROM exercises e
            JOIN workouts w ON e.workout_id = w.id
            WHERE w.user_id = :userId
        )
    """)
    @UserScoped
    suspend fun markSetCompleted(setId: Long, userId: String, completedAt: Long): Int
    
    @Query("""
        UPDATE exercise_sets
        SET completed_at = NULL
        WHERE id = :setId
        AND exercise_id IN (
            SELECT e.id FROM exercises e
            JOIN workouts w ON e.workout_id = w.id
            WHERE w.user_id = :userId
        )
    """)
    @UserScoped
    suspend fun markSetIncomplete(setId: Long, userId: String): Int
    
    @Transaction
    suspend fun replaceAllSetsForExercise(
        exerciseId: Long,
        userId: String,
        newSets: List<ExerciseSetEntity>
    ) {
        deleteSetsForExercise(exerciseId, userId)
        insertSets(newSets.map { it.copy(exerciseId = exerciseId, userId = userId) })
    }
    
    @Query("""
        SELECT MAX(set_number) FROM exercise_sets
        WHERE exercise_id = :exerciseId
        AND exercise_id IN (
            SELECT e.id FROM exercises e
            JOIN workouts w ON e.workout_id = w.id
            WHERE w.user_id = :userId
        )
    """)
    @UserScoped
    suspend fun getMaxSetNumber(exerciseId: Long, userId: String): Int?
    
    @Query("""
        SELECT AVG(es.weight_kg) FROM exercise_sets es
        JOIN exercises e ON es.exercise_id = e.id
        JOIN workouts w ON e.workout_id = w.id
        WHERE w.user_id = :userId
        AND e.exercise_library_id = :exerciseLibraryId
        AND es.weight_kg IS NOT NULL
        AND es.completed_at IS NOT NULL
    """)
    @UserScoped
    suspend fun getAverageWeightForExercise(exerciseLibraryId: String, userId: String): Float?
    
    @Query("""
        SELECT MAX(es.weight_kg) FROM exercise_sets es
        JOIN exercises e ON es.exercise_id = e.id
        JOIN workouts w ON e.workout_id = w.id
        WHERE w.user_id = :userId
        AND e.exercise_library_id = :exerciseLibraryId
        AND es.weight_kg IS NOT NULL
        AND es.completed_at IS NOT NULL
    """)
    @UserScoped
    suspend fun getMaxWeightForExercise(exerciseLibraryId: String, userId: String): Float?
    
    // Optimized analytics queries for real-time dashboard data integration
    
    /**
     * Gets 1RM calculations for specified exercises within date range for user
     * Uses Epley formula: weight * (1 + reps/30) for estimated 1RM
     * Optimized with index on (exercise_id, completed_at, weight_kg, reps)
     * 
     * @param userId User ID for scoping
     * @param exerciseLibraryIds List of exercise library IDs to filter
     * @param startDate Start date for range (inclusive)
     * @param endDate End date for range (inclusive)
     * @return List of OneRmResult ordered by date DESC
     */
    @Query("""
        SELECT 
            exercise_library_id,
            COALESCE(exercise_name, exercise_library_id) as exercise_name,
            COALESCE(weight_kg, 0) as weight_kg,
            COALESCE(reps, 0) as reps,
            COALESCE(completed_at, CAST(strftime('%s', workout_date) AS INTEGER) * 1000) as completed_at,
            COALESCE(estimated_one_rm, 0) as estimated_one_rm
        FROM exercise_set_performance_view
        WHERE user_id = :userId
        AND exercise_library_id IN (:exerciseLibraryIds)
        AND estimated_one_rm IS NOT NULL
        AND activity_date BETWEEN :startDate AND :endDate
        ORDER BY completed_at DESC, weight_kg DESC
        LIMIT :limit
    """)
    @UserScoped
    suspend fun getOneRmDataForExercises(
        userId: String,
        exerciseLibraryIds: List<String>,
        startDate: String,
        endDate: String,
        limit: Int = 5000
    ): List<OneRmResult>
    
    /**
     * Gets volume data aggregated by exercise for specified user and date range
     * Volume = weight * reps summed for all sets
     * Optimized for volume analysis detail screens
     * 
     * @param userId User ID for scoping
     * @param startDate Start date for range (inclusive)
     * @param endDate End date for range (inclusive)
     * @return List of ExerciseVolumeResult ordered by total volume DESC
     */
    @Query("""
        SELECT 
            exercise_library_id,
            COALESCE(exercise_name, exercise_library_id) as exercise_name,
            SUM(set_volume) as total_volume,
            COUNT(set_id) as total_sets,
            AVG(weight_kg) as avg_weight,
            MAX(weight_kg) as max_weight
        FROM exercise_set_performance_view
        WHERE user_id = :userId
        AND set_volume > 0
        AND activity_date BETWEEN :startDate AND :endDate
        GROUP BY exercise_library_id, exercise_name
        ORDER BY total_volume DESC
        LIMIT :limit
    """)
    @UserScoped
    suspend fun getVolumeDataByExercise(
        userId: String,
        startDate: String,
        endDate: String,
        limit: Int = 1000
    ): List<ExerciseVolumeResult>
    
    /**
     * Gets volume data aggregated by muscle group for muscle group analysis
     * Joins with exercise library to get muscle group mappings
     * 
     * @param userId User ID for scoping
     * @param startDate Start date for range (inclusive)
     * @param endDate End date for range (inclusive)
     * @return List of MuscleGroupVolumeResult ordered by total volume DESC
     */
    @Query("""
        SELECT 
            COALESCE(primary_muscle_group, 'UNKNOWN') as primary_muscle_group,
            SUM(set_volume) as total_volume,
            COUNT(DISTINCT exercise_library_id) as exercise_count,
            COUNT(set_id) as total_sets
        FROM exercise_set_performance_view
        WHERE user_id = :userId
        AND set_volume > 0
        AND activity_date BETWEEN :startDate AND :endDate
        GROUP BY primary_muscle_group
        ORDER BY total_volume DESC
        LIMIT :limit
    """)
    @UserScoped
    suspend fun getVolumeDataByMuscleGroup(
        userId: String,
        startDate: String,
        endDate: String,
        limit: Int = 50
    ): List<MuscleGroupVolumeResult>
    
    /**
     * Gets all 1RM data for user without exercise filter
     * Uses Epley formula for estimated 1RM calculation
     * 
     * @param userId User ID for scoping
     * @param startDate Start date for range (inclusive)
     * @param endDate End date for range (inclusive)
     * @return List of OneRmResult ordered by date DESC
     */
    @Query("""
        SELECT 
            exercise_library_id,
            COALESCE(exercise_name, exercise_library_id) as exercise_name,
            COALESCE(weight_kg, 0) as weight_kg,
            COALESCE(reps, 0) as reps,
            COALESCE(completed_at, CAST(strftime('%s', workout_date) AS INTEGER) * 1000) as completed_at,
            COALESCE(estimated_one_rm, 0) as estimated_one_rm
        FROM exercise_set_performance_view
        WHERE user_id = :userId
        AND estimated_one_rm IS NOT NULL
        AND activity_date BETWEEN :startDate AND :endDate
        ORDER BY completed_at DESC, weight_kg DESC
        LIMIT 5000
    """)
    @UserScoped
    suspend fun getAllOneRmData(
        userId: String,
        startDate: String,
        endDate: String
    ): List<OneRmResult>

    @Query("""
        SELECT
            exercise_library_id,
            COALESCE(exercise_name, exercise_library_id) AS exercise_name,
            activity_date,
            weight_kg,
            reps,
            COALESCE(completed_at, CAST(strftime('%s', activity_date) AS INTEGER) * 1000) AS completed_at
        FROM exercise_set_performance_view
        WHERE user_id = :userId
        AND activity_date BETWEEN :startDate AND :endDate
        AND weight_kg IS NOT NULL
        AND weight_kg > 0
        AND reps IS NOT NULL
        AND reps > 0
        AND reps < 37
        AND (equipment IS NULL OR equipment != 'BODYWEIGHT_ONLY')
        ORDER BY activity_date ASC, exercise_name ASC, completed_at ASC
        LIMIT 5000
    """)
    @UserScoped
    suspend fun getStrengthForecastSetSamples(
        userId: String,
        startDate: String,
        endDate: String
    ): List<StrengthForecastSetSampleResult>
    
    /**
     * Gets daily volume data by exercise for time series visualization
     * 
     * @param userId User ID for scoping
     * @param startDate Start date for range (inclusive)
     * @param endDate End date for range (inclusive)
     * @return List of DailyExerciseVolumeResult ordered by date, exercise ASC
     */
    @Query("""
        SELECT 
            activity_date as date,
            exercise_library_id,
            COALESCE(exercise_name, exercise_library_id) as exercise_name,
            SUM(set_volume) as total_volume,
            COUNT(set_id) as total_sets
        FROM exercise_set_performance_view
        WHERE user_id = :userId
        AND set_volume > 0
        AND activity_date BETWEEN :startDate AND :endDate
        GROUP BY activity_date, exercise_library_id, exercise_name
        ORDER BY date ASC, exercise_name ASC
    """)
    @UserScoped
    suspend fun getDailyVolumeDataByExercise(
        userId: String,
        startDate: String,
        endDate: String
    ): List<DailyExerciseVolumeResult>

    @Query("""
        SELECT
            activity_date as date,
            exercise_library_id,
            COALESCE(exercise_name, exercise_library_id) as exercise_name,
            SUM(set_volume) as total_volume,
            COUNT(set_id) as total_sets
        FROM exercise_set_performance_view
        WHERE user_id = :userId
        AND exercise_library_id = :exerciseLibraryId
        AND set_volume > 0
        AND activity_date BETWEEN :startDate AND :endDate
        GROUP BY activity_date, exercise_library_id, exercise_name
        ORDER BY date ASC
    """)
    @UserScoped
    suspend fun getDailyVolumeForExerciseFromView(
        userId: String,
        exerciseLibraryId: String,
        startDate: String,
        endDate: String
    ): List<DailyExerciseVolumeResult>
    
    /**
     * Gets daily volume data by muscle group for time series visualization
     * 
     * @param userId User ID for scoping
     * @param startDate Start date for range (inclusive)
     * @param endDate End date for range (inclusive)
     * @return List of DailyMuscleGroupVolumeResult ordered by date, muscle group ASC
     */
    @Query("""
        SELECT 
            activity_date as date,
            COALESCE(primary_muscle_group, 'UNKNOWN') as primary_muscle_group,
            SUM(set_volume) as total_volume,
            COUNT(DISTINCT exercise_library_id) as exercise_count,
            COUNT(set_id) as total_sets
        FROM exercise_set_performance_view
        WHERE user_id = :userId
        AND set_volume > 0
        AND activity_date BETWEEN :startDate AND :endDate
        GROUP BY activity_date, primary_muscle_group
        ORDER BY date ASC, primary_muscle_group ASC
    """)
    @UserScoped
    suspend fun getDailyVolumeDataByMuscleGroup(
        userId: String,
        startDate: String,
        endDate: String
    ): List<DailyMuscleGroupVolumeResult>
    
    /**
     * Gets daily volume data for time series visualization
     * Aggregates volume by date for trend analysis
     * 
     * @param userId User ID for scoping
     * @param startDate Start date for range (inclusive)
     * @param endDate End date for range (inclusive)
     * @return List of DailyVolumeResult ordered by date ASC
     */
    @Query("""
        SELECT 
            activity_date as date,
            SUM(set_volume) as total_volume,
            COUNT(set_id) as total_sets,
            COUNT(DISTINCT exercise_library_id) as exercise_count
        FROM exercise_set_performance_view
        WHERE user_id = :userId
        AND set_volume > 0
        AND activity_date BETWEEN :startDate AND :endDate
        GROUP BY activity_date
        ORDER BY date ASC
    """)
    @UserScoped
    suspend fun getDailyVolumeData(
        userId: String,
        startDate: String,
        endDate: String
    ): List<DailyVolumeResult>

    @Query("""
        SELECT
            activity_date as date,
            SUM(rep_count) as total_reps,
            COUNT(set_id) as total_sets,
            COUNT(DISTINCT exercise_library_id) as exercise_count
        FROM exercise_set_performance_view
        WHERE user_id = :userId
        AND rep_count > 0
        AND activity_date BETWEEN :startDate AND :endDate
        GROUP BY activity_date
        ORDER BY date ASC
    """)
    @UserScoped
    suspend fun getDailyRepActivityData(
        userId: String,
        startDate: String,
        endDate: String
    ): List<DailyRepActivityResult>

    @Query("""
        SELECT
            workout_id,
            SUM(set_volume) as total_volume,
            SUM(rep_count) as total_reps,
            COUNT(set_id) as total_sets
        FROM exercise_set_performance_view
        WHERE user_id = :userId
        AND workout_date BETWEEN :startDate AND :endDate
        GROUP BY workout_id
    """)
    @UserScoped
    suspend fun getWorkoutSetActivityData(
        userId: String,
        startDate: String,
        endDate: String
    ): List<WorkoutSetActivityResult>

    @Query("""
        SELECT
            COALESCE(primary_muscle_group, 'UNKNOWN') as primary_muscle_group,
            SUM(rep_count) as total_reps,
            COUNT(DISTINCT exercise_library_id) as exercise_count,
            COUNT(set_id) as total_sets
        FROM exercise_set_performance_view
        WHERE user_id = :userId
        AND rep_count > 0
        AND activity_date BETWEEN :startDate AND :endDate
        GROUP BY primary_muscle_group
        ORDER BY total_reps DESC
        LIMIT :limit
    """)
    @UserScoped
    suspend fun getRepActivityByMuscleGroup(
        userId: String,
        startDate: String,
        endDate: String,
        limit: Int = 50
    ): List<MuscleGroupRepActivityResult>

    /**
     * Gets normalized exercise performance aggregates for ranking and analytics.
     */
    @Query("""
        SELECT
            exercise_library_id,
            COALESCE(exercise_name, exercise_library_id) as exercise_name,
            COALESCE(primary_muscle_group, 'UNKNOWN') as primary_muscle_group,
            SUM(set_volume) as total_volume,
            COUNT(set_id) as total_sets,
            COUNT(DISTINCT workout_date) as workout_days,
            MAX(estimated_one_rm) as max_estimated_one_rm,
            (
                SUM(set_volume) * 0.4
                + COUNT(DISTINCT workout_date) * 10.0
                + COUNT(set_id) * 2.0
                + MAX(estimated_one_rm) * 0.3
            ) as performance_score
        FROM exercise_set_performance_view
        WHERE user_id = :userId
        AND estimated_one_rm IS NOT NULL
        AND activity_date BETWEEN :startDate AND :endDate
        GROUP BY exercise_library_id, exercise_name, primary_muscle_group
        ORDER BY performance_score DESC
        LIMIT :limit
    """)
    @UserScoped
    suspend fun getExercisePerformanceData(
        userId: String,
        startDate: String,
        endDate: String,
        limit: Int = 100
    ): List<ExercisePerformanceResult>

    /**
     * Gets normalized per-day exercise performance history.
     */
    @Query("""
        SELECT
            activity_date as date,
            exercise_library_id,
            COALESCE(exercise_name, exercise_library_id) as exercise_name,
            SUM(set_volume) as total_volume,
            COUNT(set_id) as total_sets,
            COALESCE(MAX(weight_kg), 0) as max_weight,
            COALESCE(MAX(reps), 0) as max_reps,
            COALESCE(MAX(estimated_one_rm), 0) as max_estimated_one_rm
        FROM exercise_set_performance_view
        WHERE user_id = :userId
        AND estimated_one_rm IS NOT NULL
        AND activity_date BETWEEN :startDate AND :endDate
        GROUP BY activity_date, exercise_library_id, exercise_name
        ORDER BY date ASC
    """)
    @UserScoped
    suspend fun getExercisePerformanceHistory(
        userId: String,
        startDate: String,
        endDate: String
    ): List<ExercisePerformanceHistoryResult>
    
    @Query("SELECT COUNT(*) FROM exercise_sets es JOIN exercises e ON es.exercise_id = e.id JOIN workouts w ON e.workout_id = w.id WHERE w.user_id = :userId AND es.completed_at IS NOT NULL")
    @UserScoped
    suspend fun getUserSetCount(userId: String): Int
    
    @Query("SELECT COUNT(*) FROM workouts WHERE user_id = :userId")
    @UserScoped
    suspend fun getUserWorkoutCount(userId: String): Int
    
    // Debug queries to diagnose the volume data issue
    @Query("SELECT COUNT(*) FROM exercise_sets es JOIN exercises e ON es.exercise_id = e.id JOIN workouts w ON e.workout_id = w.id WHERE w.user_id = :userId")
    @UserScoped
    suspend fun getAllUserSetCount(userId: String): Int
    
    @Query("SELECT COUNT(*) FROM exercise_sets es JOIN exercises e ON es.exercise_id = e.id JOIN workouts w ON e.workout_id = w.id WHERE w.user_id = :userId AND es.completed_at IS NULL")
    @UserScoped
    suspend fun getNullCompletedSetCount(userId: String): Int
    
    @Query("""
        SELECT 
            es.id,
            es.completed_at,
            es.weight_kg,
            es.reps,
            w.date as workout_date,
            w.status as workout_status,
            DATE(es.completed_at / 1000, 'unixepoch') as converted_date
        FROM exercise_sets es 
        JOIN exercises e ON es.exercise_id = e.id 
        JOIN workouts w ON e.workout_id = w.id 
        WHERE w.user_id = :userId 
        ORDER BY es.id DESC 
        LIMIT 10
    """)
    @UserScoped
    suspend fun getDebugSetSample(userId: String): List<DebugSetResult>
    
    @Query("SELECT COUNT(*) FROM exercise_sets es JOIN exercises e ON es.exercise_id = e.id JOIN workouts w ON e.workout_id = w.id WHERE w.user_id = :userId AND es.weight_kg > 0 AND es.reps > 0")
    @UserScoped
    suspend fun getUserValidSetCount(userId: String): Int
    
    /**
     * Gets exercise performance rankings with plateau detection
     * Calculates performance score based on volume progression and consistency
     * 
     * @param userId User ID for scoping
     * @param startDate Start date for analysis (inclusive)
     * @param endDate End date for analysis (inclusive)
     * @return List of ExerciseRankingResult ordered by performance score DESC
     */
    @Query("""
        WITH recent_performance AS (
            SELECT 
                e.exercise_library_id,
                el.name as exercise_name,
                SUM(es.weight_kg * es.reps) as total_volume,
                COUNT(DISTINCT w.date) as workout_days,
                COUNT(es.id) as total_sets,
                MAX(es.weight_kg * (1.0 + CAST(es.reps AS REAL) / 30.0)) as max_estimated_1rm
            FROM exercise_sets es
            JOIN exercises e ON es.exercise_id = e.id
            JOIN workouts w ON e.workout_id = w.id
            JOIN exercise_library el ON e.exercise_library_id = el.id
            WHERE w.user_id = :userId
            AND el.equipment != 'BODYWEIGHT_ONLY'
            AND es.weight_kg > 0
            AND es.reps BETWEEN 1 AND 10
            AND (
                (es.completed_at IS NOT NULL AND DATE(es.completed_at / 1000, 'unixepoch') BETWEEN :startDate AND :endDate)
                OR 
                (es.completed_at IS NULL AND DATE(w.date) BETWEEN :startDate AND :endDate)
            )
            GROUP BY e.exercise_library_id, el.name
        )
        SELECT 
            *,
            (total_volume * 0.4 + workout_days * 10.0 + total_sets * 2.0 + max_estimated_1rm * 0.3) as performance_score
        FROM recent_performance
        WHERE total_sets >= 3
        ORDER BY performance_score DESC
        LIMIT :limit
    """)
    @UserScoped
    suspend fun getExerciseRankings(
        userId: String,
        startDate: String,
        endDate: String,
        limit: Int = 100
    ): List<ExerciseRankingResult>
}

/**
 * Data class for 1RM calculation results
 */
data class OneRmResult(
    val exercise_library_id: String,
    val exercise_name: String,
    val weight_kg: Float,
    val reps: Int,
    val completed_at: Long,
    val estimated_one_rm: Double
)

data class StrengthForecastSetSampleResult(
    val exercise_library_id: String,
    val exercise_name: String,
    val activity_date: String,
    val weight_kg: Float,
    val reps: Int,
    val completed_at: Long
)

/**
 * Data class for exercise volume aggregation results
 */
data class ExerciseVolumeResult(
    val exercise_library_id: String,
    val exercise_name: String,
    val total_volume: Double,
    val total_sets: Int,
    val avg_weight: Double,
    val max_weight: Double
)

/**
 * Data class for muscle group volume aggregation results
 */
data class MuscleGroupVolumeResult(
    val primary_muscle_group: String,
    val total_volume: Double,
    val exercise_count: Int,
    val total_sets: Int
)

/**
 * Data class for daily volume aggregation results
 */
data class DailyVolumeResult(
    val date: String,
    val total_volume: Double,
    val total_sets: Int,
    val exercise_count: Int
)

/**
 * Data class for rep-based activity when no external load is logged.
 */
data class DailyRepActivityResult(
    val date: String,
    val total_reps: Int,
    val total_sets: Int,
    val exercise_count: Int
)

data class WorkoutSetActivityResult(
    val workout_id: String,
    val total_volume: Double,
    val total_reps: Int,
    val total_sets: Int
)

/**
 * Data class for muscle group activity when no external load is logged.
 */
data class MuscleGroupRepActivityResult(
    val primary_muscle_group: String,
    val total_reps: Int,
    val exercise_count: Int,
    val total_sets: Int
)

/**
 * Data class for daily exercise volume aggregation results
 */
data class DailyExerciseVolumeResult(
    val date: String,
    val exercise_library_id: String,
    val exercise_name: String,
    val total_volume: Double,
    val total_sets: Int
)

/**
 * Data class for daily muscle group volume aggregation results
 */
data class DailyMuscleGroupVolumeResult(
    val date: String,
    val primary_muscle_group: String,
    val total_volume: Double,
    val exercise_count: Int,
    val total_sets: Int
)

/**
 * Data class for exercise ranking results with performance metrics
 */
data class ExerciseRankingResult(
    val exercise_library_id: String,
    val exercise_name: String,
    val total_volume: Double,
    val workout_days: Int,
    val total_sets: Int,
    val max_estimated_1rm: Double,
    val performance_score: Double
)

/**
 * Debug data class for set timestamp analysis
 */
data class DebugSetResult(
    val id: Long,
    val completed_at: Long?,
    val weight_kg: Float?,
    val reps: Int?,
    val workout_date: String,
    val workout_status: String,
    val converted_date: String?
)

/**
 * Data class for normalized exercise performance analytics.
 */
data class ExercisePerformanceResult(
    val exercise_library_id: String,
    val exercise_name: String,
    val primary_muscle_group: String,
    val total_volume: Double,
    val total_sets: Int,
    val workout_days: Int,
    val max_estimated_one_rm: Double,
    val performance_score: Double
)

/**
 * Data class for normalized exercise performance history.
 */
data class ExercisePerformanceHistoryResult(
    val date: String,
    val exercise_library_id: String,
    val exercise_name: String,
    val total_volume: Double,
    val total_sets: Int,
    val max_weight: Double,
    val max_reps: Int,
    val max_estimated_one_rm: Double
)
