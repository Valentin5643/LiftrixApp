package com.example.liftrix.ui.workout.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseDefaults
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.TemplateExercise
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.usecase.exercise.GetExerciseDefaultsUseCase
import com.example.liftrix.domain.usecase.exercise.SearchExercisesUseCase
import com.example.liftrix.domain.usecase.exercise.SearchableExercise
import com.example.liftrix.domain.usecase.template.CreateWorkoutTemplateUseCase
import com.example.liftrix.domain.usecase.workout.EstimateWorkoutDurationUseCase
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import com.example.liftrix.domain.repository.FolderRepository
import com.example.liftrix.domain.model.WorkoutTemplateId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the workout template creation screen.
 * 
 * Manages the state for creating a new workout template,
 * including exercise selection, template metadata, and
 * the template creation process.
 */
@HiltViewModel
class WorkoutTemplateCreationViewModel @Inject constructor(
    private val createWorkoutTemplateUseCase: CreateWorkoutTemplateUseCase,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val searchExercisesUseCase: SearchExercisesUseCase,
    private val getExerciseDefaultsUseCase: GetExerciseDefaultsUseCase,
    private val estimateWorkoutDurationUseCase: EstimateWorkoutDurationUseCase,
    private val workoutTemplateRepository: WorkoutTemplateRepository,
    private val folderRepository: FolderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<WorkoutTemplateCreationUiState>(
        WorkoutTemplateCreationUiState.Editing(
            exercises = emptyList(),
            availableExercises = emptyList(),
            exerciseSearchQuery = "",
            selectedExercise = null,
            isExerciseSelectorExpanded = false
        )
    )
    val uiState: StateFlow<WorkoutTemplateCreationUiState> = _uiState.asStateFlow()
    
    private val _loadedTemplate = MutableStateFlow<WorkoutTemplate?>(null)
    val loadedTemplate: StateFlow<WorkoutTemplate?> = _loadedTemplate.asStateFlow()
    
    init {
        loadAvailableExercises()
        ensureDefaultFolderExists()
    }
    
    /**
     * Ensures that a default folder exists for the current user
     */
    private fun ensureDefaultFolderExists() {
        viewModelScope.launch {
            try {
                val userId = getCurrentUserIdUseCase() ?: return@launch
                val result = folderRepository.getOrCreateDefaultFolder(userId)
                if (result.isFailure) {
                    Timber.e(result.exceptionOrNull(), "Failed to create default folder for user $userId")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error ensuring default folder exists")
            }
        }
    }
    
    /**
     * Loads available exercises for selection
     */
    private fun loadAvailableExercises() {
        viewModelScope.launch {
            searchExercisesUseCase.search("", Equipment.values().toSet())
                .catch { error ->
                    Timber.e(error, "Error loading available exercises")
                }
                .collect { exercises ->
                    val currentState = _uiState.value
                    if (currentState is WorkoutTemplateCreationUiState.Editing) {
                        _uiState.value = currentState.copy(availableExercises = exercises)
                    }
                }
        }
    }

    /**
     * Creates a new workout template with the specified parameters
     */
    fun createTemplate(
        name: String,
        description: String? = null,
        exercises: List<TemplateExercise> = emptyList(),
        tags: Set<String> = emptySet()
    ) {
        viewModelScope.launch {
            try {
                _uiState.value = WorkoutTemplateCreationUiState.Loading
                
                val userId = getCurrentUserIdUseCase() 
                    ?: throw IllegalStateException("User not authenticated")
                
                val result = createWorkoutTemplateUseCase(
                    userId = userId,
                    name = name,
                    folderId = "uncategorized_$userId",
                    description = description,
                    exercises = exercises
                )
                
                result.fold(
                    onSuccess = { template ->
                        _uiState.value = WorkoutTemplateCreationUiState.Success(template)
                        Timber.i("Template created successfully: ${template.name}")
                    },
                    onFailure = { error ->
                        val errorMessage = when {
                            error.message?.contains("foreign key constraint", ignoreCase = true) == true ->
                                "Database error: Please try again or restart the app"
                            error.message?.contains("already exists", ignoreCase = true) == true ->
                                "A template with this name already exists"
                            else -> error.message ?: "Failed to create template"
                        }
                        _uiState.value = WorkoutTemplateCreationUiState.Error(errorMessage)
                        Timber.e(error, "Failed to create template: ${error.message}")
                    }
                )
            } catch (exception: Exception) {
                _uiState.value = WorkoutTemplateCreationUiState.Error(
                    exception.message ?: "Unknown error occurred"
                )
                Timber.e(exception, "Error in createTemplate")
            }
        }
    }
    
    /**
     * Updates an existing workout template with the specified parameters
     */
    fun updateTemplate(
        templateId: String,
        name: String,
        description: String? = null,
        exercises: List<TemplateExercise> = emptyList(),
        tags: Set<String> = emptySet()
    ) {
        viewModelScope.launch {
            try {
                _uiState.value = WorkoutTemplateCreationUiState.Loading
                
                val userId = getCurrentUserIdUseCase() 
                    ?: throw IllegalStateException("User not authenticated")
                
                val loadedTemplate = _loadedTemplate.value
                    ?: throw IllegalStateException("No template loaded for editing")
                
                // Create updated template preserving metadata
                val updatedTemplate = loadedTemplate.copy(
                    name = name.trim(),
                    description = description?.trim()?.takeIf { it.isNotBlank() },
                    exercises = exercises.mapIndexed { index, exercise ->
                        exercise.copy(orderIndex = index)
                    },
                    updatedAt = java.time.Instant.now()
                )
                
                val result = workoutTemplateRepository.updateTemplate(updatedTemplate)
                
                result.fold(
                    onSuccess = { template ->
                        _uiState.value = WorkoutTemplateCreationUiState.Success(template)
                        Timber.i("Template updated successfully: ${template.name}")
                    },
                    onFailure = { error ->
                        _uiState.value = WorkoutTemplateCreationUiState.Error(
                            error = error.message ?: "Failed to update template"
                        )
                        Timber.e(error, "Failed to update template")
                    }
                )
            } catch (exception: Exception) {
                _uiState.value = WorkoutTemplateCreationUiState.Error(
                    error = exception.message ?: "Unknown error occurred"
                )
                Timber.e(exception, "Error updating template")
            }
        }
    }
    
    /**
     * Adds an exercise to the template
     */
    fun addExercise(exercise: TemplateExercise) {
        val currentState = _uiState.value
        if (currentState is WorkoutTemplateCreationUiState.Editing) {
            val updatedExercises = currentState.exercises + exercise.copy(
                orderIndex = currentState.exercises.size
            )
            _uiState.value = currentState.copy(exercises = updatedExercises)
        }
    }
    
    /**
     * Adds an exercise from the search selector to the template
     */
    fun addExerciseFromSelector(searchableExercise: SearchableExercise) {
        val templateExercise = convertSearchableExerciseToTemplateExercise(searchableExercise)
        addExercise(templateExercise)
        
        // Clear selection after adding
        val currentState = _uiState.value
        if (currentState is WorkoutTemplateCreationUiState.Editing) {
            _uiState.value = currentState.copy(
                selectedExercise = null,
                exerciseSearchQuery = "",
                isExerciseSelectorExpanded = false
            )
        }
    }
    
    /**
     * Adds an exercise by ID (supports both library and custom exercises)
     */
    fun addExerciseById(exerciseId: String, isCustomExercise: Boolean) {
        viewModelScope.launch {
            try {
                val exercises = searchExercisesUseCase.search("", Equipment.values().toSet()).first()
                val searchableExercise = exercises.find { searchable ->
                    when (searchable) {
                        is SearchableExercise.LibraryExercise -> !isCustomExercise && searchable.exercise.id == exerciseId
                        is SearchableExercise.CustomExercise -> isCustomExercise && searchable.exercise.id.value == exerciseId
                    }
                }
                
                searchableExercise?.let { exercise ->
                    addExerciseFromSelector(exercise)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to add exercise by ID: $exerciseId")
                _uiState.value = WorkoutTemplateCreationUiState.Error("Failed to add exercise: ${e.message}")
            }
        }
    }
    
    /**
     * Adds a custom exercise by ID
     */
    fun addCustomExercise(exerciseId: String) {
        addExerciseById(exerciseId, isCustomExercise = true)
    }
    
    /**
     * Adds an exercise from the exercise library to the template
     */
    fun addExerciseFromLibrary(exerciseLibrary: ExerciseLibrary) {
        timber.log.Timber.d("🔥 TEMPLATE-VIEWMODEL-DEBUG: addExerciseFromLibrary called")
        timber.log.Timber.d("🔥 TEMPLATE-VIEWMODEL-DEBUG: Exercise: ${exerciseLibrary.name} (ID: ${exerciseLibrary.id})")
        
        try {
            val templateExercise = convertExerciseLibraryToTemplateExercise(exerciseLibrary)
            timber.log.Timber.d("🔥 TEMPLATE-VIEWMODEL-DEBUG: Converted to TemplateExercise: ${templateExercise.name}")
            
            addExercise(templateExercise)
            timber.log.Timber.d("🔥 TEMPLATE-VIEWMODEL-DEBUG: addExercise completed successfully")
            
            // Debug current state
            val currentState = _uiState.value
            if (currentState is WorkoutTemplateCreationUiState.Editing) {
                timber.log.Timber.d("🔥 TEMPLATE-VIEWMODEL-DEBUG: Current template exercises count: ${currentState.exercises.size}")
                timber.log.Timber.d("🔥 TEMPLATE-VIEWMODEL-DEBUG: Template exercises: ${currentState.exercises.map { it.name }}")
            }
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "🔥 TEMPLATE-VIEWMODEL-DEBUG: Error in addExerciseFromLibrary")
        }
    }
    
    /**
     * Converts ExerciseLibrary to TemplateExercise with smart defaults applied
     */
    private fun convertExerciseLibraryToTemplateExercise(exerciseLibrary: ExerciseLibrary): TemplateExercise {
        val baseExercise = TemplateExercise(
            exerciseId = ExerciseId.fromString(exerciseLibrary.id),
            name = exerciseLibrary.name,
            primaryMuscle = exerciseLibrary.primaryMuscleGroup,
            equipment = exerciseLibrary.equipment,
            targetSets = null,
            targetReps = null,
            targetWeight = null,
            restTimeSeconds = null,
            notes = null,
            orderIndex = 0, // Will be updated when added
            isCustomExercise = false,
            customExerciseId = null
        )
        
        // Apply smart defaults asynchronously
        applySmartDefaults(baseExercise, exerciseLibrary)
        
        return baseExercise
    }
    
    /**
     * Applies smart defaults to a template exercise based on user history and exercise type
     */
    private fun applySmartDefaults(templateExercise: TemplateExercise, exerciseLibrary: ExerciseLibrary) {
        viewModelScope.launch {
            try {
                val userId = getCurrentUserIdUseCase() ?: return@launch
                
                val defaultsResult = getExerciseDefaultsUseCase(
                    exerciseId = templateExercise.exerciseId,
                    userId = userId,
                    exerciseLibrary = exerciseLibrary
                )
                
                defaultsResult.fold(
                    onSuccess = { defaults ->
                        updateExerciseWithDefaults(templateExercise.exerciseId, defaults)
                        updateEstimatedDuration()
                        Timber.d("Applied smart defaults for ${exerciseLibrary.name}: ${defaults.getSourceDescription()}")
                    },
                    onFailure = { error ->
                        Timber.w(error, "Failed to get smart defaults for ${exerciseLibrary.name}")
                        // Exercise will keep the null defaults
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Error applying smart defaults for ${exerciseLibrary.name}")
            }
        }
    }
    
    /**
     * Updates an exercise with smart defaults
     */
    private fun updateExerciseWithDefaults(exerciseId: ExerciseId, defaults: ExerciseDefaults) {
        val currentState = _uiState.value
        if (currentState is WorkoutTemplateCreationUiState.Editing) {
            val updatedExercises = currentState.exercises.map { exercise ->
                if (exercise.exerciseId == exerciseId) {
                    defaults.applyToTemplateExercise(exercise)
                } else {
                    exercise
                }
            }
            _uiState.value = currentState.copy(exercises = updatedExercises)
        }
    }
    
    /**
     * Updates the estimated duration for the current template
     */
    private fun updateEstimatedDuration() {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                if (currentState is WorkoutTemplateCreationUiState.Editing && currentState.exercises.isNotEmpty()) {
                    // Create a temporary template for duration estimation
                    val tempTemplate = WorkoutTemplate.create(
                        userId = getCurrentUserIdUseCase() ?: "",
                        name = "Temporary",
                        folderId = "temp",
                        exercises = currentState.exercises
                    )
                    
                    val durationResult = estimateWorkoutDurationUseCase(tempTemplate)
                    durationResult.fold(
                        onSuccess = { duration ->
                            val minutes = duration.toMinutes().toInt()
                            Timber.d("Updated estimated duration: ${minutes} minutes")
                            // Could update UI state with estimated duration if needed
                        },
                        onFailure = { error ->
                            Timber.w(error, "Failed to estimate workout duration")
                        }
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error updating estimated duration")
            }
        }
    }
    
    /**
     * Converts SearchableExercise to TemplateExercise
     */
    private fun convertSearchableExerciseToTemplateExercise(searchableExercise: SearchableExercise): TemplateExercise {
        return when (searchableExercise) {
            is SearchableExercise.LibraryExercise -> {
                val exercise = searchableExercise.exercise
                TemplateExercise(
                    exerciseId = ExerciseId.fromString(exercise.id),
                    name = exercise.name,
                    primaryMuscle = exercise.primaryMuscleGroup,
                    equipment = exercise.equipment,
                    targetSets = null,
                    targetReps = null,
                    targetWeight = null,
                    restTimeSeconds = null,
                    notes = null,
                    orderIndex = 0, // Will be updated when added
                    isCustomExercise = false,
                    customExerciseId = null
                )
            }
            is SearchableExercise.CustomExercise -> {
                val exercise = searchableExercise.exercise
                TemplateExercise(
                    exerciseId = ExerciseId.fromString(exercise.id.value),
                    name = exercise.name,
                    primaryMuscle = exercise.primaryMuscle,
                    equipment = exercise.equipment,
                    targetSets = null,
                    targetReps = null,
                    targetWeight = null,
                    restTimeSeconds = null,
                    notes = exercise.notes,
                    orderIndex = 0, // Will be updated when added
                    isCustomExercise = true,
                    customExerciseId = exercise.id
                )
            }
        }
    }
    
    /**
     * Updates exercise search query
     */
    fun onExerciseSearchQueryChanged(query: String) {
        val currentState = _uiState.value
        if (currentState is WorkoutTemplateCreationUiState.Editing) {
            _uiState.value = currentState.copy(exerciseSearchQuery = query)
            
            // Perform search
            viewModelScope.launch {
                searchExercisesUseCase.search(query, Equipment.values().toSet())
                    .catch { error ->
                        Timber.e(error, "Error searching exercises")
                    }
                    .collect { exercises ->
                        val updatedState = _uiState.value
                        if (updatedState is WorkoutTemplateCreationUiState.Editing) {
                            _uiState.value = updatedState.copy(availableExercises = exercises)
                        }
                    }
            }
        }
    }
    
    /**
     * Updates selected exercise
     */
    fun onExerciseSelected(exercise: SearchableExercise?) {
        val currentState = _uiState.value
        if (currentState is WorkoutTemplateCreationUiState.Editing) {
            _uiState.value = currentState.copy(selectedExercise = exercise)
        }
    }
    
    /**
     * Updates exercise selector expanded state
     */
    fun onExerciseSelectorExpandedChanged(expanded: Boolean) {
        val currentState = _uiState.value
        if (currentState is WorkoutTemplateCreationUiState.Editing) {
            _uiState.value = currentState.copy(isExerciseSelectorExpanded = expanded)
        }
    }
    
    /**
     * Removes an exercise from the template
     */
    fun removeExercise(exercise: TemplateExercise) {
        val currentState = _uiState.value
        if (currentState is WorkoutTemplateCreationUiState.Editing) {
            val updatedExercises = currentState.exercises
                .filter { it.exerciseId != exercise.exerciseId }
                .mapIndexed { index, ex -> ex.copy(orderIndex = index) }
            _uiState.value = currentState.copy(exercises = updatedExercises)
        }
    }
    
    /**
     * Reorders exercises in the template
     */
    fun reorderExercises(fromIndex: Int, toIndex: Int) {
        val currentState = _uiState.value
        if (currentState is WorkoutTemplateCreationUiState.Editing) {
            val exercises = currentState.exercises.toMutableList()
            if (fromIndex in exercises.indices && toIndex in exercises.indices) {
                val item = exercises.removeAt(fromIndex)
                exercises.add(toIndex, item)
                val reorderedExercises = exercises.mapIndexed { index, exercise ->
                    exercise.copy(orderIndex = index)
                }
                _uiState.value = currentState.copy(exercises = reorderedExercises)
            }
        }
    }
    
    /**
     * Updates an exercise in the template
     */
    fun updateExercise(updatedExercise: TemplateExercise) {
        val currentState = _uiState.value
        if (currentState is WorkoutTemplateCreationUiState.Editing) {
            val updatedExercises = currentState.exercises.map { exercise ->
                if (exercise.exerciseId == updatedExercise.exerciseId) {
                    updatedExercise
                } else {
                    exercise
                }
            }
            _uiState.value = currentState.copy(exercises = updatedExercises)
        }
    }
    
    /**
     * Resets the creation state back to editing
     */
    fun resetToEditing() {
        val currentState = _uiState.value
        val exercises = when (currentState) {
            is WorkoutTemplateCreationUiState.Editing -> currentState.exercises
            is WorkoutTemplateCreationUiState.Error -> currentState.exercises
            else -> emptyList()
        }
        _uiState.value = WorkoutTemplateCreationUiState.Editing(
            exercises = exercises,
            availableExercises = emptyList(),
            exerciseSearchQuery = "",
            selectedExercise = null,
            isExerciseSelectorExpanded = false
        )
        loadAvailableExercises()
    }
    
    /**
     * Validates if the current template state is valid for creation
     */
    fun isValidForCreation(name: String): Boolean {
        val currentState = _uiState.value
        return name.isNotBlank() && 
               name.length <= WorkoutTemplate.MAX_NAME_LENGTH &&
               currentState.exercises.isNotEmpty()
    }
    
    /**
     * Loads an existing template for editing
     */
    fun loadTemplateForEditing(templateId: String) {
        viewModelScope.launch {
            try {
                val userId = getCurrentUserIdUseCase() ?: return@launch
                val template = workoutTemplateRepository.getTemplateById(
                    WorkoutTemplateId(templateId), 
                    userId
                )
                
                if (template != null) {
                    _loadedTemplate.value = template
                    _uiState.value = WorkoutTemplateCreationUiState.Editing(
                        exercises = template.exercises,
                        availableExercises = _uiState.value.availableExercises,
                        exerciseSearchQuery = "",
                        selectedExercise = null,
                        isExerciseSelectorExpanded = false
                    )
                } else {
                    Timber.e("Template not found for ID: $templateId")
                    _uiState.value = WorkoutTemplateCreationUiState.Error(
                        error = "Template not found"
                    )
                }
            } catch (exception: Exception) {
                Timber.e(exception, "Error loading template for editing")
                _uiState.value = WorkoutTemplateCreationUiState.Error(
                    error = exception.message ?: "Failed to load template"
                )
            }
        }
    }
}

/**
 * UI state for template creation
 */
sealed class WorkoutTemplateCreationUiState {
    abstract val exercises: List<TemplateExercise>
    abstract val availableExercises: List<SearchableExercise>
    abstract val exerciseSearchQuery: String
    abstract val selectedExercise: SearchableExercise?
    abstract val isExerciseSelectorExpanded: Boolean
    
    data class Editing(
        override val exercises: List<TemplateExercise>,
        override val availableExercises: List<SearchableExercise> = emptyList(),
        override val exerciseSearchQuery: String = "",
        override val selectedExercise: SearchableExercise? = null,
        override val isExerciseSelectorExpanded: Boolean = false
    ) : WorkoutTemplateCreationUiState()
    
    object Loading : WorkoutTemplateCreationUiState() {
        override val exercises: List<TemplateExercise> = emptyList()
        override val availableExercises: List<SearchableExercise> = emptyList()
        override val exerciseSearchQuery: String = ""
        override val selectedExercise: SearchableExercise? = null
        override val isExerciseSelectorExpanded: Boolean = false
    }
    
    data class Success(
        val template: WorkoutTemplate
    ) : WorkoutTemplateCreationUiState() {
        override val exercises: List<TemplateExercise> = template.exercises
        override val availableExercises: List<SearchableExercise> = emptyList()
        override val exerciseSearchQuery: String = ""
        override val selectedExercise: SearchableExercise? = null
        override val isExerciseSelectorExpanded: Boolean = false
    }
    
    data class Error(
        val error: String,
        override val exercises: List<TemplateExercise> = emptyList(),
        override val availableExercises: List<SearchableExercise> = emptyList(),
        override val exerciseSearchQuery: String = "",
        override val selectedExercise: SearchableExercise? = null,
        override val isExerciseSelectorExpanded: Boolean = false
    ) : WorkoutTemplateCreationUiState()
}