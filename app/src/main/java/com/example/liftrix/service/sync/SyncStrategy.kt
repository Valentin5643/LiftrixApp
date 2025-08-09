package com.example.liftrix.service.sync

import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.WidgetComplexity
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

/**
 * Sealed class defining different synchronization strategies based on widget complexity
 * and user activity patterns.
 * 
 * This strategy system provides:
 * - Widget complexity-based sync intervals
 * - User activity pattern optimization
 * - Network condition awareness
 * - Battery usage optimization
 * - Real-time vs batch sync coordination
 * 
 * Strategy Types:
 * - RealTimeStrategy: Immediate sync for critical data (<1s)
 * - SmartPollingStrategy: Adaptive polling (30s-5min intervals)
 * - BatchSyncStrategy: Efficient batch operations for complex widgets
 * - OfflineSyncStrategy: Queued sync for offline recovery
 */
sealed class SyncStrategy {
    
    constructor()
    
    /**
     * Real-time synchronization for critical data like workout sessions and PRs
     */
    object RealTimeStrategy : SyncStrategy() {
        val syncInterval = 0L // Immediate sync
        val priority = Priority.CRITICAL
        val networkRequirement = NetworkRequirement.CONNECTED
        
        override suspend fun shouldSync(
            widget: AnalyticsWidget, 
            lastSyncTime: Long?,
            userActivity: UserActivity
        ): Boolean {
            // Always sync real-time data immediately
            return true
        }
        
        override fun getRetryPolicy(): RetryPolicy {
            return RetryPolicy.Aggressive
        }
    }
    
    /**
     * Smart polling strategy with adaptive intervals based on widget complexity
     */
    data class SmartPollingStrategy(
        val baseInterval: Long,
        val complexityMultiplier: Float = 1.0f,
        val activityMultiplier: Float = 1.0f
    ) : SyncStrategy() {
        
        override suspend fun shouldSync(
            widget: AnalyticsWidget,
            lastSyncTime: Long?,
            userActivity: UserActivity
        ): Boolean {
            if (lastSyncTime == null) return true
            
            val currentTime = System.currentTimeMillis()
            val effectiveInterval = calculateEffectiveInterval(widget, userActivity)
            
            return (currentTime - lastSyncTime) >= effectiveInterval
        }
        
        override fun getRetryPolicy(): RetryPolicy {
            return when {
                baseInterval < 60_000L -> RetryPolicy.Moderate // < 1 minute
                baseInterval < 300_000L -> RetryPolicy.Conservative // < 5 minutes
                else -> RetryPolicy.Minimal
            }
        }
        
        private fun calculateEffectiveInterval(
            widget: AnalyticsWidget,
            userActivity: UserActivity
        ): Long {
            val complexityFactor = when (widget.complexity) {
                WidgetComplexity.SIMPLE -> 1.0f
                WidgetComplexity.MODERATE -> 2.0f
                WidgetComplexity.COMPLEX -> 4.0f
            }
            
            val activityFactor = when (userActivity) {
                UserActivity.ACTIVE -> 0.5f // Sync more frequently when user is active
                UserActivity.MODERATE -> 1.0f
                UserActivity.IDLE -> 2.0f // Sync less frequently when idle
                UserActivity.BACKGROUND -> 4.0f // Minimal sync in background
            }
            
            return (baseInterval * complexityFactor * activityFactor).toLong()
        }
        
        companion object {
            // Predefined strategies for different widget types
            fun forSimpleWidgets() = SmartPollingStrategy(30_000L) // 30 seconds
            fun forModerateWidgets() = SmartPollingStrategy(120_000L) // 2 minutes
            fun forComplexWidgets() = SmartPollingStrategy(300_000L) // 5 minutes
        }
    }
    
    /**
     * Batch synchronization strategy for efficient processing of multiple widgets
     */
    data class BatchSyncStrategy(
        val batchSize: Int = 5,
        val batchInterval: Long = 900_000L, // 15 minutes
        val priority: Priority = Priority.NORMAL
    ) : SyncStrategy() {
        
        override suspend fun shouldSync(
            widget: AnalyticsWidget,
            lastSyncTime: Long?,
            userActivity: UserActivity
        ): Boolean {
            // Batch sync decision is made at the batch level, not individual widgets
            return lastSyncTime == null || 
                   (System.currentTimeMillis() - lastSyncTime) >= batchInterval
        }
        
        override fun getRetryPolicy(): RetryPolicy {
            return RetryPolicy.Conservative
        }
    }
    
    /**
     * Offline synchronization strategy for queued operations
     */
    object OfflineSyncStrategy : SyncStrategy() {
        override suspend fun shouldSync(
            widget: AnalyticsWidget,
            lastSyncTime: Long?,
            userActivity: UserActivity
        ): Boolean {
            // Offline sync queues all operations for later execution
            return true
        }
        
        override fun getRetryPolicy(): RetryPolicy {
            return RetryPolicy.Persistent
        }
    }
    
    /**
     * Abstract methods that each strategy must implement
     */
    abstract suspend fun shouldSync(
        widget: AnalyticsWidget,
        lastSyncTime: Long?,
        userActivity: UserActivity
    ): Boolean
    
    abstract fun getRetryPolicy(): RetryPolicy
    
    /**
     * Handle workout session updates from Firestore listeners
     */
    suspend fun handleWorkoutSessionUpdate(userId: String, sessionData: Map<String, Any>) {
        try {
            Timber.d("SyncStrategy: Processing workout session update for user: $userId")
            
            // Extract session information
            val sessionId = sessionData["id"] as? String
            val status = sessionData["status"] as? String
            val lastModified = sessionData["lastModified"] as? Long
            
            if (sessionId == null || status == null) {
                Timber.w("SyncStrategy: Invalid session data - missing id or status")
                return
            }
            
            // Trigger appropriate sync based on session status
            when (status) {
                "ACTIVE", "PAUSED" -> {
                    // Real-time sync for active sessions
                    triggerRealTimeSync(userId, setOf(
                        AnalyticsWidget.TotalVolume,
                        AnalyticsWidget.WorkoutFrequency,
                        AnalyticsWidget.AverageDuration
                    ))
                }
                "COMPLETED" -> {
                    // Comprehensive sync for completed sessions
                    triggerComprehensiveSync(userId)
                }
            }
            
            Timber.d("SyncStrategy: Workout session update processed successfully")
            
        } catch (e: Exception) {
            Timber.e(e, "SyncStrategy: Error processing workout session update")
        }
    }
    
    /**
     * Handle personal record updates from Firestore listeners
     */
    suspend fun handlePersonalRecordUpdate(userId: String, prData: Map<String, Any>) {
        try {
            Timber.d("SyncStrategy: Processing personal record update for user: $userId")
            
            // Extract PR information
            val exerciseId = prData["exerciseId"] as? String
            val recordType = prData["recordType"] as? String
            val achievedAt = prData["achievedAt"] as? Long
            
            if (exerciseId == null || recordType == null) {
                Timber.w("SyncStrategy: Invalid PR data - missing exerciseId or recordType")
                return
            }
            
            // Trigger analytics recalculation for strength-related widgets
            triggerRealTimeSync(userId, setOf(
                AnalyticsWidget.StrengthProgress,
                AnalyticsWidget.OneRMProgression,
                AnalyticsWidget.MonthlySummary
            ))
            
            Timber.d("SyncStrategy: Personal record update processed successfully")
            
        } catch (e: Exception) {
            Timber.e(e, "SyncStrategy: Error processing personal record update")
        }
    }
    
    /**
     * Triggers real-time sync for specific widgets
     */
    private suspend fun triggerRealTimeSync(userId: String, widgets: Set<AnalyticsWidget>) {
        Timber.d("SyncStrategy: Triggering real-time sync for ${widgets.size} widgets")
        
        // Implementation would trigger immediate sync through the sync manager
        // This is a placeholder for the actual sync trigger mechanism
        widgets.forEach { widget ->
            Timber.d("SyncStrategy: Queuing real-time sync for widget: ${widget.id}")
        }
    }
    
    /**
     * Triggers comprehensive sync for all user widgets
     */
    private suspend fun triggerComprehensiveSync(userId: String) {
        Timber.d("SyncStrategy: Triggering comprehensive sync for user: $userId")
        
        // Implementation would trigger full sync through the sync manager
        // This would sync all enabled widgets with appropriate priorities
    }
    
    /**
     * Gets the appropriate sync strategy for a widget
     */
    companion object {
        fun getStrategyForWidget(widget: AnalyticsWidget, userActivity: UserActivity): SyncStrategy {
            return when {
                // Real-time widgets (critical data)
                widget in setOf(
                    AnalyticsWidget.TotalVolume,
                    AnalyticsWidget.WorkoutFrequency,
                    AnalyticsWidget.AverageDuration
                ) -> RealTimeStrategy
                
                // Simple widgets with frequent updates
                widget.complexity == WidgetComplexity.SIMPLE -> 
                    SmartPollingStrategy.forSimpleWidgets()
                
                // Moderate complexity widgets
                widget.complexity == WidgetComplexity.MODERATE -> 
                    SmartPollingStrategy.forModerateWidgets()
                
                // Complex widgets with expensive calculations
                widget.complexity == WidgetComplexity.COMPLEX -> 
                    SmartPollingStrategy.forComplexWidgets()
                
                else -> SmartPollingStrategy.forModerateWidgets()
            }
        }
        
        fun getBatchStrategy(userActivity: UserActivity): BatchSyncStrategy {
            return when (userActivity) {
                UserActivity.ACTIVE -> BatchSyncStrategy(batchSize = 3, batchInterval = 600_000L) // 10 min
                UserActivity.MODERATE -> BatchSyncStrategy(batchSize = 5, batchInterval = 900_000L) // 15 min
                UserActivity.IDLE -> BatchSyncStrategy(batchSize = 10, batchInterval = 1_800_000L) // 30 min
                UserActivity.BACKGROUND -> BatchSyncStrategy(batchSize = 20, batchInterval = 3_600_000L) // 1 hour
            }
        }
    }
}

/**
 * User activity levels for adaptive sync behavior
 */
enum class UserActivity {
    ACTIVE,     // User actively using the app
    MODERATE,   // User occasionally interacting
    IDLE,       // User inactive but app in foreground
    BACKGROUND  // App in background
}

/**
 * Sync priority levels
 */
enum class Priority {
    CRITICAL,   // Must sync immediately
    HIGH,       // Sync within 30 seconds
    NORMAL,     // Sync within 5 minutes
    LOW         // Sync within 30 minutes
}

/**
 * Network requirements for sync operations
 */
enum class NetworkRequirement {
    CONNECTED,      // Any network connection
    WIFI_ONLY,      // WiFi connection required
    UNRESTRICTED    // Unrestricted network access
}

/**
 * Retry policies for failed sync operations
 */
enum class RetryPolicy {
    Aggressive,     // Retry immediately, up to 10 times
    Moderate,       // Retry with 2s delay, up to 5 times  
    Conservative,   // Retry with 5s delay, up to 3 times
    Minimal,        // Retry with 30s delay, up to 2 times
    Persistent      // Keep retrying with exponential backoff
}