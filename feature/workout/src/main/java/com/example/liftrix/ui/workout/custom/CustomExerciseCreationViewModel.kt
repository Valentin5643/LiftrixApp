package com.example.liftrix.ui.workout.custom

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseType
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.usecase.exercise.CreateCustomExerciseInput
import com.example.liftrix.domain.usecase.exercise.CreateCustomExerciseUseCase
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
import com.example.liftrix.domain.model.error.LiftrixError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for custom exercise creation screen
 * 
 * Manages the form state and handles exercise creation with comprehensive validation,
 * image upload support, and error handling following MVI pattern.
 */
@HiltViewModel
class CustomExerciseCreationViewModel @Inject constructor(
    private val createCustomExerciseUseCase: CreateCustomExerciseUseCase,
    private val authRepository: AuthRepository
) : ModernBaseViewModel<UiState<CustomExerciseFormState>>(initialState = UiState.Success(CustomExerciseFormState())) {

    companion object {
        private const val MAX_SECONDARY_MUSCLES = 3
        private const val MAX_ADDITIONAL_IMAGES = 5
    }

    // Navigation events
    private val _events = MutableSharedFlow<CustomExerciseNavigationEvent>()
    val events = _events.asSharedFlow()

    fun handleEvent(event: CustomExerciseCreationEvent) {
        when (event) {
            is CustomExerciseCreationEvent.ExerciseCreated -> {
                viewModelScope.launch {
                    _events.emit(CustomExerciseNavigationEvent.ExerciseCreated(event.exerciseId))
                }
            }
            // Navigation
            is CustomExerciseCreationEvent.NavigateBack -> handleNavigateBack()

            // Basic Info Updates
            is CustomExerciseCreationEvent.UpdateName -> updateName(event.name)
            is CustomExerciseCreationEvent.UpdateDescription -> updateDescription(event.description)
            is CustomExerciseCreationEvent.UpdateExerciseType -> updateExerciseType(event.type)

            // Exercise Details Updates
            is CustomExerciseCreationEvent.UpdatePrimaryMuscle -> updatePrimaryMuscle(event.muscle)
            is CustomExerciseCreationEvent.AddSecondaryMuscle -> addSecondaryMuscle(event.muscle)
            is CustomExerciseCreationEvent.RemoveSecondaryMuscle -> removeSecondaryMuscle(event.muscle)
            is CustomExerciseCreationEvent.UpdateEquipment -> updateEquipment(event.equipment)
            is CustomExerciseCreationEvent.UpdateDifficulty -> updateDifficulty(event.difficulty)

            // Media Updates
            is CustomExerciseCreationEvent.SetMainImage -> setMainImage(event.uri)
            is CustomExerciseCreationEvent.AddImages -> addImages(event.uris)
            is CustomExerciseCreationEvent.RemoveImage -> removeImage(event.uri)
            is CustomExerciseCreationEvent.UpdateVideoUrl -> updateVideoUrl(event.url)


            // Actions
            is CustomExerciseCreationEvent.CreateExercise -> createExercise()
            is CustomExerciseCreationEvent.Retry -> retry()
            is CustomExerciseCreationEvent.ResetForm -> resetForm()
        }
    }

    private fun handleNavigateBack() {
        viewModelScope.launch {
            _events.emit(CustomExerciseNavigationEvent.NavigateBack)
        }
    }

    // Basic Info Updates
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

    // Exercise Details Updates
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

    // Media Updates
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


    // Actions
    private fun createExercise() {
        val currentState = getCurrentFormState() ?: return
        
        if (!currentState.canCreate()) {
            Timber.w("Cannot create exercise - form validation failed")
            return
        }

        viewModelScope.launch {
            updateFormState { it.copy(isCreating = true) }
            
            try {
                val input = CreateCustomExerciseInput(
                    name = currentState.name.trim(),
                    description = currentState.description.trim().takeIf { it.isNotBlank() },
                    exerciseType = currentState.exerciseType,
                    primaryMuscle = currentState.primaryMuscle!!,
                    equipment = currentState.equipment!!,
                    secondaryMuscles = currentState.secondaryMuscles,
                    difficulty = currentState.difficulty,
                    mainImage = currentState.mainImageUri,
                    additionalImages = currentState.additionalImageUris,
                    videoUrl = currentState.videoUrl.trim().takeIf { it.isNotBlank() }
                )

                createCustomExerciseUseCase(input).fold(
                    onSuccess = { exercise ->
                        Timber.i("Successfully created custom exercise: ${exercise.id}")
                        // Show success state with exercise name
                        updateFormState { 
                            it.copy(
                                isCreating = false,
                                isCreated = true,
                                createdExerciseName = exercise.name
                            ) 
                        }
                        
                        // Wait 2 seconds to show success message, then navigate back
                        kotlinx.coroutines.delay(2000)
                        _events.emit(CustomExerciseNavigationEvent.ExerciseCreated(exercise.id.value))
                    },
                    onFailure = { error ->
                        val liftrixError = if (error is com.example.liftrix.domain.model.error.LiftrixError) {
                            error
                        } else {
                            com.example.liftrix.domain.model.error.LiftrixError.BusinessLogicError(
                                code = "EXERCISE_CREATION_FAILED",
                                errorMessage = "Failed to create custom exercise: ${error.message}",
                                analyticsContext = mapOf("operation" to "CREATE_CUSTOM_EXERCISE")
                            )
                        }
                        Timber.e(error, "Failed to create custom exercise")
                        updateFormState { it.copy(isCreating = false) }
                        updateState { UiState.Error(liftrixError) }
                    }
                )
            } catch (e: Exception) {
                val liftrixError = LiftrixError.BusinessLogicError(
                    code = "EXERCISE_CREATION_EXCEPTION",
                    errorMessage = "Unexpected error during exercise creation: ${e.message}",
                    analyticsContext = mapOf("operation" to "CREATE_CUSTOM_EXERCISE")
                )
                Timber.e(e, "Exception during exercise creation")
                updateFormState { it.copy(isCreating = false) }
                updateState { UiState.Error(liftrixError) }
            }
        }
    }

    private fun retry() {
        updateState { UiState.Success(getCurrentFormState() ?: CustomExerciseFormState()) }
    }

    private fun resetForm() {
        updateState { UiState.Success(CustomExerciseFormState()) }
    }

    // Helper Methods
    private fun updateFormState(transform: (CustomExerciseFormState) -> CustomExerciseFormState) {
        updateState { currentState ->
            when (currentState) {
                is UiState.Success -> UiState.Success(transform(currentState.data))
                else -> currentState
            }
        }
    }

    private fun getCurrentFormState(): CustomExerciseFormState? {
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