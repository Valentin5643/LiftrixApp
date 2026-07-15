package com.example.liftrix.domain.usecase.chat

import com.example.liftrix.domain.model.chat.ChatMessage
import com.example.liftrix.domain.model.chat.ChatConversation
import com.example.liftrix.domain.model.chat.ChatPreferences
import com.example.liftrix.domain.model.chat.MessageType
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.ChatRepository
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import com.example.liftrix.domain.util.DomainLogger as Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Consolidated use case for chat operations including history, clearing, and export.
 *
 * Replaces:
 * - GetChatHistoryUseCase.kt
 * - ClearChatHistoryUseCase.kt
 * - ExportChatHistoryUseCase.kt
 *
 * Provides methods for:
 * - Observing and retrieving chat history
 * - Managing conversations
 * - Clearing chat history with confirmation
 * - Exporting chat history to various formats
 *
 * All operations include proper user scoping and error handling.
 *
 * @property chatRepository Repository for chat data operations
 * @property getCurrentUserIdUseCase Use case to get authenticated user ID
 */
class ChatOperationsUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val authQueryUseCase: AuthQueryUseCase
) {

    companion object {
        private const val CONFIRMATION_TEXT = "DELETE ALL CHATS"
        private const val CONFIRMATION_TEXT_RO = "ȘTERGE TOATE CONVERSAȚIILE"
    }

    // ===== Query Operations (from GetChatHistoryUseCase) =====

    /**
     * Observes messages in a specific conversation.
     * Replaces GetChatHistoryUseCase.observeConversation()
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
                Timber.d("ChatOperationsUseCase: Retrieved ${messages.size} messages for conversation $conversationId")
                Result.success(messages.sortedBy { it.createdAt })
            }
            .catch { throwable ->
                Timber.e(throwable, "ChatOperationsUseCase: Error observing conversation $conversationId")
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

    fun observeConversations(userId: String): Flow<LiftrixResult<List<ChatConversation>>> =
        chatRepository.observeConversations(userId)
            .map { Result.success(it) }
            .catch { throwable ->
                emit(Result.failure(LiftrixError.BusinessLogicError(
                    code = "CONVERSATION_LIST_FAILED",
                    errorMessage = throwable.message ?: "Failed to load conversations"
                )))
            }

    fun observePreferences(userId: String): Flow<ChatPreferences?> =
        chatRepository.observePreferences(userId)

    suspend fun renameConversation(
        userId: String,
        conversationId: String,
        title: String
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { it as? LiftrixError ?: LiftrixError.ValidationError("title", listOf(it.message ?: "Invalid title")) }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be empty" }
        require(conversationId.isNotBlank()) { "Conversation ID cannot be empty" }
        val normalized = title.trim()
        require(normalized.isNotBlank()) { "Title cannot be empty" }
        require(normalized.length <= ChatConversationTitlePolicy.MAX_LENGTH) { "Title cannot exceed 60 characters" }
        chatRepository.renameConversation(userId, conversationId, normalized).getOrThrow()
    }

    suspend fun recordMessage(
        messageId: String,
        userId: String,
        conversationId: String,
        content: String,
        type: MessageType,
        language: String = "en",
        titleSeed: String? = null
    ): LiftrixResult<ChatMessage> = liftrixCatching(
        errorMapper = { it as? LiftrixError ?: LiftrixError.BusinessLogicError("CHAT_RECORD_FAILED", it.message ?: "Failed to record message") }
    ) {
        require(messageId.isNotBlank() && userId.isNotBlank() && conversationId.isNotBlank())
        chatRepository.saveMessage(
            messageId = messageId,
            userId = userId,
            message = content,
            type = type,
            conversationId = conversationId,
            language = language,
            titleSeed = titleSeed
        ).getOrThrow()
    }

    /**
     * Gets recent messages for a user across all conversations.
     * Replaces GetChatHistoryUseCase.getRecentMessages()
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

        Timber.d("ChatOperationsUseCase: Getting recent $limit messages for user $userId")

        val messages = chatRepository.getRecentMessages(userId, limit).getOrThrow()

        Timber.d("ChatOperationsUseCase: Retrieved ${messages.size} recent messages")

        messages.sortedByDescending { it.createdAt }
    }

    /**
     * Gets all conversation IDs for a user.
     * Replaces GetChatHistoryUseCase.getConversationIds()
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
        Timber.d("ChatOperationsUseCase: Getting conversation IDs for user $userId")

        val conversationIds = chatRepository.getConversationIds(userId).getOrThrow()

        Timber.d("ChatOperationsUseCase: Retrieved ${conversationIds.size} conversation IDs")

        conversationIds
    }

    // ===== Command Operations (from GetChatHistoryUseCase, ClearChatHistoryUseCase) =====

    /**
     * Deletes a specific conversation and all its messages.
     * Replaces GetChatHistoryUseCase.deleteConversation()
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

        Timber.d("ChatOperationsUseCase: Deleting conversation $conversationId for user $userId")

        val deletedCount = chatRepository.deleteConversation(userId, conversationId).getOrThrow()

        Timber.d("ChatOperationsUseCase: Deleted $deletedCount messages from conversation $conversationId")

        deletedCount
    }

    /**
     * Cleans up old messages based on user retention settings.
     * Replaces GetChatHistoryUseCase.cleanupOldMessages()
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
        Timber.d("ChatOperationsUseCase: Cleaning up old messages for user $userId")

        val cleanedCount = chatRepository.cleanupOldMessages(userId).getOrThrow()

        Timber.d("ChatOperationsUseCase: Cleaned up $cleanedCount old messages")

        cleanedCount
    }

    /**
     * Clears all chat history for the current user with confirmation.
     * Replaces ClearChatHistoryUseCase.invoke()
     *
     * @param confirmationText User-provided confirmation text (must match exactly)
     * @param language User's language preference for confirmation validation
     * @return LiftrixResult<Int> with the number of deleted messages
     */
    suspend fun clearAllHistory(
        confirmationText: String,
        language: String = "en"
    ): LiftrixResult<Int> = liftrixCatching(
        errorMapper = { throwable ->
            Timber.e(throwable, "Failed to clear chat history")
            LiftrixError.BusinessLogicError(
                code = "CLEAR_HISTORY_FAILED",
                errorMessage = "Failed to clear chat history. Please try again.",
                analyticsContext = mapOf(
                    "operation" to "CLEAR_CHAT_HISTORY",
                    "error_type" to (throwable::class.simpleName ?: "Unknown")
                )
            )
        }
    ) {
        val userId = authQueryUseCase(waitForAuth = false).getOrNull()
            ?: throw IllegalStateException("User not authenticated")

        validateConfirmation(confirmationText, language)

        val messageCountBefore = chatRepository.getTotalMessageCount(userId.value).getOrDefault(0)
        val tokenUsageBefore = chatRepository.getTotalTokenUsage(userId.value).getOrDefault(0)

        Timber.d("Clearing chat history for user: ${userId.value}. Messages: $messageCountBefore, Tokens: $tokenUsageBefore")

        val deletedCount = chatRepository.clearAllHistory(userId.value).getOrThrow()

        Timber.i("Chat history cleared for user: ${userId.value}. Deleted $deletedCount messages.")

        val remainingCount = chatRepository.getTotalMessageCount(userId.value).getOrDefault(0)
        if (remainingCount > 0) {
            Timber.w("History clear incomplete: $remainingCount messages remain for user ${userId.value}")
        }

        deletedCount
    }

    // ===== Export Operations (from ExportChatHistoryUseCase) =====

    /**
     * Exports the user's complete chat history to JSON format.
     * Replaces ExportChatHistoryUseCase.invoke()
     *
     * @param format The export format (currently only JSON supported)
     * @return LiftrixResult<String> containing the formatted export data
     */
    suspend fun exportHistory(format: ExportFormat = ExportFormat.JSON): LiftrixResult<String> = liftrixCatching(
        errorMapper = { throwable ->
            Timber.e(throwable, "Failed to export chat history")
            LiftrixError.BusinessLogicError(
                code = "EXPORT_FAILED",
                errorMessage = "Failed to export chat history. Please try again.",
                analyticsContext = mapOf(
                    "operation" to "EXPORT_CHAT_HISTORY",
                    "format" to format.name,
                    "error_type" to (throwable::class.simpleName ?: "Unknown")
                )
            )
        }
    ) {
        val userId = authQueryUseCase(waitForAuth = false).getOrNull()
            ?: throw IllegalStateException("User not authenticated")

        Timber.d("Starting chat history export for user: ${userId.value} in format: $format")

        val exportData = chatRepository.exportChatHistory(userId.value).getOrThrow()

        Timber.i("Successfully exported chat history for user: ${userId.value}")
        exportData
    }

    // ===== Utility Methods =====

    /**
     * Gets the required confirmation text for a given language.
     * Replaces ClearChatHistoryUseCase.getRequiredConfirmationText()
     */
    fun getRequiredConfirmationText(language: String = "en"): String {
        return when (language) {
            "ro" -> CONFIRMATION_TEXT_RO
            else -> CONFIRMATION_TEXT
        }
    }

    /**
     * Gets statistics about the user's chat history.
     * Replaces ClearChatHistoryUseCase.getHistoryStats()
     */
    suspend fun getHistoryStats(userId: String): LiftrixResult<HistoryStats> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "STATS_FETCH_FAILED",
                errorMessage = "Failed to get history statistics",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        val messageCount = chatRepository.getTotalMessageCount(userId).getOrThrow()
        val tokenUsage = chatRepository.getTotalTokenUsage(userId).getOrThrow()
        val conversationCount = chatRepository.getConversationIds(userId).getOrThrow().size

        HistoryStats(
            totalMessages = messageCount,
            totalTokens = tokenUsage,
            conversationCount = conversationCount
        )
    }

    /**
     * Gets export statistics without performing the full export.
     * Replaces ExportChatHistoryUseCase.getExportPreview()
     */
    suspend fun getExportPreview(userId: String): LiftrixResult<ExportPreview> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PREVIEW_FAILED",
                errorMessage = "Failed to generate export preview",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        val messageCount = chatRepository.getTotalMessageCount(userId).getOrThrow()
        val tokenUsage = chatRepository.getTotalTokenUsage(userId).getOrThrow()
        val conversationCount = chatRepository.getConversationIds(userId).getOrThrow().size

        ExportPreview(
            messageCount = messageCount,
            conversationCount = conversationCount,
            totalTokens = tokenUsage,
            estimatedSizeKB = calculateEstimatedSize(messageCount)
        )
    }

    // ===== Private Helper Methods =====

    private fun validateConfirmation(confirmationText: String, language: String) {
        val expectedText = when (language) {
            "ro" -> CONFIRMATION_TEXT_RO
            else -> CONFIRMATION_TEXT
        }

        if (confirmationText.trim() != expectedText) {
            throw IllegalArgumentException(
                when (language) {
                    "ro" -> "Textul de confirmare trebuie să fie exact: '$expectedText'"
                    else -> "Confirmation text must be exactly: '$expectedText'"
                }
            )
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }

    private fun calculateEstimatedSize(messageCount: Int): Int {
        return maxOf(1, messageCount / 1024)
    }
}

// ===== Data Classes and Enums =====

/**
 * Statistics about user's chat history.
 */
data class HistoryStats(
    val totalMessages: Int,
    val totalTokens: Int,
    val conversationCount: Int
)

/**
 * Supported export formats.
 */
enum class ExportFormat {
    JSON
}

/**
 * Preview information about the export.
 */
data class ExportPreview(
    val messageCount: Int,
    val conversationCount: Int,
    val totalTokens: Int,
    val estimatedSizeKB: Int
)

/**
 * Complete chat history export structure.
 */
@Serializable
data class ChatHistoryExport(
    val userId: String,
    val exportTimestamp: Long,
    val exportDate: String,
    val totalConversations: Int,
    val totalMessages: Int,
    val totalTokens: Int,
    val conversations: List<ExportedConversation>,
    val metadata: ExportMetadata
)

/**
 * Exported conversation with all messages.
 */
@Serializable
data class ExportedConversation(
    val conversationId: String,
    val messageCount: Int,
    val startTime: Long,
    val endTime: Long,
    val totalTokens: Int,
    val messages: List<ExportedMessage>
)

/**
 * Individual exported message.
 */
@Serializable
data class ExportedMessage(
    val messageId: String,
    val type: String,
    val content: String,
    val language: String,
    val timestamp: Long,
    val formattedTime: String,
    val tokenCount: Int?,
    val processingTimeMs: Long?,
    val workoutContext: String?,
    val isSynced: Boolean
)

/**
 * Export metadata and information.
 */
@Serializable
data class ExportMetadata(
    val version: String,
    val format: String,
    val source: String,
    val exportedBy: String,
    val dataIncludes: List<String>
)
