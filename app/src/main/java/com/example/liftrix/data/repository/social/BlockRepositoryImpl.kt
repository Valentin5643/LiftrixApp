package com.example.liftrix.data.repository.social

import com.example.liftrix.data.local.dao.BlockedUserDao
import com.example.liftrix.data.local.entity.BlockedUserEntity
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.social.BlockRepository
import com.google.firebase.firestore.FirebaseFirestore
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
    private val firestore: FirebaseFirestore
) : BlockRepository {
    
    companion object {
        private const val BLOCKS_COLLECTION = "user_blocks"
    }
    
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
        
        blockedUserDao.insertBlockedUser(blockEntity)
        
        // Sync to Firebase
        try {
            val blockData = mapOf(
                "id" to blockId,
                "blockerId" to blockerId,
                "blockedUserId" to blockedUserId,
                "blockedAt" to currentTime
            )
            
            firestore.collection(BLOCKS_COLLECTION)
                .document(blockId)
                .set(blockData)
                .await()
            
            // Mark as synced
            blockedUserDao.updateSyncStatus(blockId, true)
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync block to Firebase, but local block saved")
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
        blockedUserDao.unblockUser(blockerId, blockedUserId)
        
        // Remove from Firebase
        try {
            val query = firestore.collection(BLOCKS_COLLECTION)
                .whereEqualTo("blockerId", blockerId)
                .whereEqualTo("blockedUserId", blockedUserId)
                .get()
                .await()
            
            for (document in query.documents) {
                document.reference.delete().await()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync unblock to Firebase, but local unblock completed")
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