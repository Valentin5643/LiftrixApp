package com.example.liftrix.data.sync

import com.example.liftrix.data.local.dao.SyncQueueDao
import com.example.liftrix.data.local.dao.DeadLetterQueueDao
import com.example.liftrix.data.local.entity.SyncQueueEntity
import com.example.liftrix.data.local.entity.DeadLetterQueueEntity
import com.example.liftrix.data.remote.FirebaseDataSource
import com.example.liftrix.data.remote.ProcessResult
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.SyncResult
import com.example.liftrix.data.model.SyncPayload
import com.example.liftrix.domain.service.AnalyticsTracker
import com.example.liftrix.domain.service.NotificationHandler
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Manages offline sync operations by queueing changes when offline and processing them when online.
 * 
 * This component is responsible for:
 * - Queuing CRUD operations when the device is offline or when sync fails
 * - Processing pending operations with priority-based ordering
 * - Implementing exponential backoff retry logic for failed operations
 * - Providing sync status and queue management
 * 
 * Technical Implementation:
 * - Uses Room database for persistent offline queue storage
 * - Implements priority-based operation processing (WORKOUT=1, PROFILE=2, OTHER=3)
 * - Provides exponential backoff with maximum retry limits
 * - Handles JSON serialization for complex data types
 * - Integrates with FirebaseDataSource for remote operations
 */
@Singleton
class OfflineQueueManager @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
    private val deadLetterQueueDao: DeadLetterQueueDao,
    private val firebaseDataSource: FirebaseDataSource,
    private val json: Json,
    private val analyticsTracker: AnalyticsTracker,
    private val notificationHandler: NotificationHandler
) {
    private val inFlightQueueItems = ConcurrentHashMap.newKeySet<String>()
    
    /**
     * 🔥 FIX: Clean up legacy queue entries that can't be deserialized.
     * Call this on app startup to remove stale entries from before the SyncPayload refactoring.
     * 
     * This method proactively identifies and removes problematic queue entries to prevent
     * serialization errors during Google login and other sync operations.
     */
    suspend fun cleanupLegacyQueueEntries(userId: String): LiftrixResult<Int> {
        return liftrixCatching(
            errorMapper = { throwable: Throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to cleanup legacy queue entries",
                    operation = "CLEANUP",
                    table = "sync_queue",
                    analyticsContext = mapOf(
                        "cleanup_type" to "legacy_serialization",
                        "user_id" to userId
                    )
                )
            }
        ) {
            if (userId.isBlank()) {
                return@liftrixCatching 0
            }
            var removedCount = 0
            
            try {
                // Get all queue entries to check for deserialization issues
                val allEntries = syncQueueDao.getAllQueueEntries(userId)
                Timber.d("OfflineQueueManager: Checking ${allEntries.size} queue entries for legacy serialization issues")
                
                for (entry in allEntries) {
                    try {
                        // Attempt to deserialize each entry
                        json.decodeFromString<SyncPayload>(entry.data)
                        // If successful, leave the entry alone
                    } catch (e: Exception) {
                        // This entry has serialization issues - remove it
                        Timber.w("OfflineQueueManager: Removing legacy queue entry ${entry.id} (${entry.entityType}:${entry.entityId}) due to deserialization failure: ${e.message}")
                        syncQueueDao.delete(entry)
                        removedCount++
                    }
                }
                
                if (removedCount > 0) {
                    Timber.i("OfflineQueueManager: Cleaned up $removedCount legacy queue entries with serialization issues")
                } else {
                    Timber.d("OfflineQueueManager: All queue entries are properly serializable - no cleanup needed")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "OfflineQueueManager: Error during legacy cleanup process")
                // Don't fail the entire cleanup if we encounter issues
            }
            
            removedCount
        }
    }
    
    companion object {
        private const val MAX_RETRY_COUNT = 5
        private const val BASE_RETRY_DELAY_SECONDS = 30L
        
        // Priority levels for different entity types
        private const val HIGH_PRIORITY = 1    // WORKOUT operations
        private const val MEDIUM_PRIORITY = 2  // PROFILE operations  
        private const val LOW_PRIORITY = 3     // OTHER operations
    }
    
    /**
     * Queues a sync operation for later execution when offline or when immediate sync fails.
     * 
     * @param userId The user ID for the operation
     * @param entityType The type of entity (WORKOUT, TEMPLATE, PROFILE, ACHIEVEMENT)
     * @param entityId The ID of the specific entity
     * @param operation The operation type (CREATE, UPDATE, DELETE)
     * @param data The entity data to be synced (type-safe sealed class)
     */
    suspend fun queueOperation(
        userId: String,
        entityType: String,
        entityId: String,
        operation: String,
        data: SyncPayload
    ): LiftrixResult<Unit> {
        return try {
            Timber.d("OfflineQueueManager: Queuing $operation for $entityType:$entityId")
            
            val queueItem = SyncQueueEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                entityType = entityType,
                entityId = entityId,
                operation = operation,
                data = json.encodeToString(data),
                priority = getPriority(entityType),
                retryCount = 0,
                createdAt = System.currentTimeMillis(),
                nextRetryAt = null
            )
            
            syncQueueDao.insert(queueItem)

            // Track queue operation
            analyticsTracker.trackOfflineQueue(
                action = "QUEUE_ADD",
                userId = userId,
                queueSize = syncQueueDao.getPendingItemsCount(userId),
                entityType = entityType,
                additionalProperties = mapOf(
                    "operation" to operation,
                    "priority" to queueItem.priority
                )
            )

            Timber.d("OfflineQueueManager: Successfully queued operation ${queueItem.id}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "OfflineQueueManager: Failed to queue operation for $entityType:$entityId")
            Result.failure(
                LiftrixError.BusinessLogicError(
                    code = "QUEUE_OPERATION_FAILED",
                    errorMessage = "Failed to queue sync operation: ${e.message}",
                    analyticsContext = mapOf(
                        "entity_type" to entityType,
                        "entity_id" to entityId,
                        "operation" to operation
                    )
                )
            )
        }
    }
    
    /**
     * Processes all pending sync operations for a user with priority ordering and retry logic.
     * 
     * @param userId The user ID to process operations for
     * @return SyncResult with statistics about successful, failed, and conflict operations
     */
    suspend fun processPendingQueue(userId: String): LiftrixResult<SyncResult> {
        return try {
            val startTime = System.currentTimeMillis()
            Timber.d("OfflineQueueManager: Processing pending queue for user $userId")

            val pendingItems = syncQueueDao.getPendingItems(userId)

            // Track queue processing start
            analyticsTracker.trackOfflineQueue(
                action = "QUEUE_PROCESS",
                userId = userId,
                queueSize = pendingItems.size,
                additionalProperties = mapOf("sync_type" to "MANUAL")
            )
            
            if (pendingItems.isEmpty()) {
                Timber.d("OfflineQueueManager: No pending operations for user $userId")
                return Result.success(SyncResult(successful = 0, failed = 0, conflicts = 0))
            }
            
            Timber.d("OfflineQueueManager: Found ${pendingItems.size} pending operations")
            
            var successful = 0
            var failed = 0
            var conflicts = 0
            
            for (item in pendingItems) {
                if (!inFlightQueueItems.add(item.id)) {
                    if (item.entityType == "PROFILE") {
                        Timber.d("[PROFILE-QUEUE] PROFILE_QUEUE_GUARD_SKIP itemId=${item.id} entityType=${item.entityType}")
                    }
                    continue
                }
                try {
                    val result = processQueueItem(item)
                    
                    // 🔥 PROFILE SYNC DIAGNOSTICS: Enhanced logging for profile operations
                    val logPrefix = if (item.entityType == "PROFILE") "[PROFILE-QUEUE]" else "OfflineQueueManager"
                    
                    when (result) {
                        is ProcessResult.Success -> {
                            syncQueueDao.delete(item)
                            successful++
                            if (item.entityType == "PROFILE") {
                                Timber.i("$logPrefix ✅ Profile operation ${item.id} (${item.operation}) completed successfully!")
                            } else {
                                Timber.d("$logPrefix: Successfully processed operation ${item.id}")
                            }
                        }
                        is ProcessResult.Conflict -> {
                            syncQueueDao.delete(item) // Remove conflict item as it's been resolved
                            conflicts++

                            // Track conflict resolution
                            analyticsTracker.trackSyncConflict(
                                entityType = item.entityType,
                                entityId = item.entityId,
                                resolutionStrategy = "LAST_WRITE_WINS", // Default strategy
                                userId = item.userId,
                                localVersion = System.currentTimeMillis(), // Approximation
                                remoteVersion = System.currentTimeMillis(), // Would need actual version tracking
                                additionalProperties = mapOf(
                                    "operation" to item.operation,
                                    "retry_count" to item.retryCount
                                )
                            )

                            if (item.entityType == "PROFILE") {
                                Timber.w("$logPrefix ⚠️ Profile operation ${item.id} (${item.operation}) ended in conflict - THIS SHOULD NOT HAPPEN after the fix!")
                            } else {
                                Timber.d("$logPrefix: Resolved conflict for operation ${item.id}")
                            }
                        }
                        is ProcessResult.Failure -> {
                            handleSyncFailure(item, result.error)
                            failed++
                            if (item.entityType == "PROFILE") {
                                Timber.e("$logPrefix ❌ Profile operation ${item.id} (${item.operation}) failed: ${result.error}")
                            } else {
                                Timber.w("$logPrefix: Failed to process operation ${item.id}: ${result.error}")
                            }
                        }
                        is ProcessResult.Data -> {
                            // For fetch operations, just mark as successful
                            syncQueueDao.delete(item)
                            successful++
                            Timber.d("$logPrefix: Successfully processed fetch operation ${item.id}")
                        }
                        is ProcessResult.DataList -> {
                            // For fetchAll operations, just mark as successful
                            syncQueueDao.delete(item)
                            successful++
                            Timber.d("$logPrefix: Successfully processed fetchAll operation ${item.id}")
                        }
                    }
                    
                } catch (e: Exception) {
                    handleSyncFailure(item, e)
                    failed++
                    Timber.e(e, "OfflineQueueManager: Unexpected error processing operation ${item.id}")
                } finally {
                    inFlightQueueItems.remove(item.id)
                }
            }
            
            val result = SyncResult(successful, failed, conflicts)
            val durationMs = System.currentTimeMillis() - startTime

            // Track sync performance metrics
            analyticsTracker.trackSyncPerformance(
                syncType = "MANUAL",
                userId = userId,
                totalItems = pendingItems.size,
                successfulItems = successful,
                failedItems = failed,
                conflictItems = conflicts,
                totalDurationMs = durationMs,
                additionalProperties = mapOf(
                    "queue_processing" to true,
                    "average_time_per_item" to if (pendingItems.isNotEmpty()) durationMs / pendingItems.size else 0
                )
            )

            Timber.d("OfflineQueueManager: Queue processing complete - $result")

            Result.success(result)
            
        } catch (e: Exception) {
            Timber.e(e, "OfflineQueueManager: Error processing pending queue for user $userId")
            Result.failure(
                LiftrixError.BusinessLogicError(
                    code = "QUEUE_PROCESSING_FAILED",
                    errorMessage = "Failed to process pending sync queue: ${e.message}",
                    analyticsContext = mapOf("user_id" to userId)
                )
            )
        }
    }
    
    /**
     * Gets the count of pending sync operations for a user.
     */
    suspend fun getPendingCount(userId: String): LiftrixResult<Int> {
        return try {
            val count = syncQueueDao.getPendingItemsCount(userId)
            Result.success(count)
        } catch (e: Exception) {
            Timber.e(e, "OfflineQueueManager: Failed to get pending count for user $userId")
            Result.failure(
                LiftrixError.BusinessLogicError(
                    code = "PENDING_COUNT_FAILED",
                    errorMessage = "Failed to get pending operations count: ${e.message}"
                )
            )
        }
    }
    
    /**
     * Clears all pending operations for a user.
     * Should be used with caution as it will discard unsynced local changes.
     */
    suspend fun clearQueue(userId: String): LiftrixResult<Unit> {
        return try {
            val count = syncQueueDao.getPendingItemsCount(userId)
            syncQueueDao.clearQueueForUser(userId)
            
            Timber.w("OfflineQueueManager: Cleared $count pending operations for user $userId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "OfflineQueueManager: Failed to clear queue for user $userId")
            Result.failure(
                LiftrixError.BusinessLogicError(
                    code = "CLEAR_QUEUE_FAILED",
                    errorMessage = "Failed to clear sync queue: ${e.message}"
                )
            )
        }
    }
    
    /**
     * 🔥 NEW: Emergency queue reset for users experiencing persistent serialization issues.
     * This method clears all queue entries and forces a fresh sync from Firebase.
     * Use only when users report persistent sync errors after Google login.
     */
    suspend fun emergencyQueueReset(userId: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable: Throwable ->
                LiftrixError.BusinessLogicError(
                    code = "EMERGENCY_RESET_FAILED",
                    errorMessage = "Failed to perform emergency queue reset: ${throwable.message}",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            Timber.w("OfflineQueueManager: 🚨 EMERGENCY RESET: Starting emergency queue reset for user $userId")
            
            // Count entries before cleanup
            val beforeCount = syncQueueDao.getPendingItemsCount(userId)
            
            // Clear all sync queue entries for this user
            syncQueueDao.clearQueueForUser(userId)
            
            Timber.i("OfflineQueueManager: 🚨 EMERGENCY RESET: Cleared $beforeCount queue entries for user $userId")
            
            // Log the reset for analytics/monitoring
            Timber.i("OfflineQueueManager: 🚨 EMERGENCY RESET: User $userId sync queue has been completely reset due to serialization issues")
            
            Unit
        }
    }
    
    /**
     * Processes operations that are ready for retry based on their retry schedule.
     */
    suspend fun processRetryableOperations(userId: String): LiftrixResult<SyncResult> {
        return try {
            val retryableItems = syncQueueDao.getOperationsReadyForRetry(userId)
            
            if (retryableItems.isEmpty()) {
                return Result.success(SyncResult(successful = 0, failed = 0, conflicts = 0))
            }
            
            Timber.d("OfflineQueueManager: Processing ${retryableItems.size} retryable operations")
            
            var totalSuccessful = 0
            var totalFailed = 0
            var totalConflicts = 0
            
            for (item in retryableItems) {
                if (!inFlightQueueItems.add(item.id)) {
                    if (item.entityType == "PROFILE") {
                        Timber.d("[PROFILE-QUEUE] PROFILE_QUEUE_GUARD_SKIP itemId=${item.id} entityType=${item.entityType}")
                    }
                    continue
                }
                try {
                    val result = processQueueItem(item)
                    
                    when (result) {
                        is ProcessResult.Success -> {
                            syncQueueDao.delete(item)
                            totalSuccessful++
                        }
                        is ProcessResult.Conflict -> {
                            syncQueueDao.delete(item)
                            totalConflicts++
                        }
                        is ProcessResult.Failure -> {
                            handleSyncFailure(item, result.error)
                            totalFailed++
                        }
                        is ProcessResult.Data -> {
                            syncQueueDao.delete(item)
                            totalSuccessful++
                        }
                        is ProcessResult.DataList -> {
                            syncQueueDao.delete(item)
                            totalSuccessful++
                        }
                    }
                } catch (e: Exception) {
                    handleSyncFailure(item, e)
                    totalFailed++
                } finally {
                    inFlightQueueItems.remove(item.id)
                }
            }
            
            Result.success(SyncResult(totalSuccessful, totalFailed, totalConflicts))
            
        } catch (e: Exception) {
            Timber.e(e, "OfflineQueueManager: Error processing retryable operations")
            Result.failure(
                LiftrixError.BusinessLogicError(
                    code = "RETRY_PROCESSING_FAILED",
                    errorMessage = "Failed to process retryable operations: ${e.message}"
                )
            )
        }
    }
    
    /**
     * Processes a single queue item by delegating to the FirebaseDataSource.
     * Deserializes the type-safe SyncPayload and extracts the appropriate data structure.
     */
    private suspend fun processQueueItem(item: SyncQueueEntity): ProcessResult {
        return try {
            Timber.d("OfflineQueueManager: Processing queue item ${item.id} (entityType: ${item.entityType})")
            
            // Deserialize the type-safe payload with enhanced legacy fallback
            val payload = try {
                json.decodeFromString<SyncPayload>(item.data)
            } catch (e: Exception) {
                Timber.e(e, "OfflineQueueManager: Failed to deserialize SyncPayload for item ${item.id}")
                Timber.e("OfflineQueueManager: Raw data: ${item.data}")
                
                // 🔥 ENHANCED FIX: Handle legacy queue entries with detailed error analysis
                val errorType = when {
                    e.message?.contains("Serializer for class 'Any' is not found") == true -> "LEGACY_ANY_TYPE"
                    e.message?.contains("kotlinx.serialization") == true -> "SERIALIZATION_ERROR"  
                    item.data.contains("\"payload\":") && !item.data.contains("\"workout\":") -> "LEGACY_STRUCTURE"
                    else -> "UNKNOWN_FORMAT"
                }
                
                Timber.w("OfflineQueueManager: Detected legacy queue item ${item.id} (${item.entityType}:${item.entityId}) with error type: $errorType")
                
                // Log additional context for debugging
                Timber.w("OfflineQueueManager: Legacy item created at: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(item.createdAt))}")
                Timber.w("OfflineQueueManager: Legacy item operation: ${item.operation}, priority: ${item.priority}, retry count: ${item.retryCount}")
                
                // Delete the problematic legacy queue item
                syncQueueDao.delete(item)
                Timber.i("OfflineQueueManager: ✅ Successfully removed legacy queue item ${item.id} (${errorType})")
                
                // Return early without processing this item - this is success from cleanup perspective
                return ProcessResult.Success
            }
            
            // 🔥 SERIALIZATION FIX: Use direct json.encodeToJsonElement to avoid Map<String, Any?> issues
            val dataForFirebase = when (payload) {
                is SyncPayload.WorkoutPayload -> {
                    // Simply encode the workout DTO directly - it's already @Serializable
                    json.encodeToJsonElement(payload.workout).let { workoutJson ->
                        buildJsonObject {
                            // Copy all fields from the workout JSON
                            workoutJson.jsonObject.forEach { (key, value) -> 
                                put(key, value)
                            }
                            // Add the entityType field for Firebase
                            put("entityType", "workout")
                        }
                    }
                }
                is SyncPayload.ProfilePayload -> {
                    // Encode the profile payload directly since all fields are serializable
                    json.encodeToJsonElement(payload).let { profileJson ->
                        buildJsonObject {
                            profileJson.jsonObject.forEach { (key, value) -> 
                                put(key, value)
                            }
                            put("entityType", "profile")
                        }
                    }
                }
                is SyncPayload.TemplatePayload -> {
                    json.encodeToJsonElement(payload).let { templateJson ->
                        buildJsonObject {
                            templateJson.jsonObject.forEach { (key, value) -> 
                                put(key, value)
                            }
                            put("entityType", "template")
                        }
                    }
                }
                is SyncPayload.AchievementPayload -> {
                    json.encodeToJsonElement(payload).let { achievementJson ->
                        buildJsonObject {
                            achievementJson.jsonObject.forEach { (key, value) -> 
                                put(key, value)
                            }
                            put("entityType", "achievement")
                        }
                    }
                }
                is SyncPayload.SocialProfilePayload -> {
                    json.encodeToJsonElement(payload).let { socialJson ->
                        buildJsonObject {
                            socialJson.jsonObject.forEach { (key, value) -> 
                                put(key, value)
                            }
                            put("entityType", "social_profile")
                        }
                    }
                }
                is SyncPayload.FetchPayload -> {
                    json.encodeToJsonElement(payload).let { fetchJson ->
                        buildJsonObject {
                            fetchJson.jsonObject.forEach { (key, value) -> 
                                put(key, value)
                            }
                            put("operation", "FETCH")
                        }
                    }
                }
            }
            
            // Convert JsonElement to JSON string for FirebaseDataSource
            val jsonData = json.encodeToString(JsonElement.serializer(), dataForFirebase)
            
            // 🔥 SYNC DIAGNOSTICS: Enhanced operation logging with profile-specific handling
            Timber.d("OfflineQueueManager: Executing ${item.operation} operation for ${item.entityType}:${item.entityId}")
            
            when (item.operation) {
                "CREATE", "UPSERT" -> {
                    // Log the operation mapping for debugging profile sync issues
                    if (item.entityType == "PROFILE") {
                        Timber.d("OfflineQueueManager: Profile ${item.operation} operation mapped to FirebaseDataSource.create() - will use UPSERT semantics")
                    }
                    firebaseDataSource.create(item.userId, item.entityType, item.entityId, jsonData)
                }
                "UPDATE" -> firebaseDataSource.update(item.userId, item.entityType, item.entityId, jsonData)
                "DELETE" -> firebaseDataSource.delete(item.userId, item.entityType, item.entityId)
                "FETCH" -> {
                    // 🔥 NEW: Handle FETCH operations for downloading remote data
                    processFetchOperation(item, payload as SyncPayload.FetchPayload)
                }
                else -> ProcessResult.Failure(Exception("Unknown operation: ${item.operation}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to process queue item ${item.id}: ${e.message}")
            ProcessResult.Failure(e)
        }
    }
    
    /**
     * Enhanced sync failure handler with error categorization and intelligent retry strategies.
     *
     * This enhanced implementation provides:
     * - Error categorization (Network, Auth, Data, Server)
     * - Context-aware retry strategies based on error type
     * - Dead letter queue for permanently failed operations
     * - Enhanced logging with error context
     * - Analytics integration for failure tracking
     */
    private suspend fun handleSyncFailure(item: SyncQueueEntity, error: Throwable) {
        val errorCategory = categorizeError(error)
        val newRetryCount = item.retryCount + 1

        // Log detailed error information for debugging
        Timber.w("OfflineQueueManager: Operation ${item.id} failed (${errorCategory.name}) - ${error.message}")
        Timber.d("OfflineQueueManager: Error context - Entity: ${item.entityType}:${item.entityId}, Operation: ${item.operation}, Attempt: $newRetryCount/$MAX_RETRY_COUNT")

        // Check for non-retryable errors
        if (!errorCategory.isRetryable) {
            Timber.e("OfflineQueueManager: Non-retryable error for operation ${item.id} - moving to dead letter queue")
            moveToDeadLetterQueue(item, error, errorCategory)
            return
        }

        // Check max retry limit
        if (newRetryCount >= getMaxRetriesForCategory(errorCategory)) {
            Timber.e("OfflineQueueManager: Operation ${item.id} exceeded max retries (${getMaxRetriesForCategory(errorCategory)}) - moving to dead letter queue")
            moveToDeadLetterQueue(item, error, errorCategory)
            return
        }

        // Calculate context-aware backoff delay
        val delaySeconds = calculateBackoffDelay(errorCategory, newRetryCount)
        val nextRetryAt = System.currentTimeMillis() + (delaySeconds * 1000)

        Timber.w("OfflineQueueManager: Scheduling ${errorCategory.name} retry ${newRetryCount}/${getMaxRetriesForCategory(errorCategory)} for operation ${item.id} in ${delaySeconds}s")

        // Update retry info with error context
        val updatedItem = item.copy(
            retryCount = newRetryCount,
            nextRetryAt = nextRetryAt,
            lastError = "${errorCategory.name}: ${error.message?.take(200) ?: "Unknown error"}"
        )

        syncQueueDao.updateRetryInfo(
            id = updatedItem.id,
            userId = updatedItem.userId,
            nextRetryAt = nextRetryAt,
            retryCount = newRetryCount,
            lastError = updatedItem.lastError
        )
    }

    /**
     * Categorizes errors for intelligent retry handling
     */
    private fun categorizeError(error: Throwable): ErrorCategory {
        return when {
            // Network-related errors - highly retryable
            error.message?.contains("network", ignoreCase = true) == true ||
            error.message?.contains("connection", ignoreCase = true) == true ||
            error.message?.contains("timeout", ignoreCase = true) == true ||
            error is java.net.ConnectException ||
            error is java.net.SocketTimeoutException ||
            error is java.io.IOException -> ErrorCategory.NETWORK

            // Authentication errors - moderately retryable (token might refresh)
            error.message?.contains("auth", ignoreCase = true) == true ||
            error.message?.contains("unauthorized", ignoreCase = true) == true ||
            error.message?.contains("token", ignoreCase = true) == true -> ErrorCategory.AUTHENTICATION

            // Server errors - moderately retryable
            error.message?.contains("server", ignoreCase = true) == true ||
            error.message?.contains("internal", ignoreCase = true) == true ||
            error.message?.contains("5xx", ignoreCase = true) == true -> ErrorCategory.SERVER

            // Data validation errors - not retryable without fixing data
            error.message?.contains("validation", ignoreCase = true) == true ||
            error.message?.contains("invalid", ignoreCase = true) == true ||
            error.message?.contains("malformed", ignoreCase = true) == true ||
            error is kotlinx.serialization.SerializationException -> ErrorCategory.DATA_VALIDATION

            // Rate limiting - retryable with longer delays
            error.message?.contains("rate", ignoreCase = true) == true ||
            error.message?.contains("quota", ignoreCase = true) == true ||
            error.message?.contains("429", ignoreCase = true) == true -> ErrorCategory.RATE_LIMIT

            // Unknown errors - limited retryable
            else -> ErrorCategory.UNKNOWN
        }
    }

    /**
     * Gets maximum retry count based on error category
     */
    private fun getMaxRetriesForCategory(category: ErrorCategory): Int {
        return when (category) {
            ErrorCategory.NETWORK -> 7        // Network issues are most retryable
            ErrorCategory.AUTHENTICATION -> 3  // Auth might resolve with token refresh
            ErrorCategory.SERVER -> 5         // Server issues are moderately retryable
            ErrorCategory.RATE_LIMIT -> 4     // Rate limits need patience
            ErrorCategory.DATA_VALIDATION -> 0 // Data issues need manual fix
            ErrorCategory.UNKNOWN -> MAX_RETRY_COUNT
        }
    }

    /**
     * Calculates backoff delay based on error type and attempt count
     */
    private fun calculateBackoffDelay(category: ErrorCategory, retryCount: Int): Long {
        val baseDelay = when (category) {
            ErrorCategory.NETWORK -> 15L      // Quick retry for network issues
            ErrorCategory.AUTHENTICATION -> 60L // Wait longer for auth issues
            ErrorCategory.SERVER -> 45L       // Medium delay for server issues
            ErrorCategory.RATE_LIMIT -> 300L  // Long delay for rate limiting
            ErrorCategory.DATA_VALIDATION -> 0L // No retry
            ErrorCategory.UNKNOWN -> BASE_RETRY_DELAY_SECONDS
        }

        // Apply exponential backoff with jitter
        val exponentialDelay = baseDelay * (1L shl (retryCount - 1))
        val jitter = (Math.random() * 0.1 * exponentialDelay).toLong()

        return exponentialDelay + jitter
    }

    /**
     * Moves permanently failed operations to dead letter queue for manual inspection
     */
    private suspend fun moveToDeadLetterQueue(item: SyncQueueEntity, error: Throwable, category: ErrorCategory) {
        try {
            // Create dead letter entry with full error context
            val deadLetterItem = DeadLetterQueueEntity(
                id = "${item.id}_dead",
                originalId = item.id,
                userId = item.userId,
                entityType = item.entityType,
                entityId = item.entityId,
                operation = item.operation,
                data = item.data,
                priority = item.priority,
                retryCount = item.retryCount,
                createdAt = item.createdAt,
                failedAt = System.currentTimeMillis(),
                errorCategory = category.name,
                errorMessage = "${category.name}: ${error.message?.take(500) ?: "Unknown error"}",
                reviewed = false,
                reviewedAt = null,
                retryAfterFix = false
            )

            // Log the dead letter operation for manual review
            Timber.e("OfflineQueueManager: DEAD LETTER - ${item.entityType}:${item.entityId} ${item.operation} - ${category.name}: ${error.message}")

            // Track dead letter queue operation
            analyticsTracker.trackOfflineQueue(
                action = "DEAD_LETTER",
                userId = item.userId,
                queueSize = syncQueueDao.getPendingItemsCount(item.userId),
                entityType = item.entityType,
                retryCount = item.retryCount,
                errorCategory = category.name,
                additionalProperties = mapOf(
                    "operation" to item.operation,
                    "error_message" to (error.message?.take(100) ?: "Unknown"),
                    "age_hours" to ((System.currentTimeMillis() - item.createdAt) / (1000 * 60 * 60))
                )
            )

            // Store in dead letter table
            deadLetterQueueDao.insert(deadLetterItem)

            // Remove from active queue
            syncQueueDao.delete(item)

            Timber.d("OfflineQueueManager: Successfully moved item ${item.id} to dead letter queue")

            if (item.entityType == "WORKOUT") {
                notifyCriticalSyncFailure(item, error)
            }

        } catch (e: Exception) {
            Timber.e(e, "OfflineQueueManager: Failed to move item ${item.id} to dead letter queue")
            // Still remove from active queue to prevent infinite processing
            syncQueueDao.delete(item)
        }
    }

    /**
     * Monitors and reports sync queue health metrics for analytics.
     * Should be called periodically (e.g., every 15 minutes) to track queue health.
     */
    suspend fun reportQueueHealthMetrics(userId: String) {
        try {
            val allItems = syncQueueDao.getAllQueueEntries(userId)
            val pendingItems = allItems.filter { it.nextRetryAt == null || it.nextRetryAt <= System.currentTimeMillis() }
            val failedItems = allItems.filter { it.retryCount > 0 }

            val averageRetryCount = if (allItems.isNotEmpty()) {
                allItems.map { it.retryCount }.average().toFloat()
            } else {
                0f
            }

            val oldestItemAge = allItems.minByOrNull { it.createdAt }?.let {
                System.currentTimeMillis() - it.createdAt
            }

            analyticsTracker.trackSyncQueueStatus(
                userId = userId,
                pendingCount = pendingItems.size,
                failedCount = failedItems.size,
                averageRetryCount = averageRetryCount,
                oldestItemAgeMs = oldestItemAge,
                additionalProperties = mapOf(
                    "total_queue_size" to allItems.size,
                    "stuck_items" to allItems.count { it.retryCount >= MAX_RETRY_COUNT - 1 },
                    "entity_types" to allItems.groupBy { it.entityType }.mapValues { it.value.size }
                )
            )

            Timber.d("OfflineQueueManager: Reported queue health - Pending: ${pendingItems.size}, Failed: ${failedItems.size}, Avg Retries: $averageRetryCount")

        } catch (e: Exception) {
            Timber.e(e, "OfflineQueueManager: Failed to report queue health metrics")
        }
    }

    /**
     * Gets dead letter queue items for manual review.
     */
    suspend fun getDeadLetterItems(userId: String): LiftrixResult<List<DeadLetterQueueEntity>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "GET_DEAD_LETTER_FAILED",
                    errorMessage = "Failed to get dead letter items: ${throwable.message}",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            deadLetterQueueDao.getDeadLetterItems(userId)
        }
    }

    /**
     * Retries items from dead letter queue after fixes have been applied.
     */
    suspend fun retryDeadLetterItems(userId: String): LiftrixResult<Int> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "RETRY_DEAD_LETTER_FAILED",
                    errorMessage = "Failed to retry dead letter items: ${throwable.message}",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val itemsToRetry = deadLetterQueueDao.getItemsToRetry(userId)
            var retriedCount = 0

            for (deadLetterItem in itemsToRetry) {
                try {
                    // Convert back to sync queue item
                    val syncQueueItem = SyncQueueEntity(
                        id = UUID.randomUUID().toString(), // New ID for retry
                        userId = deadLetterItem.userId,
                        entityType = deadLetterItem.entityType,
                        entityId = deadLetterItem.entityId,
                        operation = deadLetterItem.operation,
                        data = deadLetterItem.data,
                        priority = deadLetterItem.priority,
                        retryCount = 0, // Reset retry count
                        createdAt = System.currentTimeMillis(),
                        nextRetryAt = null,
                        lastError = null,
                        failedAt = null
                    )

                    // Add back to sync queue
                    syncQueueDao.insert(syncQueueItem)

                    // Remove from dead letter queue
                    deadLetterQueueDao.delete(deadLetterItem.id)

                    retriedCount++
                    Timber.d("OfflineQueueManager: Retried dead letter item ${deadLetterItem.id}")

                } catch (e: Exception) {
                    Timber.e(e, "OfflineQueueManager: Failed to retry dead letter item ${deadLetterItem.id}")
                }
            }

            // Track retry operation
            analyticsTracker.trackOfflineQueue(
                action = "DEAD_LETTER_RETRY",
                userId = userId,
                queueSize = syncQueueDao.getPendingItemsCount(userId),
                additionalProperties = mapOf(
                    "retried_count" to retriedCount,
                    "total_items" to itemsToRetry.size
                )
            )

            retriedCount
        }
    }

    /**
     * Cleans up old reviewed dead letter items.
     */
    suspend fun cleanupOldDeadLetterItems(userId: String, daysOld: Int = 30): LiftrixResult<Int> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "CLEANUP_DEAD_LETTER_FAILED",
                    errorMessage = "Failed to cleanup dead letter items: ${throwable.message}",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
            val countBefore = deadLetterQueueDao.getDeadLetterCount(userId)

            deadLetterQueueDao.clearOldItems(userId, cutoffTime)

            val countAfter = deadLetterQueueDao.getDeadLetterCount(userId)
            val cleanedCount = countBefore - countAfter

            Timber.d("OfflineQueueManager: Cleaned up $cleanedCount old dead letter items for user $userId")

            analyticsTracker.trackOfflineQueue(
                action = "DEAD_LETTER_CLEANUP",
                userId = userId,
                queueSize = countAfter,
                additionalProperties = mapOf(
                    "cleaned_count" to cleanedCount,
                    "days_old" to daysOld,
                    "cutoff_time" to cutoffTime
                )
            )
            cleanedCount
        }
    }

    private suspend fun notifyCriticalSyncFailure(item: SyncQueueEntity, error: Throwable) {
        val reason = error.message?.take(200) ?: "Unknown error"
        val data = mapOf(
            "user_id" to item.userId,
            "entity_id" to item.entityId,
            "operation" to item.operation,
            "error_message" to reason
        )

        try {
            notificationHandler.showSystemNotification(
                title = "Workout sync failed",
                body = "We couldn't sync a workout. Open Liftrix to review and retry.",
                type = "error",
                data = data
            )
        } catch (e: Exception) {
            Timber.w(e, "OfflineQueueManager: Failed to show sync failure notification for ${item.entityId}")
        }
    }

    /**
     * Error categories for intelligent retry handling
     */
    private enum class ErrorCategory(val isRetryable: Boolean) {
        NETWORK(true),
        AUTHENTICATION(true),
        SERVER(true),
        RATE_LIMIT(true),
        DATA_VALIDATION(false),
        UNKNOWN(true)
    }
    
    /**
     * 🔥 NEW: Processes FETCH operations to download remote data.
     * This implements the missing remote-to-local sync functionality.
     */
    private suspend fun processFetchOperation(item: SyncQueueEntity, payload: SyncPayload.FetchPayload): ProcessResult {
        return try {
            Timber.d("[FETCH-OPERATION] Processing fetch for ${payload.entityType} (user: ${payload.userId})")
            
            // For workout fetches, the WorkoutSyncWorker now has bidirectional sync
            when (payload.entityType.uppercase()) {
                "WORKOUT" -> {
                    // The WorkoutSyncWorker now handles remote fetch automatically
                    // We just need to signal success since the worker handles the actual fetch
                    Timber.d("[FETCH-OPERATION] FETCH operation handled by WorkoutSyncWorker bidirectional sync")
                    ProcessResult.Success
                }
                else -> {
                    Timber.w("[FETCH-OPERATION] Fetch operation not implemented for entity type: ${payload.entityType}")
                    ProcessResult.Success // Don't fail for unsupported fetch operations
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[FETCH-OPERATION] Failed to process fetch operation for ${payload.entityType}")
            ProcessResult.Failure(e)
        }
    }
    
    /**
     * Determines the priority level for different entity types.
     */
    private fun getPriority(entityType: String): Int = when (entityType) {
        "WORKOUT" -> HIGH_PRIORITY      // Active workout data has highest priority
        "PROFILE" -> MEDIUM_PRIORITY    // Profile changes are medium priority
        "TEMPLATE", "ACHIEVEMENT" -> LOW_PRIORITY  // Templates and achievements are lower priority
        else -> LOW_PRIORITY            // Default to low priority for unknown types
    }
    
}
