package com.example.liftrix.domain.usecase.auth

import com.example.liftrix.domain.repository.AuthRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case for getting the current authenticated user's ID
 */
class GetCurrentUserIdUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    
    /**
     * Gets the current authenticated user's ID
     * 
     * @return The user ID if authenticated, null otherwise
     */
    suspend operator fun invoke(): String? {
        return authRepository.currentUser.first()?.uid
    }
} 