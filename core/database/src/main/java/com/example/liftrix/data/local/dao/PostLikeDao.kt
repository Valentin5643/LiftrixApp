package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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
    
    @Query("SELECT * FROM post_likes WHERE id = :likeId")
    suspend fun getLikeById(likeId: String): PostLikeEntity?
    
    @Query("SELECT * FROM post_likes WHERE post_id = :postId AND user_id = :userId")
    suspend fun getLikeByPostAndUser(postId: String, userId: String): PostLikeEntity?
    
    @Query("SELECT EXISTS(SELECT 1 FROM post_likes WHERE post_id = :postId AND user_id = :userId AND is_deleted = 0)")
    suspend fun isPostLikedByUser(postId: String, userId: String): Boolean
    
    @Query("SELECT EXISTS(SELECT 1 FROM post_likes WHERE post_id = :postId AND user_id = :userId AND is_deleted = 0)")
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
    
    @Query("""
        SELECT pl.id, pl.post_id, pl.user_id, pl.created_at, pl.is_synced, 
               sp.username, sp.display_name, sp.profile_photo_url
        FROM post_likes pl
        INNER JOIN social_profiles sp ON pl.user_id = sp.user_id
        WHERE pl.post_id = :postId
        ORDER BY pl.created_at DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getPostLikersWithProfiles(postId: String, limit: Int, offset: Int): List<PostLikeWithProfile>
    
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

    // ========== OFFLINE-FIRST ARCHITECTURE METHODS (SPEC-20241228) ==========

    /**
     * Upsert postlike from LOCAL origin (user edit).
     * Sets isDirty=true and lastModified, triggering sync queue.
     */
    suspend fun upsertLocal(postLike: PostLikeEntity) {
        val entity = postLike.copy(
            isDirty = true,
            lastModified = System.currentTimeMillis()
        )
        _insert(entity)
    }

    /**
     * Upsert postlike from REMOTE origin (Firestore listener/sync).
     * Sets isDirty=false, only applies if remote is newer.
     * Does NOT trigger sync queue.
     */
    @Transaction
    suspend fun upsertFromRemote(postLike: PostLikeEntity) {
        val local = getPostLikeForSync(postLike.id, postLike.userId)
        if (local == null || postLike.lastModified > local.lastModified) {
            val entity = postLike.copy(
                isDirty = false,
                isSynced = true
            )
            _insert(entity)
        }
    }

    /**
     * Internal insert for shared logic.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun _insert(entity: PostLikeEntity)

    /**
     * Get dirty postlike that need upload to Firestore.
     */
    @Query("SELECT * FROM post_likes WHERE user_id = :userId AND is_dirty = 1 ORDER BY last_modified ASC")
    suspend fun getDirtyPostLikes(userId: String): List<PostLikeEntity>

    /**
     * Mark postlike as clean after successful Firestore upload.
     */
    @Query("UPDATE post_likes SET is_dirty = 0, is_synced = 1 WHERE id IN (:ids) AND user_id = :userId")
    suspend fun markAsClean(ids: List<String>, userId: String): Int

    @Query("UPDATE post_likes SET is_deleted = 1, is_dirty = 1, is_synced = 0, last_modified = :timestamp WHERE post_id = :postId AND user_id = :userId")
    suspend fun tombstone(postId: String, userId: String, timestamp: Long): Int

    /**
     * Get local postlike for remote deduplication.
     */
    @Query("SELECT * FROM post_likes WHERE id = :id AND user_id = :userId LIMIT 1")
    suspend fun getPostLikeForSync(id: String, userId: String): PostLikeEntity?

    // ========== END OFFLINE-FIRST METHODS ==========
}
