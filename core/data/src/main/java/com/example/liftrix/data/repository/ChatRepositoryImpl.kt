package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.ChatHistoryDao
import com.example.liftrix.data.local.dao.ChatPreferencesDao
import com.example.liftrix.data.local.entity.ChatHistoryEntity
import com.example.liftrix.data.local.entity.ChatPreferencesEntity
import com.example.liftrix.domain.model.chat.ChatMessage
import com.example.liftrix.domain.model.chat.ChatConversation
import com.example.liftrix.domain.model.chat.ChatPreferences
import com.example.liftrix.domain.model.chat.MessageType
import com.example.liftrix.domain.model.chat.WorkoutContext
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.ChatRepository
import com.example.liftrix.domain.sync.SyncScheduler
import com.example.liftrix.domain.usecase.chat.ChatConversationDeletionPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ChatRepository providing chat persistence and management.
 * Follows offline-first architecture with background sync to Firebase.
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatPreferencesDao: ChatPreferencesDao,
    private val chatHistoryDao: ChatHistoryDao,
    private val syncScheduler: SyncScheduler
) : ChatRepository {
    private companion object {
        const val MONTHLY_USAGE_TAG = "MonthlyUsageDebug"
    }
    
    override suspend fun saveMessage(
        messageId: String,
        userId: String,
        message: String,
        type: MessageType,
        conversationId: String,
        language: String,
        workoutContext: WorkoutContext?,
        tokenCount: Int?,
        processingTimeMs: Long?,
        titleSeed: String?
    ): LiftrixResult<ChatMessage> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CHAT_SAVE_FAILED",
                errorMessage = "Failed to save message: ${throwable.message}",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "message_type" to type.name
                )
            )
        }
    ) {
        ChatConversationDeletionPolicy.requireRealConversationId(conversationId)
        val retentionDays = chatPreferencesDao.getChatPreferencesSync(userId)?.autoClearDays ?: 30
        val entity = ChatHistoryEntity(
            id = messageId,
            userId = userId,
            conversationId = conversationId,
            messageType = type.name,
            language = language,
            content = message,
            workoutContext = workoutContext?.let { Json.encodeToString(it) },
            tokenCount = tokenCount,
            processingTimeMs = processingTimeMs,
            createdAt = System.currentTimeMillis(),
            isSynced = false,
            syncVersion = 0
        )
        
        chatHistoryDao.insertMessageWithConversation(
            message = entity,
            title = titleSeed ?: "New chat",
            retentionDays = retentionDays
        )
        Timber.tag(MONTHLY_USAGE_TAG).d(
            "Usage row saved userId=%s messageId=%s conversationId=%s messageType=%s tokenCount=%s createdAt=%s source=Room.chat_history increment=%s",
            userId,
            entity.id,
            conversationId,
            type.name,
            tokenCount?.toString() ?: "null",
            Date(entity.createdAt).toString(),
            if (tokenCount != null && tokenCount > 0) "monthly_tokens" else "none"
        )
        
        // Trigger sync for chat history
        syncScheduler.triggerImmediateSync(userId)
        Timber.tag(MONTHLY_USAGE_TAG).d(
            "Usage sync requested userId=%s messageId=%s source=Room.chat_history target=Firebase.chat_history",
            userId,
            entity.id
        )
        
        entity.toDomainModel()
    }

    override fun observeConversations(userId: String): Flow<List<ChatConversation>> =
        chatHistoryDao.observeConversationSummaries(userId).map { rows ->
            rows.map { row ->
                ChatConversation(
                    id = row.conversationId,
                    title = row.title,
                    lastMessagePreview = row.lastMessagePreview,
                    lastUpdatedAt = row.lastUpdatedAt,
                    messageCount = row.messageCount,
                    isTitleCustom = row.isTitleCustom
                )
            }
        }

    override suspend fun renameConversation(
        userId: String,
        conversationId: String,
        title: String
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CONVERSATION_RENAME_FAILED",
                errorMessage = throwable.message ?: "Failed to rename conversation"
            )
        }
    ) {
        ChatConversationDeletionPolicy.requireRealConversationId(conversationId)
        check(chatHistoryDao.renameConversation(userId, conversationId, title, System.currentTimeMillis()) == 1) {
            "Conversation not found"
        }
        syncScheduler.triggerImmediateSync(userId)
    }
    
    override fun observeConversation(
        userId: String,
        conversationId: String
    ): Flow<List<ChatMessage>> {
        return chatHistoryDao.getConversationMessages(userId, conversationId)
            .map { entities -> entities.map { it.toDomainModel() } }
    }
    
    override suspend fun getRecentMessages(
        userId: String,
        limit: Int
    ): LiftrixResult<List<ChatMessage>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CHAT_FETCH_FAILED",
                errorMessage = "Failed to fetch recent messages",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        chatHistoryDao.getRecentMessages(userId, limit)
            .map { it.toDomainModel() }
    }
    
    override fun observePreferences(userId: String): Flow<ChatPreferences?> {
        return chatPreferencesDao.getChatPreferences(userId)
            .map { entity -> entity?.toDomainModel() }
    }
    
    override suspend fun updatePreferences(preferences: ChatPreferences): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PREFERENCES_UPDATE_FAILED",
                errorMessage = "Failed to update preferences",
                analyticsContext = mapOf("user_id" to preferences.userId)
            )
        }
    ) {
        val entity = preferences.toEntity()
        chatPreferencesDao.upsertLocal(entity)
        
        // Trigger sync for preferences
        syncScheduler.triggerImmediateSync(preferences.userId)
    }
    
    override suspend fun cleanupOldMessages(userId: String): LiftrixResult<Int> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CLEANUP_FAILED",
                errorMessage = "Failed to cleanup old messages",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        syncScheduler.triggerImmediateSync(userId)
        0
    }
    
    override suspend fun getConversationIds(userId: String): LiftrixResult<List<String>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CONVERSATION_FETCH_FAILED",
                errorMessage = "Failed to fetch conversation IDs",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        chatHistoryDao.getConversationIds(userId)
    }
    
    override suspend fun deleteConversation(
        userId: String,
        conversationId: String
    ): LiftrixResult<Int> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CONVERSATION_DELETE_FAILED",
                errorMessage = "Failed to delete conversation",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "conversation_id" to conversationId
                )
            )
        }
    ) {
        val deletedCount = chatHistoryDao.tombstoneAndDeleteConversation(
            userId = userId,
            conversationId = conversationId,
            now = System.currentTimeMillis()
        )
        syncScheduler.triggerImmediateSync(userId)
        deletedCount
    }
    
    override suspend fun clearAllHistory(userId: String): LiftrixResult<Int> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CLEAR_HISTORY_FAILED",
                errorMessage = "Failed to clear chat history",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        val deletedCount = chatHistoryDao.clearAllHistoryWithTombstone(
            userId = userId,
            now = System.currentTimeMillis()
        )
        syncScheduler.triggerImmediateSync(userId)
        Timber.i("Cleared all chat history for user: $userId. Deleted $deletedCount messages.")
        deletedCount
    }
    
    override suspend fun exportChatHistory(userId: String): LiftrixResult<String> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "EXPORT_FAILED",
                errorMessage = "Failed to export chat history",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        val allMessages = chatHistoryDao.getAllMessagesForExport(userId)
        // Convert to JSON export format (simplified for now)
        Json.encodeToString(allMessages.map { it.toDomainModel() })
    }
    
    override suspend fun getTotalMessageCount(userId: String): LiftrixResult<Int> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "COUNT_FETCH_FAILED",
                errorMessage = "Failed to get message count",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        chatHistoryDao.getTotalMessageCount(userId)
    }
    
    override suspend fun getTotalTokenUsage(userId: String): LiftrixResult<Int> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "TOKEN_COUNT_FAILED",
                errorMessage = "Failed to get token usage",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        chatHistoryDao.getTotalTokenUsage(userId) ?: 0
    }
}

// Extension functions for entity/domain mapping

private fun ChatHistoryEntity.toDomainModel(): ChatMessage {
    return ChatMessage(
        id = id,
        userId = userId,
        conversationId = conversationId,
        type = MessageType.valueOf(messageType),
        language = language,
        content = content,
        workoutContext = workoutContext?.let { 
            try {
                Json.decodeFromString<WorkoutContext>(it)
            } catch (e: Exception) {
                Timber.e(e, "Failed to decode workout context")
                null
            }
        },
        tokenCount = tokenCount,
        processingTimeMs = processingTimeMs,
        createdAt = createdAt,
        isSynced = isSynced
    )
}

private fun ChatPreferencesEntity.toDomainModel(): ChatPreferences {
    return ChatPreferences(
        userId = userId,
        preferredLanguage = preferredLanguage,
        autoDetectLanguage = autoDetectLanguage,
        chatNotificationsEnabled = chatNotificationsEnabled,
        conversationHistoryEnabled = conversationHistoryEnabled,
        workoutContextSharing = workoutContextSharing,
        maxMessagesPerDay = maxMessagesPerDay,
        maxTokensPerMonth = maxTokensPerMonth,
        autoClearDays = autoClearDays,
        userContextPrompt = userContextPrompt,
        aiResponseStyle = aiResponseStyle,
        includeWorkoutHistory = includeWorkoutHistory,
        includeExerciseFormTips = includeExerciseFormTips,
        usageNotificationsThreshold = usageNotificationsThreshold,
        conversationSaveEnabled = conversationSaveEnabled,
        updatedAt = updatedAt
    )
}

private fun ChatPreferences.toEntity(): ChatPreferencesEntity {
    return ChatPreferencesEntity(
        userId = userId,
        preferredLanguage = preferredLanguage,
        autoDetectLanguage = autoDetectLanguage,
        chatNotificationsEnabled = chatNotificationsEnabled,
        conversationHistoryEnabled = conversationHistoryEnabled,
        workoutContextSharing = workoutContextSharing,
        maxMessagesPerDay = maxMessagesPerDay,
        maxTokensPerMonth = maxTokensPerMonth,
        autoClearDays = autoClearDays,
        userContextPrompt = userContextPrompt,
        aiResponseStyle = aiResponseStyle,
        includeWorkoutHistory = includeWorkoutHistory,
        includeExerciseFormTips = includeExerciseFormTips,
        usageNotificationsThreshold = usageNotificationsThreshold,
        conversationSaveEnabled = conversationSaveEnabled,
        isSynced = false,
        syncVersion = 0,
        updatedAt = updatedAt
    )
}
