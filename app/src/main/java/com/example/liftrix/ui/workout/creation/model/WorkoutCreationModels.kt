package com.example.liftrix.ui.workout.creation.model

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseLibrary

/**
 * Represents a selected exercise with its associated sets in the workout creation flow
 */
data class SelectedExercise(
    val libraryExercise: ExerciseLibrary,
    val sets: List<SetInput>,
    val orderIndex: Int
) {
    companion object {
        fun fromLibraryExercise(exercise: ExerciseLibrary, orderIndex: Int): SelectedExercise {
            return SelectedExercise(
                libraryExercise = exercise,
                sets = listOf(SetInput.createForEquipment(exercise.equipment)),
                orderIndex = orderIndex
            )
        }
    }
    
    /**
     * Validates if this selected exercise has at least one valid set
     */
    fun isValid(): Boolean {
        return sets.isNotEmpty() && sets.any { it.isValid() }
    }
    
    /**
     * Calculates total volume for this exercise (sets * reps * weight where applicable)
     */
    fun calculateTotalVolume(): Double {
        return sets.sumOf { set ->
            val reps = set.reps.toIntOrNull() ?: 0
            val weight = if (set.isWeightSupported) set.weight.toDoubleOrNull() ?: 0.0 else 1.0
            reps * weight
        }
    }
}

/**
 * Represents input for a single set within an exercise
 */
data class SetInput(
    val reps: String = "",
    val repsError: String? = null,
    val rpe: String = "",
    val rpeError: String? = null,
    val weight: String = "",
    val weightError: String? = null,
    val isWeightSupported: Boolean = true
) {
    companion object {
        val EMPTY = SetInput()
        
        /**
         * Creates SetInput with appropriate weight support based on equipment type
         */
        fun createForEquipment(equipment: Equipment): SetInput {
            val supportsWeight = when (equipment) {
                Equipment.DUMBBELLS,
                Equipment.BARBELL,
                Equipment.KETTLEBELLS,
                Equipment.CABLE_MACHINE -> true
                Equipment.BODYWEIGHT_ONLY,
                Equipment.PULL_UP_BAR,
                Equipment.RESISTANCE_BANDS,
                Equipment.BENCH,
                Equipment.TREADMILL,
                Equipment.EXERCISE_BIKE -> false
            }
            
            return SetInput(isWeightSupported = supportsWeight)
        }
        
        const val MIN_REPS: Int = 1
        const val MAX_REPS: Int = 999
        const val MIN_RPE: Int = 1
        const val MAX_RPE: Int = 10
        const val MIN_WEIGHT: Double = 0.0
        const val MAX_WEIGHT: Double = 9999.0
    }
    
    /**
     * Validates if this set input is complete and valid
     */
    fun isValid(): Boolean {
        return repsError == null && 
               rpeError == null && 
               weightError == null &&
               reps.isNotBlank() &&
               (rpe.isNotBlank() || !isRpeRequired()) &&
               (weight.isNotBlank() || !isWeightSupported)
    }
    
    /**
     * Determines if RPE is required for this set (optional for now)
     */
    private fun isRpeRequired(): Boolean = false
    
    /**
     * Validates reps input and returns error message if invalid
     */
    fun validateReps(input: String): String? {
        if (input.isBlank()) return "Reps are required"
        val reps = input.toIntOrNull()
        return when {
            reps == null -> "Reps must be a number"
            reps < MIN_REPS -> "Reps must be at least $MIN_REPS"
            reps > MAX_REPS -> "Reps cannot exceed $MAX_REPS"
            else -> null
        }
    }
    
    /**
     * Validates RPE input and returns error message if invalid
     */
    fun validateRpe(input: String): String? {
        if (input.isBlank()) return if (isRpeRequired()) "RPE is required" else null
        val rpe = input.toIntOrNull()
        return when {
            rpe == null -> "RPE must be a number"
            rpe < MIN_RPE -> "RPE must be at least $MIN_RPE"
            rpe > MAX_RPE -> "RPE cannot exceed $MAX_RPE"
            else -> null
        }
    }
    
    /**
     * Validates weight input and returns error message if invalid
     */
    fun validateWeight(input: String): String? {
        if (!isWeightSupported) return null
        if (input.isBlank()) return "Weight is required"
        val weight = input.toDoubleOrNull()
        return when {
            weight == null -> "Weight must be a number"
            weight < MIN_WEIGHT -> "Weight cannot be negative"
            weight > MAX_WEIGHT -> "Weight cannot exceed $MAX_WEIGHT kg"
            else -> null
        }
    }
}

/**
 * Complete UI state for the redesigned single-screen workout creation
 */
data class WorkoutCreationState(
    // Workout header information
    val workoutName: String = "",
    val workoutNameError: String? = null,
    val workoutDescription: String = "",
    val workoutDescriptionError: String? = null,
    
    // Selected exercises list
    val selectedExercises: List<SelectedExercise> = emptyList(),
    
    // Exercise selection state
    val isExerciseSelectorVisible: Boolean = false,
    val availableExercises: List<ExerciseLibrary> = emptyList(),
    val exerciseSearchQuery: String = "",
    val filteredExercises: List<ExerciseLibrary> = emptyList(),
    
    // Form validation state
    val isWorkoutNameValid: Boolean = false,
    val isExercisesValid: Boolean = false,
    val isFormValid: Boolean = false,
    
    // UI control state
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val showSuccessMessage: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
) {
    companion object {
        val EMPTY = WorkoutCreationState()
        
        const val MAX_WORKOUT_NAME_LENGTH: Int = 100
        const val MAX_WORKOUT_DESCRIPTION_LENGTH: Int = 500
        const val MAX_EXERCISES_COUNT: Int = 20
        const val MIN_EXERCISES_COUNT: Int = 1
    }
    
    /**
     * Validates workout name and returns error message if invalid
     */
    fun validateWorkoutName(name: String): String? {
        return when {
            name.isBlank() -> "Workout name is required"
            name.length > MAX_WORKOUT_NAME_LENGTH -> "Workout name cannot exceed $MAX_WORKOUT_NAME_LENGTH characters"
            else -> null
        }
    }
    
    /**
     * Validates workout description and returns error message if invalid
     */
    fun validateWorkoutDescription(description: String): String? {
        return when {
            description.length > MAX_WORKOUT_DESCRIPTION_LENGTH -> "Description cannot exceed $MAX_WORKOUT_DESCRIPTION_LENGTH characters"
            else -> null
        }
    }
    
    /**
     * Validates the complete form and returns true if all fields are valid
     */
    fun validateForm(): Boolean {
        val nameValid = workoutNameError == null && workoutName.isNotBlank()
        val descriptionValid = workoutDescriptionError == null
        val exercisesValid = selectedExercises.isNotEmpty() && 
                            selectedExercises.size <= MAX_EXERCISES_COUNT &&
                            selectedExercises.all { it.isValid() }
        
        return nameValid && descriptionValid && exercisesValid
    }
}

/**
 * Events representing all possible user interactions in workout creation
 */
sealed class WorkoutCreationEvent {
    // Workout header events
    data class UpdateWorkoutName(val name: String) : WorkoutCreationEvent()
    data class UpdateWorkoutDescription(val description: String) : WorkoutCreationEvent()
    
    // Exercise selection events
    object ShowExerciseSelector : WorkoutCreationEvent()
    object HideExerciseSelector : WorkoutCreationEvent()
    data class UpdateExerciseSearchQuery(val query: String) : WorkoutCreationEvent()
    data class SelectExercise(val exercise: ExerciseLibrary) : WorkoutCreationEvent()
    data class RemoveExercise(val exerciseIndex: Int) : WorkoutCreationEvent()
    data class ReorderExercises(val fromIndex: Int, val toIndex: Int) : WorkoutCreationEvent()
    
    // Set management events
    data class AddSetToExercise(val exerciseIndex: Int) : WorkoutCreationEvent()
    data class RemoveSetFromExercise(val exerciseIndex: Int, val setIndex: Int) : WorkoutCreationEvent()
    data class UpdateSetReps(val exerciseIndex: Int, val setIndex: Int, val reps: String) : WorkoutCreationEvent()
    data class UpdateSetRpe(val exerciseIndex: Int, val setIndex: Int, val rpe: String) : WorkoutCreationEvent()
    data class UpdateSetWeight(val exerciseIndex: Int, val setIndex: Int, val weight: String) : WorkoutCreationEvent()
    
    // Form submission events
    object SaveWorkout : WorkoutCreationEvent()
    object ResetForm : WorkoutCreationEvent()
    
    // Message handling events
    object ClearMessages : WorkoutCreationEvent()
    object DismissSuccessMessage : WorkoutCreationEvent()
}

/**
 * Side effects for workout creation that don't directly update state
 */
sealed class WorkoutCreationEffect {
    // Navigation effects
    object NavigateBack : WorkoutCreationEffect()
    object NavigateToWorkoutList : WorkoutCreationEffect()
    
    // User feedback effects
    data class ShowSnackbar(val message: String) : WorkoutCreationEffect()
    data class ShowToast(val message: String) : WorkoutCreationEffect()
    
    // System effects
    object HideKeyboard : WorkoutCreationEffect()
    object ScrollToTop : WorkoutCreationEffect()
    data class ScrollToExercise(val exerciseIndex: Int) : WorkoutCreationEffect()
    
    // Analytics effects
    data class TrackEvent(val eventName: String, val parameters: Map<String, Any> = emptyMap()) : WorkoutCreationEffect()
} 