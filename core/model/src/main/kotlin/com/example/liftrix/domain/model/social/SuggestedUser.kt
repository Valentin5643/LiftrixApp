package com.example.liftrix.domain.model.social

import java.time.LocalDateTime

/**
 * Data class representing a suggested user for follow recommendations.
 * Part of user profiles and follow system from SPEC-20250113-user-profiles-follow.
 * 
 * Contains essential profile information and suggestion metadata for
 * displaying in suggested users carousel and discovery features.
 */
data class SuggestedUser(
    val userId: String,
    val displayName: String?,
    val bio: String?,
    val profileImageUrl: String?,
    val mutualConnections: Int = 0,
    val suggestionReason: String?, // e.g., "Popular in your area", "Similar interests"
    val isFollowing: Boolean = false,
    val suggestionScore: Double = 0.0, // ML-based relevance score
    val location: String? = null,
    val workoutCount: Int = 0,
    val followersCount: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val lastActiveAt: LocalDateTime? = null
)