package com.example.liftrix.ui.workouts

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.compose.runtime.Stable
import androidx.paging.cachedIn
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.domain.model.social.PostVisibility
import com.example.liftrix.domain.model.social.PostExercise
import com.example.liftrix.domain.model.social.WorkoutSummary as SocialWorkoutSummary
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.repository.social.EngagementRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.usecase.sharing.ShareToExternalPlatformUseCase
import com.example.liftrix.domain.usecase.sharing.SharePlatform
import com.example.liftrix.domain.usecase.sharing.ShareContentType
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import com.example.liftrix.ui.common.event.ViewModelEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * UI State for the User Workouts screen
 */
@Stable
data class UserWorkoutsUiState(
    val isLoading: Boolean = false,
    val error: LiftrixError? = null,
    val workoutPosts: List<WorkoutPost> = emptyList(),
    val totalWorkouts: Int = 0,
    val likedPosts: Set<String> = emptySet(),
    val savedPosts: Set<String> = emptySet(),
    val userId: String? = null
)

/**
 * Events that can occur on the User Workouts screen
 */
sealed class UserWorkoutsEvent : ViewModelEvent {
    data class ToggleLike(val postId: String) : UserWorkoutsEvent()
    data class ToggleSave(val postId: String) : UserWorkoutsEvent()
    data class ShareWorkout(val workoutId: String) : UserWorkoutsEvent()
    data object RefreshWorkouts : UserWorkoutsEvent()
}

/**
 * ViewModel for the User Workouts screen.
 * Manages the display of all user's completed workouts in social feed format.
 * 
 * This ViewModel handles:
 * - Loading all user workouts from the workout repository
 * - Converting workouts to WorkoutPost format with default social values
 * - Managing like/save states with optimistic updates
 * - Providing workout count and social engagement
 */
@HiltViewModel
class UserWorkoutsViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val engagementRepository: EngagementRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val shareToExternalPlatformUseCase: ShareToExternalPlatformUseCase,
    errorHandler: ErrorHandler
) : BaseViewModel<UserWorkoutsUiState, UserWorkoutsEvent>(errorHandler) {
    
    override val _uiState = MutableStateFlow(UserWorkoutsUiState())
    
    // Expose workout posts from UI state
    val userWorkouts: Flow<List<WorkoutPost>> = _uiState
        .map { it.workoutPosts }
        .distinctUntilChanged()
    
    init {
        loadUserData()
        loadUserWorkouts()
    }
    
    override fun handleEvent(event: UserWorkoutsEvent) {
        when (event) {
            is UserWorkoutsEvent.ToggleLike -> toggleLike(event.postId)
            is UserWorkoutsEvent.ToggleSave -> toggleSave(event.postId)
            is UserWorkoutsEvent.ShareWorkout -> shareWorkout(event.workoutId)
            UserWorkoutsEvent.RefreshWorkouts -> refreshWorkouts()
        }
    }
    
    private fun refreshWorkouts() {
        loadUserWorkouts()
    }
    
    private fun loadUserData() {
        viewModelScope.launch {
            val userId = getCurrentUserIdUseCase()
            if (userId != null) {
                _uiState.update { it.copy(userId = userId) }
            } else {
                val error = LiftrixError.UnknownError("Failed to get user ID")
                Timber.e("Failed to get current user ID: $error")
                _uiState.update { it.copy(error = error) }
            }
        }
    }
    
    private fun loadUserWorkouts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                val userId = getCurrentUserIdUseCase()
                if (userId != null) {
                    // 🔍 ENHANCED LOGGING: Track workout loading
                    Timber.d("[WORKOUTS-DEBUG] Loading workouts for user: $userId")
                    
                    // Collect workouts from the workout repository and convert to WorkoutPost format
                    workoutRepository.getWorkoutsByUser(userId).collect { result ->
                        result.fold(
                            onSuccess = { workouts ->
                                Timber.d("[WORKOUTS-DEBUG] Successfully loaded ${workouts.size} workouts")
                                workouts.forEachIndexed { index, workout ->
                                    Timber.d("[WORKOUTS-DEBUG]   [$index] ${workout.name} - ${workout.date} - Status: ${workout.status}")
                                }
                                
                                // Convert workouts to WorkoutPost format with default social values
                                val workoutPosts = workouts.map { workout ->
                                    val totalVolume = workout.exercises.sumOf { exercise ->
                                        exercise.sets.sumOf { set -> 
                                            (set.reps?.count ?: 0) * (set.weight?.kilograms ?: 0.0)
                                        }
                                    }
                                    
                                    val durationMinutes = if (workout.startTime != null && workout.endTime != null) {
                                        java.time.Duration.between(workout.startTime, workout.endTime).toMinutes().toInt()
                                    } else null
                                    
                                    WorkoutPost(
                                        id = "post_${workout.id.value}", // Generate post ID from workout ID
                                        workoutId = workout.id.value,
                                        userId = workout.userId,
                                        authorUsername = "You", // Default username for own workouts
                                        authorDisplayName = "You",
                                        authorProfilePhotoUrl = null,
                                        caption = workout.notes ?: "",
                                        exercises = workout.exercises.map { exercise ->
                                            PostExercise(
                                                name = exercise.libraryExercise.name,
                                                isCustomExercise = false, // Default to false for now
                                                setsCount = exercise.sets.size,
                                                maxWeight = exercise.sets.maxOfOrNull { it.weight?.kilograms ?: 0.0 },
                                                isPR = false // Default to false for now
                                            )
                                        },
                                        totalVolume = totalVolume,
                                        workoutDuration = durationMinutes,
                                        exercisesCount = workout.exercises.size,
                                        mediaUrls = emptyList(), // No media for regular workouts
                                        likeCount = 0, // Default social values
                                        commentCount = 0,
                                        isLikedByViewer = false,
                                        isSavedByViewer = false,
                                        visibility = PostVisibility.PRIVATE, // Default to private for non-social workouts
                                        createdAt = workout.createdAt.epochSecond,
                                        updatedAt = workout.updatedAt.epochSecond,
                                        workoutSummary = SocialWorkoutSummary(
                                            totalSets = workout.exercises.sumOf { it.sets.size },
                                            totalReps = workout.exercises.sumOf { exercise ->
                                                exercise.sets.sumOf { set -> set.reps?.count ?: 0 }
                                            },
                                            totalVolume = totalVolume,
                                            exerciseCount = workout.exercises.size,
                                            duration = durationMinutes
                                        )
                                    )
                                }
                                
                                _uiState.update { 
                                    it.copy(
                                        isLoading = false,
                                        workoutPosts = workoutPosts,
                                        totalWorkouts = workouts.size,
                                        error = null
                                    )
                                }
                            },
                            onFailure = { error ->
                                val liftrixError = error as? LiftrixError ?: LiftrixError.UnknownError("Failed to load workouts: ${error.message}")
                                Timber.e("[WORKOUTS-DEBUG] Failed to load workouts: $liftrixError")
                                _uiState.update { 
                                    it.copy(
                                        isLoading = false,
                                        error = liftrixError
                                    )
                                }
                            }
                        )
                    }
                } else {
                    val error = LiftrixError.AuthenticationError("User not authenticated")
                    Timber.e("[WORKOUTS-DEBUG] Failed to get current user ID: $error")
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = error
                        )
                    }
                }
            } catch (e: Exception) {
                val error = LiftrixError.UnknownError("Failed to load user workouts: ${e.message}")
                Timber.e(e, "[WORKOUTS-DEBUG] Exception loading user workouts")
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = error
                    )
                }
            }
        }
    }
    
    fun toggleLike(postId: String) {
        viewModelScope.launch {
            val userId = _uiState.value.userId ?: return@launch
            
            // Optimistic update
            val currentLikedPosts = _uiState.value.likedPosts.toMutableSet()
            if (currentLikedPosts.contains(postId)) {
                currentLikedPosts.remove(postId)
            } else {
                currentLikedPosts.add(postId)
            }
            _uiState.update { it.copy(likedPosts = currentLikedPosts) }
            
            // For workout-generated posts, we just update local state since they're not real social posts
            if (postId.startsWith("post_")) {
                // This is a workout-generated post, just keep the optimistic update
                Timber.d("Toggled like for workout-generated post: $postId")
                return@launch
            }
            
            // For real social posts, perform actual operation  
            engagementRepository.toggleLike(postId, userId).fold(
                onSuccess = { 
                    // Already updated optimistically
                },
                onFailure = { throwable ->
                    val error = throwable as? LiftrixError ?: LiftrixError.UnknownError("Failed to toggle like")
                    Timber.e("Failed to toggle like: $error")
                    // Revert optimistic update on failure
                    val revertedLikedPosts = _uiState.value.likedPosts.toMutableSet()
                    if (revertedLikedPosts.contains(postId)) {
                        revertedLikedPosts.remove(postId)
                    } else {
                        revertedLikedPosts.add(postId)
                    }
                    _uiState.update { it.copy(likedPosts = revertedLikedPosts) }
                }
            )
        }
    }
    
    fun toggleSave(postId: String) {
        viewModelScope.launch {
            val userId = _uiState.value.userId ?: return@launch
            
            // Optimistic update
            val currentSavedPosts = _uiState.value.savedPosts.toMutableSet()
            if (currentSavedPosts.contains(postId)) {
                currentSavedPosts.remove(postId)
            } else {
                currentSavedPosts.add(postId)
            }
            _uiState.update { it.copy(savedPosts = currentSavedPosts) }
            
            // For workout-generated posts, we just update local state since they're not real social posts
            if (postId.startsWith("post_")) {
                // This is a workout-generated post, just keep the optimistic update
                Timber.d("Toggled save for workout-generated post: $postId")
                return@launch
            }
            
            // For real social posts, perform actual operation
            engagementRepository.toggleSave(postId, userId).fold(
                onSuccess = { 
                    // Already updated optimistically
                },
                onFailure = { throwable ->
                    val error = throwable as? LiftrixError ?: LiftrixError.UnknownError("Failed to toggle save")
                    Timber.e("Failed to toggle save: $error")
                    // Revert optimistic update on failure
                    val revertedSavedPosts = _uiState.value.savedPosts.toMutableSet()
                    if (revertedSavedPosts.contains(postId)) {
                        revertedSavedPosts.remove(postId)
                    } else {
                        revertedSavedPosts.add(postId)
                    }
                    _uiState.update { it.copy(savedPosts = revertedSavedPosts) }
                }
            )
        }
    }
    
    fun shareWorkout(workoutId: String) {
        viewModelScope.launch {
            val userId = getCurrentUserIdUseCase()
            if (userId == null) {
                Timber.e("Cannot share workout: User not authenticated")
                return@launch
            }

            // Get the workout details for sharing
            val workout = _uiState.value.workoutPosts.find { it.id == workoutId }
            if (workout == null) {
                Timber.e("Cannot share workout: Workout not found with id $workoutId")
                return@launch
            }

            // Use the ShareToExternalPlatformUseCase to share the workout
            val result = shareToExternalPlatformUseCase.invoke(
                com.example.liftrix.domain.usecase.sharing.ShareRequest(
                    workoutId = workoutId,
                    userId = userId,
                    platform = SharePlatform.GENERIC, // Let user choose platform
                    contentType = ShareContentType.WORKOUT_SUMMARY
                )
            )

            result.fold(
                onSuccess = { 
                    Timber.d("Workout shared successfully: $workoutId")
                },
                onFailure = { error ->
                    val liftrixError = error as? LiftrixError ?: LiftrixError.UnknownError("Failed to share workout: ${error.message}")
                    Timber.e("Failed to share workout: $liftrixError")
                    handleError(liftrixError)
                }
            )
        }
    }
}