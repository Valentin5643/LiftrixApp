package com.example.liftrix.domain.usecase.auth

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.AuthRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * Consolidated auth query use case for retrieving authenticated user information.
 *
 * Consolidates:
 * - GetCurrentUserIdUseCase (with cold-start handling)
 * - GetAuthenticatedUserIdUseCase (simple auth check)
 *
 * Provides unified authentication query with configurable wait behavior:
 * - waitForAuth=false: Quick check, returns immediately
 * - waitForAuth=true: Waits for auth initialization (cold-start support)
 *
 * Architecture:
 * - Query operations only (no mutations)
 * - LiftrixResult<T> pattern for error handling
 * - Preserves cold-start timeout logic from GetCurrentUserIdUseCase
 * - No debug logging (production-ready)
 */
class AuthQueryUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {

    /**
     * Gets the current authenticated user's ID.
     *
     * @param waitForAuth If true, waits up to 20 seconds for auth initialization (cold-start).
     *                    If false, returns immediately with current auth state.
     * @return LiftrixResult containing user ID or error
     */
    suspend operator fun invoke(waitForAuth: Boolean = false): LiftrixResult<String> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.AuthenticationError(
                errorMessage = "Failed to get user ID: ${throwable.message}",
                errorCode = "AUTH_QUERY_FAILED",
                analyticsContext = mapOf(
                    "operation" to "GET_CURRENT_USER_ID",
                    "waitForAuth" to waitForAuth.toString()
                )
            )
        }
    ) {
        if (waitForAuth) {
            // Cold-start handling: wait for auth state with extended timeout
            getUserIdWithWait()
        } else {
            // Quick check: return immediately
            getUserIdImmediate()
        }
    }

    /**
     * Gets user ID immediately without waiting for auth initialization.
     *
     * @return User ID if authenticated
     * @throws Exception if user is not authenticated
     */
    private suspend fun getUserIdImmediate(): String {
        val userId = authRepository.getCurrentUserId()
        return userId ?: throw LiftrixError.AuthenticationError(
            errorMessage = "User not authenticated",
            errorCode = "USER_NOT_AUTHENTICATED"
        )
    }

    /**
     * Gets user ID with wait logic for cold-start scenarios.
     *
     * Preserves the enhanced cold-start handling from GetCurrentUserIdUseCase:
     * 1. Try direct Firebase Auth check first
     * 2. If null, wait for auth state flow (20s timeout)
     * 3. On timeout, final fallback to direct check
     *
     * @return User ID if authenticated
     * @throws Exception if user is not authenticated after wait
     */
    private suspend fun getUserIdWithWait(): String {
        // First try direct Firebase Auth check for immediate response
        val directUserId = authRepository.getCurrentUserId()
        if (directUserId != null) {
            return directUserId
        }

        return try {
            // Wait for auth state flow with extended timeout for cold starts
            withTimeout(20_000) { // 20 second timeout for cold starts
                val user = authRepository.currentUser.first { it != null }
                user?.uid ?: throw LiftrixError.AuthenticationError(
                    errorMessage = "User not authenticated after wait",
                    errorCode = "USER_NOT_AUTHENTICATED_AFTER_WAIT"
                )
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            // Final fallback to direct Firebase Auth check
            val fallbackUserId = authRepository.getCurrentUserId()
            fallbackUserId ?: throw LiftrixError.AuthenticationError(
                errorMessage = "Timeout waiting for auth state",
                errorCode = "AUTH_TIMEOUT",
                analyticsContext = mapOf("timeout_ms" to "20000")
            )
        }
    }
}
