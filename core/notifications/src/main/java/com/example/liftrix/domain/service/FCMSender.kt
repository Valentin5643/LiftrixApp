package com.example.liftrix.domain.service

import android.app.Notification
import com.example.liftrix.domain.model.AppNotification
import com.example.liftrix.domain.model.common.LiftrixResult

/**
 * Service interface for sending FCM notifications.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 */
interface FCMSender {

    /**
     * Sends a notification to a specific user via FCM
     */
    suspend fun sendToUser(
        userId: String,
        notification: AppNotification
    ): LiftrixResult<SendResult>

    /**
     * Sends a notification to multiple users
     */
    suspend fun sendToUsers(
        userIds: List<String>,
        notification: AppNotification
    ): LiftrixResult<List<SendResult>>

    /**
     * Sends a system notification to a user
     */
    suspend fun sendToUser(
        userId: String,
        systemNotification: Notification
    ): LiftrixResult<SendResult>

    /**
     * Sends a data-only message (no notification UI)
     */
    suspend fun sendDataMessage(
        userId: String,
        data: Map<String, String>
    ): LiftrixResult<SendResult>

    /**
     * Sends to a specific FCM token
     */
    suspend fun sendToToken(
        fcmToken: String,
        notification: AppNotification
    ): LiftrixResult<SendResult>

    /**
     * Sends a topic notification
     */
    suspend fun sendToTopic(
        topic: String,
        notification: AppNotification
    ): LiftrixResult<SendResult>

    /**
     * Gets delivery statistics
     */
    suspend fun getDeliveryStatistics(): LiftrixResult<DeliveryStatistics>

    data class SendResult(
        val success: Boolean,
        val messageId: String?,
        val error: String? = null,
        val retryable: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class DeliveryStatistics(
        val totalSent: Long,
        val successful: Long,
        val failed: Long,
        val retryable: Long,
        val averageDeliveryTimeMs: Long,
        val lastSentAt: Long?
    )
}