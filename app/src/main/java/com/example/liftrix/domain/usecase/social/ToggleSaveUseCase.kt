package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.social.EngagementRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import timber.log.Timber
import javax.inject.Inject

/**
 * Toggle save status for a workout post with optimistic updates.
 * 
 * Implements social engagement pattern from CLAUDE.md:
 * - Optimistic updates for immediate UI feedback
 * - Background sync with error recovery
 * - LiftrixResult<T> error handling
 * - Save/bookmark functionality for later access
 */
class ToggleSaveUseCase @Inject constructor(
    private val engagementRepository: EngagementRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {
    
    /**
     * Toggle save status for a post.
     * 
     * @param postId Post to save/unsave
     * @return New save state (true if saved, false if unsaved)
     */
    suspend operator fun invoke(postId: String): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "TOGGLE_SAVE_FAILED",
                errorMessage = "Failed to toggle save status",
                analyticsContext = mapOf(
                    "operation" to "TOGGLE_SAVE",
                    "post_id" to postId,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        // Get current user ID
        val userId = getCurrentUserIdUseCase() ?: throw IllegalStateException("User not authenticated")
        
        Timber.d("Toggling save for post: $postId by user: $userId")
        
        // Toggle save with optimistic update
        val result = engagementRepository.toggleSave(postId, userId)
        
        result.fold(
            onSuccess = { newSaveState ->
                Timber.i("Save toggled successfully for post $postId: $newSaveState")
                newSaveState
            },
            onFailure = { error ->
                Timber.e(error, "Failed to toggle save for post: $postId")
                throw error
            }
        )
    }
}