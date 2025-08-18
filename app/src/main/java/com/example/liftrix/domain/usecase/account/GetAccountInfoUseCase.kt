package com.example.liftrix.domain.usecase.account

import com.example.liftrix.domain.model.UserAccount
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.UserAccountRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Use case for retrieving user account information.
 * Part of account management system from SPEC-20250116-account-management.
 */
class GetAccountInfoUseCase @Inject constructor(
    private val userAccountRepository: UserAccountRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {
    
    /**
     * Gets the current user's account information as a Flow.
     * 
     * @return Flow of UserAccount or null if not found
     */
    suspend fun asFlow(): Flow<UserAccount?> {
        val userId = getCurrentUserIdUseCase()
        return if (userId != null) {
            userAccountRepository.getAccountInfo(userId)
        } else {
            kotlinx.coroutines.flow.flowOf(null)
        }
    }
    
    /**
     * Gets the current user's account information synchronously.
     * 
     * @return UserAccount or null if not found
     */
    suspend operator fun invoke(): LiftrixResult<UserAccount?> = liftrixCatching(
        errorMapper = { throwable ->
            when (throwable) {
                is LiftrixError -> throwable
                else -> LiftrixError.BusinessLogicError(
                    code = "GET_ACCOUNT_INFO_FAILED",
                    errorMessage = "Failed to retrieve account information: ${throwable.message}",
                    analyticsContext = mapOf(
                        "operation" to "GET_ACCOUNT_INFO",
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
                analyticsContext = mapOf("operation" to "GET_ACCOUNT_INFO")
            )
        
        val accountResult = userAccountRepository.getAccountInfoSuspend(userId)
        accountResult.fold(
            onSuccess = { accountInfo -> accountInfo },
            onFailure = { error -> throw error }
        )
    }
}