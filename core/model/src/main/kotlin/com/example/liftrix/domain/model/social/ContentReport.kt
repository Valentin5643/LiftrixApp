package com.example.liftrix.domain.model.social

/**
 * Domain model representing a content report.
 * Part of social privacy and moderation system from SPEC-20250116-social-privacy-moderation.
 * 
 * This model contains information about reported content, including the reason
 * for reporting and current status of the report.
 */
data class ContentReport(
    val id: String,
    val reporterUserId: String,
    val contentType: ContentType,
    val contentId: String,
    val reason: ReportReason,
    val description: String? = null,
    val reportedAt: Long,
    val status: String
) {
    /**
     * Checks if this report is still pending review
     */
    fun isPending(): Boolean = status == "PENDING"

    /**
     * Checks if this report has been reviewed
     */
    fun isReviewed(): Boolean = status in listOf("REVIEWED", "ACTIONED", "DISMISSED")

    /**
     * Checks if action was taken based on this report
     */
    fun hasActionTaken(): Boolean = status == "ACTIONED"

    /**
     * Gets a formatted description of the report
     */
    fun getFormattedDescription(): String {
        val baseDescription = "${reason.displayName} - ${contentType.displayName}"
        return if (description.isNullOrBlank()) {
            baseDescription
        } else {
            "$baseDescription: $description"
        }
    }
}