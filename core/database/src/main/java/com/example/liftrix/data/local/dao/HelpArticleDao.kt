package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.HelpArticleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for help article operations
 * 
 * Provides methods to retrieve, insert, and update help articles
 * Includes optimized search capabilities and engagement tracking
 */
@Dao
interface HelpArticleDao {
    
    /**
     * Retrieves all help articles
     * @return Flow of all help articles
     */
    @Query("SELECT * FROM help_articles ORDER BY is_featured DESC, sort_order ASC, title ASC")
    fun getAllArticles(): Flow<List<HelpArticleEntity>>
    
    /**
     * Retrieves all help articles synchronously
     * @return List of all help articles
     */
    @Query("SELECT * FROM help_articles ORDER BY is_featured DESC, sort_order ASC, title ASC")
    suspend fun getAllArticlesSync(): List<HelpArticleEntity>
    
    /**
     * Retrieves a specific help article by ID
     * @param articleId The article identifier
     * @return Flow of HelpArticleEntity or null if not found
     */
    @Query("SELECT * FROM help_articles WHERE article_id = :articleId")
    fun getArticle(articleId: String): Flow<HelpArticleEntity?>
    
    /**
     * Retrieves a specific help article by ID synchronously
     * @param articleId The article identifier
     * @return HelpArticleEntity or null if not found
     */
    @Query("SELECT * FROM help_articles WHERE article_id = :articleId")
    suspend fun getArticleSync(articleId: String): HelpArticleEntity?
    
    /**
     * Retrieves articles by category
     * @param category The article category
     * @return Flow of articles in the specified category
     */
    @Query("SELECT * FROM help_articles WHERE category = :category ORDER BY is_featured DESC, sort_order ASC, title ASC")
    fun getArticlesByCategory(category: String): Flow<List<HelpArticleEntity>>
    
    /**
     * Retrieves featured articles for display on home screen
     * @param limit Maximum number of featured articles to return
     * @return Flow of featured articles
     */
    @Query("SELECT * FROM help_articles WHERE is_featured = 1 ORDER BY sort_order ASC, helpful_count DESC LIMIT :limit")
    fun getFeaturedArticles(limit: Int = 5): Flow<List<HelpArticleEntity>>
    
    /**
     * Retrieves popular articles based on view count and helpfulness
     * @param limit Maximum number of popular articles to return
     * @return Flow of popular articles
     */
    @Query("""
        SELECT * FROM help_articles 
        ORDER BY (view_count * 0.3 + helpful_count * 0.7) DESC, 
                 (helpful_count + not_helpful_count) DESC 
        LIMIT :limit
    """)
    fun getPopularArticles(limit: Int = 10): Flow<List<HelpArticleEntity>>
    
    /**
     * Searches articles by title, content, and keywords using FTS-like functionality
     * @param query The search query
     * @return Flow of matching articles ordered by relevance
     */
    @Query("""
        SELECT * FROM help_articles 
        WHERE title LIKE '%' || :query || '%' 
           OR content LIKE '%' || :query || '%' 
           OR keywords LIKE '%' || :query || '%'
        ORDER BY 
            CASE 
                WHEN title LIKE '%' || :query || '%' THEN 3
                WHEN keywords LIKE '%' || :query || '%' THEN 2
                WHEN content LIKE '%' || :query || '%' THEN 1
                ELSE 0
            END DESC,
            helpful_count DESC,
            view_count DESC
    """)
    fun searchArticles(query: String): Flow<List<HelpArticleEntity>>
    
    /**
     * Searches articles synchronously for immediate results
     * @param query The search query
     * @return List of matching articles ordered by relevance
     */
    @Query("""
        SELECT * FROM help_articles 
        WHERE title LIKE '%' || :query || '%' 
           OR content LIKE '%' || :query || '%' 
           OR keywords LIKE '%' || :query || '%'
        ORDER BY 
            CASE 
                WHEN title LIKE '%' || :query || '%' THEN 3
                WHEN keywords LIKE '%' || :query || '%' THEN 2
                WHEN content LIKE '%' || :query || '%' THEN 1
                ELSE 0
            END DESC,
            helpful_count DESC,
            view_count DESC
    """)
    suspend fun searchArticlesSync(query: String): List<HelpArticleEntity>
    
    /**
     * Gets all unique categories from help articles
     * @return Flow of distinct categories
     */
    @Query("SELECT DISTINCT category FROM help_articles ORDER BY category ASC")
    fun getCategories(): Flow<List<String>>
    
    /**
     * Gets category count for displaying in UI
     * @return Flow of category with article counts
     */
    @Query("SELECT category, COUNT(*) as count FROM help_articles GROUP BY category ORDER BY count DESC, category ASC")
    fun getCategoriesWithCounts(): Flow<Map<@MapColumn(columnName = "category") String, @MapColumn(columnName = "count") Int>>
    
    /**
     * Inserts a new help article or replaces existing one
     * @param article The article entity to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticle(article: HelpArticleEntity)
    
    /**
     * Inserts multiple help articles
     * @param articles List of article entities to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<HelpArticleEntity>)
    
    /**
     * Updates an existing help article
     * @param article The article entity to update
     */
    @Update
    suspend fun updateArticle(article: HelpArticleEntity)
    
    /**
     * Increments the view count for an article
     * @param articleId The article identifier
     */
    @Query("UPDATE help_articles SET view_count = view_count + 1 WHERE article_id = :articleId")
    suspend fun incrementViewCount(articleId: String)
    
    /**
     * Updates the helpful count for an article
     * @param articleId The article identifier
     * @param helpful True if marking as helpful, false if not helpful
     */
    @Query("""
        UPDATE help_articles 
        SET helpful_count = CASE WHEN :helpful THEN helpful_count + 1 ELSE helpful_count END,
            not_helpful_count = CASE WHEN :helpful THEN not_helpful_count ELSE not_helpful_count + 1 END
        WHERE article_id = :articleId
    """)
    suspend fun updateHelpfulness(articleId: String, helpful: Boolean)
    
    /**
     * Deletes a specific help article
     * @param articleId The article identifier
     */
    @Query("DELETE FROM help_articles WHERE article_id = :articleId")
    suspend fun deleteArticle(articleId: String)
    
    /**
     * Deletes all help articles (for testing or data refresh)
     */
    @Query("DELETE FROM help_articles")
    suspend fun deleteAllArticles()
    
    /**
     * Updates articles from remote configuration
     * Replaces existing articles with the same ID
     * @param articles List of articles from remote
     */
    @androidx.room.Transaction
    suspend fun updateArticlesFromRemote(articles: List<HelpArticleEntity>) {
        // First, mark all existing articles as outdated
        // Then insert or replace with new articles
        articles.forEach { article ->
            val existing = getArticleSync(article.articleId)
            if (existing != null) {
                // Preserve engagement data
                insertArticle(article.copy(
                    viewCount = existing.viewCount,
                    helpfulCount = existing.helpfulCount,
                    notHelpfulCount = existing.notHelpfulCount
                ))
            } else {
                insertArticle(article)
            }
        }
    }
    
    /**
     * Gets the total number of help articles
     * @return Total count of articles
     */
    @Query("SELECT COUNT(*) FROM help_articles")
    suspend fun getArticleCount(): Int
    
    /**
     * Checks if any articles exist for a category
     * @param category The category to check
     * @return True if articles exist in the category
     */
    @Query("SELECT COUNT(*) > 0 FROM help_articles WHERE category = :category")
    suspend fun hasArticlesInCategory(category: String): Boolean
    
    /**
     * Gets articles with low engagement (for content improvement)
     * @param minViews Minimum view count threshold
     * @return Flow of articles below engagement threshold
     */
    @Query("""
        SELECT * FROM help_articles 
        WHERE view_count < :minViews 
           OR (helpful_count + not_helpful_count > 5 AND helpful_count < not_helpful_count)
        ORDER BY view_count ASC, helpful_count ASC
    """)
    fun getLowEngagementArticles(minViews: Int = 10): Flow<List<HelpArticleEntity>>
}