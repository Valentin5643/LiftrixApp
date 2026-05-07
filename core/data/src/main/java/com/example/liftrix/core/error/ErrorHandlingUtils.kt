package com.example.liftrix.core.error

import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.error.withAnalyticsContext
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixSuccess
import com.example.liftrix.domain.model.common.liftrixFailure
import kotlinx.datetime.Clock
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.io.IOException
import java.sql.SQLException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility functions for consistent error handling across the Liftrix application.
 * 
 * Provides standardized error wrapping, context creation, and exception mapping
 * to ensure consistent error handling patterns throughout all service layers.
 * 
 * Features:
 * - Standardized error wrapping with context preservation
 * - Exception type mapping to appropriate LiftrixError types
 * - Analytics context creation with operational metadata
 * - Integration with existing error handling infrastructure
 */

/**
 * Executes a block of code and wraps any exceptions in a LiftrixResult with proper error context.
 * 
 * This function provides standardized error handling for service layer operations with
 * automatic context creation and exception mapping. It ensures consistent error handling
 * patterns across all service implementations.
 * 
 * @param operation Description of the operation being performed for context
 * @param userId Optional user ID for error context (null if no user context)
 * @param additionalContext Additional context information for error tracking
 * @param block The code block to execute
 * @return LiftrixResult with success value or mapped LiftrixError
 * 
 * Example:
 * ```
 * suspend fun getWorkoutData(userId: String): LiftrixResult<WorkoutData> {
 *     return liftrixCatching(
 *         operation = "get_workout_data",
 *         userId = userId,
 *         additionalContext = mapOf("data_source" to "database")
 *     ) {
 *         workoutRepository.getWorkoutData(userId)
 *     }
 * }
 * ```
 */
inline fun <T> liftrixCatching(
    operation: String = "unknown_operation",
    userId: String? = null,
    additionalContext: Map<String, String> = emptyMap(),
    crossinline block: () -> T
): LiftrixResult<T> {
    return try {
        liftrixSuccess(block())
    } catch (exception: Throwable) {
        val errorContext = createErrorContext(
            operation = operation,
            userId = userId,
            additionalContext = additionalContext
        )
        
        val liftrixError = mapExceptionToLiftrixError(
            exception = exception,
            operation = operation,
            context = errorContext
        )
        
        liftrixFailure(liftrixError)
    }
}

/**
 * Creates comprehensive error context for analytics and debugging purposes.
 * 
 * Builds a standardized context map with operational metadata, user information,
 * and additional context for error tracking and analytics. This context is used
 * throughout the error handling system for consistent error reporting.
 * 
 * @param operation Description of the operation that failed
 * @param userId Optional user ID for user-scoped error tracking
 * @param additionalContext Additional context information to include
 * @return Map containing comprehensive error context
 * 
 * Example:
 * ```
 * val context = createErrorContext(
 *     operation = "workout_creation",
 *     userId = "user_123",
 *     additionalContext = mapOf(
 *         "workout_type" to "strength_training",
 *         "template_id" to "template_456"
 *     )
 * )
 * ```
 */
fun createErrorContext(
    operation: String,
    userId: String? = null,
    additionalContext: Map<String, String> = emptyMap()
): Map<String, String> {
    return buildMap {
        // Core operational context
        put("operation", operation)
        put("timestamp", Clock.System.now().toString())
        put("error_source", "liftrix_app")
        
        // User context (if available)
        userId?.let { put("user_id", it) }
        
        // System context
        put("platform", "android")
        put("error_handler", "ErrorHandlingUtils")
        
        // Additional context provided by caller
        putAll(additionalContext)
        
        // Context validation
        put("context_size", size.toString())
    }
}

/**
 * Maps various exception types to appropriate LiftrixError instances with context.
 * 
 * Provides comprehensive exception type mapping that handles common Android and
 * service-layer exceptions, converting them to appropriate LiftrixError types
 * with preserved context and recovery information.
 * 
 * @param exception The exception to map
 * @param operation Description of the operation that failed
 * @param context Additional context information for the error
 * @return Mapped LiftrixError with appropriate type and context
 * 
 * Example:
 * ```
 * val error = mapExceptionToLiftrixError(
 *     exception = sqlException,
 *     operation = "database_insert",
 *     context = mapOf("table" to "workouts", "user_id" to "123")
 * )
 * ```
 */
fun mapExceptionToLiftrixError(
    exception: Throwable,
    operation: String,
    context: Map<String, String> = emptyMap()
): LiftrixError {
    return when (exception) {
        // Network-related exceptions
        is UnknownHostException -> LiftrixError.NetworkError(
            errorMessage = "Unable to connect to server: ${exception.message}",
            isRecoverable = true,
            retryAfter = 3000L,
            analyticsContext = context + mapOf(
                "network_error_type" to "unknown_host",
                "host" to (exception.message ?: "unknown")
            ),
            networkType = "DNS",
            httpStatusCode = null
        )
        
        is SocketTimeoutException -> LiftrixError.NetworkError(
            errorMessage = "Connection timed out: ${exception.message}",
            isRecoverable = true,
            retryAfter = 5000L,
            analyticsContext = context + mapOf(
                "network_error_type" to "timeout",
                "timeout_reason" to (exception.message ?: "unknown")
            ),
            networkType = "TCP",
            httpStatusCode = 408
        )
        
        is IOException -> LiftrixError.NetworkError(
            errorMessage = "Network I/O error: ${exception.message}",
            isRecoverable = true,
            retryAfter = 2000L,
            analyticsContext = context + mapOf(
                "network_error_type" to "io_error",
                "io_error_type" to (exception::class.simpleName ?: "IOException")
            ),
            networkType = "I/O",
            httpStatusCode = null
        )
        
        // Database-related exceptions
        is SQLException -> {
            val sqlErrorCode = exception.errorCode
            val isRecoverable = when (sqlErrorCode) {
                1062, 1451, 1452 -> false // Constraint violations
                else -> true // Other SQL errors might be transient
            }
            
            LiftrixError.DatabaseError(
                errorMessage = "Database operation failed: ${exception.message}",
                isRecoverable = isRecoverable,
                retryAfter = if (isRecoverable) 1000L else null,
                analyticsContext = context + mapOf(
                    "sql_error_code" to sqlErrorCode.toString(),
                    "sql_state" to (exception.sqlState ?: "unknown"),
                    "database_error_type" to "sql_exception"
                ),
                operation = operation,
                table = context["table"],
                sqlErrorCode = sqlErrorCode
            )
        }
        
        // Validation exceptions
        is IllegalArgumentException -> LiftrixError.ValidationError(
            field = context["field"] ?: "unknown_field",
            violations = listOf(exception.message ?: "Invalid argument"),
            errorMessage = "Validation failed: ${exception.message}",
            isRecoverable = true,
            retryAfter = null,
            analyticsContext = context + mapOf(
                "validation_error_type" to "illegal_argument",
                "argument_error" to (exception.message ?: "unknown")
            )
        )
        
        is IllegalStateException -> LiftrixError.BusinessLogicError(
            code = "INVALID_STATE",
            errorMessage = "Invalid application state: ${exception.message}",
            isRecoverable = false,
            retryAfter = null,
            analyticsContext = context + mapOf(
                "business_error_type" to "illegal_state",
                "state_error" to (exception.message ?: "unknown")
            )
        )
        
        // Security exceptions
        is SecurityException -> LiftrixError.AuthenticationError(
            errorMessage = "Permission denied: ${exception.message}",
            isRecoverable = false,
            retryAfter = null,
            analyticsContext = context + mapOf(
                "security_error_type" to "permission_denied",
                "permission_error" to (exception.message ?: "unknown")
            ),
            authProvider = "Android",
            errorCode = "PERMISSION_DENIED"
        )
        
        // File system exceptions
        is java.io.FileNotFoundException -> LiftrixError.FileSystemError(
            errorMessage = "File not found: ${exception.message}",
            isRecoverable = false,
            retryAfter = null,
            analyticsContext = context + mapOf(
                "file_error_type" to "file_not_found",
                "file_path" to (exception.message ?: "unknown")
            ),
            operation = "READ",
            filePath = exception.message
        )
        
        is java.io.IOException -> LiftrixError.FileSystemError(
            errorMessage = "File I/O error: ${exception.message}",
            isRecoverable = true,
            retryAfter = 1000L,
            analyticsContext = context + mapOf(
                "file_error_type" to "io_error",
                "io_operation" to operation
            ),
            operation = operation.uppercase(),
            filePath = context["file_path"]
        )
        
        // Arithmetic exceptions (for calculations)
        is ArithmeticException -> LiftrixError.CalculationError(
            errorMessage = "Calculation error: ${exception.message}",
            isRecoverable = true,
            retryAfter = 1000L,
            analyticsContext = context + mapOf(
                "calculation_error_type" to "arithmetic_error",
                "arithmetic_operation" to (exception.message ?: "unknown")
            ),
            operation = operation
        )
        
        is NumberFormatException -> LiftrixError.CalculationError(
            errorMessage = "Invalid number format: ${exception.message}",
            isRecoverable = true,
            retryAfter = null,
            analyticsContext = context + mapOf(
                "calculation_error_type" to "number_format_error",
                "invalid_number" to (exception.message ?: "unknown")
            ),
            operation = operation
        )
        
        // Concurrency exceptions
        is InterruptedException -> LiftrixError.UnknownError(
            errorMessage = "Operation was interrupted: ${exception.message}",
            isRecoverable = true,
            retryAfter = 1000L,
            analyticsContext = context + mapOf(
                "concurrency_error_type" to "interrupted_exception",
                "thread_name" to (Thread.currentThread().name ?: "unknown")
            )
        )
        
        // Firebase exceptions (delegate to specialized mapper)
        is Exception -> {
            val className = exception::class.simpleName ?: "UnknownException"
            when {
                className.startsWith("Firebase") -> {
                    // Delegate to Firebase error mapper with enhanced context
                    val firebaseError = FirebaseErrorMapper.handleFirebaseError(exception)
                    firebaseError.withAnalyticsContext(context + mapOf(
                        "delegated_to" to "FirebaseErrorMapper",
                        "original_operation" to operation
                    ))
                }
                else -> createUnknownError(exception, operation, context)
            }
        }
        
        // Fallback for any other throwable
        else -> createUnknownError(exception, operation, context)
    }
}

/**
 * Creates an UnknownError with comprehensive context for unhandled exceptions.
 * 
 * This function is used as a fallback when an exception doesn't match any
 * specific type mapping. It preserves as much information as possible for
 * debugging and analytics purposes.
 * 
 * @param exception The unhandled exception
 * @param operation Description of the operation that failed
 * @param context Additional context information
 * @return LiftrixError.UnknownError with comprehensive context
 */
private fun createUnknownError(
    exception: Throwable,
    operation: String,
    context: Map<String, String>
): LiftrixError.UnknownError {
    return LiftrixError.UnknownError(
        errorMessage = "Unexpected error in $operation: ${exception.message}",
        isRecoverable = false,
        retryAfter = null,
        analyticsContext = context + mapOf(
            "exception_type" to (exception::class.simpleName ?: "UnknownThrowable"),
            "exception_message" to (exception.message ?: "no_message"),
            "stack_trace_available" to (exception.stackTrace.isNotEmpty()).toString(),
            "cause_available" to (exception.cause != null).toString(),
            "unknown_error_reason" to "unhandled_exception_type"
        )
    )
}

/**
 * Extension function to add operation context to existing LiftrixError instances.
 * 
 * Useful for adding additional context to errors that have already been created
 * or propagated from lower layers in the application stack.
 * 
 * @param operation Description of the current operation
 * @param additionalContext Additional context to merge
 * @return LiftrixError with enhanced context
 */
fun LiftrixError.withOperationContext(
    operation: String,
    additionalContext: Map<String, String> = emptyMap()
): LiftrixError {
    val enhancedContext = createErrorContext(
        operation = operation,
        additionalContext = additionalContext
    )
    
    return this.withAnalyticsContext(enhancedContext)
}

/**
 * Determines if an operation should be retried based on error characteristics and attempt count.
 * 
 * Provides intelligent retry logic that considers error type, recoverability,
 * current attempt count, and backoff timing for optimal retry behavior.
 * 
 * @param error The error to evaluate for retry
 * @param attemptCount Current attempt number (1-based)
 * @param maxAttempts Maximum number of attempts allowed
 * @return true if the operation should be retried, false otherwise
 */
fun shouldRetryOperation(
    error: LiftrixError,
    attemptCount: Int,
    maxAttempts: Int = 3
): Boolean {
    // Don't retry if max attempts reached
    if (attemptCount >= maxAttempts) return false
    
    // Don't retry if error is not recoverable
    if (!error.isRecoverable) return false
    
    // Special handling for specific error types
    return when (error) {
        is LiftrixError.NetworkError -> {
            // Retry network errors except for client errors (4xx)
            val httpStatusCode = error.httpStatusCode
            httpStatusCode == null ||
                httpStatusCode == 408 ||
                httpStatusCode == 429 ||
                httpStatusCode >= 500
        }
        
        is LiftrixError.DatabaseError -> {
            // Retry database errors except for constraint violations
            error.sqlErrorCode == null || 
            error.sqlErrorCode !in listOf(1062, 1451, 1452)
        }
        
        is LiftrixError.AuthenticationError -> {
            // Only retry auth errors for network issues
            error.errorCode in listOf("NETWORK_ERROR", "TOO_MANY_REQUESTS")
        }
        
        is LiftrixError.ValidationError -> false // Never retry validation errors
        
        is LiftrixError.BusinessLogicError -> {
            // Only retry concurrent modification errors
            error.code == "CONCURRENT_MODIFICATION"
        }
        
        is LiftrixError.CalculationError -> true // Calculations can be retried
        
        is LiftrixError.DataRetrievalError -> error.retryable // Data retrieval based on retryable flag
        
        is LiftrixError.ConfigurationError -> true // Configuration errors can be retried
        
        is LiftrixError.ExportError -> true // Export operations can be retried
        
        is LiftrixError.FileSystemError -> {
            // Retry file operations except for not found
            error.operation != "READ" || error.filePath != null
        }
        
        is LiftrixError.NotFoundError -> false // Never retry not found errors
        
        is LiftrixError.PermissionError -> false // Never retry permission errors
        
        is LiftrixError.CacheError -> true // Cache operations can be retried
        
        is LiftrixError.UnknownError -> false // Don't retry unknown errors for safety
    }
}

/**
 * Calculates the appropriate delay before retrying an operation.
 * 
 * Implements exponential backoff with jitter for optimal retry timing
 * that respects server load and improves success rates.
 * 
 * @param error The error that occurred
 * @param attemptCount Current attempt number (1-based)
 * @param baseDelay Base delay in milliseconds
 * @return Delay in milliseconds before next retry
 */
fun calculateRetryDelay(
    error: LiftrixError,
    attemptCount: Int,
    baseDelay: Long = 1000L
): Long {
    // Use error-specific retry delay if available
    val errorDelay = error.retryAfter ?: baseDelay
    
    // Apply exponential backoff: delay * (2^attemptCount)
    val exponentialDelay = errorDelay * (1 shl (attemptCount - 1))
    
    // Add jitter to prevent thundering herd (±25% randomization)
    val jitter = (exponentialDelay * 0.25 * Math.random()).toLong()
    val jitterSign = if (Math.random() > 0.5) 1 else -1
    
    return (exponentialDelay + (jitter * jitterSign)).coerceAtLeast(100L)
}
