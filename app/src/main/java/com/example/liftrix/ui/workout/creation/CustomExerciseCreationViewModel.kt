package com.example.liftrix.ui.workout.creation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.CustomExercise
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.usecase.exercise.CreateCustomExerciseInput
import com.example.liftrix.domain.usecase.exercise.CreateCustomExerciseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for custom exercise creation
 */
data class CustomExerciseCreationUiState(
    val name: String = "",
    val nameError: String? = null,
    val primaryMuscle: ExerciseCategory? = null,
    val primaryMuscleError: String? = null,
    val equipment: Equipment? = null,
    val equipmentError: String? = null,
    val secondaryMuscles: Set<ExerciseCategory> = emptySet(),
    val difficulty: Int? = null,
    val difficultyError: String? = null,
    val notes: String = "",
    val notesError: String? = null,
    val isLoading: Boolean = false,
    val isFormValid: Boolean = false,
    val showSuccessMessage: Boolean = false,
    val errorMessage: String? = null,
    val isCreationSuccessful: Boolean = false,
    val createdExercise: CustomExercise? = null
) {
    companion object {
        val EMPTY = CustomExerciseCreationUiState()
    }
}

/**
 * Events for custom exercise creation
 */
sealed class CustomExerciseCreationEvent {
    data class UpdateName(val name: String) : CustomExerciseCreationEvent()
    data class UpdatePrimaryMuscle(val muscle: ExerciseCategory) : CustomExerciseCreationEvent()
    data class UpdateEquipment(val equipment: Equipment) : CustomExerciseCreationEvent()
    data class UpdateSecondaryMuscles(val muscles: Set<ExerciseCategory>) : CustomExerciseCreationEvent()
    data class UpdateDifficulty(val difficulty: Int?) : CustomExerciseCreationEvent()
    data class UpdateNotes(val notes: String) : CustomExerciseCreationEvent()
    object CreateExercise : CustomExerciseCreationEvent()
    object ClearMessages : CustomExerciseCreationEvent()
    object ResetForm : CustomExerciseCreationEvent()
}

/**
 * ViewModel for custom exercise creation with MVI pattern and form validation
 */
@HiltViewModel
class CustomExerciseCreationViewModel @Inject constructor(
    private val createCustomExerciseUseCase: CreateCustomExerciseUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(CustomExerciseCreationUiState.EMPTY)
    val uiState: StateFlow<CustomExerciseCreationUiState> = _uiState.asStateFlow()
    
    /**
     * Handle UI events
     */
    fun onEvent(event: CustomExerciseCreationEvent) {
        when (event) {
            is CustomExerciseCreationEvent.UpdateName -> updateName(event.name)
            is CustomExerciseCreationEvent.UpdatePrimaryMuscle -> updatePrimaryMuscle(event.muscle)
            is CustomExerciseCreationEvent.UpdateEquipment -> updateEquipment(event.equipment)
            is CustomExerciseCreationEvent.UpdateSecondaryMuscles -> updateSecondaryMuscles(event.muscles)
            is CustomExerciseCreationEvent.UpdateDifficulty -> updateDifficulty(event.difficulty)
            is CustomExerciseCreationEvent.UpdateNotes -> updateNotes(event.notes)
            is CustomExerciseCreationEvent.CreateExercise -> createExercise()
            is CustomExerciseCreationEvent.ClearMessages -> clearMessages()
            is CustomExerciseCreationEvent.ResetForm -> resetForm()
        }
    }
    
    private fun updateName(name: String) {
        val trimmedName = name.take(CreateCustomExerciseInput.MAX_NAME_LENGTH)
        val nameError = validateName(trimmedName)
        
        _uiState.value = _uiState.value.copy(
            name = trimmedName,
            nameError = nameError,
            isFormValid = validateForm(_uiState.value.copy(name = trimmedName, nameError = nameError))
        )
    }
    
    private fun updatePrimaryMuscle(muscle: ExerciseCategory) {
        val primaryMuscleError = if (muscle == ExerciseCategory.CARDIO && _uiState.value.secondaryMuscles.isNotEmpty()) {
            "Cardio exercises should not have secondary muscle groups"
        } else null
        
        val updatedSecondaryMuscles = if (muscle == ExerciseCategory.CARDIO) {
            emptySet()
        } else {
            _uiState.value.secondaryMuscles.filter { it != muscle }.toSet()
        }
        
        _uiState.value = _uiState.value.copy(
            primaryMuscle = muscle,
            primaryMuscleError = primaryMuscleError,
            secondaryMuscles = updatedSecondaryMuscles,
            isFormValid = validateForm(_uiState.value.copy(
                primaryMuscle = muscle,
                primaryMuscleError = primaryMuscleError,
                secondaryMuscles = updatedSecondaryMuscles
            ))
        )
    }
    
    private fun updateEquipment(equipment: Equipment) {
        _uiState.value = _uiState.value.copy(
            equipment = equipment,
            equipmentError = null,
            isFormValid = validateForm(_uiState.value.copy(equipment = equipment, equipmentError = null))
        )
    }
    
    private fun updateSecondaryMuscles(muscles: Set<ExerciseCategory>) {
        val filteredMuscles = muscles.filter { it != _uiState.value.primaryMuscle }.toSet()
        val secondaryMusclesError = when {
            _uiState.value.primaryMuscle == ExerciseCategory.CARDIO && filteredMuscles.isNotEmpty() -> {
                "Cardio exercises should not have secondary muscle groups"
            }
            filteredMuscles.size > MAX_SECONDARY_MUSCLES -> {
                "Cannot have more than $MAX_SECONDARY_MUSCLES secondary muscle groups"
            }
            else -> null
        }
        
        _uiState.value = _uiState.value.copy(
            secondaryMuscles = if (secondaryMusclesError == null) filteredMuscles else _uiState.value.secondaryMuscles,
            isFormValid = validateForm(_uiState.value.copy(secondaryMuscles = filteredMuscles))
        )
    }
    
    private fun updateDifficulty(difficulty: Int?) {
        val difficultyError = difficulty?.let { level ->
            if (level !in CreateCustomExerciseInput.MIN_DIFFICULTY..CreateCustomExerciseInput.MAX_DIFFICULTY) {
                "Difficulty must be between ${CreateCustomExerciseInput.MIN_DIFFICULTY} and ${CreateCustomExerciseInput.MAX_DIFFICULTY}"
            } else null
        }
        
        _uiState.value = _uiState.value.copy(
            difficulty = difficulty,
            difficultyError = difficultyError,
            isFormValid = validateForm(_uiState.value.copy(difficulty = difficulty, difficultyError = difficultyError))
        )
    }
    
    private fun updateNotes(notes: String) {
        val trimmedNotes = notes.take(CreateCustomExerciseInput.MAX_NOTES_LENGTH)
        val notesError = if (trimmedNotes.length > CreateCustomExerciseInput.MAX_NOTES_LENGTH) {
            "Notes cannot exceed ${CreateCustomExerciseInput.MAX_NOTES_LENGTH} characters"
        } else null
        
        _uiState.value = _uiState.value.copy(
            notes = trimmedNotes,
            notesError = notesError,
            isFormValid = validateForm(_uiState.value.copy(notes = trimmedNotes, notesError = notesError))
        )
    }
    
    private fun createExercise() {
        val currentState = _uiState.value
        
        if (!currentState.isFormValid) {
            _uiState.value = currentState.copy(
                errorMessage = "Please fix the form errors before creating the exercise"
            )
            return
        }
        
        _uiState.value = currentState.copy(isLoading = true, errorMessage = null)
        
        viewModelScope.launch {
            try {
                val input = CreateCustomExerciseInput(
                    name = currentState.name.trim(),
                    primaryMuscle = currentState.primaryMuscle!!,
                    equipment = currentState.equipment!!,
                    secondaryMuscles = currentState.secondaryMuscles,
                    difficulty = currentState.difficulty,
                    notes = currentState.notes.trim().takeIf { it.isNotBlank() }
                )
                
                createCustomExerciseUseCase(input).fold(
                    onSuccess = { customExercise ->
                        _uiState.value = currentState.copy(
                            isLoading = false,
                            showSuccessMessage = true,
                            errorMessage = null,
                            isCreationSuccessful = true,
                            createdExercise = customExercise
                        )
                    },
                    onFailure = { exception ->
                        _uiState.value = currentState.copy(
                            isLoading = false,
                            errorMessage = exception.message ?: "Failed to create custom exercise"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = currentState.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "An unexpected error occurred"
                )
            }
        }
    }
    
    private fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            showSuccessMessage = false
        )
    }
    
    /**
     * Handle successful creation - clears success state after navigation
     */
    fun handleCreationSuccess() {
        _uiState.value = _uiState.value.copy(
            isCreationSuccessful = false,
            createdExercise = null
        )
    }
    
    private fun resetForm() {
        _uiState.value = CustomExerciseCreationUiState.EMPTY
    }
    
    private fun validateName(name: String): String? {
        return when {
            name.isBlank() -> "Exercise name is required"
            name.length < MIN_NAME_LENGTH -> "Exercise name must be at least $MIN_NAME_LENGTH characters"
            name.length > CreateCustomExerciseInput.MAX_NAME_LENGTH -> "Exercise name cannot exceed ${CreateCustomExerciseInput.MAX_NAME_LENGTH} characters"
            else -> null
        }
    }
    
    private fun validateForm(state: CustomExerciseCreationUiState): Boolean {
        return state.nameError == null &&
               state.primaryMuscleError == null &&
               state.equipmentError == null &&
               state.difficultyError == null &&
               state.notesError == null &&
               state.name.isNotBlank() &&
               state.primaryMuscle != null &&
               state.equipment != null
    }
    
    companion object {
        private const val MIN_NAME_LENGTH = 2
        private const val MAX_SECONDARY_MUSCLES = 3
    }
} 