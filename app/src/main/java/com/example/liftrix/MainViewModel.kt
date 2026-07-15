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

    private val _profileReadiness = MutableStateFlow<ProfileReadiness>(ProfileReadiness.NotRequired)
    val profileReadiness: StateFlow<ProfileReadiness> = _profileReadiness.asStateFlow()

    private val _aiAccessEligibility = MutableStateFlow<AiAccessEligibility>(AiAccessEligibility.Loading)
    val aiAccessEligibility: StateFlow<AiAccessEligibility> = _aiAccessEligibility.asStateFlow()
    
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

                        refreshAiAccessEligibility(user.uid)
                        ensureProfileReady(user)

                        AuthenticationState.Authenticated(user)
                    } else {
                        Timber.d("User not authenticated (not during explicit auth)")
                        _aiAccessEligibility.value = AiAccessEligibility.Ineligible
                        _profileReadiness.value = ProfileReadiness.NotRequired
                        AuthenticationState.Unauthenticated
                    }
                } else {
                    Timber.d("Skipping auth state update during explicit auth flow. User: ${user?.uid ?: "null"}")
                }
            }
        }
    }

    private fun refreshAiAccessEligibility(userId: String) {
        // AI is available to authenticated users. PaidAiCallExecutor remains the
        // authoritative guard for Remote Config, abuse policy, and per-user limits.
        _aiAccessEligibility.value = AiAccessEligibility.Eligible(userId)
    }

    private suspend fun ensureProfileReady(user: User) {
        _profileReadiness.value = ProfileReadiness.Checking(user.uid)

        // AUTO-HEAL: Check if profile exists and create if missing.
        // This catches legacy users and interrupted onboarding scenarios.
        when (val existence = checkProfileExists(user.uid)) {
            ProfileExistence.Exists -> {
                Timber.d("[AUTO-HEAL] Profile exists for ${user.uid}, no action needed")
                _profileReadiness.value = ProfileReadiness.Ready(user.uid)
            }

            ProfileExistence.Missing -> {
                Timber.w("[AUTO-HEAL] User profile missing for ${user.uid}, creating default profile")
                authRepository.createUserProfile(user).fold(
                    onSuccess = {
                        Timber.i("[AUTO-HEAL] Successfully created missing profile for ${user.uid}")
                        _profileReadiness.value = ProfileReadiness.Ready(user.uid)
                    },
                    onFailure = { error ->
                        Timber.e(error, "[AUTO-HEAL] Failed to create missing profile for ${user.uid}")
                        _profileReadiness.value = ProfileReadiness.Error(
                            userId = user.uid,
                            message = "We couldn't prepare your profile. Please try again."
                        )
                    }
                )
            }

            is ProfileExistence.Failure -> {
                Timber.e(existence.error, "Failed to determine profile readiness for ${user.uid}")
                _profileReadiness.value = ProfileReadiness.Error(
                    userId = user.uid,
                    message = "We couldn't verify your profile. Please try again."
                )
            }
        }
    }

    /**
     * Checks if a user profile exists in the database.
     * Used at app startup to ensure profile is ready before showing main UI.
     *
     * @param userId The user ID to check
     * @return a distinct exists, missing, or failure outcome
     */
    suspend fun checkProfileExists(userId: String): ProfileExistence {
        return try {
            profileQueryUseCase.hasProfile(userId).fold(
                onSuccess = { exists ->
                    Timber.d("Profile check for $userId: exists=$exists")
                    if (exists) ProfileExistence.Exists else ProfileExistence.Missing
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to check profile existence for $userId")
                    ProfileExistence.Failure(error)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Exception checking profile existence for $userId")
            ProfileExistence.Failure(e)
        }
    }

    fun retryProfileReadiness() {
        val user = (authState.value as? AuthenticationState.Authenticated)?.user ?: return
        viewModelScope.launch {
            ensureProfileReady(user)
        }
    }

    sealed class AuthenticationState {
        data object Loading : AuthenticationState()
        data object Unauthenticated : AuthenticationState()
        data class Authenticated(val user: User) : AuthenticationState()
    }

    sealed class ProfileReadiness {
        data object NotRequired : ProfileReadiness()
        data class Checking(val userId: String) : ProfileReadiness()
        data class Ready(val userId: String) : ProfileReadiness()
        data class Error(val userId: String, val message: String) : ProfileReadiness()
    }

    sealed class ProfileExistence {
        data object Exists : ProfileExistence()
        data object Missing : ProfileExistence()
        data class Failure(val error: Throwable) : ProfileExistence()
    }

    sealed class AiAccessEligibility {
        data object Loading : AiAccessEligibility()
        data object Ineligible : AiAccessEligibility()
        data class Eligible(val userId: String) : AiAccessEligibility()
    }
} 
