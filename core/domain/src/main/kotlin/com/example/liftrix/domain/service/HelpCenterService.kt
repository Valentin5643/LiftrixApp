package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.help.HelpArticle
import com.example.liftrix.domain.model.help.HelpCategory

/**
 * Service interface for help center functionality
 * Provides operations for managing and accessing help content
 */
interface HelpCenterService {
    
    /**
     * Retrieves all available help categories
     * @return LiftrixResult containing list of help categories
     */
    suspend fun getCategories(): LiftrixResult<List<HelpCategory>>
    
    /**
     * Searches help articles by query string
     * @param query Search query to match against titles, content, and keywords
     * @return LiftrixResult containing list of matching articles ordered by relevance
     */
    suspend fun searchArticles(query: String): LiftrixResult<List<HelpArticle>>
    
    /**
     * Retrieves a specific help article by ID
     * @param articleId Unique identifier for the article
     * @return LiftrixResult containing the help article or null if not found
     */
    suspend fun getArticle(articleId: String): LiftrixResult<HelpArticle?>
    
    /**
     * Retrieves articles in a specific category
     * @param category Category name to filter by
     * @return LiftrixResult containing list of articles in the category
     */
    suspend fun getArticlesByCategory(category: String): LiftrixResult<List<HelpArticle>>
    
    /**
     * Retrieves popular help articles based on engagement metrics
     * @param limit Maximum number of articles to return
     * @return LiftrixResult containing list of popular articles
     */
    suspend fun getPopularArticles(limit: Int = 5): LiftrixResult<List<HelpArticle>>
    
    /**
     * Retrieves featured help articles for homepage display
     * @param limit Maximum number of featured articles to return
     * @return LiftrixResult containing list of featured articles
     */
    suspend fun getFeaturedArticles(limit: Int = 3): LiftrixResult<List<HelpArticle>>
    
    /**
     * Marks an article as helpful or not helpful
     * @param articleId Unique identifier for the article
     * @param helpful True if marking as helpful, false if not helpful
     * @return LiftrixResult indicating success or failure
     */
    suspend fun markHelpful(articleId: String, helpful: Boolean): LiftrixResult<Unit>
    
    /**
     * Records a view for an article (for analytics)
     * @param articleId Unique identifier for the article
     * @return LiftrixResult indicating success or failure
     */
    suspend fun recordArticleView(articleId: String): LiftrixResult<Unit>
    
    /**
     * Refreshes help content from remote sources
     * @param forceRefresh Whether to force refresh even if cache is valid
     * @return LiftrixResult indicating success or failure of refresh operation
     */
    suspend fun refreshContent(forceRefresh: Boolean = false): LiftrixResult<Unit>
    
    /**
     * Gets help content statistics for analytics
     * @return LiftrixResult containing content statistics
     */
    suspend fun getContentStatistics(): LiftrixResult<HelpContentStatistics>
}

/**
 * Statistics about help content engagement and usage
 */
data class HelpContentStatistics(
    val totalArticles: Int,
    val totalViews: Int,
    val totalHelpfulVotes: Int,
    val totalNotHelpfulVotes: Int,
    val mostViewedArticles: List<HelpArticle>,
    val mostHelpfulArticles: List<HelpArticle>,
    val categoryCounts: Map<String, Int>,
    val lastUpdated: java.time.Instant
) {
    /**
     * Calculates overall helpfulness ratio across all articles
     */
    fun getOverallHelpfulnessRatio(): Double {
        val totalVotes = totalHelpfulVotes + totalNotHelpfulVotes
        return if (totalVotes > 0) {
            totalHelpfulVotes.toDouble() / totalVotes.toDouble()
        } else {
            0.0
        }
    }
    
    /**
     * Gets average views per article
     */
    fun getAverageViewsPerArticle(): Double {
        return if (totalArticles > 0) {
            totalViews.toDouble() / totalArticles.toDouble()
        } else {
            0.0
        }
    }
}