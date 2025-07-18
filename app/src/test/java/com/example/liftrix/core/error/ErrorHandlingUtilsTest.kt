package com.example.liftrix.core.error

import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertContains
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.io.IOException
import java.sql.SQLException
import java.io.FileNotFoundException

/**
 * Comprehensive unit tests for ErrorHandlingUtils.
 * 
 * Tests all error handling utilities including error wrapping, context creation,
 * exception mapping, and retry logic with 95% code coverage target.
 */
class ErrorHandlingUtilsTest {
    
    @Nested
    @DisplayName("liftrixCatching function")
    inner class LiftrixCatchingTest {
        
        @Test
        fun `given successful operation, when liftrixCatching, then returns success`() {
            // Given
            val expectedValue = "test_result"
            
            // When
            val result = liftrixCatching(operation = "test_operation") {
                expectedValue
            }
            
            // Then
            assertTrue(result.isSuccess)
            assertEquals(expectedValue, result.getOrNull())
        }
        
        @Test
        fun `given operation that throws exception, when liftrixCatching, then returns failure with mapped error`() {
            // Given
            val operation = "test_operation"
            val userId = "user_123"
            val exception = IllegalArgumentException("Test error")
            
            // When
            val result = liftrixCatching(
                operation = operation,
                userId = userId
            ) {
                throw exception
            }
            
            // Then
            assertTrue(result.isFailure)
            val error = result.exceptionOrNull() as LiftrixError
            assertTrue(error is LiftrixError.ValidationError)
            assertEquals("Validation failed: Test error", error.message)
            assertEquals(userId, error.analyticsContext["user_id"])
            assertEquals(operation, error.analyticsContext["operation"])
        }
        
        @Test
        fun `given operation with additional context, when liftrixCatching, then preserves context in error`() {
            // Given
            val operation = "database_operation"
            val additionalContext = mapOf(
                "table" to "workouts",
                "action" to "insert"
            )
            
            // When
            val result = liftrixCatching(
                operation = operation,
                additionalContext = additionalContext
            ) {
                throw SQLException("Database error", "23000", 1062)
            }
            
            // Then
            assertTrue(result.isFailure)
            val error = result.exceptionOrNull() as LiftrixError
            assertTrue(error is LiftrixError.DatabaseError)
            assertEquals("workouts", error.analyticsContext["table"])
            assertEquals("insert", error.analyticsContext["action"])
            assertEquals(operation, error.analyticsContext["operation"])
        }
        
        @Test
        fun `given operation with null userId, when liftrixCatching, then does not include user_id in context`() {
            // Given
            val operation = "test_operation"
            
            // When
            val result = liftrixCatching(
                operation = operation,
                userId = null
            ) {
                throw RuntimeException("Test error")
            }
            
            // Then
            assertTrue(result.isFailure)
            val error = result.exceptionOrNull() as LiftrixError
            assertFalse(error.analyticsContext.containsKey("user_id"))
            assertEquals(operation, error.analyticsContext["operation"])
        }
    }
    
    @Nested
    @DisplayName("createErrorContext function")
    inner class CreateErrorContextTest {
        
        @Test
        fun `given operation and userId, when createErrorContext, then returns context with core fields`() {
            // Given
            val operation = "test_operation"
            val userId = "user_123"
            
            // When
            val context = createErrorContext(operation = operation, userId = userId)
            
            // Then
            assertEquals(operation, context["operation"])
            assertEquals(userId, context["user_id"])
            assertEquals("liftrix_app", context["error_source"])
            assertEquals("android", context["platform"])
            assertEquals("ErrorHandlingUtils", context["error_handler"])
            assertNotNull(context["timestamp"])
            assertNotNull(context["context_size"])
        }
        
        @Test
        fun `given operation without userId, when createErrorContext, then excludes user_id from context`() {
            // Given
            val operation = "test_operation"
            
            // When
            val context = createErrorContext(operation = operation)
            
            // Then
            assertEquals(operation, context["operation"])
            assertFalse(context.containsKey("user_id"))
            assertEquals("liftrix_app", context["error_source"])
            assertNotNull(context["timestamp"])
        }
        
        @Test
        fun `given additional context, when createErrorContext, then includes additional fields`() {
            // Given
            val operation = "test_operation"
            val additionalContext = mapOf(
                "custom_field" to "custom_value",
                "another_field" to "another_value"
            )
            
            // When
            val context = createErrorContext(
                operation = operation,
                additionalContext = additionalContext
            )
            
            // Then
            assertEquals(operation, context["operation"])
            assertEquals("custom_value", context["custom_field"])
            assertEquals("another_value", context["another_field"])
            assertNotNull(context["timestamp"])
        }
        
        @Test
        fun `given empty operation, when createErrorContext, then still creates valid context`() {
            // Given
            val operation = ""
            
            // When
            val context = createErrorContext(operation = operation)
            
            // Then
            assertEquals("", context["operation"])
            assertEquals("liftrix_app", context["error_source"])
            assertNotNull(context["timestamp"])
        }
    }
    
    @Nested
    @DisplayName("mapExceptionToLiftrixError function")
    inner class MapExceptionToLiftrixErrorTest {
        
        @Test
        fun `given UnknownHostException, when mapExceptionToLiftrixError, then returns NetworkError`() {
            // Given
            val exception = UnknownHostException("api.example.com")
            val operation = "network_request"
            
            // When
            val error = mapExceptionToLiftrixError(exception, operation)
            
            // Then
            assertTrue(error is LiftrixError.NetworkError)
            assertEquals("Unable to connect to server: api.example.com", error.message)
            assertTrue(error.isRecoverable)
            assertEquals(3000L, error.retryAfter)
            assertEquals("DNS", error.networkType)
            assertEquals("unknown_host", error.analyticsContext["network_error_type"])
        }
        
        @Test
        fun `given SocketTimeoutException, when mapExceptionToLiftrixError, then returns NetworkError with timeout context`() {
            // Given
            val exception = SocketTimeoutException("Read timeout")
            val operation = "api_call"
            
            // When
            val error = mapExceptionToLiftrixError(exception, operation)
            
            // Then
            assertTrue(error is LiftrixError.NetworkError)
            assertEquals("Connection timed out: Read timeout", error.message)
            assertTrue(error.isRecoverable)
            assertEquals(5000L, error.retryAfter)
            assertEquals("TCP", error.networkType)
            assertEquals(408, error.httpStatusCode)
            assertEquals("timeout", error.analyticsContext["network_error_type"])
        }
        
        @Test
        fun `given IOException, when mapExceptionToLiftrixError, then returns NetworkError`() {
            // Given
            val exception = IOException("Network I/O failed")
            val operation = "data_transfer"
            
            // When
            val error = mapExceptionToLiftrixError(exception, operation)
            
            // Then
            assertTrue(error is LiftrixError.NetworkError)
            assertEquals("Network I/O error: Network I/O failed", error.message)
            assertTrue(error.isRecoverable)
            assertEquals(2000L, error.retryAfter)
            assertEquals("I/O", error.networkType)
            assertEquals("io_error", error.analyticsContext["network_error_type"])
        }
        
        @Test
        fun `given SQLException with constraint violation, when mapExceptionToLiftrixError, then returns non-recoverable DatabaseError`() {
            // Given
            val exception = SQLException("Duplicate entry", "23000", 1062)
            val operation = "database_insert"
            val context = mapOf("table" to "users")
            
            // When
            val error = mapExceptionToLiftrixError(exception, operation, context)
            
            // Then
            assertTrue(error is LiftrixError.DatabaseError)
            assertEquals("Database operation failed: Duplicate entry", error.message)
            assertFalse(error.isRecoverable) // Constraint violations are not recoverable
            assertEquals(null, error.retryAfter)
            assertEquals(operation, error.operation)
            assertEquals("users", error.table)
            assertEquals(1062, error.sqlErrorCode)
        }
        
        @Test
        fun `given SQLException with transient error, when mapExceptionToLiftrixError, then returns recoverable DatabaseError`() {
            // Given
            val exception = SQLException("Connection lost", "08000", 2013)
            val operation = "database_select"
            
            // When
            val error = mapExceptionToLiftrixError(exception, operation)
            
            // Then
            assertTrue(error is LiftrixError.DatabaseError)
            assertEquals("Database operation failed: Connection lost", error.message)
            assertTrue(error.isRecoverable) // Non-constraint errors are recoverable
            assertEquals(1000L, error.retryAfter)
            assertEquals(operation, error.operation)
            assertEquals(2013, error.sqlErrorCode)
        }
        
        @Test
        fun `given IllegalArgumentException, when mapExceptionToLiftrixError, then returns ValidationError`() {
            // Given
            val exception = IllegalArgumentException("Invalid input parameter")
            val operation = "parameter_validation"
            val context = mapOf("field" to "workout_name")
            
            // When
            val error = mapExceptionToLiftrixError(exception, operation, context)
            
            // Then
            assertTrue(error is LiftrixError.ValidationError)
            assertEquals("Validation failed: Invalid input parameter", error.message)
            assertEquals("workout_name", error.field)
            assertEquals(listOf("Invalid input parameter"), error.violations)
            assertTrue(error.isRecoverable)
            assertEquals(null, error.retryAfter)
        }
        
        @Test
        fun `given IllegalStateException, when mapExceptionToLiftrixError, then returns BusinessLogicError`() {
            // Given
            val exception = IllegalStateException("Invalid application state")
            val operation = "state_validation"
            
            // When
            val error = mapExceptionToLiftrixError(exception, operation)
            
            // Then
            assertTrue(error is LiftrixError.BusinessLogicError)
            assertEquals("Invalid application state: Invalid application state", error.message)
            assertEquals("INVALID_STATE", error.code)
            assertFalse(error.isRecoverable)
            assertEquals(null, error.retryAfter)
        }
        
        @Test
        fun `given SecurityException, when mapExceptionToLiftrixError, then returns AuthenticationError`() {
            // Given
            val exception = SecurityException("Permission denied")
            val operation = "permission_check"
            
            // When
            val error = mapExceptionToLiftrixError(exception, operation)
            
            // Then
            assertTrue(error is LiftrixError.AuthenticationError)
            assertEquals("Permission denied: Permission denied", error.message)
            assertEquals("Android", error.authProvider)
            assertEquals("PERMISSION_DENIED", error.errorCode)
            assertFalse(error.isRecoverable)
            assertEquals(null, error.retryAfter)
        }
        
        @Test
        fun `given FileNotFoundException, when mapExceptionToLiftrixError, then returns FileSystemError`() {
            // Given
            val exception = FileNotFoundException("/path/to/file.txt")
            val operation = "file_read"
            
            // When
            val error = mapExceptionToLiftrixError(exception, operation)
            
            // Then
            assertTrue(error is LiftrixError.FileSystemError)
            assertEquals("File not found: /path/to/file.txt", error.message)
            assertEquals("READ", error.operation)
            assertEquals("/path/to/file.txt", error.filePath)
            assertFalse(error.isRecoverable)
            assertEquals(null, error.retryAfter)
        }
        
        @Test
        fun `given ArithmeticException, when mapExceptionToLiftrixError, then returns CalculationError`() {
            // Given
            val exception = ArithmeticException("Division by zero")
            val operation = "volume_calculation"
            
            // When
            val error = mapExceptionToLiftrixError(exception, operation)
            
            // Then
            assertTrue(error is LiftrixError.CalculationError)
            assertEquals("Calculation error: Division by zero", error.message)
            assertEquals(operation, error.operation)
            assertTrue(error.isRecoverable)
            assertEquals(1000L, error.retryAfter)
        }
        
        @Test
        fun `given NumberFormatException, when mapExceptionToLiftrixError, then returns CalculationError`() {
            // Given
            val exception = NumberFormatException("Invalid number format")
            val operation = "weight_parsing"
            
            // When
            val error = mapExceptionToLiftrixError(exception, operation)
            
            // Then
            assertTrue(error is LiftrixError.CalculationError)
            assertEquals("Invalid number format: Invalid number format", error.message)
            assertEquals(operation, error.operation)
            assertTrue(error.isRecoverable)
            assertEquals(null, error.retryAfter)
        }
        
        @Test
        fun `given InterruptedException, when mapExceptionToLiftrixError, then returns UnknownError`() {
            // Given
            val exception = InterruptedException("Thread interrupted")
            val operation = "background_task"
            
            // When
            val error = mapExceptionToLiftrixError(exception, operation)
            
            // Then
            assertTrue(error is LiftrixError.UnknownError)
            assertEquals("Operation was interrupted: Thread interrupted", error.message)
            assertTrue(error.isRecoverable)
            assertEquals(1000L, error.retryAfter)
            assertEquals("interrupted_exception", error.analyticsContext["concurrency_error_type"])
        }
        
        @Test
        fun `given unknown exception, when mapExceptionToLiftrixError, then returns UnknownError with context`() {
            // Given
            val exception = RuntimeException("Unknown error")
            val operation = "unknown_operation"
            val context = mapOf("custom" to "value")
            
            // When
            val error = mapExceptionToLiftrixError(exception, operation, context)
            
            // Then
            assertTrue(error is LiftrixError.UnknownError)
            assertEquals("Unexpected error in unknown_operation: Unknown error", error.message)
            assertFalse(error.isRecoverable)
            assertEquals(null, error.retryAfter)
            assertEquals("RuntimeException", error.analyticsContext["exception_type"])
            assertEquals("value", error.analyticsContext["custom"])
        }
    }
    
    @Nested
    @DisplayName("shouldRetryOperation function")
    inner class ShouldRetryOperationTest {
        
        @Test
        fun `given max attempts reached, when shouldRetryOperation, then returns false`() {
            // Given
            val error = LiftrixError.NetworkError(isRecoverable = true)
            val attemptCount = 3
            val maxAttempts = 3
            
            // When
            val shouldRetry = shouldRetryOperation(error, attemptCount, maxAttempts)
            
            // Then
            assertFalse(shouldRetry)
        }
        
        @Test
        fun `given non-recoverable error, when shouldRetryOperation, then returns false`() {
            // Given
            val error = LiftrixError.UnknownError(isRecoverable = false)
            val attemptCount = 1
            
            // When
            val shouldRetry = shouldRetryOperation(error, attemptCount)
            
            // Then
            assertFalse(shouldRetry)
        }
        
        @Test
        fun `given NetworkError with server error, when shouldRetryOperation, then returns true`() {
            // Given
            val error = LiftrixError.NetworkError(
                isRecoverable = true,
                httpStatusCode = 500
            )
            val attemptCount = 1
            
            // When
            val shouldRetry = shouldRetryOperation(error, attemptCount)
            
            // Then
            assertTrue(shouldRetry)
        }
        
        @Test
        fun `given NetworkError with client error, when shouldRetryOperation, then returns false`() {
            // Given
            val error = LiftrixError.NetworkError(
                isRecoverable = true,
                httpStatusCode = 400
            )
            val attemptCount = 1
            
            // When
            val shouldRetry = shouldRetryOperation(error, attemptCount)
            
            // Then
            assertFalse(shouldRetry)
        }
        
        @Test
        fun `given NetworkError with timeout, when shouldRetryOperation, then returns true`() {
            // Given
            val error = LiftrixError.NetworkError(
                isRecoverable = true,
                httpStatusCode = 408
            )
            val attemptCount = 1
            
            // When
            val shouldRetry = shouldRetryOperation(error, attemptCount)
            
            // Then
            assertTrue(shouldRetry)
        }
        
        @Test
        fun `given DatabaseError with constraint violation, when shouldRetryOperation, then returns false`() {
            // Given
            val error = LiftrixError.DatabaseError(
                isRecoverable = true,
                sqlErrorCode = 1062
            )
            val attemptCount = 1
            
            // When
            val shouldRetry = shouldRetryOperation(error, attemptCount)
            
            // Then
            assertFalse(shouldRetry)
        }
        
        @Test
        fun `given DatabaseError with transient error, when shouldRetryOperation, then returns true`() {
            // Given
            val error = LiftrixError.DatabaseError(
                isRecoverable = true,
                sqlErrorCode = 2013
            )
            val attemptCount = 1
            
            // When
            val shouldRetry = shouldRetryOperation(error, attemptCount)
            
            // Then
            assertTrue(shouldRetry)
        }
        
        @Test
        fun `given ValidationError, when shouldRetryOperation, then returns false`() {
            // Given
            val error = LiftrixError.ValidationError(
                field = "test",
                violations = listOf("error"),
                isRecoverable = true
            )
            val attemptCount = 1
            
            // When
            val shouldRetry = shouldRetryOperation(error, attemptCount)
            
            // Then
            assertFalse(shouldRetry)
        }
        
        @Test
        fun `given BusinessLogicError with concurrent modification, when shouldRetryOperation, then returns true`() {
            // Given
            val error = LiftrixError.BusinessLogicError(
                code = "CONCURRENT_MODIFICATION",
                isRecoverable = true
            )
            val attemptCount = 1
            
            // When
            val shouldRetry = shouldRetryOperation(error, attemptCount)
            
            // Then
            assertTrue(shouldRetry)
        }
        
        @Test
        fun `given CalculationError, when shouldRetryOperation, then returns true`() {
            // Given
            val error = LiftrixError.CalculationError(isRecoverable = true)
            val attemptCount = 1
            
            // When
            val shouldRetry = shouldRetryOperation(error, attemptCount)
            
            // Then
            assertTrue(shouldRetry)
        }
    }
    
    @Nested
    @DisplayName("calculateRetryDelay function")
    inner class CalculateRetryDelayTest {
        
        @Test
        fun `given error with specific retry delay, when calculateRetryDelay, then uses error delay`() {
            // Given
            val error = LiftrixError.NetworkError(retryAfter = 2000L)
            val attemptCount = 1
            
            // When
            val delay = calculateRetryDelay(error, attemptCount)
            
            // Then
            // Should be around 2000ms with exponential backoff and jitter
            assertTrue(delay >= 1500L && delay <= 3000L)
        }
        
        @Test
        fun `given error without specific retry delay, when calculateRetryDelay, then uses base delay`() {
            // Given
            val error = LiftrixError.ValidationError(field = "test", violations = listOf("error"))
            val attemptCount = 1
            val baseDelay = 1500L
            
            // When
            val delay = calculateRetryDelay(error, attemptCount, baseDelay)
            
            // Then
            // Should be around 1500ms with jitter
            assertTrue(delay >= 1000L && delay <= 2500L)
        }
        
        @Test
        fun `given higher attempt count, when calculateRetryDelay, then applies exponential backoff`() {
            // Given
            val error = LiftrixError.NetworkError(retryAfter = 1000L)
            val attemptCount = 3
            
            // When
            val delay = calculateRetryDelay(error, attemptCount)
            
            // Then
            // Should be around 4000ms (1000 * 2^(3-1)) with jitter
            assertTrue(delay >= 3000L && delay <= 6000L)
        }
        
        @Test
        fun `given any input, when calculateRetryDelay, then returns minimum 100ms`() {
            // Given
            val error = LiftrixError.NetworkError(retryAfter = 10L)
            val attemptCount = 1
            
            // When
            val delay = calculateRetryDelay(error, attemptCount)
            
            // Then
            assertTrue(delay >= 100L)
        }
    }
    
    @Nested
    @DisplayName("LiftrixError extension functions")
    inner class LiftrixErrorExtensionTest {
        
        @Test
        fun `given LiftrixError, when withOperationContext, then adds operation context`() {
            // Given
            val originalError = LiftrixError.NetworkError(
                analyticsContext = mapOf("original" to "value")
            )
            val operation = "test_operation"
            val additionalContext = mapOf("additional" to "context")
            
            // When
            val enhancedError = originalError.withOperationContext(operation, additionalContext)
            
            // Then
            assertEquals(operation, enhancedError.analyticsContext["operation"])
            assertEquals("context", enhancedError.analyticsContext["additional"])
            assertEquals("value", enhancedError.analyticsContext["original"])
            assertNotNull(enhancedError.analyticsContext["timestamp"])
        }
    }
    
    @Nested
    @DisplayName("Performance and edge cases")
    inner class PerformanceAndEdgeCasesTest {
        
        @Test
        fun `given large additional context, when createErrorContext, then handles context efficiently`() {
            // Given
            val largeContext = (1..100).associate { "key_$it" to "value_$it" }
            val operation = "performance_test"
            
            // When
            val context = createErrorContext(
                operation = operation,
                additionalContext = largeContext
            )
            
            // Then
            assertEquals(operation, context["operation"])
            assertEquals("105", context["context_size"]) // 100 + 5 core fields
            assertTrue(context.containsKey("key_1"))
            assertTrue(context.containsKey("key_100"))
        }
        
        @Test
        fun `given null exception message, when mapExceptionToLiftrixError, then handles gracefully`() {
            // Given
            val exception = RuntimeException(null as String?)
            val operation = "null_message_test"
            
            // When
            val error = mapExceptionToLiftrixError(exception, operation)
            
            // Then
            assertTrue(error is LiftrixError.UnknownError)
            assertEquals("Unexpected error in null_message_test: No message", error.message)
            assertEquals("no_message", error.analyticsContext["exception_message"])
        }
        
        @Test
        fun `given exception with very long message, when mapExceptionToLiftrixError, then preserves message`() {
            // Given
            val longMessage = "A".repeat(1000)
            val exception = RuntimeException(longMessage)
            val operation = "long_message_test"
            
            // When
            val error = mapExceptionToLiftrixError(exception, operation)
            
            // Then
            assertTrue(error is LiftrixError.UnknownError)
            assertTrue(error.message.contains(longMessage))
        }
    }
}