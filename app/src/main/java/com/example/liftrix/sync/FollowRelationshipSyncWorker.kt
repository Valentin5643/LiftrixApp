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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.WriteBatch
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
    private val firestore: FirebaseFirestore
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "follow_relationship_sync_work"
        const val KEY_SYNC_COUNT = "sync_count"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val MAX_RETRY_COUNT = 3
        
        fun createWorkRequest(userId: String, forceSync: Boolean = false): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<FollowRelationshipSyncWorker>()
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
                .addTag("follow_relationship_sync")
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

            val unsyncedRelationships = followDao.getUnsyncedRelationships(userId)
            
            if (unsyncedRelationships.isEmpty() && !forceSync) {
                Timber.d("No unsynced follow relationships found for user $userId")
                return@withContext Result.success(
                    Data.Builder()
                        .putInt(KEY_SYNC_COUNT, 0)
                        .build()
                )
            }

            Timber.d("Syncing ${unsyncedRelationships.size} follow relationships for user $userId")
            
            // Process relationships in batches of 10 for efficient Firestore writes
            val processed = unsyncedRelationships.chunked(10).fold(0) { total, batch ->
                syncRelationshipBatch(batch) + total
            }
            
            Timber.d("Successfully synced $processed follow relationships for user $userId")
            
            return@withContext Result.success(
                Data.Builder()
                    .putInt(KEY_SYNC_COUNT, processed)
                    .build()
            )
            
        } catch (e: Exception) {
            Timber.e(e, "FollowRelationshipSyncWorker failed for user ${inputData.getString("userId")}")
            
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
    
    private suspend fun syncRelationshipBatch(relationships: List<com.example.liftrix.data.local.entity.FollowRelationshipEntity>): Int {
        val batch: WriteBatch = firestore.batch()
        val timestamp = System.currentTimeMillis()
        
        relationships.forEach { relationship ->
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
        relationships.forEach { relationship ->
            followDao.updateSyncStatus(
                relationshipId = relationship.id,
                isSynced = true,
                version = timestamp.toInt()
            )
        }
        
        return relationships.size
    }
}