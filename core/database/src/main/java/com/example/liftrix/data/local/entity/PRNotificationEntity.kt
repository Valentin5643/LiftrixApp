package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * PR Notification Entity for tracking personal record notifications between gym buddies
 * Includes cooldown tracking to prevent notification spam
 */
@Entity(
    tableName = "pr_notifications",
    foreignKeys = [
        ForeignKey(
            entity = SocialProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["from_user_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SocialProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["to_user_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["cooldown_key"], unique = true),
        Index(value = ["to_user_id", "sent_at"]),
        Index(value = ["from_user_id"]),
        Index(value = ["workout_id"])
    ]
)
data class PRNotificationEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "from_user_id")
    val fromUserId: String,
    
    @ColumnInfo(name = "to_user_id")
    val toUserId: String,
    
    @ColumnInfo(name = "workout_id")
    val workoutId: String,
    
    // PR details
    @ColumnInfo(name = "exercise_name")
    val exerciseName: String,
    
    @ColumnInfo(name = "pr_weight")
    val prWeight: Double? = null,
    
    @ColumnInfo(name = "pr_reps")
    val prReps: Int? = null,
    
    @ColumnInfo(name = "pr_type")
    val prType: String, // '1RM', 'VOLUME', 'REPS'
    
    @ColumnInfo(name = "previous_best")
    val previousBest: Double? = null,
    
    @ColumnInfo(name = "improvement_percent")
    val improvementPercent: Double? = null,
    
    // Notification state
    @ColumnInfo(name = "sent_at")
    val sentAt: Long,
    
    @ColumnInfo(name = "read_at")
    val readAt: Long? = null,
    
    @ColumnInfo(name = "reacted_with")
    val reactedWith: String? = null, // Emoji reaction
    
    // Cooldown tracking
    @ColumnInfo(name = "cooldown_key")
    val cooldownKey: String // "fromUser:toUser:date"
)