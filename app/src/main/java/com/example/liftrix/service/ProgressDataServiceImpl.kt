package com.example.liftrix.service

import com.example.liftrix.core.cache.EnhancedCacheManager
import com.example.liftrix.core.cache.getOrComputeTyped
import com.example.liftrix.core.cache.CacheKeyGenerator
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.analytics.VolumeCalendarData
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.ProgressStatsRepository
import com.example.liftrix.domain.usecase.analytics.GenerateVolumeCalendarUseCase
import com.example.liftrix.domain.repository.VolumeDataPoint
import com.example.liftrix.domain.repository.DurationDataPoint
import com.example.liftrix.domain.repository.FrequencyDataPoint
import com.example.liftrix.domain.repository.ProgressSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

/**
 * Implementation of ProgressDataService providing progress data operations.
 * 
 * This service layer implementation extracts progress data operations from ViewModels
 * and provides proper error handling, caching, and background processing using
 * IO dispatcher for database operations.
 * 
 * Enhanced Caching Strategy:
 * - Multi-tier caching with memory (L1) and disk (L2) layers
 * - Intelligent TTL based on data volatility (current vs historical data)
 * - Smart cache key generation with relationship-aware invalidation
 * - Performance targets: <100ms cached, <500ms fresh queries
 * - Cache hit rate target: 80%+ for repeated queries
 * 
 * @param progressStatsRepository Repository for progress statistics data
 * @param cacheManager Enhanced cache manager with multi-tier support
 * @param ioDispatcher IO dispatcher for background database operations
 */
@Singleton
class ProgressDataServiceImpl @Inject constructor(
    private val progressStatsRepository: ProgressStatsRepository,
    private val cacheManager: EnhancedCacheManager,
    private val generateVolumeCalendarUseCase: GenerateVolumeCalendarUseCase,
    @com.example.liftrix.di.IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ProgressDataService {
    
    companion object {
        private const val TAG = "ProgressDataService"
    }
    
    override suspend fun getVolumeData(
        userId: String,
        timeRange: TimeRange
    ): LiftrixResult<List<VolumeDataPoint>> = withContext(ioDispatcher) {
        liftrixCatching(
            errorMapper = { throwable ->
                Timber.e(throwable, "$TAG: Failed to get volume data for user: $userId")
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to retrieve volume data: ${throwable.message}",
                    operation = "getVolumeData",
                    analyticsContext = mapOf(
                        "userId" to userId,
                        "timeRange" to timeRange.toString(),
                        "error_type" to (throwable::class.simpleName ?: "Unknown")
                    )
                )
            }
        ) {
            Timber.d("$TAG: getVolumeData() starting for userId=$userId")
            
            // Generate intelligent cache key with TTL
            val (cacheKey, ttl) = CacheKeyGenerator.volumeKey(userId, timeRange)
            
            // Use enhanced cache manager with automatic fallback
            val data = cacheManager.getOrComputeTyped<List<VolumeDataPoint>>(cacheKey, ttl) {
                val (startDate, endDate) = timeRange.toLocalDateRange()
                
                Timber.d("🔍 VOLUME-SERVICE-DEBUG: TimeRange for volume data - startDate=$startDate, endDate=$endDate")
                Timber.d("🔍 VOLUME-SERVICE-DEBUG: TimeRange milliseconds - start=${timeRange.startDate.time}, end=${timeRange.endDate.time}")
                
                Timber.d("$TAG: Fetching volume data from repository: startDate=$startDate, endDate=$endDate")
                kotlinx.coroutines.withTimeout(8000) {
                    progressStatsRepository.getWorkoutVolumeData(userId, startDate, endDate).first()
                }
            }
            
            Timber.d("$TAG: getVolumeData() returning ${data.size} data points")
            data
        }
    }
    
    override suspend fun getDurationData(
        userId: String,
        timeRange: TimeRange
    ): LiftrixResult<List<DurationDataPoint>> = withContext(ioDispatcher) {
        liftrixCatching(
            errorMapper = { throwable ->
                Timber.e(throwable, "$TAG: Failed to get duration data for user: $userId")
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to retrieve duration data: ${throwable.message}",
                    operation = "getDurationData",
                    analyticsContext = mapOf(
                        "userId" to userId,
                        "timeRange" to timeRange.toString(),
                        "error_type" to (throwable::class.simpleName ?: "Unknown")
                    )
                )
            }
        ) {
            // Generate cache key for duration data (deprecated - filtered out)
            val (cacheKey, ttl) = CacheKeyGenerator.volumeKey(userId, timeRange).let { (key, ttl) ->
                // Convert to duration key pattern for backward compatibility
                val durationKey = com.example.liftrix.core.cache.CacheKey.Operation(
                    operation = "duration_data",
                    userId = userId,
                    parameters = mapOf(
                        "time_range" to "${timeRange.startDate.time}:${timeRange.endDate.time}",
                        "version" to "v1"
                    )
                )
                Pair(durationKey, ttl)
            }
            
            // Use enhanced cache manager
            val data = cacheManager.getOrComputeTyped<List<DurationDataPoint>>(cacheKey, ttl) {
                val (startDate, endDate) = timeRange.toLocalDateRange()
                
                kotlinx.coroutines.withTimeout(8000) {
                    progressStatsRepository.getWorkoutDurationData(userId, startDate, endDate).first()
                }
            }
            
            data
        }
    }
    
    override suspend fun getFrequencyData(
        userId: String,
        timeRange: TimeRange
    ): LiftrixResult<List<FrequencyDataPoint>> = withContext(ioDispatcher) {
        liftrixCatching(
            errorMapper = { throwable ->
                Timber.e(throwable, "$TAG: Failed to get frequency data for user: $userId")
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to retrieve frequency data: ${throwable.message}",
                    operation = "getFrequencyData",
                    analyticsContext = mapOf(
                        "userId" to userId,
                        "timeRange" to timeRange.toString(),
                        "error_type" to (throwable::class.simpleName ?: "Unknown")
                    )
                )
            }
        ) {
            // Generate frequency cache key
            val (cacheKey, ttl) = CacheKeyGenerator.workoutFrequencyKey(
                userId = userId,
                year = kotlinx.datetime.Instant.fromEpochMilliseconds(timeRange.startDate.time).toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).year.toString()
            )
            
            // Use enhanced cache manager
            val data = cacheManager.getOrComputeTyped<List<FrequencyDataPoint>>(cacheKey, ttl) {
                val (startDate, endDate) = timeRange.toLocalDateRange()
                
                kotlinx.coroutines.withTimeout(8000) {
                    progressStatsRepository.getWorkoutFrequencyData(userId, startDate, endDate).first()
                }
            }
            
            data
        }
    }
    
    override suspend fun getProgressSummary(
        userId: String,
        timeRange: TimeRange
    ): LiftrixResult<ProgressSummary> = withContext(ioDispatcher) {
        liftrixCatching(
            errorMapper = { throwable ->
                Timber.e(throwable, "$TAG: Failed to get progress summary for user: $userId")
                LiftrixError.DatabaseError(
                    errorMessage = "Database query failed: ${throwable.message ?: "Unknown database error"}",
                    operation = "getProgressSummary",
                    analyticsContext = mapOf(
                        "userId" to userId,
                        "timeRange" to timeRange.toString(),
                        "originalError" to (throwable.message ?: "Unknown"),
                        "errorType" to (throwable::class.simpleName ?: "Unknown"),
                        "stackTrace" to throwable.stackTraceToString().take(500)
                    )
                )
            }
        ) {
            // Generate dashboard summary cache key
            val cacheKey = com.example.liftrix.core.cache.CacheKey.ProgressData.Summary(userId, timeRange)
            val ttl = 30.minutes
            
            // Use enhanced cache manager
            val data = cacheManager.getOrComputeTyped<ProgressSummary>(cacheKey, ttl) {
                val (startDate, endDate) = timeRange.toLocalDateRange()
                
                Timber.d("🔍 PROGRESS-SERVICE-DEBUG: TimeRange for progress summary - startDate=$startDate, endDate=$endDate")
                Timber.d("🔍 PROGRESS-SERVICE-DEBUG: TimeRange milliseconds - start=${timeRange.startDate.time}, end=${timeRange.endDate.time}")
                
                // Convert the problematic workout timestamp for comparison
                val workoutTimestamp = 1758316901826L
                val workoutDate = kotlinx.datetime.Instant.fromEpochMilliseconds(workoutTimestamp)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date
                Timber.d("🔍 PROGRESS-SERVICE-DEBUG: Workout with 600kg volume created at timestamp=$workoutTimestamp, which is date=$workoutDate")
                Timber.d("🔍 PROGRESS-SERVICE-DEBUG: Is workout date in range? ${workoutDate >= startDate && workoutDate <= endDate}")
                
                Timber.d("$TAG: Fetching progress summary from repository")
                kotlinx.coroutines.withTimeout(15000) { // 15 second timeout
                    progressStatsRepository.getProgressSummary(userId, startDate, endDate).first()
                }
            }
            
            data
        }
    }
    
    override suspend fun getVolumeCalendarData(
        userId: String
    ): LiftrixResult<VolumeCalendarData> = withContext(ioDispatcher) {
        liftrixCatching(
            errorMapper = { throwable ->
                Timber.e(throwable, "$TAG: Failed to get volume calendar data for user: $userId")
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to retrieve volume calendar data: ${throwable.message}",
                    operation = "getVolumeCalendarData",
                    analyticsContext = mapOf(
                        "userId" to userId,
                        "error_type" to (throwable::class.simpleName ?: "Unknown")
                    )
                )
            }
        ) {
            Timber.d("$TAG: getVolumeCalendarData() starting for userId=$userId")
            
            // Use existing GenerateVolumeCalendarUseCase to get current month data
            val result = generateVolumeCalendarUseCase.generateCurrentMonth(userId)
            
            if (result.isSuccess) {
                val calendarData = result.getOrThrow()
                Timber.d("$TAG: Successfully retrieved volume calendar data with ${calendarData.dailyVolumes.size} days")
                
                // DEBUG: Log volume data details
                calendarData.dailyVolumes.forEach { (date, volume) ->
                    if (volume.kilograms > 0.0) {
                        Timber.d("$TAG: Volume data - $date: ${volume.kilograms}kg")
                    }
                }
                Timber.d("$TAG: Max volume: ${calendarData.maxVolume.kilograms}kg, Average: ${calendarData.averageVolume.kilograms}kg")
                
                calendarData
            } else {
                val error = result.exceptionOrNull() as? LiftrixError
                    ?: LiftrixError.UnknownError("Failed to generate volume calendar data")
                throw error
            }
        }
    }
    
    override suspend fun refreshAllData(userId: String): LiftrixResult<Unit> = withContext(ioDispatcher) {
        liftrixCatching(
            errorMapper = { throwable ->
                Timber.e(throwable, "$TAG: Failed to refresh data for user: $userId")
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to refresh progress data",
                    operation = "refreshAllData",
                    analyticsContext = mapOf(
                        "userId" to userId
                    )
                )
            }
        ) {
            // Force data refresh by clearing all cached data for this user
            Timber.d("$TAG: Refreshing all data for user: $userId - clearing cache")
            
            // Use pattern-based invalidation for all user data
            val userPattern = CacheKeyGenerator.userPattern(userId)
            cacheManager.invalidatePattern(userPattern)
            
            Timber.d("$TAG: Cache cleared for user: $userId using pattern: $userPattern")
            
            // Return unit to indicate successful refresh
            Unit
        }
    }
    
    /**
     * Converts java.util.Date to kotlinx.datetime.LocalDate for repository compatibility
     */
    private fun java.util.Date.toKotlinLocalDate(): kotlinx.datetime.LocalDate {
        return kotlinx.datetime.Instant.fromEpochMilliseconds(time)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
    }

    private fun TimeRange.toLocalDateRange(): Pair<LocalDate, LocalDate> {
        val startLocalDate = startDate.toKotlinLocalDate()
        val endLocalDate = endDate.toKotlinLocalDate()
        return if (startLocalDate <= endLocalDate) {
            startLocalDate to endLocalDate
        } else {
            endLocalDate to startLocalDate
        }
    }
}
