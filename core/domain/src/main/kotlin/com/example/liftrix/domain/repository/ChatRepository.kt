package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.chat.ChatMessage
import com.example.liftrix.domain.model.chat.ChatConversation
import com.example.liftrix.domain.model.chat.ChatPreferences
import com.example.liftrix.domain.model.chat.MessageType
import com.example.liftrix.domain.model.chat.UsageLimits
import com.example.liftrix.domain.model.chat.WorkoutContext
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for chat-related operations.
 */
interface ChatRepository {
    /**
     * Saves a chat message to the database.
     */
    suspend fun saveMessage(
        messageId: String,
        userId: String,
        message: String,
        type: MessageType,
        conversationId: String,
        language: String = "en",
        workoutContext: WorkoutContext? = null,
        tokenCount: Int? = null,
        processingTimeMs: Long? = null,
        titleSeed: String? = null
    ): LiftrixResult<ChatMessage>

    fun observeConversations(userId: String): Flow<List<ChatConversation>>

    suspend fun renameConversation(userId: String, conversationId: String, title: String): LiftrixResult<Unit>
    
    /**
     * Observes messages in a conversation.
     */
    fun observeConversation(
        userId: String,
        conversationId: String
    ): Flow<List<ChatMessage>>
    
    /**
     * Gets recent messages for a user.
     */
    suspend fun getRecentMessages(
        userId: String,
        limit: Int = 10
    ): LiftrixResult<List<ChatMessage>>
    
    /**
     * Checks current usage against limits.
     */
    suspend fun checkUsageLimits(userId: String): LiftrixResult<UsageLimits>
    
    /**
     * Gets chat preferences for a user.
     */
    fun observePreferences(userId: String): Flow<ChatPreferences?>
    
    /**
     * Updates chat preferences.
     */
    suspend fun updatePreferences(preferences: ChatPreferences): LiftrixResult<Unit>
    
    /**
     * Deletes old messages based on retention settings.
     */
    suspend fun cleanupOldMessages(userId: String): LiftrixResult<Int>
    
    /**
     * Gets distinct conversation IDs for a user.
     */
    suspend fun getConversationIds(userId: String): LiftrixResult<List<String>>
    
    /**
     * Deletes a specific conversation.
     */
    suspend fun deleteConversation(
        userId: String,
        conversationId: String
    ): LiftrixResult<Int>
    
    /**
     * Gets token usage for the current hour.
     */
    suspend fun getHourlyTokenUsage(userId: String): Int
    
    /**
     * Clears all chat history for a user.
     * Used by settings screen for complete history clear.
     */
    suspend fun clearAllHistory(userId: String): LiftrixResult<Int>
    
    /**
     * Exports all chat history for a user.
     * Returns formatted export data.
     */
    suspend fun exportChatHistory(userId: String): LiftrixResult<String>
    
    /**
     * Gets total message count for storage statistics.
     */
    suspend fun getTotalMessageCount(userId: String): LiftrixResult<Int>
    
    /**
     * Gets total token usage for usage statistics.
     */
    suspend fun getTotalTokenUsage(userId: String): LiftrixResult<Int>
}
