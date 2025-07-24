package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import com.example.liftrix.data.local.entity.UserSearchCacheEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for user search cache operations
 * 
 * Provides methods to store, retrieve, and manage cached search results
 * for improved social discovery performance. All operations are user-scoped for security.
 */
@Dao
interface UserSearchCacheDao {
    
    /**
     * Insert or update a cached search result
     * @param cache The search cache entry to store
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(cache: UserSearchCacheEntity)
    
    /**
     * Insert or update multiple cached search results
     * @param caches List of search cache entries to store
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(caches: List<UserSearchCacheEntity>)
    
    /**
     * Get cached search results for a specific query
     * @param viewerUserId User ID performing the search
     * @param searchQuery The search query
     * @return Cached search result or null if not found or expired
     */
    @Query("SELECT * FROM user_search_cache WHERE viewer_user_id = :viewerUserId AND search_query = :searchQuery AND expires_at > datetime('now') LIMIT 1")
    suspend fun getCachedSearchResult(viewerUserId: String, searchQuery: String): UserSearchCacheEntity?
    
    /**
     * Get cached search results for a specific query as Flow
     * @param viewerUserId User ID performing the search
     * @param searchQuery The search query
     * @return Flow of cached search result or null if not found or expired
     */
    @Query("SELECT * FROM user_search_cache WHERE viewer_user_id = :viewerUserId AND search_query = :searchQuery AND expires_at > datetime('now') LIMIT 1")
    fun getCachedSearchResultFlow(viewerUserId: String, searchQuery: String): Flow<UserSearchCacheEntity?>
    
    /**
     * Get all cached search results for a user (for debugging or management)
     * @param viewerUserId User ID to filter by
     * @return Flow of all cached search results for the user
     */
    @Query("SELECT * FROM user_search_cache WHERE viewer_user_id = :viewerUserId ORDER BY created_at DESC")
    fun getAllCachedResults(viewerUserId: String): Flow<List<UserSearchCacheEntity>>
    
    /**
     * Get all valid cached search results for a user (not expired)
     * @param viewerUserId User ID to filter by
     * @return Flow of all valid cached search results
     */
    @Query("SELECT * FROM user_search_cache WHERE viewer_user_id = :viewerUserId AND expires_at > datetime('now') ORDER BY created_at DESC")
    fun getAllValidCachedResults(viewerUserId: String): Flow<List<UserSearchCacheEntity>>
    
    /**
     * Delete a specific cached search result
     * @param cache The search cache entry to delete
     */
    @Delete
    suspend fun delete(cache: UserSearchCacheEntity)
    
    /**
     * Delete expired cache entries for all users
     * @return Number of deleted entries
     */
    @Query("DELETE FROM user_search_cache WHERE expires_at <= datetime('now')")
    suspend fun deleteExpiredEntries(): Int
    
    /**
     * Delete expired cache entries for a specific user
     * @param viewerUserId User ID to filter by
     * @return Number of deleted entries
     */
    @Query("DELETE FROM user_search_cache WHERE viewer_user_id = :viewerUserId AND expires_at <= datetime('now')")
    suspend fun deleteExpiredEntriesForUser(viewerUserId: String): Int
    
    /**
     * Delete all cached search results for a specific user
     * @param viewerUserId User ID to filter by
     * @return Number of deleted entries
     */
    @Query("DELETE FROM user_search_cache WHERE viewer_user_id = :viewerUserId")
    suspend fun deleteAllForUser(viewerUserId: String): Int
    
    /**
     * Delete cached search results older than specified timestamp
     * @param viewerUserId User ID to filter by
     * @param timestamp Timestamp threshold (older entries will be deleted)
     * @return Number of deleted entries
     */
    @Query("DELETE FROM user_search_cache WHERE viewer_user_id = :viewerUserId AND created_at < :timestamp")
    suspend fun deleteOlderThan(viewerUserId: String, timestamp: String): Int
    
    /**
     * Get count of cached entries for a user
     * @param viewerUserId User ID to filter by
     * @return Flow of count of cached entries
     */
    @Query("SELECT COUNT(*) FROM user_search_cache WHERE viewer_user_id = :viewerUserId")
    fun getCacheCount(viewerUserId: String): Flow<Int>
    
    /**
     * Get count of valid (non-expired) cached entries for a user
     * @param viewerUserId User ID to filter by
     * @return Flow of count of valid cached entries
     */
    @Query("SELECT COUNT(*) FROM user_search_cache WHERE viewer_user_id = :viewerUserId AND expires_at > datetime('now')")
    fun getValidCacheCount(viewerUserId: String): Flow<Int>
    
    /**
     * Check if a valid cached search result exists for a query
     * @param viewerUserId User ID performing the search
     * @param searchQuery The search query
     * @return True if valid cache exists, false otherwise
     */
    @Query("SELECT COUNT(*) > 0 FROM user_search_cache WHERE viewer_user_id = :viewerUserId AND search_query = :searchQuery AND expires_at > datetime('now')")
    suspend fun hasValidCache(viewerUserId: String, searchQuery: String): Boolean
    
    /**
     * Clean up cache by keeping only the most recent N entries per user
     * @param viewerUserId User ID to filter by
     * @param maxEntries Maximum number of entries to keep
     * @return Number of deleted entries
     */
    @Query("""
        DELETE FROM user_search_cache 
        WHERE viewer_user_id = :viewerUserId 
        AND id NOT IN (
            SELECT id FROM user_search_cache 
            WHERE viewer_user_id = :viewerUserId 
            ORDER BY created_at DESC 
            LIMIT :maxEntries
        )
    """)
    suspend fun cleanupOldEntries(viewerUserId: String, maxEntries: Int): Int
}