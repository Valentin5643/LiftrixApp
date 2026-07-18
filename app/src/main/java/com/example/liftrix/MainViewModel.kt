package com.example.liftrix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.usecase.admin.CheckAdminPermissionsUseCase
import com.example.liftrix.domain.usecase.profile.ProfileQueryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileQueryUseCase: ProfileQueryUseCase,
    private val checkAdminPermissionsUseCase: CheckAdminPermissionsUseCase
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthenticationState>(AuthenticationState.Loading)
    val authState: StateFlow<AuthenticationState> = _authState.asStateFlow()

    private val _profileReadiness = MutableStateFlow<ProfileReadiness>(ProfileReadiness.NotRequired)
    val profileReadiness: StateFlow<ProfileReadiness> = _profileReadiness.asStateFlow()

    private val _aiAccessEligibility = MutableStateFlow<AiAccessEligibility>(AiAccessEligibility.Loading)
    val aiAccessEligibility: StateFlow<AiAccessEligibility> = _aiAccessEligibility.asStateFlow()
    
    // Track if we're in an explicit authentication flow (from AuthActivity)
    private var isExplicitAuthFlow = false
    private var activeUserId: String? = null
    private var profileReadinessJob: Job? = null
    private var aiAccessEligibilityJob: Job? = null
    private var profileJobToken: Any? = null
    private var aiJobToken: Any? = null

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
                if (user == null) {
                    clearUserJobs()
                    _authState.value = AuthenticationState.Unauthenticated
                    _profileReadiness.value = ProfileReadiness.NotRequired
                    _aiAccessEligibility.value = AiAccessEligibility.Ineligible
                    Timber.d("User not authenticated")
                } else if (isExplicitAuthFlow) {
                    clearUserJobs()
                    _profileReadiness.value = ProfileReadiness.NotRequired
                    _aiAccessEligibility.value = AiAccessEligibility.Loading
                    Timber.d("Skipping auth state update during explicit auth flow. User: ${user?.uid ?: "null"}")
                } else {
                    beginAuthenticatedUser(user)
                }
            }
        }
    }

    private fun beginAuthenticatedUser(user: User) {
        clearUserJobs()
        activeUserId = user.uid

        _authState.value = AuthenticationState.Authenticated(user)
        _profileReadiness.value = ProfileReadiness.Checking(user.uid)
        _aiAccessEligibility.value = AiAccessEligibility.Loading
        Timber.d("Authenticated user shell is ready")

        val profileToken = Any()
        profileJobToken = profileToken
        profileReadinessJob = viewModelScope.launch {
            ensureProfileReady(user, profileToken)
        }

        val aiToken = Any()
        aiJobToken = aiToken
        aiAccessEligibilityJob = viewModelScope.launch {
            refreshAiAccessEligibility(user.uid, aiToken)
        }
    }

    private fun clearUserJobs() {
        activeUserId = null
        profileJobToken = null
        aiJobToken = null
        profileReadinessJob?.cancel()
        aiAccessEligibilityJob?.cancel()
        profileReadinessJob = null
        aiAccessEligibilityJob = null
    }

    private suspend fun refreshAiAccessEligibility(userId: String, jobToken: Any) {
        val isAdmin = checkAdminPermissionsUseCase(userId).getOrElse {
            false
        }
        val isStillAuthenticatedUser = try {
            authRepository.getCurrentUser()?.uid == userId
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }

        if (ownsAiJob(userId, jobToken)) {
            _aiAccessEligibility.value = if (isAdmin && isStillAuthenticatedUser) {
                AiAccessEligibility.Eligible(userId)
            } else {
                AiAccessEligibility.Ineligible
            }
        }
    }

    private suspend fun ensureProfileReady(user: User, jobToken: Any) {
        // AUTO-HEAL: Check if profile exists and create if missing.
        // This catches legacy users and interrupted onboarding scenarios.
        when (val existence = checkProfileExists(user.uid)) {
            ProfileExistence.Exists -> {
                Timber.d("[AUTO-HEAL] Profile exists, no action needed")
                publishProfileReadiness(user.uid, jobToken, ProfileReadiness.Ready(user.uid))
            }

            ProfileExistence.Missing -> {
                Timber.w("[AUTO-HEAL] User profile missing, creating default profile")
                authRepository.createUserProfile(user).fold(
                    onSuccess = {
                        Timber.i("[AUTO-HEAL] Successfully created missing profile")
                        publishProfileReadiness(
                            user.uid,
                            jobToken,
                            ProfileReadiness.Ready(user.uid)
                        )
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        Timber.e(error, "[AUTO-HEAL] Failed to create missing profile")
                        publishProfileReadiness(
                            user.uid,
                            jobToken,
                            ProfileReadiness.Error(
                                userId = user.uid,
                                message = "We couldn't prepare your profile. Please try again."
                            )
                        )
                    }
                )
            }

            is ProfileExistence.Failure -> {
                if (existence.error is CancellationException) throw existence.error
                Timber.e(existence.error, "Failed to determine profile readiness")
                publishProfileReadiness(
                    user.uid,
                    jobToken,
                    ProfileReadiness.Error(
                        userId = user.uid,
                        message = "We couldn't verify your profile. Please try again."
                    )
                )
            }
        }
    }

    private fun publishProfileReadiness(
        userId: String,
        jobToken: Any,
        readiness: ProfileReadiness
    ) {
        if (ownsProfileJob(userId, jobToken)) {
            _profileReadiness.value = readiness
        }
    }

    private fun ownsProfileJob(userId: String, jobToken: Any): Boolean =
        activeUserId == userId && profileJobToken === jobToken

    private fun ownsAiJob(userId: String, jobToken: Any): Boolean =
        activeUserId == userId && aiJobToken === jobToken

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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Exception checking profile existence for $userId")
            ProfileExistence.Failure(e)
        }
    }

    fun retryProfileReadiness() {
        val user = (authState.value as? AuthenticationState.Authenticated)?.user ?: return
        if (activeUserId != user.uid) return

        profileJobToken = null
        profileReadinessJob?.cancel()
        _profileReadiness.value = ProfileReadiness.Checking(user.uid)
        val profileToken = Any()
        profileJobToken = profileToken
        profileReadinessJob = viewModelScope.launch {
            ensureProfileReady(user, profileToken)
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
