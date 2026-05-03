package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import com.example.liftrix.data.local.entity.AnalyticsCacheEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for analytics cache operations
 * 
 * Provides methods to store, retrieve, and manage cached analytics calculations
 * for improved dashboard performance. All operations are user-scoped for security.
 */
@Dao
interface AnalyticsCacheDao {
    
    /**
     * Insert or update a cached analytics calculation
     * @param cache The analytics cache entry to store
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(cache: AnalyticsCacheEntity)
    
    /**
     * Insert or update multiple cached analytics calculations
     * @param caches List of analytics cache entries to store
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(caches: List<AnalyticsCacheEntity>)
    
    /**
     * Get a specific cached calculation by user and type
     * @param userId User ID to filter by
     * @param calculationType Type of calculation to retrieve
     * @return Flow of cached result or null if not found
     */
    @Query("SELECT * FROM analytics_cache WHERE user_id = :userId AND calculation_type = :calculationType LIMIT 1")
    fun getCachedResult(userId: String, calculationType: String): Flow<AnalyticsCacheEntity?>
    
    /**
     * Get all cached calculations for a user
     * @param userId User ID to filter by
     * @return Flow of all cached results for the user
     */
    @Query("SELECT * FROM analytics_cache WHERE user_id = :userId ORDER BY timestamp DESC")
    fun getAllCachedResults(userId: String): Flow<List<AnalyticsCacheEntity>>
    
    /**
     * Get cached results by type for a user
     * @param userId User ID to filter by
     * @param calculationType Type of calculation to retrieve
     * @return Flow of cached results for the specific type
     */
    @Query("SELECT * FROM analytics_cache WHERE user_id = :userId AND calculation_type = :calculationType ORDER BY timestamp DESC")
    fun getCachedResultsByType(userId: String, calculationType: String): Flow<List<AnalyticsCacheEntity>>
    
    /**
     * Delete a specific cached calculation
     * @param cache The analytics cache entry to delete
     */
    @Delete
    suspend fun delete(cache: AnalyticsCacheEntity)
    
    /**
     * Delete cached calculations older than specified timestamp
     * @param userId User ID to filter by
     * @param timestamp Timestamp threshold (older entries will be deleted)
     * @return Number of deleted entries
     */
    @Query("DELETE FROM analytics_cache WHERE user_id = :userId AND timestamp < :timestamp")
    suspend fun deleteOlderThan(userId: String, timestamp: Long): Int
    
    /**
     * Delete all cached calculations for a specific user and type
     * @param userId User ID to filter by
     * @param calculationType Type of calculation to delete
     * @return Number of deleted entries
     */
    @Query("DELETE FROM analytics_cache WHERE user_id = :userId AND calculation_type = :calculationType")
    suspend fun deleteByType(userId: String, calculationType: String): Int
    
    /**
     * Delete all cached calculations for a user
     * @param userId User ID to filter by
     * @return Number of deleted entries
     */
    @Query("DELETE FROM analytics_cache WHERE user_id = :userId")
    suspend fun deleteAllForUser(userId: String): Int
    
    /**
     * Get count of cached entries for a user
     * @param userId User ID to filter by
     * @return Flow of count of cached entries
     */
    @Query("SELECT COUNT(*) FROM analytics_cache WHERE user_id = :userId")
    fun getCacheCount(userId: String): Flow<Int>
    
    /**
     * Check if a cached calculation exists and is newer than threshold
     * @param userId User ID to filter by
     * @param calculationType Type of calculation to check
     * @param timestamp Minimum timestamp for valid cache
     * @return True if valid cache exists, false otherwise
     */
    @Query("SELECT COUNT(*) > 0 FROM analytics_cache WHERE user_id = :userId AND calculation_type = :calculationType AND timestamp >= :timestamp")
    suspend fun hasValidCache(userId: String, calculationType: String, timestamp: Long): Boolean
}