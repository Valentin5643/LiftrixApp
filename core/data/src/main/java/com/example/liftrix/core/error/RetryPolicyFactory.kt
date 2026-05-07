package com.example.liftrix.core.error

import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.common.RetryPolicy
import com.example.liftrix.domain.usecase.common.BackoffStrategy

/**
 * Factory for creating retry policies based on error types and attempt counts.
 * 
 * Provides centralized retry logic configuration with appropriate backoff strategies
 * and retry limits based on error characteristics.
 */
object RetryPolicyFactory {
    
    /**
     * Creates a retry policy for the given error based on its type and recovery characteristics.
     * 
     * @param error The LiftrixError to create a retry policy for
     * @param currentAttempt The current attempt number (0-based)
     * @param maxAttempts Maximum allowed attempts (default: 3)
     * @return RetryPolicy containing retry logic and timing information
     */
    fun createRetryPolicy(
        error: LiftrixError,
        currentAttempt: Int = 0,
        maxAttempts: Int = 3
    ): RetryPolicy {
        // Don't retry if error is not recoverable
        if (!error.isRecoverable) {
            return RetryPolicy(
                shouldRetry = false,
                retryAfterMs = 0L,
                maxAttempts = 0,
                backoffStrategy = BackoffStrategy.FIXED,
                currentAttempt = currentAttempt
            )
        }
        
        // Don't retry if max attempts exceeded
        if (currentAttempt >= maxAttempts) {
            return RetryPolicy(
                shouldRetry = false,
                retryAfterMs = 0L,
                maxAttempts = maxAttempts,
                backoffStrategy = BackoffStrategy.FIXED,
                currentAttempt = currentAttempt
            )
        }
        
        // Create retry policy based on error type
        return when (error) {
            is LiftrixError.NetworkError -> RetryPolicy(
                shouldRetry = true,
                retryAfterMs = error.retryAfter ?: 3000L,
                maxAttempts = maxAttempts,
                backoffStrategy = BackoffStrategy.EXPONENTIAL,
                currentAttempt = currentAttempt
            )
            
            is LiftrixError.DatabaseError -> RetryPolicy(
                shouldRetry = true,
                retryAfterMs = error.retryAfter ?: 1000L,
                maxAttempts = maxAttempts,
                backoffStrategy = BackoffStrategy.LINEAR,
                currentAttempt = currentAttempt
            )
            
            is LiftrixError.CalculationError -> RetryPolicy(
                shouldRetry = true,
                retryAfterMs = error.retryAfter ?: 2000L,
                maxAttempts = maxAttempts,
                backoffStrategy = BackoffStrategy.EXPONENTIAL,
                currentAttempt = currentAttempt
            )
            
            is LiftrixError.DataRetrievalError -> RetryPolicy(
                shouldRetry = true,
                retryAfterMs = error.retryAfter ?: 2000L,
                maxAttempts = maxAttempts,
                backoffStrategy = BackoffStrategy.EXPONENTIAL,
                currentAttempt = currentAttempt
            )
            
            is LiftrixError.ConfigurationError -> RetryPolicy(
                shouldRetry = true,
                retryAfterMs = error.retryAfter ?: 1000L,
                maxAttempts = maxAttempts,
                backoffStrategy = BackoffStrategy.FIXED,
                currentAttempt = currentAttempt
            )
            
            is LiftrixError.ExportError -> RetryPolicy(
                shouldRetry = true,
                retryAfterMs = error.retryAfter ?: 3000L,
                maxAttempts = maxAttempts,
                backoffStrategy = BackoffStrategy.LINEAR,
                currentAttempt = currentAttempt
            )
            
            is LiftrixError.FileSystemError -> RetryPolicy(
                shouldRetry = true,
                retryAfterMs = error.retryAfter ?: 1000L,
                maxAttempts = maxAttempts,
                backoffStrategy = BackoffStrategy.LINEAR,
                currentAttempt = currentAttempt
            )
            
            is LiftrixError.CacheError -> RetryPolicy(
                shouldRetry = true,
                retryAfterMs = error.retryAfter ?: 2000L,
                maxAttempts = maxAttempts,
                backoffStrategy = BackoffStrategy.EXPONENTIAL,
                currentAttempt = currentAttempt
            )
            
            is LiftrixError.AuthenticationError -> RetryPolicy(
                shouldRetry = true,
                retryAfterMs = error.retryAfter ?: 2000L,
                maxAttempts = 2, // Limit auth retries
                backoffStrategy = BackoffStrategy.EXPONENTIAL,
                currentAttempt = currentAttempt
            )
            
            // Non-recoverable errors by default
            else -> RetryPolicy(
                shouldRetry = false,
                retryAfterMs = 0L,
                maxAttempts = 0,
                backoffStrategy = BackoffStrategy.FIXED,
                currentAttempt = currentAttempt
            )
        }
    }
}