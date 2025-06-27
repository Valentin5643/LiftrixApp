package com.example.liftrix.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.repository.ExerciseLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for managing exercise-related operations
 * Follows MVI pattern with reactive state management
 */
@HiltViewModel
class ExerciseViewModel @Inject constructor(
    private val exerciseLibraryRepository: ExerciseLibraryRepository
) : ViewModel() {

    private val _uiState: MutableStateFlow<ExerciseUiState> = MutableStateFlow(ExerciseUiState())
    val uiState: StateFlow<ExerciseUiState> = _uiState.asStateFlow()

    /**
     * Loads exercises from the library
     */
    fun loadExercises() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                exerciseLibraryRepository.getAllExercises()
                    .collect { exerciseLibraries ->
                        val exercises = exerciseLibraries.map { it.toExercise(WorkoutId.generate(), 0) }
                        _uiState.value = _uiState.value.copy(
                            exercises = exercises,
                            isLoading = false
                        )
                    }
            } catch (exception: Exception) {
                Timber.e(exception, "Failed to load exercises")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load exercises: ${exception.message}"
                )
            }
        }
    }

    /**
     * Searches exercises by query
     */
    fun searchExercises(query: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                exerciseLibraryRepository.searchExercises(query)
                    .collect { exerciseLibraries ->
                        val exercises = exerciseLibraries.map { it.toExercise(WorkoutId.generate(), 0) }
                        _uiState.value = _uiState.value.copy(
                            exercises = exercises,
                            isLoading = false,
                            searchQuery = query
                        )
                    }
            } catch (exception: Exception) {
                Timber.e(exception, "Failed to search exercises")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to search exercises: ${exception.message}"
                )
            }
        }
    }

    /**
     * Clears the current error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

/**
 * UI state for exercise management
 */
data class ExerciseUiState(
    val exercises: List<com.example.liftrix.domain.model.Exercise> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = ""
) 