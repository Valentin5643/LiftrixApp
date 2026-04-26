package com.example.liftrix.sync

import android.content.Context
import androidx.lifecycle.asFlow
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import com.example.liftrix.core.workmanager.WorkManagerProvider
import androidx.work.ExistingWorkPolicy
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.repository.ProgressStatsRepository
import com.example.liftrix.config.OfflineArchitectureFlags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class SyncManager @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val progressStatsRepository: ProgressStatsRepository,
    @ApplicationContext private val context: Context
) {
    
    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    /**
     * Get sync status as a Flow
     */
    fun getSyncStatus(): Flow<SyncStatus> {
        return workManager.getWorkInfosForUniqueWorkLiveData(WorkoutSyncWorker.WORK_NAME)
            .asFlow()
            .map { workInfos ->
                val workInfo = workInfos.firstOrNull()
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
            workoutRepository.getUnsyncedCount(userId).getOrElse { 0 }
        }
    }

    /**
     * Queue sync for unsynced workouts
     */
    suspend fun queueSync(userId: String): Result<Unit> {
        return try {
            // Note: queueSync typically would queue all unsynced workouts
            // For now, we'll trigger a general sync for the user
            workoutRepository.syncNow(userId).getOrThrow()
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
            workoutRepository.syncNow(userId).getOrThrow()
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
        workManager.cancelUniqueWork(WorkoutSyncWorker.WORK_NAME)
        workManager.cancelUniqueWork(AnalyticsSyncWorker.WORK_NAME)
        Timber.d("All sync operations cancelled")
    }

    // === ANALYTICS SYNC METHODS ===

    /**
     * Get analytics sync status as a Flow
     */
    fun getAnalyticsSyncStatus(): Flow<SyncStatus> {
        return workManager.getWorkInfosForUniqueWorkLiveData(AnalyticsSyncWorker.WORK_NAME)
            .asFlow()
            .map { workInfos ->
                val workInfo = workInfos.firstOrNull()
                when {
                    workInfo == null -> SyncStatus.Idle
                    workInfo.state == WorkInfo.State.RUNNING -> SyncStatus.Syncing
                    workInfo.state == WorkInfo.State.SUCCEEDED -> {
                        val syncCount = workInfo.outputData.getInt(AnalyticsSyncWorker.KEY_SYNC_COUNT, 0)
                        val conflictCount = workInfo.outputData.getInt(AnalyticsSyncWorker.KEY_CONFLICT_COUNT, 0)
                        SyncStatus.AnalyticsSuccess(syncCount, conflictCount)
                    }
                    workInfo.state == WorkInfo.State.FAILED -> {
                        val errorMessage = workInfo.outputData.getString(AnalyticsSyncWorker.KEY_ERROR_MESSAGE)
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
            val syncRequest = OneTimeWorkRequestBuilder<AnalyticsSyncWorker>()
                .addTag("analytics_sync")
                .addTag("user_$userId")
                .build()

            workManager.enqueueUniqueWork(
                AnalyticsSyncWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )

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
            // Cancel any pending analytics sync first
            workManager.cancelUniqueWork(AnalyticsSyncWorker.WORK_NAME)

            val immediateRequest = OneTimeWorkRequestBuilder<AnalyticsSyncWorker>()
                .addTag("analytics_sync_immediate")
                .addTag("user_$userId")
                .build()

            workManager.enqueueUniqueWork(
                AnalyticsSyncWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                immediateRequest
            )

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
