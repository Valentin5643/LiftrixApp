package com.example.liftrix.service

import com.example.liftrix.data.local.dao.BlockedUserDao
import com.example.liftrix.data.local.entity.BlockedUserEntity
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.BlockedUser
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.social.FollowRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing user blocking relationships.
 * Part of social privacy and moderation system from SPEC-20250116-social-privacy-moderation.
 * 
 * This service handles block/unblock operations, including Firebase sync and
 * automatic cleanup of follow relationships when blocking occurs.
 */
@Singleton
class BlockingService @Inject constructor(
    private val blockedUserDao: BlockedUserDao,
    private val followRepository: FollowRepository,
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) {

    /**
     * Block a user and sync to Firebase.
     * 
     * @param targetUserId The ID of the user to block
     * @param reason Optional reason for blocking
     * @return LiftrixResult indicating success or failure
     */
    suspend fun blockUser(
        targetUserId: String,
        reason: String? = null
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "BLOCK_USER_FAILED",
                errorMessage = "Failed to block user: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "BLOCK_USER",
                    "targetUserId" to targetUserId,
                    "hasReason" to (reason != null).toString()
                )
            )
        }
    ) {
        val currentUserId = authRepository.getCurrentUserId()
            ?: throw IllegalStateException("User not authenticated")

        // Validate inputs
        if (currentUserId == targetUserId) {
            throw IllegalArgumentException("Cannot block yourself")
        }

        // Check if already blocked
        if (blockedUserDao.isUserBlocked(currentUserId, targetUserId)) {
            Timber.d("User $targetUserId is already blocked")
            return@liftrixCatching
        }

        // Create block relationship
        val blockEntity = BlockedUserEntity(
            id = UUID.randomUUID().toString(),
            userId = currentUserId,
            blockedUserId = targetUserId,
            blockedAt = System.currentTimeMillis(),
            reason = reason,
            isSynced = false
        )

        // Save locally
        blockedUserDao.insertBlockedUser(blockEntity)

        // Remove any follow relationships (bidirectional)
        try {
            // Unfollow both directions
            val unfollowResult1 = followRepository.unfollowUser(currentUserId, targetUserId)
            unfollowResult1.onFailure { exception ->
                Timber.w("Failed to unfollow $targetUserId: ${exception.message}")
            }
            
            val unfollowResult2 = followRepository.unfollowUser(targetUserId, currentUserId)
            unfollowResult2.onFailure { exception ->
                Timber.w("Failed to unfollow $currentUserId: ${exception.message}")
            }
            
            // Cancel any pending follow requests (bidirectional)
            val cancelResult1 = followRepository.cancelFollowRequest(currentUserId, targetUserId)
            cancelResult1.onFailure { exception ->
                Timber.w("Failed to cancel follow request to $targetUserId: ${exception.message}")
            }
            
            val cancelResult2 = followRepository.cancelFollowRequest(targetUserId, currentUserId)
            cancelResult2.onFailure { exception ->
                Timber.w("Failed to cancel follow request from $targetUserId: ${exception.message}")
            }
            
            Timber.d("Cleaned up follow relationships after blocking $targetUserId")
        } catch (e: Exception) {
            Timber.w("Failed to clean up follow relationships: ${e.message}")
            // Continue with blocking even if follow cleanup fails
        }

        // Sync to Firebase
        try {
            firestore.collection("blocked_users")
                .document(blockEntity.id)
                .set(blockEntity.toFirebaseMap())
                .await()

            // Mark as synced
            blockedUserDao.updateSyncStatus(blockEntity.id, true)
            
            Timber.d("Successfully synced block to Firebase: $targetUserId")
        } catch (e: Exception) {
            Timber.w("Failed to sync block to Firebase: ${e.message}")
            // Block is still saved locally, sync will be retried later
        }

        Timber.i("User $targetUserId blocked successfully by $currentUserId")
    }

    /**
     * Unblock a user and sync to Firebase.
     * 
     * @param targetUserId The ID of the user to unblock
     * @return LiftrixResult indicating success or failure
     */
    suspend fun unblockUser(targetUserId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "UNBLOCK_USER_FAILED",
                errorMessage = "Failed to unblock user: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "UNBLOCK_USER",
                    "targetUserId" to targetUserId
                )
            )
        }
    ) {
        val currentUserId = authRepository.getCurrentUserId()
            ?: throw IllegalStateException("User not authenticated")

        // Find block relationship
        val blockEntity = blockedUserDao.getBlockedUser(currentUserId, targetUserId)
        if (blockEntity == null) {
            Timber.d("User $targetUserId is not blocked")
            return@liftrixCatching
        }

        // Delete locally
        blockedUserDao.deleteBlockedUser(blockEntity)

        // Delete from Firebase
        try {
            firestore.collection("blocked_users")
                .document(blockEntity.id)
                .delete()
                .await()
                
            Timber.d("Successfully removed block from Firebase: $targetUserId")
        } catch (e: Exception) {
            Timber.w("Failed to remove block from Firebase: ${e.message}")
            // Block is still removed locally
        }

        Timber.i("User $targetUserId unblocked successfully by $currentUserId")
    }

    /**
     * Get list of blocked users.
     * 
     * @return LiftrixResult containing list of blocked users
     */
    suspend fun getBlockedUsers(): LiftrixResult<List<BlockedUser>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "GET_BLOCKED_USERS_FAILED",
                errorMessage = "Failed to get blocked users: ${throwable.message}",
                analyticsContext = mapOf("operation" to "GET_BLOCKED_USERS")
            )
        }
    ) {
        val currentUserId = authRepository.getCurrentUserId()
            ?: throw IllegalStateException("User not authenticated")

        blockedUserDao.getBlockedUsers(currentUserId)
            .map { it.toDomainModel() }
    }

    /**
     * Get count of blocked users.
     * 
     * @return LiftrixResult containing count of blocked users
     */
    suspend fun getBlockedUserCount(): LiftrixResult<Int> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "GET_BLOCKED_USER_COUNT_FAILED",
                errorMessage = "Failed to get blocked user count: ${throwable.message}",
                analyticsContext = mapOf("operation" to "GET_BLOCKED_USER_COUNT")
            )
        }
    ) {
        val currentUserId = authRepository.getCurrentUserId()
            ?: throw IllegalStateException("User not authenticated")

        blockedUserDao.getBlockedUserCount(currentUserId)
    }

    /**
     * Check if a user is blocked.
     * 
     * @param targetUserId The ID of the user to check
     * @return LiftrixResult containing true if user is blocked, false otherwise
     */
    suspend fun isUserBlocked(targetUserId: String): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CHECK_BLOCKED_STATUS_FAILED",
                errorMessage = "Failed to check blocked status: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "CHECK_BLOCKED_STATUS",
                    "targetUserId" to targetUserId
                )
            )
        }
    ) {
        val currentUserId = authRepository.getCurrentUserId()
            ?: return@liftrixCatching false // Not authenticated, treat as not blocked

        blockedUserDao.isUserBlocked(currentUserId, targetUserId)
    }

    /**
     * Check if there's any block relationship (bidirectional).
     * 
     * @param targetUserId The ID of the user to check
     * @return LiftrixResult containing true if any block relationship exists
     */
    suspend fun hasBlockRelationship(targetUserId: String): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CHECK_BLOCK_RELATIONSHIP_FAILED",
                errorMessage = "Failed to check block relationship: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "CHECK_BLOCK_RELATIONSHIP",
                    "targetUserId" to targetUserId
                )
            )
        }
    ) {
        val currentUserId = authRepository.getCurrentUserId()
            ?: return@liftrixCatching false // Not authenticated, treat as no relationship

        blockedUserDao.hasBlockRelationship(currentUserId, targetUserId)
    }

    /**
     * Filter a list of user IDs to remove blocked users.
     * 
     * @param userIds List of user IDs to filter
     * @return LiftrixResult containing filtered list without blocked users
     */
    suspend fun filterBlockedUsers(userIds: List<String>): LiftrixResult<List<String>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "FILTER_BLOCKED_USERS_FAILED",
                errorMessage = "Failed to filter blocked users: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "FILTER_BLOCKED_USERS",
                    "userCount" to userIds.size.toString()
                )
            )
        }
    ) {
        val currentUserId = authRepository.getCurrentUserId()
            ?: return@liftrixCatching userIds // Not authenticated, return all

        if (userIds.isEmpty()) return@liftrixCatching emptyList()

        // Get blocked user IDs
        val blockedUserIds = blockedUserDao.getBlockedUserIds(currentUserId).toSet()
        val usersWhoBlockedMe = blockedUserDao.getUsersWhoBlockedMe(currentUserId).toSet()

        // Filter out blocked users and users who blocked current user
        userIds.filter { userId ->
            userId != currentUserId && 
            userId !in blockedUserIds && 
            userId !in usersWhoBlockedMe
        }
    }

    /**
     * Sync unsynced blocks to Firebase.
     * Called periodically by sync service.
     */
    suspend fun syncUnsyncedBlocks(): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "SYNC_BLOCKS_FAILED",
                errorMessage = "Failed to sync blocks: ${throwable.message}",
                analyticsContext = mapOf("operation" to "SYNC_BLOCKS")
            )
        }
    ) {
        val currentUserId = authRepository.getCurrentUserId()
            ?: return@liftrixCatching

        val unsyncedBlocks = blockedUserDao.getUnsyncedBlockedUsers(currentUserId)
        
        if (unsyncedBlocks.isEmpty()) {
            return@liftrixCatching
        }

        var syncedCount = 0
        for (block in unsyncedBlocks) {
            try {
                firestore.collection("blocked_users")
                    .document(block.id)
                    .set(block.toFirebaseMap())
                    .await()

                blockedUserDao.updateSyncStatus(block.id, true)
                syncedCount++
            } catch (e: Exception) {
                Timber.w("Failed to sync block ${block.id}: ${e.message}")
                // Continue with other blocks
            }
        }

        Timber.i("Synced $syncedCount of ${unsyncedBlocks.size} blocks to Firebase")
    }
}

/**
 * Extension function to convert BlockedUserEntity to domain model
 */
private fun BlockedUserEntity.toDomainModel(): BlockedUser = BlockedUser(
    id = id,
    userId = userId,
    blockedUserId = blockedUserId,
    reason = reason,
    blockedAt = blockedAt
)

/**
 * Extension function to convert BlockedUserEntity to Firebase map
 */
private fun BlockedUserEntity.toFirebaseMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "userId" to userId,
    "blockedUserId" to blockedUserId,
    "reason" to reason,
    "blockedAt" to blockedAt
)