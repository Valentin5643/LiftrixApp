package com.example.liftrix.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for main navigation state management and authentication integration.
 * 
 * Manages the overall navigation state, authentication status, and handles navigation-related
 * events following the MVI pattern. Integrates with AuthRepository to provide reactive
 * authentication state updates and WorkoutRepository for workout creation flows.
 * 
 * @param authRepository Repository for authentication operations and state
 * @param workoutRepository Repository for workout operations and creation
 */
@HiltViewModel
class MainNavigationViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainNavigationState())
    val uiState: StateFlow<MainNavigationState> = _uiState.asStateFlow()

    init {
        observeAuthState()
    }

    /**
     * Handles navigation events from the UI
     */
    fun onEvent(event: MainNavigationEvent) {
        when (event) {
            is MainNavigationEvent.NavigateToTab -> {
                handleTabNavigation(event.item)
            }
            is MainNavigationEvent.ShowWorkoutCreationModal -> {
                updateState { copy(isWorkoutCreationModalVisible = true) }
            }
            is MainNavigationEvent.HideWorkoutCreationModal -> {
                updateState { copy(isWorkoutCreationModalVisible = false) }
            }
            is MainNavigationEvent.StartTemplateWorkout -> {
                handleStartTemplateWorkout()
            }
            is MainNavigationEvent.StartCustomWorkout -> {
                handleStartCustomWorkout()
            }
            is MainNavigationEvent.ClearNavigationDestination -> {
                updateState { copy(navigationDestination = null) }
            }
            is MainNavigationEvent.NavigateToAuth -> {
                // This will be handled by the UI layer to navigate to AuthActivity
                Timber.d("Navigation to auth requested")
            }
            is MainNavigationEvent.SignOut -> {
                handleSignOut()
            }
            is MainNavigationEvent.ClearError -> {
                updateState { copy(error = null) }
            }
        }
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                updateState {
                    copy(
                        authenticationState = if (user != null) {
                            AuthenticationState.Authenticated(user)
                        } else {
                            AuthenticationState.Unauthenticated
                        },
                        isLoading = false
                    )
                }
                Timber.d("Auth state updated: ${if (user != null) "Authenticated" else "Unauthenticated"}")
            }
        }
    }

    private fun handleTabNavigation(navigationItem: MainNavigationItem) {
        updateState { 
            copy(
                selectedTab = navigationItem,
                isWorkoutCreationModalVisible = false, // Hide modal when navigating
                navigationDestination = null // Clear any pending navigation
            ) 
        }
        Timber.d("Navigated to tab: ${navigationItem.route}")
    }

    private fun handleStartTemplateWorkout() {
        updateState { 
            copy(
                isWorkoutCreationModalVisible = false,
                navigationDestination = WorkoutCreationDestination.TemplateWorkout
            ) 
        }
        Timber.d("Template workout creation requested")
    }

    private fun handleStartCustomWorkout() {
        updateState { 
            copy(
                isWorkoutCreationModalVisible = false,
                navigationDestination = WorkoutCreationDestination.CustomWorkout
            ) 
        }
        Timber.d("Custom workout creation requested")
    }

    private fun handleSignOut() {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            
            authRepository.signOut()
                .onSuccess {
                    Timber.d("User signed out successfully")
                    // State will be updated automatically through auth state observer
                }
                .onFailure { exception ->
                    updateState { 
                        copy(
                            isLoading = false,
                            error = "Failed to sign out: ${exception.message}"
                        ) 
                    }
                    Timber.e(exception, "Failed to sign out")
                }
        }
    }

    private fun updateState(update: MainNavigationState.() -> MainNavigationState) {
        _uiState.value = _uiState.value.update()
    }
}

/**
 * UI state for main navigation
 * 
 * @param authenticationState Current authentication state
 * @param selectedTab Currently selected navigation tab
 * @param isWorkoutCreationModalVisible Whether the workout creation modal is visible
 * @param navigationDestination Pending workout creation navigation destination
 * @param isLoading Whether a loading operation is in progress
 * @param error Error message to display, null if no error
 */
data class MainNavigationState(
    val authenticationState: AuthenticationState = AuthenticationState.Loading,
    val selectedTab: MainNavigationItem = MainNavigationItem.HOME,
    val isWorkoutCreationModalVisible: Boolean = false,
    val navigationDestination: WorkoutCreationDestination? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * Authentication state for navigation
 */
sealed class AuthenticationState {
    data object Loading : AuthenticationState()
    data object Unauthenticated : AuthenticationState()
    data class Authenticated(val user: User) : AuthenticationState()
}

/**
 * Workout creation navigation destinations
 */
sealed class WorkoutCreationDestination {
    /**
     * Navigate to template-based workout creation
     */
    data object TemplateWorkout : WorkoutCreationDestination()
    
    /**
     * Navigate to custom workout creation
     */
    data object CustomWorkout : WorkoutCreationDestination()
}

/**
 * Events that can be triggered from the navigation UI
 */
sealed class MainNavigationEvent {
    /**
     * Navigate to a specific tab
     */
    data class NavigateToTab(val item: MainNavigationItem) : MainNavigationEvent()
    
    /**
     * Show the workout creation modal
     */
    data object ShowWorkoutCreationModal : MainNavigationEvent()
    
    /**
     * Hide the workout creation modal
     */
    data object HideWorkoutCreationModal : MainNavigationEvent()
    
    /**
     * Start template-based workout creation
     */
    data object StartTemplateWorkout : MainNavigationEvent()
    
    /**
     * Start custom workout creation
     */
    data object StartCustomWorkout : MainNavigationEvent()
    
    /**
     * Clear the navigation destination after navigation is complete
     */
    data object ClearNavigationDestination : MainNavigationEvent()
    
    /**
     * Navigate to authentication screen
     */
    data object NavigateToAuth : MainNavigationEvent()
    
    /**
     * Sign out the current user
     */
    data object SignOut : MainNavigationEvent()
    
    /**
     * Clear any error state
     */
    data object ClearError : MainNavigationEvent()
} 