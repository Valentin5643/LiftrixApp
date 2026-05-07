package com.example.liftrix.domain.model.validation

/**
 * Shared validation state for reusable UI and domain-facing validation helpers.
 */
sealed class ValidationResult {
    data object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}
