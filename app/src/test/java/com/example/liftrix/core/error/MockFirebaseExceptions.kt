package com.example.liftrix.core.error

/**
 * Mock Firebase exceptions for unit testing that avoid static initializer issues.
 * These mock the behavior of Firebase exceptions without requiring Firebase SDK initialization.
 */

/**
 * Mock FirebaseAuthException for testing
 */
class MockFirebaseAuthException(
    val errorCode: String,
    override val message: String
) : Exception(message)

/**
 * Mock FirebaseFirestoreException for testing
 */
class MockFirebaseFirestoreException(
    val code: MockFirestoreCode,
    override val message: String
) : Exception(message)

/**
 * Mock Firestore error codes that mirror Firebase's codes
 */
enum class MockFirestoreCode {
    NOT_FOUND,
    PERMISSION_DENIED,
    UNAVAILABLE,
    ABORTED,
    UNKNOWN
}

/**
 * Mock FirebaseNetworkException for testing
 */
class MockFirebaseNetworkException(
    override val message: String
) : Exception(message)

/**
 * Mock FirebaseTooManyRequestsException for testing
 */
class MockFirebaseTooManyRequestsException(
    override val message: String
) : Exception(message)

/**
 * Test-specific Firebase error mapper that handles mock exceptions
 */
object TestFirebaseErrorMapper {
    
    /**
     * Maps mock Firebase exceptions to LiftrixError types for testing
     */
    fun handleMockFirebaseError(throwable: Throwable): com.example.liftrix.domain.model.error.LiftrixError {
        return when (throwable) {
            is MockFirebaseAuthException -> mapMockFirebaseAuthError(throwable)
            is MockFirebaseFirestoreException -> mapMockFirestoreError(throwable)
            is MockFirebaseNetworkException -> com.example.liftrix.domain.model.error.LiftrixError.NetworkError(
                errorMessage = "Firebase network connection failed",
                isRecoverable = true,
                retryAfter = 3000L,
                analyticsContext = mapOf(
                    "firebase_service" to "network",
                    "error_type" to "FirebaseNetworkException"
                ),
                networkType = "Firebase",
                httpStatusCode = null
            )
            is MockFirebaseTooManyRequestsException -> com.example.liftrix.domain.model.error.LiftrixError.NetworkError(
                errorMessage = "Too many Firebase requests. Please wait before trying again",
                isRecoverable = true,
                retryAfter = 30000L,
                analyticsContext = mapOf(
                    "firebase_service" to "rate_limit",
                    "error_type" to "FirebaseTooManyRequestsException"
                ),
                networkType = "Firebase",
                httpStatusCode = 429
            )
            else -> com.example.liftrix.domain.model.error.LiftrixError.UnknownError(
                errorMessage = "Unexpected Firebase error: ${throwable.message}",
                isRecoverable = false,
                analyticsContext = mapOf(
                    "firebase_service" to "unknown",
                    "error_type" to (throwable::class.simpleName ?: "UnknownFirebaseException")
                )
            )
        }
    }
    
    /**
     * Maps mock Firebase Auth exceptions to LiftrixError.AuthenticationError
     */
    private fun mapMockFirebaseAuthError(exception: MockFirebaseAuthException): com.example.liftrix.domain.model.error.LiftrixError.AuthenticationError {
        val errorCode = when (exception.errorCode) {
            "ERROR_INVALID_EMAIL" -> "INVALID_EMAIL"
            "ERROR_WRONG_PASSWORD" -> "INVALID_CREDENTIALS"
            "ERROR_USER_NOT_FOUND" -> "INVALID_CREDENTIALS"
            "ERROR_USER_DISABLED" -> "ACCOUNT_DISABLED"
            "ERROR_TOO_MANY_REQUESTS" -> "TOO_MANY_REQUESTS"
            "ERROR_OPERATION_NOT_ALLOWED" -> "OPERATION_NOT_ALLOWED"
            "ERROR_EMAIL_ALREADY_IN_USE" -> "EMAIL_ALREADY_IN_USE"
            "ERROR_WEAK_PASSWORD" -> "WEAK_PASSWORD"
            "ERROR_CREDENTIAL_ALREADY_IN_USE" -> "CREDENTIAL_ALREADY_IN_USE"
            "ERROR_INVALID_CREDENTIAL" -> "INVALID_CREDENTIALS"
            "ERROR_USER_TOKEN_EXPIRED" -> "TOKEN_EXPIRED"
            "ERROR_NETWORK_REQUEST_FAILED" -> "NETWORK_ERROR"
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> "ACCOUNT_EXISTS_DIFFERENT_CREDENTIAL"
            else -> exception.errorCode
        }
        
        val isRecoverable = when (errorCode) {
            "NETWORK_ERROR", "TOO_MANY_REQUESTS" -> true
            else -> false
        }
        
        val retryAfter = when (errorCode) {
            "TOO_MANY_REQUESTS" -> 60000L
            "NETWORK_ERROR" -> 3000L
            else -> null
        }
        
        return com.example.liftrix.domain.model.error.LiftrixError.AuthenticationError(
            errorMessage = exception.message,
            isRecoverable = isRecoverable,
            retryAfter = retryAfter,
            analyticsContext = mapOf(
                "firebase_auth_error_code" to errorCode,
                "firebase_service" to "auth",
                "error_type" to "FirebaseAuthException"
            ),
            authProvider = "Firebase",
            errorCode = errorCode
        )
    }
    
    /**
     * Maps mock Firestore exceptions to appropriate LiftrixError types
     */
    private fun mapMockFirestoreError(exception: MockFirebaseFirestoreException): com.example.liftrix.domain.model.error.LiftrixError {
        return when (exception.code) {
            MockFirestoreCode.NOT_FOUND -> com.example.liftrix.domain.model.error.LiftrixError.DatabaseError(
                errorMessage = "Requested Firestore document not found",
                isRecoverable = false,
                analyticsContext = mapOf(
                    "firestore_code" to "NOT_FOUND",
                    "firebase_service" to "firestore",
                    "error_type" to "FirebaseFirestoreException"
                ),
                operation = "READ"
            )
            
            MockFirestoreCode.PERMISSION_DENIED -> com.example.liftrix.domain.model.error.LiftrixError.AuthenticationError(
                errorMessage = "Permission denied for Firestore operation. This may indicate insufficient permissions, expired authentication, or missing security rules.",
                isRecoverable = false,
                analyticsContext = mapOf(
                    "firestore_code" to "PERMISSION_DENIED",
                    "firebase_service" to "firestore",
                    "error_type" to "FirebaseFirestoreException",
                    "suggestion" to "Check authentication state and Firebase Security Rules",
                    "recovery_action" to "Re-authenticate or skip operation"
                ),
                authProvider = "Firestore",
                errorCode = "PERMISSION_DENIED"
            )
            
            MockFirestoreCode.UNAVAILABLE -> com.example.liftrix.domain.model.error.LiftrixError.NetworkError(
                errorMessage = "Firestore service is temporarily unavailable",
                isRecoverable = true,
                retryAfter = 10000L,
                analyticsContext = mapOf(
                    "firestore_code" to "UNAVAILABLE",
                    "firebase_service" to "firestore",
                    "error_type" to "FirebaseFirestoreException"
                ),
                networkType = "Firestore",
                httpStatusCode = 503
            )
            
            MockFirestoreCode.ABORTED -> com.example.liftrix.domain.model.error.LiftrixError.DatabaseError(
                errorMessage = "Firestore transaction was aborted due to conflict",
                isRecoverable = true,
                retryAfter = 1000L,
                analyticsContext = mapOf(
                    "firestore_code" to "ABORTED",
                    "firebase_service" to "firestore",
                    "error_type" to "FirebaseFirestoreException"
                ),
                operation = "TRANSACTION"
            )
            
            else -> com.example.liftrix.domain.model.error.LiftrixError.DatabaseError(
                errorMessage = "Firestore operation failed: ${exception.code}",
                isRecoverable = true,
                retryAfter = 3000L,
                analyticsContext = mapOf(
                    "firestore_code" to exception.code.toString(),
                    "firebase_service" to "firestore",
                    "error_type" to "FirebaseFirestoreException"
                ),
                operation = "FIRESTORE_OPERATION"
            )
        }
    }
    
    /**
     * Test version of shouldRetryFirebaseOperation that matches production behavior
     */
    fun shouldRetryFirebaseOperation(error: com.example.liftrix.domain.model.error.LiftrixError): Boolean {
        return error.isRecoverable && when (error) {
            is com.example.liftrix.domain.model.error.LiftrixError.NetworkError -> {
                error.httpStatusCode == null || 
                error.httpStatusCode == 408 || 
                error.httpStatusCode == 429 || 
                error.httpStatusCode >= 500
            }
            is com.example.liftrix.domain.model.error.LiftrixError.DatabaseError -> {
                error.analyticsContext["firestore_code"] in listOf(
                    "CANCELLED", "UNKNOWN", "DEADLINE_EXCEEDED", 
                    "ABORTED", "INTERNAL", "UNAVAILABLE"
                )
            }
            is com.example.liftrix.domain.model.error.LiftrixError.AuthenticationError -> {
                error.errorCode in listOf("NETWORK_ERROR", "TOO_MANY_REQUESTS")
            }
            else -> false
        }
    }
}