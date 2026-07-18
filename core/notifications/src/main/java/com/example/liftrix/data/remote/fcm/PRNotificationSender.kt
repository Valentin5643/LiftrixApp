package com.example.liftrix.data.remote.fcm

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.PersonalRecord
import com.example.liftrix.domain.service.PRType
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for sending PR notification FCM data messages to gym buddies
 * Handles rich notifications with workout data and celebration features
 */
@Singleton
class PRNotificationSender @Inject constructor(
    private val firebaseMessaging: FirebaseMessaging,
    private val json: Json
) {

    companion object {
        private const val NOTIFICATION_TYPE_GYM_BUDDY_PR = "GYM_BUDDY_PR"
        private const val NOTIFICATION_TYPE_GYM_BUDDY_WELCOME = "GYM_BUDDY_WELCOME"
        private const val HIGH_PRIORITY = "high"
        private const val MAX_TTL_SECONDS = 24 * 60 * 60
    }

    /**
     * Sends a PR notification to a gym buddy
     */
    suspend fun sendPRNotification(
        toToken: String,
        fromUserId: String,
        fromUsername: String,
        fromDisplayName: String,
        personalRecord: PersonalRecord,
        workoutId: String
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to send PR notification: ${throwable.message}",
                analyticsContext = mapOf(
                    "from_user_id" to fromUserId,
                    "pr_type" to personalRecord.prType.name,
                    "operation" to "SEND_PR_NOTIFICATION"
                )
            )
        }
    ) {
        Timber.d("Sending PR notification: $fromUserId -> FCM token")
        
        val prData = PRNotificationData(
            type = NOTIFICATION_TYPE_GYM_BUDDY_PR,
            fromUserId = fromUserId,
            fromUsername = fromUsername,
            fromDisplayName = fromDisplayName,
            workoutId = workoutId,
            exerciseName = personalRecord.exerciseName,
            prType = personalRecord.prType.name,
            weight = personalRecord.weight,
            reps = personalRecord.reps,
            estimatedOneRM = personalRecord.estimatedOneRM,
            volume = personalRecord.volume,
            previousBest = personalRecord.previousBest,
            improvementPercent = personalRecord.improvementPercent,
            timestamp = System.currentTimeMillis()
        )
        
        val message = RemoteMessage.Builder(toToken)
            .setData(mapOf(
                "type" to NOTIFICATION_TYPE_GYM_BUDDY_PR,
                "payload" to json.encodeToString(prData),
                "title" to createNotificationTitle(fromDisplayName, personalRecord),
                "body" to createNotificationBody(personalRecord),
                "click_action" to "OPEN_WORKOUT_DETAIL",
                "sound" to "pr_celebration.mp3"
            ))
            .setMessageId("pr_${fromUserId}_${System.currentTimeMillis()}")
            .setTtl(MAX_TTL_SECONDS)
            .build()
        
        firebaseMessaging.send(message)
        Timber.d("PR notification sent successfully")
    }

    /**
     * Sends a welcome notification when gym buddies connect
     */
    suspend fun sendGymBuddyWelcomeNotification(
        toToken: String,
        fromUserId: String,
        fromUsername: String,
        fromDisplayName: String,
        connectionMethod: String = "QR"
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to send welcome notification: ${throwable.message}",
                analyticsContext = mapOf(
                    "from_user_id" to fromUserId,
                    "connection_method" to connectionMethod,
                    "operation" to "SEND_WELCOME_NOTIFICATION"
                )
            )
        }
    ) {
        Timber.d("Sending gym buddy welcome notification: $fromUserId -> FCM token")
        
        val welcomeData = GymBuddyWelcomeData(
            type = NOTIFICATION_TYPE_GYM_BUDDY_WELCOME,
            fromUserId = fromUserId,
            fromUsername = fromUsername,
            fromDisplayName = fromDisplayName,
            connectionMethod = connectionMethod,
            timestamp = System.currentTimeMillis()
        )
        
        val message = RemoteMessage.Builder(toToken)
            .setData(mapOf(
                "type" to NOTIFICATION_TYPE_GYM_BUDDY_WELCOME,
                "payload" to json.encodeToString(welcomeData),
                "title" to "New Gym Buddy! 💪",
                "body" to "$fromDisplayName is now your gym buddy",
                "click_action" to "OPEN_GYM_BUDDIES",
                "sound" to "buddy_connect.mp3"
            ))
            .setMessageId("welcome_${fromUserId}_${System.currentTimeMillis()}")
            .setTtl(MAX_TTL_SECONDS)
            .build()
        
        firebaseMessaging.send(message)
        Timber.d("Welcome notification sent successfully")
    }

    /**
     * Sends a batch of PR notifications to multiple gym buddies
     */
    suspend fun sendBatchPRNotifications(
        notifications: List<BatchPRNotification>
    ): LiftrixResult<BatchNotificationResult> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to send batch PR notifications: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "SEND_BATCH_PR_NOTIFICATIONS"
                )
            )
        }
    ) {
        Timber.d("Sending batch of ${notifications.size} PR notifications")
        
        val results = mutableListOf<NotificationResult>()
        var successCount = 0
        var failureCount = 0
        
        for (notification in notifications) {
            try {
                sendPRNotification(
                    toToken = notification.toToken,
                    fromUserId = notification.fromUserId,
                    fromUsername = notification.fromUsername,
                    fromDisplayName = notification.fromDisplayName,
                    personalRecord = notification.personalRecord,
                    workoutId = notification.workoutId
                ).fold(
                    onSuccess = {
                        results.add(NotificationResult(
                            toToken = notification.toToken,
                            success = true,
                            error = null
                        ))
                        successCount++
                    },
                    onFailure = { error ->
                        results.add(NotificationResult(
                            toToken = notification.toToken,
                            success = false,
                            error = error.toString()
                        ))
                        failureCount++
                    }
                )
            } catch (e: Exception) {
                results.add(NotificationResult(
                    toToken = notification.toToken,
                    success = false,
                    error = e.message
                ))
                failureCount++
            }
        }
        
        Timber.d("Batch PR notifications completed: $successCount success, $failureCount failures")
        
        BatchNotificationResult(
            totalSent = notifications.size,
            successCount = successCount,
            failureCount = failureCount,
            results = results
        )
    }

    /**
     * Creates a notification title for PR achievements
     */
    private fun createNotificationTitle(fromDisplayName: String, pr: PersonalRecord): String {
        return when (pr.prType) {
            PRType.ONE_RM -> "$fromDisplayName hit a new 1RM! 🔥"
            PRType.VOLUME -> "$fromDisplayName smashed a volume PR! 💪"
            PRType.REPS -> "$fromDisplayName crushed a rep PR! ⚡"
            PRType.MAX_WEIGHT -> "$fromDisplayName lifted a new max weight! 🏋️"
        }
    }

    /**
     * Creates a notification body with PR details
     */
    private fun createNotificationBody(pr: PersonalRecord): String {
        return when (pr.prType) {
            PRType.ONE_RM -> {
                val weight = pr.weight?.let { "${it}kg" } ?: ""
                val improvement = pr.improvementPercent?.let { " (+${String.format("%.1f", it * 100)}%)" } ?: ""
                "${pr.exerciseName}: ${pr.estimatedOneRM?.let { String.format("%.1f", it) }}kg 1RM$improvement"
            }
            PRType.VOLUME -> {
                val improvement = pr.improvementPercent?.let { " (+${String.format("%.1f", it * 100)}%)" } ?: ""
                "${pr.exerciseName}: ${pr.volume?.let { String.format("%.0f", it) }}kg volume$improvement"
            }
            PRType.REPS -> {
                val weight = pr.weight?.let { " @ ${it}kg" } ?: ""
                val improvement = pr.improvementPercent?.let { " (+${String.format("%.0f", it * 100)}%)" } ?: ""
                "${pr.exerciseName}: ${pr.reps} reps$weight$improvement"
            }
            PRType.MAX_WEIGHT -> {
                val improvement = pr.improvementPercent?.let { " (+${String.format("%.1f", it * 100)}%)" } ?: ""
                "${pr.exerciseName}: ${pr.weight}kg$improvement"
            }
        }
    }
}

/**
 * Data class for PR notification payload
 */
@Serializable
data class PRNotificationData(
    val type: String,
    val fromUserId: String,
    val fromUsername: String,
    val fromDisplayName: String,
    val workoutId: String,
    val exerciseName: String,
    val prType: String,
    val weight: Double?,
    val reps: Int,
    val estimatedOneRM: Double?,
    val volume: Double?,
    val previousBest: Double?,
    val improvementPercent: Double?,
    val timestamp: Long
)

/**
 * Data class for gym buddy welcome notification payload
 */
@Serializable
data class GymBuddyWelcomeData(
    val type: String,
    val fromUserId: String,
    val fromUsername: String,
    val fromDisplayName: String,
    val connectionMethod: String,
    val timestamp: Long
)

/**
 * Data class for batch PR notification requests
 */
data class BatchPRNotification(
    val toToken: String,
    val fromUserId: String,
    val fromUsername: String,
    val fromDisplayName: String,
    val personalRecord: PersonalRecord,
    val workoutId: String
)

/**
 * Result for individual notification in batch
 */
data class NotificationResult(
    val toToken: String,
    val success: Boolean,
    val error: String?
)

/**
 * Result for batch notification operation
 */
data class BatchNotificationResult(
    val totalSent: Int,
    val successCount: Int,
    val failureCount: Int,
    val results: List<NotificationResult>
)
