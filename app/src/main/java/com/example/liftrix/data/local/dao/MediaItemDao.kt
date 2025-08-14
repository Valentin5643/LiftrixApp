package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.MediaItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for media items (photos/videos).
 * All queries MUST include userId filtering to prevent data leakage.
 * Part of content sharing and media management system from SPEC-20250113-content-sharing-media.
 */
@Dao
interface MediaItemDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: MediaItemEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaItems(mediaItems: List<MediaItemEntity>)
    
    @Update
    suspend fun updateMedia(media: MediaItemEntity)
    
    @Delete
    suspend fun deleteMedia(media: MediaItemEntity)
    
    @Query("DELETE FROM media_items WHERE id = :mediaId AND user_id = :userId")
    suspend fun deleteMediaById(mediaId: String, userId: String)
    
    @Query("SELECT * FROM media_items WHERE id = :mediaId AND user_id = :userId")
    suspend fun getMediaById(mediaId: String, userId: String): MediaItemEntity?
    
    @Query("SELECT * FROM media_items WHERE user_id = :userId ORDER BY created_at DESC")
    fun getUserMediaItems(userId: String): Flow<List<MediaItemEntity>>
    
    @Query("""
        SELECT * FROM media_items 
        WHERE user_id = :userId 
        AND type = :type 
        ORDER BY created_at DESC
    """)
    fun getUserMediaByType(userId: String, type: String): Flow<List<MediaItemEntity>>
    
    @Query("""
        SELECT * FROM media_items 
        WHERE post_id = :postId 
        AND user_id = :userId 
        ORDER BY created_at ASC
    """)
    suspend fun getPostMediaItems(postId: String, userId: String): List<MediaItemEntity>
    
    @Query("""
        SELECT * FROM media_items 
        WHERE user_id = :userId 
        AND processing_status = :status 
        ORDER BY created_at DESC
    """)
    suspend fun getMediaByProcessingStatus(userId: String, status: String): List<MediaItemEntity>
    
    @Query("""
        UPDATE media_items 
        SET processing_status = :status, 
            processed_at = :processedAt,
            cdn_url = :cdnUrl,
            thumbnail_url = :thumbnailUrl,
            compression_ratio = :compressionRatio
        WHERE id = :mediaId AND user_id = :userId
    """)
    suspend fun updateProcessingStatus(
        mediaId: String,
        userId: String,
        status: String,
        processedAt: Long? = null,
        cdnUrl: String? = null,
        thumbnailUrl: String? = null,
        compressionRatio: Float? = null
    )
    
    @Query("""
        SELECT * FROM media_items 
        WHERE user_id = :userId 
        AND expires_at IS NOT NULL 
        AND expires_at < :currentTime
    """)
    suspend fun getExpiredMedia(userId: String, currentTime: Long): List<MediaItemEntity>
    
    @Query("DELETE FROM media_items WHERE expires_at IS NOT NULL AND expires_at < :currentTime")
    suspend fun deleteExpiredMedia(currentTime: Long)
    
    @Query("SELECT COUNT(*) FROM media_items WHERE user_id = :userId")
    suspend fun getUserMediaCount(userId: String): Int
    
    @Query("SELECT SUM(size_bytes) FROM media_items WHERE user_id = :userId")
    suspend fun getUserMediaStorageUsed(userId: String): Long?
    
    @Query("DELETE FROM media_items WHERE user_id = :userId")
    suspend fun deleteAllUserMedia(userId: String)
    
    @Query("""
        SELECT * FROM media_items 
        WHERE user_id = :userId 
        AND is_synced = 0
        ORDER BY created_at ASC
    """)
    suspend fun getUnsyncedMedia(userId: String): List<MediaItemEntity>
    
    @Query("""
        UPDATE media_items 
        SET is_synced = 1, sync_version = :syncVersion
        WHERE id = :mediaId
    """)
    suspend fun markAsSynced(mediaId: String, syncVersion: Int)
    
    @Query("""
        SELECT * FROM media_items 
        WHERE user_id = :userId 
        AND is_public = 1 
        ORDER BY created_at DESC 
        LIMIT :limit
    """)
    suspend fun getPublicMediaItems(userId: String, limit: Int = 20): List<MediaItemEntity>
}