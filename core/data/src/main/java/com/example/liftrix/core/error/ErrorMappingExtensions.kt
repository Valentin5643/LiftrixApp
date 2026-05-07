package com.example.liftrix.core.error

import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.error.withAnalyticsContext
import kotlinx.datetime.Clock
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.io.IOException
import java.sql.SQLException
import java.io.FileNotFoundException

/**
 * Extension functions for common exception types with context-aware error mapping.
 * 
 * This file provides extension functions that map common exception types to appropriate
 * LiftrixError instances with preserved context and recovery information. These extensions
 * build on the existing error handling infrastructure to provide type-safe error mapping
 * with consistent analytics context.
 * 
 * Features:
 * - Context-aware error mapping for all common exception types
 * - Consistent error message formatting and recovery information
 * - Integration with existing error handling utilities
 * - Type-safe extension functions for better API ergonomics
 * 
 * Usage:
 * ```
 * val error = networkException.toLiftrixError(
 *     context = mapOf("operation" to "fetch_workout", "user_id" to "123")
 * )
 * ```
 */

/**
 * Maps NetworkException to LiftrixError.NetworkError with context preservation.
 * 
 * Transforms network-related exceptions to LiftrixError.NetworkError with appropriate
 * recovery information, retry timing, and analytics context. This function handles
 * common network failure scenarios with intelligent retry strategies.
 * 
 * @param context Additional context information for error tracking
 * @return LiftrixError.NetworkError with network-specific error details
 * 
 * Example:
 * ```
 * val error = socketTimeoutException.toLiftrixError(
 *     context = mapOf("endpoint" to "api/workouts", "timeout" to "30s")
 * )
 * ```
 */
fun SocketTimeoutException.toLiftrixError(context: Map<String, String> = emptyMap()): LiftrixError.NetworkError {
    val errorContext = createErrorContext(
        operation = "network_timeout",
        additionalContext = context + mapOf(
            "network_error_type" to "timeout",
            "timeout_reason" to (this.message ?: "unknown"),
            "exception_type" to "SocketTimeoutException"
        )
    )
    return LiftrixError.NetworkError(
        errorMessage = "Connection timed out: ${this.message}",
        isRecoverable = true,
        retryAfter = 5000L, // 5 seconds for timeout errors
        analyticsContext = errorContext,
        networkType = "TCP",
        httpStatusCode = 408
    )
}

/**
 * Maps UnknownHostException to LiftrixError.NetworkError with DNS-specific context.
 * 
 * Transforms DNS resolution failures to LiftrixError.NetworkError with appropriate
 * recovery information and DNS-specific context for better debugging and analytics.
 * 
 * @param context Additional context information for error tracking
 * @return LiftrixError.NetworkError with DNS error details
 * 
 * Example:
 * ```
 * val error = unknownHostException.toLiftrixError(
 *     context = mapOf("hostname" to "api.liftrix.com")
 * )
 * ```
 */
fun UnknownHostException.toLiftrixError(context: Map<String, String> = emptyMap()): LiftrixError.NetworkError {
    val errorContext = createErrorContext(
        operation = "dns_resolution",
        additionalContext = context + mapOf(
            "network_error_type" to "dns_resolution",
            "hostname" to (this.message ?: "unknown"),
            "exception_type" to "UnknownHostException"
        )
    )
    return LiftrixError.NetworkError(
        errorMessage = "Unable to resolve hostname: ${this.message}",
        isRecoverable = true,
        retryAfter = 3000L, // 3 seconds for DNS errors
        analyticsContext = errorContext,
        networkType = "DNS",
        httpStatusCode = null
    )
}

/**
 * Maps IOException to LiftrixError.NetworkError with I/O-specific context.
 * 
 * Transforms general I/O exceptions to LiftrixError.NetworkError with appropriate
 * recovery information and I/O-specific context for network operations.
 * 
 * @param context Additional context information for error tracking
 * @return LiftrixError.NetworkError with I/O error details
 * 
 * Example:
 * ```
 * val error = ioException.toLiftrixError(
 *     context = mapOf("stream_type" to "http_response")
 * )
 * ```
 */
fun IOException.toLiftrixError(context: Map<String, String> = emptyMap()): LiftrixError.NetworkError {
    val errorContext = createErrorContext(
        operation = "io_operation",
        additionalContext = context + mapOf(
            "network_error_type" to "io_error",
            "io_error_type" to (this::class.simpleName ?: "IOException"),
            "exception_type" to "IOException"
        )
    )
    return LiftrixError.NetworkError(
        errorMessage = "Network I/O error: ${this.message}",
        isRecoverable = true,
        retryAfter = 2000L, // 2 seconds for I/O errors
        analyticsContext = errorContext,
        networkType = "I/O",
        httpStatusCode = null
    )
}

/**
 * Maps SQLException to LiftrixError.DatabaseError with SQL-specific context.
 * 
 * Transforms SQL database exceptions to LiftrixError.DatabaseError with appropriate
 * recovery information based on SQL error codes and database-specific context.
 * 
 * @param context Additional context information for error tracking
 * @return LiftrixError.DatabaseError with SQL error details
 * 
 * Example:
 * ```
 * val error = sqlException.toLiftrixError(
 *     context = mapOf("table" to "workouts", "operation" to "INSERT")
 * )
 * ```
 */
fun SQLException.toLiftrixError(context: Map<String, String> = emptyMap()): LiftrixError.DatabaseError {
    val sqlErrorCode = this.errorCode
    val isRecoverable = when (sqlErrorCode) {
        1062, 1451, 1452 -> false // Constraint violations are not recoverable
        else -> true // Other SQL errors might be transient
    }
    
    val operation = context["operation"] ?: "unknown"
    // Separate analytics context from domain operation to avoid conflicts
    val analyticsContextData = context.filterNot { it.key == "operation" } + mapOf(
        "sql_error_code" to sqlErrorCode.toString(),
        "sql_state" to (this.sqlState ?: "unknown"),
        "database_error_type" to "sql_exception",
        "exception_type" to "SQLException"
    )
    val errorContext = createErrorContext(
        operation = "database_operation",
        additionalContext = analyticsContextData
    )
    
    return LiftrixError.DatabaseError(
        errorMessage = "Database operation failed: ${this.message}",
        isRecoverable = isRecoverable,
        retryAfter = if (isRecoverable) 1000L else null,
        analyticsContext = errorContext,
        operation = operation,
        table = context["table"],
        sqlErrorCode = sqlErrorCode
    )
}

/**
 * Maps IllegalArgumentException to LiftrixError.ValidationError with validation context.
 * 
 * Transforms illegal argument exceptions to LiftrixError.ValidationError with
 * field-specific validation information and recovery guidance.
 * 
 * @param context Additional context information for error tracking
 * @return LiftrixError.ValidationError with validation error details
 * 
 * Example:
 * ```
 * val error = illegalArgumentException.toLiftrixError(
 *     context = mapOf("field" to "workout_name", "value" to "invalid_name")
 * )
 * ```
 */
fun IllegalArgumentException.toLiftrixError(context: Map<String, String> = emptyMap()): LiftrixError.ValidationError {
    val field = context["field"] ?: "unknown_field"
    val errorContext = createErrorContext(
        operation = "validation",
        additionalContext = context + mapOf(
            "validation_error_type" to "illegal_argument",
            "field_name" to field,
            "argument_error" to (this.message ?: "unknown"),
            "exception_type" to "IllegalArgumentException"
        )
    )
    
    return LiftrixError.ValidationError(
        field = field,
        violations = listOf(this.message ?: "Invalid argument"),
        errorMessage = "Validation failed for $field: ${this.message}",
        isRecoverable = true,
        retryAfter = null, // No automatic retry for validation errors
        analyticsContext = errorContext
    )
}

/**
 * Maps IllegalStateException to LiftrixError.BusinessLogicError with state context.
 * 
 * Transforms illegal state exceptions to LiftrixError.BusinessLogicError with
 * business logic violation information and context for debugging.
 * 
 * @param context Additional context information for error tracking
 * @return LiftrixError.BusinessLogicError with business logic error details
 * 
 * Example:
 * ```
 * val error = illegalStateException.toLiftrixError(
 *     context = mapOf("state" to "workout_session", "expected" to "ACTIVE")
 * )
 * ```
 */
fun IllegalStateException.toLiftrixError(context: Map<String, String> = emptyMap()): LiftrixError.BusinessLogicError {
    val errorContext = createErrorContext(
        operation = "business_logic",
        additionalContext = context + mapOf(
            "business_error_type" to "illegal_state",
            "state_error" to (this.message ?: "unknown"),
            "exception_type" to "IllegalStateException"
        )
    )
    
    return LiftrixError.BusinessLogicError(
        code = "INVALID_STATE",
        errorMessage = "Invalid application state: ${this.message}",
        isRecoverable = false,
        retryAfter = null,
        analyticsContext = errorContext
    )
}

/**
 * Maps SecurityException to LiftrixError.AuthenticationError with security context.
 * 
 * Transforms security exceptions to LiftrixError.AuthenticationError with
 * authentication and authorization specific information.
 * 
 * @param context Additional context information for error tracking
 * @return LiftrixError.AuthenticationError with security error details
 * 
 * Example:
 * ```
 * val error = securityException.toLiftrixError(
 *     context = mapOf("permission" to "WRITE_EXTERNAL_STORAGE")
 * )
 * ```
 */
fun SecurityException.toLiftrixError(context: Map<String, String> = emptyMap()): LiftrixError.AuthenticationError {
    val errorContext = createErrorContext(
        operation = "security_check",
        additionalContext = context + mapOf(
            "security_error_type" to "permission_denied",
            "permission_error" to (this.message ?: "unknown"),
            "exception_type" to "SecurityException"
        )
    )
    
    return LiftrixError.AuthenticationError(
        errorMessage = "Permission denied: ${this.message}",
        isRecoverable = false,
        retryAfter = null,
        analyticsContext = errorContext,
        authProvider = "Android",
        errorCode = "PERMISSION_DENIED"
    )
}

/**
 * Maps FileNotFoundException to LiftrixError.FileSystemError with file context.
 * 
 * Transforms file not found exceptions to LiftrixError.FileSystemError with
 * file system specific information and path context.
 * 
 * @param context Additional context information for error tracking
 * @return LiftrixError.FileSystemError with file system error details
 * 
 * Example:
 * ```
 * val error = fileNotFoundException.toLiftrixError(
 *     context = mapOf("file_path" to "/path/to/file", "operation" to "READ")
 * )
 * ```
 */
fun FileNotFoundException.toLiftrixError(context: Map<String, String> = emptyMap()): LiftrixError.FileSystemError {
    val operation = context["operation"] ?: "READ"
    // Separate analytics context from domain operation to avoid conflicts
    val analyticsContextData = context.filterNot { it.key == "operation" } + mapOf(
        "file_error_type" to "file_not_found",
        "file_path" to (this.message ?: "unknown"),
        "exception_type" to "FileNotFoundException"
    )
    val errorContext = createErrorContext(
        operation = "file_system",
        additionalContext = analyticsContextData
    )
    
    return LiftrixError.FileSystemError(
        errorMessage = "File not found: ${this.message}",
        isRecoverable = false,
        retryAfter = null,
        analyticsContext = errorContext,
        operation = operation,
        filePath = this.message
    )
}

/**
 * Maps ArithmeticException to LiftrixError.CalculationError with calculation context.
 * 
 * Transforms arithmetic exceptions to LiftrixError.CalculationError with
 * calculation-specific information and retry capabilities.
 * 
 * @param context Additional context information for error tracking
 * @return LiftrixError.CalculationError with calculation error details
 * 
 * Example:
 * ```
 * val error = arithmeticException.toLiftrixError(
 *     context = mapOf("calculation" to "calorie_calculation", "operation" to "division")
 * )
 * ```
 */
fun ArithmeticException.toLiftrixError(context: Map<String, String> = emptyMap()): LiftrixError.CalculationError {
    val operation = context["operation"] ?: "unknown"
    // Separate analytics context from domain operation to avoid conflicts
    val analyticsContextData = context.filterNot { it.key == "operation" } + mapOf(
        "calculation_error_type" to "arithmetic_error",
        "arithmetic_operation" to (this.message ?: "unknown"),
        "exception_type" to "ArithmeticException"
    )
    val errorContext = createErrorContext(
        operation = "calculation",
        additionalContext = analyticsContextData
    )
    
    return LiftrixError.CalculationError(
        errorMessage = "Calculation error: ${this.message}",
        isRecoverable = true,
        retryAfter = 1000L, // 1 second retry for calculation errors
        analyticsContext = errorContext,
        operation = operation
    )
}

/**
 * Maps NumberFormatException to LiftrixError.CalculationError with format context.
 * 
 * Transforms number format exceptions to LiftrixError.CalculationError with
 * number formatting specific information and context.
 * 
 * @param context Additional context information for error tracking
 * @return LiftrixError.CalculationError with number format error details
 * 
 * Example:
 * ```
 * val error = numberFormatException.toLiftrixError(
 *     context = mapOf("input_value" to "invalid_number", "expected_type" to "Double")
 * )
 * ```
 */
fun NumberFormatException.toLiftrixError(context: Map<String, String> = emptyMap()): LiftrixError.CalculationError {
    val errorContext = createErrorContext(
        operation = "number_parsing",
        additionalContext = context + mapOf(
            "calculation_error_type" to "number_format_error",
            "invalid_number" to (this.message ?: "unknown"),
            "exception_type" to "NumberFormatException"
        )
    )
    
    return LiftrixError.CalculationError(
        errorMessage = "Invalid number format: ${this.message}",
        isRecoverable = true,
        retryAfter = null, // No automatic retry for format errors
        analyticsContext = errorContext,
        operation = context["operation"] ?: "number_parsing"
    )
}

/**
 * Maps any Throwable to an appropriate LiftrixError using comprehensive error mapping.
 * 
 * This extension function provides a catch-all error mapping that handles any Throwable
 * by leveraging the existing mapExceptionToLiftrixError function with enhanced context
 * creation and error type detection.
 * 
 * @param operation Description of the operation that failed
 * @param context Additional context information for error tracking
 * @return LiftrixError with appropriate type and context
 * 
 * Example:
 * ```
 * val error = throwable.toLiftrixError(
 *     operation = "workout_creation",
 *     context = mapOf("user_id" to "123", "template_id" to "456")
 * )
 * ```
 */
fun Throwable.toLiftrixError(
    operation: String,
    context: Map<String, String> = emptyMap()
): LiftrixError {
    val errorContext = createErrorContext(
        operation = operation,
        additionalContext = context + mapOf(
            "error_mapping_source" to "extension_function",
            "original_exception" to (this::class.simpleName ?: "UnknownThrowable")
        )
    )
    
    return mapExceptionToLiftrixError(
        exception = this,
        operation = operation,
        context = errorContext
    )
}

/**
 * Creates a LiftrixError from an exception with enhanced context and operation information.
 * 
 * This function provides a more ergonomic way to create LiftrixError instances from
 * exceptions with enhanced context creation and operation tracking.
 * 
 * @param exception The exception to map
 * @param operation Description of the operation that failed
 * @param userId Optional user ID for user-scoped error tracking
 * @param additionalContext Additional context information
 * @return LiftrixError with comprehensive context and error information
 * 
 * Example:
 * ```
 * val error = createLiftrixError(
 *     exception = networkException,
 *     operation = "fetch_user_data",
 *     userId = "user_123",
 *     additionalContext = mapOf("endpoint" to "/api/users")
 * )
 * ```
 */
fun createLiftrixError(
    exception: Throwable,
    operation: String,
    userId: String? = null,
    additionalContext: Map<String, String> = emptyMap()
): LiftrixError {
    val errorContext = createErrorContext(
        operation = operation,
        userId = userId,
        additionalContext = additionalContext + mapOf(
            "error_creation_source" to "createLiftrixError",
            "exception_type" to (exception::class.simpleName ?: "UnknownThrowable")
        )
    )
    
    return mapExceptionToLiftrixError(
        exception = exception,
        operation = operation,
        context = errorContext
    )
}

/**
 * Extension function to enhance existing LiftrixError with additional context.
 * 
 * This function allows adding context to existing LiftrixError instances without
 * losing the original error information or type specificity.
 * 
 * @param operation Description of the current operation
 * @param additionalContext Additional context to merge with existing context
 * @return Enhanced LiftrixError with merged context
 * 
 * Example:
 * ```
 * val enhancedError = originalError.withEnhancedContext(
 *     operation = "user_profile_update",
 *     additionalContext = mapOf("update_field" to "email")
 * )
 * ```
 */
fun LiftrixError.withEnhancedContext(
    operation: String,
    additionalContext: Map<String, String> = emptyMap()
): LiftrixError {
    val enhancedContext = createErrorContext(
        operation = operation,
        additionalContext = additionalContext + mapOf(
            "context_enhancement_timestamp" to Clock.System.now().toString(),
            "original_error_type" to (this::class.simpleName ?: "UnknownLiftrixError")
        )
    )
    
    return this.withAnalyticsContext(enhancedContext)
}