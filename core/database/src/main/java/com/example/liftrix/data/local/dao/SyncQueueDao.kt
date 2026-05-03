package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.SyncQueueEntity
import com.example.liftrix.annotations.UserScoped
import kotlinx.coroutines.flow.Flow

/**
 * DAO for managing offline sync queue operations.
 * 
 * Handles CRUD operations for sync queue items including priority-based retrieval
 * and retry management for failed sync operations.
 */
@Dao
interface SyncQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SyncQueueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SyncQueueEntity>)

    @Update
    suspend fun update(item: SyncQueueEntity)

    @Delete
    suspend fun delete(item: SyncQueueEntity)

    /**
     * Gets all pending sync operations for a user, ordered by priority and creation time.
     * Higher priority (lower number) operations are returned first.
     */
    @Query("""
        SELECT * FROM sync_queue 
        WHERE user_id = :userId 
        AND (next_retry_at IS NULL OR next_retry_at <= :currentTime)
        ORDER BY priority ASC, created_at ASC
    """)
    @UserScoped
    suspend fun getPendingItems(userId: String, currentTime: Long = System.currentTimeMillis()): List<SyncQueueEntity>

    /**
     * Gets the count of pending sync operations for a user.
     */
    @Query("SELECT COUNT(*) FROM sync_queue WHERE user_id = :userId")
    @UserScoped
    suspend fun getPendingItemsCount(userId: String): Int

    /**
     * Observes the count of pending sync operations for a user.
     */
    @Query("SELECT COUNT(*) FROM sync_queue WHERE user_id = :userId")
    @UserScoped
    fun observePendingItemsCount(userId: String): Flow<Int>

    /**
     * Gets pending operations for a specific entity type.
     */
    @Query("""
        SELECT * FROM sync_queue 
        WHERE user_id = :userId 
        AND entity_type = :entityType
        AND (next_retry_at IS NULL OR next_retry_at <= :currentTime)
        ORDER BY priority ASC, created_at ASC
    """)
    @UserScoped
    suspend fun getPendingItemsByType(
        userId: String, 
        entityType: String,
        currentTime: Long = System.currentTimeMillis()
    ): List<SyncQueueEntity>

    /**
     * Gets a specific sync operation by entity ID and type.
     */
    @Query("""
        SELECT * FROM sync_queue 
        WHERE user_id = :userId 
        AND entity_type = :entityType 
        AND entity_id = :entityId
        LIMIT 1
    """)
    @UserScoped
    suspend fun getSyncItem(userId: String, entityType: String, entityId: String): SyncQueueEntity?

    /**
     * Clears all sync operations for a user.
     */
    @Query("DELETE FROM sync_queue WHERE user_id = :userId")
    @UserScoped
    suspend fun clearQueueForUser(userId: String)

    /**
     * Clears sync operations for a specific entity type.
     */
    @Query("DELETE FROM sync_queue WHERE user_id = :userId AND entity_type = :entityType")
    @UserScoped
    suspend fun clearQueueForEntityType(userId: String, entityType: String)

    /**
     * Removes operations that have exceeded the maximum retry count.
     */
    @Query("DELETE FROM sync_queue WHERE user_id = :userId AND retry_count >= :maxRetries")
    @UserScoped
    suspend fun clearFailedOperations(userId: String, maxRetries: Int = 5)

    /**
     * Updates the retry information for a sync operation.
     */
    @Query("""
        UPDATE sync_queue 
        SET retry_count = retry_count + 1, next_retry_at = :nextRetryAt 
        WHERE id = :id AND user_id = :userId
    """)
    @UserScoped
    suspend fun updateRetryInfo(id: String, userId: String, nextRetryAt: Long)

    /**
     * Enhanced retry info update with error tracking.
     */
    @Query("""
        UPDATE sync_queue
        SET retry_count = :retryCount,
            next_retry_at = :nextRetryAt,
            last_error = :lastError
        WHERE id = :id AND user_id = :userId
    """)
    @UserScoped
    suspend fun updateRetryInfo(
        id: String,
        userId: String,
        nextRetryAt: Long,
        retryCount: Int,
        lastError: String?
    )

    /**
     * Gets operations that are ready for retry.
     */
    @Query("""
        SELECT * FROM sync_queue 
        WHERE user_id = :userId
        AND next_retry_at IS NOT NULL 
        AND next_retry_at <= :currentTime
        AND retry_count < :maxRetries
        ORDER BY priority ASC, next_retry_at ASC
    """)
    @UserScoped
    suspend fun getOperationsReadyForRetry(
        userId: String,
        currentTime: Long = System.currentTimeMillis(),
        maxRetries: Int = 5
    ): List<SyncQueueEntity>

    /**
     * 🔥 FIX: Gets ALL queue entries for legacy cleanup validation.
     * Used during app startup to identify and remove entries with serialization issues.
     */
    @Query("SELECT * FROM sync_queue WHERE user_id = :userId ORDER BY created_at ASC")
    @UserScoped
    suspend fun getAllQueueEntries(userId: String): List<SyncQueueEntity>
}
