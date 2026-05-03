package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.example.liftrix.data.local.entity.FriendEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {
    
    @Query("SELECT * FROM friends WHERE user_id = :userId AND status = 'ACCEPTED' ORDER BY created_at DESC")
    fun getFriends(userId: String): Flow<List<FriendEntity>>
    
    @Query("SELECT * FROM friends WHERE user_id = :userId AND status = :status ORDER BY created_at DESC")
    fun getFriendsByStatus(userId: String, status: String): Flow<List<FriendEntity>>
    
    @Query("SELECT * FROM friends WHERE user_id = :userId AND status = 'PENDING' ORDER BY created_at DESC")
    fun getPendingFriendRequests(userId: String): Flow<List<FriendEntity>>
    
    @Query("SELECT * FROM friends WHERE friend_user_id = :userId AND status = 'PENDING' ORDER BY created_at DESC")
    fun getIncomingFriendRequests(userId: String): Flow<List<FriendEntity>>
    
    @Query("SELECT * FROM friends WHERE user_id = :userId AND friend_user_id = :friendUserId")
    suspend fun getFriendRelationship(userId: String, friendUserId: String): FriendEntity?
    
    @Query("SELECT * FROM friends WHERE (user_id = :userId AND friend_user_id = :friendUserId) OR (user_id = :friendUserId AND friend_user_id = :userId)")
    suspend fun getBidirectionalFriendRelationship(userId: String, friendUserId: String): List<FriendEntity>
    
    @Query("SELECT COUNT(*) FROM friends WHERE user_id = :userId AND status = 'ACCEPTED'")
    suspend fun getFriendCount(userId: String): Int
    
    @Query("SELECT COUNT(*) FROM friends WHERE friend_user_id = :userId AND status = 'PENDING'")
    suspend fun getPendingRequestCount(userId: String): Int
    
    @Query("SELECT * FROM friends WHERE is_synced = 0 AND user_id = :userId ORDER BY updated_at ASC")
    suspend fun getUnsyncedFriendsForUser(userId: String): List<FriendEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriend(friend: FriendEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriends(friends: List<FriendEntity>): List<Long>
    
    @Update
    suspend fun updateFriend(friend: FriendEntity): Int
    
    @Query("UPDATE friends SET status = :status, updated_at = :updatedAt WHERE user_id = :userId AND friend_user_id = :friendUserId")
    suspend fun updateFriendStatus(userId: String, friendUserId: String, status: String, updatedAt: Long): Int
    
    @Query("UPDATE friends SET is_synced = :isSynced WHERE user_id = :userId AND friend_user_id = :friendUserId")
    suspend fun updateSyncStatusForUser(userId: String, friendUserId: String, isSynced: Boolean): Int
    
    @Query("UPDATE friends SET is_synced = 1 WHERE user_id = :userId AND friend_user_id IN (:friendUserIds)")
    suspend fun markFriendsAsSyncedForUser(userId: String, friendUserIds: List<String>): Int
    
    @Delete
    suspend fun deleteFriend(friend: FriendEntity): Int
    
    @Query("DELETE FROM friends WHERE user_id = :userId AND friend_user_id = :friendUserId")
    suspend fun deleteFriendRelationship(userId: String, friendUserId: String): Int
    
    @Query("DELETE FROM friends WHERE (user_id = :userId AND friend_user_id = :friendUserId) OR (user_id = :friendUserId AND friend_user_id = :userId)")
    suspend fun deleteBidirectionalFriendRelationship(userId: String, friendUserId: String): Int
    
    @Query("DELETE FROM friends WHERE user_id = :userId")
    suspend fun deleteAllFriendsForUser(userId: String): Int
    
    // Following/Followers distinction methods
    
    @Query("SELECT * FROM friends WHERE user_id = :userId AND status = 'ACCEPTED' ORDER BY created_at DESC")
    fun getFollowing(userId: String): Flow<List<FriendEntity>>
    
    @Query("SELECT * FROM friends WHERE friend_user_id = :userId AND status = 'ACCEPTED' ORDER BY created_at DESC")
    fun getFollowers(userId: String): Flow<List<FriendEntity>>
    
    @Query("SELECT COUNT(*) FROM friends WHERE user_id = :userId AND status = 'ACCEPTED'")
    suspend fun getFollowingCount(userId: String): Int
    
    @Query("SELECT COUNT(*) FROM friends WHERE friend_user_id = :userId AND status = 'ACCEPTED'")
    suspend fun getFollowersCount(userId: String): Int

    // ========== OFFLINE-FIRST ARCHITECTURE METHODS (SPEC-20241228) ==========

    /**
     * Upsert friend from LOCAL origin (user edit).
     * Sets isDirty=true and lastModified, triggering sync queue.
     */
    suspend fun upsertLocal(friend: FriendEntity) {
        val entity = friend.copy(
            isDirty = true,
            lastModified = System.currentTimeMillis()
        )
        _insert(entity)
    }

    /**
     * Upsert friend from REMOTE origin (Firestore listener/sync).
     * Sets isDirty=false, only applies if remote is newer.
     * Does NOT trigger sync queue.
     */
    @Transaction
    suspend fun upsertFromRemote(friend: FriendEntity) {
        val local = getFriendForSync(friend.userId, friend.friendUserId)
        if (local == null || friend.lastModified > local.lastModified) {
            val entity = friend.copy(
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
    suspend fun _insert(entity: FriendEntity)

    /**
     * Get dirty friend that need upload to Firestore.
     */
    @Query("SELECT * FROM friends WHERE user_id = :userId AND is_dirty = 1 ORDER BY last_modified ASC")
    suspend fun getDirtyFriends(userId: String): List<FriendEntity>

    /**
     * Mark friend as clean after successful Firestore upload.
     */
    @Query("UPDATE friends SET is_dirty = 0, is_synced = 1 WHERE user_id = :userId AND friend_user_id IN (:ids)")
    suspend fun markAsClean(ids: List<String>, userId: String): Int

    /**
     * Get local friend for remote deduplication.
     */
    @Query("SELECT * FROM friends WHERE user_id = :userId AND friend_user_id = :friendUserId LIMIT 1")
    suspend fun getFriendForSync(userId: String, friendUserId: String): FriendEntity?

    // ========== END OFFLINE-FIRST METHODS ==========
} 
