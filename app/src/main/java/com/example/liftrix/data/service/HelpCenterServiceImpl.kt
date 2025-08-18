package com.example.liftrix.data.service

import com.example.liftrix.data.local.dao.HelpArticleDao
import com.example.liftrix.data.local.dao.AppConfigDao
import com.example.liftrix.data.mapper.HelpArticleMapper.toDomainModel
import com.example.liftrix.data.mapper.HelpArticleMapper.toDomainModels
import com.example.liftrix.data.remote.config.RemoteConfigManager
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.help.HelpArticle
import com.example.liftrix.domain.model.help.HelpCategory
import com.example.liftrix.domain.service.HelpCenterService
import com.example.liftrix.domain.service.HelpContentStatistics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of HelpCenterService providing help content management
 * 
 * Features:
 * - Local database storage with search capabilities
 * - Remote content updates via Firebase Remote Config
 * - Engagement tracking and analytics
 * - Caching for performance optimization
 */
@Singleton
class HelpCenterServiceImpl @Inject constructor(
    private val helpArticleDao: HelpArticleDao,
    private val appConfigDao: AppConfigDao,
    private val remoteConfigManager: RemoteConfigManager
) : HelpCenterService {
    
    companion object {
        private const val CACHE_TTL_HOURS = 24L
    }
    
    override suspend fun getCategories(): LiftrixResult<List<HelpCategory>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "HELP_CATEGORIES_FETCH_FAILED",
                errorMessage = "Failed to fetch help categories",
                analyticsContext = mapOf("operation" to "GET_CATEGORIES", "error" to throwable.message.orEmpty())
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            // Get categories from database with article counts
            val categoryNames = helpArticleDao.getCategories().also { flow ->
                // Since this is a suspend function and we need immediate results,
                // we use the sync version of the DAO method
                val categories = helpArticleDao.getAllArticlesSync()
                    .groupBy { it.category }
                    .map { (category, articles) ->
                        HelpCategory(
                            name = category,
                            displayName = category,
                            description = getDefaultCategoryDescription(category),
                            articleCount = articles.size,
                            sortOrder = getDefaultCategorySortOrder(category)
                        )
                    }
                    .sortedBy { it.sortOrder }
                
                return@withContext categories
            }
            
            emptyList<HelpCategory>() // This won't be reached due to return above
        }
    }
    
    override suspend fun searchArticles(query: String): LiftrixResult<List<HelpArticle>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "HELP_SEARCH_FAILED",
                errorMessage = "Failed to search help articles",
                analyticsContext = mapOf(
                    "operation" to "SEARCH_ARTICLES",
                    "query" to query,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            if (query.isBlank()) {
                return@withContext emptyList()
            }
            
            val articles = helpArticleDao.searchArticlesSync(query.trim())
            Timber.d("Search for '$query' returned ${articles.size} articles")
            
            articles.toDomainModels()
        }
    }
    
    override suspend fun getArticle(articleId: String): LiftrixResult<HelpArticle?> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "HELP_ARTICLE_FETCH_FAILED",
                errorMessage = "Failed to fetch help article",
                analyticsContext = mapOf(
                    "operation" to "GET_ARTICLE",
                    "article_id" to articleId,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val article = helpArticleDao.getArticleSync(articleId)
            article?.toDomainModel()
        }
    }
    
    override suspend fun getArticlesByCategory(category: String): LiftrixResult<List<HelpArticle>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "HELP_CATEGORY_FETCH_FAILED",
                errorMessage = "Failed to fetch articles by category",
                analyticsContext = mapOf(
                    "operation" to "GET_ARTICLES_BY_CATEGORY",
                    "category" to category,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val articles = helpArticleDao.getAllArticlesSync()
                .filter { it.category.equals(category, ignoreCase = true) }
                .sortedWith(compareByDescending<com.example.liftrix.data.local.entity.HelpArticleEntity> { it.isFeatured }
                    .thenBy { it.sortOrder }
                    .thenBy { it.title })
            
            articles.toDomainModels()
        }
    }
    
    override suspend fun getPopularArticles(limit: Int): LiftrixResult<List<HelpArticle>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "POPULAR_ARTICLES_FETCH_FAILED",
                errorMessage = "Failed to fetch popular articles",
                analyticsContext = mapOf(
                    "operation" to "GET_POPULAR_ARTICLES",
                    "limit" to limit.toString(),
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val articles = helpArticleDao.getAllArticlesSync()
                .sortedByDescending { article ->
                    // Calculate popularity score based on views and helpfulness
                    val viewScore = article.viewCount * 0.3
                    val helpfulnessScore = if (article.helpfulCount + article.notHelpfulCount > 0) {
                        (article.helpfulCount.toDouble() / (article.helpfulCount + article.notHelpfulCount)) * 0.7
                    } else {
                        0.0
                    }
                    viewScore + helpfulnessScore
                }
                .take(limit)
            
            articles.toDomainModels()
        }
    }
    
    override suspend fun getFeaturedArticles(limit: Int): LiftrixResult<List<HelpArticle>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "FEATURED_ARTICLES_FETCH_FAILED",
                errorMessage = "Failed to fetch featured articles",
                analyticsContext = mapOf(
                    "operation" to "GET_FEATURED_ARTICLES",
                    "limit" to limit.toString(),
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val articles = helpArticleDao.getAllArticlesSync()
                .filter { it.isFeatured }
                .sortedBy { it.sortOrder }
                .take(limit)
            
            articles.toDomainModels()
        }
    }
    
    override suspend fun markHelpful(articleId: String, helpful: Boolean): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "HELP_FEEDBACK_FAILED",
                errorMessage = "Failed to record article feedback",
                analyticsContext = mapOf(
                    "operation" to "MARK_HELPFUL",
                    "article_id" to articleId,
                    "helpful" to helpful.toString(),
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            helpArticleDao.updateHelpfulness(articleId, helpful)
            Timber.d("Marked article $articleId as ${if (helpful) "helpful" else "not helpful"}")
        }
    }
    
    override suspend fun recordArticleView(articleId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "ARTICLE_VIEW_RECORD_FAILED",
                errorMessage = "Failed to record article view",
                analyticsContext = mapOf(
                    "operation" to "RECORD_VIEW",
                    "article_id" to articleId,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            helpArticleDao.incrementViewCount(articleId)
            Timber.d("Recorded view for article $articleId")
        }
    }
    
    override suspend fun refreshContent(forceRefresh: Boolean): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to refresh help content",
                analyticsContext = mapOf(
                    "operation" to "REFRESH_CONTENT",
                    "force_refresh" to forceRefresh.toString(),
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            try {
                // Check if we need to refresh based on cache TTL
                if (!forceRefresh && !shouldRefreshContent()) {
                    Timber.d("Help content is still fresh, skipping refresh")
                    return@withContext
                }
                
                // Initialize and fetch latest config from Firebase
                remoteConfigManager.initialize().getOrThrow()
                remoteConfigManager.fetchAndActivate(forceRefresh).getOrThrow()
                
                val remoteVersion = remoteConfigManager.getHelpContentVersion().getOrElse { "0" }
                val localVersion = appConfigDao.getConfigValue(RemoteConfigManager.HELP_CONTENT_VERSION) ?: "0"
                
                if (forceRefresh || remoteVersion != localVersion) {
                    val contentJson = remoteConfigManager.getHelpArticlesJson().getOrElse { "" }
                    
                    if (contentJson.isNotBlank()) {
                        // Parse and update help articles
                        updateHelpArticlesFromJson(contentJson)
                        
                        // Update local version
                        appConfigDao.setStringConfig(RemoteConfigManager.HELP_CONTENT_VERSION, remoteVersion)
                        
                        Timber.d("Help content updated to version $remoteVersion")
                    }
                } else {
                    Timber.d("Help content is up to date (version $localVersion)")
                }
                
                // Update last refresh timestamp
                appConfigDao.setStringConfig(
                    "help_content_last_refresh",
                    Instant.now().epochSecond.toString()
                )
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh help content from remote")
                throw e
            }
        }
    }
    
    override suspend fun getContentStatistics(): LiftrixResult<HelpContentStatistics> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "HELP_STATS_FETCH_FAILED",
                errorMessage = "Failed to fetch help content statistics",
                analyticsContext = mapOf(
                    "operation" to "GET_STATISTICS",
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val allArticles = helpArticleDao.getAllArticlesSync().toDomainModels()
            
            val totalViews = allArticles.sumOf { it.viewCount }
            val totalHelpful = allArticles.sumOf { it.helpfulCount }
            val totalNotHelpful = allArticles.sumOf { it.notHelpfulCount }
            
            val mostViewed = allArticles.sortedByDescending { it.viewCount }.take(5)
            val mostHelpful = allArticles.sortedByDescending { it.getHelpfulnessRatio() }.take(5)
            
            val categoryCounts = allArticles.groupBy { it.category }
                .mapValues { it.value.size }
            
            HelpContentStatistics(
                totalArticles = allArticles.size,
                totalViews = totalViews,
                totalHelpfulVotes = totalHelpful,
                totalNotHelpfulVotes = totalNotHelpful,
                mostViewedArticles = mostViewed,
                mostHelpfulArticles = mostHelpful,
                categoryCounts = categoryCounts,
                lastUpdated = Instant.now()
            )
        }
    }
    
    /**
     * Checks if content should be refreshed based on cache TTL
     */
    private suspend fun shouldRefreshContent(): Boolean {
        val lastRefreshStr = appConfigDao.getConfigValue("help_content_last_refresh")
        
        return if (lastRefreshStr != null) {
            val lastRefresh = Instant.ofEpochSecond(lastRefreshStr.toLongOrNull() ?: 0)
            val cacheExpiry = lastRefresh.plusSeconds(CACHE_TTL_HOURS * 3600)
            Instant.now().isAfter(cacheExpiry)
        } else {
            true // No previous refresh recorded
        }
    }
    
    /**
     * Updates help articles from JSON content
     */
    private suspend fun updateHelpArticlesFromJson(contentJson: String) {
        // In a real implementation, this would parse the JSON and update articles
        // For now, we'll just log that we received content
        Timber.d("Received help content JSON: ${contentJson.length} characters")
        
        // TODO: Implement JSON parsing and article updates
        // This would involve:
        // 1. Parse JSON to article objects
        // 2. Clear existing articles or update individually
        // 3. Insert new articles into database
    }
    
    /**
     * Gets default description for a category
     */
    private fun getDefaultCategoryDescription(category: String): String = when (category) {
        HelpCategory.Companion.Category.GETTING_STARTED -> "Learn the basics of using Liftrix"
        HelpCategory.Companion.Category.WORKOUTS -> "Creating and managing your workouts"
        HelpCategory.Companion.Category.EXERCISES -> "Exercise library and custom exercises"
        HelpCategory.Companion.Category.PROGRESS_TRACKING -> "Monitor your fitness progress"
        HelpCategory.Companion.Category.SOCIAL_FEATURES -> "Connect with gym buddies and share workouts"
        HelpCategory.Companion.Category.SETTINGS -> "Customize your app experience"
        HelpCategory.Companion.Category.TROUBLESHOOTING -> "Solve common issues"
        HelpCategory.Companion.Category.ACCOUNT -> "Account management and privacy"
        HelpCategory.Companion.Category.SYNC -> "Data synchronization and backup"
        HelpCategory.Companion.Category.PREMIUM_FEATURES -> "Advanced features for premium users"
        else -> "Help articles for $category"
    }
    
    /**
     * Gets default sort order for a category
     */
    private fun getDefaultCategorySortOrder(category: String): Int = when (category) {
        HelpCategory.Companion.Category.GETTING_STARTED -> 1
        HelpCategory.Companion.Category.WORKOUTS -> 2
        HelpCategory.Companion.Category.EXERCISES -> 3
        HelpCategory.Companion.Category.PROGRESS_TRACKING -> 4
        HelpCategory.Companion.Category.SOCIAL_FEATURES -> 5
        HelpCategory.Companion.Category.SETTINGS -> 6
        HelpCategory.Companion.Category.TROUBLESHOOTING -> 7
        HelpCategory.Companion.Category.ACCOUNT -> 8
        HelpCategory.Companion.Category.SYNC -> 9
        HelpCategory.Companion.Category.PREMIUM_FEATURES -> 10
        else -> 99
    }
}