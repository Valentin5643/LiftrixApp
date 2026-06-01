package com.example.liftrix.domain.repository.workout

import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

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
     * @return Flow of user's workouts from the local source of truth
     */
    fun getWorkoutsByUser(userId: String): Flow<List<Workout>>
    
    /**
     * Get workouts for a specific date and user.
     * 
     * @param date The date to filter by
     * @param userId The user ID for data scoping
     * @return Flow of workouts on the specified date from the local source of truth
     */
    fun getWorkoutsByDate(date: LocalDate, userId: String): Flow<List<Workout>>
    
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
     * @return Flow of recent workouts ordered by date descending from the local source of truth
     */
    fun getRecentWorkouts(userId: String, limit: Int = 10): Flow<List<Workout>>
    
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

    /**
     * Save a workout (legacy method for compatibility).
     * Maps to createWorkout() or updateWorkout() based on workout state.
     * 
     * @param workout The workout to save
     * @return Result with Unit indicating success or failure
     */
    suspend fun saveWorkout(workout: Workout): Result<Unit>

}
