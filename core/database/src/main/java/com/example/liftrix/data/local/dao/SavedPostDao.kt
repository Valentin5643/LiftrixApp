package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.liftrix.data.local.entity.SavedPostEntity
import com.example.liftrix.data.local.dto.SavedPostWithDetails
import com.example.liftrix.data.local.dto.PostSaveStats
import kotlinx.coroutines.flow.Flow

/**
 * DAO for saved workout posts.
 * All queries MUST include userId filtering to prevent data leakage.
 * Part of social feed and engagement system from SPEC-20250113-social-feed-engagement.
 */
@Dao
interface SavedPostDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePost(savedPost: SavedPostEntity)
    
    @Delete
    suspend fun unsavePost(savedPost: SavedPostEntity)
    
    @Query("DELETE FROM saved_posts WHERE user_id = :userId AND post_id = :postId")
    suspend fun unsavePostByIds(userId: String, postId: String)
    
    @Query("SELECT * FROM saved_posts WHERE user_id = :userId AND post_id = :postId AND is_deleted = 0")
    suspend fun getSavedPost(userId: String, postId: String): SavedPostEntity?
    
    @Query("SELECT EXISTS(SELECT 1 FROM saved_posts WHERE user_id = :userId AND post_id = :postId AND is_deleted = 0)")
    suspend fun isPostSaved(userId: String, postId: String): Boolean
    
    @Query("SELECT EXISTS(SELECT 1 FROM saved_posts WHERE user_id = :userId AND post_id = :postId AND is_deleted = 0)")
    fun observeIsPostSaved(userId: String, postId: String): Flow<Boolean>
    
    @Query("""
        SELECT sp.id, sp.user_id as userId, sp.post_id as postId, sp.saved_at as savedAt,
               wp.caption, 
               prof.username as authorUsername,
               prof.display_name as authorDisplayName,
               prof.profile_photo_url as authorProfilePhotoUrl,
               wp.workout_duration as workoutDuration,
               wp.exercises_count as exercisesCount,
               wp.prs_count as prsCount,
               wp.created_at as createdAt
        FROM saved_posts sp
        INNER JOIN workout_posts wp ON sp.post_id = wp.id
        INNER JOIN social_profiles prof ON wp.user_id = prof.user_id
        WHERE sp.user_id = :userId AND sp.is_deleted = 0
        ORDER BY sp.saved_at DESC
    """)
    fun getUserSavedPostsWithDetails(userId: String): Flow<List<SavedPostWithDetails>>
    
    @Query("SELECT * FROM saved_posts WHERE user_id = :userId ORDER BY saved_at DESC")
    fun getUserSavedPosts(userId: String): Flow<List<SavedPostEntity>>
    
    @Query("SELECT COUNT(*) FROM saved_posts WHERE user_id = :userId")
    suspend fun getUserSavedPostCount(userId: String): Int
    
    @Query("SELECT COUNT(*) FROM saved_posts WHERE post_id = :postId")
    suspend fun getPostSaveCount(postId: String): Int
    
    @Query("DELETE FROM saved_posts WHERE user_id = :userId")
    suspend fun deleteAllUserSavedPosts(userId: String)
    
    @Query("DELETE FROM saved_posts WHERE post_id = :postId")
    suspend fun deleteAllSavesForPost(postId: String)
    
    @Query("""
        SELECT sp.post_id as postId, COUNT(*) as saveCount
        FROM saved_posts sp
        INNER JOIN workout_posts wp ON sp.post_id = wp.id
        WHERE wp.user_id = :userId
        GROUP BY sp.post_id
        ORDER BY saveCount DESC
        LIMIT :limit
    """)
    suspend fun getMostSavedPostsByUser(userId: String, limit: Int = 10): List<PostSaveStats>

    @Query("UPDATE saved_posts SET is_deleted = 1, is_dirty = 1, is_synced = 0, last_modified = :timestamp WHERE user_id = :userId AND post_id = :postId")
    suspend fun tombstone(userId: String, postId: String, timestamp: Long): Int

    @Query("SELECT * FROM saved_posts WHERE user_id = :userId AND is_dirty = 1 ORDER BY last_modified ASC")
    suspend fun getDirtySavedPosts(userId: String): List<SavedPostEntity>

    @Query("UPDATE saved_posts SET is_dirty = 0, is_synced = 1, sync_version = :syncVersion WHERE id IN (:ids) AND user_id = :userId")
    suspend fun markAsClean(ids: List<String>, userId: String, syncVersion: Long): Int
}
