package com.example.liftrix.ui.workout.completion

import android.net.Uri
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import javax.inject.Inject

import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.service.MediaUploadService
import com.example.liftrix.data.local.dao.WorkoutPostDao
import com.example.liftrix.data.local.dao.SocialProfileDao
import com.example.liftrix.data.local.entity.SocialProfileEntity
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.domain.model.social.PostVisibility
import com.example.liftrix.domain.model.social.WorkoutSummary
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.data.local.entity.WorkoutPostEntity
import com.example.liftrix.data.mapper.WorkoutPostMapper
import com.example.liftrix.domain.model.WorkoutId

/**
 * ViewModel for post creation screen, handling workout post creation with media upload.
 * 
 * Implements the MVI pattern with BaseViewModel and manages the complete post creation flow
 * including media upload, workout data retrieval, and social post creation.
 * 
 * Key Features:
 * - Media upload with progress tracking and compression
 * - Workout data integration with user information
 * - Privacy controls for post visibility
 * - Error handling with user-friendly messages
 * - Optimistic updates for immediate UI feedback
 */
@HiltViewModel
class PostCreationViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val mediaUploadService: MediaUploadService,
    private val workoutPostDao: WorkoutPostDao,
    private val socialProfileDao: SocialProfileDao,
    private val authRepository: AuthRepository,
    private val workoutPostMapper: WorkoutPostMapper
) : ModernBaseViewModel<UiState<PostCreationUiState>>(initialState = UiState.Success(PostCreationUiState.Initial)) {

    /**
     * Creates a new workout post with optional media attachments.
     * 
     * This method orchestrates the complete post creation flow:
     * 1. Validates user authentication
     * 2. Retrieves workout details
     * 3. Uploads media files (if any) with compression
     * 4. Creates and saves the workout post
     * 5. Provides real-time progress updates
     * 
     * @param workoutId The ID of the workout to share
     * @param caption User-provided caption for the post
     * @param mediaUris List of media URIs to upload (max 10)
     * @param privacy Visibility setting for the post
     */
    fun createPost(
        workoutId: String,
        caption: String,
        mediaUris: List<Uri>,
        privacy: PostVisibility
    ) {
        Timber.d("Creating post for workout $workoutId with ${mediaUris.size} media items")

        viewModelScope.launch {
            updateState { UiState.Loading }
            val result = liftrixCatching(
                errorMapper = { throwable ->
                    LiftrixError.BusinessLogicError(
                        code = "POST_CREATION_FAILED",
                        errorMessage = "Failed to create post: ${throwable.message}",
                        analyticsContext = mapOf(
                            "workout_id" to workoutId,
                            "media_count" to mediaUris.size.toString(),
                            "privacy" to privacy.name
                        )
                    )
                }
            ) {
                createPostInternal(workoutId, caption, mediaUris, privacy)
            }
            result.onSuccess { createdPost ->
                Timber.d("Post created successfully: ${createdPost.id}")
                updateState {
                    UiState.Success(
                        PostCreationUiState.Success(
                            createdPostId = createdPost.id,
                            message = "Workout shared successfully!"
                        )
                    )
                }
            }.onFailure { error ->
                logError(error, "createPost")
                updateState { UiState.Error(error as? LiftrixError ?: LiftrixError.UnknownError(errorMessage = error.message ?: "Failed to create post")) }
            }
        }
    }

    /**
     * Ensures a social profile exists for the user before post creation.
     * IMPORTANT: Only creates a profile if one doesn't exist, never replaces existing ones.
     */
    private suspend fun ensureSocialProfileExists(userId: String) {
        val existingProfile = socialProfileDao.getProfile(userId)
        if (existingProfile == null) {
            Timber.d("Creating social profile for user: $userId")
            
            // Get user info from auth repository for profile creation
            val currentUser = authRepository.currentUser.first()
            
            // Extract profile photo URL with better fallback handling
            val profilePhotoUrl = currentUser?.photoUrl?.takeIf { it.isNotBlank() }
            Timber.d("Creating social profile for user $userId with photo URL: $profilePhotoUrl")
            
            // Create a basic social profile with default values
            // BUT use a flag to indicate this is temporary
            val newProfile = SocialProfileEntity(
                userId = userId,
                username = "", // Empty username indicates profile needs setup
                displayName = currentUser?.displayName ?: "Liftrix User",
                bio = null,
                profilePhotoUrl = profilePhotoUrl,
                coverPhotoUrl = null,
                workoutCount = 0,
                followerCount = 0,
                followingCount = 0,
                memberSince = System.currentTimeMillis(),
                lastActive = System.currentTimeMillis(),
                isVerified = false,
                isPrivate = false,  // Default to public for new users
                hideFromSuggestions = false,
                allowFriendRequests = true,
                instagramHandle = null,
                youtubeChannel = null,
                personalWebsite = null,
                isSynced = false,
                syncVersion = 0,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            
            try {
                socialProfileDao.insertProfile(newProfile)
                Timber.d("Social profile created successfully for user: $userId")
            } catch (e: Exception) {
                Timber.e(e, "Failed to create social profile, it might already exist")
                // If insert fails due to conflict, that's okay - profile exists
            }
        } else {
            Timber.d("Social profile already exists for user: $userId with username: ${existingProfile.username}")
        }
    }

    /**
     * Internal post creation logic with detailed error handling.
     */
    private suspend fun createPostInternal(
        workoutId: String,
        caption: String,
        mediaUris: List<Uri>,
        privacy: PostVisibility
    ): WorkoutPost {
        updateState { UiState.Loading }

        // Get current user
        val userId = authRepository.getCurrentUserId()
            ?: throw LiftrixError.AuthenticationError(
                errorMessage = "User not authenticated for post creation",
                analyticsContext = mapOf("workout_id" to workoutId)
            )
        
        // CRITICAL: Ensure social profile exists before creating post
        // This prevents foreign key constraint failures
        // But NEVER replaces existing profiles
        ensureSocialProfileExists(userId)

        updateState { UiState.Loading }

        // Get workout details
        val workoutResult = workoutRepository.getWorkoutById(WorkoutId(workoutId), userId)
        val workout = workoutResult.fold(
            onSuccess = { it ?: throw LiftrixError.NotFoundError(
                errorMessage = "Workout not found",
                resourceType = "workout",
                resourceId = workoutId,
                analyticsContext = mapOf("user_id" to userId)
            ) },
            onFailure = { error ->
                throw LiftrixError.NotFoundError(
                    errorMessage = "Failed to retrieve workout",
                    resourceType = "workout",
                    resourceId = workoutId,
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        )

        // Upload media if present
        val mediaUrls = if (mediaUris.isNotEmpty()) {
            updateState { UiState.Loading }
            uploadMedia(mediaUris, userId)
        } else {
            emptyList()
        }

        updateState { UiState.Loading }

        // Calculate total volume using centralized VolumeCalculator with debug logging
        val calculatedVolume = workout.calculateTotalVolume()
        Timber.d("🔍 COMPLETION-POST-DEBUG: Domain model volume calculation - totalVolume=${calculatedVolume.value}kg")
        Timber.d("🔍 COMPLETION-POST-DEBUG: Workout has ${workout.exercises.size} exercises")
        
        // Also calculate manually to compare
        val manualTotalVolume = workout.exercises.sumOf { exercise ->
            com.example.liftrix.domain.util.VolumeCalculator.calculateVolumeFromSets(exercise.sets)
        }
        Timber.d("🔍 COMPLETION-POST-DEBUG: Manual VolumeCalculator total: ${manualTotalVolume}kg")
        
        workout.exercises.forEachIndexed { index, exercise ->
            // Use VolumeCalculator with debug logging to identify any issues
            val exerciseVolumeKg = com.example.liftrix.domain.util.VolumeCalculator.calculateVolumeWithDebug(
                sets = exercise.sets,
                exerciseName = exercise.libraryExercise.name
            )
            val exerciseVolume = exercise.getTotalVolume()
            Timber.d("🔍 COMPLETION-POST-DEBUG: Exercise $index '${exercise.libraryExercise.name}' - calculated=${exerciseVolumeKg}kg, domain=${exerciseVolume?.value}kg, sets=${exercise.sets.size}")
        }
        
        // Use the manually calculated volume if domain model is still showing 0
        val finalVolume = if (calculatedVolume.value > 0.0) calculatedVolume.value else manualTotalVolume
        Timber.d("🔍 COMPLETION-POST-DEBUG: Final volume to use: ${finalVolume}kg (domain=${calculatedVolume.value}kg, manual=${manualTotalVolume}kg)")
        
        // Create workout summary using corrected volume
        val workoutSummary = WorkoutSummary(
            totalSets = workout.getTotalSets(),
            totalReps = workout.getTotalRepsCompleted().count,
            totalVolume = finalVolume,
            exerciseCount = workout.exercises.size,
            duration = workout.getDuration()?.toMinutes()?.toInt() ?: 0
        )

        // Create post entity using corrected volume
        val post = WorkoutPost(
            id = UUID.randomUUID().toString(),
            userId = userId,
            workoutId = workoutId,
            caption = caption.trim(),
            mediaUrls = mediaUrls,
            mediaThumbnails = mediaUrls.map { it.replace("/original/", "/thumb/") },
            workoutDuration = workout.getDuration()?.toMinutes()?.toInt() ?: 0,
            totalVolume = finalVolume,
            exercisesCount = workout.exercises.size,
            prsCount = 0, // PR detection handled separately
            workoutSummary = workoutSummary,
            visibility = privacy,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        // Save to local database
        val entity = workoutPostMapper.toEntity(post)
        workoutPostDao.insertPost(entity)

        Timber.d("Post saved locally, will sync to Firebase in background")
        
        return post
    }

    /**
     * Uploads multiple media files with progress tracking and error handling.
     * 
     * @param uris List of media URIs to upload
     * @param userId Current user ID for file organization
     * @return List of uploaded media URLs
     */
    private suspend fun uploadMedia(uris: List<Uri>, userId: String): List<String> {
        // Generate a single workout ID for all images in this post
        val workoutPostId = UUID.randomUUID().toString()
        
        return coroutineScope {
            uris.mapIndexed { index, uri ->
                async {
                    val progress = ((index + 1).toFloat() / uris.size) * 100
                    updateState { 
                        UiState.Loading 
                    }
                    
                    // Use the workout_images path which is allowed in Firebase Storage rules
                    // All images from the same post share the same workoutPostId directory
                    // NOTE: uploadImage appends .jpg to the path, so we only provide the directory
                    // and base filename without extension
                    val imageFileName = UUID.randomUUID().toString()
                    val result = mediaUploadService.uploadImage(
                        uri = uri,
                        path = "workout_images/$userId/$workoutPostId/$imageFileName",
                        maxSizeKb = 5000 // 5MB max per image
                    )
                    
                    result.fold(
                        onSuccess = { url ->
                            Timber.d("Media upload successful: $url")
                            url
                        },
                        onFailure = { error ->
                            Timber.e("Media upload failed: $error")
                            throw LiftrixError.BusinessLogicError(
                                code = "MEDIA_UPLOAD_FAILED",
                                errorMessage = "Failed to upload photo ${index + 1}",
                                analyticsContext = mapOf(
                                    "user_id" to userId,
                                    "media_index" to index.toString(),
                                    "workout_post_id" to workoutPostId
                                )
                            )
                        }
                    )
                }
            }.awaitAll()
        }
    }

    /**
     * Handles events from the UI.
     */
    fun handleEvent(event: PostCreationEvent) {
        when (event) {
            is PostCreationEvent.CreatePost -> {
                createPost(
                    workoutId = event.workoutId,
                    caption = event.caption,
                    mediaUris = event.mediaUris,
                    privacy = event.privacy
                )
            }
            is PostCreationEvent.ResetState -> {
                updateState { UiState.Success(PostCreationUiState.Initial) }
            }
        }
    }
}

/**
 * UI state for post creation screen.
 */
sealed class PostCreationUiState {
    object Initial : PostCreationUiState()
    data class Loading(val message: String) : PostCreationUiState()
    data class Success(val createdPostId: String, val message: String) : PostCreationUiState()
    data class Error(val message: String) : PostCreationUiState()
}

/**
 * Events for post creation screen.
 */
sealed class PostCreationEvent : ViewModelEvent {
    data class CreatePost(
        val workoutId: String,
        val caption: String,
        val mediaUris: List<Uri>,
        val privacy: PostVisibility
    ) : PostCreationEvent()
    
    object ResetState : PostCreationEvent()
}