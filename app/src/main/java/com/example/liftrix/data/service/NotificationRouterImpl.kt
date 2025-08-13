package com.example.liftrix.data.service

import com.example.liftrix.data.local.dao.NotificationPreferenceDao
import com.example.liftrix.data.local.dao.NotificationQueueDao
import com.example.liftrix.data.local.entity.NotificationQueueEntity
import com.example.liftrix.domain.model.AppNotification
import com.example.liftrix.domain.service.NotificationRouter
import com.example.liftrix.domain.service.NotificationPrivacyFilter
import com.example.liftrix.domain.service.BatchProcessor
import com.example.liftrix.domain.service.FCMSender
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of NotificationRouter with intelligent routing, batching, and priority handling.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 */
@Singleton
class NotificationRouterImpl @Inject constructor(
    private val preferenceDao: NotificationPreferenceDao,
    private val queueDao: NotificationQueueDao,
    private val privacyFilter: NotificationPrivacyFilter,
    private val batchProcessor: BatchProcessor,
    private val fcmSender: FCMSender
) : NotificationRouter {

    companion object {
        private const val BATCH_WINDOW_DEFAULT_MINUTES = 60L
        private const val HIGH_PRIORITY_IMMEDIATE_DELIVERY = true
    }

    override suspend fun route(
        notification: AppNotification,
        targetUserId: String
    ): LiftrixResult<NotificationRouter.RoutingResult> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "ROUTE_NOTIFICATION_FAILED",
                    errorMessage = "Failed to route notification",
                    analyticsContext = mapOf(
                        "user_id" to targetUserId,
                        "notification_type" to notification.type.value
                    )
                )
            }
        ) {
            withContext(Dispatchers.IO) {
                // Step 1: Get user preferences
                val preferences = preferenceDao.getPreferences(targetUserId)
                if (preferences == null) {
                    return@withContext NotificationRouter.RoutingResult(
                        action = NotificationRouter.RoutingAction.BLOCKED_BY_PREFERENCES,
                        reason = "No notification preferences found for user"
                    )
                }

                // Step 2: Check if notifications are enabled
                if (!preferences.notificationsEnabled) {
                    return@withContext NotificationRouter.RoutingResult(
                        action = NotificationRouter.RoutingAction.BLOCKED_BY_PREFERENCES,
                        reason = "Notifications disabled by user"
                    )
                }

                // Step 3: Apply privacy filters
                val privacyResult = privacyFilter.canSendNotification(
                    notification = notification,
                    fromUserId = notification.fromUserId,
                    toUserId = targetUserId
                )

                val privacyAllowed = privacyResult.fold(
                    onSuccess = { allowed -> allowed },
                    onFailure = { error -> 
                        Timber.w("Privacy filter error: $error")
                        false
                    }
                )
                
                if (!privacyAllowed) {
                    return@withContext NotificationRouter.RoutingResult(
                        action = NotificationRouter.RoutingAction.BLOCKED_BY_PRIVACY,
                        reason = "Privacy rules blocked notification"
                    )
                }

                // Step 4: Check category-specific preferences
                if (!isCategoryEnabled(notification, preferences)) {
                    return@withContext NotificationRouter.RoutingResult(
                        action = NotificationRouter.RoutingAction.BLOCKED_BY_PREFERENCES,
                        reason = "Notification category disabled by user"
                    )
                }

                // Step 5: Check quiet hours
                val quietHoursResult = checkQuietHours(preferences)
                if (quietHoursResult.isInQuietHours && !notification.shouldDeliverImmediately()) {
                    val deliveryTime = quietHoursResult.nextDeliveryTime ?: (System.currentTimeMillis() + 60000) // Default to 1 minute later
                    scheduleForLater(notification, targetUserId, deliveryTime)
                    return@withContext NotificationRouter.RoutingResult(
                        action = NotificationRouter.RoutingAction.SCHEDULED_FOR_LATER,
                        reason = "Scheduled due to quiet hours",
                        scheduledFor = deliveryTime
                    )
                }

                // Step 6: Determine delivery method based on priority and batching preferences
                when {
                    notification.shouldDeliverImmediately() -> {
                        // High priority - deliver immediately
                        val deliveryResult = fcmSender.sendToUser(targetUserId, notification)
                        deliveryResult.fold(
                            onSuccess = { 
                                NotificationRouter.RoutingResult(
                                    action = NotificationRouter.RoutingAction.DELIVERED_IMMEDIATELY,
                                    reason = "High priority notification delivered immediately"
                                )
                            },
                            onFailure = { error ->
                                Timber.e("Failed to deliver high priority notification: $error")
                                NotificationRouter.RoutingResult(
                                    action = NotificationRouter.RoutingAction.FAILED_TO_DELIVER,
                                    reason = "Failed to deliver high priority notification"
                                )
                            }
                        )
                    }
                    
                    preferences.batchSocialNotifications && notification.isBatchable() -> {
                        // Queue for batching
                        val batchKey = notification.generateBatchKey()
                        queueForBatching(notification, targetUserId, batchKey, preferences.batchWindowMinutes)
                        NotificationRouter.RoutingResult(
                            action = NotificationRouter.RoutingAction.QUEUED_FOR_BATCHING,
                            reason = "Queued for batch delivery",
                            batchKey = batchKey
                        )
                    }
                    
                    else -> {
                        // Send with normal priority
                        val deliveryResult = fcmSender.sendToUser(targetUserId, notification)
                        deliveryResult.fold(
                            onSuccess = {
                                NotificationRouter.RoutingResult(
                                    action = NotificationRouter.RoutingAction.DELIVERED_IMMEDIATELY,
                                    reason = "Normal priority notification delivered"
                                )
                            },
                            onFailure = { error ->
                                Timber.e("Failed to deliver normal priority notification: $error")
                                NotificationRouter.RoutingResult(
                                    action = NotificationRouter.RoutingAction.FAILED_TO_DELIVER,
                                    reason = "Failed to deliver normal priority notification"
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    override suspend fun routeBatch(
        notifications: List<AppNotification>,
        targetUserId: String
    ): LiftrixResult<List<NotificationRouter.RoutingResult>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "ROUTE_BATCH_FAILED",
                    errorMessage = "Failed to route notification batch",
                    analyticsContext = mapOf(
                        "user_id" to targetUserId,
                        "notification_count" to notifications.size.toString()
                    )
                )
            }
        ) {
            notifications.map { notification ->
                route(notification, targetUserId).fold(
                    onSuccess = { it },
                    onFailure = { 
                        NotificationRouter.RoutingResult(
                            action = NotificationRouter.RoutingAction.FAILED_TO_DELIVER,
                            reason = "Failed to route notification in batch"
                        )
                    }
                )
            }
        }
    }

    override suspend fun processQueue(userId: String): LiftrixResult<NotificationRouter.ProcessingResult> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "PROCESS_QUEUE_FAILED",
                    errorMessage = "Failed to process notification queue",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val currentTime = System.currentTimeMillis()
            
            // Get ready notifications (scheduled time has passed)
            val readyNotifications = queueDao.getReadyNotifications(userId, currentTime)
            
            var processed = 0
            var delivered = 0
            var queued = 0
            var failed = 0
            val errors = mutableListOf<String>()
            
            readyNotifications.forEach { queuedNotification ->
                try {
                    val notification = mapToAppNotification(queuedNotification)
                    val deliveryResult = fcmSender.sendToUser(userId, notification)
                    
                    deliveryResult.fold(
                        onSuccess = {
                            queueDao.updateStatus(userId, queuedNotification.id, "SENT", currentTime)
                            delivered++
                        },
                        onFailure = { error ->
                            queueDao.markNotificationFailed(
                                userId, 
                                queuedNotification.id, 
                                "Delivery failed: $error"
                            )
                            failed++
                            errors.add("Failed to deliver notification ${queuedNotification.id}: $error")
                        }
                    )
                    processed++
                } catch (e: Exception) {
                    failed++
                    errors.add("Error processing notification ${queuedNotification.id}: ${e.message}")
                    Timber.e(e, "Error processing queued notification")
                }
            }
            
            // Mark expired notifications
            val expiredCount = queueDao.markExpiredNotifications(userId, currentTime)
            if (expiredCount > 0) {
                Timber.d("Marked $expiredCount expired notifications for user $userId")
            }
            
            NotificationRouter.ProcessingResult(
                processed = processed,
                delivered = delivered,
                queued = queued,
                failed = failed,
                errors = errors
            )
        }
    }

    override suspend fun processPendingBatches(userId: String): LiftrixResult<NotificationRouter.ProcessingResult> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "PROCESS_BATCHES_FAILED",
                    errorMessage = "Failed to process pending batches",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val batchResult = batchProcessor.processBatch(userId)
            batchResult.fold(
                onSuccess = { result ->
                    NotificationRouter.ProcessingResult(
                        processed = result.processed,
                        delivered = result.delivered,
                        queued = result.queued,
                        failed = result.failed,
                        errors = result.errors
                    )
                },
                onFailure = { error -> throw error }
            )
        }
    }

    override suspend fun scheduleForLater(
        notification: AppNotification,
        targetUserId: String,
        deliveryTime: Long
    ): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "SCHEDULE_NOTIFICATION_FAILED",
                    errorMessage = "Failed to schedule notification",
                    analyticsContext = mapOf(
                        "user_id" to targetUserId,
                        "delivery_time" to deliveryTime.toString()
                    )
                )
            }
        ) {
            val queueEntity = NotificationQueueEntity(
                id = UUID.randomUUID().toString(),
                userId = targetUserId,
                type = notification.type.value,
                title = notification.title,
                body = notification.body,
                data = notification.data.entries.joinToString(",") { "${it.key}=${it.value}" },
                priority = notification.priority.value,
                channelId = notification.channelId,
                batchKey = notification.batchKey,
                canBatch = notification.canBatch,
                scheduledFor = deliveryTime,
                expiresAt = notification.expiresAt,
                status = "PENDING",
                sentAt = null,
                failureReason = null,
                createdAt = System.currentTimeMillis()
            )
            
            queueDao.insertNotification(queueEntity)
        }
    }

    override suspend fun cancelNotificationsByType(
        userId: String,
        notificationType: String
    ): LiftrixResult<Int> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "CANCEL_NOTIFICATIONS_FAILED",
                    errorMessage = "Failed to cancel notifications",
                    analyticsContext = mapOf(
                        "user_id" to userId,
                        "notification_type" to notificationType
                    )
                )
            }
        ) {
            queueDao.cancelPendingNotificationsByType(userId, notificationType)
        }
    }

    override suspend fun getRoutingStatistics(userId: String): LiftrixResult<NotificationRouter.RoutingStatistics> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "GET_STATISTICS_FAILED",
                    errorMessage = "Failed to get routing statistics",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val statusCounts = queueDao.getNotificationCountsByStatus(userId)
            val averageProcessingTime = queueDao.getAverageProcessingTime(userId) ?: 0L
            val pendingCount = queueDao.getPendingNotificationCount(userId)
            
            NotificationRouter.RoutingStatistics(
                totalRouted = statusCounts.values.sum(),
                immediateDeliveries = statusCounts["SENT"] ?: 0,
                batchedNotifications = pendingCount,
                scheduledNotifications = statusCounts["PENDING"] ?: 0,
                blockedByPreferences = 0, // Would need additional tracking
                blockedByQuietHours = 0, // Would need additional tracking  
                blockedByPrivacy = 0, // Would need additional tracking
                averageProcessingTimeMs = averageProcessingTime,
                lastProcessedAt = System.currentTimeMillis()
            )
        }
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    private suspend fun queueForBatching(
        notification: AppNotification,
        userId: String,
        batchKey: String,
        batchWindowMinutes: Int
    ) {
        val batchWindow = batchWindowMinutes * 60 * 1000L // Convert to milliseconds
        val scheduledFor = System.currentTimeMillis() + batchWindow
        
        val queueEntity = NotificationQueueEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            type = notification.type.value,
            title = notification.title,
            body = notification.body,
            data = notification.data.entries.joinToString(",") { "${it.key}=${it.value}" },
            priority = notification.priority.value,
            channelId = notification.channelId,
            batchKey = batchKey,
            canBatch = true,
            scheduledFor = scheduledFor,
            expiresAt = notification.expiresAt,
            status = "PENDING",
            sentAt = null,
            failureReason = null,
            createdAt = System.currentTimeMillis()
        )
        
        queueDao.insertNotification(queueEntity)
        
        // Schedule batch processing
        batchProcessor.scheduleProcessing(userId, batchWindow)
    }

    private fun isCategoryEnabled(
        notification: AppNotification,
        preferences: com.example.liftrix.data.local.entity.NotificationPreferenceEntity
    ): Boolean {
        return when (notification.category) {
            AppNotification.NotificationCategory.WORKOUT -> preferences.workoutNotifications
            AppNotification.NotificationCategory.SOCIAL -> {
                when (notification.type) {
                    AppNotification.NotificationType.GYM_BUDDY_PR -> preferences.gymBuddyPrs
                    AppNotification.NotificationType.FOLLOW_REQUEST -> preferences.followRequests
                    AppNotification.NotificationType.POST_LIKE -> preferences.postLikes
                    AppNotification.NotificationType.POST_COMMENT -> preferences.postComments
                    AppNotification.NotificationType.SOCIAL_MENTION -> preferences.mentions
                    else -> preferences.socialNotifications
                }
            }
            AppNotification.NotificationCategory.ACHIEVEMENT -> preferences.achievementNotifications
            AppNotification.NotificationCategory.REMINDER -> preferences.reminderNotifications
            AppNotification.NotificationCategory.SYSTEM -> true // System notifications always enabled
        }
    }

    private fun checkQuietHours(
        preferences: com.example.liftrix.data.local.entity.NotificationPreferenceEntity
    ): QuietHoursResult {
        if (!preferences.quietHoursEnabled) {
            return QuietHoursResult(false, null)
        }

        val currentTime = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = currentTime
        }
        
        val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val startHour = preferences.quietHoursStart
        val endHour = preferences.quietHoursEnd
        
        val isInQuietHours = if (startHour < endHour) {
            // Same day quiet hours (e.g., 10 PM to 6 AM next day)
            currentHour >= startHour && currentHour < endHour
        } else {
            // Cross-midnight quiet hours (e.g., 10 PM to 6 AM next day)
            currentHour >= startHour || currentHour < endHour
        }
        
        val nextDeliveryTime = if (isInQuietHours) {
            // Calculate next delivery time (end of quiet hours)
            val nextDeliveryCalendar = calendar.clone() as java.util.Calendar
            if (currentHour >= startHour) {
                // Next day
                nextDeliveryCalendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            nextDeliveryCalendar.set(java.util.Calendar.HOUR_OF_DAY, endHour)
            nextDeliveryCalendar.set(java.util.Calendar.MINUTE, 0)
            nextDeliveryCalendar.set(java.util.Calendar.SECOND, 0)
            nextDeliveryCalendar.timeInMillis
        } else {
            null
        }
        
        return QuietHoursResult(isInQuietHours, nextDeliveryTime)
    }

    private fun mapToAppNotification(entity: NotificationQueueEntity): AppNotification {
        val dataMap = if (entity.data.isNullOrEmpty()) {
            emptyMap()
        } else {
            entity.data.split(",").associate { pair ->
                val (key, value) = pair.split("=", limit = 2)
                key to value
            }
        }
        
        return AppNotification(
            id = entity.id,
            type = AppNotification.NotificationType.fromString(entity.type),
            title = entity.title,
            body = entity.body,
            data = dataMap,
            priority = AppNotification.Priority.fromString(entity.priority),
            category = AppNotification.NotificationCategory.fromString(entity.type),
            channelId = entity.channelId,
            canBatch = entity.canBatch,
            batchKey = entity.batchKey,
            expiresAt = entity.expiresAt,
            deliverAfter = entity.scheduledFor,
            createdAt = entity.createdAt
        )
    }

    private data class QuietHoursResult(
        val isInQuietHours: Boolean,
        val nextDeliveryTime: Long?
    )
}