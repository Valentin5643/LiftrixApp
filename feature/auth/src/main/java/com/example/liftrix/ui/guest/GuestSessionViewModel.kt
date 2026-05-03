package com.example.liftrix.ui.guest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.GuestSession
import com.example.liftrix.domain.model.SignificantInteraction
import com.example.liftrix.domain.usecase.guest.ManageGuestSessionUseCase
import com.example.liftrix.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for managing guest session state and nudging logic
 */
@HiltViewModel
class GuestSessionViewModel @Inject constructor(
    private val manageGuestSessionUseCase: ManageGuestSessionUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _guestSessionState = MutableStateFlow<GuestSessionState>(GuestSessionState.Loading)
    val guestSessionState: StateFlow<GuestSessionState> = _guestSessionState.asStateFlow()

    private val _shouldShowNudge = MutableStateFlow(false)
    val shouldShowNudge: StateFlow<Boolean> = _shouldShowNudge.asStateFlow()

    private val _shouldShowLimitWarning = MutableStateFlow(false)
    val shouldShowLimitWarning: StateFlow<Boolean> = _shouldShowLimitWarning.asStateFlow()

    private val _showLimitReachedDialog = MutableStateFlow(false)
    val showLimitReachedDialog: StateFlow<Boolean> = _showLimitReachedDialog.asStateFlow()

    init {
        observeUserAuth()
    }

    private fun observeUserAuth() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                if (user?.isAnonymous == true) {
                    loadGuestSession(user.uid)
                } else {
                    _guestSessionState.value = GuestSessionState.NotGuest
                    _shouldShowNudge.value = false
                    _shouldShowLimitWarning.value = false
                    _showLimitReachedDialog.value = false
                }
            }
        }
    }

    private suspend fun loadGuestSession(userId: String) {
        manageGuestSessionUseCase.getOrCreateGuestSession(userId)
            .onSuccess { guestSession ->
                _guestSessionState.value = GuestSessionState.Active(guestSession)
                checkForNudges(userId)
                checkForLimitWarning(userId)
            }
            .onFailure { error ->
                _guestSessionState.value = GuestSessionState.Error(error.message ?: "Failed to load guest session")
                Timber.e("Failed to load guest session: ${error.message}")
            }
    }

    /**
     * Records a significant interaction from the user
     */
    fun recordSignificantInteraction(interaction: SignificantInteraction) {
        viewModelScope.launch {
            val currentUser = authRepository.getCurrentUser()
            if (currentUser?.isAnonymous == true) {
                manageGuestSessionUseCase.recordSignificantInteraction(currentUser.uid, interaction)
                    .onSuccess { updatedSession ->
                        _guestSessionState.value = GuestSessionState.Active(updatedSession)
                        checkForNudges(currentUser.uid)
                    }
                    .onFailure { error ->
                        Timber.w("Failed to record interaction: ${error.message}")
                    }
            }
        }
    }

    /**
     * Records a completed workout for guest users
     */
    fun recordWorkoutCompleted() {
        viewModelScope.launch {
            val currentUser = authRepository.getCurrentUser()
            if (currentUser?.isAnonymous == true) {
                manageGuestSessionUseCase.recordWorkoutCompleted(currentUser.uid)
                    .onSuccess { updatedSession ->
                        _guestSessionState.value = GuestSessionState.Active(updatedSession)
                        
                        // Check if limit is reached and show dialog
                        if (updatedSession.isLimitReached) {
                            _showLimitReachedDialog.value = true
                        } else {
                            checkForLimitWarning(currentUser.uid)
                        }
                    }
                    .onFailure { error ->
                        Timber.e("Failed to record workout completion: ${error.message}")
                    }
            }
        }
    }

    /**
     * Dismisses the current nudge
     */
    fun dismissNudge() {
        viewModelScope.launch {
            val currentUser = authRepository.getCurrentUser()
            if (currentUser?.isAnonymous == true) {
                manageGuestSessionUseCase.recordNudgeShown(currentUser.uid)
                    .onSuccess {
                        _shouldShowNudge.value = false
                    }
                    .onFailure { error ->
                        Timber.w("Failed to record nudge dismissal: ${error.message}")
                        _shouldShowNudge.value = false // Still dismiss on error
                    }
            }
        }
    }

    /**
     * Dismisses the limit warning
     */
    fun dismissLimitWarning() {
        viewModelScope.launch {
            val currentUser = authRepository.getCurrentUser()
            if (currentUser?.isAnonymous == true) {
                manageGuestSessionUseCase.markLimitWarningSeen(currentUser.uid)
                    .onSuccess {
                        _shouldShowLimitWarning.value = false
                    }
                    .onFailure { error ->
                        Timber.w("Failed to record limit warning dismissal: ${error.message}")
                        _shouldShowLimitWarning.value = false // Still dismiss on error
                    }
            }
        }
    }

    /**
     * Dismisses the limit reached dialog
     */
    fun dismissLimitReachedDialog() {
        _showLimitReachedDialog.value = false
    }

    /**
     * Clears the guest session (when user signs up)
     */
    fun clearGuestSession() {
        viewModelScope.launch {
            val currentUser = authRepository.getCurrentUser()
            if (currentUser != null) {
                manageGuestSessionUseCase.clearGuestSession(currentUser.uid)
                    .onSuccess {
                        _guestSessionState.value = GuestSessionState.NotGuest
                        _shouldShowNudge.value = false
                        _shouldShowLimitWarning.value = false
                        _showLimitReachedDialog.value = false
                    }
                    .onFailure { error ->
                        Timber.w("Failed to clear guest session: ${error.message}")
                    }
            }
        }
    }

    private suspend fun checkForNudges(userId: String) {
        manageGuestSessionUseCase.shouldShowNudge(userId)
            .onSuccess { shouldShow ->
                _shouldShowNudge.value = shouldShow
            }
            .onFailure { error ->
                Timber.w("Failed to check nudge state: ${error.message}")
            }
    }

    private suspend fun checkForLimitWarning(userId: String) {
        manageGuestSessionUseCase.shouldShowLimitWarning(userId)
            .onSuccess { shouldShow ->
                _shouldShowLimitWarning.value = shouldShow
            }
            .onFailure { error ->
                Timber.w("Failed to check limit warning state: ${error.message}")
            }
    }

    /**
     * Gets the current nudge message
     */
    fun getNudgeMessage(): String {
        return when (val state = _guestSessionState.value) {
            is GuestSessionState.Active -> state.guestSession.getNudgeMessage()
            else -> "Create a free account to save your progress."
        }
    }

    /**
     * Gets the number of workouts remaining
     */
    fun getWorkoutsRemaining(): Int {
        return when (val state = _guestSessionState.value) {
            is GuestSessionState.Active -> state.guestSession.getWorkoutsRemaining()
            else -> 0
        }
    }

    /**
     * Checks if the guest session is still active (within limits)
     */
    fun isGuestSessionActive(): Boolean {
        return when (val state = _guestSessionState.value) {
            is GuestSessionState.Active -> state.guestSession.isActive()
            else -> false
        }
    }

    /**
     * Refreshes the guest session state (for retry after errors)
     */
    fun refreshGuestSession() {
        viewModelScope.launch {
            val currentUser = authRepository.getCurrentUser()
            if (currentUser != null) {
                _guestSessionState.value = GuestSessionState.Loading
                loadGuestSession(currentUser.uid)
            }
        }
    }
}

/**
 * State for guest session management
 */
sealed class GuestSessionState {
    data object Loading : GuestSessionState()
    data object NotGuest : GuestSessionState()
    data class Active(val guestSession: GuestSession) : GuestSessionState()
    data class Error(val message: String) : GuestSessionState()
}