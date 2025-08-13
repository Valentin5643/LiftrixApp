package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.liftrix.data.local.entity.PostLikeEntity
import com.example.liftrix.data.local.dto.PostLikeWithProfile
import kotlinx.coroutines.flow.Flow

/**
 * DAO for post likes in the social feed.
 * All queries MUST include userId filtering to prevent data leakage.
 * Part of social feed and engagement system from SPEC-20250113-social-feed-engagement.
 */
@Dao
interface PostLikeDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLike(like: PostLikeEntity)
    
    @Delete
    suspend fun deleteLike(like: PostLikeEntity)
    
    @Query("DELETE FROM post_likes WHERE post_id = :postId AND user_id = :userId")
    suspend fun deleteLikeByPostAndUser(postId: String, userId: String)
    
    @Query("SELECT * FROM post_likes WHERE post_id = :postId AND user_id = :userId")
    suspend fun getLikeByPostAndUser(postId: String, userId: String): PostLikeEntity?
    
    @Query("SELECT EXISTS(SELECT 1 FROM post_likes WHERE post_id = :postId AND user_id = :userId)")
    suspend fun isPostLikedByUser(postId: String, userId: String): Boolean
    
    @Query("SELECT EXISTS(SELECT 1 FROM post_likes WHERE post_id = :postId AND user_id = :userId)")
    fun observeIsPostLikedByUser(postId: String, userId: String): Flow<Boolean>
    
    @Query("SELECT COUNT(*) FROM post_likes WHERE post_id = :postId")
    suspend fun getLikeCount(postId: String): Int
    
    @Query("SELECT COUNT(*) FROM post_likes WHERE post_id = :postId")
    fun observeLikeCount(postId: String): Flow<Int>
    
    @Query("""
        SELECT pl.id, pl.post_id, pl.user_id, pl.created_at, pl.is_synced, 
               sp.username, sp.display_name, sp.profile_photo_url
        FROM post_likes pl
        INNER JOIN social_profiles sp ON pl.user_id = sp.user_id
        WHERE pl.post_id = :postId
        ORDER BY pl.created_at DESC
        LIMIT :limit
    """)
    suspend fun getPostLikersWithProfiles(postId: String, limit: Int = 20): List<PostLikeWithProfile>
    
    @Query("SELECT * FROM post_likes WHERE user_id = :userId ORDER BY created_at DESC")
    fun getUserLikes(userId: String): Flow<List<PostLikeEntity>>
    
    @Query("DELETE FROM post_likes WHERE post_id = :postId")
    suspend fun deleteAllLikesForPost(postId: String)
    
    @Query("DELETE FROM post_likes WHERE user_id = :userId")
    suspend fun deleteAllUserLikes(userId: String)
    
    @Query("""
        SELECT * FROM post_likes 
        WHERE user_id = :userId 
        AND is_synced = 0
        ORDER BY created_at ASC
    """)
    suspend fun getUnsyncedLikes(userId: String): List<PostLikeEntity>
    
    @Query("""
        UPDATE post_likes 
        SET is_synced = 1
        WHERE id = :likeId
    """)
    suspend fun markAsSynced(likeId: String)
}