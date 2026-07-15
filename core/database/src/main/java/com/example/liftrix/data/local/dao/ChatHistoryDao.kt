package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.liftrix.data.local.entity.ChatHistoryEntity
import com.example.liftrix.data.local.entity.ChatConversationEntity
import com.example.liftrix.data.local.dto.ChatConversationSummaryRow
import com.example.liftrix.annotations.UserScoped
import com.example.liftrix.domain.usecase.chat.ChatConversationDeletionPolicy
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
    suspend fun insertMessage(message: ChatHistoryEntity) = upsertLocal(message)
    
    /**
     * Inserts multiple chat messages in a single batch operation.
     * Used for efficient bulk operations during sync processes.
     */
    @Transaction
    suspend fun insertMessages(messages: List<ChatHistoryEntity>) {
        messages.forEach { upsertLocal(it) }
    }
    
    /**
     * Observes all messages in a conversation for a specific user.
     * Returns messages ordered by creation time ascending.
     */
    @Query("""
        SELECT * FROM chat_history 
        WHERE user_id = :userId AND conversation_id = :conversationId 
        AND NOT EXISTS (
            SELECT 1 FROM chat_conversations c
            WHERE c.user_id = :userId AND c.conversation_id = :conversationId
            AND c.deleted_at IS NOT NULL
        )
        ORDER BY created_at ASC,
            CASE message_type WHEN 'USER' THEN 0 WHEN 'AI_RESPONSE' THEN 1 ELSE 2 END ASC,
            id ASC
    """)
    @UserScoped
    fun getConversationMessages(userId: String, conversationId: String): Flow<List<ChatHistoryEntity>>

    @Query("""
        SELECT h.conversation_id,
            COALESCE(NULLIF(c.title, ''), 'New chat') AS title,
            SUBSTR((SELECT newest.content FROM chat_history newest
                WHERE newest.user_id = :userId AND newest.conversation_id = h.conversation_id
                ORDER BY newest.created_at DESC,
                    CASE newest.message_type WHEN 'AI_RESPONSE' THEN 0 WHEN 'USER' THEN 1 ELSE 2 END,
                    newest.id DESC LIMIT 1), 1, 160) AS last_message_preview,
            MAX(h.created_at) AS last_updated_at,
            COUNT(*) AS message_count,
            COALESCE(c.is_title_custom, 0) AS is_title_custom
        FROM chat_history h
        LEFT JOIN chat_conversations c
            ON c.user_id = h.user_id AND c.conversation_id = h.conversation_id
        WHERE h.user_id = :userId AND c.deleted_at IS NULL
        GROUP BY h.conversation_id
        ORDER BY last_updated_at DESC, h.conversation_id ASC
    """)
    @UserScoped
    fun observeConversationSummaries(userId: String): Flow<List<ChatConversationSummaryRow>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertConversationIfAbsent(conversation: ChatConversationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ChatConversationEntity)

    @Query("SELECT * FROM chat_conversations WHERE user_id = :userId ORDER BY updated_at ASC, conversation_id ASC")
    @UserScoped
    suspend fun getConversationMetadataForSync(userId: String): List<ChatConversationEntity>

    @Query("""
        UPDATE chat_conversations SET title = :title, is_title_custom = 1, updated_at = :updatedAt
        WHERE user_id = :userId AND conversation_id = :conversationId AND deleted_at IS NULL
    """)
    @UserScoped
    suspend fun renameConversation(userId: String, conversationId: String, title: String, updatedAt: Long): Int
    
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
    suspend fun deleteConversationMessages(userId: String, conversationId: String): Int

    @Query("SELECT * FROM chat_conversations WHERE user_id = :userId AND conversation_id = :conversationId LIMIT 1")
    @UserScoped
    suspend fun getConversationMetadata(userId: String, conversationId: String): ChatConversationEntity?

    @Query("SELECT * FROM chat_conversations WHERE user_id = :userId AND conversation_id = :clearAllConversationId LIMIT 1")
    @UserScoped
    suspend fun getClearAllTombstone(
        userId: String,
        clearAllConversationId: String = ChatConversationDeletionPolicy.ALL_HISTORY_CONVERSATION_ID
    ): ChatConversationEntity?

    @Transaction
    suspend fun tombstoneAndDeleteConversation(userId: String, conversationId: String, now: Long): Int {
        ChatConversationDeletionPolicy.requireRealConversationId(conversationId)
        val existing = getConversationMetadata(userId, conversationId)
        upsertConversation(
            (existing ?: ChatConversationEntity(userId, conversationId, "New chat", createdAt = now, updatedAt = now))
                .copy(updatedAt = now, deletedAt = now)
        )
        return deleteConversationMessages(userId, conversationId)
    }

    @Transaction
    suspend fun clearAllHistoryWithTombstone(userId: String, now: Long): Int {
        val existing = getClearAllTombstone(userId)
        upsertConversation(
            (existing ?: ChatConversationEntity(
                userId = userId,
                conversationId = ChatConversationDeletionPolicy.ALL_HISTORY_CONVERSATION_ID,
                title = ChatConversationDeletionPolicy.ALL_HISTORY_TOMBSTONE_TITLE,
                createdAt = now,
                updatedAt = now
            )).copy(updatedAt = now, deletedAt = now)
        )
        return deleteMessagesAtOrBefore(userId, now)
    }

    @Transaction
    suspend fun upsertConversationFromRemote(conversation: ChatConversationEntity) {
        val local = getConversationMetadata(conversation.userId, conversation.conversationId)
        if (local != null && local.updatedAt >= conversation.updatedAt) {
            local.deletedAt?.let { deletedAt ->
                if (ChatConversationDeletionPolicy.isReservedConversationId(local.conversationId)) {
                    deleteMessagesAtOrBefore(local.userId, deletedAt)
                } else {
                    deleteConversationMessages(local.userId, local.conversationId)
                }
            }
            return
        }

        upsertConversation(conversation)
        conversation.deletedAt?.let { deletedAt ->
            if (ChatConversationDeletionPolicy.isReservedConversationId(conversation.conversationId)) {
                deleteMessagesAtOrBefore(conversation.userId, deletedAt)
            } else {
                deleteConversationMessages(conversation.userId, conversation.conversationId)
            }
        }
    }

    @Transaction
    suspend fun insertMessageWithConversation(
        message: ChatHistoryEntity,
        title: String,
        retentionDays: Int = 30
    ) {
        ChatConversationDeletionPolicy.requireRealConversationId(message.conversationId)
        insertConversationIfAbsent(
            ChatConversationEntity(
                userId = message.userId,
                conversationId = message.conversationId,
                title = title,
                createdAt = message.createdAt,
                updatedAt = message.createdAt
            )
        )
        upsertLocal(message, retentionDays)
    }
    
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
    
    /**
     * Deletes ALL chat history for a user.
     * Used by settings screen for complete history clear.
     * Returns the number of deleted messages.
     */
    @Query("DELETE FROM chat_history WHERE user_id = :userId AND created_at <= :cutoffTimestamp")
    @UserScoped
    suspend fun deleteMessagesAtOrBefore(userId: String, cutoffTimestamp: Long): Int
    
    /**
     * Gets all chat messages for a user for export purposes.
     * Ordered by conversation and creation time.
     */
    @Query("""
        SELECT * FROM chat_history 
        WHERE user_id = :userId 
        ORDER BY conversation_id, created_at ASC
    """)
    suspend fun getAllMessagesForExport(userId: String): List<ChatHistoryEntity>
    
    /**
     * Gets total message count for a user.
     * Used for storage statistics display.
     */
    @Query("SELECT COUNT(*) FROM chat_history WHERE user_id = :userId")
    suspend fun getTotalMessageCount(userId: String): Int
    
    /**
     * Gets total token usage across all conversations for a user.
     * Used for usage statistics display.
     */
    @Query("SELECT SUM(token_count) FROM chat_history WHERE user_id = :userId")
    suspend fun getTotalTokenUsage(userId: String): Int?

    // ========== OFFLINE-FIRST ARCHITECTURE METHODS (SPEC-20241228) ==========

    /**
     * Upsert chathistory from LOCAL origin (user edit).
     * Sets isDirty=true and lastModified, triggering sync queue.
     * Calculates expires_at based on RETENTION_DAYS (default: 30 days).
     */
    suspend fun upsertLocal(chatHistory: ChatHistoryEntity, retentionDays: Int = 30) {
        val currentTime = System.currentTimeMillis()
        val expiresAt = currentTime + (retentionDays * 24 * 60 * 60 * 1000L)
        val entity = chatHistory.copy(
            isDirty = true,
            isSynced = false,
            lastModified = currentTime,
            expiresAt = expiresAt
        )
        _insert(entity)
    }

    /**
     * Upsert chathistory from REMOTE origin (Firestore listener/sync).
     * Sets isDirty=false, only applies if remote is newer.
     * Does NOT trigger sync queue.
     */
    @Transaction
    suspend fun upsertFromRemote(chatHistory: ChatHistoryEntity) {
        val conversation = getConversationMetadata(chatHistory.userId, chatHistory.conversationId)
        val allHistoryTombstone = getClearAllTombstone(chatHistory.userId)
        if (ChatConversationDeletionPolicy.shouldSuppressMessage(
                conversationId = chatHistory.conversationId,
                messageCreatedAt = chatHistory.createdAt,
                conversationDeletedAt = conversation?.deletedAt,
                allHistoryDeletedAt = allHistoryTombstone?.deletedAt
            )
        ) {
            return
        }
        val local = getChatHistoryForSync(chatHistory.id, chatHistory.userId)
        if (local == null || chatHistory.lastModified > local.lastModified) {
            val entity = chatHistory.copy(
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
    suspend fun _insert(entity: ChatHistoryEntity)

    /**
     * Get dirty chathistory that need upload to Firestore.
     */
    @Query("SELECT * FROM chat_history WHERE user_id = :userId AND is_dirty = 1 ORDER BY last_modified ASC")
    suspend fun getDirtyChatHistories(userId: String): List<ChatHistoryEntity>

    /**
     * Mark chathistory as clean after successful Firestore upload.
     */
    @Query("UPDATE chat_history SET is_dirty = 0, is_synced = 1, sync_version = :syncVersion WHERE id IN (:ids) AND user_id = :userId")
    suspend fun markAsClean(ids: List<String>, userId: String, syncVersion: Long = System.currentTimeMillis()): Int

    /**
     * Get local chathistory for remote deduplication.
     */
    @Query("SELECT * FROM chat_history WHERE id = :id AND user_id = :userId LIMIT 1")
    suspend fun getChatHistoryForSync(id: String, userId: String): ChatHistoryEntity?

    // ========== END OFFLINE-FIRST METHODS ==========

    // ========== USER-SCOPED RETENTION METHODS ==========

    @Query("""
        SELECT * FROM chat_history
        WHERE user_id = :userId
        AND expires_at IS NOT NULL
        AND expires_at < :currentTime
        ORDER BY expires_at ASC
        LIMIT :limit
    """)
    @UserScoped
    suspend fun getExpiredMessages(
        userId: String,
        currentTime: Long = System.currentTimeMillis(),
        limit: Int = 20
    ): List<ChatHistoryEntity>

    @Query("DELETE FROM chat_history WHERE user_id = :userId AND id IN (:messageIds)")
    @UserScoped
    suspend fun deleteMessagesByIds(userId: String, messageIds: List<String>): Int

    // ========== END USER-SCOPED RETENTION METHODS ==========
}
