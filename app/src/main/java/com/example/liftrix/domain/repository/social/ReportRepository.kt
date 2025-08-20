package com.example.liftrix.domain.repository.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.usecase.social.ReportReason

/**
 * Repository interface for managing user reports
 */
interface ReportRepository {
    
    /**
     * Submit a report against a user
     * 
     * @param reporterId The user submitting the report
     * @param targetUserId The user being reported
     * @param reason The reason for the report
     * @param description Additional details
     */
    suspend fun submitReport(
        reporterId: String,
        targetUserId: String,
        reason: ReportReason,
        description: String?
    ): LiftrixResult<Unit>
    
    /**
     * Check if a user has already reported another user
     * 
     * @param reporterId The reporter
     * @param targetUserId The reported user
     * @return True if a report exists, false otherwise
     */
    suspend fun hasExistingReport(
        reporterId: String,
        targetUserId: String
    ): Boolean
    
    /**
     * Get report statistics for moderation
     * 
     * @param userId The user to get report stats for
     * @return Number of reports against the user
     */
    suspend fun getReportCount(userId: String): Int
}