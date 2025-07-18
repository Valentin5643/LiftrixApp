package com.example.liftrix.service

import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.VolumeDataPoint
import com.example.liftrix.domain.repository.DurationDataPoint
import com.example.liftrix.domain.repository.FrequencyDataPoint
import com.example.liftrix.domain.repository.ProgressSummary

/**
 * Service interface for progress data operations extracted from ViewModel.
 * 
 * Provides a dedicated service layer for handling progress data with proper error handling
 * and caching mechanisms. This interface abstracts the repository layer and provides
 * consistent data access patterns for the UI layer.
 * 
 * All operations return LiftrixResult<T> for consistent error handling throughout the application.
 */
interface ProgressDataService {
    
    /**
     * Retrieves workout volume data for the specified user and time period.
     * 
     * @param userId User identifier for data filtering
     * @param timeRange Time period for data retrieval
     * @return LiftrixResult containing list of volume data points or error
     */
    suspend fun getVolumeData(
        userId: String,
        timeRange: TimeRange
    ): LiftrixResult<List<VolumeDataPoint>>
    
    /**
     * Retrieves workout duration data for the specified user and time period.
     * 
     * @param userId User identifier for data filtering
     * @param timeRange Time period for data retrieval
     * @return LiftrixResult containing list of duration data points or error
     */
    suspend fun getDurationData(
        userId: String,
        timeRange: TimeRange
    ): LiftrixResult<List<DurationDataPoint>>
    
    /**
     * Retrieves workout frequency data for the specified user and time period.
     * 
     * @param userId User identifier for data filtering
     * @param timeRange Time period for data retrieval
     * @return LiftrixResult containing list of frequency data points or error
     */
    suspend fun getFrequencyData(
        userId: String,
        timeRange: TimeRange
    ): LiftrixResult<List<FrequencyDataPoint>>
    
    /**
     * Retrieves comprehensive progress summary for the specified user and time period.
     * 
     * @param userId User identifier for data filtering
     * @param timeRange Time period for data retrieval
     * @return LiftrixResult containing progress summary or error
     */
    suspend fun getProgressSummary(
        userId: String,
        timeRange: TimeRange
    ): LiftrixResult<ProgressSummary>
    
    /**
     * Refreshes all cached data for the specified user.
     * 
     * This method clears cached data and forces a fresh retrieval from the repository.
     * Useful for ensuring data consistency after workout completion or data sync.
     * 
     * @param userId User identifier for data refresh
     * @return LiftrixResult indicating success or failure of refresh operation
     */
    suspend fun refreshAllData(userId: String): LiftrixResult<Unit>
}