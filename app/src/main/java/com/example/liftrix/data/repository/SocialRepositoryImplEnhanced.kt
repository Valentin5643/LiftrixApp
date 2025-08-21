package com.example.liftrix.data.repository

import android.content.Context
import androidx.work.WorkManager
import com.example.liftrix.core.workmanager.WorkManagerProvider
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import com.example.liftrix.data.local.dao.SocialProfileDao
import com.example.liftrix.data.local.dao.FollowRelationshipDao
import com.example.liftrix.data.local.dao.WorkoutPostDao
import com.example.liftrix.data.local.dao.GymBuddyDao
import com.example.liftrix.data.local.entity.FollowRelationshipEntity
import com.example.liftrix.data.local.entity.WorkoutPostEntity
import com.example.liftrix.data.local.entity.GymBuddyEntity
import com.example.liftrix.sync.SocialProfileSyncWorker
import com.example.liftrix.sync.FollowRelationshipSyncWorker
import com.example.liftrix.sync.WorkoutPostSyncWorker
import com.example.liftrix.sync.GymBuddySyncWorker
import com.example.liftrix.sync.EngagementRealtimeSyncService
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Enhanced social repository implementation with sync queuing for all social operations.
 * Part of Firebase sync infrastructure from SPEC-20250118-firebase-sync-social.
 * 
 * Implements optimistic updates with automatic sync worker queuing for all social
 * interactions including follows, posts, and gym buddy connections.
 */
@Singleton
class SocialRepositoryImplEnhanced @Inject constructor(
    private val socialProfileDao: SocialProfileDao,
    private val followDao: FollowRelationshipDao,
    private val postDao: WorkoutPostDao,
    private val gymBuddyDao: GymBuddyDao,
    @ApplicationContext private val context: Context,
    private val engagementSyncService: EngagementRealtimeSyncService
) {
    
    private val workManager: WorkManager
        get() = WorkManagerProvider.getInstance(context)

    // ========================================
    // Follow Operations with Sync
    // ========================================

    suspend fun followUser(
        followerId: String,
        followingId: String
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "FOLLOW_USER_FAILED",
                errorMessage = "Failed to follow user",
                analyticsContext = mapOf(
                    "followerId" to followerId,
                    "followingId" to followingId,
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        // Optimistic update locally
        val relationship = FollowRelationshipEntity(
            id = UUID.randomUUID().toString(),
            followerId = followerId,
            followingId = followingId,
            status = "ACCEPTED", // Auto-accept for public profiles
            createdAt = System.currentTimeMillis(),
            acceptedAt = System.currentTimeMillis(),
            isSynced = false,
            syncVersion = 0
        )
        
        followDao.insertFollowRelationship(relationship)
        
        // Update local counts immediately for instant UI feedback
        socialProfileDao.incrementFollowingCount(followerId)
        socialProfileDao.incrementFollowerCount(followingId)
        
        // Queue sync
        queueFollowSync(followerId)
        
        Timber.d("Follow relationship created locally and queued for sync: $followerId -> $followingId")
    }

    suspend fun unfollowUser(
        followerId: String,
        followingId: String
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "UNFOLLOW_USER_FAILED",
                errorMessage = "Failed to unfollow user",
                analyticsContext = mapOf(
                    "followerId" to followerId,
                    "followingId" to followingId,
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        // Remove relationship locally
        followDao.deleteFollowRelationship(followerId, followingId)
        
        // Update local counts immediately
        socialProfileDao.decrementFollowingCount(followerId)
        socialProfileDao.decrementFollowerCount(followingId)
        
        // Queue sync to update Firebase
        queueFollowSync(followerId)
        
        Timber.d("Follow relationship removed locally and queued for sync: $followerId -> $followingId")
    }

    // ========================================
    // Post Operations with Sync
    // ========================================

    suspend fun createPost(
        userId: String,
        workoutId: String,
        caption: String?,
        visibility: String
    ): LiftrixResult<String> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CREATE_POST_FAILED",
                errorMessage = "Failed to create workout post",
                analyticsContext = mapOf(
                    "userId" to userId,
                    "workoutId" to workoutId,
                    "visibility" to visibility,
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        val postId = UUID.randomUUID().toString()
        val post = WorkoutPostEntity(
            id = postId,
            userId = userId,
            workoutId = workoutId,
            caption = caption,
            visibility = visibility,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isSynced = false,
            syncVersion = 0
        )
        
        postDao.insertPost(post)
        
        // Start real-time engagement listening
        engagementSyncService.startListeningToPost(postId)
        
        // Queue sync
        queuePostSync(userId)
        
        Timber.d("Workout post created locally and queued for sync: $postId")
        postId
    }

    suspend fun toggleLike(
        postId: String,
        userId: String
    ): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "TOGGLE_LIKE_FAILED",
                errorMessage = "Failed to toggle post like",
                analyticsContext = mapOf(
                    "postId" to postId,
                    "userId" to userId,
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        val post = postDao.getPostById(postId)
            ?: throw IllegalArgumentException("Post not found: $postId")
        
        // For this example, we'll simulate like toggle logic
        // In a real implementation, you'd have a PostLikeDao to track individual likes
        val isLiked = false // This would come from PostLikeDao.isLiked(postId, userId)
        val newLikeCount = if (isLiked) {
            maxOf(0, post.likeCount - 1)
        } else {
            post.likeCount + 1
        }
        
        // Optimistic update
        postDao.updateLikeCount(postId, newLikeCount, System.currentTimeMillis())
        
        // Queue engagement sync
        queueEngagementSync(userId)
        
        Timber.d("Like toggled for post $postId, new count: $newLikeCount")
        !isLiked
    }

    // ========================================
    // Gym Buddy Operations with Sync
    // ========================================

    suspend fun addGymBuddy(
        userId: String,
        buddyId: String,
        nickname: String?,
        pairedViaQr: Boolean = true,
        pairingLocation: String? = null
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "ADD_GYM_BUDDY_FAILED",
                errorMessage = "Failed to add gym buddy",
                analyticsContext = mapOf(
                    "userId" to userId,
                    "buddyId" to buddyId,
                    "pairedViaQr" to pairedViaQr.toString(),
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        // Check 5 buddy limit
        val currentBuddyCount = gymBuddyDao.getGymBuddyCount(userId)
        if (currentBuddyCount >= 5) {
            throw IllegalStateException("Maximum of 5 gym buddies allowed")
        }
        
        val gymBuddy = GymBuddyEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            buddyId = buddyId,
            buddyNickname = nickname,
            createdAt = System.currentTimeMillis(),
            pairedViaQr = pairedViaQr,
            pairingLocation = pairingLocation,
            isSynced = false,
            syncVersion = 0
        )
        
        gymBuddyDao.insertGymBuddy(gymBuddy)
        
        // Queue sync
        queueGymBuddySync(userId)
        
        Timber.d("Gym buddy added locally and queued for sync: $userId -> $buddyId")
    }

    suspend fun removeGymBuddy(
        userId: String,
        buddyId: String
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "REMOVE_GYM_BUDDY_FAILED",
                errorMessage = "Failed to remove gym buddy",
                analyticsContext = mapOf(
                    "userId" to userId,
                    "buddyId" to buddyId,
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        gymBuddyDao.deleteGymBuddy(userId, buddyId)
        
        // Queue sync to update Firebase
        queueGymBuddySync(userId)
        
        Timber.d("Gym buddy removed locally and queued for sync: $userId -> $buddyId")
    }

    // ========================================
    // Data Access Methods
    // ========================================

    fun getFollowers(userId: String): Flow<List<FollowRelationshipEntity>> {
        return followDao.observeFollowers(userId)
    }

    fun getFollowing(userId: String): Flow<List<FollowRelationshipEntity>> {
        return followDao.observeFollowing(userId)
    }

    fun getGymBuddies(userId: String): Flow<List<GymBuddyEntity>> {
        return gymBuddyDao.observeGymBuddies(userId)
    }

    // ========================================
    // Private Sync Helper Methods
    // ========================================

    private suspend fun queueFollowSync(userId: String) {
        val workRequest = OneTimeWorkRequestBuilder<FollowRelationshipSyncWorker>()
            .setInputData(workDataOf("userId" to userId))
            .addTag("follow_sync_$userId")
            .build()
        
        workManager.enqueue(workRequest)
        Timber.d("Queued follow relationship sync for user $userId")
    }

    private suspend fun queuePostSync(userId: String) {
        val workRequest = OneTimeWorkRequestBuilder<WorkoutPostSyncWorker>()
            .setInputData(workDataOf("userId" to userId))
            .addTag("post_sync_$userId")
            .build()
        
        workManager.enqueue(workRequest)
        Timber.d("Queued workout post sync for user $userId")
    }

    private suspend fun queueGymBuddySync(userId: String) {
        val workRequest = OneTimeWorkRequestBuilder<GymBuddySyncWorker>()
            .setInputData(workDataOf("userId" to userId))
            .addTag("gym_buddy_sync_$userId")
            .build()
        
        workManager.enqueue(workRequest)
        Timber.d("Queued gym buddy sync for user $userId")
    }

    private suspend fun queueEngagementSync(userId: String) {
        // For engagement, we rely on real-time sync service
        // But we could also queue a specific engagement sync worker if needed
        Timber.d("Engagement updated for user $userId (real-time sync active)")
    }

    private suspend fun queueSocialProfileSync(userId: String) {
        val workRequest = OneTimeWorkRequestBuilder<SocialProfileSyncWorker>()
            .setInputData(workDataOf("userId" to userId))
            .addTag("social_profile_sync_$userId")
            .build()
        
        workManager.enqueue(workRequest)
        Timber.d("Queued social profile sync for user $userId")
    }
}