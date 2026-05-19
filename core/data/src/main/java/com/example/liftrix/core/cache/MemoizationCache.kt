package com.example.liftrix.core.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Generic memoization cache for expensive computations with TTL support
 *
 * Provides thread-safe caching for expensive calculations with automatic expiration
 * and memory management. Optimized for use with suspend functions and coroutines.
 *
 * Features:
 * - Thread-safe memoization with Mutex protection
 * - TTL-based cache expiration
 * - Memory-efficient LRU eviction
 * - Comprehensive cache statistics
 * - Configurable cache size limits
 *
 * Performance Targets:
 * - Cache lookup: <1ms
 * - Cache put: <2ms
 * - Memory usage: <50MB for 1000 entries
 *
 * @param K Cache key type
 * @param V Cache value type
 * @param maxSize Maximum number of entries (default: 100)
 * @param defaultTtl Default time-to-live for cache entries (default: 5 minutes)
 */
class MemoizationCache<K, V>(
    private val maxSize: Int = 100,
    private val defaultTtl: Duration = 5.minutes
) {
    
    /**
     * Cache entry with timestamp and TTL information
     */
    private data class CacheEntry<V>(
        val value: V,
        val timestamp: Instant,
        val ttl: Duration
    ) {
        fun isExpired(now: Instant = Clock.System.now()): Boolean {
            return now - timestamp > ttl
        }
    }
    
    private val cache = mutableMapOf<K, CacheEntry<V>>()
    private val accessOrder = mutableListOf<K>()
    private val mutex = Mutex()
    
    // Cache statistics
    private var hits = 0L
    private var misses = 0L
    private var evictions = 0L
    
    /**
     * Memoizes a computation with automatic caching
     *
     * @param key Cache key for the computation
     * @param ttl Time-to-live for the cache entry (optional)
     * @param computation Suspend function to compute the value
     * @return Cached or computed value
     */
    suspend fun memoize(
        key: K,
        ttl: Duration = defaultTtl,
        computation: suspend () -> V
    ): V {
        mutex.withLock {
            // Check if key exists and is not expired
            val entry = cache[key]
            if (entry != null && !entry.isExpired()) {
                // Update access order for LRU
                accessOrder.remove(key)
                accessOrder.add(key)
                hits++
                return entry.value
            }
            
            // Remove expired entry if present
            if (entry != null && entry.isExpired()) {
                cache.remove(key)
                accessOrder.remove(key)
            }
        }
        
        // Compute value outside of lock to prevent blocking
        val result = computation()
        
        mutex.withLock {
            // Store result in cache
            val newEntry = CacheEntry(result, Clock.System.now(), ttl)
            cache[key] = newEntry
            
            // Update access order
            accessOrder.remove(key) // Remove if already present
            accessOrder.add(key)
            
            // Perform LRU eviction if necessary
            if (cache.size > maxSize) {
                evictLeastRecentlyUsed()
            }
            
            misses++
            return result
        }
    }
    
    /**
     * Invalidates a specific cache entry
     *
     * @param key Cache key to invalidate
     * @return True if entry was found and removed
     */
    suspend fun invalidate(key: K): Boolean {
        mutex.withLock {
            val wasPresent = cache.remove(key) != null
            accessOrder.remove(key)
            return wasPresent
        }
    }
    
    /**
     * Clears all cache entries
     */
    suspend fun clear() {
        mutex.withLock {
            cache.clear()
            accessOrder.clear()
            hits = 0L
            misses = 0L
            evictions = 0L
        }
    }
    
    /**
     * Gets the current cache size
     */
    suspend fun size(): Int {
        mutex.withLock {
            return cache.size
        }
    }
    
    /**
     * Checks if a key exists in the cache (and is not expired)
     */
    suspend fun containsKey(key: K): Boolean {
        mutex.withLock {
            val entry = cache[key]
            return entry != null && !entry.isExpired()
        }
    }
    
    /**
     * Gets cache statistics
     */
    suspend fun getStats(): CacheStats {
        mutex.withLock {
            return CacheStats(
                size = cache.size,
                maxSize = maxSize,
                hits = hits,
                misses = misses,
                evictions = evictions,
                hitRate = if (hits + misses > 0) hits.toDouble() / (hits + misses) else 0.0
            )
        }
    }
    
    /**
     * Removes expired entries from the cache
     */
    suspend fun cleanupExpired() {
        mutex.withLock {
            val now = Clock.System.now()
            val expiredKeys = cache.entries
                .filter { (_, entry) -> entry.isExpired(now) }
                .map { it.key }
            
            expiredKeys.forEach { key ->
                cache.remove(key)
                accessOrder.remove(key)
            }
        }
    }
    
    /**
     * Evicts the least recently used entry
     */
    private fun evictLeastRecentlyUsed() {
        if (accessOrder.isNotEmpty()) {
            val lruKey = accessOrder.removeAt(0)
            cache.remove(lruKey)
            evictions++
        }
    }
}

/**
 * Cache statistics data class
 */
data class CacheStats(
    val size: Int,
    val maxSize: Int,
    val hits: Long,
    val misses: Long,
    val evictions: Long,
    val hitRate: Double
)

/**
 * Extension function to create cache keys from multiple parameters
 */
fun createCacheKey(vararg params: Any?): String {
    return params.joinToString(":") { it?.toString() ?: "null" }
}

/**
 * Extension function to create cache keys with hash for complex objects
 */
fun createHashedCacheKey(vararg params: Any?): String {
    val combined = params.joinToString(":") { it?.toString() ?: "null" }
    return combined.hashCode().toString()
}
