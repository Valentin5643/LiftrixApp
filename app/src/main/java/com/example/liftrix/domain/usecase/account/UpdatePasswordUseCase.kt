package com.example.liftrix.domain.usecase.account

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.UserAccountRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import javax.inject.Inject

/**
 * Use case for updating user password with validation and security checks.
 * Part of account management system from SPEC-20250116-account-management.
 */
class UpdatePasswordUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userAccountRepository: UserAccountRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {
    
    /**
     * Updates the user's password after validation and current password verification.
     * 
     * @param currentPassword Current password for verification
     * @param newPassword New password to set
     * @return Unit if successful
     */
    suspend operator fun invoke(currentPassword: String, newPassword: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            when (throwable) {
                is LiftrixError -> throwable
                else -> LiftrixError.BusinessLogicError(
                    code = "PASSWORD_UPDATE_FAILED",
                    errorMessage = "Failed to update password: ${throwable.message}",
                    analyticsContext = mapOf(
                        "operation" to "UPDATE_PASSWORD",
                        "error" to (throwable.message ?: "Unknown error")
                    )
                )
            }
        }
    ) {
        // Validate input
        if (currentPassword.isBlank()) {
            throw LiftrixError.ValidationError(
                field = "currentPassword",
                violations = listOf("Current password cannot be empty"),
                analyticsContext = mapOf("operation" to "UPDATE_PASSWORD")
            )
        }
        
        if (newPassword.isBlank()) {
            throw LiftrixError.ValidationError(
                field = "newPassword",
                violations = listOf("New password cannot be empty"),
                analyticsContext = mapOf("operation" to "UPDATE_PASSWORD")
            )
        }
        
        val passwordValidation = validatePasswordStrength(newPassword)
        if (passwordValidation.isNotEmpty()) {
            throw LiftrixError.ValidationError(
                field = "newPassword",
                violations = passwordValidation,
                analyticsContext = mapOf("operation" to "UPDATE_PASSWORD")
            )
        }
        
        if (currentPassword == newPassword) {
            throw LiftrixError.ValidationError(
                field = "newPassword",
                violations = listOf("New password must be different from current password"),
                analyticsContext = mapOf("operation" to "UPDATE_PASSWORD")
            )
        }
        
        val userId = getCurrentUserIdUseCase()
            ?: throw LiftrixError.AuthenticationError(
                errorMessage = "User not authenticated",
                errorCode = "NO_USER",
                analyticsContext = mapOf("operation" to "UPDATE_PASSWORD")
            )
        
        // Update password in Firebase Auth (includes reauthentication)
        val updatePasswordResult = authRepository.updatePassword(currentPassword, newPassword)
        updatePasswordResult.fold(
            onSuccess = { /* Continue */ },
            onFailure = { error -> throw error }
        )
        
        // Update password change timestamp in local storage
        val updateTimestampResult = userAccountRepository.updatePasswordChangeTime(userId)
        updateTimestampResult.fold(
            onSuccess = { /* Complete */ },
            onFailure = { error -> throw error }
        )
    }
    
    /**
     * Validates password strength according to security requirements.
     * 
     * @param password The password to validate
     * @return List of validation errors, empty if valid
     */
    private fun validatePasswordStrength(password: String): List<String> {
        val violations = mutableListOf<String>()
        
        if (password.length < 6) {
            violations.add("Password must be at least 6 characters long")
        }
        
        if (password.length > 128) {
            violations.add("Password must be less than 128 characters long")
        }
        
        if (!password.any { it.isLetterOrDigit() }) {
            violations.add("Password must contain at least one letter or number")
        }
        
        // Check for common weak passwords
        val commonWeakPasswords = setOf(
            "password", "123456", "password123", "admin", "qwerty",
            "abc123", "123123", "password1", "1234567", "12345678"
        )
        
        if (commonWeakPasswords.contains(password.lowercase())) {
            violations.add("Password is too common. Please choose a more secure password.")
        }
        
        return violations
    }
}