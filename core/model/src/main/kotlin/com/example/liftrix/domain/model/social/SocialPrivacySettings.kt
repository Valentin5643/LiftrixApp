package com.example.liftrix.domain.model.social

/**
 * Profile visibility levels
 */
enum class ProfileVisibility {
    PUBLIC,     // Visible to everyone
    FOLLOWERS,  // Visible to followers only  
    PRIVATE     // Not visible to others
}

/**
 * Workout visibility levels
 */
enum class WorkoutVisibility {
    PUBLIC,     // Visible to everyone
    FOLLOWERS,  // Visible to followers only
    PRIVATE     // Not visible to others
}

/**
 * Domain model representing granular social privacy settings.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 */
data class SocialPrivacySettings(
    val userId: String,

    // Master controls
    val socialEnabled: Boolean = false,
    val profileVisibility: ProfileVisibility = ProfileVisibility.PRIVATE,

    // Feature toggles
    val allowFollowRequests: Boolean = false,
    val workoutSharingEnabled: Boolean = false,
    val gymBuddiesEnabled: Boolean = false,
    val communityParticipation: Boolean = false,
    val challengeParticipation: Boolean = false,
    val routineSharingEnabled: Boolean = false,

    // Content visibility
    val defaultWorkoutVisibility: WorkoutVisibility = WorkoutVisibility.PRIVATE,
    val showWorkoutStats: Boolean = true,
    val showAchievements: Boolean = true,
    val showWorkoutStreak: Boolean = true,

    // Discovery controls
    val hideFromSuggestions: Boolean = true,
    val hideFromSearch: Boolean = false,

    // Notification preferences
    val notificationSettings: Map<String, Any> = emptyMap(),

    // Metadata
    val updatedAt: Long
) {
    /**
     * Checks if user has enabled any social features
     */
    fun hasSocialFeaturesEnabled(): Boolean {
        return socialEnabled && (
            allowFollowRequests ||
            workoutSharingEnabled ||
            gymBuddiesEnabled ||
            communityParticipation ||
            challengeParticipation ||
            routineSharingEnabled
        )
    }

    /**
     * Returns social-enabled defaults for a new user
     */
    companion object {
        fun createDefault(userId: String): SocialPrivacySettings = SocialPrivacySettings(
            userId = userId,
            socialEnabled = true,
            profileVisibility = ProfileVisibility.PUBLIC,
            allowFollowRequests = true,
            workoutSharingEnabled = true,
            gymBuddiesEnabled = true,
            communityParticipation = true,
            challengeParticipation = false,
            routineSharingEnabled = true,
            defaultWorkoutVisibility = WorkoutVisibility.FOLLOWERS,
            showWorkoutStats = true,
            showAchievements = true,
            showWorkoutStreak = true,
            hideFromSuggestions = false,
            hideFromSearch = false,
            notificationSettings = emptyMap(),
            updatedAt = System.currentTimeMillis()
        )
    }
}