package com.example.liftrix.ui.feed

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.social.*
import com.example.liftrix.domain.repository.social.FeedRepository
import com.example.liftrix.domain.service.MediaUploadService
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.usecase.workout.WorkoutQueryUseCase
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.toSummary
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import com.example.liftrix.ui.common.event.ViewModelEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for post creation screen
 * Handles workout loading, media upload, and post creation
 */
@HiltViewModel
class PostCreationViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    private val mediaUploadService: MediaUploadService,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val workoutQueryUseCase: WorkoutQueryUseCase,
    errorHandler: ErrorHandler
) : BaseViewModel<PostCreationUiState, PostCreationEvent>(errorHandler) {

    override val _uiState: MutableStateFlow<PostCreationUiState> = MutableStateFlow(PostCreationUiState())
    
    // Public method for UI to call
    fun onEvent(event: PostCreationEvent) {
        handleEvent(event)
    }

    override fun handleEvent(event: PostCreationEvent) {
        when (event) {
            is PostCreationEvent.LoadWorkout -> loadWorkout(event.workoutId)
            is PostCreationEvent.UpdateCaption -> updateCaption(event.caption)
            is PostCreationEvent.UpdateVisibility -> updateVisibility(event.visibility)
            is PostCreationEvent.AddMedia -> addMedia(event.uris)
            is PostCreationEvent.RemoveMedia -> removeMedia(event.index)
            is PostCreationEvent.CreatePost -> createPost()
        }
    }


    private fun loadWorkout(workoutId: String) {
        updateState { currentState ->
            currentState.copy(
                isLoading = true,
                workoutId = workoutId,
                error = null
            )
        }
        
        viewModelScope.launch {
            try {
                val userId = getCurrentUserIdUseCase()
                if (userId == null) {
                    updateState { currentState ->
                        currentState.copy(
                            isLoading = false,
                            error = "User not authenticated"
                        )
                    }
                    return@launch
                }

                val result = workoutQueryUseCase.getById(WorkoutId(workoutId), userId)
                result.fold(
                    onSuccess = { workout ->
                        if (workout != null) {
                            val domainSummary = workout.toSummary()
                            // Convert domain WorkoutSummary to social WorkoutSummary
                            val socialWorkoutSummary = WorkoutSummary(
                                id = domainSummary.id.value,
                                name = domainSummary.name,
                                durationMinutes = domainSummary.duration?.toMinutes()?.toInt() ?: 0,
                                totalVolume = domainSummary.totalVolume.kilograms,
                                exerciseCount = domainSummary.exerciseCount,
                                prsCount = 0 // PR calculation can be added when needed
                            )
                            updateState { currentState ->
                                currentState.copy(
                                    isLoading = false,
                                    workoutSummary = socialWorkoutSummary
                                )
                            }
                            Timber.d("Workout loaded successfully: $workoutId")
                        } else {
                            updateState { currentState ->
                                currentState.copy(
                                    isLoading = false,
                                    error = "Workout not found"
                                )
                            }
                        }
                    },
                    onFailure = { error ->
                        updateState { currentState ->
                            currentState.copy(
                                isLoading = false,
                                error = "Failed to load workout: ${error.message}"
                            )
                        }
                        Timber.e(error, "Failed to load workout: $workoutId")
                    }
                )
            } catch (e: Exception) {
                updateState { currentState ->
                    currentState.copy(
                        isLoading = false,
                        error = "Failed to load workout: ${e.message}"
                    )
                }
                Timber.e(e, "Failed to load workout: $workoutId")
            }
        }
    }

    private fun updateCaption(caption: String) {
        updateState { currentState ->
            currentState.copy(caption = caption)
        }
    }

    private fun updateVisibility(visibility: PostVisibility) {
        updateState { currentState ->
            currentState.copy(visibility = visibility)
        }
    }

    private fun addMedia(uris: List<Uri>) {
        val currentRequests = _uiState.value.mediaRequests
        val newRequests = uris.map { uri ->
            MediaUploadRequest(
                uri = uri,
                type = determineMediaType(uri)
            )
        }
        
        // Limit to 5 total media items
        val allRequests = (currentRequests + newRequests).take(5)
        
        updateState { currentState ->
            currentState.copy(mediaRequests = allRequests)
        }
        
        Timber.d("Added ${newRequests.size} media items, total: ${allRequests.size}")
    }

    private fun removeMedia(index: Int) {
        val currentRequests = _uiState.value.mediaRequests.toMutableList()
        if (index in 0 until currentRequests.size) {
            currentRequests.removeAt(index)
            updateState { currentState ->
                currentState.copy(mediaRequests = currentRequests)
            }
            Timber.d("Removed media item at index $index")
        }
    }

    private fun createPost() {
        val state = _uiState.value
        
        if (!state.canCreatePost) {
            updateState { currentState ->
                currentState.copy(error = "Please complete all required fields")
            }
            return
        }
        
        updateState { currentState ->
            currentState.copy(
                isCreatingPost = true,
                error = null
            )
        }
        
        viewModelScope.launch {
            val userId = getCurrentUserIdUseCase()
            if (userId != null) {
                createPostForUser(userId, state)
            } else {
                updateState { currentState ->
                    currentState.copy(
                        isCreatingPost = false,
                        error = "Authentication required"
                    )
                }
                Timber.e("Failed to get current user ID for post creation")
            }
        }
    }

    private suspend fun createPostForUser(userId: String, state: PostCreationUiState) {
        try {
            // Upload media first if any
            val uploadedMedia = if (state.mediaRequests.isNotEmpty()) {
                mediaUploadService.uploadMediaItems(
                    userId = userId,
                    mediaRequests = state.mediaRequests
                ).getOrThrow()
            } else {
                emptyList()
            }
            
            // Create the post
            val createRequest = CreateWorkoutPostRequest(
                workoutId = state.workoutId,
                caption = state.caption.takeIf { it.isNotBlank() },
                mediaUrls = uploadedMedia.map { it.originalUrl },
                visibility = state.visibility
            )
            
            feedRepository.createPost(userId, createRequest).fold(
                onSuccess = { post ->
                    updateState { currentState ->
                        currentState.copy(
                            isCreatingPost = false,
                            isPostCreated = true
                        )
                    }
                    Timber.d("Post created successfully: ${post.id}")
                },
                onFailure = { error ->
                    updateState { currentState ->
                        currentState.copy(
                            isCreatingPost = false,
                            error = "Failed to create post: ${error.message}"
                        )
                    }
                    Timber.e("Failed to create post", error)
                }
            )
        } catch (e: Exception) {
            updateState { currentState ->
                currentState.copy(
                    isCreatingPost = false,
                    error = "Failed to create post: ${e.message}"
                )
            }
            Timber.e(e, "Exception during post creation")
        }
    }

    private fun determineMediaType(uri: Uri): MediaType {
        val mimeType = uri.toString()
        return when {
            mimeType.contains("video") -> MediaType.VIDEO
            else -> MediaType.IMAGE
        }
    }
}

/**
 * UI state for post creation screen
 */
@Stable
data class PostCreationUiState(
    val workoutId: String = "",
    val workoutSummary: WorkoutSummary? = null,
    val caption: String = "",
    val visibility: PostVisibility = PostVisibility.FOLLOWERS,
    val mediaRequests: List<MediaUploadRequest> = emptyList(),
    val isLoading: Boolean = false,
    val isCreatingPost: Boolean = false,
    val isPostCreated: Boolean = false,
    val error: String? = null
) {
    val canCreatePost: Boolean
        get() = workoutSummary != null && !isCreatingPost
}

/**
 * Events for post creation screen
 */
sealed class PostCreationEvent : ViewModelEvent {
    data class LoadWorkout(val workoutId: String) : PostCreationEvent()
    data class UpdateCaption(val caption: String) : PostCreationEvent()
    data class UpdateVisibility(val visibility: PostVisibility) : PostCreationEvent()
    data class AddMedia(val uris: List<Uri>) : PostCreationEvent()
    data class RemoveMedia(val index: Int) : PostCreationEvent()
    object CreatePost : PostCreationEvent()
}