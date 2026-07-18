package com.example.liftrix.ui.workout.completion

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.interactor.auth.AuthInteractor
import com.example.liftrix.domain.interactor.workout.WorkoutInteractor
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.service.MediaProcessingService
import com.example.liftrix.domain.service.PRDetectionService
import com.example.liftrix.domain.repository.PersonalRecordRepository
import com.example.liftrix.domain.usecase.sharing.ShareToExternalPlatformUseCase
import com.example.liftrix.domain.usecase.sharing.ShareRequest
import com.example.liftrix.domain.usecase.sharing.SharePlatform
import com.example.liftrix.domain.usecase.sharing.ShareContentType
import com.example.liftrix.domain.usecase.sharing.ShareWorkoutData
import com.example.liftrix.domain.model.WorkoutId
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
    private val workoutInteractor: WorkoutInteractor,
    private val authInteractor: AuthInteractor,
    private val prDetectionService: PRDetectionService,
    private val personalRecordRepository: PersonalRecordRepository,
    private val mediaProcessingService: MediaProcessingService,
    private val shareToExternalPlatformUseCase: ShareToExternalPlatformUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<PostWorkoutUiState>(PostWorkoutUiState.Loading)
    val uiState: StateFlow<PostWorkoutUiState> = _uiState.asStateFlow()
    
    private var currentUserId: String? = null
    
    fun loadWorkoutSummary(workoutId: String) {
        viewModelScope.launch {
            _uiState.value = PostWorkoutUiState.Loading

            // Get current user ID
            val userId = authInteractor.currentUser(waitForAuth = false).fold(
                onSuccess = { it.value },
                onFailure = {
                    _uiState.value = PostWorkoutUiState.Error(
                        message = "User not authenticated"
                    )
                    return@launch
                }
            )

            // Get workout details
            workoutInteractor.getById(WorkoutId(workoutId), userId).fold(
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
            val userId = currentUserId ?: authInteractor.currentUser(waitForAuth = false).fold(
                onSuccess = { it.value },
                onFailure = {
                    Timber.e("Cannot discard workout - user not authenticated")
                    return@launch
                }
            )
            
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
    
    
    private suspend fun detectPersonalRecords(workout: com.example.liftrix.domain.model.Workout): List<PersonalRecord> {
        val userId = currentUserId ?: return emptyList()
        
        // Use the PRDetectionService to detect actual personal records
        val detectionResult = prDetectionService.detectPersonalRecords(workout, userId)
        
        return detectionResult.fold(
            onSuccess = { domainRecords ->
                // CRITICAL FIX: Save detected PRs to database so they persist
                if (domainRecords.isNotEmpty()) {
                    Timber.d("Saving ${domainRecords.size} detected PRs to database")
                    val saveResult = personalRecordRepository.savePRs(
                        personalRecords = domainRecords,
                        userId = userId,
                        workoutId = workout.id.value
                    )
                    
                    saveResult.fold(
                        onSuccess = {
                            Timber.d("Successfully saved ${domainRecords.size} PRs to database")
                        },
                        onFailure = { error ->
                            Timber.e("Failed to save PRs to database: $error")
                            // Continue with UI display even if save fails
                        }
                    )
                }
                
                // Map domain PersonalRecords to UI PersonalRecords
                domainRecords.map { domainRecord ->
                    PersonalRecord(
                        exerciseName = domainRecord.exerciseName,
                        type = domainRecord.prType.displayName,
                        value = formatPRValue(domainRecord)
                    )
                }
            },
            onFailure = { error ->
                Timber.e("Failed to detect personal records: $error")
                emptyList()
            }
        )
    }
    
    /**
     * Formats a PR value for UI display based on the PR type
     */
    private fun formatPRValue(record: com.example.liftrix.domain.service.PersonalRecord): String {
        return when (record.prType) {
            com.example.liftrix.domain.service.PRType.ONE_RM -> {
                val oneRM = record.estimatedOneRM ?: (record.weight ?: 0.0)
                "${String.format("%.1f", oneRM)}kg 1RM"
            }
            com.example.liftrix.domain.service.PRType.MAX_WEIGHT -> {
                "${String.format("%.1f", record.weight ?: 0.0)}kg"
            }
            com.example.liftrix.domain.service.PRType.VOLUME -> {
                val volume = record.volume ?: 0.0
                "${String.format("%.0f", volume)}kg total volume"
            }
            com.example.liftrix.domain.service.PRType.REPS -> {
                "${record.reps} reps @ ${String.format("%.1f", record.weight ?: 0.0)}kg"
            }
        }
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
        
        // Create ShareWorkoutData from current workout state
        val workoutData = ShareWorkoutData(
            workoutName = state.workoutName,
            duration = state.duration.toString(),
            totalVolume = "${state.totalVolume}kg",
            exercises = state.exercises.map { it.name },
            personalRecords = state.personalRecords.map { "${it.exerciseName}: ${it.type} - ${it.value}" },
            imageUrl = state.workoutImageUrl
        )
        
        // Create share request for Instagram Story
        val shareRequest = ShareRequest(
            userId = userId,
            platform = SharePlatform.INSTAGRAM_STORY,
            contentType = ShareContentType.WORKOUT_SUMMARY,
            workoutId = workoutId,
            workoutData = workoutData,
            customMessage = "Just crushed this workout! 💪"
        )
        
        // Use ShareToExternalPlatformUseCase to handle the sharing
        val shareResult = shareToExternalPlatformUseCase.invoke(shareRequest)
        shareResult.fold(
            onSuccess = { result ->
                Timber.d("Successfully created Instagram Story share intent")
                try {
                    // The result.intent can be used to launch Instagram
                    // In a real implementation, this would be handled by the UI layer
                    // For now, we'll emit a side effect or update UI state
                    _uiState.value = state.copy(
                        // Add any success indicators if needed
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed to launch Instagram Story intent")
                }
            },
            onFailure = { error ->
                Timber.e("Failed to share to Instagram Story: $error")
                _uiState.value = PostWorkoutUiState.Error(
                    message = (error as? LiftrixError)?.getDisplayMessage() ?: "Failed to share to Instagram Story"
                )
            }
        )
    }
    
    private suspend fun copyWorkoutLink(workoutId: String, userId: String) {
        val state = _uiState.value as? PostWorkoutUiState.Success ?: return
        
        // Create share request for generic link sharing
        val shareRequest = ShareRequest(
            userId = userId,
            platform = SharePlatform.GENERIC,
            contentType = ShareContentType.WORKOUT_LINK,
            workoutId = workoutId,
            customMessage = "Check out my workout!"
        )
        
        // Use ShareToExternalPlatformUseCase to handle link generation
        val shareResult = shareToExternalPlatformUseCase.invoke(shareRequest)
        shareResult.fold(
            onSuccess = { result ->
                Timber.d("Successfully generated workout link: ${result.shareableContent.linkUrl}")
                
                // In a real implementation, this would copy to clipboard
                // For now, we'll log and update the UI state
                val link = result.shareableContent.linkUrl ?: "https://liftrix.app/workout/$workoutId"
                Timber.d("Workout link ready to copy: $link")
                
                _uiState.value = state.copy(
                    // Add any success indicators if needed
                )
            },
            onFailure = { error ->
                Timber.e("Failed to generate workout link: $error")
                _uiState.value = PostWorkoutUiState.Error(
                    message = (error as? LiftrixError)?.getDisplayMessage() ?: "Failed to generate workout link"
                )
            }
        )
    }
    
    private suspend fun shareToWhatsApp(workoutId: String, userId: String) {
        val state = _uiState.value as? PostWorkoutUiState.Success ?: return
        
        // Create ShareWorkoutData from current workout state
        val workoutData = ShareWorkoutData(
            workoutName = state.workoutName,
            duration = formatDuration(state.duration),
            totalVolume = "${state.totalVolume/1000.0} tons",
            exercises = state.exercises.map { it.name },
            personalRecords = state.personalRecords.map { "${it.exerciseName}: ${it.type} - ${it.value}" },
            imageUrl = state.workoutImageUrl
        )
        
        // Create share request for WhatsApp
        val shareRequest = ShareRequest(
            userId = userId,
            platform = SharePlatform.WHATSAPP,
            contentType = ShareContentType.WORKOUT_SUMMARY,
            workoutId = workoutId,
            workoutData = workoutData,
            customMessage = "Check out my latest workout session!"
        )
        
        // Use ShareToExternalPlatformUseCase to handle the sharing
        val shareResult = shareToExternalPlatformUseCase.invoke(shareRequest)
        shareResult.fold(
            onSuccess = { result ->
                Timber.d("Successfully created WhatsApp share intent")
                try {
                    // The result.intent can be used to launch WhatsApp
                    // In a real implementation, this would be handled by the UI layer
                    _uiState.value = state.copy(
                        // Add any success indicators if needed
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed to launch WhatsApp intent")
                }
            },
            onFailure = { error ->
                Timber.e("Failed to share to WhatsApp: $error")
                _uiState.value = PostWorkoutUiState.Error(
                    message = (error as? LiftrixError)?.getDisplayMessage() ?: "Failed to share to WhatsApp"
                )
            }
        )
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
        val minutes = duration.toMinutes() % 60
        
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
        val personalRecords: List<PersonalRecord>,
        val exercises: List<ExerciseSummary>,
        val workoutImageUrl: String?
    ) : PostWorkoutUiState()
    
    data class Error(
        val message: String
    ) : PostWorkoutUiState()
}
