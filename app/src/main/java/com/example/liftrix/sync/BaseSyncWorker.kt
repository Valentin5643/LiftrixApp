package com.example.liftrix.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Base class for all sync workers that provides proper cancellation handling
 * and common error handling patterns.
 * 
 * Key features:
 * - Proper CancellationException propagation for coroutine cancellation semantics
 * - Cancellation checkpoints for long-running operations
 * - Structured error handling with retry logic
 * - Consistent logging and metrics
 * 
 * Implementation notes:
 * - Always re-throw CancellationException to maintain cancellation chain
 * - Check coroutineContext.ensureActive() in loops and batch operations
 * - Use withContext(Dispatchers.IO) for IO operations
 * - Return Result.retry() for transient errors within retry limit
 */
abstract class BaseSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    protected companion object {
        const val KEY_USER_ID = "userId"
        const val KEY_SYNC_COUNT = "sync_count"
        const val KEY_ERROR_MESSAGE = "error_message"
        const val KEY_CANCELLATION = "cancelled_by_system"
        const val DEFAULT_MAX_RETRY_COUNT = 3
        const val DEFAULT_BATCH_SIZE = 20
    }

    /**
     * Override this to provide the worker name for logging
     */
    abstract val workerName: String

    /**
     * Override this to provide the maximum retry count
     */
    open val maxRetryCount: Int = DEFAULT_MAX_RETRY_COUNT

    /**
     * Main work function that handles cancellation properly
     */
    final override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val userId = inputData.getString(KEY_USER_ID)
        
        if (userId.isNullOrEmpty()) {
            Timber.e("$workerName: User ID not provided")
            return@withContext Result.failure(
                Data.Builder()
                    .putString(KEY_ERROR_MESSAGE, "User ID not provided")
                    .build()
            )
        }

        Timber.d("$workerName: Starting sync for user $userId")
        
        try {
            // Check if we should continue before starting heavy work
            coroutineContext.ensureActive()
            
            // Perform the actual sync work
            val result = performSync(userId)
            
            Timber.d("$workerName: Sync completed successfully for user $userId")
            return@withContext result
            
        } catch (e: CancellationException) {
            // CRITICAL: Always re-throw CancellationException
            // This maintains proper coroutine cancellation semantics
            Timber.d("$workerName: Work cancelled by system for user $userId")
            
            // Log the cancellation but still re-throw
            // WorkManager will handle this appropriately
            throw e
            
        } catch (e: Exception) {
            // Handle all other exceptions
            Timber.e(e, "$workerName: Sync failed for user $userId")
            
            return@withContext handleSyncError(e, userId)
        }
    }

    /**
     * Implement this to perform the actual sync work
     * @param userId The user ID to sync for
     * @return Result indicating success, failure, or retry
     */
    protected abstract suspend fun performSync(userId: String): Result

    /**
     * Handle sync errors with appropriate retry logic
     * Can be overridden for custom error handling
     */
    protected open fun handleSyncError(error: Exception, userId: String): Result {
        return when (error) {
            is com.google.firebase.firestore.FirebaseFirestoreException -> {
                handleFirestoreError(error, userId)
            }
            is java.net.UnknownHostException,
            is java.net.SocketTimeoutException,
            is java.io.IOException -> {
                // Network errors - retry with backoff
                if (runAttemptCount < maxRetryCount) {
                    Timber.d("$workerName: Network error, retrying... (attempt ${runAttemptCount + 1}/$maxRetryCount)")
                    Result.retry()
                } else {
                    Result.failure(
                        Data.Builder()
                            .putString(KEY_ERROR_MESSAGE, "Network error after $maxRetryCount attempts: ${error.message}")
                            .build()
                    )
                }
            }
            else -> {
                // Other errors - retry if attempts remaining
                if (runAttemptCount < maxRetryCount) {
                    Timber.d("$workerName: Error occurred, retrying... (attempt ${runAttemptCount + 1}/$maxRetryCount)")
                    Result.retry()
                } else {
                    Result.failure(
                        Data.Builder()
                            .putString(KEY_ERROR_MESSAGE, error.message ?: "Unknown error after $maxRetryCount attempts")
                            .build()
                    )
                }
            }
        }
    }

    /**
     * Handle Firestore-specific errors
     */
    private fun handleFirestoreError(
        error: com.google.firebase.firestore.FirebaseFirestoreException,
        userId: String
    ): Result {
        return when (error.code) {
            com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                // Permission denied - don't retry as it won't succeed
                Timber.e("$workerName: Permission denied for user $userId")
                Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, "Permission denied: ${error.message}")
                        .build()
                )
            }
            com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE,
            com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            com.google.firebase.firestore.FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> {
                // Transient errors - retry with backoff
                if (runAttemptCount < maxRetryCount) {
                    Timber.d("$workerName: Firestore transient error, retrying... (attempt ${runAttemptCount + 1}/$maxRetryCount)")
                    Result.retry()
                } else {
                    Result.failure(
                        Data.Builder()
                            .putString(KEY_ERROR_MESSAGE, "Firestore error after $maxRetryCount attempts: ${error.message}")
                            .build()
                    )
                }
            }
            com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAUTHENTICATED -> {
                // Auth token expired - don't retry, needs re-authentication
                Timber.e("$workerName: Authentication token expired for user $userId")
                Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, "Authentication expired, please sign in again")
                        .build()
                )
            }
            else -> {
                // Other Firestore errors - retry if attempts remaining
                if (runAttemptCount < maxRetryCount) {
                    Result.retry()
                } else {
                    Result.failure(
                        Data.Builder()
                            .putString(KEY_ERROR_MESSAGE, "Firestore error: ${error.message}")
                            .build()
                    )
                }
            }
        }
    }

    /**
     * Helper function to check if the coroutine is still active
     * Use this in loops and between batch operations
     */
    protected suspend fun checkCancellation() {
        coroutineContext.ensureActive()
    }

    /**
     * Process items in batches with cancellation checks
     * @param items List of items to process
     * @param batchSize Size of each batch
     * @param processBlock Function to process each batch
     */
    protected suspend fun <T> processBatchesWithCancellation(
        items: List<T>,
        batchSize: Int = DEFAULT_BATCH_SIZE,
        processBlock: suspend (List<T>) -> Unit
    ) {
        val batches = items.chunked(batchSize)
        
        batches.forEachIndexed { index, batch ->
            // Check cancellation before each batch
            checkCancellation()
            
            Timber.d("$workerName: Processing batch ${index + 1}/${batches.size} (${batch.size} items)")
            
            try {
                processBlock(batch)
            } catch (e: CancellationException) {
                // Re-throw cancellation
                throw e
            } catch (e: Exception) {
                // Log batch error but continue with next batch
                Timber.w(e, "$workerName: Error processing batch ${index + 1}")
                // Optionally, you might want to track failed items
            }
            
            // Check cancellation after each batch
            checkCancellation()
        }
    }

    /**
     * Create sync metadata that complies with Firestore security rules
     * Firestore security rules expect: syncVersion (int), lastModified (timestamp), isSynced (bool)
     */
    protected fun createSyncMetadata(syncVersion: Int = 1): Map<String, Any> {
        return mapOf(
            "syncVersion" to syncVersion,
            "lastModified" to FieldValue.serverTimestamp(),
            "isSynced" to true
        )
    }

    /**
     * Add sync metadata to an existing data map
     * @param data The existing data map
     * @param syncVersion The sync version (defaults to 1)
     * @return New map with sync metadata added
     */
    protected fun addSyncMetadata(data: Map<String, Any?>, syncVersion: Int = 1): Map<String, Any?> {
        return data + createSyncMetadata(syncVersion)
    }

    /**
     * Create sync metadata for templates that comply with Firestore security rules
     * Templates require additional fields beyond base sync metadata
     */
    protected fun createTemplateSyncMetadata(syncVersion: Int = 1): Map<String, Any> {
        return mapOf(
            "syncVersion" to syncVersion,
            "lastModified" to FieldValue.serverTimestamp(),
            "isSynced" to true,
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}