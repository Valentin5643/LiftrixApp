package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters
import java.time.Instant

/**
 * Room entity representing help articles in local database
 * Stores help content with search capabilities and user engagement metrics
 * 
 * Uses snake_case column names following existing database conventions
 * and proper type converters for Instant fields
 */
@Entity(tableName = "help_articles")
@TypeConverters(DateTimeConverters::class)
data class HelpArticleEntity(
    @PrimaryKey
    @ColumnInfo(name = "article_id")
    val articleId: String,
    
    @ColumnInfo(name = "category")
    val category: String,
    
    @ColumnInfo(name = "title")
    val title: String,
    
    @ColumnInfo(name = "content")
    val content: String,
    
    @ColumnInfo(name = "keywords")
    val keywords: String? = null,
    
    @ColumnInfo(name = "view_count")
    val viewCount: Int = 0,
    
    @ColumnInfo(name = "helpful_count")
    val helpfulCount: Int = 0,
    
    @ColumnInfo(name = "not_helpful_count")
    val notHelpfulCount: Int = 0,
    
    @ColumnInfo(name = "last_updated")
    val lastUpdated: Instant,
    
    @ColumnInfo(name = "version")
    val version: Int = 1,
    
    @ColumnInfo(name = "is_featured")
    val isFeatured: Boolean = false,
    
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0
) {
    companion object {
        /**
         * Creates a help article entity from basic information
         * 
         * @param articleId Unique identifier for the article
         * @param category Article category (e.g., "Getting Started", "Workouts")
         * @param title Article title
         * @param content Article content in Markdown or HTML
         * @param keywords Comma-separated keywords for search
         * @return HelpArticleEntity with default engagement metrics
         */
        fun create(
            articleId: String,
            category: String,
            title: String,
            content: String,
            keywords: String? = null,
            isFeatured: Boolean = false,
            sortOrder: Int = 0
        ): HelpArticleEntity = HelpArticleEntity(
            articleId = articleId,
            category = category,
            title = title,
            content = content,
            keywords = keywords,
            viewCount = 0,
            helpfulCount = 0,
            notHelpfulCount = 0,
            lastUpdated = Instant.now(),
            version = 1,
            isFeatured = isFeatured,
            sortOrder = sortOrder
        )
    }
    
    /**
     * Calculates the helpfulness ratio for this article
     * @return Ratio of helpful to total feedback (0.0 to 1.0)
     */
    fun getHelpfulnessRatio(): Double {
        val totalFeedback = helpfulCount + notHelpfulCount
        return if (totalFeedback > 0) {
            helpfulCount.toDouble() / totalFeedback.toDouble()
        } else {
            0.0
        }
    }
    
    /**
     * Calculates a relevance score for search ranking
     * @param query The search query
     * @return Score from 0.0 to 1.0 based on title/keyword matches and engagement
     */
    fun getRelevanceScore(query: String): Double {
        val normalizedQuery = query.lowercase()
        var score = 0.0
        
        // Title match (highest weight)
        if (title.lowercase().contains(normalizedQuery)) {
            score += 0.5
        }
        
        // Keywords match (medium weight)
        keywords?.let { keywordString ->
            if (keywordString.lowercase().contains(normalizedQuery)) {
                score += 0.3
            }
        }
        
        // Content match (lower weight)
        if (content.lowercase().contains(normalizedQuery)) {
            score += 0.1
        }
        
        // Engagement boost (helpful articles rank higher)
        score += getHelpfulnessRatio() * 0.1
        
        return score.coerceAtMost(1.0)
    }
}