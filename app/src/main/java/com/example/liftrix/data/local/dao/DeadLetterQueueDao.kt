package com.example.liftrix.data.local.dao

import androidx.room.*
import com.example.liftrix.data.local.entity.DeadLetterQueueEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for managing dead letter queue operations.
 *
 * Provides access to permanently failed sync operations for manual review and debugging.
 */
@Dao
interface DeadLetterQueueDao {

    /**
     * Inserts a failed operation into the dead letter queue.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DeadLetterQueueEntity)

    /**
     * Gets all dead letter items for a user.
     */
    @Query("""
        SELECT * FROM dead_letter_queue
        WHERE user_id = :userId
        ORDER BY failed_at DESC
    """)
    suspend fun getDeadLetterItems(userId: String): List<DeadLetterQueueEntity>

    /**
     * Gets unreviewed dead letter items for a user.
     */
    @Query("""
        SELECT * FROM dead_letter_queue
        WHERE user_id = :userId AND reviewed = 0
        ORDER BY failed_at DESC
    """)
    suspend fun getUnreviewedItems(userId: String): List<DeadLetterQueueEntity>

    /**
     * Gets dead letter items by error category.
     */
    @Query("""
        SELECT * FROM dead_letter_queue
        WHERE user_id = :userId AND error_category = :errorCategory
        ORDER BY failed_at DESC
    """)
    suspend fun getItemsByErrorCategory(userId: String, errorCategory: String): List<DeadLetterQueueEntity>

    /**
     * Gets items marked for retry after fix.
     */
    @Query("""
        SELECT * FROM dead_letter_queue
        WHERE user_id = :userId AND retry_after_fix = 1
        ORDER BY failed_at DESC
    """)
    suspend fun getItemsToRetry(userId: String): List<DeadLetterQueueEntity>

    /**
     * Marks an item as reviewed.
     */
    @Query("""
        UPDATE dead_letter_queue
        SET reviewed = 1, reviewed_at = :reviewedAt
        WHERE id = :itemId
    """)
    suspend fun markAsReviewed(itemId: String, reviewedAt: Long = System.currentTimeMillis())

    /**
     * Marks an item for retry after fix.
     */
    @Query("""
        UPDATE dead_letter_queue
        SET retry_after_fix = 1
        WHERE id = :itemId
    """)
    suspend fun markForRetry(itemId: String)

    /**
     * Removes an item from dead letter queue.
     */
    @Query("DELETE FROM dead_letter_queue WHERE id = :itemId")
    suspend fun delete(itemId: String)

    /**
     * Gets count of dead letter items for a user.
     */
    @Query("SELECT COUNT(*) FROM dead_letter_queue WHERE user_id = :userId")
    suspend fun getDeadLetterCount(userId: String): Int

    /**
     * Gets count of unreviewed items for a user.
     */
    @Query("SELECT COUNT(*) FROM dead_letter_queue WHERE user_id = :userId AND reviewed = 0")
    suspend fun getUnreviewedCount(userId: String): Int

    /**
     * Observes dead letter queue changes for a user.
     */
    @Query("""
        SELECT * FROM dead_letter_queue
        WHERE user_id = :userId
        ORDER BY failed_at DESC
    """)
    fun observeDeadLetterItems(userId: String): Flow<List<DeadLetterQueueEntity>>

    /**
     * Clears old reviewed items (older than specified days).
     */
    @Query("""
        DELETE FROM dead_letter_queue
        WHERE user_id = :userId
        AND reviewed = 1
        AND failed_at < :cutoffTime
    """)
    suspend fun clearOldReviewedItems(userId: String, cutoffTime: Long)

    /**
     * Gets dead letter statistics for analytics.
     */
    @Query("""
        SELECT
            error_category,
            COUNT(*) as count,
            AVG(retry_count) as avg_retry_count
        FROM dead_letter_queue
        WHERE user_id = :userId
        GROUP BY error_category
    """)
    suspend fun getDeadLetterStatistics(userId: String): List<DeadLetterStatistics>
}

/**
 * Data class for dead letter queue statistics.
 */
data class DeadLetterStatistics(
    val error_category: String,
    val count: Int,
    val avg_retry_count: Double
)