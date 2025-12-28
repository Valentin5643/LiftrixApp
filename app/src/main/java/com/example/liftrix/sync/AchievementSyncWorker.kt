package com.example.liftrix.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import androidx.work.workDataOf
import com.example.liftrix.data.local.dao.AchievementDao
import com.example.liftrix.data.local.entity.UserAchievementEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import com.google.firebase.auth.FirebaseAuth
import com.example.liftrix.config.OfflineArchitectureFlags
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.assisted.AssistedFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for syncing user achievements to Firebase Firestore.
 * 
 * This worker handles:
 * - Achievement deduplication to prevent duplicate achievements across devices
 * - Batch processing of unsynced achievements for efficiency
 * - Achievement unlock timestamp preservation
 * - User-scoped achievement synchronization
 * 
 * Achievement deduplication strategy:
 * - Uses composite key: userId + achievementType + achievementTitle
 * - Preserves earliest unlock timestamp when duplicates are found
 * - Maintains achievement consistency across all user devices
 * - Handles offline achievement unlocks with proper conflict resolution
 */
@HiltWorker
class AchievementSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val achievementDao: AchievementDao,
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : BaseSyncWorker(context, params) {

    // 🔧 HOTFIX: Fallback constructor for when Hilt factory generation fails
    // This allows WorkManager to instantiate the worker via reflection
    // TEMPORARY: Remove once Hilt assisted factories are confirmed working
    constructor(context: Context, params: WorkerParameters) : this(
        context,
        params,
        WorkerServiceLocator.getAchievementSyncDependencies(context).run {
            Timber.w("⚠️ AchievementSyncWorker using FALLBACK constructor - Hilt factory failed!")
            return@run this
        }
    )
    
    // Helper constructor to unpack the dependency structure
    private constructor(
        context: Context,
        params: WorkerParameters,
        deps: WorkerServiceLocator.AchievementSyncDependencies
    ) : this(
        context, params,
        deps.achievementDao, deps.firestore, deps.firebaseAuth
    )

    init {
        val processName = getProcessName()
        Timber.d("✅ AchievementSyncWorker constructed with Hilt dependency injection in process: $processName")
    }
    
    private fun getProcessName(): String {
        return try {
            val processName = applicationContext.packageManager
                .getApplicationLabel(applicationContext.applicationInfo)
                .toString()
            processName
        } catch (e: Exception) {
            "unknown"
        }
    }

    override val workerName: String = "AchievementSyncWorker"

    @AssistedFactory
    interface Factory {
        fun create(context: Context, params: WorkerParameters): AchievementSyncWorker
    }

    companion object {
        const val WORK_NAME = "achievement_sync_work"
        const val KEY_SYNC_COUNT = "sync_count"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val MAX_RETRY_COUNT = 3
        private const val BATCH_SIZE = 25 // Achievements are small, can batch more

        fun createWorkRequest(userId: String): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<AchievementSyncWorker>()
                .setInputData(workDataOf("userId" to userId))
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
                .addTag("achievement_sync")
                .build()
        }
    }

    override suspend fun performSync(userId: String): Result = withContext(Dispatchers.IO) {
        try {
            // 🔥 CRITICAL: Validate Firebase Auth state before attempting Firestore operations
            val currentUser = firebaseAuth.currentUser
            if (currentUser == null) {
                Timber.e("$workerName: No authenticated user found for sync operation")
                return@withContext Result.failure(
                    workDataOf(
                        KEY_ERROR_MESSAGE to "User not authenticated - sync aborted",
                        "error_type" to "AUTHENTICATION_REQUIRED",
                        "requires_signin" to true
                    )
                )
            }
            
            if (currentUser.uid != userId) {
                Timber.e("$workerName: Auth user ID (${currentUser.uid}) doesn't match sync user ID ($userId)")
                return@withContext Result.failure(
                    workDataOf(
                        KEY_ERROR_MESSAGE to "User ID mismatch - sync aborted",
                        "error_type" to "USER_ID_MISMATCH",
                        "requires_signin" to true
                    )
                )
            }
            
            Timber.d("$workerName: Auth validation passed for user $userId")
            val useDirtyFlagGating = OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
                OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING
            val achievementsToProcess = if (useDirtyFlagGating) {
                achievementDao.getDirtyUserAchievements(userId)
            } else {
                achievementDao.getUnsyncedAchievements(userId)
            }
            
            if (achievementsToProcess.isEmpty()) {
                Timber.d("No unsynced achievements found for user $userId")
                return@withContext Result.success()
            }

            Timber.d("Found ${achievementsToProcess.size} achievements to sync for user $userId (dirty gating: $useDirtyFlagGating)")
            
            // Process achievements in batches for efficiency
            val batches = achievementsToProcess.chunked(BATCH_SIZE)
            var successCount = 0
            var failureCount = 0
            var deduplicatedCount = 0

            batches.forEach { batch ->
                val firestoreBatch = firestore.batch()
                val achievementsToMarkClean = mutableListOf<String>() // Track achievement IDs to mark as clean
                var batchHasWrites = false
                
                batch.forEach { achievement ->
                    try {
                        if (useDirtyFlagGating && !achievement.isDirty) {
                            return@forEach
                        }

                        val collectionRef = firestore
                            .collection("users")
                            .document(userId)
                            .collection("achievements")
                        
                        // Check for existing achievement with same type and title (deduplication)
                        val existingQuery = collectionRef
                            .whereEqualTo("type", achievement.achievementType)
                            .whereEqualTo("title", achievement.achievementTitle)
                            .limit(1)
                        
                        val existingDocs = existingQuery.get().await()
                        
                        if (existingDocs.isEmpty) {
                            // No duplicate found, create new achievement
                            val docRef = collectionRef.document(achievement.id)
                            
                            val achievementData = mapOf(
                                "id" to achievement.id,
                                "userId" to userId,
                                "type" to achievement.achievementType,
                                "title" to achievement.achievementTitle,
                                "description" to achievement.achievementDescription,
                                "unlockedAt" to achievement.unlockedAt.atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
                                "isDisplayed" to achievement.isDisplayed,
                                "syncVersion" to System.currentTimeMillis(),
                                "lastModified" to achievement.lastModified,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                            
                            firestoreBatch.set(docRef, achievementData)
                            batchHasWrites = true
                            achievementsToMarkClean.add(achievement.id)
                            
                        } else {
                            // Duplicate found, check which unlock time is earlier
                            val existingDoc = existingDocs.documents.first()
                            val remoteLastModified = when (val remoteValue = existingDoc.get("lastModified")) {
                                is com.google.firebase.Timestamp -> remoteValue.toDate().time
                                is Number -> remoteValue.toLong()
                                else -> 0L
                            }

                            if (existingDoc.id == achievement.id &&
                                remoteLastModified > achievement.lastModified
                            ) {
                                val currentTime = System.currentTimeMillis()
                                val remoteEntity = UserAchievementEntity(
                                    id = existingDoc.id,
                                    userId = userId,
                                    achievementType = existingDoc.getString("type") ?: achievement.achievementType,
                                    achievementTitle = existingDoc.getString("title") ?: achievement.achievementTitle,
                                    achievementDescription = existingDoc.getString("description")
                                        ?: achievement.achievementDescription,
                                    unlockedAt = achievement.unlockedAt,
                                    isDisplayed = existingDoc.getBoolean("isDisplayed") ?: achievement.isDisplayed,
                                    createdAt = achievement.createdAt,
                                    updatedAt = achievement.updatedAt,
                                    isSynced = true,
                                    syncVersion = existingDoc.getLong("syncVersion") ?: achievement.syncVersion,
                                    isDirty = false,
                                    lastModified = remoteLastModified
                                )
                                achievementDao.upsertFromRemote(remoteEntity)
                                return@forEach
                            }

                            val existingUnlockedAt = existingDoc.getLong("unlockedAt") ?: Long.MAX_VALUE
                            val localUnlockedAtMillis = achievement.unlockedAt.atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                            
                            if (localUnlockedAtMillis < existingUnlockedAt) {
                                // Local achievement was unlocked earlier, update remote
                                val achievementData = mapOf(
                                    "unlockedAt" to localUnlockedAtMillis,
                                    "syncVersion" to System.currentTimeMillis(),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                                
                                firestoreBatch.update(existingDoc.reference, achievementData)
                                batchHasWrites = true
                                Timber.d("Updated remote achievement ${achievement.id} with earlier unlock time")
                            } else {
                                Timber.d("Remote achievement ${achievement.achievementTitle} has earlier or same unlock time, keeping remote version")
                            }
                            
                            achievementsToMarkClean.add(achievement.id)
                            deduplicatedCount++
                        }
                        
                    } catch (e: Exception) {
                        Timber.e(e, "Error preparing achievement ${achievement.id} for batch sync")
                        failureCount++
                    }
                }
                
                try {
                    if (batchHasWrites) {
                        firestoreBatch.commit().await()
                    }
                        
                        // Mark achievements as synced
                        if (achievementsToMarkClean.isNotEmpty()) {
                            achievementDao.markAsClean(
                                ids = achievementsToMarkClean,
                                userId = userId,
                                syncVersion = System.currentTimeMillis()
                            )
                        }
                        
                        successCount += achievementsToMarkClean.size
                        Timber.d("Successfully synced batch of ${achievementsToMarkClean.size} achievements")
                    
                } catch (e: FirebaseFirestoreException) {
                    when (e.code) {
                        FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                            Timber.e("$workerName: PERMISSION_DENIED for user $userId on achievements path: /users/$userId/achievements/")
                            Timber.e("$workerName: Auth user: ${firebaseAuth.currentUser?.uid}, Email: ${firebaseAuth.currentUser?.email}")
                            Timber.e("$workerName: Check Firestore security rules for achievements collection")
                            // Don't retry permission errors - they won't succeed
                            throw e
                        }
                        FirebaseFirestoreException.Code.UNAUTHENTICATED -> {
                            Timber.e("$workerName: UNAUTHENTICATED error - Firebase Auth token expired")
                            throw e
                        }
                        else -> {
                            Timber.e(e, "$workerName: Batch sync failed for ${achievementsToMarkClean.size} achievements")
                            failureCount += achievementsToMarkClean.size
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "$workerName: Batch sync failed for ${achievementsToMarkClean.size} achievements")
                    failureCount += achievementsToMarkClean.size
                }
            }
            
            Timber.d("Achievement sync complete - Success: $successCount, Failed: $failureCount, Deduplicated: $deduplicatedCount")
            
            return@withContext when {
                failureCount > 0 && successCount == 0 -> {
                    // All failed - let BaseSyncWorker handle retry logic
                    throw Exception("All achievement syncs failed. Success: $successCount, Failed: $failureCount")
                }
                else -> {
                    // Some or all succeeded
                    Result.success(
                        workDataOf(
                            KEY_SYNC_COUNT to successCount,
                            "failed_count" to failureCount,
                            "deduplicated_count" to deduplicatedCount
                        )
                    )
                }
            }
            
        } catch (e: Exception) {
            // Let base class handle the error
            throw e
        }
    }
}
