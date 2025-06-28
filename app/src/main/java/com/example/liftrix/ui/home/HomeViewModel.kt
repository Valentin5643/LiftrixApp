package com.example.liftrix.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.data.repository.WorkoutRepository
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for loading and managing recent workouts data following MVI pattern
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadRecentWorkouts()
        observeAuthState()
    }

    /**
     * Observe authentication state to reload data when user changes
     */
    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                if (user != null) {
                    loadRecentWorkouts()
                } else {
                    _uiState.value = _uiState.value.copy(
                        workouts = emptyList(),
                        isLoading = false,
                        error = null
                    )
                }
            }
        }
    }

    /**
     * Load recent workouts for the current user
     */
    fun loadRecentWorkouts() {
        viewModelScope.launch {
            try {
                val userId = authRepository.getCurrentUserId()
                if (userId == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "User not authenticated"
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                workoutRepository.getAllWorkoutsForUser(userId)
                    .catch { exception ->
                        Timber.e(exception, "Failed to load workouts for user: $userId")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Failed to load workouts: ${exception.message}"
                        )
                    }
                    .collect { workouts ->
                        // Sort workouts by date (most recent first) and limit to recent ones
                        val recentWorkouts = workouts
                            .sortedByDescending { it.date }
                            .take(RECENT_WORKOUTS_LIMIT)

                        _uiState.value = _uiState.value.copy(
                            workouts = recentWorkouts,
                            isLoading = false,
                            error = null
                        )
                    }
            } catch (exception: Exception) {
                Timber.e(exception, "Unexpected error loading workouts")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Unexpected error: ${exception.message}"
                )
            }
        }
    }

    /**
     * Refresh workouts data (pull-to-refresh)
     */
    fun refreshWorkouts() {
        viewModelScope.launch {
            try {
                val userId = authRepository.getCurrentUserId()
                if (userId == null) {
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        error = "User not authenticated"
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)

                // Trigger sync to get latest data from server
                workoutRepository.syncNowForUser(userId)
                    .onSuccess {
                        Timber.d("Workout sync completed successfully for user: $userId")
                    }
                    .onFailure { exception ->
                        Timber.w(exception, "Workout sync failed for user: $userId, continuing with local data")
                        // Don't show error for sync failures, just continue with local data
                    }

                // Load fresh data regardless of sync result
                loadRecentWorkouts()

            } catch (exception: Exception) {
                Timber.e(exception, "Unexpected error during refresh")
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = "Refresh failed: ${exception.message}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }

    /**
     * Handle workout selection
     */
    fun onWorkoutSelected(workoutId: WorkoutId) {
        Timber.d("Workout selected: ${workoutId.value}")
        // This will be handled by navigation in the UI layer
        // ViewModel just logs the selection for debugging
    }

    /**
     * Clear any error state
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    companion object {
        private const val RECENT_WORKOUTS_LIMIT = 20
    }
}

/**
 * UI state data class for Home screen
 */
data class HomeUiState(
    val workouts: List<Workout> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
) {
    /**
     * Convenience property to check if we're in an empty state
     */
    val isEmpty: Boolean
        get() = workouts.isEmpty() && !isLoading

    /**
     * Convenience property to check if we have data
     */
    val hasData: Boolean
        get() = workouts.isNotEmpty()

    /**
     * Convenience property to check if we're in a loading state
     */
    val isInitialLoading: Boolean
        get() = isLoading && workouts.isEmpty()
} 