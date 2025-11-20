package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.social.EngagementRepository
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import timber.log.Timber
import javax.inject.Inject

/**
 * Record a share action for analytics and engagement tracking.
 * 
 * Implements social engagement pattern from CLAUDE.md:
 * - Share action analytics tracking
 * - Platform-aware sharing methods
 * - LiftrixResult<T> error handling
 * - Share count updates
 */
class RecordShareUseCase @Inject constructor(
    private val engagementRepository: EngagementRepository,
    private val authQueryUseCase: AuthQueryUseCase
) {
    
    /**
     * Record a share action for a post.
     * 
     * @param postId Post that was shared
     * @param shareMethod How the post was shared (e.g., "copy_link", "instagram", "whatsapp")
     * @return Success result
     */
    suspend operator fun invoke(
        postId: String,
        shareMethod: String
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "RECORD_SHARE_FAILED",
                errorMessage = "Failed to record share action",
                analyticsContext = mapOf(
                    "operation" to "RECORD_SHARE",
                    "post_id" to postId,
                    "share_method" to shareMethod,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        // Validate share method
        if (shareMethod.isBlank()) {
            throw IllegalArgumentException("Share method cannot be empty")
        }
        
        // Get current user ID
        val userId = authQueryUseCase(waitForAuth = false).getOrThrow()
        
        Timber.d("Recording share for post: $postId by user: $userId via $shareMethod")
        
        // Record share action
        val result = engagementRepository.recordShare(postId, userId, shareMethod)
        
        result.fold(
            onSuccess = {
                Timber.i("Share recorded successfully for post $postId via $shareMethod")
            },
            onFailure = { error ->
                Timber.e(error, "Failed to record share for post: $postId")
                throw error
            }
        )
    }
}