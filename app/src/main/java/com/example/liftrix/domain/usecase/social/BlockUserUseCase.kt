package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.social.BlockRepository
import com.example.liftrix.domain.repository.social.FollowRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for blocking/unblocking users
 * 
 * Features:
 * - Block/unblock user actions
 * - Automatic unfollow when blocking
 * - Privacy enforcement after blocking
 * - Analytics tracking for moderation
 */
class BlockUserUseCase @Inject constructor(
    private val blockRepository: BlockRepository,
    private val followRepository: FollowRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {
    
    /**
     * Block or unblock a user
     * 
     * @param targetUserId The user to block/unblock
     * @param shouldBlock True to block, false to unblock
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(
        targetUserId: String,
        shouldBlock: Boolean
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = if (shouldBlock) "BLOCK_USER_FAILED" else "UNBLOCK_USER_FAILED",
                errorMessage = "Failed to ${if (shouldBlock) "block" else "unblock"} user",
                analyticsContext = mapOf(
                    "target_user_id" to targetUserId,
                    "action" to if (shouldBlock) "BLOCK" else "UNBLOCK",
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        // Get current user ID
        val currentUserId = getCurrentUserIdUseCase()
            ?: throw IllegalStateException("User not authenticated")
        
        // Validate not blocking self
        if (currentUserId == targetUserId) {
            throw IllegalArgumentException("Cannot block yourself")
        }
        
        if (shouldBlock) {
            // When blocking, first unfollow in both directions
            try {
                followRepository.unfollowUser(currentUserId, targetUserId)
                followRepository.unfollowUser(targetUserId, currentUserId)
            } catch (e: Exception) {
                Timber.w(e, "Failed to unfollow during block, continuing with block")
            }
            
            // Block the user
            blockRepository.blockUser(currentUserId, targetUserId)
            
            Timber.d("User blocked: $targetUserId by $currentUserId")
        } else {
            // Unblock the user
            blockRepository.unblockUser(currentUserId, targetUserId)
            
            Timber.d("User unblocked: $targetUserId by $currentUserId")
        }
        
        Unit
    }
}