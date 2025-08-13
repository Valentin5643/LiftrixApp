package com.example.liftrix.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for creating and managing notification channels on Android O+ (API 26+).
 * 
 * Responsible for:
 * - Creating notification channels with appropriate importance levels
 * - Organizing channels into logical groups
 * - Configuring sound, vibration, and visual settings
 * - Updating channel settings when needed
 * - Providing channel IDs for notification routing
 * 
 * Follows Android's notification channel best practices:
 * - Semantic channel naming for user understanding
 * - Appropriate importance levels for different content types
 * - Grouped channels for better organization in settings
 * - Proper audio attributes and custom sounds
 */
@Singleton
class NotificationChannelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        // Channel Groups
        const val GROUP_SOCIAL = "social_group"
        const val GROUP_WORKOUT = "workout_group"
        const val GROUP_SYSTEM = "system_group"
        
        // Social Channels
        const val CHANNEL_GYM_BUDDY = "gym_buddy_channel"
        const val CHANNEL_SOCIAL_REQUESTS = "social_requests_channel"
        const val CHANNEL_SOCIAL_ENGAGEMENT = "social_engagement_channel"
        const val CHANNEL_MENTIONS = "mentions_channel"
        
        // Workout Channels
        const val CHANNEL_ACHIEVEMENT = "achievement_channel"
        const val CHANNEL_REMINDER = "reminder_channel"
        const val CHANNEL_WORKOUT_COMPLETE = "workout_complete_channel"
        
        // System Channels
        const val CHANNEL_DEFAULT = "default_channel"
        const val CHANNEL_ERROR = "error_channel"
        const val CHANNEL_SYNC = "sync_channel"
    }

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    /**
     * Initialize all notification channels.
     * Should be called during app startup to ensure channels are available.
     */
    fun initializeChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Timber.d("Initializing notification channels")
            createChannelGroups()
            createAllChannels()
            Timber.i("Notification channels initialized successfully")
        } else {
            Timber.d("Notification channels not needed for Android version < O")
        }
    }

    /**
     * Create notification channel groups for better organization
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannelGroups() {
        val groups = listOf(
            NotificationChannelGroup(
                GROUP_SOCIAL,
                "Social & Community"
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    description = "Notifications about social activity, friends, and community interactions"
                }
            },
            
            NotificationChannelGroup(
                GROUP_WORKOUT,
                "Workouts & Achievements"
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    description = "Notifications about workouts, achievements, and personal records"
                }
            },
            
            NotificationChannelGroup(
                GROUP_SYSTEM,
                "System & Sync"
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    description = "System notifications, errors, and data synchronization"
                }
            }
        )
        
        groups.forEach { group ->
            notificationManager.createNotificationChannelGroup(group)
            Timber.d("Created notification channel group: ${group.id}")
        }
    }

    /**
     * Create all notification channels
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createAllChannels() {
        createSocialChannels()
        createWorkoutChannels()
        createSystemChannels()
    }

    /**
     * Create social notification channels
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createSocialChannels() {
        // Gym Buddy PRs - High priority, custom sound
        val gymBuddyChannel = NotificationChannel(
            CHANNEL_GYM_BUDDY,
            "Gym Buddy PRs",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Get notified when your gym buddies hit new personal records"
            group = GROUP_SOCIAL
            enableLights(true)
            lightColor = android.graphics.Color.GREEN
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500) // Celebration pattern
            setSound(getCustomSoundUri("achievement"), createAudioAttributes())
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        
        // Social Requests - High priority for immediate action
        val socialRequestsChannel = NotificationChannel(
            CHANNEL_SOCIAL_REQUESTS,
            "Follow Requests",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "New follower requests that need your response"
            group = GROUP_SOCIAL
            enableLights(true)
            lightColor = android.graphics.Color.BLUE
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 100, 300)
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        
        // Social Engagement - Default priority, can be batched
        val socialEngagementChannel = NotificationChannel(
            CHANNEL_SOCIAL_ENGAGEMENT,
            "Likes & Comments",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Likes, comments, and other social interactions"
            group = GROUP_SOCIAL
            enableLights(true)
            lightColor = android.graphics.Color.CYAN
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 100, 200)
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        
        // Mentions - High priority as they're direct interactions
        val mentionsChannel = NotificationChannel(
            CHANNEL_MENTIONS,
            "Mentions",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "When someone mentions you in posts or comments"
            group = GROUP_SOCIAL
            enableLights(true)
            lightColor = android.graphics.Color.MAGENTA
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 150, 400)
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        
        val socialChannels = listOf(
            gymBuddyChannel,
            socialRequestsChannel,
            socialEngagementChannel,
            mentionsChannel
        )
        
        socialChannels.forEach { channel ->
            notificationManager.createNotificationChannel(channel)
            Timber.d("Created social notification channel: ${channel.id}")
        }
    }

    /**
     * Create workout-related notification channels
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createWorkoutChannels() {
        // Achievements - High priority with special sound
        val achievementChannel = NotificationChannel(
            CHANNEL_ACHIEVEMENT,
            "Personal Records",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Your personal records and achievement milestones"
            group = GROUP_WORKOUT
            enableLights(true)
            lightColor = android.graphics.Color.YELLOW
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 200, 600, 200, 600) // Triple vibration for achievements
            setSound(getCustomSoundUri("achievement"), createAudioAttributes())
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        
        // Workout Reminders - Low priority to avoid interruption
        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDER,
            "Workout Reminders",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Gentle reminders about workouts and rest days"
            group = GROUP_WORKOUT
            enableLights(false) // Less intrusive for reminders
            enableVibration(false) // No vibration for gentle reminders
            setShowBadge(false) // Don't clutter badge count with reminders
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        
        // Workout Complete - Default priority
        val workoutCompleteChannel = NotificationChannel(
            CHANNEL_WORKOUT_COMPLETE,
            "Workout Summaries",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Summaries and insights after completing workouts"
            group = GROUP_WORKOUT
            enableLights(true)
            lightColor = android.graphics.Color.GREEN
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 150, 300)
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        
        val workoutChannels = listOf(
            achievementChannel,
            reminderChannel,
            workoutCompleteChannel
        )
        
        workoutChannels.forEach { channel ->
            notificationManager.createNotificationChannel(channel)
            Timber.d("Created workout notification channel: ${channel.id}")
        }
    }

    /**
     * Create system notification channels
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createSystemChannels() {
        // Default channel for uncategorized notifications
        val defaultChannel = NotificationChannel(
            CHANNEL_DEFAULT,
            "General",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "General app notifications"
            group = GROUP_SYSTEM
            enableLights(true)
            lightColor = android.graphics.Color.WHITE
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        
        // Error notifications - High priority for user attention
        val errorChannel = NotificationChannel(
            CHANNEL_ERROR,
            "Errors & Warnings",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Important errors and warnings that need attention"
            group = GROUP_SYSTEM
            enableLights(true)
            lightColor = android.graphics.Color.RED
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 300, 500, 300, 500)
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        
        // Sync notifications - Low priority background operations
        val syncChannel = NotificationChannel(
            CHANNEL_SYNC,
            "Data Sync",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background data synchronization status"
            group = GROUP_SYSTEM
            enableLights(false)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        
        val systemChannels = listOf(
            defaultChannel,
            errorChannel,
            syncChannel
        )
        
        systemChannels.forEach { channel ->
            notificationManager.createNotificationChannel(channel)
            Timber.d("Created system notification channel: ${channel.id}")
        }
    }

    /**
     * Get custom sound URI for specific notification types
     */
    private fun getCustomSoundUri(soundType: String): Uri {
        return when (soundType) {
            "achievement" -> {
                // Try to get custom achievement sound, fallback to default
                try {
                    Uri.parse("android.resource://${context.packageName}/raw/achievement_sound")
                } catch (e: Exception) {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                }
            }
            else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
    }

    /**
     * Create audio attributes for notification sounds
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun createAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    /**
     * Get appropriate channel ID for notification type
     */
    fun getChannelIdForNotificationType(type: String): String {
        return when (type.uppercase()) {
            "GYM_BUDDY_PR" -> CHANNEL_GYM_BUDDY
            "FOLLOW_REQUEST" -> CHANNEL_SOCIAL_REQUESTS
            "POST_LIKE", "POST_COMMENT" -> CHANNEL_SOCIAL_ENGAGEMENT
            "MENTION" -> CHANNEL_MENTIONS
            "ACHIEVEMENT", "PERSONAL_RECORD" -> CHANNEL_ACHIEVEMENT
            "WORKOUT_REMINDER", "REST_DAY_REMINDER" -> CHANNEL_REMINDER
            "WORKOUT_COMPLETE", "WORKOUT_SUMMARY" -> CHANNEL_WORKOUT_COMPLETE
            "ERROR", "WARNING" -> CHANNEL_ERROR
            "SYNC" -> CHANNEL_SYNC
            else -> CHANNEL_DEFAULT
        }
    }

    /**
     * Check if notification permission is granted
     */
    @SuppressLint("MissingPermission")
    fun areNotificationsEnabled(): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Check if a specific channel is enabled
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun isChannelEnabled(channelId: String): Boolean {
        val channel = notificationManager.getNotificationChannel(channelId)
        return channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /**
     * Get channel importance level
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun getChannelImportance(channelId: String): Int {
        val channel = notificationManager.getNotificationChannel(channelId)
        return channel?.importance ?: NotificationManager.IMPORTANCE_NONE
    }

    /**
     * Update channel settings (limited on Android O+)
     * Note: Most channel settings cannot be changed after creation on Android O+
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun updateChannelDescription(channelId: String, newDescription: String) {
        val channel = notificationManager.getNotificationChannel(channelId)
        channel?.let {
            // Only description can be updated after channel creation
            it.description = newDescription
            notificationManager.createNotificationChannel(it)
            Timber.d("Updated channel description for: $channelId")
        }
    }

    /**
     * Delete a notification channel (use with caution)
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun deleteChannel(channelId: String) {
        notificationManager.deleteNotificationChannel(channelId)
        Timber.w("Deleted notification channel: $channelId")
    }

    /**
     * Get all created notification channels
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun getAllChannels(): List<NotificationChannel> {
        return notificationManager.notificationChannels
    }

    /**
     * Get channels by group
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun getChannelsByGroup(groupId: String): List<NotificationChannel> {
        return notificationManager.notificationChannels.filter { it.group == groupId }
    }

    /**
     * Check if notification channels need migration (for app updates)
     */
    fun shouldMigrateChannels(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        
        // Check if all required channels exist
        val requiredChannels = listOf(
            CHANNEL_GYM_BUDDY,
            CHANNEL_SOCIAL_REQUESTS,
            CHANNEL_SOCIAL_ENGAGEMENT,
            CHANNEL_MENTIONS,
            CHANNEL_ACHIEVEMENT,
            CHANNEL_REMINDER,
            CHANNEL_WORKOUT_COMPLETE,
            CHANNEL_DEFAULT,
            CHANNEL_ERROR,
            CHANNEL_SYNC
        )
        
        return requiredChannels.any { channelId ->
            notificationManager.getNotificationChannel(channelId) == null
        }
    }

    /**
     * Migrate channels for app updates
     */
    fun migrateChannels() {
        if (shouldMigrateChannels()) {
            Timber.i("Migrating notification channels")
            initializeChannels()
        }
    }

    /**
     * Get notification settings summary for debugging
     */
    fun getNotificationSettingsSummary(): String {
        val builder = StringBuilder()
        builder.appendLine("Notification Settings Summary:")
        builder.appendLine("- Notifications enabled: ${areNotificationsEnabled()}")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = getAllChannels()
            builder.appendLine("- Total channels: ${channels.size}")
            
            channels.forEach { channel ->
                builder.appendLine("  * ${channel.id}: ${channel.name} (Importance: ${channel.importance})")
            }
            
            val groups = notificationManager.notificationChannelGroups
            builder.appendLine("- Total channel groups: ${groups.size}")
        }
        
        return builder.toString()
    }
}

/**
 * Extension functions for easier channel management
 */
fun Context.initializeNotificationChannels() {
    val channelManager = NotificationChannelManager(this)
    channelManager.initializeChannels()
}

fun Context.getNotificationChannelManager(): NotificationChannelManager {
    return NotificationChannelManager(this)
}