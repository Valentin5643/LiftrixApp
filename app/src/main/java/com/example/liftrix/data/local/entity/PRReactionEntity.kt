package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.liftrix.domain.sync.SyncableEntity

/**
 * PR Reaction Entity for tracking user reactions to PR notifications
 * Implements SyncableEntity for Firebase synchronization
 */
@Entity(
    tableName = "pr_reactions",
    foreignKeys = [
        ForeignKey(
            entity = PRNotificationEntity::class,
            parentColumns = ["id"],
            childColumns = ["pr_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SocialProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SocialProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["buddy_user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["pr_id"]),
        Index(value = ["user_id"]),
        Index(value = ["buddy_user_id"]),
        Index(value = ["user_id", "pr_id"], unique = true), // One reaction per user per PR
        Index(value = ["timestamp"])
    ]
)
data class PRReactionEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "buddy_user_id")
    val buddyUserId: String,
    
    @ColumnInfo(name = "pr_id")
    val prId: String,
    
    @ColumnInfo(name = "reaction_type")
    val reactionType: String, // "fire", "muscle", "clap", "heart", "wow"
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    
    // Sync metadata
    @ColumnInfo(name = "is_synced")
    override val isSynced: Boolean = false,
    
    @ColumnInfo(name = "sync_version")
    override val syncVersion: Long = 0,
    
    @ColumnInfo(name = "last_modified")
    override val lastModified: Long = System.currentTimeMillis()
) : SyncableEntity