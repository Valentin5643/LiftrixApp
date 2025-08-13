package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.social.SocialProfileRepository
import com.example.liftrix.domain.validation.ProfileValidator
import javax.inject.Inject

/**
 * Use case for checking username availability with validation.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 */
class CheckUsernameAvailabilityUseCase @Inject constructor(
    private val repository: SocialProfileRepository,
    private val validator: ProfileValidator
) {
    /**
     * Checks if a username is available for registration.
     * 
     * @param username The username to check
     * @return true if available, false if taken
     */
    suspend operator fun invoke(username: String): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.ValidationError(
                field = "username",
                violations = listOf(throwable.message ?: "Failed to check username availability"),
                errorMessage = "Username validation failed",
                analyticsContext = mapOf(
                    "username" to username,
                    "operation" to "CHECK_USERNAME_AVAILABILITY",
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        // Validate username format first
        validator.validateUsername(username).getOrThrow()
        
        // Check availability in repository
        repository.checkUsernameAvailability(username).getOrThrow()
    }
}