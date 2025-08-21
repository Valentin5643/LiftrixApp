package com.example.liftrix.data.sync

import com.example.liftrix.data.local.dao.SyncQueueDao
import com.example.liftrix.data.local.entity.SyncQueueEntity
import com.example.liftrix.data.remote.FirebaseDataSource
import com.example.liftrix.data.remote.ProcessResult
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.SyncResult
import com.example.liftrix.data.model.SyncPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
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
                    
                    when (result) {
                        is ProcessResult.Success -> {
                            syncQueueDao.delete(item)
                            successful++
                            Timber.d("OfflineQueueManager: Successfully processed operation ${item.id}")
                        }
                        is ProcessResult.Conflict -> {
                            syncQueueDao.delete(item) // Remove conflict item as it's been resolved
                            conflicts++
                            Timber.d("OfflineQueueManager: Resolved conflict for operation ${item.id}")
                        }
                        is ProcessResult.Failure -> {
                            handleSyncFailure(item, result.error)
                            failed++
                            Timber.w("OfflineQueueManager: Failed to process operation ${item.id}: ${result.error}")
                        }
                        is ProcessResult.Data -> {
                            // For fetch operations, just mark as successful
                            syncQueueDao.delete(item)
                            successful++
                            Timber.d("OfflineQueueManager: Successfully processed fetch operation ${item.id}")
                        }
                        is ProcessResult.DataList -> {
                            // For fetchAll operations, just mark as successful
                            syncQueueDao.delete(item)
                            successful++
                            Timber.d("OfflineQueueManager: Successfully processed fetchAll operation ${item.id}")
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
            // Deserialize the type-safe payload
            val payload = json.decodeFromString<SyncPayload>(item.data)
            
            // Extract the actual data based on payload type
            val dataForFirebase = when (payload) {
                is SyncPayload.WorkoutPayload -> {
                    // Convert WorkoutSyncDto to Firebase-compatible format
                    val workout = payload.workout
                    mapOf(
                        "id" to workout.id,
                        "userId" to workout.userId,
                        "name" to workout.name,
                        "date" to workout.date,
                        "status" to workout.status,
                        "startTime" to workout.startTime,
                        "endTime" to workout.endTime,
                        "exercises" to workout.exercises.map { exercise ->
                            mapOf(
                                "id" to exercise.id,
                                "name" to exercise.name,
                                "muscleGroup" to exercise.muscleGroup,
                                "orderIndex" to exercise.orderIndex,
                                "notes" to exercise.notes,
                                "sets" to exercise.sets.map { set ->
                                    mapOf(
                                        "setNumber" to set.setNumber,
                                        "targetReps" to set.targetReps,
                                        "actualReps" to set.actualReps,
                                        "targetWeight" to set.targetWeight,
                                        "actualWeight" to set.actualWeight,
                                        "completed" to set.completed,
                                        "rpe" to set.rpe
                                    )
                                }
                            )
                        },
                        "notes" to workout.notes,
                        "templateId" to workout.templateId,
                        "createdAt" to workout.createdAt,
                        "updatedAt" to workout.updatedAt,
                        "syncVersion" to workout.syncVersion,
                        "isSynced" to workout.isSynced
                    )
                }
                is SyncPayload.ProfilePayload -> {
                    mapOf(
                        "userId" to payload.userId,
                        "displayName" to payload.displayName,
                        "email" to payload.email,
                        "profileImageUrl" to payload.profileImageUrl,
                        "goals" to payload.goals,
                        "preferences" to payload.preferences,
                        "syncVersion" to payload.syncVersion,
                        "lastModified" to payload.lastModified
                    )
                }
                is SyncPayload.TemplatePayload -> {
                    mapOf(
                        "templateId" to payload.templateId,
                        "userId" to payload.userId,
                        "name" to payload.name,
                        "description" to payload.description,
                        "exercises" to payload.exercises,
                        "isPublic" to payload.isPublic,
                        "syncVersion" to payload.syncVersion,
                        "lastModified" to payload.lastModified
                    )
                }
                is SyncPayload.AchievementPayload -> {
                    mapOf(
                        "achievementId" to payload.achievementId,
                        "userId" to payload.userId,
                        "type" to payload.type,
                        "title" to payload.title,
                        "description" to payload.description,
                        "unlockedAt" to payload.unlockedAt,
                        "syncVersion" to payload.syncVersion
                    )
                }
                is SyncPayload.SocialProfilePayload -> {
                    mapOf(
                        "userId" to payload.userId,
                        "username" to payload.username,
                        "displayName" to payload.displayName,
                        "bio" to payload.bio,
                        "isPrivate" to payload.isPrivate,
                        "followerCount" to payload.followerCount,
                        "followingCount" to payload.followingCount,
                        "syncVersion" to payload.syncVersion,
                        "lastModified" to payload.lastModified
                    )
                }
            }
            
            // Convert map to JSON string for FirebaseDataSource
            val jsonData = json.encodeToString(dataForFirebase)
            
            when (item.operation) {
                "CREATE", "UPSERT" -> firebaseDataSource.create(item.userId, item.entityType, item.entityId, jsonData)
                "UPDATE" -> firebaseDataSource.update(item.userId, item.entityType, item.entityId, jsonData)
                "DELETE" -> firebaseDataSource.delete(item.userId, item.entityType, item.entityId)
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
     * Determines the priority level for different entity types.
     */
    private fun getPriority(entityType: String): Int = when (entityType) {
        "WORKOUT" -> HIGH_PRIORITY      // Active workout data has highest priority
        "PROFILE" -> MEDIUM_PRIORITY    // Profile changes are medium priority
        "TEMPLATE", "ACHIEVEMENT" -> LOW_PRIORITY  // Templates and achievements are lower priority
        else -> LOW_PRIORITY            // Default to low priority for unknown types
    }
    
}