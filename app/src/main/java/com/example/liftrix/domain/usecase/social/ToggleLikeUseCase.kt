package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.social.EngagementRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import timber.log.Timber
import javax.inject.Inject

/**
 * Toggle like status for a workout post with optimistic updates.
 * 
 * Implements social engagement pattern from CLAUDE.md:
 * - Optimistic updates for immediate UI feedback
 * - Background sync with error recovery
 * - LiftrixResult<T> error handling
 * - Privacy-aware engagement
 */
class ToggleLikeUseCase @Inject constructor(
    private val engagementRepository: EngagementRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {
    
    /**
     * Toggle like status for a post.
     * 
     * @param postId Post to like/unlike
     * @return New like state (true if liked, false if unliked)
     */
    suspend operator fun invoke(postId: String): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "TOGGLE_LIKE_FAILED",
                errorMessage = "Failed to toggle like status",
                analyticsContext = mapOf(
                    "operation" to "TOGGLE_LIKE",
                    "post_id" to postId,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        // Get current user ID
        val userId = getCurrentUserIdUseCase() ?: throw IllegalStateException("User not authenticated")
        
        Timber.d("Toggling like for post: $postId by user: $userId")
        
        // Toggle like with optimistic update
        val result = engagementRepository.toggleLike(postId, userId)
        
        result.fold(
            onSuccess = { newLikeState ->
                Timber.i("Like toggled successfully for post $postId: $newLikeState")
                newLikeState
            },
            onFailure = { error ->
                Timber.e(error, "Failed to toggle like for post: $postId")
                throw error
            }
        )
    }
}