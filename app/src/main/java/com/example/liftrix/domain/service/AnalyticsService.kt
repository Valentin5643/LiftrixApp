package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.WorkoutMetrics

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
        metrics: WorkoutMetrics,
        durationMinutes: Long?
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
     * Logs general analytics event with parameters
     */
    suspend fun logEvent(
        eventName: String,
        parameters: Map<String, Any>
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
     * Clears user properties (on logout)
     */
    suspend fun clearUserProperties(): Result<Unit>
} 