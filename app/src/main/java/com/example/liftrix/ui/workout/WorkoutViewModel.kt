package com.example.liftrix.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.data.repository.WorkoutRepository
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.usecase.SaveWorkoutUseCase
import com.example.liftrix.domain.usecase.analytics.LogWorkoutEventUseCase
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.sync.SyncManager
import com.example.liftrix.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val authRepository: AuthRepository,
    private val saveWorkoutUseCase: SaveWorkoutUseCase,
    private val syncManager: SyncManager,
    private val logWorkoutEventUseCase: LogWorkoutEventUseCase,
    private val analyticsService: AnalyticsService
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkoutUiState())
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        observeAuthState()
        observeWorkouts()
        observeSyncStatus()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _currentUser.value = user
                if (user != null) {
                    Timber.d("User authenticated in WorkoutViewModel: ${user.uid}")
                    // Set user properties for analytics and crashlytics
                    analyticsService.setUserProperties(user)
                        .onFailure { exception ->
                            Timber.e(exception, "Failed to set user properties for analytics")
                        }
                } else {
                    Timber.d("User not authenticated in WorkoutViewModel")
                    // Clear workout data when user logs out
                    _uiState.value = _uiState.value.copy(
                        workouts = emptyList(),
                        isLoading = false
                    )
                    // Clear analytics user properties
                    analyticsService.clearUserProperties()
                        .onFailure { exception ->
                            Timber.e(exception, "Failed to clear user properties for analytics")
                        }
                }
            }
        }
    }

    private fun observeWorkouts() {
        viewModelScope.launch {
            // Only observe workouts when user is authenticated
            authRepository.currentUser
                .filterNotNull()
                .collect { user ->
                    combine(
                        workoutRepository.getAllWorkoutsForUser(user.uid),
                        syncManager.getSyncStatus()
                    ) { workouts, syncStatus ->
                        _uiState.value = _uiState.value.copy(
                            workouts = workouts,
                            syncStatus = syncStatus,
                            isLoading = false
                        )
                    }.collect { /* Updates handled in combine block */ }
                }
        }
    }

    private fun observeSyncStatus() {
        viewModelScope.launch {
            syncManager.getSyncStatus().collect { status ->
                _uiState.value = _uiState.value.copy(syncStatus = status)
                
                when (status) {
                    is SyncStatus.Success -> {
                        Timber.d("Sync completed successfully: ${status.syncedCount} workouts synced")
                    }
                    is SyncStatus.Error -> {
                        Timber.e("Sync failed: ${status.message}")
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "Sync failed: ${status.message}"
                        )
                    }
                    else -> { /* Handle other states if needed */ }
                }
            }
        }
    }

    fun saveWorkout(workout: Workout) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            
            // Track previous status for analytics
            val previousStatus = workout.status
            
            val result = saveWorkoutUseCase(workout)
            
            // Log analytics events based on workout status changes
            if (result.isSuccess) {
                logWorkoutEventUseCase.logWorkoutStatusChange(workout, previousStatus)
                    .onFailure { exception ->
                        Timber.e(exception, "Failed to log workout analytics event")
                        // Don't fail the save operation if analytics fails
                    }
            }
            
            _uiState.value = _uiState.value.copy(
                isSaving = false,
                errorMessage = if (result.isFailure) {
                    "Failed to save workout: ${result.exceptionOrNull()?.message}"
                } else null
            )
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            val user = _currentUser.value
            if (user != null) {
                val result = workoutRepository.syncNowForUser(user.uid)
                
                if (result.isFailure) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Failed to start sync: ${result.exceptionOrNull()?.message}"
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Cannot sync: user not authenticated"
                )
            }
        }
    }

    fun getUnsyncedCount() {
        viewModelScope.launch {
            val user = _currentUser.value
            if (user != null) {
                val count = workoutRepository.getUnsyncedCountForUser(user.uid)
                _uiState.value = _uiState.value.copy(unsyncedCount = count)
            }
        }
    }

    fun startWorkout(workout: Workout) {
        viewModelScope.launch {
            val startedWorkout = workout.start()
            saveWorkout(startedWorkout)
        }
    }
    
    fun completeWorkout(workout: Workout) {
        viewModelScope.launch {
            val completedWorkout = workout.complete()
            saveWorkout(completedWorkout)
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

data class WorkoutUiState(
    val workouts: List<Workout> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val unsyncedCount: Int = 0,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
) 