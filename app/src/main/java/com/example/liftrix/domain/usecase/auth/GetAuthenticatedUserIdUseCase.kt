package com.example.liftrix.domain.usecase.auth

import com.example.liftrix.domain.repository.AuthRepository
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Use case for getting the current authenticated user's ID, waiting for authentication if needed
 * 
 * Unlike GetCurrentUserIdUseCase which returns null immediately if not authenticated,
 * this use case waits for authentication to complete before returning the user ID.
 */
class GetAuthenticatedUserIdUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    
    /**
     * Gets the current authenticated user's ID, waiting for authentication if needed
     * 
     * This method will suspend until a user is authenticated. It's designed to solve
     * the timing issue where ViewModels attempt to load data before authentication
     * completes during app startup.
     * 
     * @return The user ID once authentication is confirmed
     * @throws IllegalStateException if authentication fails or user is not found
     */
    suspend operator fun invoke(): String {
        return authRepository.currentUser
            .filterNotNull() // Wait for non-null user (authenticated state)
            .map { user -> user.uid }
            .first() // Take the first authenticated user ID
    }
}