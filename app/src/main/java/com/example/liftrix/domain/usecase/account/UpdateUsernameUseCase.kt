package com.example.liftrix.domain.usecase.account

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.UserAccountRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import javax.inject.Inject

/**
 * Use case for updating username with availability checking and validation.
 * Part of account management system from SPEC-20250116-account-management.
 */
class UpdateUsernameUseCase @Inject constructor(
    private val userAccountRepository: UserAccountRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {
    
    /**
     * Updates the user's username after validation and availability check.
     * 
     * @param username New username to set (null to remove username)
     * @return Unit if successful
     */
    suspend operator fun invoke(username: String?): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            when (throwable) {
                is LiftrixError -> throwable
                else -> LiftrixError.BusinessLogicError(
                    code = "USERNAME_UPDATE_FAILED",
                    errorMessage = "Failed to update username: ${throwable.message}",
                    analyticsContext = mapOf(
                        "operation" to "UPDATE_USERNAME",
                        "username" to (username ?: "null"),
                        "error" to (throwable.message ?: "Unknown error")
                    )
                )
            }
        }
    ) {
        val userId = getCurrentUserIdUseCase()
            ?: throw LiftrixError.AuthenticationError(
                errorMessage = "User not authenticated",
                errorCode = "NO_USER",
                analyticsContext = mapOf("operation" to "UPDATE_USERNAME")
            )
        
        // If username is provided, validate it
        username?.let { name ->
            val validationErrors = validateUsername(name)
            if (validationErrors.isNotEmpty()) {
                throw LiftrixError.ValidationError(
                    field = "username",
                    violations = validationErrors,
                    analyticsContext = mapOf(
                        "operation" to "UPDATE_USERNAME",
                        "username" to name
                    )
                )
            }
            
            // Check availability
            val availabilityResult = userAccountRepository.checkUsernameAvailability(name)
            val isAvailable = availabilityResult.fold(
                onSuccess = { it },
                onFailure = { error -> throw error }
            )
            if (!isAvailable) {
                throw LiftrixError.ValidationError(
                    field = "username",
                    violations = listOf("Username is already taken"),
                    analyticsContext = mapOf(
                        "operation" to "UPDATE_USERNAME",
                        "username" to name
                    )
                )
            }
        }
        
        // Update username in repository
        val updateResult = userAccountRepository.updateUsername(userId, username)
        updateResult.fold(
            onSuccess = { /* Complete */ },
            onFailure = { error -> throw error }
        )
    }
    
    /**
     * Validates username format according to requirements.
     * 
     * @param username The username to validate
     * @return List of validation errors, empty if valid
     */
    private fun validateUsername(username: String): List<String> {
        val violations = mutableListOf<String>()
        
        if (username.isBlank()) {
            violations.add("Username cannot be empty")
            return violations
        }
        
        if (username.length < 3) {
            violations.add("Username must be at least 3 characters long")
        }
        
        if (username.length > 20) {
            violations.add("Username must be less than 20 characters long")
        }
        
        if (!username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            violations.add("Username can only contain letters, numbers, and underscores")
        }
        
        if (username.startsWith("_") || username.endsWith("_")) {
            violations.add("Username cannot start or end with an underscore")
        }
        
        if (username.contains("__")) {
            violations.add("Username cannot contain consecutive underscores")
        }
        
        // Check for reserved usernames
        val reservedUsernames = setOf(
            "admin", "administrator", "root", "system", "support", "help",
            "api", "www", "mail", "email", "test", "demo", "guest",
            "user", "null", "undefined", "liftrix", "fitness", "workout"
        )
        
        if (reservedUsernames.contains(username.lowercase())) {
            violations.add("Username is reserved and cannot be used")
        }
        
        return violations
    }
}