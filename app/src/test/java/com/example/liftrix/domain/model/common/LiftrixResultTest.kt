package com.example.liftrix.domain.model.common

import com.example.liftrix.domain.model.error.LiftrixError
import org.junit.Test
import org.junit.Assert.*

class LiftrixResultTest {

    @Test
    fun `typealias should maintain full Result functionality`() {
        // Test that LiftrixResult is fully compatible with Result<T>
        val successResult: LiftrixResult<String> = Result.success("test")
        val failureResult: LiftrixResult<String> = Result.failure(Exception("test error"))
        
        assertTrue(successResult.isSuccess)
        assertFalse(successResult.isFailure)
        assertEquals("test", successResult.getOrNull())
        
        assertFalse(failureResult.isSuccess)
        assertTrue(failureResult.isFailure)
        assertNull(failureResult.getOrNull())
    }

    @Test
    fun `mapError should transform Throwable to LiftrixError on failure`() {
        val originalException = IllegalArgumentException("Invalid input")
        val failureResult: LiftrixResult<String> = Result.failure(originalException)
        
        val mappedResult = failureResult.mapError { throwable ->
            LiftrixError.ValidationError(
                field = "testField",
                violations = listOf(throwable.message ?: "Unknown error")
            )
        }
        
        assertTrue(mappedResult.isFailure)
        val exception = mappedResult.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is LiftrixError.ValidationError)
        
        val validationError = exception as LiftrixError.ValidationError
        assertEquals("testField", validationError.field)
        assertEquals(listOf("Invalid input"), validationError.violations)
    }

    @Test
    fun `mapError should preserve success value`() {
        val successResult: LiftrixResult<String> = Result.success("test value")
        
        val mappedResult = successResult.mapError { 
            LiftrixError.UnknownError(errorMessage = "Should not be called")
        }
        
        assertTrue(mappedResult.isSuccess)
        assertEquals("test value", mappedResult.getOrNull())
    }

    @Test
    fun `onLiftrixError should execute action only for LiftrixError failures`() {
        var actionCalled = false
        var capturedError: LiftrixError? = null
        
        val liftrixError = LiftrixError.NetworkError(errorMessage = "Network failed")
        val liftrixFailure: LiftrixResult<String> = Result.failure(liftrixError)
        
        val result = liftrixFailure.onLiftrixError { error ->
            actionCalled = true
            capturedError = error
        }
        
        assertTrue(actionCalled)
        assertEquals(liftrixError, capturedError)
        assertEquals(liftrixFailure, result) // Should return original result
    }

    @Test
    fun `onLiftrixError should not execute action for non-LiftrixError failures`() {
        var actionCalled = false
        
        val nonLiftrixFailure: LiftrixResult<String> = Result.failure(IllegalStateException("Test"))
        
        nonLiftrixFailure.onLiftrixError { 
            actionCalled = true
        }
        
        assertFalse(actionCalled)
    }

    @Test
    fun `onLiftrixError should not execute action for success`() {
        var actionCalled = false
        
        val successResult: LiftrixResult<String> = Result.success("test")
        
        successResult.onLiftrixError { 
            actionCalled = true
        }
        
        assertFalse(actionCalled)
    }

    @Test
    fun `recoverWithLiftrix should recover from LiftrixError`() {
        val networkError = LiftrixError.NetworkError(errorMessage = "Connection failed")
        val failureResult: LiftrixResult<String> = Result.failure(networkError)
        
        val recovered = failureResult.recoverWithLiftrix { error ->
            when (error) {
                is LiftrixError.NetworkError -> "fallback value"
                else -> "default value"
            }
        }
        
        assertEquals("fallback value", recovered)
    }

    @Test
    fun `recoverWithLiftrix should return success value unchanged`() {
        val successResult: LiftrixResult<String> = Result.success("original value")
        
        val result = successResult.recoverWithLiftrix { 
            "should not be called"
        }
        
        assertEquals("original value", result)
    }

    @Test
    fun `recoverWithLiftrix should rethrow non-LiftrixError`() {
        val nonLiftrixException = IllegalStateException("Not a LiftrixError")
        val failureResult: LiftrixResult<String> = Result.failure(nonLiftrixException)
        
        try {
            failureResult.recoverWithLiftrix { "should not be called" }
            fail("Expected exception to be rethrown")
        } catch (e: IllegalStateException) {
            assertEquals("Not a LiftrixError", e.message)
        }
    }

    @Test
    fun `recoverWithLiftrixResult should handle chained recovery`() {
        val networkError = LiftrixError.NetworkError(errorMessage = "Primary failed")
        val primaryFailure: LiftrixResult<String> = Result.failure(networkError)
        
        val result = primaryFailure.recoverWithLiftrixResult { error ->
            when (error) {
                is LiftrixError.NetworkError -> Result.success("recovered value")
                else -> Result.failure(LiftrixError.UnknownError())
            }
        }
        
        assertTrue(result.isSuccess)
        assertEquals("recovered value", result.getOrNull())
    }

    @Test
    fun `recoverWithLiftrixResult should propagate recovery failure`() {
        val networkError = LiftrixError.NetworkError(errorMessage = "Primary failed")
        val primaryFailure: LiftrixResult<String> = Result.failure(networkError)
        
        val recoveryError = LiftrixError.DatabaseError(errorMessage = "Recovery also failed")
        val result = primaryFailure.recoverWithLiftrixResult { 
            Result.failure(recoveryError)
        }
        
        assertTrue(result.isFailure)
        assertEquals(recoveryError, result.exceptionOrNull())
    }

    @Test
    fun `mapLiftrix should transform success value`() {
        val successResult: LiftrixResult<Int> = Result.success(42)
        
        val mappedResult = successResult.mapLiftrix { value ->
            "Number: $value"
        }
        
        assertTrue(mappedResult.isSuccess)
        assertEquals("Number: 42", mappedResult.getOrNull())
    }

    @Test
    fun `mapLiftrix should preserve failure`() {
        val error = LiftrixError.ValidationError(field = "test", violations = listOf("error"))
        val failureResult: LiftrixResult<Int> = Result.failure(error)
        
        val mappedResult = failureResult.mapLiftrix { value ->
            "Number: $value"
        }
        
        assertTrue(mappedResult.isFailure)
        assertEquals(error, mappedResult.exceptionOrNull())
    }

    @Test
    fun `flatMapLiftrix should chain successful operations`() {
        val successResult: LiftrixResult<Int> = Result.success(10)
        
        val result = successResult.flatMapLiftrix { value ->
            Result.success("Transformed: $value")
        }
        
        assertTrue(result.isSuccess)
        assertEquals("Transformed: 10", result.getOrNull())
    }

    @Test
    fun `flatMapLiftrix should propagate first failure`() {
        val error = LiftrixError.NetworkError(errorMessage = "First operation failed")
        val failureResult: LiftrixResult<Int> = Result.failure(error)
        
        val result = failureResult.flatMapLiftrix { value ->
            Result.success("Should not be called")
        }
        
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    @Test
    fun `flatMapLiftrix should propagate second failure`() {
        val successResult: LiftrixResult<Int> = Result.success(10)
        val secondError = LiftrixError.DatabaseError(errorMessage = "Second operation failed")
        
        val result = successResult.flatMapLiftrix { 
            Result.failure(secondError)
        }
        
        assertTrue(result.isFailure)
        assertEquals(secondError, result.exceptionOrNull())
    }

    @Test
    fun `liftrixSuccess should create successful result`() {
        val result = liftrixSuccess("test value")
        
        assertTrue(result.isSuccess)
        assertEquals("test value", result.getOrNull())
    }

    @Test
    fun `liftrixFailure should create failed result with LiftrixError`() {
        val error = LiftrixError.AuthenticationError(errorMessage = "Auth failed")
        val result = liftrixFailure<String>(error)
        
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    @Test
    fun `liftrixCatching should wrap successful execution`() {
        val result = liftrixCatching(
            errorMapper = { LiftrixError.UnknownError("Test error: ${it.message}") }
        ) {
            "successful execution"
        }
        
        assertTrue(result.isSuccess)
        assertEquals("successful execution", result.getOrNull())
    }

    @Test
    fun `liftrixCatching should map exceptions to LiftrixError`() {
        val result = liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(errorMessage = "Mapped: ${throwable.message}")
            }
        ) {
            throw IllegalArgumentException("Original exception")
        }
        
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is LiftrixError.DatabaseError)
        
        val dbError = error as LiftrixError.DatabaseError
        assertEquals("Mapped: Original exception", dbError.message)
        assertTrue(dbError.cause is IllegalArgumentException)
    }

    @Test
    fun `extension functions should be chainable`() {
        val result = liftrixCatching(
            errorMapper = { LiftrixError.NetworkError("Network error: ${it.message}") }
        ) {
            throw RuntimeException("Network issue")
        }.onLiftrixError { error ->
            // Analytics would be called here
            assertTrue(error is LiftrixError.NetworkError)
        }.recoverWithLiftrixResult { error ->
            when (error) {
                is LiftrixError.NetworkError -> liftrixSuccess("recovered")
                else -> liftrixFailure(error)
            }
        }.mapLiftrix { value ->
            value.uppercase()
        }
        
        assertTrue(result.isSuccess)
        assertEquals("RECOVERED", result.getOrNull())
    }

    @Test
    fun `extension functions should integrate with existing Result patterns`() {
        // Test integration with runCatching pattern from EstimateWorkoutDurationUseCase
        val result = runCatching {
            throw IllegalStateException("Database connection lost")
        }.mapError { throwable ->
            LiftrixError.DatabaseError(
                errorMessage = "Repository operation failed",
            )
        }.onLiftrixError { error ->
            // Would log analytics here
            assertTrue(error.isRecoverable)
            assertEquals(1000L, error.retryAfter)
        }
        
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is LiftrixError.DatabaseError)
        assertEquals("Repository operation failed", error.message)
    }

    @Test
    fun `extension functions should work with getOrDefault pattern`() {
        val networkError = LiftrixError.NetworkError(errorMessage = "No connection")
        val result: LiftrixResult<String> = Result.failure(networkError)
        
        val value = result.mapError { it as? LiftrixError ?: LiftrixError.UnknownError("Unknown error: ${it.message}") }
            .getOrElse { 
                when (it) {
                    is LiftrixError.NetworkError -> "offline_fallback"
                    else -> "default_value"
                }
            }
        
        assertEquals("offline_fallback", value)
    }
}