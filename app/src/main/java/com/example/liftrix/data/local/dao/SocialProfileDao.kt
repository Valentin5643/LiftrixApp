package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.example.liftrix.data.local.entity.SocialProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for social profile operations with mandatory user scoping.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 * 
 * CRITICAL SECURITY: All queries MUST include userId filtering to prevent data leakage.
 */
@Dao
interface SocialProfileDao {

    // ========================================
    // Profile Retrieval (User-Scoped)
    // ========================================

    @Query("SELECT * FROM social_profiles WHERE user_id = :userId")
    fun observeProfile(userId: String): Flow<SocialProfileEntity?>

    @Query("SELECT * FROM social_profiles WHERE user_id = :userId")
    suspend fun getProfile(userId: String): SocialProfileEntity?

    @Query("SELECT * FROM social_profiles WHERE user_id = :userId")
    suspend fun getSocialProfileByUserId(userId: String): SocialProfileEntity?

    @Query("""
        SELECT sp.* FROM social_profiles sp
        WHERE sp.username = :username
        AND sp.is_private = 0
        AND (:viewerId IS NULL OR sp.user_id != :viewerId)
        AND (:viewerId IS NULL OR sp.user_id NOT IN (
            SELECT blocked_user_id FROM blocked_users WHERE user_id = :viewerId
        ))
        AND (:viewerId IS NULL OR :viewerId NOT IN (
            SELECT user_id FROM blocked_users WHERE blocked_user_id = sp.user_id
        ))
    """)
    suspend fun getProfileByUsername(viewerId: String?, username: String): SocialProfileEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM social_profiles WHERE user_id = :userId)")
    suspend fun hasProfile(userId: String): Boolean

    // ========================================
    // Username Management
    // ========================================

    @Query("SELECT EXISTS(SELECT 1 FROM social_profiles WHERE username = :username)")
    suspend fun isUsernameAvailable(username: String): Boolean

    @Query("SELECT username FROM social_profiles WHERE user_id = :userId")
    suspend fun getUsernameForUser(userId: String): String?

    // ========================================
    // Profile Discovery (Privacy-Filtered)
    // ========================================

    @Query("""
        SELECT sp.* FROM social_profiles sp
        WHERE sp.user_id != :viewerId 
        AND sp.is_private = 0
        AND sp.hide_from_suggestions = 0
        AND sp.user_id NOT IN (
            SELECT blocked_user_id FROM blocked_users WHERE user_id = :viewerId
        )
        AND :viewerId NOT IN (
            SELECT user_id FROM blocked_users WHERE blocked_user_id = sp.user_id
        )
        ORDER BY sp.member_since DESC
        LIMIT :limit
    """)
    suspend fun getDiscoverableProfiles(viewerId: String, limit: Int = 50): List<SocialProfileEntity>

    @Query("""
        SELECT sp.* FROM social_profiles sp
        WHERE sp.user_id != :viewerId
        AND sp.username LIKE '%' || :query || '%'
        AND sp.is_private = 0
        AND sp.user_id NOT IN (
            SELECT blocked_user_id FROM blocked_users WHERE user_id = :viewerId
        )
        AND :viewerId NOT IN (
            SELECT user_id FROM blocked_users WHERE blocked_user_id = sp.user_id
        )
        ORDER BY sp.follower_count DESC
        LIMIT :limit
    """)
    suspend fun searchProfiles(viewerId: String, query: String, limit: Int = 20): List<SocialProfileEntity>

    /**
     * Search profiles by partial username match
     */
    @Query("""
        SELECT sp.* FROM social_profiles sp
        WHERE sp.username LIKE '%' || :query || '%'
        AND sp.is_private = 0
        ORDER BY sp.follower_count DESC
        LIMIT :limit
    """)
    suspend fun searchProfilesByUsernamePartial(query: String, limit: Int = 20): List<SocialProfileEntity>

    /**
     * Get social profile by exact username match
     */
    @Query("SELECT * FROM social_profiles WHERE username = :username")
    suspend fun getSocialProfileByUsername(username: String): SocialProfileEntity?

    /**
     * Search profiles by display name
     */
    @Query("""
        SELECT sp.* FROM social_profiles sp
        WHERE sp.display_name LIKE '%' || :displayName || '%'
        AND sp.is_private = 0
        ORDER BY sp.follower_count DESC
        LIMIT :limit
    """)
    suspend fun searchProfilesByDisplayName(displayName: String, limit: Int = 20): List<SocialProfileEntity>

    /**
     * Get profiles by workout range
     */
    @Query("""
        SELECT sp.* FROM social_profiles sp
        WHERE sp.workout_count >= :minWorkouts 
        AND sp.workout_count <= :maxWorkouts
        AND sp.is_private = 0
        ORDER BY sp.workout_count DESC
        LIMIT :limit
    """)
    suspend fun getProfilesByWorkoutRange(minWorkouts: Int, maxWorkouts: Int, limit: Int = 20): List<SocialProfileEntity>

    /**
     * Get recently active profiles
     */
    @Query("""
        SELECT sp.* FROM social_profiles sp
        WHERE sp.last_active > :sinceTimestamp
        AND sp.is_private = 0
        ORDER BY sp.last_active DESC
        LIMIT :limit
    """)
    suspend fun getRecentlyActiveProfiles(sinceTimestamp: Long, limit: Int = 20): List<SocialProfileEntity>

    /**
     * Get new users based on member_since
     */
    @Query("""
        SELECT sp.* FROM social_profiles sp
        WHERE sp.member_since > :sinceTimestamp
        AND sp.is_private = 0
        ORDER BY sp.member_since DESC
        LIMIT :limit
    """)
    suspend fun getNewUsers(sinceTimestamp: Long, limit: Int = 20): List<SocialProfileEntity>

    /**
     * Get most followed profiles for user suggestions
     */
    @Query("""
        SELECT sp.* FROM social_profiles sp
        WHERE sp.is_private = 0
        AND sp.hide_from_suggestions = 0
        ORDER BY sp.follower_count DESC
        LIMIT :limit
    """)
    suspend fun getMostFollowedProfiles(limit: Int = 20): List<SocialProfileEntity>

    // ========================================
    // Profile Management
    // ========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: SocialProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: SocialProfileEntity): Int

    @Query("""
        UPDATE social_profiles 
        SET display_name = :displayName, bio = :bio, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateBasicInfo(
        userId: String,
        displayName: String?,
        bio: String?,
        updatedAt: Long
    ): Int

    @Query("""
        UPDATE social_profiles 
        SET profile_photo_url = :photoUrl, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateProfilePhoto(userId: String, photoUrl: String?, updatedAt: Long): Int

    @Query("""
        UPDATE social_profiles 
        SET is_private = :isPrivate, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updatePrivacySetting(userId: String, isPrivate: Boolean, updatedAt: Long): Int

    // ========================================
    // Social Stats Updates
    // ========================================

    @Query("""
        UPDATE social_profiles 
        SET workout_count = :count, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateWorkoutCount(userId: String, count: Int, updatedAt: Long): Int

    @Query("""
        UPDATE social_profiles 
        SET follower_count = :count, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateFollowerCount(userId: String, count: Int, updatedAt: Long): Int

    @Query("""
        UPDATE social_profiles 
        SET following_count = :count, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateFollowingCount(userId: String, count: Int, updatedAt: Long): Int

    /**
     * Increment follower count by 1
     */
    @Query("""
        UPDATE social_profiles 
        SET follower_count = follower_count + 1, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun incrementFollowerCount(userId: String, updatedAt: Long = System.currentTimeMillis()): Int

    /**
     * Decrement follower count by 1
     */
    @Query("""
        UPDATE social_profiles 
        SET follower_count = CASE WHEN follower_count > 0 THEN follower_count - 1 ELSE 0 END, 
            updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun decrementFollowerCount(userId: String, updatedAt: Long = System.currentTimeMillis()): Int

    /**
     * Increment following count by 1
     */
    @Query("""
        UPDATE social_profiles 
        SET following_count = following_count + 1, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun incrementFollowingCount(userId: String, updatedAt: Long = System.currentTimeMillis()): Int

    /**
     * Decrement following count by 1
     */
    @Query("""
        UPDATE social_profiles 
        SET following_count = CASE WHEN following_count > 0 THEN following_count - 1 ELSE 0 END, 
            updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun decrementFollowingCount(userId: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("""
        UPDATE social_profiles 
        SET last_active = :lastActive, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateLastActive(userId: String, lastActive: Long, updatedAt: Long): Int

    // ========================================
    // Sync Management
    // ========================================

    @Query("SELECT * FROM social_profiles WHERE user_id = :userId AND is_synced = 0")
    suspend fun getUnsyncedProfiles(userId: String): List<SocialProfileEntity>

    @Query("""
        UPDATE social_profiles 
        SET is_synced = :isSynced, sync_version = :version
        WHERE user_id = :userId
    """)
    suspend fun updateSyncStatus(userId: String, isSynced: Boolean, version: Int): Int

    // ========================================
    // Profile Deletion
    // ========================================

    @Delete
    suspend fun deleteProfile(profile: SocialProfileEntity): Int

    @Query("DELETE FROM social_profiles WHERE user_id = :userId")
    suspend fun deleteProfileForUser(userId: String): Int

    // ========================================
    // Analytics Support
    // ========================================

    @Query("SELECT COUNT(*) FROM social_profiles WHERE user_id = :userId")
    suspend fun getProfileCount(userId: String): Int

    @Query("""
        SELECT AVG(follower_count) FROM social_profiles 
        WHERE user_id = :userId AND is_private = 0
    """)
    suspend fun getAverageFollowerCount(userId: String): Double?
    
    // ========================================
    // Debug Support
    // ========================================
    
    @Query("SELECT * FROM social_profiles ORDER BY created_at DESC LIMIT 10")
    suspend fun getAllProfilesForDebug(): List<SocialProfileEntity>
    
    @Query("SELECT COUNT(*) FROM social_profiles")
    suspend fun getTotalProfileCount(): Int

    // ========== OFFLINE-FIRST ARCHITECTURE METHODS (SPEC-20241228) ==========

    /**
     * Upsert socialprofile from LOCAL origin (user edit).
     * Sets isDirty=true and lastModified, triggering sync queue.
     */
    suspend fun upsertLocal(socialProfile: SocialProfileEntity) {
        val entity = socialProfile.copy(
            isDirty = true,
            lastModified = System.currentTimeMillis()
        )
        _insert(entity)
    }

    /**
     * Upsert socialprofile from REMOTE origin (Firestore listener/sync).
     * Sets isDirty=false, only applies if remote is newer.
     * Does NOT trigger sync queue.
     */
    @Transaction
    suspend fun upsertFromRemote(socialProfile: SocialProfileEntity) {
        val local = getSocialProfileForSync(socialProfile.userId)
        if (local == null || socialProfile.lastModified > local.lastModified) {
            val entity = socialProfile.copy(
                isDirty = false,
                isSynced = true,
                syncVersion = System.currentTimeMillis().toInt()
            )
            _insert(entity)
        }
    }

    /**
     * Internal insert for shared logic.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun _insert(entity: SocialProfileEntity)

    /**
     * Get dirty socialprofile that need upload to Firestore.
     */
    @Query("SELECT * FROM social_profiles WHERE user_id = :userId AND is_dirty = 1 ORDER BY last_modified ASC")
    suspend fun getDirtySocialProfiles(userId: String): List<SocialProfileEntity>

    /**
     * Mark socialprofile as clean after successful Firestore upload.
     */
    @Query("UPDATE social_profiles SET is_dirty = 0, is_synced = 1, sync_version = :syncVersion WHERE user_id IN (:ids) AND user_id = :userId")
    suspend fun markAsClean(ids: List<String>, userId: String, syncVersion: Long = System.currentTimeMillis()): Int

    /**
     * Get local socialprofile for remote deduplication.
     */
    @Query("SELECT * FROM social_profiles WHERE user_id = :userId LIMIT 1")
    suspend fun getSocialProfileForSync(userId: String): SocialProfileEntity?

    /**
     * IDEMPOTENT: Update social stats from REMOTE origin (Firestore listener).
     * Only applies if remote timestamp is newer, sets isDirty=false (no sync trigger).
     * Used by FollowRealtimeService for partial stat updates.
     */
    @Transaction
    suspend fun updateStatsFromRemote(
        userId: String,
        followerCount: Int,
        followingCount: Int,
        workoutCount: Int,
        remoteModified: Long
    ) {
        val local = getSocialProfileForSync(userId)

        // Only apply if remote is newer (or doesn't exist locally)
        if (local == null || remoteModified > local.lastModified) {
            _updateStatsInternal(
                userId = userId,
                followerCount = followerCount,
                followingCount = followingCount,
                workoutCount = workoutCount,
                lastModified = remoteModified,
                isDirty = false  // Don't sync back - already from Firestore
            )
        }
    }

    /**
     * Internal stat update - sets all stats + timestamp + dirty flag atomically.
     */
    @Query("""
        UPDATE social_profiles
        SET follower_count = :followerCount,
            following_count = :followingCount,
            workout_count = :workoutCount,
            last_modified = :lastModified,
            is_dirty = :isDirty,
            updated_at = :lastModified
        WHERE user_id = :userId
    """)
    suspend fun _updateStatsInternal(
        userId: String,
        followerCount: Int,
        followingCount: Int,
        workoutCount: Int,
        lastModified: Long,
        isDirty: Boolean
    )

    // ========== END OFFLINE-FIRST METHODS ==========
}
