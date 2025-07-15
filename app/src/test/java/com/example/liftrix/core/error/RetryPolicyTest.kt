package com.example.liftrix.core.error

import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.common.BackoffStrategy
import org.junit.Test
import org.junit.Assert.*

class RetryPolicyTest {

    @Test
    fun `createRetryPolicy should not retry non-recoverable errors`() {
        val nonRecoverableError = LiftrixError.AuthenticationError(
            errorMessage = "Invalid credentials",
            isRecoverable = false
        )

        val policy = RetryPolicyFactory.createRetryPolicy(nonRecoverableError, 0)

        assertFalse(policy.shouldRetry)
        assertEquals(0, policy.maxAttempts)
        assertEquals(0, policy.retryAfterMs)
    }

    @Test
    fun `createRetryPolicy should not retry when max attempts exceeded`() {
        val recoverableError = LiftrixError.NetworkError(isRecoverable = true)

        val policy = RetryPolicyFactory.createRetryPolicy(recoverableError, 3, 3)

        assertFalse(policy.shouldRetry)
        assertEquals(3, policy.maxAttempts)
        assertEquals(3, policy.currentAttempt)
    }

    @Test
    fun `createRetryPolicy for network errors should use exponential backoff`() {
        val networkError = LiftrixError.NetworkError(
            httpStatusCode = 500,
            isRecoverable = true,
            retryAfter = 1000L
        )

        val policy = RetryPolicyFactory.createRetryPolicy(networkError, 0)

        assertTrue(policy.shouldRetry)
        assertEquals(BackoffStrategy.EXPONENTIAL, policy.backoffStrategy)
        assertEquals(1000L, policy.retryAfterMs)
        assertEquals(3, policy.maxAttempts)
    }

    @Test
    fun `createRetryPolicy for network errors should handle different HTTP status codes`() {
        val testCases = listOf(
            400 to 0,  // Bad request - don't retry
            401 to 1,  // Unauthorized - retry once
            404 to 0,  // Not found - don't retry
            408 to 3,  // Timeout - retry with max attempts
            429 to 3,  // Too many requests - retry with max attempts
            500 to 2,  // Server error - retry with reduced attempts
            502 to 3,  // Bad gateway - retry with max attempts
            503 to 3   // Service unavailable - retry with max attempts
        )

        testCases.forEach { (statusCode, expectedMaxAttempts) ->
            val networkError = LiftrixError.NetworkError(
                httpStatusCode = statusCode,
                isRecoverable = true
            )

            val policy = RetryPolicyFactory.createRetryPolicy(networkError, 0)

            assertEquals(
                "Failed for status code $statusCode",
                expectedMaxAttempts,
                policy.maxAttempts
            )
            assertEquals(
                "Failed for status code $statusCode",
                expectedMaxAttempts > 0,
                policy.shouldRetry
            )
        }
    }

    @Test
    fun `createRetryPolicy for database errors should handle SQL error codes`() {
        val testCases = listOf(
            1205 to true,  // Deadlock - retry
            1213 to true,  // Lock timeout - retry
            1062 to false, // Duplicate key - don't retry
            1451 to false, // Foreign key constraint - don't retry
            1452 to false, // Cannot add child row - don't retry
            null to true   // Unknown SQL error - retry
        )

        testCases.forEach { (sqlErrorCode, shouldRetry) ->
            val dbError = LiftrixError.DatabaseError(
                sqlErrorCode = sqlErrorCode,
                isRecoverable = true
            )

            val policy = RetryPolicyFactory.createRetryPolicy(dbError, 0)

            assertEquals(
                "Failed for SQL error code $sqlErrorCode",
                shouldRetry,
                policy.shouldRetry
            )
        }
    }

    @Test
    fun `createRetryPolicy for validation errors should not retry`() {
        val validationError = LiftrixError.ValidationError(
            field = "email",
            violations = listOf("Invalid format"),
            isRecoverable = true // Even if marked recoverable
        )

        val policy = RetryPolicyFactory.createRetryPolicy(validationError, 0)

        assertFalse(policy.shouldRetry)
        assertEquals(0, policy.maxAttempts)
        assertEquals(BackoffStrategy.FIXED, policy.backoffStrategy)
    }

    @Test
    fun `createRetryPolicy for auth errors should handle specific error codes`() {
        val testCases = listOf(
            "NETWORK_ERROR" to true,
            "TOO_MANY_REQUESTS" to true,
            "INVALID_CREDENTIALS" to false,
            "TOKEN_EXPIRED" to false,
            null to false
        )

        testCases.forEach { (errorCode, shouldRetry) ->
            val authError = LiftrixError.AuthenticationError(
                errorCode = errorCode,
                isRecoverable = true
            )

            val policy = RetryPolicyFactory.createRetryPolicy(authError, 0)

            if (shouldRetry) {
                assertTrue("Failed for auth error code $errorCode", policy.shouldRetry)
                assertEquals(BackoffStrategy.LINEAR, policy.backoffStrategy)
            } else {
                assertFalse("Failed for auth error code $errorCode", policy.shouldRetry)
            }
        }
    }

    @Test
    fun `createRetryPolicy for business logic errors should handle retryable codes`() {
        val retryableCodes = listOf("CONCURRENT_MODIFICATION", "RATE_LIMIT_EXCEEDED")
        val nonRetryableCodes = listOf("WORKOUT_ALREADY_STARTED", "PREMIUM_FEATURE_REQUIRED")

        retryableCodes.forEach { code ->
            val businessError = LiftrixError.BusinessLogicError(
                code = code,
                isRecoverable = true
            )

            val policy = RetryPolicyFactory.createRetryPolicy(businessError, 0)

            assertTrue("Should retry for $code", policy.shouldRetry)
            assertEquals(1, policy.maxAttempts) // Limited to 1 retry
            assertEquals(BackoffStrategy.FIXED, policy.backoffStrategy)
        }

        nonRetryableCodes.forEach { code ->
            val businessError = LiftrixError.BusinessLogicError(
                code = code,
                isRecoverable = true
            )

            val policy = RetryPolicyFactory.createRetryPolicy(businessError, 0)

            assertFalse("Should not retry for $code", policy.shouldRetry)
        }
    }

    @Test
    fun `createRetryPolicy for unknown errors should be conservative`() {
        val unknownError = LiftrixError.UnknownError(isRecoverable = true)

        val policy = RetryPolicyFactory.createRetryPolicy(unknownError, 0, 3)

        assertTrue(policy.shouldRetry)
        assertEquals(2, policy.maxAttempts) // Conservative: max - 1
        assertEquals(BackoffStrategy.EXPONENTIAL, policy.backoffStrategy)
        assertEquals(2000L, policy.retryAfterMs) // Double the default base delay
    }

    @Test
    fun `calculateDelay should implement exponential backoff correctly`() {
        val baseDelay = 1000L
        val maxDelay = 30_000L

        val delays = (0..5).map { attempt ->
            RetryPolicyFactory.calculateDelay(baseDelay, attempt, BackoffStrategy.EXPONENTIAL)
        }

        // Verify exponential growth: 1s, 2s, 4s, 8s, 16s, 30s (capped)
        assertEquals(1000L, delays[0])
        assertEquals(2000L, delays[1])
        assertEquals(4000L, delays[2])
        assertEquals(8000L, delays[3])
        assertEquals(16000L, delays[4])
        assertEquals(maxDelay, delays[5]) // Should be capped at max
    }

    @Test
    fun `calculateDelay should implement linear backoff correctly`() {
        val baseDelay = 1000L

        val delays = (0..3).map { attempt ->
            RetryPolicyFactory.calculateDelay(baseDelay, attempt, BackoffStrategy.LINEAR)
        }

        // Verify linear growth: 1s, 2s, 3s, 4s
        assertEquals(1000L, delays[0])
        assertEquals(2000L, delays[1])
        assertEquals(3000L, delays[2])
        assertEquals(4000L, delays[3])
    }

    @Test
    fun `calculateDelay should implement fixed backoff correctly`() {
        val baseDelay = 1000L

        val delays = (0..3).map { attempt ->
            RetryPolicyFactory.calculateDelay(baseDelay, attempt, BackoffStrategy.FIXED)
        }

        // Verify fixed delay: 1s, 1s, 1s, 1s
        delays.forEach { delay ->
            assertEquals(baseDelay, delay)
        }
    }

    @Test
    fun `shouldRetryQuick should provide fast checks for common scenarios`() {
        // Non-recoverable error
        val nonRecoverableError = LiftrixError.AuthenticationError(isRecoverable = false)
        assertFalse(RetryPolicyFactory.shouldRetryQuick(nonRecoverableError, 0))

        // Exceeded max attempts
        val recoverableError = LiftrixError.NetworkError(isRecoverable = true)
        assertFalse(RetryPolicyFactory.shouldRetryQuick(recoverableError, 3, 3))

        // Validation error (never retry)
        val validationError = LiftrixError.ValidationError(
            field = "test", violations = listOf("error"), isRecoverable = true
        )
        assertFalse(RetryPolicyFactory.shouldRetryQuick(validationError, 0))

        // Network error within limits
        assertTrue(RetryPolicyFactory.shouldRetryQuick(recoverableError, 1, 3))

        // Auth error with network issue
        val networkAuthError = LiftrixError.AuthenticationError(
            errorCode = "NETWORK_ERROR",
            isRecoverable = true
        )
        assertTrue(RetryPolicyFactory.shouldRetryQuick(networkAuthError, 0))
        assertFalse(RetryPolicyFactory.shouldRetryQuick(networkAuthError, 1)) // Only one retry

        // Business logic error with retryable code
        val concurrentModError = LiftrixError.BusinessLogicError(
            code = "CONCURRENT_MODIFICATION",
            isRecoverable = true
        )
        assertTrue(RetryPolicyFactory.shouldRetryQuick(concurrentModError, 0))
        assertFalse(RetryPolicyFactory.shouldRetryQuick(concurrentModError, 1)) // Only one retry
    }

    @Test
    fun `RetryPolicy nextAttempt should increment correctly`() {
        val initialPolicy = RetryPolicy(
            shouldRetry = true,
            retryAfterMs = 1000L,
            maxAttempts = 3,
            backoffStrategy = BackoffStrategy.EXPONENTIAL,
            currentAttempt = 0
        )

        val nextAttemptPolicy = initialPolicy.nextAttempt()

        assertEquals(1, nextAttemptPolicy.currentAttempt)
        assertTrue(nextAttemptPolicy.shouldRetry) // Still under max attempts
        assertEquals(2000L, nextAttemptPolicy.retryAfterMs) // Doubled for exponential

        // Test when reaching max attempts
        val lastAttemptPolicy = initialPolicy.copy(currentAttempt = 2)
        val finalPolicy = lastAttemptPolicy.nextAttempt()

        assertEquals(3, finalPolicy.currentAttempt)
        assertFalse(finalPolicy.shouldRetry) // Reached max attempts
    }

    @Test
    fun `RetryPolicy calculateNextRetryDelay should respect max delay cap`() {
        val policy = RetryPolicy(
            shouldRetry = true,
            retryAfterMs = 1000L,
            maxAttempts = 10,
            backoffStrategy = BackoffStrategy.EXPONENTIAL,
            currentAttempt = 10 // Very high attempt count
        )

        val nextDelay = policy.calculateNextRetryDelay()

        assertEquals(30_000L, nextDelay) // Should be capped at 30 seconds
    }
}