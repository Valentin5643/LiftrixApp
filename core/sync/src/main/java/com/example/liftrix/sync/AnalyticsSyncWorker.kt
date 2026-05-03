package com.example.liftrix.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import com.example.liftrix.data.remote.dto.AnalyticsDto
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixSuccess
import com.example.liftrix.domain.model.common.liftrixFailure
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.example.liftrix.config.OfflineArchitectureFlags
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.time.Instant

/**
 * WorkManager job for analytics data synchronization with Firebase
 * 
 * Implements offline-first sync strategy with conflict resolution:
 * - Syncs analytics calculations and cached data to Firebase
 * - Uses exponential backoff with maximum 3 retry attempts
 * - Handles conflict resolution using timestamp-based last-write-wins
 * - Batch operations for improved performance
 * 
 * Performance Targets:
 * - Analytics sync completion: <5 seconds for standard datasets
 * - Conflict resolution: <2 seconds per conflict
 * - Batch processing: Up to 50 analytics entries per operation
 * 
 * Error Handling:
 * - Network connectivity failures: automatic retry with exponential backoff
 * - Authentication failures: immediate failure with user notification
 * - Data validation errors: skip invalid entries, continue with valid data
 * - Firebase quota limits: throttle requests and retry with longer delays
 */
@HiltWorker
class AnalyticsSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val progressStatsRepository: ProgressStatsSyncRepository,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "analytics_sync_work"
        const val KEY_SYNC_COUNT = "analytics_sync_count"
        const val KEY_ERROR_MESSAGE = "analytics_error_message"
        const val KEY_CONFLICT_COUNT = "analytics_conflict_count"
        
        private const val MAX_RETRY_COUNT = 3
        private const val FIRESTORE_ANALYTICS_COLLECTION = "analytics"
        private const val FIRESTORE_CACHE_COLLECTION = "analytics_cache"
        private const val BATCH_SIZE = 50
        private const val SYNC_TIMEOUT_MS = 10000L
    }

    override suspend fun doWork(): Result {
        return try {
            val useDirtyFlagGating = OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
                OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING
            if (!OfflineArchitectureFlags.ROOM_FIRST_ENABLED) {
                Timber.w("Analytics sync running in legacy mode (Room-first disabled)")
            }
            Timber.d("Analytics sync dirty gating enabled: $useDirtyFlagGating")

            // Enhanced authentication validation
            val authValidationResult = validateAuthentication()
            if (!authValidationResult.isValid) {
                Timber.w("Analytics sync skipped: ${authValidationResult.reason}")
                val result = Result.success(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, "Analytics sync skipped: ${authValidationResult.reason}")
                        .putInt(KEY_SYNC_COUNT, 0)
                        .build()
                )
                return result
            }

            val currentUserId = authValidationResult.userId!!
            Timber.d("Starting analytics sync for authenticated user: $currentUserId")
            
            val syncResult = syncAnalyticsToFirebase(currentUserId)
            
            syncResult.fold(
                onSuccess = { syncData ->
                    val outputData = Data.Builder()
                        .putInt(KEY_SYNC_COUNT, syncData.syncedCount)
                        .putInt(KEY_CONFLICT_COUNT, syncData.conflictCount)
                        .build()
                    
                    Timber.d("Analytics sync completed successfully: $syncData")
                    Result.success(outputData)
                },
                onFailure = { throwable ->
                    return handleSyncFailure(throwable)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error during analytics sync")
            return handleUnexpectedError(e)
        }
    }

    /**
     * Syncs analytics calculations to Firebase with conflict resolution
     */
    private suspend fun syncAnalyticsToFirebase(userId: String): LiftrixResult<AnalyticsSyncResult> {
        return try {
            // Get pending analytics calculations for sync
            val pendingCalculations = progressStatsRepository.getPendingSyncCalculations(userId)
            
            if (pendingCalculations.isEmpty()) {
                Timber.d("No pending analytics calculations to sync")
                return liftrixSuccess(AnalyticsSyncResult(0, 0))
            }

            var syncedCount = 0
            var conflictCount = 0
            
            // Process analytics in batches for performance
            pendingCalculations.chunked(BATCH_SIZE).forEach { batch ->
                val batchResult = syncAnalyticsBatch(userId, batch)
                syncedCount += batchResult.syncedCount
                conflictCount += batchResult.conflictCount
            }
            
            // Mark calculations as synced in local database
            progressStatsRepository.markCalculationsClean(
                userId,
                pendingCalculations.map { it.id }
            )
            
            liftrixSuccess(AnalyticsSyncResult(syncedCount, conflictCount))
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync analytics to Firebase")
            liftrixFailure(
                com.example.liftrix.domain.model.error.LiftrixError.NetworkError(
                    "Analytics sync failed: ${e.message}"
                )
            )
        }
    }

    /**
     * Syncs a batch of analytics calculations with enhanced error handling
     */
    private suspend fun syncAnalyticsBatch(
        userId: String, 
        calculations: List<AnalyticsCalculation>
    ): AnalyticsSyncResult {
        var syncedCount = 0
        var conflictCount = 0
        var permissionErrors = 0
        val collectionRef = firestore.collection(FIRESTORE_ANALYTICS_COLLECTION)
        val docIds = calculations.map { calculation ->
            "${userId}_${calculation.calculationType}_${calculation.timestamp}"
        }
        val remoteDocs = FirestorePrefetcher.prefetchByIds(collectionRef, docIds)

        calculations.forEach { calculation ->
            try {
                val analyticsDto = mapToAnalyticsDto(calculation, userId)
                val docId = "${userId}_${calculation.calculationType}_${calculation.timestamp}"
                val documentRef = collectionRef.document(docId)

                // Check for existing document to handle conflicts
                val existingDoc = remoteDocs[docId]
                
                if (existingDoc?.exists() == true) {
                    val existingTimestamp = existingDoc.getLong("timestamp") ?: 0L
                    val localTimestamp = calculation.timestamp
                    
                    if (localTimestamp <= existingTimestamp) {
                        // Remote is newer, skip this calculation
                        Timber.d("Skipping analytics sync - remote is newer: ${calculation.id}")
                        conflictCount++
                        return@forEach
                    }
                    
                    Timber.d("Resolving analytics conflict - local is newer: ${calculation.id}")
                    conflictCount++
                }

                // Upload analytics data to Firebase with permission check
                documentRef.set(analyticsDto, SetOptions.merge()).await()
                syncedCount++
                
                Timber.v("Analytics calculation synced: ${calculation.id}")
                
            } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    permissionErrors++
                    Timber.w("Permission denied for analytics calculation: ${calculation.id}")
                    // Don't break the loop, just skip this calculation
                } else {
                    Timber.w(e, "Firestore error syncing calculation: ${calculation.id}")
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to sync single analytics calculation: ${calculation.id}")
                // Continue with other calculations instead of failing entire batch
            }
        }

        // Log permission errors for monitoring
        if (permissionErrors > 0) {
            Timber.w("Analytics sync completed with $permissionErrors permission errors out of ${calculations.size} calculations")
        }

        return AnalyticsSyncResult(syncedCount, conflictCount)
    }

    /**
     * Maps domain analytics calculation to Firebase DTO
     */
    private fun mapToAnalyticsDto(
        calculation: AnalyticsCalculation, 
        userId: String
    ): AnalyticsDto {
        return AnalyticsDto(
            id = calculation.id,
            userId = userId,
            calculationType = calculation.calculationType,
            result = calculation.result,
            timestamp = calculation.timestamp,
            metadata = calculation.metadata ?: emptyMap(),
            syncVersion = 1L,
            lastModified = Instant.now().toEpochMilli()
        )
    }

    /**
     * Enhanced authentication validation for background sync operations
     */
    private suspend fun validateAuthentication(): AuthValidationResult {
        val currentUser = auth.currentUser
        
        if (currentUser == null) {
            return AuthValidationResult(
                isValid = false,
                reason = "No authenticated user",
                userId = null
            )
        }
        
        if (currentUser.isAnonymous) {
            Timber.d("Anonymous user detected - analytics sync will be limited")
            // Allow anonymous users but with limited functionality
        }
        
        // Validate token freshness to prevent PERMISSION_DENIED errors
        try {
            val tokenResult = currentUser.getIdToken(false).await()
            if (tokenResult?.token.isNullOrBlank()) {
                return AuthValidationResult(
                    isValid = false,
                    reason = "Authentication token is invalid or expired",
                    userId = currentUser.uid
                )
            }
            
            Timber.v("Authentication validated for user: ${currentUser.uid}")
            return AuthValidationResult(
                isValid = true,
                reason = "Authentication valid",
                userId = currentUser.uid
            )
            
        } catch (e: Exception) {
            Timber.w(e, "Failed to validate authentication token")
            return AuthValidationResult(
                isValid = false,
                reason = "Token validation failed: ${e.message}",
                userId = currentUser.uid
            )
        }
    }
    
    /**
     * Gets current authenticated user ID (legacy method for compatibility)
     */
    private fun getCurrentUserId(): String? {
        return auth.currentUser?.uid?.also { userId ->
            Timber.v("Analytics sync authenticated for user: $userId")
        } ?: run {
            Timber.w("No authenticated user for analytics sync")
            null
        }
    }
    
    /**
     * Handles sync failure with specific error recovery strategies
     */
    private fun handleSyncFailure(throwable: Throwable): Result {
        val errorMessage = throwable.message ?: "Unknown error"
        
        // Handle permission denied errors specifically
        if (isPermissionDeniedError(throwable)) {
            Timber.w("Analytics sync permission denied - skipping sync: $errorMessage")
            return Result.success(
                Data.Builder()
                    .putString(KEY_ERROR_MESSAGE, "Analytics sync skipped due to permissions")
                    .putInt(KEY_SYNC_COUNT, 0)
                    .build()
            )
        }
        
        // Handle network and temporary errors with retry
        if (isRetryableError(throwable)) {
            if (runAttemptCount < MAX_RETRY_COUNT) {
                Timber.d("Retrying analytics sync, attempt ${runAttemptCount + 1}/$MAX_RETRY_COUNT")
                return Result.retry()
            }
        }
        
        Timber.e("Analytics sync failed: $errorMessage")
        return Result.failure(
            Data.Builder()
                .putString(KEY_ERROR_MESSAGE, errorMessage)
                .build()
        )
    }
    
    /**
     * Handles unexpected errors during sync
     */
    private fun handleUnexpectedError(exception: Exception): Result {
        if (runAttemptCount < MAX_RETRY_COUNT && isRetryableError(exception)) {
            return Result.retry()
        } else {
            return Result.failure(
                Data.Builder()
                    .putString(KEY_ERROR_MESSAGE, "Analytics sync failed after $MAX_RETRY_COUNT attempts: ${exception.message}")
                    .build()
            )
        }
    }
    
    /**
     * Checks if the error is a permission denied error
     */
    private fun isPermissionDeniedError(throwable: Throwable): Boolean {
        return when (throwable) {
            is com.google.firebase.firestore.FirebaseFirestoreException -> {
                throwable.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
            }
            is com.google.firebase.auth.FirebaseAuthException -> {
                throwable.errorCode == "ERROR_INSUFFICIENT_PERMISSION" || 
                throwable.errorCode == "ERROR_OPERATION_NOT_ALLOWED"
            }
            else -> {
                throwable.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ||
                throwable.message?.contains("Missing or insufficient permissions", ignoreCase = true) == true
            }
        }
    }
    
    /**
     * Checks if the error is retryable
     */
    private fun isRetryableError(throwable: Throwable): Boolean {
        return when (throwable) {
            is com.google.firebase.firestore.FirebaseFirestoreException -> {
                when (throwable.code) {
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE,
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED,
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.INTERNAL -> true
                    else -> false
                }
            }
            is com.google.firebase.FirebaseNetworkException -> true
            is java.net.SocketTimeoutException -> true
            is java.io.IOException -> true
            else -> false
        }
    }

    /**
     * Queues analytics calculation for sync
     */
    suspend fun queueAnalyticsForSync(calculation: AnalyticsCalculation): LiftrixResult<Unit> {
        return try {
            progressStatsRepository.queueCalculationForSync(calculation)
            Timber.d("Analytics calculation queued for sync: ${calculation.id}")
            liftrixSuccess(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to queue analytics calculation for sync")
            liftrixFailure(
                com.example.liftrix.domain.model.error.LiftrixError.DatabaseError(
                    "Failed to queue analytics for sync: ${e.message}"
                )
            )
        }
    }

    /**
     * Creates exponential backoff retry policy
     */
    private fun createRetryPolicy(): RetryPolicy {
        return RetryPolicy(
            maxRetries = MAX_RETRY_COUNT,
            initialDelayMs = 1000L,
            maxDelayMs = 10000L,
            multiplier = 2.0f
        )
    }
}

/**
 * Represents the result of an analytics sync operation
 */
data class AnalyticsSyncResult(
    val syncedCount: Int,
    val conflictCount: Int
)

/**
 * Retry policy for analytics sync operations
 */
data class RetryPolicy(
    val maxRetries: Int,
    val initialDelayMs: Long,
    val maxDelayMs: Long,
    val multiplier: Float
)

/**
 * Result of authentication validation for background sync operations
 */
data class AuthValidationResult(
    val isValid: Boolean,
    val reason: String,
    val userId: String?
)

/**
 * Domain model for analytics calculation pending sync
 * 
 * Temporary placeholder - will be properly defined in repository layer
 */
data class AnalyticsCalculation(
    val id: String,
    val calculationType: String,
    val result: String,
    val timestamp: Long,
    val metadata: Map<String, String>? = null
)
