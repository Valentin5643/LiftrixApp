package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.AppNotification
import com.example.liftrix.domain.model.common.LiftrixResult

/**
 * Service interface for intelligent notification routing with batching and priority handling.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 */
interface NotificationRouter {

    /**
     * Routes a notification to the appropriate delivery mechanism based on user preferences,
     * priority level, quiet hours, and batching rules.
     */
    suspend fun route(
        notification: AppNotification,
        targetUserId: String
    ): LiftrixResult<RoutingResult>

    /**
     * Routes multiple notifications as a batch, applying intelligent grouping
     */
    suspend fun routeBatch(
        notifications: List<AppNotification>,
        targetUserId: String
    ): LiftrixResult<List<RoutingResult>>

    /**
     * Processes pending notifications in the queue for immediate delivery
     */
    suspend fun processQueue(userId: String): LiftrixResult<ProcessingResult>

    /**
     * Processes all pending batches that are ready for delivery
     */
    suspend fun processPendingBatches(userId: String): LiftrixResult<ProcessingResult>

    /**
     * Schedules a notification for later delivery (respects quiet hours)
     */
    suspend fun scheduleForLater(
        notification: AppNotification,
        targetUserId: String,
        deliveryTime: Long
    ): LiftrixResult<Unit>

    /**
     * Cancels pending notifications of a specific type
     */
    suspend fun cancelNotificationsByType(
        userId: String,
        notificationType: String
    ): LiftrixResult<Int>

    /**
     * Gets routing statistics for analytics
     */
    suspend fun getRoutingStatistics(userId: String): LiftrixResult<RoutingStatistics>

    data class RoutingResult(
        val action: RoutingAction,
        val reason: String,
        val scheduledFor: Long? = null,
        val batchKey: String? = null
    )

    enum class RoutingAction {
        DELIVERED_IMMEDIATELY,
        QUEUED_FOR_BATCHING,
        SCHEDULED_FOR_LATER,
        BLOCKED_BY_PREFERENCES,
        BLOCKED_BY_QUIET_HOURS,
        BLOCKED_BY_PRIVACY,
        FAILED_TO_DELIVER
    }

    data class ProcessingResult(
        val processed: Int,
        val delivered: Int,
        val queued: Int,
        val failed: Int,
        val errors: List<String> = emptyList()
    )

    data class RoutingStatistics(
        val totalRouted: Int,
        val immediateDeliveries: Int,
        val batchedNotifications: Int,
        val scheduledNotifications: Int,
        val blockedByPreferences: Int,
        val blockedByQuietHours: Int,
        val blockedByPrivacy: Int,
        val averageProcessingTimeMs: Long,
        val lastProcessedAt: Long?
    )
}