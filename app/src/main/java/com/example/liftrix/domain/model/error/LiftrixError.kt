package com.example.liftrix.domain.model.error

import kotlinx.serialization.Serializable

/**
 * Sealed class hierarchy for domain-specific errors with recovery mechanisms and analytics context.
 * 
 * Provides unified error handling across the Liftrix application with support for:
 * - Recovery information for automated retry logic
 * - Analytics context for error tracking and reporting
 * - Serialization for crash reporting integration
 * - Type-safe error categorization
 */
sealed class LiftrixError(
    override val message: String,
    open val isRecoverable: Boolean = false,
    open val retryAfter: Long? = null,
    open val analyticsContext: Map<String, String> = emptyMap()
) : Throwable(message) {
    
    /**
     * Network connectivity and communication errors.
     * Typically recoverable with appropriate retry logic.
     */
    data class NetworkError(
        val errorMessage: String = "Network connection failed",
        override val isRecoverable: Boolean = true,
        override val retryAfter: Long? = 3000L, // 3 seconds default retry
        override val analyticsContext: Map<String, String> = emptyMap(),
        val networkType: String? = null,
        val httpStatusCode: Int? = null
    ) : LiftrixError(errorMessage, isRecoverable, retryAfter, analyticsContext)
    
    /**
     * Input validation and constraint violations.
     * Usually recoverable with user correction.
     */
    data class ValidationError(
        val field: String,
        val violations: List<String>,
        val errorMessage: String = "Validation failed for $field",
        override val isRecoverable: Boolean = true,
        override val retryAfter: Long? = null, // No automatic retry for validation
        override val analyticsContext: Map<String, String> = emptyMap()
    ) : LiftrixError(errorMessage, isRecoverable, retryAfter, analyticsContext)
    
    /**
     * Authentication and authorization failures.
     * May be recoverable depending on the specific auth issue.
     */
    data class AuthenticationError(
        val errorMessage: String = "Authentication failed",
        override val isRecoverable: Boolean = false, // Usually requires user action
        override val retryAfter: Long? = null,
        override val analyticsContext: Map<String, String> = emptyMap(),
        val authProvider: String? = null,
        val errorCode: String? = null
    ) : LiftrixError(errorMessage, isRecoverable, retryAfter, analyticsContext)
    
    /**
     * Database operations and persistence errors.
     * Some database errors are recoverable (transaction conflicts, locks).
     */
    data class DatabaseError(
        val errorMessage: String = "Database operation failed",
        override val isRecoverable: Boolean = true,
        override val retryAfter: Long? = 1000L, // 1 second retry for DB conflicts
        override val analyticsContext: Map<String, String> = emptyMap(),
        val operation: String? = null,
        val table: String? = null,
        val sqlErrorCode: Int? = null
    ) : LiftrixError(errorMessage, isRecoverable, retryAfter, analyticsContext)
    
    /**
     * Business logic violations and domain constraint errors.
     * Typically not automatically recoverable - requires business logic resolution.
     */
    data class BusinessLogicError(
        val code: String,
        val errorMessage: String = "Business logic violation: $code",
        override val isRecoverable: Boolean = false,
        override val retryAfter: Long? = null,
        override val analyticsContext: Map<String, String> = emptyMap()
    ) : LiftrixError(errorMessage, isRecoverable, retryAfter, analyticsContext)
    
    /**
     * Analytics calculation and processing errors.
     * May be recoverable depending on the specific calculation issue.
     */
    data class CalculationError(
        val errorMessage: String = "Analytics calculation failed",
        override val isRecoverable: Boolean = true,
        override val retryAfter: Long? = 2000L, // 2 seconds retry for calculations
        override val analyticsContext: Map<String, String> = emptyMap(),
        val operation: String? = null
    ) : LiftrixError(errorMessage, isRecoverable, retryAfter, analyticsContext)
    
    /**
     * Data retrieval and fetching errors.
     * May be recoverable depending on the specific retrieval issue.
     */
    data class DataRetrievalError(
        val errorMessage: String = "Data retrieval failed",
        override val isRecoverable: Boolean = true,
        override val retryAfter: Long? = 2000L, // 2 seconds retry for data retrieval
        override val analyticsContext: Map<String, String> = emptyMap(),
        val operation: String? = null,
        val retryable: Boolean = true
    ) : LiftrixError(errorMessage, isRecoverable, retryAfter, analyticsContext)
    
    /**
     * Configuration and settings errors.
     * May be recoverable depending on the specific configuration issue.
     */
    data class ConfigurationError(
        val errorMessage: String = "Configuration error",
        override val isRecoverable: Boolean = true,
        override val retryAfter: Long? = 1000L, // 1 second retry for configuration
        override val analyticsContext: Map<String, String> = emptyMap(),
        val configKey: String? = null,
        val configValue: String? = null
    ) : LiftrixError(errorMessage, isRecoverable, retryAfter, analyticsContext)
    
    /**
     * Data export and file generation errors.
     * May be recoverable depending on the specific export issue.
     */
    data class ExportError(
        val errorMessage: String = "Data export failed",
        override val isRecoverable: Boolean = true,
        override val retryAfter: Long? = 3000L, // 3 seconds retry for exports
        override val analyticsContext: Map<String, String> = emptyMap(),
        val operation: String? = null,
        val format: String? = null
    ) : LiftrixError(errorMessage, isRecoverable, retryAfter, analyticsContext)
    
    /**
     * File system operations and storage errors.
     * May be recoverable depending on the specific file system issue.
     */
    data class FileSystemError(
        val errorMessage: String = "File system operation failed",
        override val isRecoverable: Boolean = true,
        override val retryAfter: Long? = 1000L, // 1 second retry for file operations
        override val analyticsContext: Map<String, String> = emptyMap(),
        val operation: String? = null,
        val filePath: String? = null
    ) : LiftrixError(errorMessage, isRecoverable, retryAfter, analyticsContext)
    
    /**
     * Resource not found errors.
     * Typically not recoverable unless the resource is created.
     */
    data class NotFoundError(
        val errorMessage: String = "Resource not found",
        override val isRecoverable: Boolean = false,
        override val retryAfter: Long? = null,
        override val analyticsContext: Map<String, String> = emptyMap(),
        val resourceType: String? = null,
        val resourceId: String? = null
    ) : LiftrixError(errorMessage, isRecoverable, retryAfter, analyticsContext)
    
    /**
     * Unknown or unexpected errors that don't fit other categories.
     * Marked as non-recoverable by default for safety.
     */
    data class UnknownError(
        val errorMessage: String = "An unexpected error occurred",
        override val isRecoverable: Boolean = false,
        override val retryAfter: Long? = null,
        override val analyticsContext: Map<String, String> = emptyMap()
    ) : LiftrixError(errorMessage, isRecoverable, retryAfter, analyticsContext)
}

/**
 * Extension functions for enhanced error handling and analytics integration
 */

/**
 * Creates a copy of this error with additional analytics context
 */
fun LiftrixError.withAnalyticsContext(additionalContext: Map<String, String>): LiftrixError {
    val mergedContext = analyticsContext + additionalContext
    return when (this) {
        is LiftrixError.NetworkError -> copy(analyticsContext = mergedContext)
        is LiftrixError.ValidationError -> copy(analyticsContext = mergedContext)
        is LiftrixError.AuthenticationError -> copy(analyticsContext = mergedContext)
        is LiftrixError.DatabaseError -> copy(analyticsContext = mergedContext)
        is LiftrixError.BusinessLogicError -> copy(analyticsContext = mergedContext)
        is LiftrixError.CalculationError -> copy(analyticsContext = mergedContext)
        is LiftrixError.DataRetrievalError -> copy(analyticsContext = mergedContext)
        is LiftrixError.ConfigurationError -> copy(analyticsContext = mergedContext)
        is LiftrixError.ExportError -> copy(analyticsContext = mergedContext)
        is LiftrixError.FileSystemError -> copy(analyticsContext = mergedContext)
        is LiftrixError.NotFoundError -> copy(analyticsContext = mergedContext)
        is LiftrixError.UnknownError -> copy(analyticsContext = mergedContext)
    }
}

/**
 * Creates a copy of this error with retry timing information
 */
fun LiftrixError.withRetryAfter(retryAfterMs: Long): LiftrixError {
    return when (this) {
        is LiftrixError.NetworkError -> copy(retryAfter = retryAfterMs)
        is LiftrixError.ValidationError -> copy(retryAfter = retryAfterMs)
        is LiftrixError.AuthenticationError -> copy(retryAfter = retryAfterMs)
        is LiftrixError.DatabaseError -> copy(retryAfter = retryAfterMs)
        is LiftrixError.BusinessLogicError -> copy(retryAfter = retryAfterMs)
        is LiftrixError.CalculationError -> copy(retryAfter = retryAfterMs)
        is LiftrixError.DataRetrievalError -> copy(retryAfter = retryAfterMs)
        is LiftrixError.ConfigurationError -> copy(retryAfter = retryAfterMs)
        is LiftrixError.ExportError -> copy(retryAfter = retryAfterMs)
        is LiftrixError.FileSystemError -> copy(retryAfter = retryAfterMs)
        is LiftrixError.NotFoundError -> copy(retryAfter = retryAfterMs)
        is LiftrixError.UnknownError -> copy(retryAfter = retryAfterMs)
    }
}

/**
 * Determines if this error should be retried based on current attempt count
 */
fun LiftrixError.shouldRetry(attemptCount: Int, maxAttempts: Int = 3): Boolean {
    return isRecoverable && attemptCount < maxAttempts
}