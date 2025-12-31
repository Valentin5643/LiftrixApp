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
import com.example.liftrix.data.local.dao.GymBuddyDao
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
 * Worker responsible for syncing gym buddy relationships to Firebase with 5 buddy limit enforcement.
 * Part of Firebase sync infrastructure from SPEC-20250118-firebase-sync-social.
 * 
 * Creates bidirectional gym buddy relationships and enforces the 5 buddy limit
 * as per gym buddy system specifications.
 */
@HiltWorker
class GymBuddySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val gymBuddyDao: GymBuddyDao,
    private val firestore: FirebaseFirestore
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "gym_buddy_sync_work"
        const val KEY_SYNC_COUNT = "sync_count"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val MAX_RETRY_COUNT = 3
        private const val MAX_GYM_BUDDIES = 5 // Enforce 5 buddy limit
        
        fun createWorkRequest(userId: String, forceSync: Boolean = false): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<GymBuddySyncWorker>()
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
                .addTag("gym_buddy_sync")
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
            val unsyncedBuddies = if (useDirtyFlagGating) {
                gymBuddyDao.getDirtyGymBuddies(userId)
            } else {
                gymBuddyDao.getUnsyncedGymBuddies(userId)
            }
            
            if (unsyncedBuddies.isEmpty() && !forceSync) {
                Timber.d("No unsynced gym buddies found for user $userId")
                return@withContext Result.success(
                    Data.Builder()
                        .putInt(KEY_SYNC_COUNT, 0)
                        .build()
                )
            }

            // Enforce 5 buddy limit as per specification
            if (unsyncedBuddies.size > MAX_GYM_BUDDIES) {
                Timber.w("User $userId has more than $MAX_GYM_BUDDIES gym buddies, limiting sync to first $MAX_GYM_BUDDIES")
            }

            val buddiesToSync = unsyncedBuddies.take(MAX_GYM_BUDDIES)
            
            Timber.d("Syncing ${buddiesToSync.size} gym buddies for user $userId")
            
            // Create bidirectional relationships in Firebase
            val processed = syncGymBuddyBatch(buddiesToSync, userId)
            
            Timber.d("Successfully synced $processed gym buddy relationships for user $userId")
            
            return@withContext Result.success(
                Data.Builder()
                    .putInt(KEY_SYNC_COUNT, processed)
                    .build()
            )
            
        } catch (e: Exception) {
            Timber.e(e, "GymBuddySyncWorker failed for user ${inputData.getString("userId")}")
            
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
    
    private suspend fun syncGymBuddyBatch(
        buddies: List<com.example.liftrix.data.local.entity.GymBuddyEntity>,
        userId: String
    ): Int {
        val buddiesToUpload = mutableListOf<com.example.liftrix.data.local.entity.GymBuddyEntity>()
        val collectionRef = firestore
            .collection("users")
            .document(userId)
            .collection("gym_buddies")
        val remoteDocs = FirestorePrefetcher.prefetchByIds(
            collection = collectionRef,
            ids = buddies.map { it.buddyId }
        )

        for (buddy in buddies) {
            val remoteDoc = remoteDocs[buddy.buddyId]
            if (remoteDoc?.exists() == true) {
                val remoteLastModified = when (val remoteValue = remoteDoc.get("lastModified")) {
                    is com.google.firebase.Timestamp -> remoteValue.toDate().time
                    is Number -> remoteValue.toLong()
                    else -> 0L
                }
                if (remoteLastModified > buddy.lastModified) {
                    val remoteEntity = buddy.copy(
                        buddyNickname = remoteDoc.getString("nickname") ?: buddy.buddyNickname,
                        pairedViaQr = remoteDoc.getBoolean("pairedViaQr") ?: buddy.pairedViaQr,
                        pairingLocation = remoteDoc.getString("pairingLocation") ?: buddy.pairingLocation,
                        createdAt = remoteDoc.getLong("createdAt") ?: buddy.createdAt,
                        lastPrNotificationSent = remoteDoc.getLong("lastPrNotificationSent") ?: buddy.lastPrNotificationSent,
                        notificationCooldownHours = remoteDoc.getLong("notificationCooldownHours")?.toInt()
                            ?: buddy.notificationCooldownHours,
                        isDirty = false,
                        isSynced = true,
                        syncVersion = remoteDoc.getLong("syncVersion") ?: buddy.syncVersion,
                        lastModified = remoteLastModified
                    )
                    gymBuddyDao.upsertFromRemote(remoteEntity)
                    continue
                }
            }

            buddiesToUpload.add(buddy)
        }

        if (buddiesToUpload.isEmpty()) {
            return 0
        }

        val batch: WriteBatch = firestore.batch()
        val timestamp = System.currentTimeMillis()
        
        buddiesToUpload.forEach { buddy ->
            // Create bidirectional relationship as per specification
            val userBuddyRef = collectionRef.document(buddy.buddyId)
            
            val buddyUserRef = firestore
                .collection("users")
                .document(buddy.buddyId)
                .collection("gym_buddies")
                .document(userId)
            
            // Data for user -> buddy relationship
            val userBuddyData = mapOf(
                "buddyId" to buddy.buddyId,
                "nickname" to buddy.buddyNickname,
                "pairedViaQr" to buddy.pairedViaQr,
                "pairingLocation" to buddy.pairingLocation,
                "prNotificationsEnabled" to true, // Default to enabled
                "createdAt" to buddy.createdAt,
                "syncVersion" to timestamp,
                "lastModified" to buddy.lastModified,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            
            // Data for buddy -> user relationship (reciprocal)
            val buddyUserData = mapOf(
                "buddyId" to userId,
                "pairedViaQr" to buddy.pairedViaQr,
                "pairingLocation" to buddy.pairingLocation,
                "prNotificationsEnabled" to true, // Default to enabled
                "createdAt" to buddy.createdAt,
                "syncVersion" to timestamp,
                "lastModified" to buddy.lastModified,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            
            batch.set(userBuddyRef, userBuddyData)
            batch.set(buddyUserRef, buddyUserData)
        }
        
        // Commit the batch
        batch.commit().await()
        
        // Mark buddies as synced in local database
        gymBuddyDao.markAsClean(
            ids = buddiesToUpload.map { it.id },
            userId = userId,
            syncVersion = timestamp
        )
        
        return buddiesToUpload.size
    }
}
