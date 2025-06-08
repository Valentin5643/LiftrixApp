package com.example.liftrix.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.AuthEvent
import com.example.liftrix.domain.model.AuthState
import com.example.liftrix.domain.usecase.auth.ForgotPasswordUseCase
import com.example.liftrix.domain.usecase.auth.SignInAnonymouslyUseCase
import com.example.liftrix.domain.usecase.auth.SignInWithEmailUseCase
import com.example.liftrix.domain.usecase.auth.SignInWithGoogleUseCase
import com.example.liftrix.domain.usecase.auth.SignOutUseCase
import com.example.liftrix.domain.usecase.auth.SignUpWithEmailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val signInWithEmailUseCase: SignInWithEmailUseCase,
    private val signUpWithEmailUseCase: SignUpWithEmailUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val signInAnonymouslyUseCase: SignInAnonymouslyUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val forgotPasswordUseCase: ForgotPasswordUseCase
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun handleEvent(event: AuthEvent) {
        when (event) {
            is AuthEvent.EmailPasswordSignIn -> signInWithEmail(event.email, event.password)
            is AuthEvent.EmailPasswordSignUp -> signUpWithEmail(event.email, event.password, event.displayName)
            is AuthEvent.ForgotPassword -> sendPasswordResetEmail(event.email)
            is AuthEvent.GoogleSignIn -> _authState.value = AuthState.Loading // Google Sign-In flow will be handled by UI
            is AuthEvent.AnonymousSignIn -> signInAnonymously()
            is AuthEvent.SignOut -> signOut()
            is AuthEvent.ClearError -> clearError()
        }
    }

    fun handleGoogleSignInResult(idToken: String?) {
        if (idToken != null) {
            signInWithGoogle(idToken)
        } else {
            _authState.value = AuthState.Error("Google Sign-In was cancelled or failed. Please ensure you have a Google account signed in on this device.")
        }
    }

    private fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            
            signInWithEmailUseCase(email, password)
                .onSuccess { user ->
                    _authState.value = AuthState.Authenticated(user)
                    Timber.d("Sign in successful for user: ${user.uid}")
                }
                .onFailure { exception ->
                    val errorMessage = getErrorMessage(exception)
                    _authState.value = AuthState.Error(errorMessage, exception)
                    Timber.e(exception, "Sign in failed")
                }
        }
    }

    private fun signUpWithEmail(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            
            signUpWithEmailUseCase(email, password, displayName)
                .onSuccess { user ->
                    _authState.value = AuthState.Authenticated(user)
                    Timber.d("Sign up successful for user: ${user.uid}")
                }
                .onFailure { exception ->
                    val errorMessage = getErrorMessage(exception)
                    _authState.value = AuthState.Error(errorMessage, exception)
                    Timber.e(exception, "Sign up failed")
                }
        }
    }

    private fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            
            signInWithGoogleUseCase(idToken)
                .onSuccess { user ->
                    _authState.value = AuthState.Authenticated(user)
                    Timber.d("Google sign in successful for user: ${user.uid}")
                }
                .onFailure { exception ->
                    val errorMessage = getErrorMessage(exception)
                    _authState.value = AuthState.Error(errorMessage, exception)
                    Timber.e(exception, "Google sign in failed")
                }
        }
    }

    private fun signInAnonymously() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            
            signInAnonymouslyUseCase()
                .onSuccess { user ->
                    _authState.value = AuthState.Authenticated(user)
                    Timber.d("Anonymous sign in successful for user: ${user.uid}")
                }
                .onFailure { exception ->
                    val errorMessage = getErrorMessage(exception)
                    _authState.value = AuthState.Error(errorMessage, exception)
                    Timber.e(exception, "Anonymous sign in failed")
                }
        }
    }

    private fun signOut() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            
            signOutUseCase()
                .onSuccess {
                    _authState.value = AuthState.Unauthenticated
                    Timber.d("Sign out successful")
                }
                .onFailure { exception ->
                    val errorMessage = getErrorMessage(exception)
                    _authState.value = AuthState.Error(errorMessage, exception)
                    Timber.e(exception, "Sign out failed")
                }
        }
    }

    private fun sendPasswordResetEmail(email: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            
            forgotPasswordUseCase(email)
                .onSuccess {
                    _authState.value = AuthState.Unauthenticated
                    // Could show a success message here
                    Timber.d("Password reset email sent successfully")
                }
                .onFailure { exception ->
                    val errorMessage = getErrorMessage(exception)
                    _authState.value = AuthState.Error(errorMessage, exception)
                    Timber.e(exception, "Failed to send password reset email")
                }
        }
    }

    private fun clearError() {
        when (val currentState = _authState.value) {
            is AuthState.Error -> _authState.value = AuthState.Unauthenticated
            else -> { /* No action needed */ }
        }
    }

    private fun getErrorMessage(exception: Throwable): String {
        return when {
            exception.message?.contains("email", ignoreCase = true) == true -> "Invalid email address"
            exception.message?.contains("password", ignoreCase = true) == true -> "Invalid password"
            exception.message?.contains("network", ignoreCase = true) == true -> "Network error. Please check your connection"
            exception.message?.contains("user not found", ignoreCase = true) == true -> "No account found with this email"
            exception.message?.contains("wrong password", ignoreCase = true) == true -> "Incorrect password"
            exception.message?.contains("email already in use", ignoreCase = true) == true -> "An account with this email already exists"
            exception is IllegalArgumentException -> exception.message ?: "Invalid input"
            else -> exception.message ?: "Authentication failed. Please try again"
        }
    }
} 