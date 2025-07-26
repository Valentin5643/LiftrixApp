package com.example.liftrix.domain.usecase.auth

import com.example.liftrix.domain.repository.AuthRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case for getting the current authenticated user's ID with timeout and retry logic
 */
class GetCurrentUserIdUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    
    /**
     * Gets the current authenticated user's ID with timeout protection
     * 
     * @return The user ID if authenticated, null otherwise
     */
    suspend operator fun invoke(): String? {
        return try {
            // Add timeout to prevent infinite waiting
            kotlinx.coroutines.withTimeout(15_000) { // 15 second timeout
                val user = authRepository.currentUser.first()
                timber.log.Timber.d("GetCurrentUserIdUseCase: Retrieved user ID: ${user?.uid}")
                user?.uid
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            timber.log.Timber.w("GetCurrentUserIdUseCase: Timeout waiting for auth state - falling back to direct check")
            // Fallback to direct Firebase Auth check
            authRepository.getCurrentUserId()
        } catch (e: Exception) {
            timber.log.Timber.e(e, "GetCurrentUserIdUseCase: Error getting current user ID")
            null
        }
    }
} 