package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
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
}