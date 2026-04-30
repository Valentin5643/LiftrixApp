package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.annotations.UserScoped
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.time.Instant

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
    @UserScoped
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
    @UserScoped
    fun getRecentCompletedWorkouts(userId: String, limit: Int): Flow<List<WorkoutEntity>>
    
    @Query("SELECT * FROM workouts WHERE id = :id AND user_id = :userId")
    @UserScoped
    suspend fun getWorkoutByIdForUser(id: String, userId: String): WorkoutEntity?
    
    @Query("SELECT * FROM workouts WHERE date = :date AND user_id = :userId ORDER BY created_at DESC")
    @UserScoped
    fun getWorkoutsByDateForUser(date: String, userId: String): Flow<List<WorkoutEntity>>
    
    @Query("SELECT * FROM workouts WHERE is_synced = 0 AND user_id = :userId ORDER BY updated_at ASC")
    @UserScoped
    suspend fun getUnsyncedWorkoutsForUser(userId: String): List<WorkoutEntity>
    
    @Query("SELECT COUNT(*) FROM workouts WHERE is_synced = 0 AND user_id = :userId")
    @UserScoped
    suspend fun getUnsyncedCountForUser(userId: String): Int
    
    @Query("SELECT COUNT(*) FROM workouts WHERE is_synced = 1 AND user_id = :userId")
    @UserScoped
    suspend fun getSyncedCountForUser(userId: String): Int
    
    @Query("""
        UPDATE workouts
        SET is_synced = 0,
            sync_version = :version
        WHERE user_id = :userId
    """)
    @UserScoped
    suspend fun markAllWorkoutsAsUnsyncedForUser(userId: String, version: Long = System.currentTimeMillis()): Int
    
    @Query("SELECT * FROM workouts WHERE status = 'IN_PROGRESS' AND user_id = :userId ORDER BY updated_at DESC LIMIT 1")
    @UserScoped
    suspend fun getActiveWorkoutForUser(userId: String): WorkoutEntity?
    
    @Query("SELECT * FROM workouts WHERE template_id = :templateId AND user_id = :userId ORDER BY created_at DESC LIMIT :limit")
    @UserScoped
    fun getWorkoutsFromTemplateForUser(templateId: String, userId: String, limit: Int = 1000): Flow<List<WorkoutEntity>>
    
    @Query("SELECT * FROM workouts WHERE user_id = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date ASC LIMIT :limit")
    @UserScoped
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
    @UserScoped
    suspend fun getWorkoutsByDateRange(userId: String, startDate: String, endDate: String, limit: Int = 10000): List<WorkoutEntity>
    
    @Query("""
        SELECT w.* FROM workouts w
        JOIN exercises e ON w.id = e.workout_id
        WHERE e.id = :exerciseId
        AND w.user_id = :userId
    """)
    @UserScoped
    suspend fun getWorkoutByExerciseId(exerciseId: Long, userId: String): WorkoutEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: WorkoutEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkouts(workouts: List<WorkoutEntity>): List<Long>
    
    @Update
    suspend fun updateWorkout(workout: WorkoutEntity): Int
    
    @Query("""
        UPDATE workouts
        SET is_synced = :isSynced,
            sync_version = :version
        WHERE id = :id
        AND user_id = :userId
    """)
    @UserScoped
    suspend fun updateSyncStatusForUser(id: String, userId: String, isSynced: Boolean, version: Long): Int
    
    @Query("""
        UPDATE workouts
        SET is_synced = 1,
            sync_version = :version
        WHERE id IN (:ids)
        AND user_id = :userId
    """)
    @UserScoped
    suspend fun markWorkoutsAsSyncedForUser(ids: List<String>, userId: String, version: Long): Int
    
    @Delete
    suspend fun deleteWorkout(workout: WorkoutEntity): Int
    
    @Query("DELETE FROM workouts WHERE id = :id AND user_id = :userId")
    @UserScoped
    suspend fun deleteWorkoutByIdForUser(id: String, userId: String): Int
    
    @Query("DELETE FROM workouts WHERE user_id = :userId")
    @UserScoped
    suspend fun deleteAllWorkoutsForUser(userId: String): Int
    
    @Query("SELECT EXISTS(SELECT 1 FROM workouts WHERE id = :workoutId AND user_id = :userId)")
    @UserScoped
    suspend fun workoutExistsByIdAndUser(workoutId: String, userId: String): Boolean
    
    // Export support methods
    @Query("SELECT * FROM workouts WHERE user_id = :userId ORDER BY date DESC")
    @UserScoped
    suspend fun getAllWorkoutsForUserSync(userId: String): List<WorkoutEntity>
    
    @Query("SELECT * FROM workouts WHERE user_id = :userId AND date >= :startDate AND date <= :endDate ORDER BY date DESC")
    @UserScoped
    suspend fun getWorkoutsInDateRangeForExport(userId: String, startDate: String, endDate: String): List<WorkoutEntity>

    // ========== OFFLINE-FIRST ARCHITECTURE METHODS (SPEC-20241228) ==========

    /**
     * Upsert workout from LOCAL origin (user edit).
     * Sets isDirty=true and lastModified, triggering sync queue.
     */
    suspend fun upsertLocal(workout: WorkoutEntity): Long {
        val beforeCount = getWorkoutCountForUser(workout.userId)
        val timestamp = System.currentTimeMillis()
        val entity = workout.copy(
            isDirty = true,
            lastModified = System.currentTimeMillis()
        )
        val result = _insert(entity)
        val afterCount = getWorkoutCountForUser(workout.userId)
        Timber.tag("WorkoutSyncDebug").d(
            "[DATABASE-DEBUG] operation=ROOM_UPSERT_LOCAL source=Room userId=${workout.userId} workoutId=${workout.id} timestamp=$timestamp beforeCount=$beforeCount afterCount=$afterCount result=$result isDirty=${entity.isDirty} isSynced=${entity.isSynced} lastModified=${entity.lastModified}"
        )
        return result
    }

    /**
     * Upsert workout from REMOTE origin (Firestore listener/sync).
     * Sets isDirty=false, only applies if remote is newer.
     * Does NOT trigger sync queue.
     */
    suspend fun upsertFromRemote(workout: WorkoutEntity) {
        val beforeCount = getWorkoutCountForUser(workout.userId)
        val timestamp = System.currentTimeMillis()
        val local = getWorkoutByIdForUser(workout.id, workout.userId)
        if (local == null || workout.lastModified > local.lastModified) {
            val entity = workout.copy(
                isDirty = false,
                isSynced = true,
                syncVersion = System.currentTimeMillis()
            )
            _insert(entity)
            val afterCount = getWorkoutCountForUser(workout.userId)
            Timber.tag("FreshLoginRestoreDebug").i(
                "operation=ROOM_UPSERT_FROM_FIREBASE_APPLIED userId=${workout.userId} workoutId=${workout.id} roomBeforeCount=$beforeCount roomAfterCount=$afterCount localExists=${local != null} localLastModified=${local?.lastModified ?: 0L} remoteLastModified=${workout.lastModified} timestamp=$timestamp"
            )
            Timber.tag("WorkoutSyncDebug").w(
                "[DATABASE-DEBUG] operation=REMOTE_OVERWRITE_ROOM source=Firebase userId=${workout.userId} workoutId=${workout.id} timestamp=$timestamp beforeCount=$beforeCount afterCount=$afterCount localExists=${local != null} localLastModified=${local?.lastModified ?: 0L} remoteLastModified=${workout.lastModified} localStatus=${local?.status} remoteStatus=${workout.status} localEndTimePresent=${local?.endTime != null} remoteEndTimePresent=${workout.endTime != null}"
            )
        } else {
            val afterCount = getWorkoutCountForUser(workout.userId)
            Timber.tag("FreshLoginRestoreDebug").d(
                "operation=ROOM_UPSERT_FROM_FIREBASE_SKIPPED userId=${workout.userId} workoutId=${workout.id} roomBeforeCount=$beforeCount roomAfterCount=$afterCount reason=local_newer_or_equal localLastModified=${local.lastModified} remoteLastModified=${workout.lastModified} timestamp=$timestamp"
            )
            Timber.tag("WorkoutSyncDebug").d(
                "[DATABASE-DEBUG] operation=REMOTE_UPSERT_SKIPPED source=Firebase userId=${workout.userId} workoutId=${workout.id} timestamp=$timestamp beforeCount=$beforeCount afterCount=$afterCount localLastModified=${local.lastModified} remoteLastModified=${workout.lastModified} localStatus=${local.status} remoteStatus=${workout.status}"
            )
        }
    }

    /**
     * Internal insert for shared logic.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun _insert(workout: WorkoutEntity): Long

    /**
     * Get dirty workouts that need upload to Firestore.
     */
    @Query("SELECT * FROM workouts WHERE user_id = :userId AND is_dirty = 1 ORDER BY last_modified ASC")
    @UserScoped
    suspend fun getDirtyWorkouts(userId: String): List<WorkoutEntity>

    /**
     * Mark all workouts as dirty to force a full upload (startup sync recovery).
     */
    @Query("""
        UPDATE workouts
        SET is_dirty = 1,
            is_synced = 0,
            last_modified = :lastModified
        WHERE user_id = :userId
    """)
    @UserScoped
    suspend fun markAllDirtyForUser(userId: String, lastModified: Long = System.currentTimeMillis()): Int

    /**
     * Mark workouts as clean after successful Firestore upload.
     */
    @Query("""
        UPDATE workouts
        SET is_dirty = 0,
            is_synced = 1,
            sync_version = :syncVersion
        WHERE id IN (:ids)
        AND user_id = :userId
    """)
    @UserScoped
    suspend fun markAsClean(ids: List<String>, userId: String, syncVersion: Long = System.currentTimeMillis()): Int

    /**
     * Mark workout as no longer dirty after a fatal sync validation failure.
     * This prevents repeated upload attempts while keeping isSynced=false.
     */
    @Query("""
        UPDATE workouts
        SET is_dirty = 0,
            is_synced = 0
        WHERE id = :id
        AND user_id = :userId
    """)
    @UserScoped
    suspend fun markSyncFailed(id: String, userId: String): Int

    @Query("""
        UPDATE workouts
        SET exercises_json = :exercisesJson,
            updated_at = :updatedAt,
            last_modified = :lastModified,
            is_dirty = 1,
            is_synced = 0
        WHERE id = :workoutId
        AND user_id = :userId
    """)
    @UserScoped
    suspend fun updateExercisesJsonForWorkout(
        workoutId: String,
        userId: String,
        exercisesJson: String,
        updatedAt: Instant,
        lastModified: Long
    ): Int

    // ========== END OFFLINE-FIRST METHODS ==========

    // Legacy methods without user filtering - deprecated for migration
    @Deprecated("Use user-scoped methods instead")
    @Query("SELECT * FROM workouts WHERE user_id = :userId ORDER BY date DESC, created_at DESC")
    @UserScoped
    fun getAllWorkouts(userId: String): Flow<List<WorkoutEntity>>
    
    @Deprecated("Use getWorkoutByIdForUser instead")
    @Query("SELECT * FROM workouts WHERE id = :id AND user_id = :userId")
    @UserScoped
    suspend fun getWorkoutById(id: String, userId: String): WorkoutEntity?
    
    @Deprecated("Use user-scoped methods instead")
    @Query("SELECT * FROM workouts WHERE date = :date AND user_id = :userId ORDER BY created_at DESC")
    @UserScoped
    fun getWorkoutsByDate(date: String, userId: String): Flow<List<WorkoutEntity>>
    
    @Deprecated("Use user-scoped methods instead")
    @Query("SELECT COUNT(*) FROM workouts WHERE is_synced = 0 AND user_id = :userId")
    @UserScoped
    suspend fun getUnsyncedCount(userId: String): Int
    
    @Deprecated("Use user-scoped methods instead")
    @Query("SELECT * FROM workouts WHERE is_synced = 0 AND user_id = :userId ORDER BY updated_at ASC")
    @UserScoped
    suspend fun getUnsyncedWorkouts(userId: String): List<WorkoutEntity>
    
    @Deprecated("Use user-scoped methods instead")
    @Query("""
        UPDATE workouts
        SET is_synced = 1,
            sync_version = :version
        WHERE id IN (:ids)
        AND user_id = :userId
    """)
    @UserScoped
    suspend fun markWorkoutsAsSynced(ids: List<String>, userId: String, version: Long): Int
    
    @Deprecated("Use deleteWorkoutByIdForUser instead")
    @Query("DELETE FROM workouts WHERE id = :id AND user_id = :userId")
    @UserScoped
    suspend fun deleteWorkoutById(id: String, userId: String): Int
    
    @Deprecated("Use deleteAllWorkoutsForUser instead")
    @Query("DELETE FROM workouts WHERE user_id = :userId")
    @UserScoped
    suspend fun deleteAllWorkouts(userId: String): Int
    
    @Deprecated("Use updateSyncStatusForUser instead")
    @Query("""
        UPDATE workouts
        SET is_synced = :isSynced,
            sync_version = :version
        WHERE id = :id
        AND user_id = :userId
    """)
    @UserScoped
    suspend fun updateSyncStatus(id: String, userId: String, isSynced: Boolean, version: Long): Int
    
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
    @UserScoped(userIdParam = "currentUserId")
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
    @UserScoped
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
    @UserScoped
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
    
    /**
     * Gets the last completed workouts containing a specific exercise for previous set data retrieval.
     *
     * Searches for completed workouts that contain the specified exercise ID using the
     * normalized exercises table instead of JSON querying. This is more secure and efficient.
     * Used by GetPreviousSetDataUseCase to find historical performance data for workout comparison.
     *
     * Security:
     * - User-scoped query prevents data leakage between users
     * - Parameterized query eliminates SQL injection risk (SEC-010)
     * - Uses normalized table instead of JSON LIKE patterns
     *
     * Performance:
     * - Leverages idx_exercises_user_library index for fast lookups
     * - Subquery efficiently filters workouts by exercise presence
     * - DISTINCT prevents duplicate results from multiple sets
     *
     * Ordering: Returns most recent workouts first for optimal previous data relevance
     *
     * @param userId The user ID for data scoping (MANDATORY for security)
     * @param exerciseId The exercise library ID to search for
     * @param limit Maximum number of workouts to return (default: 5 for performance)
     * @param excludeWorkoutId Optional workout ID to exclude (current active session)
     * @return List of WorkoutEntity ordered by date DESC, updated_at DESC
     */
    @Query("""
        SELECT * FROM workouts
        WHERE user_id = :userId
        AND status = 'COMPLETED'
        AND id IN (
            SELECT DISTINCT workout_id
            FROM exercises
            WHERE exercise_library_id = :exerciseId
            AND user_id = :userId
        )
        AND (:excludeWorkoutId IS NULL OR id != :excludeWorkoutId)
        ORDER BY date DESC, updated_at DESC
        LIMIT :limit
    """)
    suspend fun getLastCompletedWorkoutsWithExercise(
        userId: String,
        exerciseId: String,
        limit: Int = 5,
        excludeWorkoutId: String? = null
    ): List<WorkoutEntity>
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
