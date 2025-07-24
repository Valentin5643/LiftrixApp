package com.example.liftrix.ui.workout.create

import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.state.WorkoutTemplateCreationUiState
import com.example.liftrix.ui.common.state.WorkoutTemplateCreationData
import com.example.liftrix.ui.common.state.dataOrNull
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.domain.usecase.common.ErrorHandler
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseDefaults
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.TemplateExercise
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.usecase.auth.GetAuthenticatedUserIdUseCase
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
import com.example.liftrix.ui.common.state.dataOrNull

/**
 * ViewModel for the workout creation screen.
 * 
 * Manages the state for creating a new workout routine,
 * including exercise selection, workout metadata, and
 * the workout routine creation process.
 * 
 * Uses BaseViewModel<S,E> pattern with workflow-based terminology
 * for user-friendly language ("Creating a workout" vs technical "template").
 */
@HiltViewModel
class WorkoutTemplateCreationViewModel @Inject constructor(
    private val createWorkoutTemplateUseCase: CreateWorkoutTemplateUseCase,
    private val getAuthenticatedUserIdUseCase: GetAuthenticatedUserIdUseCase,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val searchExercisesUseCase: SearchExercisesUseCase,
    private val getExerciseDefaultsUseCase: GetExerciseDefaultsUseCase,
    private val estimateWorkoutDurationUseCase: EstimateWorkoutDurationUseCase,
    private val workoutTemplateRepository: WorkoutTemplateRepository,
    private val folderRepository: FolderRepository,
    errorHandler: ErrorHandler
) : BaseViewModel<WorkoutTemplateCreationUiState, WorkoutTemplateCreationEvent>(errorHandler) {

    override val _uiState = MutableStateFlow<WorkoutTemplateCreationUiState>(
        WorkoutTemplateCreationUiState.Loading
    )
    
    private val _loadedTemplate = MutableStateFlow<WorkoutTemplate?>(null)
    val loadedTemplate: StateFlow<WorkoutTemplate?> = _loadedTemplate.asStateFlow()
    
    init {
        initializeWhenAuthenticated()
    }

    /**
     * Handles events from the UI following BaseViewModel MVI pattern
     */
    override fun handleEvent(event: WorkoutTemplateCreationEvent) {
        when (event) {
            is WorkoutTemplateCreationEvent.CreateWorkout -> createWorkout(event.name, event.description, event.exercises, event.tags)
            is WorkoutTemplateCreationEvent.UpdateTemplate -> updateTemplate(event.templateId, event.name, event.description, event.exercises, event.tags)
            is WorkoutTemplateCreationEvent.AddExercise -> addExercise(event.exercise)
            is WorkoutTemplateCreationEvent.AddExerciseFromSelector -> addExerciseFromSelector(event.exercise)
            is WorkoutTemplateCreationEvent.AddExerciseById -> addExerciseById(event.exerciseId, event.isCustomExercise)
            is WorkoutTemplateCreationEvent.AddExerciseFromLibrary -> addExerciseFromLibrary(event.exerciseLibrary)
            is WorkoutTemplateCreationEvent.RemoveExercise -> removeExercise(event.exercise)
            is WorkoutTemplateCreationEvent.ReorderExercises -> reorderExercises(event.fromIndex, event.toIndex)
            is WorkoutTemplateCreationEvent.UpdateExercise -> updateExercise(event.exercise)
            is WorkoutTemplateCreationEvent.SearchExercises -> onExerciseSearchQueryChanged(event.query)
            is WorkoutTemplateCreationEvent.SelectExercise -> onExerciseSelected(event.exercise)
            is WorkoutTemplateCreationEvent.ExpandSelector -> onExerciseSelectorExpandedChanged(event.expanded)
            is WorkoutTemplateCreationEvent.LoadTemplate -> loadTemplateForEditing(event.templateId)
            WorkoutTemplateCreationEvent.ResetToEditing -> resetToEditing()
        }
    }

    /**
     * Override to handle loading state updates
     */
    override fun setLoadingState() {
        setState(WorkoutTemplateCreationUiState.Loading)
    }

    /**
     * Override to handle error state updates
     */
    override fun updateErrorState(error: com.example.liftrix.domain.model.error.LiftrixError) {
        val currentData = _uiState.value.dataOrNull()
        val preservedData = currentData ?: WorkoutTemplateCreationData()
        
        setState(
            WorkoutTemplateCreationUiState.Error(
                error = error,
                previousData = preservedData
            )
        )
    }
    
    /**
     * Initialize ViewModel after authentication completes
     */
    private fun initializeWhenAuthenticated() {
        viewModelScope.launch {
            try {
                // Wait for authentication to complete
                val userId = getAuthenticatedUserIdUseCase()
                Timber.d("User authenticated, initializing template creation for user: $userId")
                
                // Transition to success state with initial data
                _uiState.value = WorkoutTemplateCreationUiState.Success(
                    data = WorkoutTemplateCreationData(
                        exercises = emptyList(),
                        availableExercises = emptyList(),
                        exerciseSearchQuery = "",
                        selectedExercise = null,
                        isExerciseSelectorExpanded = false
                    )
                )
                
                loadAvailableExercises()
                ensureDefaultFolderExists()
            } catch (exception: Exception) {
                val error = com.example.liftrix.domain.model.error.LiftrixError.AuthenticationError(
                    errorMessage = "Authentication failed: ${exception.message}"
                )
                setState(WorkoutTemplateCreationUiState.Error(error))
                Timber.e(exception, "Error in initializeWhenAuthenticated")
            }
        }
    }
    
    /**
     * Ensures that a default folder exists for the current user
     */
    private fun ensureDefaultFolderExists() {
        viewModelScope.launch {
            try {
                val userId = getAuthenticatedUserIdUseCase()
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
            searchExercisesUseCase.search("", Equipment.entries.toSet())
                .catch { error ->
                    Timber.e(error, "Error loading available exercises")
                }
                .collect { exercises ->
                    val currentData = _uiState.value.dataOrNull() ?: WorkoutTemplateCreationData()
                    setState(WorkoutTemplateCreationUiState.Success(
                        data = currentData.copy(availableExercises = exercises)
                    ))
                }
        }
    }

    /**
     * Creates a new workout routine with the specified parameters
     */
    fun createWorkout(
        name: String,
        description: String? = null,
        exercises: List<TemplateExercise> = emptyList(),
        tags: Set<String> = emptySet()
    ) {
        createTemplate(name, description, exercises, tags)
    }
    
    /**
     * Creates a new workout routine with the specified parameters (backend compatibility)
     * @deprecated Use createWorkout() instead for user-friendly workflow terminology
     */
    @Deprecated("Use createWorkout() instead", ReplaceWith("createWorkout(name, description, exercises, tags)"))
    fun createTemplate(
        name: String,
        description: String? = null,
        exercises: List<TemplateExercise> = emptyList(),
        tags: Set<String> = emptySet()
    ) {
        executeUseCase(
            useCase = {
                val userId = getAuthenticatedUserIdUseCase()
                createWorkoutTemplateUseCase(
                    userId = userId,
                    name = name,
                    folderId = "uncategorized_$userId",
                    description = description,
                    exercises = exercises
                )
            },
            onSuccess = { template ->
                setState(WorkoutTemplateCreationUiState.Success(
                    data = WorkoutTemplateCreationData(template = template)
                ))
                Timber.i("Workout routine created successfully: ${template.name}")
            },
            onError = { error ->
                Timber.e("Failed to create workout routine: ${error.message}")
            }
        )
    }
    
    /**
     * Updates an existing workout routine with the specified parameters
     */
    fun updateTemplate(
        templateId: String,
        name: String,
        description: String? = null,
        exercises: List<TemplateExercise> = emptyList(),
        tags: Set<String> = emptySet()
    ) {
        executeUseCase(
            useCase = {
                val userId = getAuthenticatedUserIdUseCase()
                
                val loadedTemplate = _loadedTemplate.value
                    ?: throw IllegalStateException("No template loaded for editing")
                
                // Create updated workout routine preserving metadata
                val updatedTemplate = loadedTemplate.copy(
                    name = name.trim(),
                    description = description?.trim()?.takeIf { it.isNotBlank() },
                    exercises = exercises.mapIndexed { index, exercise ->
                        exercise.copy(orderIndex = index)
                    },
                    updatedAt = java.time.Instant.now()
                )
                
                workoutTemplateRepository.updateTemplate(updatedTemplate)
            },
            onSuccess = { template ->
                setState(WorkoutTemplateCreationUiState.Success(
                    data = WorkoutTemplateCreationData(template = template)
                ))
                Timber.i("Workout routine updated successfully: ${template.name}")
            },
            onError = { error ->
                Timber.e("Failed to update workout routine: ${error.message}")
            }
        )
    }
    
    /**
     * Adds an exercise to the workout routine
     */
    fun addExercise(exercise: TemplateExercise) {
        timber.log.Timber.d("🔥 ADD-EXERCISE-DEBUG: Adding exercise: ${exercise.name}")
        val currentData = _uiState.value.dataOrNull() ?: WorkoutTemplateCreationData()
        timber.log.Timber.d("🔥 ADD-EXERCISE-DEBUG: Current exercises before add: ${currentData.exercises.size}")
        timber.log.Timber.d("🔥 ADD-EXERCISE-DEBUG: Current exercises list: ${currentData.exercises.map { it.name }}")
        
        val updatedExercises = currentData.exercises + exercise.copy(
            orderIndex = currentData.exercises.size
        )
        timber.log.Timber.d("🔥 ADD-EXERCISE-DEBUG: Updated exercises after add: ${updatedExercises.size}")
        timber.log.Timber.d("🔥 ADD-EXERCISE-DEBUG: Updated exercises list: ${updatedExercises.map { it.name }}")
        
        // Use BaseViewModel's setState method for consistency
        setState(WorkoutTemplateCreationUiState.Success(
            data = currentData.copy(exercises = updatedExercises)
        ))
        timber.log.Timber.d("🔥 ADD-EXERCISE-DEBUG: State update completed using setState()")
        
        // Verify state was set correctly
        val verifyData = _uiState.value.dataOrNull()
        timber.log.Timber.d("🔥 ADD-EXERCISE-DEBUG: State verification - exercises count: ${verifyData?.exercises?.size}")
        timber.log.Timber.d("🔥 ADD-EXERCISE-DEBUG: State verification - exercises list: ${verifyData?.exercises?.map { it.name }}")
    }
    
    /**
     * Adds an exercise from the search selector to the workout routine
     */
    fun addExerciseFromSelector(searchableExercise: SearchableExercise) {
        timber.log.Timber.d("🔥 ADD-FROM-SELECTOR-DEBUG: Adding exercise from selector")
        val templateExercise = convertSearchableExerciseToTemplateExercise(searchableExercise)
        timber.log.Timber.d("🔥 ADD-FROM-SELECTOR-DEBUG: Converted to template exercise: ${templateExercise.name}")
        
        addExercise(templateExercise)
        timber.log.Timber.d("🔥 ADD-FROM-SELECTOR-DEBUG: addExercise completed")
        
        // Clear selection after adding - but preserve exercises!
        val currentData = _uiState.value.dataOrNull()
        timber.log.Timber.d("🔥 ADD-FROM-SELECTOR-DEBUG: Current data after addExercise: exercises=${currentData?.exercises?.size}")
        if (currentData != null) {
            // Use BaseViewModel's setState method for consistency
            setState(WorkoutTemplateCreationUiState.Success(
                data = currentData.copy(
                    selectedExercise = null,
                    exerciseSearchQuery = "",
                    isExerciseSelectorExpanded = false
                )
            ))
            timber.log.Timber.d("🔥 ADD-FROM-SELECTOR-DEBUG: State cleared using setState(), exercises preserved: ${currentData.exercises.size}")
            
            // Final verification
            val finalData = _uiState.value.dataOrNull()
            timber.log.Timber.d("🔥 ADD-FROM-SELECTOR-DEBUG: Final verification - exercises count: ${finalData?.exercises?.size}")
        }
    }
    
    /**
     * Adds an exercise by ID (supports both library and custom exercises)
     */
    fun addExerciseById(exerciseId: String, isCustomExercise: Boolean) {
        viewModelScope.launch {
            try {
                val exercises = searchExercisesUseCase.search("", Equipment.entries.toSet()).first()
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
                val error = com.example.liftrix.domain.model.error.LiftrixError.UnknownError(
                    errorMessage = "Failed to add exercise: ${e.message}"
                )
                handleError(error)
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
     * Adds an exercise from the exercise library to the workout routine
     */
    fun addExerciseFromLibrary(exerciseLibrary: ExerciseLibrary) {
        timber.log.Timber.d("🔥 TEMPLATE-VIEWMODEL-DEBUG: addExerciseFromLibrary called")
        timber.log.Timber.d("🔥 TEMPLATE-VIEWMODEL-DEBUG: Exercise: ${exerciseLibrary.name} (ID: ${exerciseLibrary.id})")
        
        try {
            val templateExercise = convertExerciseLibraryToTemplateExercise(exerciseLibrary)
            timber.log.Timber.d("🔥 TEMPLATE-VIEWMODEL-DEBUG: Converted to TemplateExercise: ${templateExercise.name}")
            
            // Add the exercise to state first
            addExercise(templateExercise)
            timber.log.Timber.d("🔥 TEMPLATE-VIEWMODEL-DEBUG: addExercise completed successfully")
            
            // Apply smart defaults AFTER the exercise is added to state
            applySmartDefaults(templateExercise, exerciseLibrary)
            timber.log.Timber.d("🔥 TEMPLATE-VIEWMODEL-DEBUG: Smart defaults application started")
            
            // Debug current state
            val currentData = _uiState.value.dataOrNull()
            if (currentData != null) {
                timber.log.Timber.d("🔥 WORKOUT-VIEWMODEL-DEBUG: Current workout routine exercises count: ${currentData.exercises.size}")
                timber.log.Timber.d("🔥 WORKOUT-VIEWMODEL-DEBUG: Workout routine exercises: ${currentData.exercises.map { it.name }}")
            }
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "🔥 TEMPLATE-VIEWMODEL-DEBUG: Error in addExerciseFromLibrary")
        }
    }
    
    /**
     * Converts ExerciseLibrary to TemplateExercise with smart defaults applied
     * Fixed to prevent race conditions by deferring smart defaults until after exercise is added
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
        
        timber.log.Timber.d("🔥 CONVERT-DEBUG: Created base exercise ${baseExercise.name}, will apply defaults after adding to state")
        
        return baseExercise
    }
    
    /**
     * Applies smart defaults to a template exercise based on user history and exercise type
     * Fixed to prevent race conditions by ensuring exercise exists before applying defaults
     */
    private fun applySmartDefaults(templateExercise: TemplateExercise, exerciseLibrary: ExerciseLibrary) {
        viewModelScope.launch {
            try {
                // Add a small delay to ensure the exercise is properly added to state first
                kotlinx.coroutines.delay(50)
                
                val userId = getAuthenticatedUserIdUseCase()
                
                timber.log.Timber.d("🔥 SMART-DEFAULTS-DEBUG: Starting smart defaults for ${exerciseLibrary.name}")
                
                // Verify the exercise still exists in the current state before applying defaults
                val currentData = _uiState.value.dataOrNull()
                val exerciseExists = currentData?.exercises?.any { it.exerciseId == templateExercise.exerciseId } == true
                
                if (!exerciseExists) {
                    timber.log.Timber.w("🔥 SMART-DEFAULTS-DEBUG: Exercise ${exerciseLibrary.name} no longer exists in state, skipping defaults")
                    return@launch
                }
                
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
     * Fixed to prevent race conditions with concurrent state updates
     */
    private fun updateExerciseWithDefaults(exerciseId: ExerciseId, defaults: ExerciseDefaults) {
        timber.log.Timber.d("🔥 DEFAULTS-DEBUG: Updating exercise ${exerciseId.value} with defaults")
        val currentData = _uiState.value.dataOrNull()
        if (currentData != null) {
            timber.log.Timber.d("🔥 DEFAULTS-DEBUG: Current exercises before defaults: ${currentData.exercises.size}")
            timber.log.Timber.d("🔥 DEFAULTS-DEBUG: Exercise names: ${currentData.exercises.map { it.name }}")
            
            val updatedExercises = currentData.exercises.map { exercise ->
                if (exercise.exerciseId == exerciseId) {
                    timber.log.Timber.d("🔥 DEFAULTS-DEBUG: Applying defaults to ${exercise.name}")
                    defaults.applyToTemplateExercise(exercise)
                } else {
                    exercise
                }
            }
            
            timber.log.Timber.d("🔥 DEFAULTS-DEBUG: Updated exercises after defaults: ${updatedExercises.size}")
            setState(WorkoutTemplateCreationUiState.Success(
                data = currentData.copy(exercises = updatedExercises)
            ))
            
            // Verify the update didn't lose exercises
            val verifyData = _uiState.value.dataOrNull()
            timber.log.Timber.d("🔥 DEFAULTS-DEBUG: Verification - exercises count: ${verifyData?.exercises?.size}")
        } else {
            timber.log.Timber.w("🔥 DEFAULTS-DEBUG: No current data available for defaults update")
        }
    }
    
    /**
     * Updates the estimated duration for the current template
     */
    private fun updateEstimatedDuration() {
        viewModelScope.launch {
            try {
                val currentData = _uiState.value.dataOrNull()
                if (currentData != null && currentData.exercises.isNotEmpty()) {
                    // Create a temporary template for duration estimation
                    val tempTemplate = WorkoutTemplate.create(
                        userId = getAuthenticatedUserIdUseCase(),
                        name = "Temporary",
                        folderId = "temp",
                        exercises = currentData.exercises
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
        val currentData = _uiState.value.dataOrNull()
        if (currentData != null) {
            setState(WorkoutTemplateCreationUiState.Success(
                data = currentData.copy(exerciseSearchQuery = query)
            ))
            
            // Perform search
            viewModelScope.launch {
                searchExercisesUseCase.search(query, Equipment.entries.toSet())
                    .catch { error ->
                        Timber.e(error, "Error searching exercises")
                    }
                    .collect { exercises ->
                        val updatedData = _uiState.value.dataOrNull()
                        if (updatedData != null) {
                            setState(WorkoutTemplateCreationUiState.Success(
                                data = updatedData.copy(availableExercises = exercises)
                            ))
                        }
                    }
            }
        }
    }
    
    /**
     * Updates selected exercise
     */
    fun onExerciseSelected(exercise: SearchableExercise?) {
        val currentData = _uiState.value.dataOrNull()
        if (currentData != null) {
            setState(WorkoutTemplateCreationUiState.Success(
                data = currentData.copy(selectedExercise = exercise)
            ))
        }
    }
    
    /**
     * Updates exercise selector expanded state
     */
    fun onExerciseSelectorExpandedChanged(expanded: Boolean) {
        val currentData = _uiState.value.dataOrNull()
        if (currentData != null) {
            setState(WorkoutTemplateCreationUiState.Success(
                data = currentData.copy(isExerciseSelectorExpanded = expanded)
            ))
        }
    }
    
    /**
     * Removes an exercise from the template
     */
    fun removeExercise(exercise: TemplateExercise) {
        val currentData = _uiState.value.dataOrNull()
        if (currentData != null) {
            val updatedExercises = currentData.exercises
                .filter { it.exerciseId != exercise.exerciseId }
                .mapIndexed { index, ex -> ex.copy(orderIndex = index) }
            setState(WorkoutTemplateCreationUiState.Success(
                data = currentData.copy(exercises = updatedExercises)
            ))
        }
    }
    
    /**
     * Reorders exercises in the template
     */
    fun reorderExercises(fromIndex: Int, toIndex: Int) {
        val currentData = _uiState.value.dataOrNull()
        if (currentData != null) {
            val exercises = currentData.exercises.toMutableList()
            if (fromIndex in exercises.indices && toIndex in exercises.indices) {
                val item = exercises.removeAt(fromIndex)
                exercises.add(toIndex, item)
                val reorderedExercises = exercises.mapIndexed { index, exercise ->
                    exercise.copy(orderIndex = index)
                }
                setState(WorkoutTemplateCreationUiState.Success(
                    data = currentData.copy(exercises = reorderedExercises)
                ))
            }
        }
    }
    
    /**
     * Updates an exercise in the template
     */
    fun updateExercise(updatedExercise: TemplateExercise) {
        val currentData = _uiState.value.dataOrNull()
        if (currentData != null) {
            val updatedExercises = currentData.exercises.map { exercise ->
                if (exercise.exerciseId == updatedExercise.exerciseId) {
                    updatedExercise
                } else {
                    exercise
                }
            }
            setState(WorkoutTemplateCreationUiState.Success(
                data = currentData.copy(exercises = updatedExercises)
            ))
        }
    }
    
    /**
     * Resets the creation state back to editing
     */
    fun resetToEditing() {
        val currentData = _uiState.value.dataOrNull()
        val exercises = currentData?.exercises ?: emptyList()
        
        setState(WorkoutTemplateCreationUiState.Success(
            data = WorkoutTemplateCreationData(
                exercises = exercises,
                availableExercises = emptyList(),
                exerciseSearchQuery = "",
                selectedExercise = null,
                isExerciseSelectorExpanded = false
            )
        ))
        loadAvailableExercises()
    }
    
    /**
     * Validates if the current template state is valid for creation
     */
    fun isValidForCreation(name: String): Boolean {
        val currentData = _uiState.value.dataOrNull()
        return name.isNotBlank() && 
               name.length <= WorkoutTemplate.MAX_NAME_LENGTH &&
               (currentData?.exercises?.isNotEmpty() == true)
    }
    
    /**
     * Loads an existing template for editing
     */
    fun loadTemplateForEditing(templateId: String) {
        viewModelScope.launch {
            try {
                val userId = getAuthenticatedUserIdUseCase()
                val templateResult = workoutTemplateRepository.getTemplateById(
                    WorkoutTemplateId(templateId), 
                    userId
                )
                
                if (templateResult.isSuccess) {
                    val template = templateResult.getOrNull()
                    if (template != null) {
                        _loadedTemplate.value = template
                        val currentData = _uiState.value.dataOrNull()
                        setState(WorkoutTemplateCreationUiState.Success(
                            data = WorkoutTemplateCreationData(
                                exercises = template.exercises,
                                availableExercises = currentData?.availableExercises ?: emptyList(),
                                exerciseSearchQuery = "",
                                selectedExercise = null,
                                isExerciseSelectorExpanded = false
                            )
                        ))
                    } else {
                        val error = com.example.liftrix.domain.model.error.LiftrixError.NotFoundError(
                            errorMessage = "Template not found",
                            resourceType = "template",
                            resourceId = templateId
                        )
                        handleError(error)
                    }
                } else {
                    val error = com.example.liftrix.domain.model.error.LiftrixError.DataRetrievalError(
                        errorMessage = templateResult.exceptionOrNull()?.message ?: "Failed to load template"
                    )
                    handleError(error)
                }
            } catch (exception: Exception) {
                Timber.e(exception, "Error loading template for editing")
                val error = com.example.liftrix.domain.model.error.LiftrixError.UnknownError(
                    errorMessage = exception.message ?: "Failed to load template"
                )
                handleError(error)
            }
        }
    }
}

// WorkoutTemplateCreationData and WorkoutTemplateCreationUiState are now defined in ViewModelState.kt

/**
 * Events for workout creation (updated terminology)
 */
sealed class WorkoutTemplateCreationEvent : ViewModelEvent {
    data class CreateWorkout(
        val name: String,
        val description: String? = null,
        val exercises: List<TemplateExercise> = emptyList(),
        val tags: Set<String> = emptySet()
    ) : WorkoutTemplateCreationEvent()
    
    data class UpdateTemplate(
        val templateId: String,
        val name: String,
        val description: String? = null,
        val exercises: List<TemplateExercise> = emptyList(),
        val tags: Set<String> = emptySet()
    ) : WorkoutTemplateCreationEvent()
    
    data class AddExercise(val exercise: TemplateExercise) : WorkoutTemplateCreationEvent()
    data class AddExerciseFromSelector(val exercise: SearchableExercise) : WorkoutTemplateCreationEvent()
    data class AddExerciseById(val exerciseId: String, val isCustomExercise: Boolean) : WorkoutTemplateCreationEvent()
    data class AddExerciseFromLibrary(val exerciseLibrary: ExerciseLibrary) : WorkoutTemplateCreationEvent()
    data class RemoveExercise(val exercise: TemplateExercise) : WorkoutTemplateCreationEvent()
    data class ReorderExercises(val fromIndex: Int, val toIndex: Int) : WorkoutTemplateCreationEvent()
    data class UpdateExercise(val exercise: TemplateExercise) : WorkoutTemplateCreationEvent()
    data class SearchExercises(val query: String) : WorkoutTemplateCreationEvent()
    data class SelectExercise(val exercise: SearchableExercise?) : WorkoutTemplateCreationEvent()
    data class ExpandSelector(val expanded: Boolean) : WorkoutTemplateCreationEvent()
    data class LoadTemplate(val templateId: String) : WorkoutTemplateCreationEvent()
    object ResetToEditing : WorkoutTemplateCreationEvent()
}