package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.ExternalShareEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data class for platform share statistics.
 * Used by Room to handle GROUP BY queries with Map return types.
 */
data class PlatformShareStats(
    val platform: String,
    val count: Int
)

/**
 * Data class for content type share statistics.
 * Used by Room to handle GROUP BY queries with Map return types.
 */
data class ContentTypeShareStats(
    val contentType: String,
    val count: Int
)

/**
 * DAO for external platform shares tracking.
 * All queries MUST include userId filtering to prevent data leakage.
 * Part of content sharing and media management system from SPEC-20250113-content-sharing-media.
 */
@Dao
interface ExternalShareDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShare(share: ExternalShareEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShares(shares: List<ExternalShareEntity>)
    
    @Update
    suspend fun updateShare(share: ExternalShareEntity)
    
    @Delete
    suspend fun deleteShare(share: ExternalShareEntity)
    
    @Query("DELETE FROM external_shares WHERE id = :shareId AND user_id = :userId")
    suspend fun deleteShareById(shareId: String, userId: String)
    
    @Query("SELECT * FROM external_shares WHERE id = :shareId AND user_id = :userId")
    suspend fun getShareById(shareId: String, userId: String): ExternalShareEntity?
    
    @Query("SELECT * FROM external_shares WHERE user_id = :userId ORDER BY shared_at DESC")
    fun getUserShares(userId: String): Flow<List<ExternalShareEntity>>
    
    @Query("""
        SELECT * FROM external_shares 
        WHERE user_id = :userId 
        AND platform = :platform 
        ORDER BY shared_at DESC
    """)
    fun getUserSharesByPlatform(userId: String, platform: String): Flow<List<ExternalShareEntity>>
    
    @Query("""
        SELECT * FROM external_shares 
        WHERE user_id = :userId 
        AND content_type = :contentType 
        ORDER BY shared_at DESC
    """)
    fun getUserSharesByContentType(userId: String, contentType: String): Flow<List<ExternalShareEntity>>
    
    @Query("""
        SELECT * FROM external_shares 
        WHERE user_id = :userId 
        AND content_id = :contentId 
        ORDER BY shared_at DESC
    """)
    suspend fun getSharesForContent(userId: String, contentId: String): List<ExternalShareEntity>
    
    @Query("""
        SELECT COUNT(*) FROM external_shares 
        WHERE user_id = :userId 
        AND platform = :platform 
        AND shared_at >= :fromTime
    """)
    suspend fun getShareCountByPlatform(userId: String, platform: String, fromTime: Long): Int
    
    @Query("""
        SELECT COUNT(*) FROM external_shares 
        WHERE user_id = :userId 
        AND content_type = :contentType 
        AND shared_at >= :fromTime
    """)
    suspend fun getShareCountByContentType(userId: String, contentType: String, fromTime: Long): Int
    
    @Query("""
        SELECT platform, COUNT(*) as count
        FROM external_shares 
        WHERE user_id = :userId 
        AND shared_at >= :fromTime
        GROUP BY platform
        ORDER BY count DESC
    """)
    suspend fun getPlatformShareStats(userId: String, fromTime: Long): Map<@MapColumn(columnName = "platform") String, @MapColumn(columnName = "count") Int>
    
    @Query("""
        SELECT content_type, COUNT(*) as count
        FROM external_shares 
        WHERE user_id = :userId 
        AND shared_at >= :fromTime
        GROUP BY content_type
        ORDER BY count DESC
    """)
    suspend fun getContentTypeShareStats(userId: String, fromTime: Long): Map<@MapColumn(columnName = "content_type") String, @MapColumn(columnName = "count") Int>
    
    @Query("""
        SELECT * FROM external_shares 
        WHERE user_id = :userId 
        AND shared_at >= :fromTime 
        AND shared_at <= :toTime
        ORDER BY shared_at DESC
    """)
    suspend fun getSharesInTimeRange(userId: String, fromTime: Long, toTime: Long): List<ExternalShareEntity>
    
    @Query("SELECT COUNT(*) FROM external_shares WHERE user_id = :userId")
    suspend fun getUserTotalShareCount(userId: String): Int
    
    @Query("DELETE FROM external_shares WHERE user_id = :userId")
    suspend fun deleteAllUserShares(userId: String)
    
    @Query("""
        DELETE FROM external_shares 
        WHERE user_id = :userId 
        AND shared_at < :cutoffTime
    """)
    suspend fun deleteOldShares(userId: String, cutoffTime: Long)
    
    @Query("""
        SELECT * FROM external_shares 
        WHERE user_id = :userId 
        ORDER BY shared_at DESC 
        LIMIT :limit
    """)
    suspend fun getRecentShares(userId: String, limit: Int = 10): List<ExternalShareEntity>
}