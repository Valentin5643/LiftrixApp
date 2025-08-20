package com.example.liftrix.domain.model

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
    data class ForgotPassword(val email: String) : AuthEvent()
    data object GoogleSignIn : AuthEvent()
    data object AnonymousSignIn : AuthEvent()
    data object SignOut : AuthEvent()
    data object ClearError : AuthEvent()
} 