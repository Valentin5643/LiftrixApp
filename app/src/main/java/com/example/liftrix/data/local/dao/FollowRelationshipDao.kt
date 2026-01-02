package com.example.liftrix.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.liftrix.data.local.entity.FollowRelationshipEntity
import kotlinx.coroutines.flow.Flow

/**
 * Enriched follow relationship that includes profile data from joins.
 * Used for displaying follow/follower lists with complete user information.
 */
data class EnrichedFollowRelationship(
    @androidx.room.ColumnInfo(name = "id") val id: String,
    @androidx.room.ColumnInfo(name = "follower_id") val followerId: String,
    @androidx.room.ColumnInfo(name = "following_id") val followingId: String,
    @androidx.room.ColumnInfo(name = "status") val status: String,
    @androidx.room.ColumnInfo(name = "created_at") val createdAt: Long,
    @androidx.room.ColumnInfo(name = "accepted_at") val acceptedAt: Long?,
    @androidx.room.ColumnInfo(name = "blocked_at") val blockedAt: Long?,
    @androidx.room.ColumnInfo(name = "is_synced") val isSynced: Boolean,
    @androidx.room.ColumnInfo(name = "sync_version") val syncVersion: Long,
    @androidx.room.ColumnInfo(name = "last_modified") val lastModified: Long,
    // Profile data from social_profiles join
    @androidx.room.ColumnInfo(name = "profile_display_name") val profileDisplayName: String?,
    @androidx.room.ColumnInfo(name = "profile_image_url") val profileImageUrl: String?,
    @androidx.room.ColumnInfo(name = "profile_bio") val profileBio: String?
) {
    /**
     * Converts to domain model with proper display name fallback
     */
    fun toFollowRelationshipEntity(): FollowRelationshipEntity {
        return FollowRelationshipEntity(
            id = id,
            followerId = followerId,
            followingId = followingId,
            status = status,
            createdAt = createdAt,
            acceptedAt = acceptedAt,
            blockedAt = blockedAt,
            isSynced = isSynced,
            syncVersion = syncVersion,
            lastModified = lastModified
        )
    }
}

/**
 * DAO for follow relationship operations with mandatory user scoping.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 * 
 * CRITICAL SECURITY: All queries MUST include userId filtering to prevent data leakage.
 */
@Dao
interface FollowRelationshipDao {

    // ========================================
    // Follow Relationship Queries (User-Scoped)
    // ========================================

    @Query("""
        SELECT * FROM follow_relationships 
        WHERE follower_id = :userId AND following_id = :targetUserId
    """)
    suspend fun getFollowRelationship(userId: String, targetUserId: String): FollowRelationshipEntity?

    @Query("""
        SELECT fr.* FROM follow_relationships fr
        INNER JOIN social_profiles sp ON fr.following_id = sp.user_id
        WHERE fr.follower_id = :userId AND fr.status = :status
        ORDER BY fr.created_at DESC
        LIMIT :limit
    """)
    suspend fun getFollowing(
        userId: String, 
        status: String = FollowRelationshipEntity.STATUS_ACCEPTED,
        limit: Int = 100
    ): List<FollowRelationshipEntity>
    
    @Query("""
        SELECT following_id FROM follow_relationships 
        WHERE follower_id = :userId AND status = 'ACCEPTED'
    """)
    suspend fun getFollowingUserIds(userId: String): List<String>

    @Query("""
        SELECT follower_id FROM follow_relationships
        WHERE following_id = :userId AND status = 'ACCEPTED'
    """)
    suspend fun getFollowerUserIds(userId: String): List<String>

    @Query("""
        SELECT fr.* FROM follow_relationships fr
        INNER JOIN social_profiles sp ON fr.follower_id = sp.user_id
        WHERE fr.following_id = :userId AND fr.status = :status
        ORDER BY fr.created_at DESC
        LIMIT :limit
    """)
    suspend fun getFollowers(
        userId: String, 
        status: String = FollowRelationshipEntity.STATUS_ACCEPTED,
        limit: Int = 100
    ): List<FollowRelationshipEntity>

    @Query("""
        SELECT fr.* FROM follow_relationships fr
        WHERE fr.following_id = :userId AND fr.status = 'PENDING'
        ORDER BY fr.created_at DESC
    """)
    suspend fun getPendingFollowRequests(userId: String): List<FollowRelationshipEntity>

    @Query("""
        SELECT fr.* FROM follow_relationships fr
        WHERE fr.follower_id = :userId AND fr.status = 'PENDING'
        ORDER BY fr.created_at DESC
    """)
    suspend fun getSentFollowRequests(userId: String): List<FollowRelationshipEntity>

    // ========================================
    // Follow Status Checks
    // ========================================

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM follow_relationships 
            WHERE follower_id = :userId 
            AND following_id = :targetUserId 
            AND status = 'ACCEPTED'
        )
    """)
    suspend fun isFollowing(userId: String, targetUserId: String): Boolean

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM follow_relationships 
            WHERE follower_id = :userId 
            AND following_id = :targetUserId 
            AND status = 'PENDING'
        )
    """)
    suspend fun hasPendingFollowRequest(userId: String, targetUserId: String): Boolean

    @Query("""
        SELECT status FROM follow_relationships 
        WHERE follower_id = :userId AND following_id = :targetUserId
    """)
    suspend fun getFollowStatus(userId: String, targetUserId: String): String?

    // ========================================
    // Follow Count Queries
    // ========================================

    @Query("""
        SELECT COUNT(*) FROM follow_relationships 
        WHERE follower_id = :userId AND status = 'ACCEPTED'
    """)
    suspend fun getFollowingCount(userId: String): Int

    @Query("""
        SELECT COUNT(*) FROM follow_relationships 
        WHERE following_id = :userId AND status = 'ACCEPTED'
    """)
    suspend fun getFollowerCount(userId: String): Int

    @Query("""
        SELECT COUNT(*) FROM follow_relationships 
        WHERE following_id = :userId AND status = 'PENDING'
    """)
    suspend fun getPendingRequestCount(userId: String): Int

    // ========================================
    // Reactive Follow Queries
    // ========================================

    @Query("""
        SELECT fr.* FROM follow_relationships fr
        WHERE fr.follower_id = :userId AND fr.status = 'ACCEPTED'
        ORDER BY fr.accepted_at DESC
    """)
    fun observeFollowing(userId: String): Flow<List<FollowRelationshipEntity>>

    @Query("""
        SELECT fr.* FROM follow_relationships fr
        WHERE fr.following_id = :userId AND fr.status = 'ACCEPTED'
        ORDER BY fr.accepted_at DESC
    """)
    fun observeFollowers(userId: String): Flow<List<FollowRelationshipEntity>>

    // ========================================
    // Enriched Follow Queries with Profile Data
    // ========================================

    @Query("""
        SELECT 
            fr.id,
            fr.follower_id,
            fr.following_id,
            fr.status,
            fr.created_at,
            fr.accepted_at,
            fr.blocked_at,
            fr.is_synced,
            fr.sync_version,
            fr.last_modified,
            sp.display_name as profile_display_name,
            sp.profile_photo_url as profile_image_url,
            sp.bio as profile_bio
        FROM follow_relationships fr
        INNER JOIN social_profiles sp ON fr.following_id = sp.user_id
        WHERE fr.follower_id = :userId AND fr.status = 'ACCEPTED'
        ORDER BY fr.accepted_at DESC
    """)
    fun observeFollowingWithProfiles(userId: String): Flow<List<EnrichedFollowRelationship>>

    @Query("""
        SELECT 
            fr.id,
            fr.follower_id,
            fr.following_id,
            fr.status,
            fr.created_at,
            fr.accepted_at,
            fr.blocked_at,
            fr.is_synced,
            fr.sync_version,
            fr.last_modified,
            sp.display_name as profile_display_name,
            sp.profile_photo_url as profile_image_url,
            sp.bio as profile_bio
        FROM follow_relationships fr
        INNER JOIN social_profiles sp ON fr.follower_id = sp.user_id
        WHERE fr.following_id = :userId AND fr.status = 'ACCEPTED'
        ORDER BY fr.accepted_at DESC
    """)
    fun observeFollowersWithProfiles(userId: String): Flow<List<EnrichedFollowRelationship>>

    @Query("""
        SELECT COUNT(*) FROM follow_relationships 
        WHERE following_id = :userId AND status = 'PENDING'
    """)
    fun observePendingRequestCount(userId: String): Flow<Int>

    // ========================================
    // Follow Relationship Management
    // ========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollowRelationship(relationship: FollowRelationshipEntity): Long
    
    @androidx.room.Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFollowRelationships(relationships: List<FollowRelationshipEntity>)
    

    @Update
    suspend fun updateFollowRelationship(relationship: FollowRelationshipEntity): Int

    @Query("""
        UPDATE follow_relationships 
        SET status = :status, accepted_at = :acceptedAt
        WHERE follower_id = :followerId AND following_id = :followingId
    """)
    suspend fun updateFollowStatus(
        followerId: String, 
        followingId: String, 
        status: String,
        acceptedAt: Long? = null
    ): Int

    @Query("""
        UPDATE follow_relationships 
        SET status = 'BLOCKED', blocked_at = :blockedAt
        WHERE follower_id = :followerId AND following_id = :followingId
    """)
    suspend fun blockFollowRelationship(
        followerId: String, 
        followingId: String, 
        blockedAt: Long
    ): Int

    // ========================================
    // Follow Relationship Deletion
    // ========================================

    @Delete
    suspend fun deleteFollowRelationship(relationship: FollowRelationshipEntity): Int

    @Query("""
        DELETE FROM follow_relationships 
        WHERE follower_id = :followerId AND following_id = :followingId
    """)
    suspend fun deleteFollowRelationship(followerId: String, followingId: String): Int

    @Query("""
        DELETE FROM follow_relationships 
        WHERE (follower_id = :userId OR following_id = :userId)
    """)
    suspend fun deleteAllRelationshipsForUser(userId: String): Int

    // ========================================
    // Sync Management
    // ========================================

    @Query("""
        SELECT * FROM follow_relationships 
        WHERE (follower_id = :userId OR following_id = :userId) 
        AND is_synced = 0
    """)
    suspend fun getUnsyncedRelationships(userId: String): List<FollowRelationshipEntity>

    @Query("""
        UPDATE follow_relationships 
        SET is_synced = :isSynced, sync_version = :version
        WHERE id = :relationshipId
    """)
    suspend fun updateSyncStatus(relationshipId: String, isSynced: Boolean, version: Long): Int

    // ========================================
    // Mutual Follow Detection
    // ========================================

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM follow_relationships fr1
            INNER JOIN follow_relationships fr2 
            ON fr1.follower_id = fr2.following_id 
            AND fr1.following_id = fr2.follower_id
            WHERE (fr1.follower_id = :userId AND fr1.following_id = :targetUserId)
            AND fr1.status = 'ACCEPTED' AND fr2.status = 'ACCEPTED'
        )
    """)
    suspend fun areMutuallyFollowing(userId: String, targetUserId: String): Boolean

    // ========================================
    // Analytics Support
    // ========================================

    @Query("""
        SELECT COUNT(*) FROM follow_relationships 
        WHERE (follower_id = :userId OR following_id = :userId)
    """)
    suspend fun getTotalRelationshipCount(userId: String): Int

    // ========== OFFLINE-FIRST ARCHITECTURE METHODS (SPEC-20241228) ==========

    /**
     * Upsert followrelationship from LOCAL origin (user edit).
     * Sets isDirty=true and lastModified, triggering sync queue.
     */
    suspend fun upsertLocal(followRelationship: FollowRelationshipEntity) {
        val entity = followRelationship.copy(
            isDirty = true,
            lastModified = System.currentTimeMillis()
        )
        _insert(entity)
    }

    /**
     * Upsert followrelationship from REMOTE origin (Firestore listener/sync).
     * Sets isDirty=false, only applies if remote is newer.
     * Does NOT trigger sync queue.
     */
    @Transaction
    suspend fun upsertFromRemote(followRelationship: FollowRelationshipEntity) {
        val local = getFollowRelationshipForSync(
            followRelationship.id,
            followRelationship.followerId,
            followRelationship.followingId
        )
        if (local == null || followRelationship.lastModified > local.lastModified) {
            val entity = followRelationship.copy(
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
    suspend fun _insert(entity: FollowRelationshipEntity)

    /**
     * Get dirty followrelationship that need upload to Firestore.
     */
    @Query("""
        SELECT * FROM follow_relationships 
        WHERE (follower_id = :userId OR following_id = :userId)
        AND is_dirty = 1
        ORDER BY last_modified ASC
    """)
    suspend fun getDirtyFollowRelationships(userId: String): List<FollowRelationshipEntity>

    /**
     * Mark followrelationship as clean after successful Firestore upload.
     */
    @Query("""
        UPDATE follow_relationships 
        SET is_dirty = 0, is_synced = 1, sync_version = :syncVersion 
        WHERE id IN (:ids) 
        AND (follower_id = :userId OR following_id = :userId)
    """)
    suspend fun markAsClean(ids: List<String>, userId: String, syncVersion: Long = System.currentTimeMillis()): Int

    /**
     * Get local followrelationship for remote deduplication.
     */
    @Query("""
        SELECT * FROM follow_relationships 
        WHERE id = :id 
        AND (follower_id = :followerId OR following_id = :followingId)
        LIMIT 1
    """)
    suspend fun getFollowRelationshipForSync(
        id: String,
        followerId: String,
        followingId: String
    ): FollowRelationshipEntity?

    // ========== END OFFLINE-FIRST METHODS ==========
}
