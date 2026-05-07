package com.example.liftrix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.usecase.profile.ProfileQueryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileQueryUseCase: ProfileQueryUseCase
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

                        // AUTO-HEAL: Check if profile exists and create if missing
                        // This catches legacy users and interrupted onboarding scenarios
                        val profileExists = checkProfileExists(user.uid)
                        if (!profileExists) {
                            Timber.w("[AUTO-HEAL] User profile missing for ${user.uid}, creating default profile")
                            viewModelScope.launch {
                                try {
                                    authRepository.createUserProfile(user).fold(
                                        onSuccess = {
                                            Timber.i("[AUTO-HEAL] Successfully created missing profile for ${user.uid}")
                                        },
                                        onFailure = { error ->
                                            Timber.e("[AUTO-HEAL] Failed to create missing profile for ${user.uid}: $error")
                                            // Don't block authentication even if profile creation fails
                                        }
                                    )
                                } catch (e: Exception) {
                                    Timber.e(e, "[AUTO-HEAL] Exception creating missing profile for ${user.uid}")
                                }
                            }
                        } else {
                            Timber.d("[AUTO-HEAL] Profile exists for ${user.uid}, no action needed")
                        }

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

    /**
     * Checks if a user profile exists in the database.
     * Used at app startup to ensure profile is ready before showing main UI.
     *
     * @param userId The user ID to check
     * @return true if profile exists, false otherwise
     */
    suspend fun checkProfileExists(userId: String): Boolean {
        return try {
            profileQueryUseCase.hasProfile(userId).fold(
                onSuccess = { exists ->
                    Timber.d("Profile check for $userId: exists=$exists")
                    exists
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to check profile existence for $userId")
                    false
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Exception checking profile existence for $userId")
            false
        }
    }

    sealed class AuthenticationState {
        data object Loading : AuthenticationState()
        data object Unauthenticated : AuthenticationState()
        data class Authenticated(val user: User) : AuthenticationState()
    }
} 
