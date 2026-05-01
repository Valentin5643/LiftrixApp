package com.example.liftrix.domain.repository.notifications

/**
 * Repository interface for managing user muting for notifications.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 * 
 * Handles muting users from social notifications including:
 * - Muting/unmuting specific users
 * - Getting muted users list and count
 * - Checking mute status
 * 
 * All operations are user-scoped for security.
 */
interface NotificationMuteRepository {
    
    /**
     * Gets the count of users muted by a specific user.
     * 
     * @param userId User ID to get muted count for
     * @return Number of users muted by this user
     * @throws IllegalArgumentException if userId is blank
     */
    suspend fun getMutedUsersCount(userId: String): Int
    
    /**
     * Gets the list of user IDs muted by a specific user.
     * 
     * @param userId User ID to get muted list for
     * @return List of muted user IDs
     * @throws IllegalArgumentException if userId is blank
     */
    suspend fun getMutedUsers(userId: String): List<String>
    
    /**
     * Checks if a user has muted another user.
     * 
     * @param userId User ID who potentially muted
     * @param mutedUserId User ID who is potentially muted
     * @return true if mutedUserId is muted by userId, false otherwise
     */
    suspend fun isUserMuted(userId: String, mutedUserId: String): Boolean
    
    /**
     * Mutes a user for notifications.
     * 
     * @param userId User ID who is muting
     * @param mutedUserId User ID to mute
     * @throws IllegalArgumentException if either userId is blank
     */
    suspend fun muteUser(userId: String, mutedUserId: String)
    
    /**
     * Unmutes a user for notifications.
     * 
     * @param userId User ID who is unmuting
     * @param mutedUserId User ID to unmute
     * @throws IllegalArgumentException if either userId is blank
     */
    suspend fun unmuteUser(userId: String, mutedUserId: String)
    
    /**
     * Clears all muted users for a specific user.
     * 
     * @param userId User ID to clear muted users for
     * @throws IllegalArgumentException if userId is blank
     */
    suspend fun clearAllMutedUsers(userId: String)
}