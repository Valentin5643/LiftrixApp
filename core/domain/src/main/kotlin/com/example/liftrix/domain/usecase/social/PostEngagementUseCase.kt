package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.CreateCommentRequest
import com.example.liftrix.domain.model.social.EngagementDataSource
import com.example.liftrix.domain.model.social.PostComment
import com.example.liftrix.domain.model.social.PostEngagementStats
import com.example.liftrix.domain.repository.social.EngagementRepository
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * Consolidated use case for post engagement operations.
 *
 * Consolidates:
 * - ToggleLikeUseCase (like/unlike posts)
 * - ToggleSaveUseCase (save/unsave posts)
 * - CreateCommentUseCase (comment on posts)
 * - GetPostEngagementStatusUseCase (get engagement metrics)
 *
 * Part of Phase 3: Social & Workout Domains consolidation.
 * Ref: SPEC-20251031-usecase-consolidation.md
 *
 * Implements social engagement pattern from CLAUDE.md:
 * - Optimistic updates for immediate UI feedback
 * - Real-time comment sync for live updates
 * - Privacy enforcement for engagement visibility
 * - Parallel fetching of engagement data
 */
class PostEngagementUseCase @Inject constructor(
    private val engagementRepository: EngagementRepository,
    private val authQueryUseCase: AuthQueryUseCase
) {

    /**
     * Toggle like status for a post with optimistic updates.
     * Replaces: ToggleLikeUseCase.invoke()
     *
     * @param postId Post to like/unlike
     * @return New like state (true if liked, false if unliked)
     */
    suspend fun toggleLike(postId: String): LiftrixResult<Boolean> = liftrixCatching(
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
        val userId = authQueryUseCase(waitForAuth = false).getOrThrow()

        // Toggle like with optimistic update
        val result = engagementRepository.toggleLike(postId, userId.value)

        result.fold(
            onSuccess = { newLikeState ->
                newLikeState
            },
            onFailure = { error ->
                throw error
            }
        )
    }

    /**
     * Toggle save status for a post with optimistic updates.
     * Replaces: ToggleSaveUseCase.invoke()
     *
     * @param postId Post to save/unsave
     * @return New save state (true if saved, false if unsaved)
     */
    suspend fun toggleSave(postId: String): LiftrixResult<Boolean> = liftrixCatching(
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
        val userId = authQueryUseCase(waitForAuth = false).getOrThrow()

        // Toggle save with optimistic update
        val result = engagementRepository.toggleSave(postId, userId.value)

        result.fold(
            onSuccess = { newSaveState ->
                newSaveState
            },
            onFailure = { error ->
                throw error
            }
        )
    }

    /**
     * Create a comment on a post with real-time sync.
     * Replaces: CreateCommentUseCase.invoke()
     *
     * @param postId Post to comment on
     * @param content Comment content
     * @param parentCommentId Optional parent comment for replies
     * @return Created comment
     */
    suspend fun createComment(
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
        val userId = authQueryUseCase(waitForAuth = false).getOrThrow()

        // Create comment request
        val request = CreateCommentRequest(
            postId = postId,
            content = content,
            parentCommentId = parentCommentId
        )

        // Create comment with real-time sync
        val result = engagementRepository.createComment(userId.value, request)

        result.fold(
            onSuccess = { comment ->
                comment
            },
            onFailure = { error ->
                throw error
            }
        )
    }

    /**
     * Get complete engagement status for a post with parallel data fetching.
     * Replaces: GetPostEngagementStatusUseCase.invoke()
     *
     * @param postId Post to get engagement status for
     * @return Engagement status with like/save state and stats
     */
    suspend fun getEngagementStatus(postId: String): LiftrixResult<PostEngagementStatus> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "GET_ENGAGEMENT_STATUS_FAILED",
                errorMessage = "Failed to get engagement status",
                analyticsContext = mapOf(
                    "operation" to "GET_ENGAGEMENT_STATUS",
                    "post_id" to postId,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        // Get current user ID
        val userId = authQueryUseCase(waitForAuth = false).getOrThrow()

        // Fetch engagement data in parallel for performance
        coroutineScope {
            val likedDeferred = async {
                engagementRepository.isPostLiked(postId, userId.value)
            }
            val savedDeferred = async {
                engagementRepository.isPostSaved(postId, userId.value)
            }
            val statsDeferred = async {
                engagementRepository.getPostEngagementStats(postId, userId.value)
            }

            // Await all results
            val likedResult = likedDeferred.await()
            val savedResult = savedDeferred.await()
            val statsResult = statsDeferred.await()

            // Handle results with proper error propagation
            val isLiked = likedResult.fold(
                onSuccess = { it },
                onFailure = {
                    false
                }
            )

            val isSaved = savedResult.fold(
                onSuccess = { it },
                onFailure = {
                    false
                }
            )

            // Determine data source and stats with proper error tracking
            val dataSource = statsResult.fold(
                onSuccess = { stats ->
                    EngagementDataSource.Loaded(stats)
                },
                onFailure = { error ->
                    // Create fallback stats with clear indication this is not real data
                    val fallbackStats = PostEngagementStats(
                        postId = postId,
                        likeCount = 0,
                        commentCount = 0,
                        shareCount = 0,
                        saveCount = 0,
                        isLikedByViewer = isLiked,
                        isSavedByViewer = isSaved
                    )
                    EngagementDataSource.Fallback(
                        reason = error.message ?: "Stats fetch failed",
                        fallbackStats = fallbackStats
                    )
                }
            )

            // Extract stats from data source
            val stats = when (dataSource) {
                is EngagementDataSource.Loaded -> dataSource.stats
                is EngagementDataSource.Fallback -> dataSource.fallbackStats
                is EngagementDataSource.Unavailable -> PostEngagementStats(
                    postId = postId,
                    likeCount = 0,
                    commentCount = 0,
                    shareCount = 0,
                    saveCount = 0,
                    isLikedByViewer = false,
                    isSavedByViewer = false
                )
            }

            val engagementStatus = PostEngagementStatus(
                isLiked = isLiked,
                isSaved = isSaved,
                stats = stats.copy(
                    isLikedByViewer = isLiked,
                    isSavedByViewer = isSaved
                ),
                dataSource = dataSource
            )

            // Only log success if data was actually loaded, not using fallback
            when (dataSource) {
                is EngagementDataSource.Loaded -> {
                }
                is EngagementDataSource.Fallback -> {
                }
                is EngagementDataSource.Unavailable -> {
                }
            }

            engagementStatus
        }
    }

    /**
     * Comprehensive engagement status data for a post.
     *
     * @param isLiked Whether the viewer has liked the post
     * @param isSaved Whether the viewer has saved the post
     * @param stats Engagement statistics for the post
     * @param dataSource Source of the engagement data (Loaded, Fallback, or Unavailable)
     */
    data class PostEngagementStatus(
        val isLiked: Boolean,
        val isSaved: Boolean,
        val stats: PostEngagementStats,
        val dataSource: EngagementDataSource
    )
}
