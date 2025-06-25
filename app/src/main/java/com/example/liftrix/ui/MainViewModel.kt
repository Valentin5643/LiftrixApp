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

    init {
        observeAuthState()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _authState.value = if (user != null) {
                    Timber.d("User authenticated: ${user.uid}")
                    AuthenticationState.Authenticated(user)
                } else {
                    Timber.d("User not authenticated")
                    AuthenticationState.Unauthenticated
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