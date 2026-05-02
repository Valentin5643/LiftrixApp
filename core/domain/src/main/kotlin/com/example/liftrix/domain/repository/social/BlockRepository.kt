package com.example.liftrix.domain.repository.social

import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing user blocks
 */
interface BlockRepository {
    
    /**
     * Block a user
     * 
     * @param blockerId The user performing the block
     * @param blockedUserId The user being blocked
     */
    suspend fun blockUser(blockerId: String, blockedUserId: String): LiftrixResult<Unit>
    
    /**
     * Unblock a user
     * 
     * @param blockerId The user performing the unblock
     * @param blockedUserId The user being unblocked
     */
    suspend fun unblockUser(blockerId: String, blockedUserId: String): LiftrixResult<Unit>
    
    /**
     * Check if a user is blocked
     * 
     * @param blockerId The user who might have blocked
     * @param blockedUserId The user who might be blocked
     * @return True if blocked, false otherwise
     */
    suspend fun isBlocked(blockerId: String, blockedUserId: String): Boolean
    
    /**
     * Get list of blocked users
     * 
     * @param userId The user whose block list to retrieve
     * @return Flow of blocked user IDs
     */
    fun getBlockedUsers(userId: String): Flow<List<String>>
}