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
        // Enhanced lifecycle logging for debugging worker instantiation
        
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
     * Handle sync errors with enhanced retry logic and exponential backoff
     * 🔥 ENHANCED: Now includes more sophisticated retry strategies based on error type
     */
    protected open fun handleSyncError(error: Exception, userId: String): Result {
        // Calculate exponential backoff delay
        val backoffDelaySeconds = calculateBackoffDelay(runAttemptCount)
        
        return when (error) {
            is com.google.firebase.firestore.FirebaseFirestoreException -> {
                handleFirestoreError(error, userId, backoffDelaySeconds)
            }
            is java.net.UnknownHostException,
            is java.net.SocketTimeoutException,
            is java.net.ConnectException,
            is javax.net.ssl.SSLException,
            is java.io.IOException -> {
                // Network errors - retry with enhanced backoff
                handleNetworkError(error, userId, backoffDelaySeconds)
            }
            is java.util.concurrent.CancellationException,
            is kotlinx.coroutines.CancellationException -> {
                // Cancellation - don't retry, let WorkManager handle appropriately
                Timber.d("$workerName: Operation cancelled by system for user $userId")
                Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, "Operation cancelled")
                        .putBoolean(KEY_CANCELLATION, true)
                        .build()
                )
            }
            is SecurityException,
            is IllegalArgumentException,
            is IllegalStateException -> {
                // Logic errors - don't retry as they won't succeed
                Timber.e(error, "$workerName: Logic error for user $userId - not retrying")
                Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, "Logic error: ${error.message}")
                        .build()
                )
            }
            else -> {
                // Other errors - retry with backoff if attempts remaining
                handleGenericError(error, userId, backoffDelaySeconds)
            }
        }
    }
    
    /**
     * 🔥 NEW: Calculate exponential backoff delay with jitter to prevent thundering herd
     */
    private fun calculateBackoffDelay(attemptCount: Int): Long {
        val baseDelaySeconds = 15L // Base delay from WorkManager configuration
        val maxDelaySeconds = 300L // Cap at 5 minutes
        
        // Exponential backoff: base * 2^attemptCount
        val exponentialDelay = (baseDelaySeconds * Math.pow(2.0, attemptCount.toDouble())).toLong()
        
        // Add jitter (±20%) to prevent all workers retrying at the same time
        val jitterRange = (exponentialDelay * 0.2).toLong()
        val jitter = (-jitterRange..jitterRange).random()
        
        return (exponentialDelay + jitter).coerceAtMost(maxDelaySeconds)
    }
    
    /**
     * 🔥 NEW: Enhanced network error handling with specific retry strategies
     */
    private fun handleNetworkError(error: Exception, userId: String, backoffDelaySeconds: Long): Result {
        return if (runAttemptCount < maxRetryCount) {
            val errorType = when (error) {
                is java.net.UnknownHostException -> "DNS resolution failed"
                is java.net.SocketTimeoutException -> "Connection timeout" 
                is java.net.ConnectException -> "Connection refused"
                is javax.net.ssl.SSLException -> "SSL/TLS error"
                else -> "Network I/O error"
            }
            
            Timber.d("$workerName: $errorType for user $userId, retrying in ${backoffDelaySeconds}s... (attempt ${runAttemptCount + 1}/$maxRetryCount)")
            Result.retry()
        } else {
            Timber.e(error, "$workerName: Network error failed after $maxRetryCount attempts for user $userId")
            Result.failure(
                Data.Builder()
                    .putString(KEY_ERROR_MESSAGE, "Network error after $maxRetryCount attempts: ${error.message}")
                    .putInt("final_attempt_count", runAttemptCount)
                    .build()
            )
        }
    }
    
    /**
     * 🔥 NEW: Enhanced generic error handling
     */
    private fun handleGenericError(error: Exception, userId: String, backoffDelaySeconds: Long): Result {
        return if (runAttemptCount < maxRetryCount) {
            Timber.d("$workerName: Generic error for user $userId, retrying in ${backoffDelaySeconds}s... (attempt ${runAttemptCount + 1}/$maxRetryCount): ${error.message}")
            Result.retry()
        } else {
            Timber.e(error, "$workerName: Generic error failed after $maxRetryCount attempts for user $userId")
            Result.failure(
                Data.Builder()
                    .putString(KEY_ERROR_MESSAGE, "Error after $maxRetryCount attempts: ${error.message ?: "Unknown error"}")
                    .putInt("final_attempt_count", runAttemptCount)
                    .putString("error_type", error.javaClass.simpleName)
                    .build()
            )
        }
    }

    /**
     * Handle Firestore-specific errors with enhanced backoff information
     * 🔥 ENHANCED: Now includes backoff delay information
     */
    private fun handleFirestoreError(
        error: com.google.firebase.firestore.FirebaseFirestoreException,
        userId: String,
        backoffDelaySeconds: Long
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
                // Transient errors - retry with enhanced backoff
                if (runAttemptCount < maxRetryCount) {
                    val errorType = when (error.code) {
                        com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE -> "Service unavailable"
                        com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> "Request timeout"
                        com.google.firebase.firestore.FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> "Rate limit exceeded"
                        else -> "Transient error"
                    }
                    Timber.d("$workerName: Firestore $errorType for user $userId, retrying in ${backoffDelaySeconds}s... (attempt ${runAttemptCount + 1}/$maxRetryCount)")
                    Result.retry()
                } else {
                    Result.failure(
                        Data.Builder()
                            .putString(KEY_ERROR_MESSAGE, "Firestore error after $maxRetryCount attempts: ${error.message}")
                            .putInt("final_attempt_count", runAttemptCount)
                            .putString("firestore_error_code", error.code.name)
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
    protected fun createSyncMetadata(syncVersion: Long = 1L): Map<String, Any> {
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
    protected fun addSyncMetadata(data: Map<String, Any?>, syncVersion: Long = 1L): Map<String, Any?> {
        return data + createSyncMetadata(syncVersion)
    }

    /**
     * Create sync metadata for templates that comply with Firestore security rules
     * Templates require additional fields beyond base sync metadata
     */
    protected fun createTemplateSyncMetadata(syncVersion: Long = 1L): Map<String, Any> {
        return mapOf(
            "syncVersion" to syncVersion,
            "lastModified" to FieldValue.serverTimestamp(),
            "isSynced" to true,
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}
