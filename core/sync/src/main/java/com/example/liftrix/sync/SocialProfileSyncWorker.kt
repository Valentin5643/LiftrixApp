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
import com.example.liftrix.config.OfflineArchitectureFlags
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.TimeUnit
import com.example.liftrix.data.local.LiftrixDatabase
import android.os.Process

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
    private val database: LiftrixDatabase,
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
            val useDirtyFlagGating = OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
                OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING

            logProfileDbDiagnostics("PROFILE_SYNC_START userId=$userId")
            val preTotalCount = socialProfileDao.getTotalProfileCount()
            val preUserCount = socialProfileDao.getProfileCount(userId)
            Timber.d("PROFILE_SYNC_PRE_COUNTS userId=$userId userCount=$preUserCount totalCount=$preTotalCount")
            
            Timber.i("[SOCIAL-SYNC] 🔄 Starting social profile sync for user: $userId")
            Timber.d("[SOCIAL-SYNC]   - Force sync: $forceSync")
            Timber.d("[SOCIAL-SYNC]   - Target collection: social_profiles")
            Timber.d("[SOCIAL-SYNC]   - This sync makes user discoverable in search")

            val profile = socialProfileDao.getProfile(userId)
            Timber.d(
                "PROFILE_SYNC_LOOKUP userId=$userId found=${profile != null} " +
                    "profileUserId=${profile?.userId} profileUsername=${profile?.username}"
            )

            if (profile == null) {
                val recentProfiles = socialProfileDao.getAllProfilesForDebug()
                val recentProfileIds = recentProfiles.joinToString { it.userId }
                Timber.e(
                    "PROFILE_SYNC_MISSING userId=$userId recentCount=${recentProfiles.size} " +
                        "recentUserIds=[$recentProfileIds]"
                )
                return@withContext Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, "Social profile not found for user $userId")
                        .build()
                ).also {
                    Timber.e("[SOCIAL-SYNC] ❌ Social profile not found in local DB for user: $userId")
                    Timber.e("[SOCIAL-SYNC]   - User must create social profile first to be discoverable")
                    Timber.e("[SOCIAL-SYNC]   - Check CreateSocialProfileUseCase execution")
                }
            }
            
            if (useDirtyFlagGating && !profile.isDirty) {
                Timber.d("[SOCIAL-SYNC] ✅ Profile not dirty for user $userId, skipping (dirty gating enabled)")
                if (forceSync) {
                    Timber.d("[SOCIAL-SYNC]   - Force sync requested but ignored due to dirty gating")
                }
                return@withContext Result.success(
                    Data.Builder()
                        .putInt(KEY_SYNC_COUNT, 0)
                        .build()
                )
            }

            if (!useDirtyFlagGating && profile.isSynced && !forceSync) {
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

            val remoteDoc = docRef.get().await()
            if (remoteDoc.exists()) {
                val remoteLastModified = when (val remoteValue = remoteDoc.get("lastModified")) {
                    is com.google.firebase.Timestamp -> remoteValue.toDate().time
                    is Number -> remoteValue.toLong()
                    else -> 0L
                }
                if (remoteLastModified > profile.lastModified) {
                    val remoteEntity = profile.copy(
                        username = remoteDoc.getString("username") ?: profile.username,
                        displayName = remoteDoc.getString("displayName") ?: profile.displayName,
                        bio = remoteDoc.getString("bio") ?: profile.bio,
                        profilePhotoUrl = remoteDoc.getString("profilePhotoUrl") ?: profile.profilePhotoUrl,
                        coverPhotoUrl = remoteDoc.getString("coverPhotoUrl") ?: profile.coverPhotoUrl,
                        workoutCount = remoteDoc.getLong("workoutCount")?.toInt() ?: profile.workoutCount,
                        followerCount = remoteDoc.getLong("followerCount")?.toInt() ?: profile.followerCount,
                        followingCount = remoteDoc.getLong("followingCount")?.toInt() ?: profile.followingCount,
                        memberSince = remoteDoc.getLong("memberSince") ?: profile.memberSince,
                        lastActive = remoteDoc.getTimestamp("lastActive")?.toDate()?.time ?: profile.lastActive,
                        isVerified = remoteDoc.getBoolean("isVerified") ?: profile.isVerified,
                        isPrivate = remoteDoc.getBoolean("isPrivate") ?: profile.isPrivate,
                        hideFromSuggestions = remoteDoc.getBoolean("hideFromSuggestions")
                            ?: profile.hideFromSuggestions,
                        allowFriendRequests = remoteDoc.getBoolean("allowFriendRequests")
                            ?: profile.allowFriendRequests,
                        instagramHandle = remoteDoc.getString("instagramHandle") ?: profile.instagramHandle,
                        youtubeChannel = remoteDoc.getString("youtubeChannel") ?: profile.youtubeChannel,
                        personalWebsite = remoteDoc.getString("personalWebsite") ?: profile.personalWebsite,
                        isDirty = false,
                        isSynced = true,
                        syncVersion = remoteDoc.getLong("syncVersion")
                            ?: profile.syncVersion,
                        lastModified = remoteLastModified,
                        createdAt = remoteDoc.getLong("createdAt") ?: profile.createdAt,
                        updatedAt = remoteDoc.getTimestamp("updatedAt")?.toDate()?.time ?: profile.updatedAt
                    )
                    socialProfileDao.upsertFromRemote(remoteEntity)
                    Timber.d("[SOCIAL-SYNC] ✅ Remote profile newer; local updated and upload skipped")
                    return@withContext Result.success(
                        Data.Builder()
                            .putInt(KEY_SYNC_COUNT, 0)
                            .build()
                    )
                }
            }
            
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
                "lastModified" to profile.lastModified,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            
            Timber.d("[SOCIAL-SYNC] 📦 Uploading profile data to social_profiles/$userId")
            Timber.d("[SOCIAL-SYNC]   - Profile fields: ${profileData.keys}")
            docRef.set(profileData, SetOptions.merge()).await()
            Timber.i("[SOCIAL-SYNC] ✅ Profile data uploaded to Firebase")
            
            // Mark as synced in local database
            Timber.d("[SOCIAL-SYNC] 📋 Updating local sync status for user: $userId")
            socialProfileDao.markAsClean(
                ids = listOf(userId),
                userId = userId,
                syncVersion = System.currentTimeMillis()
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

    private fun logProfileDbDiagnostics(context: String) {
        val dbName = runCatching { database.openHelper.databaseName }.getOrNull()
        val dbPath = runCatching { database.openHelper.writableDatabase.path }.getOrNull()
        val dbId = System.identityHashCode(database)
        val daoId = System.identityHashCode(socialProfileDao)
        val pid = Process.myPid()
        val threadName = Thread.currentThread().name
        Timber.d(
            "PROFILE_DB_DIAG $context dbName=$dbName dbPath=$dbPath dbId=$dbId daoId=$daoId pid=$pid thread=$threadName"
        )
    }
}
