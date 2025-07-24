package com.example.liftrix.domain.usecase.auth

import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.repository.AuthRepository
import javax.inject.Inject

class SignUpWithEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String, displayName: String): Result<User> {
        if (email.isBlank()) {
            return Result.failure(IllegalArgumentException("Email cannot be blank"))
        }
        
        if (password.isBlank()) {
            return Result.failure(IllegalArgumentException("Password cannot be blank"))
        }
        
        if (displayName.isBlank()) {
            return Result.failure(IllegalArgumentException("Display name cannot be blank"))
        }
        
        if (!isValidEmail(email)) {
            return Result.failure(IllegalArgumentException("Invalid email format"))
        }
        
        if (!isValidPassword(password)) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters long"))
        }
        
        return authRepository.signUpWithEmail(email, password, displayName)
    }
    
    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
    
    private fun isValidPassword(password: String): Boolean {
        return password.length >= 6
    }
} 