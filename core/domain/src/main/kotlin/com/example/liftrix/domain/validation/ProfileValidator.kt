package com.example.liftrix.domain.validation

import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.common.LiftrixResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ProfileValidator - Validates social profile data according to business rules
 * 
 * Validation Rules:
 * - Username: 3-30 chars, alphanumeric + underscore, unique
 * - Display name: 1-50 chars, no profanity
 * - Bio: 0-500 chars, no offensive content
 * 
 * Used by: CreateSocialProfileUseCase, UpdateSocialProfileUseCase
 */
@Singleton
class ProfileValidator @Inject constructor() {
    
    companion object {
        const val MIN_USERNAME_LENGTH = 3
        const val MAX_USERNAME_LENGTH = 30
        const val MAX_DISPLAY_NAME_LENGTH = 50
        const val MAX_BIO_LENGTH = 500
        
        private val USERNAME_REGEX = "^[a-zA-Z0-9_]+$".toRegex()
        private val RESERVED_USERNAMES = setOf(
            "admin", "root", "system", "liftrix", "support", "help", "api", "www",
            "mail", "email", "test", "demo", "user", "profile", "settings"
        )
    }
    
    fun validateUsername(username: String): LiftrixResult<Unit> {
        val trimmedUsername = username.trim()
        
        return when {
            trimmedUsername.isBlank() -> LiftrixResult.failure(
                LiftrixError.ValidationError(
                    field = "username",
                    violations = listOf("Username cannot be empty")
                )
            )
            trimmedUsername.length < MIN_USERNAME_LENGTH -> LiftrixResult.failure(
                LiftrixError.ValidationError(
                    field = "username", 
                    violations = listOf("Username must be at least $MIN_USERNAME_LENGTH characters")
                )
            )
            trimmedUsername.length > MAX_USERNAME_LENGTH -> LiftrixResult.failure(
                LiftrixError.ValidationError(
                    field = "username",
                    violations = listOf("Username cannot exceed $MAX_USERNAME_LENGTH characters")
                )
            )
            !USERNAME_REGEX.matches(trimmedUsername) -> LiftrixResult.failure(
                LiftrixError.ValidationError(
                    field = "username",
                    violations = listOf("Username can only contain letters, numbers, and underscores")
                )
            )
            RESERVED_USERNAMES.contains(trimmedUsername.lowercase()) -> LiftrixResult.failure(
                LiftrixError.ValidationError(
                    field = "username",
                    violations = listOf("This username is reserved")
                )
            )
            else -> LiftrixResult.success(Unit)
        }
    }
    
    fun validateDisplayName(displayName: String): LiftrixResult<Unit> {
        val trimmedName = displayName.trim()
        
        return when {
            trimmedName.isBlank() -> LiftrixResult.failure(
                LiftrixError.ValidationError(
                    field = "displayName",
                    violations = listOf("Display name cannot be empty")
                )
            )
            trimmedName.length > MAX_DISPLAY_NAME_LENGTH -> LiftrixResult.failure(
                LiftrixError.ValidationError(
                    field = "displayName", 
                    violations = listOf("Display name cannot exceed $MAX_DISPLAY_NAME_LENGTH characters")
                )
            )
            containsInappropriateContent(trimmedName) -> LiftrixResult.failure(
                LiftrixError.ValidationError(
                    field = "displayName",
                    violations = listOf("Display name contains inappropriate content")
                )
            )
            else -> LiftrixResult.success(Unit)
        }
    }
    
    fun validateBio(bio: String): LiftrixResult<Unit> {
        val trimmedBio = bio.trim()
        
        return when {
            trimmedBio.length > MAX_BIO_LENGTH -> LiftrixResult.failure(
                LiftrixError.ValidationError(
                    field = "bio",
                    violations = listOf("Bio cannot exceed $MAX_BIO_LENGTH characters")
                )
            )
            containsInappropriateContent(trimmedBio) -> LiftrixResult.failure(
                LiftrixError.ValidationError(
                    field = "bio",
                    violations = listOf("Bio contains inappropriate content")
                )
            )
            else -> LiftrixResult.success(Unit)
        }
    }
    
    private fun containsInappropriateContent(text: String): Boolean {
        // Basic profanity filter - in production this would use a more sophisticated system
        val flaggedWords = listOf(
            "spam", "scam", "fake", "bot", "admin", "moderator"
        )
        
        val lowercaseText = text.lowercase()
        return flaggedWords.any { word -> lowercaseText.contains(word) }
    }
}