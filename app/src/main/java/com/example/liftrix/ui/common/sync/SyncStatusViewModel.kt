package com.example.liftrix.ui.common.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.repository.SyncStatusRepository
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.sync.SyncManager
import com.example.liftrix.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for managing sync status across the application.
 * 
 * Provides centralized sync state management with:
 * - Real-time sync status from SyncStatusRepository
 * - User-scoped sync operations
 * - Error handling and recovery
 * - Manual sync triggering
 * - Combined sync status for multiple operations
 * 
 * This ViewModel is designed to be used by UI components that need to:
 * - Display sync status indicators
 * - Allow users to manually trigger sync
 * - Show progress during sync operations
 * - Handle sync errors gracefully
 */
@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    private val syncStatusRepository: SyncStatusRepository,
    private val syncManager: SyncManager,
    private val authQueryUseCase: AuthQueryUseCase
) : ViewModel() {

    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    init {
        loadCurrentUser()
    }

    /**
     * Gets the current sync status for the authenticated user
     */
    val syncStatus: Flow<SyncStatus> = currentUserId
        .filterNotNull()
        .flatMapLatest { userId ->
            syncStatusRepository.getSyncStatus(userId)
        }
        .catch { throwable ->
            Timber.e(throwable, "Error observing sync status")
            emit(SyncStatus.Error("Failed to get sync status"))
        }

    /**
     * Gets combined sync status from SyncManager (includes analytics)
     */
    val combinedSyncStatus = syncManager.getCombinedSyncStatus()
        .catch { throwable ->
            Timber.e(throwable, "Error observing combined sync status")
            emit(com.example.liftrix.sync.CombinedSyncStatus(
                workoutStatus = SyncStatus.Error("Failed to get sync status"),
                analyticsStatus = SyncStatus.Error("Failed to get sync status")
            ))
        }

    /**
     * Manually triggers sync for the current user
     */
    fun triggerSync() {
        viewModelScope.launch {
            val userId = _currentUserId.value
            if (userId != null) {
                try {
                    Timber.d("SyncStatusViewModel: Triggering manual sync for user $userId")
                    syncManager.syncAllData(userId).fold(
                        onSuccess = {
                            Timber.d("SyncStatusViewModel: Manual sync initiated successfully")
                        },
                        onFailure = { throwable ->
                            Timber.e(throwable, "SyncStatusViewModel: Failed to trigger manual sync")
                            // Error will be reflected in sync status flows
                        }
                    )
                } catch (e: Exception) {
                    Timber.e(e, "SyncStatusViewModel: Exception during manual sync trigger")
                }
            } else {
                Timber.w("SyncStatusViewModel: Cannot trigger sync - no authenticated user")
            }
        }
    }

    /**
     * Triggers only workout sync
     */
    fun triggerWorkoutSync() {
        viewModelScope.launch {
            val userId = _currentUserId.value
            if (userId != null) {
                try {
                    Timber.d("SyncStatusViewModel: Triggering workout sync for user $userId")
                    syncManager.syncNow(userId).fold(
                        onSuccess = {
                            Timber.d("SyncStatusViewModel: Workout sync initiated successfully")
                        },
                        onFailure = { throwable ->
                            Timber.e(throwable, "SyncStatusViewModel: Failed to trigger workout sync")
                        }
                    )
                } catch (e: Exception) {
                    Timber.e(e, "SyncStatusViewModel: Exception during workout sync trigger")
                }
            }
        }
    }

    /**
     * Triggers only analytics sync
     */
    fun triggerAnalyticsSync() {
        viewModelScope.launch {
            val userId = _currentUserId.value
            if (userId != null) {
                try {
                    Timber.d("SyncStatusViewModel: Triggering analytics sync for user $userId")
                    syncManager.syncAnalyticsNow(userId).fold(
                        onSuccess = {
                            Timber.d("SyncStatusViewModel: Analytics sync initiated successfully")
                        },
                        onFailure = { throwable ->
                            Timber.e(throwable, "SyncStatusViewModel: Failed to trigger analytics sync")
                        }
                    )
                } catch (e: Exception) {
                    Timber.e(e, "SyncStatusViewModel: Exception during analytics sync trigger")
                }
            }
        }
    }

    /**
     * Gets unsynced count for current user
     */
    suspend fun getUnsyncedCount(): Int {
        val userId = _currentUserId.value ?: return 0
        return try {
            syncManager.getUnsyncedCount(userId)
        } catch (e: Exception) {
            Timber.e(e, "SyncStatusViewModel: Failed to get unsynced count")
            0
        }
    }

    /**
     * Gets unsynced analytics count for current user
     */
    suspend fun getUnsyncedAnalyticsCount(): Int {
        val userId = _currentUserId.value ?: return 0
        return try {
            syncManager.getUnsyncedAnalyticsCount(userId)
        } catch (e: Exception) {
            Timber.e(e, "SyncStatusViewModel: Failed to get unsynced analytics count")
            0
        }
    }

    /**
     * Cancels all sync operations
     */
    fun cancelSync() {
        try {
            syncManager.cancelSync()
            Timber.d("SyncStatusViewModel: Sync operations cancelled")
        } catch (e: Exception) {
            Timber.e(e, "SyncStatusViewModel: Failed to cancel sync")
        }
    }

    /**
     * Resets sync status for current user (useful during logout)
     */
    fun resetSyncStatus() {
        viewModelScope.launch {
            val userId = _currentUserId.value
            if (userId != null) {
                try {
                    syncStatusRepository.resetSyncStatus(userId)
                    Timber.d("SyncStatusViewModel: Sync status reset for user $userId")
                } catch (e: Exception) {
                    Timber.e(e, "SyncStatusViewModel: Failed to reset sync status")
                }
            }
        }
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it?.value },
                    onFailure = { null }
                )
                _currentUserId.value = userId
                Timber.d("SyncStatusViewModel: Current user loaded: $userId")
            } catch (e: Exception) {
                Timber.e(e, "SyncStatusViewModel: Failed to load current user")
                _currentUserId.value = null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up sync status for current user
        val userId = _currentUserId.value
        if (userId != null) {
            syncStatusRepository.cleanupUserStatus(userId)
        }
    }
}