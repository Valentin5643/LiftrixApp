package com.example.liftrix.domain.repository.workout

import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.WorkoutStats
import com.example.liftrix.domain.model.WorkoutSummary
import com.example.liftrix.domain.model.FeedWorkout
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

// Type alias for ExercisePerformanceData from CalculateExerciseRankingUseCase
typealias ExercisePerformanceData = com.example.liftrix.domain.usecase.analytics.ExercisePerformanceData

/**
 * Repository interface for core workout operations following single responsibility principle.
 * 
 * Handles:
 * - CRUD operations for workouts
 * - User-scoped data access
 * - Basic workout querying and filtering
 * 
 * Does NOT handle:
 * - Statistics computation (see WorkoutStatsRepository)
 * - Social feed operations (see WorkoutFeedRepository) 
 * - Sync operations (see WorkoutSyncRepository)
 * - History pagination (see WorkoutHistoryRepository)
 */
interface WorkoutRepository {
    
    /**
     * Create a new workout for the specified user.
     * 
     * @param workout The workout to create (must include valid userId)
     * @return LiftrixResult with created workout including generated ID
     */
    suspend fun createWorkout(workout: Workout): LiftrixResult<Workout>
    
    /**
     * Get a specific workout by ID for the specified user.
     * 
     * @param id The workout ID to retrieve
     * @param userId The user ID for data scoping
     * @return LiftrixResult with workout if found, null if not found
     */
    suspend fun getWorkoutById(id: WorkoutId, userId: String): LiftrixResult<Workout?>
    
    /**
     * Get all workouts for a specific user.
     * 
     * @param userId The user ID for data scoping
     * @return Flow of LiftrixResult with list of user's workouts
     */
    fun getWorkoutsByUser(userId: String): Flow<LiftrixResult<List<Workout>>>
    
    /**
     * Get workouts for a specific date and user.
     * 
     * @param date The date to filter by
     * @param userId The user ID for data scoping
     * @return Flow of LiftrixResult with workouts on the specified date
     */
    fun getWorkoutsByDate(date: LocalDate, userId: String): Flow<LiftrixResult<List<Workout>>>
    
    /**
     * Get exercise performance data for analytics calculations.
     * 
     * @param userId The user ID for data scoping
     * @param startDate Start date for data collection
     * @param endDate End date for data collection
     * @return LiftrixResult with list of exercise performance data
     */
    suspend fun getExercisePerformanceData(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): LiftrixResult<List<ExercisePerformanceData>>
    
    /**
     * Update an existing workout.
     * 
     * @param workout The workout with updated data (must include valid ID and userId)
     * @return LiftrixResult with updated workout
     */
    suspend fun updateWorkout(workout: Workout): LiftrixResult<Workout>
    
    /**
     * Delete a workout for the specified user.
     * 
     * @param workoutId The ID of the workout to delete
     * @param userId The user ID for data scoping and authorization
     * @return LiftrixResult indicating success or failure
     */
    suspend fun deleteWorkout(workoutId: WorkoutId, userId: String): LiftrixResult<Unit>
    
    /**
     * Get the currently active workout for a user (workout in progress).
     * 
     * @param userId The user ID for data scoping
     * @return LiftrixResult with active workout if exists, null otherwise
     */
    suspend fun getActiveWorkout(userId: String): LiftrixResult<Workout?>
    
    /**
     * Get recent workouts for a user with specified limit.
     * 
     * @param userId The user ID for data scoping
     * @param limit Maximum number of workouts to return (default: 10)
     * @return Flow of LiftrixResult with recent workouts ordered by date descending
     */
    fun getRecentWorkouts(userId: String, limit: Int = 10): Flow<LiftrixResult<List<Workout>>>
    
    /**
     * Check if a workout exists for the specified user.
     * 
     * @param workoutId The workout ID to check
     * @param userId The user ID for data scoping
     * @return LiftrixResult with true if workout exists, false otherwise
     */
    suspend fun workoutExists(workoutId: WorkoutId, userId: String): LiftrixResult<Boolean>
    
    /**
     * Get total count of workouts for a user.
     * 
     * @param userId The user ID for data scoping
     * @return LiftrixResult with total workout count
     */
    suspend fun getWorkoutCount(userId: String): LiftrixResult<Int>
    
    /**
     * Delete all workouts for a specific user.
     * WARNING: This is a destructive operation.
     * 
     * @param userId The user ID for data scoping
     * @return LiftrixResult indicating success or failure
     */
    suspend fun deleteAllWorkouts(userId: String): LiftrixResult<Unit>
    
    // ============== LEGACY COMPATIBILITY METHODS ==============
    // These methods provide compatibility with the legacy codebase
    // and will be gradually refactored to use the standardized methods above
    
    /**
     * Save a workout (legacy method for compatibility).
     * Maps to createWorkout() or updateWorkout() based on workout state.
     * 
     * @param workout The workout to save
     * @return Result with Unit indicating success or failure
     */
    suspend fun saveWorkout(workout: Workout): Result<Unit>
    
    /**
     * Get workout history for a user (legacy method for compatibility).
     * 
     * @param userId The user ID for data scoping
     * @param limit Maximum number of workouts to return
     * @param offset Offset for pagination
     * @return LiftrixResult with list of workout summaries
     */
    suspend fun getUserWorkoutHistory(userId: String, limit: Int = 20, offset: Int = 0): LiftrixResult<List<WorkoutSummary>>
    
    /**
     * Get count of workout history entries for a user (legacy method for compatibility).
     * 
     * @param userId The user ID for data scoping
     * @return LiftrixResult with total count
     */
    suspend fun getWorkoutHistoryCount(userId: String): LiftrixResult<Int>
    
    /**
     * Get all workouts for a user (legacy method for compatibility).
     * Maps to getWorkoutsByUser() but returns Flow of List instead of Flow of LiftrixResult.
     * 
     * @param userId The user ID for data scoping
     * @return Flow of workout lists
     */
    fun getAllWorkoutsForUser(userId: String): Flow<List<Workout>>
    
    /**
     * Get unsynced workout count for a user (legacy method for compatibility).
     * 
     * @param userId The user ID for data scoping
     * @return LiftrixResult with unsynced count
     */
    suspend fun getUnsyncedCount(userId: String): LiftrixResult<Int>
    
    /**
     * Get unsynced workout count for a user (legacy method for compatibility).
     * 
     * @param userId The user ID for data scoping
     * @return LiftrixResult with unsynced count
     */
    suspend fun getUnsyncedCountForUser(userId: String): LiftrixResult<Int>
    
    /**
     * Queue workout for sync (legacy method for compatibility).
     * 
     * @param workoutId The workout ID to queue for sync
     * @param userId The user ID for data scoping
     * @return LiftrixResult indicating success or failure
     */
    suspend fun queueSync(workoutId: WorkoutId, userId: String): LiftrixResult<Unit>
    
    /**
     * Trigger immediate sync for user (legacy method for compatibility).
     * 
     * @param userId The user ID for data scoping
     * @return LiftrixResult indicating success or failure
     */
    suspend fun syncNow(userId: String): LiftrixResult<Unit>
    
    /**
     * Trigger immediate sync for user (legacy method for compatibility).
     * 
     * @param userId The user ID for data scoping
     * @return LiftrixResult indicating success or failure
     */
    suspend fun syncNowForUser(userId: String): LiftrixResult<Unit>
    
    /**
     * Get feed workouts for display (legacy method for compatibility).
     * 
     * @param userId The user ID for data scoping
     * @param limit Maximum number of feed workouts to return
     * @return LiftrixResult with list of feed workouts
     */
    suspend fun getFeedWorkouts(userId: String, limit: Int = 10): LiftrixResult<List<FeedWorkout>>
    
    /**
     * Get feed workouts for display as reactive Flow.
     * 
     * Returns a Flow of completed workouts formatted for display in the social feed.
     * The Flow automatically updates when new workouts are completed.
     * 
     * @param userId The user ID for data scoping
     * @param limit Maximum number of feed workouts to return
     * @return Flow of LiftrixResult with list of feed workouts
     */
    fun getFeedWorkoutsReactive(userId: String, limit: Int = 10): Flow<LiftrixResult<List<FeedWorkout>>>
    
    /**
     * Get workout statistics for a user (legacy method for compatibility).
     * 
     * @param userId The user ID for data scoping
     * @return LiftrixResult with workout statistics
     */
    suspend fun getWorkoutStats(userId: String): LiftrixResult<WorkoutStats>
}