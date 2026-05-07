package com.example.liftrix.data.repository.social

import com.example.liftrix.data.local.dao.FollowRelationshipDao
import com.example.liftrix.data.local.dao.FollowRequestDao
import com.example.liftrix.data.local.dao.ProfileViewDao
import com.example.liftrix.data.local.dao.SocialProfileDao
import com.example.liftrix.data.local.dao.BlockedUserDao
import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.data.local.dao.EnrichedFollowRelationship
import com.example.liftrix.data.local.dao.SafeFollowRelationshipDaoImpl
import com.example.liftrix.data.local.entity.FollowRelationshipEntity
import com.example.liftrix.data.local.entity.FollowRequestEntity
import com.example.liftrix.data.local.entity.ProfileViewEntity
import com.example.liftrix.data.local.entity.SocialProfileEntity
import com.example.liftrix.data.local.entity.UserProfileEntity
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.FollowRelationship
import com.example.liftrix.domain.model.social.ConnectionStatus
import com.example.liftrix.domain.model.social.FollowStatus
import com.example.liftrix.domain.repository.social.FollowRepository
import com.example.liftrix.domain.repository.social.FollowCounts
import com.example.liftrix.config.OfflineArchitectureFlags
import com.example.liftrix.data.remote.legacy.LegacyFollowFirestoreDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of FollowRepository with comprehensive follow/unfollow functionality.
 * Part of user profiles and follow system from SPEC-20250113-user-profiles-follow.
 * 
 * Features:
 * - Follow request management for private/public profiles
 * - Real-time relationship status tracking
 * - Privacy-aware operations with viewer context validation
 * - Mutual connection calculation and analytics
 * - Profile view tracking for suggestions
 * - Request expiration and cleanup
 * - Firebase sync with conflict resolution
 * - Comprehensive error handling and recovery
 */
@Singleton
class FollowRepositoryImpl @Inject constructor(
    private val followRelationshipDao: FollowRelationshipDao,
    private val followRequestDao: FollowRequestDao,
    private val profileViewDao: ProfileViewDao,
    private val socialProfileDao: SocialProfileDao,
    private val blockedUserDao: BlockedUserDao,
    private val userProfileDao: UserProfileDao,
    private val userAccountDao: com.example.liftrix.data.local.dao.UserAccountDao,
    private val safeFollowDao: SafeFollowRelationshipDaoImpl,
    private val legacyDataSource: LegacyFollowFirestoreDataSource
) : FollowRepository {

    companion object {
        private const val REQUEST_EXPIRATION_DAYS = 30L
    }

    // ========================================
    // Follow Request Management
    // ========================================

    override suspend fun sendFollowRequest(
        followerId: String,
        targetUserId: String,
        requestSource: String,
        requestMessage: String?
    ): LiftrixResult<FollowStatus> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.NetworkError(
                    errorMessage = "Failed to send follow request: ${throwable.message}",
                    isRecoverable = true,
                    analyticsContext = mapOf("operation" to "SEND_FOLLOW_REQUEST")
                )
            }
        ) {
            Timber.d("Sending follow request: follower=$followerId, target=$targetUserId, source=$requestSource")
            
            // Validate inputs
            if (followerId == targetUserId) {
                throw IllegalArgumentException("Cannot follow yourself")
            }
            
            // Check if user is blocked
            val isBlocked = blockedUserDao.isUserBlocked(targetUserId, followerId)
            if (isBlocked) {
                throw IllegalArgumentException("Cannot follow blocked user")
            }
            
            // Check existing relationship
            val existingRelationship = followRelationshipDao.getFollowRelationship(followerId, targetUserId)
            if (existingRelationship != null) {
                return@liftrixCatching when (existingRelationship.status) {
                    FollowRelationshipEntity.STATUS_ACCEPTED -> FollowStatus.FOLLOWING
                    FollowRelationshipEntity.STATUS_PENDING -> FollowStatus.PENDING_SENT
                    else -> throw IllegalStateException("Invalid existing relationship status")
                }
            }
            
            // Check existing pending request
            val existingRequest = followRequestDao.getFollowRequest(followerId, targetUserId)
            if (existingRequest?.status == FollowRequestEntity.STATUS_PENDING) {
                return@liftrixCatching FollowStatus.PENDING_SENT
            }
            
            // Get target user's profile privacy - try local first, then Firebase fallback
            var targetProfile = socialProfileDao.getSocialProfileByUserId(targetUserId)
            
            if (targetProfile == null) {
                Timber.w("Social profile not found locally for user $targetUserId, attempting Firebase fallback")
                targetProfile = fetchAndCreateSocialProfileFromFirebase(targetUserId)
                    ?: throw IllegalArgumentException("Target user profile not found in local DB or Firebase")
            }
            
            val currentTime = System.currentTimeMillis()
            val relationshipId = UUID.randomUUID().toString()
            
            if (targetProfile.isPrivate) {
                // Private profile - create pending request
                val requestId = UUID.randomUUID().toString()
                val expiresAt = currentTime + (REQUEST_EXPIRATION_DAYS * 24 * 60 * 60 * 1000L)
                
                val followRequest = FollowRequestEntity(
                    id = requestId,
                    requesterId = followerId,
                    targetId = targetUserId,
                    status = FollowRequestEntity.STATUS_PENDING,
                    requestMessage = requestMessage,
                    createdAt = currentTime,
                    expiresAt = expiresAt,
                    requestSource = requestSource,
                    updatedAt = currentTime
                )
                
                // Insert pending request
                followRequestDao.insertFollowRequest(followRequest)
                
                // Create pending relationship
                val relationship = FollowRelationshipEntity(
                    id = relationshipId,
                    followerId = followerId,
                    followingId = targetUserId,
                    status = FollowRelationshipEntity.STATUS_PENDING,
                    createdAt = currentTime
                )
                
                // Use safe insert method that handles user validation automatically
                safeFollowDao.insertFollowRelationshipsWithUserValidation(
                    relationships = listOf(relationship),
                    markDirty = true
                )
                
                // Sync to Firebase
                syncFollowRequestToFirebase(followRequest)
                syncRelationshipToFirebase(relationship)
                
                FollowStatus.PENDING_SENT
            } else {
                // Public profile - immediate acceptance
                val relationship = FollowRelationshipEntity(
                    id = relationshipId,
                    followerId = followerId,
                    followingId = targetUserId,
                    status = FollowRelationshipEntity.STATUS_ACCEPTED,
                    createdAt = currentTime,
                    acceptedAt = currentTime
                )
                
                // Use safe insert method that handles user validation automatically
                safeFollowDao.insertFollowRelationshipsWithUserValidation(
                    relationships = listOf(relationship),
                    markDirty = true
                )
                
                // Update follower counts
                updateFollowerCounts(followerId, targetUserId, isFollow = true)
                
                // Sync to Firebase
                syncRelationshipToFirebase(relationship)
                
                FollowStatus.FOLLOWING
            }
        }
    }

    override suspend fun acceptFollowRequest(
        targetUserId: String,
        requesterId: String
    ): LiftrixResult<FollowStatus> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.NetworkError(
                    errorMessage = "Failed to accept follow request: ${throwable.message}",
                    isRecoverable = true,
                    analyticsContext = mapOf("operation" to "ACCEPT_FOLLOW_REQUEST")
                )
            }
        ) {
            Timber.d("Accepting follow request: requester=$requesterId, target=$targetUserId")
            
            val currentTime = System.currentTimeMillis()
            
            // Update request status
            val updatedRows = followRequestDao.acceptRequest(requesterId, targetUserId, currentTime, currentTime)
            if (updatedRows == 0) {
                throw IllegalArgumentException("No pending request found")
            }
            
            // Update relationship status
            followRelationshipDao.updateFollowStatus(
                followerId = requesterId,
                followingId = targetUserId,
                status = FollowRelationshipEntity.STATUS_ACCEPTED,
                acceptedAt = currentTime
            )
            followRelationshipDao.getFollowRelationship(requesterId, targetUserId)?.let { updated ->
                followRelationshipDao.upsertLocal(updated)
            }
            
            // Update follower counts
            updateFollowerCounts(requesterId, targetUserId, isFollow = true)
            
            // Get updated relationship for sync
            val relationship = followRelationshipDao.getFollowRelationship(requesterId, targetUserId)
            relationship?.let { syncRelationshipToFirebase(it) }
            
            // Check if this creates a mutual follow relationship
            val isMutual = followRelationshipDao.areMutuallyFollowing(targetUserId, requesterId)
            
            if (isMutual) {
                Timber.d("Mutual follow relationship detected between $targetUserId and $requesterId")
                FollowStatus.MUTUAL_FOLLOW
            } else {
                FollowStatus.FOLLOWING
            }
        }
    }

    override suspend fun declineFollowRequest(
        targetUserId: String,
        requesterId: String
    ): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.NetworkError(
                    errorMessage = "Failed to decline follow request: ${throwable.message}",
                    isRecoverable = true,
                    analyticsContext = mapOf("operation" to "DECLINE_FOLLOW_REQUEST")
                )
            }
        ) {
            Timber.d("Declining follow request: requester=$requesterId, target=$targetUserId")
            
            val currentTime = System.currentTimeMillis()
            
            // Update request status
            val updatedRows = followRequestDao.declineRequest(requesterId, targetUserId, currentTime, currentTime)
            if (updatedRows == 0) {
                throw IllegalArgumentException("No pending request found")
            }
            
            // Remove pending relationship
            followRelationshipDao.deleteFollowRelationship(requesterId, targetUserId)
        }
    }

    override suspend fun cancelFollowRequest(
        followerId: String,
        targetUserId: String
    ): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.NetworkError(
                    errorMessage = "Failed to cancel follow request: ${throwable.message}",
                    isRecoverable = true,
                    analyticsContext = mapOf("operation" to "CANCEL_FOLLOW_REQUEST")
                )
            }
        ) {
            Timber.d("Canceling follow request: follower=$followerId, target=$targetUserId")
            
            val currentTime = System.currentTimeMillis()
            
            // Update request status
            followRequestDao.cancelRequest(followerId, targetUserId, currentTime, currentTime)
            
            // Remove pending relationship
            followRelationshipDao.deleteFollowRelationship(followerId, targetUserId)
        }
    }

    override suspend fun unfollowUser(
        followerId: String,
        targetUserId: String
    ): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.NetworkError(
                    errorMessage = "Failed to unfollow user: ${throwable.message}",
                    isRecoverable = true,
                    analyticsContext = mapOf("operation" to "UNFOLLOW_USER")
                )
            }
        ) {
            Timber.d("Unfollowing user: follower=$followerId, target=$targetUserId")
            
            // Remove relationship
            val deletedRows = followRelationshipDao.deleteFollowRelationship(followerId, targetUserId)
            if (deletedRows == 0) {
                throw IllegalArgumentException("No follow relationship found")
            }
            
            // Update follower counts
            updateFollowerCounts(followerId, targetUserId, isFollow = false)
            
            // Sync deletion to Firebase
            syncRelationshipDeletionToFirebase(followerId, targetUserId)
        }
    }

    // ========================================
    // Follow Status & Relationship Queries
    // ========================================

    override suspend fun getFollowStatus(
        followerId: String,
        targetUserId: String
    ): LiftrixResult<FollowStatus?> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get follow status: ${throwable.message}",
                    analyticsContext = mapOf("operation" to "GET_FOLLOW_STATUS")
                )
            }
        ) {
            val statusString = followRelationshipDao.getFollowStatus(followerId, targetUserId)
            statusString?.let { 
                when (it) {
                    FollowRelationshipEntity.STATUS_PENDING -> FollowStatus.PENDING_SENT
                    FollowRelationshipEntity.STATUS_ACCEPTED -> FollowStatus.FOLLOWING
                    FollowRelationshipEntity.STATUS_BLOCKED -> FollowStatus.BLOCKED
                    else -> null
                }
            }
        }
    }

    override suspend fun getFollowRelationship(
        followerId: String,
        targetUserId: String
    ): LiftrixResult<FollowRelationship?> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get follow relationship: ${throwable.message}",
                    analyticsContext = mapOf("operation" to "GET_FOLLOW_RELATIONSHIP")
                )
            }
        ) {
            val entity = followRelationshipDao.getFollowRelationship(followerId, targetUserId)
            entity?.let { mapEntityToDomain(it) }
        }
    }

    override suspend fun areMutuallyFollowing(
        userId1: String,
        userId2: String
    ): LiftrixResult<Boolean> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to check mutual following: ${throwable.message}",
                    analyticsContext = mapOf("operation" to "ARE_MUTUALLY_FOLLOWING")
                )
            }
        ) {
            followRelationshipDao.areMutuallyFollowing(userId1, userId2)
        }
    }

    // ========================================
    // Follow Lists & Counts
    // ========================================

    override suspend fun getFollowing(
        userId: String,
        limit: Int,
        offset: Int
    ): LiftrixResult<List<FollowRelationship>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get following list: ${throwable.message}",
                    analyticsContext = mapOf("operation" to "GET_FOLLOWING")
                )
            }
        ) {
            val entities = followRelationshipDao.getFollowing(userId, FollowRelationshipEntity.STATUS_ACCEPTED, limit)
            entities.map { mapEntityToDomain(it) }
        }
    }

    override suspend fun getFollowers(
        userId: String,
        limit: Int,
        offset: Int
    ): LiftrixResult<List<FollowRelationship>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get followers list: ${throwable.message}",
                    analyticsContext = mapOf("operation" to "GET_FOLLOWERS")
                )
            }
        ) {
            val entities = followRelationshipDao.getFollowers(userId, FollowRelationshipEntity.STATUS_ACCEPTED, limit)
            entities.map { mapEntityToDomain(it) }
        }
    }

    override suspend fun getPendingFollowRequests(
        userId: String
    ): LiftrixResult<List<FollowRelationship>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get pending requests: ${throwable.message}",
                    analyticsContext = mapOf("operation" to "GET_PENDING_REQUESTS")
                )
            }
        ) {
            val entities = followRelationshipDao.getPendingFollowRequests(userId)
            entities.map { mapEntityToDomain(it) }
        }
    }

    override suspend fun getSentFollowRequests(
        userId: String
    ): LiftrixResult<List<FollowRelationship>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get sent requests: ${throwable.message}",
                    analyticsContext = mapOf("operation" to "GET_SENT_REQUESTS")
                )
            }
        ) {
            val entities = followRelationshipDao.getSentFollowRequests(userId)
            entities.map { mapEntityToDomain(it) }
        }
    }

    override suspend fun getFollowCounts(
        userId: String
    ): LiftrixResult<FollowCounts> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get follow counts: ${throwable.message}",
                    analyticsContext = mapOf("operation" to "GET_FOLLOW_COUNTS")
                )
            }
        ) {
            val followersCount = followRelationshipDao.getFollowerCount(userId)
            val followingCount = followRelationshipDao.getFollowingCount(userId)
            val pendingRequestsCount = followRelationshipDao.getPendingRequestCount(userId)
            val sentRequestsCount = followRequestDao.getSentRequestCount(userId)
            
            FollowCounts(
                followersCount = followersCount,
                followingCount = followingCount,
                pendingRequestsCount = pendingRequestsCount,
                sentRequestsCount = sentRequestsCount
            )
        }
    }

    // ========================================
    // Reactive Follow Observables
    // ========================================

    override fun observeFollowing(userId: String): Flow<List<FollowRelationship>> {
        return followRelationshipDao.observeFollowing(userId)
            .map { entities -> entities.map { mapEntityToDomain(it) } }
    }

    override fun observeFollowers(userId: String): Flow<List<FollowRelationship>> {
        return followRelationshipDao.observeFollowers(userId)
            .map { entities -> entities.map { mapEntityToDomain(it) } }
    }

    // ========================================
    // Enriched Follow Data with Profile Information
    // ========================================

    /**
     * Observes following list with enriched profile data (names, images, bio)
     * This resolves the "Unknown User" issue by joining with social_profiles table
     */
    override fun observeFollowingWithProfiles(userId: String): Flow<List<FollowRelationship>> {
        return followRelationshipDao.observeFollowingWithProfiles(userId)
            .map { enrichedList -> 
                enrichedList.map { enriched -> mapEnrichedToDomain(enriched) }
            }
    }

    /**
     * Observes followers list with enriched profile data (names, images, bio)
     * This resolves the "Unknown User" issue by joining with social_profiles table
     */
    override fun observeFollowersWithProfiles(userId: String): Flow<List<FollowRelationship>> {
        return followRelationshipDao.observeFollowersWithProfiles(userId)
            .map { enrichedList -> 
                enrichedList.map { enriched -> mapEnrichedToDomain(enriched) }
            }
    }

    override fun observePendingRequestCount(userId: String): Flow<Int> {
        return followRelationshipDao.observePendingRequestCount(userId)
    }

    // ========================================
    // Blocking & Privacy
    // ========================================

    override suspend fun blockUser(
        blockerId: String,
        targetUserId: String
    ): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.NetworkError(
                    errorMessage = "Failed to block user: ${throwable.message}",
                    isRecoverable = true,
                    analyticsContext = mapOf("operation" to "BLOCK_USER")
                )
            }
        ) {
            // Remove any existing relationships
            followRelationshipDao.deleteFollowRelationship(blockerId, targetUserId)
            followRelationshipDao.deleteFollowRelationship(targetUserId, blockerId)
            
            // Cancel any pending requests
            followRequestDao.deleteFollowRequest(blockerId, targetUserId)
            followRequestDao.deleteFollowRequest(targetUserId, blockerId)
            
            // Create block relationship using BlockedUserDao
            blockedUserDao.blockUser(blockerId, targetUserId)
        }
    }

    override suspend fun unblockUser(
        blockerId: String,
        targetUserId: String
    ): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.NetworkError(
                    errorMessage = "Failed to unblock user: ${throwable.message}",
                    isRecoverable = true,
                    analyticsContext = mapOf("operation" to "UNBLOCK_USER")
                )
            }
        ) {
            blockedUserDao.unblockUser(blockerId, targetUserId)
        }
    }

    override suspend fun isUserBlocked(
        userId: String,
        potentialBlockerId: String
    ): LiftrixResult<Boolean> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to check block status: ${throwable.message}",
                    analyticsContext = mapOf("operation" to "IS_USER_BLOCKED")
                )
            }
        ) {
            blockedUserDao.isUserBlocked(potentialBlockerId, userId)
        }
    }

    // ========================================
    // Analytics & Suggestions
    // ========================================

    override suspend fun calculateMutualConnections(
        userId1: String,
        userId2: String
    ): LiftrixResult<Int> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to calculate mutual connections: ${throwable.message}",
                    analyticsContext = mapOf("operation" to "CALCULATE_MUTUAL_CONNECTIONS")
                )
            }
        ) {
            // Get mutual following relationships
            val user1Following = followRelationshipDao.getFollowing(userId1, FollowRelationshipEntity.STATUS_ACCEPTED, 1000)
            val user2Following = followRelationshipDao.getFollowing(userId2, FollowRelationshipEntity.STATUS_ACCEPTED, 1000)
            
            val user1FollowingIds = user1Following.map { it.followingId }.toSet()
            val user2FollowingIds = user2Following.map { it.followingId }.toSet()
            
            user1FollowingIds.intersect(user2FollowingIds).size
        }
    }

    override suspend fun getSuggestedUsers(
        userId: String,
        limit: Int
    ): LiftrixResult<List<String>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get suggested users: ${throwable.message}",
                    analyticsContext = mapOf("operation" to "GET_SUGGESTED_USERS")
                )
            }
        ) {
            // Simple implementation - could be enhanced with ML algorithms
            val recentViews = profileViewDao.getRecentlyViewedUnfollowedProfiles(
                viewerId = userId,
                recentTimestamp = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L), // 7 days
                limit = limit
            )
            
            recentViews
        }
    }

    override suspend fun trackProfileView(
        viewerId: String,
        profileId: String,
        viewSource: String,
        viewDurationMs: Long?
    ): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to track profile view: ${throwable.message}",
                    analyticsContext = mapOf("operation" to "TRACK_PROFILE_VIEW")
                )
            }
        ) {
            if (viewerId != profileId) { // Don't track self-views
                val profileView = ProfileViewEntity(
                    id = UUID.randomUUID().toString(),
                    viewerId = viewerId,
                    profileId = profileId,
                    viewedAt = System.currentTimeMillis(),
                    viewSource = viewSource,
                    viewDurationMs = viewDurationMs,
                    interactionType = ProfileViewEntity.INTERACTION_NONE,
                    createdAt = System.currentTimeMillis()
                )
                
                profileViewDao.insertProfileView(profileView)
            }
        }
    }

    override suspend fun cleanupExpiredRequests(): LiftrixResult<Int> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to cleanup expired requests: ${throwable.message}",
                    analyticsContext = mapOf("operation" to "CLEANUP_EXPIRED_REQUESTS")
                )
            }
        ) {
            val currentTime = System.currentTimeMillis()
            
            // Expire old requests
            val expiredCount = followRequestDao.expireOldRequests(currentTime)
            
            // Clean up processed requests older than 30 days
            val cleanupCutoff = currentTime - (30 * 24 * 60 * 60 * 1000L)
            followRequestDao.cleanupProcessedRequests(cleanupCutoff)
            
            expiredCount
        }
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    private fun mapEntityToDomain(entity: FollowRelationshipEntity): FollowRelationship {
        return FollowRelationship(
            id = entity.id,
            followerId = entity.followerId,
            followingId = entity.followingId,
            status = when (entity.status) {
                FollowRelationshipEntity.STATUS_PENDING -> FollowStatus.PENDING_SENT
                FollowRelationshipEntity.STATUS_ACCEPTED -> FollowStatus.FOLLOWING
                FollowRelationshipEntity.STATUS_BLOCKED -> FollowStatus.BLOCKED
                else -> throw IllegalArgumentException("Unknown status: ${entity.status}")
            },
            createdAt = entity.createdAt,
            acceptedAt = entity.acceptedAt,
            blockedAt = entity.blockedAt,
            // Display properties - need to be populated from user profile data
            userId = entity.followingId, // For display purposes, show the followed user
            displayName = null, // Will be populated by repository query
            profileImageUrl = null,
            bio = null,
            location = null,
            connectionStatus = ConnectionStatus.NONE // Default, should be updated by calling code
        )
    }

    /**
     * Maps enriched follow relationship (with profile data) to domain model
     * This provides complete user information to prevent "Unknown User" display
     */
    private fun mapEnrichedToDomain(enriched: EnrichedFollowRelationship): FollowRelationship {
        return FollowRelationship(
            id = enriched.id,
            followerId = enriched.followerId,
            followingId = enriched.followingId,
            status = when (enriched.status) {
                FollowRelationshipEntity.STATUS_PENDING -> FollowStatus.PENDING_SENT
                FollowRelationshipEntity.STATUS_ACCEPTED -> FollowStatus.FOLLOWING
                FollowRelationshipEntity.STATUS_BLOCKED -> FollowStatus.BLOCKED
                else -> throw IllegalArgumentException("Unknown status: ${enriched.status}")
            },
            createdAt = enriched.createdAt,
            acceptedAt = enriched.acceptedAt,
            blockedAt = enriched.blockedAt,
            // Enriched display properties from social_profiles join
            userId = enriched.followingId, // For display purposes, show the followed user
            displayName = enriched.profileDisplayName ?: "Unknown User", // Fallback for missing profiles
            profileImageUrl = enriched.profileImageUrl,
            bio = enriched.profileBio,
            location = null, // Not included in current join, could be added if needed
            connectionStatus = ConnectionStatus.CONNECTED // Accepted relationships are connected
        )
    }

    private suspend fun updateFollowerCounts(followerId: String, targetUserId: String, isFollow: Boolean) {
        try {
            if (isFollow) {
                // Increment target's follower count and follower's following count
                socialProfileDao.incrementFollowerCount(targetUserId)
                socialProfileDao.incrementFollowingCount(followerId)
            } else {
                // Decrement counts
                socialProfileDao.decrementFollowerCount(targetUserId)
                socialProfileDao.decrementFollowingCount(followerId)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update follower counts")
        }
    }

    private suspend fun syncRelationshipToFirebase(relationship: FollowRelationshipEntity) {
        if (OfflineArchitectureFlags.FIX_FOLLOW_REPOSITORY) {
            return
        }

        try {
            legacyDataSource.syncRelationship(relationship)
                
            // Update sync status
            followRelationshipDao.updateSyncStatus(relationship.id, true, relationship.syncVersion + 1)
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync relationship to Firebase: ${relationship.id}")
        }
    }

    private suspend fun syncFollowRequestToFirebase(request: FollowRequestEntity) {
        if (OfflineArchitectureFlags.FIX_FOLLOW_REPOSITORY) {
            return
        }

        try {
            legacyDataSource.syncFollowRequest(request)
                
            // Update sync status
            followRequestDao.updateSyncStatus(request.id, true, request.syncVersion + 1, System.currentTimeMillis())
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync follow request to Firebase: ${request.id}")
        }
    }

    private suspend fun syncRelationshipDeletionToFirebase(followerId: String, targetUserId: String) {
        if (OfflineArchitectureFlags.FIX_FOLLOW_REPOSITORY) {
            return
        }

        try {
            legacyDataSource.deleteRelationship(followerId, targetUserId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync relationship deletion to Firebase")
        }
    }
    
    /**
     * Syncs follow relationships from Firebase using upsert strategy to prevent feed flickering.
     * This method preserves existing relationships while updating with new data from Firebase.
     * 
     * @param userId The user ID to sync relationships for
     * @return The number of relationships synced
     */
    suspend fun syncFollowRelationshipsFromFirebaseUpsert(userId: String): Int {
        if (OfflineArchitectureFlags.FIX_FOLLOW_REPOSITORY) {
            return 0
        }

        return try {
            Timber.d("🔥 SYNC-FIX: Starting upsert-based follow sync for user $userId")
            
            // Fetch follower relationships (where user is being followed)
            val followerDocs = legacyDataSource.fetchFollowerRelationships(userId)
            
            // Fetch following relationships (where user is following others)
            val followingDocs = legacyDataSource.fetchFollowingRelationships(userId)
            
            val relationshipsToUpsert = mutableListOf<FollowRelationshipEntity>()
            val currentTime = System.currentTimeMillis()
            
            // Process follower relationships
            followerDocs.forEach { data ->
                val id = data["id"] as? String ?: java.util.UUID.randomUUID().toString()
                relationshipsToUpsert.add(
                    FollowRelationshipEntity(
                        id = id,
                        followerId = data["followerId"] as String,
                        followingId = data["followingId"] as String,
                        status = data["status"] as String,
                        createdAt = when (val created = data["createdAt"]) {
                            is com.google.firebase.Timestamp -> created.toDate().time
                            is Number -> created.toLong()
                            else -> currentTime
                        },
                        acceptedAt = when (val accepted = data["acceptedAt"]) {
                            is com.google.firebase.Timestamp -> accepted.toDate().time
                            is Number -> accepted.toLong()
                            else -> null
                        },
                        blockedAt = when (val blocked = data["blockedAt"]) {
                            is com.google.firebase.Timestamp -> blocked.toDate().time
                            is Number -> blocked.toLong()
                            else -> null
                        },
                        isSynced = true,
                        syncVersion = (data["syncVersion"] as? Number)?.toLong() ?: 1L,
                        lastModified = currentTime
                    )
                )
            }
            
            // Process following relationships
            followingDocs.forEach { data ->
                val id = data["id"] as? String ?: java.util.UUID.randomUUID().toString()
                // Check if not already added (to avoid duplicates)
                if (relationshipsToUpsert.none { it.id == id }) {
                    relationshipsToUpsert.add(
                        FollowRelationshipEntity(
                            id = id,
                            followerId = data["followerId"] as String,
                            followingId = data["followingId"] as String,
                            status = data["status"] as String,
                            createdAt = when (val created = data["createdAt"]) {
                                is com.google.firebase.Timestamp -> created.toDate().time
                                is Number -> created.toLong()
                                else -> currentTime
                            },
                            acceptedAt = when (val accepted = data["acceptedAt"]) {
                                is com.google.firebase.Timestamp -> accepted.toDate().time
                                is Number -> accepted.toLong()
                                else -> null
                            },
                            blockedAt = when (val blocked = data["blockedAt"]) {
                                is com.google.firebase.Timestamp -> blocked.toDate().time
                                is Number -> blocked.toLong()
                                else -> null
                            },
                            isSynced = true,
                            syncVersion = (data["syncVersion"] as? Number)?.toLong() ?: 1L,
                            lastModified = currentTime
                        )
                    )
                }
            }
            
            // 🔥 FIX: Use safe upsert to prevent FK violations and feed flickering
            if (relationshipsToUpsert.isNotEmpty()) {
                val insertedCount = safeFollowDao.insertFollowRelationshipsWithUserValidation(relationshipsToUpsert)
                Timber.d("🔥 SYNC-FIX: Safely upserted $insertedCount/${relationshipsToUpsert.size} follow relationships for user $userId")
            } else {
                Timber.d("🔥 SYNC-FIX: No follow relationships to upsert for user $userId")
            }
            
            relationshipsToUpsert.size
            
        } catch (e: Exception) {
            Timber.e(e, "🔥 SYNC-FIX: Failed to sync follow relationships from Firebase for user $userId")
            0
        }
    }
    
    /**
     * Fetches a user's profile from Firebase and creates local database entries.
     * This is a fallback mechanism for when a user exists in Firebase but not locally.
     * Creates both UserProfileEntity (parent) and SocialProfileEntity (child) to satisfy foreign key constraints.
     * 
     * @param userId The user ID to fetch profile for
     * @return The created SocialProfileEntity or null if not found
     */
    private suspend fun fetchAndCreateSocialProfileFromFirebase(userId: String): SocialProfileEntity? {
        if (OfflineArchitectureFlags.FIX_FOLLOW_REPOSITORY) {
            // ROOM-FIRST FIX: Try to create profile from local UserAccount data
            return tryCreateSocialProfileFromUserAccount(userId)
        }

        return try {
            Timber.d("Attempting to fetch social profile from Firebase for user: $userId")
            
            // First check if user already exists in user_profiles table
            val existingUserProfile = userProfileDao.getProfileForUserSuspend(userId)
            if (existingUserProfile == null) {
                Timber.d("User profile doesn't exist locally, will create it first")
            }
            
            val data = legacyDataSource.fetchSocialProfileData(userId) ?: return null
            val currentTime = System.currentTimeMillis()
            val currentDateTime = java.time.LocalDateTime.now()
            
            // Step 1: Create UserProfileEntity first (parent table) if it doesn't exist
            if (existingUserProfile == null) {
                val userProfile = UserProfileEntity(
                    id = userId, // Use userId as the primary key
                    userId = userId,
                    displayName = data["displayName"] as? String ?: data["display_name"] as? String ?: data["username"] as? String ?: "User",
                    age = (data["age"] as? Number)?.toInt(),
                    weightKg = (data["weightKg"] as? Number)?.toDouble(),
                    heightCm = (data["heightCm"] as? Number)?.toDouble(),
                    fitnessLevel = data["fitnessLevel"] as? String,
                    goals = data["goals"] as? String ?: data["fitnessGoals"] as? String,
                    availableEquipment = data["availableEquipment"] as? String,
                    workoutFrequency = (data["workoutFrequency"] as? Number)?.toInt(),
                    preferredWorkoutDuration = (data["preferredWorkoutDuration"] as? Number)?.toInt(),
                    completedAt = null, // Profile not completed yet
                    createdAt = currentDateTime,
                    updatedAt = currentDateTime,
                    isSynced = true,
                    syncVersion = 1L,
                    bio = data["bio"] as? String,
                    isPublic = data["isPublic"] as? Boolean ?: true,
                    lastActiveAt = currentDateTime,
                    totalWorkouts = (data["totalWorkouts"] as? Number)?.toInt() ?: 0,
                    currentStreak = (data["currentStreak"] as? Number)?.toInt() ?: 0,
                    longestStreak = (data["longestStreak"] as? Number)?.toInt() ?: 0,
                    memberSince = currentDateTime,
                    profileCompletionPercentage = 50, // Default partial completion
                    profileImageUrl = data["profileImageUrl"] as? String ?: data["profilePhotoUrl"] as? String,
                    profileImageUpdatedAt = null,
                    hasCustomProfileImage = (data["profileImageUrl"] as? String != null || data["profilePhotoUrl"] as? String != null)
                )
                
                try {
                    userProfileDao.insertProfile(userProfile)
                    Timber.i("Successfully created UserProfileEntity for user: $userId")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to create UserProfileEntity for user: $userId")
                    // If we can't create the parent, we can't proceed
                    return null
                }
            }
            
            // Step 2: Create SocialProfileEntity (child table) - now the foreign key constraint will be satisfied
            val socialProfile = SocialProfileEntity(
                userId = userId,
                username = data["username"] as? String ?: "user_$userId",
                displayName = data["displayName"] as? String ?: data["display_name"] as? String,
                bio = data["bio"] as? String,
                profilePhotoUrl = data["profilePhotoUrl"] as? String ?: data["profileImageUrl"] as? String ?: data["profile_photo_url"] as? String,
                coverPhotoUrl = data["coverPhotoUrl"] as? String ?: data["coverImageUrl"] as? String ?: data["cover_photo_url"] as? String,
                workoutCount = (data["workoutCount"] as? Number)?.toInt() ?: (data["totalWorkouts"] as? Number)?.toInt() ?: 0,
                followerCount = (data["followerCount"] as? Number)?.toInt() ?: (data["followersCount"] as? Number)?.toInt() ?: 0,
                followingCount = (data["followingCount"] as? Number)?.toInt() ?: 0,
                memberSince = (data["memberSince"] as? Number)?.toLong() ?: currentTime,
                lastActive = (data["lastActive"] as? Number)?.toLong() ?: currentTime,
                isVerified = data["isVerified"] as? Boolean ?: false,
                isPrivate = data["isPrivate"] as? Boolean ?: false,
                hideFromSuggestions = data["hideFromSuggestions"] as? Boolean ?: false,
                allowFriendRequests = data["allowFriendRequests"] as? Boolean ?: true,
                instagramHandle = data["instagramHandle"] as? String,
                youtubeChannel = data["youtubeChannel"] as? String,
                personalWebsite = data["personalWebsite"] as? String,
                isSynced = true, // Mark as synced since we just fetched from Firebase
                syncVersion = 1,
                createdAt = (data["createdAt"] as? Number)?.toLong() ?: currentTime,
                updatedAt = currentTime
            )
            
            // Insert social profile into local database
            try {
                socialProfileDao.insertProfile(socialProfile)
                Timber.i("Successfully created local social profile for user: $userId from Firebase data")
            } catch (e: Exception) {
                // If insert fails (e.g., already exists), try to update instead
                Timber.w("Social profile insert failed, attempting update for user: $userId", e)
                try {
                    socialProfileDao.updateProfile(socialProfile)
                    Timber.i("Successfully updated social profile for user: $userId")
                } catch (updateError: Exception) {
                    Timber.e(updateError, "Failed to update social profile for user: $userId")
                    return null
                }
            }
            
            return socialProfile
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch and create profiles from Firebase for user: $userId")
            null
        }
    }
    
    /**
     * Ensures both users exist in the database before creating a follow relationship.
     * This prevents foreign key constraint violations during sync operations.
     * Creates minimal user stubs if users don't exist.
     * 
     * @param userId1 First user ID
     * @param userId2 Second user ID
     */
    private suspend fun ensureUsersExist(userId1: String, userId2: String) {
        try {
            // Check if both users exist
            val user1Exists = userProfileDao.hasProfile(userId1)
            val user2Exists = userProfileDao.hasProfile(userId2)
            
            val currentDateTime = java.time.LocalDateTime.now()
            
            // Create minimal stub for user1 if doesn't exist
            if (!user1Exists) {
                Timber.w("User $userId1 doesn't exist, creating minimal stub to prevent FK violation")
                val userStub = UserProfileEntity(
                    id = userId1,
                    userId = userId1,
                    displayName = "User",
                    age = null,
                    weightKg = null,
                    heightCm = null,
                    fitnessLevel = null,
                    goals = null,
                    availableEquipment = null,
                    workoutFrequency = null,
                    preferredWorkoutDuration = null,
                    completedAt = null,
                    createdAt = currentDateTime,
                    updatedAt = currentDateTime,
                    isSynced = false, // Will be synced later by ProfileSyncWorker
                    syncVersion = 0L,
                    bio = null,
                    isPublic = true,
                    lastActiveAt = currentDateTime,
                    totalWorkouts = 0,
                    currentStreak = 0,
                    longestStreak = 0,
                    memberSince = currentDateTime,
                    profileCompletionPercentage = 0,
                    profileImageUrl = null,
                    profileImageUpdatedAt = null,
                    hasCustomProfileImage = false
                )
                
                try {
                    userProfileDao.insertProfile(userStub)
                    Timber.i("Created user stub for $userId1")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to create user stub for $userId1")
                }
            }
            
            // Create minimal stub for user2 if doesn't exist
            if (!user2Exists) {
                Timber.w("User $userId2 doesn't exist, creating minimal stub to prevent FK violation")
                val userStub = UserProfileEntity(
                    id = userId2,
                    userId = userId2,
                    displayName = "User",
                    age = null,
                    weightKg = null,
                    heightCm = null,
                    fitnessLevel = null,
                    goals = null,
                    availableEquipment = null,
                    workoutFrequency = null,
                    preferredWorkoutDuration = null,
                    completedAt = null,
                    createdAt = currentDateTime,
                    updatedAt = currentDateTime,
                    isSynced = false, // Will be synced later by ProfileSyncWorker
                    syncVersion = 0L,
                    bio = null,
                    isPublic = true,
                    lastActiveAt = currentDateTime,
                    totalWorkouts = 0,
                    currentStreak = 0,
                    longestStreak = 0,
                    memberSince = currentDateTime,
                    profileCompletionPercentage = 0,
                    profileImageUrl = null,
                    profileImageUpdatedAt = null,
                    hasCustomProfileImage = false
                )
                
                try {
                    userProfileDao.insertProfile(userStub)
                    Timber.i("Created user stub for $userId2")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to create user stub for $userId2")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to ensure users exist for follow relationship")
            // Don't throw - this is a defensive mechanism, let the FK constraint handle actual errors
        }
    }

    /**
     * Tries to create a social profile from local UserAccount data (Room-First fallback).
     * This is used when Room-First architecture is enabled and Firebase is not available.
     *
     * @param userId The user ID to create profile for
     * @return The created SocialProfileEntity or null if UserAccount doesn't exist
     */
    private suspend fun tryCreateSocialProfileFromUserAccount(userId: String): SocialProfileEntity? {
        return try {
            Timber.w("⚠️ FOLLOW-FIX: Missing social profile for user $userId - attempting auto-creation from UserAccount")

            // Get UserProfile data for bio, photo, etc. (optional - use fallbacks if missing)
            val userProfile = userProfileDao.getProfileForUserSuspend(userId)

            // Get actual username from UserAccount (optional - use fallbacks if missing)
            val userAccount = userAccountDao.getAccountForUserSuspend(userId)

            if (userProfile == null && userAccount == null) {
                Timber.e("❌ FOLLOW-FIX: Cannot create social profile - BOTH UserProfile AND UserAccount missing for user: $userId")
                Timber.e("   This indicates a severely corrupted user state - manual intervention required")
                return null
            }

            // MAXIMUM RESILIENCE: Use actual data if available, otherwise intelligent fallbacks
            val actualUsername = userAccount?.username
                ?: userAccount?.displayName?.replace(" ", "_")?.lowercase()
                ?: userProfile?.displayName?.replace(" ", "_")?.lowercase()
                ?: "user_${userId.take(8)}"

            val displayName = userProfile?.displayName
                ?: userAccount?.displayName
                ?: userAccount?.username
                ?: "User ${userId.take(8)}"

            val currentTime = System.currentTimeMillis()

            if (userAccount == null) {
                Timber.w("⚠️ FOLLOW-FIX: UserAccount missing - using fallback username: $actualUsername")
            } else {
                Timber.d("✅ FOLLOW-FIX: Using actual username: $actualUsername (from UserAccount)")
            }
            Timber.d("   - Display name: $displayName")
            Timber.d("   - Has bio: ${userProfile?.bio != null}")
            Timber.d("   - Has profile photo: ${userProfile?.profileImageUrl != null}")

            // Create social profile entity (using available data with fallbacks)
            val socialProfile = SocialProfileEntity(
                userId = userId,
                username = actualUsername,
                displayName = displayName,
                bio = userProfile?.bio,
                profilePhotoUrl = userProfile?.profileImageUrl,
                coverPhotoUrl = null,
                isPrivate = !(userProfile?.isPublic ?: true), // Default to public if no UserProfile
                hideFromSuggestions = false,
                allowFriendRequests = true,
                instagramHandle = null,
                youtubeChannel = null,
                personalWebsite = null,
                memberSince = currentTime,
                createdAt = currentTime,
                updatedAt = currentTime
            )

            // Insert into database
            socialProfileDao.insertProfile(socialProfile)
            Timber.i("✅ FOLLOW-FIX: Successfully auto-created social profile for user $userId")
            Timber.i("   - Username: $actualUsername ${if (userAccount != null) "(from UserAccount)" else "(fallback)"}")
            Timber.i("   - Display Name: $displayName")
            Timber.i("   - Bio: ${userProfile?.bio ?: "none"}")
            Timber.i("   - Profile Photo: ${if (userProfile?.profileImageUrl != null) "preserved" else "none"}")
            Timber.i("   - Data source: ${when {
                userAccount != null && userProfile != null -> "UserAccount + UserProfile"
                userAccount != null -> "UserAccount only (UserProfile missing)"
                userProfile != null -> "UserProfile only (UserAccount missing)"
                else -> "Full fallback (both missing - should not reach here)"
            }}")
            Timber.i("   - Profile should now be usable for follow operations")

            // Return the created profile
            socialProfile
        } catch (e: Exception) {
            Timber.e(e, "❌ FOLLOW-FIX: Failed to create social profile from UserAccount for user: $userId")
            null
        }
    }
}
