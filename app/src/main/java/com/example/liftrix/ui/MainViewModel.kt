package com.example.liftrix.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthenticationState>(AuthenticationState.Loading)
    val authState: StateFlow<AuthenticationState> = _authState.asStateFlow()
    
    // Track if we're in an explicit authentication flow (from AuthActivity)
    private var isExplicitAuthFlow = false

    init {
        observeAuthState()
    }
    
    fun setExplicitAuthFlow(isExplicit: Boolean) {
        isExplicitAuthFlow = isExplicit
        Timber.d("Explicit auth flow set to: $isExplicit")
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                // Only update state if we're not in the middle of an explicit auth flow
                // This prevents MainViewModel from overriding AuthViewModel's error states
                if (!isExplicitAuthFlow) {
                    _authState.value = if (user != null) {
                        Timber.d("User authenticated (not during explicit auth): ${user.uid}")
                        AuthenticationState.Authenticated(user)
                    } else {
                        Timber.d("User not authenticated (not during explicit auth)")
                        AuthenticationState.Unauthenticated
                    }
                } else {
                    Timber.d("Skipping auth state update during explicit auth flow. User: ${user?.uid ?: "null"}")
                }
            }
        }
    }

    sealed class AuthenticationState {
        data object Loading : AuthenticationState()
        data object Unauthenticated : AuthenticationState()
        data class Authenticated(val user: User) : AuthenticationState()
    }
} 