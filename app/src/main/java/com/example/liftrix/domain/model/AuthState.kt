package com.example.liftrix.domain.model

import com.example.liftrix.ui.auth.components.ConsentData

sealed class AuthState {
    data object Initial : AuthState()
    data object Loading : AuthState()
    data class Authenticated(val user: User) : AuthState()
    data object Unauthenticated : AuthState()
    data class Error(val message: String, val throwable: Throwable? = null) : AuthState()
}

sealed class AuthEvent {
    data class EmailPasswordSignIn(val email: String, val password: String) : AuthEvent()
    data class EmailPasswordSignUp(val email: String, val password: String, val username: String) : AuthEvent()
    data class EmailPasswordSignUpWithConsent(
        val email: String,
        val password: String,
        val username: String,
        val consents: ConsentData
    ) : AuthEvent()
    data class ForgotPassword(val email: String) : AuthEvent()
    data object GoogleSignIn : AuthEvent()
    data object AnonymousSignIn : AuthEvent()
    data object SignOut : AuthEvent()
    data object ClearError : AuthEvent()
} 