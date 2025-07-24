package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing user profile data.
 * Defines the contract for all profile-related operations, including local persistence and remote synchronization.
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
     * Gets the number of unsynchronized profiles.
     *
     * @return The count of unsynced profiles.
     */
    suspend fun getUnsyncedCount(): Int
    
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
} 