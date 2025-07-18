package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.common.LiftrixResult

/**
 * Repository interface for user profile operations.
 * 
 * This interface provides methods for retrieving and managing user profiles
 * with LiftrixResult pattern for consistent error handling.
 */
interface UserRepository {
    
    /**
     * Retrieves a user's profile.
     * 
     * @param userId The ID of the user whose profile is to be fetched.
     * @return LiftrixResult with UserProfile if found, null if not found
     */
    suspend fun getUserProfile(userId: String): LiftrixResult<UserProfile?>
    
    /**
     * Saves or updates a user profile.
     * 
     * @param profile The UserProfile object to save.
     * @return LiftrixResult indicating success or failure
     */
    suspend fun saveUserProfile(profile: UserProfile): LiftrixResult<Unit>
    
    /**
     * Checks if a user profile exists.
     * 
     * @param userId The ID of the user to check.
     * @return LiftrixResult with true if profile exists, false otherwise
     */
    suspend fun hasUserProfile(userId: String): LiftrixResult<Boolean>
    
    /**
     * Deletes a user's profile.
     * 
     * @param userId The ID of the user whose profile is to be deleted.
     * @return LiftrixResult indicating success or failure
     */
    suspend fun deleteUserProfile(userId: String): LiftrixResult<Unit>
}