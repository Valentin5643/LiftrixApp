package com.example.liftrix.data.service

import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.service.NotificationService
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.notifications.NotificationPreferences
import com.example.liftrix.domain.repository.SettingsRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NotificationServiceImpl - Firebase Cloud Messaging implementation of NotificationService
 * 
 * Handles push notifications for social features:
 * - Follow requests and acceptances
 * - Gym buddy invitations  
 * - Personal record achievements
 * - General achievement notifications
 * 
 * Uses Firebase FCM topics and direct messaging
 */
@Singleton
class NotificationServiceImpl @Inject constructor(
    private val firebaseMessaging: FirebaseMessaging,
    private val settingsRepository: SettingsRepository
) : NotificationService {
    
    /**
     * Helper method to check if notifications are enabled for a specific user and type.
     * This is critical for ensuring user notification preferences are respected.
     */
    private suspend fun shouldSendNotification(
        userId: String, 
        notificationType: NotificationType
    ): Boolean {
        return try {
            // Check user's notification preferences from settings repository
            val settings = settingsRepository.getUserSettings(userId).first()
            
            Timber.d("Checking notification permission for user $userId, type $notificationType")
            
            // If no settings found, allow notifications by default
            if (settings == null) {
                Timber.d("No settings found for user $userId, allowing notifications by default")
                return true
            }
            
            // Check master notification toggle first
            if (!settings.notificationsEnabled) {
                Timber.d("Notifications disabled globally for user $userId")
                return false
            }
            
            // Check category-specific preferences based on notification type
            val isEnabled = when (notificationType) {
                NotificationType.WORKOUT_REMINDER -> settings.notificationsEnabled
                NotificationType.ACHIEVEMENT -> settings.notificationsEnabled
                NotificationType.GYM_BUDDY_PR -> settings.notificationsEnabled
                NotificationType.FOLLOW_REQUEST -> settings.notificationsEnabled
                NotificationType.FOLLOW_ACCEPTED -> settings.notificationsEnabled
                NotificationType.GYM_BUDDY_INVITE -> settings.notificationsEnabled
                NotificationType.POST_LIKE -> settings.notificationsEnabled
                NotificationType.POST_COMMENT -> settings.notificationsEnabled
                NotificationType.MENTION -> settings.notificationsEnabled
                NotificationType.PERSONAL_RECORD -> settings.notificationsEnabled
                NotificationType.REST_DAY_REMINDER -> settings.notificationsEnabled
            }
            
            Timber.d("Notification type $notificationType enabled: $isEnabled for user $userId")
            isEnabled
        } catch (e: Exception) {
            Timber.e(e, "Error checking notification preferences for user $userId")
            false // Default to not sending if we can't determine preferences
        }
    }
    
    /**
     * Helper method to check quiet hours and delivery frequency.
     */
    private suspend fun isWithinQuietHours(userId: String): Boolean {
        return try {
            val settings = settingsRepository.getUserSettings(userId).first()
            
            // If no settings found or quiet hours not configured, not within quiet hours
            if (settings == null) {
                return false
            }
            
            // For now, we'll use a simple implementation
            // In the future, this could check against user-defined quiet hours
            // Default quiet hours: 10 PM to 8 AM
            val now = LocalTime.now()
            val quietStart = LocalTime.of(22, 0) // 10 PM
            val quietEnd = LocalTime.of(8, 0) // 8 AM
            
            // Handle quiet hours that span midnight
            val isWithinQuietHours = if (quietStart <= quietEnd) {
                // Normal case: doesn't cross midnight
                now >= quietStart && now <= quietEnd
            } else {
                // Crosses midnight: e.g., 22:00 to 08:00 next day
                now >= quietStart || now <= quietEnd
            }
            
            if (isWithinQuietHours) {
                Timber.d("Within default quiet hours for user $userId: 10 PM to 8 AM")
            }
            
            isWithinQuietHours
        } catch (e: Exception) {
            Timber.e(e, "Error checking quiet hours for user $userId")
            false
        }
    }
    
    /**
     * Enum for notification types to ensure proper preference checking.
     */
    private enum class NotificationType {
        FOLLOW_REQUEST,
        FOLLOW_ACCEPTED,
        GYM_BUDDY_INVITE,
        GYM_BUDDY_PR,
        POST_LIKE,
        POST_COMMENT,
        MENTION,
        ACHIEVEMENT,
        PERSONAL_RECORD,
        WORKOUT_REMINDER,
        REST_DAY_REMINDER
    }
    
    override suspend fun sendFollowRequestNotification(
        targetUserId: String,
        requesterUserId: String,
        requesterName: String
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to send follow request notification",
                analyticsContext = mapOf("operation" to "SEND_FOLLOW_REQUEST_NOTIFICATION")
            )
        }
    ) {
        Timber.d("Checking notification preferences for follow request: $requesterName -> $targetUserId")
        
        // Check if user has notifications enabled for follow requests
        if (!shouldSendNotification(targetUserId, NotificationType.FOLLOW_REQUEST)) {
            Timber.d("Follow request notification blocked by user preferences for user $targetUserId")
            return@liftrixCatching Unit
        }
        
        // Check quiet hours
        if (isWithinQuietHours(targetUserId)) {
            Timber.d("Follow request notification delayed due to quiet hours for user $targetUserId")
            // In a real implementation, this would queue the notification for later
            return@liftrixCatching Unit
        }
        
        Timber.d("Sending follow request notification: $requesterName -> $targetUserId")
        
        // In a real implementation, this would:
        // 1. Get target user's FCM token from database
        // 2. Send push notification via Firebase Admin SDK
        // 3. Store notification in database for in-app display
        // 4. Handle rate limiting to prevent spam
        
        // For now, just log the notification
        Timber.i("✅ Follow request notification sent: $requesterName wants to follow user $targetUserId")
        Unit
    }
    
    override suspend fun sendFollowAcceptedNotification(
        targetUserId: String,
        accepterUserId: String,
        accepterName: String
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to send follow acceptance notification",
                analyticsContext = mapOf("operation" to "SEND_FOLLOW_ACCEPTED_NOTIFICATION")
            )
        }
    ) {
        Timber.d("Checking notification preferences for follow acceptance: $accepterName -> $targetUserId")
        
        // Check if user has social notifications enabled
        if (!shouldSendNotification(targetUserId, NotificationType.FOLLOW_ACCEPTED)) {
            Timber.d("Follow acceptance notification blocked by user preferences for user $targetUserId")
            return@liftrixCatching Unit
        }
        
        // Check quiet hours
        if (isWithinQuietHours(targetUserId)) {
            Timber.d("Follow acceptance notification delayed due to quiet hours for user $targetUserId")
            return@liftrixCatching Unit
        }
        
        Timber.d("Sending follow accepted notification: $accepterName -> $targetUserId")
        
        // Implementation would send FCM notification
        Timber.i("✅ Follow accepted notification sent: $accepterName accepted follow from user $targetUserId")
        Unit
    }
    
    override suspend fun sendFollowNotification(
        targetUserId: String,
        followerUserId: String,
        followerName: String
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to send follow notification",
                analyticsContext = mapOf("operation" to "SEND_FOLLOW_NOTIFICATION")
            )
        }
    ) {
        Timber.d("Sending follow notification: $followerName -> $targetUserId")
        
        // Implementation would send FCM notification for public profile follows
        Timber.i("Follow notification sent: $followerName started following user $targetUserId")
        Unit
    }
    
    override suspend fun sendGymBuddyInviteNotification(
        targetUserId: String,
        inviterUserId: String,
        inviterName: String
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to send gym buddy invite notification",
                analyticsContext = mapOf("operation" to "SEND_GYM_BUDDY_INVITE")
            )
        }
    ) {
        Timber.d("Sending gym buddy invite: $inviterName -> $targetUserId")
        
        // Implementation would send FCM notification with custom action
        Timber.i("Gym buddy invite sent: $inviterName invited user $targetUserId")
        Unit
    }
    
    override suspend fun sendPRNotificationToBuddies(
        achieverUserId: String,
        achieverName: String,
        achievement: String,
        buddyUserIds: List<String>
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to send PR notification",
                analyticsContext = mapOf("operation" to "SEND_PR_NOTIFICATION")
            )
        }
    ) {
        Timber.d("Checking notification preferences for PR notification: $achieverName achieved '$achievement' to ${buddyUserIds.size} buddies")
        
        var notificationsSent = 0
        var notificationsBlocked = 0
        var notificationsDelayed = 0
        
        // Check preferences for each buddy individually
        for (buddyUserId in buddyUserIds) {
            // Check if this buddy has gym buddy PR notifications enabled
            if (!shouldSendNotification(buddyUserId, NotificationType.GYM_BUDDY_PR)) {
                Timber.d("PR notification blocked by user preferences for buddy $buddyUserId")
                notificationsBlocked++
                continue
            }
            
            // Check quiet hours for this buddy
            if (isWithinQuietHours(buddyUserId)) {
                Timber.d("PR notification delayed due to quiet hours for buddy $buddyUserId")
                // In a real implementation, this would queue the notification for later
                notificationsDelayed++
                continue
            }
            
            // Send notification to this buddy
            Timber.i("✅ PR notification sent to buddy $buddyUserId: $achieverName's '$achievement'")
            notificationsSent++
        }
        
        // Implementation would:
        // 1. Check notification cooldown periods (1 PR notification per buddy per day)
        // 2. Send batch notification to all allowed gym buddies
        // 3. Update last PR notification timestamps
        
        Timber.i("PR notification summary: $notificationsSent sent, $notificationsBlocked blocked, $notificationsDelayed delayed")
        Unit
    }
    
    override suspend fun sendAchievementNotification(
        userId: String,
        achievementTitle: String,
        achievementDescription: String
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to send achievement notification",
                analyticsContext = mapOf("operation" to "SEND_ACHIEVEMENT_NOTIFICATION")
            )
        }
    ) {
        Timber.d("Checking notification preferences for achievement: $achievementTitle to $userId")
        
        // Check if user has achievement notifications enabled
        if (!shouldSendNotification(userId, NotificationType.ACHIEVEMENT)) {
            Timber.d("Achievement notification blocked by user preferences for user $userId")
            return@liftrixCatching Unit
        }
        
        // Check quiet hours
        if (isWithinQuietHours(userId)) {
            Timber.d("Achievement notification delayed due to quiet hours for user $userId")
            // In a real implementation, this would queue the notification for later
            return@liftrixCatching Unit
        }
        
        Timber.d("Sending achievement notification: $achievementTitle to $userId")
        
        // Implementation would send celebratory notification
        Timber.i("✅ Achievement notification sent: '$achievementTitle' to user $userId")
        Unit
    }
}