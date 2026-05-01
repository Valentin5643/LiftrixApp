package com.example.liftrix.domain.model.help

import java.time.Instant

/**
 * Domain model representing a help article
 * Contains information about help content including engagement metrics
 */
data class HelpArticle(
    val id: String,
    val category: String,
    val title: String,
    val content: String,
    val keywords: List<String> = emptyList(),
    val viewCount: Int = 0,
    val helpfulCount: Int = 0,
    val notHelpfulCount: Int = 0,
    val lastUpdated: Instant,
    val version: Int = 1,
    val isFeatured: Boolean = false,
    val sortOrder: Int = 0
) {
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
     * Checks if this article has high engagement
     * @param threshold Minimum view count threshold
     * @return True if article has high engagement
     */
    fun hasHighEngagement(threshold: Int = 50): Boolean {
        return viewCount >= threshold && getHelpfulnessRatio() > 0.7
    }
    
    /**
     * Gets a preview of the article content
     * @param maxLength Maximum length of the preview
     * @return Truncated content with ellipsis if needed
     */
    fun getContentPreview(maxLength: Int = 150): String {
        return if (content.length <= maxLength) {
            content
        } else {
            content.take(maxLength).trimEnd() + "..."
        }
    }
    
    companion object {
        /**
         * Creates a default empty article
         */
        fun empty(): HelpArticle = HelpArticle(
            id = "",
            category = "",
            title = "",
            content = "",
            keywords = emptyList(),
            lastUpdated = Instant.now()
        )
        
        /**
         * Categories for help articles
         */
        object Category {
            const val GETTING_STARTED = "Getting Started"
            const val WORKOUTS = "Workouts"
            const val EXERCISES = "Exercises"
            const val PROGRESS_TRACKING = "Progress Tracking"
            const val SOCIAL_FEATURES = "Social Features"
            const val SETTINGS = "Settings"
            const val TROUBLESHOOTING = "Troubleshooting"
            const val ACCOUNT = "Account"
            const val SYNC = "Sync & Backup"
            const val PREMIUM_FEATURES = "Premium Features"
        }
    }
}