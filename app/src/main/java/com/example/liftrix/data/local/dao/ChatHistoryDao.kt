package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.liftrix.data.local.entity.ChatHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for managing AI chatbot conversation history with user-scoped queries.
 * Provides methods for message storage, retrieval, usage tracking, and sync.
 */
@Dao
interface ChatHistoryDao {
    /**
     * Inserts a new chat message or replaces existing one.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatHistoryEntity)
    
    /**
     * Inserts multiple chat messages in a single batch operation.
     * Used for efficient bulk operations during sync processes.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatHistoryEntity>)
    
    /**
     * Observes all messages in a conversation for a specific user.
     * Returns messages ordered by creation time ascending.
     */
    @Query("""
        SELECT * FROM chat_history 
        WHERE user_id = :userId AND conversation_id = :conversationId 
        ORDER BY created_at ASC
    """)
    fun getConversationMessages(userId: String, conversationId: String): Flow<List<ChatHistoryEntity>>
    
    /**
     * Gets recent messages for a user.
     * Used for quick history access and context loading.
     */
    @Query("""
        SELECT * FROM chat_history 
        WHERE user_id = :userId 
        ORDER BY created_at DESC 
        LIMIT :limit
    """)
    suspend fun getRecentMessages(userId: String, limit: Int = 10): List<ChatHistoryEntity>
    
    /**
     * Counts messages sent by a user today.
     * Used for daily limit enforcement.
     */
    @Query("""
        SELECT COUNT(*) FROM chat_history 
        WHERE user_id = :userId 
        AND message_type = 'USER'
        AND created_at >= :todayStartTimestamp
    """)
    suspend fun getTodayMessageCount(userId: String, todayStartTimestamp: Long): Int
    
    /**
     * Calculates total token usage for a user in the current month.
     * Returns null if no messages with token counts exist.
     */
    @Query("""
        SELECT SUM(token_count) FROM chat_history 
        WHERE user_id = :userId 
        AND created_at >= :monthStartTimestamp
    """)
    suspend fun getMonthlyTokenUsage(userId: String, monthStartTimestamp: Long): Int?
    
    /**
     * Deletes old messages for a user based on cutoff timestamp.
     * Returns the number of deleted messages.
     */
    @Query("""
        DELETE FROM chat_history 
        WHERE user_id = :userId AND created_at < :cutoffTimestamp
    """)
    suspend fun deleteOldMessages(userId: String, cutoffTimestamp: Long): Int
    
    /**
     * Gets all unsynced messages for a user.
     * Used by sync workers to batch upload to Firebase.
     */
    @Query("""
        SELECT * FROM chat_history 
        WHERE user_id = :userId AND is_synced = 0 
        ORDER BY created_at ASC
    """)
    suspend fun getUnsyncedMessages(userId: String): List<ChatHistoryEntity>
    
    /**
     * Marks multiple messages as synced after successful Firebase upload.
     */
    @Query("""
        UPDATE chat_history 
        SET is_synced = 1, sync_version = :version 
        WHERE user_id = :userId AND id IN (:messageIds)
    """)
    suspend fun markMessagesAsSynced(userId: String, messageIds: List<String>, version: Int)
    
    /**
     * Gets distinct conversation IDs for a user.
     * Used for conversation listing and management.
     */
    @Query("""
        SELECT conversation_id FROM chat_history 
        WHERE user_id = :userId 
        GROUP BY conversation_id
        ORDER BY MAX(created_at) DESC
    """)
    suspend fun getConversationIds(userId: String): List<String>
    
    /**
     * Deletes all messages in a specific conversation.
     * Used for conversation cleanup.
     */
    @Query("""
        DELETE FROM chat_history 
        WHERE user_id = :userId AND conversation_id = :conversationId
    """)
    suspend fun deleteConversation(userId: String, conversationId: String): Int
    
    /**
     * Gets token usage since a specific timestamp.
     * Used for hourly rate limiting calculations.
     */
    @Query("""
        SELECT SUM(token_count) FROM chat_history 
        WHERE user_id = :userId 
        AND created_at >= :sinceTimestamp
    """)
    suspend fun getTokenUsageSince(userId: String, sinceTimestamp: Long): Int?
    
    /**
     * Gets hourly token usage for rate limiting.
     */
    @Query("""
        SELECT SUM(token_count) FROM chat_history 
        WHERE user_id = :userId 
        AND created_at >= :hourAgoTimestamp
    """)
    suspend fun getHourlyTokenUsage(userId: String, hourAgoTimestamp: Long): Int?
}