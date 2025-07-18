package com.example.liftrix.core.cache

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import timber.log.Timber

/**
 * Thread-safe LRU cache implementation with TTL support for service layer responses.
 * 
 * This cache manager provides:
 * - LRU eviction policy with configurable size limits
 * - TTL-based expiration with automatic cleanup
 * - Thread-safe operations using coroutines and mutex
 * - Immutable data structures for performance and safety
 * - Comprehensive logging for debugging and monitoring
 * 
 * Performance Characteristics:
 * - Get operations: O(1) average case
 * - Put operations: O(1) average case with occasional O(n) for LRU eviction
 * - Memory usage: Bounded by maxSize parameter
 * - Thread safety: Full concurrency support via mutex
 * 
 * Usage:
 * ```
 * val cacheManager = CacheManager(maxSize = 100, defaultTtl = 15.minutes)
 * 
 * // Put data with default TTL
 * cacheManager.put(key, data)
 * 
 * // Put data with custom TTL
 * cacheManager.put(key, data, ttl = 5.minutes)
 * 
 * // Get data
 * val result = cacheManager.get<MyDataType>(key)
 * if (result != null && !result.isExpired()) {
 *     // Use cached data
 * }
 * ```
 * 
 * @param maxSize Maximum number of entries in cache (default: 100)
 * @param defaultTtl Default TTL for cache entries (default: 15 minutes)
 */
class CacheManager(
    private val maxSize: Int = 100,
    private val defaultTtl: Duration = 15.minutes
) {
    
    private val mutex = Mutex()
    private val cache: MutableMap<CacheKey, CacheEntry<Any>> = ConcurrentHashMap()
    private val accessOrder: MutableMap<CacheKey, Long> = ConcurrentHashMap()
    private var accessCounter: Long = 0
    
    companion object {
        private const val TAG = "CacheManager"
    }
    
    /**
     * Retrieves a cached entry for the given key.
     * 
     * @param key The cache key to retrieve
     * @return CacheEntry if found and not expired, null otherwise
     */
    suspend fun <T> get(key: CacheKey): CacheEntry<T>? = mutex.withLock {
        @Suppress("UNCHECKED_CAST")
        val entry = cache[key] as? CacheEntry<T>
        
        if (entry == null) {
            Timber.v("$TAG: Cache miss for key: ${key.toString()}")
            return null
        }
        
        if (entry.isExpired()) {
            Timber.v("$TAG: Cache entry expired for key: ${key.toString()}")
            // Remove expired entry
            cache.remove(key)
            accessOrder.remove(key)
            return null
        }
        
        // Update access order for LRU
        accessOrder[key] = ++accessCounter
        
        Timber.v("$TAG: Cache hit for key: ${key.toString()}")
        return entry
    }
    
    /**
     * Stores an entry in the cache with specified TTL.
     * 
     * @param key The cache key
     * @param value The value to cache
     * @param ttl Time-to-live for the entry (uses default if not specified)
     * @return The created CacheEntry
     */
    suspend fun <T> put(key: CacheKey, value: T, ttl: Duration = defaultTtl): CacheEntry<T> = mutex.withLock {
        val entry = CacheEntry(
            data = value,
            timestamp = Clock.System.now(),
            ttl = ttl
        )
        
        // Add to cache
        @Suppress("UNCHECKED_CAST")
        cache[key] = entry as CacheEntry<Any>
        accessOrder[key] = ++accessCounter
        
        // Check if we need to evict entries
        if (cache.size > maxSize) {
            evictLeastRecentlyUsed()
        }
        
        Timber.v("$TAG: Cache entry stored for key: ${key.toString()}, ttl: $ttl")
        return entry
    }
    
    /**
     * Invalidates (removes) a specific cache entry.
     * 
     * @param key The cache key to invalidate
     */
    suspend fun invalidate(key: CacheKey) = mutex.withLock {
        val removed = cache.containsKey(key)
        cache.remove(key)
        accessOrder.remove(key)
        
        if (removed) {
            Timber.d("$TAG: Invalidated cache entry for key: ${key.toString()}")
        }
    }
    
    /**
     * Invalidates all cache entries matching the key pattern.
     * 
     * @param pattern The key pattern to match (e.g., by userId or operation type)
     */
    suspend fun invalidatePattern(pattern: (CacheKey) -> Boolean) = mutex.withLock {
        val keysToRemove = cache.keys.filter(pattern)
        
        keysToRemove.forEach { key ->
            cache.remove(key)
            accessOrder.remove(key)
        }
        
        if (keysToRemove.isNotEmpty()) {
            Timber.d("$TAG: Invalidated ${keysToRemove.size} cache entries matching pattern")
        }
    }
    
    /**
     * Clears all cache entries.
     */
    suspend fun invalidateAll() = mutex.withLock {
        val entryCount = cache.size
        cache.clear()
        accessOrder.clear()
        accessCounter = 0
        
        Timber.d("$TAG: Cleared all cache entries ($entryCount entries)")
    }
    
    /**
     * Checks if a cache entry exists and is not expired.
     * 
     * @param key The cache key to check
     * @return true if entry exists and is valid, false otherwise
     */
    suspend fun contains(key: CacheKey): Boolean = mutex.withLock {
        val entry = cache[key]
        return entry != null && !entry.isExpired()
    }
    
    /**
     * Returns current cache statistics.
     * 
     * @return CacheStats containing current cache state information
     */
    suspend fun getStats(): CacheManagerStats = mutex.withLock {
        val expiredCount = cache.values.count { it.isExpired() }
        val validCount = cache.size - expiredCount
        
        return CacheManagerStats(
            totalEntries = cache.size,
            validEntries = validCount,
            expiredEntries = expiredCount,
            maxSize = maxSize,
            hitRate = 0.0, // Would need to track hits/misses for accurate calculation
            memoryUsage = estimateMemoryUsage()
        )
    }
    
    /**
     * Performs cleanup of expired entries.
     * This is called automatically but can be invoked manually for eager cleanup.
     */
    suspend fun cleanup() = mutex.withLock {
        val expiredKeys = cache.filterValues { it.isExpired() }.keys
        
        expiredKeys.forEach { key ->
            cache.remove(key)
            accessOrder.remove(key)
        }
        
        if (expiredKeys.isNotEmpty()) {
            Timber.d("$TAG: Cleaned up ${expiredKeys.size} expired cache entries")
        }
    }
    
    /**
     * Evicts the least recently used entry to make room for new entries.
     */
    private fun evictLeastRecentlyUsed() {
        // Find the key with the lowest access counter
        val lruKey = accessOrder.minByOrNull { it.value }?.key
        
        if (lruKey != null) {
            cache.remove(lruKey)
            accessOrder.remove(lruKey)
            Timber.v("$TAG: Evicted LRU entry for key: ${lruKey.toString()}")
        }
    }
    
    /**
     * Estimates memory usage of the cache.
     * This is a rough estimate for monitoring purposes.
     */
    private fun estimateMemoryUsage(): Long {
        // Rough estimation based on average entry size
        // In a production system, this would be more sophisticated
        return cache.size * 1024L // Assume 1KB per entry on average
    }
}

/**
 * Data class representing cache statistics.
 */
data class CacheManagerStats(
    val totalEntries: Int,
    val validEntries: Int,
    val expiredEntries: Int,
    val maxSize: Int,
    val hitRate: Double,
    val memoryUsage: Long
)