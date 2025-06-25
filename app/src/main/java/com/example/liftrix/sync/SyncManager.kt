package com.example.liftrix.sync

import androidx.lifecycle.asFlow
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.liftrix.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val workManager: WorkManager
) {

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
                        val syncCount = workInfo.outputData.getInt(WorkoutSyncWorker.KEY_SYNC_COUNT, 0)
                        SyncStatus.Success(syncCount)
                    }
                    workInfo.state == WorkInfo.State.FAILED -> {
                        val errorMessage = workInfo.outputData.getString(WorkoutSyncWorker.KEY_ERROR_MESSAGE)
                        SyncStatus.Error(errorMessage ?: "Unknown sync error")
                    }
                    else -> SyncStatus.Idle
                }
            }
    }

    /**
     * Get count of unsynced workouts
     */
    suspend fun getUnsyncedCount(): Int {
        return workoutRepository.getUnsyncedCount()
    }

    /**
     * Queue sync for unsynced workouts
     */
    suspend fun queueSync(): Result<Unit> {
        return try {
            workoutRepository.queueSync()
            Timber.d("Sync queued successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to queue sync")
            Result.failure(e)
        }
    }

    /**
     * Force immediate sync
     */
    suspend fun syncNow(): Result<Unit> {
        return try {
            workoutRepository.syncNow()
            Timber.d("Immediate sync initiated")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initiate immediate sync")
            Result.failure(e)
        }
    }

    /**
     * Cancel any pending sync operations
     */
    fun cancelSync() {
        workManager.cancelUniqueWork(WorkoutSyncWorker.WORK_NAME)
        Timber.d("Sync operations cancelled")
    }
}

/**
 * Represents the current sync status
 */
sealed class SyncStatus {
    object Idle : SyncStatus()
    object Syncing : SyncStatus()
    data class Success(val syncedCount: Int) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
} 