package com.example.liftrix.domain.usecase.account

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.UserAccountRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import javax.inject.Inject

/**
 * Use case for updating user email address with validation and reauthentication.
 * Part of account management system from SPEC-20250116-account-management.
 */
class UpdateEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userAccountRepository: UserAccountRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {
    
    /**
     * Updates the user's email address after validation and reauthentication.
     * 
     * @param newEmail The new email address
     * @param currentPassword Current password for reauthentication
     * @return Unit if successful
     */
    suspend operator fun invoke(newEmail: String, currentPassword: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            when (throwable) {
                is LiftrixError -> throwable
                else -> LiftrixError.BusinessLogicError(
                    code = "EMAIL_UPDATE_FAILED",
                    errorMessage = "Failed to update email address: ${throwable.message}",
                    analyticsContext = mapOf(
                        "operation" to "UPDATE_EMAIL",
                        "error" to (throwable.message ?: "Unknown error")
                    )
                )
            }
        }
    ) {
        // Validate input
        if (newEmail.isBlank()) {
            throw LiftrixError.ValidationError(
                field = "email",
                violations = listOf("Email address cannot be empty"),
                analyticsContext = mapOf("operation" to "UPDATE_EMAIL")
            )
        }
        
        if (!isValidEmail(newEmail)) {
            throw LiftrixError.ValidationError(
                field = "email",
                violations = listOf("Email address format is invalid"),
                analyticsContext = mapOf("operation" to "UPDATE_EMAIL")
            )
        }
        
        if (currentPassword.isBlank()) {
            throw LiftrixError.ValidationError(
                field = "currentPassword",
                violations = listOf("Current password is required for email update"),
                analyticsContext = mapOf("operation" to "UPDATE_EMAIL")
            )
        }
        
        val userId = getCurrentUserIdUseCase()
            ?: throw LiftrixError.AuthenticationError(
                errorMessage = "User not authenticated",
                errorCode = "NO_USER",
                analyticsContext = mapOf("operation" to "UPDATE_EMAIL")
            )
        
        // Reauthenticate before email change
        val reauthResult = authRepository.reauthenticate(currentPassword)
        reauthResult.fold(
            onSuccess = { /* Continue */ },
            onFailure = { error -> throw error }
        )
        
        // Update email in Firebase Auth
        val updateAuthEmailResult = authRepository.updateEmail(newEmail)
        updateAuthEmailResult.fold(
            onSuccess = { /* Continue */ },
            onFailure = { error -> throw error }
        )
        
        // Update email in local account repository
        val updateLocalEmailResult = userAccountRepository.updateEmail(userId, newEmail)
        updateLocalEmailResult.fold(
            onSuccess = { /* Continue */ },
            onFailure = { error -> throw error }
        )
        
        // Mark email as unverified since it was changed
        val updateVerificationResult = userAccountRepository.updateEmailVerified(userId, false)
        updateVerificationResult.fold(
            onSuccess = { /* Complete */ },
            onFailure = { error -> throw error }
        )
    }
    
    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}