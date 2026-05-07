package com.example.liftrix.core.performance

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.liftrix.core.data.BuildConfig
import java.security.MessageDigest

/**
 * High-performance caching manager for workout serialization results.
 *
 * Features:
 * - LRU eviction policy for memory management
 * - Cache invalidation based on data mutations
 * - Performance monitoring integration
 * - Automatic cache size management
 * - Thread-safe operations
 */
@Singleton
class SerializationCacheManager @Inject constructor(
    private val performanceMonitor: SerializationPerformanceMonitor
) {

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, CacheEntry>()
    private val accessOrder = mutableListOf<String>()

    companion object {
        const val MAX_CACHE_SIZE = 50 // Maximum cached serialization results
        const val CACHE_ENTRY_TTL_MS = 5 * 60 * 1000L // 5 minutes TTL
        const val MAX_CACHED_SIZE_BYTES = 100 * 1024 // 100KB max per entry
    }

    /**
     * Gets cached serialization result.
     */
    suspend fun getCachedSerialization(
        exerciseIds: List<String>,
        lastModified: Long
    ): String? = mutex.withLock {
        val startTime = System.currentTimeMillis()
        val cacheKey = generateCacheKey(exerciseIds, lastModified)

        val entry = cache[cacheKey]
        val result = if (entry != null && !entry.isExpired()) {
            // Update access order for LRU
            accessOrder.remove(cacheKey)
            accessOrder.add(cacheKey)

            entry.serializedData
        } else {
            // Remove expired entry
            if (entry != null && entry.isExpired()) {
                cache.remove(cacheKey)
                accessOrder.remove(cacheKey)
                if (BuildConfig.DEBUG) {
                    Timber.d("🗑️ CACHE: Expired entry removed for key: ${cacheKey.take(8)}")
                }
            }
            null
        }

        val retrievalTime = System.currentTimeMillis() - startTime
        performanceMonitor.recordCacheMetric(cacheKey, result != null, retrievalTime)

        result
    }

    /**
     * Caches serialization result.
     */
    suspend fun cacheSerialization(
        exerciseIds: List<String>,
        lastModified: Long,
        serializedData: String
    ) = mutex.withLock {
        // Don't cache oversized data
        if (serializedData.length > MAX_CACHED_SIZE_BYTES) {
            if (BuildConfig.DEBUG) {
                Timber.w("⚠️ CACHE: Serialization result too large to cache: ${serializedData.length} bytes")
            }
            return@withLock
        }

        val cacheKey = generateCacheKey(exerciseIds, lastModified)

        // Remove if already exists (for updating access order)
        if (cache.containsKey(cacheKey)) {
            accessOrder.remove(cacheKey)
        }

        // Add new entry
        cache[cacheKey] = CacheEntry(
            serializedData = serializedData,
            cachedAt = System.currentTimeMillis(),
            sizeBytes = serializedData.length
        )
        accessOrder.add(cacheKey)

        // Enforce cache size limits
        evictIfNecessary()

        if (BuildConfig.DEBUG) {
            Timber.d("💾 CACHE: Stored serialization result (${serializedData.length} bytes) for ${exerciseIds.size} exercises")
        }
    }

    /**
     * Invalidates cache entries for specific exercises.
     */
    suspend fun invalidateExercises(exerciseIds: List<String>) = mutex.withLock {
        val keysToRemove = cache.keys.filter { key ->
            exerciseIds.any { exerciseId -> key.contains(exerciseId) }
        }

        keysToRemove.forEach { key ->
            cache.remove(key)
            accessOrder.remove(key)
        }

        if (BuildConfig.DEBUG && keysToRemove.isNotEmpty()) {
            Timber.d("🧹 CACHE: Invalidated ${keysToRemove.size} entries for ${exerciseIds.size} exercises")
        }
    }

    /**
     * Invalidates all cache entries for a specific workout.
     */
    suspend fun invalidateWorkout(workoutId: String) = mutex.withLock {
        val keysToRemove = cache.keys.filter { key ->
            key.contains(workoutId)
        }

        keysToRemove.forEach { key ->
            cache.remove(key)
            accessOrder.remove(key)
        }

        if (BuildConfig.DEBUG && keysToRemove.isNotEmpty()) {
            Timber.d("🧹 CACHE: Invalidated ${keysToRemove.size} entries for workout: $workoutId")
        }
    }

    /**
     * Clears all cached data.
     */
    suspend fun clearCache() = mutex.withLock {
        val clearedCount = cache.size
        cache.clear()
        accessOrder.clear()

        if (BuildConfig.DEBUG) {
            Timber.d("🧹 CACHE: Cleared all $clearedCount cached entries")
        }
    }

    /**
     * Gets cache statistics for monitoring.
     */
    suspend fun getCacheStats(): CacheStats = mutex.withLock {
        val totalSizeBytes = cache.values.sumOf { it.sizeBytes }
        val averageEntrySize = if (cache.isNotEmpty()) totalSizeBytes / cache.size else 0
        val expiredEntries = cache.values.count { it.isExpired() }

        CacheStats(
            totalEntries = cache.size,
            totalSizeBytes = totalSizeBytes,
            averageEntrySizeBytes = averageEntrySize,
            expiredEntries = expiredEntries,
            maxCacheSize = MAX_CACHE_SIZE,
            cacheUtilization = (cache.size.toDouble() / MAX_CACHE_SIZE * 100).toInt()
        )
    }

    /**
     * Evicts old entries if cache size exceeds limits.
     */
    private fun evictIfNecessary() {
        while (cache.size > MAX_CACHE_SIZE) {
            val oldestKey = accessOrder.firstOrNull()
            if (oldestKey != null) {
                cache.remove(oldestKey)
                accessOrder.remove(oldestKey)
                if (BuildConfig.DEBUG) {
                    Timber.d("🗑️ CACHE: Evicted LRU entry: ${oldestKey.take(8)}")
                }
            } else {
                break
            }
        }

        // Remove expired entries
        val expiredKeys = cache.filterValues { it.isExpired() }.keys.toList()
        expiredKeys.forEach { key ->
            cache.remove(key)
            accessOrder.remove(key)
        }

        if (BuildConfig.DEBUG && expiredKeys.isNotEmpty()) {
            Timber.d("🗑️ CACHE: Removed ${expiredKeys.size} expired entries")
        }
    }

    /**
     * Generates a cache key based on exercise IDs and modification times.
     */
    private fun generateCacheKey(exerciseIds: List<String>, lastModified: Long): String {
        val input = exerciseIds.sorted().joinToString(",") + ":$lastModified"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(16)
    }
}

/**
 * Individual cache entry with TTL and size tracking.
 */
data class CacheEntry(
    val serializedData: String,
    val cachedAt: Long,
    val sizeBytes: Int
) {
    fun isExpired(): Boolean {
        return System.currentTimeMillis() - cachedAt > SerializationCacheManager.CACHE_ENTRY_TTL_MS
    }
}

/**
 * Cache statistics for monitoring and debugging.
 */
data class CacheStats(
    val totalEntries: Int,
    val totalSizeBytes: Int,
    val averageEntrySizeBytes: Int,
    val expiredEntries: Int,
    val maxCacheSize: Int,
    val cacheUtilization: Int // Percentage
)
