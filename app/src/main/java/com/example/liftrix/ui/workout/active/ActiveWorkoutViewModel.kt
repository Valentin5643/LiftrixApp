package com.example.liftrix.ui.workout.active

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.data.repository.WorkoutRepository
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.domain.model.ExerciseSetId
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.RestTimer
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutExercise
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.service.FirebasePresenceService
import com.example.liftrix.service.TimerServiceManager
import com.example.liftrix.service.WorkoutTimerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for active workout session management with timer service integration.
 * 
 * Manages workout state, timer integration, exercise tracking, and session persistence
 * following the MVI pattern with reactive state management.
 */
@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    private val timerServiceManager: TimerServiceManager,
    private val workoutRepository: WorkoutRepository,
    private val authRepository: AuthRepository,
    private val presenceService: FirebasePresenceService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActiveWorkoutState())
    val uiState: StateFlow<ActiveWorkoutState> = _uiState.asStateFlow()

    init {
        bindTimerService()
        observeTimerState()
        loadActiveWorkout()
    }

    /**
     * Handles all user events following MVI pattern
     */
    fun onEvent(event: ActiveWorkoutEvent) {
        when (event) {
            is ActiveWorkoutEvent.StartSession -> startWorkoutSession()
            is ActiveWorkoutEvent.PauseSession -> pauseWorkoutSession()
            is ActiveWorkoutEvent.ResumeSession -> resumeWorkoutSession()
            is ActiveWorkoutEvent.StopSession -> stopWorkoutSession()
            is ActiveWorkoutEvent.StartRest -> startRestTimer(event.restTimer)
            is ActiveWorkoutEvent.SkipRest -> skipRestTimer()
            is ActiveWorkoutEvent.AddExercise -> addExercise(event.exercise)
            is ActiveWorkoutEvent.RemoveExercise -> removeExercise(event.exerciseIndex)
            is ActiveWorkoutEvent.AddSet -> addSetToExercise(event.exerciseIndex)
            is ActiveWorkoutEvent.UpdateSet -> updateExerciseSet(event.exerciseIndex, event.setIndex, event.set)
            is ActiveWorkoutEvent.RemoveSet -> removeSetFromExercise(event.exerciseIndex, event.setIndex)
            is ActiveWorkoutEvent.SaveWorkout -> saveCurrentWorkout()
            is ActiveWorkoutEvent.LoadWorkout -> loadWorkout(event.workoutId)
            is ActiveWorkoutEvent.ClearError -> clearError()
            is ActiveWorkoutEvent.DismissMessage -> dismissMessage()
        }
    }

    /**
     * Binds to the timer service for reactive state updates
     */
    private fun bindTimerService() {
        viewModelScope.launch {
            val result = timerServiceManager.bindService()
            if (result.isFailure) {
                updateState { 
                    copy(
                        error = "Failed to connect to timer service: ${result.exceptionOrNull()?.message}",
                        isTimerServiceConnected = false
                    )
                }
            }
        }
    }

    /**
     * Observes timer service state for reactive UI updates
     */
    private fun observeTimerState() {
        viewModelScope.launch {
            combine(
                timerServiceManager.connectionState,
                timerServiceManager.timerState
            ) { connectionState, timerState ->
                val isConnected = connectionState is TimerServiceManager.ConnectionState.Connected
                updateState {
                    copy(
                        isTimerServiceConnected = isConnected,
                        timerState = timerState,
                        formattedSessionTime = formatSessionTime(timerState),
                        formattedRestTime = formatRestTime(timerState),
                        isSessionActive = timerState.isRunning,
                        connectionError = if (connectionState is TimerServiceManager.ConnectionState.Error) {
                            connectionState.exception.message
                        } else null
                    )
                }
            }.collect { }
        }
    }

    /**
     * Loads the active workout for the current user
     */
    private fun loadActiveWorkout() {
        viewModelScope.launch {
            try {
                updateState { copy(isLoading = true) }
                
                val user = authRepository.currentUser.first()
                if (user == null) {
                    updateState { 
                        copy(
                            isLoading = false,
                            error = "User not authenticated"
                        )
                    }
                    return@launch
                }

                val activeWorkout = workoutRepository.getActiveWorkoutForUser(user.uid)
                updateState {
                    copy(
                        isLoading = false,
                        currentWorkout = activeWorkout,
                        hasActiveWorkout = activeWorkout != null
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load active workout")
                updateState { 
                    copy(
                        isLoading = false,
                        error = "Failed to load active workout: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Starts a new workout session with timer
     */
    private fun startWorkoutSession() {
        viewModelScope.launch {
            val result = timerServiceManager.startSession()
            if (result.isFailure) {
                updateState { 
                    copy(error = "Failed to start session: ${result.exceptionOrNull()?.message}")
                }
            } else {
                // Create new workout if none exists
                if (_uiState.value.currentWorkout == null) {
                    createNewWorkout()
                }
                
                // Update presence status to WORKING_OUT
                _uiState.value.currentWorkout?.let { workout ->
                    val presenceResult = presenceService.updateWorkoutStatus(workout.id.value)
                    if (presenceResult.isFailure) {
                        Timber.w(presenceResult.exceptionOrNull(), "Failed to update presence status - continuing workout")
                        // Don't block workout functionality if presence update fails
                    }
                }
            }
        }
    }

    /**
     * Pauses the current workout session
     */
    private fun pauseWorkoutSession() {
        viewModelScope.launch {
            val result = timerServiceManager.pauseTimer()
            if (result.isFailure) {
                updateState { 
                    copy(error = "Failed to pause session: ${result.exceptionOrNull()?.message}")
                }
            } else {
                // Update presence to ONLINE when paused (user not actively working out)
                val presenceResult = presenceService.updateWorkoutStatus(null)
                if (presenceResult.isFailure) {
                    Timber.w(presenceResult.exceptionOrNull(), "Failed to update presence on pause - continuing")
                }
            }
        }
    }

    /**
     * Resumes the paused workout session
     */
    private fun resumeWorkoutSession() {
        viewModelScope.launch {
            val result = timerServiceManager.resumeTimer()
            if (result.isFailure) {
                updateState { 
                    copy(error = "Failed to resume session: ${result.exceptionOrNull()?.message}")
                }
            } else {
                // Update presence back to WORKING_OUT when resumed
                _uiState.value.currentWorkout?.let { workout ->
                    val presenceResult = presenceService.updateWorkoutStatus(workout.id.value)
                    if (presenceResult.isFailure) {
                        Timber.w(presenceResult.exceptionOrNull(), "Failed to update presence on resume - continuing")
                    }
                }
            }
        }
    }

    /**
     * Stops the workout session and timer
     */
    private fun stopWorkoutSession() {
        viewModelScope.launch {
            val result = timerServiceManager.stopTimer()
            if (result.isFailure) {
                updateState { 
                    copy(error = "Failed to stop session: ${result.exceptionOrNull()?.message}")
                }
            } else {
                // Clear presence status to ONLINE
                val presenceResult = presenceService.updateWorkoutStatus(null)
                if (presenceResult.isFailure) {
                    Timber.w(presenceResult.exceptionOrNull(), "Failed to clear presence status - continuing workout stop")
                    // Don't block workout functionality if presence update fails
                }
                
                // Auto-save workout before stopping
                saveCurrentWorkout()
            }
        }
    }

    /**
     * Starts a rest timer with the specified configuration
     */
    private fun startRestTimer(restTimer: RestTimer) {
        viewModelScope.launch {
            val result = timerServiceManager.startRestTimer(restTimer)
            if (result.isFailure) {
                updateState { 
                    copy(error = "Failed to start rest timer: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    /**
     * Skips the current rest timer
     */
    private fun skipRestTimer() {
        viewModelScope.launch {
            val result = timerServiceManager.skipRest()
            if (result.isFailure) {
                updateState { 
                    copy(error = "Failed to skip rest: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    /**
     * Adds an exercise to the current workout
     */
    private fun addExercise(exercise: WorkoutExercise) {
        // TODO: Convert WorkoutExercise to Exercise - this is a type mismatch that needs proper conversion
        // For now, just mark as having unsaved changes
        updateState {
            copy(hasUnsavedChanges = true)
        }
    }

    /**
     * Removes an exercise from the current workout
     */
    private fun removeExercise(exerciseIndex: Int) {
        updateState {
            val currentWorkout = this.currentWorkout ?: return@updateState this
            if (exerciseIndex !in currentWorkout.exercises.indices) return@updateState this
            
            val updatedExercises = currentWorkout.exercises.toMutableList().apply {
                removeAt(exerciseIndex)
            }
            copy(
                currentWorkout = currentWorkout.copy(exercises = updatedExercises),
                hasUnsavedChanges = true
            )
        }
    }

    /**
     * Adds a set to the specified exercise
     */
    private fun addSetToExercise(exerciseIndex: Int) {
        updateState {
            val currentWorkout = this.currentWorkout ?: return@updateState this
            if (exerciseIndex !in currentWorkout.exercises.indices) return@updateState this
            
            val exercise = currentWorkout.exercises[exerciseIndex]
            val newSet = ExerciseSet(
                id = ExerciseSetId.generate(),
                setNumber = exercise.sets.size + 1,
                reps = Reps.of(1) // Minimum required metric
            )
            val updatedSets = exercise.sets + newSet
            val updatedExercise = exercise.copy(sets = updatedSets)
            
            val updatedExercises = currentWorkout.exercises.toMutableList().apply {
                set(exerciseIndex, updatedExercise)
            }
            
            copy(
                currentWorkout = currentWorkout.copy(exercises = updatedExercises),
                hasUnsavedChanges = true
            )
        }
    }

    /**
     * Updates a specific set in an exercise
     */
    private fun updateExerciseSet(exerciseIndex: Int, setIndex: Int, set: ExerciseSet) {
        updateState {
            val currentWorkout = this.currentWorkout ?: return@updateState this
            if (exerciseIndex !in currentWorkout.exercises.indices) return@updateState this
            
            val exercise = currentWorkout.exercises[exerciseIndex]
            if (setIndex !in exercise.sets.indices) return@updateState this
            
            val updatedSets = exercise.sets.toMutableList().apply {
                set(setIndex, set)
            }
            val updatedExercise = exercise.copy(sets = updatedSets)
            
            val updatedExercises = currentWorkout.exercises.toMutableList().apply {
                set(exerciseIndex, updatedExercise)
            }
            
            copy(
                currentWorkout = currentWorkout.copy(exercises = updatedExercises),
                hasUnsavedChanges = true
            )
        }
    }

    /**
     * Removes a set from the specified exercise
     */
    private fun removeSetFromExercise(exerciseIndex: Int, setIndex: Int) {
        updateState {
            val currentWorkout = this.currentWorkout ?: return@updateState this
            if (exerciseIndex !in currentWorkout.exercises.indices) return@updateState this
            
            val exercise = currentWorkout.exercises[exerciseIndex]
            if (setIndex !in exercise.sets.indices) return@updateState this
            
            val updatedSets = exercise.sets.toMutableList().apply {
                removeAt(setIndex)
            }
            val updatedExercise = exercise.copy(sets = updatedSets)
            
            val updatedExercises = currentWorkout.exercises.toMutableList().apply {
                set(exerciseIndex, updatedExercise)
            }
            
            copy(
                currentWorkout = currentWorkout.copy(exercises = updatedExercises),
                hasUnsavedChanges = true
            )
        }
    }

    /**
     * Creates a new empty workout for the current session
     */
    private suspend fun createNewWorkout() {
        try {
            val user = authRepository.currentUser.first() ?: return
            
            val now = java.time.Instant.now()
            val newWorkout = Workout(
                userId = user.uid,
                id = WorkoutId.generate(),
                name = "Active Workout",
                date = java.time.LocalDate.now(),
                exercises = emptyList(),
                status = WorkoutStatus.IN_PROGRESS,
                startTime = now,
                endTime = null,
                createdAt = now,
                updatedAt = now
            )
            
            updateState { 
                copy(
                    currentWorkout = newWorkout,
                    hasActiveWorkout = true,
                    hasUnsavedChanges = true
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to create new workout")
            updateState { copy(error = "Failed to create workout: ${e.message}") }
        }
    }

    /**
     * Saves the current workout to the repository
     */
    private fun saveCurrentWorkout() {
        viewModelScope.launch {
            try {
                val workout = _uiState.value.currentWorkout ?: return@launch
                
                updateState { copy(isSaving = true) }
                
                val result = workoutRepository.saveWorkout(workout)
                if (result.isSuccess) {
                    updateState { 
                        copy(
                            isSaving = false,
                            hasUnsavedChanges = false,
                            successMessage = "Workout saved successfully"
                        )
                    }
                } else {
                    updateState { 
                        copy(
                            isSaving = false,
                            error = "Failed to save workout: ${result.exceptionOrNull()?.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save workout")
                updateState { 
                    copy(
                        isSaving = false,
                        error = "Failed to save workout: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Loads a specific workout by ID
     */
    private fun loadWorkout(workoutId: WorkoutId) {
        viewModelScope.launch {
            try {
                updateState { copy(isLoading = true) }
                
                val user = authRepository.currentUser.first()
                if (user == null) {
                    updateState { 
                        copy(
                            isLoading = false,
                            error = "User not authenticated"
                        )
                    }
                    return@launch
                }

                val workout = workoutRepository.getWorkoutByIdForUser(workoutId, user.uid)
                updateState {
                    copy(
                        isLoading = false,
                        currentWorkout = workout,
                        hasActiveWorkout = workout != null
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load workout $workoutId")
                updateState { 
                    copy(
                        isLoading = false,
                        error = "Failed to load workout: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Formats session time for display
     */
    private fun formatSessionTime(timerState: WorkoutTimerService.TimerServiceState): String {
        return when (val state = timerState.timerState) {
            is WorkoutTimerService.TimerState.SessionRunning -> formatTime(state.elapsedSeconds)
            is WorkoutTimerService.TimerState.SessionPaused -> formatTime(state.pausedAtSeconds)
            else -> "00:00"
        }
    }

    /**
     * Formats rest time for display
     */
    private fun formatRestTime(timerState: WorkoutTimerService.TimerServiceState): String {
        return when (val state = timerState.timerState) {
            is WorkoutTimerService.TimerState.RestActive -> formatTime(state.remainingSeconds.toLong())
            is WorkoutTimerService.TimerState.RestPaused -> formatTime(state.remainingSeconds.toLong())
            else -> ""
        }
    }

    /**
     * Formats time in seconds to HH:MM:SS or MM:SS format
     */
    private fun formatTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%d:%02d".format(minutes, secs)
        }
    }

    /**
     * Clears the current error state
     */
    private fun clearError() {
        updateState { copy(error = null, connectionError = null) }
    }

    /**
     * Dismisses the current success message
     */
    private fun dismissMessage() {
        updateState { copy(successMessage = null) }
    }

    /**
     * Updates the UI state using the provided transform function
     */
    private fun updateState(transform: ActiveWorkoutState.() -> ActiveWorkoutState) {
        _uiState.value = _uiState.value.transform()
    }

    override fun onCleared() {
        super.onCleared()
        // Unbind from timer service when ViewModel is cleared
        timerServiceManager.unbindService()
    }
}

/**
 * UI state for the active workout screen
 */
data class ActiveWorkoutState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isTimerServiceConnected: Boolean = false,
    val isSessionActive: Boolean = false,
    val hasActiveWorkout: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val currentWorkout: Workout? = null,
    val timerState: WorkoutTimerService.TimerServiceState = WorkoutTimerService.TimerServiceState(),
    val formattedSessionTime: String = "00:00",
    val formattedRestTime: String = "",
    val error: String? = null,
    val connectionError: String? = null,
    val successMessage: String? = null
)

/**
 * Events for the active workout screen
 */
sealed class ActiveWorkoutEvent {
    data object StartSession : ActiveWorkoutEvent()
    data object PauseSession : ActiveWorkoutEvent()
    data object ResumeSession : ActiveWorkoutEvent()
    data object StopSession : ActiveWorkoutEvent()
    data class StartRest(val restTimer: RestTimer) : ActiveWorkoutEvent()
    data object SkipRest : ActiveWorkoutEvent()
    data class AddExercise(val exercise: WorkoutExercise) : ActiveWorkoutEvent()
    data class RemoveExercise(val exerciseIndex: Int) : ActiveWorkoutEvent()
    data class AddSet(val exerciseIndex: Int) : ActiveWorkoutEvent()
    data class UpdateSet(val exerciseIndex: Int, val setIndex: Int, val set: ExerciseSet) : ActiveWorkoutEvent()
    data class RemoveSet(val exerciseIndex: Int, val setIndex: Int) : ActiveWorkoutEvent()
    data object SaveWorkout : ActiveWorkoutEvent()
    data class LoadWorkout(val workoutId: WorkoutId) : ActiveWorkoutEvent()
    data object ClearError : ActiveWorkoutEvent()
    data object DismissMessage : ActiveWorkoutEvent()
}