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
import com.example.liftrix.config.OfflineArchitectureFlags
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
) : BaseSyncWorker(context, params) {

    init {
        Timber.d("✅ WorkoutPostSyncWorker constructed with Hilt dependency injection")
    }

    override val workerName: String = "WorkoutPostSyncWorker"

    companion object {
        const val WORK_NAME = "workout_post_sync_work"
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
                .addTag("user_$userId") // 🔥 NEW: User-specific tagging for job management
                .build()
        }
        
        /**
         * 🔥 NEW: Get unique work name per user to prevent job conflicts
         */
        fun getWorkName(userId: String): String = "${WORK_NAME}_$userId"
    }

    override suspend fun performSync(userId: String): Result {
        try {
            // Check cancellation before starting
            checkCancellation()
            
            val forceSync = inputData.getBoolean("forceSync", false)

            // 🔥 ENHANCED: Comprehensive logging for workout post sync
            Timber.i("[POST-SYNC] 🔍 Starting WorkoutPostSyncWorker for user $userId (forceSync: $forceSync)")
            
            val useDirtyFlagGating = OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
                OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING
            val unsyncedPosts = if (useDirtyFlagGating) {
                postDao.getDirtyPosts(userId)
            } else {
                postDao.getUnsyncedPosts(userId)
            }
            
            if (unsyncedPosts.isEmpty() && !forceSync) {
                Timber.i("[POST-SYNC] ✅ No unsynced workout posts found for user $userId")
                return Result.success(
                    Data.Builder()
                        .putInt(KEY_SYNC_COUNT, 0)
                        .build()
                )
            }

            Timber.i("[POST-SYNC] 📊 Found ${unsyncedPosts.size} workout posts to sync for user $userId (dirty gating: $useDirtyFlagGating)")
            unsyncedPosts.forEachIndexed { index, post ->
                Timber.d("[POST-SYNC]   - Post ${index + 1}: ${post.id} (workout: ${post.workoutId})")
                Timber.d("[POST-SYNC]     - Caption: '${post.caption?.take(50) ?: "No caption"}${if (post.caption?.length ?: 0 > 50) "..." else ""}'")
                Timber.d("[POST-SYNC]     - Visibility: ${post.visibility}")
                Timber.d("[POST-SYNC]     - Engagement: ${post.likeCount} likes, ${post.commentCount} comments")
            }
            
            // Use batch processing with cancellation checks
            var processed = 0
            processBatchesWithCancellation(
                items = unsyncedPosts,
                batchSize = 10
            ) { batch ->
                processed += syncPostBatch(batch)
            }
            
            Timber.i("[POST-SYNC] ✅ Successfully synced $processed/${unsyncedPosts.size} workout posts for user $userId")
            
            return Result.success(
                Data.Builder()
                    .putInt(KEY_SYNC_COUNT, processed)
                    .build()
            )
            
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Re-throw cancellation to maintain cancellation chain
            throw e
        } catch (e: Exception) {
            // Let base class handle the error
            throw e
        }
    }
    
    private suspend fun syncPostBatch(posts: List<com.example.liftrix.data.local.entity.WorkoutPostEntity>): Int {
        val postsToUpload = mutableListOf<com.example.liftrix.data.local.entity.WorkoutPostEntity>()
        val collectionRef = firestore.collection("workout_posts")
        val remoteDocs = FirestorePrefetcher.prefetchByIds(
            collection = collectionRef,
            ids = posts.map { it.id }
        )

        for (post in posts) {
            val remoteDoc = remoteDocs[post.id]
            if (remoteDoc?.exists() == true) {
                val remoteLastModified = when (val remoteValue = remoteDoc.get("lastModified")) {
                    is com.google.firebase.Timestamp -> remoteValue.toDate().time
                    is Number -> remoteValue.toLong()
                    else -> 0L
                }
                if (remoteLastModified > post.lastModified) {
                    val mediaUrlsJson = when (val remoteMediaUrls = remoteDoc.get("mediaUrls")) {
                        is List<*> -> gson.toJson(remoteMediaUrls)
                        is String -> remoteMediaUrls
                        else -> post.mediaUrls
                    }
                    val thumbnailsJson = when (val remoteMediaThumbnails = remoteDoc.get("mediaThumbnails")) {
                        is List<*> -> gson.toJson(remoteMediaThumbnails)
                        is String -> remoteMediaThumbnails
                        else -> post.mediaThumbnails
                    }
                    val remoteEntity = post.copy(
                        caption = remoteDoc.getString("caption") ?: post.caption,
                        mediaUrls = mediaUrlsJson,
                        mediaThumbnails = thumbnailsJson,
                        workoutDuration = remoteDoc.getLong("workoutDuration")?.toInt() ?: post.workoutDuration,
                        totalVolume = remoteDoc.getDouble("totalVolume") ?: post.totalVolume,
                        exercisesCount = remoteDoc.getLong("exercisesCount")?.toInt() ?: post.exercisesCount,
                        prsCount = remoteDoc.getLong("prsCount")?.toInt() ?: post.prsCount,
                        likeCount = remoteDoc.getLong("likeCount")?.toInt() ?: post.likeCount,
                        commentCount = remoteDoc.getLong("commentCount")?.toInt() ?: post.commentCount,
                        shareCount = remoteDoc.getLong("shareCount")?.toInt() ?: post.shareCount,
                        saveCount = remoteDoc.getLong("saveCount")?.toInt() ?: post.saveCount,
                        visibility = remoteDoc.getString("visibility") ?: post.visibility,
                        createdAt = remoteDoc.getLong("createdAt") ?: post.createdAt,
                        updatedAt = remoteDoc.getTimestamp("updatedAt")?.toDate()?.time ?: post.updatedAt,
                        isDirty = false,
                        isSynced = true,
                        syncVersion = remoteDoc.getLong("syncVersion") ?: post.syncVersion,
                        lastModified = remoteLastModified
                    )
                    postDao.upsertFromRemote(remoteEntity)
                    continue
                }
            }

            postsToUpload.add(post)
        }

        if (postsToUpload.isEmpty()) {
            return 0
        }

        val batch: WriteBatch = firestore.batch()
        val timestamp = System.currentTimeMillis()
        
        // 🔥 ENHANCED: Log batch processing start
        Timber.d("[POST-BATCH] 🚀 Processing batch of ${postsToUpload.size} workout posts")
        
        postsToUpload.forEach { post ->
            val docRef = collectionRef.document(post.id)
            
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
                "syncVersion" to timestamp,
                "lastModified" to post.lastModified
            )
            
            batch.set(docRef, postData, SetOptions.merge())
        }
        
        // 🔥 ENHANCED: Log batch commit with timing
        val batchCommitStart = System.currentTimeMillis()
        Timber.d("[POST-BATCH] 📤 Committing batch of ${postsToUpload.size} posts to Firestore")
        
        try {
            batch.commit().await()
            
            val batchCommitDuration = System.currentTimeMillis() - batchCommitStart
            Timber.i("[POST-BATCH] ✅ Batch commit successful in ${batchCommitDuration}ms")
            
            // Mark posts as synced in local database
            postDao.markAsClean(
                ids = postsToUpload.map { it.id },
                userId = postsToUpload.first().userId,
                syncVersion = timestamp
            )
            
            Timber.i("[POST-BATCH] 📊 Successfully synced batch of ${postsToUpload.size} workout posts")
            return postsToUpload.size
            
        } catch (e: Exception) {
            Timber.e(e, "[POST-BATCH] ❌ Failed to commit batch of ${postsToUpload.size} workout posts")
            Timber.e("[POST-BATCH]   - Error type: ${e.javaClass.simpleName}")
            Timber.e("[POST-BATCH]   - Error message: ${e.message}")
            postsToUpload.forEach { post ->
                Timber.w("[POST-BATCH]   - Failed post: ${post.id} (workout: ${post.workoutId})")
            }
            throw e
        }
    }
}
