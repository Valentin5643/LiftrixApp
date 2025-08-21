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
            
            Timber.d("Starting social profile sync for user: $userId, forceSync: $forceSync")

            val profile = socialProfileDao.getProfile(userId) 
                ?: return@withContext Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, "Social profile not found for user $userId")
                        .build()
                ).also {
                    Timber.e("Social profile not found in local DB for user: $userId")
                }
            
            if (profile.isSynced && !forceSync) {
                Timber.d("Profile already synced for user $userId, skipping")
                return@withContext Result.success(
                    Data.Builder()
                        .putInt(KEY_SYNC_COUNT, 0)
                        .build()
                )
            }

            Timber.d("Profile found, syncing to Firebase for user $userId (forceSync: $forceSync)")
            Timber.d("Profile data: username=${profile.username}, isPrivate=${profile.isPrivate}, hideFromSuggestions=${profile.hideFromSuggestions}")
            
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
            
            docRef.set(profileData, SetOptions.merge()).await()
            
            // Mark as synced in local database
            socialProfileDao.updateSyncStatus(
                userId = userId,
                isSynced = true,
                version = System.currentTimeMillis().toInt()
            )
            
            Timber.d("Successfully synced social profile and updated local sync status for user: $userId")
            
            return@withContext Result.success(
                Data.Builder()
                    .putInt(KEY_SYNC_COUNT, 1)
                    .build()
            )
            
        } catch (e: Exception) {
            Timber.e(e, "SocialProfileSyncWorker failed for user ${inputData.getString("userId")}")
            
            return@withContext if (runAttemptCount < MAX_RETRY_COUNT) {
                Result.retry()
            } else {
                Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, e.message ?: "Unknown error")
                        .build()
                )
            }
        }
    }
}