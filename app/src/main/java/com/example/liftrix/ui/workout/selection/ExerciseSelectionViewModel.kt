package com.example.liftrix.ui.workout.selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.repository.ExerciseLibraryRepository
import com.example.liftrix.domain.usecase.exercise.SearchExercisesUseCase
import com.example.liftrix.domain.usecase.exercise.SearchableExercise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber

/**
 * UI state for exercise selection screen
 */
data class ExerciseSelectionUiState(
    val allExercises: List<SearchableExercise> = emptyList(),
    val filteredExercises: List<SearchableExercise> = emptyList(),
    val recentExercises: List<SearchableExercise> = emptyList(),
    val searchQuery: String = "",
    val selectedEquipment: Set<Equipment> = emptySet(),
    val selectedMuscleGroups: Set<ExerciseCategory> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * ViewModel for exercise selection screen with comprehensive filtering
 * Manages search, equipment filters, muscle group filters, and recent exercises
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class ExerciseSelectionViewModel @Inject constructor(
    private val exerciseLibraryRepository: ExerciseLibraryRepository,
    private val searchExercisesUseCase: SearchExercisesUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ExerciseSelectionUiState())
    val uiState: StateFlow<ExerciseSelectionUiState> = _uiState.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    private val _selectedEquipment = MutableStateFlow<Set<Equipment>>(emptySet())
    private val _selectedMuscleGroups = MutableStateFlow<Set<ExerciseCategory>>(emptySet())
    
    init {
        // CRITICAL FIX: Ensure database is populated immediately
        viewModelScope.launch {
            try {
                // Trigger database population proactively
                exerciseLibraryRepository.getAllExercises().first()
                Timber.d("ExerciseSelectionViewModel: Database population check completed")
            } catch (e: Exception) {
                Timber.e(e, "ExerciseSelectionViewModel: Error during database check")
            }
        }
        
        // Load recent exercises (placeholder - would typically come from usage history)
        loadRecentExercises()
        
        // Set up filtered exercise flow with debounced search using SearchExercisesUseCase
        combine(
            _searchQuery.debounce(300).distinctUntilChanged(),
            _selectedEquipment,
            _selectedMuscleGroups
        ) { searchQuery, equipment, muscleGroups ->
            FilterParams(searchQuery, equipment, muscleGroups)
        }
        .onStart { emit(FilterParams("", emptySet(), emptySet())) }
        .flatMapLatest { params ->
            searchExercisesUseCase.search(params.searchQuery, params.selectedEquipment)
        }
        .onEach { searchResults ->
            val filteredByMuscleGroup = if (_selectedMuscleGroups.value.isNotEmpty()) {
                searchResults.filter { searchableExercise ->
                    when (searchableExercise) {
                        is SearchableExercise.LibraryExercise -> {
                            val exercise = searchableExercise.exercise
                            _selectedMuscleGroups.value.contains(exercise.primaryMuscleGroup) ||
                            exercise.secondaryMuscleGroups.any { secondary -> 
                                _selectedMuscleGroups.value.contains(secondary) 
                            }
                        }
                        is SearchableExercise.CustomExercise -> {
                            val exercise = searchableExercise.exercise
                            _selectedMuscleGroups.value.contains(exercise.primaryMuscle)
                        }
                    }
                }
            } else {
                searchResults
            }
            
            Timber.d("ExerciseSelectionViewModel: searchResults=${searchResults.size}, filtered=${filteredByMuscleGroup.size}")
            Timber.d("ExerciseSelectionViewModel: query='${_searchQuery.value}', equipment=${_selectedEquipment.value.size}, muscleGroups=${_selectedMuscleGroups.value.size}")
            
            _uiState.update { currentState ->
                currentState.copy(
                    allExercises = searchResults,
                    filteredExercises = filteredByMuscleGroup,
                    searchQuery = _searchQuery.value,
                    isLoading = false,
                    error = null // Clear any previous errors
                )
            }
        }
        .catch { e: Throwable ->
            Timber.e(e, "ExerciseSelectionViewModel: Error in exercise search flow")
            _uiState.update { currentState ->
                currentState.copy(
                    isLoading = false,
                    error = "Failed to load exercises: ${e.message ?: "Unknown error"}"
                )
            }
        }
        .launchIn(viewModelScope)
    }
    
    /**
     * Update search query with debouncing
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    /**
     * Update selected equipment filters
     */
    fun updateSelectedEquipment(equipment: Set<Equipment>) {
        _selectedEquipment.value = equipment
        _uiState.update { it.copy(selectedEquipment = equipment) }
    }
    
    /**
     * Update selected muscle group filters  
     */
    fun updateSelectedMuscleGroups(muscleGroups: Set<ExerciseCategory>) {
        _selectedMuscleGroups.value = muscleGroups
        _uiState.update { it.copy(selectedMuscleGroups = muscleGroups) }
    }
    
    
    /**
     * Load recent exercises - placeholder implementation
     * In a real app, this would come from exercise usage history
     */
    private fun loadRecentExercises() {
        viewModelScope.launch {
            try {
                Timber.d("ExerciseSelectionViewModel: Loading recent exercises...")
                // Get a sample of exercises for recent list
                val allExercises = searchExercisesUseCase.search("", Equipment.entries.toSet()).first()
                val recentExercises = allExercises.take(5)
                Timber.d("ExerciseSelectionViewModel: Loaded ${recentExercises.size} recent exercises from ${allExercises.size} total")
                _uiState.update { currentState ->
                    currentState.copy(recentExercises = recentExercises)
                }
            } catch (e: Exception) {
                Timber.e(e, "ExerciseSelectionViewModel: Failed to load recent exercises")
                _uiState.update { currentState ->
                    currentState.copy(error = "Failed to load recent exercises: ${e.message ?: "Unknown error"}")
                }
            }
        }
    }
    
    /**
     * Clear all filters and search
     */
    fun clearAllFilters() {
        _searchQuery.value = ""
        _selectedEquipment.value = emptySet()
        _selectedMuscleGroups.value = emptySet()
        _uiState.update { currentState ->
            currentState.copy(
                searchQuery = "",
                selectedEquipment = emptySet(),
                selectedMuscleGroups = emptySet()
            )
        }
    }
}

/**
 * Data class for filter parameters
 */
private data class FilterParams(
    val searchQuery: String,
    val selectedEquipment: Set<Equipment>,
    val selectedMuscleGroups: Set<ExerciseCategory>
)