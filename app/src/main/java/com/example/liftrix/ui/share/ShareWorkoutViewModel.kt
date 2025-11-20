package com.example.liftrix.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.ShareableContent
import com.example.liftrix.domain.model.ShareableContentType
import com.example.liftrix.domain.model.SocialPlatform
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for ShareWorkout screen
 * 
 * Manages workout sharing functionality including social platform sharing,
 * QR code generation, and shareable content creation.
 */
@HiltViewModel
class ShareWorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val authQueryUseCase: AuthQueryUseCase
) : ViewModel() {

    data class ShareWorkoutUiState(
        val isLoading: Boolean = false,
        val shareableContent: ShareableContent? = null,
        val qrCodeData: String? = null,
        val error: LiftrixError? = null
    )

    private val _uiState = MutableStateFlow(ShareWorkoutUiState())
    val uiState: StateFlow<ShareWorkoutUiState> = _uiState.asStateFlow()

    /**
     * Load workout data and prepare it for sharing
     */
    fun loadWorkout(workoutId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                // Get current user ID
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = LiftrixError.BusinessLogicError(
                                code = "USER_NOT_AUTHENTICATED",
                                errorMessage = "User must be authenticated to share workouts",
                                analyticsContext = mapOf("operation" to "SHARE_WORKOUT")
                            )
                        )
                        return@launch
                    }
                )
                
                val result = workoutRepository.getWorkoutById(WorkoutId.fromString(workoutId), userId)
                result.fold(
                    onSuccess = { workout ->
                        if (workout == null) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = LiftrixError.BusinessLogicError(
                                    code = "WORKOUT_NOT_FOUND",
                                    errorMessage = "Workout not found",
                                    analyticsContext = mapOf("workoutId" to workoutId)
                                )
                            )
                            return@launch
                        }
                        
                        val duration = if (workout.startTime != null && workout.endTime != null) {
                            java.time.Duration.between(workout.startTime, workout.endTime)
                        } else null
                        val durationText = if (duration != null) {
                            "${duration.toMinutes()} minutes"
                        } else {
                            "Duration not recorded"
                        }
                        
                        val shareableContent = ShareableContent(
                            id = workoutId,
                            type = ShareableContentType.WORKOUT,
                            title = workout.name,
                            subtitle = "${workout.exercises.size} exercises",
                            stats = mapOf(
                                "Duration" to durationText,
                                "Exercises" to workout.exercises.size.toString(),
                                "Total Sets" to workout.getTotalSets().toString(),
                                "Completed Sets" to workout.getCompletedSets().toString()
                            ),
                            imageUrl = null, // Could add workout thumbnail in future
                            userAvatar = null,
                            metadata = mapOf(
                                "status" to workout.status.name,
                                "date" to workout.date.toString()
                            )
                        )
                        
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            shareableContent = shareableContent
                        )
                    },
                    onFailure = { throwable ->
                        val liftrixError = if (throwable is LiftrixError) {
                            throwable
                        } else {
                            LiftrixError.BusinessLogicError(
                                code = "WORKOUT_LOAD_FAILED",
                                errorMessage = "Failed to load workout: ${throwable.message}",
                                analyticsContext = mapOf("workoutId" to workoutId)
                            )
                        }
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = liftrixError
                        )
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Error loading workout for sharing")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = LiftrixError.BusinessLogicError(
                        code = "WORKOUT_LOAD_FAILED",
                        errorMessage = "Failed to load workout for sharing",
                        analyticsContext = mapOf("workoutId" to workoutId)
                    )
                )
            }
        }
    }

    /**
     * Share workout to specified social platform
     */
    fun shareWorkout(platform: SocialPlatform, message: String, shareUrl: String) {
        viewModelScope.launch {
            try {
                // Implementation would integrate with platform-specific sharing APIs
                Timber.d("Sharing workout to ${platform.name}: $message - $shareUrl")
                // For now, just log the sharing attempt
            } catch (e: Exception) {
                Timber.e(e, "Error sharing workout to ${platform.name}")
            }
        }
    }

    /**
     * Generate QR code for workout sharing
     */
    fun generateQRCode(shareUrl: String) {
        viewModelScope.launch {
            try {
                // Implementation would integrate with QR code generation service
                _uiState.value = _uiState.value.copy(qrCodeData = shareUrl)
                Timber.d("Generated QR code for: $shareUrl")
            } catch (e: Exception) {
                Timber.e(e, "Error generating QR code")
            }
        }
    }
}