package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.ProgressPhotoEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for progress photos organization and management.
 * All queries MUST include userId filtering to prevent data leakage.
 * Part of content sharing and media management system from SPEC-20250113-content-sharing-media.
 */
@Dao
interface ProgressPhotoDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgressPhoto(photo: ProgressPhotoEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgressPhotos(photos: List<ProgressPhotoEntity>)
    
    @Update
    suspend fun updateProgressPhoto(photo: ProgressPhotoEntity)
    
    @Delete
    suspend fun deleteProgressPhoto(photo: ProgressPhotoEntity)
    
    @Query("DELETE FROM progress_photos WHERE id = :photoId AND user_id = :userId")
    suspend fun deleteProgressPhotoById(photoId: String, userId: String)
    
    @Query("SELECT * FROM progress_photos WHERE id = :photoId AND user_id = :userId")
    suspend fun getProgressPhotoById(photoId: String, userId: String): ProgressPhotoEntity?
    
    @Query("SELECT * FROM progress_photos WHERE user_id = :userId ORDER BY taken_at DESC")
    fun getUserProgressPhotos(userId: String): Flow<List<ProgressPhotoEntity>>
    
    @Query("""
        SELECT * FROM progress_photos 
        WHERE user_id = :userId 
        AND body_part = :bodyPart 
        ORDER BY taken_at DESC
    """)
    fun getProgressPhotosByBodyPart(userId: String, bodyPart: String): Flow<List<ProgressPhotoEntity>>
    
    @Query("""
        SELECT * FROM progress_photos 
        WHERE user_id = :userId 
        AND photo_type = :photoType 
        ORDER BY taken_at DESC
    """)
    fun getProgressPhotosByType(userId: String, photoType: String): Flow<List<ProgressPhotoEntity>>
    
    @Query("""
        SELECT * FROM progress_photos 
        WHERE user_id = :userId 
        AND body_part = :bodyPart 
        AND photo_type = :photoType 
        ORDER BY taken_at DESC
    """)
    fun getProgressPhotosByCategory(
        userId: String, 
        bodyPart: String, 
        photoType: String
    ): Flow<List<ProgressPhotoEntity>>
    
    @Query("""
        SELECT * FROM progress_photos 
        WHERE comparison_group_id = :groupId 
        AND user_id = :userId
        ORDER BY is_before DESC, taken_at ASC
    """)
    suspend fun getComparisonPhotos(groupId: String, userId: String): List<ProgressPhotoEntity>
    
    @Query("""
        SELECT * FROM progress_photos 
        WHERE user_id = :userId 
        AND is_private = 0 
        ORDER BY taken_at DESC
    """)
    fun getPublicProgressPhotos(userId: String): Flow<List<ProgressPhotoEntity>>
    
    @Query("""
        SELECT * FROM progress_photos 
        WHERE user_id = :userId 
        AND taken_at >= :fromTime 
        AND taken_at <= :toTime
        ORDER BY taken_at DESC
    """)
    suspend fun getProgressPhotosInTimeRange(
        userId: String, 
        fromTime: Long, 
        toTime: Long
    ): List<ProgressPhotoEntity>
    
    @Query("""
        SELECT * FROM progress_photos 
        WHERE user_id = :userId 
        AND weight_kg IS NOT NULL 
        ORDER BY taken_at DESC
    """)
    suspend fun getProgressPhotosWithWeight(userId: String): List<ProgressPhotoEntity>
    
    @Query("""
        SELECT * FROM progress_photos 
        WHERE user_id = :userId 
        AND body_fat_percent IS NOT NULL 
        ORDER BY taken_at DESC
    """)
    suspend fun getProgressPhotosWithBodyFat(userId: String): List<ProgressPhotoEntity>
    
    @Query("""
        UPDATE progress_photos 
        SET weight_kg = :weightKg, body_fat_percent = :bodyFatPercent
        WHERE id = :photoId AND user_id = :userId
    """)
    suspend fun updateMeasurements(
        photoId: String, 
        userId: String, 
        weightKg: Float?, 
        bodyFatPercent: Float?
    )
    
    @Query("""
        UPDATE progress_photos 
        SET is_private = :isPrivate
        WHERE id = :photoId AND user_id = :userId
    """)
    suspend fun updatePrivacyStatus(photoId: String, userId: String, isPrivate: Boolean)
    
    @Query("""
        UPDATE progress_photos 
        SET comparison_group_id = :groupId, is_before = :isBefore
        WHERE id = :photoId AND user_id = :userId
    """)
    suspend fun updateComparisonInfo(
        photoId: String, 
        userId: String, 
        groupId: String?, 
        isBefore: Boolean?
    )
    
    @Query("SELECT DISTINCT body_part FROM progress_photos WHERE user_id = :userId AND body_part IS NOT NULL")
    suspend fun getUserBodyParts(userId: String): List<String>
    
    @Query("SELECT DISTINCT photo_type FROM progress_photos WHERE user_id = :userId AND photo_type IS NOT NULL")
    suspend fun getUserPhotoTypes(userId: String): List<String>
    
    @Query("SELECT COUNT(*) FROM progress_photos WHERE user_id = :userId")
    suspend fun getUserProgressPhotoCount(userId: String): Int
    
    @Query("DELETE FROM progress_photos WHERE user_id = :userId")
    suspend fun deleteAllUserProgressPhotos(userId: String)
    
    @Query("""
        SELECT * FROM progress_photos 
        WHERE user_id = :userId 
        AND is_synced = 0
        ORDER BY created_at ASC
    """)
    suspend fun getUnsyncedProgressPhotos(userId: String): List<ProgressPhotoEntity>
    
    @Query("""
        UPDATE progress_photos 
        SET is_synced = 1, sync_version = :syncVersion
        WHERE id = :photoId
    """)
    suspend fun markAsSynced(photoId: String, syncVersion: Int)
    
    @Query("""
        SELECT * FROM progress_photos 
        WHERE user_id = :userId 
        ORDER BY taken_at DESC 
        LIMIT :limit
    """)
    suspend fun getRecentProgressPhotos(userId: String, limit: Int = 10): List<ProgressPhotoEntity>
}