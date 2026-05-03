package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Transaction
import com.example.liftrix.data.local.entity.UserProfileCacheEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for User Profile Cache operations with mandatory user scoping.
 * Part of Phase 1: User Profile Data Integration from SPEC-20250901-todo-implementation.
 * 
 * CRITICAL SECURITY: All queries MUST include userId filtering to prevent data leakage.
 */
@Dao
interface UserProfileCacheDao {
    
    /**
     * Inserts or updates user profile cache entry.
     * Uses REPLACE strategy for efficient upsert operation.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfileCache(profileCache: UserProfileCacheEntity)
    
    /**
     * Upserts user profile cache entry using Room's @Upsert annotation.
     */
    @Upsert
    suspend fun upsertProfileCache(profileCache: UserProfileCacheEntity)
    
    /**
     * Gets user profile cache by user ID.
     * @param userId The user ID to get profile cache for (mandatory user scoping)
     * @return UserProfileCacheEntity or null if not found
     */
    @Query("SELECT * FROM user_profile_cache WHERE user_id = :userId")
    suspend fun getProfileCache(userId: String): UserProfileCacheEntity?
    
    /**
     * Observes user profile cache changes for a specific user.
     * @param userId The user ID to observe (mandatory user scoping)
     * @return Flow of UserProfileCacheEntity or null if not found
     */
    @Query("SELECT * FROM user_profile_cache WHERE user_id = :userId")
    fun observeProfileCache(userId: String): Flow<UserProfileCacheEntity?>
    
    /**
     * Gets all cached profile entries for batch operations.
     * Used for bulk sync operations with proper user context.
     * @return List of all cached profile entries
     */
    @Query("SELECT * FROM user_profile_cache ORDER BY last_modified DESC")
    suspend fun getAllProfileCaches(): List<UserProfileCacheEntity>
    
    /**
     * Gets expired cache entries based on timestamp.
     * @param expirationTimestamp Timestamp before which entries are considered expired
     * @return List of expired cache entries
     */
    @Query("SELECT * FROM user_profile_cache WHERE cache_timestamp < :expirationTimestamp")
    suspend fun getExpiredCaches(expirationTimestamp: Long): List<UserProfileCacheEntity>
    
    /**
     * Deletes user profile cache for a specific user.
     * @param userId The user ID to delete cache for (mandatory user scoping)
     */
    @Query("DELETE FROM user_profile_cache WHERE user_id = :userId")
    suspend fun deleteProfileCache(userId: String)
    
    /**
     * Deletes expired cache entries to manage storage.
     * @param expirationTimestamp Timestamp before which entries are considered expired
     * @return Number of deleted entries
     */
    @Query("DELETE FROM user_profile_cache WHERE cache_timestamp < :expirationTimestamp")
    suspend fun deleteExpiredCaches(expirationTimestamp: Long): Int
    
    /**
     * Clears all cached profile data.
     * Used for testing and complete cache invalidation.
     */
    @Query("DELETE FROM user_profile_cache")
    suspend fun clearAllCache()
    
    /**
     * Gets cache statistics for monitoring.
     * @return Total number of cached entries
     */
    @Query("SELECT COUNT(*) FROM user_profile_cache")
    suspend fun getCacheCount(): Int
    
    /**
     * Gets cache entries that need to be synced.
     * @return List of unsynced cache entries
     */
    @Query("SELECT * FROM user_profile_cache WHERE is_synced = 0 ORDER BY last_modified ASC")
    suspend fun getUnsyncedCaches(): List<UserProfileCacheEntity>
    
    /**
     * Marks cache entry as synced.
     * @param userId The user ID to mark as synced (mandatory user scoping)
     * @param syncVersion The sync version to set
     */
    @Query("UPDATE user_profile_cache SET is_synced = 1, sync_version = :syncVersion WHERE user_id = :userId")
    suspend fun markAsSynced(userId: String, syncVersion: Long)
    
    /**
     * Updates cache timestamp for a specific user.
     * @param userId The user ID to update timestamp for (mandatory user scoping)
     * @param timestamp The new cache timestamp
     */
    @Query("UPDATE user_profile_cache SET cache_timestamp = :timestamp WHERE user_id = :userId")
    suspend fun updateCacheTimestamp(userId: String, timestamp: Long)

    // ========== OFFLINE-FIRST ARCHITECTURE METHODS (SPEC-20241228) ==========

    /**
     * Upsert userprofilecache from LOCAL origin (user edit).
     * Sets isDirty=true and lastModified, triggering sync queue.
     */
    suspend fun upsertLocal(userProfileCache: UserProfileCacheEntity) {
        val entity = userProfileCache.copy(
            isDirty = true,
            lastModified = System.currentTimeMillis()
        )
        _insert(entity)
    }

    /**
     * Upsert userprofilecache from REMOTE origin (Firestore listener/sync).
     * Sets isDirty=false, only applies if remote is newer.
     * Does NOT trigger sync queue.
     */
    @Transaction
    suspend fun upsertFromRemote(userProfileCache: UserProfileCacheEntity) {
        val local = getUserProfileCacheForSync(userProfileCache.userId)
        if (local == null || userProfileCache.lastModified > local.lastModified) {
            val entity = userProfileCache.copy(
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
    suspend fun _insert(entity: UserProfileCacheEntity)

    /**
     * Get dirty userprofilecache that need upload to Firestore.
     */
    @Query("SELECT * FROM user_profile_cache WHERE user_id = :userId AND is_dirty = 1 ORDER BY last_modified ASC")
    suspend fun getDirtyUserProfileCaches(userId: String): List<UserProfileCacheEntity>

    /**
     * Mark userprofilecache as clean after successful Firestore upload.
     */
    @Query("UPDATE user_profile_cache SET is_dirty = 0, is_synced = 1, sync_version = :syncVersion WHERE user_id IN (:ids) AND user_id = :userId")
    suspend fun markAsClean(ids: List<String>, userId: String, syncVersion: Long = System.currentTimeMillis()): Int

    /**
     * Get local userprofilecache for remote deduplication.
     */
    @Query("SELECT * FROM user_profile_cache WHERE user_id = :userId LIMIT 1")
    suspend fun getUserProfileCacheForSync(userId: String): UserProfileCacheEntity?

    // ========== END OFFLINE-FIRST METHODS ==========
}
