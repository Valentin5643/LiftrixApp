package com.example.liftrix.data.remote.realtime

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
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for real-time post engagement synchronization
 * Handles likes, shares, and engagement metrics updates
 */
@Singleton
class PostEngagementListener @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val postLikeDao: PostLikeDao,
    private val workoutPostDao: WorkoutPostDao
) {
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
                                    operation = "LIKES_SYNC",
                                    analyticsContext = mapOf("post_id" to postId)
                                )
                            )
                        )
                        return@addSnapshotListener
                    }

                    snapshot?.let { querySnapshot ->
                        try {
                            // Process like changes
                            for (change in querySnapshot.documentChanges) {
                                when (change.type) {
                                    DocumentChange.Type.ADDED -> {
                                        val like = parseLikeDocument(change.document.data, change.document.id)
                                        like?.let {
                                            CoroutineScope(Dispatchers.IO).launch {
                                                insertLikeLocally(it)
                                            }
                                        }
                                    }
                                    DocumentChange.Type.REMOVED -> {
                                        CoroutineScope(Dispatchers.IO).launch {
                                            deleteLikeLocally(change.document.id)
                                        }
                                    }
                                    else -> { /* Likes typically don't get modified */ }
                                }
                            }

                            // Update post like count
                            val totalLikes = querySnapshot.size()
                            CoroutineScope(Dispatchers.IO).launch {
                                updatePostLikeCount(postId, totalLikes)
                            }
                            
                            trySend(Result.success(Unit))
                        } catch (e: Exception) {
                            Timber.e(e, "Error processing like changes for post: $postId")
                        }
                    }
                }

            // Listen to post engagement metrics
            val postListener = firestore.collection("workout_posts")
                .document(postId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Timber.e(error, "Error in post metrics sync for post: $postId")
                        return@addSnapshotListener
                    }

                    snapshot?.let { document ->
                        if (document.exists()) {
                            try {
                                val data = document.data!!
                                updatePostEngagementMetrics(
                                    postId = postId,
                                    likeCount = (data["like_count"] as? Long)?.toInt() ?: 0,
                                    commentCount = (data["comment_count"] as? Long)?.toInt() ?: 0,
                                    shareCount = (data["share_count"] as? Long)?.toInt() ?: 0,
                                    saveCount = (data["save_count"] as? Long)?.toInt() ?: 0
                                )
                            } catch (e: Exception) {
                                Timber.e(e, "Error updating post metrics for: $postId")
                            }
                        }
                    }
                }

            // Store listeners for cleanup
            listeners["${postId}_likes"] = likesListener
            listeners["${postId}_post"] = postListener
            
            Timber.d("Started engagement sync for post: $postId")

        } catch (e: Exception) {
            Timber.e(e, "Failed to start engagement sync for post: $postId")
            trySend(
                LiftrixResult.Error(
                    LiftrixError.NetworkError(
                        errorMessage = "Failed to start engagement sync: ${e.message}",
                        operation = "START_ENGAGEMENT_SYNC",
                        analyticsContext = mapOf("post_id" to postId)
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
        listeners["${postId}_post"]?.remove()
        listeners.remove("${postId}_likes")
        listeners.remove("${postId}_post")
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

    private suspend fun insertLikeLocally(like: PostLikeEntity) {
        try {
            postLikeDao.insertLike(like)
            Timber.v("Inserted like locally: ${like.id}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to insert like locally: ${like.id}")
        }
    }

    private suspend fun deleteLikeLocally(likeId: String) {
        try {
            postLikeDao.deleteLike(likeId)
            Timber.v("Deleted like locally: $likeId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete like locally: $likeId")
        }
    }

    private suspend fun updatePostLikeCount(postId: String, likeCount: Int) {
        try {
            workoutPostDao.updateLikeCount(postId, likeCount)
            Timber.v("Updated post like count: $postId -> $likeCount")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update post like count: $postId")
        }
    }

    private suspend fun updatePostEngagementMetrics(
        postId: String,
        likeCount: Int,
        commentCount: Int,
        shareCount: Int,
        saveCount: Int
    ) {
        try {
            workoutPostDao.updateEngagementMetrics(
                postId = postId,
                likeCount = likeCount,
                commentCount = commentCount,
                shareCount = shareCount,
                saveCount = saveCount
            )
            Timber.v("Updated post engagement metrics: $postId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update post engagement metrics: $postId")
        }
    }

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