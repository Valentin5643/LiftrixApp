package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.UserAccount
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for user account management operations.
 * 
 * Handles username management, account information, and account deletion scheduling.
 * All operations are user-scoped for security.
 */
interface UserAccountRepository {
    
    /**
     * Gets the account information for a specific user as a Flow
     */
    fun getAccountInfo(userId: String): Flow<UserAccount?>
    
    /**
     * Gets the account information for a specific user synchronously
     */
    suspend fun getAccountInfoSuspend(userId: String): LiftrixResult<UserAccount?>
    
    /**
     * Updates the username for a user after validating availability
     * @param username The new username to set, or null to remove username
     */
    suspend fun updateUsername(userId: String, username: String?): LiftrixResult<Unit>
    
    /**
     * Checks if a username is available (not taken by any user)
     */
    suspend fun checkUsernameAvailability(username: String): LiftrixResult<Boolean>
    
    /**
     * Creates or updates account information for a user
     */
    suspend fun upsertAccountInfo(userAccount: UserAccount): LiftrixResult<Unit>
    
    /**
     * Updates email address in the local account record
     */
    suspend fun updateEmail(userId: String, email: String): LiftrixResult<Unit>
    
    /**
     * Updates email verification status
     */
    suspend fun updateEmailVerified(userId: String, isVerified: Boolean): LiftrixResult<Unit>
    
    /**
     * Updates display name in the local account record
     */
    suspend fun updateDisplayName(userId: String, displayName: String?): LiftrixResult<Unit>
    
    /**
     * Records when the password was last changed
     */
    suspend fun updatePasswordChangeTime(userId: String): LiftrixResult<Unit>
    
    /**
     * Schedules an account for deletion with a grace period
     */
    suspend fun scheduleAccountDeletion(userId: String): LiftrixResult<Unit>
    
    /**
     * Cancels a scheduled account deletion
     */
    suspend fun cancelAccountDeletion(userId: String): LiftrixResult<Unit>
    
    /**
     * Gets all accounts that are ready for deletion (grace period expired)
     */
    suspend fun getAccountsReadyForDeletion(): LiftrixResult<List<UserAccount>>
    
    /**
     * Permanently deletes an account from local storage
     */
    suspend fun deleteAccount(userId: String): LiftrixResult<Unit>
    
    /**
     * Syncs account information from Firebase to local storage
     */
    suspend fun syncAccountFromFirebase(userId: String, email: String, displayName: String?): LiftrixResult<Unit>
}