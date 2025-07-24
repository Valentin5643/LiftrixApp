package com.example.liftrix.ui.workout.edit

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.usecase.workout.GetWorkoutSessionForEditingRequest
import com.example.liftrix.domain.usecase.workout.GetWorkoutSessionForEditingUseCase
import com.example.liftrix.domain.usecase.workout.UpdateWorkoutSessionUseCase
import com.example.liftrix.domain.usecase.workout.UpdateWorkoutSessionRequest
import com.example.liftrix.domain.usecase.workout.WorkoutSessionEditingData
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.state.EditSessionUiState
import com.example.liftrix.ui.common.state.EditSessionData
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * EditSessionViewModel - ViewModel for editing completed workout sessions
 * 
 * This ViewModel handles historical data editing using the existing use cases:
 * - GetWorkoutSessionForEditingUseCase for loading session data
 * - UpdateWorkoutSessionUseCase for saving changes
 * 
 * The simplified approach leverages existing infrastructure while providing
 * the necessary functionality for session editing.
 */
@HiltViewModel
class EditSessionViewModel @Inject constructor(
    private val getWorkoutSessionForEditingUseCase: GetWorkoutSessionForEditingUseCase,
    private val updateWorkoutSessionUseCase: UpdateWorkoutSessionUseCase,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    errorHandler: ErrorHandler
) : BaseViewModel<EditSessionUiState, EditSessionEvent>(errorHandler) {

    override val _uiState = MutableStateFlow<EditSessionUiState>(EditSessionUiState.Loading)

    // Event flow for navigation and UI events
    private val _events = MutableSharedFlow<EditSessionEvent>()
    val events: SharedFlow<EditSessionEvent> = _events.asSharedFlow()

    // Store original session for change tracking
    private var originalEditingData: WorkoutSessionEditingData? = null
    private var currentUserId: String? = null

    init {
        // Get current user ID
        viewModelScope.launch {
            currentUserId = getCurrentUserIdUseCase()
        }
    }

    override fun handleEvent(event: EditSessionEvent) {
        when (event) {
            is EditSessionEvent.LoadSession -> loadSession(event.sessionId)
            is EditSessionEvent.UpdateSessionNotes -> updateSessionNotes(event.notes)
            is EditSessionEvent.UpdateSessionDuration -> updateSessionDuration(event.duration)
            is EditSessionEvent.UpdateExerciseSet -> updateExerciseSet(event.exerciseIndex, event.setIndex, event.set)
            is EditSessionEvent.SaveChanges -> saveChanges()
            is EditSessionEvent.DiscardChanges -> discardChanges()
            EditSessionEvent.NavigateBack -> navigateBack()
            is EditSessionEvent.ShowError -> showError(event.message)
        }
    }

    override fun setLoadingState() {
        setState(EditSessionUiState.Loading)
    }

    override fun updateErrorState(error: LiftrixError) {
        val currentData = _uiState.value.dataOrNull()
        setState(EditSessionUiState.Error(
            error = error,
            previousData = currentData
        ))
    }

    /**
     * Loads workout session data for historical editing
     */
    private fun loadSession(sessionId: WorkoutId) {
        executeUseCase(
            useCase = { 
                getWorkoutSessionForEditingUseCase(GetWorkoutSessionForEditingRequest(sessionId))
            },
            onSuccess = { editingData ->
                originalEditingData = editingData
                setState(
                    EditSessionUiState.Success(
                        data = EditSessionData(
                            session = convertWorkoutToUnifiedSession(editingData.session),
                            hasChanges = false
                        )
                    )
                )
                Timber.d("Successfully loaded session for editing: ${editingData.session.name}")
            },
            onError = { error ->
                Timber.e("Failed to load workout session: ${error.message}")
            }
        )
    }

    /**
     * Updates session notes
     */
    private fun updateSessionNotes(notes: String) {
        val currentData = _uiState.value.dataOrNull()
        if (currentData != null) {
            val updatedSession = currentData.session.copy(
                notes = notes.ifBlank { null },
                lastModified = Instant.now()
            )
            val updatedData = currentData.copy(
                session = updatedSession,
                hasChanges = true
            )
            setState(EditSessionUiState.Success(data = updatedData))
        }
    }

    /**
     * Updates session duration
     */
    private fun updateSessionDuration(duration: Duration) {
        val currentData = _uiState.value.dataOrNull()
        if (currentData != null) {
            val session = currentData.session
            val newEndTime = session.startedAt.plus(duration)
            
            val updatedSession = session.copy(
                endedAt = newEndTime,
                lastModified = Instant.now()
            )
            val updatedData = currentData.copy(
                session = updatedSession,
                hasChanges = true
            )
            setState(EditSessionUiState.Success(data = updatedData))
        }
    }

    /**
     * Updates a specific exercise set within the session
     */
    private fun updateExerciseSet(exerciseIndex: Int, setIndex: Int, set: SessionSet) {
        val currentData = _uiState.value.dataOrNull()
        if (currentData != null) {
            val session = currentData.session
            val updatedExercises = session.exercises.toMutableList()
            
            if (exerciseIndex in updatedExercises.indices) {
                val exercise = updatedExercises[exerciseIndex]
                val updatedSets = exercise.sets.toMutableList()
                
                if (setIndex in updatedSets.indices) {
                    updatedSets[setIndex] = set
                    val updatedExercise = exercise.copy(sets = updatedSets)
                    updatedExercises[exerciseIndex] = updatedExercise
                    
                    val updatedSession = session.copy(
                        exercises = updatedExercises,
                        lastModified = Instant.now()
                    )
                    val updatedData = currentData.copy(
                        session = updatedSession,
                        hasChanges = true
                    )
                    setState(EditSessionUiState.Success(data = updatedData))
                }
            }
        }
    }

    /**
     * Saves all changes to the workout session
     */
    private fun saveChanges() {
        val currentData = _uiState.value.dataOrNull()
        val originalData = originalEditingData
        if (currentData != null && originalData != null && currentData.hasChanges) {
            
            // Convert UnifiedWorkoutSession to Workout for the use case
            val workoutFromSession = convertSessionToWorkout(currentData.session)
            
            executeUseCase(
                useCase = { 
                    updateWorkoutSessionUseCase(
                        UpdateWorkoutSessionRequest(
                            updatedSession = workoutFromSession,
                            originalCreatedAt = originalData.originalCreatedAt
                        )
                    )
                },
                onSuccess = { savedWorkout ->
                    Timber.i("Successfully saved session changes: ${savedWorkout.name}")
                    // Navigate back after successful save
                    viewModelScope.launch {
                        _events.emit(EditSessionEvent.NavigateBack)
                    }
                },
                onError = { error ->
                    Timber.e("Failed to save session changes: ${error.message}")
                    showError("Failed to save changes: ${error.message}")
                }
            )
        }
    }

    /**
     * Converts UnifiedWorkoutSession to Workout for the update use case
     */
    private fun convertSessionToWorkout(session: UnifiedWorkoutSession): Workout {
        val exercises = session.exercises.map { sessionExercise ->
            // This is a simplified conversion - in a real implementation,
            // you'd need proper conversion logic
            Exercise(
                id = ExerciseId(sessionExercise.exerciseId.value),
                libraryExercise = ExerciseLibrary(
                    id = sessionExercise.exerciseId.value,
                    name = sessionExercise.name,
                    primaryMuscleGroup = sessionExercise.category,
                    secondaryMuscleGroups = sessionExercise.secondaryMuscles.toList(),
                    equipment = sessionExercise.equipment,
                    instructions = "",
                    movementPattern = "compound",
                    difficultyLevel = 5,
                    isCompound = true,
                    searchableTerms = listOf(sessionExercise.name.lowercase())
                ),
                sets = sessionExercise.sets.map { sessionSet ->
                    ExerciseSet(
                        id = ExerciseSetId.generate(),
                        setNumber = sessionSet.setNumber,
                        reps = sessionSet.actualReps?.let { Reps(it) } ?: sessionSet.targetReps?.let { Reps(it) },
                        weight = sessionSet.actualWeight ?: sessionSet.targetWeight,
                        completedAt = sessionSet.completedAt,
                        notes = sessionSet.notes
                    )
                },
                workoutId = WorkoutId("session_workout"),
                orderIndex = sessionExercise.orderIndex,
                notes = sessionExercise.notes,
                createdAt = Instant.now()
            )
        }

        return Workout(
            userId = session.userId,
            id = WorkoutId(session.id.value),
            name = session.name,
            date = session.startedAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
            exercises = exercises,
            status = if (session.sessionStatus == UnifiedWorkoutSession.SessionStatus.COMPLETED) WorkoutStatus.COMPLETED else WorkoutStatus.IN_PROGRESS,
            startTime = session.startedAt,
            endTime = session.endedAt,
            notes = session.notes,
            templateId = session.templateId?.let { WorkoutId(it) },
            createdAt = session.startedAt,
            updatedAt = session.lastModified
        )
    }

    /**
     * Discards all changes and navigates back
     */
    private fun discardChanges() {
        navigateBack()
    }

    /**
     * Navigates back to the previous screen
     */
    private fun navigateBack() {
        viewModelScope.launch {
            _events.emit(EditSessionEvent.NavigateBack)
        }
    }

    /**
     * Shows an error message
     */
    private fun showError(message: String) {
        viewModelScope.launch {
            _events.emit(EditSessionEvent.ShowError(message))
        }
    }
}

/**
 * Events for Edit Session Screen
 */
sealed class EditSessionEvent : ViewModelEvent {
    data class LoadSession(val sessionId: WorkoutId) : EditSessionEvent()
    data class UpdateSessionNotes(val notes: String) : EditSessionEvent()
    data class UpdateSessionDuration(val duration: Duration) : EditSessionEvent()
    data class UpdateExerciseSet(val exerciseIndex: Int, val setIndex: Int, val set: SessionSet) : EditSessionEvent()
    data object SaveChanges : EditSessionEvent()
    data object DiscardChanges : EditSessionEvent()
    data object NavigateBack : EditSessionEvent()
    data class ShowError(val message: String) : EditSessionEvent()
}

/**
 * Helper function to convert Workout to UnifiedWorkoutSession for editing
 */
private fun convertWorkoutToUnifiedSession(workout: Workout): UnifiedWorkoutSession {
    val sessionExercises = workout.exercises.mapIndexed { index, exercise ->
        SessionExercise(
            exerciseId = exercise.id,
            name = exercise.libraryExercise.name,
            category = exercise.libraryExercise.primaryMuscleGroup,
            primaryMuscle = exercise.libraryExercise.primaryMuscleGroup,
            equipment = exercise.libraryExercise.equipment,
            secondaryMuscles = exercise.libraryExercise.secondaryMuscleGroups.toSet(),
            sets = exercise.sets.map { exerciseSet ->
                SessionSet(
                    setNumber = exerciseSet.setNumber,
                    targetReps = exerciseSet.reps?.count,
                    targetWeight = exerciseSet.weight,
                    actualReps = exerciseSet.reps?.count,
                    actualWeight = exerciseSet.weight,
                    completedAt = exerciseSet.completedAt,
                    notes = exerciseSet.notes
                )
            },
            orderIndex = index,
            notes = exercise.notes
        )
    }

    return UnifiedWorkoutSession(
        id = WorkoutSessionId(workout.id.value),
        userId = workout.userId,
        name = workout.name,
        templateId = workout.templateId?.value,
        exercises = sessionExercises,
        currentExerciseIndex = 0,
        sessionStatus = if (workout.status == WorkoutStatus.COMPLETED) 
            UnifiedWorkoutSession.SessionStatus.COMPLETED 
        else 
            UnifiedWorkoutSession.SessionStatus.ACTIVE,
        startedAt = workout.startTime ?: workout.createdAt,
        endedAt = workout.endTime,
        elapsedTimeSeconds = workout.getDuration()?.toSeconds() ?: 0,
        notes = workout.notes,
        lastModified = workout.updatedAt
    )
}

/**
 * Extension function to safely get data from UiState
 */
private fun EditSessionUiState.dataOrNull(): EditSessionData? {
    return when (this) {
        is EditSessionUiState.Success -> data
        is EditSessionUiState.Error -> previousData
        else -> null
    }
}