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
    
    @Query("SELECT * FROM workouts WHERE user_id = :userId ORDER BY date DESC, created_at DESC")
    fun getAllWorkoutsForUser(userId: String): Flow<List<WorkoutEntity>>
    
    @Query("SELECT * FROM workouts WHERE user_id = :userId AND status = 'COMPLETED' ORDER BY date DESC, created_at DESC LIMIT :limit")
    fun getRecentCompletedWorkouts(userId: String, limit: Int): Flow<List<WorkoutEntity>>
    
    @Query("SELECT * FROM workouts WHERE id = :id AND user_id = :userId")
    suspend fun getWorkoutByIdForUser(id: String, userId: String): WorkoutEntity?
    
    @Query("SELECT * FROM workouts WHERE date = :date AND user_id = :userId ORDER BY created_at DESC")
    fun getWorkoutsByDateForUser(date: String, userId: String): Flow<List<WorkoutEntity>>
    
    @Query("SELECT * FROM workouts WHERE is_synced = 0 AND user_id = :userId ORDER BY updated_at ASC")
    suspend fun getUnsyncedWorkoutsForUser(userId: String): List<WorkoutEntity>
    
    @Query("SELECT COUNT(*) FROM workouts WHERE is_synced = 0 AND user_id = :userId")
    suspend fun getUnsyncedCountForUser(userId: String): Int
    
    @Query("SELECT * FROM workouts WHERE status = 'IN_PROGRESS' AND user_id = :userId ORDER BY updated_at DESC LIMIT 1")
    suspend fun getActiveWorkoutForUser(userId: String): WorkoutEntity?
    
    @Query("SELECT * FROM workouts WHERE template_id = :templateId AND user_id = :userId ORDER BY created_at DESC")
    fun getWorkoutsFromTemplateForUser(templateId: String, userId: String): Flow<List<WorkoutEntity>>
    
    @Query("SELECT * FROM workouts WHERE user_id = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getWorkoutsInDateRangeForUser(userId: String, startDate: String, endDate: String): List<WorkoutEntity>
    
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
     * 
     * @param currentUserId The current user's ID
     * @param friendIds List of friend user IDs to include in the feed
     * @param limit Maximum number of workouts to return
     * @param offset Number of workouts to skip for pagination
     * @return Flow of workout entities ordered by completion time (newest first)
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
     * Gets personal completed workouts only for the current user
     * 
     * @param userId The user's ID
     * @param limit Maximum number of workouts to return
     * @param offset Number of workouts to skip for pagination
     * @return Flow of personal workout entities ordered by completion time (newest first)
     */
    @Query("""
        SELECT * FROM workouts 
        WHERE user_id = :userId 
        AND status = 'COMPLETED' 
        AND end_time IS NOT NULL
        ORDER BY end_time DESC 
        LIMIT :limit OFFSET :offset
    """)
    fun getPersonalCompletedWorkouts(
        userId: String,
        limit: Int,
        offset: Int
    ): Flow<List<WorkoutEntity>>
    
    /**
     * Gets friends' completed workouts only
     * 
     * @param friendIds List of friend user IDs
     * @param limit Maximum number of workouts to return
     * @param offset Number of workouts to skip for pagination
     * @return Flow of friends' workout entities ordered by completion time (newest first)
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
     * 
     * @param userId The user's ID
     * @return List of friend user IDs with ACCEPTED status
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
     * 
     * @param currentUserId The current user's ID
     * @param friendIds List of friend user IDs to include in the feed
     * @return Total count of completed workouts in the feed
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
} 