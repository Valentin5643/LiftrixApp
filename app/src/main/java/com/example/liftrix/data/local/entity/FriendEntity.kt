package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters
import com.example.liftrix.domain.model.FriendStatus
import java.time.Instant

/**
 * Room entity representing a friend relationship in local database
 * Matches the friends table schema from Migration_16_to_17
 */
@Entity(
    tableName = "friends",
    primaryKeys = ["user_id", "friend_user_id"],
    indices = [
        Index(value = ["user_id"], name = "index_friends_user_id"),
        Index(value = ["friend_user_id"], name = "index_friends_friend_user_id"),
        Index(value = ["status"], name = "index_friends_status"),
        Index(value = ["created_at"], name = "index_friends_created_at")
    ]
)
@TypeConverters(DateTimeConverters::class)
data class FriendEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "friend_user_id")
    val friendUserId: String,
    
    @ColumnInfo(name = "status")
    val status: String, // Stored as string, converted from FriendStatus enum
    
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
    
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false
) {
    companion object {
        /**
         * Converts FriendStatus enum to string for database storage
         */
        fun fromFriendStatus(status: FriendStatus): String = status.name
        
        /**
         * Converts string from database to FriendStatus enum
         */
        fun toFriendStatus(statusString: String): FriendStatus = FriendStatus.valueOf(statusString)
    }
} 