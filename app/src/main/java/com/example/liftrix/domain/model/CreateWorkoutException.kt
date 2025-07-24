package com.example.liftrix.domain.model

/**
 * Exception hierarchy for simple workout creation errors
 */
sealed class CreateWorkoutException(message: String) : Exception(message) {
    object UserNotAuthenticated : CreateWorkoutException("User must be authenticated to create workouts")
    data class InvalidInput(val field: String, val reason: String) : CreateWorkoutException("Invalid $field: $reason")
    data class RepositoryError(override val cause: Throwable) : CreateWorkoutException("Database error: ${cause.message}")
    data class UnknownError(override val cause: Throwable) : CreateWorkoutException("Unknown error: ${cause.message}")
}