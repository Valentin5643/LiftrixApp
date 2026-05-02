package com.example.liftrix.domain.usecase.common

import com.example.liftrix.domain.model.error.LiftrixError
import kotlin.math.pow

/**
 * Centralized error handler interface for processing domain errors with analytics, logging, and user messaging.
 * 
 * Provides consistent error handling across the Liftrix application with support for:
 * - Error analytics and crash reporting
 * - User-friendly error message generation
 * - Retry policy determination with exponential backoff
 * - Error context preservation for debugging
 * - Recovery guidance based on error type
 */
interface ErrorHandler {
    
    /**
     * Processes a LiftrixError with comprehensive error handling including analytics, logging, and user messaging.
     * 
     * This is the main entry point for all error processing in the application.
     * It coordinates analytics reporting, user message generation, and retry policy creation.
     * 
     * @param error The LiftrixError to process
     * @param context Additional context information for error analysis and debugging
     * @return ErrorHandlingResult containing user message and retry policy
     */
    suspend fun handleError(
        error: LiftrixError,
        context: Map<String, Any> = emptyMap()
    ): ErrorHandlingResult
    
    /**
     * Logs error information for debugging and monitoring purposes.
     * 
     * Integrates with the application's logging framework (Timber) and ensures
     * that error context is preserved for debugging while avoiding sensitive data exposure.
     * 
     * @param error The LiftrixError to log
     * @param context Additional context information for the error
     */
    suspend fun logError(
        error: LiftrixError,
        context: Map<String, Any> = emptyMap()
    )
    
    /**
     * Sends error analytics to Firebase Crashlytics for monitoring and analysis.
     * 
     * Integrates with the existing AnalyticsService to ensure error data is properly
     * reported for proactive issue resolution and system health monitoring.
     * 
     * @param error The LiftrixError to report
     * @param context Additional analytics context for the error
     */
    suspend fun sendAnalytics(
        error: LiftrixError,
        context: Map<String, Any> = emptyMap()
    )
    
    /**
     * Generates a user-friendly error message for display in the UI.
     * 
     * Converts technical LiftrixError details into appropriate user-facing messages
     * that provide helpful guidance without exposing technical implementation details.
     * 
     * @param error The LiftrixError to convert to a user message
     * @return User-friendly error message string
     */
    fun mapToUserMessage(error: LiftrixError): String
    
    /**
     * Creates a retry policy for the given error based on its type and recovery characteristics.
     * 
     * Determines appropriate retry behavior including exponential backoff timing,
     * maximum retry attempts, and recovery strategies based on the error type.
     * 
     * @param error The LiftrixError to create a retry policy for
     * @param currentAttempt The current attempt number (0-based)
     * @return RetryPolicy containing retry logic and timing information
     */
    fun createRetryPolicy(error: LiftrixError, currentAttempt: Int = 0): RetryPolicy
    
    /**
     * Determines if the given error should be retried based on its characteristics and attempt count.
     * 
     * Evaluates error recoverability, current attempt count, and retry policy to determine
     * if automatic retry should be attempted.
     * 
     * @param error The LiftrixError to evaluate for retry
     * @param attemptCount The current attempt count
     * @param maxAttempts Maximum allowed attempts (default: 3)
     * @return true if the error should be retried, false otherwise
     */
    fun shouldRetry(
        error: LiftrixError,
        attemptCount: Int,
        maxAttempts: Int = 3
    ): Boolean
}

/**
 * Result of error handling processing containing user messaging and retry policy information.
 * 
 * Encapsulates the complete result of error processing to provide coordinated
 * error handling across the application.
 * 
 * @property userMessage User-friendly error message for display
 * @property retryPolicy Retry policy with timing and strategy information
 * @property shouldShowToUser Whether this error should be displayed to the user
 * @property isRecoverable Whether the error is potentially recoverable
 * @property analyticsReported Whether analytics were successfully reported
 */
data class ErrorHandlingResult(
    val userMessage: String,
    val retryPolicy: RetryPolicy,
    val shouldShowToUser: Boolean = true,
    val isRecoverable: Boolean = false,
    val analyticsReported: Boolean = false
)

/**
 * Retry policy configuration for automatic error recovery with exponential backoff.
 * 
 * Defines retry behavior including timing, maximum attempts, and backoff strategy
 * for automatic error recovery scenarios.
 * 
 * @property shouldRetry Whether automatic retry should be attempted
 * @property retryAfterMs Delay in milliseconds before retry attempt
 * @property maxAttempts Maximum number of retry attempts allowed
 * @property backoffStrategy Strategy for calculating retry delays
 * @property currentAttempt Current attempt number (0-based)
 */
data class RetryPolicy(
    val shouldRetry: Boolean,
    val retryAfterMs: Long,
    val maxAttempts: Int = 3,
    val backoffStrategy: BackoffStrategy = BackoffStrategy.EXPONENTIAL,
    val currentAttempt: Int = 0
) {
    
    /**
     * Calculates the next retry delay based on the backoff strategy.
     * 
     * @return Delay in milliseconds for the next retry attempt
     */
    fun calculateNextRetryDelay(): Long {
        return when (backoffStrategy) {
            BackoffStrategy.EXPONENTIAL -> {
                val baseDelay = retryAfterMs
                (baseDelay * 2.0.pow(currentAttempt.toDouble())).toLong()
                    .coerceAtMost(30_000L) // Maximum 30 seconds
            }
            BackoffStrategy.LINEAR -> {
                retryAfterMs + (currentAttempt * 1000L) // Add 1 second per attempt
            }
            BackoffStrategy.FIXED -> retryAfterMs
        }
    }
    
    /**
     * Creates a new RetryPolicy for the next attempt.
     * 
     * @return RetryPolicy configured for the next retry attempt
     */
    fun nextAttempt(): RetryPolicy {
        return copy(
            currentAttempt = currentAttempt + 1,
            shouldRetry = currentAttempt + 1 < maxAttempts,
            retryAfterMs = calculateNextRetryDelay()
        )
    }
}

/**
 * Backoff strategy for retry delay calculation.
 */
enum class BackoffStrategy {
    /**
     * Exponential backoff: delay doubles with each attempt
     */
    EXPONENTIAL,
    
    /**
     * Linear backoff: delay increases linearly with each attempt
     */
    LINEAR,
    
    /**
     * Fixed backoff: same delay for all attempts
     */
    FIXED
}