package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.PersonalRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for personal records with MANDATORY user scoping
 * 
 * CRITICAL: ALL queries MUST include user_id filtering to prevent data leakage
 * Follows established patterns from other DAOs in the codebase
 * 
 * Supports:
 * - User-scoped queries for security
 * - Historical best tracking by PR type
 * - Sync status management for Firebase integration
 * - Recent achievements for UI display
 */
@Dao
interface PersonalRecordDao {
    
    /**
     * Gets all personal records for a user and exercise
     * CRITICAL: MUST filter by userId
     */
    @Query("""
        SELECT * FROM personal_records 
        WHERE user_id = :userId AND exercise_name = :exerciseName 
        ORDER BY achieved_at DESC
    """)
    suspend fun getPRsForExercise(userId: String, exerciseName: String): List<PersonalRecordEntity>
    
    /**
     * Gets the best personal record for a user, exercise, and PR type
     * CRITICAL: MUST filter by userId
     */
    @Query("""
        SELECT * FROM personal_records 
        WHERE user_id = :userId 
        AND exercise_name = :exerciseName 
        AND pr_type = :prType 
        ORDER BY 
            CASE 
                WHEN :prType = 'ONE_RM' THEN estimated_one_rm
                WHEN :prType = 'VOLUME' THEN volume 
                WHEN :prType = 'REPS' THEN reps
                WHEN :prType = 'MAX_WEIGHT' THEN weight_kg
                ELSE estimated_one_rm
            END DESC 
        LIMIT 1
    """)
    suspend fun getBestPRByType(
        userId: String, 
        exerciseName: String, 
        prType: String
    ): PersonalRecordEntity?
    
    /**
     * Gets the historical best 1RM for an exercise
     * CRITICAL: MUST filter by userId
     */
    @Query("""
        SELECT * FROM personal_records 
        WHERE user_id = :userId 
        AND exercise_name = :exerciseName 
        AND estimated_one_rm IS NOT NULL
        ORDER BY estimated_one_rm DESC 
        LIMIT 1
    """)
    suspend fun getBest1RM(userId: String, exerciseName: String): PersonalRecordEntity?
    
    /**
     * Gets the historical best volume for an exercise
     * CRITICAL: MUST filter by userId
     */
    @Query("""
        SELECT * FROM personal_records 
        WHERE user_id = :userId 
        AND exercise_name = :exerciseName 
        AND volume IS NOT NULL
        ORDER BY volume DESC 
        LIMIT 1
    """)
    suspend fun getBestVolume(userId: String, exerciseName: String): PersonalRecordEntity?
    
    /**
     * Gets the historical best reps for an exercise
     * CRITICAL: MUST filter by userId
     */
    @Query("""
        SELECT * FROM personal_records 
        WHERE user_id = :userId 
        AND exercise_name = :exerciseName 
        ORDER BY reps DESC 
        LIMIT 1
    """)
    suspend fun getBestReps(userId: String, exerciseName: String): PersonalRecordEntity?
    
    /**
     * Gets the historical max weight for an exercise
     * CRITICAL: MUST filter by userId
     */
    @Query("""
        SELECT * FROM personal_records 
        WHERE user_id = :userId 
        AND exercise_name = :exerciseName 
        AND weight_kg IS NOT NULL
        ORDER BY weight_kg DESC 
        LIMIT 1
    """)
    suspend fun getBestWeight(userId: String, exerciseName: String): PersonalRecordEntity?
    
    /**
     * Gets recent personal records for a user (last 30 days)
     * CRITICAL: MUST filter by userId
     */
    @Query("""
        SELECT * FROM personal_records 
        WHERE user_id = :userId 
        AND achieved_at >= :since 
        ORDER BY achieved_at DESC 
        LIMIT :limit
    """)
    suspend fun getRecentPRs(
        userId: String, 
        since: Long = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L), // 30 days
        limit: Int = 50
    ): List<PersonalRecordEntity>
    
    /**
     * Gets all personal records for a user within date range
     * CRITICAL: MUST filter by userId
     */
    @Query("""
        SELECT * FROM personal_records 
        WHERE user_id = :userId 
        AND achieved_at BETWEEN :startDate AND :endDate 
        ORDER BY achieved_at DESC
    """)
    suspend fun getPRsInDateRange(
        userId: String, 
        startDate: Long, 
        endDate: Long
    ): List<PersonalRecordEntity>
    
    /**
     * Observes personal records for reactive UI updates
     * CRITICAL: MUST filter by userId
     */
    @Query("""
        SELECT * FROM personal_records 
        WHERE user_id = :userId 
        ORDER BY achieved_at DESC
    """)
    fun observePRsForUser(userId: String): Flow<List<PersonalRecordEntity>>
    
    /**
     * Gets PR count by exercise for analytics
     * CRITICAL: MUST filter by userId
     */
    @Query("""
        SELECT exercise_name, COUNT(*) as pr_count 
        FROM personal_records 
        WHERE user_id = :userId 
        GROUP BY exercise_name 
        ORDER BY pr_count DESC
    """)
    suspend fun getPRCountsByExercise(userId: String): List<PRCountResult>
    
    /**
     * Inserts a new personal record
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPR(personalRecord: PersonalRecordEntity): Long
    
    /**
     * Inserts multiple personal records
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPRs(personalRecords: List<PersonalRecordEntity>): List<Long>
    
    /**
     * Updates an existing personal record
     */
    @Update
    suspend fun updatePR(personalRecord: PersonalRecordEntity): Int
    
    /**
     * Updates multiple personal records
     */
    @Update
    suspend fun updatePRs(personalRecords: List<PersonalRecordEntity>): Int
    
    /**
     * Deletes a personal record by ID
     * CRITICAL: MUST filter by userId for security
     */
    @Query("DELETE FROM personal_records WHERE id = :prId AND user_id = :userId")
    suspend fun deletePR(prId: String, userId: String): Int
    
    /**
     * Deletes all personal records for a user
     * CRITICAL: MUST filter by userId
     */
    @Query("DELETE FROM personal_records WHERE user_id = :userId")
    suspend fun deleteAllPRsForUser(userId: String): Int
    
    // ==================== SYNC QUERIES ====================
    
    /**
     * Gets unsynced personal records for Firebase sync
     * CRITICAL: MUST filter by userId
     */
    @Query("""
        SELECT * FROM personal_records 
        WHERE user_id = :userId 
        AND is_synced = 0 
        ORDER BY last_modified ASC 
        LIMIT :limit
    """)
    suspend fun getUnsyncedPRs(userId: String, limit: Int = 20): List<PersonalRecordEntity>
    
    /**
     * Marks personal records as synced
     */
    @Query("""
        UPDATE personal_records 
        SET is_synced = 1, sync_version = :syncVersion 
        WHERE id IN (:prIds) AND user_id = :userId
    """)
    suspend fun markPRsAsSynced(prIds: List<String>, userId: String, syncVersion: Long): Int
    
    /**
     * Gets sync status count for monitoring
     * CRITICAL: MUST filter by userId
     */
    @Query("""
        SELECT 
            COUNT(CASE WHEN is_synced = 1 THEN 1 END) as synced_count,
            COUNT(CASE WHEN is_synced = 0 THEN 1 END) as unsynced_count,
            COUNT(*) as total_count
        FROM personal_records 
        WHERE user_id = :userId
    """)
    suspend fun getSyncStatus(userId: String): PRSyncStatus
    
    /**
     * Gets personal records by workout ID for workout details
     * CRITICAL: MUST filter by userId
     */
    @Query("""
        SELECT * FROM personal_records 
        WHERE user_id = :userId 
        AND workout_id = :workoutId 
        ORDER BY achieved_at DESC
    """)
    suspend fun getPRsForWorkout(userId: String, workoutId: String): List<PersonalRecordEntity>
}

/**
 * Data class for PR count results
 */
data class PRCountResult(
    val exercise_name: String,
    val pr_count: Int
)

/**
 * Data class for sync status results
 */
data class PRSyncStatus(
    val synced_count: Int,
    val unsynced_count: Int,
    val total_count: Int
)