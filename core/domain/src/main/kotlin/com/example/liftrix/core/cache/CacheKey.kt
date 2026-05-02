package com.example.liftrix.core.cache

import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.TimeRange
import kotlinx.datetime.LocalDate

/**
 * Structured cache key implementation for type-safe cache operations.
 * 
 * This sealed class hierarchy provides strongly-typed cache keys for different
 * data types and operations, ensuring cache key uniqueness and preventing
 * cache key collisions across different data domains.
 * 
 * Key Features:
 * - Type-safe cache key generation
 * - Hierarchical key structure for easy invalidation
 * - Consistent toString() implementation for debugging
 * - Immutable data structures for thread safety
 * - Clear separation between different data types
 * 
 * Usage:
 * ```
 * // Progress data keys
 * val volumeKey = CacheKey.ProgressData.Volume(userId = "user123", timeRange = timeRange)
 * val summaryKey = CacheKey.ProgressData.Summary(userId = "user123", timeRange = timeRange)
 * 
 * // Analytics widget keys
 * val widgetKey = CacheKey.AnalyticsWidget.Data(userId = "user123", widget = AnalyticsWidget.TotalVolume)
 * val prefsKey = CacheKey.AnalyticsWidget.Preferences(userId = "user123")
 * 
 * // Custom operation keys
 * val customKey = CacheKey.Operation(operation = "calculateStats", userId = "user123", parameters = mapOf("period" to "weekly"))
 * ```
 */
sealed class CacheKey {
    
    /**
     * Abstract property for consistent string representation across all key types.
     */
    abstract val keyString: String
    
    /**
     * Cache keys for progress data operations.
     */
    sealed class ProgressData : CacheKey() {
        
        /**
         * Cache key for volume data by user and time range.
         */
        data class Volume(
            val userId: String,
            val timeRange: TimeRange
        ) : ProgressData() {
            override val keyString: String = "progress:volume:$userId:${timeRange.toKeyString()}"
        }
        
        /**
         * Cache key for duration data by user and time range.
         */
        data class Duration(
            val userId: String,
            val timeRange: TimeRange
        ) : ProgressData() {
            override val keyString: String = "progress:duration:$userId:${timeRange.toKeyString()}"
        }
        
        /**
         * Cache key for frequency data by user and time range.
         */
        data class Frequency(
            val userId: String,
            val timeRange: TimeRange
        ) : ProgressData() {
            override val keyString: String = "progress:frequency:$userId:${timeRange.toKeyString()}"
        }
        
        /**
         * Cache key for progress summary by user and time range.
         */
        data class Summary(
            val userId: String,
            val timeRange: TimeRange
        ) : ProgressData() {
            override val keyString: String = "progress:summary:$userId:${timeRange.toKeyString()}"
        }
    }
    
    /**
     * Cache keys for analytics widget operations.
     */
    sealed class AnalyticsWidget : CacheKey() {
        
        /**
         * Cache key for widget data by user and widget type.
         */
        data class Data(
            val userId: String,
            val widget: com.example.liftrix.domain.model.analytics.AnalyticsWidget
        ) : AnalyticsWidget() {
            override val keyString: String = "analytics:widget:$userId:${widget.id}"
        }
        
        /**
         * Cache key for widget preferences by user.
         */
        data class Preferences(
            val userId: String
        ) : AnalyticsWidget() {
            override val keyString: String = "analytics:preferences:$userId"
        }
        
        /**
         * Cache key for widget metadata by widget type.
         */
        data class Metadata(
            val widget: com.example.liftrix.domain.model.analytics.AnalyticsWidget
        ) : AnalyticsWidget() {
            override val keyString: String = "analytics:metadata:${widget.id}"
        }
    }
    
    /**
     * Cache keys for calorie service operations.
     */
    sealed class CalorieData : CacheKey() {
        
        /**
         * Cache key for calorie summary by user.
         */
        data class Summary(
            val userId: String
        ) : CalorieData() {
            override val keyString: String = "calorie:summary:$userId"
        }
        
        /**
         * Cache key for daily calorie data by user and time range.
         */
        data class Daily(
            val userId: String,
            val timeRange: TimeRange
        ) : CalorieData() {
            override val keyString: String = "calorie:daily:$userId:${timeRange.toKeyString()}"
        }
        
        /**
         * Cache key for weekly calorie trend by user.
         */
        data class WeeklyTrend(
            val userId: String
        ) : CalorieData() {
            override val keyString: String = "calorie:weekly_trend:$userId"
        }
        
        /**
         * Cache key for workout calorie calculation by workout ID.
         */
        data class WorkoutCalories(
            val workoutId: String
        ) : CalorieData() {
            override val keyString: String = "calorie:workout:$workoutId"
        }
    }
    
    /**
     * Cache keys for user preferences operations.
     */
    sealed class UserPreferences : CacheKey() {
        
        /**
         * Cache key for user preferences by user ID.
         */
        data class Data(
            val userId: String
        ) : UserPreferences() {
            override val keyString: String = "preferences:user:$userId"
        }
        
        /**
         * Cache key for layout mode preferences by user ID.
         */
        data class LayoutMode(
            val userId: String
        ) : UserPreferences() {
            override val keyString: String = "preferences:layout:$userId"
        }
    }
    
    /**
     * Generic cache key for custom operations with parameters.
     */
    data class Operation(
        val operation: String,
        val userId: String? = null,
        val parameters: Map<String, String> = emptyMap()
    ) : CacheKey() {
        override val keyString: String = buildString {
            append("operation:$operation")
            userId?.let { append(":user:$it") }
            if (parameters.isNotEmpty()) {
                append(":params:")
                append(parameters.entries.joinToString(",") { "${it.key}=${it.value}" })
            }
        }
    }
    
    /**
     * Cache key for computed values that depend on multiple data sources.
     */
    data class Computed(
        val computationType: String,
        val userId: String,
        val dependencies: List<String> = emptyList()
    ) : CacheKey() {
        override val keyString: String = buildString {
            append("computed:$computationType:$userId")
            if (dependencies.isNotEmpty()) {
                append(":deps:")
                append(dependencies.sorted().joinToString(","))
            }
        }
    }
    
    /**
     * Returns string representation of the cache key.
     */
    override fun toString(): String = keyString
    
    /**
     * Hash code based on keyString for proper Map usage.
     */
    override fun hashCode(): Int = keyString.hashCode()
    
    /**
     * Equality check based on keyString.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CacheKey) return false
        return keyString == other.keyString
    }
}

/**
 * Extension function to convert TimeRange to a cache key string.
 */
private fun TimeRange.toKeyString(): String {
    return "${startDate.time}:${endDate.time}"
}

/**
 * Utility functions for cache key operations.
 */
object CacheKeyUtils {
    
    /**
     * Creates a progress data volume key.
     */
    fun createVolumeKey(userId: String, timeRange: TimeRange): CacheKey.ProgressData.Volume {
        return CacheKey.ProgressData.Volume(userId, timeRange)
    }
    
    /**
     * Creates a progress data duration key.
     */
    fun createDurationKey(userId: String, timeRange: TimeRange): CacheKey.ProgressData.Duration {
        return CacheKey.ProgressData.Duration(userId, timeRange)
    }
    
    /**
     * Creates a progress data frequency key.
     */
    fun createFrequencyKey(userId: String, timeRange: TimeRange): CacheKey.ProgressData.Frequency {
        return CacheKey.ProgressData.Frequency(userId, timeRange)
    }
    
    /**
     * Creates a progress summary key.
     */
    fun createSummaryKey(userId: String, timeRange: TimeRange): CacheKey.ProgressData.Summary {
        return CacheKey.ProgressData.Summary(userId, timeRange)
    }
    
    /**
     * Creates an analytics widget data key.
     */
    fun createWidgetKey(userId: String, widget: com.example.liftrix.domain.model.analytics.AnalyticsWidget): CacheKey.AnalyticsWidget.Data {
        return CacheKey.AnalyticsWidget.Data(userId, widget)
    }
    
    /**
     * Creates a widget preferences key.
     */
    fun createPreferencesKey(userId: String): CacheKey.AnalyticsWidget.Preferences {
        return CacheKey.AnalyticsWidget.Preferences(userId)
    }
    
    /**
     * Checks if a cache key belongs to a specific user.
     */
    fun belongsToUser(key: CacheKey, userId: String): Boolean {
        return key.keyString.contains(":$userId:")
    }
    
    /**
     * Extracts user ID from cache key if present.
     */
    fun extractUserId(key: CacheKey): String? {
        val keyString = key.keyString
        val regex = Regex(":([^:]+):")
        val matches = regex.findAll(keyString)
        
        // Look for user ID pattern in key string
        for (match in matches) {
            val potential = match.groupValues[1]
            if (potential.startsWith("user") || potential.length == 36) { // UUID length
                return potential
            }
        }
        
        return null
    }
}