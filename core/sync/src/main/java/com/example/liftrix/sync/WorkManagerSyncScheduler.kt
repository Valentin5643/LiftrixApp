package com.example.liftrix.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.service.CombinedSyncStatus
import com.example.liftrix.domain.sync.SyncScheduler
import com.example.liftrix.service.sync.RealtimeSyncManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext context: Context,
    private val syncCoordinator: SyncCoordinator,
    private val syncManager: SyncManager,
    private val realtimeSyncManager: RealtimeSyncManager,
    private val engagementRealtimeSyncService: EngagementRealtimeSyncService
) : SyncScheduler {

    private val workManager: WorkManager = WorkManager.getInstance(context)

    override fun schedulePeriodicSync(userId: String) {
        syncCoordinator.schedulePeriodicSync(userId)
    }

    override suspend fun triggerImmediateSync(userId: String): LiftrixResult<Unit> {
        return syncCoordinator.triggerImmediateSync(userId)
    }

    override suspend fun triggerAllSync(userId: String): LiftrixResult<Unit> {
        return syncManager.syncAllData(userId)
    }

    override suspend fun triggerAnalyticsSync(userId: String): LiftrixResult<Unit> {
        return syncManager.syncAnalyticsNow(userId)
    }

    override suspend fun triggerEntitySync(userId: String, entityType: String): LiftrixResult<Unit> {
        return syncCoordinator.triggerEntitySync(userId, entityType)
    }

    override suspend fun triggerStartupSync(
        userId: String,
        source: String,
        force: Boolean
    ): LiftrixResult<Unit> {
        return syncCoordinator.triggerStartupSync(
            userId = userId,
            source = source,
            force = force
        )
    }

    override suspend fun forceFullResync(userId: String): LiftrixResult<Unit> {
        return syncCoordinator.forceFullResync(userId)
    }

    override fun cancelSyncForUser(userId: String) {
        syncCoordinator.cancelSyncForUser(userId)
    }

    override fun cancelAllSync() {
        syncManager.cancelSync()
        syncManager.cancelAnalyticsSync()
        workManager.cancelAllWork()
    }

    override fun observeWorkoutSyncStatus(): Flow<com.example.liftrix.domain.service.SyncStatus> {
        return syncManager.getSyncStatus().map { it.toDomainSyncStatus() }
    }

    override fun observeAnalyticsSyncStatus(): Flow<com.example.liftrix.domain.service.SyncStatus> {
        return syncManager.getAnalyticsSyncStatus().map { it.toDomainSyncStatus() }
    }

    override fun observeCombinedSyncStatus(): Flow<CombinedSyncStatus> {
        return syncManager.getCombinedSyncStatus().map { status ->
            CombinedSyncStatus(
                workoutStatus = status.workoutStatus.toDomainSyncStatus(),
                analyticsStatus = status.analyticsStatus.toDomainSyncStatus()
            )
        }
    }

    override suspend fun getUnsyncedWorkoutCount(userId: String): Int {
        return syncManager.getUnsyncedCount(userId)
    }

    override suspend fun getUnsyncedAnalyticsCount(userId: String): Int {
        return syncManager.getUnsyncedAnalyticsCount(userId)
    }

    override fun enqueueUserPublicSync(userId: String, forceSync: Boolean) {
        workManager.enqueue(UserPublicSyncWorker.createWorkRequest(userId, forceSync))
        Timber.d("Queued user public sync for user $userId")
    }

    override fun enqueueSocialProfileSync(userId: String, forceSync: Boolean) {
        workManager.enqueue(SocialProfileSyncWorker.createWorkRequest(userId, forceSync))
        Timber.d("Queued social profile sync for user $userId")
    }

    override fun enqueueFollowRelationshipSync(
        userId: String,
        forceSync: Boolean,
        restoreFromFirebase: Boolean
    ) {
        val request = if (restoreFromFirebase) {
            FollowRelationshipSyncWorker.createRestoreWorkRequest(userId)
        } else {
            FollowRelationshipSyncWorker.createWorkRequest(
                userId = userId,
                forceSync = forceSync,
                restoreFromFirebase = false
            )
        }
        workManager.enqueue(request)
        Timber.d("Queued follow relationship sync for user $userId")
    }

    override fun enqueueWorkoutPostSync(userId: String, forceSync: Boolean) {
        workManager.enqueue(WorkoutPostSyncWorker.createWorkRequest(userId, forceSync))
        Timber.d("Queued workout post sync for user $userId")
    }

    override fun enqueueGymBuddySync(userId: String, forceSync: Boolean) {
        workManager.enqueue(GymBuddySyncWorker.createWorkRequest(userId, forceSync))
        Timber.d("Queued gym buddy sync for user $userId")
    }

    override fun enqueueAchievementSync(userId: String) {
        workManager.enqueueUniqueWork(
            "${AchievementSyncWorker.WORK_NAME}_$userId",
            ExistingWorkPolicy.REPLACE,
            AchievementSyncWorker.createWorkRequest(userId)
        )
        Timber.d("Queued achievement sync for user $userId")
    }

    override fun enqueueProfileSync(userId: String, forceSync: Boolean) {
        workManager.enqueueUniqueWork(
            ProfileSyncWorker.getWorkName(userId),
            ExistingWorkPolicy.REPLACE,
            ProfileSyncWorker.createWorkRequest(userId, forceSync)
        )
        Timber.d("Queued profile sync for user $userId")
    }

    override fun enqueueSettingsSync(userId: String, forceSync: Boolean) {
        workManager.enqueue(SettingsSyncWorkerV2.createWorkRequest(userId, forceSync))
        Timber.d("Queued settings sync for user $userId")
    }

    override suspend fun startRealtimeSync(userId: String): LiftrixResult<Unit> {
        return try {
            realtimeSyncManager.startRealtimeSync(userId)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start realtime sync for user $userId")
            Result.failure(e)
        }
    }

    override suspend fun stopRealtimeSync(userId: String): LiftrixResult<Unit> {
        return try {
            realtimeSyncManager.stopRealtimeSync(userId)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop realtime sync for user $userId")
            Result.failure(e)
        }
    }

    override suspend fun forceRealtimeSyncAll(userId: String): LiftrixResult<Unit> {
        return realtimeSyncManager.forceSyncAll(userId)
    }

    override suspend fun startPostEngagementSync(postId: String): LiftrixResult<Unit> {
        return try {
            engagementRealtimeSyncService.startListeningToPost(postId)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start engagement sync for post $postId")
            Result.failure(e)
        }
    }

    private fun SyncStatus.toDomainSyncStatus(): com.example.liftrix.domain.service.SyncStatus =
        when (this) {
            SyncStatus.Idle -> com.example.liftrix.domain.service.SyncStatus.Idle
            SyncStatus.Syncing -> com.example.liftrix.domain.service.SyncStatus.Syncing
            is SyncStatus.Success -> com.example.liftrix.domain.service.SyncStatus.Success(syncedCount)
            is SyncStatus.AnalyticsSuccess ->
                com.example.liftrix.domain.service.SyncStatus.AnalyticsSuccess(syncedCount, conflictCount)
            is SyncStatus.Error -> com.example.liftrix.domain.service.SyncStatus.Error(message)
        }
}
