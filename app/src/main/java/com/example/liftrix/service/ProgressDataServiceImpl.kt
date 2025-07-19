package com.example.liftrix.service

import com.example.liftrix.core.cache.CacheManager
import com.example.liftrix.core.cache.CacheKey
import com.example.liftrix.core.cache.CacheKeyUtils
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.ProgressStatsRepository
import com.example.liftrix.domain.repository.VolumeDataPoint
import com.example.liftrix.domain.repository.DurationDataPoint
import com.example.liftrix.domain.repository.FrequencyDataPoint
import com.example.liftrix.domain.repository.ProgressSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
 * Caching Strategy:
 * - Uses LRU cache with 15-minute TTL for data operations
 * - Cache keys are structured by userId and timeRange for efficient invalidation
 * - Cache-first approach with fallback to repository
 * - Automatic cache invalidation on data refresh operations
 * 
 * @param progressStatsRepository Repository for progress statistics data
 * @param cacheManager Cache manager for LRU caching with TTL
 * @param ioDispatcher IO dispatcher for background database operations
 */
@Singleton
class ProgressDataServiceImpl @Inject constructor(
    private val progressStatsRepository: ProgressStatsRepository,
    private val cacheManager: CacheManager,
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
                    errorMessage = "Failed to retrieve volume data",
                    operation = "getVolumeData",
                    analyticsContext = mapOf(
                        "userId" to userId,
                        "timeRange" to timeRange.toString()
                    )
                )
            }
        ) {
            val cacheKey = CacheKeyUtils.createVolumeKey(userId, timeRange)
            
            // Check cache first
            val cachedEntry = cacheManager.get<List<VolumeDataPoint>>(cacheKey)
            if (cachedEntry != null && cachedEntry.isValid()) {
                Timber.d("$TAG: Cache hit for volume data - user: $userId, timeRange: $timeRange")
                return@liftrixCatching cachedEntry.data
            }
            
            Timber.d("$TAG: Cache miss for volume data - fetching from repository")
            
            val startDate = kotlinx.datetime.LocalDate.fromEpochDays(
                (timeRange.startDate.time / (24 * 60 * 60 * 1000)).toInt()
            )
            val endDate = kotlinx.datetime.LocalDate.fromEpochDays(
                (timeRange.endDate.time / (24 * 60 * 60 * 1000)).toInt()
            )
            
            val data = kotlinx.coroutines.withTimeout(15000) { // 15 second timeout
                progressStatsRepository.getWorkoutVolumeData(userId, startDate, endDate).first()
            }
            
            // Cache the result with 15-minute TTL
            cacheManager.put(cacheKey, data, ttl = 15.minutes)
            
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
                    errorMessage = "Failed to retrieve duration data",
                    operation = "getDurationData",
                    analyticsContext = mapOf(
                        "userId" to userId,
                        "timeRange" to timeRange.toString()
                    )
                )
            }
        ) {
            val cacheKey = CacheKeyUtils.createDurationKey(userId, timeRange)
            
            // Check cache first
            val cachedEntry = cacheManager.get<List<DurationDataPoint>>(cacheKey)
            if (cachedEntry != null && cachedEntry.isValid()) {
                Timber.d("$TAG: Cache hit for duration data - user: $userId, timeRange: $timeRange")
                return@liftrixCatching cachedEntry.data
            }
            
            Timber.d("$TAG: Cache miss for duration data - fetching from repository")
            
            val startDate = kotlinx.datetime.LocalDate.fromEpochDays(
                (timeRange.startDate.time / (24 * 60 * 60 * 1000)).toInt()
            )
            val endDate = kotlinx.datetime.LocalDate.fromEpochDays(
                (timeRange.endDate.time / (24 * 60 * 60 * 1000)).toInt()
            )
            
            val data = kotlinx.coroutines.withTimeout(15000) { // 15 second timeout
                progressStatsRepository.getWorkoutDurationData(userId, startDate, endDate).first()
            }
            
            // Cache the result with 15-minute TTL
            cacheManager.put(cacheKey, data, ttl = 15.minutes)
            
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
                    errorMessage = "Failed to retrieve frequency data",
                    operation = "getFrequencyData",
                    analyticsContext = mapOf(
                        "userId" to userId,
                        "timeRange" to timeRange.toString()
                    )
                )
            }
        ) {
            val cacheKey = CacheKeyUtils.createFrequencyKey(userId, timeRange)
            
            // Check cache first
            val cachedEntry = cacheManager.get<List<FrequencyDataPoint>>(cacheKey)
            if (cachedEntry != null && cachedEntry.isValid()) {
                Timber.d("$TAG: Cache hit for frequency data - user: $userId, timeRange: $timeRange")
                return@liftrixCatching cachedEntry.data
            }
            
            Timber.d("$TAG: Cache miss for frequency data - fetching from repository")
            
            val startDate = kotlinx.datetime.LocalDate.fromEpochDays(
                (timeRange.startDate.time / (24 * 60 * 60 * 1000)).toInt()
            )
            val endDate = kotlinx.datetime.LocalDate.fromEpochDays(
                (timeRange.endDate.time / (24 * 60 * 60 * 1000)).toInt()
            )
            
            val data = kotlinx.coroutines.withTimeout(15000) { // 15 second timeout
                progressStatsRepository.getWorkoutFrequencyData(userId, startDate, endDate).first()
            }
            
            // Cache the result with 15-minute TTL
            cacheManager.put(cacheKey, data, ttl = 15.minutes)
            
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
            val cacheKey = CacheKeyUtils.createSummaryKey(userId, timeRange)
            
            // Check cache first
            val cachedEntry = cacheManager.get<ProgressSummary>(cacheKey)
            if (cachedEntry != null && cachedEntry.isValid()) {
                Timber.d("$TAG: Cache hit for progress summary - user: $userId, timeRange: $timeRange")
                return@liftrixCatching cachedEntry.data
            }
            
            Timber.d("$TAG: Cache miss for progress summary - fetching from repository")
            
            val startDate = kotlinx.datetime.LocalDate.fromEpochDays(
                (timeRange.startDate.time / (24 * 60 * 60 * 1000)).toInt()
            )
            val endDate = kotlinx.datetime.LocalDate.fromEpochDays(
                (timeRange.endDate.time / (24 * 60 * 60 * 1000)).toInt()
            )
            
            val data = kotlinx.coroutines.withTimeout(15000) { // 15 second timeout
                progressStatsRepository.getProgressSummary(userId, startDate, endDate).first()
            }
            
            // Cache the result with 15-minute TTL
            cacheManager.put(cacheKey, data, ttl = 15.minutes)
            
            data
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
            
            // Invalidate all progress data cache entries for this user
            cacheManager.invalidatePattern { cacheKey ->
                cacheKey.keyString.contains("progress:") && cacheKey.keyString.contains(":$userId:")
            }
            
            Timber.d("$TAG: Cache cleared for user: $userId")
            
            // Return unit to indicate successful refresh
            Unit
        }
    }
    
    /**
     * Converts java.util.Date to kotlinx.datetime.LocalDate for repository compatibility
     */
    private fun java.util.Date.toKotlinLocalDate(): kotlinx.datetime.LocalDate {
        return kotlinx.datetime.LocalDate.fromEpochDays(
            (this.time / (24 * 60 * 60 * 1000)).toInt()
        )
    }
}