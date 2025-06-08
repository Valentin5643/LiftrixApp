package com.example.liftrix.data.repository

import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface WorkoutRepository {
    
    // User-scoped methods for data isolation
    fun getAllWorkoutsForUser(userId: String): Flow<List<Workout>>
    
    suspend fun getWorkoutByIdForUser(id: WorkoutId, userId: String): Workout?
    
    fun getWorkoutsByDateForUser(date: LocalDate, userId: String): Flow<List<Workout>>
    
    suspend fun getActiveWorkoutForUser(userId: String): Workout?
    
    suspend fun getUnsyncedCountForUser(userId: String): Int
    
    suspend fun getUnsyncedWorkoutsForUser(userId: String): List<Workout>
    
    // Workout CRUD operations (userId validation handled in implementation)
    suspend fun saveWorkout(workout: Workout): Result<Unit>
    
    suspend fun updateWorkout(workout: Workout): Result<Unit>
    
    suspend fun deleteWorkoutForUser(workoutId: WorkoutId, userId: String): Result<Unit>
    
    suspend fun deleteAllWorkoutsForUser(userId: String): Result<Unit>
    
    // Sync operations (user-scoped)
    suspend fun queueSyncForUser(userId: String): Result<Unit>
    
    suspend fun syncNowForUser(userId: String): Result<Unit>
    
    suspend fun markWorkoutsAsSyncedForUser(workoutIds: List<String>, userId: String): Result<Unit>
    
    // Legacy methods (deprecated - for migration only)
    @Deprecated("Use user-scoped methods instead", ReplaceWith("getAllWorkoutsForUser(userId)"))
    fun getAllWorkouts(): Flow<List<Workout>>
    
    @Deprecated("Use user-scoped methods instead", ReplaceWith("getWorkoutByIdForUser(id, userId)"))
    suspend fun getWorkoutById(id: WorkoutId): Workout?
    
    @Deprecated("Use user-scoped methods instead", ReplaceWith("getWorkoutsByDateForUser(date, userId)"))
    fun getWorkoutsByDate(date: LocalDate): Flow<List<Workout>>
    
    @Deprecated("Use user-scoped methods instead", ReplaceWith("deleteWorkoutForUser(workoutId, userId)"))
    suspend fun deleteWorkout(workoutId: WorkoutId): Result<Unit>
    
    @Deprecated("Use user-scoped methods instead", ReplaceWith("queueSyncForUser(userId)"))
    suspend fun queueSync(): Result<Unit>
    
    @Deprecated("Use user-scoped methods instead", ReplaceWith("syncNowForUser(userId)"))
    suspend fun syncNow(): Result<Unit>
    
    @Deprecated("Use user-scoped methods instead", ReplaceWith("getUnsyncedCountForUser(userId)"))
    suspend fun getUnsyncedCount(): Int
    
    @Deprecated("Use user-scoped methods instead", ReplaceWith("deleteAllWorkoutsForUser(userId)"))
    suspend fun clearAllWorkouts(): Result<Unit>
} 