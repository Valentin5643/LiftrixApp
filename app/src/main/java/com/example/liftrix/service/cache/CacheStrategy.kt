package com.example.liftrix.service.cache

import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.WidgetComplexity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Sealed class hierarchy for different cache invalidation and TTL strategies.
 * 
 * This implementation provides widget complexity-based caching strategies aligned with
 * performance requirements and user activity patterns:
 * 
 * - SIMPLE widgets: Fast calculations, frequent updates (15-30min TTL)
 * - MODERATE widgets: Medium calculations, balanced updates (60-120min TTL)  
 * - COMPLEX widgets: Heavy calculations, infrequent updates (240-720min TTL)
 * 
 * Strategy Selection Factors:
 * - Computational complexity of widget data
 * - Data freshness requirements
 * - User interaction patterns
 * - Network and battery optimization
 * - 60fps rendering performance targets
 * 
 * Usage:
 * ```
 * val strategy = CacheStrategy.ComplexityBased()
 * val ttl = strategy.getTtlForWidget(AnalyticsWidget.TotalVolume)
 * val shouldRefresh = strategy.shouldRefresh(widget, lastUpdate, userActivity)
 * ```
 */
sealed class CacheStrategy {
    
    /**
     * Determines TTL (Time-To-Live) for a specific widget based on its complexity.
     * 
     * @param widget Analytics widget to determine TTL for
     * @return Duration representing cache TTL
     */
    abstract fun getTtlForWidget(widget: AnalyticsWidget): Duration
    
    /**
     * Determines if a widget should be refreshed based on strategy rules.
     * 
     * @param widget Analytics widget to check
     * @param lastUpdate Time of last data update
     * @param userActivity Recent user activity level (0.0 to 1.0)
     * @return true if widget should be refreshed, false otherwise
     */
    abstract fun shouldRefresh(widget: AnalyticsWidget, lastUpdate: Long, userActivity: Double): Boolean
    
    /**
     * Returns the refresh priority for a widget (higher = more urgent).
     * 
     * @param widget Analytics widget to prioritize
     * @return Int priority value (0-10, where 10 is highest priority)
     */
    abstract fun getRefreshPriority(widget: AnalyticsWidget): Int
    
    /**
     * Complexity-based caching strategy optimized for performance and user experience.
     * 
     * This strategy aligns cache TTL with widget computational complexity:
     * - Reduces unnecessary recalculations for complex widgets
     * - Ensures fresh data for fast-changing simple widgets
     * - Balances performance with data freshness requirements
     * - Considers user activity patterns for intelligent refresh timing
     */
    class ComplexityBased : CacheStrategy() {
        
        companion object {
            // TTL ranges for different complexity levels (aligned with PRD requirements)
            
            // SIMPLE widgets: Metrics like calories, frequency (fast calculations)
            private val SIMPLE_TTL_MIN = 15.minutes
            private val SIMPLE_TTL_MAX = 30.minutes
            
            // MODERATE widgets: Progress tracking, streaks (medium calculations) 
            private val MODERATE_TTL_MIN = 60.minutes
            private val MODERATE_TTL_MAX = 120.minutes
            
            // COMPLEX widgets: Charts, analytics, trends (heavy calculations)
            private val COMPLEX_TTL_MIN = 240.minutes
            private val COMPLEX_TTL_MAX = 720.minutes
            
            // User activity thresholds for dynamic TTL adjustment
            private const val HIGH_ACTIVITY_THRESHOLD = 0.7
            private const val LOW_ACTIVITY_THRESHOLD = 0.3
        }
        
        override fun getTtlForWidget(widget: AnalyticsWidget): Duration {
            return when (widget.complexity) {
                WidgetComplexity.SIMPLE -> {
                    // Simple widgets get shorter TTL for fresh data
                    if (isRealTimeWidget(widget)) {
                        SIMPLE_TTL_MIN // 15 minutes for real-time data
                    } else {
                        SIMPLE_TTL_MAX // 30 minutes for static metrics
                    }
                }
                
                WidgetComplexity.MODERATE -> {
                    // Moderate widgets balance freshness and performance
                    if (isUserFacingWidget(widget)) {
                        MODERATE_TTL_MIN // 60 minutes for user-facing widgets
                    } else {
                        MODERATE_TTL_MAX // 120 minutes for background calculations
                    }
                }
                
                WidgetComplexity.COMPLEX -> {
                    // Complex widgets get longer TTL to reduce computational load
                    if (isVolatileWidget(widget)) {
                        COMPLEX_TTL_MIN // 240 minutes for trending data
                    } else {
                        COMPLEX_TTL_MAX // 720 minutes for historical analysis
                    }
                }
            }
        }
        
        override fun shouldRefresh(widget: AnalyticsWidget, lastUpdate: Long, userActivity: Double): Boolean {
            val currentTime = System.currentTimeMillis()
            val timeSinceUpdate = currentTime - lastUpdate
            val baseTtl = getTtlForWidget(widget)
            
            // Adjust TTL based on user activity
            val adjustedTtl = when {
                userActivity > HIGH_ACTIVITY_THRESHOLD -> {
                    // High activity: refresh more frequently
                    (baseTtl.inWholeMilliseconds * 0.7).toLong()
                }
                userActivity < LOW_ACTIVITY_THRESHOLD -> {
                    // Low activity: can wait longer for refresh
                    (baseTtl.inWholeMilliseconds * 1.3).toLong()
                }
                else -> {
                    // Normal activity: use base TTL
                    baseTtl.inWholeMilliseconds
                }
            }
            
            return timeSinceUpdate >= adjustedTtl
        }
        
        override fun getRefreshPriority(widget: AnalyticsWidget): Int {
            return when (widget.complexity) {
                WidgetComplexity.SIMPLE -> {
                    // Simple widgets have high priority for quick refresh
                    if (isRealTimeWidget(widget)) 10 else 8
                }
                WidgetComplexity.MODERATE -> {
                    // Moderate widgets have medium priority
                    if (isUserFacingWidget(widget)) 6 else 4
                }
                WidgetComplexity.COMPLEX -> {
                    // Complex widgets have lower priority due to computation cost
                    if (isVolatileWidget(widget)) 3 else 1
                }
            }
        }
        
        /**
         * Determines if a widget requires real-time data updates.
         */
        private fun isRealTimeWidget(widget: AnalyticsWidget): Boolean {
            return when (widget) {
                AnalyticsWidget.CaloriesBurned,
                AnalyticsWidget.DailyCalories,
                AnalyticsWidget.TotalVolume -> true
                else -> false
            }
        }
        
        /**
         * Determines if a widget is prominently displayed to users.
         */
        private fun isUserFacingWidget(widget: AnalyticsWidget): Boolean {
            return when (widget) {
                AnalyticsWidget.WorkoutFrequency,
                AnalyticsWidget.ConsistencyStreak,
                AnalyticsWidget.StrengthProgress,
                AnalyticsWidget.AverageDuration -> true
                else -> false
            }
        }
        
        /**
         * Determines if a widget's data changes frequently.
         */
        private fun isVolatileWidget(widget: AnalyticsWidget): Boolean {
            return when (widget) {
                AnalyticsWidget.VolumeTrends,
                AnalyticsWidget.WeeklyTrends,
                AnalyticsWidget.PerformanceAnalysis -> true
                else -> false
            }
        }
    }
    
    /**
     * Time-based caching strategy using fixed intervals.
     * 
     * This strategy provides predictable cache behavior with fixed TTL values:
     * - Consistent refresh intervals regardless of complexity
     * - Simplified cache invalidation patterns
     * - Useful for testing and debugging scenarios
     */
    class TimeBased(
        private val defaultTtl: Duration = 30.minutes
    ) : CacheStrategy() {
        
        override fun getTtlForWidget(widget: AnalyticsWidget): Duration {
            return defaultTtl
        }
        
        override fun shouldRefresh(widget: AnalyticsWidget, lastUpdate: Long, userActivity: Double): Boolean {
            val currentTime = System.currentTimeMillis()
            val timeSinceUpdate = currentTime - lastUpdate
            return timeSinceUpdate >= defaultTtl.inWholeMilliseconds
        }
        
        override fun getRefreshPriority(widget: AnalyticsWidget): Int {
            return 5 // Medium priority for all widgets
        }
    }
    
    /**
     * Adaptive caching strategy that learns from user behavior.
     * 
     * This strategy adjusts cache behavior based on:
     * - User interaction patterns with specific widgets
     * - Historical refresh requirements
     * - App usage patterns and session lengths
     * - Performance metrics and response times
     * 
     * Note: This is a placeholder for future ML-based optimization.
     */
    class Adaptive : CacheStrategy() {
        
        private val baseStrategy = ComplexityBased()
        
        override fun getTtlForWidget(widget: AnalyticsWidget): Duration {
            // For now, delegate to complexity-based strategy
            // Future: Use ML model to predict optimal TTL
            return baseStrategy.getTtlForWidget(widget)
        }
        
        override fun shouldRefresh(widget: AnalyticsWidget, lastUpdate: Long, userActivity: Double): Boolean {
            // For now, delegate to complexity-based strategy
            // Future: Use learned patterns for refresh decisions
            return baseStrategy.shouldRefresh(widget, lastUpdate, userActivity)
        }
        
        override fun getRefreshPriority(widget: AnalyticsWidget): Int {
            // For now, delegate to complexity-based strategy
            // Future: Use user interaction data for priority
            return baseStrategy.getRefreshPriority(widget)
        }
    }
}

/**
 * Implementation of cache strategy interface with dependency injection support.
 * 
 * This class provides the concrete cache strategy implementation used throughout
 * the application, defaulting to complexity-based strategy for optimal performance.
 */
@Singleton
class CacheStrategyImpl @Inject constructor() : CacheStrategy() {
    
    private val strategy = ComplexityBased()
    
    override fun getTtlForWidget(widget: AnalyticsWidget): Duration {
        return strategy.getTtlForWidget(widget)
    }
    
    override fun shouldRefresh(widget: AnalyticsWidget, lastUpdate: Long, userActivity: Double): Boolean {
        return strategy.shouldRefresh(widget, lastUpdate, userActivity)
    }
    
    override fun getRefreshPriority(widget: AnalyticsWidget): Int {
        return strategy.getRefreshPriority(widget)
    }
}