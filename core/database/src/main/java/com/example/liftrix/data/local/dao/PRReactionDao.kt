package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Transaction
import com.example.liftrix.data.local.entity.PRReactionEntity
import kotlinx.coroutines.flow.Flow

data class ReactionCount(
    @androidx.room.ColumnInfo(name = "reaction_type") val reactionType: String,
    @androidx.room.ColumnInfo(name = "count") val count: Int
)

/**
 * DAO for PR Reaction operations with mandatory user scoping.
 * Handles PR celebration reactions between gym buddies.
 * 
 * CRITICAL SECURITY: All queries MUST include userId filtering to prevent data leakage.
 */
@Dao
interface PRReactionDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReaction(reaction: PRReactionEntity): Long
    
    @Delete
    suspend fun deleteReaction(reaction: PRReactionEntity)
    
    @Query("SELECT * FROM pr_reactions WHERE pr_id = :prId ORDER BY timestamp DESC")
    fun getReactionsForPR(prId: String): Flow<List<PRReactionEntity>>
    
    @Query("""
        SELECT reaction_type, COUNT(*) as count 
        FROM pr_reactions 
        WHERE pr_id = :prId 
        GROUP BY reaction_type
    """)
    suspend fun getReactionCounts(prId: String): List<ReactionCount>
    
    @Query("SELECT EXISTS(SELECT 1 FROM pr_reactions WHERE user_id = :userId AND pr_id = :prId)")
    suspend fun hasUserReacted(userId: String, prId: String): Boolean
    
    @Query("DELETE FROM pr_reactions WHERE user_id = :userId AND pr_id = :prId")
    suspend fun removeUserReaction(userId: String, prId: String)
    
    @Query("""
        SELECT r.* 
        FROM pr_reactions r 
        WHERE r.user_id = :userId 
        AND r.timestamp > :sinceTimestamp 
        ORDER BY r.timestamp DESC
    """)
    fun getUserReactions(userId: String, sinceTimestamp: Long): Flow<List<PRReactionEntity>>

    // ========== OFFLINE-FIRST ARCHITECTURE METHODS (SPEC-20241228) ==========

    /**
     * Upsert prreaction from LOCAL origin (user edit).
     * Sets isDirty=true and lastModified, triggering sync queue.
     */
    suspend fun upsertLocal(pRReaction: PRReactionEntity) {
        val entity = pRReaction.copy(
            isDirty = true,
            lastModified = System.currentTimeMillis()
        )
        _insert(entity)
    }

    /**
     * Upsert prreaction from REMOTE origin (Firestore listener/sync).
     * Sets isDirty=false, only applies if remote is newer.
     * Does NOT trigger sync queue.
     */
    @Transaction
    suspend fun upsertFromRemote(pRReaction: PRReactionEntity) {
        val local = getPRReactionForSync(pRReaction.id, pRReaction.userId)
        if (local == null || pRReaction.lastModified > local.lastModified) {
            val entity = pRReaction.copy(
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
    suspend fun _insert(entity: PRReactionEntity)

    /**
     * Get dirty prreaction that need upload to Firestore.
     */
    @Query("SELECT * FROM pr_reactions WHERE user_id = :userId AND is_dirty = 1 ORDER BY last_modified ASC")
    suspend fun getDirtyPRReactions(userId: String): List<PRReactionEntity>

    /**
     * Mark prreaction as clean after successful Firestore upload.
     */
    @Query("UPDATE pr_reactions SET is_dirty = 0, is_synced = 1, sync_version = :syncVersion WHERE id IN (:ids) AND user_id = :userId")
    suspend fun markAsClean(ids: List<String>, userId: String, syncVersion: Long = System.currentTimeMillis()): Int

    /**
     * Get local prreaction for remote deduplication.
     */
    @Query("SELECT * FROM pr_reactions WHERE id = :id AND user_id = :userId LIMIT 1")
    suspend fun getPRReactionForSync(id: String, userId: String): PRReactionEntity?

    // ========== END OFFLINE-FIRST METHODS ==========
}
