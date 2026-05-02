package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow

sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Syncing : SyncStatus()
    data class Success(val syncedCount: Int = 0) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

data class CombinedSyncStatus(
    val workoutStatus: SyncStatus = SyncStatus.Idle,
    val analyticsStatus: SyncStatus = SyncStatus.Idle
)

interface ProfileSyncService {
    fun observeCombinedSyncStatus(): Flow<CombinedSyncStatus>
    suspend fun getUnsyncedCount(userId: String): Int
    suspend fun triggerProfileSync(userId: String): LiftrixResult<Unit>
    suspend fun triggerImmediateSync(userId: String): LiftrixResult<Unit>
}
