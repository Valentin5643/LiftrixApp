package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.StreakData
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow

/**
 * Enhanced repository interface for managing user profile data.
 * Includes social features, achievements integration, and privacy controls.
 */
interface ProfileRepository {
    
    /**
     * Retrieves a user's profile as a reactive stream.
     *
     * @param userId The ID of the user whose profile is to be fetched.
     * @return A Flow that emits the UserProfile, or null if not found.
     */
    fun getProfile(userId: String): Flow<UserProfile?>
    
    /**
     * Saves a complete user profile.
     * This is typically used at the end of the onboarding flow.
     *
     * @param profile The UserProfile object to save.
     * @return A Result indicating success or failure.
     */
    suspend fun saveProfile(profile: UserProfile): Result<Unit>
    
    /**
     * Updates parts of a user's profile.
     * Useful for progressive onboarding or profile edits.
     *
     * @param userId The ID of the user whose profile is to be updated.
     * @param updates A map of fields to update.
     * @return A Result indicating success or failure.
     */
    suspend fun updatePartialProfile(userId: String, updates: Map<String, Any>): Result<Unit>
    
    /**
     * Deletes a user's profile.
     *
     * @param userId The ID of the user whose profile is to be deleted.
     * @return A Result indicating success or failure.
     */
    suspend fun deleteProfile(userId: String): Result<Unit>
    
    /**
     * Checks if a profile exists for the given user.
     *
     * @param userId The ID of the user to check.
     * @return True if a profile exists, false otherwise.
     */
    suspend fun hasProfile(userId: String): Boolean
    
    /**
     * Checks if a user has completed their profile onboarding.
     *
     * @param userId The ID of the user to check.
     * @return True if the profile is complete, false otherwise.
     */
    suspend fun hasCompletedProfile(userId: String): Boolean
    
    /**
     * Gets the number of unsynchronized profiles for a specific user.
     *
     * @param userId The ID of the user to check for unsynced profiles.
     * @return The count of unsynced profiles for the user.
     */
    suspend fun getUnsyncedCount(userId: String): Int
    
    /**
     * Queues a background sync for the user's profile.
     *
     * @param userId The ID of the user whose profile needs syncing.
     * @return A Result indicating if the sync was successfully queued.
     */
    suspend fun queueSync(userId: String): Result<Unit>
    
    /**
     * Triggers an immediate synchronization of the user's profile.
     *
     * @param userId The ID of the user whose profile needs syncing.
     * @return A Result indicating success or failure of the immediate sync.
     */
    suspend fun syncNow(userId: String): Result<Unit>

    // Enhanced methods for social profile system

    /**
     * Gets a user profile with enhanced data for display.
     *
     * @param userId The ID of the user whose profile to retrieve
     * @return LiftrixResult with enhanced UserProfile or null if not found
     */
    suspend fun getUserProfile(userId: String): LiftrixResult<UserProfile?>

    /**
     * Saves an enhanced user profile with social features.
     *
     * @param profile The enhanced UserProfile to save
     * @return LiftrixResult indicating success or failure
     */
    suspend fun saveUserProfile(profile: UserProfile): LiftrixResult<Unit>

    /**
     * Updates profile completion percentage.
     *
     * @param userId The user ID for the profile
     * @return LiftrixResult with calculated completion percentage
     */
    suspend fun updateProfileCompletion(userId: String): LiftrixResult<Int>

    /**
     * Calculates and returns streak data for a user.
     *
     * @param userId The user ID for calculation
     * @return LiftrixResult with StreakData
     */
    suspend fun calculateStreakData(userId: String): LiftrixResult<StreakData>

    /**
     * Updates privacy settings for a user profile.
     *
     * @param userId The user ID to update
     * @param isPublic Whether the profile should be public
     * @return LiftrixResult indicating success or failure
     */
    suspend fun updatePrivacySettings(userId: String, isPublic: Boolean): LiftrixResult<Unit>

    /**
     * Gets public profiles for discovery features.
     *
     * @param limit Maximum number of profiles to return
     * @return LiftrixResult with list of public profiles
     */
    suspend fun getPublicProfiles(limit: Int = 50): LiftrixResult<List<UserProfile>>

    /**
     * Gets a public profile if the user has made it public.
     *
     * @param userId The user ID to retrieve
     * @return LiftrixResult with public profile or null if private/not found
     */
    suspend fun getPublicProfile(userId: String): LiftrixResult<UserProfile?>
} 