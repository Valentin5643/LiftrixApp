package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.ExerciseHistoryEntity
import java.time.Instant

/**
 * DAO for exercise history data operations
 */
@Dao
interface ExerciseHistoryDao {

    /**
     * Gets exercise history for a specific user and exercise
     */
    @Query("SELECT * FROM exercise_history WHERE user_id = :userId AND exercise_id = :exerciseId LIMIT 1")
    suspend fun getExerciseHistory(userId: String, exerciseId: String): ExerciseHistoryEntity?

    /**
     * Inserts or updates exercise history
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(history: ExerciseHistoryEntity)

    /**
     * Updates existing exercise history
     */
    @Update
    suspend fun update(history: ExerciseHistoryEntity)

    /**
     * Gets all exercise histories for a user
     */
    @Query("SELECT * FROM exercise_history WHERE user_id = :userId ORDER BY last_performed DESC")
    suspend fun getAllUserExerciseHistories(userId: String): List<ExerciseHistoryEntity>

    /**
     * Gets recently performed exercises for a user
     */
    @Query("""
        SELECT * FROM exercise_history 
        WHERE user_id = :userId AND last_performed IS NOT NULL 
        ORDER BY last_performed DESC 
        LIMIT :limit
    """)
    suspend fun getRecentlyPerformed(userId: String, limit: Int = 20): List<ExerciseHistoryEntity>

    /**
     * Gets exercise histories that haven't been updated recently
     */
    @Query("""
        SELECT * FROM exercise_history 
        WHERE last_performed IS NULL OR last_performed < :cutoffTime
    """)
    suspend fun getStaleHistories(cutoffTime: Instant): List<ExerciseHistoryEntity>

    /**
     * Deletes exercise history for a specific user and exercise
     */
    @Query("DELETE FROM exercise_history WHERE user_id = :userId AND exercise_id = :exerciseId")
    suspend fun deleteExerciseHistory(userId: String, exerciseId: String)

    /**
     * Deletes all exercise history for a user
     */
    @Query("DELETE FROM exercise_history WHERE user_id = :userId")
    suspend fun deleteAllUserHistory(userId: String)

    /**
     * Gets count of exercises with history for a user
     */
    @Query("SELECT COUNT(*) FROM exercise_history WHERE user_id = :userId")
    suspend fun getUserExerciseHistoryCount(userId: String): Int

    /**
     * Gets exercises with most historical data
     */
    @Query("""
        SELECT * FROM exercise_history 
        WHERE user_id = :userId 
        ORDER BY (length(recent_weights) + length(recent_reps) + length(recent_durations)) DESC 
        LIMIT :limit
    """)
    suspend fun getMostTrackedExercises(userId: String, limit: Int = 10): List<ExerciseHistoryEntity>

    /**
     * Cleans up old exercise histories with no recent activity
     */
    @Query("""
        DELETE FROM exercise_history 
        WHERE last_performed IS NOT NULL AND last_performed < :cutoffTime
    """)
    suspend fun cleanupOldHistories(cutoffTime: Instant): Int
}