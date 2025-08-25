package com.example.liftrix.ui.workouts

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.social.FeedRepository
import com.example.liftrix.domain.repository.social.EngagementRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import com.example.liftrix.ui.common.event.ViewModelEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * UI State for the User Workouts screen
 */
data class UserWorkoutsUiState(
    val isLoading: Boolean = false,
    val error: LiftrixError? = null,
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
 * Manages the display of all user's completed workouts with social engagement metrics.
 * 
 * This ViewModel handles:
 * - Loading paginated user workout posts from the feed repository
 * - Managing like/save states with optimistic updates
 * - Sharing workouts to social platforms
 * - Tracking total workout count for the user
 */
@HiltViewModel
class UserWorkoutsViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    private val engagementRepository: EngagementRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    errorHandler: ErrorHandler
) : BaseViewModel<UserWorkoutsUiState, UserWorkoutsEvent>(errorHandler) {
    
    override val _uiState = MutableStateFlow(UserWorkoutsUiState())
    
    private val _userWorkouts = MutableStateFlow<Flow<PagingData<WorkoutPost>>>(emptyFlow())
    val userWorkouts: Flow<PagingData<WorkoutPost>> = _userWorkouts
        .flatMapLatest { it }
        .cachedIn(viewModelScope)
    
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
                    // Load user's workout posts using the feed repository
                    // Pass userId as both userId and viewerId since we're viewing our own posts
                    val userPostsFlow = feedRepository.getUserPosts(
                        userId = userId,
                        viewerId = userId,
                        pageSize = 20
                    )
                    
                    // Update the flow properly
                    _userWorkouts.value = userPostsFlow
                    
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            totalWorkouts = 0 // Will be updated as posts are loaded
                        )
                    }
                } else {
                    val error = LiftrixError.AuthenticationError("User not authenticated")
                    Timber.e("Failed to get current user ID: $error")
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = error
                        )
                    }
                }
            } catch (e: Exception) {
                val error = LiftrixError.UnknownError("Failed to load user workouts: ${e.message}")
                Timber.e(e, "Failed to load user workouts")
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
            
            // Perform actual operation  
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
            
            // Perform actual operation
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
            // TODO: Implement when ShareWorkoutUseCase is available
            // For now, just log the request
            Timber.d("Share workout requested for workout: $workoutId")
        }
    }
}