package com.example.liftrix.sync

import com.example.liftrix.data.local.dao.WorkoutPostDao
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
 * Provides real-time Firestore listeners for engagement metrics with proper
 * lifecycle management and error handling.
 */
@Singleton
class EngagementRealtimeSyncService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val postDao: WorkoutPostDao
) {
    private val scope = CoroutineScope(SupervisorJob())
    private val listeners = mutableMapOf<String, ListenerRegistration>()
    
    /**
     * Start listening to real-time engagement updates for a specific post
     */
    fun startListeningToPost(postId: String) {
        // Remove existing listener if any
        listeners[postId]?.remove()
        
        Timber.d("Starting real-time engagement listening for post $postId")
        
        listeners[postId] = firestore
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
                            val likeCount = (data["likeCount"] as? Long)?.toInt() ?: 0
                            val commentCount = (data["commentCount"] as? Long)?.toInt() ?: 0
                            val shareCount = (data["shareCount"] as? Long)?.toInt() ?: 0
                            val saveCount = (data["saveCount"] as? Long)?.toInt() ?: 0
                            
                            // Update local database with real-time engagement counts
                            postDao.updateEngagementMetrics(
                                postId = postId,
                                likeCount = likeCount,
                                commentCount = commentCount,
                                shareCount = shareCount,
                                saveCount = saveCount,
                                updatedAt = System.currentTimeMillis()
                            )
                            
                            Timber.d("Updated engagement for post $postId: likes=$likeCount, comments=$commentCount")
                            
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to update engagement metrics for post $postId")
                        }
                    }
                }
            }
    }
    
    /**
     * Start listening to multiple posts at once for batch engagement updates
     */
    fun startListeningToPosts(postIds: List<String>) {
        postIds.forEach { postId ->
            startListeningToPost(postId)
        }
        Timber.d("Started real-time engagement listening for ${postIds.size} posts")
    }
    
    /**
     * Stop listening to real-time updates for a specific post
     */
    fun stopListening(postId: String) {
        listeners[postId]?.remove()
        listeners.remove(postId)
        Timber.d("Stopped real-time engagement listening for post $postId")
    }
    
    /**
     * Stop listening to multiple posts at once
     */
    fun stopListening(postIds: List<String>) {
        postIds.forEach { postId ->
            stopListening(postId)
        }
        Timber.d("Stopped real-time engagement listening for ${postIds.size} posts")
    }
    
    /**
     * Stop all active listeners - call this when user logs out or app is destroyed
     */
    fun stopAllListeners() {
        val activeListenerCount = listeners.size
        listeners.values.forEach { it.remove() }
        listeners.clear()
        
        Timber.d("Stopped all real-time engagement listeners ($activeListenerCount active)")
    }
    
    /**
     * Get the number of active listeners for monitoring
     */
    fun getActiveListenerCount(): Int = listeners.size
    
    /**
     * Check if a specific post is being listened to
     */
    fun isListeningToPost(postId: String): Boolean = listeners.containsKey(postId)
    
    /**
     * Get all post IDs currently being listened to
     */
    fun getListenedPostIds(): Set<String> = listeners.keys.toSet()
}