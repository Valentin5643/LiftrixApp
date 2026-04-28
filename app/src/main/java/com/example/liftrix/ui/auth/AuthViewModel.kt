package com.example.liftrix.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.AuthEvent
import com.example.liftrix.domain.model.AuthState
import com.example.liftrix.domain.service.ConsentManagementService
import com.example.liftrix.domain.usecase.auth.AuthCommandUseCase
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.usecase.guest.ManageGuestSessionUseCase
import com.example.liftrix.ui.auth.components.ConsentData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authCommandUseCase: AuthCommandUseCase,
    private val authRepository: AuthRepository,
    private val manageGuestSessionUseCase: ManageGuestSessionUseCase,
    private val consentManagementService: ConsentManagementService
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    // Track if we're in the middle of an authentication operation
    private var isAuthOperationInProgress = false

    init {
        observeAuthState()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                // CRITICAL FIX: Don't update state if we're in the middle of an auth operation
                // This prevents race conditions where Firebase Auth state updates override
                // authentication errors before they can be displayed
                if (isAuthOperationInProgress) {
                    Timber.d("Auth state observer: Ignoring update during active operation (user=${user?.uid})")
                    return@collect
                }
                
                // Only update state if we're not in an error or loading state
                // This prevents overriding error messages that should be shown to user
                if (_authState.value !is AuthState.Error && _authState.value !is AuthState.Loading) {
                    _authState.value = if (user != null) {
                        AuthState.Authenticated(user)
                    } else {
                        AuthState.Unauthenticated
                    }
                    Timber.d("Auth state updated by observer: ${if (user != null) "Authenticated (${user.uid})" else "Unauthenticated"}")
                } else {
                    Timber.d("Auth state observer preserving current state: ${_authState.value}")
                }
            }
        }
    }

    fun checkInitialAuthState() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                // Check if user is already authenticated via Firebase Auth persistence
                val currentUser = authRepository.getCurrentUser()
                if (currentUser != null) {
                    _authState.value = AuthState.Authenticated(currentUser)
                    Timber.d("User already authenticated: ${currentUser.uid}")
                } else {
                    _authState.value = AuthState.Unauthenticated
                    Timber.d("No authenticated user found")
                }
            } catch (exception: Exception) {
                val errorMessage = getErrorMessage(exception)
                _authState.value = AuthState.Error(errorMessage, exception)
                Timber.e(exception, "Failed to check initial auth state")
            }
        }
    }

    fun handleEvent(event: AuthEvent) {
        when (event) {
            is AuthEvent.EmailPasswordSignIn -> signInWithEmail(event.email, event.password)
            is AuthEvent.EmailPasswordSignUp -> signUpWithEmail(event.email, event.password, event.username)
            is AuthEvent.EmailPasswordSignUpWithConsent -> signUpWithEmailAndConsent(
                event.email, event.password, event.username, event.consents
            )
            is AuthEvent.ForgotPassword -> sendPasswordResetEmail(event.email)
            is AuthEvent.GoogleSignIn -> {
                // Mark operation as in progress when Google Sign-In starts
                isAuthOperationInProgress = true
            }
            is AuthEvent.AnonymousSignIn -> signInAnonymously()
            is AuthEvent.SignOut -> signOut()
            is AuthEvent.ClearError -> clearError()
        }
    }

    fun handleGoogleSignInResult(idToken: String?) {
        if (idToken != null) {
            signInWithGoogle(idToken)
        } else {
            viewModelScope.launch {
                _authState.value = AuthState.Error("Google Sign-In was cancelled or failed. Please ensure you have a Google account signed in on this device.")
                // Add small delay to ensure error state is properly set before allowing observer updates
                kotlinx.coroutines.delay(100)
                isAuthOperationInProgress = false
            }
        }
    }

    private fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            isAuthOperationInProgress = true
            _authState.value = AuthState.Loading

            val result = authCommandUseCase.signInWithEmail(email, password)
            result.fold(
                onSuccess = { user ->
                    _authState.value = AuthState.Authenticated(user)
                    Timber.d("Sign in successful for user: ${user.uid}")
                    isAuthOperationInProgress = false
                },
                onFailure = { exception ->
                    val errorMessage = getErrorMessage(exception)
                    _authState.value = AuthState.Error(errorMessage, exception)
                    Timber.e(exception, "Sign in failed")
                    // Add small delay to ensure error state is properly set before allowing observer updates
                    // This prevents race conditions where Firebase Auth state overrides the error
                    kotlinx.coroutines.delay(100)
                    isAuthOperationInProgress = false
                }
            )
        }
    }

    private fun signUpWithEmail(email: String, password: String, username: String) {
        viewModelScope.launch {
            isAuthOperationInProgress = true
            _authState.value = AuthState.Loading

            val result = authCommandUseCase.signUpWithEmail(email, password, username)
            result.fold(
                onSuccess = { user ->
                    _authState.value = AuthState.Authenticated(user)
                    Timber.d("Sign up successful for user: ${user.uid}")
                    isAuthOperationInProgress = false
                },
                onFailure = { exception ->
                    val errorMessage = getErrorMessage(exception)
                    _authState.value = AuthState.Error(errorMessage, exception)
                    Timber.e(exception, "Sign up failed")
                    // Add small delay to ensure error state is properly set before allowing observer updates
                    kotlinx.coroutines.delay(100)
                    isAuthOperationInProgress = false
                }
            )
        }
    }

    private fun signUpWithEmailAndConsent(
        email: String,
        password: String,
        username: String,
        consents: ConsentData
    ) {
        viewModelScope.launch {
            isAuthOperationInProgress = true
            _authState.value = AuthState.Loading

            // First, create the account
            val signUpResult = authCommandUseCase.signUpWithEmail(email, password, username)
            signUpResult.fold(
                onSuccess = { user ->
                    // Account created, now record consents
                    val consentResult = consentManagementService.recordConsent(
                        userId = user.uid,
                        privacyPolicyVersion = "1.0.0", // Follow-up: Get from constants
                        healthDataConsent = consents.healthData,
                        aiChatConsent = consents.aiChat,
                        analyticsConsent = consents.analytics,
                        marketingConsent = false // Not collected in current dialog
                    )

                    consentResult.fold(
                        onSuccess = {
                            _authState.value = AuthState.Authenticated(user)
                            Timber.d("Sign up with consent successful for user: ${user.uid}")
                            isAuthOperationInProgress = false
                        },
                        onFailure = { consentError ->
                            // Consent recording failed - this is a critical error
                            // We should delete the account to maintain GDPR compliance
                            Timber.e(consentError, "Failed to record consent for user: ${user.uid}")
                            _authState.value = AuthState.Error(
                                "Account creation failed. Please try again.",
                                consentError
                            )
                            kotlinx.coroutines.delay(100)
                            isAuthOperationInProgress = false
                        }
                    )
                },
                onFailure = { exception ->
                    val errorMessage = getErrorMessage(exception)
                    _authState.value = AuthState.Error(errorMessage, exception)
                    Timber.e(exception, "Sign up with consent failed")
                    kotlinx.coroutines.delay(100)
                    isAuthOperationInProgress = false
                }
            )
        }
    }

    private fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            isAuthOperationInProgress = true
            _authState.value = AuthState.Loading

            val result = authCommandUseCase.signInWithGoogle(idToken)
            result.fold(
                onSuccess = { user ->
                    _authState.value = AuthState.Authenticated(user)
                    Timber.d("Google sign in successful for user: ${user.uid}")
                    isAuthOperationInProgress = false
                },
                onFailure = { exception ->
                    val errorMessage = getErrorMessage(exception)
                    _authState.value = AuthState.Error(errorMessage, exception)
                    Timber.e(exception, "Google sign in failed")
                    // Add small delay to ensure error state is properly set before allowing observer updates
                    kotlinx.coroutines.delay(100)
                    isAuthOperationInProgress = false
                }
            )
        }
    }

    private fun signInAnonymously() {
        viewModelScope.launch {
            isAuthOperationInProgress = true
            _authState.value = AuthState.Loading

            val result = authCommandUseCase.signInAnonymously()
            result.fold(
                onSuccess = { user ->
                    // Initialize guest session tracking for anonymous users
                    if (user.isAnonymous) {
                        manageGuestSessionUseCase.getOrCreateGuestSession(user.uid)
                            .onSuccess { guestSession ->
                                Timber.d("Guest session initialized for user: ${user.uid}, workouts remaining: ${guestSession.getWorkoutsRemaining()}")
                            }
                            .onFailure { error ->
                                Timber.w("Failed to initialize guest session for ${user.uid}: ${error.message}")
                                // Don't fail authentication, just log the warning
                            }
                    }
                    _authState.value = AuthState.Authenticated(user)
                    Timber.d("Anonymous sign in successful for user: ${user.uid}")
                    isAuthOperationInProgress = false
                },
                onFailure = { exception ->
                    val errorMessage = getErrorMessage(exception)
                    _authState.value = AuthState.Error(errorMessage, exception)
                    Timber.e(exception, "Anonymous sign in failed")
                    // Add small delay to ensure error state is properly set before allowing observer updates
                    kotlinx.coroutines.delay(100)
                    isAuthOperationInProgress = false
                }
            )
        }
    }

    private fun signOut() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading

            val result = authCommandUseCase.signOut()
            result.fold(
                onSuccess = {
                    _authState.value = AuthState.Unauthenticated
                    Timber.d("Sign out successful")
                },
                onFailure = { exception ->
                    val errorMessage = getErrorMessage(exception)
                    _authState.value = AuthState.Error(errorMessage, exception)
                    Timber.e(exception, "Sign out failed")
                }
            )
        }
    }

    private fun sendPasswordResetEmail(email: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading

            val result = authCommandUseCase.resetPassword(email)
            result.fold(
                onSuccess = {
                    _authState.value = AuthState.Unauthenticated
                    // Could show a success message here
                    Timber.d("Password reset email sent successfully")
                },
                onFailure = { exception ->
                    val errorMessage = getErrorMessage(exception)
                    _authState.value = AuthState.Error(errorMessage, exception)
                    Timber.e(exception, "Failed to send password reset email")
                }
            )
        }
    }

    private fun clearError() {
        when (val currentState = _authState.value) {
            is AuthState.Error -> {
                _authState.value = AuthState.Unauthenticated
                Timber.d("Error cleared, returning to unauthenticated state")
            }
            else -> { 
                Timber.d("ClearError called but current state is not Error: $currentState")
            }
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
