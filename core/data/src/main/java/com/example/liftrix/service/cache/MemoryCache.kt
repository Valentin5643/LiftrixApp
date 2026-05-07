package com.example.liftrix.service.cache

import com.example.liftrix.core.cache.CacheManager
import com.example.liftrix.core.cache.CacheManagerStats
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Memory cache implementation optimized for analytics widget data.
 * 
 * This class provides a specialized wrapper around the core CacheManager with
 * analytics-specific optimizations and monitoring:
 * 
 * - 50MB memory limit with LRU eviction policy
 * - TTL-based expiration aligned with widget complexity
 * - Performance monitoring for 60fps rendering targets
 * - Memory pressure handling and optimization
 * - User-scoped cache operations for data isolation
 * 
 * Key Features:
 * - Thread-safe operations using coroutines
 * - Automatic cleanup and maintenance
 * - Performance metrics and monitoring
 * - Integration with multi-tier cache architecture
 * - Optimized for frequent read operations
 * 
 * Performance Characteristics:
 * - Access time: <50ms for 95th percentile
 * - Hit rate target: >90% for active widgets
 * - Memory usage: Bounded by 50MB limit
 * - Concurrent access: Full thread safety support
 * 
 * Usage:
 * ```
 * val memoryCache = MemoryCache()
 * 
 * // Store widget data
 * memoryCache.put("widget_key", widgetData, 15.minutes)
 * 
 * // Retrieve widget data
 * val data = memoryCache.get<WidgetData>("widget_key")
 * if (data != null && data.isValid()) {
 *     // Use cached data
 * }
 * ```
 */
@Singleton
class MemoryCache @Inject constructor() {
    
    companion object {
        // Memory cache configuration aligned with 50MB target
        private const val MAX_CACHE_SIZE = 1000 // Maximum number of entries
        private val DEFAULT_TTL = 15.minutes    // Default TTL for entries
        
        // Performance monitoring constants
        private const val TARGET_HIT_RATE = 0.90
        private const val MAX_ACCESS_TIME_MS = 50L
        private const val MEMORY_LIMIT_BYTES = 50 * 1024 * 1024L // 50MB
    }
    
    // Core cache manager for actual storage operations
    private val cacheManager = CacheManager(
        maxSize = MAX_CACHE_SIZE,
        defaultTtl = DEFAULT_TTL
    )
    
    /**
     * Retrieves an entry from the memory cache.
     * 
     * @param key Cache key for the entry
     * @return Cached entry if found and valid, null otherwise
     */
    suspend fun <T> get(key: String): T? {
        val startTime = System.currentTimeMillis()
        
        return try {
            val cacheKey = com.example.liftrix.core.cache.CacheKey.Operation(
                operation = "memory_cache",
                parameters = mapOf("key" to key)
            )
            
            val entry = cacheManager.get<T>(cacheKey)
            val accessTime = System.currentTimeMillis() - startTime
            
            if (entry != null && entry.isValid()) {
                // Monitor access time performance
                if (accessTime > MAX_ACCESS_TIME_MS) {
                    timber.log.Timber.w("MemoryCache: Slow access time: ${accessTime}ms for key: $key")
                }
                
                return entry.data
            }
            
            return null
            
        } catch (e: Exception) {
            val accessTime = System.currentTimeMillis() - startTime
            timber.log.Timber.e(e, "MemoryCache: Error accessing key: $key (${accessTime}ms)")
            null
        }
    }
    
    /**
     * Stores an entry in the memory cache with specified TTL.
     * 
     * @param key Cache key for the entry
     * @param value Value to cache
     * @param ttl Time-to-live for the entry
     */
    suspend fun <T> put(key: String, value: T, ttl: Duration = DEFAULT_TTL) {
        try {
            val cacheKey = com.example.liftrix.core.cache.CacheKey.Operation(
                operation = "memory_cache",
                parameters = mapOf("key" to key)
            )
            
            cacheManager.put(cacheKey, value, ttl)
            
            // Monitor memory usage and trigger cleanup if needed
            val stats = cacheManager.getStats()
            if (stats.memoryUsage > MEMORY_LIMIT_BYTES * 0.9) {
                timber.log.Timber.w("MemoryCache: Approaching memory limit: ${stats.memoryUsage / 1024 / 1024}MB")
                cacheManager.cleanup()
            }
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "MemoryCache: Error storing key: $key")
        }
    }
    
    /**
     * Removes an entry from the memory cache.
     * 
     * @param key Cache key to remove
     */
    suspend fun remove(key: String) {
        try {
            val cacheKey = com.example.liftrix.core.cache.CacheKey.Operation(
                operation = "memory_cache",
                parameters = mapOf("key" to key)
            )
            
            cacheManager.invalidate(cacheKey)
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "MemoryCache: Error removing key: $key")
        }
    }
    
    /**
     * Checks if an entry exists and is valid in the cache.
     * 
     * @param key Cache key to check
     * @return true if entry exists and is valid, false otherwise
     */
    suspend fun contains(key: String): Boolean {
        return try {
            val cacheKey = com.example.liftrix.core.cache.CacheKey.Operation(
                operation = "memory_cache",
                parameters = mapOf("key" to key)
            )
            
            cacheManager.contains(cacheKey)
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "MemoryCache: Error checking key: $key")
            false
        }
    }
    
    /**
     * Removes entries matching the specified pattern.
     * 
     * @param pattern Function to determine if a key should be removed
     */
    suspend fun invalidatePattern(pattern: (String) -> Boolean) {
        try {
            cacheManager.invalidatePattern { cacheKey ->
                val keyString = cacheKey.keyString
                // Extract the actual key from the cache key format
                val actualKey = extractActualKey(keyString)
                actualKey?.let { pattern(it) } ?: false
            }
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "MemoryCache: Error invalidating pattern")
        }
    }
    
    /**
     * Clears all entries from the memory cache.
     */
    suspend fun clear() {
        try {
            cacheManager.invalidateAll()
            timber.log.Timber.d("MemoryCache: Cleared all entries")
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "MemoryCache: Error clearing cache")
        }
    }
    
    /**
     * Performs cleanup of expired entries and memory optimization.
     */
    suspend fun cleanup() {
        try {
            cacheManager.cleanup()
            
            val stats = cacheManager.getStats()
            timber.log.Timber.d(
                "MemoryCache: Cleanup completed - ${stats.validEntries}/${stats.totalEntries} entries, " +
                "${stats.memoryUsage / 1024 / 1024}MB used"
            )
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "MemoryCache: Error during cleanup")
        }
    }
    
    /**
     * Returns current cache statistics for monitoring.
     * 
     * @return MemoryCacheStats containing performance metrics
     */
    suspend fun getStats(): MemoryCacheStats {
        return try {
            val cacheStats = cacheManager.getStats()
            
            MemoryCacheStats(
                totalEntries = cacheStats.totalEntries,
                validEntries = cacheStats.validEntries,
                expiredEntries = cacheStats.expiredEntries,
                hitRate = cacheStats.hitRate,
                memoryUsage = cacheStats.memoryUsage,
                memoryLimit = MEMORY_LIMIT_BYTES,
                memoryUtilization = (cacheStats.memoryUsage.toDouble() / MEMORY_LIMIT_BYTES * 100).toInt()
            )
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "MemoryCache: Error getting stats")
            MemoryCacheStats()
        }
    }
    
    /**
     * Checks if the cache is operating within performance targets.
     * 
     * @return true if cache is performing well, false if issues detected
     */
    suspend fun isHealthy(): Boolean {
        return try {
            val stats = getStats()
            
            val isHitRateHealthy = stats.hitRate >= TARGET_HIT_RATE
            val isMemoryHealthy = stats.memoryUtilization < 90
            val hasValidEntries = stats.validEntries > 0
            
            val healthy = isHitRateHealthy && isMemoryHealthy && hasValidEntries
            
            if (!healthy) {
                timber.log.Timber.w(
                    "MemoryCache: Health check failed - " +
                    "hit rate: ${stats.hitRate} (target: $TARGET_HIT_RATE), " +
                    "memory: ${stats.memoryUtilization}% (limit: 90%), " +
                    "entries: ${stats.validEntries}"
                )
            }
            
            healthy
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "MemoryCache: Error checking health")
            false
        }
    }
    
    /**
     * Extracts the actual cache key from the internal cache key format.
     * 
     * @param keyString Internal cache key string
     * @return Extracted actual key or null if extraction fails
     */
    private fun extractActualKey(keyString: String): String? {
        return try {
            // Extract key from format: "operation:memory_cache:params:key=actualkey"
            val keyMatch = Regex("key=([^,]+)").find(keyString)
            keyMatch?.groupValues?.get(1)
        } catch (e: Exception) {
            timber.log.Timber.w(e, "MemoryCache: Failed to extract key from: $keyString")
            null
        }
    }
}

/**
 * Data class containing memory cache performance statistics.
 */
data class MemoryCacheStats(
    val totalEntries: Int = 0,
    val validEntries: Int = 0,
    val expiredEntries: Int = 0,
    val hitRate: Double = 0.0,
    val memoryUsage: Long = 0L,
    val memoryLimit: Long = 0L,
    val memoryUtilization: Int = 0
)