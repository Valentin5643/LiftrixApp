package com.example.liftrix.data.remote.realtime

import com.example.liftrix.config.OfflineArchitectureFlags
import com.example.liftrix.data.local.dao.PostLikeDao
import com.example.liftrix.data.local.dao.WorkoutPostDao
import com.example.liftrix.data.local.entity.PostLikeEntity
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.common.LiftrixResult
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for real-time post engagement synchronization
 * Handles likes, shares, and engagement metrics updates
 *
 * Refactored for offline-first architecture (SPEC-20241228):
 * - Uses shared CoroutineScope with SupervisorJob for listener operations
 * - Uses upsertFromRemote() for idempotent listener writes
 * - Feature-flag gated for rollback support
 */
@Singleton
class PostEngagementListener @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val postLikeDao: PostLikeDao,
    private val workoutPostDao: WorkoutPostDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listeners = mutableMapOf<String, ListenerRegistration>()

    /**
     * Starts real-time sync for post engagement (likes, shares, etc.)
     */
    fun startEngagementSync(postId: String): Flow<LiftrixResult<Unit>> = callbackFlow {
        try {
            // Listen to post likes
            val likesListener = firestore.collection("post_likes")
                .whereEqualTo("post_id", postId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Timber.e(error, "Error in likes sync for post: $postId")
                        trySend(
                            Result.failure(
                                LiftrixError.NetworkError(
                                    errorMessage = "Real-time likes sync failed: ${error.message}",
                                    analyticsContext = mapOf("post_id" to postId, "operation" to "LIKES_SYNC")
                                )
                            )
                        )
                        return@addSnapshotListener
                    }

                    snapshot?.let { querySnapshot ->
                        try {
                            // Process like changes using shared scope
                            for (change in querySnapshot.documentChanges) {
                                when (change.type) {
                                    DocumentChange.Type.ADDED -> {
                                        val like = parseLikeDocument(change.document.data, change.document.id)
                                        like?.let {
                                            scope.launch {
                                                insertLikeFromRemote(it)
                                            }
                                        }
                                    }
                                    DocumentChange.Type.REMOVED -> {
                                        scope.launch {
                                            deleteLikeLocally(change.document.id)
                                        }
                                    }
                                    else -> { /* Likes typically don't get modified */ }
                                }
                            }

                            // Note: Post like count is updated by EngagementRealtimeSyncService
                            // via upsertEngagementFromRemote(). No need to duplicate here.

                            trySend(Result.success(Unit))
                        } catch (e: Exception) {
                            Timber.e(e, "Error processing like changes for post: $postId")
                        }
                    }
                }

            // Note: Post engagement metrics are now handled by EngagementRealtimeSyncService
            // which uses upsertEngagementFromRemote() for idempotent updates.
            // This listener is kept for backward compatibility but should migrate to the service.

            // Store listener for cleanup
            listeners["${postId}_likes"] = likesListener
            
            Timber.d("Started engagement sync for post: $postId")

        } catch (e: Exception) {
            Timber.e(e, "Failed to start engagement sync for post: $postId")
            trySend(
                Result.failure(
                    LiftrixError.NetworkError(
                        errorMessage = "Failed to start engagement sync: ${e.message}",
                        analyticsContext = mapOf("post_id" to postId, "operation" to "START_ENGAGEMENT_SYNC")
                    )
                )
            )
        }

        awaitClose {
            stopEngagementSync(postId)
        }
    }

    /**
     * Stops real-time sync for post engagement
     */
    fun stopEngagementSync(postId: String) {
        listeners["${postId}_likes"]?.remove()
        listeners.remove("${postId}_likes")
        Timber.d("Stopped engagement sync for post: $postId")
    }

    /**
     * Stops all active engagement sync listeners
     */
    fun stopAllEngagementSync() {
        listeners.values.forEach { it.remove() }
        listeners.clear()
        Timber.d("Stopped all engagement sync listeners")
    }

    /**
     * Observes real-time changes to user's like status for posts
     */
    fun observeUserLikeStatus(userId: String): Flow<Map<String, Boolean>> = callbackFlow {
        val listener = firestore.collection("post_likes")
            .whereEqualTo("user_id", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Error in user likes sync for user: $userId")
                    return@addSnapshotListener
                }

                snapshot?.let { querySnapshot ->
                    try {
                        val likedPosts = mutableMapOf<String, Boolean>()
                        
                        querySnapshot.documents.forEach { document ->
                            val postId = document.data?.get("post_id") as? String
                            if (postId != null) {
                                likedPosts[postId] = true
                            }
                        }
                        
                        trySend(likedPosts)
                    } catch (e: Exception) {
                        Timber.e(e, "Error processing user likes for user: $userId")
                    }
                }
            }

        awaitClose {
            listener.remove()
        }
    }

    /**
     * Observes real-time changes to post engagement metrics
     */
    fun observePostEngagement(postId: String): Flow<PostEngagementMetrics> = callbackFlow {
        val listener = firestore.collection("workout_posts")
            .document(postId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Error observing post engagement for: $postId")
                    return@addSnapshotListener
                }

                snapshot?.let { document ->
                    if (document.exists()) {
                        try {
                            val data = document.data!!
                            val metrics = PostEngagementMetrics(
                                likeCount = (data["like_count"] as? Long)?.toInt() ?: 0,
                                commentCount = (data["comment_count"] as? Long)?.toInt() ?: 0,
                                shareCount = (data["share_count"] as? Long)?.toInt() ?: 0,
                                saveCount = (data["save_count"] as? Long)?.toInt() ?: 0
                            )
                            trySend(metrics)
                        } catch (e: Exception) {
                            Timber.e(e, "Error parsing engagement metrics for: $postId")
                        }
                    }
                }
            }

        awaitClose {
            listener.remove()
        }
    }

    /**
     * IDEMPOTENT: Insert like from REMOTE origin (Firestore listener ADDED).
     * Uses upsertFromRemote() with timestamp deduplication, sets isDirty=false.
     * Feature-flag gated for rollback support.
     */
    private suspend fun insertLikeFromRemote(like: PostLikeEntity) {
        try {
            if (OfflineArchitectureFlags.USE_IDEMPOTENT_LISTENERS) {
                // NEW: Room-first idempotent pattern
                postLikeDao.upsertFromRemote(like)
                Timber.v("✅ IDEMPOTENT: Inserted like from remote: ${like.id}")
            } else {
                // LEGACY: Direct insert (feedback loop risk)
                postLikeDao.insertLike(like)
                Timber.w("⚠️ LEGACY: Inserted like (feedback loop risk): ${like.id}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to insert like from remote: ${like.id}")
        }
    }

    private suspend fun deleteLikeLocally(likeId: String) {
        try {
            val like = postLikeDao.getLikeById(likeId)
            if (like != null) {
                postLikeDao.deleteLike(like)
                Timber.v("Deleted like locally: $likeId")
            } else {
                Timber.w("Like not found for deletion: $likeId")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete like locally: $likeId")
        }
    }

    // Note: updatePostLikeCount() and updatePostEngagementMetrics() removed
    // These are now handled by EngagementRealtimeSyncService.upsertEngagementFromRemote()
    // which provides idempotent updates with timestamp deduplication.

    private fun parseLikeDocument(data: Map<String, Any>, documentId: String): PostLikeEntity? {
        return try {
            PostLikeEntity(
                id = documentId,
                postId = data["post_id"] as String,
                userId = data["user_id"] as String,
                createdAt = (data["created_at"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: System.currentTimeMillis(),
                isSynced = true
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse like document: $documentId")
            null
        }
    }
}

/**
 * Data class for post engagement metrics
 */
data class PostEngagementMetrics(
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val shareCount: Int = 0,
    val saveCount: Int = 0
)