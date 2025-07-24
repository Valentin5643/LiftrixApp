package com.example.liftrix.ui.workout

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutTemplatePreview
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import com.example.liftrix.domain.usecase.auth.GetAuthenticatedUserIdUseCase
import com.example.liftrix.domain.repository.FolderRepository
import com.example.liftrix.domain.usecase.SaveWorkoutUseCase
import com.example.liftrix.domain.usecase.analytics.LogWorkoutEventUseCase
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.analytics.UxMetricsTracker
import com.example.liftrix.analytics.TaskCompletionTracker
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.state.WorkoutScreenData
import com.example.liftrix.ui.common.state.WorkoutUiState
import com.example.liftrix.ui.common.state.dataOrNull
import com.example.liftrix.domain.usecase.common.ErrorHandler
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
    private val getAuthenticatedUserIdUseCase: GetAuthenticatedUserIdUseCase,
    private val saveWorkoutUseCase: SaveWorkoutUseCase,
    private val syncManager: SyncManager,
    private val logWorkoutEventUseCase: LogWorkoutEventUseCase,
    private val analyticsService: AnalyticsService,
    private val uxMetricsTracker: UxMetricsTracker,
    private val taskCompletionTracker: TaskCompletionTracker,
    errorHandler: ErrorHandler
) : BaseViewModel<WorkoutUiState, WorkoutEvent>(errorHandler) {

    override val _uiState = MutableStateFlow<WorkoutUiState>(WorkoutUiState.Loading)

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        observeAuthState()
        observeWorkouts()
        observeTemplates()
        observeSyncStatus()
    }

    /**
     * Handles events from the UI following BaseViewModel MVI pattern
     */
    override fun handleEvent(event: WorkoutEvent) {
        when (event) {
            is WorkoutEvent.StartWorkout -> startWorkout(event.workout)
            is WorkoutEvent.CompleteWorkout -> completeWorkout(event.workout)
            is WorkoutEvent.SaveWorkout -> saveWorkout(event.workout)
            is WorkoutEvent.NavigateToEdit -> {
                // Navigation will be handled by the screen
            }
            WorkoutEvent.ClearError -> clearError()
            WorkoutEvent.RefreshData -> refreshData()
        }
    }

    /**
     * Override to handle loading state updates
     */
    override fun setLoadingState() {
        setState(WorkoutUiState.Loading)
    }

    /**
     * Override to handle error state updates
     */
    override fun updateErrorState(error: com.example.liftrix.domain.model.error.LiftrixError) {
        val currentData = _uiState.value.dataOrNull() ?: WorkoutScreenData()
        setState(WorkoutUiState.Error(error, currentData))
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
                    setState(WorkoutUiState.Success(data = WorkoutScreenData()))
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
                        val currentData = _uiState.value.dataOrNull() ?: WorkoutScreenData()
                        setState(WorkoutUiState.Success(
                            currentData.copy(
                                workouts = workouts,
                                syncStatus = syncStatus
                            )
                        ))
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
                                    val currentData = _uiState.value.dataOrNull() ?: WorkoutScreenData()
                                    setState(WorkoutUiState.Success(
                                        currentData.copy(templates = templates)
                                    ))
                                    Timber.d("Loaded ${templates.size} templates for user ${user.uid}")
                                },
                                onFailure = { exception ->
                                    Timber.e(exception, "Failed to load templates for user ${user.uid}")
                                    val currentData = _uiState.value.dataOrNull() ?: WorkoutScreenData()
                                    setState(WorkoutUiState.Success(
                                        currentData.copy(templates = emptyList())
                                    ))
                                }
                            )
                        }
                }
        }
    }

    private fun observeSyncStatus() {
        viewModelScope.launch {
            syncManager.getSyncStatus().collect { status ->
                val currentData = _uiState.value.dataOrNull() ?: WorkoutScreenData()
                setState(WorkoutUiState.Success(
                    currentData.copy(syncStatus = status)
                ))
                
                when (status) {
                    is SyncStatus.Success -> {
                        Timber.d("Sync completed successfully: ${status.syncedCount} workouts synced")
                    }
                    is SyncStatus.Error -> {
                        Timber.e("Sync failed: ${status.message}")
                        val error = com.example.liftrix.domain.model.error.LiftrixError.NetworkError(
                            errorMessage = "Sync failed: ${status.message}"
                        )
                        setState(WorkoutUiState.Error(error, currentData))
                    }
                    else -> { /* Handle other states if needed */ }
                }
            }
        }
    }

    fun saveWorkout(workout: Workout) {
        executeUseCase(
            useCase = {
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
                result
            },
            onSuccess = { 
                Timber.d("Workout saved successfully: ${workout.name}")
            },
            onError = { error ->
                Timber.e("Failed to save workout: ${error.message}")
            }
        )
    }

    fun syncNow() {
        executeUseCase(
            useCase = {
                val userId = getAuthenticatedUserIdUseCase()
                workoutRepository.syncNowForUser(userId)
            },
            onSuccess = {
                Timber.d("Sync started successfully")
            },
            onError = { error ->
                Timber.e("Failed to start sync: ${error.message}")
            }
        )
    }

    fun getUnsyncedCount() {
        executeUseCase(
            useCase = {
                val userId = getAuthenticatedUserIdUseCase()
                workoutRepository.getUnsyncedCountForUser(userId)
            },
            onSuccess = { count ->
                val currentData = _uiState.value.dataOrNull() ?: WorkoutScreenData()
                setState(WorkoutUiState.Success(
                    currentData.copy(unsyncedCount = count)
                ))
            },
            onError = { error ->
                Timber.e("Failed to get unsynced count: ${error.message}")
            },
            showLoading = false
        )
    }

    fun startWorkout(workout: Workout) {
        // Track workflow start for PRD metrics
        val workflowId = "workout_start_${workout.id.value}_${System.currentTimeMillis()}"
        uxMetricsTracker.startWorkflowTracking(workflowId)
        uxMetricsTracker.trackInteraction(workflowId, "workout_start_button")
        
        // Track task completion for PRD metrics  
        val taskId = "start_task_${workout.id.value}_${System.currentTimeMillis()}"
        taskCompletionTracker.trackTaskStart(taskId, TaskCompletionTracker.TASK_WORKOUT_START)
        
        val startedWorkout = workout.start()
        saveWorkout(startedWorkout)
        
        // Track successful completion
        uxMetricsTracker.completeWorkflowTracking(workflowId, successful = true)
        taskCompletionTracker.trackTaskCompletion(
            taskId, 
            TaskCompletionTracker.TASK_WORKOUT_START,
            com.example.liftrix.analytics.TaskCompletionResult(
                status = com.example.liftrix.analytics.CompletionStatus.SUCCESS,
                completionTime = 1000L, // Quick action
                errorCount = 0,
                retryCount = 0
            )
        )
    }
    
    fun completeWorkout(workout: Workout) {
        val completedWorkout = workout.complete()
        saveWorkout(completedWorkout)
    }
    
    private fun clearError() {
        updateState { currentState ->
            when (currentState) {
                is WorkoutUiState.Error -> WorkoutUiState.Success(currentState.previousData ?: WorkoutScreenData())
                else -> currentState
            }
        }
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
        when (state) {
            is WorkoutUiState.Success -> {
                state.data.workouts.firstOrNull()?.let { workout ->
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
            }
            else -> null
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
}

// WorkoutUiState is now defined in ViewModelState.kt as a proper sealed class hierarchy

/**
 * Events that can be triggered from the workout screen UI
 * 
 * Follows the MVI pattern for reactive state management.
 */
sealed class WorkoutEvent : ViewModelEvent {
    data class StartWorkout(val workout: Workout) : WorkoutEvent()
    data class CompleteWorkout(val workout: Workout) : WorkoutEvent()
    data class SaveWorkout(val workout: Workout) : WorkoutEvent()
    data class NavigateToEdit(val workoutId: WorkoutId) : WorkoutEvent()
    object ClearError : WorkoutEvent()
    object RefreshData : WorkoutEvent()
}