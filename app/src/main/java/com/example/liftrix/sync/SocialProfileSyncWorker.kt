package com.example.liftrix.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import androidx.work.workDataOf
import com.example.liftrix.data.local.dao.SocialProfileDao
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Worker responsible for syncing social profile data to Firebase.
 * Part of Firebase sync infrastructure from SPEC-20250118-firebase-sync-social.
 * 
 * Syncs all social profile fields including privacy settings, stats, and external links
 * with optimistic locking via sync version timestamps.
 */
@HiltWorker
class SocialProfileSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val socialProfileDao: SocialProfileDao,
    private val firestore: FirebaseFirestore
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "social_profile_sync_work"
        const val KEY_SYNC_COUNT = "sync_count"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val MAX_RETRY_COUNT = 3
        
        fun createWorkRequest(userId: String, forceSync: Boolean = false): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<SocialProfileSyncWorker>()
                .setInputData(workDataOf(
                    "userId" to userId,
                    "forceSync" to forceSync
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
                .addTag("social_profile_sync")
                .build()
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val userId = inputData.getString("userId") ?: return@withContext Result.failure(
                Data.Builder()
                    .putString(KEY_ERROR_MESSAGE, "User ID not provided")
                    .build()
            )
            
            val forceSync = inputData.getBoolean("forceSync", false)
            
            Timber.i("[SOCIAL-SYNC] 🔄 Starting social profile sync for user: $userId")
            Timber.d("[SOCIAL-SYNC]   - Force sync: $forceSync")
            Timber.d("[SOCIAL-SYNC]   - Target collection: social_profiles")
            Timber.d("[SOCIAL-SYNC]   - This sync makes user discoverable in search")

            val profile = socialProfileDao.getProfile(userId) 
                ?: return@withContext Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, "Social profile not found for user $userId")
                        .build()
                ).also {
                    Timber.e("[SOCIAL-SYNC] ❌ Social profile not found in local DB for user: $userId")
                    Timber.e("[SOCIAL-SYNC]   - User must create social profile first to be discoverable")
                    Timber.e("[SOCIAL-SYNC]   - Check CreateSocialProfileUseCase execution")
                }
            
            if (profile.isSynced && !forceSync) {
                Timber.d("[SOCIAL-SYNC] ✅ Profile already synced for user $userId, skipping")
                Timber.d("[SOCIAL-SYNC]   - User should already be discoverable in search")
                return@withContext Result.success(
                    Data.Builder()
                        .putInt(KEY_SYNC_COUNT, 0)
                        .build()
                )
            }

            Timber.i("[SOCIAL-SYNC] 📝 Profile found, syncing to Firebase for user: $userId")
            Timber.d("[SOCIAL-SYNC]   - Username: '${profile.username}'")
            Timber.d("[SOCIAL-SYNC]   - Display Name: '${profile.displayName}'")
            Timber.d("[SOCIAL-SYNC]   - Is Private: ${profile.isPrivate}")
            Timber.d("[SOCIAL-SYNC]   - Hide From Suggestions: ${profile.hideFromSuggestions}")
            Timber.d("[SOCIAL-SYNC]   - Allow Friend Requests: ${profile.allowFriendRequests}")
            
            // Log discoverability status
            val isDiscoverable = !profile.isPrivate && !profile.hideFromSuggestions
            if (isDiscoverable) {
                Timber.i("[SOCIAL-SYNC] ✅ Profile will be DISCOVERABLE after sync")
            } else {
                Timber.w("[SOCIAL-SYNC] ⚠️ Profile will NOT be discoverable (private: ${profile.isPrivate}, hidden: ${profile.hideFromSuggestions})")
            }
            
            val docRef = firestore
                .collection("social_profiles")
                .document(userId)
            
            // Complete social profile data as per specification
            val profileData = mapOf(
                "userId" to userId,
                "username" to profile.username,
                "displayName" to profile.displayName,
                "bio" to profile.bio,
                "profilePhotoUrl" to profile.profilePhotoUrl,
                "coverPhotoUrl" to profile.coverPhotoUrl,
                "workoutCount" to profile.workoutCount,
                "followerCount" to profile.followerCount,
                "followingCount" to profile.followingCount,
                "memberSince" to profile.memberSince,
                "lastActive" to FieldValue.serverTimestamp(),
                "isVerified" to profile.isVerified,
                "isPrivate" to profile.isPrivate,
                "hideFromSuggestions" to profile.hideFromSuggestions,
                "allowFriendRequests" to profile.allowFriendRequests,
                "instagramHandle" to profile.instagramHandle,
                "youtubeChannel" to profile.youtubeChannel,
                "personalWebsite" to profile.personalWebsite,
                "createdAt" to profile.createdAt,  // Critical for discovery ordering
                "syncVersion" to System.currentTimeMillis(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            
            Timber.d("[SOCIAL-SYNC] 📦 Uploading profile data to social_profiles/$userId")
            Timber.d("[SOCIAL-SYNC]   - Profile fields: ${profileData.keys}")
            docRef.set(profileData, SetOptions.merge()).await()
            Timber.i("[SOCIAL-SYNC] ✅ Profile data uploaded to Firebase")
            
            // Mark as synced in local database
            Timber.d("[SOCIAL-SYNC] 📋 Updating local sync status for user: $userId")
            socialProfileDao.updateSyncStatus(
                userId = userId,
                isSynced = true,
                version = System.currentTimeMillis().toInt()
            )
            Timber.d("[SOCIAL-SYNC] ✅ Local sync status updated")
            
            Timber.i("[SOCIAL-SYNC] ✅ Successfully synced social profile for user: $userId")
            Timber.i("[SOCIAL-SYNC]   - User is now discoverable in search (if not private/hidden)")
            Timber.i("[SOCIAL-SYNC]   - Profile available in social_profiles collection")
            Timber.i("[SOCIAL-SYNC]   - Local sync status updated to prevent duplicate syncs")
            
            return@withContext Result.success(
                Data.Builder()
                    .putInt(KEY_SYNC_COUNT, 1)
                    .build()
            )
            
        } catch (e: Exception) {
            val userId = inputData.getString("userId") ?: "unknown"
            Timber.e(e, "[SOCIAL-SYNC] ❌ SocialProfileSyncWorker failed for user: $userId")
            Timber.e("[SOCIAL-SYNC]   - Error type: ${e.javaClass.simpleName}")
            Timber.e("[SOCIAL-SYNC]   - User will remain undiscoverable until sync succeeds")
            Timber.e("[SOCIAL-SYNC]   - Retry attempt: ${runAttemptCount + 1}/$MAX_RETRY_COUNT")
            
            return@withContext if (runAttemptCount < MAX_RETRY_COUNT) {
                Timber.w("[SOCIAL-SYNC] 🔄 Retrying sync for user: $userId (attempt ${runAttemptCount + 1}/$MAX_RETRY_COUNT)")
                Result.retry()
            } else {
                Timber.e("[SOCIAL-SYNC] ❌ All retry attempts exhausted for user: $userId")
                Timber.e("[SOCIAL-SYNC]   - User will remain undiscoverable until manual sync")
                Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, e.message ?: "Unknown error")
                        .build()
                )
            }
        }
    }
}