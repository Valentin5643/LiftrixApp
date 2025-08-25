package com.example.liftrix.ui.workout.completion

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.service.MediaProcessingService
import com.example.liftrix.domain.service.PRDetectionService
import com.example.liftrix.domain.usecase.workout.GetWorkoutByIdUseCase
import com.example.liftrix.domain.usecase.workout.GetWorkoutByIdRequest
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.ui.navigation.LiftrixRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Duration
import javax.inject.Inject

/**
 * ViewModel for the enhanced post-workout summary screen.
 * Aggregates workout data and handles sharing functionality.
 */
@HiltViewModel
class PostWorkoutSummaryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val getWorkoutByIdUseCase: GetWorkoutByIdUseCase,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val prDetectionService: PRDetectionService,
    private val mediaProcessingService: MediaProcessingService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<PostWorkoutUiState>(PostWorkoutUiState.Loading)
    val uiState: StateFlow<PostWorkoutUiState> = _uiState.asStateFlow()
    
    private var currentUserId: String? = null
    
    fun loadWorkoutSummary(workoutId: String) {
        viewModelScope.launch {
            _uiState.value = PostWorkoutUiState.Loading
            
            // Get current user ID
            val userId = getCurrentUserIdUseCase() ?: run {
                _uiState.value = PostWorkoutUiState.Error(
                    message = "User not authenticated"
                )
                return@launch
            }
            
            // Get workout details
            val request = GetWorkoutByIdRequest(
                workoutId = WorkoutId(workoutId),
                userId = userId
            )
            
            getWorkoutByIdUseCase(request).fold(
                onSuccess = { workout ->
                    if (workout == null) {
                        _uiState.value = PostWorkoutUiState.Error(
                            message = "Workout not found"
                        )
                        return@fold
                    }
                    currentUserId = workout.userId
                    
                    // Detect personal records
                    val personalRecords = detectPersonalRecords(workout)
                    
                    // Calculate metrics
                    val totalVolume = calculateTotalVolume(workout)
                    val totalSets = workout.exercises.sumOf { it.sets.size }
                    val totalReps = workout.exercises.sumOf { exercise ->
                        exercise.sets.sumOf { set -> set.reps?.count ?: 0 }
                    }
                    
                    // Calculate calories (simplified calculation)
                    val caloriesBurned = estimateCaloriesBurned(
                        duration = workout.getDuration() ?: Duration.ZERO,
                        totalVolume = totalVolume
                    )
                    
                    // Create exercise summaries
                    val exerciseSummaries = workout.exercises.map { exercise ->
                        val repsRange = exercise.sets
                            .mapNotNull { set -> set.reps?.count }
                            .distinct()
                            .sorted()
                        
                        ExerciseSummary(
                            name = exercise.libraryExercise.name,
                            sets = exercise.sets.size,
                            reps = when (repsRange.size) {
                                0 -> "0"
                                1 -> repsRange[0].toString()
                                else -> "${repsRange.first()}-${repsRange.last()}"
                            }
                        )
                    }
                    
                    // Generate or get workout image
                    val workoutImageUrl = generateWorkoutImage(workout)
                    
                    _uiState.value = PostWorkoutUiState.Success(
                        workoutName = workout.name,
                        workoutDate = workout.date,
                        duration = workout.getDuration() ?: Duration.ZERO,
                        totalVolume = totalVolume,
                        totalSets = totalSets,
                        totalReps = totalReps,
                        prsCount = personalRecords.size,
                        caloriesBurned = caloriesBurned,
                        personalRecords = personalRecords,
                        exercises = exerciseSummaries,
                        workoutImageUrl = workoutImageUrl
                    )
                },
                onFailure = { error ->
                    _uiState.value = PostWorkoutUiState.Error(
                        message = (error as? LiftrixError)?.getDisplayMessage() 
                            ?: "Failed to load workout summary"
                    )
                    Timber.e("Failed to load workout summary: $error")
                }
            )
        }
    }
    
    fun shareWorkout(workoutId: String, method: ShareMethod) {
        viewModelScope.launch {
            val userId = currentUserId ?: return@launch
            
            when (method) {
                ShareMethod.InstagramStory -> {
                    shareToInstagramStory(workoutId, userId)
                }
                ShareMethod.CopyLink -> {
                    copyWorkoutLink(workoutId, userId)
                }
                ShareMethod.WhatsApp -> {
                    shareToWhatsApp(workoutId, userId)
                }
                ShareMethod.SaveImage -> {
                    saveWorkoutImage(workoutId, userId)
                }
            }
        }
    }
    
    /**
     * Discards the workout by deleting it from the database.
     * This is called when the user chooses to discard the workout instead of sharing it.
     */
    fun discardWorkout(workoutId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val userId = currentUserId ?: getCurrentUserIdUseCase() ?: run {
                Timber.e("Cannot discard workout - user not authenticated")
                return@launch
            }
            
            try {
                // Delete the workout from the repository
                val result = workoutRepository.deleteWorkout(
                    workoutId = WorkoutId(workoutId),
                    userId = userId
                )
                
                result.fold(
                    onSuccess = {
                        Timber.d("Workout $workoutId successfully discarded")
                        onSuccess() // Navigate away after successful deletion
                    },
                    onFailure = { error ->
                        Timber.e("Failed to discard workout $workoutId: $error")
                        // Still navigate away even if deletion fails to not block the user
                        onSuccess()
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Exception while discarding workout $workoutId")
                // Still navigate away to not block the user
                onSuccess()
            }
        }
    }
    
    
    private fun detectPersonalRecords(workout: com.example.liftrix.domain.model.Workout): List<PersonalRecord> {
        val records = mutableListOf<PersonalRecord>()
        
        workout.exercises.forEach { exercise ->
            // Check for 1RM PR
            val maxWeight = exercise.sets.maxOfOrNull { set ->
                set.weight?.kilograms ?: 0.0
            } ?: 0.0
            
            // Check if this is a PR (simplified - would need historical data)
            if (maxWeight > 0) {
                // TODO: Implement actual PR detection when service is ready
                val isPR = false // Placeholder
                
                if (isPR) {
                    records.add(
                        PersonalRecord(
                            exerciseName = exercise.libraryExercise.name,
                            type = "Max Weight",
                            value = "${maxWeight}kg"
                        )
                    )
                }
            }
            
            // Check for volume PR
            val totalVolume = exercise.sets.sumOf { set ->
                val weight = set.weight?.kilograms ?: 0.0
                val reps = set.reps?.count ?: 0
                (weight * reps).toInt()
            }
            
            if (totalVolume > 0) {
                // TODO: Implement actual volume PR detection when service is ready
                val isVolumePR = false // Placeholder
                
                if (isVolumePR) {
                    records.add(
                        PersonalRecord(
                            exerciseName = exercise.libraryExercise.name,
                            type = "Volume PR",
                            value = "${totalVolume}kg"
                        )
                    )
                }
            }
        }
        
        return records
    }
    
    private fun calculateTotalVolume(workout: com.example.liftrix.domain.model.Workout): Int {
        return workout.exercises.sumOf { exercise ->
            exercise.sets.sumOf { set ->
                val weight = set.weight?.kilograms?.toInt() ?: 0
                val reps = set.reps?.count ?: 0
                weight * reps
            }
        }
    }
    
    private fun estimateCaloriesBurned(duration: Duration, totalVolume: Int): Int {
        // Simplified calorie calculation
        // Base rate: 5 calories per minute
        // Adjustment for intensity based on volume
        val minutes = duration.toMinutes().toInt()
        val baseCalories = minutes * 5
        val volumeBonus = (totalVolume / 1000) * 2 // 2 calories per ton lifted
        
        return baseCalories + volumeBonus
    }
    
    private suspend fun generateWorkoutImage(workout: com.example.liftrix.domain.model.Workout): String? {
        // Check if workout already has an associated image
        // Otherwise, generate a default workout image
        return try {
            // This would call the media service to generate or retrieve an image
            mediaProcessingService.generateWorkoutSummaryImage(workout)
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate workout image")
            null
        }
    }
    
    private suspend fun shareToInstagramStory(workoutId: String, userId: String) {
        val state = _uiState.value as? PostWorkoutUiState.Success ?: return
        
        // For now, just log the share action
        // TODO: Implement actual Instagram sharing when ShareToExternalPlatformUseCase is available
        Timber.d("Share to Instagram Story requested for workout: $workoutId")
        
        // Generate the workout image if not already done
        state.workoutImageUrl?.let { imageUrl ->
            // Open Instagram with the image
            Timber.d("Would share image to Instagram: $imageUrl")
        }
    }
    
    private suspend fun copyWorkoutLink(workoutId: String, userId: String) {
        // TODO: Implement when ShareWorkoutUseCase is available
        Timber.d("Copy workout link requested for workout: $workoutId")
        
        // For now, generate a simple shareable link
        val link = "https://liftrix.app/workout/$workoutId"
        Timber.d("Generated workout link: $link")
    }
    
    private suspend fun shareToWhatsApp(workoutId: String, userId: String) {
        val state = _uiState.value as? PostWorkoutUiState.Success ?: return
        
        // TODO: Implement actual WhatsApp sharing when ShareToExternalPlatformUseCase is available
        Timber.d("Share to WhatsApp requested for workout: $workoutId")
        
        val message = """
            Just completed: ${state.workoutName} 💪
            Duration: ${formatDuration(state.duration)}
            Volume: ${state.totalVolume/1000.0} tons
            Exercises: ${state.exercises.size}
        """.trimIndent()
        
        Timber.d("Would share to WhatsApp: $message")
    }
    
    private suspend fun saveWorkoutImage(workoutId: String, userId: String) {
        val state = _uiState.value as? PostWorkoutUiState.Success ?: return
        
        // Generate and save workout summary image
        state.workoutImageUrl?.let { imageUrl ->
            mediaProcessingService.saveImageToGallery(imageUrl, "workout_${workoutId}")
                .fold(
                    onSuccess = {
                        Timber.d("Workout image saved to gallery")
                    },
                    onFailure = { error ->
                        Timber.e("Failed to save workout image: $error")
                    }
                )
        }
    }
    
    private fun formatDuration(duration: Duration): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutesPart()
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }
    
    private fun LiftrixError.getDisplayMessage(): String {
        return when (this) {
            is LiftrixError.NetworkError -> "Check your internet connection"
            is LiftrixError.BusinessLogicError -> errorMessage
            else -> "Something went wrong. Please try again."
        }
    }
}

// UI State sealed class
sealed class PostWorkoutUiState {
    object Loading : PostWorkoutUiState()
    
    data class Success(
        val workoutName: String,
        val workoutDate: java.time.LocalDate,
        val duration: Duration,
        val totalVolume: Int,
        val totalSets: Int,
        val totalReps: Int,
        val prsCount: Int,
        val caloriesBurned: Int?,
        val personalRecords: List<PersonalRecord>,
        val exercises: List<ExerciseSummary>,
        val workoutImageUrl: String?
    ) : PostWorkoutUiState()
    
    data class Error(
        val message: String
    ) : PostWorkoutUiState()
}