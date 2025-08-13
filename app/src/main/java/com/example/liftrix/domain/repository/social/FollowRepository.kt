package com.example.liftrix.domain.repository.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.social.FollowRelationship
import com.example.liftrix.domain.model.social.FollowStatus
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for follow/unfollow operations and relationship management.
 * Part of user profiles and follow system from SPEC-20250113-user-profiles-follow.
 * 
 * Responsibilities:
 * - Follow request management (send, accept, decline, cancel)
 * - Follow relationship validation and privacy checks
 * - Mutual connection detection and analytics
 * - Real-time follow status updates via Flow
 * 
 * Security: All operations include viewer context validation at repository level
 */
interface FollowRepository {

    // ========================================
    // Follow Request Management
    // ========================================

    /**
     * Send follow request to another user.
     * - For public profiles: Creates immediate ACCEPTED relationship
     * - For private profiles: Creates PENDING relationship and sends notification
     */
    suspend fun sendFollowRequest(
        followerId: String,
        targetUserId: String,
        requestSource: String = "PROFILE_VIEW",
        requestMessage: String? = null
    ): LiftrixResult<FollowStatus>

    /**
     * Accept a pending follow request
     */
    suspend fun acceptFollowRequest(
        targetUserId: String,
        requesterId: String
    ): LiftrixResult<FollowStatus>

    /**
     * Decline a pending follow request
     */
    suspend fun declineFollowRequest(
        targetUserId: String,
        requesterId: String
    ): LiftrixResult<Unit>

    /**
     * Cancel a sent follow request (before acceptance)
     */
    suspend fun cancelFollowRequest(
        followerId: String,
        targetUserId: String
    ): LiftrixResult<Unit>

    /**
     * Unfollow a user (removes ACCEPTED relationship)
     */
    suspend fun unfollowUser(
        followerId: String,
        targetUserId: String
    ): LiftrixResult<Unit>

    // ========================================
    // Follow Status & Relationship Queries
    // ========================================

    /**
     * Get current follow status between two users
     */
    suspend fun getFollowStatus(
        followerId: String,
        targetUserId: String
    ): LiftrixResult<FollowStatus?>

    /**
     * Get specific follow relationship with metadata
     */
    suspend fun getFollowRelationship(
        followerId: String,
        targetUserId: String
    ): LiftrixResult<FollowRelationship?>

    /**
     * Check if two users mutually follow each other
     */
    suspend fun areMutuallyFollowing(
        userId1: String,
        userId2: String
    ): LiftrixResult<Boolean>

    // ========================================
    // Follow Lists & Counts
    // ========================================

    /**
     * Get list of users that the given user is following
     */
    suspend fun getFollowing(
        userId: String,
        limit: Int = 100,
        offset: Int = 0
    ): LiftrixResult<List<FollowRelationship>>

    /**
     * Get list of users following the given user
     */
    suspend fun getFollowers(
        userId: String,
        limit: Int = 100,
        offset: Int = 0
    ): LiftrixResult<List<FollowRelationship>>

    /**
     * Get pending follow requests for a user (received requests)
     */
    suspend fun getPendingFollowRequests(
        userId: String
    ): LiftrixResult<List<FollowRelationship>>

    /**
     * Get sent follow requests that are still pending
     */
    suspend fun getSentFollowRequests(
        userId: String
    ): LiftrixResult<List<FollowRelationship>>

    /**
     * Get follow counts (followers, following, pending)
     */
    suspend fun getFollowCounts(
        userId: String
    ): LiftrixResult<FollowCounts>

    // ========================================
    // Reactive Follow Observables
    // ========================================

    /**
     * Observe follow relationships in real-time
     */
    fun observeFollowing(userId: String): Flow<List<FollowRelationship>>

    /**
     * Observe followers in real-time
     */
    fun observeFollowers(userId: String): Flow<List<FollowRelationship>>

    /**
     * Observe pending request count
     */
    fun observePendingRequestCount(userId: String): Flow<Int>

    // ========================================
    // Blocking & Privacy
    // ========================================

    /**
     * Block a user (prevents them from following or viewing profile)
     */
    suspend fun blockUser(
        blockerId: String,
        targetUserId: String
    ): LiftrixResult<Unit>

    /**
     * Unblock a previously blocked user
     */
    suspend fun unblockUser(
        blockerId: String,
        targetUserId: String
    ): LiftrixResult<Unit>

    /**
     * Check if user is blocked by another user
     */
    suspend fun isUserBlocked(
        userId: String,
        potentialBlockerId: String
    ): LiftrixResult<Boolean>

    // ========================================
    // Analytics & Suggestions
    // ========================================

    /**
     * Calculate mutual connections between two users
     */
    suspend fun calculateMutualConnections(
        userId1: String,
        userId2: String
    ): LiftrixResult<Int>

    /**
     * Get suggested users based on mutual connections
     */
    suspend fun getSuggestedUsers(
        userId: String,
        limit: Int = 20
    ): LiftrixResult<List<String>>

    /**
     * Track profile view for follow suggestions
     */
    suspend fun trackProfileView(
        viewerId: String,
        profileId: String,
        viewSource: String,
        viewDurationMs: Long? = null
    ): LiftrixResult<Unit>

    /**
     * Cleanup expired follow requests
     */
    suspend fun cleanupExpiredRequests(): LiftrixResult<Int>
}

/**
 * Data class representing follow counts for a user
 */
data class FollowCounts(
    val followersCount: Int,
    val followingCount: Int,
    val pendingRequestsCount: Int,
    val sentRequestsCount: Int
)

/**
 * Enum representing different follow actions
 */
enum class FollowAction {
    FOLLOW,
    UNFOLLOW,
    ACCEPT,
    DECLINE,
    CANCEL,
    BLOCK,
    UNBLOCK
}