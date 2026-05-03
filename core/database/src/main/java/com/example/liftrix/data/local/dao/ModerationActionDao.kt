package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.liftrix.data.local.entity.ModerationActionEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for managing moderation action audit logs.
 *
 * Provides query access for admin dashboard and compliance audits.
 */
@Dao
interface ModerationActionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(action: ModerationActionEntity)

    /**
     * Get moderation actions by admin user.
     *
     * @param adminUserId The admin user ID
     * @param limit Maximum number of actions to return
     */
    @Query("""
        SELECT * FROM moderation_actions
        WHERE admin_user_id = :adminUserId
        ORDER BY created_at DESC
        LIMIT :limit
    """)
    suspend fun getActionsByAdmin(
        adminUserId: String,
        limit: Int = 100
    ): List<ModerationActionEntity>

    /**
     * Get moderation actions for a specific target.
     *
     * @param targetType The target type (POST, COMMENT, USER)
     * @param targetId The target ID
     */
    @Query("""
        SELECT * FROM moderation_actions
        WHERE target_type = :targetType
          AND target_id = :targetId
        ORDER BY created_at DESC
    """)
    suspend fun getActionsForTarget(
        targetType: String,
        targetId: String
    ): List<ModerationActionEntity>

    /**
     * Get actions related to a specific report.
     *
     * @param reportId The content report ID
     */
    @Query("""
        SELECT * FROM moderation_actions
        WHERE report_id = :reportId
        ORDER BY created_at DESC
    """)
    suspend fun getActionsForReport(reportId: String): List<ModerationActionEntity>

    /**
     * Get recent moderation actions (for admin dashboard).
     *
     * @param limit Maximum number of actions to return
     */
    @Query("""
        SELECT * FROM moderation_actions
        ORDER BY created_at DESC
        LIMIT :limit
    """)
    suspend fun getRecentActions(limit: Int = 50): List<ModerationActionEntity>

    /**
     * Observe recent moderation actions in real-time.
     *
     * @param limit Maximum number of actions to observe
     */
    @Query("""
        SELECT * FROM moderation_actions
        ORDER BY created_at DESC
        LIMIT :limit
    """)
    fun observeRecentActions(limit: Int = 50): Flow<List<ModerationActionEntity>>

    /**
     * Get action count by admin (for performance metrics).
     *
     * @param adminUserId The admin user ID
     */
    @Query("""
        SELECT COUNT(*) FROM moderation_actions
        WHERE admin_user_id = :adminUserId
    """)
    suspend fun getActionCountByAdmin(adminUserId: String): Int

    /**
     * Get action count by type and time range.
     *
     * @param actionType The action type filter
     * @param startTime Start of time range
     * @param endTime End of time range
     */
    @Query("""
        SELECT COUNT(*) FROM moderation_actions
        WHERE action_type = :actionType
          AND created_at >= :startTime
          AND created_at <= :endTime
    """)
    suspend fun getActionCountByType(
        actionType: String,
        startTime: Long,
        endTime: Long
    ): Int

    /**
     * Delete moderation actions older than retention period (cleanup).
     *
     * @param cutoffTime Timestamp before which actions should be deleted
     */
    @Query("""
        DELETE FROM moderation_actions
        WHERE created_at < :cutoffTime
    """)
    suspend fun deleteOldActions(cutoffTime: Long)
}
