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
import com.example.liftrix.data.local.dao.SocialProfileDao
import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.dao.WorkoutPostDao
import com.example.liftrix.data.local.entity.SocialProfileEntity
import com.example.liftrix.data.local.entity.UserProfileEntity
import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.data.local.entity.WorkoutPostEntity
import com.example.liftrix.domain.model.WorkoutStatus
import com.google.firebase.Timestamp
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
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
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
    private val socialProfileDao: SocialProfileDao,
    private val userProfileDao: UserProfileDao,
    private val workoutDao: WorkoutDao,
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
        private const val PUBLIC_POST_SYNC_LIMIT = 100
        
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
            Timber.i("[PUBLIC-LOG] WorkoutPostSyncWorker started user=$userId forceSync=$forceSync")

            // 🔥 ENHANCED: Comprehensive logging for workout post sync
            Timber.i("[POST-SYNC] 🔍 Starting WorkoutPostSyncWorker for user $userId (forceSync: $forceSync)")
            
            val useDirtyFlagGating = OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
                OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING
            val unsyncedPosts = if (useDirtyFlagGating) {
                (postDao.getDirtyPosts(userId) + postDao.getUnsyncedPosts(userId))
                    .distinctBy { it.id }
            } else {
                postDao.getUnsyncedPosts(userId)
            }
            
            if (unsyncedPosts.isEmpty() && !forceSync) {
                Timber.w("[PUBLIC-LOG] WorkoutPostSyncWorker found no unsynced posts for user=$userId")
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
                Timber.i("[PUBLIC-LOG] Pending sync post id=${post.id} workout=${post.workoutId} visibility=${post.visibility} dirty=${post.isDirty} synced=${post.isSynced}")
            }
            
            // Use batch processing with cancellation checks
            var processed = 0
            if (unsyncedPosts.isNotEmpty()) {
                processBatchesWithCancellation(
                    items = unsyncedPosts,
                    batchSize = 10
                ) { batch ->
                    processed += syncPostBatch(batch)
                }
            }
            val hydratedPublicPosts = if (forceSync) {
                hydrateRecentPublicPosts(userId)
            } else {
                0
            }
            Timber.i("[POST-SYNC] Hydrated $hydratedPublicPosts public posts for Explore during force sync")
            
            Timber.i("[POST-SYNC] ✅ Successfully synced $processed/${unsyncedPosts.size} workout posts for user $userId")
            
            return Result.success(
                Data.Builder()
                    .putInt(KEY_SYNC_COUNT, processed + hydratedPublicPosts)
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
            if (post.visibility == "PUBLIC") {
                Timber.i("[PUBLIC-LOG] Uploading PUBLIC workout post id=${post.id} user=${post.userId} workout=${post.workoutId} likes=${post.likeCount} comments=${post.commentCount}")
            }
            
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
            postsToUpload.filter { it.visibility == "PUBLIC" }.forEach { post ->
                Timber.i("[PUBLIC-LOG] Uploaded PUBLIC workout post id=${post.id} syncVersion=$timestamp")
            }
            
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

    private suspend fun hydrateRecentPublicPosts(viewerId: String): Int = withContext(Dispatchers.IO) {
        val snapshot = firestore.collection("workout_posts")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(PUBLIC_POST_SYNC_LIMIT.toLong())
            .get()
            .await()

        var hydrated = 0
        for (doc in snapshot.documents.sortedByDescending { it.data?.longValue("createdAt") ?: 0L }) {
            checkCancellation()
            val post = remotePostToEntity(doc.id, doc.data ?: continue) ?: continue
            if (post.userId == viewerId || post.visibility != "PUBLIC" || post.isHidden) continue

            ensureAuthorProfileForPublicPost(post.userId)
            ensureWorkoutStubForPublicPost(post)
            postDao.upsertFromRemote(post)
            Timber.i("[PUBLIC-LOG] Hydrated PUBLIC post into local feed id=${post.id} author=${post.userId} viewer=$viewerId")
            hydrated++
        }

        Timber.i("[POST-SYNC] Hydrated $hydrated recent PUBLIC posts for Explore")
        hydrated
    }

    private suspend fun ensureAuthorProfileForPublicPost(authorId: String) {
        val existingUserProfile = userProfileDao.getProfileForUserSuspend(authorId)
        val existingSocialProfile = socialProfileDao.getSocialProfileByUserId(authorId)
        if (existingUserProfile != null && existingSocialProfile != null) return

        val publicProfileData = try {
            firestore.collection("users_public").document(authorId).get().await().data
        } catch (e: Exception) {
            Timber.w(e, "[POST-SYNC] Failed to fetch public profile for post author $authorId")
            null
        }

        val now = LocalDateTime.now()
        val nowMillis = System.currentTimeMillis()
        val displayName = publicProfileData?.stringValue("displayName")
            ?: publicProfileData?.stringValue("username")
            ?: "Liftrix User"
        val username = publicProfileData?.stringValue("username") ?: "user_${authorId.take(8)}"
        val photoUrl = publicProfileData?.stringValue("profileImageUrl")
            ?: publicProfileData?.stringValue("profilePhotoUrl")

        if (existingUserProfile == null) {
            userProfileDao.insertProfile(
                UserProfileEntity(
                    id = authorId,
                    userId = authorId,
                    displayName = displayName,
                    age = null,
                    weightKg = null,
                    heightCm = null,
                    fitnessLevel = null,
                    goals = null,
                    availableEquipment = null,
                    workoutFrequency = null,
                    preferredWorkoutDuration = null,
                    completedAt = null,
                    createdAt = now,
                    updatedAt = now,
                    isSynced = true,
                    syncVersion = nowMillis,
                    isDirty = false,
                    lastModified = nowMillis,
                    bio = publicProfileData?.stringValue("bio"),
                    isPublic = !(publicProfileData?.booleanValue("isPrivate") ?: false),
                    lastActiveAt = now,
                    totalWorkouts = publicProfileData?.intValue("totalWorkouts") ?: 0,
                    currentStreak = publicProfileData?.intValue("currentStreak") ?: 0,
                    longestStreak = publicProfileData?.intValue("longestStreak") ?: 0,
                    memberSince = now,
                    profileCompletionPercentage = 0,
                    profileImageUrl = photoUrl,
                    profileImageUpdatedAt = null,
                    hasCustomProfileImage = photoUrl != null
                )
            )
        }

        if (existingSocialProfile == null) {
            socialProfileDao.insertProfile(
                SocialProfileEntity(
                    userId = authorId,
                    username = username,
                    displayName = displayName,
                    bio = publicProfileData?.stringValue("bio"),
                    profilePhotoUrl = photoUrl,
                    coverPhotoUrl = publicProfileData?.stringValue("coverPhotoUrl"),
                    workoutCount = publicProfileData?.intValue("totalWorkouts") ?: 0,
                    followerCount = publicProfileData?.intValue("followerCount") ?: 0,
                    followingCount = publicProfileData?.intValue("followingCount") ?: 0,
                    memberSince = publicProfileData?.longValue("memberSince") ?: nowMillis,
                    lastActive = publicProfileData?.longValue("lastActive") ?: nowMillis,
                    isVerified = publicProfileData?.booleanValue("isVerified") ?: false,
                    isPrivate = publicProfileData?.booleanValue("isPrivate") ?: false,
                    hideFromSuggestions = publicProfileData?.booleanValue("hideFromSuggestions") ?: false,
                    allowFriendRequests = publicProfileData?.booleanValue("allowFriendRequests") ?: true,
                    instagramHandle = publicProfileData?.stringValue("instagramHandle"),
                    youtubeChannel = publicProfileData?.stringValue("youtubeChannel"),
                    personalWebsite = publicProfileData?.stringValue("personalWebsite"),
                    isSynced = true,
                    syncVersion = nowMillis,
                    isDirty = false,
                    lastModified = nowMillis,
                    createdAt = publicProfileData?.longValue("createdAt") ?: nowMillis,
                    updatedAt = nowMillis
                )
            )
        }
    }

    private suspend fun ensureWorkoutStubForPublicPost(post: WorkoutPostEntity) {
        if (workoutDao.getWorkoutByIdForUser(post.workoutId, post.userId) != null) return

        val createdAt = Instant.ofEpochMilli(post.createdAt)
        workoutDao.insertWorkout(
            WorkoutEntity(
                id = post.workoutId,
                userId = post.userId,
                name = "Shared workout",
                date = createdAt.atZone(ZoneOffset.UTC).toLocalDate(),
                exercisesJson = "[]",
                status = WorkoutStatus.COMPLETED,
                startTime = null,
                endTime = null,
                notes = null,
                templateId = null,
                createdAt = createdAt,
                updatedAt = Instant.ofEpochMilli(post.updatedAt),
                isSynced = true,
                syncVersion = post.syncVersion,
                isDirty = false,
                lastModified = post.lastModified
            )
        )
    }

    private fun remotePostToEntity(documentId: String, data: Map<String, Any?>): WorkoutPostEntity? {
        val userId = data.stringValue("userId") ?: return null
        val workoutId = data.stringValue("workoutId") ?: return null
        val createdAt = data.longValue("createdAt") ?: System.currentTimeMillis()
        val updatedAt = data.longValue("updatedAt") ?: createdAt
        val lastModified = data.longValue("lastModified") ?: updatedAt

        return WorkoutPostEntity(
            id = data.stringValue("id") ?: documentId,
            userId = userId,
            workoutId = workoutId,
            caption = data.stringValue("caption"),
            mediaUrls = data.jsonArrayString("mediaUrls"),
            mediaThumbnails = data.jsonArrayString("mediaThumbnails"),
            workoutDuration = data.intValue("workoutDuration"),
            totalVolume = data.doubleValue("totalVolume"),
            exercisesCount = data.intValue("exercisesCount"),
            prsCount = data.intValue("prsCount") ?: 0,
            likeCount = data.intValue("likeCount") ?: 0,
            commentCount = data.intValue("commentCount") ?: 0,
            shareCount = data.intValue("shareCount") ?: 0,
            saveCount = data.intValue("saveCount") ?: 0,
            visibility = data.stringValue("visibility") ?: "PUBLIC",
            createdAt = createdAt,
            updatedAt = updatedAt,
            isSynced = true,
            syncVersion = data.longValue("syncVersion") ?: lastModified,
            isDirty = false,
            lastModified = lastModified,
            isHidden = data.booleanValue("isHidden") ?: false,
            hiddenReason = data.stringValue("hiddenReason"),
            hiddenAt = data.longValue("hiddenAt"),
            hiddenByUserId = data.stringValue("hiddenByUserId")
        )
    }

    private fun Map<String, Any?>.stringValue(key: String): String? = this[key] as? String

    private fun Map<String, Any?>.booleanValue(key: String): Boolean? = this[key] as? Boolean

    private fun Map<String, Any?>.intValue(key: String): Int? = (this[key] as? Number)?.toInt()

    private fun Map<String, Any?>.doubleValue(key: String): Double? = (this[key] as? Number)?.toDouble()

    private fun Map<String, Any?>.longValue(key: String): Long? {
        return when (val value = this[key]) {
            is Timestamp -> value.toDate().time
            is Number -> value.toLong()
            else -> null
        }
    }

    private fun Map<String, Any?>.jsonArrayString(key: String): String? {
        return when (val value = this[key]) {
            null -> null
            is String -> value
            is List<*> -> gson.toJson(value)
            else -> null
        }
    }
}
