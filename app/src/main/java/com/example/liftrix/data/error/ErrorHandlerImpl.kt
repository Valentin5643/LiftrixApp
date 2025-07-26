package com.example.liftrix.data.error

import com.example.liftrix.core.error.ErrorMapper
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.usecase.common.ErrorHandlingResult
import com.example.liftrix.domain.usecase.common.RetryPolicy
import com.example.liftrix.domain.usecase.common.BackoffStrategy
import com.example.liftrix.ui.error.ValidationError
import com.example.liftrix.ui.error.ValidationSeverity
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ErrorHandler providing centralized error processing with analytics, logging, and user messaging.
 * 
 * Coordinates error handling across the application by integrating:
 * - Analytics reporting via AnalyticsService and Firebase Crashlytics
 * - Comprehensive logging via Timber with structured error context
 * - User-friendly message generation via ErrorMapper
 * - Intelligent retry policy creation via RetryPolicyFactory
 * - Error context preservation for debugging and monitoring
 */
@Singleton
class ErrorHandlerImpl @Inject constructor(
    private val analyticsService: AnalyticsService
) : ErrorHandler {
    
    companion object {
        private const val TAG = "ErrorHandler"
        private const val MAX_CONTEXT_ENTRIES = 20
        private const val MAX_CONTEXT_VALUE_LENGTH = 500
    }
    
    /**
     * Processes a LiftrixError with comprehensive error handling including analytics, logging, and user messaging.
     */
    override suspend fun handleError(
        error: LiftrixError,
        context: Map<String, Any>
    ): ErrorHandlingResult {
        Timber.tag(TAG).d("Processing error: ${error::class.simpleName} - ${error.message}")
        
        // Log error for debugging
        logError(error, context)
        
        // Send analytics (don't fail the whole operation if analytics fail)
        val analyticsReported = try {
            sendAnalytics(error, context)
            true
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to send error analytics")
            false
        }
        
        // Generate user message
        val userMessage = mapToUserMessage(error)
        
        // Create retry policy
        val retryPolicy = createRetryPolicy(error, context.getCurrentAttempt())
        
        // Determine if error should be shown to user
        val shouldShowToUser = ErrorMapper.shouldShowToUser(error)
        
        return ErrorHandlingResult(
            userMessage = userMessage,
            retryPolicy = retryPolicy,
            shouldShowToUser = shouldShowToUser,
            isRecoverable = error.isRecoverable,
            analyticsReported = analyticsReported
        )
    }
    
    /**
     * Logs error information for debugging and monitoring purposes.
     */
    override suspend fun logError(
        error: LiftrixError,
        context: Map<String, Any>
    ) {
        val sanitizedContext = sanitizeContext(context)
        val errorContext = buildErrorContext(error, sanitizedContext)
        
        when {
            error.isRecoverable -> {
                Timber.tag(TAG).w(
                    "Recoverable error: ${error::class.simpleName}\n" +
                    "Message: ${error.message}\n" +
                    "Context: $errorContext\n" +
                    "Retry after: ${error.retryAfter}ms"
                )
            }
            else -> {
                Timber.tag(TAG).e(
                    error.cause,
                    "Non-recoverable error: ${error::class.simpleName}\n" +
                    "Message: ${error.message}\n" +
                    "Context: $errorContext"
                )
            }
        }
        
        // Log additional details for specific error types
        when (error) {
            is LiftrixError.NetworkError -> {
                Timber.tag(TAG).d(
                    "Network error details - Status: ${error.httpStatusCode}, " +
                    "Type: ${error.networkType}"
                )
            }
            is LiftrixError.DatabaseError -> {
                Timber.tag(TAG).d(
                    "Database error details - Operation: ${error.operation}, " +
                    "Table: ${error.table}, SQL Code: ${error.sqlErrorCode}"
                )
            }
            is LiftrixError.ValidationError -> {
                Timber.tag(TAG).d(
                    "Validation error details - Field: ${error.field}, " +
                    "Violations: ${error.violations.joinToString(", ")}"
                )
            }
            is LiftrixError.AuthenticationError -> {
                Timber.tag(TAG).d(
                    "Auth error details - Provider: ${error.authProvider}, " +
                    "Code: ${error.errorCode}"
                )
            }
            is LiftrixError.BusinessLogicError -> {
                Timber.tag(TAG).d(
                    "Business logic error details - Code: ${error.code}, " +
                    "Context: ${error.analyticsContext}"
                )
            }
            is LiftrixError.CalculationError -> {
                Timber.tag(TAG).d(
                    "Calculation error details - Operation: ${error.operation}, " +
                    "Context: ${error.analyticsContext}"
                )
            }
            is LiftrixError.DataRetrievalError -> {
                Timber.tag(TAG).d(
                    "Data retrieval error details - Operation: ${error.operation}, " +
                    "Retryable: ${error.retryable}"
                )
            }
            is LiftrixError.ConfigurationError -> {
                Timber.tag(TAG).d(
                    "Configuration error details - Key: ${error.configKey}, " +
                    "Value: ${error.configValue}"
                )
            }
            is LiftrixError.ExportError -> {
                Timber.tag(TAG).d(
                    "Export error details - Operation: ${error.operation}, " +
                    "Format: ${error.format}"
                )
            }
            is LiftrixError.FileSystemError -> {
                Timber.tag(TAG).d(
                    "File system error details - Operation: ${error.operation}, " +
                    "FilePath: ${error.filePath}"
                )
            }
            is LiftrixError.NotFoundError -> {
                Timber.tag(TAG).d(
                    "Resource not found - Type: ${error.resourceType}, " +
                    "ID: ${error.resourceId}"
                )
            }
            is LiftrixError.PermissionError -> {
                Timber.tag(TAG).w(
                    "Permission denied - Permission: ${error.permission}"
                )
            }
            is LiftrixError.CacheError -> {
                Timber.tag(TAG).d(
                    "Cache error - Operation: ${error.operation}, " +
                    "Key: ${error.cacheKey}"
                )
            }
            is LiftrixError.UnknownError -> {
                Timber.tag(TAG).e(error.cause, "Unknown error with no specific handling")
            }
        }
    }
    
    /**
     * Sends error analytics to Firebase Crashlytics for monitoring and analysis.
     */
    override suspend fun sendAnalytics(
        error: LiftrixError,
        context: Map<String, Any>
    ) {
        try {
            val sanitizedContext = sanitizeContext(context)
            val analyticsContext = buildAnalyticsContext(error, sanitizedContext)
            
            // Record exception via AnalyticsService
            val result = analyticsService.recordException(
                throwable = error.cause ?: RuntimeException(error.message),
                additionalData = analyticsContext
            )
            
            if (result.isFailure) {
                Timber.tag(TAG).w("Failed to record exception in analytics: ${result.exceptionOrNull()}")
            }
            
            // Set custom keys for enhanced error context
            error.analyticsContext.forEach { (key, value) ->
                analyticsService.setCustomKey(key, value)
            }
            
            Timber.tag(TAG).d("Error analytics sent successfully for ${error::class.simpleName}")
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to send error analytics")
            throw e
        }
    }
    
    /**
     * Generates a user-friendly error message for display in the UI with enhanced messaging for new error types.
     */
    override fun mapToUserMessage(error: LiftrixError): String {
        // Enhanced user messaging with context-aware responses
        return when (error) {
            is LiftrixError.NetworkError -> {
                when (error.httpStatusCode) {
                    408 -> "Connection timed out. Please check your internet and try again."
                    429 -> "Too many requests. Please wait a moment and try again."
                    500, 502, 503, 504 -> "Server is temporarily unavailable. Your data is saved locally."
                    401, 403 -> "Please sign in again to continue."
                    404 -> "The requested information could not be found."
                    else -> "Connection issue detected. Your changes are saved locally and will sync automatically."
                }
            }
            is LiftrixError.DatabaseError -> {
                when (error.operation) {
                    "save_workout", "save_session" -> "Unable to save changes. Your data has been preserved and we'll try again automatically."
                    "load_workout", "load_session" -> "Unable to load workout data. Please try refreshing or contact support if this persists."
                    "backup_restore" -> "Data recovery in progress. Please wait while we restore your information."
                    else -> "A data issue occurred. Your information is secure and we're working to resolve this."
                }
            }
            is LiftrixError.ValidationError -> {
                when {
                    error.violations.size == 1 -> error.violations.first()
                    error.violations.size > 1 -> "Please fix ${error.violations.size} issues: ${error.violations.joinToString(", ")}"
                    else -> "Please check your input and try again."
                }
            }
            is LiftrixError.AuthenticationError -> {
                when (error.errorCode) {
                    "TOKEN_EXPIRED" -> "Your session has expired. Please sign in again."
                    "INVALID_CREDENTIALS" -> "Invalid credentials. Please check your login information."
                    "NO_USER_ID" -> "Please sign in to continue using the app."
                    else -> "Authentication required. Please sign in to access your workouts."
                }
            }
            is LiftrixError.BusinessLogicError -> {
                when (error.code) {
                    "INVALID_OPERATION" -> "This action is not allowed at this time."
                    "CONCURRENT_MODIFICATION" -> "Someone else modified this data. Please refresh and try again."
                    "RATE_LIMIT_EXCEEDED" -> "Please slow down and try again in a moment."
                    else -> "Unable to complete this action. Please try again later."
                }
            }
            is LiftrixError.NotFoundError -> {
                when (error.resourceType) {
                    "workout" -> "This workout could not be found. It may have been deleted or moved."
                    "workout_session" -> "This workout session could not be found."
                    "backup" -> "No backup data available for recovery."
                    else -> "The requested information could not be found."
                }
            }
            else -> ErrorMapper.mapToUserMessage(error)
        }
    }
    
    /**
     * Enhanced validation error handling for historical data editing
     */
    fun handleValidationErrors(errors: List<ValidationError>): LiftrixError.ValidationError {
        val criticalErrors = errors.filter { it.severity == ValidationSeverity.CRITICAL }
        val regularErrors = errors.filter { it.severity == ValidationSeverity.ERROR }
        val warnings = errors.filter { it.severity == ValidationSeverity.WARNING }
        
        return when {
            criticalErrors.isNotEmpty() -> {
                LiftrixError.ValidationError(
                    field = "critical_data_integrity",
                    violations = criticalErrors.map { it.message },
                    errorMessage = "Critical data integrity issues must be resolved",
                    isRecoverable = false
                )
            }
            regularErrors.isNotEmpty() -> {
                LiftrixError.ValidationError(
                    field = "input_validation",
                    violations = regularErrors.map { it.message },
                    errorMessage = "Please fix the following issues before continuing",
                    isRecoverable = true
                )
            }
            warnings.isNotEmpty() -> {
                LiftrixError.ValidationError(
                    field = "input_warnings",
                    violations = warnings.map { "${it.message} (Warning)" },
                    errorMessage = "Please review these warnings",
                    isRecoverable = true
                )
            }
            else -> {
                LiftrixError.ValidationError(
                    field = "unknown",
                    violations = emptyList(),
                    errorMessage = "Validation completed successfully"
                )
            }
        }
    }
    
    /**
     * Handles data corruption scenarios with specific guidance
     */
    fun handleDataCorruptionError(entityType: String, entityId: String, hasBackup: Boolean): LiftrixError.DatabaseError {
        val message = if (hasBackup) {
            "Data corruption detected for $entityType. Backup restoration available."
        } else {
            "Data corruption detected for $entityType. No backup available - data may be lost."
        }
        
        return LiftrixError.DatabaseError(
            errorMessage = message,
            operation = "corruption_detection",
            table = entityType,
            isRecoverable = hasBackup,
            analyticsContext = mapOf(
                "entity_type" to entityType,
                "entity_id" to entityId,
                "backup_available" to hasBackup.toString()
            )
        )
    }
    
    /**
     * Creates network failure error with offline guidance
     */
    fun createNetworkFailureError(operation: String, canWorkOffline: Boolean): LiftrixError.NetworkError {
        val message = if (canWorkOffline) {
            "Network unavailable. You can continue working - changes will sync when connection is restored."
        } else {
            "Network connection required for this operation. Please check your connection and try again."
        }
        
        return LiftrixError.NetworkError(
            errorMessage = message,
            isRecoverable = true,
            retryAfter = if (canWorkOffline) null else 5000L,
            analyticsContext = mapOf(
                "operation" to operation,
                "offline_capable" to canWorkOffline.toString()
            )
        )
    }
    
    /**
     * Creates a retry policy for the given error based on its type and recovery characteristics.
     */
    override fun createRetryPolicy(error: LiftrixError, currentAttempt: Int): RetryPolicy {
        val maxAttempts = 3
        
        // If error is not recoverable, don't retry
        if (!error.isRecoverable) {
            return RetryPolicy(
                shouldRetry = false,
                retryAfterMs = 0,
                maxAttempts = 0,
                currentAttempt = currentAttempt
            )
        }
        
        // If we've exceeded max attempts, don't retry
        if (currentAttempt >= maxAttempts) {
            return RetryPolicy(
                shouldRetry = false,
                retryAfterMs = 0,
                maxAttempts = maxAttempts,
                currentAttempt = currentAttempt
            )
        }
        
        val baseDelay = error.retryAfter ?: 1000L
        
        return when (error) {
            is LiftrixError.NetworkError -> {
                val (strategy, attempts) = when (error.httpStatusCode) {
                    408, 429, 502, 503, 504 -> BackoffStrategy.EXPONENTIAL to maxAttempts
                    500 -> BackoffStrategy.EXPONENTIAL to (maxAttempts - 1).coerceAtLeast(1)
                    401, 403 -> BackoffStrategy.FIXED to 1
                    400, 404 -> BackoffStrategy.FIXED to 0
                    else -> BackoffStrategy.EXPONENTIAL to maxAttempts
                }
                
                RetryPolicy(
                    shouldRetry = currentAttempt < attempts,
                    retryAfterMs = baseDelay,
                    maxAttempts = attempts,
                    backoffStrategy = strategy,
                    currentAttempt = currentAttempt
                )
            }
            is LiftrixError.DatabaseError -> {
                RetryPolicy(
                    shouldRetry = true,
                    retryAfterMs = baseDelay,
                    maxAttempts = maxAttempts,
                    backoffStrategy = BackoffStrategy.LINEAR,
                    currentAttempt = currentAttempt
                )
            }
            is LiftrixError.CalculationError -> {
                RetryPolicy(
                    shouldRetry = true,
                    retryAfterMs = baseDelay,
                    maxAttempts = maxAttempts,
                    backoffStrategy = BackoffStrategy.LINEAR,
                    currentAttempt = currentAttempt
                )
            }
            is LiftrixError.ExportError -> {
                RetryPolicy(
                    shouldRetry = true,
                    retryAfterMs = baseDelay,
                    maxAttempts = maxAttempts,
                    backoffStrategy = BackoffStrategy.LINEAR,
                    currentAttempt = currentAttempt
                )
            }
            is LiftrixError.FileSystemError -> {
                RetryPolicy(
                    shouldRetry = true,
                    retryAfterMs = baseDelay,
                    maxAttempts = maxAttempts,
                    backoffStrategy = BackoffStrategy.LINEAR,
                    currentAttempt = currentAttempt
                )
            }
            else -> {
                RetryPolicy(
                    shouldRetry = false,
                    retryAfterMs = 0,
                    maxAttempts = 0,
                    currentAttempt = currentAttempt
                )
            }
        }
    }
    
    /**
     * Determines if the given error should be retried based on its characteristics and attempt count.
     */
    override fun shouldRetry(
        error: LiftrixError,
        attemptCount: Int,
        maxAttempts: Int
    ): Boolean {
        // Quick checks to avoid policy creation overhead
        if (!error.isRecoverable || attemptCount >= maxAttempts) {
            return false
        }
        
        return when (error) {
            is LiftrixError.ValidationError -> false
            is LiftrixError.AuthenticationError -> {
                error.errorCode in setOf("NETWORK_ERROR", "TOO_MANY_REQUESTS") && attemptCount < 1
            }
            is LiftrixError.BusinessLogicError -> {
                error.code in setOf("CONCURRENT_MODIFICATION", "RATE_LIMIT_EXCEEDED") && attemptCount < 1
            }
            is LiftrixError.NetworkError -> attemptCount < maxAttempts
            is LiftrixError.DatabaseError -> attemptCount < maxAttempts
            is LiftrixError.CalculationError -> attemptCount < maxAttempts
            is LiftrixError.DataRetrievalError -> error.retryable && attemptCount < maxAttempts
            is LiftrixError.ConfigurationError -> attemptCount < maxAttempts
            is LiftrixError.ExportError -> attemptCount < maxAttempts
            is LiftrixError.FileSystemError -> attemptCount < maxAttempts
            is LiftrixError.NotFoundError -> false // Not found errors are typically not recoverable by retry
            is LiftrixError.PermissionError -> false // Permission errors are typically not recoverable by retry
            is LiftrixError.CacheError -> attemptCount < maxAttempts // Cache errors can be retried
            is LiftrixError.UnknownError -> attemptCount < (maxAttempts - 1).coerceAtLeast(1)
        }
    }
    
    /**
     * Builds error context for logging with relevant error and system information.
     */
    private fun buildErrorContext(error: LiftrixError, context: Map<String, Any>): Map<String, String> {
        val errorContext = mutableMapOf<String, String>()
        
        // Add error-specific context
        errorContext["error_type"] = error::class.simpleName ?: "Unknown"
        errorContext["is_recoverable"] = error.isRecoverable.toString()
        errorContext["retry_after"] = error.retryAfter?.toString() ?: "null"
        
        // Add analytics context from error
        errorContext.putAll(error.analyticsContext)
        
        // Add provided context (limited and sanitized)
        context.entries.take(MAX_CONTEXT_ENTRIES).forEach { (key, value) ->
            val stringValue = value.toString().take(MAX_CONTEXT_VALUE_LENGTH)
            errorContext["ctx_$key"] = stringValue
        }
        
        return errorContext
    }
    
    /**
     * Builds analytics context for Firebase Crashlytics reporting.
     */
    private fun buildAnalyticsContext(error: LiftrixError, context: Map<String, Any>): Map<String, String> {
        val analyticsContext = mutableMapOf<String, String>()
        
        // Core error information
        analyticsContext["liftrix_error_type"] = error::class.simpleName ?: "Unknown"
        analyticsContext["liftrix_error_message"] = error.message ?: "Unknown error"
        analyticsContext["liftrix_is_recoverable"] = error.isRecoverable.toString()
        analyticsContext["liftrix_retry_after"] = error.retryAfter?.toString() ?: "none"
        
        // Error-specific details
        when (error) {
            is LiftrixError.NetworkError -> {
                analyticsContext["network_status_code"] = error.httpStatusCode?.toString() ?: "unknown"
                analyticsContext["network_type"] = error.networkType ?: "unknown"
            }
            is LiftrixError.DatabaseError -> {
                analyticsContext["db_operation"] = error.operation ?: "unknown"
                analyticsContext["db_table"] = error.table ?: "unknown"
                analyticsContext["db_sql_code"] = error.sqlErrorCode?.toString() ?: "unknown"
            }
            is LiftrixError.ValidationError -> {
                analyticsContext["validation_field"] = error.field
                analyticsContext["validation_violations_count"] = error.violations.size.toString()
            }
            is LiftrixError.AuthenticationError -> {
                analyticsContext["auth_provider"] = error.authProvider ?: "unknown"
                analyticsContext["auth_error_code"] = error.errorCode ?: "unknown"
            }
            is LiftrixError.BusinessLogicError -> {
                analyticsContext["business_logic_code"] = error.code
            }
            is LiftrixError.CalculationError -> {
                analyticsContext["calculation_operation"] = error.operation ?: "unknown"
            }
            is LiftrixError.DataRetrievalError -> {
                analyticsContext["data_retrieval_operation"] = error.operation ?: "unknown"
                analyticsContext["data_retrieval_retryable"] = error.retryable.toString()
            }
            is LiftrixError.ConfigurationError -> {
                analyticsContext["configuration_key"] = error.configKey ?: "unknown"
                analyticsContext["configuration_value"] = error.configValue ?: "unknown"
            }
            is LiftrixError.ExportError -> {
                analyticsContext["export_operation"] = error.operation ?: "unknown"
                analyticsContext["export_format"] = error.format ?: "unknown"
            }
            is LiftrixError.FileSystemError -> {
                analyticsContext["file_operation"] = error.operation ?: "unknown"
                analyticsContext["file_path"] = error.filePath ?: "unknown"
            }
            is LiftrixError.NotFoundError -> {
                analyticsContext["resource_type"] = error.resourceType ?: "unknown"
                analyticsContext["resource_id"] = error.resourceId ?: "unknown"
            }
            is LiftrixError.PermissionError -> {
                analyticsContext["permission"] = error.permission ?: "unknown"
            }
            is LiftrixError.CacheError -> {
                analyticsContext["cache_operation"] = error.operation ?: "unknown"
                analyticsContext["cache_key"] = error.cacheKey ?: "unknown"
            }
            is LiftrixError.UnknownError -> {
                analyticsContext["unknown_error_cause"] = error.cause?.javaClass?.simpleName ?: "none"
            }
        }
        
        // Add error's own analytics context
        analyticsContext.putAll(error.analyticsContext)
        
        // Add provided context (limited)
        context.entries.take(10).forEach { (key, value) ->
            val stringValue = value.toString().take(100)
            analyticsContext["context_$key"] = stringValue
        }
        
        return analyticsContext
    }
    
    /**
     * Sanitizes context to remove sensitive information and limit size.
     */
    private fun sanitizeContext(context: Map<String, Any>): Map<String, Any> {
        val sensitiveKeys = setOf(
            "password", "token", "secret", "key", "auth", "credential",
            "email", "phone", "address", "ssn", "credit", "card"
        )
        
        return context.filterKeys { key ->
            !sensitiveKeys.any { sensitiveKey -> 
                key.lowercase().contains(sensitiveKey) 
            }
        }.mapValues { (_, value) ->
            when {
                value.toString().length > MAX_CONTEXT_VALUE_LENGTH -> 
                    value.toString().take(MAX_CONTEXT_VALUE_LENGTH) + "..."
                else -> value
            }
        }
    }
    
    /**
     * Extension function to extract current attempt count from context.
     */
    private fun Map<String, Any>.getCurrentAttempt(): Int {
        return (this["attempt_count"] as? Int) 
            ?: (this["currentAttempt"] as? Int) 
            ?: 0
    }
}