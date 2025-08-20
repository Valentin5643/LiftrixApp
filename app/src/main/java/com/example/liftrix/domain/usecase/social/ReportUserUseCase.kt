package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.social.ReportRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for reporting users or content
 * 
 * Features:
 * - Report inappropriate users/content
 * - Track report reasons and evidence
 * - Prevent duplicate reports
 * - Support moderation workflows
 */
class ReportUserUseCase @Inject constructor(
    private val reportRepository: ReportRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {
    
    /**
     * Report a user for inappropriate behavior
     * 
     * @param targetUserId The user being reported
     * @param reason The reason for the report
     * @param description Additional details about the report
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(
        targetUserId: String,
        reason: ReportReason,
        description: String? = null
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "REPORT_USER_FAILED",
                errorMessage = "Failed to submit report",
                analyticsContext = mapOf(
                    "target_user_id" to targetUserId,
                    "reason" to reason.name,
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        // Get current user ID
        val currentUserId = getCurrentUserIdUseCase()
            ?: throw IllegalStateException("User not authenticated")
        
        // Validate not reporting self
        if (currentUserId == targetUserId) {
            throw IllegalArgumentException("Cannot report yourself")
        }
        
        // Check for existing report to prevent duplicates
        val hasExistingReport = reportRepository.hasExistingReport(
            reporterId = currentUserId,
            targetUserId = targetUserId
        )
        
        if (hasExistingReport) {
            Timber.w("User already reported: $targetUserId by $currentUserId")
            return@liftrixCatching Unit
        }
        
        // Submit the report
        reportRepository.submitReport(
            reporterId = currentUserId,
            targetUserId = targetUserId,
            reason = reason,
            description = description
        )
        
        Timber.d("User reported: $targetUserId by $currentUserId for reason: ${reason.name}")
        
        Unit
    }
}

/**
 * Reasons for reporting a user
 */
enum class ReportReason {
    INAPPROPRIATE_CONTENT,
    HARASSMENT,
    SPAM,
    FAKE_PROFILE,
    VIOLENCE,
    HATE_SPEECH,
    OTHER
}