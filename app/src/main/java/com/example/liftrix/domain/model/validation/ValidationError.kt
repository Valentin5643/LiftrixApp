package com.example.liftrix.domain.model.validation

/**
 * Domain model for validation errors in business logic operations.
 * 
 * Represents validation failures that occur during domain operations such as
 * data integrity checks, business rule validation, and constraint verification.
 * This belongs in the domain layer as it represents core business validation logic.
 */
data class ValidationError(
    val field: String,
    val message: String,
    val severity: ValidationSeverity = ValidationSeverity.ERROR
)

/**
 * Severity levels for domain validation errors.
 * 
 * Represents the criticality of validation failures from a business perspective:
 * - WARNING: Non-blocking validation issue that should be noted
 * - ERROR: Blocking validation failure that prevents operation completion
 * - CRITICAL: System integrity issue that requires immediate attention
 */
enum class ValidationSeverity {
    WARNING,  // Non-blocking, informational
    ERROR,    // Blocking, must be fixed
    CRITICAL  // System integrity issue
}