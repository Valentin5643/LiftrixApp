package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.WorkoutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    
    @Query("""
        SELECT * FROM workouts 
        WHERE user_id = :userId 
        ORDER BY 
            CASE WHEN updated_at > 0 THEN updated_at ELSE created_at END DESC,
            date DESC,
            created_at DESC
    """)
    fun getAllWorkoutsForUser(userId: String): Flow<List<WorkoutEntity>>
    
    @Query("""
        SELECT * FROM workouts 
        WHERE user_id = :userId 
        ORDER BY 
            CASE WHEN updated_at > 0 THEN updated_at ELSE created_at END DESC,
            date DESC,
            created_at DESC 
        LIMIT :limit
    """)
    fun getRecentCompletedWorkouts(userId: String, limit: Int): Flow<List<WorkoutEntity>>
    
    @Query("SELECT * FROM workouts WHERE id = :id AND user_id = :userId")
    suspend fun getWorkoutByIdForUser(id: String, userId: String): WorkoutEntity?
    
    @Query("SELECT * FROM workouts WHERE date = :date AND user_id = :userId ORDER BY created_at DESC")
    fun getWorkoutsByDateForUser(date: String, userId: String): Flow<List<WorkoutEntity>>
    
    @Query("SELECT * FROM workouts WHERE is_synced = 0 AND user_id = :userId ORDER BY updated_at ASC")
    suspend fun getUnsyncedWorkoutsForUser(userId: String): List<WorkoutEntity>
    
    @Query("SELECT COUNT(*) FROM workouts WHERE is_synced = 0 AND user_id = :userId")
    suspend fun getUnsyncedCountForUser(userId: String): Int
    
    @Query("SELECT COUNT(*) FROM workouts WHERE is_synced = 1 AND user_id = :userId")
    suspend fun getSyncedCountForUser(userId: String): Int
    
    @Query("UPDATE workouts SET is_synced = 0, sync_version = :version WHERE user_id = :userId")
    suspend fun markAllWorkoutsAsUnsyncedForUser(userId: String, version: Long = System.currentTimeMillis()): Int
    
    @Query("SELECT * FROM workouts WHERE status = 'IN_PROGRESS' AND user_id = :userId ORDER BY updated_at DESC LIMIT 1")
    suspend fun getActiveWorkoutForUser(userId: String): WorkoutEntity?
    
    @Query("SELECT * FROM workouts WHERE template_id = :templateId AND user_id = :userId ORDER BY created_at DESC LIMIT :limit")
    fun getWorkoutsFromTemplateForUser(templateId: String, userId: String, limit: Int = 1000): Flow<List<WorkoutEntity>>
    
    @Query("SELECT * FROM workouts WHERE user_id = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date ASC LIMIT :limit")
    suspend fun getWorkoutsInDateRangeForUser(userId: String, startDate: String, endDate: String, limit: Int = 10000): List<WorkoutEntity>
    
    /**
     * Gets workouts for a user within a specific date range
     * 
     * This method is used by CalorieAnalyticsUseCase for calorie calculations
     * and analytics data processing. Returns workouts ordered by date.
     * 
     * @param userId The user's ID
     * @param startDate Start date in string format (inclusive)
     * @param endDate End date in string format (inclusive)
     * @return List of workout entities in the date range
     */
    @Query("SELECT * FROM workouts WHERE user_id = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date ASC LIMIT :limit")
    suspend fun getWorkoutsByDateRange(userId: String, startDate: String, endDate: String, limit: Int = 10000): List<WorkoutEntity>
    
    @Query("SELECT w.* FROM workouts w JOIN exercises e ON w.id = e.workout_id WHERE e.id = :exerciseId")
    suspend fun getWorkoutByExerciseId(exerciseId: Long): WorkoutEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: WorkoutEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkouts(workouts: List<WorkoutEntity>): List<Long>
    
    @Update
    suspend fun updateWorkout(workout: WorkoutEntity): Int
    
    @Query("UPDATE workouts SET is_synced = :isSynced, sync_version = :version WHERE id = :id AND user_id = :userId")
    suspend fun updateSyncStatusForUser(id: String, userId: String, isSynced: Boolean, version: Long): Int
    
    @Query("UPDATE workouts SET is_synced = 1, sync_version = :version WHERE id IN (:ids) AND user_id = :userId")
    suspend fun markWorkoutsAsSyncedForUser(ids: List<String>, userId: String, version: Long): Int
    
    @Delete
    suspend fun deleteWorkout(workout: WorkoutEntity): Int
    
    @Query("DELETE FROM workouts WHERE id = :id AND user_id = :userId")
    suspend fun deleteWorkoutByIdForUser(id: String, userId: String): Int
    
    @Query("DELETE FROM workouts WHERE user_id = :userId")
    suspend fun deleteAllWorkoutsForUser(userId: String): Int
    
    @Query("SELECT EXISTS(SELECT 1 FROM workouts WHERE id = :workoutId AND user_id = :userId)")
    suspend fun workoutExistsByIdAndUser(workoutId: String, userId: String): Boolean
    
    // Export support methods
    @Query("SELECT * FROM workouts WHERE user_id = :userId ORDER BY date DESC")
    suspend fun getAllWorkoutsForUserSync(userId: String): List<WorkoutEntity>
    
    @Query("SELECT * FROM workouts WHERE user_id = :userId AND date >= :startDate AND date <= :endDate ORDER BY date DESC")  
    suspend fun getWorkoutsInDateRangeForExport(userId: String, startDate: String, endDate: String): List<WorkoutEntity>
    
    // Legacy methods without user filtering - deprecated for migration
    @Deprecated("Use user-scoped methods instead")
    @Query("SELECT * FROM workouts ORDER BY date DESC, created_at DESC")
    fun getAllWorkouts(): Flow<List<WorkoutEntity>>
    
    @Deprecated("Use user-scoped methods instead")
    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkoutById(id: String): WorkoutEntity?
    
    @Deprecated("Use user-scoped methods instead")
    @Query("SELECT * FROM workouts WHERE date = :date ORDER BY created_at DESC")
    fun getWorkoutsByDate(date: String): Flow<List<WorkoutEntity>>
    
    @Deprecated("Use user-scoped methods instead")
    @Query("SELECT COUNT(*) FROM workouts WHERE is_synced = 0")
    suspend fun getUnsyncedCount(): Int
    
    @Deprecated("Use user-scoped methods instead")
    @Query("SELECT * FROM workouts WHERE is_synced = 0 ORDER BY updated_at ASC")
    suspend fun getUnsyncedWorkouts(): List<WorkoutEntity>
    
    @Deprecated("Use user-scoped methods instead")
    @Query("UPDATE workouts SET is_synced = 1, sync_version = :version WHERE id IN (:ids)")
    suspend fun markWorkoutsAsSynced(ids: List<String>, version: Long): Int
    
    @Deprecated("Use user-scoped methods instead")
    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkoutById(id: String): Int
    
    @Deprecated("Use user-scoped methods instead")
    @Query("DELETE FROM workouts")
    suspend fun deleteAllWorkouts(): Int
    
    @Deprecated("Use user-scoped updateSyncStatusForUser instead")
    @Query("UPDATE workouts SET is_synced = :isSynced, sync_version = :version WHERE id = :id")
    suspend fun updateSyncStatus(id: String, isSynced: Boolean, version: Long): Int
    
    // Feed-specific queries for social workout timeline
    
    /**
     * Gets combined feed of personal and friends' completed workouts in chronological order
     */
    @Query("""
        SELECT w.* FROM workouts w 
        WHERE (w.user_id = :currentUserId OR w.user_id IN (:friendIds))
        AND w.status = 'COMPLETED' 
        AND w.end_time IS NOT NULL
        ORDER BY w.end_time DESC 
        LIMIT :limit OFFSET :offset
    """)
    fun getFeedWorkouts(
        currentUserId: String,
        friendIds: List<String>,
        limit: Int,
        offset: Int
    ): Flow<List<WorkoutEntity>>
    
    /**
     * Gets personal workouts for the current user (all statuses)
     */
    @Query("""
        SELECT * FROM workouts 
        WHERE user_id = :userId 
        ORDER BY 
            CASE WHEN updated_at > 0 THEN updated_at ELSE created_at END DESC,
            date DESC,
            created_at DESC
        LIMIT :limit OFFSET :offset
    """)
    fun getPersonalCompletedWorkouts(
        userId: String,
        limit: Int,
        offset: Int
    ): Flow<List<WorkoutEntity>>
    
    /**
     * Gets friends' completed workouts only
     */
    @Query("""
        SELECT * FROM workouts 
        WHERE user_id IN (:friendIds)
        AND status = 'COMPLETED' 
        AND end_time IS NOT NULL
        ORDER BY end_time DESC 
        LIMIT :limit OFFSET :offset
    """)
    fun getFriendsCompletedWorkouts(
        friendIds: List<String>,
        limit: Int,
        offset: Int
    ): Flow<List<WorkoutEntity>>
    
    /**
     * Gets accepted friend user IDs for a given user
     * Used to retrieve friend IDs for feed queries
     */
    @Query("""
        SELECT f.friend_user_id FROM friends f 
        WHERE f.user_id = :userId 
        AND f.status = 'ACCEPTED'
        UNION
        SELECT f.user_id FROM friends f 
        WHERE f.friend_user_id = :userId 
        AND f.status = 'ACCEPTED'
    """)
    suspend fun getAcceptedFriendIds(userId: String): List<String>
    
    /**
     * Counts total available feed workouts for pagination logic
     */
    @Query("""
        SELECT COUNT(*) FROM workouts w 
        WHERE (w.user_id = :currentUserId OR w.user_id IN (:friendIds))
        AND w.status = 'COMPLETED' 
        AND w.end_time IS NOT NULL
    """)
    suspend fun getFeedWorkoutCount(
        currentUserId: String,
        friendIds: List<String>
    ): Int
    
    /**
     * Gets paginated workout history for a user ordered by effective timestamp (newest first)
     * Uses updatedAt when available, falls back to createdAt for workouts with updatedAt = 0
     */
    @Query("""
        SELECT * FROM workouts 
        WHERE user_id = :userId 
        ORDER BY 
            CASE WHEN updated_at > 0 THEN updated_at ELSE created_at END DESC,
            date DESC,
            created_at DESC
        LIMIT :limit OFFSET :offset
    """)
    fun getWorkoutHistoryPaginated(
        userId: String,
        limit: Int,
        offset: Int
    ): Flow<List<WorkoutEntity>>
    
    /**
     * Counts total workouts for a user for pagination logic
     */
    @Query("SELECT COUNT(*) FROM workouts WHERE user_id = :userId")
    suspend fun getWorkoutCountForUser(userId: String): Int
    
    // Analytics-specific queries for performance optimization
    
    /**
     * Gets daily volume aggregation for analytics calendar view
     * Optimized for monthly calendar widget with volume-based color coding
     */
    @Query("""
        SELECT 
            date,
            SUM(
                CASE 
                    WHEN exercises_json IS NOT NULL AND exercises_json != '' 
                    THEN json_extract(exercises_json, '$.totalVolume') 
                    ELSE 0 
                END
            ) as total_volume,
            SUM(
                CASE 
                    WHEN exercises_json IS NOT NULL AND exercises_json != '' 
                    THEN json_extract(exercises_json, '$.totalSets') 
                    ELSE 0 
                END
            ) as total_sets,
            SUM(
                CASE 
                    WHEN exercises_json IS NOT NULL AND exercises_json != '' 
                    THEN json_extract(exercises_json, '$.exerciseCount') 
                    ELSE 0 
                END
            ) as exercise_count
        FROM workouts 
        WHERE user_id = :userId 
        AND status = 'COMPLETED' 
        AND date BETWEEN :startDate AND :endDate 
        GROUP BY date
        ORDER BY date
    """)
    suspend fun getDailyVolumesByDateRange(
        userId: String,
        startDate: String,
        endDate: String
    ): List<DailyVolumeResult>
    
    
    /**
     * Gets workout statistics for analytics dashboard
     * Calculates average duration, volume, and workout count for specified period
     */
    @Query("""
        SELECT 
            AVG(CASE WHEN end_time IS NOT NULL AND start_time IS NOT NULL 
                THEN (strftime('%s', end_time) - strftime('%s', start_time)) / 60 
                ELSE 0 END) as avgDurationMinutes,
            AVG(CASE 
                WHEN exercises_json IS NOT NULL AND exercises_json != '' 
                THEN json_extract(exercises_json, '$.totalVolume') 
                ELSE 0 
            END) as avgVolume,
            COUNT(*) as workoutCount
        FROM workouts 
        WHERE user_id = :userId 
        AND status = 'COMPLETED'
        AND date >= :since
    """)
    suspend fun getWorkoutStats(userId: String, since: String): WorkoutStatsResult
    
    /**
     * Gets workout count by month for frequency analysis
     * Used for workout frequency patterns and consistency tracking
     */
    @Query("""
        SELECT 
            strftime('%Y-%m', date) as month,
            COUNT(*) as workoutCount
        FROM workouts 
        WHERE user_id = :userId 
        AND status = 'COMPLETED'
        AND strftime('%Y', date) = :year
        GROUP BY strftime('%Y-%m', date)
        ORDER BY month
    """)
    suspend fun getMonthlyWorkoutCounts(userId: String, year: String): List<MonthlyWorkoutCount>
    
    /**
     * Gets maximum volume for a user in specified date range
     * Used for volume calendar intensity calculations
     */
    @Query("""
        SELECT MAX(
            CASE 
                WHEN exercises_json IS NOT NULL AND exercises_json != '' 
                THEN json_extract(exercises_json, '$.totalVolume') 
                ELSE 0 
            END
        ) as maxVolume
        FROM workouts 
        WHERE user_id = :userId 
        AND status = 'COMPLETED'
        AND date BETWEEN :startDate AND :endDate
    """)
    suspend fun getMaxVolumeInDateRange(
        userId: String,
        startDate: String,
        endDate: String
    ): Double?
}

// DailyVolumeResult is defined in ExerciseSetDao.kt - no duplicate needed

/**
 * Data class for workout statistics results
 */
data class WorkoutStatsResult(
    val avgDurationMinutes: Double,
    val avgVolume: Double,
    val workoutCount: Int
)

/**
 * Data class for monthly workout count results
 */
data class MonthlyWorkoutCount(
    val month: String,
    val workoutCount: Int
) 