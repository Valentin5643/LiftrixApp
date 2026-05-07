package com.example.liftrix.domain.service.notifications

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.common.liftrixSuccess
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.notifications.NotificationQueue
import com.example.liftrix.domain.model.notifications.QueuedNotification
import com.example.liftrix.domain.repository.notifications.NotificationQueueRepository
import kotlinx.coroutines.flow.first

/**
 * Service for processing batched notifications
 */
class NotificationBatchProcessor(
    private val queueRepository: NotificationQueueRepository,
    private val fcmSender: FCMSender,
    private val notificationBuilder: BatchNotificationBuilder
) {
    
    /**
     * Process all pending notifications for a user
     */
    suspend fun processBatch(userId: String): LiftrixResult<Unit> {
        return try {
            val pendingResult = queueRepository.getPending(userId).first()
            
            pendingResult.fold(
                onSuccess = { notifications ->
                    if (notifications.isEmpty()) {
                        return liftrixSuccess(Unit)
                    }
                    
                    // Group notifications by batch key
                    val groupedNotifications = notifications.groupBy { it.batchKey }
                    
                    var hasError = false
                    var lastError: Throwable? = null
                    
                    for ((batchKey, batchNotifications) in groupedNotifications) {
                        try {
                            val result = processBatchGroup(userId, batchNotifications)
                            result.fold(
                                onSuccess = {
                                    // Mark all notifications in this batch as sent
                                    batchNotifications.forEach { notification ->
                                        val markSentResult = queueRepository.markSent(notification.id).first()
                                        markSentResult.fold(
                                            onSuccess = { /* Success - continue */ },
                                            onFailure = { error ->
                                                hasError = true
                                                lastError = error
                                            }
                                        )
                                    }
                                },
                                onFailure = { error ->
                                    hasError = true
                                    lastError = error
                                }
                            )
                        } catch (e: Exception) {
                            hasError = true
                            lastError = e
                        }
                    }
                    
                    if (hasError) {
                        val finalError = lastError  // Create local copy to avoid smart cast issues
                        liftrixFailure(
                            LiftrixError.BusinessLogicError(
                                code = "BATCH_PROCESSING_FAILED",
                                errorMessage = finalError?.message ?: "Batch processing failed"
                            )
                        )
                    } else {
                        liftrixSuccess(Unit)
                    }
                },
                onFailure = { error ->
                    liftrixFailure(error as? LiftrixError ?: LiftrixError.BusinessLogicError(
                        code = "BATCH_PROCESSING_ERROR",
                        errorMessage = error.message ?: "Error retrieving pending notifications"
                    ))
                }
            )
        } catch (e: Exception) {
            liftrixFailure(
                LiftrixError.BusinessLogicError(
                    code = "BATCH_PROCESSING_ERROR",
                    errorMessage = e.message ?: "Unknown error processing batch"
                )
            )
        }
    }
    
    private suspend fun processBatchGroup(userId: String, notifications: List<QueuedNotification>): LiftrixResult<Unit> {
        return when (notifications.size) {
            1 -> {
                // Send single notification
                fcmSender.sendSingle(notifications.first())
            }
            in 2..4 -> {
                // Send inbox style notification
                val inboxResult = notificationBuilder.buildInboxStyleNotification(notifications, userId)
                inboxResult.fold(
                    onSuccess = { notificationQueue ->
                        fcmSender.sendToUser(userId, notificationQueue)
                    },
                    onFailure = { error ->
                        liftrixFailure(error as? LiftrixError ?: LiftrixError.BusinessLogicError(
                            code = "INBOX_NOTIFICATION_FAILED",
                            errorMessage = error.message ?: "Failed to build inbox notification"
                        ))
                    }
                )
            }
            else -> {
                // Send summary notification
                val summaryResult = notificationBuilder.buildSummaryNotification(notifications, userId)
                summaryResult.fold(
                    onSuccess = { notificationQueue ->
                        fcmSender.sendToUser(userId, notificationQueue)
                    },
                    onFailure = { error ->
                        liftrixFailure(error as? LiftrixError ?: LiftrixError.BusinessLogicError(
                            code = "SUMMARY_NOTIFICATION_FAILED",
                            errorMessage = error.message ?: "Failed to build summary notification"
                        ))
                    }
                )
            }
        }
    }
}