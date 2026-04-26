package com.example.liftrix.sync

import androidx.lifecycle.LifecycleOwner
import com.example.liftrix.config.OfflineArchitectureFlags
import com.example.liftrix.data.local.dao.WorkoutPostDao
import com.example.liftrix.data.service.FirestoreListenerManager
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time engagement sync service for instant like/comment count updates.
 * Part of Firebase sync infrastructure from SPEC-20250118-firebase-sync-social.
 *
 * Refactored to use FirestoreListenerManager for memory leak prevention (SPEC-20251230-google-play-compliance).
 * Provides real-time Firestore listeners for engagement metrics with automatic lifecycle cleanup.
 */
@Singleton
class EngagementRealtimeSyncService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val postDao: WorkoutPostDao,
    private val listenerManager: FirestoreListenerManager
) {
    private val scope = CoroutineScope(SupervisorJob())
    
    /**
     * Start listening to real-time engagement updates for a specific post.
     * Uses FirestoreListenerManager for automatic lifecycle cleanup.
     *
     * @param postId Post ID to listen to
     * @param lifecycleOwner Optional LifecycleOwner for automatic cleanup (recommended)
     */
    fun startListeningToPost(postId: String, lifecycleOwner: LifecycleOwner? = null) {
        val listenerId = "engagement_$postId"

        Timber.d("Starting real-time engagement listening for post $postId")

        val registration = firestore
            .collection("workout_posts")
            .document(postId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Engagement sync error for post $postId")
                    return@addSnapshotListener
                }

                snapshot?.data?.let { data ->
                    scope.launch {
                        try {
                            updateEngagementFromRemote(postId, data)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to update engagement metrics for post $postId")
                        }
                    }
                }
            }

        // Register with listener manager for memory leak prevention
        listenerManager.registerListener(listenerId, registration, lifecycleOwner)
    }
    
    /**
     * Start listening to multiple posts at once for batch engagement updates.
     *
     * @param postIds List of post IDs to listen to
     * @param lifecycleOwner Optional LifecycleOwner for automatic cleanup (recommended)
     */
    fun startListeningToPosts(postIds: List<String>, lifecycleOwner: LifecycleOwner? = null) {
        postIds.forEach { postId ->
            startListeningToPost(postId, lifecycleOwner)
        }
        Timber.d("Started real-time engagement listening for ${postIds.size} posts")
    }

    /**
     * IDEMPOTENT: Update engagement metrics from remote Firestore data.
     * Uses upsertFromRemote to apply only if newer, prevents feedback loops.
     * Feature-flag gated for rollback support.
     */
    private suspend fun updateEngagementFromRemote(postId: String, data: Map<String, Any>) {
        if (OfflineArchitectureFlags.USE_IDEMPOTENT_LISTENERS) {
            // NEW: Room-first idempotent pattern
            val likeCount = (data["likeCount"] as? Long)?.toInt() ?: 0
            val commentCount = (data["commentCount"] as? Long)?.toInt() ?: 0
            val shareCount = (data["shareCount"] as? Long)?.toInt() ?: 0
            val saveCount = (data["saveCount"] as? Long)?.toInt() ?: 0
            val remoteModified = (data["lastModified"] as? Long) ?: System.currentTimeMillis()

            // IDEMPOTENT: Only applies if remote is newer
            postDao.upsertEngagementFromRemote(
                postId = postId,
                likeCount = likeCount,
                commentCount = commentCount,
                shareCount = shareCount,
                saveCount = saveCount,
                lastModified = remoteModified
            )
            // NO SYNC TRIGGER - already from Firestore

            Timber.d("✅ IDEMPOTENT: Updated engagement for post $postId (Room-first)")
        } else {
            // LEGACY: Direct update (feedback loop risk)
            val likeCount = (data["likeCount"] as? Long)?.toInt() ?: 0
            val commentCount = (data["commentCount"] as? Long)?.toInt() ?: 0
            val shareCount = (data["shareCount"] as? Long)?.toInt() ?: 0
            val saveCount = (data["saveCount"] as? Long)?.toInt() ?: 0

            postDao.updateEngagementMetrics(
                postId = postId,
                likeCount = likeCount,
                commentCount = commentCount,
                shareCount = shareCount,
                saveCount = saveCount,
                updatedAt = System.currentTimeMillis()
            )

            Timber.w("⚠️ LEGACY: Updated engagement for post $postId (feedback loop risk)")
        }
    }

    /**
     * Stop listening to real-time updates for a specific post.
     */
    fun stopListening(postId: String) {
        val listenerId = "engagement_$postId"
        listenerManager.removeListener(listenerId)
        Timber.d("Stopped real-time engagement listening for post $postId")
    }

    /**
     * Stop listening to multiple posts at once.
     */
    fun stopListening(postIds: List<String>) {
        postIds.forEach { postId ->
            stopListening(postId)
        }
        Timber.d("Stopped real-time engagement listening for ${postIds.size} posts")
    }

    /**
     * Stop all active listeners - call this when user logs out or app is destroyed.
     */
    fun stopAllListeners() {
        val activeListenerCount = listenerManager.getActiveListenerCount()
        listenerManager.removeAllListeners()

        Timber.d("Stopped all real-time engagement listeners ($activeListenerCount active)")
    }

    /**
     * Get the number of active listeners for monitoring.
     */
    fun getActiveListenerCount(): Int = listenerManager.getActiveListenerCount()

    /**
     * Check if a specific post is being listened to.
     */
    fun isListeningToPost(postId: String): Boolean {
        val listenerId = "engagement_$postId"
        return listenerManager.isListenerActive(listenerId)
    }

    /**
     * Get all post IDs currently being listened to.
     */
    fun getListenedPostIds(): Set<String> {
        return listenerManager.getActiveListenerIds()
            .filter { it.startsWith("engagement_") }
            .map { it.removePrefix("engagement_") }
            .toSet()
    }
}