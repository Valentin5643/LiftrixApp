package com.example.liftrix.data.service

import com.example.liftrix.data.local.dao.NotificationMuteDao
import com.example.liftrix.data.local.dao.BlockedUserDao
import com.example.liftrix.data.local.dao.SocialPrivacySettingsDao
import com.example.liftrix.data.local.dao.FollowRelationshipDao
import com.example.liftrix.domain.model.AppNotification
import com.example.liftrix.domain.service.NotificationPrivacyFilter
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of NotificationPrivacyFilter with comprehensive privacy rule enforcement.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 * 
 * Privacy Rules Applied:
 * - User blocks and mutes
 * - Social relationship requirements
 * - Content filtering based on user preferences
 * - Category-specific privacy settings
 */
@Singleton
class NotificationPrivacyFilterImpl @Inject constructor(
    private val muteDao: NotificationMuteDao,
    private val blockedUserDao: BlockedUserDao,
    private val socialPrivacyDao: SocialPrivacySettingsDao,
    private val followRelationshipDao: FollowRelationshipDao
) : NotificationPrivacyFilter {

    override suspend fun canSendNotification(
        notification: AppNotification,
        fromUserId: String?,
        toUserId: String
    ): LiftrixResult<Boolean> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "CHECK_NOTIFICATION_PRIVACY",
                    errorMessage = "Failed to check notification privacy",
                    analyticsContext = mapOf(
                        "to_user_id" to toUserId,
                        "from_user_id" to (fromUserId ?: "system"),
                        "notification_type" to notification.type.value
                    )
                )
            }
        ) {
            withContext(Dispatchers.IO) {
                val currentTime = System.currentTimeMillis()

                // Step 1: Check if all notifications are muted
                val allMuted = muteDao.areAllNotificationsMuted(toUserId, currentTime)
                if (allMuted) {
                    Timber.d("All notifications muted for user $toUserId")
                    return@withContext false
                }

                // Step 2: Check if notification category is muted
                val categoryMuted = muteDao.isCategoryMuted(
                    toUserId, 
                    notification.category.value, 
                    currentTime
                )
                if (categoryMuted) {
                    Timber.d("Category ${notification.category.value} muted for user $toUserId")
                    return@withContext false
                }

                // Step 3: Check sender-specific rules (if notification has a sender)
                fromUserId?.let { sender ->
                    // Check if sender is blocked
                    val isBlocked = blockedUserDao.isUserBlocked(toUserId, sender)
                    if (isBlocked) {
                        Timber.d("Sender $sender is blocked by user $toUserId")
                        return@withContext false
                    }

                    // Check if sender is muted
                    val isMuted = muteDao.isUserMuted(toUserId, sender, currentTime)
                    if (isMuted) {
                        Timber.d("Sender $sender is muted by user $toUserId")
                        return@withContext false
                    }

                    // Check social relationship requirements for social notifications
                    if (notification.category == AppNotification.NotificationCategory.SOCIAL) {
                        val relationshipRequired = checkRelationshipRequirement(toUserId, sender, notification)
                        if (!relationshipRequired) {
                            Timber.d("Relationship requirement not met for social notification from $sender to $toUserId")
                            return@withContext false
                        }
                    }
                }

                // Step 4: Check privacy settings for notification category
                val privacySettings = getPrivacySettings(toUserId)
                val categoryAllowed = privacySettings.fold(
                    onSuccess = { settings ->
                        isCategoryAllowed(notification, settings)
                    },
                    onFailure = { error ->
                        // If we can't get privacy settings, default to allowing (fail open for notifications)
                        Timber.w("Could not get privacy settings for user $toUserId, defaulting to allow")
                        true
                    }
                )
                if (!categoryAllowed) {
                    Timber.d("Notification category ${notification.category.value} not allowed by privacy settings for user $toUserId")
                    return@withContext false
                }

                // Step 5: Apply content filtering (if needed)
                // For now, we'll just log this step - content filtering could be implemented here
                Timber.d("Notification passed privacy checks for user $toUserId")
                
                true
            }
        }
    }

    override suspend fun isUserBlocked(
        fromUserId: String,
        toUserId: String
    ): LiftrixResult<Boolean> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to check if user is blocked",
                    analyticsContext = mapOf(
                        "from_user_id" to fromUserId,
                        "to_user_id" to toUserId
                    ),
                    operation = "CHECK_USER_BLOCKED"
                )
            }
        ) {
            blockedUserDao.isUserBlocked(toUserId, fromUserId)
        }
    }

    override suspend fun isUserMuted(
        fromUserId: String,
        toUserId: String
    ): LiftrixResult<Boolean> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to check if user is muted",
                    analyticsContext = mapOf(
                        "from_user_id" to fromUserId,
                        "to_user_id" to toUserId
                    ),
                    operation = "CHECK_USER_MUTED"
                )
            }
        ) {
            val currentTime = System.currentTimeMillis()
            muteDao.isUserMuted(toUserId, fromUserId, currentTime)
        }
    }

    override suspend fun isCategoryMuted(
        category: String,
        userId: String
    ): LiftrixResult<Boolean> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to check if category is muted",
                    analyticsContext = mapOf(
                        "user_id" to userId,
                        "category" to category
                    ),
                    operation = "CHECK_CATEGORY_MUTED"
                )
            }
        ) {
            val currentTime = System.currentTimeMillis()
            muteDao.isCategoryMuted(userId, category, currentTime)
        }
    }

    override suspend fun areAllNotificationsMuted(
        userId: String
    ): LiftrixResult<Boolean> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to check if all notifications are muted",
                    analyticsContext = mapOf("user_id" to userId),
                    operation = "CHECK_ALL_MUTED"
                )
            }
        ) {
            val currentTime = System.currentTimeMillis()
            muteDao.areAllNotificationsMuted(userId, currentTime)
        }
    }

    override suspend fun filterNotificationContent(
        notification: AppNotification,
        userId: String
    ): LiftrixResult<AppNotification> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "FILTER_CONTENT",
                    errorMessage = "Failed to filter notification content",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            // Get user's content filtering preferences
            val privacySettings = getPrivacySettings(userId)
            
            privacySettings.fold(
                onSuccess = { settings ->
                    val filterLevel = settings.contentFiltering
                    applyContentFilter(notification, filterLevel)
                },
                onFailure = { error ->
                    // If we can't get settings, return original notification
                    notification
                }
            )
        }
    }

    override suspend fun getPrivacySettings(
        userId: String
    ): LiftrixResult<NotificationPrivacyFilter.NotificationPrivacySettings> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get privacy settings",
                    analyticsContext = mapOf("user_id" to userId),
                    operation = "GET_PRIVACY_SETTINGS"
                )
            }
        ) {
            val socialSettings = socialPrivacyDao.getPrivacySettings(userId)
            
            if (socialSettings != null) {
                NotificationPrivacyFilter.NotificationPrivacySettings(
                    socialEnabled = socialSettings.socialEnabled,
                    workoutNotifications = socialSettings.workoutSharingEnabled,
                    achievementNotifications = socialSettings.showAchievements,
                    allowFromStrangers = socialSettings.allowFollowRequests,
                    requireFollowToNotify = !socialSettings.allowFollowRequests,
                    contentFiltering = when (socialSettings.profileVisibility) {
                        "PUBLIC" -> NotificationPrivacyFilter.ContentFilterLevel.NONE
                        "FOLLOWERS" -> NotificationPrivacyFilter.ContentFilterLevel.BASIC
                        else -> NotificationPrivacyFilter.ContentFilterLevel.STRICT
                    }.let { defaultLevel ->
                        // Use profile visibility as proxy for content filtering
                        when (defaultLevel) {
                            NotificationPrivacyFilter.ContentFilterLevel.NONE -> NotificationPrivacyFilter.ContentFilterLevel.NONE
                            NotificationPrivacyFilter.ContentFilterLevel.BASIC -> NotificationPrivacyFilter.ContentFilterLevel.BASIC
                            NotificationPrivacyFilter.ContentFilterLevel.STRICT -> NotificationPrivacyFilter.ContentFilterLevel.STRICT
                        }
                    }
                )
            } else {
                // Default privacy settings if none exist
                NotificationPrivacyFilter.NotificationPrivacySettings(
                    socialEnabled = true,
                    workoutNotifications = true,
                    achievementNotifications = true,
                    allowFromStrangers = false,
                    requireFollowToNotify = true,
                    contentFiltering = NotificationPrivacyFilter.ContentFilterLevel.BASIC
                )
            }
        }
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    private suspend fun checkRelationshipRequirement(
        toUserId: String,
        fromUserId: String,
        notification: AppNotification
    ): Boolean {
        // Get privacy settings to check relationship requirements
        val privacyResult = getPrivacySettings(toUserId)
        val requiresFollowership = privacyResult.fold(
            onSuccess = { settings -> settings.requireFollowToNotify },
            onFailure = { error -> true } // Default to requiring relationship
        )

        if (!requiresFollowership) {
            return true // No relationship required
        }

        // For certain notification types, check if users have a relationship
        return when (notification.type) {
            AppNotification.NotificationType.GYM_BUDDY_PR,
            AppNotification.NotificationType.POST_LIKE,
            AppNotification.NotificationType.POST_COMMENT,
            AppNotification.NotificationType.SOCIAL_MENTION -> {
                // Check if they follow each other or are gym buddies
                val isFollowing = followRelationshipDao.isFollowing(fromUserId, toUserId)
                val areGymBuddies = followRelationshipDao.areMutuallyFollowing(fromUserId, toUserId)
                isFollowing || areGymBuddies
            }
            
            AppNotification.NotificationType.FOLLOW_REQUEST -> {
                // Follow requests don't require existing relationship
                true
            }
            
            else -> {
                // For other social notifications, require some relationship
                followRelationshipDao.areMutuallyFollowing(fromUserId, toUserId)
            }
        }
    }

    private fun isCategoryAllowed(
        notification: AppNotification,
        settings: NotificationPrivacyFilter.NotificationPrivacySettings
    ): Boolean {
        return when (notification.category) {
            AppNotification.NotificationCategory.SOCIAL -> settings.socialEnabled
            AppNotification.NotificationCategory.WORKOUT -> settings.workoutNotifications
            AppNotification.NotificationCategory.ACHIEVEMENT -> settings.achievementNotifications
            AppNotification.NotificationCategory.REMINDER,
            AppNotification.NotificationCategory.SYSTEM -> true // Always allowed
        }
    }

    private fun applyContentFilter(
        notification: AppNotification,
        filterLevel: NotificationPrivacyFilter.ContentFilterLevel
    ): AppNotification {
        return when (filterLevel) {
            NotificationPrivacyFilter.ContentFilterLevel.NONE -> notification
            
            NotificationPrivacyFilter.ContentFilterLevel.BASIC -> {
                // Basic filtering: remove sensitive information
                notification.copy(
                    body = filterBasicContent(notification.body),
                    data = notification.data.filterKeys { key ->
                        !key.contains("personal", ignoreCase = true) &&
                        !key.contains("private", ignoreCase = true)
                    }
                )
            }
            
            NotificationPrivacyFilter.ContentFilterLevel.STRICT -> {
                // Strict filtering: generic messages only
                val genericBody = when (notification.category) {
                    AppNotification.NotificationCategory.SOCIAL -> "You have a new social activity"
                    AppNotification.NotificationCategory.WORKOUT -> "Workout activity update"
                    AppNotification.NotificationCategory.ACHIEVEMENT -> "New achievement unlocked"
                    else -> "New notification"
                }
                
                notification.copy(
                    body = genericBody,
                    data = emptyMap() // Remove all data for strict filtering
                )
            }
        }
    }

    private fun filterBasicContent(content: String): String {
        // Basic content filtering - remove potentially sensitive patterns
        var filtered = content
        
        // Remove email addresses
        filtered = filtered.replace(Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"), "[email]")
        
        // Remove phone numbers (basic pattern)
        filtered = filtered.replace(Regex("\\b\\d{3}-\\d{3}-\\d{4}\\b"), "[phone]")
        
        // Remove what looks like personal names in certain contexts
        // This is basic and could be improved with NLP
        
        return filtered
    }
}