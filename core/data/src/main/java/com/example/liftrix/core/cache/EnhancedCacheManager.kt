package com.example.liftrix.core.cache

import android.content.Context
import android.util.LruCache
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import com.example.liftrix.data.di.IoDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced multi-tier cache manager with memory and disk cache layers.
 * 
 * This implementation provides:
 * - L1 Cache: In-memory LRU cache for instant access (<10ms)
 * - L2 Cache: Persistent disk cache for session survival (<100ms)
 * - Intelligent cache warming and preloading
 * - TTL-based expiration with configurable policies
 * - Pattern-based invalidation for smart cache management
 * - Performance monitoring and analytics integration
 * 
 * Performance Targets (SPEC-20250110):
 * - Memory cache hits: <10ms response time
 * - Disk cache hits: <100ms response time
 * - Fresh data queries: <500ms response time
 * - Cache hit rate: 80%+ for repeated queries
 * - Memory usage: <100MB for large datasets
 * 
 * Architecture:
 * ```
 * UI Request → Memory Cache → Disk Cache → Repository → Database
 *                ↓ HIT          ↓ HIT        ↓ MISS      ↓ COMPUTE
 *               <10ms          <100ms       <500ms       Variable
 * ```
 * 
 * @param context Android application context for file operations
 * @param gson JSON serializer for disk cache persistence
 * @param ioDispatcher Coroutine dispatcher for disk I/O operations
 * @param cacheConfig Configuration for cache behavior and limits
 */
@Singleton
class EnhancedCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val cacheConfig: CacheConfiguration
) {
    
    // Memory cache (L1) - LRU cache for instant access
    internal val memoryCache = object : LruCache<String, CacheEntry<*>>(cacheConfig.memoryCacheSizeMB * 1024 * 1024) {
        override fun sizeOf(key: String, value: CacheEntry<*>): Int {
            // Calculate memory size (rough estimation)
            return key.length * 2 + estimateObjectSize(value)
        }
    }
    
    // Disk cache directory with robust creation
    private val cacheDir = File(context.cacheDir, "enhanced_cache").apply {
        ensureDirectoryExists()
    }
    
    // Thread-safe access coordination
    internal val memoryMutex = Mutex()
    internal val diskMutex = Mutex()
    
    // Performance tracking
    internal var hitCount = 0
    internal var missCount = 0
    internal var diskHitCount = 0
    
    companion object {
        private const val TAG = "EnhancedCacheManager"
        private const val DISK_CACHE_VERSION = 1
    }
    
    /**
     * Gets cached data with fallback computation.
     * 
     * This method implements the multi-tier cache strategy:
     * 1. Check memory cache (L1)
     * 2. Check disk cache (L2)
     * 3. Compute fresh data and cache at both levels
     * 
     * @param key Cache key for the data
     * @param ttl Time-to-live for the cached entry
     * @param compute Suspension function to compute fresh data
     * @return Cached or computed data of type T
     */
    suspend fun <T> getOrCompute(
        key: CacheKey,
        ttl: Duration = cacheConfig.defaultTTL,
        typeClass: Class<T>,
        compute: suspend () -> T
    ): T {
        val keyString = key.keyString
        
        // L1: Check memory cache first
        memoryMutex.withLock {
            memoryCache.get(keyString)?.let { entry ->
                @Suppress("UNCHECKED_CAST")
                val typedEntry = entry as CacheEntry<T>
                if (typedEntry.isValid()) {
                    hitCount++
                    Timber.d("$TAG: Memory cache HIT: $keyString")
                    return typedEntry.data
                } else {
                    // Remove expired entry
                    memoryCache.remove(keyString)
                    Timber.v("$TAG: Memory cache entry expired: $keyString")
                }
            }
        }
        
        // L2: Check disk cache
        val diskData = getDiskCache<T>(keyString, ttl, typeClass)
        if (diskData != null) {
            // Cache hit - store in memory for next access
            val cacheEntry = CacheEntry(diskData, Clock.System.now(), ttl)
            memoryMutex.withLock {
                memoryCache.put(keyString, cacheEntry)
            }
            diskHitCount++
            Timber.d("$TAG: Disk cache HIT: $keyString")
            return diskData
        }
        
        // Cache miss - compute fresh data
        Timber.d("$TAG: Cache MISS: $keyString - computing")
        missCount++
        
        val startTime = System.currentTimeMillis()
        val result = compute()
        val computeTime = System.currentTimeMillis() - startTime
        
        // Cache at both levels
        val cacheEntry = CacheEntry(result, Clock.System.now(), ttl)
        
        // Store in memory cache
        memoryMutex.withLock {
            memoryCache.put(keyString, cacheEntry)
        }
        
        // Store in disk cache (async for performance)
        putDiskCache(keyString, result, ttl, typeClass)
        
        Timber.d("$TAG: Computed and cached: $keyString (${computeTime}ms)")
        return result
    }
    
    /**
     * Puts data into both cache levels.
     * 
     * @param key Cache key
     * @param value Data to cache
     * @param ttl Time-to-live for the entry
     */
    suspend fun <T> put(key: CacheKey, value: T, typeClass: Class<T>, ttl: Duration = cacheConfig.defaultTTL) {
        val keyString = key.keyString
        val cacheEntry = CacheEntry(value, Clock.System.now(), ttl)
        
        // Store in memory cache
        memoryMutex.withLock {
            memoryCache.put(keyString, cacheEntry)
        }
        
        // Store in disk cache
        putDiskCache(keyString, value, ttl, typeClass)
        
        Timber.v("$TAG: Stored in both caches: $keyString")
    }
    
    /**
     * Gets data from memory cache only.
     * 
     * @param key Cache key
     * @return Cached data if found and valid, null otherwise
     */
    suspend fun <T> getMemoryCache(key: CacheKey): T? = memoryMutex.withLock {
        val keyString = key.keyString
        val entry = memoryCache.get(keyString)
        
        if (entry != null && entry.isValid()) {
            @Suppress("UNCHECKED_CAST")
            return (entry as CacheEntry<T>).data
        }
        
        return null
    }
    
    /**
     * Invalidates cache entries matching the pattern.
     * 
     * Supports pattern-based invalidation for smart cache management:
     * - User-scoped: "user:123:*"
     * - Time-scoped: "*:2025-01-*"
     * - Widget-scoped: "analytics:widget:*"
     * 
     * @param pattern String pattern to match cache keys
     */
    suspend fun invalidatePattern(pattern: String) {
        val regex = pattern.replace("*", ".*").toRegex()
        
        // Invalidate memory cache
        memoryMutex.withLock {
            val keysToRemove = memoryCache.snapshot().keys.filter { regex.matches(it) }
            keysToRemove.forEach { memoryCache.remove(it) }
            if (keysToRemove.isNotEmpty()) {
                Timber.d("$TAG: Invalidated ${keysToRemove.size} memory cache entries for pattern: $pattern")
            }
        }
        
        // Invalidate disk cache
        withContext(ioDispatcher) {
            diskMutex.withLock {
                try {
                    val filesToDelete = cacheDir.listFiles()?.filter { file ->
                        regex.matches(file.nameWithoutExtension)
                    } ?: emptyList()
                    
                    filesToDelete.forEach { file ->
                        if (file.delete()) {
                            Timber.v("$TAG: Deleted disk cache file: ${file.name}")
                        }
                    }
                    
                    if (filesToDelete.isNotEmpty()) {
                        Timber.d("$TAG: Invalidated ${filesToDelete.size} disk cache entries for pattern: $pattern")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: Error invalidating disk cache for pattern: $pattern")
                }
            }
        }
    }
    
    /**
     * Invalidates a specific cache key.
     * 
     * @param key Cache key to invalidate
     */
    suspend fun invalidate(key: CacheKey) {
        val keyString = key.keyString
        
        // Remove from memory cache
        memoryMutex.withLock {
            memoryCache.remove(keyString)
        }
        
        // Remove from disk cache
        withContext(ioDispatcher) {
            diskMutex.withLock {
                val file = File(cacheDir, "$keyString.cache")
                if (file.exists() && file.delete()) {
                    Timber.d("$TAG: Invalidated cache entry: $keyString")
                }
            }
        }
    }
    
    /**
     * Clears all cache data.
     */
    suspend fun clear() {
        // Clear memory cache
        memoryMutex.withLock {
            val size = memoryCache.size()
            memoryCache.evictAll()
            Timber.d("$TAG: Cleared memory cache ($size entries)")
        }
        
        // Clear disk cache
        withContext(ioDispatcher) {
            diskMutex.withLock {
                try {
                    val deletedCount = cacheDir.listFiles()?.count { it.delete() } ?: 0
                    Timber.d("$TAG: Cleared disk cache ($deletedCount files)")
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: Error clearing disk cache")
                }
            }
        }
        
        // Reset statistics
        hitCount = 0
        missCount = 0
        diskHitCount = 0
    }
    
    /**
     * Returns cache performance statistics.
     * 
     * @return EnhancedCacheStats with hit rates and performance metrics
     */
    suspend fun getStats(): EnhancedCacheStats = memoryMutex.withLock {
        val totalRequests = hitCount + missCount + diskHitCount
        val memoryHitRate = if (totalRequests > 0) hitCount.toDouble() / totalRequests else 0.0
        val diskHitRate = if (totalRequests > 0) diskHitCount.toDouble() / totalRequests else 0.0
        val overallHitRate = if (totalRequests > 0) (hitCount + diskHitCount).toDouble() / totalRequests else 0.0
        
        EnhancedCacheStats(
            memoryEntries = memoryCache.size(),
            diskEntries = cacheDir.listFiles()?.size ?: 0,
            memoryHitRate = memoryHitRate,
            diskHitRate = diskHitRate,
            overallHitRate = overallHitRate,
            totalRequests = totalRequests,
            memorySize = memoryCache.size(),
            diskSize = estimateDiskCacheSize()
        )
    }
    
    /**
     * Performs cache maintenance and cleanup.
     */
    suspend fun performMaintenance() {
        // Clean expired memory entries
        memoryMutex.withLock {
            val snapshot = memoryCache.snapshot()
            val expiredKeys = snapshot.entries.filter { (_, entry) ->
                entry.isExpired()
            }.map { it.key }
            
            expiredKeys.forEach { memoryCache.remove(it) }
            
            if (expiredKeys.isNotEmpty()) {
                Timber.d("$TAG: Cleaned ${expiredKeys.size} expired memory cache entries")
            }
        }
        
        // Clean expired disk entries
        withContext(ioDispatcher) {
            diskMutex.withLock {
                cleanExpiredDiskEntries()
            }
        }
    }
    
    // Private helper methods
    
    internal suspend fun <T> getDiskCache(key: String, ttl: Duration, clazz: Class<T>): T? {
        return withContext(ioDispatcher) {
            diskMutex.withLock {
                try {
                    val file = File(cacheDir, "$key.cache")
                    if (!file.exists()) return@withLock null
                    
                    val content = file.readText()
                    // Create TypeToken with concrete type using ParameterizedTypeImpl
                    val cacheEntryType = com.google.gson.internal.`$Gson$Types`.newParameterizedTypeWithOwner(
                        null,
                        CacheEntry::class.java,
                        clazz
                    )
                    val entry = gson.fromJson<CacheEntry<T>>(content, cacheEntryType)
                    
                    if (entry.isValid()) {
                        entry.data
                    } else {
                        // Delete expired file
                        file.delete()
                        null
                    }
                } catch (e: IOException) {
                    Timber.e(e, "$TAG: I/O error reading disk cache: $key")
                    // Try to delete corrupted file
                    try {
                        File(cacheDir, "$key.cache").delete()
                    } catch (deleteException: Exception) {
                        Timber.w(deleteException, "$TAG: Failed to delete corrupted cache file: $key")
                    }
                    null
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: Error reading disk cache: $key")
                    null
                }
            }
        }
    }
    
    internal suspend fun <T> putDiskCache(key: String, value: T, ttl: Duration, clazz: Class<T>) {
        withContext(ioDispatcher) {
            diskMutex.withLock {
                try {
                    // Ensure cache directory exists before writing
                    if (!cacheDir.ensureDirectoryExists()) {
                        Timber.e("$TAG: Cannot create cache directory: ${cacheDir.absolutePath}")
                        return@withLock
                    }
                    
                    val entry = CacheEntry(value, Clock.System.now(), ttl)
                    val json = gson.toJson(entry)
                    val file = File(cacheDir, "$key.cache")
                    
                    // Use atomic write with temporary file to prevent corruption
                    val tempFile = File(cacheDir, "$key.cache.tmp")
                    tempFile.writeText(json)
                    
                    // Atomic rename to final file
                    if (!tempFile.renameTo(file)) {
                        tempFile.delete() // Clean up temp file if rename failed
                        Timber.w("$TAG: Failed to rename temp file for key: $key")
                    }
                } catch (e: IOException) {
                    Timber.e(e, "$TAG: I/O error writing disk cache: $key")
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: Error writing disk cache: $key")
                }
            }
        }
    }
    
    private fun cleanExpiredDiskEntries() {
        try {
            val files = cacheDir.listFiles() ?: return
            var cleanedCount = 0
            
            for (file in files) {
                try {
                    val content = file.readText()
                    // For cleanup, we can use a raw type since we only need metadata
                    val cacheEntryType = com.google.gson.internal.`$Gson$Types`.newParameterizedTypeWithOwner(
                        null,
                        CacheEntry::class.java,
                        Any::class.java
                    )
                    val entry = gson.fromJson<CacheEntry<Any>>(content, cacheEntryType)
                    
                    if (entry.isExpired()) {
                        file.delete()
                        cleanedCount++
                    }
                } catch (e: Exception) {
                    // Corrupted file - delete it
                    file.delete()
                    cleanedCount++
                }
            }
            
            if (cleanedCount > 0) {
                Timber.d("$TAG: Cleaned $cleanedCount expired/corrupted disk cache entries")
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error during disk cache cleanup")
        }
    }
    
    private fun estimateObjectSize(obj: Any?): Int {
        // Simple estimation - in production would use more sophisticated measurement
        return when (obj) {
            is String -> obj.length * 2
            is List<*> -> obj.size * 100
            is Map<*, *> -> obj.size * 200
            else -> 500 // Default estimate
        }
    }
    
    private fun estimateDiskCacheSize(): Long {
        return try {
            cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * Ensures the cache directory exists with proper error handling.
     * 
     * @return true if directory exists or was created successfully, false otherwise
     */
    private fun File.ensureDirectoryExists(): Boolean {
        return try {
            when {
                exists() && isDirectory -> true
                exists() && isFile -> {
                    Timber.w("$TAG: Cache path exists but is a file, not directory: $absolutePath")
                    false
                }
                else -> {
                    val created = mkdirs()
                    if (!created) {
                        Timber.e("$TAG: Failed to create cache directory: $absolutePath")
                    }
                    created
                }
            }
        } catch (e: SecurityException) {
            Timber.e(e, "$TAG: Security exception creating cache directory: $absolutePath")
            false
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Exception creating cache directory: $absolutePath")
            false
        }
    }
}

/**
 * Configuration class for enhanced cache behavior.
 */
data class CacheConfiguration(
    val memoryCacheSizeMB: Int = 50,
    val diskCacheSizeMB: Int = 200,
    val defaultTTL: Duration = 15.minutes,
    val maxDiskCacheAge: Duration = 7.days,
    val cleanupIntervalHours: Int = 6
) {
    companion object {
        fun forPerformance() = CacheConfiguration(
            memoryCacheSizeMB = 100,
            diskCacheSizeMB = 500,
            defaultTTL = 30.minutes
        )
        
        fun forLowMemory() = CacheConfiguration(
            memoryCacheSizeMB = 25,
            diskCacheSizeMB = 100,
            defaultTTL = 10.minutes
        )
    }
}

/**
 * Inline extension function to provide reified type support for getOrCompute.
 * This allows calling without explicitly passing the Class parameter.
 */
suspend inline fun <reified T> EnhancedCacheManager.getOrComputeTyped(
    key: CacheKey,
    ttl: Duration = CacheConfiguration().defaultTTL,
    noinline compute: suspend () -> T
): T = getOrCompute(key, ttl, T::class.java, compute)

/**
 * Inline extension function to provide reified type support for put.
 * This allows calling without explicitly passing the Class parameter.
 */
suspend inline fun <reified T> EnhancedCacheManager.putTyped(
    key: CacheKey,
    value: T,
    ttl: Duration = CacheConfiguration().defaultTTL
) = put(key, value, T::class.java, ttl)

/**
 * Statistics for enhanced cache performance monitoring.
 */
data class EnhancedCacheStats(
    val memoryEntries: Int,
    val diskEntries: Int,
    val memoryHitRate: Double,
    val diskHitRate: Double,
    val overallHitRate: Double,
    val totalRequests: Int,
    val memorySize: Int,
    val diskSize: Long
) {
    fun isPerformanceTarget(): Boolean {
        return overallHitRate >= 0.8 // 80% hit rate target
    }
}