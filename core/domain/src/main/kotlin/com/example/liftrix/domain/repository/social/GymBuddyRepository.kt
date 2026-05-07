package com.example.liftrix.domain.repository.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.social.GymBuddy
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for gym buddy management with enhanced QR code pairing
 * Supports mutual connections, limit enforcement, and PR notification management
 */
interface GymBuddyRepository {
    
    /**
     * Gets all gym buddies for a user
     */
    suspend fun getGymBuddies(userId: String): LiftrixResult<List<GymBuddy>>
    
    /**
     * Observes gym buddies for a user
     */
    fun observeGymBuddies(userId: String): Flow<List<GymBuddy>>
    
    /**
     * Gets a specific gym buddy relationship
     */
    suspend fun getGymBuddy(userId: String, buddyId: String): LiftrixResult<GymBuddy?>
    
    /**
     * Checks if two users are mutual gym buddies
     */
    suspend fun areMutualGymBuddies(userId: String, buddyId: String): LiftrixResult<Boolean>
    
    /**
     * Gets the count of gym buddies for a user
     */
    suspend fun getGymBuddyCount(userId: String): LiftrixResult<Int>
    
    /**
     * Creates a mutual gym buddy connection between two users
     * Enforces the 5 buddy limit and validates both users can add each other
     */
    suspend fun createMutualConnection(
        userId1: String,
        userId2: String,
        viaQr: Boolean = false,
        location: String? = null
    ): LiftrixResult<Pair<GymBuddy, GymBuddy>>
    
    /**
     * Removes a gym buddy connection (one-way removal)
     */
    suspend fun removeGymBuddy(userId: String, buddyId: String): LiftrixResult<Unit>
    
    /**
     * Removes a mutual gym buddy connection (both ways)
     */
    suspend fun removeMutualConnection(userId1: String, userId2: String): LiftrixResult<Unit>
    
    /**
     * Updates buddy nickname
     */
    suspend fun updateBuddyNickname(
        userId: String,
        buddyId: String,
        nickname: String?
    ): LiftrixResult<Unit>
    
    /**
     * Gets buddies eligible for PR notifications (not in cooldown)
     */
    suspend fun getBuddiesEligibleForPrNotification(userId: String): LiftrixResult<List<GymBuddy>>
    
    /**
     * Updates the last PR notification sent timestamp
     */
    suspend fun updatePrNotificationSent(
        userId: String,
        buddyId: String,
        timestamp: Long
    ): LiftrixResult<Unit>
    
    /**
     * Updates notification cooldown period
     */
    suspend fun updateNotificationCooldown(
        userId: String,
        buddyId: String,
        cooldownHours: Int
    ): LiftrixResult<Unit>
    
    /**
     * Gets buddies paired via QR code
     */
    suspend fun getQrPairedBuddies(userId: String): LiftrixResult<List<GymBuddy>>
    
    /**
     * Validates if a user can add more gym buddies (under limit of 5)
     */
    suspend fun canAddMoreBuddies(userId: String): LiftrixResult<Boolean>
    
    /**
     * Gets gym buddy statistics for analytics
     */
    suspend fun getGymBuddyStats(userId: String): LiftrixResult<GymBuddyStats>
}

/**
 * Statistics for gym buddy analytics
 */
data class GymBuddyStats(
    val totalBuddies: Int,
    val qrPairedBuddies: Int,
    val buddiesWithNotifications: Int,
    val averageCooldownHours: Double
)