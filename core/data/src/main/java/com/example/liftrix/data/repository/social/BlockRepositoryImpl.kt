package com.example.liftrix.data.repository.social

import com.example.liftrix.data.local.dao.BlockedUserDao
import com.example.liftrix.data.local.entity.BlockedUserEntity
import com.example.liftrix.config.OfflineArchitectureFlags
import com.example.liftrix.data.remote.legacy.LegacyBlockFirestoreDataSource
import com.example.liftrix.data.sync.OfflineQueueManager
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.social.BlockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of BlockRepository for managing user blocks
 */
@Singleton
class BlockRepositoryImpl @Inject constructor(
    private val blockedUserDao: BlockedUserDao,
    private val legacyDataSource: LegacyBlockFirestoreDataSource,
    private val offlineQueueManager: OfflineQueueManager
) : BlockRepository {
    
    override suspend fun blockUser(
        blockerId: String,
        blockedUserId: String
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to block user: ${throwable.message}",
                isRecoverable = true
            )
        }
    ) {
        val blockId = UUID.randomUUID().toString()
        val currentTime = System.currentTimeMillis()
        
        // Save to local database
        val blockEntity = BlockedUserEntity(
            id = blockId,
            userId = blockerId,
            blockedUserId = blockedUserId,
            blockedAt = currentTime,
            isSynced = false
        )

        if (OfflineArchitectureFlags.FIX_BLOCK_REPOSITORY) {
            blockedUserDao.upsertLocal(blockEntity)
            offlineQueueManager.queueSocialMutation(blockerId, "BLOCKED_USER", blockId, "CREATE").getOrThrow()
        } else {
            blockedUserDao.insertBlockedUser(blockEntity)
            try {
                legacyDataSource.blockUser(blockId, blockerId, blockedUserId, currentTime)
                blockedUserDao.updateSyncStatus(blockId, true)
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync block to Firebase, but local block saved")
            }
        }
        
        Unit
    }
    
    override suspend fun unblockUser(
        blockerId: String,
        blockedUserId: String
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to unblock user: ${throwable.message}",
                isRecoverable = true
            )
        }
    ) {
        // Remove from local database
        val block = blockedUserDao.getBlockedUser(blockerId, blockedUserId)
        if (block != null) {
            blockedUserDao.tombstone(blockerId, blockedUserId, System.currentTimeMillis())
            offlineQueueManager.queueSocialMutation(blockerId, "BLOCKED_USER", block.id, "DELETE").getOrThrow()
        }

        if (!OfflineArchitectureFlags.FIX_BLOCK_REPOSITORY) {
            try {
                legacyDataSource.unblockUser(blockerId, blockedUserId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync unblock to Firebase, but local unblock completed")
            }
        }
        
        Unit
    }
    
    override suspend fun isBlocked(blockerId: String, blockedUserId: String): Boolean {
        return blockedUserDao.isUserBlocked(blockerId, blockedUserId)
    }
    
    override fun getBlockedUsers(userId: String): Flow<List<String>> {
        return blockedUserDao.observeBlockedUserIds(userId)
    }
}
