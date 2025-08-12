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
     * Gets the current authenticated user's ID with enhanced cold-start handling
     * 
     * @return The user ID if authenticated, null otherwise
     */
    suspend operator fun invoke(): String? {
        timber.log.Timber.d("🔥 EDIT-WORKOUT-DEBUG: GetCurrentUserIdUseCase invoked")
        return try {
            // First try direct Firebase Auth check for immediate response
            val directUserId = authRepository.getCurrentUserId()
            if (directUserId != null) {
                timber.log.Timber.d("🔥 EDIT-WORKOUT-DEBUG: GetCurrentUserIdUseCase: Direct Firebase Auth returned: $directUserId")
                timber.log.Timber.d("GetCurrentUserIdUseCase: Direct Firebase Auth returned: $directUserId")
                return directUserId
            }
            
            timber.log.Timber.d("🔥 EDIT-WORKOUT-DEBUG: GetCurrentUserIdUseCase: Direct check returned null, trying reactive flow...")
            timber.log.Timber.d("GetCurrentUserIdUseCase: Direct check returned null, trying reactive flow...")
            
            // If direct check fails, wait for auth state flow with extended timeout for cold starts
            kotlinx.coroutines.withTimeout(20_000) { // Increased to 20 second timeout for cold starts
                val user = authRepository.currentUser.first { it != null } // Wait for non-null user
                timber.log.Timber.d("🔥 EDIT-WORKOUT-DEBUG: GetCurrentUserIdUseCase: Flow retrieved user ID: ${user?.uid}")
                timber.log.Timber.d("GetCurrentUserIdUseCase: Flow retrieved user ID: ${user?.uid}")
                user?.uid
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            timber.log.Timber.w("🔥 EDIT-WORKOUT-DEBUG: GetCurrentUserIdUseCase: Timeout waiting for auth state - final fallback")
            timber.log.Timber.w("GetCurrentUserIdUseCase: Timeout waiting for auth state - final fallback")
            // Final fallback to direct Firebase Auth check
            val fallbackUserId = authRepository.getCurrentUserId()
            timber.log.Timber.d("🔥 EDIT-WORKOUT-DEBUG: GetCurrentUserIdUseCase: Fallback returned: $fallbackUserId")
            timber.log.Timber.d("GetCurrentUserIdUseCase: Fallback returned: $fallbackUserId")
            fallbackUserId
        } catch (e: Exception) {
            timber.log.Timber.e("🔥 EDIT-WORKOUT-DEBUG: GetCurrentUserIdUseCase: Error getting current user ID - ${e.message}")
            timber.log.Timber.e(e, "GetCurrentUserIdUseCase: Error getting current user ID")
            null
        }
    }
} 