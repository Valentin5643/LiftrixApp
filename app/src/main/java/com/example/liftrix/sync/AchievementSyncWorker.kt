package com.example.liftrix.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import androidx.work.workDataOf
import com.example.liftrix.data.local.dao.AchievementDao
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
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
    private val firestore: FirebaseFirestore
) : CoroutineWorker(context, params) {

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

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val userId = inputData.getString("userId") ?: return@withContext Result.failure()

            val unsyncedAchievements = achievementDao.getUnsyncedAchievements(userId)
            
            if (unsyncedAchievements.isEmpty()) {
                Timber.d("No unsynced achievements found for user $userId")
                return@withContext Result.success()
            }

            Timber.d("Found ${unsyncedAchievements.size} unsynced achievements for user $userId")
            
            // Process achievements in batches for efficiency
            val batches = unsyncedAchievements.chunked(BATCH_SIZE)
            var successCount = 0
            var failureCount = 0
            var deduplicatedCount = 0

            batches.forEach { batch ->
                val firestoreBatch = firestore.batch()
                val achievementsToSync = mutableListOf<String>() // Track achievement IDs to mark as synced
                
                batch.forEach { achievement ->
                    try {
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
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                            
                            firestoreBatch.set(docRef, achievementData)
                            achievementsToSync.add(achievement.id)
                            
                        } else {
                            // Duplicate found, check which unlock time is earlier
                            val existingDoc = existingDocs.documents.first()
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
                                Timber.d("Updated remote achievement ${achievement.id} with earlier unlock time")
                            } else {
                                Timber.d("Remote achievement ${achievement.achievementTitle} has earlier or same unlock time, keeping remote version")
                            }
                            
                            achievementsToSync.add(achievement.id)
                            deduplicatedCount++
                        }
                        
                    } catch (e: Exception) {
                        Timber.e(e, "Error preparing achievement ${achievement.id} for batch sync")
                        failureCount++
                    }
                }
                
                try {
                    if (achievementsToSync.isNotEmpty()) {
                        firestoreBatch.commit().await()
                        
                        // Mark achievements as synced
                        achievementsToSync.forEach { achievementId ->
                            achievementDao.markAsSynced(achievementId, System.currentTimeMillis())
                        }
                        
                        successCount += achievementsToSync.size
                        Timber.d("Successfully synced batch of ${achievementsToSync.size} achievements")
                    }
                    
                } catch (e: Exception) {
                    Timber.e(e, "Batch sync failed for ${achievementsToSync.size} achievements")
                    failureCount += achievementsToSync.size
                }
            }
            
            Timber.d("Achievement sync complete - Success: $successCount, Failed: $failureCount, Deduplicated: $deduplicatedCount")
            
            return@withContext when {
                failureCount > 0 && successCount == 0 -> {
                    // All failed
                    if (runAttemptCount < MAX_RETRY_COUNT) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }
                else -> {
                    // Some or all succeeded
                    Result.success()
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "AchievementSyncWorker failed with exception")
            
            if (runAttemptCount < MAX_RETRY_COUNT) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}