package com.example.liftrix.domain.model.social

/**
 * Domain model representing a personal record notification
 * 
 * This is sent between gym buddies when someone achieves a new PR,
 * allowing friends to celebrate achievements together and maintain
 * motivation through social connections.
 * 
 * Includes PR details like exercise, weight, reps, improvement percentage,
 * and notification state for tracking reads and reactions.
 */
data class PRNotification(
    val id: String,
    val fromUserId: String,
    val fromUserName: String,
    val toUserId: String,
    val workoutId: String,
    
    // PR details
    val exerciseName: String,
    val prWeight: Float?,
    val prReps: Int?,
    val prType: String, // '1RM', 'VOLUME', 'REPS', 'MAX_WEIGHT'
    val previousBest: Float?,
    val improvementPercent: Float?,
    val weightUnit: String? = "lbs",
    
    // Notification state
    val sentAt: Long,
    val readAt: Long?,
    val reactedWith: String?, // Emoji reaction
    
    // Cooldown tracking
    val cooldownKey: String // "fromUser:toUser:date"
)