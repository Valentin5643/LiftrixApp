package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.liftrix.data.local.entity.FeedCacheEntity

/**
 * DAO for feed cache to optimize feed performance.
 * All queries MUST include userId filtering to prevent data leakage.
 * Part of social feed and engagement system from SPEC-20250113-social-feed-engagement.
 */
@Dao
interface FeedCacheDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCacheEntry(entry: FeedCacheEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCacheEntries(entries: List<FeedCacheEntity>)
    
    @Query("""
        SELECT post_id FROM feed_cache 
        WHERE user_id = :userId 
        ORDER BY score DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getCachedPostIds(userId: String, limit: Int, offset: Int): List<String>
    
    @Query("""
        SELECT * FROM feed_cache 
        WHERE user_id = :userId 
        ORDER BY score DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getCachedFeedEntries(userId: String, limit: Int, offset: Int): List<FeedCacheEntity>
    
    @Query("SELECT COUNT(*) FROM feed_cache WHERE user_id = :userId")
    suspend fun getCacheSize(userId: String): Int
    
    @Query("DELETE FROM feed_cache WHERE user_id = :userId")
    suspend fun clearUserCache(userId: String)
    
    @Query("DELETE FROM feed_cache WHERE user_id = :userId AND post_id = :postId")
    suspend fun removeCacheEntry(userId: String, postId: String)
    
    @Query("""
        DELETE FROM feed_cache 
        WHERE user_id = :userId 
        AND fetched_at < :olderThan
    """)
    suspend fun removeOldCacheEntries(userId: String, olderThan: Long)
    
    @Query("""
        SELECT AVG(score) FROM feed_cache 
        WHERE user_id = :userId
    """)
    suspend fun getAverageScore(userId: String): Double?
    
    @Query("""
        UPDATE feed_cache 
        SET score = :score, fetched_at = :fetchedAt
        WHERE user_id = :userId AND post_id = :postId
    """)
    suspend fun updateCacheEntry(userId: String, postId: String, score: Double, fetchedAt: Long)
    
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM feed_cache 
            WHERE user_id = :userId AND post_id = :postId
        )
    """)
    suspend fun isCached(userId: String, postId: String): Boolean
    
    @Query("""
        DELETE FROM feed_cache 
        WHERE user_id = :userId 
        AND post_id NOT IN (
            SELECT id FROM workout_posts 
            WHERE visibility IN ('PUBLIC', 'FOLLOWERS')
        )
    """)
    suspend fun removeInvalidCacheEntries(userId: String)
    
    @Query("""
        SELECT post_id FROM feed_cache 
        WHERE user_id = :userId 
        AND feed_type = :feedType
        ORDER BY score DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getCachedPostIdsByType(userId: String, feedType: String, limit: Int, offset: Int): List<String>
    
    @Query("""
        SELECT * FROM feed_cache 
        WHERE user_id = :userId 
        AND feed_type = :feedType
        ORDER BY score DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getCachedFeedEntriesByType(userId: String, feedType: String, limit: Int, offset: Int): List<FeedCacheEntity>
}