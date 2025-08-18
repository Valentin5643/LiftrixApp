package com.example.liftrix.domain.usecase.account

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.UserAccountRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import javax.inject.Inject

/**
 * Use case for permanently deleting user account with proper data cleanup.
 * Part of account management system from SPEC-20250116-account-management.
 */
class DeleteAccountUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userAccountRepository: UserAccountRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {
    
    /**
     * Permanently deletes the user's account and all associated data.
     * 
     * @param currentPassword Current password for reauthentication
     * @return Unit if successful
     */
    suspend operator fun invoke(currentPassword: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            when (throwable) {
                is LiftrixError -> throwable
                else -> LiftrixError.BusinessLogicError(
                    code = "ACCOUNT_DELETION_FAILED",
                    errorMessage = "Failed to delete account: ${throwable.message}",
                    analyticsContext = mapOf(
                        "operation" to "DELETE_ACCOUNT",
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
                violations = listOf("Current password is required for account deletion"),
                analyticsContext = mapOf("operation" to "DELETE_ACCOUNT")
            )
        }
        
        val userId = getCurrentUserIdUseCase()
            ?: throw LiftrixError.AuthenticationError(
                errorMessage = "User not authenticated",
                errorCode = "NO_USER",
                analyticsContext = mapOf("operation" to "DELETE_ACCOUNT")
            )
        
        // Reauthenticate before account deletion
        val reauthResult = authRepository.reauthenticate(currentPassword)
        reauthResult.fold(
            onSuccess = { /* Continue */ },
            onFailure = { error -> throw error }
        )
        
        // Delete from Firebase Auth (includes Firestore cleanup)
        val deleteAuthResult = authRepository.deleteAccount()
        deleteAuthResult.fold(
            onSuccess = { /* Continue */ },
            onFailure = { error -> throw error }
        )
        
        // Delete from local Room database
        val deleteLocalResult = userAccountRepository.deleteAccount(userId)
        deleteLocalResult.fold(
            onSuccess = { /* Complete */ },
            onFailure = { error -> throw error }
        )
    }
}