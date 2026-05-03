package com.example.liftrix.domain.usecase.notifications

import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.notifications.NotificationPreferences
import com.example.liftrix.domain.repository.notifications.NotificationMuteRepository
import com.example.liftrix.domain.repository.notifications.NotificationPreferencesRepository
import javax.inject.Inject

/**
 * Consolidated use case for notification preferences and muting operations.
 * Part of Phase 4: Remaining Domains consolidation.
 *
 * **Replaces**:
 * - GetNotificationPreferencesUseCase.kt
 * - UpdateNotificationPreferencesUseCase.kt
 *
 * Follows CQRS-lite pattern:
 * - Query: invoke() for getting preferences
 * - Commands: update(), reset(), toggleCategory(), muteUser(), unmuteUser(), etc.
 *
 * **User Scoping**: All operations enforce user_id filtering for security.
 * **Error Handling**: All operations return LiftrixResult<T> with proper error context.
 *
 * @property preferencesRepository Repository for notification preferences operations
 * @property muteRepository Repository for user muting operations
 */
class NotificationPreferencesUseCase @Inject constructor(
    private val preferencesRepository: NotificationPreferencesRepository,
    private val muteRepository: NotificationMuteRepository
) {

    // ==================== QUERY OPERATIONS ====================

    /**
     * Gets notification preferences for a user.
     * Returns default preferences if none exist.
     *
     * **Replaces**: GetNotificationPreferencesUseCase.invoke()
     *
     * @param userId User ID to get preferences for
     * @return LiftrixResult<NotificationPreferences> with preferences or error
     */
    suspend operator fun invoke(userId: String): LiftrixResult<NotificationPreferences> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "GET_NOTIFICATION_PREFERENCES_FAILED",
                errorMessage = "Failed to get notification preferences: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "GET_NOTIFICATION_PREFERENCES",
                    "user_id" to userId
                )
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        preferencesRepository.getNotificationPreferences(userId)
    }

    /**
     * Checks if notifications are enabled for a specific category.
     *
     * @param userId User ID to check
     * @param category Notification category (social, workout, achievement, reminder)
     * @return LiftrixResult<Boolean> indicating if category is enabled
     */
    suspend fun isCategoryEnabled(userId: String, category: String): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CHECK_CATEGORY_ENABLED_FAILED",
                errorMessage = "Failed to check if category is enabled: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "CHECK_CATEGORY_ENABLED",
                    "user_id" to userId,
                    "category" to category
                )
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        preferencesRepository.isCategoryEnabled(userId, category)
    }

    /**
     * Gets the list of user IDs muted by a specific user.
     *
     * @param userId User ID to get muted list for
     * @return LiftrixResult<List<String>> with muted user IDs
     */
    suspend fun getMutedUsers(userId: String): LiftrixResult<List<String>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "GET_MUTED_USERS_FAILED",
                errorMessage = "Failed to get muted users: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "GET_MUTED_USERS",
                    "user_id" to userId
                )
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        muteRepository.getMutedUsers(userId)
    }

    /**
     * Checks if a user has muted another user.
     *
     * @param userId User ID who potentially muted
     * @param targetUserId User ID who is potentially muted
     * @return LiftrixResult<Boolean> indicating if user is muted
     */
    suspend fun isUserMuted(userId: String, targetUserId: String): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CHECK_USER_MUTED_FAILED",
                errorMessage = "Failed to check if user is muted: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "CHECK_USER_MUTED",
                    "user_id" to userId,
                    "target_user_id" to targetUserId
                )
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(targetUserId.isNotBlank()) { "Target user ID cannot be blank" }
        muteRepository.isUserMuted(userId, targetUserId)
    }

    /**
     * Gets the count of users muted by a specific user.
     *
     * @param userId User ID to get muted count for
     * @return LiftrixResult<Int> with count of muted users
     */
    suspend fun getMutedUsersCount(userId: String): LiftrixResult<Int> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "GET_MUTED_COUNT_FAILED",
                errorMessage = "Failed to get muted users count: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "GET_MUTED_COUNT",
                    "user_id" to userId
                )
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        muteRepository.getMutedUsersCount(userId)
    }

    // ==================== COMMAND OPERATIONS ====================

    /**
     * Updates notification preferences for a user.
     * Creates new preferences if they don't exist.
     *
     * **Replaces**: UpdateNotificationPreferencesUseCase.invoke()
     *
     * @param preferences Updated notification preferences
     * @return LiftrixResult<Unit> indicating success or failure
     */
    suspend fun update(preferences: NotificationPreferences): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "UPDATE_NOTIFICATION_PREFERENCES_FAILED",
                errorMessage = "Failed to update notification preferences: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "UPDATE_NOTIFICATION_PREFERENCES",
                    "user_id" to preferences.userId,
                    "notifications_enabled" to preferences.notificationsEnabled.toString()
                )
            )
        }
    ) {
        require(preferences.userId.isNotBlank()) { "User ID cannot be blank" }

        // Validate quiet hours
        require(preferences.quietHoursStart in 0..23) { "Quiet hours start must be between 0-23" }
        require(preferences.quietHoursEnd in 0..23) { "Quiet hours end must be between 0-23" }

        // Validate batch window
        require(preferences.batchWindowMinutes in 1..1440) { "Batch window must be between 1-1440 minutes" }

        // Validate delivery frequency
        require(preferences.deliveryFrequency in listOf("IMMEDIATE", "HOURLY", "DAILY")) {
            "Delivery frequency must be IMMEDIATE, HOURLY, or DAILY"
        }

        preferencesRepository.updateNotificationPreferences(preferences)
    }

    /**
     * Resets notification preferences to default values for a user.
     *
     * @param userId User ID to reset preferences for
     * @return LiftrixResult<Unit> indicating success or failure
     */
    suspend fun reset(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "RESET_NOTIFICATION_PREFERENCES_FAILED",
                errorMessage = "Failed to reset notification preferences: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "RESET_NOTIFICATION_PREFERENCES",
                    "user_id" to userId
                )
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        preferencesRepository.resetNotificationPreferences(userId)
    }

    /**
     * Toggles notification category on/off for a user.
     * Convenience method for toggling individual categories.
     *
     * @param userId User ID to toggle category for
     * @param category Category to toggle (social, workout, achievement, reminder)
     * @param enabled New state for the category
     * @return LiftrixResult<Unit> indicating success or failure
     */
    suspend fun toggleCategory(
        userId: String,
        category: String,
        enabled: Boolean
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "TOGGLE_CATEGORY_FAILED",
                errorMessage = "Failed to toggle category: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "TOGGLE_CATEGORY",
                    "user_id" to userId,
                    "category" to category,
                    "enabled" to enabled.toString()
                )
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(category.isNotBlank()) { "Category cannot be blank" }

        val currentPreferences = preferencesRepository.getNotificationPreferences(userId)
        val updatedPreferences = when (category.lowercase()) {
            "social" -> currentPreferences.copy(socialNotifications = enabled)
            "workout" -> currentPreferences.copy(workoutNotifications = enabled)
            "achievement" -> currentPreferences.copy(achievementNotifications = enabled)
            "reminder" -> currentPreferences.copy(reminderNotifications = enabled)
            else -> throw IllegalArgumentException("Unknown category: $category")
        }

        preferencesRepository.updateNotificationPreferences(
            updatedPreferences.copy(updatedAt = System.currentTimeMillis())
        )
    }

    /**
     * Toggles master notification switch on/off for a user.
     *
     * @param userId User ID to toggle notifications for
     * @param enabled New state for notifications
     * @return LiftrixResult<Unit> indicating success or failure
     */
    suspend fun toggleNotifications(userId: String, enabled: Boolean): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "TOGGLE_NOTIFICATIONS_FAILED",
                errorMessage = "Failed to toggle notifications: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "TOGGLE_NOTIFICATIONS",
                    "user_id" to userId,
                    "enabled" to enabled.toString()
                )
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }

        val currentPreferences = preferencesRepository.getNotificationPreferences(userId)
        preferencesRepository.updateNotificationPreferences(
            currentPreferences.copy(
                notificationsEnabled = enabled,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Updates quiet hours configuration for a user.
     *
     * @param userId User ID to update quiet hours for
     * @param enabled Whether quiet hours are enabled
     * @param startHour Start hour (0-23)
     * @param endHour End hour (0-23)
     * @return LiftrixResult<Unit> indicating success or failure
     */
    suspend fun updateQuietHours(
        userId: String,
        enabled: Boolean,
        startHour: Int,
        endHour: Int
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "UPDATE_QUIET_HOURS_FAILED",
                errorMessage = "Failed to update quiet hours: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "UPDATE_QUIET_HOURS",
                    "user_id" to userId,
                    "enabled" to enabled.toString(),
                    "start_hour" to startHour.toString(),
                    "end_hour" to endHour.toString()
                )
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(startHour in 0..23) { "Start hour must be between 0-23" }
        require(endHour in 0..23) { "End hour must be between 0-23" }

        val currentPreferences = preferencesRepository.getNotificationPreferences(userId)
        preferencesRepository.updateNotificationPreferences(
            currentPreferences.copy(
                quietHoursEnabled = enabled,
                quietHoursStart = startHour,
                quietHoursEnd = endHour,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Mutes a user from social notifications.
     *
     * @param userId User ID who is muting
     * @param targetUserId User ID to mute
     * @return LiftrixResult<Unit> indicating success or failure
     */
    suspend fun muteUser(userId: String, targetUserId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "MUTE_USER_FAILED",
                errorMessage = "Failed to mute user: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "MUTE_USER",
                    "user_id" to userId,
                    "target_user_id" to targetUserId
                )
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(targetUserId.isNotBlank()) { "Target user ID cannot be blank" }
        require(userId != targetUserId) { "Cannot mute yourself" }

        muteRepository.muteUser(userId, targetUserId)
    }

    /**
     * Unmutes a user from social notifications.
     *
     * @param userId User ID who is unmuting
     * @param targetUserId User ID to unmute
     * @return LiftrixResult<Unit> indicating success or failure
     */
    suspend fun unmuteUser(userId: String, targetUserId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "UNMUTE_USER_FAILED",
                errorMessage = "Failed to unmute user: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "UNMUTE_USER",
                    "user_id" to userId,
                    "target_user_id" to targetUserId
                )
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(targetUserId.isNotBlank()) { "Target user ID cannot be blank" }

        muteRepository.unmuteUser(userId, targetUserId)
    }

    /**
     * Clears all muted users for a specific user.
     *
     * @param userId User ID to clear muted users for
     * @return LiftrixResult<Unit> indicating success or failure
     */
    suspend fun clearAllMutedUsers(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CLEAR_MUTED_USERS_FAILED",
                errorMessage = "Failed to clear muted users: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "CLEAR_MUTED_USERS",
                    "user_id" to userId
                )
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        muteRepository.clearAllMutedUsers(userId)
    }
}
