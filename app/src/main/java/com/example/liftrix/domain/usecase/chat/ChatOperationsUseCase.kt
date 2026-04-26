package com.example.liftrix.domain.usecase.chat

import com.example.liftrix.domain.model.chat.ChatMessage
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.ChatRepository
import com.example.liftrix.data.local.dao.ChatHistoryDao
import com.example.liftrix.data.local.entity.ChatHistoryEntity
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
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
 * @property chatHistoryDao DAO for direct database access
 * @property getCurrentUserIdUseCase Use case to get authenticated user ID
 */
class ChatOperationsUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val chatHistoryDao: ChatHistoryDao,
    private val authQueryUseCase: AuthQueryUseCase
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

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

        val messageCountBefore = chatHistoryDao.getTotalMessageCount(userId.value)
        val tokenUsageBefore = chatHistoryDao.getTotalTokenUsage(userId.value) ?: 0

        Timber.d("Clearing chat history for user: ${userId.value}. Messages: $messageCountBefore, Tokens: $tokenUsageBefore")

        val deletedCount = chatHistoryDao.clearAllHistory(userId.value)

        Timber.i("Chat history cleared for user: ${userId.value}. Deleted $deletedCount messages.")

        val remainingCount = chatHistoryDao.getTotalMessageCount(userId.value)
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

        val allMessages = chatHistoryDao.getAllMessagesForExport(userId.value)

        if (allMessages.isEmpty()) {
            Timber.i("No chat history found for user: ${userId.value}")
            return@liftrixCatching when (format) {
                ExportFormat.JSON -> generateEmptyExport(userId.value)
            }
        }

        val exportData = when (format) {
            ExportFormat.JSON -> exportToJson(allMessages, userId.value)
        }

        Timber.i("Successfully exported ${allMessages.size} messages for user: ${userId.value}")
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
        val messageCount = chatHistoryDao.getTotalMessageCount(userId)
        val tokenUsage = chatHistoryDao.getTotalTokenUsage(userId) ?: 0
        val conversationCount = chatHistoryDao.getConversationIds(userId).size

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
        val messageCount = chatHistoryDao.getTotalMessageCount(userId)
        val tokenUsage = chatHistoryDao.getTotalTokenUsage(userId) ?: 0
        val conversationIds = chatHistoryDao.getConversationIds(userId)

        ExportPreview(
            messageCount = messageCount,
            conversationCount = conversationIds.size,
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

    private fun exportToJson(messages: List<ChatHistoryEntity>, userId: String): String {
        val conversationGroups = messages.groupBy { it.conversationId }

        val conversations = conversationGroups.map { (conversationId, messages) ->
            ExportedConversation(
                conversationId = conversationId,
                messageCount = messages.size,
                startTime = messages.minOf { it.createdAt },
                endTime = messages.maxOf { it.createdAt },
                totalTokens = messages.sumOf { it.tokenCount ?: 0 },
                messages = messages.map { entity ->
                    ExportedMessage(
                        messageId = entity.id,
                        type = entity.messageType,
                        content = entity.content,
                        language = entity.language,
                        timestamp = entity.createdAt,
                        formattedTime = formatTimestamp(entity.createdAt),
                        tokenCount = entity.tokenCount,
                        processingTimeMs = entity.processingTimeMs,
                        workoutContext = entity.workoutContext,
                        isSynced = entity.isSynced
                    )
                }
            )
        }

        val exportData = ChatHistoryExport(
            userId = userId,
            exportTimestamp = System.currentTimeMillis(),
            exportDate = formatTimestamp(System.currentTimeMillis()),
            totalConversations = conversations.size,
            totalMessages = messages.size,
            totalTokens = messages.sumOf { it.tokenCount ?: 0 },
            conversations = conversations,
            metadata = ExportMetadata(
                version = "1.0",
                format = "JSON",
                source = "Liftrix AI Chat",
                exportedBy = "User",
                dataIncludes = listOf(
                    "conversation_messages",
                    "timestamps",
                    "token_usage",
                    "workout_context",
                    "language_info"
                )
            )
        )

        return json.encodeToString(exportData)
    }

    private fun generateEmptyExport(userId: String): String {
        val emptyExport = ChatHistoryExport(
            userId = userId,
            exportTimestamp = System.currentTimeMillis(),
            exportDate = formatTimestamp(System.currentTimeMillis()),
            totalConversations = 0,
            totalMessages = 0,
            totalTokens = 0,
            conversations = emptyList(),
            metadata = ExportMetadata(
                version = "1.0",
                format = "JSON",
                source = "Liftrix AI Chat",
                exportedBy = "User",
                dataIncludes = emptyList()
            )
        )

        return json.encodeToString(emptyExport)
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
