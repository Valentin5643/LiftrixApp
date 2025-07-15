package com.example.liftrix.ui.workout

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutTemplatePreview
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import com.example.liftrix.domain.repository.FolderRepository
import com.example.liftrix.domain.usecase.SaveWorkoutUseCase
import com.example.liftrix.domain.usecase.analytics.LogWorkoutEventUseCase
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.domain.model.common.LiftrixResult
import androidx.lifecycle.ViewModel
import com.example.liftrix.sync.SyncManager
import com.example.liftrix.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val workoutTemplateRepository: WorkoutTemplateRepository,
    private val folderRepository: FolderRepository,
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
        observeTemplates()
        observeSyncStatus()
    }

    /**
     * Handles events from the UI following MVI pattern
     */
    fun onEvent(event: WorkoutEvent) {
        when (event) {
            is WorkoutEvent.StartWorkout -> startWorkout(event.workout)
            is WorkoutEvent.CompleteWorkout -> completeWorkout(event.workout)
            is WorkoutEvent.SaveWorkout -> saveWorkout(event.workout)
            is WorkoutEvent.ClearError -> clearError()
            is WorkoutEvent.RefreshData -> refreshData()
        }
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
                        templates = emptyList(),
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

    private fun observeTemplates() {
        viewModelScope.launch {
            // Only observe templates when user is authenticated
            authRepository.currentUser
                .filterNotNull()
                .collect { user ->
                    // Ensure default folder exists for the user
                    folderRepository.getOrCreateDefaultFolder(user.uid)
                        .onFailure { exception ->
                            Timber.e(exception, "Failed to create default folder for user ${user.uid}")
                        }
                    
                    workoutTemplateRepository.getAllTemplatesForUser(user.uid)
                        .collect { templatesResult ->
                            templatesResult.fold(
                                onSuccess = { templates ->
                                    _uiState.value = _uiState.value.copy(
                                        templates = templates
                                    )
                                    Timber.d("Loaded ${templates.size} templates for user ${user.uid}")
                                },
                                onFailure = { exception ->
                                    Timber.e(exception, "Failed to load templates for user ${user.uid}")
                                    _uiState.value = _uiState.value.copy(
                                        templates = emptyList()
                                    )
                                }
                            )
                        }
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
                val countResult = workoutRepository.getUnsyncedCountForUser(user.uid)
                val count = countResult.getOrElse { 0 }
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
    
    private fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun refreshData() {
        // Refresh all data by re-observing
        observeWorkouts()
        observeTemplates()
        observeSyncStatus()
    }

    
    /**
     * Enhanced UI template preview for workout creation flow
     * Transforms workout data into structured preview format
     */
    val templatePreview: StateFlow<WorkoutTemplatePreview?> = uiState.map { state ->
        state.workouts.firstOrNull()?.let { workout ->
            WorkoutTemplatePreview(
                name = workout.name,
                description = workout.notes,
                exerciseCount = workout.exercises.size,
                estimatedDuration = workout.getDuration()?.toMinutes()?.toString() + "m" ?: "Unknown",
                targetMuscleGroups = workout.exercises.map { it.libraryExercise.primaryMuscleGroup.displayName }.distinct(),
                difficulty = when {
                    workout.exercises.size <= 3 -> "Beginner"
                    workout.exercises.size <= 6 -> "Intermediate" 
                    else -> "Advanced"
                },
                lastUsed = null,
                isPopular = false
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
}

data class WorkoutUiState(
    val workouts: List<Workout> = emptyList(),
    val templates: List<com.example.liftrix.domain.model.WorkoutTemplate> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val unsyncedCount: Int = 0,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Events that can be triggered from the workout screen UI
 * 
 * Follows the MVI pattern for reactive state management.
 */
sealed class WorkoutEvent : ViewModelEvent {
    data class StartWorkout(val workout: Workout) : WorkoutEvent()
    data class CompleteWorkout(val workout: Workout) : WorkoutEvent()
    data class SaveWorkout(val workout: Workout) : WorkoutEvent()
    object ClearError : WorkoutEvent()
    object RefreshData : WorkoutEvent()
}