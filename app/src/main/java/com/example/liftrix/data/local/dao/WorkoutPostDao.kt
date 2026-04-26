package com.example.liftrix.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.WorkoutPostEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for workout posts in the social feed.
 * All queries MUST include userId filtering to prevent data leakage.
 * Part of social feed and engagement system from SPEC-20250113-social-feed-engagement.
 */
@Dao
interface WorkoutPostDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: WorkoutPostEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<WorkoutPostEntity>)
    
    @Update
    suspend fun updatePost(post: WorkoutPostEntity)
    
    @Delete
    suspend fun deletePost(post: WorkoutPostEntity)
    
    @Query("DELETE FROM workout_posts WHERE id = :postId AND user_id = :userId")
    suspend fun deletePostById(postId: String, userId: String)
    
    @Query("SELECT * FROM workout_posts WHERE id = :postId")
    suspend fun getPostById(postId: String): WorkoutPostEntity?
    
    @Query("SELECT * FROM workout_posts WHERE user_id = :userId AND workout_id = :workoutId LIMIT 1")
    suspend fun getPostByWorkoutId(userId: String, workoutId: String): WorkoutPostEntity?
    
    @Query("SELECT * FROM workout_posts WHERE user_id = :userId AND is_hidden = 0 ORDER BY created_at DESC")
    fun getUserPosts(userId: String): Flow<List<WorkoutPostEntity>>
    
    @Query("""
        SELECT * FROM workout_posts
        WHERE user_id = :userId
        AND is_hidden = 0
        ORDER BY created_at DESC
        LIMIT :limit
    """)
    fun getRecentUserPosts(userId: String, limit: Int): Flow<List<WorkoutPostEntity>>
    
    @Query("""
        SELECT * FROM workout_posts
        WHERE user_id IN (:userIds)
        AND visibility IN ('PUBLIC', 'FOLLOWERS')
        AND is_hidden = 0
        ORDER BY created_at DESC
        LIMIT :limit
    """)
    fun getRecentPostsFromUsers(userIds: List<String>, limit: Int): Flow<List<WorkoutPostEntity>>
    
    @Query("""
        SELECT * FROM workout_posts
        WHERE visibility = 'PUBLIC'
        AND is_hidden = 0
        ORDER BY created_at DESC
        LIMIT :limit
    """)
    fun getRecentPublicPosts(limit: Int): Flow<List<WorkoutPostEntity>>
    
    @Query("""
        SELECT * FROM workout_posts
        WHERE user_id IN (:followedUserIds)
        AND visibility IN ('PUBLIC', 'FOLLOWERS')
        AND is_hidden = 0
        ORDER BY created_at DESC
    """)
    fun getHomeFeedPosts(followedUserIds: List<String>): PagingSource<Int, WorkoutPostEntity>
    
    @Query("""
        SELECT * FROM workout_posts
        WHERE (user_id = :currentUserId OR (user_id IN (:followedUserIds) AND visibility IN ('PUBLIC', 'FOLLOWERS')))
        AND is_hidden = 0
        ORDER BY created_at DESC
    """)
    fun getHomeFeedPostsWithSelf(currentUserId: String, followedUserIds: List<String>): PagingSource<Int, WorkoutPostEntity>
    
    @Query("""
        SELECT * FROM workout_posts
        WHERE visibility = 'PUBLIC'
        AND user_id NOT IN (:excludeUserIds)
        AND is_hidden = 0
        ORDER BY
            CASE
                WHEN prs_count > 0 THEN created_at + (prs_count * 3600000)
                ELSE created_at
            END DESC
    """)
    fun getDiscoveryFeedPosts(excludeUserIds: List<String>): PagingSource<Int, WorkoutPostEntity>
    
    @Query("""
        SELECT * FROM workout_posts
        WHERE visibility = 'PUBLIC'
        AND user_id NOT IN (:excludeUserIds)
        AND created_at >= :sinceTimestamp
        AND is_hidden = 0
        ORDER BY
            CASE
                WHEN prs_count > 0 THEN created_at + (prs_count * 3600000)
                ELSE created_at
            END DESC
    """)
    fun getDiscoveryFeedPostsWithTimeFilter(
        excludeUserIds: List<String>,
        sinceTimestamp: Long
    ): PagingSource<Int, WorkoutPostEntity>
    
    @Query("""
        SELECT * FROM workout_posts
        WHERE user_id = :userId
        AND visibility = :visibility
        AND is_hidden = 0
        ORDER BY created_at DESC
    """)
    fun getUserPostsByVisibility(userId: String, visibility: String): Flow<List<WorkoutPostEntity>>
    
    @Query("""
        UPDATE workout_posts 
        SET like_count = :likeCount, updated_at = :updatedAt
        WHERE id = :postId
    """)
    suspend fun updateLikeCount(postId: String, likeCount: Int, updatedAt: Long)
    
    @Query("""
        UPDATE workout_posts 
        SET comment_count = :commentCount, updated_at = :updatedAt
        WHERE id = :postId
    """)
    suspend fun updateCommentCount(postId: String, commentCount: Int, updatedAt: Long)
    
    @Query("""
        UPDATE workout_posts 
        SET share_count = :shareCount, updated_at = :updatedAt
        WHERE id = :postId
    """)
    suspend fun updateShareCount(postId: String, shareCount: Int, updatedAt: Long)
    
    @Query("""
        UPDATE workout_posts 
        SET save_count = :saveCount, updated_at = :updatedAt
        WHERE id = :postId
    """)
    suspend fun updateSaveCount(postId: String, saveCount: Int, updatedAt: Long)
    
    @Query("""
        UPDATE workout_posts 
        SET like_count = :likeCount, 
            comment_count = :commentCount, 
            share_count = :shareCount, 
            save_count = :saveCount, 
            updated_at = :updatedAt
        WHERE id = :postId
    """)
    suspend fun updateEngagementMetrics(
        postId: String, 
        likeCount: Int, 
        commentCount: Int, 
        shareCount: Int, 
        saveCount: Int, 
        updatedAt: Long = System.currentTimeMillis()
    )
    
    @Query("SELECT COUNT(*) FROM workout_posts WHERE user_id = :userId")
    suspend fun getUserPostCount(userId: String): Int
    
    @Query("SELECT COUNT(*) FROM workout_posts WHERE user_id = :userId AND visibility = :visibility")
    suspend fun getUserPostCountByVisibility(userId: String, visibility: String): Int
    
    @Query("DELETE FROM workout_posts WHERE user_id = :userId")
    suspend fun deleteAllUserPosts(userId: String)
    
    @Query("""
        SELECT * FROM workout_posts 
        WHERE user_id = :userId 
        AND is_synced = 0
        ORDER BY created_at ASC
    """)
    suspend fun getUnsyncedPosts(userId: String): List<WorkoutPostEntity>
    
    @Query("""
        UPDATE workout_posts 
        SET is_synced = 1, sync_version = :syncVersion
        WHERE id = :postId
    """)
    suspend fun markAsSynced(postId: String, syncVersion: Long)
    
    @Query("""
        SELECT * FROM workout_posts
        WHERE visibility = 'PUBLIC'
        AND user_id NOT IN (:excludeUserIds)
        AND is_hidden = 0
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getPublicPostsExcludingUsers(
        excludeUserIds: List<String>,
        limit: Int,
        offset: Int
    ): List<WorkoutPostEntity>

    // ========== OFFLINE-FIRST ARCHITECTURE METHODS (SPEC-20241228) ==========

    /**
     * Upsert post from LOCAL origin (user edit).
     * Sets isDirty=true and lastModified, triggering sync queue.
     */
    suspend fun upsertLocal(post: WorkoutPostEntity) {
        val entity = post.copy(
            isDirty = true,
            lastModified = System.currentTimeMillis()
        )
        _insert(entity)
    }

    /**
     * Upsert post from REMOTE origin (Firestore listener/sync).
     * Sets isDirty=false, only applies if remote is newer.
     * Does NOT trigger sync queue.
     */
    suspend fun upsertFromRemote(post: WorkoutPostEntity) {
        val local = getPostById(post.id)
        if (local == null || post.lastModified > local.lastModified) {
            val entity = post.copy(
                isDirty = false,
                isSynced = true,
                syncVersion = System.currentTimeMillis()
            )
            _insert(entity)
        }
    }

    /**
     * Upsert engagement metrics from REMOTE (real-time listener).
     * Idempotent - only updates if remote is newer.
     * Does NOT trigger sync queue.
     */
    suspend fun upsertEngagementFromRemote(
        postId: String,
        likeCount: Int,
        commentCount: Int,
        shareCount: Int,
        saveCount: Int,
        lastModified: Long
    ) {
        val existing = getPostById(postId)
        if (existing == null || lastModified > existing.lastModified) {
            _updateEngagementMetrics(
                postId = postId,
                likeCount = likeCount,
                commentCount = commentCount,
                shareCount = shareCount,
                saveCount = saveCount,
                lastModified = lastModified,
                isDirty = false
            )
        }
    }

    /**
     * Internal insert for shared logic.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun _insert(post: WorkoutPostEntity)

    /**
     * Internal update for engagement metrics.
     */
    @Query("""
        UPDATE workout_posts
        SET like_count = :likeCount,
            comment_count = :commentCount,
            share_count = :shareCount,
            save_count = :saveCount,
            last_modified = :lastModified,
            is_dirty = :isDirty
        WHERE id = :postId
    """)
    suspend fun _updateEngagementMetrics(
        postId: String,
        likeCount: Int,
        commentCount: Int,
        shareCount: Int,
        saveCount: Int,
        lastModified: Long,
        isDirty: Boolean
    )

    /**
     * Get dirty posts that need upload to Firestore.
     */
    @Query("SELECT * FROM workout_posts WHERE user_id = :userId AND is_dirty = 1 ORDER BY last_modified ASC")
    suspend fun getDirtyPosts(userId: String): List<WorkoutPostEntity>

    /**
     * Mark posts as clean after successful Firestore upload.
     */
    @Query("UPDATE workout_posts SET is_dirty = 0, is_synced = 1, sync_version = :syncVersion WHERE id IN (:ids) AND user_id = :userId")
    suspend fun markAsClean(ids: List<String>, userId: String, syncVersion: Long = System.currentTimeMillis()): Int

    // ========== END OFFLINE-FIRST METHODS ==========

    // ========== MODERATION METHODS (SPEC-20251230-google-play-compliance) ==========

    /**
     * Hide a post (moderation action).
     * Sets is_hidden=true and records moderation metadata.
     */
    @Query("""
        UPDATE workout_posts
        SET is_hidden = 1,
            hidden_reason = :reason,
            hidden_at = :hiddenAt,
            hidden_by_user_id = :hiddenByUserId,
            is_dirty = 1,
            last_modified = :hiddenAt
        WHERE id = :postId
    """)
    suspend fun hidePost(
        postId: String,
        reason: String,
        hiddenByUserId: String,
        hiddenAt: Long = System.currentTimeMillis()
    )

    /**
     * Unhide a post (restore visibility).
     * Clears moderation metadata.
     */
    @Query("""
        UPDATE workout_posts
        SET is_hidden = 0,
            hidden_reason = NULL,
            hidden_at = NULL,
            hidden_by_user_id = NULL,
            is_dirty = 1,
            last_modified = :restoredAt
        WHERE id = :postId
    """)
    suspend fun unhidePost(
        postId: String,
        restoredAt: Long = System.currentTimeMillis()
    )

    /**
     * Get all hidden posts (admin moderation dashboard).
     */
    @Query("""
        SELECT * FROM workout_posts
        WHERE is_hidden = 1
        ORDER BY hidden_at DESC
        LIMIT :limit
    """)
    suspend fun getHiddenPosts(limit: Int = 100): List<WorkoutPostEntity>

    // ========== END MODERATION METHODS ==========
}