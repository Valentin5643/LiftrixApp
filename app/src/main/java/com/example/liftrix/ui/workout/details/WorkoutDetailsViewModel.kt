package com.example.liftrix.ui.workout.details

import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.SessionExercise
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.service.PRDetectionService
import com.example.liftrix.domain.usecase.workout.WorkoutQueryUseCase
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.domain.service.PRComparison
import com.example.liftrix.domain.service.PersonalRecord
import com.example.liftrix.domain.service.PrivacyEnforcementService
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
    private val workoutQueryUseCase: WorkoutQueryUseCase,
    private val authQueryUseCase: AuthQueryUseCase,
    private val workoutRepository: WorkoutRepository,
    private val prDetectionService: PRDetectionService,
    private val privacyEnforcementService: PrivacyEnforcementService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<WorkoutDetailsUiState>(WorkoutDetailsUiState.Loading)
    val uiState: StateFlow<WorkoutDetailsUiState> = _uiState.asStateFlow()
    
    fun loadWorkoutDetails(workoutId: String) {
        viewModelScope.launch {
            _uiState.value = WorkoutDetailsUiState.Loading

            // Get current user ID
            val currentUserId = authQueryUseCase(waitForAuth = false).fold(
                onSuccess = { it.value },
                onFailure = {
                    _uiState.value = WorkoutDetailsUiState.Error(
                        message = "User not authenticated"
                    )
                    return@launch
                }
            )

            // First try to get workout for current user (most common case)
            var workout: com.example.liftrix.domain.model.Workout? = null
            var workoutOwnerId: String = currentUserId

            // Try to get workout as current user first
            workoutQueryUseCase.getById(WorkoutId(workoutId), currentUserId).fold(
                onSuccess = { userWorkout ->
                    if (userWorkout != null) {
                        workout = userWorkout
                        workoutOwnerId = userWorkout.userId
                    }
                },
                onFailure = { error ->
                    Timber.d("Could not load workout for current user: $error")
                }
            )

            // If not found as current user's workout, it might be another user's workout
            // For now, we can only view our own workouts due to repository constraints
            // This is a limitation that would need repository-level changes to support
            if (workout == null) {
                _uiState.value = WorkoutDetailsUiState.Error(
                    message = "Workout not found or you don't have permission to view it"
                )
                return@launch
            }

            val isOwnWorkout = workout.userId == currentUserId

            // Check privacy if not own workout (currently this won't happen due to repository constraints)
            if (!isOwnWorkout) {
                val canView = privacyEnforcementService.canViewWorkout(
                    workoutOwnerId = workout.userId,
                    viewerId = currentUserId
                )

                if (!canView) {
                    _uiState.value = WorkoutDetailsUiState.Error(
                        message = "This workout is private or you don't have permission to view it"
                    )
                    return@launch
                }
            }

            // Load full workout details
            loadFullWorkoutDetails(workout, currentUserId, isOwnWorkout)
        }
    }
    
    private suspend fun loadFullWorkoutDetails(
        workout: com.example.liftrix.domain.model.Workout,
        currentUserId: String,
        isOwnWorkout: Boolean
    ) {
        // Calculate aggregate statistics
        val totalVolume = calculateTotalVolume(workout.exercises)
        val totalSets = workout.exercises.sumOf { exercise -> exercise.sets.size }
        val totalReps = workout.exercises.sumOf { exercise ->
            exercise.sets.sumOf { set -> set.reps?.count ?: 0 }
        }
        
        // Calculate average rest time from session exercises
        val avgRestTime = calculateAverageRestTime(workout.exercises)
        
        // Detect PRs using PR detection service (only for own workouts)
        val personalRecords = if (isOwnWorkout) {
            detectPersonalRecords(workout, currentUserId)
        } else {
            emptyList()
        }
        val prsCount = personalRecords.size
        
        // Get previous workout data for comparison (only for own workouts)
        val exerciseLibraryIds = workout.exercises.map { it.libraryExercise.id }
        val previousWorkoutData = if (isOwnWorkout) {
            getPreviousWorkoutData(currentUserId, exerciseLibraryIds, workout.id.value)
        } else {
            null
        }
        
        // Create exercise data with PR and previous set information
        val exerciseDataWithPRs = createExerciseDataWithPRs(workout.exercises, personalRecords, previousWorkoutData)
        
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
            exerciseDataWithPRs = exerciseDataWithPRs,
            personalRecords = personalRecords,
            previousWorkoutData = previousWorkoutData,
            notes = notes,
            isOwnWorkout = isOwnWorkout
        )
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
    
    /**
     * Detects personal records for the workout
     */
    private suspend fun detectPersonalRecords(
        workout: com.example.liftrix.domain.model.Workout,
        userId: String
    ): List<PersonalRecord> {
        return prDetectionService.detectPersonalRecords(workout, userId).fold(
            onSuccess = { it },
            onFailure = { error ->
                Timber.e("Failed to detect PRs: $error")
                emptyList()
            }
        )
    }
    
    /**
     * Gets previous workout data for exercise comparison
     */
    private suspend fun getPreviousWorkoutData(
        userId: String,
        exerciseLibraryIds: List<String>,
        currentWorkoutId: String
    ): Map<String, List<com.example.liftrix.domain.usecase.workout.PreviousSetData>>? {
        return workoutQueryUseCase.getPreviousWorkoutData(
            userId = userId,
            exerciseLibraryIds = exerciseLibraryIds,
            excludeWorkoutId = currentWorkoutId
        ).fold(
            onSuccess = { it },
            onFailure = { error ->
                Timber.e("Failed to get previous workout data: $error")
                null
            }
        )
    }
    
    /**
     * Calculates average rest time from exercises with rest time data
     */
    private fun calculateAverageRestTime(exercises: List<com.example.liftrix.domain.model.Exercise>): Duration? {
        val restTimes = exercises.mapNotNull { exercise ->
            when (exercise) {
                is com.example.liftrix.domain.model.SessionExercise -> exercise.restTimeSeconds
                else -> null
            }
        }.filter { it > 0 }
        
        return if (restTimes.isNotEmpty()) {
            val avgSeconds = restTimes.average().toInt()
            Duration.ofSeconds(avgSeconds.toLong())
        } else {
            null
        }
    }
    
    /**
     * Creates exercise data enriched with PR and previous set information
     */
    private fun createExerciseDataWithPRs(
        exercises: List<com.example.liftrix.domain.model.Exercise>,
        personalRecords: List<PersonalRecord>,
        previousWorkoutData: Map<String, List<com.example.liftrix.domain.usecase.workout.PreviousSetData>>?
    ): List<ExerciseWithPRData> {
        return exercises.mapIndexed { index, exercise ->
            val exerciseLibraryId = exercise.libraryExercise.id
            val exercisePRs = personalRecords.filter { it.exerciseName == exerciseLibraryId }

            val setDataWithPRs = exercise.sets.mapIndexed { setIndex, set ->
                val isPR = exercisePRs.any { pr ->
                    pr.weight == set.weight?.kilograms && pr.reps == set.reps?.count
                }
                val previousSetData = previousWorkoutData?.get(exerciseLibraryId)?.getOrNull(setIndex)
                val restTime = if (exercise is com.example.liftrix.domain.model.SessionExercise) {
                    exercise.restTimeSeconds?.let { Duration.ofSeconds(it.toLong()) }
                } else {
                    null
                }
                
                SetWithPRData(
                    set = set,
                    isPersonalRecord = isPR,
                    previousSetData = previousSetData,
                    restTime = restTime
                )
            }
            
            ExerciseWithPRData(
                exercise = exercise,
                exerciseNumber = index + 1,
                setsWithPRData = setDataWithPRs,
                totalPRsInExercise = exercisePRs.size
            )
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
@Stable
sealed class WorkoutDetailsUiState {
    @Stable
    object Loading : WorkoutDetailsUiState()

    @Stable
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
        val exerciseDataWithPRs: List<ExerciseWithPRData>,
        val personalRecords: List<PersonalRecord>,
        val previousWorkoutData: Map<String, List<com.example.liftrix.domain.usecase.workout.PreviousSetData>>?,
        val notes: String,
        val isOwnWorkout: Boolean
    ) : WorkoutDetailsUiState()

    @Stable
    data class Error(
        val message: String
    ) : WorkoutDetailsUiState()
}

/**
 * Data class representing an exercise with PR and previous workout data
 */
@Stable
data class ExerciseWithPRData(
    val exercise: com.example.liftrix.domain.model.Exercise,
    val exerciseNumber: Int,
    val setsWithPRData: List<SetWithPRData>,
    val totalPRsInExercise: Int
)

@Stable
data class SetWithPRData(
    val set: com.example.liftrix.domain.model.ExerciseSet,
    val isPersonalRecord: Boolean,
    val previousSetData: com.example.liftrix.domain.usecase.workout.PreviousSetData?,
    val restTime: Duration?
)