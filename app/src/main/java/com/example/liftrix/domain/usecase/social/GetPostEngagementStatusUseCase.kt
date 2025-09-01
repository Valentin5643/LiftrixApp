package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.PostEngagementStats
import com.example.liftrix.domain.repository.social.EngagementRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import javax.inject.Inject

/**
 * Get comprehensive engagement status for a post (like status, save status, stats).
 * 
 * Implements social engagement pattern from CLAUDE.md:
 * - Parallel fetching of engagement data
 * - Privacy-aware engagement visibility
 * - LiftrixResult<T> error handling
 * - Comprehensive engagement statistics
 */
class GetPostEngagementStatusUseCase @Inject constructor(
    private val engagementRepository: EngagementRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {
    
    /**
     * Comprehensive engagement status data for a post.
     */
    data class PostEngagementStatus(
        val isLiked: Boolean,
        val isSaved: Boolean,
        val stats: PostEngagementStats
    )
    
    /**
     * Get complete engagement status for a post.
     * 
     * @param postId Post to get engagement status for
     * @return Engagement status with like/save state and stats
     */
    suspend operator fun invoke(postId: String): LiftrixResult<PostEngagementStatus> = liftrixCatching(
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
        val userId = getCurrentUserIdUseCase() ?: throw IllegalStateException("User not authenticated")
        
        Timber.d("Getting engagement status for post: $postId by user: $userId")
        
        // Fetch engagement data in parallel for performance
        coroutineScope {
            val likedDeferred = async {
                engagementRepository.isPostLiked(postId, userId)
            }
            val savedDeferred = async {
                engagementRepository.isPostSaved(postId, userId)
            }
            val statsDeferred = async {
                engagementRepository.getPostEngagementStats(postId, userId)
            }
            
            // Await all results
            val likedResult = likedDeferred.await()
            val savedResult = savedDeferred.await()
            val statsResult = statsDeferred.await()
            
            // Handle results with proper error propagation
            val isLiked = likedResult.fold(
                onSuccess = { it },
                onFailure = { 
                    Timber.w("Failed to get like status for post $postId, defaulting to false")
                    false
                }
            )
            
            val isSaved = savedResult.fold(
                onSuccess = { it },
                onFailure = { 
                    Timber.w("Failed to get save status for post $postId, defaulting to false")
                    false
                }
            )
            
            val stats = statsResult.fold(
                onSuccess = { it },
                onFailure = { error ->
                    Timber.e(error, "Failed to get engagement stats for post $postId")
                    // Return default stats if stats fetch fails
                    PostEngagementStats(
                        postId = postId,
                        likeCount = 0,
                        commentCount = 0,
                        shareCount = 0,
                        saveCount = 0,
                        isLikedByViewer = isLiked,
                        isSavedByViewer = isSaved
                    )
                }
            )
            
            val engagementStatus = PostEngagementStatus(
                isLiked = isLiked,
                isSaved = isSaved,
                stats = stats.copy(
                    isLikedByViewer = isLiked,
                    isSavedByViewer = isSaved
                )
            )
            
            Timber.i("Engagement status retrieved for post $postId: liked=$isLiked, saved=$isSaved")
            
            engagementStatus
        }
    }
}