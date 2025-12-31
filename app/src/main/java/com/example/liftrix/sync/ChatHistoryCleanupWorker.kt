package com.example.liftrix.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.example.liftrix.data.local.dao.ChatHistoryDao
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Worker for automatic cleanup of expired chat history messages.
 *
 * Enforces 30-day retention policy for AI chat messages (GDPR compliance).
 * Runs daily via WorkManager with best-effort scheduling (non-deterministic).
 *
 * Features:
 * - Deletes messages where expires_at < current_time
 * - Removes from both Room (local) and Firestore (remote)
 * - Batch processing for efficiency (1000 messages per run)
 * - Logs deletion count for analytics
 *
 * Part of Google Play compliance (SPEC-20251230-google-play-compliance).
 *
 * Usage:
 * ```
 * val request = PeriodicWorkRequestBuilder<ChatHistoryCleanupWorker>(1, TimeUnit.DAYS)
 *     .setConstraints(Constraints.Builder()
 *         .setRequiresBatteryNotLow(true)
 *         .build())
 *     .build()
 * WorkManager.getInstance(context).enqueueUniquePeriodicWork(
 *     "chat_history_cleanup",
 *     ExistingPeriodicWorkPolicy.KEEP,
 *     request
 * )
 * ```
 */
class ChatHistoryCleanupWorker @Inject constructor(
    context: Context,
    params: WorkerParameters,
    private val chatHistoryDao: ChatHistoryDao,
    private val firestore: FirebaseFirestore
) : CoroutineWorker(context, params) {

    companion object {
        const val WORKER_NAME = "ChatHistoryCleanupWorker"
        const val KEY_DELETED_COUNT = "deleted_count"
        const val KEY_ERROR_MESSAGE = "error_message"
        const val BATCH_SIZE = 1000
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Timber.i("$WORKER_NAME: Starting chat history cleanup")

        try {
            val currentTime = System.currentTimeMillis()

            // Get expired messages count for logging
            val expiredCount = chatHistoryDao.countExpiredMessages(currentTime)
            Timber.d("$WORKER_NAME: Found $expiredCount expired messages")

            if (expiredCount == 0) {
                Timber.i("$WORKER_NAME: No expired messages to delete")
                return@withContext Result.success()
            }

            // Get expired messages (batch processing - limit to BATCH_SIZE)
            val expiredMessages = chatHistoryDao.getExpiredMessages(
                currentTime = currentTime,
                limit = BATCH_SIZE
            )

            if (expiredMessages.isEmpty()) {
                Timber.i("$WORKER_NAME: No expired messages found (race condition)")
                return@withContext Result.success()
            }

            // Group by user for Firestore deletion
            val messagesByUser = expiredMessages.groupBy { it.userId }

            Timber.d("$WORKER_NAME: Deleting ${expiredMessages.size} messages for ${messagesByUser.size} users")

            // Delete from Firestore (batch operations per user)
            messagesByUser.forEach { (userId, messages) ->
                try {
                    deleteFromFirestore(userId, messages.map { it.id })
                } catch (e: Exception) {
                    Timber.e(e, "$WORKER_NAME: Failed to delete messages from Firestore for user $userId")
                    // Continue with local deletion even if Firestore fails
                }
            }

            // Delete from Room (local database)
            val deletedCount = chatHistoryDao.deleteExpiredMessages(currentTime)

            Timber.i("$WORKER_NAME: Successfully deleted $deletedCount expired messages")

            // Return success with deletion count for analytics
            Result.success(
                Data.Builder()
                    .putInt(KEY_DELETED_COUNT, deletedCount)
                    .build()
            )

        } catch (e: CancellationException) {
            // Re-throw cancellation to maintain coroutine semantics
            Timber.d("$WORKER_NAME: Work cancelled by system")
            throw e

        } catch (e: Exception) {
            Timber.e(e, "$WORKER_NAME: Cleanup failed with exception")

            Result.failure(
                Data.Builder()
                    .putString(KEY_ERROR_MESSAGE, e.message ?: "Unknown error")
                    .build()
            )
        }
    }

    /**
     * Delete messages from Firestore in batch.
     * Max batch size is 500, so we split if necessary.
     *
     * @param userId User ID
     * @param messageIds List of message IDs to delete
     */
    private suspend fun deleteFromFirestore(userId: String, messageIds: List<String>) {
        if (messageIds.isEmpty()) return

        // Firestore batch write limit is 500 operations
        val batchLimit = 500
        val batches = messageIds.chunked(batchLimit)

        batches.forEach { batch ->
            val firestoreBatch = firestore.batch()

            batch.forEach { messageId ->
                val docRef = firestore
                    .collection("users")
                    .document(userId)
                    .collection("chat_history")
                    .document(messageId)

                firestoreBatch.delete(docRef)
            }

            // Execute batch delete
            firestoreBatch.commit().await()

            Timber.d("$WORKER_NAME: Deleted ${batch.size} messages from Firestore for user $userId")
        }
    }
}
