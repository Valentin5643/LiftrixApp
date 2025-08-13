package com.example.liftrix.domain.model

/**
 * Domain model representing an application notification before delivery.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 */
data class AppNotification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val data: Map<String, String> = emptyMap(),
    
    // Routing properties
    val priority: Priority = Priority.NORMAL,
    val category: NotificationCategory,
    val channelId: String,
    
    // Batching properties
    val canBatch: Boolean = true,
    val batchKey: String? = null,
    
    // Sender information (for privacy filtering)
    val fromUserId: String? = null,
    val fromUserName: String? = null,
    
    // Scheduling properties
    val expiresAt: Long? = null,
    val deliverAfter: Long? = null,
    
    // Creation timestamp
    val createdAt: Long = System.currentTimeMillis()
) {
    
    enum class NotificationType(val value: String) {
        // Workout notifications
        WORKOUT_REMINDER("workout_reminder"),
        REST_DAY_REMINDER("rest_day_reminder"),
        CONSISTENCY_NUDGE("consistency_nudge"),
        
        // Social notifications  
        GYM_BUDDY_PR("gym_buddy_pr"),
        FOLLOW_REQUEST("follow_request"),
        FOLLOW_ACCEPTED("follow_accepted"),
        POST_LIKE("post_like"),
        POST_COMMENT("post_comment"),
        SOCIAL_MENTION("social_mention"),
        
        // Achievement notifications
        ACHIEVEMENT_UNLOCKED("achievement_unlocked"),
        PERSONAL_RECORD("personal_record"),
        MILESTONE_REACHED("milestone_reached"),
        
        // System notifications
        SYNC_COMPLETED("sync_completed"),
        BACKUP_COMPLETED("backup_completed"),
        UPDATE_AVAILABLE("update_available"),
        
        // Generic
        GENERAL("general");
        
        companion object {
            fun fromString(value: String): NotificationType {
                return values().firstOrNull { it.value == value } ?: GENERAL
            }
        }
    }
    
    enum class Priority(val value: String, val numericValue: Int) {
        LOW("low", 1),
        NORMAL("normal", 2),
        HIGH("high", 3),
        CRITICAL("critical", 4);
        
        companion object {
            fun fromString(value: String): Priority {
                return values().firstOrNull { it.value == value } ?: NORMAL
            }
        }
    }
    
    enum class NotificationCategory(val value: String) {
        WORKOUT("workout"),
        SOCIAL("social"),
        ACHIEVEMENT("achievement"),
        REMINDER("reminder"),
        SYSTEM("system");
        
        companion object {
            fun fromString(value: String): NotificationCategory {
                return values().firstOrNull { it.value == value } ?: SYSTEM
            }
        }
    }
    
    /**
     * Generates a batch key for grouping similar notifications
     */
    fun generateBatchKey(): String {
        return batchKey ?: when (type) {
            NotificationType.POST_LIKE, NotificationType.POST_COMMENT -> "post_engagement"
            NotificationType.FOLLOW_REQUEST -> "follow_requests"
            NotificationType.GYM_BUDDY_PR -> "gym_buddy_prs"
            NotificationType.ACHIEVEMENT_UNLOCKED -> "achievements"
            else -> type.value
        }
    }
    
    /**
     * Checks if this notification should be delivered immediately (high priority)
     */
    fun shouldDeliverImmediately(): Boolean {
        return priority == Priority.HIGH || priority == Priority.CRITICAL ||
               type in listOf(
                   NotificationType.GYM_BUDDY_PR,
                   NotificationType.SOCIAL_MENTION,
                   NotificationType.ACHIEVEMENT_UNLOCKED
               )
    }
    
    /**
     * Checks if this notification can be batched with others
     */
    fun isBatchable(): Boolean {
        return canBatch && priority != Priority.CRITICAL &&
               type in listOf(
                   NotificationType.POST_LIKE,
                   NotificationType.POST_COMMENT,
                   NotificationType.FOLLOW_REQUEST,
                   NotificationType.FOLLOW_ACCEPTED
               )
    }
    
    /**
     * Checks if this notification has expired
     */
    fun isExpired(currentTime: Long = System.currentTimeMillis()): Boolean {
        return expiresAt != null && currentTime > expiresAt
    }
    
    /**
     * Checks if this notification can be delivered now (not scheduled for later)
     */
    fun canDeliverNow(currentTime: Long = System.currentTimeMillis()): Boolean {
        return deliverAfter == null || currentTime >= deliverAfter
    }
    
    /**
     * Gets display-appropriate title with emoji if needed
     */
    fun getDisplayTitle(): String {
        return when (type) {
            NotificationType.GYM_BUDDY_PR -> "🎉 $title"
            NotificationType.ACHIEVEMENT_UNLOCKED -> "🏆 $title"
            NotificationType.FOLLOW_REQUEST -> "👤 $title"
            NotificationType.POST_LIKE -> "❤️ $title"
            NotificationType.POST_COMMENT -> "💬 $title"
            NotificationType.WORKOUT_REMINDER -> "🏋️ $title"
            NotificationType.REST_DAY_REMINDER -> "😴 $title"
            else -> title
        }
    }
}