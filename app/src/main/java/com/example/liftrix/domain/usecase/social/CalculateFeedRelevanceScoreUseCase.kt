package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.service.FeedCacheService
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import javax.inject.Inject

/**
 * Use case for calculating feed relevance scores for personalized feed generation.
 * Implements the scoring algorithm defined in SPEC-20250113-social-feed-engagement.
 * 
 * Scoring factors:
 * - Recency (40 points max): Newer posts get higher scores with decay over time
 * - Engagement (30 points max): Likes and comments boost relevance
 * - Personal Records (20 points max): PRs are highly relevant for fitness community
 * - Media Presence (10 points max): Posts with photos/videos are more engaging
 * - Following Relationship (15 points max): Posts from followed users get priority
 * - Interaction History (10 points max): Users they've engaged with before
 * 
 * Total score range: 0-125 points (normalized to 0-100)
 */
class CalculateFeedRelevanceScoreUseCase @Inject constructor(
    private val feedCacheService: FeedCacheService
) {
    
    /**
     * Calculates the relevance score for a specific post and viewer combination.
     * 
     * @param postId The workout post to score
     * @param viewerId The user viewing the feed
     * @return Relevance score between 0.0 and 100.0
     */
    suspend operator fun invoke(
        postId: String,
        viewerId: String
    ): LiftrixResult<Double> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                errorMessage = "Failed to calculate feed relevance score",
                operation = "CALCULATE_FEED_RELEVANCE_SCORE",
                analyticsContext = mapOf(
                    "post_id" to postId,
                    "viewer_id" to viewerId,
                    "error" to throwable.message
                )
            )
        }
    ) {
        feedCacheService.calculateRelevanceScore(postId, viewerId).getOrThrow()
    }
    
    /**
     * Batch calculates relevance scores for multiple posts.
     * Optimized for feed generation performance.
     * 
     * @param postIds List of post IDs to score
     * @param viewerId The user viewing the feed
     * @return Map of post ID to relevance score
     */
    suspend fun batchCalculateScores(
        postIds: List<String>,
        viewerId: String
    ): LiftrixResult<Map<String, Double>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                errorMessage = "Failed to batch calculate relevance scores",
                operation = "BATCH_CALCULATE_RELEVANCE_SCORES",
                analyticsContext = mapOf(
                    "post_count" to postIds.size,
                    "viewer_id" to viewerId,
                    "error" to throwable.message
                )
            )
        }
    ) {
        val scores = mutableMapOf<String, Double>()
        
        postIds.forEach { postId ->
            val score = feedCacheService.calculateRelevanceScore(postId, viewerId).getOrNull()
            if (score != null) {
                scores[postId] = score
            }
        }
        
        scores
    }
}