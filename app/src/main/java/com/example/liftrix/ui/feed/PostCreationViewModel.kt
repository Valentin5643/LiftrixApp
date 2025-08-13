package com.example.liftrix.ui.feed

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.social.*
import com.example.liftrix.domain.repository.social.FeedRepository
import com.example.liftrix.domain.service.MediaUploadService
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
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
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) : BaseViewModel<PostCreationUiState, PostCreationEvent>() {

    override val initialState = PostCreationUiState()

    override fun onEvent(event: PostCreationEvent) {
        when (event) {
            is PostCreationEvent.LoadWorkout -> loadWorkout(event.workoutId)
            is PostCreationEvent.UpdateCaption -> updateCaption(event.caption)
            is PostCreationEvent.UpdateVisibility -> updateVisibility(event.visibility)
            is PostCreationEvent.AddMedia -> addMedia(event.uris)
            is PostCreationEvent.RemoveMedia -> removeMedia(event.index)
            is PostCreationEvent.CreatePost -> createPost()
        }
    }

    override fun updateUiState(update: PostCreationUiState.() -> PostCreationUiState) {
        _uiState.value = _uiState.value.update()
    }

    private fun loadWorkout(workoutId: String) {
        updateUiState {
            copy(
                isLoading = true,
                workoutId = workoutId,
                error = null
            )
        }
        
        viewModelScope.launch {
            try {
                // TODO: Replace with actual workout loading use case
                val mockWorkout = WorkoutSummary(
                    id = workoutId,
                    name = "Push Day Workout",
                    durationMinutes = 65,
                    totalVolume = 12500.0,
                    exerciseCount = 6,
                    prsCount = 2
                )
                
                updateUiState {
                    copy(
                        isLoading = false,
                        workoutSummary = mockWorkout
                    )
                }
                
                Timber.d("Workout loaded successfully: $workoutId")
            } catch (e: Exception) {
                updateUiState {
                    copy(
                        isLoading = false,
                        error = "Failed to load workout: ${e.message}"
                    )
                }
                Timber.e(e, "Failed to load workout: $workoutId")
            }
        }
    }

    private fun updateCaption(caption: String) {
        updateUiState {
            copy(caption = caption)
        }
    }

    private fun updateVisibility(visibility: PostVisibility) {
        updateUiState {
            copy(visibility = visibility)
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
        
        updateUiState {
            copy(mediaRequests = allRequests)
        }
        
        Timber.d("Added ${newRequests.size} media items, total: ${allRequests.size}")
    }

    private fun removeMedia(index: Int) {
        val currentRequests = _uiState.value.mediaRequests.toMutableList()
        if (index in 0 until currentRequests.size) {
            currentRequests.removeAt(index)
            updateUiState {
                copy(mediaRequests = currentRequests)
            }
            Timber.d("Removed media item at index $index")
        }
    }

    private fun createPost() {
        val state = _uiState.value
        
        if (!state.canCreatePost) {
            updateUiState {
                copy(error = "Please complete all required fields")
            }
            return
        }
        
        updateUiState {
            copy(
                isCreatingPost = true,
                error = null
            )
        }
        
        viewModelScope.launch {
            getCurrentUserIdUseCase().collect { userResult ->
                when (userResult) {
                    is LiftrixResult.Success -> {
                        val userId = userResult.data
                        createPostForUser(userId, state)
                    }
                    is LiftrixResult.Error -> {
                        updateUiState {
                            copy(
                                isCreatingPost = false,
                                error = "Authentication required"
                            )
                        }
                        Timber.e("Failed to get current user ID for post creation")
                    }
                }
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
            
            feedRepository.createPost(userId, createRequest).collect { result ->
                when (result) {
                    is LiftrixResult.Success -> {
                        updateUiState {
                            copy(
                                isCreatingPost = false,
                                isPostCreated = true
                            )
                        }
                        Timber.d("Post created successfully: ${result.data.id}")
                    }
                    is LiftrixResult.Error -> {
                        updateUiState {
                            copy(
                                isCreatingPost = false,
                                error = "Failed to create post: ${result.error.errorMessage}"
                            )
                        }
                        Timber.e("Failed to create post", result.error)
                    }
                }
            }
        } catch (e: Exception) {
            updateUiState {
                copy(
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
sealed class PostCreationEvent {
    data class LoadWorkout(val workoutId: String) : PostCreationEvent()
    data class UpdateCaption(val caption: String) : PostCreationEvent()
    data class UpdateVisibility(val visibility: PostVisibility) : PostCreationEvent()
    data class AddMedia(val uris: List<Uri>) : PostCreationEvent()
    data class RemoveMedia(val index: Int) : PostCreationEvent()
    object CreatePost : PostCreationEvent()
}