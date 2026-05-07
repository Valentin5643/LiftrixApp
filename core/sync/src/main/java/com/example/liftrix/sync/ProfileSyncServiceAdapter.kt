package com.example.liftrix.sync

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.service.CombinedSyncStatus
import com.example.liftrix.domain.service.ProfileSyncService
import com.example.liftrix.domain.service.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileSyncServiceAdapter @Inject constructor(
    private val syncCoordinator: SyncCoordinator,
    private val syncManager: SyncManager
) : ProfileSyncService {

    override fun observeCombinedSyncStatus(): Flow<CombinedSyncStatus> =
        syncManager.getCombinedSyncStatus().map { status ->
            CombinedSyncStatus(
                workoutStatus = status.workoutStatus.toDomainStatus(),
                analyticsStatus = status.analyticsStatus.toDomainStatus()
            )
        }

    override suspend fun getUnsyncedCount(userId: String): Int =
        syncManager.getUnsyncedCount(userId)

    override suspend fun triggerProfileSync(userId: String): LiftrixResult<Unit> =
        syncCoordinator.triggerEntitySync(userId, "profile")

    override suspend fun triggerImmediateSync(userId: String): LiftrixResult<Unit> =
        syncCoordinator.triggerImmediateSync(userId)

    private fun com.example.liftrix.sync.SyncStatus.toDomainStatus(): SyncStatus =
        when (this) {
            com.example.liftrix.sync.SyncStatus.Idle -> SyncStatus.Idle
            com.example.liftrix.sync.SyncStatus.Syncing -> SyncStatus.Syncing
            is com.example.liftrix.sync.SyncStatus.Success -> SyncStatus.Success(syncedCount)
            is com.example.liftrix.sync.SyncStatus.AnalyticsSuccess -> SyncStatus.Success(syncedCount)
            is com.example.liftrix.sync.SyncStatus.Error -> SyncStatus.Error(message)
        }
}
