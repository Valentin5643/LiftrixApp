package com.example.liftrix.domain.repository

import com.example.liftrix.sync.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing and observing sync status across the application.
 * 
 * This repository provides:
 * - Real-time sync status updates via Flow
 * - Per-user sync status tracking
 * - Global sync status aggregation
 * - Integration with UI components for status display
 * 
 * The sync status flows are used by:
 * - SyncStatusIndicator components in the UI
 * - ViewModels that need to show sync progress
 * - Background services that coordinate sync operations
 */
@Singleton
class SyncStatusRepository @Inject constructor() {
    
    // Internal state holders for each user's sync status
    private val _syncStatusMap = mutableMapOf<String, MutableStateFlow<SyncStatus>>()
    
    /**
     * Gets the current sync status for a specific user as a Flow.
     * Creates a new status flow if one doesn't exist for the user.
     * 
     * @param userId The user ID to get status for
     * @return Flow of sync status updates
     */
    fun getSyncStatus(userId: String): Flow<SyncStatus> {
        return getUserStatusFlow(userId).asStateFlow()
    }
    
    /**
     * Updates the sync status for a specific user.
     * 
     * @param userId The user ID to update status for
     * @param isInProgress Whether sync is currently in progress
     * @param hasError Whether sync has encountered an error
     * @param lastSyncTime Optional timestamp of last successful sync
     * @param syncedCount Number of items synced (for success states)
     * @param errorMessage Error message (for error states)
     */
    suspend fun updateSyncStatus(
        userId: String,
        isInProgress: Boolean = false,
        hasError: Boolean = false,
        lastSyncTime: Long? = null,
        syncedCount: Int = 0,
        errorMessage: String? = null
    ) {
        val status = when {
            isInProgress -> SyncStatus.Syncing
            hasError -> SyncStatus.Error(errorMessage ?: "Sync failed")
            syncedCount > 0 -> SyncStatus.Success(syncedCount)
            else -> SyncStatus.Idle
        }
        
        val statusFlow = getUserStatusFlow(userId)
        statusFlow.value = status
        
        Timber.d("SyncStatusRepository: Updated status for user $userId: $status")
    }
    
    /**
     * Resets sync status to idle for a specific user.
     * Used during user logout or when clearing sync state.
     * 
     * @param userId The user ID to reset status for
     */
    suspend fun resetSyncStatus(userId: String) {
        val statusFlow = getUserStatusFlow(userId)
        statusFlow.value = SyncStatus.Idle
        
        Timber.d("SyncStatusRepository: Reset sync status for user $userId")
    }
    
    /**
     * Gets or creates a status flow for a specific user.
     * 
     * @param userId The user ID to get flow for
     * @return MutableStateFlow for the user's sync status
     */
    private fun getUserStatusFlow(userId: String): MutableStateFlow<SyncStatus> {
        return _syncStatusMap.getOrPut(userId) {
            MutableStateFlow(SyncStatus.Idle)
        }
    }
    
    /**
     * Cleans up status flow for a user (e.g., during logout).
     * 
     * @param userId The user ID to clean up
     */
    fun cleanupUserStatus(userId: String) {
        _syncStatusMap.remove(userId)
        Timber.d("SyncStatusRepository: Cleaned up status for user $userId")
    }
    
    /**
     * Gets a combined view of all active sync statuses.
     * Useful for global sync indicators.
     * 
     * @return Flow that emits true if any user has an active sync
     */
    fun hasAnySyncInProgress(): Flow<Boolean> {
        return kotlinx.coroutines.flow.combine(_syncStatusMap.values) { statuses ->
            statuses.any { it is SyncStatus.Syncing }
        }
    }
}