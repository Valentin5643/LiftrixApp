package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters
import com.example.liftrix.domain.model.PrivacyLevel
import com.example.liftrix.domain.model.SharingDefault
import java.time.Instant

/**
 * Room entity representing user privacy settings in local database
 * Matches the user_privacy_settings table schema from Migration_16_to_17
 */
@Entity(tableName = "user_privacy_settings")
@TypeConverters(DateTimeConverters::class)
data class PrivacySettingsEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "online_status_visibility")
    val onlineStatusVisibility: String, // Stored as string, converted from PrivacyLevel enum
    
    @ColumnInfo(name = "workout_sharing_default")
    val workoutSharingDefault: String, // Stored as string, converted from SharingDefault enum
    
    @ColumnInfo(name = "allow_friend_requests")
    val allowFriendRequests: Boolean,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant
) {
    companion object {
        /**
         * Converts PrivacyLevel enum to string for database storage
         */
        fun fromPrivacyLevel(privacyLevel: PrivacyLevel): String = privacyLevel.name
        
        /**
         * Converts string from database to PrivacyLevel enum
         */
        fun toPrivacyLevel(privacyString: String): PrivacyLevel = PrivacyLevel.valueOf(privacyString)
        
        /**
         * Converts SharingDefault enum to string for database storage
         */
        fun fromSharingDefault(sharingDefault: SharingDefault): String = sharingDefault.name
        
        /**
         * Converts string from database to SharingDefault enum
         */
        fun toSharingDefault(sharingString: String): SharingDefault = SharingDefault.valueOf(sharingString)
        
        /**
         * Creates default privacy settings for a new user
         */
        fun createDefault(userId: String): PrivacySettingsEntity = PrivacySettingsEntity(
            userId = userId,
            onlineStatusVisibility = fromPrivacyLevel(PrivacyLevel.ALL_FRIENDS),
            workoutSharingDefault = fromSharingDefault(SharingDefault.ASK_EACH_TIME),
            allowFriendRequests = true,
            updatedAt = Instant.now()
        )
    }
} 