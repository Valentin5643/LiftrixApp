package com.example.liftrix.data.remote.realtime

import com.example.liftrix.config.OfflineArchitectureFlags
import com.example.liftrix.data.local.dao.PostCommentDao
import com.example.liftrix.data.local.entity.PostCommentEntity
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for real-time comment synchronization with Firestore
 * Provides live updates for comments on workout posts
 *
 * Refactored for offline-first architecture (SPEC-20241228):
 * - Uses shared CoroutineScope with SupervisorJob for listener operations
 * - Uses upsertFromRemote() for idempotent listener writes
 * - Feature-flag gated for rollback support
 */
@Singleton
class CommentSyncService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val commentDao: PostCommentDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listeners = mutableMapOf<String, ListenerRegistration>()

    /**
     * Starts real-time sync for comments on a specific post
     * Updates local database with live Firestore changes
     */
    fun startCommentSync(postId: String): Flow<LiftrixResult<Unit>> = callbackFlow {
        try {
            val query = firestore.collection("post_comments")
                .whereEqualTo("post_id", postId)
                .orderBy("created_at", Query.Direction.ASCENDING)

            val listener = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Error in comment sync for post: $postId")
                    trySend(
                        Result.failure(
                            LiftrixError.NetworkError(
                                errorMessage = "Real-time comment sync failed: ${error.message}",
                                analyticsContext = mapOf("post_id" to postId, "operation" to "COMMENT_SYNC")
                            )
                        )
                    )
                    return@addSnapshotListener
                }

                snapshot?.let { querySnapshot ->
                    try {
                        // Process document changes for efficient updates using shared scope
                        for (change in querySnapshot.documentChanges) {
                            when (change.type) {
                                DocumentChange.Type.ADDED -> {
                                    val comment = parseCommentDocument(change.document.data, change.document.id)
                                    comment?.let {
                                        scope.launch {
                                            insertCommentFromRemote(it, postId)
                                        }
                                    }
                                }
                                DocumentChange.Type.MODIFIED -> {
                                    val comment = parseCommentDocument(change.document.data, change.document.id)
                                    comment?.let {
                                        scope.launch {
                                            updateCommentFromRemote(it, postId)
                                        }
                                    }
                                }
                                DocumentChange.Type.REMOVED -> {
                                    scope.launch {
                                        deleteCommentLocally(change.document.id, postId)
                                    }
                                }
                            }
                        }

                        trySend(Result.success(Unit))
                        Timber.d("Comment sync completed for post: $postId, changes: ${querySnapshot.documentChanges.size}")
                    } catch (e: Exception) {
                        Timber.e(e, "Error processing comment changes for post: $postId")
                        trySend(
                            Result.failure(
                                LiftrixError.BusinessLogicError(
                                    code = "PROCESS_COMMENT_CHANGES",
                                    errorMessage = "Failed to process comment updates: ${e.message}",
                                    analyticsContext = emptyMap()
                                )
                            )
                        )
                    }
                }
            }

            // Store listener for cleanup
            listeners[postId] = listener
            
            Timber.d("Started comment sync for post: $postId")

        } catch (e: Exception) {
            Timber.e(e, "Failed to start comment sync for post: $postId")
            trySend(
                Result.failure(
                    LiftrixError.NetworkError(
                        errorMessage = "Failed to start comment sync: ${e.message}",
                        analyticsContext = mapOf("post_id" to postId, "operation" to "START_COMMENT_SYNC")
                    )
                )
            )
        }

        awaitClose {
            stopCommentSync(postId)
        }
    }

    /**
     * Stops real-time sync for comments on a specific post
     */
    fun stopCommentSync(postId: String) {
        listeners[postId]?.let { listener ->
            listener.remove()
            listeners.remove(postId)
            Timber.d("Stopped comment sync for post: $postId")
        }
    }

    /**
     * Stops all active comment sync listeners
     */
    fun stopAllCommentSync() {
        listeners.values.forEach { it.remove() }
        listeners.clear()
        Timber.d("Stopped all comment sync listeners")
    }

    /**
     * Creates a new comment with optimistic updates
     * Updates local database immediately, syncs to Firestore in background
     */
    suspend fun createCommentWithSync(
        postId: String,
        userId: String,
        content: String,
        parentCommentId: String? = null
    ): LiftrixResult<String> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to create comment: ${throwable.message}",
                analyticsContext = mapOf(
                    "post_id" to postId,
                    "user_id" to userId,
                    "has_parent" to (parentCommentId != null).toString(),
                    "operation" to "CREATE_COMMENT_WITH_SYNC"
                )
            )
        }
    ) {
        val commentId = generateCommentId()
        val timestamp = System.currentTimeMillis()

        // Create local entity for optimistic update
        val localComment = PostCommentEntity(
            id = commentId,
            postId = postId,
            userId = userId,
            content = content,
            replyToCommentId = parentCommentId, // Use correct property name
            likeCount = 0,
            isEdited = false,
            createdAt = timestamp,
            editedAt = null,
            updatedAt = timestamp,
            isSynced = false, // Will be updated after Firestore sync
            syncVersion = 0
        )

        // Insert locally first (optimistic update)
        commentDao.insertComment(localComment)

        // Sync to Firestore
        val firestoreData = mapOf(
            "post_id" to postId,
            "user_id" to userId,
            "content" to content,
            "parent_comment_id" to parentCommentId,
            "like_count" to 0,
            "is_edited" to false,
            "created_at" to com.google.firebase.Timestamp(timestamp / 1000, 0),
            "edited_at" to null,
            "sync_version" to 1
        )

        try {
            firestore.collection("post_comments")
                .document(commentId)
                .set(firestoreData)
                .await()

            // Mark as synced
            commentDao.markCommentSynced(commentId, 1, System.currentTimeMillis())
            
            Timber.d("Comment created and synced: $commentId")
        } catch (e: Exception) {
            Timber.w(e, "Failed to sync comment to Firestore: $commentId")
            // Local comment remains with isSynced = false for retry
        }

        commentId
    }

    /**
     * Updates comment like count with real-time sync
     */
    suspend fun updateCommentLikeCount(
        commentId: String,
        newLikeCount: Int
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to update comment like count: ${throwable.message}",
                analyticsContext = mapOf("comment_id" to commentId, "operation" to "UPDATE_COMMENT_LIKES")
            )
        }
    ) {
        // Update locally first
        val currentTime = System.currentTimeMillis()
        commentDao.updateLikeCount(commentId, newLikeCount, currentTime)

        // Sync to Firestore
        firestore.collection("post_comments")
            .document(commentId)
            .update("like_count", newLikeCount)
            .await()

        Timber.d("Comment like count updated: $commentId -> $newLikeCount")
    }

    /**
     * Deletes a comment with real-time sync
     */
    suspend fun deleteCommentWithSync(
        commentId: String,
        userId: String
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to delete comment: ${throwable.message}",
                analyticsContext = mapOf("comment_id" to commentId, "user_id" to userId, "operation" to "DELETE_COMMENT_WITH_SYNC")
            )
        }
    ) {
        // Delete from Firestore first
        firestore.collection("post_comments")
            .document(commentId)
            .delete()
            .await()

        // Delete locally (will also be handled by real-time listener)
        commentDao.deleteCommentById(commentId, userId)

        Timber.d("Comment deleted: $commentId")
    }

    /**
     * IDEMPOTENT: Insert comment from REMOTE origin (Firestore listener ADDED).
     * Uses upsertFromRemote() with timestamp deduplication, sets isDirty=false.
     * Feature-flag gated for rollback support.
     */
    private suspend fun insertCommentFromRemote(comment: PostCommentEntity, postId: String) {
        try {
            if (OfflineArchitectureFlags.USE_IDEMPOTENT_LISTENERS) {
                // NEW: Room-first idempotent pattern
                commentDao.upsertFromRemote(comment)
                Timber.v("✅ IDEMPOTENT: Inserted comment from remote: ${comment.id}")
            } else {
                // LEGACY: Direct insert (feedback loop risk)
                commentDao.insertComment(comment.copy(isSynced = true))
                Timber.w("⚠️ LEGACY: Inserted comment (feedback loop risk): ${comment.id}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to insert comment from remote: ${comment.id}")
        }
    }

    /**
     * IDEMPOTENT: Update comment from REMOTE origin (Firestore listener MODIFIED).
     * Uses upsertFromRemote() with timestamp deduplication, sets isDirty=false.
     * Feature-flag gated for rollback support.
     */
    private suspend fun updateCommentFromRemote(comment: PostCommentEntity, postId: String) {
        try {
            if (OfflineArchitectureFlags.USE_IDEMPOTENT_LISTENERS) {
                // NEW: Room-first idempotent pattern
                commentDao.upsertFromRemote(comment)
                Timber.v("✅ IDEMPOTENT: Updated comment from remote: ${comment.id}")
            } else {
                // LEGACY: Direct update (feedback loop risk)
                commentDao.updateComment(comment.copy(isSynced = true))
                Timber.w("⚠️ LEGACY: Updated comment (feedback loop risk): ${comment.id}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update comment from remote: ${comment.id}")
        }
    }

    private suspend fun deleteCommentLocally(commentId: String, postId: String) {
        try {
            val comment = commentDao.getCommentById(commentId)
            if (comment != null) {
                commentDao.deleteComment(comment)
                Timber.v("Deleted comment locally: $commentId")
            } else {
                Timber.w("Comment not found for deletion: $commentId")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete comment locally: $commentId")
        }
    }

    private fun parseCommentDocument(data: Map<String, Any>, documentId: String): PostCommentEntity? {
        return try {
            PostCommentEntity(
                id = documentId,
                postId = data["post_id"] as String,
                userId = data["user_id"] as String,
                content = data["content"] as String,
                replyToCommentId = data["parent_comment_id"] as? String, // Use correct property name
                likeCount = (data["like_count"] as? Long)?.toInt() ?: 0,
                isEdited = data["is_edited"] as? Boolean ?: false,
                createdAt = (data["created_at"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: System.currentTimeMillis(),
                editedAt = (data["edited_at"] as? com.google.firebase.Timestamp)?.toDate()?.time,
                updatedAt = System.currentTimeMillis(),
                isSynced = true, // From Firestore, so already synced
                syncVersion = (data["sync_version"] as? Long)?.toInt() ?: 0
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse comment document: $documentId")
            null
        }
    }

    private fun generateCommentId(): String = firestore.collection("post_comments").document().id
}

