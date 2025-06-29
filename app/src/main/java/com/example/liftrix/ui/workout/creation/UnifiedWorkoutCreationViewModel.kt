package com.example.liftrix.ui.workout.creation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.ExerciseLibraryRepository
import com.example.liftrix.domain.usecase.CreateWorkoutWithExercisesUseCase
import com.example.liftrix.domain.usecase.exercise.SearchExercisesUseCase
import com.example.liftrix.ui.common.updateState
import com.example.liftrix.ui.workout.creation.model.SelectedExercise
import com.example.liftrix.ui.workout.creation.model.SetInput
import com.example.liftrix.ui.workout.creation.model.WorkoutCreationEvent
import com.example.liftrix.ui.workout.creation.model.WorkoutCreationState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

/**
 * ViewModel for the redesigned single-screen workout creation flow
 * Implements MVI pattern with StateFlow for reactive state management
 */
@HiltViewModel
class UnifiedWorkoutCreationViewModel @Inject constructor(
    private val createWorkoutWithExercisesUseCase: CreateWorkoutWithExercisesUseCase,
    private val exerciseLibraryRepository: ExerciseLibraryRepository,
    private val searchExercisesUseCase: SearchExercisesUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow(WorkoutCreationState.EMPTY)
    val state: StateFlow<WorkoutCreationState> = _state.asStateFlow()
    
    init {
        loadExerciseLibrary()
    }
    
    /**
     * Handles all user events following MVI pattern
     */
    fun onEvent(event: WorkoutCreationEvent) {
        when (event) {
            is WorkoutCreationEvent.UpdateWorkoutName -> updateWorkoutName(event.name)
            is WorkoutCreationEvent.UpdateWorkoutDescription -> updateWorkoutDescription(event.description)
            is WorkoutCreationEvent.ShowExerciseSelector -> showExerciseSelector()
            is WorkoutCreationEvent.HideExerciseSelector -> hideExerciseSelector()
            is WorkoutCreationEvent.UpdateExerciseSearchQuery -> updateExerciseSearchQuery(event.query)
            is WorkoutCreationEvent.SelectExercise -> addExercise(event.exercise)
            is WorkoutCreationEvent.RemoveExercise -> removeExercise(event.exerciseIndex)
            is WorkoutCreationEvent.AddSetToExercise -> addSet(event.exerciseIndex)
            is WorkoutCreationEvent.RemoveSetFromExercise -> removeSet(event.exerciseIndex, event.setIndex)
            is WorkoutCreationEvent.UpdateSetReps -> updateSetReps(event.exerciseIndex, event.setIndex, event.reps)
            is WorkoutCreationEvent.UpdateSetRpe -> updateSetRpe(event.exerciseIndex, event.setIndex, event.rpe)
            is WorkoutCreationEvent.UpdateSetWeight -> updateSetWeight(event.exerciseIndex, event.setIndex, event.weight)
            is WorkoutCreationEvent.SaveWorkout -> saveWorkout()
            is WorkoutCreationEvent.ClearMessages -> clearMessages()
            is WorkoutCreationEvent.DismissSuccessMessage -> clearSuccessMessage()
            is WorkoutCreationEvent.ResetForm -> resetForm()
            is WorkoutCreationEvent.ReorderExercises -> reorderExercises(event.fromIndex, event.toIndex)
        }
    }
    
    /**
     * Loads exercise library from repository
     */
    private fun loadExerciseLibrary() {
        exerciseLibraryRepository.getAllExercises()
            .catch { error ->
                Timber.e(error, "Failed to load exercise library")
                _state.updateState { 
                    copy(
                        isLoading = false,
                        errorMessage = "Failed to load exercises. Please try again."
                    )
                }
            }
            .onEach { exercises ->
                _state.updateState { 
                    copy(
                        availableExercises = exercises,
                        filteredExercises = exercises,
                        isLoading = false
                    )
                }
            }
            .launchIn(viewModelScope)
    }
    
    /**
     * Updates workout name with validation
     */
    private fun updateWorkoutName(name: String) {
        val trimmedName = name.take(WorkoutCreationState.MAX_WORKOUT_NAME_LENGTH)
        val nameError = _state.value.validateWorkoutName(trimmedName)
        val isValid = nameError == null
        
        _state.value = _state.value.copy(
            workoutName = trimmedName,
            workoutNameError = nameError,
            isWorkoutNameValid = isValid,
            isFormValid = isValid && _state.value.isExercisesValid
        )
    }
    
    /**
     * Updates workout description with validation
     */
    private fun updateWorkoutDescription(description: String) {
        val trimmedDescription = description.take(WorkoutCreationState.MAX_WORKOUT_DESCRIPTION_LENGTH)
        val descriptionError = _state.value.validateWorkoutDescription(trimmedDescription)
        
        _state.value = _state.value.copy(
            workoutDescription = trimmedDescription,
            workoutDescriptionError = descriptionError
        )
    }
    
    /**
     * Shows exercise selector bottom sheet
     */
    private fun showExerciseSelector() {
        _state.value = _state.value.copy(
            isExerciseSelectorVisible = true
        )
    }
    
    /**
     * Hides exercise selector bottom sheet
     */
    private fun hideExerciseSelector() {
        _state.value = _state.value.copy(
            isExerciseSelectorVisible = false,
            exerciseSearchQuery = ""
        )
    }
    
    /**
     * Updates exercise search query and filters exercises
     */
    private fun updateExerciseSearchQuery(query: String) {
        val filteredExercises = if (query.isBlank()) {
            _state.value.availableExercises
        } else {
            _state.value.availableExercises.filter { exercise ->
                exercise.matchesQuery(query)
            }.sortedByDescending { exercise ->
                exercise.calculateMatchScore(query)
            }
        }
        
        _state.value = _state.value.copy(
            exerciseSearchQuery = query,
            filteredExercises = filteredExercises
        )
    }
    
    /**
     * Adds exercise to the workout
     */
    private fun addExercise(exercise: ExerciseLibrary) {
        val currentExercises = _state.value.selectedExercises
        
        // Check if exercise already exists
        val existingExercise = currentExercises.find { it.libraryExercise.id == exercise.id }
        if (existingExercise != null) {
            _state.value = _state.value.copy(
                errorMessage = "Exercise '${exercise.name}' is already added to this workout"
            )
            return
        }
        
        // Check maximum exercises limit
        if (currentExercises.size >= WorkoutCreationState.MAX_EXERCISES_COUNT) {
            _state.value = _state.value.copy(
                errorMessage = "Maximum ${WorkoutCreationState.MAX_EXERCISES_COUNT} exercises allowed per workout"
            )
            return
        }
        
        val selectedExercise = SelectedExercise.fromLibraryExercise(
            exercise = exercise,
            orderIndex = currentExercises.size
        )
        
        val updatedExercises = currentExercises + selectedExercise
        val isExercisesValid = updatedExercises.isNotEmpty() && updatedExercises.all { it.isValid() }
        
        _state.value = _state.value.copy(
            selectedExercises = updatedExercises,
            isExerciseSelectorVisible = false,
            exerciseSearchQuery = "",
            isExercisesValid = isExercisesValid,
            isFormValid = _state.value.isWorkoutNameValid && isExercisesValid
        )
    }
    
    /**
     * Removes exercise from the workout
     */
    private fun removeExercise(exerciseIndex: Int) {
        val currentExercises = _state.value.selectedExercises
        if (exerciseIndex < 0 || exerciseIndex >= currentExercises.size) return
        
        val updatedExercises = currentExercises.toMutableList().apply {
            removeAt(exerciseIndex)
            // Update order indices
            forEachIndexed { newIndex, exercise ->
                this[newIndex] = exercise.copy(orderIndex = newIndex)
            }
        }
        
        val isExercisesValid = updatedExercises.isNotEmpty() && updatedExercises.all { it.isValid() }
        
        _state.value = _state.value.copy(
            selectedExercises = updatedExercises,
            isExercisesValid = isExercisesValid,
            isFormValid = _state.value.isWorkoutNameValid && isExercisesValid
        )
    }
    
    /**
     * Updates a specific set in an exercise
     */
    private fun updateSet(exerciseIndex: Int, setIndex: Int, setInput: SetInput) {
        val currentExercises = _state.value.selectedExercises
        if (exerciseIndex < 0 || exerciseIndex >= currentExercises.size) return
        
        val exercise = currentExercises[exerciseIndex]
        if (setIndex < 0 || setIndex >= exercise.sets.size) return
        
        val updatedSets = exercise.sets.toMutableList().apply {
            this[setIndex] = setInput
        }
        
        val updatedExercise = exercise.copy(sets = updatedSets)
        val updatedExercises = currentExercises.toMutableList().apply {
            this[exerciseIndex] = updatedExercise
        }
        
        val isExercisesValid = updatedExercises.isNotEmpty() && updatedExercises.all { it.isValid() }
        
        _state.value = _state.value.copy(
            selectedExercises = updatedExercises,
            isExercisesValid = isExercisesValid,
            isFormValid = _state.value.isWorkoutNameValid && isExercisesValid
        )
    }
    
    /**
     * Adds a new set to an exercise
     */
    private fun addSet(exerciseIndex: Int) {
        val currentExercises = _state.value.selectedExercises
        if (exerciseIndex < 0 || exerciseIndex >= currentExercises.size) return
        
        val exercise = currentExercises[exerciseIndex]
        val newSet = SetInput.createForEquipment(exercise.libraryExercise.equipment)
        val updatedSets = exercise.sets + newSet
        
        val updatedExercise = exercise.copy(sets = updatedSets)
        val updatedExercises = currentExercises.toMutableList().apply {
            this[exerciseIndex] = updatedExercise
        }
        
        _state.value = _state.value.copy(
            selectedExercises = updatedExercises
        )
    }
    
    /**
     * Removes a set from an exercise
     */
    private fun removeSet(exerciseIndex: Int, setIndex: Int) {
        val currentExercises = _state.value.selectedExercises
        if (exerciseIndex < 0 || exerciseIndex >= currentExercises.size) return
        
        val exercise = currentExercises[exerciseIndex]
        if (setIndex < 0 || setIndex >= exercise.sets.size || exercise.sets.size <= 1) return
        
        val updatedSets = exercise.sets.toMutableList().apply {
            removeAt(setIndex)
        }
        
        val updatedExercise = exercise.copy(sets = updatedSets)
        val updatedExercises = currentExercises.toMutableList().apply {
            this[exerciseIndex] = updatedExercise
        }
        
        val isExercisesValid = updatedExercises.isNotEmpty() && updatedExercises.all { it.isValid() }
        
        _state.value = _state.value.copy(
            selectedExercises = updatedExercises,
            isExercisesValid = isExercisesValid,
            isFormValid = _state.value.isWorkoutNameValid && isExercisesValid
        )
    }
    
    /**
     * Saves the workout using the unified workout system
     */
    private fun saveWorkout() {
        val currentState = _state.value
        
        if (!currentState.isFormValid) {
            _state.value = currentState.copy(
                errorMessage = "Please fix validation errors before saving"
            )
            return
        }
        
        _state.value = currentState.copy(isSaving = true)
        
        viewModelScope.launch {
            try {
                // Get current user ID
                val userId = authRepository.getCurrentUserId()
                if (userId == null) {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        errorMessage = "Authentication required. Please log in again."
                    )
                    return@launch
                }
                
                // Map selected exercises to exercise requests
                val exerciseRequests = currentState.selectedExercises.map { selectedExercise ->
                    mapSelectedExerciseToRequest(selectedExercise)
                }
                
                // Create workout request
                val workoutRequest = CreateWorkoutWithExercisesUseCase.CreateWorkoutRequest(
                    userId = userId,
                    name = currentState.workoutName,
                    date = null, // Use current date
                    exerciseRequests = exerciseRequests,
                    notes = currentState.workoutDescription.takeIf { it.isNotBlank() },
                    templateId = null
                )
                
                // Execute use case
                val result = createWorkoutWithExercisesUseCase(workoutRequest)
                
                result.fold(
                    onSuccess = { workout ->
                        Timber.d("Workout created successfully: ${workout.id}")
                        _state.value = _state.value.copy(
                            isSaving = false,
                            showSuccessMessage = true,
                            successMessage = "Workout '${workout.name}' created successfully!"
                        )
                    },
                    onFailure = { error ->
                        Timber.e(error, "Failed to create workout")
                        _state.value = _state.value.copy(
                            isSaving = false,
                            errorMessage = "Failed to create workout: ${error.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error creating workout. Error type: ${e::class.simpleName}, Message: ${e.message}")
                val errorMessage = when {
                    e.message?.contains("exercise_usage_history") == true -> 
                        "Error saving workout progress data. Your workout was created but progress tracking may be affected."
                    e.message?.contains("FOREIGN KEY constraint") == true -> 
                        "Database error: Invalid exercise or user data. Please try again."
                    e.message?.contains("NOT NULL constraint") == true -> 
                        "Database error: Missing required data. Please check all fields are filled."
                    else -> "An unexpected error occurred. Please try again. (${e::class.simpleName})"
                }
                _state.value = _state.value.copy(
                    isSaving = false,
                    errorMessage = errorMessage
                )
            }
        }
    }
    
    /**
     * Maps a SelectedExercise from the UI to an ExerciseRequest for the domain layer
     */
    private fun mapSelectedExerciseToRequest(selectedExercise: SelectedExercise): CreateWorkoutWithExercisesUseCase.ExerciseRequest {
        // Calculate target values from the sets
        val validSets = selectedExercise.sets.filter { it.isValid() }
        val firstValidSet = validSets.firstOrNull()
        
        val targetSets = validSets.size.takeIf { it > 0 }
        val targetReps = firstValidSet?.reps?.toIntOrNull()
        val targetWeight = if (firstValidSet?.isWeightSupported == true) {
            firstValidSet.weight.toDoubleOrNull()?.let { Weight.fromKilograms(it) }
        } else null
        
        return CreateWorkoutWithExercisesUseCase.ExerciseRequest(
            libraryExercise = selectedExercise.libraryExercise,
            targetSets = targetSets,
            targetReps = targetReps,
            targetWeight = targetWeight,
            targetTime = null, // Could be extended for time-based exercises
            targetDistance = null, // Could be extended for distance-based exercises
            notes = null // Could be extended for exercise-specific notes
        )
    }
    
    /**
     * Clears error message
     */
    private fun clearErrorMessage() {
        _state.value = _state.value.copy(errorMessage = null)
    }
    
    /**
     * Clears success message
     */
    private fun clearSuccessMessage() {
        _state.value = _state.value.copy(
            successMessage = null,
            showSuccessMessage = false
        )
    }
    
    /**
     * Updates reps for a specific set
     */
    private fun updateSetReps(exerciseIndex: Int, setIndex: Int, reps: String) {
        val currentExercises = _state.value.selectedExercises
        if (exerciseIndex < 0 || exerciseIndex >= currentExercises.size) return
        
        val exercise = currentExercises[exerciseIndex]
        if (setIndex < 0 || setIndex >= exercise.sets.size) return
        
        val currentSet = exercise.sets[setIndex]
        val repsError = currentSet.validateReps(reps)
        val updatedSet = currentSet.copy(reps = reps, repsError = repsError)
        
        updateSet(exerciseIndex, setIndex, updatedSet)
    }
    
    /**
     * Updates RPE for a specific set
     */
    private fun updateSetRpe(exerciseIndex: Int, setIndex: Int, rpe: String) {
        val currentExercises = _state.value.selectedExercises
        if (exerciseIndex < 0 || exerciseIndex >= currentExercises.size) return
        
        val exercise = currentExercises[exerciseIndex]
        if (setIndex < 0 || setIndex >= exercise.sets.size) return
        
        val currentSet = exercise.sets[setIndex]
        val rpeError = currentSet.validateRpe(rpe)
        val updatedSet = currentSet.copy(rpe = rpe, rpeError = rpeError)
        
        updateSet(exerciseIndex, setIndex, updatedSet)
    }
    
    /**
     * Updates weight for a specific set
     */
    private fun updateSetWeight(exerciseIndex: Int, setIndex: Int, weight: String) {
        val currentExercises = _state.value.selectedExercises
        if (exerciseIndex < 0 || exerciseIndex >= currentExercises.size) return
        
        val exercise = currentExercises[exerciseIndex]
        if (setIndex < 0 || setIndex >= exercise.sets.size) return
        
        val currentSet = exercise.sets[setIndex]
        val weightError = currentSet.validateWeight(weight)
        val updatedSet = currentSet.copy(weight = weight, weightError = weightError)
        
        updateSet(exerciseIndex, setIndex, updatedSet)
    }
    
    /**
     * Clears all messages
     */
    private fun clearMessages() {
        _state.value = _state.value.copy(
            errorMessage = null,
            successMessage = null,
            showSuccessMessage = false
        )
    }
    
    /**
     * Resets the form to initial state
     */
    private fun resetForm() {
        _state.value = WorkoutCreationState.EMPTY.copy(
            availableExercises = _state.value.availableExercises,
            filteredExercises = _state.value.availableExercises
        )
    }
    
    /**
     * Reorders exercises in the list
     */
    private fun reorderExercises(fromIndex: Int, toIndex: Int) {
        val currentExercises = _state.value.selectedExercises
        if (fromIndex < 0 || fromIndex >= currentExercises.size || 
            toIndex < 0 || toIndex >= currentExercises.size) return
        
        val updatedExercises = currentExercises.toMutableList().apply {
            val exercise = removeAt(fromIndex)
            add(toIndex, exercise)
            // Update order indices
            forEachIndexed { index, ex ->
                this[index] = ex.copy(orderIndex = index)
            }
        }
        
        _state.value = _state.value.copy(selectedExercises = updatedExercises)
    }
} 