package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.liftrix.data.local.entity.ProfileViewEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for profile view tracking operations with mandatory user scoping.
 * Part of user profiles and follow system from SPEC-20250113-user-profiles-follow.
 * 
 * CRITICAL SECURITY: All queries MUST include userId filtering to prevent data leakage.
 */
@Dao
interface ProfileViewDao {

    // ========================================
    // Profile View Tracking
    // ========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfileView(profileView: ProfileViewEntity): Long

    @Query("""
        SELECT * FROM profile_views 
        WHERE viewer_id = :viewerId
        ORDER BY viewed_at DESC
        LIMIT :limit
    """)
    suspend fun getRecentViews(viewerId: String, limit: Int = 50): List<ProfileViewEntity>

    @Query("""
        SELECT * FROM profile_views 
        WHERE profile_id = :profileId
        ORDER BY viewed_at DESC
        LIMIT :limit
    """)
    suspend fun getProfileViews(profileId: String, limit: Int = 50): List<ProfileViewEntity>

    @Query("""
        SELECT COUNT(*) FROM profile_views 
        WHERE profile_id = :profileId
        AND viewed_at >= :sinceTimestamp
    """)
    suspend fun getViewCount(profileId: String, sinceTimestamp: Long): Int

    @Query("""
        SELECT COUNT(DISTINCT viewer_id) FROM profile_views 
        WHERE profile_id = :profileId
        AND viewed_at >= :sinceTimestamp
    """)
    suspend fun getUniqueViewerCount(profileId: String, sinceTimestamp: Long): Int

    // ========================================
    // Analytics Queries
    // ========================================

    @Query("""
        SELECT view_source, COUNT(*) as count FROM profile_views 
        WHERE profile_id = :profileId
        AND viewed_at >= :sinceTimestamp
        GROUP BY view_source
        ORDER BY count DESC
    """)
    suspend fun getViewsBySource(profileId: String, sinceTimestamp: Long): List<ViewSourceCount>

    @Query("""
        SELECT AVG(view_duration_ms) FROM profile_views 
        WHERE profile_id = :profileId
        AND view_duration_ms IS NOT NULL
        AND viewed_at >= :sinceTimestamp
    """)
    suspend fun getAverageViewDuration(profileId: String, sinceTimestamp: Long): Double?

    @Query("""
        SELECT interaction_type, COUNT(*) as count FROM profile_views 
        WHERE profile_id = :profileId
        AND interaction_type IS NOT NULL
        AND viewed_at >= :sinceTimestamp
        GROUP BY interaction_type
        ORDER BY count DESC
    """)
    suspend fun getInteractionTypes(profileId: String, sinceTimestamp: Long): List<InteractionTypeCount>

    // ========================================
    // Suggestion Support Queries
    // ========================================

    @Query("""
        SELECT profile_id, COUNT(*) as view_count FROM profile_views 
        WHERE viewer_id = :viewerId
        AND viewed_at >= :sinceTimestamp
        GROUP BY profile_id
        ORDER BY view_count DESC, viewed_at DESC
        LIMIT :limit
    """)
    suspend fun getFrequentlyViewedProfiles(
        viewerId: String, 
        sinceTimestamp: Long,
        limit: Int = 10
    ): List<FrequentlyViewedProfile>

    @Query("""
        SELECT DISTINCT profile_id FROM profile_views 
        WHERE viewer_id = :viewerId
        AND viewed_at >= :recentTimestamp
        AND profile_id NOT IN (
            SELECT following_id FROM follow_relationships 
            WHERE follower_id = :viewerId AND status = 'ACCEPTED'
        )
        ORDER BY viewed_at DESC
        LIMIT :limit
    """)
    suspend fun getRecentlyViewedUnfollowedProfiles(
        viewerId: String,
        recentTimestamp: Long,
        limit: Int = 5
    ): List<String>

    // ========================================
    // Reactive Queries
    // ========================================

    @Query("""
        SELECT COUNT(*) FROM profile_views 
        WHERE profile_id = :profileId
        AND viewed_at >= :sinceTimestamp
    """)
    fun observeViewCount(profileId: String, sinceTimestamp: Long): Flow<Int>

    // ========================================
    // Cleanup Operations
    // ========================================

    @Query("""
        DELETE FROM profile_views 
        WHERE viewed_at < :cutoffTimestamp
    """)
    suspend fun deleteOldViews(cutoffTimestamp: Long): Int

    @Query("""
        DELETE FROM profile_views 
        WHERE viewer_id = :viewerId
    """)
    suspend fun deleteViewsForUser(viewerId: String): Int

    // ========================================
    // Sync Support
    // ========================================

    @Query("""
        SELECT * FROM profile_views 
        WHERE viewer_id = :viewerId 
        AND is_synced = 0
        ORDER BY viewed_at DESC
        LIMIT :limit
    """)
    suspend fun getUnsyncedViews(viewerId: String, limit: Int = 100): List<ProfileViewEntity>

    @Query("""
        UPDATE profile_views 
        SET is_synced = :isSynced, sync_version = :version
        WHERE id = :viewId
    """)
    suspend fun updateSyncStatus(viewId: String, isSynced: Boolean, version: Int): Int

    // ========== OFFLINE-FIRST ARCHITECTURE METHODS (SPEC-20241228) ==========

    /**
     * Upsert profileview from LOCAL origin (user edit).
     * Sets isDirty=true and lastModified, triggering sync queue.
     */
    suspend fun upsertLocal(profileView: ProfileViewEntity) {
        val entity = profileView.copy(
            isDirty = true,
            lastModified = System.currentTimeMillis()
        )
        _insert(entity)
    }

    /**
     * Upsert profileview from REMOTE origin (Firestore listener/sync).
     * Sets isDirty=false, only applies if remote is newer.
     * Does NOT trigger sync queue.
     */
    @Transaction
    suspend fun upsertFromRemote(profileView: ProfileViewEntity) {
        val local = getProfileViewForSync(profileView.id, profileView.viewerId)
        if (local == null || profileView.lastModified > local.lastModified) {
            val entity = profileView.copy(
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
    suspend fun _insert(entity: ProfileViewEntity)

    /**
     * Get dirty profileview that need upload to Firestore.
     */
    @Query("SELECT * FROM profile_views WHERE viewer_id = :viewerId AND is_dirty = 1 ORDER BY last_modified ASC")
    suspend fun getDirtyProfileViews(viewerId: String): List<ProfileViewEntity>

    /**
     * Mark profileview as clean after successful Firestore upload.
     */
    @Query("UPDATE profile_views SET is_dirty = 0, is_synced = 1, sync_version = :syncVersion WHERE id IN (:ids) AND viewer_id = :viewerId")
    suspend fun markAsClean(ids: List<String>, viewerId: String, syncVersion: Long = System.currentTimeMillis()): Int

    /**
     * Get local profileview for remote deduplication.
     */
    @Query("SELECT * FROM profile_views WHERE id = :id AND viewer_id = :viewerId LIMIT 1")
    suspend fun getProfileViewForSync(id: String, viewerId: String): ProfileViewEntity?

    // ========== END OFFLINE-FIRST METHODS ==========
}

/**
 * Data classes for analytics results
 */
data class ViewSourceCount(
    val view_source: String,
    val count: Int
)

data class InteractionTypeCount(
    val interaction_type: String,
    val count: Int
)

data class FrequentlyViewedProfile(
    val profile_id: String,
    val view_count: Int
)
