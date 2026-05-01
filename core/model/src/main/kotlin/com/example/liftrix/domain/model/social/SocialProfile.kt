package com.example.liftrix.domain.model.social

/**
 * Domain model representing a user's social profile.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 */
data class SocialProfile(
    val userId: String,
    val username: String,
    val displayName: String? = null,
    val bio: String? = null,
    val profilePhotoUrl: String? = null,
    val coverPhotoUrl: String? = null,

    // Social stats
    val workoutCount: Int = 0,
    val followerCount: Int = 0,
    val followingCount: Int = 0,

    // Profile metadata
    val memberSince: Long,
    val lastActive: Long? = null,
    val isVerified: Boolean = false,

    // Privacy settings
    val isPrivate: Boolean = true,
    val hideFromSuggestions: Boolean = false,
    val allowFriendRequests: Boolean = true,

    // External links
    val instagramHandle: String? = null,
    val youtubeChannel: String? = null,
    val personalWebsite: String? = null,

    // Metadata
    val createdAt: Long,
    val updatedAt: Long
) {
    /**
     * Checks if this profile can be viewed by the specified viewer.
     * Enforces privacy rules based on profile settings.
     */
    fun canBeViewedBy(viewerId: String?, isFollower: Boolean = false, isBlocked: Boolean = false): Boolean {
        // Owner can always view their own profile
        if (viewerId == userId) return true
        
        // Blocked users cannot view profile
        if (isBlocked) return false
        
        // Public profiles can be viewed by anyone
        if (!isPrivate) return true
        
        // Private profiles can only be viewed by followers
        return isFollower
    }

    /**
     * Returns display name if available, otherwise username
     */
    fun getDisplayNameOrUsername(): String = displayName ?: username

    /**
     * Checks if profile has all basic information filled out
     */
    fun isComplete(): Boolean {
        return displayName?.isNotBlank() == true &&
               bio?.isNotBlank() == true &&
               profilePhotoUrl?.isNotBlank() == true
    }
}

/**
 * Represents the different states a profile can be in when loading.
 *
 * This prevents treating missing profiles as fatal errors and allows proper UI handling:
 * - User has auth but no profile → Show "Create Profile" UI
 * - Profile is private → Show "Private Profile" message
 * - Profile loaded successfully → Show profile content
 *
 * Rationale:
 * - Missing profiles are RECOVERABLE states (new users, incomplete onboarding, etc.)
 * - UI needs to distinguish between "not found" vs "private" vs "loaded"
 * - Analytics should not log errors for expected states like new users
 */
sealed class ProfileLoadState {
    /**
     * Profile loaded successfully from database.
     */
    data class Loaded(val profile: SocialProfile) : ProfileLoadState()

    /**
     * Profile does not exist (auth user exists but no profile document).
     *
     * Common causes:
     * - New user who hasn't completed profile creation
     * - Profile creation failed during onboarding
     * - Profile was deleted but auth still exists
     * - Dev/test account that bypassed profile creation
     *
     * UI should show: "Create Profile" or trigger profile creation flow
     */
    data object MissingProfile : ProfileLoadState()

    /**
     * Profile exists but is private and viewer is not a follower.
     *
     * UI should show: "This profile is private" message
     */
    data object PrivateProfile : ProfileLoadState()

    /**
     * Failed to load profile due to unexpected error (network, database, etc.).
     *
     * UI should show: Error message with retry option
     */
    data class LoadError(val error: String) : ProfileLoadState()
}