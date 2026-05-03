package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.notifications.NotificationPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing notification preferences and muted users.
 * 
 * This interface defines the contract for all notification-related operations,
 * including preference management, mute functionality, and notification delivery
 * settings. The repository follows an offline-first approach where preferences
 * are immediately persisted locally and synchronized with remote storage.
 * 
 * Key responsibilities:
 * - Notification preferences CRUD operations with user scoping
 * - Muted users management (blocking specific user notifications)
 * - Real-time preference updates via reactive streams
 * - Privacy-aware notification filtering
 * - Cross-device preference synchronization
 */
interface NotificationRepository {
    
    /**
     * Retrieves notification preferences for a user as a reactive stream.
     * 
     * The Flow will emit the latest preferences from local storage with
     * remote synchronization. Updates to preferences will automatically
     * emit new values to observers.
     * 
     * @param userId The ID of the user whose preferences to retrieve
     * @return A Flow that emits NotificationPreferences or null if none exist
     */
    fun getNotificationPreferences(userId: String): Flow<NotificationPreferences?>
    
    /**
     * Updates notification preferences for a user.
     * 
     * This operation will immediately persist to local storage for instant
     * UI updates and asynchronously sync to remote storage for cross-device
     * consistency. All preference updates are scoped to the specific user.
     * 
     * @param preferences The updated NotificationPreferences to save
     * @return LiftrixResult indicating success or failure with error details
     */
    suspend fun updateNotificationPreferences(preferences: NotificationPreferences): LiftrixResult<Unit>
    
    /**
     * Mutes notifications from a specific user.
     * 
     * When a user is muted, all notifications from that user will be blocked
     * regardless of other notification settings. This is useful for blocking
     * spam or unwanted social interactions.
     * 
     * @param userId The ID of the user whose notifications to mute
     * @param targetUserId The ID of the user to mute
     * @return LiftrixResult indicating success or failure
     */
    suspend fun muteUser(userId: String, targetUserId: String): LiftrixResult<Unit>
    
    /**
     * Unmutes notifications from a specific user.
     * 
     * @param userId The ID of the user whose mute list to update
     * @param targetUserId The ID of the user to unmute
     * @return LiftrixResult indicating success or failure
     */
    suspend fun unmuteUser(userId: String, targetUserId: String): LiftrixResult<Unit>
    
    /**
     * Retrieves the list of muted users for a user.
     * 
     * @param userId The ID of the user whose muted users to retrieve
     * @return Flow that emits the list of muted user IDs
     */
    fun getMutedUsers(userId: String): Flow<List<String>>
    
    /**
     * Gets the count of muted users for display in settings.
     * 
     * @param userId The ID of the user whose muted users count to retrieve
     * @return Flow that emits the count of muted users
     */
    fun getMutedUsersCount(userId: String): Flow<Int>
    
    /**
     * Checks if a specific user is muted.
     * 
     * @param userId The ID of the user whose mute status to check
     * @param targetUserId The ID of the potentially muted user
     * @return True if the target user is muted, false otherwise
     */
    suspend fun isUserMuted(userId: String, targetUserId: String): Boolean
    
    /**
     * Creates default notification preferences for a new user.
     * 
     * This is typically called during user registration or when preferences
     * don't exist. Creates sensible defaults based on the user's profile
     * and app-wide notification policies.
     * 
     * @param userId The ID of the user for whom to create defaults
     * @return LiftrixResult containing the created NotificationPreferences
     */
    suspend fun createDefaultPreferences(userId: String): LiftrixResult<NotificationPreferences>
    
    /**
     * Deletes all notification preferences and muted users for a user.
     * 
     * This is typically used during account deletion or data cleanup.
     * Removes both local and remote notification data.
     * 
     * @param userId The ID of the user whose notification data to delete
     * @return LiftrixResult indicating success or failure
     */
    suspend fun deleteAllNotificationData(userId: String): LiftrixResult<Unit>
    
    /**
     * Forces synchronization of notification preferences from local to remote.
     * 
     * This operation ensures that remote storage is up-to-date with the
     * latest preferences from local storage. Useful for manual sync operations
     * or after connectivity is restored.
     * 
     * @param userId The ID of the user whose preferences to sync
     * @return LiftrixResult indicating success or failure
     */
    suspend fun syncNotificationPreferences(userId: String): LiftrixResult<Unit>
}