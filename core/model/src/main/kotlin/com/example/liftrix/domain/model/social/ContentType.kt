package com.example.liftrix.domain.model.social

/**
 * Enum representing types of content that can be reported.
 * Part of social privacy and moderation system from SPEC-20250116-social-privacy-moderation.
 */
enum class ContentType(val displayName: String) {
    POST("Post"),
    COMMENT("Comment"),
    PROFILE("Profile")
}