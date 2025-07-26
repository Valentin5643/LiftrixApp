package com.example.liftrix.service

import com.example.liftrix.data.local.dao.UserSearchCacheDao
import com.example.liftrix.data.local.entity.UserSearchCacheEntity
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.UserSearchResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing user search result caching with TTL and memory management
 * 
 * Provides in-memory and persistent caching for user search results with automatic
 * cleanup, expiration handling, and memory management. Optimizes search performance
 * by reducing Firebase queries and providing instant results for repeated searches.
 * 
 * Key Features:
 * - Two-tier caching: In-memory (fast) + Database (persistent)
 * - Automatic TTL-based expiration
 * - Memory management with size limits
 * - Thread-safe operations with proper synchronization
 * - Cache warming and preloading capabilities
 * - Analytics for cache hit rates and performance monitoring
 */
@Singleton
class UserSearchCache @Inject constructor(
    private val userSearchCacheDao: UserSearchCacheDao,
    private val gson: Gson
) {
    
    // In-memory cache for fastest access
    private val memoryCache = ConcurrentHashMap<String, CacheEntry>()
    private val cacheMutex = Mutex()
    
    companion object {
        private const val MEMORY_CACHE_MAX_SIZE = 100
        private const val CACHE_TTL_MINUTES = 30L
        private const val CLEANUP_THRESHOLD = 50 // Cleanup when cache exceeds this percentage
        private const val MAX_CACHE_KEY_LENGTH = 200
    }
    
    /**
     * Retrieves cached search results for a query
     * 
     * @param viewerId ID of user performing the search
     * @param query Search query string
     * @return LiftrixResult containing cached results or null if not found/expired
     */
    suspend fun getCachedResults(
        viewerId: String,
        query: String
    ): LiftrixResult<List<UserSearchResult>?> {
        return liftrixCatching(
            errorMapper = { exception ->
                when (exception) {
                    is LiftrixError -> exception
                    else -> LiftrixError.CacheError(
                        errorMessage = "Cache operation failed: ${exception.message}",
                        operation = "getCachedResults"
                    )
                }
            }
        ) {
            val cacheKey = createCacheKey(viewerId, query)
            Timber.v("Getting cached results for key: $cacheKey")
            
            // Check memory cache first
            memoryCache[cacheKey]?.let { entry ->
                if (!entry.isExpired()) {
                    Timber.d("Memory cache hit for query: $query")
                    return@liftrixCatching entry.results
                } else {
                    // Remove expired entry from memory
                    memoryCache.remove(cacheKey)
                }
            }
            
            // Check database cache
            val cachedEntity = userSearchCacheDao.getCachedSearchResult(viewerId, query)
            if (cachedEntity != null) {
                try {
                    val type = object : TypeToken<List<UserSearchResult>>() {}.type
                    val results: List<UserSearchResult> = gson.fromJson(cachedEntity.searchResults, type)
                    
                    // Store in memory cache for faster future access
                    val entry = CacheEntry(
                        results = results,
                        createdAt = LocalDateTime.now(),
                        ttlMinutes = CACHE_TTL_MINUTES
                    )
                    
                    cacheMutex.withLock {
                        memoryCache[cacheKey] = entry
                        manageCacheSize()
                    }
                    
                    Timber.d("Database cache hit for query: $query")
                    return@liftrixCatching results
                } catch (e: Exception) {
                    Timber.w(e, "Failed to deserialize cached results for query: $query")
                    // Clean up corrupted cache entry
                    userSearchCacheDao.deleteExpiredEntriesForUser(viewerId)
                }
            }
            
            Timber.v("Cache miss for query: $query")
            null
        }
    }
    
    /**
     * Caches search results with TTL
     * 
     * @param viewerId ID of user performing the search
     * @param query Search query string
     * @param results Search results to cache
     * @return LiftrixResult indicating success or failure
     */
    suspend fun cacheResults(
        viewerId: String,
        query: String,
        results: List<UserSearchResult>
    ): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { exception ->
                when (exception) {
                    is LiftrixError -> exception
                    else -> LiftrixError.CacheError(
                        errorMessage = "Cache operation failed: ${exception.message}",
                        operation = "getCachedResults"
                    )
                }
            }
        ) {
            if (results.isEmpty()) {
                Timber.v("Skipping cache for empty results: $query")
                return@liftrixCatching
            }
            
            val cacheKey = createCacheKey(viewerId, query)
            val now = LocalDateTime.now()
            val expiresAt = now.plusMinutes(CACHE_TTL_MINUTES)
            
            Timber.d("Caching ${results.size} results for query: $query")
            
            // Store in memory cache
            val memoryEntry = CacheEntry(
                results = results,
                createdAt = now,
                ttlMinutes = CACHE_TTL_MINUTES
            )
            
            cacheMutex.withLock {
                memoryCache[cacheKey] = memoryEntry
                manageCacheSize()
            }
            
            // Store in database cache
            val cacheEntity = UserSearchCacheEntity(
                id = UUID.randomUUID().toString(),
                viewerUserId = viewerId,
                searchQuery = query,
                searchResults = gson.toJson(results),
                createdAt = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                expiresAt = expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
            
            userSearchCacheDao.insertOrUpdate(cacheEntity)
            
            Timber.v("Results cached successfully for query: $query")
        }
    }
    
    /**
     * Clears all cached results for a user
     * 
     * @param viewerId ID of user whose cache to clear
     * @return LiftrixResult indicating success or failure
     */
    suspend fun clearUserCache(viewerId: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { exception ->
                when (exception) {
                    is LiftrixError -> exception
                    else -> LiftrixError.CacheError(
                        errorMessage = "Cache operation failed: ${exception.message}",
                        operation = "getCachedResults"
                    )
                }
            }
        ) {
            Timber.d("Clearing cache for user: $viewerId")
            
            // Clear memory cache entries for this user
            cacheMutex.withLock {
                val keysToRemove = memoryCache.keys.filter { key ->
                    key.startsWith("${viewerId}:")
                }
                keysToRemove.forEach { key ->
                    memoryCache.remove(key)
                }
            }
            
            // Clear database cache
            val deletedCount = userSearchCacheDao.deleteAllForUser(viewerId)
            
            Timber.d("Cache cleared for user: $viewerId ($deletedCount entries deleted)")
        }
    }
    
    /**
     * Performs cache cleanup by removing expired entries
     * 
     * @return LiftrixResult containing cleanup statistics
     */
    suspend fun performCleanup(): LiftrixResult<CacheCleanupResult> {
        return liftrixCatching(
            errorMapper = { exception ->
                when (exception) {
                    is LiftrixError -> exception
                    else -> LiftrixError.CacheError(
                        errorMessage = "Cache operation failed: ${exception.message}",
                        operation = "getCachedResults"
                    )
                }
            }
        ) {
            Timber.d("Performing cache cleanup")
            
            var memoryEntriesRemoved = 0
            var databaseEntriesRemoved = 0
            
            // Clean memory cache
            cacheMutex.withLock {
                val expiredKeys = memoryCache.entries
                    .filter { (_, entry) -> entry.isExpired() }
                    .map { (key, _) -> key }
                
                expiredKeys.forEach { key ->
                    memoryCache.remove(key)
                    memoryEntriesRemoved++
                }
            }
            
            // Clean database cache
            databaseEntriesRemoved = userSearchCacheDao.deleteExpiredEntries()
            
            val result = CacheCleanupResult(
                memoryEntriesRemoved = memoryEntriesRemoved,
                databaseEntriesRemoved = databaseEntriesRemoved,
                totalEntriesRemoved = memoryEntriesRemoved + databaseEntriesRemoved
            )
            
            Timber.d("Cache cleanup completed: $result")
            result
        }
    }
    
    /**
     * Gets cache statistics for monitoring and analytics
     * 
     * @return LiftrixResult containing cache statistics
     */
    suspend fun getCacheStatistics(): LiftrixResult<CacheStatistics> {
        return liftrixCatching(
            errorMapper = { exception ->
                when (exception) {
                    is LiftrixError -> exception
                    else -> LiftrixError.CacheError(
                        errorMessage = "Cache operation failed: ${exception.message}",
                        operation = "getCachedResults"
                    )
                }
            }
        ) {
            val memorySize = memoryCache.size
            val memoryExpiredCount = memoryCache.values.count { it.isExpired() }
            
            // Get database statistics
            val totalDatabaseEntries = userSearchCacheDao.getCacheCount("").first() // Get total count
            val validDatabaseEntries = userSearchCacheDao.getValidCacheCount("").first()
            
            CacheStatistics(
                memoryCacheSize = memorySize,
                memoryExpiredEntries = memoryExpiredCount,
                databaseCacheSize = totalDatabaseEntries,
                databaseValidEntries = validDatabaseEntries,
                memoryHitRate = calculateHitRate(),
                lastCleanupTime = getLastCleanupTime()
            )
        }
    }
    
    /**
     * Preloads cache with common search queries for a user
     * 
     * @param viewerId ID of user to preload cache for
     * @param commonQueries List of common search queries to preload
     * @return LiftrixResult indicating success or failure
     */
    suspend fun preloadCache(
        viewerId: String,
        commonQueries: List<String>
    ): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { exception ->
                when (exception) {
                    is LiftrixError -> exception
                    else -> LiftrixError.CacheError(
                        errorMessage = "Cache operation failed: ${exception.message}",
                        operation = "getCachedResults"
                    )
                }
            }
        ) {
            Timber.d("Preloading cache for user: $viewerId (${commonQueries.size} queries)")
            
            for (query in commonQueries) {
                // Check if already cached
                val cachedResult = getCachedResults(viewerId, query)
                if (cachedResult.isSuccess && cachedResult.getOrNull() != null) {
                    continue // Already cached
                }
                
                // This would typically trigger a search to populate the cache
                // For now, we just log the preload attempt
                Timber.v("Cache preload needed for query: $query")
            }
            
            Timber.d("Cache preload completed for user: $viewerId")
        }
    }
    
    /**
     * Creates a cache key from viewer ID and query
     */
    private fun createCacheKey(viewerId: String, query: String): String {
        val key = "$viewerId:${query.lowercase().trim()}"
        return if (key.length > MAX_CACHE_KEY_LENGTH) {
            key.take(MAX_CACHE_KEY_LENGTH)
        } else {
            key
        }
    }
    
    /**
     * Manages memory cache size by removing oldest entries when limit is exceeded
     */
    private fun manageCacheSize() {
        if (memoryCache.size <= MEMORY_CACHE_MAX_SIZE) {
            return
        }
        
        // Remove oldest entries (simple LRU-like behavior)
        val entriesToRemove = memoryCache.size - (MEMORY_CACHE_MAX_SIZE * CLEANUP_THRESHOLD / 100)
        val oldestEntries = memoryCache.entries
            .sortedBy { (_, entry) -> entry.createdAt }
            .take(entriesToRemove)
        
        oldestEntries.forEach { (key, _) ->
            memoryCache.remove(key)
        }
        
        Timber.v("Memory cache size managed: removed $entriesToRemove entries")
    }
    
    /**
     * Calculates cache hit rate for performance monitoring
     */
    private fun calculateHitRate(): Float {
        // This would typically track hits vs misses over time
        // For now, return a placeholder value
        return 0.0f
    }
    
    /**
     * Gets the last cleanup time
     */
    private fun getLastCleanupTime(): LocalDateTime? {
        // This would typically track cleanup times
        // For now, return null
        return null
    }
    
    /**
     * Represents a cache entry with TTL
     */
    private data class CacheEntry(
        val results: List<UserSearchResult>,
        val createdAt: LocalDateTime,
        val ttlMinutes: Long
    ) {
        fun isExpired(): Boolean {
            val expiresAt = createdAt.plusMinutes(ttlMinutes)
            return LocalDateTime.now().isAfter(expiresAt)
        }
    }
}

/**
 * Result data class for cache cleanup operations
 */
data class CacheCleanupResult(
    val memoryEntriesRemoved: Int,
    val databaseEntriesRemoved: Int,
    val totalEntriesRemoved: Int
)

/**
 * Statistics data class for cache monitoring
 */
data class CacheStatistics(
    val memoryCacheSize: Int,
    val memoryExpiredEntries: Int,
    val databaseCacheSize: Int,
    val databaseValidEntries: Int,
    val memoryHitRate: Float,
    val lastCleanupTime: LocalDateTime?
)