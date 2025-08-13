package com.example.liftrix.domain.model.social

/**
 * Domain model representing a blocked user relationship.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 */
data class BlockedUser(
    val id: String,
    val userId: String,
    val blockedUserId: String,
    val reason: String? = null,
    val blockedAt: Long
) {
    /**
     * Returns human-readable reason for blocking or default message
     */
    fun getBlockReason(): String = reason ?: "No reason provided"

    /**
     * Checks if this block was recent (within last 24 hours)
     */
    fun isRecentBlock(): Boolean {
        val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        return blockedAt >= twentyFourHoursAgo
    }
}