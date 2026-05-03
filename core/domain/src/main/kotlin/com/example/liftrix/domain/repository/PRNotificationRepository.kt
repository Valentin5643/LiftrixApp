package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for PR (Personal Record) notification operations
 * 
 * Handles the persistence and retrieval of PR celebration reactions,
 * notification preferences, and gym buddy PR sharing functionality.
 * 
 * Business Rules:
 * - Users can react to gym buddy PRs with celebration emojis
 * - PR notifications respect user privacy settings and mute preferences
 * - Reactions are tracked for analytics and social engagement
 * - Daily cooldown prevents spam notifications between gym buddies
 * - All operations must be scoped to authenticated users
 */
interface PRNotificationRepository {
    
    /**
     * Saves a user's reaction to a gym buddy's PR notification
     * 
     * @param userId The ID of the user reacting
     * @param buddyUserId The ID of the gym buddy who achieved the PR
     * @param prId The unique identifier for the PR achievement
     * @param reactionType The type of reaction (e.g., "fire", "muscle", "clap")
     * @return LiftrixResult indicating success or failure
     */
    suspend fun saveReaction(
        userId: String,
        buddyUserId: String,
        prId: String,
        reactionType: String
    ): LiftrixResult<Unit>
    
    /**
     * Retrieves reactions for a specific PR
     * 
     * @param prId The unique identifier for the PR achievement
     * @return Flow of reactions for the PR
     */
    fun getReactionsForPR(prId: String): Flow<List<PRReaction>>
    
    /**
     * Gets the reaction count for a specific PR grouped by reaction type
     * 
     * @param prId The unique identifier for the PR achievement
     * @return LiftrixResult containing reaction counts by type
     */
    suspend fun getReactionCounts(prId: String): LiftrixResult<Map<String, Int>>
    
    /**
     * Checks if a user has already reacted to a specific PR
     * 
     * @param userId The ID of the user
     * @param prId The unique identifier for the PR achievement
     * @return LiftrixResult containing true if user has reacted, false otherwise
     */
    suspend fun hasUserReacted(userId: String, prId: String): LiftrixResult<Boolean>
    
    /**
     * Removes a user's reaction from a PR
     * 
     * @param userId The ID of the user removing their reaction
     * @param prId The unique identifier for the PR achievement
     * @return LiftrixResult indicating success or failure
     */
    suspend fun removeReaction(userId: String, prId: String): LiftrixResult<Unit>
    
    /**
     * Gets all PRs that a user has reacted to within a time period
     * 
     * @param userId The ID of the user
     * @param daysSince Number of days to look back (default: 30)
     * @return Flow of PRs the user has reacted to
     */
    fun getUserReactions(userId: String, daysSince: Int = 30): Flow<List<UserReaction>>
    
    /**
     * Records that a PR notification has been sent to prevent duplicate sends
     * 
     * @param fromUserId The ID of the user who achieved the PR
     * @param toUserId The ID of the gym buddy receiving the notification
     * @param prId The unique identifier for the PR achievement
     * @return LiftrixResult indicating success or failure
     */
    suspend fun markNotificationSent(
        fromUserId: String,
        toUserId: String,
        prId: String
    ): LiftrixResult<Unit>
    
    /**
     * Checks if a PR notification has already been sent to prevent duplicates
     * 
     * @param fromUserId The ID of the user who achieved the PR
     * @param toUserId The ID of the gym buddy
     * @param prId The unique identifier for the PR achievement
     * @return LiftrixResult containing true if notification was sent, false otherwise
     */
    suspend fun wasNotificationSent(
        fromUserId: String,
        toUserId: String,
        prId: String
    ): LiftrixResult<Boolean>
    
    /**
     * Gets PR notification preferences for a user
     * 
     * @param userId The ID of the user
     * @return LiftrixResult containing PR notification preferences
     */
    suspend fun getPRNotificationPreferences(userId: String): LiftrixResult<PRNotificationPreferences>
    
    /**
     * Updates PR notification preferences for a user
     * 
     * @param userId The ID of the user
     * @param preferences The updated PR notification preferences
     * @return LiftrixResult indicating success or failure
     */
    suspend fun updatePRNotificationPreferences(
        userId: String,
        preferences: PRNotificationPreferences
    ): LiftrixResult<Unit>
    
    /**
     * Gets all gym buddies who should be notified about a user's PR
     * This respects both users' privacy settings and notification preferences
     * 
     * @param userId The ID of the user who achieved the PR
     * @return LiftrixResult containing list of buddy IDs who should be notified
     */
    suspend fun getBuddiesForPRNotification(userId: String): LiftrixResult<List<String>>
}

/**
 * Represents a user's reaction to a PR notification
 */
data class PRReaction(
    val id: String,
    val userId: String,
    val buddyUserId: String,
    val prId: String,
    val reactionType: String,
    val timestamp: Long,
    val userName: String? = null,
    val userProfileImage: String? = null
)

/**
 * Represents a user's reaction history entry
 */
data class UserReaction(
    val prId: String,
    val buddyUserId: String,
    val buddyName: String,
    val reactionType: String,
    val timestamp: Long,
    val prDescription: String,
    val exerciseName: String
)

/**
 * User preferences for PR notifications
 */
data class PRNotificationPreferences(
    val enablePRNotifications: Boolean = true,
    val enableReactionNotifications: Boolean = true,
    val onlyFromBuddies: Boolean = true,
    val quietHoursEnabled: Boolean = true,
    val quietHoursStart: String = "22:00", // 10 PM
    val quietHoursEnd: String = "08:00",   // 8 AM
    val minimumPRSignificance: PRSignificance = PRSignificance.MODERATE,
    val maxNotificationsPerDay: Int = 10
)

/**
 * PR significance levels for notification filtering
 */
enum class PRSignificance {
    MINOR,      // <2% improvement
    MODERATE,   // 2-5% improvement  
    MAJOR,      // 5-10% improvement
    EXCEPTIONAL // >10% improvement
}