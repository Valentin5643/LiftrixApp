package com.example.liftrix.security

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixSuccess
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.core.identity.UserId
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UserIdValidator provides centralized validation for user context consistency
 * and prevents cross-user data access violations.
 * 
 * This validator ensures that:
 * - All data operations use validated Firebase user IDs
 * - Cross-user data isolation is maintained
 * - Unauthorized access attempts are prevented with proper error handling
 * 
 * @property firebaseAuth Firebase Authentication instance
 * @property authQueryUseCase Use case for retrieving current authenticated user ID
 */
@Singleton
class UserIdValidator @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val authQueryUseCase: AuthQueryUseCase
) {
    
    /**
     * Validates the current Firebase user and returns their ID.
     * 
     * @return LiftrixResult with the current user's ID, or error if not authenticated
     */
    suspend fun validateCurrentUser(): LiftrixResult<UserId> {
        return authQueryUseCase(waitForAuth = false).fold(
            onSuccess = { currentUserId ->
                // Verify Firebase auth state consistency
                val firebaseUser = firebaseAuth.currentUser
                if (firebaseUser?.uid != currentUserId.value) {
                    liftrixFailure(
                        LiftrixError.AuthenticationError(
                            errorMessage = "Firebase auth state inconsistent with user session"
                        )
                    )
                } else {
                    liftrixSuccess(currentUserId)
                }
            },
            onFailure = { error ->
                liftrixFailure(
                    if (error is LiftrixError) error
                    else LiftrixError.AuthenticationError(
                        errorMessage = "Failed to get user ID: ${error.message}"
                    )
                )
            }
        )
    }
    
    /**
     * Validates that the requested user ID matches the current authenticated user.
     * This prevents unauthorized access to other users' data.
     * 
     * @param requestedUserId The user ID being requested for data access
     * @param operation Description of the operation being performed (for logging/debugging)
     * @return LiftrixResult indicating whether access is authorized
     */
    suspend fun validateUserContext(
        requestedUserId: String,
        operation: String
    ): LiftrixResult<Unit> {
        return validateCurrentUser().fold(
            onSuccess = { currentUserId ->
                if (currentUserId.value == requestedUserId) {
                    liftrixSuccess(Unit)
                } else {
                    liftrixFailure(
                        LiftrixError.AuthenticationError(
                            errorMessage = "User ${currentUserId.value} cannot access data for $requestedUserId in operation: $operation"
                        )
                    )
                }
            },
            onFailure = { error ->
                // Pass through the authentication error
                liftrixFailure(error as LiftrixError)
            }
        )
    }
    
    /**
     * Validates user ID format and ensures it's not a placeholder value.
     * 
     * @param userId The user ID to validate
     * @return LiftrixResult indicating whether the user ID is valid
     */
    fun validateUserIdFormat(userId: String): LiftrixResult<Unit> {
        return when {
            userId.isBlank() -> {
                liftrixFailure(
                    LiftrixError.ValidationError(
                        field = "userId",
                        violations = listOf("User ID cannot be blank")
                    )
                )
            }
            userId == "current_user" -> {
                liftrixFailure(
                    LiftrixError.ValidationError(
                        field = "userId",
                        violations = listOf("Hardcoded 'current_user' placeholder detected. Use proper Firebase user ID.")
                    )
                )
            }
            userId.length < 20 -> {
                liftrixFailure(
                    LiftrixError.ValidationError(
                        field = "userId",
                        violations = listOf("User ID appears to be invalid (too short for Firebase UID)")
                    )
                )
            }
            else -> {
                liftrixSuccess(Unit)
            }
        }
    }
    
    /**
     * Convenience method to get the current authenticated user ID with validation.
     *
     * @return The current user's ID if authenticated and valid, null otherwise
     */
    suspend fun getCurrentValidatedUserId(): UserId? {
        return validateCurrentUser().getOrNull()
    }
    
    /**
     * Checks if the current user has proper authentication state.
     * 
     * @return true if user is properly authenticated, false otherwise
     */
    suspend fun isCurrentUserAuthenticated(): Boolean {
        return validateCurrentUser().isSuccess
    }
}