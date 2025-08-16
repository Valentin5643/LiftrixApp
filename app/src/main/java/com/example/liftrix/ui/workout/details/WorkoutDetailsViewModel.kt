package com.example.liftrix.ui.workout.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.SessionExercise
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.service.PRDetectionService
import com.example.liftrix.domain.usecase.workout.GetWorkoutByIdUseCase
import com.example.liftrix.domain.usecase.workout.GetWorkoutByIdRequest
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

/**
 * ViewModel for the workout details screen.
 * Loads and manages detailed workout information.
 */
@HiltViewModel
class WorkoutDetailsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getWorkoutByIdUseCase: GetWorkoutByIdUseCase,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val workoutRepository: WorkoutRepository,
    private val prDetectionService: PRDetectionService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<WorkoutDetailsUiState>(WorkoutDetailsUiState.Loading)
    val uiState: StateFlow<WorkoutDetailsUiState> = _uiState.asStateFlow()
    
    fun loadWorkoutDetails(workoutId: String) {
        viewModelScope.launch {
            _uiState.value = WorkoutDetailsUiState.Loading
            
            // Get current user ID
            val userId = getCurrentUserIdUseCase() ?: run {
                _uiState.value = WorkoutDetailsUiState.Error(
                    message = "User not authenticated"
                )
                return@launch
            }
            
            val request = GetWorkoutByIdRequest(
                workoutId = WorkoutId(workoutId),
                userId = userId
            )
            
            getWorkoutByIdUseCase(request).fold(
                onSuccess = { workout ->
                    if (workout == null) {
                        _uiState.value = WorkoutDetailsUiState.Error(
                            message = "Workout not found"
                        )
                        return@fold
                    }
                    
                    // Calculate aggregate statistics
                    val totalVolume = calculateTotalVolume(workout.exercises)
                    val totalSets = workout.exercises.sumOf { exercise -> exercise.sets.size }
                    val totalReps = workout.exercises.sumOf { exercise ->
                        exercise.sets.sumOf { set -> set.reps?.count ?: 0 }
                    }
                    
                    // Calculate average rest time
                    // TODO: Implement rest time tracking when available
                    val avgRestTime: Duration? = null
                    
                    // Count PRs
                    // TODO: Implement PR detection when available
                    val prsCount = 0
                    
                    // Format date
                    val formattedDate = workout.date.format(
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                    )
                    
                    // Get workout notes (if any)
                    val notes = workout.notes ?: ""
                    
                    _uiState.value = WorkoutDetailsUiState.Success(
                        workoutName = workout.name,
                        date = formattedDate,
                        duration = workout.getDuration() ?: Duration.ZERO,
                        totalVolume = totalVolume,
                        totalSets = totalSets,
                        totalReps = totalReps,
                        avgRestTime = avgRestTime,
                        prsCount = prsCount,
                        exercises = workout.exercises,
                        notes = notes
                    )
                },
                onFailure = { error ->
                    _uiState.value = WorkoutDetailsUiState.Error(
                        message = (error as? LiftrixError)?.getDisplayMessage() 
                            ?: "Failed to load workout details"
                    )
                    Timber.e("Failed to load workout details: $error")
                }
            )
        }
    }
    
    fun repeatWorkout(workoutId: String) {
        viewModelScope.launch {
            // This would trigger navigation to start a new workout with the same template
            // The actual navigation is handled by the UI layer
            Timber.d("Repeating workout: $workoutId")
        }
    }
    
    private fun calculateTotalVolume(exercises: List<com.example.liftrix.domain.model.Exercise>): Int {
        return exercises.sumOf { exercise ->
            exercise.sets.sumOf { set ->
                val weight = set.weight?.kilograms?.toInt() ?: 0
                val reps = set.reps?.count ?: 0
                weight * reps
            }
        }
    }
    
    private fun LiftrixError.getDisplayMessage(): String {
        return when (this) {
            is LiftrixError.NetworkError -> "Check your internet connection"
            is LiftrixError.BusinessLogicError -> errorMessage
            else -> "Unable to load workout details"
        }
    }
}

// UI State sealed class
sealed class WorkoutDetailsUiState {
    object Loading : WorkoutDetailsUiState()
    
    data class Success(
        val workoutName: String,
        val date: String,
        val duration: Duration,
        val totalVolume: Int,
        val totalSets: Int,
        val totalReps: Int,
        val avgRestTime: Duration?,
        val prsCount: Int,
        val exercises: List<com.example.liftrix.domain.model.Exercise>,
        val notes: String
    ) : WorkoutDetailsUiState()
    
    data class Error(
        val message: String
    ) : WorkoutDetailsUiState()
}