package com.example.liftrix.domain.model.social

/**
 * Enum representing reasons for reporting content.
 * Part of social privacy and moderation system from SPEC-20250116-social-privacy-moderation.
 */
enum class ReportReason(val displayName: String, val description: String) {
    SPAM("Spam or misleading", "Unwanted promotional content or misleading information"),
    INAPPROPRIATE_CONTENT("Inappropriate content", "Content that violates community guidelines"),
    HARASSMENT("Harassment or bullying", "Targeted harassment, bullying, or threatening behavior"),
    MISINFORMATION("False information", "Deliberately false or misleading health/fitness information"),
    COPYRIGHT("Copyright violation", "Unauthorized use of copyrighted material"),
    OTHER("Other", "Other reason not listed above")
}