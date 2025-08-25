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
import com.example.liftrix.domain.usecase.workout.GetPreviousWorkoutDataUseCase
import com.example.liftrix.domain.usecase.workout.GetPreviousWorkoutDataRequest
import com.example.liftrix.domain.usecase.workout.PreviousWorkoutData
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.service.PRComparison
import com.example.liftrix.domain.service.PersonalRecord
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
    private val prDetectionService: PRDetectionService,
    private val getPreviousWorkoutDataUseCase: GetPreviousWorkoutDataUseCase
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
                    
                    // Calculate average rest time from session exercises
                    val avgRestTime = calculateAverageRestTime(workout.exercises)
                    
                    // Detect PRs using PR detection service
                    val personalRecords = detectPersonalRecords(workout, userId)
                    val prsCount = personalRecords.size
                    
                    // Get previous workout data for comparison
                    val exerciseLibraryIds = workout.exercises.map { it.libraryExercise.id }
                    val previousWorkoutData = getPreviousWorkoutData(userId, exerciseLibraryIds, workout.id.value)
                    
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
    ): PreviousWorkoutData? {
        val request = GetPreviousWorkoutDataRequest(
            userId = userId,
            exerciseLibraryIds = exerciseLibraryIds,
            excludeWorkoutId = currentWorkoutId
        )
        
        return getPreviousWorkoutDataUseCase(request).fold(
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
        previousWorkoutData: PreviousWorkoutData?
    ): List<ExerciseWithPRData> {
        return exercises.mapIndexed { index, exercise ->
            val exerciseLibraryId = exercise.libraryExercise.id
            val exercisePRs = personalRecords.filter { it.exerciseName == exerciseLibraryId }
            
            val setDataWithPRs = exercise.sets.mapIndexed { setIndex, set ->
                val isPR = exercisePRs.any { pr ->
                    pr.weight == set.weight?.kilograms && pr.reps == set.reps?.count
                }
                val previousSetData = previousWorkoutData?.getPreviousSetData(exerciseLibraryId, setIndex + 1)
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
        val exerciseDataWithPRs: List<ExerciseWithPRData>,
        val personalRecords: List<PersonalRecord>,
        val previousWorkoutData: PreviousWorkoutData?,
        val notes: String
    ) : WorkoutDetailsUiState()
    
    data class Error(
        val message: String
    ) : WorkoutDetailsUiState()
}

/**
 * Data class representing an exercise with PR and previous workout data
 */
data class ExerciseWithPRData(
    val exercise: com.example.liftrix.domain.model.Exercise,
    val exerciseNumber: Int,
    val setsWithPRData: List<SetWithPRData>,
    val totalPRsInExercise: Int
)

/**
 * Data class representing a set with PR and previous set information
 */
data class SetWithPRData(
    val set: com.example.liftrix.domain.model.ExerciseSet,
    val isPersonalRecord: Boolean,
    val previousSetData: com.example.liftrix.domain.usecase.workout.PreviousSetData?,
    val restTime: Duration?
)