package com.example.liftrix.domain.usecase.progress

import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.VolumeDataPoint
import com.example.liftrix.domain.repository.DurationDataPoint
import com.example.liftrix.domain.repository.FrequencyDataPoint
import com.example.liftrix.domain.repository.ProgressSummary
import com.example.liftrix.service.ProgressDataService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for retrieving progress data including volume, duration, and frequency metrics.
 * 
 * This use case provides a unified interface for accessing progress data through the
 * ProgressDataService abstraction layer. It handles comprehensive progress data retrieval
 * with proper error handling and context switching for background operations.
 * 
 * Key Features:
 * - Volume data retrieval for strength progress tracking
 * - Duration data for workout time analysis
 * - Frequency data for workout consistency tracking
 * - Progress summary with comprehensive metrics
 * - Proper error handling with LiftrixError context
 * - Background thread execution for performance
 * 
 * Error Handling:
 * All operations return LiftrixResult<T> for consistent error handling with proper
 * context information and recovery mechanisms.
 * 
 * Usage:
 * ```
 * val progressData = getProgressDataUseCase.getVolumeData(userId, TimeRange.LAST_30_DAYS)
 * progressData.fold(
 *     onSuccess = { volumeData -> updateUI(volumeData) },
 *     onFailure = { error -> handleError(error) }
 * )
 * ```
 */
@Singleton
class GetProgressDataUseCase @Inject constructor(
    private val progressDataService: ProgressDataService
) {
    
    /**
     * Retrieves workout volume data for the specified user and time period.
     * 
     * Volume data includes weight × reps calculations, total volume per workout,
     * and trend analysis over the specified time range.
     * 
     * @param userId User identifier for data filtering
     * @param timeRange Time period for data retrieval (LAST_7_DAYS, LAST_30_DAYS, etc.)
     * @return LiftrixResult containing list of volume data points or error
     */
    suspend fun getVolumeData(
        userId: String,
        timeRange: TimeRange
    ): LiftrixResult<List<VolumeDataPoint>> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DataRetrievalError(
                    errorMessage = "Failed to retrieve volume data for user $userId: ${throwable.message}",
                    operation = "getVolumeData",
                    retryable = true
                )
            }
        ) {
            Timber.d("Retrieving volume data for user: $userId, timeRange: $timeRange")
            
            val result = progressDataService.getVolumeData(userId, timeRange)
            result.getOrThrow()
        }
    }
    
    /**
     * Retrieves workout duration data for the specified user and time period.
     * 
     * Duration data includes workout lengths, rest periods, and time-based analytics
     * for understanding workout efficiency and consistency.
     * 
     * @param userId User identifier for data filtering
     * @param timeRange Time period for data retrieval
     * @return LiftrixResult containing list of duration data points or error
     */
    suspend fun getDurationData(
        userId: String,
        timeRange: TimeRange
    ): LiftrixResult<List<DurationDataPoint>> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DataRetrievalError(
                    errorMessage = "Failed to retrieve duration data for user $userId: ${throwable.message}",
                    operation = "getDurationData",
                    retryable = true
                )
            }
        ) {
            Timber.d("Retrieving duration data for user: $userId, timeRange: $timeRange")
            
            val result = progressDataService.getDurationData(userId, timeRange)
            result.getOrThrow()
        }
    }
    
    /**
     * Retrieves workout frequency data for the specified user and time period.
     * 
     * Frequency data includes workout count per time period, consistency streaks,
     * and patterns for understanding workout adherence.
     * 
     * @param userId User identifier for data filtering
     * @param timeRange Time period for data retrieval
     * @return LiftrixResult containing list of frequency data points or error
     */
    suspend fun getFrequencyData(
        userId: String,
        timeRange: TimeRange
    ): LiftrixResult<List<FrequencyDataPoint>> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DataRetrievalError(
                    errorMessage = "Failed to retrieve frequency data for user $userId: ${throwable.message}",
                    operation = "getFrequencyData",
                    retryable = true
                )
            }
        ) {
            Timber.d("Retrieving frequency data for user: $userId, timeRange: $timeRange")
            
            val result = progressDataService.getFrequencyData(userId, timeRange)
            result.getOrThrow()
        }
    }
    
    /**
     * Retrieves comprehensive progress summary for the specified user and time period.
     * 
     * Progress summary includes aggregated metrics across all progress dimensions:
     * - Total volume and volume trends
     * - Average workout duration and consistency
     * - Workout frequency and adherence patterns
     * - Personal records and achievements
     * 
     * @param userId User identifier for data filtering
     * @param timeRange Time period for data retrieval
     * @return LiftrixResult containing progress summary or error
     */
    suspend fun getProgressSummary(
        userId: String,
        timeRange: TimeRange
    ): LiftrixResult<ProgressSummary> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DataRetrievalError(
                    errorMessage = "Failed to retrieve progress summary for user $userId: ${throwable.message}",
                    operation = "getProgressSummary",
                    retryable = true
                )
            }
        ) {
            Timber.d("Retrieving progress summary for user: $userId, timeRange: $timeRange")
            
            val result = progressDataService.getProgressSummary(userId, timeRange)
            result.getOrThrow()
        }
    }
    
    /**
     * Refreshes all cached progress data for the specified user.
     * 
     * This method forces a refresh of all cached progress data including volume,
     * duration, frequency, and summary metrics. Useful for ensuring data consistency
     * after workout completion or when synchronizing with remote data sources.
     * 
     * @param userId User identifier for data refresh
     * @return LiftrixResult indicating success or failure of refresh operation
     */
    suspend fun refreshAllData(userId: String): LiftrixResult<Unit> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DataRetrievalError(
                    errorMessage = "Failed to refresh progress data for user $userId: ${throwable.message}",
                    operation = "refreshAllData",
                    retryable = true
                )
            }
        ) {
            Timber.d("Refreshing all progress data for user: $userId")
            
            val result = progressDataService.refreshAllData(userId)
            result.getOrThrow()
        }
    }
}