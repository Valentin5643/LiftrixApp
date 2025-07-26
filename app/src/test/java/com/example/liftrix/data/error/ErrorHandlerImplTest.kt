package com.example.liftrix.data.error

import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.domain.usecase.common.BackoffStrategy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

class ErrorHandlerImplTest {

    private lateinit var mockAnalyticsService: AnalyticsService
    private lateinit var errorHandler: ErrorHandlerImpl

    @Before
    fun setup() {
        mockAnalyticsService = mockk(relaxed = true)
        errorHandler = ErrorHandlerImpl(mockAnalyticsService)
    }

    @Test
    fun `handleError should process recoverable network error correctly`() = runTest {
        // Given
        val networkError = LiftrixError.NetworkError(
            errorMessage = "Connection failed",
            httpStatusCode = 500,
            isRecoverable = true,
            retryAfter = 3000L
        )
        val context = mapOf("screen" to "workout", "action" to "save")
        
        coEvery { mockAnalyticsService.recordException(any(), any()) } returns Result.success(Unit)
        coEvery { mockAnalyticsService.setCustomKey(any(), any()) } returns Result.success(Unit)

        // When
        val result = errorHandler.handleError(networkError, context)

        // Then
        assertTrue(result.isRecoverable)
        assertTrue(result.shouldShowToUser)
        assertEquals("Server is temporarily unavailable. Please try again later.", result.userMessage)
        assertTrue(result.retryPolicy.shouldRetry)
        assertEquals(BackoffStrategy.EXPONENTIAL, result.retryPolicy.backoffStrategy)
        assertTrue(result.analyticsReported)
        
        coVerify { mockAnalyticsService.recordException(any(), any()) }
    }

    @Test
    fun `handleError should process non-recoverable authentication error correctly`() = runTest {
        // Given
        val authError = LiftrixError.AuthenticationError(
            errorMessage = "Invalid credentials",
            errorCode = "INVALID_CREDENTIALS",
            isRecoverable = false
        )
        
        coEvery { mockAnalyticsService.recordException(any(), any()) } returns Result.success(Unit)

        // When
        val result = errorHandler.handleError(authError, emptyMap())

        // Then
        assertFalse(result.isRecoverable)
        assertTrue(result.shouldShowToUser)
        assertEquals("Invalid email or password. Please try again.", result.userMessage)
        assertFalse(result.retryPolicy.shouldRetry)
        assertTrue(result.analyticsReported)
    }

    @Test
    fun `handleError should process validation error correctly`() = runTest {
        // Given
        val validationError = LiftrixError.ValidationError(
            field = "workout_name",
            violations = listOf("Field is required", "Must be at least 3 characters"),
            isRecoverable = true
        )
        
        coEvery { mockAnalyticsService.recordException(any(), any()) } returns Result.success(Unit)

        // When
        val result = errorHandler.handleError(validationError, emptyMap())

        // Then
        assertTrue(result.isRecoverable)
        assertTrue(result.shouldShowToUser)
        assertEquals("Workout name has 2 issues. Field is required", result.userMessage)
        assertFalse(result.retryPolicy.shouldRetry) // Validation errors don't auto-retry
    }

    @Test
    fun `handleError should process database error with SQL code correctly`() = runTest {
        // Given
        val dbError = LiftrixError.DatabaseError(
            errorMessage = "Duplicate entry",
            operation = "INSERT",
            table = "workouts",
            sqlErrorCode = 1062,
            isRecoverable = true
        )
        
        coEvery { mockAnalyticsService.recordException(any(), any()) } returns Result.success(Unit)

        // When
        val result = errorHandler.handleError(dbError, emptyMap())

        // Then
        assertTrue(result.isRecoverable)
        assertTrue(result.shouldShowToUser)
        assertEquals("This item already exists. Please try a different name.", result.userMessage)
        assertFalse(result.retryPolicy.shouldRetry) // Duplicate key errors don't retry
    }

    @Test
    fun `handleError should process business logic error correctly`() = runTest {
        // Given
        val businessError = LiftrixError.BusinessLogicError(
            code = "WORKOUT_ALREADY_STARTED",
            analyticsContext = mapOf("userId" to "123", "currentWorkoutId" to "456"),
            isRecoverable = false
        )
        
        coEvery { mockAnalyticsService.recordException(any(), any()) } returns Result.success(Unit)

        // When
        val result = errorHandler.handleError(businessError, emptyMap())

        // Then
        assertFalse(result.isRecoverable)
        assertTrue(result.shouldShowToUser)
        assertEquals("A workout is already in progress. Please finish it before starting a new one.", result.userMessage)
        assertFalse(result.retryPolicy.shouldRetry)
    }

    @Test
    fun `handleError should handle analytics failure gracefully`() = runTest {
        // Given
        val networkError = LiftrixError.NetworkError(isRecoverable = true)
        
        coEvery { mockAnalyticsService.recordException(any(), any()) } returns Result.failure(Exception("Analytics failed"))

        // When
        val result = errorHandler.handleError(networkError, emptyMap())

        // Then
        assertFalse(result.analyticsReported)
        // Should still provide user message and retry policy
        assertNotNull(result.userMessage)
        assertNotNull(result.retryPolicy)
    }

    @Test
    fun `logError should include error context and details`() = runTest {
        // Given
        val networkError = LiftrixError.NetworkError(
            errorMessage = "Timeout occurred",
            httpStatusCode = 408,
            networkType = "WiFi"
        )
        val context = mapOf("screen" to "home", "userId" to "123")

        // When
        errorHandler.logError(networkError, context)

        // Then
        // This test verifies that logging doesn't throw exceptions
        // In a real scenario, you might want to capture Timber logs for verification
    }

    @Test
    fun `sendAnalytics should call analytics service with proper context`() = runTest {
        // Given
        val validationError = LiftrixError.ValidationError(
            field = "email",
            violations = listOf("Invalid format"),
            analyticsContext = mapOf("screen" to "registration")
        )
        val context = mapOf("attempt" to 1)
        
        coEvery { mockAnalyticsService.recordException(any(), any()) } returns Result.success(Unit)
        coEvery { mockAnalyticsService.setCustomKey(any(), any()) } returns Result.success(Unit)

        // When
        errorHandler.sendAnalytics(validationError, context)

        // Then
        coVerify { 
            mockAnalyticsService.recordException(
                any(),
                match { data ->
                    data["liftrix_error_type"] == "ValidationError" &&
                    data["validation_field"] == "email" &&
                    data["validation_violations_count"] == "1"
                }
            )
        }
        coVerify { mockAnalyticsService.setCustomKey("screen", "registration") }
    }

    @Test
    fun `mapToUserMessage should return appropriate message for different error types`() {
        // Test various error types
        val testCases = listOf(
            LiftrixError.NetworkError(httpStatusCode = 404) to "The requested information could not be found.",
            LiftrixError.ValidationError(field = "password", violations = listOf("Too short")) to "Password: Too short",
            LiftrixError.AuthenticationError(errorCode = "TOKEN_EXPIRED") to "Your session has expired. Please sign in again.",
            LiftrixError.DatabaseError(sqlErrorCode = 1451) to "Cannot delete this item because it's being used elsewhere.",
            LiftrixError.BusinessLogicError(code = "PREMIUM_FEATURE_REQUIRED") to "This feature requires a premium subscription. Upgrade to continue.",
            LiftrixError.UnknownError() to "An unexpected error occurred. Please try again or contact support if the problem persists."
        )

        testCases.forEach { (error, expectedMessage) ->
            val message = errorHandler.mapToUserMessage(error)
            assertEquals("Failed for ${error::class.simpleName}", expectedMessage, message)
        }
    }

    @Test
    fun `createRetryPolicy should respect error recovery characteristics`() {
        val testCases = listOf(
            // Recoverable network error should retry
            LiftrixError.NetworkError(isRecoverable = true) to true,
            // Non-recoverable auth error should not retry
            LiftrixError.AuthenticationError(isRecoverable = false) to false,
            // Validation errors should not retry automatically
            LiftrixError.ValidationError(field = "test", violations = listOf("error"), isRecoverable = true) to false,
            // Recoverable database errors should retry
            LiftrixError.DatabaseError(isRecoverable = true, sqlErrorCode = 1205) to true
        )

        testCases.forEach { (error, shouldRetry) ->
            val policy = errorHandler.createRetryPolicy(error, 0)
            assertEquals(
                "Failed for ${error::class.simpleName}", 
                shouldRetry, 
                policy.shouldRetry
            )
        }
    }

    @Test
    fun `shouldRetry should check attempt limits correctly`() {
        val recoverableError = LiftrixError.NetworkError(isRecoverable = true)
        
        // Should retry for attempts below max
        assertTrue(errorHandler.shouldRetry(recoverableError, 0, 3))
        assertTrue(errorHandler.shouldRetry(recoverableError, 1, 3))
        assertTrue(errorHandler.shouldRetry(recoverableError, 2, 3))
        
        // Should not retry when at or above max attempts
        assertFalse(errorHandler.shouldRetry(recoverableError, 3, 3))
        assertFalse(errorHandler.shouldRetry(recoverableError, 4, 3))
    }

    @Test
    fun `handleError should sanitize sensitive context data`() = runTest {
        // Given
        val error = LiftrixError.NetworkError(isRecoverable = true)
        val sensitiveContext = mapOf(
            "password" to "secret123",
            "token" to "abc123",
            "email" to "user@example.com",
            "safe_data" to "this_should_remain"
        )
        
        coEvery { mockAnalyticsService.recordException(any(), any()) } returns Result.success(Unit)

        // When
        errorHandler.handleError(error, sensitiveContext)

        // Then
        coVerify { 
            mockAnalyticsService.recordException(
                any(),
                match { data ->
                    // Should not contain sensitive keys
                    !data.keys.any { it.contains("password") || it.contains("token") || it.contains("email") } &&
                    // Should contain safe data
                    data.values.any { it.toString().contains("this_should_remain") }
                }
            )
        }
    }
}