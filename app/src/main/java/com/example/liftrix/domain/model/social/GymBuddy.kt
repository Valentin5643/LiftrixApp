package com.example.liftrix.domain.model.social

/**
 * Domain model representing a gym buddy relationship (inner circle connection).
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 */
data class GymBuddy(
    val id: String,
    val userId: String,
    val buddyId: String,
    
    // Buddy metadata
    val buddyNickname: String? = null,
    val createdAt: Long,
    val lastPrNotificationSent: Long? = null,
    val notificationCooldownHours: Int = 24,
    
    // QR code pairing
    val pairedViaQr: Boolean = true,
    val pairingLocation: String? = null
) {
    /**
     * Checks if this buddy is eligible for PR notifications based on cooldown
     */
    fun isEligibleForPrNotification(): Boolean {
        if (lastPrNotificationSent == null) return true
        
        val cooldownMillis = notificationCooldownHours * 60 * 60 * 1000L
        val timeSinceLastNotification = System.currentTimeMillis() - lastPrNotificationSent
        
        return timeSinceLastNotification >= cooldownMillis
    }

    /**
     * Returns the buddy's display name (nickname if set, otherwise uses buddyId)
     */
    fun getBuddyDisplayName(): String = buddyNickname ?: "Gym Buddy"
}