package com.example.liftrix.domain.repository.sync

import com.example.liftrix.domain.model.common.LiftrixResult

/**
 * Service interface for managing data synchronization operations.
 * 
 * This service handles synchronization between local database and remote services,
 * operation queuing for offline scenarios, and retry logic for failed operations.
 */
interface SyncService {
    
    /**
     * Queues an operation for later execution when network becomes available.
     * 
     * @param userId The ID of the user for whom to queue the operation
     * @param operation The operation to queue
     * @param priority Priority level for the operation (1-5, where 1 is highest)
     * @return A Result containing the operation ID if successful
     */
    suspend fun queueOperation(userId: String, operation: String, priority: Int = 3): LiftrixResult<String>
    
    /**
     * Processes all queued operations for a user.
     * 
     * @param userId The ID of the user whose operations to process
     * @return A Result indicating how many operations were processed successfully
     */
    suspend fun processQueuedOperations(userId: String): LiftrixResult<Int>
    
    /**
     * Retries a failed operation with exponential backoff.
     * 
     * @param operationId The ID of the operation to retry
     * @return A Result indicating success or failure
     */
    suspend fun retryOperation(operationId: String): LiftrixResult<Unit>
    
    /**
     * Forces immediate sync of all user data.
     * 
     * @param userId The ID of the user whose data to sync
     * @return A Result indicating success or failure
     */
    suspend fun forceSyncAll(userId: String): LiftrixResult<Unit>
    
    /**
     * Checks the sync status for a specific user.
     * 
     * @param userId The ID of the user whose sync status to check
     * @return A Result containing sync status information
     */
    suspend fun getSyncStatus(userId: String): LiftrixResult<SyncStatus>
    
    /**
     * Cancels all pending operations for a user.
     * 
     * @param userId The ID of the user whose operations to cancel
     * @return A Result indicating success or failure
     */
    suspend fun cancelPendingOperations(userId: String): LiftrixResult<Unit>
}

/**
 * Represents the synchronization status for a user.
 */
data class SyncStatus(
    val pendingOperations: Int,
    val lastSyncTime: Long,
    val isOnline: Boolean,
    val hasErrors: Boolean
)