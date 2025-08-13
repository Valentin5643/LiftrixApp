package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing granular social privacy settings for each user.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 * 
 * This entity is separate from the legacy PrivacySettingsEntity to provide
 * granular controls specifically for social features with privacy-first defaults.
 * 
 * Security Note: All queries against this table MUST include userId filtering 
 * to prevent data leakage between users.
 */
@Entity(
    tableName = "privacy_settings",
    indices = [
        Index(value = ["user_id"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SocialPrivacySettingsEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,

    // Master controls
    @ColumnInfo(name = "social_enabled", defaultValue = "0")
    val socialEnabled: Boolean = false,

    @ColumnInfo(name = "profile_visibility", defaultValue = "PRIVATE")
    val profileVisibility: String = "PRIVATE", // PUBLIC, FOLLOWERS, PRIVATE

    // Feature toggles
    @ColumnInfo(name = "allow_follow_requests", defaultValue = "0")
    val allowFollowRequests: Boolean = false,

    @ColumnInfo(name = "workout_sharing_enabled", defaultValue = "0")
    val workoutSharingEnabled: Boolean = false,

    @ColumnInfo(name = "gym_buddies_enabled", defaultValue = "0")
    val gymBuddiesEnabled: Boolean = false,

    @ColumnInfo(name = "community_participation", defaultValue = "0")
    val communityParticipation: Boolean = false,

    @ColumnInfo(name = "challenge_participation", defaultValue = "0")
    val challengeParticipation: Boolean = false,

    @ColumnInfo(name = "routine_sharing_enabled", defaultValue = "0")
    val routineSharingEnabled: Boolean = false,

    // Content visibility
    @ColumnInfo(name = "default_workout_visibility", defaultValue = "PRIVATE")
    val defaultWorkoutVisibility: String = "PRIVATE", // PUBLIC, FOLLOWERS, PRIVATE

    @ColumnInfo(name = "show_workout_stats", defaultValue = "1")
    val showWorkoutStats: Boolean = true,

    @ColumnInfo(name = "show_achievements", defaultValue = "1")
    val showAchievements: Boolean = true,

    @ColumnInfo(name = "show_workout_streak", defaultValue = "1")
    val showWorkoutStreak: Boolean = true,

    // Discovery controls
    @ColumnInfo(name = "hide_from_suggestions", defaultValue = "1")
    val hideFromSuggestions: Boolean = true,

    @ColumnInfo(name = "hide_from_search", defaultValue = "0")
    val hideFromSearch: Boolean = false,

    // Notification preferences (JSON string)
    @ColumnInfo(name = "notification_settings", defaultValue = "{}")
    val notificationSettings: String = "{}",

    // Sync metadata
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "sync_version", defaultValue = "0")
    val syncVersion: Int = 0,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    companion object {
        // Profile visibility levels
        const val PROFILE_VISIBILITY_PUBLIC = "PUBLIC"
        const val PROFILE_VISIBILITY_FOLLOWERS = "FOLLOWERS"
        const val PROFILE_VISIBILITY_PRIVATE = "PRIVATE"

        // Workout visibility levels
        const val WORKOUT_VISIBILITY_PUBLIC = "PUBLIC"
        const val WORKOUT_VISIBILITY_FOLLOWERS = "FOLLOWERS"
        const val WORKOUT_VISIBILITY_PRIVATE = "PRIVATE"

        /**
         * Creates default privacy settings with maximum privacy for a new user
         */
        fun createDefault(userId: String): SocialPrivacySettingsEntity = SocialPrivacySettingsEntity(
            userId = userId,
            socialEnabled = false,
            profileVisibility = PROFILE_VISIBILITY_PRIVATE,
            allowFollowRequests = false,
            workoutSharingEnabled = false,
            gymBuddiesEnabled = false,
            communityParticipation = false,
            challengeParticipation = false,
            routineSharingEnabled = false,
            defaultWorkoutVisibility = WORKOUT_VISIBILITY_PRIVATE,
            showWorkoutStats = true,
            showAchievements = true,
            showWorkoutStreak = true,
            hideFromSuggestions = true,
            hideFromSearch = false,
            notificationSettings = "{}",
            updatedAt = System.currentTimeMillis()
        )
    }
}