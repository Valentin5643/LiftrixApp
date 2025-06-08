package com.example.liftrix.ui.workout.creation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.usecase.exercise.ExerciseGroup
import com.example.liftrix.domain.usecase.exercise.SearchExercisesUseCase
import com.example.liftrix.domain.usecase.exercise.SearchableExercise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject

/**
 * UI state for exercise selection screen
 */
data class ExerciseSelectionUiState(
    val exercises: List<SearchableExercise> = emptyList(),
    val exerciseGroups: List<ExerciseGroup> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val selectedEquipment: Set<Equipment> = Equipment.values().toSet(),
    val isEquipmentFilterExpanded: Boolean = false
)

/**
 * ViewModel for exercise selection with search and variations functionality
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class ExerciseSelectionViewModel @Inject constructor(
    private val searchExercisesUseCase: SearchExercisesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExerciseSelectionUiState())
    val uiState: StateFlow<ExerciseSelectionUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        setupSearchFlow()
    }

    /**
     * Sets up the reactive search flow with debouncing
     */
    private fun setupSearchFlow() {
        _searchQuery
            .debounce(300) // Debounce search queries by 300ms
            .distinctUntilChanged()
            .flatMapLatest { query: String ->
                if (query.isBlank()) {
                    flowOf(emptyList<SearchableExercise>())
                } else {
                    searchExercisesUseCase.search(
                        query = query,
                        userEquipment = _uiState.value.selectedEquipment
                    )
                        .onStart { 
                            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                        }
                        .catch { exception ->
                            Timber.e(exception, "Error searching exercises")
                            _uiState.update { 
                                it.copy(
                                    isLoading = false, 
                                    errorMessage = "Failed to search exercises. Please try again."
                                )
                            }
                        }
                }
            }
            .onEach { exercises: List<SearchableExercise> ->
                val exerciseGroups = groupExercisesByMovement(exercises)
                _uiState.update { 
                    it.copy(
                        exercises = exercises,
                        exerciseGroups = exerciseGroups,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Triggers a search for exercises
     * 
     * @param query The search query
     */
    fun searchExercises(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        _searchQuery.value = query
    }

    /**
     * Clears the current error message
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Refreshes the exercise list (useful after custom exercise creation)
     */
    fun refreshExerciseList() {
        val currentQuery = _uiState.value.searchQuery
        if (currentQuery.isNotBlank()) {
            searchExercises(currentQuery)
        }
    }

    /**
     * Groups exercises by movement pattern for variation display
     */
    private fun groupExercisesByMovement(exercises: List<SearchableExercise>): List<ExerciseGroup> {
        val groupedByMovement = exercises.groupBy { it.movementPattern }
        
        return groupedByMovement.map { (movementPattern, exerciseList) ->
            val libraryExercises = exerciseList.filterIsInstance<SearchableExercise.LibraryExercise>()
                .map { it.exercise }
            val customExercises = exerciseList.filterIsInstance<SearchableExercise.CustomExercise>()
                .map { it.exercise }
            
            ExerciseGroup(
                movementPattern = movementPattern,
                libraryVariations = libraryExercises.sortedBy { it.equipment.displayName },
                customVariations = customExercises
            )
        }.sortedBy { it.movementPattern }
    }

    /**
     * Updates the selected equipment filter
     */
    fun updateEquipmentFilter(equipment: Set<Equipment>) {
        _uiState.update { 
            it.copy(selectedEquipment = equipment) 
        }
        // Re-trigger search with new equipment filter
        val currentQuery = _uiState.value.searchQuery
        if (currentQuery.isNotBlank()) {
            searchExercises(currentQuery)
        }
    }

    /**
     * Toggles equipment filter expansion
     */
    fun toggleEquipmentFilter() {
        _uiState.update { 
            it.copy(isEquipmentFilterExpanded = !it.isEquipmentFilterExpanded) 
        }
    }

    /**
     * Filters exercises by equipment (utility function)
     */
    fun filterByEquipment(exercises: List<SearchableExercise>): List<SearchableExercise> {
        val selectedEquipment = _uiState.value.selectedEquipment
        return exercises.filter { exercise: SearchableExercise ->
            selectedEquipment.contains(exercise.equipment)
        }
    }
} 