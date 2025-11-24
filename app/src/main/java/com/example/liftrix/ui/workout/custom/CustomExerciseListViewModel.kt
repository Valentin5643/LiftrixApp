package com.example.liftrix.ui.workout.custom

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.CustomExercise
import com.example.liftrix.domain.model.CustomExerciseId
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.CustomExerciseRepository
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for custom exercise list screen
 * 
 * Manages the list of custom exercises with search, filtering, and sorting capabilities.
 * Provides functionality for loading, deleting, and organizing user's custom exercises.
 */
@HiltViewModel
class CustomExerciseListViewModelImpl @Inject constructor(
    private val customExerciseRepository: CustomExerciseRepository,
    private val authRepository: AuthRepository
) : ModernBaseViewModel<UiState<CustomExerciseListState>>(initialState = UiState.Loading),
    CustomExerciseListViewModel {

    private var allExercises: List<CustomExercise> = emptyList()

    init {
        loadExercises()
    }

    fun handleEvent(event: CustomExerciseListEvent) {
        when (event) {
            is CustomExerciseListEvent.LoadExercises -> loadExercises()
            is CustomExerciseListEvent.UpdateSearchQuery -> updateSearchQuery(event.query)
            is CustomExerciseListEvent.SetSortOption -> setSortOption(event.option)
            is CustomExerciseListEvent.SetFilters -> setFilters(event.filters)
            is CustomExerciseListEvent.ClearFilter -> clearFilter()
            is CustomExerciseListEvent.DeleteExercise -> deleteExercise(event.exerciseId)
        }
    }

    override fun loadExercises() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                setState(UiState.Error(LiftrixError.AuthenticationError(
                    errorMessage = "User not authenticated",
                    analyticsContext = mapOf("operation" to "LOAD_CUSTOM_EXERCISES")
                )))
                return@launch
            }
            
            setState(UiState.Loading)
            try {
                customExerciseRepository.getAllCustomExercises(userId).collectLatest { exercises ->
                    allExercises = exercises
                    val currentState = getCurrentState()?.copy(exercises = exercises) ?: CustomExerciseListState(exercises = exercises)
                    applyFiltersAndSearch(currentState)
                    Timber.d("Successfully loaded ${exercises.size} custom exercises")
                }
            } catch (e: Exception) {
                setState(UiState.Error(LiftrixError.DataRetrievalError(
                    operation = "LOAD_CUSTOM_EXERCISES",
                    analyticsContext = mapOf("userId" to userId)
                )))
            }
        }
    }

    override fun updateSearchQuery(query: String) {
        val currentState = getCurrentState() ?: return
        val newState = currentState.copy(searchQuery = query)
        applyFiltersAndSearch(newState)
    }

    override fun setSortOption(option: CustomExerciseSortOption) {
        val currentState = getCurrentState() ?: return
        val newState = currentState.copy(sortOption = option)
        applyFiltersAndSearch(newState)
    }

    override fun setFilters(filters: CustomExerciseFilters) {
        val currentState = getCurrentState() ?: return
        val newState = currentState.copy(selectedFilters = filters)
        applyFiltersAndSearch(newState)
    }

    override fun clearFilter() {
        val currentState = getCurrentState() ?: return
        val newState = currentState.copy(selectedFilters = CustomExerciseFilters())
        applyFiltersAndSearch(newState)
    }

    override fun deleteExercise(exerciseId: String) {
        viewModelScope.launch {
            try {
                val userId = authRepository.getCurrentUserId()
                if (userId == null) {
                    setState(UiState.Error(
                        LiftrixError.AuthenticationError(
                            errorMessage = "User not authenticated",
                            analyticsContext = mapOf("operation" to "DELETE_CUSTOM_EXERCISE")
                        )
                    ))
                    return@launch
                }

                val result = customExerciseRepository.deleteCustomExercise(userId, CustomExerciseId.fromString(exerciseId))
                result.fold(
                    onSuccess = {
                        Timber.i("Successfully deleted custom exercise: $exerciseId")
                        // Reload exercises to reflect the deletion
                        loadExercises()
                    },
                    onFailure = { error ->
                        Timber.e("Failed to delete custom exercise: $error")
                        logError(error, "deleteExercise")
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Exception during delete exercise")
                logError(e, "deleteExercise")
            }
        }
    }

    private fun applyFiltersAndSearch(state: CustomExerciseListState) {
        var filteredExercises = allExercises

        // Apply search query
        if (state.searchQuery.isNotBlank()) {
            val query = state.searchQuery.lowercase()
            filteredExercises = filteredExercises.filter { exercise ->
                exercise.name.lowercase().contains(query) ||
                exercise.description?.lowercase()?.contains(query) == true ||
                exercise.tags.any { it.lowercase().contains(query) } ||
                exercise.primaryMuscle.name.lowercase().contains(query) ||
                exercise.secondaryMuscles.any { it.name.lowercase().contains(query) }
            }
        }

        // Apply filters
        val filters = state.selectedFilters
        if (filters.exerciseTypes.isNotEmpty()) {
            filteredExercises = filteredExercises.filter { 
                filters.exerciseTypes.contains(it.exerciseType) 
            }
        }
        
        if (filters.muscleGroups.isNotEmpty()) {
            filteredExercises = filteredExercises.filter { exercise ->
                filters.muscleGroups.contains(exercise.primaryMuscle) ||
                exercise.secondaryMuscles.any { filters.muscleGroups.contains(it) }
            }
        }
        
        if (filters.equipment.isNotEmpty()) {
            filteredExercises = filteredExercises.filter { exercise ->
                exercise.equipment?.let { filters.equipment.contains(it) } == true
            }
        }

        if (filters.hasImages != null) {
            filteredExercises = filteredExercises.filter { exercise ->
                val hasImages = !exercise.mainImageUrl.isNullOrBlank() || 
                               exercise.additionalImageUrls.isNotEmpty()
                hasImages == filters.hasImages
            }
        }

        // Apply sorting
        filteredExercises = when (state.sortOption) {
            CustomExerciseSortOption.NAME -> filteredExercises.sortedBy { it.name }
            CustomExerciseSortOption.CREATED_DATE -> filteredExercises.sortedByDescending { it.createdAt }
            CustomExerciseSortOption.LAST_MODIFIED -> filteredExercises.sortedByDescending { it.updatedAt }
            CustomExerciseSortOption.USAGE_COUNT -> filteredExercises.sortedByDescending { 0 } // Would need usage tracking
            CustomExerciseSortOption.EXERCISE_TYPE -> filteredExercises.sortedBy { it.exerciseType.name }
        }

        val newState = state.copy(exercises = filteredExercises, isLoading = false)
        
        // Determine if we should show empty state
        if (filteredExercises.isEmpty() && allExercises.isEmpty()) {
            setState(UiState.Empty(
                message = "No custom exercises yet",
                actionText = "Create Exercise",
                showAction = true
            ))
        } else if (filteredExercises.isEmpty()) {
            setState(UiState.Empty(
                message = if (state.searchQuery.isNotBlank()) {
                    "No exercises found matching your search"
                } else {
                    "No exercises match your filters"
                },
                actionText = "Clear Filters",
                showAction = true
            ))
        } else {
            setState(UiState.Success(newState))
        }
    }

    private fun getCurrentState(): CustomExerciseListState? {
        return when (val currentState = _uiState.value) {
            is UiState.Success -> currentState.data
            else -> null
        }
    }
}

/**
 * Events for custom exercise list
 */
sealed class CustomExerciseListEvent : com.example.liftrix.ui.common.event.ViewModelEvent {
    object LoadExercises : CustomExerciseListEvent()
    data class UpdateSearchQuery(val query: String) : CustomExerciseListEvent()
    data class SetSortOption(val option: CustomExerciseSortOption) : CustomExerciseListEvent()
    data class SetFilters(val filters: CustomExerciseFilters) : CustomExerciseListEvent()
    object ClearFilter : CustomExerciseListEvent()
    data class DeleteExercise(val exerciseId: String) : CustomExerciseListEvent()
}