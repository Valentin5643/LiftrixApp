package com.example.liftrix.domain.model.validation

/**
 * Field-level validation failure used by shared validation primitives.
 */
data class ValidationError(
    val field: String,
    val message: String,
    val code: String? = null,
    val severity: ValidationSeverity = ValidationSeverity.ERROR
)

enum class ValidationSeverity {
    WARNING,
    ERROR,
    CRITICAL
}
