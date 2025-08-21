package com.example.liftrix.domain.usecase.chat

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.data.local.dao.ChatHistoryDao
import com.example.liftrix.data.local.entity.ChatHistoryEntity
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Use case for exporting user's chat history to various formats.
 * Supports JSON export with structured conversation data.
 */
class ExportChatHistoryUseCase @Inject constructor(
    private val chatHistoryDao: ChatHistoryDao,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    /**
     * Exports the user's complete chat history to JSON format.
     * 
     * @param format The export format (currently only JSON supported)
     * @return LiftrixResult<String> containing the formatted export data
     */
    suspend operator fun invoke(format: ExportFormat = ExportFormat.JSON): LiftrixResult<String> = liftrixCatching(
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
        // Get current user ID
        val userId = getCurrentUserIdUseCase() 
            ?: throw IllegalStateException("User not authenticated")
        
        Timber.d("Starting chat history export for user: $userId in format: $format")
        
        // Fetch all messages for the user
        val allMessages = chatHistoryDao.getAllMessagesForExport(userId)
        
        if (allMessages.isEmpty()) {
            Timber.i("No chat history found for user: $userId")
            return@liftrixCatching when (format) {
                ExportFormat.JSON -> generateEmptyExport(userId)
            }
        }
        
        // Export based on format
        val exportData = when (format) {
            ExportFormat.JSON -> exportToJson(allMessages, userId)
        }
        
        Timber.i("Successfully exported ${allMessages.size} messages for user: $userId")
        exportData
    }
    
    /**
     * Exports chat history to JSON format with structured conversations.
     */
    private fun exportToJson(messages: List<ChatHistoryEntity>, userId: String): String {
        // Group messages by conversation
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
    
    /**
     * Generates an empty export for users with no chat history.
     */
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
    
    /**
     * Formats timestamp to human-readable string.
     */
    private fun formatTimestamp(timestamp: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }
    
    /**
     * Gets export statistics without performing the full export.
     * Useful for showing users what will be exported.
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
    
    /**
     * Estimates the export file size based on message count.
     */
    private fun calculateEstimatedSize(messageCount: Int): Int {
        // Rough estimate: 1KB per message (including JSON overhead)
        return maxOf(1, messageCount / 1024) // Return at least 1KB
    }
}

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