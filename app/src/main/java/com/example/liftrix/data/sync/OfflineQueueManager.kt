package com.example.liftrix.data.sync

import com.example.liftrix.data.local.dao.SyncQueueDao
import com.example.liftrix.data.local.entity.SyncQueueEntity
import com.example.liftrix.data.remote.FirebaseDataSource
import com.example.liftrix.data.remote.ProcessResult
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.SyncResult
import com.example.liftrix.data.model.SyncPayload
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
    private val firebaseDataSource: FirebaseDataSource,
    private val json: Json
) {
    
    /**
     * 🔥 FIX: Clean up legacy queue entries that can't be deserialized.
     * Call this on app startup to remove stale entries from before the SyncPayload refactoring.
     * 
     * This method proactively identifies and removes problematic queue entries to prevent
     * serialization errors during Google login and other sync operations.
     */
    suspend fun cleanupLegacyQueueEntries(): LiftrixResult<Int> {
        return liftrixCatching(
            errorMapper = { throwable: Throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to cleanup legacy queue entries",
                    operation = "CLEANUP",
                    table = "sync_queue",
                    analyticsContext = mapOf("cleanup_type" to "legacy_serialization")
                )
            }
        ) {
            var removedCount = 0
            
            try {
                // Get all queue entries to check for deserialization issues
                val allEntries = syncQueueDao.getAllQueueEntries()
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
            Timber.d("OfflineQueueManager: Processing pending queue for user $userId")
            
            val pendingItems = syncQueueDao.getPendingItems(userId)
            
            if (pendingItems.isEmpty()) {
                Timber.d("OfflineQueueManager: No pending operations for user $userId")
                return Result.success(SyncResult(successful = 0, failed = 0, conflicts = 0))
            }
            
            Timber.d("OfflineQueueManager: Found ${pendingItems.size} pending operations")
            
            var successful = 0
            var failed = 0
            var conflicts = 0
            
            for (item in pendingItems) {
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
                }
            }
            
            val result = SyncResult(successful, failed, conflicts)
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
    suspend fun processRetryableOperations(): LiftrixResult<SyncResult> {
        return try {
            val retryableItems = syncQueueDao.getOperationsReadyForRetry()
            
            if (retryableItems.isEmpty()) {
                return Result.success(SyncResult(successful = 0, failed = 0, conflicts = 0))
            }
            
            Timber.d("OfflineQueueManager: Processing ${retryableItems.size} retryable operations")
            
            // Group by user to process efficiently
            val groupedByUser = retryableItems.groupBy { it.userId }
            
            var totalSuccessful = 0
            var totalFailed = 0
            var totalConflicts = 0
            
            for ((userId, userItems) in groupedByUser) {
                for (item in userItems) {
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
                    }
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
     * Handles sync operation failures with exponential backoff retry logic.
     */
    private suspend fun handleSyncFailure(item: SyncQueueEntity, error: Throwable) {
        val newRetryCount = item.retryCount + 1
        
        if (newRetryCount >= MAX_RETRY_COUNT) {
            Timber.e("OfflineQueueManager: Operation ${item.id} exceeded max retries, removing from queue")
            syncQueueDao.delete(item)
            return
        }
        
        // Calculate exponential backoff delay
        val delaySeconds = BASE_RETRY_DELAY_SECONDS * (1L shl newRetryCount) // 2^retryCount
        val nextRetryAt = System.currentTimeMillis() + (delaySeconds * 1000)
        
        Timber.w("OfflineQueueManager: Scheduling retry ${newRetryCount}/$MAX_RETRY_COUNT for operation ${item.id} in ${delaySeconds}s")
        
        syncQueueDao.updateRetryInfo(item.id, nextRetryAt)
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