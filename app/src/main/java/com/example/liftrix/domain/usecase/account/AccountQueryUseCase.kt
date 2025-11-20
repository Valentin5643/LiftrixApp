package com.example.liftrix.domain.usecase.account

import com.example.liftrix.domain.model.UserAccount
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.UserAccountRepository
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Consolidated query use case for account information retrieval.
 *
 * Replaces:
 * - GetAccountInfoUseCase.kt
 *
 * Provides both Flow-based and suspend-based access patterns for account information.
 *
 * @property userAccountRepository Repository for account data access
 * @property getCurrentUserIdUseCase Use case to get authenticated user ID
 */
class AccountQueryUseCase @Inject constructor(
    private val userAccountRepository: UserAccountRepository,
    private val authQueryUseCase: AuthQueryUseCase
) {

    /**
     * Gets the current user's account information as a Flow.
     * Replaces GetAccountInfoUseCase.asFlow()
     *
     * @return Flow of UserAccount or null if not found
     */
    suspend fun asFlow(): Flow<UserAccount?> {
        val userId = authQueryUseCase(waitForAuth = false).getOrNull()
        return if (userId != null) {
            userAccountRepository.getAccountInfo(userId)
        } else {
            flowOf(null)
        }
    }

    /**
     * Gets the current user's account information synchronously.
     * Replaces GetAccountInfoUseCase.invoke()
     *
     * @return LiftrixResult with UserAccount or null if not found
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
        val userId = authQueryUseCase(waitForAuth = false).getOrNull()
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
