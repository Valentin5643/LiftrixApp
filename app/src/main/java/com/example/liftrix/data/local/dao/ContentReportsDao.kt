package com.example.liftrix.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.ContentReportEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for content reports.
 * Part of social privacy and moderation system from SPEC-20250116-social-privacy-moderation.
 * 
 * Provides database operations for content reporting and moderation functionality.
 * All queries include proper user scoping to prevent data leakage.
 */
@Dao
interface ContentReportsDao {

    /**
     * Insert a new content report
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: ContentReportEntity)

    /**
     * Update an existing content report
     */
    @Update
    suspend fun update(report: ContentReportEntity)

    /**
     * Check if a user has already reported specific content
     */
    @Query("""
        SELECT COUNT(*) > 0 
        FROM content_reports 
        WHERE reporter_user_id = :reporterUserId 
        AND content_id = :contentId
    """)
    suspend fun hasUserReported(reporterUserId: String, contentId: String): Boolean

    /**
     * Get the total number of reports for specific content
     */
    @Query("""
        SELECT COUNT(*) 
        FROM content_reports 
        WHERE content_id = :contentId 
        AND status IN ('PENDING', 'REVIEWED')
    """)
    suspend fun getReportCount(contentId: String): Int

    /**
     * Get all reports for a specific content item
     */
    @Query("""
        SELECT * 
        FROM content_reports 
        WHERE content_id = :contentId 
        ORDER BY reported_at DESC
    """)
    suspend fun getReportsForContent(contentId: String): List<ContentReportEntity>

    /**
     * Get all reports by a specific user
     */
    @Query("""
        SELECT * 
        FROM content_reports 
        WHERE reporter_user_id = :userId 
        ORDER BY reported_at DESC
    """)
    suspend fun getReportsByUser(userId: String): List<ContentReportEntity>

    /**
     * Get pending reports (for admin review)
     */
    @Query("""
        SELECT * 
        FROM content_reports 
        WHERE status = 'PENDING' 
        ORDER BY reported_at ASC
    """)
    suspend fun getPendingReports(): List<ContentReportEntity>

    /**
     * Get reports that need syncing to Firebase
     */
    @Query("""
        SELECT * 
        FROM content_reports 
        WHERE is_synced = 0 
        ORDER BY reported_at ASC
    """)
    suspend fun getUnsyncedReports(): List<ContentReportEntity>

    /**
     * Mark reports as synced
     */
    @Query("""
        UPDATE content_reports 
        SET is_synced = 1 
        WHERE id IN (:reportIds)
    """)
    suspend fun markAsSynced(reportIds: List<String>)

    /**
     * Get reports by content type and status
     */
    @Query("""
        SELECT * 
        FROM content_reports 
        WHERE content_type = :contentType 
        AND status = :status 
        ORDER BY reported_at DESC
    """)
    suspend fun getReportsByTypeAndStatus(
        contentType: String, 
        status: String
    ): List<ContentReportEntity>

    /**
     * Update report status
     */
    @Query("""
        UPDATE content_reports 
        SET status = :status,
            reviewed_by = :reviewedBy,
            reviewed_at = :reviewedAt,
            action_taken = :actionTaken
        WHERE id = :reportId
    """)
    suspend fun updateReportStatus(
        reportId: String,
        status: String,
        reviewedBy: String?,
        reviewedAt: Long?,
        actionTaken: String?
    )

    /**
     * Delete old processed reports (cleanup)
     */
    @Query("""
        DELETE FROM content_reports 
        WHERE status IN ('DISMISSED', 'ACTIONED') 
        AND reviewed_at < :cutoffTime
    """)
    suspend fun deleteOldReports(cutoffTime: Long)

    /**
     * Get reports that have reached auto-hide threshold
     */
    @Query("""
        SELECT content_id, content_type, COUNT(*) as report_count
        FROM content_reports 
        WHERE status = 'PENDING'
        GROUP BY content_id, content_type
        HAVING COUNT(*) >= :threshold
    """)
    suspend fun getContentReachingThreshold(threshold: Int = ContentReportEntity.AUTO_HIDE_THRESHOLD): List<ContentThresholdData>

    /**
     * Data class for content that has reached reporting threshold
     */
    data class ContentThresholdData(
        @ColumnInfo(name = "content_id") val contentId: String,
        @ColumnInfo(name = "content_type") val contentType: String,
        @ColumnInfo(name = "report_count") val reportCount: Int
    )
}