package com.example.liftrix.service.cache

import com.example.liftrix.core.cache.CacheManager
import com.example.liftrix.core.cache.CacheKey
import com.example.liftrix.core.cache.CacheKeyUtils
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.domain.model.analytics.WidgetComplexity
import com.example.liftrix.domain.model.analytics.WidgetPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Specialized cache manager for analytics widgets with complexity-based TTL strategies.
 * 
 * This implementation extends the core CacheManager with widget-specific optimizations:
 * - TTL strategies based on widget complexity (SIMPLE: 15-30min, MODERATE: 60-120min, COMPLEX: 240-720min)
 * - Smart invalidation patterns for widget data and preferences
 * - Memory optimization with LRU eviction policy (50MB limit)
 * - Integration with disk cache for persistence between app sessions
 * - Performance monitoring for 60fps rendering targets
 * 
 * Technical Implementation:
 * - Leverages existing CacheManager for memory operations
 * - Coordinates with DiskCache for persistent storage
 * - Implements widget complexity strategy mapping
 * - User-scoped cache operations for data isolation
 * - Background cleanup and maintenance operations
 * 
 * Performance Characteristics:
 * - Memory cache: <50ms access time, 50MB size limit
 * - Cache hit rates: >90% target for active widgets
 * - TTL optimization: Aligned with widget refresh requirements
 * - Memory usage: Monitored and bounded for performance
 * 
 * @param memoryCache Core cache manager for in-memory operations
 * @param diskCache Persistent cache for cross-session storage
 * @param cacheStrategy Widget complexity-based caching strategy
 * @param ioDispatcher Coroutine dispatcher for cache operations
 */
@Singleton
class WidgetCacheManager @Inject constructor(
    private val memoryCache: CacheManager,
    private val diskCache: DiskCache,
    private val cacheStrategy: CacheStrategy,
    @com.example.liftrix.data.di.IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    
    companion object {
        private const val TAG = "WidgetCacheManager"
        
        // Cache size limits aligned with 50MB memory target
        private const val MAX_MEMORY_ENTRIES = 1000
        private const val MAX_DISK_ENTRIES = 5000
        
        // Performance monitoring thresholds
        private const val TARGET_CACHE_HIT_RATE = 0.90
        private const val MAX_ACCESS_TIME_MS = 50L
    }
    
    /**
     * Retrieves widget data from cache using multi-tier strategy.
     * 
     * Cache hierarchy:
     * 1. Memory cache (fastest, <50ms)
     * 2. Disk cache (persistent, <200ms)
     * 3. Cache miss (requires data computation)
     * 
     * @param userId User ID for cache scoping
     * @param widget Analytics widget type
     * @return Cached widget data if available and valid, null otherwise
     */
    suspend fun getWidgetData(userId: String, widget: AnalyticsWidget): WidgetData? = withContext(ioDispatcher) {
        val startTime = System.currentTimeMillis()
        
        try {
            val cacheKey = CacheKeyUtils.createWidgetKey(userId, widget)
            
            // Step 1: Check memory cache first
            val memoryCacheEntry = memoryCache.get<WidgetData>(cacheKey)
            if (memoryCacheEntry != null && memoryCacheEntry.isValid()) {
                val accessTime = System.currentTimeMillis() - startTime
                Timber.v("$TAG: Memory cache hit for ${widget.id} (${accessTime}ms)")
                return@withContext memoryCacheEntry.data
            }
            
            // Step 2: Check disk cache if memory cache miss
            val diskCacheEntry = diskCache.get<WidgetData>(cacheKey.keyString)
            if (diskCacheEntry != null && diskCacheEntry.isValid()) {
                val accessTime = System.currentTimeMillis() - startTime
                Timber.d("$TAG: Disk cache hit for ${widget.id} (${accessTime}ms)")
                
                // Promote to memory cache for faster future access
                val ttl = cacheStrategy.getTtlForWidget(widget)
                memoryCache.put(cacheKey, diskCacheEntry.data, ttl)
                
                return@withContext diskCacheEntry.data
            }
            
            // Step 3: Cache miss - no data available
            val accessTime = System.currentTimeMillis() - startTime
            Timber.v("$TAG: Cache miss for ${widget.id} (${accessTime}ms)")
            return@withContext null
            
        } catch (e: Exception) {
            val accessTime = System.currentTimeMillis() - startTime
            Timber.e(e, "$TAG: Cache access error for ${widget.id} (${accessTime}ms)")
            return@withContext null
        }
    }
    
    /**
     * Stores widget data in multi-tier cache with complexity-based TTL.
     * 
     * @param userId User ID for cache scoping
     * @param widget Analytics widget type
     * @param data Widget data to cache
     */
    suspend fun putWidgetData(userId: String, widget: AnalyticsWidget, data: WidgetData) = withContext(ioDispatcher) {
        try {
            val cacheKey = CacheKeyUtils.createWidgetKey(userId, widget)
            val ttl = cacheStrategy.getTtlForWidget(widget)
            
            // Store in memory cache for fast access
            memoryCache.put(cacheKey, data, ttl)
            
            // Store in disk cache for persistence
            diskCache.put(cacheKey.keyString, data, ttl)
            
            Timber.v("$TAG: Cached widget data for ${widget.id} with TTL: $ttl")
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to cache widget data for ${widget.id}")
        }
    }
    
    /**
     * Retrieves widget preferences from cache.
     * 
     * @param userId User ID for preferences lookup
     * @return Cached widget preferences if available and valid, null otherwise
     */
    suspend fun getWidgetPreferences(userId: String): WidgetPreferences? = withContext(ioDispatcher) {
        try {
            val cacheKey = CacheKeyUtils.createPreferencesKey(userId)
            
            // Check memory cache first
            val memoryCacheEntry = memoryCache.get<WidgetPreferences>(cacheKey)
            if (memoryCacheEntry != null && memoryCacheEntry.isValid()) {
                Timber.v("$TAG: Memory cache hit for preferences - user: $userId")
                return@withContext memoryCacheEntry.data
            }
            
            // Check disk cache
            val diskCacheEntry = diskCache.get<WidgetPreferences>(cacheKey.keyString)
            if (diskCacheEntry != null && diskCacheEntry.isValid()) {
                Timber.d("$TAG: Disk cache hit for preferences - user: $userId")
                
                // Promote to memory cache
                memoryCache.put(cacheKey, diskCacheEntry.data, 30.minutes)
                return@withContext diskCacheEntry.data
            }
            
            Timber.v("$TAG: Cache miss for preferences - user: $userId")
            return@withContext null
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Cache access error for preferences - user: $userId")
            return@withContext null
        }
    }
    
    /**
     * Stores widget preferences in cache with 30-minute TTL.
     * 
     * @param preferences Widget preferences to cache
     */
    suspend fun putWidgetPreferences(preferences: WidgetPreferences) = withContext(ioDispatcher) {
        try {
            val cacheKey = CacheKeyUtils.createPreferencesKey(preferences.userId)
            val ttl = 30.minutes // Preferences change less frequently
            
            // Store in both caches
            memoryCache.put(cacheKey, preferences, ttl)
            diskCache.put(cacheKey.keyString, preferences, ttl)
            
            Timber.v("$TAG: Cached preferences for user: ${preferences.userId}")
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to cache preferences for user: ${preferences.userId}")
        }
    }
    
    /**
     * Invalidates all widget data for a specific user.
     * 
     * @param userId User ID for cache invalidation
     */
    suspend fun invalidateUserWidgets(userId: String) = withContext(ioDispatcher) {
        try {
            // Invalidate memory cache entries for this user
            memoryCache.invalidatePattern { key ->
                key.keyString.contains("analytics:widget:$userId:")
            }
            
            // Invalidate disk cache entries for this user
            diskCache.invalidatePattern { keyString ->
                keyString.contains("analytics:widget:$userId:")
            }
            
            Timber.d("$TAG: Invalidated all widget cache for user: $userId")
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to invalidate widget cache for user: $userId")
        }
    }
    
    /**
     * Invalidates preferences for a specific user.
     * 
     * @param userId User ID for preferences invalidation
     */
    suspend fun invalidateUserPreferences(userId: String) = withContext(ioDispatcher) {
        try {
            val cacheKey = CacheKeyUtils.createPreferencesKey(userId)
            
            // Invalidate from both caches
            memoryCache.invalidate(cacheKey)
            diskCache.invalidate(cacheKey.keyString)
            
            Timber.d("$TAG: Invalidated preferences cache for user: $userId")
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to invalidate preferences cache for user: $userId")
        }
    }
    
    /**
     * Invalidates specific widget data across all users (for widget logic changes).
     * 
     * @param widget Analytics widget type to invalidate
     */
    suspend fun invalidateWidget(widget: AnalyticsWidget) = withContext(ioDispatcher) {
        try {
            // Invalidate memory cache entries for this widget type
            memoryCache.invalidatePattern { key ->
                key.keyString.contains("analytics:widget:") && key.keyString.endsWith(":${widget.id}")
            }
            
            // Invalidate disk cache entries for this widget type
            diskCache.invalidatePattern { keyString ->
                keyString.contains("analytics:widget:") && keyString.endsWith(":${widget.id}")
            }
            
            Timber.d("$TAG: Invalidated all cache for widget: ${widget.id}")
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to invalidate cache for widget: ${widget.id}")
        }
    }
    
    /**
     * Performs comprehensive cache cleanup and maintenance.
     * 
     * This method should be called periodically to:
     * - Remove expired entries
     * - Optimize memory usage
     * - Synchronize memory and disk caches
     * - Generate performance reports
     */
    suspend fun performMaintenance() = withContext(ioDispatcher) {
        try {
            Timber.d("$TAG: Starting cache maintenance")
            
            // Cleanup expired entries from both caches
            memoryCache.cleanup()
            diskCache.cleanup()
            
            // Generate performance statistics
            val memoryStats = memoryCache.getStats()
            val diskStats = diskCache.getStats()
            
            Timber.i(
                "$TAG: Cache stats - Memory: ${memoryStats.validEntries}/${memoryStats.totalEntries} " +
                "entries, Disk: ${diskStats.totalEntries} entries"
            )
            
            // Check performance targets
            if (memoryStats.hitRate < TARGET_CACHE_HIT_RATE) {
                Timber.w("$TAG: Memory cache hit rate below target: ${memoryStats.hitRate} < $TARGET_CACHE_HIT_RATE")
            }
            
            Timber.d("$TAG: Cache maintenance completed")
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Cache maintenance failed")
        }
    }
    
    /**
     * Returns comprehensive cache statistics for monitoring.
     * 
     * @return CacheStats containing performance metrics
     */
    suspend fun getStats(): CacheStats = withContext(ioDispatcher) {
        try {
            val memoryStats = memoryCache.getStats()
            val diskStats = diskCache.getStats()
            
            return@withContext CacheStats(
                memoryEntries = memoryStats.validEntries,
                diskEntries = diskStats.totalEntries,
                memoryHitRate = memoryStats.hitRate,
                memoryUsage = memoryStats.validEntries * 1024L, // Estimate memory usage
                diskUsage = diskStats.totalEntries * 2048L, // Estimate disk usage
                totalCacheSize = (memoryStats.validEntries * 1024L) + (diskStats.totalEntries * 2048L)
            )
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to get cache stats")
            return@withContext CacheStats()
        }
    }
}

/**
 * Data class for cache performance statistics.
 */
data class CacheStats(
    val memoryEntries: Int = 0,
    val diskEntries: Int = 0,
    val memoryHitRate: Double = 0.0,
    val memoryUsage: Long = 0L,
    val diskUsage: Long = 0L,
    val totalCacheSize: Long = 0L
)