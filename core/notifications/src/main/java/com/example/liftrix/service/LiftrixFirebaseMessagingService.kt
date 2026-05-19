package com.example.liftrix.service

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.liftrix.domain.repository.FCMTokenRepository
import com.example.liftrix.domain.service.NotificationHandler
import com.example.liftrix.domain.service.AnalyticsTracker
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.service.NotificationActionService
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Firebase Cloud Messaging service for handling FCM tokens and incoming messages.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 * 
 * Features:
 * - FCM token management with device tracking
 * - Intelligent notification routing based on app state
 * - Rich notifications with actions for different types
 * - Analytics tracking for notification delivery
 */
@AndroidEntryPoint
class LiftrixFirebaseMessagingService : FirebaseMessagingService() {
    
    @Inject lateinit var tokenRepository: FCMTokenRepository
    @Inject lateinit var notificationHandler: NotificationHandler
    @Inject lateinit var analyticsTracker: AnalyticsTracker
    @Inject lateinit var authQueryUseCase: AuthQueryUseCase
    @Inject lateinit var applicationScope: CoroutineScope
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        
        Timber.d("New FCM token received: ${token.take(20)}...")
        
        applicationScope.launch {
            val userIdResult = authQueryUseCase(waitForAuth = false)

            userIdResult.fold(
                onSuccess = { userId ->
                        val updateResult = liftrixCatching(
                            errorMapper = { throwable ->
                                LiftrixError.NetworkError(
                                    errorMessage = "Failed to update FCM token",
                                    analyticsContext = mapOf("user_id" to userId.value)
                                )
                            }
                        ) {
                            tokenRepository.updateToken(
                                userId = userId.value,
                                token = token,
                                deviceId = getLiftrixDeviceId(),
                                platform = "ANDROID",
                                appVersion = getAppVersion()
                            )
                        }
                        updateResult.fold(
                            onSuccess = { Timber.d("FCM token updated for user: ${userId.value}") },
                            onFailure = { error -> Timber.e("Failed to update FCM token: $error") }
                        )
                },
                onFailure = { error ->
                    Timber.w("Failed to get current user ID for FCM token update: $error")
                }
            )
        }
    }
    
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        val notificationType = message.data["type"] ?: run {
            Timber.w("Received FCM message without notification type")
            return
        }
        
        Timber.d("Received notification type: $notificationType")
        
        // Check if app is in foreground
        if (isAppInForeground()) {
            // Show in-app notification
            applicationScope.launch {
                notificationHandler.showInApp(message)
            }
        } else {
            // Process based on type for background/killed app
            when (notificationType) {
                "GYM_BUDDY_PR" -> handleGymBuddyPR(message)
                "GYM_BUDDY_WORKOUT_COMPLETED" -> handleGymBuddyWorkoutCompleted(message)
                "FOLLOW_REQUEST" -> handleFollowRequest(message)
                "POST_ENGAGEMENT" -> handlePostEngagement(message)
                "WORKOUT_REMINDER" -> handleWorkoutReminder(message)
                "ACHIEVEMENT_UNLOCKED" -> handleAchievementUnlocked(message)
                "SOCIAL_MENTION" -> handleSocialMention(message)
                else -> handleGenericNotification(message)
            }
        }
        
        // Track delivery analytics
        analyticsTracker.trackNotificationReceived(
            type = notificationType,
            isInForeground = isAppInForeground()
        )
    }
    
    private fun handleGymBuddyPR(message: RemoteMessage) {
        val fromUser = message.data["fromUser"] ?: return
        val fromUserName = message.data["fromUserName"] ?: "A gym buddy"
        val prDetail = message.data["prDetail"] ?: "hit a new PR!"
        val exerciseName = message.data["exerciseName"] ?: ""
        val prValue = message.data["prValue"] ?: ""
        
        val title = "🎉 $fromUserName hit a PR!"
        val body = if (exerciseName.isNotEmpty() && prValue.isNotEmpty()) {
            "$exerciseName: $prValue"
        } else {
            prDetail
        }
        
        val notification = NotificationCompat.Builder(this, CHANNEL_GYM_BUDDY)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(createMainAppPendingIntent(message))
            .addAction(createCelebrateAction(fromUser))
            .addAction(createViewProfileAction(fromUser))
            .setVibrate(longArrayOf(0, 500, 200, 500)) // Celebration pattern
            .setLights(0xFF00FF00.toInt(), 1000, 500) // Green light
            .build()
        
        NotificationManagerCompat.from(this)
            .notify(generateNotificationId(), notification)
    }

    private fun handleGymBuddyWorkoutCompleted(message: RemoteMessage) {
        val title = message.data["title"] ?: "Your Gym Buddy Finished a Workout"
        val body = message.data["body"] ?: "Your gym buddy just finished a workout"

        val notification = NotificationCompat.Builder(this, CHANNEL_GYM_BUDDY)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(createMainAppPendingIntent(message))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .build()

        NotificationManagerCompat.from(this)
            .notify(generateNotificationId(), notification)
    }
    
    private fun handleFollowRequest(message: RemoteMessage) {
        val fromUser = message.data["fromUser"] ?: return
        val fromUserName = message.data["fromUserName"] ?: "Someone"
        val fromUserPhoto = message.data["fromUserPhoto"]
        
        val notification = NotificationCompat.Builder(this, CHANNEL_SOCIAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Follow Request")
            .setContentText("$fromUserName wants to follow you")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(createFollowRequestPendingIntent(fromUser))
            .addAction(createAcceptFollowAction(fromUser))
            .addAction(createDeclineFollowAction(fromUser))
            .build()
        
        NotificationManagerCompat.from(this)
            .notify(generateNotificationId(), notification)
    }
    
    private fun handlePostEngagement(message: RemoteMessage) {
        val engagementType = message.data["engagementType"] ?: return // "like", "comment"
        val fromUserName = message.data["fromUserName"] ?: "Someone"
        val postId = message.data["postId"] ?: return
        val commentText = message.data["commentText"] // For comments
        
        val (title, body) = when (engagementType) {
            "like" -> "❤️ New Like" to "$fromUserName liked your workout post"
            "comment" -> "💬 New Comment" to if (commentText != null) {
                "$fromUserName: $commentText"
            } else {
                "$fromUserName commented on your post"
            }
            else -> "Social Activity" to "$fromUserName interacted with your post"
        }
        
        val notification = NotificationCompat.Builder(this, CHANNEL_SOCIAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(createPostPendingIntent(postId))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .build()
        
        NotificationManagerCompat.from(this)
            .notify(generateNotificationId(), notification)
    }
    
    private fun handleWorkoutReminder(message: RemoteMessage) {
        val reminderType = message.data["reminderType"] ?: "general" // "rest_day", "consistency", "general"
        val customMessage = message.data["customMessage"]
        
        val (title, body) = when (reminderType) {
            "rest_day" -> Pair("😴 Rest Day Reminder", "Don't forget to rest and recover today")
            "consistency" -> Pair("💪 Stay Consistent", "You haven't worked out in a few days. Ready to get back to it?")
            else -> Pair("🏋️ Workout Reminder", customMessage ?: "Time for your workout!")
        }
        
        val notification = NotificationCompat.Builder(this, CHANNEL_WORKOUT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(createMainAppPendingIntent(message))
            .addAction(createStartWorkoutAction())
            .build()
        
        NotificationManagerCompat.from(this)
            .notify(generateNotificationId(), notification)
    }
    
    private fun handleAchievementUnlocked(message: RemoteMessage) {
        val achievementName = message.data["achievementName"] ?: "New Achievement"
        val achievementDescription = message.data["achievementDescription"] ?: "You've unlocked a new achievement!"
        val achievementIcon = message.data["achievementIcon"]
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ACHIEVEMENT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🏆 Achievement Unlocked!")
            .setContentText("$achievementName - $achievementDescription")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(createAchievementPendingIntent())
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$achievementName\n\n$achievementDescription"))
            .setVibrate(longArrayOf(0, 300, 100, 300, 100, 300))
            .setLights(0xFFFFD700.toInt(), 2000, 1000) // Gold light
            .build()
        
        NotificationManagerCompat.from(this)
            .notify(generateNotificationId(), notification)
    }
    
    private fun handleSocialMention(message: RemoteMessage) {
        val fromUserName = message.data["fromUserName"] ?: "Someone"
        val postId = message.data["postId"]
        val mentionContext = message.data["mentionContext"] ?: "mentioned you in a post"
        
        val notification = NotificationCompat.Builder(this, CHANNEL_SOCIAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📢 You were mentioned")
            .setContentText("$fromUserName $mentionContext")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(if (postId != null) createPostPendingIntent(postId) else createMainAppPendingIntent(message))
            .build()
        
        NotificationManagerCompat.from(this)
            .notify(generateNotificationId(), notification)
    }
    
    private fun handleGenericNotification(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "Liftrix"
        val body = message.notification?.body ?: message.data["body"] ?: "New notification"
        
        val notification = NotificationCompat.Builder(this, CHANNEL_GENERAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(createMainAppPendingIntent(message))
            .build()
        
        NotificationManagerCompat.from(this)
            .notify(generateNotificationId(), notification)
    }
    
    // ========================================
    // Utility Methods
    // ========================================
    
    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        
        val packageName = applicationContext.packageName
        return appProcesses.any { processInfo ->
            processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
            processInfo.processName == packageName
        }
    }
    
    private fun getLiftrixDeviceId(): String {
        // Generate a unique device ID - in production, you might want to use Android ID
        val sharedPrefs = getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
        var deviceId = sharedPrefs.getString("device_id", null)
        
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            sharedPrefs.edit().putString("device_id", deviceId).apply()
        }
        
        return deviceId ?: UUID.randomUUID().toString()
    }
    
    private fun getAppVersion(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    private fun generateNotificationId(): Int {
        return System.currentTimeMillis().toInt()
    }
    
    // ========================================
    // PendingIntent Creators
    // ========================================
    
    private fun createMainAppPendingIntent(message: RemoteMessage): PendingIntent {
        val intent = (packageManager.getLaunchIntentForPackage(packageName) ?: Intent()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Add any navigation data from the message
            message.data["navigate_to"]?.let { 
                putExtra("navigate_to", it)
            }
        }
        
        return PendingIntent.getActivity(
            this,
            generateNotificationId(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun createFollowRequestPendingIntent(fromUser: String): PendingIntent {
        val intent = (packageManager.getLaunchIntentForPackage(packageName) ?: Intent()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "follow_requests")
            putExtra("highlight_user", fromUser)
        }
        
        return PendingIntent.getActivity(
            this,
            generateNotificationId(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun createPostPendingIntent(postId: String): PendingIntent {
        val intent = (packageManager.getLaunchIntentForPackage(packageName) ?: Intent()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "post_detail")
            putExtra("post_id", postId)
        }
        
        return PendingIntent.getActivity(
            this,
            generateNotificationId(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun createAchievementPendingIntent(): PendingIntent {
        val intent = (packageManager.getLaunchIntentForPackage(packageName) ?: Intent()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "achievements")
        }
        
        return PendingIntent.getActivity(
            this,
            generateNotificationId(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    // ========================================
    // Action Creators
    // ========================================
    
    private fun createCelebrateAction(fromUser: String): NotificationCompat.Action {
        val intent = Intent(this, NotificationActionService::class.java).apply {
            action = "CELEBRATE_PR"
            putExtra("from_user", fromUser)
        }
        
        val pendingIntent = PendingIntent.getService(
            this,
            generateNotificationId(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_input_add,
            "🎉 Celebrate",
            pendingIntent
        ).build()
    }
    
    private fun createViewProfileAction(fromUser: String): NotificationCompat.Action {
        val intent = (packageManager.getLaunchIntentForPackage(packageName) ?: Intent()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "profile")
            putExtra("user_id", fromUser)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            generateNotificationId(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view,
            "View Profile",
            pendingIntent
        ).build()
    }
    
    private fun createAcceptFollowAction(fromUser: String): NotificationCompat.Action {
        val intent = Intent(this, NotificationActionService::class.java).apply {
            action = "ACCEPT_FOLLOW"
            putExtra("from_user", fromUser)
        }
        
        val pendingIntent = PendingIntent.getService(
            this,
            generateNotificationId(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_input_add,
            "Accept",
            pendingIntent
        ).build()
    }
    
    private fun createDeclineFollowAction(fromUser: String): NotificationCompat.Action {
        val intent = Intent(this, NotificationActionService::class.java).apply {
            action = "DECLINE_FOLLOW"
            putExtra("from_user", fromUser)
        }
        
        val pendingIntent = PendingIntent.getService(
            this,
            generateNotificationId(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_delete,
            "Decline",
            pendingIntent
        ).build()
    }
    
    private fun createStartWorkoutAction(): NotificationCompat.Action {
        val intent = (packageManager.getLaunchIntentForPackage(packageName) ?: Intent()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "start_workout")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            generateNotificationId(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_play,
            "Start Workout",
            pendingIntent
        ).build()
    }
    
    companion object {
        // Notification channels
        const val CHANNEL_GYM_BUDDY = "gym_buddy_channel"
        const val CHANNEL_SOCIAL = "social_channel"
        const val CHANNEL_WORKOUT = "workout_channel"
        const val CHANNEL_ACHIEVEMENT = "achievement_channel"
        const val CHANNEL_GENERAL = "general_channel"
    }
}
