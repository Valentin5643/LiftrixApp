package com.example.liftrix.domain.model.social

import java.time.LocalDateTime

/**
 * Domain model representing a follow relationship between users.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 */
data class FollowRelationship(
    val id: String,
    val followerId: String,
    val followingId: String,
    val status: FollowStatus,
    
    // Relationship metadata
    val createdAt: Long,
    val acceptedAt: Long? = null,
    val blockedAt: Long? = null,
    
    // Display properties for UI (populated by repository)
    val userId: String,
    val displayName: String?,
    val profileImageUrl: String?,
    val bio: String?,
    val location: String? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.NONE
) {
    /**
     * Checks if this relationship is currently active (following)
     */
    fun isActive(): Boolean = status == FollowStatus.FOLLOWING

    /**
     * Checks if this relationship is pending approval
     */
    fun isPending(): Boolean = status == FollowStatus.PENDING_SENT || status == FollowStatus.PENDING_RECEIVED

    /**
     * Checks if this relationship is blocked
     */
    fun isBlocked(): Boolean = status == FollowStatus.BLOCKED
}

/**
 * Display model for followers/following lists that includes profile data
 */
data class FollowerDisplayItem(
    val userId: String,
    val displayName: String?,
    val profileImageUrl: String?,
    val bio: String?,
    val mutualConnections: Int,
    val memberSince: LocalDateTime,
    val connectionStatus: ConnectionStatus,
    val followStatus: FollowStatus,
    val location: String? = null
)

// FollowStatus enum is defined in PublicUserProfile.kt