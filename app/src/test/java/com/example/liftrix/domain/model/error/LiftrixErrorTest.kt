package com.example.liftrix.domain.model.error

import org.junit.Test
import org.junit.Assert.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class LiftrixErrorTest {

    @Test
    fun `NetworkError should have correct default values`() {
        val error = LiftrixError.NetworkError()
        
        assertEquals("Network connection failed", error.message)
        assertNull(error.cause)
        assertTrue(error.isRecoverable)
        assertEquals(3000L, error.retryAfter)
        assertTrue(error.analyticsContext.isEmpty())
        assertNull(error.networkType)
        assertNull(error.httpStatusCode)
    }

    @Test
    fun `NetworkError should accept custom values`() {
        val customContext = mapOf("endpoint" to "/api/workouts", "method" to "POST")
        val error = LiftrixError.NetworkError(
            errorMessage = "API request failed",
            networkType = "WiFi",
            httpStatusCode = 500,
            analyticsContext = customContext,
            retryAfter = 5000L
        )
        
        assertEquals("API request failed", error.message)
        assertEquals("WiFi", error.networkType)
        assertEquals(500, error.httpStatusCode)
        assertEquals(customContext, error.analyticsContext)
        assertEquals(5000L, error.retryAfter)
        assertTrue(error.isRecoverable)
    }

    @Test
    fun `ValidationError should contain field and violations`() {
        val violations = listOf("Field is required", "Must be at least 3 characters")
        val error = LiftrixError.ValidationError(
            field = "workoutName",
            violations = violations
        )
        
        assertEquals("workoutName", error.field)
        assertEquals(violations, error.violations)
        assertEquals("Validation failed for workoutName", error.message)
        assertTrue(error.isRecoverable)
        assertNull(error.retryAfter) // No automatic retry for validation
    }

    @Test
    fun `AuthenticationError should have correct defaults`() {
        val error = LiftrixError.AuthenticationError()
        
        assertEquals("Authentication failed", error.message)
        assertFalse(error.isRecoverable) // Auth errors usually require user action
        assertNull(error.retryAfter)
        assertNull(error.authProvider)
        assertNull(error.errorCode)
    }

    @Test
    fun `AuthenticationError should accept provider and error code`() {
        val error = LiftrixError.AuthenticationError(
            errorMessage = "Google sign-in failed",
            authProvider = "Google",
            errorCode = "SIGN_IN_FAILED"
        )
        
        assertEquals("Google sign-in failed", error.message)
        assertEquals("Google", error.authProvider)
        assertEquals("SIGN_IN_FAILED", error.errorCode)
        assertFalse(error.isRecoverable)
    }

    @Test
    fun `DatabaseError should have correct defaults`() {
        val error = LiftrixError.DatabaseError()
        
        assertEquals("Database operation failed", error.message)
        assertTrue(error.isRecoverable)
        assertEquals(1000L, error.retryAfter)
        assertNull(error.operation)
        assertNull(error.table)
        assertNull(error.sqlErrorCode)
    }

    @Test
    fun `DatabaseError should accept operation details`() {
        val error = LiftrixError.DatabaseError(
            errorMessage = "Failed to insert workout",
            operation = "INSERT",
            table = "workouts",
            sqlErrorCode = 1062
        )
        
        assertEquals("Failed to insert workout", error.message)
        assertEquals("INSERT", error.operation)
        assertEquals("workouts", error.table)
        assertEquals(1062, error.sqlErrorCode)
        assertTrue(error.isRecoverable)
    }

    @Test
    fun `BusinessLogicError should contain code and context`() {
        val context = mapOf("userId" to "123", "workoutId" to "456")
        val error = LiftrixError.BusinessLogicError(
            code = "WORKOUT_ALREADY_STARTED",
            context = context
        )
        
        assertEquals("WORKOUT_ALREADY_STARTED", error.code)
        assertEquals(context, error.context)
        assertEquals("Business logic violation: WORKOUT_ALREADY_STARTED", error.message)
        assertFalse(error.isRecoverable) // Business logic errors not auto-recoverable
        assertNull(error.retryAfter)
    }

    @Test
    fun `UnknownError should have safe defaults`() {
        val error = LiftrixError.UnknownError()
        
        assertEquals("An unexpected error occurred", error.message)
        assertFalse(error.isRecoverable) // Unknown errors are not recoverable by default
        assertNull(error.retryAfter)
    }

    @Test
    fun `withAnalyticsContext should merge contexts correctly`() {
        val originalContext = mapOf("screen" to "workout", "feature" to "create")
        val additionalContext = mapOf("userId" to "123", "timestamp" to "2025-07-12")
        
        val error = LiftrixError.NetworkError(analyticsContext = originalContext)
        val updatedError = error.withAnalyticsContext(additionalContext)
        
        val expectedContext = mapOf(
            "screen" to "workout",
            "feature" to "create", 
            "userId" to "123",
            "timestamp" to "2025-07-12"
        )
        
        assertEquals(expectedContext, updatedError.analyticsContext)
    }

    @Test
    fun `withAnalyticsContext should override duplicate keys`() {
        val originalContext = mapOf("userId" to "old-id", "feature" to "create")
        val additionalContext = mapOf("userId" to "new-id", "screen" to "workout")
        
        val error = LiftrixError.ValidationError(field = "test", violations = emptyList(), analyticsContext = originalContext)
        val updatedError = error.withAnalyticsContext(additionalContext)
        
        val expectedContext = mapOf(
            "userId" to "new-id", // Should be overridden
            "feature" to "create",
            "screen" to "workout"
        )
        
        assertEquals(expectedContext, updatedError.analyticsContext)
    }

    @Test
    fun `withRetryAfter should update retry timing`() {
        val error = LiftrixError.DatabaseError()
        val updatedError = error.withRetryAfter(5000L)
        
        assertEquals(5000L, updatedError.retryAfter)
        // Other properties should remain unchanged
        assertEquals(error.message, updatedError.message)
        assertEquals(error.isRecoverable, updatedError.isRecoverable)
    }

    @Test
    fun `shouldRetry should respect recoverable flag`() {
        val recoverableError = LiftrixError.NetworkError() // isRecoverable = true
        val nonRecoverableError = LiftrixError.AuthenticationError() // isRecoverable = false
        
        assertTrue(recoverableError.shouldRetry(attemptCount = 1))
        assertFalse(nonRecoverableError.shouldRetry(attemptCount = 1))
    }

    @Test
    fun `shouldRetry should respect max attempts`() {
        val error = LiftrixError.NetworkError() // isRecoverable = true
        
        assertTrue(error.shouldRetry(attemptCount = 1, maxAttempts = 3))
        assertTrue(error.shouldRetry(attemptCount = 2, maxAttempts = 3))
        assertFalse(error.shouldRetry(attemptCount = 3, maxAttempts = 3))
        assertFalse(error.shouldRetry(attemptCount = 4, maxAttempts = 3))
    }

    @Test
    fun `shouldRetry should use default max attempts`() {
        val error = LiftrixError.NetworkError()
        
        assertTrue(error.shouldRetry(attemptCount = 1)) // Default max = 3
        assertTrue(error.shouldRetry(attemptCount = 2))
        assertFalse(error.shouldRetry(attemptCount = 3))
    }

    @Test
    fun `all error types should be serializable`() {
        val errors = listOf(
            LiftrixError.NetworkError(errorMessage = "Network test"),
            LiftrixError.ValidationError(field = "test", violations = listOf("error")),
            LiftrixError.AuthenticationError(errorMessage = "Auth test"),
            LiftrixError.DatabaseError(errorMessage = "DB test"),
            LiftrixError.BusinessLogicError(code = "TEST_ERROR"),
            LiftrixError.UnknownError(errorMessage = "Unknown test")
        )
        
        errors.forEach { error ->
            // Test serialization
            val json = Json.encodeToString<LiftrixError>(error)
            assertNotNull(json)
            assertTrue(json.isNotEmpty())
            
            // Test deserialization
            val deserializedError = Json.decodeFromString<LiftrixError>(json)
            assertEquals(error.message, deserializedError.message)
            assertEquals(error.isRecoverable, deserializedError.isRecoverable)
            assertEquals(error.retryAfter, deserializedError.retryAfter)
        }
    }

    @Test
    fun `error hierarchy should maintain type information after serialization`() {
        val networkError = LiftrixError.NetworkError(httpStatusCode = 404)
        val json = Json.encodeToString<LiftrixError>(networkError)
        val deserializedError = Json.decodeFromString<LiftrixError>(json)
        
        assertTrue(deserializedError is LiftrixError.NetworkError)
        assertEquals(404, (deserializedError as LiftrixError.NetworkError).httpStatusCode)
    }

    @Test
    fun `extension functions should work with all error types`() {
        val errors = listOf(
            LiftrixError.NetworkError(),
            LiftrixError.ValidationError(field = "test", violations = emptyList()),
            LiftrixError.AuthenticationError(),
            LiftrixError.DatabaseError(),
            LiftrixError.BusinessLogicError(code = "TEST"),
            LiftrixError.UnknownError()
        )
        
        errors.forEach { error ->
            // Test withAnalyticsContext
            val contextError = error.withAnalyticsContext(mapOf("test" to "value"))
            assertEquals("value", contextError.analyticsContext["test"])
            
            // Test withRetryAfter
            val retryError = error.withRetryAfter(2000L)
            assertEquals(2000L, retryError.retryAfter)
        }
    }
}