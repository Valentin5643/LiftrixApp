package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.liftrix.domain.sync.SyncableEntity

/**
 * Entity for caching user profile data to reduce database queries.
 * Part of Phase 1: User Profile Data Integration from SPEC-20250901-todo-implementation.
 * 
 * Implements SyncableEntity for Firebase synchronization and follows user scoping pattern.
 */
@Entity(
    tableName = "user_profile_cache",
    indices = [
        Index(value = ["user_id"], unique = true), // One cache entry per user
        Index(value = ["last_modified"]),
        Index(value = ["is_synced"]),
        Index(value = ["cache_timestamp"])
    ]
)
data class UserProfileCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "display_name")
    val displayName: String,
    
    @ColumnInfo(name = "profile_image_url")
    val profileImageUrl: String? = null,
    
    @ColumnInfo(name = "bio")
    val bio: String? = null,
    
    @ColumnInfo(name = "member_since")
    val memberSince: Long? = null,
    
    @ColumnInfo(name = "total_workouts")
    val totalWorkouts: Int = 0,
    
    @ColumnInfo(name = "cache_timestamp")
    val cacheTimestamp: Long = System.currentTimeMillis(),
    
    // Sync metadata for SyncableEntity compliance
    @ColumnInfo(name = "is_synced")
    override val isSynced: Boolean = false,
    
    @ColumnInfo(name = "sync_version")
    override val syncVersion: Long = 0L,
    
    @ColumnInfo(name = "last_modified")
    override val lastModified: Long = System.currentTimeMillis()
) : SyncableEntity {
    
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(displayName.isNotBlank()) { "Display name cannot be blank" }
    }
    
    /**
     * Checks if the cache entry has expired based on the given expiry time.
     * @param cacheExpiryTimeMs Cache expiry time in milliseconds
     * @return true if cache has expired, false otherwise
     */
    fun isExpired(cacheExpiryTimeMs: Long): Boolean {
        return System.currentTimeMillis() - cacheTimestamp > cacheExpiryTimeMs
    }
}