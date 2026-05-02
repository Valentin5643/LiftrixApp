package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.service.PersonalRecord
import com.example.liftrix.domain.service.PRType
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for personal record persistence and retrieval
 * 
 * Provides user-scoped operations for personal records with proper error handling
 * Supports both individual and batch operations for performance
 * Includes reactive data access for real-time UI updates
 * 
 * CRITICAL: All operations MUST filter by userId to prevent data leakage
 */
interface PersonalRecordRepository {
    
    /**
     * Saves a personal record to persistent storage
     * 
     * @param personalRecord The personal record to save
     * @param userId The ID of the user who achieved the record
     * @param workoutId The ID of the workout where the record was achieved
     * @return LiftrixResult indicating success or failure
     */
    suspend fun savePR(
        personalRecord: PersonalRecord,
        userId: String,
        workoutId: String
    ): LiftrixResult<Unit>
    
    /**
     * Saves multiple personal records in a single transaction
     * 
     * @param personalRecords List of personal records to save
     * @param userId The ID of the user who achieved the records
     * @param workoutId The ID of the workout where the records were achieved
     * @return LiftrixResult indicating success or failure
     */
    suspend fun savePRs(
        personalRecords: List<PersonalRecord>,
        userId: String,
        workoutId: String
    ): LiftrixResult<Unit>
    
    /**
     * Gets the best personal record for a specific exercise and PR type
     * 
     * @param userId The ID of the user
     * @param exerciseName The name of the exercise
     * @param prType The type of PR to retrieve
     * @return LiftrixResult containing the best PR or null if none exists
     */
    suspend fun getBestPR(
        userId: String,
        exerciseName: String,
        prType: PRType
    ): LiftrixResult<PersonalRecord?>
    
    /**
     * Gets the historical best 1RM for an exercise
     * 
     * @param userId The ID of the user
     * @param exerciseName The name of the exercise
     * @return LiftrixResult containing the best 1RM PR or null if none exists
     */
    suspend fun getBest1RM(
        userId: String,
        exerciseName: String
    ): LiftrixResult<PersonalRecord?>
    
    /**
     * Gets the historical best volume for an exercise
     * 
     * @param userId The ID of the user
     * @param exerciseName The name of the exercise
     * @return LiftrixResult containing the best volume PR or null if none exists
     */
    suspend fun getBestVolume(
        userId: String,
        exerciseName: String
    ): LiftrixResult<PersonalRecord?>
    
    /**
     * Gets the historical best reps for an exercise
     * 
     * @param userId The ID of the user
     * @param exerciseName The name of the exercise
     * @return LiftrixResult containing the best reps PR or null if none exists
     */
    suspend fun getBestReps(
        userId: String,
        exerciseName: String
    ): LiftrixResult<PersonalRecord?>
    
    /**
     * Gets the historical max weight for an exercise
     * 
     * @param userId The ID of the user
     * @param exerciseName The name of the exercise
     * @return LiftrixResult containing the best weight PR or null if none exists
     */
    suspend fun getBestWeight(
        userId: String,
        exerciseName: String
    ): LiftrixResult<PersonalRecord?>
    
    /**
     * Gets all personal records for a specific exercise
     * 
     * @param userId The ID of the user
     * @param exerciseName The name of the exercise
     * @return LiftrixResult containing list of PRs for the exercise
     */
    suspend fun getPRsForExercise(
        userId: String,
        exerciseName: String
    ): LiftrixResult<List<PersonalRecord>>
    
    /**
     * Gets recent personal records (last 30 days)
     * 
     * @param userId The ID of the user
     * @param limit Maximum number of records to return
     * @return LiftrixResult containing list of recent PRs
     */
    suspend fun getRecentPRs(
        userId: String,
        limit: Int = 50
    ): LiftrixResult<List<PersonalRecord>>
    
    /**
     * Gets personal records within a date range
     * 
     * @param userId The ID of the user
     * @param startDate Start timestamp in milliseconds
     * @param endDate End timestamp in milliseconds
     * @return LiftrixResult containing list of PRs in the date range
     */
    suspend fun getPRsInDateRange(
        userId: String,
        startDate: Long,
        endDate: Long
    ): LiftrixResult<List<PersonalRecord>>
    
    /**
     * Gets personal records for a specific workout
     * 
     * @param userId The ID of the user
     * @param workoutId The ID of the workout
     * @return LiftrixResult containing list of PRs achieved in the workout
     */
    suspend fun getPRsForWorkout(
        userId: String,
        workoutId: String
    ): LiftrixResult<List<PersonalRecord>>
    
    /**
     * Observes personal records for reactive UI updates
     * 
     * @param userId The ID of the user
     * @return Flow of personal records list
     */
    fun observePRsForUser(userId: String): Flow<List<PersonalRecord>>
    
    /**
     * Gets PR counts by exercise for analytics
     * 
     * @param userId The ID of the user
     * @return LiftrixResult containing map of exercise names to PR counts
     */
    suspend fun getPRCountsByExercise(userId: String): LiftrixResult<Map<String, Int>>
    
    /**
     * Deletes a personal record
     * 
     * @param prId The ID of the personal record to delete
     * @param userId The ID of the user (for security)
     * @return LiftrixResult indicating success or failure
     */
    suspend fun deletePR(prId: String, userId: String): LiftrixResult<Unit>
    
    /**
     * Deletes all personal records for a user
     * 
     * @param userId The ID of the user
     * @return LiftrixResult indicating success or failure
     */
    suspend fun deleteAllPRsForUser(userId: String): LiftrixResult<Unit>
    
    /**
     * Gets sync status for personal records
     * 
     * @param userId The ID of the user
     * @return LiftrixResult containing sync status information
     */
    suspend fun getSyncStatus(userId: String): LiftrixResult<PRSyncStatus>
}

/**
 * Data class for PR sync status
 */
data class PRSyncStatus(
    val syncedCount: Int,
    val unsyncedCount: Int,
    val totalCount: Int
) {
    val syncPercentage: Float = if (totalCount > 0) syncedCount.toFloat() / totalCount else 0f
    val isFullySynced: Boolean = unsyncedCount == 0
}