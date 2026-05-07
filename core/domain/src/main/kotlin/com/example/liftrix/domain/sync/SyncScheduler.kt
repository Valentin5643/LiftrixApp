package com.example.liftrix.domain.sync

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.service.CombinedSyncStatus
import com.example.liftrix.domain.service.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Domain boundary for scheduling background sync work.
 *
 * Data repositories request sync through this port so concrete WorkManager
 * workers remain owned by the sync module.
 */
interface SyncScheduler {
    fun schedulePeriodicSync(userId: String)

    suspend fun triggerImmediateSync(userId: String): LiftrixResult<Unit>

    suspend fun triggerAllSync(userId: String): LiftrixResult<Unit>

    suspend fun triggerAnalyticsSync(userId: String): LiftrixResult<Unit>

    suspend fun triggerEntitySync(userId: String, entityType: String): LiftrixResult<Unit>

    suspend fun triggerStartupSync(
        userId: String,
        source: String,
        force: Boolean = false
    ): LiftrixResult<Unit>

    suspend fun forceFullResync(userId: String): LiftrixResult<Unit>

    fun cancelSyncForUser(userId: String)

    fun cancelAllSync()

    fun observeWorkoutSyncStatus(): Flow<SyncStatus>

    fun observeAnalyticsSyncStatus(): Flow<SyncStatus>

    fun observeCombinedSyncStatus(): Flow<CombinedSyncStatus>

    suspend fun getUnsyncedWorkoutCount(userId: String): Int

    suspend fun getUnsyncedAnalyticsCount(userId: String): Int

    fun enqueueUserPublicSync(userId: String, forceSync: Boolean = false)

    fun enqueueSocialProfileSync(userId: String, forceSync: Boolean = false)

    fun enqueueFollowRelationshipSync(
        userId: String,
        forceSync: Boolean = false,
        restoreFromFirebase: Boolean = false
    )

    fun enqueueWorkoutPostSync(userId: String, forceSync: Boolean = false)

    fun enqueueGymBuddySync(userId: String, forceSync: Boolean = false)

    fun enqueueAchievementSync(userId: String)

    fun enqueueProfileSync(userId: String, forceSync: Boolean = false)

    fun enqueueSettingsSync(userId: String, forceSync: Boolean = false)

    suspend fun startRealtimeSync(userId: String): LiftrixResult<Unit>

    suspend fun stopRealtimeSync(userId: String): LiftrixResult<Unit>

    suspend fun forceRealtimeSyncAll(userId: String): LiftrixResult<Unit>

    suspend fun startPostEngagementSync(postId: String): LiftrixResult<Unit>
}
