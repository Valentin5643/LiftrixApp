package com.example.liftrix.domain.model.notifications

/**
 * Domain model representing user notification preferences.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 * 
 * Controls all aspects of notification delivery including:
 * - Category-based toggles (social, workout, achievement)
 * - Delivery timing and frequency settings
 * - Sound and vibration preferences  
 * - Quiet hours configuration
 * - Batching preferences for social notifications
 */
data class NotificationPreferences(
    val userId: String,
    
    // Master controls
    val notificationsEnabled: Boolean = true,
    
    // Category toggles
    val socialNotifications: Boolean = true,
    val workoutNotifications: Boolean = true,
    val achievementNotifications: Boolean = true,
    val reminderNotifications: Boolean = true,
    
    // Social subcategories
    val gymBuddyPrs: Boolean = true,
    val followRequests: Boolean = true,
    val postLikes: Boolean = true,
    val postComments: Boolean = true,
    val mentions: Boolean = true,
    
    // Delivery preferences
    val deliveryFrequency: String = "IMMEDIATE", // 'IMMEDIATE', 'HOURLY', 'DAILY'
    val quietHoursEnabled: Boolean = true,
    val quietHoursStart: Int = 22, // 10 PM
    val quietHoursEnd: Int = 8, // 8 AM
    
    // Batching preferences
    val batchSocialNotifications: Boolean = true,
    val batchWindowMinutes: Int = 60,
    
    // Sound and vibration
    val notificationSound: Boolean = true,
    val notificationVibration: Boolean = true,
    
    // In-app settings
    val showInAppNotifications: Boolean = true,
    
    // Updated timestamp
    val updatedAt: Long
) {
    
    companion object {
        /**
         * Creates default notification preferences for a user
         */
        fun createDefault(userId: String): NotificationPreferences {
            return NotificationPreferences(
                userId = userId,
                updatedAt = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Checks if notifications are enabled for a specific category
     */
    fun isCategoryEnabled(category: String): Boolean {
        if (!notificationsEnabled) return false
        
        return when (category.lowercase()) {
            "social" -> socialNotifications
            "workout" -> workoutNotifications
            "achievement" -> achievementNotifications
            "reminder" -> reminderNotifications
            else -> false
        }
    }
    
    /**
     * Checks if a specific notification type should be delivered
     */
    fun shouldDeliverNotificationType(type: String): Boolean {
        if (!notificationsEnabled) return false
        
        return when (type.lowercase()) {
            // Social notifications
            "gym_buddy_pr" -> socialNotifications && gymBuddyPrs
            "follow_request" -> socialNotifications && followRequests
            "post_like" -> socialNotifications && postLikes
            "post_comment" -> socialNotifications && postComments
            "mention" -> socialNotifications && mentions
            
            // Workout notifications
            "workout_reminder" -> workoutNotifications && reminderNotifications
            "rest_day_reminder" -> workoutNotifications
            
            // Achievement notifications
            "achievement_unlocked" -> achievementNotifications
            "personal_record" -> achievementNotifications
            "milestone_reached" -> achievementNotifications
            
            else -> true // Default to enabled for unknown types
        }
    }
    
    /**
     * Checks if current time is within quiet hours
     */
    fun isInQuietHours(currentHour: Int): Boolean {
        if (!quietHoursEnabled) return false
        
        return if (quietHoursStart > quietHoursEnd) {
            // Quiet hours span midnight (e.g., 22:00 - 08:00)
            currentHour >= quietHoursStart || currentHour < quietHoursEnd
        } else {
            // Quiet hours within same day
            currentHour >= quietHoursStart && currentHour < quietHoursEnd
        }
    }
    
    /**
     * Gets delivery frequency enum value
     */
    fun getDeliveryFrequencyEnum(): DeliveryFrequency {
        return when (deliveryFrequency) {
            "IMMEDIATE" -> DeliveryFrequency.IMMEDIATE
            "HOURLY" -> DeliveryFrequency.HOURLY
            "DAILY" -> DeliveryFrequency.DAILY
            else -> DeliveryFrequency.IMMEDIATE
        }
    }
}

/**
 * Enum for notification delivery frequency options
 */
enum class DeliveryFrequency(
    val displayName: String,
    val description: String
) {
    IMMEDIATE("Immediately", "Receive notifications as they happen"),
    HOURLY("Hourly", "Group notifications and deliver once per hour"),
    DAILY("Daily Summary", "Single daily summary of all activity")
}