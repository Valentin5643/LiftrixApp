package com.example.liftrix.core.error

import com.example.liftrix.domain.model.error.LiftrixError

/**
 * Utility object for mapping Firebase exceptions to LiftrixError types.
 * 
 * Provides specialized error mapping for Firebase Auth, Firestore, and network-related
 * exceptions to ensure consistent error handling across the Firebase integration layer.
 */
object FirebaseErrorMapper {
    
    /**
     * Maps Firebase exceptions to appropriate LiftrixError types.
     * 
     * @param throwable The Firebase throwable to map
     * @return Mapped LiftrixError with Firebase-specific context
     */
    fun handleFirebaseError(throwable: Throwable): LiftrixError {
        val exception = if (throwable is Exception) throwable else Exception(throwable)
        return when (exception) {
            is com.google.firebase.auth.FirebaseAuthException -> mapFirebaseAuthError(exception)
            is com.google.firebase.firestore.FirebaseFirestoreException -> mapFirestoreError(exception)
            is com.google.firebase.FirebaseNetworkException -> LiftrixError.NetworkError(
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
            is com.google.firebase.FirebaseTooManyRequestsException -> LiftrixError.NetworkError(
                errorMessage = "Too many Firebase requests. Please wait before trying again",
                isRecoverable = true,
                retryAfter = 30000L, // 30 seconds backoff for rate limiting
                analyticsContext = mapOf(
                    "firebase_service" to "rate_limit",
                    "error_type" to "FirebaseTooManyRequestsException"
                ),
                networkType = "Firebase",
                httpStatusCode = 429
            )
            else -> LiftrixError.UnknownError(
                errorMessage = "Unexpected Firebase error: ${exception.message}",
                isRecoverable = false,
                analyticsContext = mapOf(
                    "firebase_service" to "unknown",
                    "error_type" to (exception::class.simpleName ?: "UnknownFirebaseException")
                )
            )
        }
    }
    
    /**
     * Maps Firebase Authentication exceptions to LiftrixError.AuthenticationError.
     */
    private fun mapFirebaseAuthError(exception: com.google.firebase.auth.FirebaseAuthException): LiftrixError.AuthenticationError {
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
            else -> exception.errorCode ?: "UNKNOWN_AUTH_ERROR"
        }
        
        val isRecoverable = when (errorCode) {
            "NETWORK_ERROR", "TOO_MANY_REQUESTS" -> true
            else -> false
        }
        
        val retryAfter = when (errorCode) {
            "TOO_MANY_REQUESTS" -> 60000L // 1 minute
            "NETWORK_ERROR" -> 3000L // 3 seconds
            else -> null
        }
        
        return LiftrixError.AuthenticationError(
            errorMessage = exception.message ?: "Authentication failed",
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
     * Maps Firebase Firestore exceptions to appropriate LiftrixError types.
     */
    private fun mapFirestoreError(exception: com.google.firebase.firestore.FirebaseFirestoreException): LiftrixError {
        return when (exception.code) {
            com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND -> LiftrixError.DatabaseError(
                errorMessage = "Requested Firestore document not found",
                isRecoverable = false,
                analyticsContext = mapOf(
                    "firestore_code" to "NOT_FOUND",
                    "firebase_service" to "firestore",
                    "error_type" to "FirebaseFirestoreException"
                ),
                operation = "READ"
            )
            
            com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> LiftrixError.AuthenticationError(
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
            
            com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE -> LiftrixError.NetworkError(
                errorMessage = "Firestore service is temporarily unavailable",
                isRecoverable = true,
                retryAfter = 10000L, // 10 seconds for service unavailable
                analyticsContext = mapOf(
                    "firestore_code" to "UNAVAILABLE",
                    "firebase_service" to "firestore",
                    "error_type" to "FirebaseFirestoreException"
                ),
                networkType = "Firestore",
                httpStatusCode = 503
            )
            
            com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED -> LiftrixError.DatabaseError(
                errorMessage = "Firestore transaction was aborted due to conflict",
                isRecoverable = true,
                retryAfter = 1000L, // 1 second retry for transaction conflicts
                analyticsContext = mapOf(
                    "firestore_code" to "ABORTED",
                    "firebase_service" to "firestore",
                    "error_type" to "FirebaseFirestoreException"
                ),
                operation = "TRANSACTION"
            )
            
            else -> LiftrixError.DatabaseError(
                errorMessage = "Firestore operation failed: ${exception.code}",
                isRecoverable = true,
                retryAfter = 3000L,
                analyticsContext = mapOf(
                    "firestore_code" to (exception.code?.toString() ?: "UNKNOWN"),
                    "firebase_service" to "firestore",
                    "error_type" to "FirebaseFirestoreException"
                ),
                operation = "FIRESTORE_OPERATION"
            )
        }
    }
    
    /**
     * Determines if a Firebase operation should be retried based on the error type.
     */
    fun shouldRetryFirebaseOperation(error: LiftrixError): Boolean {
        return error.isRecoverable && when (error) {
            is LiftrixError.NetworkError -> {
                // Retry network errors except for 4xx client errors (except 408, 429)
                error.httpStatusCode == null || 
                error.httpStatusCode == 408 || 
                error.httpStatusCode == 429 || 
                error.httpStatusCode >= 500
            }
            is LiftrixError.DatabaseError -> {
                // Retry transient database errors like conflicts and timeouts
                error.analyticsContext["firestore_code"] in listOf(
                    "CANCELLED", "UNKNOWN", "DEADLINE_EXCEEDED", 
                    "ABORTED", "INTERNAL", "UNAVAILABLE"
                )
            }
            is LiftrixError.AuthenticationError -> {
                // Only retry auth errors for network issues or rate limiting
                error.errorCode in listOf("NETWORK_ERROR", "TOO_MANY_REQUESTS")
            }
            else -> false
        }
    }
}