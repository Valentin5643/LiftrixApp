package com.example.liftrix.ui.workout.edit

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixSuccess
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.usecase.workout.GetWorkoutByIdRequest
import com.example.liftrix.domain.usecase.workout.GetWorkoutByIdUseCase
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.state.EditWorkoutUiState
import com.example.liftrix.ui.common.state.EditWorkoutData
import com.example.liftrix.ui.common.state.dataOrNull
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
 * EditWorkoutViewModel - Manages state for editing workout routines and completed sessions
 * 
 * This ViewModel handles both workout routine editing and workout session editing with:
 * - Historical data management with change tracking
 * - Direct modification of original records with timestamp updates
 * - Comprehensive validation preventing data corruption
 * - Visual indicators for editing vs creation modes
 * 
 * Key Features:
 * - Unified state management for both routines and sessions
 * - Change tracking with save/discard functionality
 * - Form validation with user-friendly error messages
 * - Real-time state updates with proper error handling
 * - Integration with repository layer for data persistence
 * 
 * @param getWorkoutByIdUseCase Use case for retrieving workout data
 * @param workoutRepository Repository for workout data persistence
 * @param getCurrentUserIdUseCase Use case for getting current user ID
 * @param errorHandler Centralized error handler
 */
@HiltViewModel
class EditWorkoutViewModel @Inject constructor(
    private val getWorkoutByIdUseCase: GetWorkoutByIdUseCase,
    private val workoutRepository: WorkoutRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    errorHandler: ErrorHandler
) : BaseViewModel<EditWorkoutUiState, EditWorkoutEvent>(errorHandler) {

    override val _uiState = MutableStateFlow<EditWorkoutUiState>(EditWorkoutUiState.Loading)

    // Event flow for navigation and UI events
    private val _events = MutableSharedFlow<EditWorkoutEvent>()
    val events: SharedFlow<EditWorkoutEvent> = _events.asSharedFlow()

    // Store original workout for change tracking
    private var originalWorkout: Workout? = null
    private var currentUserId: String? = null
    
    // Track if user ID has been initialized
    private var isUserIdInitialized = false

    init {
        // Get current user ID
        viewModelScope.launch {
            Timber.d("🔥 EDIT-WORKOUT-DEBUG: EditWorkoutViewModel init - Getting current user ID")
            currentUserId = getCurrentUserIdUseCase()
            isUserIdInitialized = true
            Timber.d("🔥 EDIT-WORKOUT-DEBUG: EditWorkoutViewModel init - Retrieved userId: $currentUserId")
        }
    }

    override fun handleEvent(event: EditWorkoutEvent) {
        when (event) {
            is EditWorkoutEvent.LoadWorkout -> loadWorkout(event.workoutId)
            is EditWorkoutEvent.LoadWorkoutSession -> loadWorkoutSession(event.sessionId)
            is EditWorkoutEvent.UpdateName -> updateName(event.name)
            is EditWorkoutEvent.UpdateDescription -> updateDescription(event.description)
            is EditWorkoutEvent.UpdateNotes -> updateNotes(event.notes)
            is EditWorkoutEvent.UpdateDuration -> updateDuration(event.duration)
            is EditWorkoutEvent.UpdateExercise -> updateExercise(event.index, event.exercise)
            is EditWorkoutEvent.UpdateExerciseSet -> updateExerciseSet(event.exerciseIndex, event.setIndex, event.set)
            is EditWorkoutEvent.RemoveExercise -> removeExercise(event.index)
            is EditWorkoutEvent.ReorderExercises -> reorderExercises(event.exerciseIds)
            is EditWorkoutEvent.SaveChanges -> saveChanges()
            is EditWorkoutEvent.DiscardChanges -> discardChanges()
            EditWorkoutEvent.NavigateBack -> navigateBack()
            is EditWorkoutEvent.ShowError -> showError(event.message)
        }
    }

    override fun setLoadingState() {
        setState(EditWorkoutUiState.Loading)
    }

    override fun updateErrorState(error: LiftrixError) {
        val currentData = _uiState.value.dataOrNull()
        setState(EditWorkoutUiState.Error(
            error = error,
            previousData = currentData
        ))
    }

    /**
     * Loads workout data for editing a routine
     */
    fun loadWorkout(workoutId: WorkoutId) {
        Timber.d("🔥 EDIT-WORKOUT-DEBUG: Starting loadWorkout - workoutId: ${workoutId.value}")
        
        // If user ID is not yet initialized, wait for it
        if (!isUserIdInitialized) {
            Timber.d("🔥 EDIT-WORKOUT-DEBUG: User ID not yet initialized, fetching...")
            viewModelScope.launch {
                // Fetch user ID synchronously if not yet available
                if (currentUserId == null) {
                    currentUserId = getCurrentUserIdUseCase()
                    isUserIdInitialized = true
                    Timber.d("🔥 EDIT-WORKOUT-DEBUG: User ID fetched in loadWorkout: $currentUserId")
                }
                // Retry loading workout with the user ID now available
                loadWorkoutInternal(workoutId)
            }
            return
        }
        
        loadWorkoutInternal(workoutId)
    }
    
    /**
     * Internal method to load workout after ensuring user ID is available
     */
    private fun loadWorkoutInternal(workoutId: WorkoutId) {
        val userId = currentUserId
        Timber.d("🔥 EDIT-WORKOUT-DEBUG: loadWorkoutInternal - Current userId: $userId")
        
        if (userId == null) {
            Timber.e("🔥 EDIT-WORKOUT-DEBUG: User authentication failed - userId is null")
            handleError(
                LiftrixError.AuthenticationError(
                    errorMessage = "User not authenticated",
                    errorCode = "NO_USER_ID"
                )
            )
            return
        }

        if (userId.isBlank()) {
            Timber.e("🔥 EDIT-WORKOUT-DEBUG: User authentication failed - userId is blank")
            handleError(
                LiftrixError.AuthenticationError(
                    errorMessage = "User not authenticated",
                    errorCode = "BLANK_USER_ID"
                )
            )
            return
        }

        val request = GetWorkoutByIdRequest(workoutId, userId)
        Timber.d("🔥 EDIT-WORKOUT-DEBUG: Creating request - workoutId: ${request.workoutId.value}, userId: ${request.userId}")

        executeUseCase(
            useCase = { 
                Timber.d("🔥 EDIT-WORKOUT-DEBUG: Calling getWorkoutByIdUseCase with request")
                getWorkoutByIdUseCase(request)
            },
            onSuccess = { workout ->
                Timber.d("🔥 EDIT-WORKOUT-DEBUG: Use case completed successfully - workout is ${if (workout != null) "NOT NULL" else "NULL"}")
                
                if (workout != null) {
                    Timber.d("🔥 EDIT-WORKOUT-DEBUG: Workout found - id: ${workout.id.value}, name: ${workout.name}, userId: ${workout.userId}, status: ${workout.status}")
                    
                    // Additional validation check
                    if (workout.userId != userId) {
                        Timber.e("🔥 EDIT-WORKOUT-DEBUG: User ID mismatch - workout.userId: ${workout.userId}, current userId: $userId")
                        handleError(
                            LiftrixError.AuthenticationError(
                                errorMessage = "Access denied: workout belongs to different user",
                                errorCode = "WORKOUT_USER_MISMATCH"
                            )
                        )
                        return@executeUseCase
                    }
                    
                    originalWorkout = workout
                    setState(
                        EditWorkoutUiState.Success(
                            data = EditWorkoutData(
                                originalWorkout = workout,
                                editedWorkout = workout,
                                editedName = workout.name,
                                editedDescription = workout.notes ?: "",
                                editedExercises = workout.exercises,
                                lastModified = workout.updatedAt
                            )
                        )
                    )
                    Timber.i("🔥 EDIT-WORKOUT-DEBUG: Successfully loaded workout for editing: ${workout.name}")
                } else {
                    Timber.e("🔥 EDIT-WORKOUT-DEBUG: Workout not found - workoutId: ${workoutId.value}, userId: $userId")
                    handleError(
                        LiftrixError.NotFoundError(
                            errorMessage = "Workout not found",
                            resourceType = "workout",
                            resourceId = workoutId.value
                        )
                    )
                }
            },
            onError = { error ->
                Timber.e("🔥 EDIT-WORKOUT-DEBUG: Use case failed - error: ${error.message}, type: ${error::class.simpleName}")
            }
        )
    }

    /**
     * Loads workout session data for editing a completed session
     */
    fun loadWorkoutSession(sessionId: WorkoutId) {
        loadWorkout(sessionId) // Same loading logic, different UI context
    }

    /**
     * Updates the workout name
     */
    fun updateName(name: String) {
        val currentData = _uiState.value.dataOrNull()
        if (currentData != null) {
            setState(EditWorkoutUiState.Success(
                data = currentData.copy(editedName = name)
            ))
        }
    }

    /**
     * Updates the workout description/notes
     */
    fun updateDescription(description: String) {
        val currentData = _uiState.value.dataOrNull()
        if (currentData != null) {
            setState(EditWorkoutUiState.Success(
                data = currentData.copy(editedDescription = description)
            ))
        }
    }

    /**
     * Updates session notes (alias for description)
     */
    fun updateNotes(notes: String) = updateDescription(notes)

    /**
     * Updates workout duration (for session editing)
     */
    fun updateDuration(duration: Duration?) {
        val currentData = _uiState.value.dataOrNull()
        if (currentData != null && currentData.editedWorkout != null) {
            val workout = currentData.editedWorkout
            val updatedWorkout = workout.copy(
                startTime = workout.startTime,
                endTime = duration?.let { workout.startTime?.plus(it) },
                updatedAt = Instant.now()
            )
            
            setState(EditWorkoutUiState.Success(
                data = currentData.copy(
                    editedWorkout = updatedWorkout,
                    lastModified = updatedWorkout.updatedAt
                )
            ))
        }
    }

    /**
     * Updates an exercise at the specified index
     */
    fun updateExercise(index: Int, exercise: Exercise) {
        val currentData = _uiState.value.dataOrNull()
        if (currentData != null) {
            val updatedExercises = currentData.editedExercises.toMutableList()
            if (index in updatedExercises.indices) {
                updatedExercises[index] = exercise
                setState(EditWorkoutUiState.Success(
                    data = currentData.copy(editedExercises = updatedExercises)
                ))
            }
        }
    }

    /**
     * Replaces an exercise at the specified index with a new exercise from the library
     */
    fun replaceExercise(index: Int, newExerciseLibrary: com.example.liftrix.domain.model.ExerciseLibrary) {
        val currentData = _uiState.value.dataOrNull()
        if (currentData != null) {
            val updatedExercises = currentData.editedExercises.toMutableList()
            if (index in updatedExercises.indices) {
                val oldExercise = updatedExercises[index]
                val newExercise = Exercise(
                    id = oldExercise.id, // Keep same ID to maintain references
                    workoutId = oldExercise.workoutId, // Keep same workout ID
                    libraryExercise = newExerciseLibrary,
                    orderIndex = oldExercise.orderIndex, // Keep same position
                    targetSets = oldExercise.targetSets, // Preserve target sets
                    targetReps = oldExercise.targetReps, // Preserve target reps
                    targetWeight = oldExercise.targetWeight, // Preserve target weight
                    targetTime = oldExercise.targetTime, // Preserve target time
                    targetDistance = oldExercise.targetDistance, // Preserve target distance
                    sets = emptyList(), // Start with no sets for the new exercise
                    notes = oldExercise.notes, // Preserve notes if any
                    createdAt = oldExercise.createdAt // Keep original creation time
                )
                updatedExercises[index] = newExercise
                setState(EditWorkoutUiState.Success(
                    data = currentData.copy(editedExercises = updatedExercises)
                ))
                Timber.d("🔥 REPLACE-DEBUG: Replaced exercise at index $index with ${newExerciseLibrary.name}")
            }
        }
    }

    /**
     * Updates a specific exercise set
     */
    fun updateExerciseSet(exerciseIndex: Int, setIndex: Int, set: ExerciseSet) {
        val currentData = _uiState.value.dataOrNull()
        if (currentData != null) {
            val updatedExercises = currentData.editedExercises.toMutableList()
            if (exerciseIndex in updatedExercises.indices) {
                val exercise = updatedExercises[exerciseIndex]
                val updatedSets = exercise.sets.toMutableList()
                if (setIndex in updatedSets.indices) {
                    updatedSets[setIndex] = set
                    val updatedExercise = exercise.copy(sets = updatedSets)
                    updatedExercises[exerciseIndex] = updatedExercise
                    setState(EditWorkoutUiState.Success(
                        data = currentData.copy(editedExercises = updatedExercises)
                    ))
                }
            }
        }
    }

    /**
     * Removes an exercise at the specified index
     */
    fun removeExercise(index: Int) {
        val currentData = _uiState.value.dataOrNull()
        if (currentData != null) {
            val updatedExercises = currentData.editedExercises.toMutableList()
            if (index in updatedExercises.indices) {
                updatedExercises.removeAt(index)
                setState(EditWorkoutUiState.Success(
                    data = currentData.copy(editedExercises = updatedExercises)
                ))
                Timber.d("🔥 REMOVE-DEBUG: Removed exercise at index $index, ${updatedExercises.size} exercises remaining")
            }
        }
    }

    /**
     * Reorders exercises based on new exercise ID order
     */
    fun reorderExercises(exerciseIds: List<ExerciseId>) {
        val currentData = _uiState.value.dataOrNull()
        if (currentData != null) {
            val currentExercises = currentData.editedExercises
            val reorderedExercises = exerciseIds.mapNotNull { id ->
                currentExercises.find { it.id == id }
            }
            
            if (reorderedExercises.size == currentExercises.size) {
                setState(EditWorkoutUiState.Success(
                    data = currentData.copy(editedExercises = reorderedExercises)
                ))
            }
        }
    }

    /**
     * Saves all changes to the workout with comprehensive validation and error recovery
     */
    fun saveChanges() {
        val currentData = _uiState.value.dataOrNull()
        if (currentData != null && currentData.hasChanges) {
            val originalWorkout = this.originalWorkout ?: return
            
            // Validate changes before saving
            val validationErrors = validateWorkoutChanges(currentData)
            if (validationErrors.isNotEmpty()) {
                handleValidationErrors(validationErrors)
                return
            }
            
            // Create updated workout with changes
            val updatedWorkout = originalWorkout.copy(
                name = currentData.editedName,
                notes = currentData.editedDescription.ifBlank { null },
                exercises = currentData.editedExercises,
                updatedAt = Instant.now()
            )

            executeUseCase(
                useCase = { 
                    Timber.d("Saving workout changes for ${updatedWorkout.id.value}")
                    workoutRepository.updateWorkout(updatedWorkout)
                },
                onSuccess = { savedWorkout ->
                    Timber.i("Successfully saved workout changes: ${savedWorkout.name}")
                    // Update original workout reference for future change tracking
                    this.originalWorkout = savedWorkout
                    // Navigate back after successful save
                    viewModelScope.launch {
                        _events.emit(EditWorkoutEvent.NavigateBack)
                    }
                },
                onError = { error ->
                    handleSaveError(error)
                }
            )
        }
    }
    
    /**
     * Validates workout changes before saving
     */
    private fun validateWorkoutChanges(data: EditWorkoutData): List<String> {
        val errors = mutableListOf<String>()
        
        // Validate workout name
        if (data.editedName.isBlank()) {
            errors.add("Workout name cannot be empty")
        } else if (data.editedName.length < 2) {
            errors.add("Workout name must be at least 2 characters")
        } else if (data.editedName.length > 100) {
            errors.add("Workout name must be less than 100 characters")
        }
        
        // Validate exercises
        if (data.editedExercises.isEmpty()) {
            errors.add("Workout must have at least one exercise")
        }
        
        // Validate individual exercises and sets
        data.editedExercises.forEachIndexed { exerciseIndex, exercise ->
            if (exercise.sets.isEmpty()) {
                errors.add("Exercise '${exercise.libraryExercise.name}' must have at least one set")
            }
            
            exercise.sets.forEachIndexed { setIndex, set ->
                // Weight validation
                set.weight?.let { weight ->
                    if (weight.kilograms < 0) {
                        errors.add("Weight cannot be negative")
                    }
                    if (weight.kilograms > 900) { // ~2000 lbs
                        errors.add("Weight over 2000 lbs seems unrealistic")
                    }
                }
                
                // Reps validation
                set.reps?.let { reps ->
                    if (reps.count <= 0) {
                        errors.add("Reps must be greater than 0")
                    }
                    
                    if (reps.count > 1000) {
                        errors.add("Reps over 1000 seems unrealistic")
                    }
                }
            }
        }
        
        return errors
    }
    
    /**
     * Handles validation errors by displaying them to the user
     */
    private fun handleValidationErrors(errors: List<String>) {
        if (errors.isNotEmpty()) {
            val validationError = LiftrixError.ValidationError(
                field = "workout_data",
                violations = errors,
                errorMessage = "Please fix the following issues before saving"
            )
            
            handleError(validationError)
        }
    }
    
    /**
     * Handles save operation errors with recovery strategies
     */
    private fun handleSaveError(error: LiftrixError) {
        viewModelScope.launch {
            when (error) {
                is LiftrixError.NetworkError -> {
                    _events.emit(EditWorkoutEvent.ShowError("Network error. Changes will be saved when connection is restored."))
                }
                is LiftrixError.DatabaseError -> {
                    _events.emit(EditWorkoutEvent.ShowError("Database error: ${error.message}"))
                }
                else -> {
                    Timber.e("Failed to save workout changes: ${error.message}")
                    _events.emit(EditWorkoutEvent.ShowError(error.message ?: "Failed to save changes"))
                }
            }
        }
    }

    /**
     * Discards all changes and navigates back
     */
    fun discardChanges() {
        navigateBack()
    }

    /**
     * Navigates back to the previous screen
     */
    private fun navigateBack() {
        viewModelScope.launch {
            _events.emit(EditWorkoutEvent.NavigateBack)
        }
    }

    /**
     * Shows an error message
     */
    private fun showError(message: String) {
        viewModelScope.launch {
            _events.emit(EditWorkoutEvent.ShowError(message))
        }
    }
}

// EditWorkoutData and EditWorkoutUiState are now defined in ViewModelState.kt

/**
 * Events for Edit Workout Screen
 */
sealed class EditWorkoutEvent : ViewModelEvent {
    data class LoadWorkout(val workoutId: WorkoutId) : EditWorkoutEvent()
    data class LoadWorkoutSession(val sessionId: WorkoutId) : EditWorkoutEvent()
    data class UpdateName(val name: String) : EditWorkoutEvent()
    data class UpdateDescription(val description: String) : EditWorkoutEvent()
    data class UpdateNotes(val notes: String) : EditWorkoutEvent()
    data class UpdateDuration(val duration: Duration?) : EditWorkoutEvent()
    data class UpdateExercise(val index: Int, val exercise: Exercise) : EditWorkoutEvent()
    data class UpdateExerciseSet(val exerciseIndex: Int, val setIndex: Int, val set: ExerciseSet) : EditWorkoutEvent()
    data class RemoveExercise(val index: Int) : EditWorkoutEvent()
    data class ReorderExercises(val exerciseIds: List<ExerciseId>) : EditWorkoutEvent()
    data object SaveChanges : EditWorkoutEvent()
    data object DiscardChanges : EditWorkoutEvent()
    data object NavigateBack : EditWorkoutEvent()
    data class ShowError(val message: String) : EditWorkoutEvent()
}