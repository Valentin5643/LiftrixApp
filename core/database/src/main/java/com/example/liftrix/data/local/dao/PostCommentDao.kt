package com.example.liftrix.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.PostCommentEntity
import com.example.liftrix.data.local.dto.PostCommentWithProfile
import kotlinx.coroutines.flow.Flow

/**
 * DAO for post comments in the social feed.
 * All queries MUST include userId filtering to prevent data leakage.
 * Part of social feed and engagement system from SPEC-20250113-social-feed-engagement.
 */
@Dao
interface PostCommentDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: PostCommentEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComments(comments: List<PostCommentEntity>)
    
    @Update
    suspend fun updateComment(comment: PostCommentEntity)
    
    @Delete
    suspend fun deleteComment(comment: PostCommentEntity)
    
    @Query("""
        DELETE FROM post_comments
        WHERE id = :commentId
        AND (
            user_id = :userId
            OR post_id IN (SELECT id FROM workout_posts WHERE user_id = :userId)
        )
    """)
    suspend fun deleteCommentById(commentId: String, userId: String): Int
    
    @Query("SELECT * FROM post_comments WHERE id = :commentId")
    suspend fun getCommentById(commentId: String): PostCommentEntity?
    
    @Query("""
        SELECT * FROM post_comments 
        WHERE post_id = :postId 
        AND reply_to_comment_id IS NULL
        AND is_deleted = 0
        ORDER BY created_at ASC
    """)
    fun getTopLevelComments(postId: String): PagingSource<Int, PostCommentEntity>
    
    @Query("""
        SELECT * FROM post_comments 
        WHERE reply_to_comment_id = :parentCommentId AND is_deleted = 0
        ORDER BY created_at ASC
    """)
    suspend fun getReplies(parentCommentId: String): List<PostCommentEntity>
    
    @Query("""
        SELECT * FROM post_comments 
        WHERE reply_to_comment_id = :parentCommentId AND is_deleted = 0
        ORDER BY created_at ASC
    """)
    fun observeReplies(parentCommentId: String): Flow<List<PostCommentEntity>>
    
    @Query("SELECT COUNT(*) FROM post_comments WHERE post_id = :postId AND is_deleted = 0")
    suspend fun getCommentCount(postId: String): Int
    
    @Query("SELECT COUNT(*) FROM post_comments WHERE post_id = :postId AND is_deleted = 0")
    fun observeCommentCount(postId: String): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM post_comments WHERE reply_to_comment_id = :parentCommentId")
    suspend fun getReplyCount(parentCommentId: String): Int
    
    @Query("""
        SELECT * FROM post_comments 
        WHERE user_id = :userId 
        ORDER BY created_at DESC
    """)
    fun getUserComments(userId: String): Flow<List<PostCommentEntity>>
    
    @Query("""
        SELECT pc.id, pc.post_id, pc.user_id, pc.content, pc.reply_to_comment_id,
               pc.like_count, pc.is_edited, pc.created_at, pc.edited_at, pc.updated_at,
               pc.is_synced, pc.sync_version,
               sp.username, sp.display_name, sp.profile_photo_url
        FROM post_comments pc
        INNER JOIN social_profiles sp ON pc.user_id = sp.user_id
        WHERE pc.post_id = :postId
        AND pc.reply_to_comment_id IS NULL
        ORDER BY pc.created_at ASC
    """)
    fun getCommentsWithProfiles(postId: String): PagingSource<Int, PostCommentWithProfile>
    
    @Query("""
        SELECT pc.id, pc.post_id, pc.user_id, pc.content, pc.reply_to_comment_id,
               pc.like_count, pc.is_edited, pc.created_at, pc.edited_at, pc.updated_at,
               pc.is_synced, pc.sync_version,
               sp.username, sp.display_name, sp.profile_photo_url
        FROM post_comments pc
        INNER JOIN social_profiles sp ON pc.user_id = sp.user_id
        WHERE pc.reply_to_comment_id = :parentCommentId
        ORDER BY pc.created_at ASC
    """)
    suspend fun getRepliesWithProfiles(parentCommentId: String): List<PostCommentWithProfile>
    
    @Query("""
        UPDATE post_comments 
        SET content = :content, is_edited = 1, edited_at = :editedAt, updated_at = :updatedAt
        WHERE id = :commentId AND user_id = :userId
    """)
    suspend fun editComment(commentId: String, userId: String, content: String, editedAt: Long, updatedAt: Long)
    
    @Query("""
        UPDATE post_comments 
        SET like_count = :likeCount, updated_at = :updatedAt
        WHERE id = :commentId
    """)
    suspend fun updateLikeCount(commentId: String, likeCount: Int, updatedAt: Long)
    
    /**
     * Marks a comment as synced with the specified sync version
     * @param commentId ID of the comment to mark as synced
     * @param syncVersion Version number to set for sync tracking
     */
    @Query("""
        UPDATE post_comments 
        SET is_synced = 1, sync_version = :syncVersion, updated_at = :updatedAt
        WHERE id = :commentId
    """)
    suspend fun markCommentSynced(commentId: String, syncVersion: Long = 1L, updatedAt: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM post_comments WHERE post_id = :postId")
    suspend fun deleteAllCommentsForPost(postId: String)
    
    @Query("DELETE FROM post_comments WHERE user_id = :userId")
    suspend fun deleteAllUserComments(userId: String)
    
    @Query("""
        SELECT * FROM post_comments 
        WHERE user_id = :userId 
        AND is_synced = 0
        ORDER BY created_at ASC
    """)
    suspend fun getUnsyncedComments(userId: String): List<PostCommentEntity>
    
    @Query("""
        UPDATE post_comments
        SET is_synced = 1, sync_version = :syncVersion
        WHERE id = :commentId
    """)
    suspend fun markAsSynced(commentId: String, syncVersion: Long)

    // ========== OFFLINE-FIRST ARCHITECTURE METHODS (SPEC-20241228) ==========

    /**
     * Upsert comment from LOCAL origin (user edit).
     * Sets isDirty=true and lastModified, triggering sync queue.
     */
    suspend fun upsertLocal(comment: PostCommentEntity) {
        val entity = comment.copy(
            isDirty = true,
            lastModified = System.currentTimeMillis()
        )
        _insert(entity)
    }

    /**
     * Upsert comment from REMOTE origin (Firestore listener/sync).
     * Sets isDirty=false, only applies if remote is newer.
     * Does NOT trigger sync queue.
     */
    suspend fun upsertFromRemote(comment: PostCommentEntity) {
        val local = getCommentById(comment.id)
        if (local == null || comment.lastModified > local.lastModified) {
            val entity = comment.copy(
                isDirty = false,
                isSynced = true,
                syncVersion = System.currentTimeMillis()
            )
            _insert(entity)
        }
    }

    /**
     * Internal insert for shared logic.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun _insert(comment: PostCommentEntity)

    /**
     * Get dirty comments that need upload to Firestore.
     */
    @Query("SELECT * FROM post_comments WHERE user_id = :userId AND is_dirty = 1 ORDER BY last_modified ASC")
    suspend fun getDirtyComments(userId: String): List<PostCommentEntity>

    /**
     * Mark comments as clean after successful Firestore upload.
     */
    @Query("UPDATE post_comments SET is_dirty = 0, is_synced = 1, sync_version = :syncVersion WHERE id IN (:ids) AND user_id = :userId")
    suspend fun markAsClean(ids: List<String>, userId: String, syncVersion: Long = System.currentTimeMillis()): Int

    @Query("UPDATE post_comments SET is_deleted = 1, is_dirty = 1, is_synced = 0, last_modified = :timestamp WHERE id = :commentId AND (user_id = :userId OR post_id IN (SELECT id FROM workout_posts WHERE user_id = :userId))")
    suspend fun tombstone(commentId: String, userId: String, timestamp: Long): Int

    // ========== END OFFLINE-FIRST METHODS ==========
}
