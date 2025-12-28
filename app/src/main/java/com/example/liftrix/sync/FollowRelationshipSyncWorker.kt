package com.example.liftrix.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import androidx.work.workDataOf
import com.example.liftrix.data.local.dao.FollowRelationshipDao
import com.example.liftrix.data.local.dao.SocialProfileDao
import com.example.liftrix.data.local.dao.SafeFollowRelationshipDaoImpl
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.WriteBatch
import com.example.liftrix.config.OfflineArchitectureFlags
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Worker responsible for syncing follow relationships to Firebase with bidirectional updates.
 * Part of Firebase sync infrastructure from SPEC-20250118-firebase-sync-social.
 * 
 * Handles bidirectional follow sync with proper follower/following count updates
 * and ensures data consistency across all devices.
 */
@HiltWorker
class FollowRelationshipSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val followDao: FollowRelationshipDao,
    private val socialProfileDao: SocialProfileDao,
    private val safeFollowDao: SafeFollowRelationshipDaoImpl,
    private val firestore: FirebaseFirestore
) : CoroutineWorker(context, params) {
    
    // 🔧 HOTFIX: Fallback constructor for when Hilt factory generation fails
    // This allows WorkManager to instantiate the worker via reflection
    // TEMPORARY: Remove once Hilt assisted factories are confirmed working
    constructor(context: Context, params: WorkerParameters) : this(
        context,
        params,
        WorkerServiceLocator.getFollowRelationshipSyncDependencies(context).run {
            Timber.w("⚠️ FollowRelationshipSyncWorker using FALLBACK constructor - Hilt factory failed!")
            return@run this
        }
    )
    
    // Helper constructor to unpack the dependency structure
    private constructor(
        context: Context,
        params: WorkerParameters,
        deps: WorkerServiceLocator.FollowRelationshipSyncDependencies
    ) : this(
        context, params,
        deps.followDao, deps.socialProfileDao, deps.safeFollowDao,
        deps.firestore
    )

    init {
        val processName = getProcessName()
        Timber.d("✅ FollowRelationshipSyncWorker constructed with Hilt dependency injection in process: $processName")
    }
    
    private fun getProcessName(): String {
        return try {
            val pid = android.os.Process.myPid()
            val manager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            manager.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName ?: "unknown"
        } catch (e: Exception) {
            "error: ${e.message}"
        }
    }

    companion object {
        const val WORK_NAME = "follow_relationship_sync_work"
        const val KEY_SYNC_COUNT = "sync_count"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val MAX_RETRY_COUNT = 3
        
        fun createWorkRequest(userId: String, forceSync: Boolean = false, restoreFromFirebase: Boolean = false): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<FollowRelationshipSyncWorker>()
                .setInputData(workDataOf(
                    "userId" to userId,
                    "forceSync" to forceSync,
                    "restoreFromFirebase" to restoreFromFirebase
                ))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .addTag("follow_relationship_sync")
                .build()
        }
        
        /**
         * 🔥 FOLLOW-SYNC-FIX: Creates a work request specifically for restoring follow relationships after login.
         * This should be called when user logs in and local follow data may have been cleared.
         */
        fun createRestoreWorkRequest(userId: String): OneTimeWorkRequest {
            return createWorkRequest(
                userId = userId,
                forceSync = true,
                restoreFromFirebase = true
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val userId = inputData.getString("userId") ?: run {
                return@withContext Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, "User ID not provided")
                        .build()
                )
            }
            
            val forceSync = inputData.getBoolean("forceSync", false)
            val restoreFromFirebase = inputData.getBoolean("restoreFromFirebase", false)
            val useDirtyFlagGating = OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
                OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING

            // 🔥 FOLLOW-SYNC-FIX: Check if we need to restore relationships from Firebase first
            if (restoreFromFirebase) {
                Timber.d("🔥 FOLLOW-SYNC-FIX: Restoring follow relationships from Firebase for user $userId")
                val restoredCount = restoreFollowRelationshipsFromFirebase(userId)
                Timber.d("🔥 FOLLOW-SYNC-FIX: Restored $restoredCount follow relationships from Firebase")
            }

            val unsyncedRelationships = if (useDirtyFlagGating) {
                followDao.getDirtyFollowRelationships(userId)
            } else {
                followDao.getUnsyncedRelationships(userId)
            }
            
            // 🔥 FOLLOW-SYNC-FIX: For login scenarios, always check Firebase even if no local unsynced data
            if (unsyncedRelationships.isEmpty() && !forceSync && !restoreFromFirebase) {
                // Check if user has any local relationships at all
                val localFollowingCount = followDao.getFollowingCount(userId)
                val localFollowerCount = followDao.getFollowerCount(userId)
                
                if (localFollowingCount == 0 && localFollowerCount == 0) {
                    Timber.w("🔥 FOLLOW-SYNC-FIX: No local relationships found, attempting Firebase restore for user $userId")
                    val restoredCount = restoreFollowRelationshipsFromFirebase(userId)
                    if (restoredCount > 0) {
                        return@withContext Result.success(
                            Data.Builder()
                                .putInt(KEY_SYNC_COUNT, restoredCount)
                                .putString("operation", "restore_from_firebase")
                                .build()
                        )
                    }
                }
                
                Timber.d("No unsynced follow relationships found for user $userId")
                return@withContext Result.success(
                    Data.Builder()
                        .putInt(KEY_SYNC_COUNT, 0)
                        .build()
                )
            }

            Timber.d("Syncing ${unsyncedRelationships.size} follow relationships for user $userId (dirty gating: $useDirtyFlagGating)")
            
            // Process relationships in batches of 10 for efficient Firestore writes
            val processed = unsyncedRelationships.chunked(10).fold(0) { total, batch ->
                syncRelationshipBatch(batch, userId) + total
            }
            
            Timber.d("Successfully synced $processed follow relationships for user $userId")
            
            return@withContext Result.success(
                Data.Builder()
                    .putInt(KEY_SYNC_COUNT, processed)
                    .build()
            )
            
        } catch (e: Exception) {
            Timber.e(e, "FollowRelationshipSyncWorker failed for user ${inputData.getString("userId")}")
            
            val result = if (runAttemptCount < MAX_RETRY_COUNT) {
                Result.retry()
            } else {
                Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, e.message ?: "Unknown error")
                        .build()
                )
            }
            return@withContext result
        }
    }
    
    private suspend fun syncRelationshipBatch(
        relationships: List<com.example.liftrix.data.local.entity.FollowRelationshipEntity>,
        userId: String
    ): Int {
        val relationshipsToUpload = mutableListOf<com.example.liftrix.data.local.entity.FollowRelationshipEntity>()

        for (relationship in relationships) {
            val docRef = firestore
                .collection("follow_relationships")
                .document(relationship.id)

            val remoteDoc = docRef.get().await()
            if (remoteDoc.exists()) {
                val remoteLastModified = when (val remoteValue = remoteDoc.get("lastModified")) {
                    is com.google.firebase.Timestamp -> remoteValue.toDate().time
                    is Number -> remoteValue.toLong()
                    else -> 0L
                }
                if (remoteLastModified > relationship.lastModified) {
                    val remoteEntity = relationship.copy(
                        followerId = remoteDoc.getString("followerId") ?: relationship.followerId,
                        followingId = remoteDoc.getString("followingId") ?: relationship.followingId,
                        status = remoteDoc.getString("status") ?: relationship.status,
                        createdAt = remoteDoc.getLong("createdAt") ?: relationship.createdAt,
                        acceptedAt = remoteDoc.getLong("acceptedAt") ?: relationship.acceptedAt,
                        blockedAt = remoteDoc.getLong("blockedAt") ?: relationship.blockedAt,
                        isDirty = false,
                        isSynced = true,
                        syncVersion = (remoteDoc.getLong("syncVersion")
                            ?: relationship.syncVersion.toLong()).toInt(),
                        lastModified = remoteLastModified
                    )
                    followDao.upsertFromRemote(remoteEntity)
                    continue
                }
            }

            relationshipsToUpload.add(relationship)
        }

        if (relationshipsToUpload.isEmpty()) {
            return 0
        }

        val batch: WriteBatch = firestore.batch()
        val timestamp = System.currentTimeMillis()
        
        relationshipsToUpload.forEach { relationship ->
            // Add relationship document to Firestore
            val docRef = firestore
                .collection("follow_relationships")
                .document(relationship.id)
            
            val relationshipData = mapOf(
                "id" to relationship.id,
                "followerId" to relationship.followerId,
                "followingId" to relationship.followingId,
                "status" to relationship.status,
                "createdAt" to relationship.createdAt,
                "acceptedAt" to relationship.acceptedAt,
                "syncVersion" to timestamp,
                "lastModified" to relationship.lastModified,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            
            batch.set(docRef, relationshipData)
            
            // Update follower counts in social profiles for ACCEPTED relationships
            if (relationship.status == "ACCEPTED") {
                val followerRef = firestore
                    .collection("social_profiles")
                    .document(relationship.followerId)
                val followingRef = firestore
                    .collection("social_profiles")
                    .document(relationship.followingId)
                
                batch.update(followerRef, mapOf(
                    "followingCount" to FieldValue.increment(1),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
                batch.update(followingRef, mapOf(
                    "followerCount" to FieldValue.increment(1),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            }
        }
        
        // Commit the batch
        batch.commit().await()
        
        // Mark relationships as synced in local database
        followDao.markAsClean(
            ids = relationshipsToUpload.map { it.id },
            userId = userId,
            syncVersion = timestamp
        )
        
        return relationshipsToUpload.size
    }
    
    /**
     * 🔥 FOLLOW-SYNC-FIX: Restores follow relationships from Firebase when local database is empty.
     * This method is called during login to restore previously synced relationships that may have
     * been cleared during logout.
     * 
     * @param userId The user ID to restore relationships for
     * @return The number of relationships restored from Firebase
     */
    private suspend fun restoreFollowRelationshipsFromFirebase(userId: String): Int {
        return try {
            Timber.d("🔥 FOLLOW-SYNC-FIX: Starting Firebase restoration for user $userId")
            
            val currentTime = System.currentTimeMillis()
            val restoredRelationships = mutableListOf<com.example.liftrix.data.local.entity.FollowRelationshipEntity>()
            
            // Fetch relationships where user is the follower (following others)
            val followingQuery = firestore.collection("follow_relationships")
                .whereEqualTo("followerId", userId)
                .whereEqualTo("status", "ACCEPTED")
                .get()
                .await()
            
            Timber.d("🔥 FOLLOW-SYNC-FIX: Found ${followingQuery.documents.size} following relationships in Firebase")
            
            for (doc in followingQuery.documents) {
                val data = doc.data ?: continue
                try {
                    val remoteLastModified = when (val remoteValue = data["lastModified"]) {
                        is com.google.firebase.Timestamp -> remoteValue.toDate().time
                        is Number -> remoteValue.toLong()
                        else -> currentTime
                    }
                    val relationship = com.example.liftrix.data.local.entity.FollowRelationshipEntity(
                        id = doc.id,
                        followerId = data["followerId"] as String,
                        followingId = data["followingId"] as String,
                        status = data["status"] as String,
                        createdAt = (data["createdAt"] as? Long) ?: currentTime,
                        acceptedAt = (data["acceptedAt"] as? Long),
                        blockedAt = (data["blockedAt"] as? Long),
                        isSynced = true, // Mark as synced since we're fetching from Firebase
                        syncVersion = (data["syncVersion"] as? Long)?.toInt() ?: 1,
                        lastModified = remoteLastModified,
                        isDirty = false
                    )
                    restoredRelationships.add(relationship)
                } catch (e: Exception) {
                    Timber.w(e, "🔥 FOLLOW-SYNC-FIX: Failed to parse following relationship ${doc.id}")
                }
            }
            
            // Fetch relationships where user is being followed (followers)
            val followersQuery = firestore.collection("follow_relationships")
                .whereEqualTo("followingId", userId)
                .whereEqualTo("status", "ACCEPTED")
                .get()
                .await()
            
            Timber.d("🔥 FOLLOW-SYNC-FIX: Found ${followersQuery.documents.size} follower relationships in Firebase")
            
            for (doc in followersQuery.documents) {
                val data = doc.data ?: continue
                // Skip if we already have this relationship from the following query
                if (restoredRelationships.any { it.id == doc.id }) continue
                
                try {
                    val remoteLastModified = when (val remoteValue = data["lastModified"]) {
                        is com.google.firebase.Timestamp -> remoteValue.toDate().time
                        is Number -> remoteValue.toLong()
                        else -> currentTime
                    }
                    val relationship = com.example.liftrix.data.local.entity.FollowRelationshipEntity(
                        id = doc.id,
                        followerId = data["followerId"] as String,
                        followingId = data["followingId"] as String,
                        status = data["status"] as String,
                        createdAt = (data["createdAt"] as? Long) ?: currentTime,
                        acceptedAt = (data["acceptedAt"] as? Long),
                        blockedAt = (data["blockedAt"] as? Long),
                        isSynced = true,
                        syncVersion = (data["syncVersion"] as? Long)?.toInt() ?: 1,
                        lastModified = remoteLastModified,
                        isDirty = false
                    )
                    restoredRelationships.add(relationship)
                } catch (e: Exception) {
                    Timber.w(e, "🔥 FOLLOW-SYNC-FIX: Failed to parse follower relationship ${doc.id}")
                }
            }
            
            // Batch insert all restored relationships using safe method
            if (restoredRelationships.isNotEmpty()) {
                val insertedCount = safeFollowDao.insertFollowRelationshipsWithUserValidation(restoredRelationships)
                Timber.i("🔥 FOLLOW-SYNC-FIX: Successfully restored $insertedCount/${restoredRelationships.size} follow relationships from Firebase with user validation")

                restoredRelationships.forEach { relationship ->
                    followDao.upsertFromRemote(relationship)
                }
                
                // 🔥 FIX: Actively fetch and cache missing user profiles for restored relationships
                val allUserIds = restoredRelationships.flatMap { listOf(it.followerId, it.followingId) }.distinct()
                val missingUserIds = mutableListOf<String>()
                
                for (checkUserId in allUserIds) {
                    if (checkUserId != userId) {
                        val profileExists = socialProfileDao.getSocialProfileByUserId(checkUserId) != null
                        if (!profileExists) {
                            Timber.d("🔥 FOLLOW-SYNC-FIX: Social profile missing for $checkUserId, will fetch from Firebase")
                            missingUserIds.add(checkUserId)
                        }
                    }
                }
                
                // Fetch missing user profiles in parallel
                if (missingUserIds.isNotEmpty()) {
                    val profileFetchCount = fetchMissingUserProfiles(missingUserIds)
                    Timber.i("🔥 FOLLOW-SYNC-FIX: Fetched $profileFetchCount/${missingUserIds.size} missing user profiles from Firebase")
                }
            } else {
                Timber.d("🔥 FOLLOW-SYNC-FIX: No follow relationships found in Firebase for user $userId")
            }
            
            restoredRelationships.size
            
        } catch (e: Exception) {
            Timber.e(e, "🔥 FOLLOW-SYNC-FIX: Failed to restore follow relationships from Firebase for user $userId")
            0
        }
    }
    
    /**
     * 🔥 FIX: Fetches and caches missing user profiles from Firebase in parallel batches.
     * This ensures that follow relationships always have corresponding user profile data
     * for proper display in followers/following lists.
     * 
     * @param userIds List of user IDs to fetch profiles for
     * @return The number of profiles successfully fetched and cached
     */
    private suspend fun fetchMissingUserProfiles(userIds: List<String>): Int {
        return try {
            Timber.d("🔥 FOLLOW-SYNC-FIX: Fetching ${userIds.size} missing user profiles from Firebase")
            var successCount = 0
            
            // Process in batches of 10 to avoid overwhelming Firebase and the local database
            userIds.chunked(10).forEach { batch ->
                batch.forEach { userId ->
                    try {
                        // Check social_profiles collection first
                        val socialProfileDoc = firestore.collection("social_profiles")
                            .document(userId)
                            .get()
                            .await()
                        
                        if (socialProfileDoc.exists()) {
                            val data = socialProfileDoc.data ?: return@forEach
                            val currentTime = System.currentTimeMillis()
                            
                            // Create social profile from Firebase data
                            val socialProfile = com.example.liftrix.data.local.entity.SocialProfileEntity(
                                userId = userId,
                                username = data["username"] as? String ?: "user_$userId",
                                displayName = data["displayName"] as? String ?: data["display_name"] as? String,
                                bio = data["bio"] as? String,
                                profilePhotoUrl = data["profilePhotoUrl"] as? String ?: data["profileImageUrl"] as? String,
                                coverPhotoUrl = data["coverPhotoUrl"] as? String,
                                workoutCount = (data["workoutCount"] as? Number)?.toInt() ?: 0,
                                followerCount = (data["followerCount"] as? Number)?.toInt() ?: 0,
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
                                isSynced = true, 
                                syncVersion = 1,
                                createdAt = (data["createdAt"] as? Number)?.toLong() ?: currentTime,
                                updatedAt = currentTime
                            )
                            
                            // Insert social profile
                            try {
                                socialProfileDao.insertProfile(socialProfile)
                                successCount++
                                Timber.d("🔥 FOLLOW-SYNC-FIX: Created social profile for user $userId")
                            } catch (e: Exception) {
                                // Try update if insert fails
                                try {
                                    socialProfileDao.updateProfile(socialProfile)
                                    successCount++
                                    Timber.d("🔥 FOLLOW-SYNC-FIX: Updated social profile for user $userId")
                                } catch (updateError: Exception) {
                                    Timber.w(updateError, "🔥 FOLLOW-SYNC-FIX: Failed to insert/update social profile for user $userId")
                                }
                            }
                        } else {
                            // Fallback: try social_profiles collection
                            val publicProfileDoc = firestore.collection("social_profiles")
                                .document(userId)
                                .get()
                                .await()
                            
                            if (publicProfileDoc.exists()) {
                                val data = publicProfileDoc.data ?: return@forEach
                                val currentTime = System.currentTimeMillis()
                                
                                // Create minimal social profile from public data
                                val socialProfile = com.example.liftrix.data.local.entity.SocialProfileEntity(
                                    userId = userId,
                                    username = data["username"] as? String ?: "user_$userId",
                                    displayName = data["displayName"] as? String ?: "User",
                                    bio = data["bio"] as? String,
                                    profilePhotoUrl = data["profileImageUrl"] as? String,
                                    coverPhotoUrl = null,
                                    workoutCount = (data["totalWorkouts"] as? Number)?.toInt() ?: 0,
                                    followerCount = (data["followersCount"] as? Number)?.toInt() ?: 0,
                                    followingCount = (data["followingCount"] as? Number)?.toInt() ?: 0,
                                    memberSince = currentTime,
                                    lastActive = currentTime,
                                    isVerified = data["isVerified"] as? Boolean ?: false,
                                    isPrivate = data["isPrivate"] as? Boolean ?: false,
                                    hideFromSuggestions = false,
                                    allowFriendRequests = true,
                                    instagramHandle = null,
                                    youtubeChannel = null,
                                    personalWebsite = null,
                                    isSynced = true,
                                    syncVersion = 1,
                                    createdAt = currentTime,
                                    updatedAt = currentTime
                                )
                                
                                // Insert social profile
                                try {
                                    socialProfileDao.insertProfile(socialProfile)
                                    successCount++
                                    Timber.d("🔥 FOLLOW-SYNC-FIX: Created social profile from public data for user $userId")
                                } catch (e: Exception) {
                                    try {
                                        socialProfileDao.updateProfile(socialProfile)
                                        successCount++
                                        Timber.d("🔥 FOLLOW-SYNC-FIX: Updated social profile from public data for user $userId")
                                    } catch (updateError: Exception) {
                                        Timber.w(updateError, "🔥 FOLLOW-SYNC-FIX: Failed to insert/update social profile from public data for user $userId")
                                    }
                                }
                            } else {
                                Timber.w("🔥 FOLLOW-SYNC-FIX: No profile data found in Firebase for user $userId")
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "🔥 FOLLOW-SYNC-FIX: Failed to fetch profile for user $userId")
                    }
                }
            }
            
            Timber.i("🔥 FOLLOW-SYNC-FIX: Successfully fetched and cached $successCount/${userIds.size} user profiles")
            successCount
            
        } catch (e: Exception) {
            Timber.e(e, "🔥 FOLLOW-SYNC-FIX: Failed to fetch missing user profiles")
            0
        }
    }
}
