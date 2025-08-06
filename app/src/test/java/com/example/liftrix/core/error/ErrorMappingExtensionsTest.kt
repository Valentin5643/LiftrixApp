package com.example.liftrix.core.error

import com.example.liftrix.domain.model.error.LiftrixError
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.sql.SQLException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comprehensive unit tests for ErrorMappingExtensions.kt
 * 
 * Tests all extension functions for exception mapping with proper context preservation,
 * error type mapping, and analytics context integration. Validates error handling
 * patterns and ensures consistent error mapping across all exception types.
 * 
 * Test Coverage:
 * - All extension functions for common exception types
 * - Context preservation and analytics integration
 * - Error message formatting and recovery information
 * - Edge cases and error scenarios
 * - Integration with existing error handling infrastructure
 */
class ErrorMappingExtensionsTest {

    // No setup needed - we'll test the extensions directly without mocking time

    // SocketTimeoutException Tests
    @Test
    fun `given SocketTimeoutException with message, when toLiftrixError, then returns NetworkError with timeout context`() {
        // Given
        val exception = SocketTimeoutException("Connection timeout after 30 seconds")
        val context = mapOf("endpoint" to "api/workouts", "timeout" to "30s")

        // When
        val result = exception.toLiftrixError(context)

        // Then
        assertTrue(result is LiftrixError.NetworkError)
        assertEquals("Connection timed out: Connection timeout after 30 seconds", result.errorMessage)
        assertTrue(result.isRecoverable)
        assertEquals(5000L, result.retryAfter)
        assertEquals("TCP", result.networkType)
        assertEquals(408, result.httpStatusCode)
        
        // Verify analytics context
        assertEquals("network_timeout", result.analyticsContext["operation"])
        assertEquals("timeout", result.analyticsContext["network_error_type"])
        assertEquals("Connection timeout after 30 seconds", result.analyticsContext["timeout_reason"])
        assertEquals("SocketTimeoutException", result.analyticsContext["exception_type"])
        assertEquals("api/workouts", result.analyticsContext["endpoint"])
        assertEquals("30s", result.analyticsContext["timeout"])
        // Verify timestamp is present (without checking exact value)
        assertTrue(result.analyticsContext.containsKey("timestamp"))
        assertEquals("android", result.analyticsContext["platform"])
        assertEquals("liftrix_app", result.analyticsContext["error_source"])
    }

    @Test
    fun `given SocketTimeoutException with null message, when toLiftrixError, then returns NetworkError with unknown timeout`() {
        // Given
        val exception = SocketTimeoutException(null)
        val context = emptyMap<String, String>()

        // When
        val result = exception.toLiftrixError(context)

        // Then
        assertTrue(result is LiftrixError.NetworkError)
        assertEquals("Connection timed out: null", result.errorMessage)
        assertEquals("unknown", result.analyticsContext["timeout_reason"])
        // Verify basic context structure
        assertTrue(result.analyticsContext.containsKey("timestamp"))
        assertEquals("android", result.analyticsContext["platform"])
    }

    // UnknownHostException Tests
    @Test
    fun `given UnknownHostException with hostname, when toLiftrixError, then returns NetworkError with DNS context`() {
        // Given
        val exception = UnknownHostException("api.liftrix.com")
        val context = mapOf("hostname" to "api.liftrix.com")

        // When
        val result = exception.toLiftrixError(context)

        // Then
        assertTrue(result is LiftrixError.NetworkError)
        assertEquals("Unable to resolve hostname: api.liftrix.com", result.errorMessage)
        assertTrue(result.isRecoverable)
        assertEquals(3000L, result.retryAfter)
        assertEquals("DNS", result.networkType)
        assertNull(result.httpStatusCode)
        
        // Verify analytics context
        assertEquals("dns_resolution", result.analyticsContext["operation"])
        assertEquals("dns_resolution", result.analyticsContext["network_error_type"])
        assertEquals("api.liftrix.com", result.analyticsContext["hostname"])
        assertEquals("UnknownHostException", result.analyticsContext["exception_type"])
        // Verify basic context structure
        assertTrue(result.analyticsContext.containsKey("timestamp"))
        assertEquals("android", result.analyticsContext["platform"])
    }

    @Test
    fun `given UnknownHostException without context, when toLiftrixError, then returns NetworkError with default context`() {
        // Given
        val exception = UnknownHostException("unknown.host.com")

        // When
        val result = exception.toLiftrixError()

        // Then
        assertTrue(result is LiftrixError.NetworkError)
        assertEquals("Unable to resolve hostname: unknown.host.com", result.errorMessage)
        assertEquals("unknown.host.com", result.analyticsContext["hostname"])
        // Verify basic context structure
        assertTrue(result.analyticsContext.containsKey("timestamp"))
    }

    // IOException Tests
    @Test
    fun `given IOException with message, when toLiftrixError, then returns NetworkError with IO context`() {
        // Given
        val exception = IOException("Stream closed unexpectedly")
        val context = mapOf("stream_type" to "http_response")

        // When
        val result = exception.toLiftrixError(context)

        // Then
        assertTrue(result is LiftrixError.NetworkError)
        assertEquals("Network I/O error: Stream closed unexpectedly", result.errorMessage)
        assertTrue(result.isRecoverable)
        assertEquals(2000L, result.retryAfter)
        assertEquals("I/O", result.networkType)
        assertNull(result.httpStatusCode)
        
        // Verify analytics context
        assertEquals("io_operation", result.analyticsContext["operation"])
        assertEquals("io_error", result.analyticsContext["network_error_type"])
        assertEquals("IOException", result.analyticsContext["io_error_type"])
        assertEquals("IOException", result.analyticsContext["exception_type"])
        assertEquals("http_response", result.analyticsContext["stream_type"])
        // Verify basic context structure
        assertTrue(result.analyticsContext.containsKey("timestamp"))
        assertEquals("android", result.analyticsContext["platform"])
    }

    // SQLException Tests
    @Test
    fun `given SQLException with constraint violation error code, when toLiftrixError, then returns non-recoverable DatabaseError`() {
        // Given
        val exception = SQLException("Duplicate entry 'test' for key 'PRIMARY'", "23000", 1062)
        val context = mapOf("table" to "workouts", "operation" to "INSERT")

        // When
        val result = exception.toLiftrixError(context)

        // Then
        assertTrue(result is LiftrixError.DatabaseError)
        assertEquals("Database operation failed: Duplicate entry 'test' for key 'PRIMARY'", result.errorMessage)
        assertFalse(result.isRecoverable) // Constraint violations are not recoverable
        assertNull(result.retryAfter)
        assertEquals("INSERT", result.operation)
        assertEquals("workouts", result.table)
        assertEquals(1062, result.sqlErrorCode)
        
        // Verify analytics context
        assertEquals("database_operation", result.analyticsContext["operation"])
        assertEquals("1062", result.analyticsContext["sql_error_code"])
        assertEquals("23000", result.analyticsContext["sql_state"])
        assertEquals("sql_exception", result.analyticsContext["database_error_type"])
        assertEquals("SQLException", result.analyticsContext["exception_type"])
        assertEquals("workouts", result.analyticsContext["table"])
        // Note: operation appears twice - once in base context, once from input context
        assertTrue(result.analyticsContext.containsKey("timestamp"))
        assertEquals("android", result.analyticsContext["platform"])
    }

    @Test
    fun `given SQLException with transient error code, when toLiftrixError, then returns recoverable DatabaseError`() {
        // Given
        val exception = SQLException("Lock wait timeout exceeded", "HY000", 1205)
        val context = mapOf("table" to "users", "operation" to "UPDATE")

        // When
        val result = exception.toLiftrixError(context)

        // Then
        assertTrue(result is LiftrixError.DatabaseError)
        assertTrue(result.isRecoverable) // Transient errors are recoverable
        assertEquals(1000L, result.retryAfter)
        assertEquals("UPDATE", result.operation)
        assertEquals("users", result.table)
        assertEquals(1205, result.sqlErrorCode)
    }

    // IllegalArgumentException Tests
    @Test
    fun `given IllegalArgumentException with field context, when toLiftrixError, then returns ValidationError with field info`() {
        // Given
        val exception = IllegalArgumentException("Workout name cannot be empty")
        val context = mapOf("field" to "workout_name", "value" to "")

        // When
        val result = exception.toLiftrixError(context)

        // Then
        assertTrue(result is LiftrixError.ValidationError)
        assertEquals("workout_name", result.field)
        assertEquals(listOf("Workout name cannot be empty"), result.violations)
        assertEquals("Validation failed for workout_name: Workout name cannot be empty", result.errorMessage)
        assertTrue(result.isRecoverable)
        assertNull(result.retryAfter) // No automatic retry for validation errors
        
        // Verify analytics context
        assertEquals("validation", result.analyticsContext["operation"])
        assertEquals("illegal_argument", result.analyticsContext["validation_error_type"])
        assertEquals("workout_name", result.analyticsContext["field_name"])
        assertEquals("Workout name cannot be empty", result.analyticsContext["argument_error"])
        assertEquals("IllegalArgumentException", result.analyticsContext["exception_type"])
        assertEquals("workout_name", result.analyticsContext["field"])
        assertEquals("", result.analyticsContext["value"])
        // Verify basic context structure
        assertTrue(result.analyticsContext.containsKey("timestamp"))
        assertEquals("android", result.analyticsContext["platform"])
    }

    @Test
    fun `given IllegalArgumentException without field context, when toLiftrixError, then returns ValidationError with unknown field`() {
        // Given
        val exception = IllegalArgumentException("Invalid input")

        // When
        val result = exception.toLiftrixError()

        // Then
        assertTrue(result is LiftrixError.ValidationError)
        assertEquals("unknown_field", result.field)
        assertEquals(listOf("Invalid input"), result.violations)
        assertEquals("Validation failed for unknown_field: Invalid input", result.errorMessage)
        // Verify basic context structure
        assertTrue(result.analyticsContext.containsKey("timestamp"))
        assertEquals("android", result.analyticsContext["platform"])
    }

    // IllegalStateException Tests
    @Test
    fun `given IllegalStateException with state context, when toLiftrixError, then returns BusinessLogicError`() {
        // Given
        val exception = IllegalStateException("Workout session is not active")
        val context = mapOf("state" to "workout_session", "expected" to "ACTIVE")

        // When
        val result = exception.toLiftrixError(context)

        // Then
        assertTrue(result is LiftrixError.BusinessLogicError)
        assertEquals("INVALID_STATE", result.code)
        assertEquals("Invalid application state: Workout session is not active", result.errorMessage)
        assertFalse(result.isRecoverable)
        assertNull(result.retryAfter)
        
        // Verify analytics context
        assertEquals("business_logic", result.analyticsContext["operation"])
        assertEquals("illegal_state", result.analyticsContext["business_error_type"])
        assertEquals("Workout session is not active", result.analyticsContext["state_error"])
        assertEquals("IllegalStateException", result.analyticsContext["exception_type"])
        assertEquals("workout_session", result.analyticsContext["state"])
        assertEquals("ACTIVE", result.analyticsContext["expected"])
        // Verify basic context structure
        assertTrue(result.analyticsContext.containsKey("timestamp"))
        assertEquals("android", result.analyticsContext["platform"])
    }

    // SecurityException Tests
    @Test
    fun `given SecurityException with permission context, when toLiftrixError, then returns AuthenticationError`() {
        // Given
        val exception = SecurityException("Permission denied: WRITE_EXTERNAL_STORAGE")
        val context = mapOf("permission" to "WRITE_EXTERNAL_STORAGE")

        // When
        val result = exception.toLiftrixError(context)

        // Then
        assertTrue(result is LiftrixError.AuthenticationError)
        assertEquals("Permission denied: Permission denied: WRITE_EXTERNAL_STORAGE", result.errorMessage)
        assertFalse(result.isRecoverable)
        assertNull(result.retryAfter)
        assertEquals("Android", result.authProvider)
        assertEquals("PERMISSION_DENIED", result.errorCode)
        
        // Verify analytics context
        assertEquals("security_check", result.analyticsContext["operation"])
        assertEquals("permission_denied", result.analyticsContext["security_error_type"])
        assertEquals("Permission denied: WRITE_EXTERNAL_STORAGE", result.analyticsContext["permission_error"])
        assertEquals("SecurityException", result.analyticsContext["exception_type"])
        assertEquals("WRITE_EXTERNAL_STORAGE", result.analyticsContext["permission"])
        // Verify basic context structure
        assertTrue(result.analyticsContext.containsKey("timestamp"))
        assertEquals("android", result.analyticsContext["platform"])
    }

    // FileNotFoundException Tests
    @Test
    fun `given FileNotFoundException with file path, when toLiftrixError, then returns FileSystemError`() {
        // Given
        val exception = FileNotFoundException("/path/to/missing/file.txt")
        val context = mapOf("file_path" to "/path/to/missing/file.txt", "operation" to "READ")

        // When
        val result = exception.toLiftrixError(context)

        // Then
        assertTrue(result is LiftrixError.FileSystemError)
        assertEquals("File not found: /path/to/missing/file.txt", result.errorMessage)
        assertFalse(result.isRecoverable)
        assertNull(result.retryAfter)
        assertEquals("READ", result.operation)
        assertEquals("/path/to/missing/file.txt", result.filePath)
        
        // Verify analytics context
        assertEquals("file_system", result.analyticsContext["operation"])
        assertEquals("file_not_found", result.analyticsContext["file_error_type"])
        assertEquals("/path/to/missing/file.txt", result.analyticsContext["file_path"])
        assertEquals("FileNotFoundException", result.analyticsContext["exception_type"])
        // Verify basic context structure
        assertTrue(result.analyticsContext.containsKey("timestamp"))
        assertEquals("android", result.analyticsContext["platform"])
    }

    // ArithmeticException Tests
    @Test
    fun `given ArithmeticException with calculation context, when toLiftrixError, then returns CalculationError`() {
        // Given
        val exception = ArithmeticException("Division by zero")
        val context = mapOf("calculation" to "calorie_calculation", "operation" to "division")

        // When
        val result = exception.toLiftrixError(context)

        // Then
        assertTrue(result is LiftrixError.CalculationError)
        assertEquals("Calculation error: Division by zero", result.errorMessage)
        assertTrue(result.isRecoverable)
        assertEquals(1000L, result.retryAfter)
        assertEquals("division", result.operation)
        
        // Verify analytics context
        assertEquals("calculation", result.analyticsContext["operation"])
        assertEquals("arithmetic_error", result.analyticsContext["calculation_error_type"])
        assertEquals("Division by zero", result.analyticsContext["arithmetic_operation"])
        assertEquals("ArithmeticException", result.analyticsContext["exception_type"])
        assertEquals("calorie_calculation", result.analyticsContext["calculation"])
        // Verify basic context structure
        assertTrue(result.analyticsContext.containsKey("timestamp"))
        assertEquals("android", result.analyticsContext["platform"])
    }

    // NumberFormatException Tests
    @Test
    fun `given NumberFormatException with format context, when toLiftrixError, then returns CalculationError`() {
        // Given
        val exception = NumberFormatException("For input string: \"abc\"")
        val context = mapOf("input_value" to "abc", "expected_type" to "Double")

        // When
        val result = exception.toLiftrixError(context)

        // Then
        assertTrue(result is LiftrixError.CalculationError)
        assertEquals("Invalid number format: For input string: \"abc\"", result.errorMessage)
        assertTrue(result.isRecoverable)
        assertNull(result.retryAfter) // No automatic retry for format errors
        assertEquals("number_parsing", result.operation)
        
        // Verify analytics context
        assertEquals("number_parsing", result.analyticsContext["operation"])
        assertEquals("number_format_error", result.analyticsContext["calculation_error_type"])
        assertEquals("For input string: \"abc\"", result.analyticsContext["invalid_number"])
        assertEquals("NumberFormatException", result.analyticsContext["exception_type"])
        assertEquals("abc", result.analyticsContext["input_value"])
        assertEquals("Double", result.analyticsContext["expected_type"])
        // Verify basic context structure
        assertTrue(result.analyticsContext.containsKey("timestamp"))
        assertEquals("android", result.analyticsContext["platform"])
    }

    // Throwable Extension Tests
    @Test
    fun `given custom throwable, when toLiftrixError, then delegates to mapExceptionToLiftrixError`() {
        // Given
        val exception = RuntimeException("Custom runtime error")
        val operation = "custom_operation"
        val context = mapOf("custom_key" to "custom_value")

        // When
        val result = exception.toLiftrixError(operation, context)

        // Then
        assertTrue(result is LiftrixError.UnknownError)
        assertEquals("Unexpected error in custom_operation: Custom runtime error", result.errorMessage)
        assertFalse(result.isRecoverable)
        assertNull(result.retryAfter)
        
        // Verify analytics context includes both original and added context
        assertEquals("custom_operation", result.analyticsContext["operation"])
        assertEquals("extension_function", result.analyticsContext["error_mapping_source"])
        assertEquals("RuntimeException", result.analyticsContext["original_exception"])
        assertEquals("custom_value", result.analyticsContext["custom_key"])
        // Verify basic context structure
        assertTrue(result.analyticsContext.containsKey("timestamp"))
        assertEquals("android", result.analyticsContext["platform"])
    }

    // createLiftrixError Tests
    @Test
    fun `given exception with full context, when createLiftrixError, then returns error with comprehensive context`() {
        // Given
        val exception = SQLException("Database connection failed", "08001", 2003)
        val operation = "database_connection"
        val userId = "user_123"
        val context = mapOf("database" to "liftrix_prod", "host" to "db.liftrix.com")

        // When
        val result = createLiftrixError(exception, operation, userId, context)

        // Then
        assertTrue(result is LiftrixError.DatabaseError)
        assertEquals("Database operation failed: Database connection failed", result.errorMessage)
        
        // Verify comprehensive context
        assertEquals("database_connection", result.analyticsContext["operation"])
        assertEquals("user_123", result.analyticsContext["user_id"])
        assertEquals("createLiftrixError", result.analyticsContext["error_creation_source"])
        assertEquals("SQLException", result.analyticsContext["exception_type"])
        assertEquals("liftrix_prod", result.analyticsContext["database"])
        assertEquals("db.liftrix.com", result.analyticsContext["host"])
        assertTrue(result.analyticsContext.containsKey("timestamp"))
        assertEquals("android", result.analyticsContext["platform"])
        assertEquals("liftrix_app", result.analyticsContext["error_source"])
    }

    @Test
    fun `given exception without userId, when createLiftrixError, then returns error without user context`() {
        // Given
        val exception = IOException("Network error")
        val operation = "network_operation"

        // When
        val result = createLiftrixError(exception, operation)

        // Then
        assertTrue(result is LiftrixError.NetworkError)
        assertEquals("createLiftrixError", result.analyticsContext["error_creation_source"])
        assertEquals("IOException", result.analyticsContext["exception_type"])
        assertFalse(result.analyticsContext.containsKey("user_id"))
        // Verify basic context structure
        assertTrue(result.analyticsContext.containsKey("timestamp"))
        assertEquals("android", result.analyticsContext["platform"])
        assertEquals("liftrix_app", result.analyticsContext["error_source"])
    }

    // withEnhancedContext Tests
    @Test
    fun `given existing LiftrixError, when withEnhancedContext, then returns error with merged context`() {
        // Given
        val originalError = LiftrixError.NetworkError(
            errorMessage = "Connection failed",
            analyticsContext = mapOf("original_key" to "original_value")
        )
        val operation = "enhanced_operation"
        val additionalContext = mapOf("additional_key" to "additional_value")

        // When
        val result = originalError.withEnhancedContext(operation, additionalContext)

        // Then
        assertTrue(result is LiftrixError.NetworkError)
        assertEquals("Connection failed", result.errorMessage)
        
        // Verify merged context
        assertEquals("original_value", result.analyticsContext["original_key"])
        assertEquals("additional_value", result.analyticsContext["additional_key"])
        assertEquals("enhanced_operation", result.analyticsContext["operation"])
        assertEquals("NetworkError", result.analyticsContext["original_error_type"])
        assertTrue(result.analyticsContext.containsKey("context_enhancement_timestamp"))
        assertTrue(result.analyticsContext.containsKey("timestamp"))
        assertEquals("android", result.analyticsContext["platform"])
        assertEquals("liftrix_app", result.analyticsContext["error_source"])
    }

    // Edge Cases Tests
    @Test
    fun `given exception with null message, when toLiftrixError, then handles gracefully`() {
        // Given
        val exception = RuntimeException(null as String?)
        val operation = "null_message_test"

        // When
        val result = exception.toLiftrixError(operation)

        // Then
        assertTrue(result is LiftrixError.UnknownError)
        assertEquals("Unexpected error in null_message_test: null", result.errorMessage)
        assertEquals("no_message", result.analyticsContext["exception_message"])
        // Verify basic context structure
        assertTrue(result.analyticsContext.containsKey("timestamp"))
        assertEquals("android", result.analyticsContext["platform"])
    }

    @Test
    fun `given exception with empty context, when toLiftrixError, then creates context with defaults`() {
        // Given
        val exception = IllegalArgumentException("Test error")
        val emptyContext = emptyMap<String, String>()

        // When
        val result = exception.toLiftrixError(emptyContext)

        // Then
        assertTrue(result is LiftrixError.ValidationError)
        assertEquals("unknown_field", result.field)
        assertEquals("validation", result.analyticsContext["operation"])
        assertEquals("android", result.analyticsContext["platform"])
        assertEquals("liftrix_app", result.analyticsContext["error_source"])
        // Verify timestamp is present
        assertTrue(result.analyticsContext.containsKey("timestamp"))
    }

    @Test
    fun `given large context map, when toLiftrixError, then preserves all context`() {
        // Given
        val exception = IOException("Large context test")
        val largeContext = (1..50).associate { "key_$it" to "value_$it" }

        // When
        val result = exception.toLiftrixError(largeContext)

        // Then
        assertTrue(result is LiftrixError.NetworkError)
        
        // Verify all context keys are preserved
        largeContext.forEach { (key, value) ->
            assertEquals(value, result.analyticsContext[key])
        }
        // Verify basic context structure is also present
        assertTrue(result.analyticsContext.containsKey("timestamp"))
        assertEquals("android", result.analyticsContext["platform"])
        assertEquals("liftrix_app", result.analyticsContext["error_source"])
    }
}