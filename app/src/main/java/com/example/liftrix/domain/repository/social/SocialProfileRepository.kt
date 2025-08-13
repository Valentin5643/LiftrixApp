package com.example.liftrix.domain.repository.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.social.SocialProfile
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for social profile operations.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 * 
 * All operations are user-scoped to ensure data isolation and privacy.
 */
interface SocialProfileRepository {

    // ========================================
    // Profile Retrieval
    // ========================================

    /**
     * Observes changes to the user's social profile
     */
    fun observeProfile(userId: String): Flow<SocialProfile?>

    /**
     * Gets the user's social profile
     */
    suspend fun getProfile(userId: String, viewerId: String?): LiftrixResult<SocialProfile?>

    /**
     * Gets a social profile by username (with privacy filtering)
     */
    suspend fun getProfileByUsername(viewerId: String, username: String): LiftrixResult<SocialProfile?>

    /**
     * Checks if user has a social profile
     */
    suspend fun hasProfile(userId: String): LiftrixResult<Boolean>

    // ========================================
    // Username Management
    // ========================================

    /**
     * Checks if username is available for registration
     */
    suspend fun checkUsernameAvailability(username: String): LiftrixResult<Boolean>

    // ========================================
    // Profile Discovery
    // ========================================

    /**
     * Gets discoverable profiles for user suggestions
     */
    suspend fun getDiscoverableProfiles(viewerId: String, limit: Int = 50): LiftrixResult<List<SocialProfile>>

    /**
     * Searches profiles by username or display name
     */
    suspend fun searchProfiles(viewerId: String, query: String, limit: Int = 20): LiftrixResult<List<SocialProfile>>

    // ========================================
    // Profile Management
    // ========================================

    /**
     * Creates a new social profile
     */
    suspend fun createProfile(profile: SocialProfile): LiftrixResult<SocialProfile>

    /**
     * Updates an existing social profile
     */
    suspend fun updateProfile(userId: String, updates: ProfileUpdate): LiftrixResult<SocialProfile>

    /**
     * Updates profile photo
     */
    suspend fun updateProfilePhoto(userId: String, photoUrl: String?): LiftrixResult<Unit>

    /**
     * Updates privacy settings
     */
    suspend fun updatePrivacySetting(userId: String, isPrivate: Boolean): LiftrixResult<Unit>

    // ========================================
    // Social Stats
    // ========================================

    /**
     * Updates workout count for user
     */
    suspend fun updateWorkoutCount(userId: String, count: Int): LiftrixResult<Unit>

    /**
     * Updates follower count for user
     */
    suspend fun updateFollowerCount(userId: String, count: Int): LiftrixResult<Unit>

    /**
     * Updates following count for user
     */
    suspend fun updateFollowingCount(userId: String, count: Int): LiftrixResult<Unit>

    // ========================================
    // Profile Deletion
    // ========================================

    /**
     * Deletes user's social profile and all associated data
     */
    suspend fun deleteProfile(userId: String): LiftrixResult<Unit>

    // ========================================
    // Data Classes
    // ========================================

    /**
     * Data class for profile updates
     */
    data class ProfileUpdate(
        val displayName: String? = null,
        val bio: String? = null,
        val profilePhotoUrl: String? = null,
        val coverPhotoUrl: String? = null,
        val instagramHandle: String? = null,
        val youtubeChannel: String? = null,
        val personalWebsite: String? = null
    )
}