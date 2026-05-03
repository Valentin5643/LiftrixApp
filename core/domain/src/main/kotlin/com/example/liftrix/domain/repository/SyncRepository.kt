package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing data synchronization between local and remote storage.
 * 
 * Follows offline-first architecture where Room database serves as the single source of truth
 * and Firebase acts as the network synchronization layer. All sync operations return
 * LiftrixResult for consistent error handling throughout the domain layer.
 */
interface SyncRepository {
    
    // Core sync operations
    /**
     * Synchronizes all user data (profile, workouts, templates, achievements) bidirectionally.
     * 
     * @param userId The user ID to sync data for
     * @return SyncResult containing sync operation statistics
     */
    suspend fun syncAll(userId: String): LiftrixResult<SyncResult>
    
    /**
     * Synchronizes workout data for the specified user.
     * Handles bidirectional sync with conflict resolution.
     */
    suspend fun syncWorkouts(userId: String): LiftrixResult<Unit>
    
    /**
     * Synchronizes workout template data for the specified user.
     */
    suspend fun syncTemplates(userId: String): LiftrixResult<Unit>
    
    /**
     * Synchronizes achievement data for the specified user.
     */
    suspend fun syncAchievements(userId: String): LiftrixResult<Unit>
    
    /**
     * Synchronizes user profile data.
     */
    suspend fun syncProfile(userId: String): LiftrixResult<Unit>
    
    // Real-time operations
    /**
     * Observes real-time updates for a specific workout during active workout sessions.
     * Enables collaborative workout features and live progress sharing.
     * 
     * @param workoutId The ID of the workout to observe
     * @return Flow emitting workout updates in real-time
     */
    fun observeRealtimeWorkout(workoutId: String): Flow<WorkoutUpdate>
    
    /**
     * Enables real-time synchronization for active workout sessions.
     * Should be called when starting an active workout.
     */
    fun enableRealtimeSync(userId: String)
    
    /**
     * Disables real-time synchronization to conserve resources.
     * Should be called when ending active workout sessions.
     */
    fun disableRealtimeSync()
    
    // Sync status monitoring
    /**
     * Observes the current synchronization status including pending operations and errors.
     * 
     * @return Flow emitting current sync status updates
     */
    fun observeSyncStatus(): Flow<SyncStatus>
    
    /**
     * Gets the count of unsynced items in the offline queue for the specified user.
     * 
     * @param userId The user ID to check unsynced items for
     * @return Number of pending sync operations
     */
    suspend fun getUnsyncedCount(userId: String): LiftrixResult<Int>
    
    /**
     * Clears all pending sync operations for the specified user.
     * Should be used with caution as it will discard unsynced local changes.
     * 
     * @param userId The user ID to clear sync queue for
     */
    suspend fun clearSyncQueue(userId: String): LiftrixResult<Unit>
}

/**
 * Represents the current synchronization status of the application.
 */
data class SyncStatus(
    val isSyncing: Boolean,
    val lastSyncTime: Long?,
    val pendingItems: Int,
    val errors: List<SyncError>
)

/**
 * Contains the results of a sync operation with success/failure statistics.
 */
data class SyncResult(
    val successful: Int,
    val failed: Int,
    val conflicts: Int
)

/**
 * Represents a workout update received through real-time synchronization.
 */
data class WorkoutUpdate(
    val workoutId: String,
    val updatedFields: Map<String, Any>,
    val timestamp: Long
)

/**
 * Represents a synchronization error that occurred during sync operations.
 */
data class SyncError(
    val entityType: String,
    val entityId: String,
    val operation: String,
    val errorMessage: String,
    val timestamp: Long
)