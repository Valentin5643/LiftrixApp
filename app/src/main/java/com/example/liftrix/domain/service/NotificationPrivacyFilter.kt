package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.AppNotification
import com.example.liftrix.domain.model.common.LiftrixResult

/**
 * Service interface for applying privacy rules to notifications.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 */
interface NotificationPrivacyFilter {

    /**
     * Checks if a notification can be sent to a user based on privacy settings,
     * blocks, mutes, and relationship status.
     */
    suspend fun canSendNotification(
        notification: AppNotification,
        fromUserId: String?,
        toUserId: String
    ): LiftrixResult<Boolean>

    /**
     * Checks if notifications from a specific user are blocked
     */
    suspend fun isUserBlocked(
        fromUserId: String,
        toUserId: String
    ): LiftrixResult<Boolean>

    /**
     * Checks if notifications from a user are muted (temporarily or permanently)
     */
    suspend fun isUserMuted(
        fromUserId: String,
        toUserId: String
    ): LiftrixResult<Boolean>

    /**
     * Checks if a notification category is muted for the user
     */
    suspend fun isCategoryMuted(
        category: String,
        userId: String
    ): LiftrixResult<Boolean>

    /**
     * Checks if all notifications are muted for the user
     */
    suspend fun areAllNotificationsMuted(
        userId: String
    ): LiftrixResult<Boolean>

    /**
     * Applies content filtering to notification text based on user preferences
     */
    suspend fun filterNotificationContent(
        notification: AppNotification,
        userId: String
    ): LiftrixResult<AppNotification>

    /**
     * Gets privacy settings that affect notifications for a user
     */
    suspend fun getPrivacySettings(
        userId: String
    ): LiftrixResult<NotificationPrivacySettings>

    data class NotificationPrivacySettings(
        val socialEnabled: Boolean,
        val workoutNotifications: Boolean,
        val achievementNotifications: Boolean,
        val allowFromStrangers: Boolean,
        val requireFollowToNotify: Boolean,
        val contentFiltering: ContentFilterLevel
    )

    enum class ContentFilterLevel {
        NONE,
        BASIC,
        STRICT
    }
}