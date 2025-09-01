package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.CreateCommentRequest
import com.example.liftrix.domain.model.social.PostComment
import com.example.liftrix.domain.repository.social.EngagementRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import timber.log.Timber
import javax.inject.Inject

/**
 * Create a comment on a workout post with real-time sync.
 * 
 * Implements social engagement pattern from CLAUDE.md:
 * - Real-time comment sync for live updates
 * - Privacy enforcement for comment visibility
 * - LiftrixResult<T> error handling
 * - Nested comment support
 */
class CreateCommentUseCase @Inject constructor(
    private val engagementRepository: EngagementRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {
    
    /**
     * Create a new comment on a post.
     * 
     * @param postId Post to comment on
     * @param content Comment content
     * @param parentCommentId Optional parent comment for replies
     * @return Created comment
     */
    suspend operator fun invoke(
        postId: String,
        content: String,
        parentCommentId: String? = null
    ): LiftrixResult<PostComment> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CREATE_COMMENT_FAILED",
                errorMessage = "Failed to create comment",
                analyticsContext = mapOf(
                    "operation" to "CREATE_COMMENT",
                    "post_id" to postId,
                    "has_parent" to (parentCommentId != null).toString(),
                    "content_length" to content.length.toString(),
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        // Validate comment content
        if (content.isBlank()) {
            throw IllegalArgumentException("Comment content cannot be empty")
        }
        
        if (content.length > 500) {
            throw IllegalArgumentException("Comment cannot exceed 500 characters")
        }
        
        // Get current user ID
        val userId = getCurrentUserIdUseCase() ?: throw IllegalStateException("User not authenticated")
        
        Timber.d("Creating comment for post: $postId by user: $userId")
        
        // Create comment request
        val request = CreateCommentRequest(
            postId = postId,
            content = content,
            parentCommentId = parentCommentId
        )
        
        // Create comment with real-time sync
        val result = engagementRepository.createComment(userId, request)
        
        result.fold(
            onSuccess = { comment ->
                Timber.i("Comment created successfully for post $postId: ${comment.id}")
                comment
            },
            onFailure = { error ->
                Timber.e(error, "Failed to create comment for post: $postId")
                throw error
            }
        )
    }
}