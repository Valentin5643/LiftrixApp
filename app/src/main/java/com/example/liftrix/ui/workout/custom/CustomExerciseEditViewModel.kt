package com.example.liftrix.ui.workout.custom

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.CustomExercise
import com.example.liftrix.domain.model.CustomExerciseId
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseType
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.CustomExerciseRepository
import com.example.liftrix.domain.usecase.exercise.CreateCustomExerciseInput
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for custom exercise editing screen
 * 
 * Manages the form state for editing existing custom exercises with comprehensive validation,
 * image upload support, change tracking, and error handling following MVI pattern.
 * 
 * Key Features:
 * - Load existing exercise data and populate form
 * - Track changes against original state
 * - Support for all exercise fields including media and organization
 * - Delete functionality with confirmation
 * - Navigation event handling with unsaved changes detection
 * - Consistent error handling with LiftrixError system
 */
@HiltViewModel
class CustomExerciseEditViewModel @Inject constructor(
    private val customExerciseRepository: CustomExerciseRepository,
    private val authRepository: AuthRepository
) : ModernBaseViewModel<UiState<CustomExerciseEditFormState>>(initialState = UiState.Loading) {

    // Navigation events
    private val _events = MutableSharedFlow<CustomExerciseEditEvent>()
    val events = _events.asSharedFlow()

    fun handleEvent(event: CustomExerciseEditEvent) {
        when (event) {
            // Navigation
            is CustomExerciseEditEvent.NavigateBack -> handleNavigateBack()

            // Form Updates - Basic Info
            is CustomExerciseEditEvent.UpdateName -> updateName(event.name)
            is CustomExerciseEditEvent.UpdateDescription -> updateDescription(event.description)
            is CustomExerciseEditEvent.UpdateExerciseType -> updateExerciseType(event.type)

            // Form Updates - Exercise Details
            is CustomExerciseEditEvent.UpdatePrimaryMuscle -> updatePrimaryMuscle(event.muscle)
            is CustomExerciseEditEvent.AddSecondaryMuscle -> addSecondaryMuscle(event.muscle)
            is CustomExerciseEditEvent.RemoveSecondaryMuscle -> removeSecondaryMuscle(event.muscle)
            is CustomExerciseEditEvent.UpdateEquipment -> updateEquipment(event.equipment)
            is CustomExerciseEditEvent.UpdateDifficulty -> updateDifficulty(event.difficulty)

            // Form Updates - Media
            is CustomExerciseEditEvent.SetMainImage -> setMainImage(event.uri)
            is CustomExerciseEditEvent.AddImages -> addImages(event.uris)
            is CustomExerciseEditEvent.RemoveImage -> removeImage(event.uri)
            is CustomExerciseEditEvent.UpdateVideoUrl -> updateVideoUrl(event.url)


            // Actions
            is CustomExerciseEditEvent.SaveExercise -> saveExercise()
            is CustomExerciseEditEvent.DeleteExercise -> deleteExercise()

            // Navigation events - handled by emitting to event flow
            is CustomExerciseEditEvent.ExerciseUpdated -> {
                // This event is emitted by the ViewModel itself, not handled
                // No action needed here
            }
            is CustomExerciseEditEvent.ExerciseDeleted -> {
                // This event is emitted by the ViewModel itself, not handled
                // No action needed here
            }
        }
    }

    /**
     * Loads exercise data from repository and populates the form
     */
    fun loadExercise(exerciseId: String) {
        viewModelScope.launch {
            setState(UiState.Loading)
            try {
                val userId = authRepository.getCurrentUserId()
                if (userId == null) {
                    setState(UiState.Error(
                        LiftrixError.AuthenticationError(
                            errorMessage = "User not authenticated",
                            analyticsContext = mapOf("operation" to "LOAD_CUSTOM_EXERCISE")
                        )
                    ))
                    return@launch
                }

                val result = customExerciseRepository.getCustomExercise(userId, CustomExerciseId.fromString(exerciseId))
                result.fold(
                    onSuccess = { exercise ->
                        val formState = fromCustomExercise(exercise)
                        setState(UiState.Success(formState))
                        Timber.d("Successfully loaded exercise for editing: ${exercise.name}")
                    },
                    onFailure = { error ->
                        val liftrixError = if (error is LiftrixError) error else LiftrixError.UnknownError(error.message ?: "Unknown error")
                        setState(UiState.Error(liftrixError))
                        logError(error, "loadExercise")
                    }
                )
            } catch (e: Exception) {
                setState(UiState.Error(LiftrixError.UnknownError(e.message ?: "Unknown error")))
                logError(e, "loadExercise")
            }
        }
    }

    private fun handleNavigateBack() {
        viewModelScope.launch {
            val currentState = getCurrentFormState()
            val hasUnsavedChanges = currentState?.hasChanges() == true
            
            _events.emit(
                CustomExerciseEditEvent.NavigateBack.apply {
                    this as CustomExerciseEditEvent.NavigateBack
                    // Note: hasUnsavedChanges is determined in the UI component
                }
            )
        }
    }

    // Form update methods (similar to creation but with change tracking)
    private fun updateName(name: String) {
        updateFormState { currentState ->
            val nameError = when {
                name.length > CreateCustomExerciseInput.MAX_NAME_LENGTH -> 
                    "Name cannot exceed ${CreateCustomExerciseInput.MAX_NAME_LENGTH} characters"
                else -> null
            }
            currentState.copy(name = name, nameError = nameError)
        }
    }

    private fun updateDescription(description: String) {
        updateFormState { currentState ->
            val descriptionError = when {
                description.length > CreateCustomExerciseInput.MAX_DESCRIPTION_LENGTH -> 
                    "Description cannot exceed ${CreateCustomExerciseInput.MAX_DESCRIPTION_LENGTH} characters"
                else -> null
            }
            currentState.copy(description = description, descriptionError = descriptionError)
        }
    }

    private fun updateExerciseType(type: ExerciseType) {
        updateFormState { currentState ->
            // Auto-adjust equipment based on exercise type
            val newEquipment = when (type) {
                ExerciseType.BODYWEIGHT -> Equipment.BODYWEIGHT_ONLY
                ExerciseType.CARDIO -> null // Let user choose
                else -> currentState.equipment
            }
            
            currentState.copy(
                exerciseType = type,
                equipment = newEquipment
            )
        }
    }

    private fun updatePrimaryMuscle(muscle: ExerciseCategory) {
        updateFormState { currentState ->
            // Remove from secondary muscles if it was there
            val newSecondaryMuscles = currentState.secondaryMuscles - muscle
            currentState.copy(
                primaryMuscle = muscle,
                secondaryMuscles = newSecondaryMuscles
            )
        }
    }

    private fun addSecondaryMuscle(muscle: ExerciseCategory) {
        updateFormState { currentState ->
            if (muscle != currentState.primaryMuscle && 
                !currentState.secondaryMuscles.contains(muscle) &&
                currentState.secondaryMuscles.size < CreateCustomExerciseInput.MAX_SECONDARY_MUSCLES) {
                currentState.copy(
                    secondaryMuscles = currentState.secondaryMuscles + muscle
                )
            } else {
                currentState
            }
        }
    }

    private fun removeSecondaryMuscle(muscle: ExerciseCategory) {
        updateFormState { currentState ->
            currentState.copy(
                secondaryMuscles = currentState.secondaryMuscles - muscle
            )
        }
    }

    private fun updateEquipment(equipment: Equipment) {
        updateFormState { currentState ->
            currentState.copy(equipment = equipment)
        }
    }

    private fun updateDifficulty(difficulty: Int?) {
        updateFormState { currentState ->
            currentState.copy(difficulty = difficulty)
        }
    }

    private fun setMainImage(uri: Uri) {
        updateFormState { currentState ->
            // Remove from additional images if it was there
            val newAdditionalImages = currentState.additionalImageUris.filter { it != uri }
            currentState.copy(
                mainImageUri = uri,
                additionalImageUris = newAdditionalImages
            )
        }
    }

    private fun addImages(uris: List<Uri>) {
        updateFormState { currentState ->
            val existingUris = listOfNotNull(currentState.mainImageUri) + currentState.additionalImageUris
            val newUris = uris.filter { !existingUris.contains(it) }
                .take(CreateCustomExerciseInput.MAX_ADDITIONAL_IMAGES - currentState.additionalImageUris.size)
            
            currentState.copy(
                additionalImageUris = currentState.additionalImageUris + newUris
            )
        }
    }

    private fun removeImage(uri: Uri) {
        updateFormState { currentState ->
            when {
                currentState.mainImageUri == uri -> {
                    // If removing main image, promote first additional image if available
                    val newMainImage = currentState.additionalImageUris.firstOrNull()
                    val newAdditionalImages = currentState.additionalImageUris.drop(1)
                    currentState.copy(
                        mainImageUri = newMainImage,
                        additionalImageUris = newAdditionalImages
                    )
                }
                else -> {
                    currentState.copy(
                        additionalImageUris = currentState.additionalImageUris - uri
                    )
                }
            }
        }
    }

    private fun updateVideoUrl(url: String) {
        updateFormState { currentState ->
            val videoUrlError = if (url.isNotBlank() && !isValidVideoUrl(url)) {
                "Please enter a valid video URL (YouTube, Vimeo, or direct link)"
            } else {
                null
            }
            
            currentState.copy(
                videoUrl = url,
                videoUrlError = videoUrlError
            )
        }
    }


    private fun saveExercise() {
        val currentState = getCurrentFormState() ?: return

        if (!currentState.canSave() || !currentState.hasChanges()) {
            Timber.w("Cannot save exercise - form validation failed or no changes")
            return
        }

        viewModelScope.launch {
            updateFormState { it.copy(isSaving = true) }

            try {
                val userId = authRepository.getCurrentUserId()
                if (userId == null) {
                    updateFormState { it.copy(isSaving = false) }
                    setState(UiState.Error(
                        LiftrixError.AuthenticationError(
                            errorMessage = "User not authenticated",
                            analyticsContext = mapOf("operation" to "SAVE_CUSTOM_EXERCISE")
                        )
                    ))
                    return@launch
                }

                // First get the current exercise, then update it
                val existingExerciseResult = customExerciseRepository.getCustomExercise(
                    userId,
                    CustomExerciseId.fromString(currentState.exerciseId)
                )
                existingExerciseResult.fold(
                    onSuccess = { existingExercise ->
                        val updatedExercise = existingExercise.copy(
                            name = currentState.name.trim(),
                            description = currentState.description.trim().takeIf { it.isNotBlank() },
                            exerciseType = currentState.exerciseType,
                            primaryMuscle = currentState.primaryMuscle!!,
                            equipment = currentState.equipment!!,
                            secondaryMuscles = currentState.secondaryMuscles,
                            difficulty = currentState.difficulty,
                            videoUrl = currentState.videoUrl.trim().takeIf { it.isNotBlank() }
                        )
                        val updateResult = customExerciseRepository.updateCustomExercise(userId, updatedExercise)
                        updateResult.fold(
                            onSuccess = { exercise ->
                                Timber.i("Successfully updated custom exercise: ${exercise.id}")
                                updateFormState { it.copy(isSaving = false) }
                                _events.emit(CustomExerciseEditEvent.ExerciseUpdated(exercise.id.value))
                            },
                            onFailure = { error ->
                                Timber.e("Failed to update custom exercise: $error")
                                updateFormState { it.copy(isSaving = false) }
                                logError(error, "saveExercise")
                            }
                        )
                    },
                    onFailure = { error ->
                        Timber.e("Failed to get existing exercise: $error")
                        updateFormState { it.copy(isSaving = false) }
                        logError(error, "saveExercise")
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Exception during save exercise")
                updateFormState { it.copy(isSaving = false) }
                logError(e, "saveExercise")
            }
        }
    }

    private fun deleteExercise() {
        val currentState = getCurrentFormState() ?: return

        viewModelScope.launch {
            updateFormState { it.copy(isDeleting = true) }

            try {
                val userId = authRepository.getCurrentUserId()
                if (userId == null) {
                    updateFormState { it.copy(isDeleting = false) }
                    setState(UiState.Error(
                        LiftrixError.AuthenticationError(
                            errorMessage = "User not authenticated",
                            analyticsContext = mapOf("operation" to "DELETE_CUSTOM_EXERCISE")
                        )
                    ))
                    return@launch
                }

                val result = customExerciseRepository.deleteCustomExercise(userId, CustomExerciseId.fromString(currentState.exerciseId))
                result.fold(
                    onSuccess = {
                        Timber.i("Successfully deleted custom exercise: ${currentState.exerciseId}")
                        updateFormState { it.copy(isDeleting = false) }
                        _events.emit(CustomExerciseEditEvent.ExerciseDeleted)
                    },
                    onFailure = { error ->
                        Timber.e("Failed to delete custom exercise: $error")
                        updateFormState { it.copy(isDeleting = false) }
                        logError(error, "deleteExercise")
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Exception during delete exercise")
                updateFormState { it.copy(isDeleting = false) }
                logError(e, "deleteExercise")
            }
        }
    }

    // Helper Methods
    private fun updateFormState(transform: (CustomExerciseEditFormState) -> CustomExerciseEditFormState) {
        updateState { currentState ->
            when (currentState) {
                is UiState.Success -> UiState.Success(transform(currentState.data))
                else -> currentState
            }
        }
    }

    private fun getCurrentFormState(): CustomExerciseEditFormState? {
        return when (val currentState = _uiState.value) {
            is UiState.Success -> currentState.data
            else -> null
        }
    }

    private fun isValidVideoUrl(url: String): Boolean {
        val trimmedUrl = url.trim()
        return trimmedUrl.startsWith("http://") || 
               trimmedUrl.startsWith("https://") ||
               trimmedUrl.contains("youtube.com") ||
               trimmedUrl.contains("youtu.be") ||
               trimmedUrl.contains("vimeo.com")
    }
}

/**
 * Extension to create edit form state from existing exercise
 */
private fun fromCustomExercise(exercise: CustomExercise): CustomExerciseEditFormState {
    val originalState = CustomExerciseFormState(
        name = exercise.name,
        description = exercise.description ?: "",
        exerciseType = exercise.exerciseType,
        primaryMuscle = exercise.primaryMuscle,
        secondaryMuscles = exercise.secondaryMuscles,
        equipment = exercise.equipment,
        difficulty = exercise.difficulty,
        mainImageUri = null, // URIs are not stored in domain model
        additionalImageUris = emptyList(),
        videoUrl = exercise.videoUrl ?: ""
    )
    
    return CustomExerciseEditFormState(
        exerciseId = exercise.id.value,
        name = exercise.name,
        description = exercise.description ?: "",
        exerciseType = exercise.exerciseType,
        primaryMuscle = exercise.primaryMuscle,
        secondaryMuscles = exercise.secondaryMuscles,
        equipment = exercise.equipment,
        difficulty = exercise.difficulty,
        videoUrl = exercise.videoUrl ?: "",
        originalState = originalState,
        createdAt = exercise.createdAt.toString(),
        lastModified = exercise.updatedAt.toString(),
        usageCount = 0 // Would need to be fetched separately
    )
}