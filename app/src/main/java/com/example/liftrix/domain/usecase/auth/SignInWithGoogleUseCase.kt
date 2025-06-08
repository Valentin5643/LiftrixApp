package com.example.liftrix.domain.usecase.auth

import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.repository.AuthRepository
import javax.inject.Inject

class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(idToken: String): Result<User> {
        if (idToken.isBlank()) {
            return Result.failure(IllegalArgumentException("Google ID token cannot be blank"))
        }
        
        return authRepository.signInWithGoogle(idToken)
    }
} 