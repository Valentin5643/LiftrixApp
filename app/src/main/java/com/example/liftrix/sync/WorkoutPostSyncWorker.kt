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
import com.example.liftrix.data.local.dao.WorkoutPostDao
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.WriteBatch
import com.google.gson.Gson
import com.google.gson.JsonElement
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Worker responsible for syncing workout posts to Firebase with all engagement metrics.
 * Part of Firebase sync infrastructure from SPEC-20250118-firebase-sync-social.
 * 
 * Syncs workout posts with media URLs, engagement counts, and metadata
 * in batches for optimal performance.
 */
@HiltWorker
class WorkoutPostSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val postDao: WorkoutPostDao,
    private val firestore: FirebaseFirestore,
    private val gson: Gson
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "workout_post_sync_work"
        const val KEY_SYNC_COUNT = "sync_count"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val MAX_RETRY_COUNT = 3
        
        fun createWorkRequest(userId: String, forceSync: Boolean = false): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<WorkoutPostSyncWorker>()
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
                .addTag("workout_post_sync")
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

            val unsyncedPosts = postDao.getUnsyncedPosts(userId)
            
            if (unsyncedPosts.isEmpty() && !forceSync) {
                Timber.d("No unsynced workout posts found for user $userId")
                return@withContext Result.success(
                    Data.Builder()
                        .putInt(KEY_SYNC_COUNT, 0)
                        .build()
                )
            }

            Timber.d("Syncing ${unsyncedPosts.size} workout posts for user $userId")
            
            // Process posts in batches of 10 to avoid Firestore batch write limits
            val processed = unsyncedPosts.chunked(10).fold(0) { total, batch ->
                syncPostBatch(batch) + total
            }
            
            Timber.d("Successfully synced $processed workout posts for user $userId")
            
            return@withContext Result.success(
                Data.Builder()
                    .putInt(KEY_SYNC_COUNT, processed)
                    .build()
            )
            
        } catch (e: Exception) {
            Timber.e(e, "WorkoutPostSyncWorker failed for user ${inputData.getString("userId")}")
            
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
    
    private suspend fun syncPostBatch(posts: List<com.example.liftrix.data.local.entity.WorkoutPostEntity>): Int {
        val batch: WriteBatch = firestore.batch()
        val timestamp = System.currentTimeMillis()
        
        posts.forEach { post ->
            val docRef = firestore
                .collection("workout_posts")
                .document(post.id)
            
            // Parse media URLs JSON safely
            val mediaUrls: JsonElement? = post.mediaUrls?.let { 
                try {
                    gson.fromJson(it, JsonElement::class.java)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse mediaUrls JSON for post ${post.id}")
                    null
                }
            }
            
            // Complete post data with all engagement metrics as per specification
            val postData = mapOf(
                "id" to post.id,
                "userId" to post.userId,
                "workoutId" to post.workoutId,
                "caption" to post.caption,
                "mediaUrls" to mediaUrls,
                "visibility" to post.visibility,
                "likeCount" to post.likeCount,
                "commentCount" to post.commentCount,
                "shareCount" to post.shareCount,
                "saveCount" to post.saveCount,
                "createdAt" to post.createdAt,
                "updatedAt" to FieldValue.serverTimestamp(),
                "syncVersion" to timestamp
            )
            
            batch.set(docRef, postData, SetOptions.merge())
        }
        
        // Commit the batch
        batch.commit().await()
        
        // Mark posts as synced in local database
        posts.forEach { post ->
            postDao.markAsSynced(
                postId = post.id,
                syncVersion = timestamp.toInt()
            )
        }
        
        return posts.size
    }
}