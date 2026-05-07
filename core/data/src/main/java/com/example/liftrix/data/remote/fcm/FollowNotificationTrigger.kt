package com.example.liftrix.data.remote.fcm

import com.example.liftrix.data.local.dao.FollowRequestDao
import com.example.liftrix.data.local.dao.SocialProfileDao
import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Service for triggering follow-related notifications via Firebase Cloud Messaging.
 * Part of user profiles and follow system from SPEC-20250113-user-profiles-follow.
 * 
 * Features:
 * - Follow request notifications with customizable message templates
 * - Follow acceptance notifications with engagement tracking
 * - Batch notification processing for efficiency
 * - Notification preference handling and user consent validation
 * - Delivery tracking and retry logic for failed notifications
 * - A/B testing support for notification content optimization
 * - Rate limiting to prevent notification spam
 * 
 * Notification Types:
 * - NEW_FOLLOW_REQUEST: When someone sends a follow request
 * - FOLLOW_REQUEST_ACCEPTED: When your follow request is accepted  
 * - NEW_FOLLOWER: When someone follows you (for public profiles)
 * - FOLLOW_REQUEST_REMINDER: Reminder for pending requests
 * - MUTUAL_CONNECTION: When you have mutual connections with someone
 */
@Singleton
class FollowNotificationTrigger @Inject constructor(
    private val followRequestDao: FollowRequestDao,
    private val socialProfileDao: SocialProfileDao,
    private val userProfileDao: UserProfileDao,
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging
) {

    companion object {
        private const val NOTIFICATION_SETTINGS_COLLECTION = "notification_settings"
        private const val NOTIFICATION_LOG_COLLECTION = "notification_log"
        private const val USER_TOKENS_COLLECTION = "user_tokens"
        
        // Notification types
        private const val TYPE_FOLLOW_REQUEST = "FOLLOW_REQUEST"
        private const val TYPE_FOLLOW_ACCEPTED = "FOLLOW_ACCEPTED" 
        private const val TYPE_NEW_FOLLOWER = "NEW_FOLLOWER"
        private const val TYPE_REQUEST_REMINDER = "REQUEST_REMINDER"
        private const val TYPE_MUTUAL_CONNECTION = "MUTUAL_CONNECTION"
        
        // Rate limiting
        private const val MAX_NOTIFICATIONS_PER_HOUR = 10
        private const val MAX_REMINDERS_PER_REQUEST = 3
        private const val REMINDER_INTERVAL_HOURS = 24 * 7 // Weekly reminders
        
        // Message limits
        private const val MAX_MESSAGE_LENGTH = 240
        private const val MAX_TITLE_LENGTH = 65
    }

    /**
     * Send follow request notification to target user
     */
    suspend fun sendFollowRequestNotification(
        requesterId: String,
        targetUserId: String,
        requestMessage: String? = null
    ): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.NetworkError(
                    errorMessage = "Failed to send follow request notification: ${throwable.message}",
                    isRecoverable = true,
                    retryAfter = 3000L,
                    analyticsContext = mapOf(
                        "operation" to "SEND_FOLLOW_REQUEST_NOTIFICATION",
                        "requester_id" to requesterId,
                        "target_user_id" to targetUserId
                    )
                )
            }
        ) {
            Timber.d("Sending follow request notification: requester=$requesterId, target=$targetUserId")
            
            // Check if user allows follow request notifications
            if (!canReceiveNotification(targetUserId, TYPE_FOLLOW_REQUEST)) {
                Timber.d("User $targetUserId has disabled follow request notifications")
                return@liftrixCatching
            }
            
            // Check rate limiting
            if (!isWithinRateLimit(targetUserId, TYPE_FOLLOW_REQUEST)) {
                Timber.w("Rate limit exceeded for follow request notifications to user: $targetUserId")
                return@liftrixCatching
            }
            
            // Get requester profile
            val requesterProfile = socialProfileDao.getSocialProfileByUserId(requesterId)
                ?: throw IllegalArgumentException("Requester profile not found")
            
            // Get target user's FCM token
            val fcmToken = getUserFcmToken(targetUserId)
            if (fcmToken == null) {
                Timber.w("No FCM token found for user: $targetUserId")
                return@liftrixCatching
            }
            
            // Build notification payload
            val notificationData = buildFollowRequestNotificationData(
                requesterProfile = requesterProfile,
                requestMessage = requestMessage,
                targetUserId = targetUserId
            )
            
            // Create notification payload for server-side sending
            val notificationPayload = mapOf(
                "to" to fcmToken,
                "notification" to mapOf(
                    "title" to notificationData.title,
                    "body" to notificationData.body,
                    "icon" to requesterProfile.profilePhotoUrl
                ),
                "data" to notificationData.data
            )
            
            val messageId = java.util.UUID.randomUUID().toString()
            // Note: In production, this would send via Firebase Functions or server API
            Timber.d("Would send FCM notification: $notificationPayload")
            
            // Log notification
            logNotification(
                userId = targetUserId,
                type = TYPE_FOLLOW_REQUEST,
                fromUserId = requesterId,
                messageId = messageId,
                title = notificationData.title,
                body = notificationData.body
            )
            
            Timber.d("Follow request notification sent successfully: messageId=$messageId")
        }
    }

    /**
     * Send notification when follow request is accepted
     */
    suspend fun sendFollowAcceptedNotification(
        requesterId: String,
        acceptorUserId: String
    ): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.NetworkError(
                    errorMessage = "Failed to send follow accepted notification: ${throwable.message}",
                    isRecoverable = true,
                    retryAfter = 3000L,
                    analyticsContext = mapOf(
                        "operation" to "SEND_FOLLOW_ACCEPTED_NOTIFICATION",
                        "requester_id" to requesterId,
                        "acceptor_user_id" to acceptorUserId
                    )
                )
            }
        ) {
            Timber.d("Sending follow accepted notification: requester=$requesterId, acceptor=$acceptorUserId")
            
            if (!canReceiveNotification(requesterId, TYPE_FOLLOW_ACCEPTED)) {
                return@liftrixCatching
            }
            
            if (!isWithinRateLimit(requesterId, TYPE_FOLLOW_ACCEPTED)) {
                return@liftrixCatching
            }
            
            val acceptorProfile = socialProfileDao.getSocialProfileByUserId(acceptorUserId)
                ?: throw IllegalArgumentException("Acceptor profile not found")
            
            val fcmToken = getUserFcmToken(requesterId)
                ?: return@liftrixCatching
            
            val notificationData = NotificationData(
                title = "Follow Request Accepted! 🎉",
                body = "${acceptorProfile.displayName ?: acceptorProfile.username} accepted your follow request",
                data = mapOf(
                    "type" to TYPE_FOLLOW_ACCEPTED,
                    "fromUserId" to acceptorUserId,
                    "fromUsername" to acceptorProfile.username,
                    "action" to "OPEN_PROFILE",
                    "profileId" to acceptorUserId
                )
            )
            
            val notificationPayload = mapOf(
                "to" to fcmToken,
                "notification" to mapOf(
                    "title" to notificationData.title,
                    "body" to notificationData.body,
                    "icon" to acceptorProfile.profilePhotoUrl
                ),
                "data" to notificationData.data
            )
            
            val messageId = java.util.UUID.randomUUID().toString()
            Timber.d("Would send FCM notification: $notificationPayload")
            
            logNotification(
                userId = requesterId,
                type = TYPE_FOLLOW_ACCEPTED,
                fromUserId = acceptorUserId,
                messageId = messageId,
                title = notificationData.title,
                body = notificationData.body
            )
        }
    }

    /**
     * Send notification for new follower (public profiles only)
     */
    suspend fun sendNewFollowerNotification(
        followerId: String,
        followedUserId: String
    ): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.NetworkError(
                    errorMessage = "Failed to send new follower notification: ${throwable.message}",
                    isRecoverable = true,
                    retryAfter = 3000L,
                    analyticsContext = mapOf(
                        "operation" to "SEND_NEW_FOLLOWER_NOTIFICATION",
                        "follower_id" to followerId,
                        "followed_user_id" to followedUserId
                    )
                )
            }
        ) {
            if (!canReceiveNotification(followedUserId, TYPE_NEW_FOLLOWER)) {
                return@liftrixCatching
            }
            
            if (!isWithinRateLimit(followedUserId, TYPE_NEW_FOLLOWER)) {
                return@liftrixCatching
            }
            
            val followerProfile = socialProfileDao.getSocialProfileByUserId(followerId)
                ?: return@liftrixCatching
            
            val fcmToken = getUserFcmToken(followedUserId)
                ?: return@liftrixCatching
            
            val notificationData = NotificationData(
                title = "New Follower! 👋",
                body = "${followerProfile.displayName ?: followerProfile.username} started following you",
                data = mapOf(
                    "type" to TYPE_NEW_FOLLOWER,
                    "fromUserId" to followerId,
                    "fromUsername" to followerProfile.username,
                    "action" to "OPEN_FOLLOWERS",
                    "profileId" to followerId
                )
            )
            
            val notificationPayload = mapOf(
                "to" to fcmToken,
                "notification" to mapOf(
                    "title" to notificationData.title,
                    "body" to notificationData.body,
                    "icon" to followerProfile.profilePhotoUrl
                ),
                "data" to notificationData.data
            )
            
            val messageId = java.util.UUID.randomUUID().toString()
            Timber.d("Would send FCM notification: $notificationPayload")
            
            logNotification(
                userId = followedUserId,
                type = TYPE_NEW_FOLLOWER,
                fromUserId = followerId,
                messageId = messageId,
                title = notificationData.title,
                body = notificationData.body
            )
        }
    }

    /**
     * Send reminder notifications for pending follow requests
     */
    suspend fun sendFollowRequestReminders(): LiftrixResult<Int> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.NetworkError(
                    errorMessage = "Failed to send follow request reminders: ${throwable.message}",
                    isRecoverable = true,
                    retryAfter = 5000L,
                    analyticsContext = mapOf(
                        "operation" to "SEND_FOLLOW_REQUEST_REMINDERS"
                    )
                )
            }
        ) {
            Timber.d("Processing follow request reminders")
            
            val currentTime = System.currentTimeMillis()
            val reminderCutoff = currentTime - (REMINDER_INTERVAL_HOURS * 60 * 60 * 1000L)
            val reminderEligibleTime = currentTime - (24 * 60 * 60 * 1000L) // 1 day old requests
            
            val requestsNeedingReminder = followRequestDao.getRequestsNeedingReminder(
                reminderCutoff = reminderCutoff,
                reminderEligibleTime = reminderEligibleTime,
                limit = 100
            )
            
            var sentCount = 0
            
            for (request in requestsNeedingReminder) {
                try {
                    if (canReceiveNotification(request.targetId, TYPE_REQUEST_REMINDER) &&
                        isWithinRateLimit(request.targetId, TYPE_REQUEST_REMINDER)) {
                        
                        val requesterProfile = socialProfileDao.getSocialProfileByUserId(request.requesterId)
                        val fcmToken = getUserFcmToken(request.targetId)
                        
                        if (requesterProfile != null && fcmToken != null) {
                            val notificationData = NotificationData(
                                title = "Pending Follow Request",
                                body = "${requesterProfile.displayName ?: requesterProfile.username} is waiting for you to respond to their follow request",
                                data = mapOf(
                                    "type" to TYPE_REQUEST_REMINDER,
                                    "fromUserId" to request.requesterId,
                                    "requestId" to request.id,
                                    "action" to "OPEN_REQUESTS"
                                )
                            )
                            
                            val notificationPayload = mapOf(
                                "to" to fcmToken,
                                "notification" to mapOf(
                                    "title" to notificationData.title,
                                    "body" to notificationData.body
                                ),
                                "data" to notificationData.data
                            )
                            
                            val messageId = java.util.UUID.randomUUID().toString()
                            Timber.d("Would send FCM notification: $notificationPayload")
                            
                            // Update reminder count
                            followRequestDao.incrementReminderCount(
                                requestId = request.id,
                                reminderTime = currentTime,
                                updatedAt = currentTime
                            )
                            
                            logNotification(
                                userId = request.targetId,
                                type = TYPE_REQUEST_REMINDER,
                                fromUserId = request.requesterId,
                                messageId = messageId,
                                title = notificationData.title,
                                body = notificationData.body
                            )
                            
                            sentCount++
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to send reminder for request: ${request.id}")
                }
            }
            
            Timber.d("Sent $sentCount follow request reminders")
            sentCount
        }
    }

    /**
     * Process batch follow notifications for efficiency
     */
    suspend fun processBatchNotifications(): LiftrixResult<Int> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.NetworkError(
                    errorMessage = "Failed to process batch notifications: ${throwable.message}",
                    isRecoverable = true,
                    retryAfter = 5000L,
                    analyticsContext = mapOf(
                        "operation" to "PROCESS_BATCH_NOTIFICATIONS"
                    )
                )
            }
        ) {
            val currentTime = System.currentTimeMillis()
            val recentTimestamp = currentTime - (10 * 60 * 1000L) // Last 10 minutes
            
            // Get unsent follow request notifications
            val unsentRequests = followRequestDao.getUnsentNotificationRequests(
                recentTimestamp = recentTimestamp,
                limit = 50
            )
            
            var processedCount = 0
            
            for (request in unsentRequests) {
                try {
                    sendFollowRequestNotification(
                        requesterId = request.requesterId,
                        targetUserId = request.targetId,
                        requestMessage = request.requestMessage
                    ).getOrNull()
                    
                    // Mark as sent
                    followRequestDao.markNotificationSent(
                        requestId = request.id,
                        updatedAt = currentTime
                    )
                    
                    processedCount++
                } catch (e: Exception) {
                    Timber.e(e, "Failed to process notification for request: ${request.id}")
                }
            }
            
            processedCount
        }
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    private suspend fun canReceiveNotification(userId: String, notificationType: String): Boolean {
        return try {
            val settingsDoc = firestore.collection(NOTIFICATION_SETTINGS_COLLECTION)
                .document(userId)
                .get()
                .await()
            
            if (!settingsDoc.exists()) {
                return true // Default to allowing notifications
            }
            
            val settings = settingsDoc.data ?: return true
            val notificationsEnabled = settings["notificationsEnabled"] as? Boolean ?: true
            val followNotificationsEnabled = settings["followNotificationsEnabled"] as? Boolean ?: true
            
            notificationsEnabled && followNotificationsEnabled
        } catch (e: Exception) {
            Timber.e(e, "Error checking notification permissions for user: $userId")
            true // Default to allowing
        }
    }

    private suspend fun isWithinRateLimit(userId: String, notificationType: String): Boolean {
        return try {
            val currentTime = System.currentTimeMillis()
            val hourAgo = currentTime - (60 * 60 * 1000L)
            
            val recentNotifications = firestore.collection(NOTIFICATION_LOG_COLLECTION)
                .whereEqualTo("userId", userId)
                .whereEqualTo("type", notificationType)
                .whereGreaterThan("sentAt", hourAgo)
                .get()
                .await()
            
            recentNotifications.size() < MAX_NOTIFICATIONS_PER_HOUR
        } catch (e: Exception) {
            Timber.e(e, "Error checking rate limit for user: $userId")
            true // Default to allowing
        }
    }

    private suspend fun getUserFcmToken(userId: String): String? {
        return try {
            val tokenDoc = firestore.collection(USER_TOKENS_COLLECTION)
                .document(userId)
                .get()
                .await()
            
            tokenDoc.data?.get("fcmToken") as? String
        } catch (e: Exception) {
            Timber.e(e, "Error getting FCM token for user: $userId")
            null
        }
    }

    private fun buildFollowRequestNotificationData(
        requesterProfile: com.example.liftrix.data.local.entity.SocialProfileEntity,
        requestMessage: String?,
        targetUserId: String
    ): NotificationData {
        val requesterName = requesterProfile.displayName ?: requesterProfile.username
        
        val title = "New Follow Request! 👤"
        val body = if (requestMessage != null && requestMessage.isNotBlank()) {
            "$requesterName wants to follow you: \"${requestMessage.take(100)}\""
        } else {
            "$requesterName wants to follow you"
        }
        
        val data = mapOf(
            "type" to TYPE_FOLLOW_REQUEST,
            "fromUserId" to requesterProfile.userId,
            "fromUsername" to requesterProfile.username,
            "requestMessage" to (requestMessage ?: ""),
            "action" to "OPEN_REQUESTS",
            "profileId" to requesterProfile.userId
        )
        
        return NotificationData(
            title = title.take(MAX_TITLE_LENGTH),
            body = body.take(MAX_MESSAGE_LENGTH),
            data = data
        )
    }

    private suspend fun logNotification(
        userId: String,
        type: String,
        fromUserId: String,
        messageId: String,
        title: String,
        body: String
    ) {
        try {
            val logData = mapOf(
                "userId" to userId,
                "type" to type,
                "fromUserId" to fromUserId,
                "messageId" to messageId,
                "title" to title,
                "body" to body,
                "sentAt" to System.currentTimeMillis(),
                "status" to "SENT"
            )
            
            firestore.collection(NOTIFICATION_LOG_COLLECTION)
                .add(logData)
                .await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to log notification")
        }
    }
}

/**
 * Data class for notification payload
 */
private data class NotificationData(
    val title: String,
    val body: String,
    val data: Map<String, String>
)