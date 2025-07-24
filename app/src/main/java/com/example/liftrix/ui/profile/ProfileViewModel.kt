package com.example.liftrix.ui.profile

import android.graphics.Rect
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.repository.ProfileRepository
import com.example.liftrix.domain.usecase.GetProfileUseCase
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.usecase.profile.DeleteProfileImageUseCase
import com.example.liftrix.domain.usecase.profile.UploadProfileImageUseCase
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Enhanced ProfileViewModel with comprehensive image management capabilities.
 * 
 * Features:
 * - Profile data loading and state management
 * - Image upload with progress tracking and error handling
 * - Image deletion with user confirmation workflow
 * - Real-time profile image URL updates
 * - Error recovery strategies with user-friendly messaging
 * - Integration with existing profile use cases
 * - Material Design loading and error states
 * 
 * State Management:
 * - ProfileUiState: Complete profile state including image operations
 * - ProfileEvent: User actions and system events
 * - Reactive flows for real-time UI updates
 * - Error handling with contextual recovery options
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val getProfileUseCase: GetProfileUseCase,
    private val uploadProfileImageUseCase: UploadProfileImageUseCase,
    private val deleteProfileImageUseCase: DeleteProfileImageUseCase,
    private val profileRepository: ProfileRepository
) : BaseViewModel<ProfileUiState, ProfileEvent>(
    initialState = ProfileUiState()
) {
    
    // Current user ID flow
    private val currentUserId = flow {
        emit(getCurrentUserIdUseCase())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
    
    // Profile data flow with real-time updates  
    private val profileFlow = currentUserId
        .filterNotNull()
        .flatMapLatest { userId ->
            getProfileUseCase(userId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    
    init {
        // Subscribe to profile changes and update UI state
        viewModelScope.launch {
            combine(
                currentUserId,
                profileFlow
            ) { userId, profile ->
                updateState { currentState ->
                    currentState.copy(
                        userId = userId,
                        profile = profile,
                        isLoading = userId == null,
                        error = null
                    )
                }
            }.collect()
        }
        
        // Load initial profile data
        loadProfile()
    }
    
    override fun handleEvent(event: ProfileEvent) {
        when (event) {
            is ProfileEvent.LoadProfile -> loadProfile()
            is ProfileEvent.UploadImage -> uploadProfileImage(event.imageUri, event.cropRect)
            is ProfileEvent.DeleteImage -> deleteProfileImage()
            is ProfileEvent.ClearError -> clearError()
            is ProfileEvent.RetryLastOperation -> retryLastOperation()
        }
    }
    
    private fun loadProfile() {
        viewModelScope.launch {
            try {
                updateState { it.copy(isLoading = true, error = null) }
                
                val userId = currentUserId.value
                if (userId == null) {
                    updateState { 
                        it.copy(
                            isLoading = false,
                            error = ProfileError.Authentication("User not authenticated")
                        )
                    }
                    return@launch
                }
                
                // Profile loading is handled by profileFlow subscription
                Timber.d("Profile loading initiated for user: $userId")
                
            } catch (e: Exception) {
                Timber.e(e, "Error loading profile")
                updateState { 
                    it.copy(
                        isLoading = false,
                        error = ProfileError.LoadingFailed("Failed to load profile: ${e.message}")
                    )
                }
            }
        }
    }
    
    private fun uploadProfileImage(imageUri: Uri, cropRect: Rect?) {
        viewModelScope.launch {
            try {
                val userId = currentUserId.value
                if (userId == null) {
                    updateState { 
                        it.copy(
                            error = ProfileError.Authentication("User not authenticated")
                        )
                    }
                    return@launch
                }
                
                updateState { 
                    it.copy(
                        imageUploadState = ImageUploadState.Uploading(progress = 0F),
                        error = null,
                        lastOperation = ProfileOperation.UploadImage(imageUri, cropRect)
                    )
                }
                
                Timber.d("Starting image upload for user: $userId")
                
                // Simulate progress updates (in real implementation, this would come from use case)
                updateState { 
                    it.copy(imageUploadState = ImageUploadState.Uploading(progress = 0.3f))
                }
                
                val result = uploadProfileImageUseCase(
                    userId = userId,
                    imageUri = imageUri,
                    cropRect = cropRect
                )
                
                if (result.isSuccess) {
                    val imageUrl = result.getOrThrow()
                    updateState { 
                        it.copy(
                            imageUploadState = ImageUploadState.Success(imageUrl),
                            successMessage = "Profile picture updated successfully!",
                            lastOperation = null
                        )
                    }
                    Timber.i("Profile image upload successful: $imageUrl")
                    
                    // Clear success message after delay
                    clearMessageAfterDelay(isSuccess = true)
                    
                } else {
                    val error = result.exceptionOrNull()
                    val errorMessage = error?.message ?: "Failed to upload image"
                    updateState { 
                        it.copy(
                            imageUploadState = ImageUploadState.Error(errorMessage),
                            error = ProfileError.UploadFailed(errorMessage)
                        )
                    }
                    Timber.e(error, "Profile image upload failed")
                }
                
            } catch (e: Exception) {
                val errorMessage = "Unexpected error during upload: ${e.message}"
                updateState { 
                    it.copy(
                        imageUploadState = ImageUploadState.Error(errorMessage),
                        error = ProfileError.UploadFailed(errorMessage)
                    )
                }
                Timber.e(e, "Unexpected error during profile image upload")
            }
        }
    }
    
    private fun deleteProfileImage() {
        viewModelScope.launch {
            try {
                val userId = currentUserId.value
                if (userId == null) {
                    updateState { 
                        it.copy(
                            error = ProfileError.Authentication("User not authenticated")
                        )
                    }
                    return@launch
                }
                
                updateState { 
                    it.copy(
                        imageUploadState = ImageUploadState.Uploading(progress = 0.5f),
                        error = null,
                        lastOperation = ProfileOperation.DeleteImage
                    )
                }
                
                Timber.d("Starting image deletion for user: $userId")
                
                val result = deleteProfileImageUseCase(userId)
                
                if (result.isSuccess) {
                    updateState { 
                        it.copy(
                            imageUploadState = ImageUploadState.Idle,
                            successMessage = "Profile picture removed successfully!",
                            lastOperation = null
                        )
                    }
                    Timber.i("Profile image deletion successful")
                    
                    // Clear success message after delay
                    clearMessageAfterDelay(isSuccess = true)
                    
                } else {
                    val error = result.exceptionOrNull()
                    val errorMessage = error?.message ?: "Failed to remove image"
                    updateState { 
                        it.copy(
                            imageUploadState = ImageUploadState.Error(errorMessage),
                            error = ProfileError.DeletionFailed(errorMessage)
                        )
                    }
                    Timber.e(error, "Profile image deletion failed")
                }
                
            } catch (e: Exception) {
                val errorMessage = "Unexpected error during deletion: ${e.message}"
                updateState { 
                    it.copy(
                        imageUploadState = ImageUploadState.Error(errorMessage),
                        error = ProfileError.DeletionFailed(errorMessage)
                    )
                }
                Timber.e(e, "Unexpected error during profile image deletion")
            }
        }
    }
    
    private fun clearError() {
        updateState { 
            it.copy(
                error = null,
                imageUploadState = if (it.imageUploadState is ImageUploadState.Error) {
                    ImageUploadState.Idle
                } else {
                    it.imageUploadState
                }
            )
        }
    }
    
    private fun retryLastOperation() {
        val lastOp = currentState.value.lastOperation
        when (lastOp) {
            is ProfileOperation.UploadImage -> {
                uploadProfileImage(lastOp.imageUri, lastOp.cropRect)
            }
            ProfileOperation.DeleteImage -> {
                deleteProfileImage()
            }
            null -> {
                loadProfile()
            }
        }
    }
    
    private fun clearMessageAfterDelay(isSuccess: Boolean) {
        viewModelScope.launch {
            kotlinx.coroutines.delay(if (isSuccess) 3000 else 5000)
            updateState { 
                it.copy(
                    successMessage = if (isSuccess) null else it.successMessage,
                    imageUploadState = if (it.imageUploadState is ImageUploadState.Success) {
                        ImageUploadState.Idle
                    } else {
                        it.imageUploadState
                    }
                )
            }
        }
    }
}

/**
 * UI state for profile screen with comprehensive image management.
 */
data class ProfileUiState(
    val userId: String? = null,
    val profile: UserProfile? = null,
    val isLoading: Boolean = false,
    val imageUploadState: ImageUploadState = ImageUploadState.Idle,
    val error: ProfileError? = null,
    val successMessage: String? = null,
    val lastOperation: ProfileOperation? = null
) : UiState

/**
 * Image upload state with progress tracking.
 */
sealed class ImageUploadState {
    data object Idle : ImageUploadState()
    data class Uploading(val progress: Float) : ImageUploadState()
    data class Success(val imageUrl: String) : ImageUploadState()
    data class Error(val message: String) : ImageUploadState()
}

/**
 * Profile-specific error types with recovery context.
 */
sealed class ProfileError(val message: String, val isRecoverable: Boolean = true) {
    data class Authentication(private val msg: String) : ProfileError(msg, false)
    data class LoadingFailed(private val msg: String) : ProfileError(msg, true)
    data class UploadFailed(private val msg: String) : ProfileError(msg, true)
    data class DeletionFailed(private val msg: String) : ProfileError(msg, true)
    data class NetworkError(private val msg: String) : ProfileError(msg, true)
}

/**
 * Profile operations for retry functionality.
 */
sealed class ProfileOperation {
    data class UploadImage(val imageUri: Uri, val cropRect: Rect?) : ProfileOperation()
    data object DeleteImage : ProfileOperation()
}

/**
 * Profile events for user interactions and system actions.
 */
sealed class ProfileEvent {
    data object LoadProfile : ProfileEvent()
    data class UploadImage(val imageUri: Uri, val cropRect: Rect?) : ProfileEvent()
    data object DeleteImage : ProfileEvent()
    data object ClearError : ProfileEvent()
    data object RetryLastOperation : ProfileEvent()
}