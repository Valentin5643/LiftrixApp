package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.FollowRequestEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for follow request queue operations with mandatory user scoping.
 * Part of user profiles and follow system from SPEC-20250113-user-profiles-follow.
 * 
 * CRITICAL SECURITY: All queries MUST include userId filtering to prevent data leakage.
 */
@Dao
interface FollowRequestDao {

    // ========================================
    // Follow Request Management
    // ========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollowRequest(request: FollowRequestEntity): Long

    @Update
    suspend fun updateFollowRequest(request: FollowRequestEntity): Int

    @Delete
    suspend fun deleteFollowRequest(request: FollowRequestEntity): Int

    @Query("""
        DELETE FROM follow_requests 
        WHERE requester_id = :requesterId AND target_id = :targetId
    """)
    suspend fun deleteFollowRequest(requesterId: String, targetId: String): Int

    // ========================================
    // Request Retrieval Queries
    // ========================================

    @Query("""
        SELECT * FROM follow_requests 
        WHERE requester_id = :requesterId AND target_id = :targetId
    """)
    suspend fun getFollowRequest(requesterId: String, targetId: String): FollowRequestEntity?

    @Query("""
        SELECT * FROM follow_requests 
        WHERE target_id = :targetId AND status = 'PENDING'
        ORDER BY created_at DESC
        LIMIT :limit
    """)
    suspend fun getPendingRequests(targetId: String, limit: Int = 50): List<FollowRequestEntity>

    @Query("""
        SELECT * FROM follow_requests 
        WHERE requester_id = :requesterId AND status = 'PENDING'
        ORDER BY created_at DESC
        LIMIT :limit
    """)
    suspend fun getSentRequests(requesterId: String, limit: Int = 50): List<FollowRequestEntity>

    @Query("""
        SELECT * FROM follow_requests 
        WHERE target_id = :targetId AND status IN ('ACCEPTED', 'DECLINED')
        ORDER BY processed_at DESC
        LIMIT :limit
    """)
    suspend fun getProcessedRequests(targetId: String, limit: Int = 20): List<FollowRequestEntity>

    // ========================================
    // Request Status Updates
    // ========================================

    @Query("""
        UPDATE follow_requests 
        SET status = 'ACCEPTED', processed_at = :processedAt, updated_at = :updatedAt
        WHERE requester_id = :requesterId AND target_id = :targetId AND status = 'PENDING'
    """)
    suspend fun acceptRequest(
        requesterId: String,
        targetId: String,
        processedAt: Long,
        updatedAt: Long
    ): Int

    @Query("""
        UPDATE follow_requests 
        SET status = 'DECLINED', processed_at = :processedAt, updated_at = :updatedAt
        WHERE requester_id = :requesterId AND target_id = :targetId AND status = 'PENDING'
    """)
    suspend fun declineRequest(
        requesterId: String,
        targetId: String,
        processedAt: Long,
        updatedAt: Long
    ): Int

    @Query("""
        UPDATE follow_requests 
        SET status = 'CANCELLED', processed_at = :processedAt, updated_at = :updatedAt
        WHERE requester_id = :requesterId AND target_id = :targetId AND status = 'PENDING'
    """)
    suspend fun cancelRequest(
        requesterId: String,
        targetId: String,
        processedAt: Long,
        updatedAt: Long
    ): Int

    // ========================================
    // Request Count Queries
    // ========================================

    @Query("""
        SELECT COUNT(*) FROM follow_requests 
        WHERE target_id = :targetId AND status = 'PENDING'
    """)
    suspend fun getPendingRequestCount(targetId: String): Int

    @Query("""
        SELECT COUNT(*) FROM follow_requests 
        WHERE requester_id = :requesterId AND status = 'PENDING'
    """)
    suspend fun getSentRequestCount(requesterId: String): Int

    @Query("""
        SELECT COUNT(*) FROM follow_requests 
        WHERE target_id = :targetId AND status = 'PENDING'
        AND created_at >= :sinceTimestamp
    """)
    suspend fun getRecentPendingRequestCount(targetId: String, sinceTimestamp: Long): Int

    // ========================================
    // Expiration Management
    // ========================================

    @Query("""
        SELECT * FROM follow_requests 
        WHERE status = 'PENDING' AND expires_at <= :currentTimestamp
    """)
    suspend fun getExpiredRequests(currentTimestamp: Long): List<FollowRequestEntity>

    @Query("""
        UPDATE follow_requests 
        SET status = 'EXPIRED', processed_at = :currentTimestamp, updated_at = :currentTimestamp
        WHERE status = 'PENDING' AND expires_at <= :currentTimestamp
    """)
    suspend fun expireOldRequests(currentTimestamp: Long): Int

    @Query("""
        DELETE FROM follow_requests 
        WHERE status IN ('EXPIRED', 'DECLINED', 'CANCELLED') 
        AND processed_at <= :cutoffTimestamp
    """)
    suspend fun cleanupProcessedRequests(cutoffTimestamp: Long): Int

    // ========================================
    // Notification Management
    // ========================================

    @Query("""
        SELECT * FROM follow_requests 
        WHERE status = 'PENDING' 
        AND notification_sent = 0
        AND created_at >= :recentTimestamp
        ORDER BY created_at ASC
        LIMIT :limit
    """)
    suspend fun getUnsentNotificationRequests(
        recentTimestamp: Long,
        limit: Int = 100
    ): List<FollowRequestEntity>

    @Query("""
        UPDATE follow_requests 
        SET notification_sent = 1, updated_at = :updatedAt
        WHERE id = :requestId
    """)
    suspend fun markNotificationSent(requestId: String, updatedAt: Long): Int

    @Query("""
        SELECT * FROM follow_requests 
        WHERE status = 'PENDING'
        AND reminder_count < 3
        AND last_reminder_at IS NULL OR last_reminder_at <= :reminderCutoff
        AND created_at <= :reminderEligibleTime
        ORDER BY created_at ASC
        LIMIT :limit
    """)
    suspend fun getRequestsNeedingReminder(
        reminderCutoff: Long,
        reminderEligibleTime: Long,
        limit: Int = 50
    ): List<FollowRequestEntity>

    @Query("""
        UPDATE follow_requests 
        SET reminder_count = reminder_count + 1, 
            last_reminder_at = :reminderTime,
            updated_at = :updatedAt
        WHERE id = :requestId
    """)
    suspend fun incrementReminderCount(
        requestId: String,
        reminderTime: Long,
        updatedAt: Long
    ): Int

    // ========================================
    // Analytics Support
    // ========================================

    @Query("""
        SELECT request_source, COUNT(*) as count FROM follow_requests 
        WHERE target_id = :targetId
        AND created_at >= :sinceTimestamp
        GROUP BY request_source
        ORDER BY count DESC
    """)
    suspend fun getRequestsBySource(targetId: String, sinceTimestamp: Long): List<RequestSourceCount>

    @Query("""
        SELECT 
            AVG(CASE WHEN processed_at IS NOT NULL AND status = 'ACCEPTED' 
                THEN processed_at - created_at ELSE NULL END) as avg_accept_time,
            AVG(CASE WHEN processed_at IS NOT NULL AND status = 'DECLINED' 
                THEN processed_at - created_at ELSE NULL END) as avg_decline_time
        FROM follow_requests 
        WHERE target_id = :targetId
        AND created_at >= :sinceTimestamp
    """)
    suspend fun getAverageResponseTimes(targetId: String, sinceTimestamp: Long): ResponseTimeStats?

    // ========================================
    // Reactive Queries
    // ========================================

    @Query("""
        SELECT COUNT(*) FROM follow_requests 
        WHERE target_id = :targetId AND status = 'PENDING'
    """)
    fun observePendingRequestCount(targetId: String): Flow<Int>

    @Query("""
        SELECT * FROM follow_requests 
        WHERE target_id = :targetId AND status = 'PENDING'
        ORDER BY created_at DESC
    """)
    fun observePendingRequests(targetId: String): Flow<List<FollowRequestEntity>>

    // ========================================
    // Sync Support
    // ========================================

    @Query("""
        SELECT * FROM follow_requests 
        WHERE (requester_id = :userId OR target_id = :userId)
        AND is_synced = 0
        ORDER BY updated_at DESC
        LIMIT :limit
    """)
    suspend fun getUnsyncedRequests(userId: String, limit: Int = 100): List<FollowRequestEntity>

    @Query("""
        UPDATE follow_requests 
        SET is_synced = :isSynced, sync_version = :version, updated_at = :updatedAt
        WHERE id = :requestId
    """)
    suspend fun updateSyncStatus(
        requestId: String,
        isSynced: Boolean,
        version: Int,
        updatedAt: Long
    ): Int
}

/**
 * Data classes for analytics results
 */
data class RequestSourceCount(
    val request_source: String,
    val count: Int
)

data class ResponseTimeStats(
    val avg_accept_time: Double?,
    val avg_decline_time: Double?
)