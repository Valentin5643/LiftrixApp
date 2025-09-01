package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.liftrix.domain.sync.SyncableEntity

/**
 * Entity for storing user sync preferences.
 * Part of Phase 2: Sync Infrastructure Enhancement from SPEC-20250901-todo-implementation.
 * 
 * Implements SyncableEntity for Firebase synchronization and follows user scoping pattern.
 */
@Entity(
    tableName = "sync_preferences",
    indices = [
        Index(value = ["user_id"], unique = true), // One preference entry per user
        Index(value = ["last_modified"]),
        Index(value = ["is_synced"]),
        Index(value = ["auto_sync_enabled"])
    ]
)
data class SyncPreferencesEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "auto_sync_enabled")
    val autoSyncEnabled: Boolean = true,
    
    @ColumnInfo(name = "sync_interval_minutes")
    val syncIntervalMinutes: Long = 15L, // Default 15 minutes
    
    @ColumnInfo(name = "sync_on_wifi_only")
    val syncOnWifiOnly: Boolean = false,
    
    @ColumnInfo(name = "sync_on_battery_saver")
    val syncOnBatterySaver: Boolean = false,
    
    @ColumnInfo(name = "last_sync_timestamp")
    val lastSyncTimestamp: Long? = null,
    
    @ColumnInfo(name = "sync_workout_data")
    val syncWorkoutData: Boolean = true,
    
    @ColumnInfo(name = "sync_profile_data")
    val syncProfileData: Boolean = true,
    
    @ColumnInfo(name = "sync_social_data")
    val syncSocialData: Boolean = true,
    
    @ColumnInfo(name = "sync_settings")
    val syncSettings: Boolean = true,
    
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
        require(syncIntervalMinutes > 0) { "Sync interval must be positive" }
    }
    
    /**
     * Checks if sync should be enabled based on current conditions.
     * @return true if sync should be enabled, false otherwise
     */
    fun shouldSync(): Boolean {
        return autoSyncEnabled && hasValidInterval()
    }
    
    /**
     * Checks if the sync interval is within valid bounds.
     * @return true if interval is valid (between 5 minutes and 24 hours)
     */
    private fun hasValidInterval(): Boolean {
        return syncIntervalMinutes >= 5 && syncIntervalMinutes <= 1440 // 5 min to 24 hours
    }
}