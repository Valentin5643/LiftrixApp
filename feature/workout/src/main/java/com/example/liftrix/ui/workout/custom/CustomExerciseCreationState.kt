package com.example.liftrix.ui.workout.custom

import android.net.Uri
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseType
import com.example.liftrix.ui.common.event.ViewModelEvent

/**
 * UI state for custom exercise creation form
 */
data class CustomExerciseFormState(
    // Basic Info
    val name: String = "",
    val nameError: String? = null,
    val description: String = "",
    val descriptionError: String? = null,
    val exerciseType: ExerciseType = ExerciseType.WEIGHT_BASED,
    
    // Exercise Details
    val primaryMuscle: ExerciseCategory? = null,
    val secondaryMuscles: Set<ExerciseCategory> = emptySet(),
    val equipment: Equipment? = null,
    val difficulty: Int? = null,
    
    // Media
    val mainImageUri: Uri? = null,
    val additionalImageUris: List<Uri> = emptyList(),
    val videoUrl: String = "",
    val videoUrlError: String? = null,
    
    
    // State
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val isCreated: Boolean = false,
    val createdExerciseName: String? = null
) {
    /**
     * Checks if the form has the minimum required fields to create an exercise
     */
    fun canCreate(): Boolean {
        return name.isNotBlank() &&
                nameError == null &&
                primaryMuscle != null &&
                equipment != null &&
                descriptionError == null &&
                videoUrlError == null &&
                !isCreating &&
                !isCreated
    }
    
    /**
     * Checks if the form has been modified from its initial state
     */
    fun hasChanges(): Boolean {
        return name.isNotBlank() ||
                description.isNotBlank() ||
                primaryMuscle != null ||
                secondaryMuscles.isNotEmpty() ||
                equipment != null ||
                difficulty != null ||
                mainImageUri != null ||
                additionalImageUris.isNotEmpty() ||
                videoUrl.isNotBlank()
    }
    
    /**
     * Gets validation errors as a list
     */
    fun getValidationErrors(): List<String> {
        val errors = mutableListOf<String>()
        
        nameError?.let { errors.add("Name: $it") }
        descriptionError?.let { errors.add("Description: $it") }
        videoUrlError?.let { errors.add("Video URL: $it") }
        
        if (primaryMuscle == null) {
            errors.add("Primary muscle group is required")
        }
        
        if (equipment == null) {
            errors.add("Equipment is required")
        }
        
        return errors
    }
}

/**
 * Events for custom exercise creation
 */
sealed class CustomExerciseCreationEvent : ViewModelEvent {
    // Navigation
    object NavigateBack : CustomExerciseCreationEvent()
    data class ExerciseCreated(val exerciseId: String) : CustomExerciseCreationEvent()
    
    // Form Updates - Basic Info
    data class UpdateName(val name: String) : CustomExerciseCreationEvent()
    data class UpdateDescription(val description: String) : CustomExerciseCreationEvent()
    data class UpdateExerciseType(val type: ExerciseType) : CustomExerciseCreationEvent()
    
    // Form Updates - Exercise Details
    data class UpdatePrimaryMuscle(val muscle: ExerciseCategory) : CustomExerciseCreationEvent()
    data class AddSecondaryMuscle(val muscle: ExerciseCategory) : CustomExerciseCreationEvent()
    data class RemoveSecondaryMuscle(val muscle: ExerciseCategory) : CustomExerciseCreationEvent()
    data class UpdateEquipment(val equipment: Equipment) : CustomExerciseCreationEvent()
    data class UpdateDifficulty(val difficulty: Int?) : CustomExerciseCreationEvent()
    
    // Form Updates - Media
    data class SetMainImage(val uri: Uri) : CustomExerciseCreationEvent()
    data class AddImages(val uris: List<Uri>) : CustomExerciseCreationEvent()
    data class RemoveImage(val uri: Uri) : CustomExerciseCreationEvent()
    data class UpdateVideoUrl(val url: String) : CustomExerciseCreationEvent()
    
    
    // Actions
    object CreateExercise : CustomExerciseCreationEvent()
    object Retry : CustomExerciseCreationEvent()
    object ResetForm : CustomExerciseCreationEvent()
}

/**
 * Internal navigation events for the ViewModel
 */
sealed class CustomExerciseNavigationEvent {
    object NavigateBack : CustomExerciseNavigationEvent()
    data class ExerciseCreated(val exerciseId: String) : CustomExerciseNavigationEvent()
}