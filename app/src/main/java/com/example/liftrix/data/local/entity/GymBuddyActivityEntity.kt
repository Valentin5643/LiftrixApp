package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Gym Buddy Activity Entity for tracking engagement metrics between gym buddies
 * Used for analytics and improving the gym buddy experience
 */
@Entity(
    tableName = "gym_buddy_activities",
    foreignKeys = [
        ForeignKey(
            entity = GymBuddyEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GymBuddyEntity::class,
            parentColumns = ["buddy_id"],
            childColumns = ["buddy_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["user_id", "buddy_id"], unique = true),
        Index(value = ["user_id"]),
        Index(value = ["buddy_id"])
    ]
)
data class GymBuddyActivityEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "buddy_id")
    val buddyId: String,
    
    // Activity metrics
    @ColumnInfo(name = "workouts_together_count")
    val workoutsTogetherCount: Int = 0,
    
    @ColumnInfo(name = "last_workout_together")
    val lastWorkoutTogether: Long? = null,
    
    @ColumnInfo(name = "total_prs_celebrated")
    val totalPrsCelebrated: Int = 0,
    
    // Engagement
    @ColumnInfo(name = "encouragement_messages_sent")
    val encouragementMessagesSent: Int = 0,
    
    @ColumnInfo(name = "workout_templates_shared")
    val workoutTemplatesShared: Int = 0
)