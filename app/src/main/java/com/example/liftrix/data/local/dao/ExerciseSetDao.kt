package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.liftrix.data.local.entity.ExerciseSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseSetDao {
    
    @Query("SELECT * FROM exercise_sets WHERE exercise_id = :exerciseId ORDER BY set_number ASC")
    suspend fun getSetsByExercise(exerciseId: Long): List<ExerciseSetEntity>
    
    @Query("SELECT * FROM exercise_sets WHERE exercise_id = :exerciseId ORDER BY set_number ASC")
    fun getSetsByExerciseFlow(exerciseId: Long): Flow<List<ExerciseSetEntity>>
    
    @Query("SELECT * FROM exercise_sets WHERE id = :setId")
    suspend fun getSetById(setId: Long): ExerciseSetEntity?
    
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
    suspend fun getExerciseHistory(userId: String, exerciseLibraryId: String, limit: Int): List<ExerciseSetEntity>
    
    @Query("SELECT * FROM exercise_sets WHERE completed_at IS NOT NULL ORDER BY completed_at DESC LIMIT :limit")
    suspend fun getRecentCompletedSets(limit: Int): List<ExerciseSetEntity>
    
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
    
    @Query("DELETE FROM exercise_sets WHERE id = :setId")
    suspend fun deleteSetById(setId: Long): Int
    
    @Query("DELETE FROM exercise_sets WHERE exercise_id = :exerciseId")
    suspend fun deleteSetsForExercise(exerciseId: Long): Int
    
    @Query("DELETE FROM exercise_sets WHERE exercise_id IN (SELECT id FROM exercises WHERE workout_id = :workoutId)")
    suspend fun deleteSetsForWorkout(workoutId: String): Int
    
    @Query("SELECT COUNT(*) FROM exercise_sets WHERE exercise_id = :exerciseId")
    suspend fun getSetCountForExercise(exerciseId: Long): Int
    
    @Query("SELECT COUNT(*) FROM exercise_sets WHERE exercise_id = :exerciseId AND completed_at IS NOT NULL")
    suspend fun getCompletedSetCountForExercise(exerciseId: Long): Int
    
    @Query("UPDATE exercise_sets SET completed_at = :completedAt WHERE id = :setId")
    suspend fun markSetCompleted(setId: Long, completedAt: Long): Int
    
    @Query("UPDATE exercise_sets SET completed_at = NULL WHERE id = :setId")
    suspend fun markSetIncomplete(setId: Long): Int
    
    @Transaction
    suspend fun replaceAllSetsForExercise(exerciseId: Long, newSets: List<ExerciseSetEntity>) {
        deleteSetsForExercise(exerciseId)
        insertSets(newSets.map { it.copy(exerciseId = exerciseId) })
    }
    
    @Query("SELECT MAX(set_number) FROM exercise_sets WHERE exercise_id = :exerciseId")
    suspend fun getMaxSetNumber(exerciseId: Long): Int?
    
    @Query("""
        SELECT AVG(weight_kg) FROM exercise_sets 
        WHERE exercise_id IN (
            SELECT id FROM exercises WHERE exercise_library_id = :exerciseLibraryId
        ) 
        AND weight_kg IS NOT NULL 
        AND completed_at IS NOT NULL
    """)
    suspend fun getAverageWeightForExercise(exerciseLibraryId: String): Float?
    
    @Query("""
        SELECT MAX(weight_kg) FROM exercise_sets 
        WHERE exercise_id IN (
            SELECT id FROM exercises WHERE exercise_library_id = :exerciseLibraryId
        ) 
        AND weight_kg IS NOT NULL 
        AND completed_at IS NOT NULL
    """)
    suspend fun getMaxWeightForExercise(exerciseLibraryId: String): Float?
    
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
            e.exercise_library_id,
            es.weight_kg,
            es.reps,
            es.completed_at,
            es.weight_kg * (1.0 + CAST(es.reps AS REAL) / 30.0) as estimated_one_rm
        FROM exercise_sets es
        JOIN exercises e ON es.exercise_id = e.id
        JOIN workouts w ON e.workout_id = w.id
        WHERE w.user_id = :userId
        AND e.exercise_library_id IN (:exerciseLibraryIds)
        AND es.weight_kg > 0
        AND es.reps > 0
        AND (
            (es.completed_at IS NOT NULL AND DATE(es.completed_at / 1000, 'unixepoch') BETWEEN :startDate AND :endDate)
            OR 
            (es.completed_at IS NULL AND DATE(w.date) BETWEEN :startDate AND :endDate)
        )
        ORDER BY es.completed_at DESC, es.weight_kg DESC
        LIMIT :limit
    """)
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
            e.exercise_library_id,
            el.name as exercise_name,
            SUM(es.weight_kg * es.reps) as total_volume,
            COUNT(es.id) as total_sets,
            AVG(es.weight_kg) as avg_weight,
            MAX(es.weight_kg) as max_weight
        FROM exercise_sets es
        JOIN exercises e ON es.exercise_id = e.id
        JOIN workouts w ON e.workout_id = w.id
        JOIN exercise_library el ON e.exercise_library_id = el.id
        WHERE w.user_id = :userId
        AND es.weight_kg > 0
        AND es.reps > 0
        AND (
            (es.completed_at IS NOT NULL AND DATE(es.completed_at / 1000, 'unixepoch') BETWEEN :startDate AND :endDate)
            OR 
            (es.completed_at IS NULL AND DATE(w.date) BETWEEN :startDate AND :endDate)
        )
        GROUP BY e.exercise_library_id, el.name
        ORDER BY total_volume DESC
        LIMIT :limit
    """)
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
            el.primary_muscle_group,
            SUM(es.weight_kg * es.reps) as total_volume,
            COUNT(DISTINCT e.exercise_library_id) as exercise_count,
            COUNT(es.id) as total_sets
        FROM exercise_sets es
        JOIN exercises e ON es.exercise_id = e.id
        JOIN workouts w ON e.workout_id = w.id
        JOIN exercise_library el ON e.exercise_library_id = el.id
        WHERE w.user_id = :userId
        AND es.weight_kg > 0
        AND es.reps > 0
        AND (
            (es.completed_at IS NOT NULL AND DATE(es.completed_at / 1000, 'unixepoch') BETWEEN :startDate AND :endDate)
            OR 
            (es.completed_at IS NULL AND DATE(w.date) BETWEEN :startDate AND :endDate)
        )
        GROUP BY el.primary_muscle_group
        ORDER BY total_volume DESC
        LIMIT :limit
    """)
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
            e.exercise_library_id,
            es.weight_kg,
            es.reps,
            es.completed_at,
            es.weight_kg * (1.0 + CAST(es.reps AS REAL) / 30.0) as estimated_one_rm
        FROM exercise_sets es
        JOIN exercises e ON es.exercise_id = e.id
        JOIN workouts w ON e.workout_id = w.id
        WHERE w.user_id = :userId
        AND es.weight_kg > 0
        AND es.reps > 0
        AND (
            (es.completed_at IS NOT NULL AND DATE(es.completed_at / 1000, 'unixepoch') BETWEEN :startDate AND :endDate)
            OR 
            (es.completed_at IS NULL AND DATE(w.date) BETWEEN :startDate AND :endDate)
        )
        ORDER BY es.completed_at DESC, es.weight_kg DESC
        LIMIT 5000
    """)
    suspend fun getAllOneRmData(
        userId: String,
        startDate: String,
        endDate: String
    ): List<OneRmResult>
    
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
            DATE(es.completed_at / 1000, 'unixepoch') as date,
            e.exercise_library_id,
            el.name as exercise_name,
            SUM(es.weight_kg * es.reps) as total_volume,
            COUNT(es.id) as total_sets
        FROM exercise_sets es
        JOIN exercises e ON es.exercise_id = e.id
        JOIN workouts w ON e.workout_id = w.id
        JOIN exercise_library el ON e.exercise_library_id = el.id
        WHERE w.user_id = :userId
        AND es.weight_kg > 0
        AND es.reps > 0
        AND (
            (es.completed_at IS NOT NULL AND DATE(es.completed_at / 1000, 'unixepoch') BETWEEN :startDate AND :endDate)
            OR 
            (es.completed_at IS NULL AND DATE(w.date) BETWEEN :startDate AND :endDate)
        )
        GROUP BY DATE(es.completed_at / 1000, 'unixepoch'), e.exercise_library_id, el.name
        ORDER BY date ASC, el.name ASC
    """)
    suspend fun getDailyVolumeDataByExercise(
        userId: String,
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
            DATE(es.completed_at / 1000, 'unixepoch') as date,
            el.primary_muscle_group,
            SUM(es.weight_kg * es.reps) as total_volume,
            COUNT(DISTINCT e.exercise_library_id) as exercise_count,
            COUNT(es.id) as total_sets
        FROM exercise_sets es
        JOIN exercises e ON es.exercise_id = e.id
        JOIN workouts w ON e.workout_id = w.id
        JOIN exercise_library el ON e.exercise_library_id = el.id
        WHERE w.user_id = :userId
        AND es.weight_kg > 0
        AND es.reps > 0
        AND (
            (es.completed_at IS NOT NULL AND DATE(es.completed_at / 1000, 'unixepoch') BETWEEN :startDate AND :endDate)
            OR 
            (es.completed_at IS NULL AND DATE(w.date) BETWEEN :startDate AND :endDate)
        )
        GROUP BY DATE(es.completed_at / 1000, 'unixepoch'), el.primary_muscle_group
        ORDER BY date ASC, el.primary_muscle_group ASC
    """)
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
            DATE(es.completed_at / 1000, 'unixepoch') as date,
            SUM(es.weight_kg * es.reps) as total_volume,
            COUNT(es.id) as total_sets,
            COUNT(DISTINCT e.exercise_library_id) as exercise_count
        FROM exercise_sets es
        JOIN exercises e ON es.exercise_id = e.id
        JOIN workouts w ON e.workout_id = w.id
        WHERE w.user_id = :userId
        AND es.weight_kg > 0
        AND es.reps > 0
        AND (
            (es.completed_at IS NOT NULL AND DATE(es.completed_at / 1000, 'unixepoch') BETWEEN :startDate AND :endDate)
            OR 
            (es.completed_at IS NULL AND DATE(w.date) BETWEEN :startDate AND :endDate)
        )
        GROUP BY DATE(es.completed_at / 1000, 'unixepoch')
        ORDER BY date ASC
    """)
    suspend fun getDailyVolumeData(
        userId: String,
        startDate: String,
        endDate: String
    ): List<DailyVolumeResult>
    
    @Query("SELECT COUNT(*) FROM exercise_sets es JOIN exercises e ON es.exercise_id = e.id JOIN workouts w ON e.workout_id = w.id WHERE w.user_id = :userId AND es.completed_at IS NOT NULL")
    suspend fun getUserSetCount(userId: String): Int
    
    @Query("SELECT COUNT(*) FROM workouts WHERE user_id = :userId")
    suspend fun getUserWorkoutCount(userId: String): Int
    
    // Debug queries to diagnose the volume data issue
    @Query("SELECT COUNT(*) FROM exercise_sets es JOIN exercises e ON es.exercise_id = e.id JOIN workouts w ON e.workout_id = w.id WHERE w.user_id = :userId")
    suspend fun getAllUserSetCount(userId: String): Int
    
    @Query("SELECT COUNT(*) FROM exercise_sets es JOIN exercises e ON es.exercise_id = e.id JOIN workouts w ON e.workout_id = w.id WHERE w.user_id = :userId AND es.completed_at IS NULL")
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
    suspend fun getDebugSetSample(userId: String): List<DebugSetResult>
    
    @Query("SELECT COUNT(*) FROM exercise_sets es JOIN exercises e ON es.exercise_id = e.id JOIN workouts w ON e.workout_id = w.id WHERE w.user_id = :userId AND es.weight_kg > 0 AND es.reps > 0")
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
            AND es.weight_kg > 0
            AND es.reps > 0
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
    val weight_kg: Float,
    val reps: Int,
    val completed_at: Long,
    val estimated_one_rm: Double
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