package com.example.liftrix.sync

import android.content.Context
import androidx.lifecycle.asFlow
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.liftrix.domain.sync.ProgressStatsSyncRepository
import com.example.liftrix.domain.repository.workout.WorkoutSyncStatusRepository
import com.example.liftrix.config.OfflineArchitectureFlags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class SyncManager @Inject constructor(
    private val workoutSyncStatusRepository: WorkoutSyncStatusRepository,
    private val progressStatsRepository: ProgressStatsSyncRepository,
    private val syncCoordinatorProvider: Provider<SyncCoordinator>,
    @ApplicationContext private val context: Context
) {
    
    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    /**
     * Get sync status as a Flow
     */
    fun getSyncStatus(): Flow<SyncStatus> {
        return workManager.getWorkInfosByTagLiveData("unified_sync")
            .asFlow()
            .map { workInfos ->
                val workInfo = workInfos.firstOrNull {
                    it.state == WorkInfo.State.RUNNING ||
                        it.tags.contains("sync_type_all") ||
                        it.tags.contains("immediate_sync")
                } ?: workInfos.firstOrNull()
                when {
                    workInfo == null -> SyncStatus.Idle
                    workInfo.state == WorkInfo.State.RUNNING -> SyncStatus.Syncing
                    workInfo.state == WorkInfo.State.SUCCEEDED -> {
                        val syncCount = workInfo.outputData.getInt("sync_count", 0)
                        SyncStatus.Success(syncCount)
                    }
                    workInfo.state == WorkInfo.State.FAILED -> {
                        val errorMessage = workInfo.outputData.getString("error_message")
                        SyncStatus.Error(errorMessage ?: "Unknown sync error")
                    }
                    else -> SyncStatus.Idle
                }
            }
    }

    /**
     * Get count of unsynced workouts
     */
    suspend fun getUnsyncedCount(userId: String): Int {
        return if (OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
            OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING
        ) {
            0
        } else {
            workoutSyncStatusRepository.getUnsyncedCount(userId).getOrElse { 0 }
        }
    }

    /**
     * Queue sync for unsynced workouts
     */
    suspend fun queueSync(userId: String): Result<Unit> {
        return try {
            syncCoordinatorProvider.get().triggerImmediateSync(userId).getOrThrow()
            Timber.d("Sync queued successfully for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to queue sync for user: $userId")
            Result.failure(e)
        }
    }

    /**
     * Force immediate sync
     */
    suspend fun syncNow(userId: String): Result<Unit> {
        return try {
            syncCoordinatorProvider.get().triggerImmediateSync(userId).getOrThrow()
            Timber.d("Immediate sync initiated for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initiate immediate sync for user: $userId")
            Result.failure(e)
        }
    }

    /**
     * Cancel any pending sync operations
     */
    fun cancelSync() {
        syncCoordinatorProvider.get().cancelAllSync()
        Timber.d("All sync operations cancelled")
    }

    // === ANALYTICS SYNC METHODS ===

    /**
     * Get analytics sync status as a Flow
     */
    fun getAnalyticsSyncStatus(): Flow<SyncStatus> {
        return workManager.getWorkInfosByTagLiveData("sync_type_analytics")
            .asFlow()
            .map { workInfos ->
                val workInfo = workInfos.firstOrNull()
                when {
                    workInfo == null -> SyncStatus.Idle
                    workInfo.state == WorkInfo.State.RUNNING -> SyncStatus.Syncing
                    workInfo.state == WorkInfo.State.SUCCEEDED -> {
                        val syncCount = workInfo.outputData.getInt("sync_count", 0)
                        val conflictCount = workInfo.outputData.getInt("conflict_count", 0)
                        SyncStatus.AnalyticsSuccess(syncCount, conflictCount)
                    }
                    workInfo.state == WorkInfo.State.FAILED -> {
                        val errorMessage = workInfo.outputData.getString("error_message")
                        SyncStatus.Error(errorMessage ?: "Unknown analytics sync error")
                    }
                    else -> SyncStatus.Idle
                }
            }
    }

    /**
     * Get combined sync status for both workouts and analytics
     */
    fun getCombinedSyncStatus(): Flow<CombinedSyncStatus> {
        return combine(
            getSyncStatus(),
            getAnalyticsSyncStatus()
        ) { workoutStatus, analyticsStatus ->
            CombinedSyncStatus(
                workoutStatus = workoutStatus,
                analyticsStatus = analyticsStatus
            )
        }
    }

    /**
     * Get count of unsynced analytics calculations
     */
    suspend fun getUnsyncedAnalyticsCount(userId: String): Int {
        return try {
            progressStatsRepository.getUnsyncedCalculationsCount(userId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get unsynced analytics count for user: $userId")
            0
        }
    }

    /**
     * Queue analytics sync for unsynced calculations
     */
    suspend fun queueAnalyticsSync(userId: String): Result<Unit> {
        return try {
            syncCoordinatorProvider.get().triggerEntitySync(userId, "analytics").getOrThrow()
            Timber.d("Analytics sync queued successfully for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to queue analytics sync for user: $userId")
            Result.failure(e)
        }
    }

    /**
     * Force immediate analytics sync
     */
    suspend fun syncAnalyticsNow(userId: String): Result<Unit> {
        return try {
            syncCoordinatorProvider.get().triggerEntitySync(userId, "analytics").getOrThrow()
            Timber.d("Immediate analytics sync initiated for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initiate immediate analytics sync for user: $userId")
            Result.failure(e)
        }
    }

    /**
     * Sync both workouts and analytics data
     */
    suspend fun syncAllData(userId: String): Result<Unit> {
        return try {
            // Queue both workout and analytics sync
            queueSync(userId).getOrThrow()
            queueAnalyticsSync(userId).getOrThrow()
            
            Timber.d("Complete data sync initiated for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initiate complete data sync for user: $userId")
            Result.failure(e)
        }
    }

    /**
     * Cancel analytics sync operations
     */
    fun cancelAnalyticsSync() {
        workManager.cancelAllWorkByTag("sync_type_analytics")
        workManager.cancelUniqueWork(AnalyticsSyncWorker.WORK_NAME)
        Timber.d("Analytics sync operations cancelled")
    }
}

/**
 * Represents the current sync status
 */
sealed class SyncStatus {
    object Idle : SyncStatus()
    object Syncing : SyncStatus()
    data class Success(val syncedCount: Int) : SyncStatus()
    data class AnalyticsSuccess(val syncedCount: Int, val conflictCount: Int) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

/**
 * Combined sync status for both workouts and analytics
 */
data class CombinedSyncStatus(
    val workoutStatus: SyncStatus,
    val analyticsStatus: SyncStatus
) {
    val isAnySync: Boolean = workoutStatus is SyncStatus.Syncing || analyticsStatus is SyncStatus.Syncing
    val hasAnyError: Boolean = workoutStatus is SyncStatus.Error || analyticsStatus is SyncStatus.Error
    val isAllSuccess: Boolean = workoutStatus is SyncStatus.Success && analyticsStatus is SyncStatus.Success
} 
