package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.User

/**
 * Service interface for analytics and crash reporting
 * Centralizes all Firebase Analytics and Crashlytics operations
 */
interface AnalyticsService {
    
    /**
     * Sets user-level custom properties for analytics and crash reporting
     */
    suspend fun setUserProperties(user: User): Result<Unit>
    
    /**
     * Logs workout start event
     */
    suspend fun logWorkoutStart(
        userId: String,
        workoutId: String,
        workoutName: String
    ): Result<Unit>
    
    /**
     * Logs workout completion with detailed metadata
     */
    suspend fun logWorkoutComplete(
        userId: String,
        workoutId: String,
        workoutName: String,
        metrics: Any,
        durationMinutes: Long?
    ): Result<Unit>
    
    /**
     * Logs unified workout creation event
     */
    suspend fun logWorkoutCreationEvent(
        userId: String,
        workoutId: String,
        workoutName: String,
        workoutType: String,
        exerciseCount: Int
    ): Result<Unit>
    
    /**
     * Logs exercise selection event with selection method
     */
    suspend fun logExerciseSelectionEvent(
        userId: String,
        exerciseId: String,
        exerciseName: String,
        selectionMethod: String
    ): Result<Unit>
    
    /**
     * Logs personal record achievement
     */
    suspend fun logPersonalRecord(
        userId: String,
        exerciseName: String,
        recordType: String,
        newValue: Double,
        previousValue: Double?
    ): Result<Unit>
    
    /**
     * Logs AI summary view event
     */
    suspend fun logAiSummaryViewed(
        userId: String,
        workoutId: String,
        summaryType: String
    ): Result<Unit>
    
    /**
     * Logs spotter addition event
     */
    suspend fun logSpotterAdded(
        userId: String,
        spotterUserId: String,
        connectionType: String
    ): Result<Unit>
    
    /**
     * Logs friend request sent event
     */
    suspend fun logFriendRequestSent(
        userId: String,
        targetUserId: String
    ): Result<Unit>
    
    /**
     * Logs friend request response event (accept/decline)
     */
    suspend fun logFriendRequestResponse(
        userId: String,
        targetUserId: String,
        accepted: Boolean
    ): Result<Unit>
    
    /**
     * Logs workout sharing event
     */
    suspend fun logWorkoutShared(
        userId: String,
        workoutId: String,
        workoutName: String,
        shareMethod: String
    ): Result<Unit>
    
    /**
     * Logs social workout viewed event
     */
    suspend fun logSocialWorkoutViewed(
        userId: String,
        workoutId: String,
        friendUserId: String,
        workoutName: String
    ): Result<Unit>
    
    /**
     * Logs social feed interaction events
     */
    suspend fun logSocialFeedEvent(
        userId: String,
        eventType: String,
        additionalData: Map<String, Any> = emptyMap()
    ): Result<Unit>
    
    /**
     * Logs general analytics event with parameters
     */
    suspend fun logEvent(
        eventName: String,
        parameters: Map<String, Any>
    ): Result<Unit>
    
    /**
     * Logs UX workflow start event for cognitive load and completion rate tracking
     */
    suspend fun logUxWorkflowStart(
        workflowId: String,
        workflowType: String,
        userId: String
    ): Result<Unit>
    
    /**
     * Logs UX workflow interaction for metrics tracking
     */
    suspend fun logUxWorkflowInteraction(
        workflowId: String,
        interactionType: String,
        interactionCount: Int
    ): Result<Unit>
    
    /**
     * Logs UX workflow completion with metrics for PRD success validation
     */
    suspend fun logUxWorkflowCompletion(
        workflowId: String,
        completionTimeMs: Long,
        totalInteractions: Int,
        successful: Boolean,
        efficiencyScore: Double,
        cognitiveLoadScore: Double
    ): Result<Unit>
    
    /**
     * Logs task completion rate metrics for PRD 30% improvement tracking
     */
    suspend fun logTaskCompletionMetrics(
        taskId: String,
        taskType: String,
        completionStatus: String,
        completionTimeMs: Long,
        errorCount: Int,
        retryCount: Int
    ): Result<Unit>
    
    /**
     * Records a non-fatal exception for crash reporting
     */
    suspend fun recordException(
        throwable: Throwable,
        additionalData: Map<String, String> = emptyMap()
    ): Result<Unit>
    
    /**
     * Sets custom key for crash reporting
     */
    suspend fun setCustomKey(key: String, value: String): Result<Unit>
    
    /**
     * Tracks feed load time for performance monitoring
     */
    suspend fun trackFeedLoadTime(duration: Long): Result<Unit>
    
    /**
     * Tracks user discovery engagement actions
     */
    suspend fun trackUserDiscoveryEngagement(
        action: String,
        additionalData: Map<String, Any> = emptyMap()
    ): Result<Unit>
    
    /**
     * Tracks feed scroll depth for engagement analysis
     */
    suspend fun trackFeedScrollDepth(itemCount: Int): Result<Unit>
    
    /**
     * Clears user properties (on logout)
     */
    suspend fun clearUserProperties(): Result<Unit>
} 
