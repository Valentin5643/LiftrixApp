package com.example.liftrix.domain.usecase.chat

import com.example.liftrix.domain.model.chat.ChatMessage
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for retrieving chat history and conversations.
 * Provides access to conversation history with proper error handling and filtering.
 */
class GetChatHistoryUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    
    /**
     * Observes messages in a specific conversation.
     * 
     * @param userId The authenticated user's ID
     * @param conversationId The conversation ID to observe
     * @return Flow of chat messages with error handling
     */
    fun observeConversation(
        userId: String,
        conversationId: String
    ): Flow<LiftrixResult<List<ChatMessage>>> {
        return chatRepository.observeConversation(userId, conversationId)
            .map { messages ->
                Timber.d("GetChatHistoryUseCase: Retrieved ${messages.size} messages for conversation $conversationId")
                Result.success(messages.sortedBy { it.createdAt })
            }
            .catch { throwable ->
                Timber.e(throwable, "GetChatHistoryUseCase: Error observing conversation $conversationId")
                emit(
                    Result.failure(
                        LiftrixError.BusinessLogicError(
                            code = "CONVERSATION_OBSERVATION_FAILED",
                            errorMessage = "Failed to load conversation. Please try again.",
                            analyticsContext = mapOf(
                                "user_id" to userId,
                                "conversation_id" to conversationId,
                                "operation" to "OBSERVE_CONVERSATION",
                                "error" to (throwable.message ?: "Unknown error")
                            )
                        )
                    )
                )
            }
    }
    
    /**
     * Gets recent messages for a user across all conversations.
     * 
     * @param userId The authenticated user's ID
     * @param limit Maximum number of messages to retrieve (default: 20)
     * @return LiftrixResult containing recent messages
     */
    suspend fun getRecentMessages(
        userId: String,
        limit: Int = 20
    ): LiftrixResult<List<ChatMessage>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "RECENT_MESSAGES_FAILED",
                errorMessage = "Failed to load recent messages. Please try again.",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "limit" to limit.toString(),
                    "operation" to "GET_RECENT_MESSAGES",
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        if (limit <= 0) {
            throw IllegalArgumentException("Limit must be greater than 0")
        }
        
        if (limit > 100) {
            throw IllegalArgumentException("Limit cannot exceed 100 messages")
        }
        
        Timber.d("GetChatHistoryUseCase: Getting recent $limit messages for user $userId")
        
        val messages = chatRepository.getRecentMessages(userId, limit).getOrThrow()
        
        Timber.d("GetChatHistoryUseCase: Retrieved ${messages.size} recent messages")
        
        // Sort by creation time (newest first) for better UX
        messages.sortedByDescending { it.createdAt }
    }
    
    /**
     * Gets all conversation IDs for a user.
     * Useful for showing conversation list in the UI.
     * 
     * @param userId The authenticated user's ID
     * @return LiftrixResult containing list of conversation IDs
     */
    suspend fun getConversationIds(userId: String): LiftrixResult<List<String>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CONVERSATION_IDS_FAILED",
                errorMessage = "Failed to load conversations. Please try again.",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "operation" to "GET_CONVERSATION_IDS",
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        Timber.d("GetChatHistoryUseCase: Getting conversation IDs for user $userId")
        
        val conversationIds = chatRepository.getConversationIds(userId).getOrThrow()
        
        Timber.d("GetChatHistoryUseCase: Retrieved ${conversationIds.size} conversation IDs")
        
        conversationIds
    }
    
    /**
     * Deletes a specific conversation and all its messages.
     * 
     * @param userId The authenticated user's ID
     * @param conversationId The conversation ID to delete
     * @return LiftrixResult containing the number of deleted messages
     */
    suspend fun deleteConversation(
        userId: String,
        conversationId: String
    ): LiftrixResult<Int> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "DELETE_CONVERSATION_FAILED",
                errorMessage = "Failed to delete conversation. Please try again.",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "conversation_id" to conversationId,
                    "operation" to "DELETE_CONVERSATION",
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        if (conversationId.isBlank()) {
            throw IllegalArgumentException("Conversation ID cannot be empty")
        }
        
        Timber.d("GetChatHistoryUseCase: Deleting conversation $conversationId for user $userId")
        
        val deletedCount = chatRepository.deleteConversation(userId, conversationId).getOrThrow()
        
        Timber.d("GetChatHistoryUseCase: Deleted $deletedCount messages from conversation $conversationId")
        
        deletedCount
    }
    
    /**
     * Cleans up old messages based on user retention settings.
     * 
     * @param userId The authenticated user's ID
     * @return LiftrixResult containing the number of cleaned up messages
     */
    suspend fun cleanupOldMessages(userId: String): LiftrixResult<Int> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CLEANUP_MESSAGES_FAILED",
                errorMessage = "Failed to cleanup old messages. Please try again.",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "operation" to "CLEANUP_OLD_MESSAGES",
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        Timber.d("GetChatHistoryUseCase: Cleaning up old messages for user $userId")
        
        val cleanedCount = chatRepository.cleanupOldMessages(userId).getOrThrow()
        
        Timber.d("GetChatHistoryUseCase: Cleaned up $cleanedCount old messages")
        
        cleanedCount
    }
}