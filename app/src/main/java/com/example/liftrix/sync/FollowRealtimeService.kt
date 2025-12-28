package com.example.liftrix.sync

import com.example.liftrix.config.OfflineArchitectureFlags
import com.example.liftrix.data.local.dao.SocialProfileDao
import com.example.liftrix.data.local.entity.SocialProfileEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time follow service for instant follower/following count updates.
 * Part of Firebase sync infrastructure from SPEC-20250118-firebase-sync-social.
 * 
 * Provides real-time Firestore listeners for social profile metrics with proper
 * lifecycle management and error handling.
 */
@Singleton
class FollowRealtimeService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val socialProfileDao: SocialProfileDao
) {
    private val scope = CoroutineScope(SupervisorJob())
    private val listeners = mutableMapOf<String, ListenerRegistration>()
    
    /**
     * Start listening to real-time follower count updates for a specific user
     */
    fun startListeningToProfile(userId: String) {
        // Remove existing listener if any
        listeners[userId]?.remove()
        
        Timber.d("Starting real-time follow count listening for user $userId")
        
        listeners[userId] = firestore
            .collection("social_profiles")
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Follow count sync error for user $userId")
                    return@addSnapshotListener
                }
                
                snapshot?.data?.let { data ->
                    scope.launch {
                        try {
                            updateSocialProfileFromRemote(userId, data)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to update follow counts for user $userId")
                        }
                    }
                }
            }
    }
    
    /**
     * Start listening to multiple user profiles at once for batch follow count updates
     */
    fun startListeningToProfiles(userIds: List<String>) {
        userIds.forEach { userId ->
            startListeningToProfile(userId)
        }
        Timber.d("Started real-time follow count listening for ${userIds.size} profiles")
    }

    /**
     * IDEMPOTENT: Update social profile from remote Firestore data.
     * Uses updateStatsFromRemote() for atomic stat updates with timestamp deduplication.
     * Feature-flag gated for rollback support.
     */
    private suspend fun updateSocialProfileFromRemote(userId: String, data: Map<String, Any>) {
        if (OfflineArchitectureFlags.USE_IDEMPOTENT_LISTENERS) {
            // NEW: Room-first idempotent pattern with atomic stat update
            val followerCount = (data["followerCount"] as? Long)?.toInt() ?: 0
            val followingCount = (data["followingCount"] as? Long)?.toInt() ?: 0
            val workoutCount = (data["workoutCount"] as? Long)?.toInt() ?: 0
            val remoteModified = (data["lastModified"] as? Long) ?: System.currentTimeMillis()

            // IDEMPOTENT: Single atomic update with timestamp deduplication + isDirty=false
            socialProfileDao.updateStatsFromRemote(
                userId = userId,
                followerCount = followerCount,
                followingCount = followingCount,
                workoutCount = workoutCount,
                remoteModified = remoteModified
            )
            // NO SYNC TRIGGER - already from Firestore

            Timber.d("✅ IDEMPOTENT: Updated social stats for $userId (Room-first)")
        } else {
            // LEGACY: Direct update (feedback loop risk)
            val followerCount = (data["followerCount"] as? Long)?.toInt() ?: 0
            val followingCount = (data["followingCount"] as? Long)?.toInt() ?: 0
            val workoutCount = (data["workoutCount"] as? Long)?.toInt() ?: 0
            val currentTime = System.currentTimeMillis()

            socialProfileDao.updateFollowerCount(userId, followerCount, currentTime)
            socialProfileDao.updateFollowingCount(userId, followingCount, currentTime)
            socialProfileDao.updateWorkoutCount(userId, workoutCount, currentTime)
            socialProfileDao.updateLastActive(userId, currentTime, currentTime)

            Timber.w("⚠️ LEGACY: Updated social profile for $userId (feedback loop risk)")
        }
    }

    /**
     * Stop listening to real-time updates for a specific user profile
     */
    fun stopListening(userId: String) {
        listeners[userId]?.remove()
        listeners.remove(userId)
        Timber.d("Stopped real-time follow count listening for user $userId")
    }
    
    /**
     * Stop listening to multiple user profiles at once
     */
    fun stopListening(userIds: List<String>) {
        userIds.forEach { userId ->
            stopListening(userId)
        }
        Timber.d("Stopped real-time follow count listening for ${userIds.size} profiles")
    }
    
    /**
     * Stop all active listeners - call this when user logs out or app is destroyed
     */
    fun stopAllListeners() {
        val activeListenerCount = listeners.size
        listeners.values.forEach { it.remove() }
        listeners.clear()
        
        Timber.d("Stopped all real-time follow count listeners ($activeListenerCount active)")
    }
    
    /**
     * Get the number of active listeners for monitoring
     */
    fun getActiveListenerCount(): Int = listeners.size
    
    /**
     * Check if a specific user profile is being listened to
     */
    fun isListeningToProfile(userId: String): Boolean = listeners.containsKey(userId)
    
    /**
     * Get all user IDs currently being listened to
     */
    fun getListenedUserIds(): Set<String> = listeners.keys.toSet()
    
    /**
     * Start listening to follow relationships for real-time follow status updates
     */
    fun startListeningToFollowRelationships(userId: String) {
        val followRelationshipsListenerId = "${userId}_follow_relationships"
        
        // Remove existing listener if any
        listeners[followRelationshipsListenerId]?.remove()
        
        Timber.d("Starting real-time follow relationships listening for user $userId")
        
        listeners[followRelationshipsListenerId] = firestore
            .collection("follow_relationships")
            .whereEqualTo("followerId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Follow relationships sync error for user $userId")
                    return@addSnapshotListener
                }
                
                snapshot?.documents?.let { documents ->
                    scope.launch {
                        try {
                            // Count accepted follow relationships for real-time following count
                            val acceptedCount = documents.count { doc ->
                                doc.getString("status") == "ACCEPTED"
                            }
                            
                            socialProfileDao.updateFollowingCount(
                                userId = userId,
                                count = acceptedCount,
                                updatedAt = System.currentTimeMillis()
                            )
                            
                            Timber.d("Updated following count from relationships for user $userId: following=$acceptedCount")
                            
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to update following count from relationships for user $userId")
                        }
                    }
                }
            }
    }
}