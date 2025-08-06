package com.example.liftrix.data.repository

import com.example.liftrix.core.error.TestFirebaseErrorMapper
import com.example.liftrix.core.error.MockFirebaseAuthException
import com.example.liftrix.core.error.MockFirebaseFirestoreException
import com.example.liftrix.core.error.MockFirestoreCode
import com.example.liftrix.core.error.MockFirebaseNetworkException
import com.example.liftrix.core.error.MockFirebaseTooManyRequestsException
import com.example.liftrix.domain.model.error.LiftrixError
import org.junit.Test
import org.junit.Assert.*

/**
 * Comprehensive test suite for Firebase error handling integration with LiftrixError system.
 * 
 * Uses mock Firebase exceptions to avoid static initializer issues while validating that 
 * Firebase exceptions are correctly mapped to appropriate LiftrixError types with proper 
 * context, recovery information, and retry logic.
 */
class FirebaseErrorHandlingTest {

    @Test
    fun `firebase auth exception maps to authentication error with proper context`() {
        // Given
        val firebaseAuthException = MockFirebaseAuthException(
            errorCode = "ERROR_INVALID_EMAIL",
            message = "The email address is badly formatted."
        )

        // When
        val result = TestFirebaseErrorMapper.handleMockFirebaseError(firebaseAuthException)

        // Then
        assertTrue("Should map to AuthenticationError", result is LiftrixError.AuthenticationError)
        val authError = result as LiftrixError.AuthenticationError
        assertEquals("INVALID_EMAIL", authError.errorCode)
        assertEquals("Firebase", authError.authProvider)
        assertFalse("Email validation errors should not be recoverable", authError.isRecoverable)
        assertEquals("auth", authError.analyticsContext["firebase_service"])
    }

    @Test
    fun `firebase auth network error is recoverable with retry`() {
        // Given
        val firebaseAuthException = MockFirebaseAuthException(
            errorCode = "ERROR_NETWORK_REQUEST_FAILED",
            message = "Network request failed"
        )

        // When
        val result = TestFirebaseErrorMapper.handleMockFirebaseError(firebaseAuthException)

        // Then
        assertTrue("Should map to AuthenticationError", result is LiftrixError.AuthenticationError)
        val authError = result as LiftrixError.AuthenticationError
        assertEquals("NETWORK_ERROR", authError.errorCode)
        assertTrue("Network errors should be recoverable", authError.isRecoverable)
        assertEquals(3000L, authError.retryAfter)
        assertTrue("Should be retryable", TestFirebaseErrorMapper.shouldRetryFirebaseOperation(authError))
    }

    @Test
    fun `firebase too many requests error has proper backoff`() {
        // Given
        val firebaseAuthException = MockFirebaseAuthException(
            errorCode = "ERROR_TOO_MANY_REQUESTS",
            message = "Too many requests"
        )

        // When
        val result = TestFirebaseErrorMapper.handleMockFirebaseError(firebaseAuthException)

        // Then
        assertTrue("Should map to AuthenticationError", result is LiftrixError.AuthenticationError)
        val authError = result as LiftrixError.AuthenticationError
        assertEquals("TOO_MANY_REQUESTS", authError.errorCode)
        assertTrue("Rate limit errors should be recoverable", authError.isRecoverable)
        assertEquals(60000L, authError.retryAfter) // 1 minute backoff
    }

    @Test
    fun `firestore permission denied maps to authentication error`() {
        // Given
        val firestoreException = MockFirebaseFirestoreException(
            code = MockFirestoreCode.PERMISSION_DENIED,
            message = "Permission denied"
        )

        // When
        val result = TestFirebaseErrorMapper.handleMockFirebaseError(firestoreException)

        // Then
        assertTrue("Should map to AuthenticationError", result is LiftrixError.AuthenticationError)
        val authError = result as LiftrixError.AuthenticationError
        assertEquals("PERMISSION_DENIED", authError.errorCode)
        assertEquals("Firestore", authError.authProvider)
        assertFalse("Permission errors should not be recoverable", authError.isRecoverable)
    }

    @Test
    fun `firestore unavailable error is recoverable with retry`() {
        // Given
        val firestoreException = MockFirebaseFirestoreException(
            code = MockFirestoreCode.UNAVAILABLE,
            message = "Service unavailable"
        )

        // When
        val result = TestFirebaseErrorMapper.handleMockFirebaseError(firestoreException)

        // Then
        assertTrue("Should map to NetworkError", result is LiftrixError.NetworkError)
        val networkError = result as LiftrixError.NetworkError
        assertTrue("Service unavailable should be recoverable", networkError.isRecoverable)
        assertEquals(10000L, networkError.retryAfter) // 10 seconds
        assertEquals(503, networkError.httpStatusCode)
        assertTrue("Should be retryable", TestFirebaseErrorMapper.shouldRetryFirebaseOperation(networkError))
    }

    @Test
    fun `firestore transaction aborted is recoverable`() {
        // Given
        val firestoreException = MockFirebaseFirestoreException(
            code = MockFirestoreCode.ABORTED,
            message = "Transaction aborted"
        )

        // When
        val result = TestFirebaseErrorMapper.handleMockFirebaseError(firestoreException)

        // Then
        assertTrue("Should map to DatabaseError", result is LiftrixError.DatabaseError)
        val dbError = result as LiftrixError.DatabaseError
        assertTrue("Transaction conflicts should be recoverable", dbError.isRecoverable)
        assertEquals(1000L, dbError.retryAfter) // 1 second retry
        assertEquals("TRANSACTION", dbError.operation)
        assertTrue("Should be retryable", TestFirebaseErrorMapper.shouldRetryFirebaseOperation(dbError))
    }

    @Test
    fun `firestore not found error is not recoverable`() {
        // Given
        val firestoreException = MockFirebaseFirestoreException(
            code = MockFirestoreCode.NOT_FOUND,
            message = "Document not found"
        )

        // When
        val result = TestFirebaseErrorMapper.handleMockFirebaseError(firestoreException)

        // Then
        assertTrue("Should map to DatabaseError", result is LiftrixError.DatabaseError)
        val dbError = result as LiftrixError.DatabaseError
        assertFalse("Not found errors should not be recoverable", dbError.isRecoverable)
        assertEquals("READ", dbError.operation)
        assertFalse("Should not be retryable", TestFirebaseErrorMapper.shouldRetryFirebaseOperation(dbError))
    }

    @Test
    fun `firebase network exception maps to network error`() {
        // Given
        val networkException = MockFirebaseNetworkException(
            message = "Network connection failed"
        )

        // When
        val result = TestFirebaseErrorMapper.handleMockFirebaseError(networkException)

        // Then
        assertTrue("Should map to NetworkError", result is LiftrixError.NetworkError)
        val networkError = result as LiftrixError.NetworkError
        assertTrue("Network errors should be recoverable", networkError.isRecoverable)
        assertEquals(3000L, networkError.retryAfter)
        assertEquals("Firebase", networkError.networkType)
        assertTrue("Should be retryable", TestFirebaseErrorMapper.shouldRetryFirebaseOperation(networkError))
    }

    @Test
    fun `firebase too many requests exception has rate limit backoff`() {
        // Given
        val tooManyRequestsException = MockFirebaseTooManyRequestsException(
            message = "Too many requests"
        )

        // When
        val result = TestFirebaseErrorMapper.handleMockFirebaseError(tooManyRequestsException)

        // Then
        assertTrue("Should map to NetworkError", result is LiftrixError.NetworkError)
        val networkError = result as LiftrixError.NetworkError
        assertTrue("Rate limit errors should be recoverable", networkError.isRecoverable)
        assertEquals(30000L, networkError.retryAfter) // 30 seconds
        assertEquals(429, networkError.httpStatusCode)
        assertEquals("Firebase", networkError.networkType)
    }

    @Test
    fun `unknown firebase exception maps to unknown error`() {
        // Given
        val unknownException = RuntimeException("Unknown Firebase error")

        // When
        val result = TestFirebaseErrorMapper.handleMockFirebaseError(unknownException)

        // Then
        assertTrue("Should map to UnknownError", result is LiftrixError.UnknownError)
        val unknownError = result as LiftrixError.UnknownError
        assertFalse("Unknown errors should not be recoverable by default", unknownError.isRecoverable)
        assertEquals("unknown", unknownError.analyticsContext["firebase_service"])
        assertFalse("Should not be retryable", TestFirebaseErrorMapper.shouldRetryFirebaseOperation(unknownError))
    }

    @Test
    fun `retry logic correctly identifies retryable errors`() {
        // Given retryable errors
        val networkError = LiftrixError.NetworkError(
            httpStatusCode = 503,
            isRecoverable = true
        )
        val transactionError = LiftrixError.DatabaseError(
            analyticsContext = mapOf("firestore_code" to "ABORTED"),
            isRecoverable = true
        )
        
        // Given non-retryable errors
        val validationError = LiftrixError.ValidationError(
            field = "email",
            violations = listOf("Invalid format")
        )
        val notFoundError = LiftrixError.DatabaseError(
            analyticsContext = mapOf("firestore_code" to "NOT_FOUND"),
            isRecoverable = false
        )

        // When/Then
        assertTrue("5xx network errors should be retryable", 
            TestFirebaseErrorMapper.shouldRetryFirebaseOperation(networkError))
        assertTrue("Transaction conflicts should be retryable", 
            TestFirebaseErrorMapper.shouldRetryFirebaseOperation(transactionError))
        assertFalse("Validation errors should not be retryable", 
            TestFirebaseErrorMapper.shouldRetryFirebaseOperation(validationError))
        assertFalse("Not found errors should not be retryable", 
            TestFirebaseErrorMapper.shouldRetryFirebaseOperation(notFoundError))
    }

    @Test
    fun `analytics context contains firebase service information`() {
        // Given
        val firebaseAuthException = MockFirebaseAuthException(
            errorCode = "ERROR_USER_DISABLED",
            message = "User account disabled"
        )

        // When
        val result = TestFirebaseErrorMapper.handleMockFirebaseError(firebaseAuthException)

        // Then
        val analyticsContext = result.analyticsContext
        assertEquals("auth", analyticsContext["firebase_service"])
        assertEquals("FirebaseAuthException", analyticsContext["error_type"])
        assertEquals("ACCOUNT_DISABLED", analyticsContext["firebase_auth_error_code"])
        
        // Verify analytics context is useful for debugging
        assertTrue("Analytics context should contain service info", 
            analyticsContext.containsKey("firebase_service"))
        assertTrue("Analytics context should contain error type", 
            analyticsContext.containsKey("error_type"))
    }
}