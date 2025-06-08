package com.example.liftrix.ui.workout.daily

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.DailyWorkout
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.usecase.exercise.SearchableExercise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * UI state for daily workout execution
 */
data class DailyWorkoutUiState(
    val currentWorkout: DailyWorkout? = null,
    val sourceTemplate: WorkoutTemplate? = null,
    val isLoading: Boolean = false,
    val isWorkoutActive: Boolean = false,
    val isWorkoutCompleted: Boolean = false,
    val errorMessage: String? = null,
    val canConvertToTemplate: Boolean = false,
    val showConvertToTemplateDialog: Boolean = false,
    val showExerciseSelection: Boolean = false,
    val selectedExerciseForEdit: ExerciseId? = null
)

/**
 * Events for daily workout execution
 */
sealed class DailyWorkoutEvent {
    data class StartFromTemplate(val templateId: String) : DailyWorkoutEvent()
    object StartFromScratch : DailyWorkoutEvent()
    data class AddExercise(val searchableExercise: SearchableExercise) : DailyWorkoutEvent()
    data class UpdateExerciseSet(
        val exerciseId: ExerciseId, 
        val setIndex: Int, 
        val weight: Weight, 
        val reps: Reps
    ) : DailyWorkoutEvent()
    data class CompleteSet(val exerciseId: ExerciseId, val setIndex: Int) : DailyWorkoutEvent()
    data class AddSetToExercise(val exerciseId: ExerciseId) : DailyWorkoutEvent()
    data class RemoveExercise(val exerciseId: ExerciseId) : DailyWorkoutEvent()
    object CompleteWorkout : DailyWorkoutEvent()
    object PauseWorkout : DailyWorkoutEvent()
    object ResumeWorkout : DailyWorkoutEvent()
    data class ConvertToTemplate(val templateName: String) : DailyWorkoutEvent()
    object ShowConvertToTemplateDialog : DailyWorkoutEvent()
    object HideConvertToTemplateDialog : DailyWorkoutEvent()
    object ShowExerciseSelection : DailyWorkoutEvent()
    object HideExerciseSelection : DailyWorkoutEvent()
    object ClearError : DailyWorkoutEvent()
}

/**
 * ViewModel for daily workout execution with template integration
 */
@HiltViewModel
class DailyWorkoutViewModel @Inject constructor(
    // TODO: Inject repositories when available
    // private val workoutRepository: WorkoutRepository,
    // private val templateRepository: WorkoutTemplateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyWorkoutUiState())
    val uiState: StateFlow<DailyWorkoutUiState> = _uiState.asStateFlow()

    /**
     * Handle UI events
     */
    fun onEvent(event: DailyWorkoutEvent) {
        when (event) {
            is DailyWorkoutEvent.StartFromTemplate -> startFromTemplate(event.templateId)
            is DailyWorkoutEvent.StartFromScratch -> startFromScratch()
            is DailyWorkoutEvent.AddExercise -> addExercise(event.searchableExercise)
            is DailyWorkoutEvent.UpdateExerciseSet -> updateExerciseSet(
                event.exerciseId, event.setIndex, event.weight, event.reps
            )
            is DailyWorkoutEvent.CompleteSet -> completeSet(event.exerciseId, event.setIndex)
            is DailyWorkoutEvent.AddSetToExercise -> addSetToExercise(event.exerciseId)
            is DailyWorkoutEvent.RemoveExercise -> removeExercise(event.exerciseId)
            is DailyWorkoutEvent.CompleteWorkout -> completeWorkout()
            is DailyWorkoutEvent.PauseWorkout -> pauseWorkout()
            is DailyWorkoutEvent.ResumeWorkout -> resumeWorkout()
            is DailyWorkoutEvent.ConvertToTemplate -> convertToTemplate(event.templateName)
            is DailyWorkoutEvent.ShowConvertToTemplateDialog -> showConvertToTemplateDialog()
            is DailyWorkoutEvent.HideConvertToTemplateDialog -> hideConvertToTemplateDialog()
            is DailyWorkoutEvent.ShowExerciseSelection -> showExerciseSelection()
            is DailyWorkoutEvent.HideExerciseSelection -> hideExerciseSelection()
            is DailyWorkoutEvent.ClearError -> clearError()
        }
    }

    /**
     * Start a workout from a template
     */
    private fun startFromTemplate(templateId: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        
        viewModelScope.launch {
            try {
                // TODO: Load template and create daily workout
                // val template = templateRepository.getTemplate(templateId)
                // val dailyWorkout = DailyWorkout.fromTemplate(template)
                
                // Mock implementation for now
                Timber.d("Starting workout from template: $templateId")
                
                val mockWorkout = createMockWorkout(isFromTemplate = true)
                
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        isWorkoutActive = true,
                        currentWorkout = mockWorkout,
                        sourceTemplate = null, // TODO: Load real template
                        canConvertToTemplate = false // Already from template
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to start workout from template")
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load template. Please try again."
                    )
                }
            }
        }
    }

    /**
     * Start a workout from scratch
     */
    private fun startFromScratch() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        
        viewModelScope.launch {
            try {
                Timber.d("Starting workout from scratch")
                
                val newWorkout = DailyWorkout.create(
                    userId = "current-user", // TODO: Get from auth
                    name = "Quick Workout",
                    date = LocalDate.now(),
                    exercises = emptyList()
                ).start()
                
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        isWorkoutActive = true,
                        currentWorkout = newWorkout,
                        sourceTemplate = null,
                        canConvertToTemplate = true // Can convert custom workout to template
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to start workout from scratch")
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to start workout. Please try again."
                    )
                }
            }
        }
    }

    /**
     * Add an exercise to the current workout
     */
    private fun addExercise(searchableExercise: SearchableExercise) {
        val currentWorkout = _uiState.value.currentWorkout ?: return
        
        try {
            val exercise = createExerciseFromSearchable(searchableExercise)
            val updatedWorkout = currentWorkout.addExercise(exercise)
            
            _uiState.update { 
                it.copy(
                    currentWorkout = updatedWorkout,
                    showExerciseSelection = false
                )
            }
            
            Timber.d("Added exercise: ${exercise.name}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to add exercise")
            _uiState.update { 
                it.copy(errorMessage = "Failed to add exercise. Please try again.")
            }
        }
    }

    /**
     * Update a specific set in an exercise
     */
    private fun updateExerciseSet(exerciseId: ExerciseId, setIndex: Int, weight: Weight, reps: Reps) {
        val currentWorkout = _uiState.value.currentWorkout ?: return
        
        try {
            val exercise = currentWorkout.exercises.find { it.id == exerciseId } ?: return
            val updatedSet = exercise.sets[setIndex].updateWeightAndReps(weight, reps)
            val updatedExercise = exercise.updateSet(setIndex + 1, updatedSet) // setNumber is 1-indexed
            val updatedWorkout = currentWorkout.updateExercise(exerciseId, updatedExercise)
            
            _uiState.update { it.copy(currentWorkout = updatedWorkout) }
            
            Timber.d("Updated set $setIndex for exercise ${exercise.name}: ${weight.kilograms}kg x ${reps.count}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update exercise set")
            _uiState.update { 
                it.copy(errorMessage = "Failed to update set. Please try again.")
            }
        }
    }

    /**
     * Complete a set in the current workout
     */
    private fun completeSet(exerciseId: ExerciseId, setIndex: Int) {
        val currentWorkout = _uiState.value.currentWorkout ?: return
        
        try {
            val exercise = currentWorkout.exercises.find { it.id == exerciseId } ?: return
            val updatedSet = exercise.sets[setIndex].markCompleted()
            val updatedExercise = exercise.updateSet(setIndex + 1, updatedSet) // setNumber is 1-indexed
            val updatedWorkout = currentWorkout.updateExercise(exerciseId, updatedExercise)
            
            _uiState.update { it.copy(currentWorkout = updatedWorkout) }
            
            Timber.d("Completed set $setIndex for exercise ${exercise.name}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to complete set")
            _uiState.update { 
                it.copy(errorMessage = "Failed to complete set. Please try again.")
            }
        }
    }

    /**
     * Add a new set to an exercise
     */
    private fun addSetToExercise(exerciseId: ExerciseId) {
        val currentWorkout = _uiState.value.currentWorkout ?: return
        
        try {
            val exercise = currentWorkout.exercises.find { it.id == exerciseId } ?: return
            val lastSet = exercise.sets.lastOrNull()
            val newWeight = lastSet?.weight ?: Weight.fromKilograms(20.0)
            val newReps = lastSet?.reps ?: Reps.of(10)
            
            val updatedExercise = exercise.addSet(newWeight, newReps)
            val updatedWorkout = currentWorkout.updateExercise(exerciseId, updatedExercise)
            
            _uiState.update { it.copy(currentWorkout = updatedWorkout) }
            
            Timber.d("Added new set to exercise ${exercise.name}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to add set")
            _uiState.update { 
                it.copy(errorMessage = "Failed to add set. Please try again.")
            }
        }
    }

    /**
     * Remove an exercise from the workout
     */
    private fun removeExercise(exerciseId: ExerciseId) {
        val currentWorkout = _uiState.value.currentWorkout ?: return
        
        try {
            val updatedWorkout = currentWorkout.removeExercise(exerciseId)
            _uiState.update { it.copy(currentWorkout = updatedWorkout) }
            
            Timber.d("Removed exercise $exerciseId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove exercise")
            _uiState.update { 
                it.copy(errorMessage = "Failed to remove exercise. Please try again.")
            }
        }
    }

    /**
     * Complete the current workout
     */
    private fun completeWorkout() {
        val currentWorkout = _uiState.value.currentWorkout ?: return
        
        viewModelScope.launch {
            try {
                val completedWorkout = currentWorkout.complete()
                
                _uiState.update { 
                    it.copy(
                        currentWorkout = completedWorkout,
                        isWorkoutActive = false,
                        isWorkoutCompleted = true,
                        canConvertToTemplate = it.sourceTemplate == null // Only if not from template
                    )
                }
                
                Timber.d("Completed workout: ${completedWorkout.name}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to complete workout")
                _uiState.update { 
                    it.copy(errorMessage = "Failed to save workout. Please try again.")
                }
            }
        }
    }

    /**
     * Pause the current workout
     */
    private fun pauseWorkout() {
        val currentWorkout = _uiState.value.currentWorkout ?: return
        
        try {
            val pausedWorkout = currentWorkout.pause()
            _uiState.update { it.copy(currentWorkout = pausedWorkout) }
            
            Timber.d("Paused workout")
        } catch (e: Exception) {
            Timber.e(e, "Failed to pause workout")
        }
    }

    /**
     * Resume the current workout
     */
    private fun resumeWorkout() {
        val currentWorkout = _uiState.value.currentWorkout ?: return
        
        try {
            val resumedWorkout = currentWorkout.resume()
            _uiState.update { it.copy(currentWorkout = resumedWorkout) }
            
            Timber.d("Resumed workout")
        } catch (e: Exception) {
            Timber.e(e, "Failed to resume workout")
        }
    }

    /**
     * Convert the current workout to a template
     */
    private fun convertToTemplate(templateName: String) {
        val currentWorkout = _uiState.value.currentWorkout ?: return
        
        viewModelScope.launch {
            try {
                // TODO: Implement template conversion
                // val template = currentWorkout.toTemplate(templateName)
                // templateRepository.saveTemplate(template)
                
                _uiState.update { 
                    it.copy(
                        showConvertToTemplateDialog = false,
                        errorMessage = null
                    )
                }
                
                Timber.d("Converted workout to template: $templateName")
            } catch (e: Exception) {
                Timber.e(e, "Failed to convert to template")
                _uiState.update { 
                    it.copy(errorMessage = "Failed to create template. Please try again.")
                }
            }
        }
    }

    private fun showConvertToTemplateDialog() {
        _uiState.update { it.copy(showConvertToTemplateDialog = true) }
    }

    private fun hideConvertToTemplateDialog() {
        _uiState.update { it.copy(showConvertToTemplateDialog = false) }
    }

    private fun showExerciseSelection() {
        _uiState.update { it.copy(showExerciseSelection = true) }
    }

    private fun hideExerciseSelection() {
        _uiState.update { it.copy(showExerciseSelection = false) }
    }

    private fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Helper function to create an Exercise from a SearchableExercise
     */
    private fun createExerciseFromSearchable(searchableExercise: SearchableExercise): Exercise {
        val now = Instant.now()
        val initialSet = ExerciseSet(
            setNumber = 1,
            weight = Weight.fromKilograms(20.0), // Default starting weight
            reps = Reps.of(10), // Default starting reps
            isCompleted = false
        )
        
        return Exercise(
            id = ExerciseId.generate(),
            name = searchableExercise.name,
            category = when (searchableExercise) {
                is SearchableExercise.LibraryExercise -> searchableExercise.exercise.primaryMuscleGroup
                is SearchableExercise.CustomExercise -> searchableExercise.exercise.primaryMuscle
            },
            sets = listOf(initialSet),
            createdAt = now,
            updatedAt = now
        )
    }

    /**
     * Temporary helper function to create mock workout for template testing
     */
    private fun createMockWorkout(isFromTemplate: Boolean): DailyWorkout {
        val now = Instant.now()
        val mockExercises = if (isFromTemplate) {
            listOf(
                Exercise(
                    id = ExerciseId.generate(),
                    name = "Bench Press",
                    category = com.example.liftrix.domain.model.ExerciseCategory.CHEST,
                    sets = listOf(
                        ExerciseSet(1, Weight.fromKilograms(60.0), Reps.of(10)),
                        ExerciseSet(2, Weight.fromKilograms(65.0), Reps.of(8)),
                        ExerciseSet(3, Weight.fromKilograms(70.0), Reps.of(6))
                    ),
                    createdAt = now,
                    updatedAt = now
                ),
                Exercise(
                    id = ExerciseId.generate(),
                    name = "Squat",
                    category = com.example.liftrix.domain.model.ExerciseCategory.LEGS,
                    sets = listOf(
                        ExerciseSet(1, Weight.fromKilograms(80.0), Reps.of(12)),
                        ExerciseSet(2, Weight.fromKilograms(85.0), Reps.of(10)),
                        ExerciseSet(3, Weight.fromKilograms(90.0), Reps.of(8))
                    ),
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            emptyList()
        }
        
        return DailyWorkout.create(
            userId = "current-user",
            name = if (isFromTemplate) "Template Workout" else "Quick Workout",
            exercises = mockExercises
        ).start()
    }
} 