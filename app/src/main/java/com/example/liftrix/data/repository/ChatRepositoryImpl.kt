package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.ChatHistoryDao
import com.example.liftrix.data.local.dao.ChatPreferencesDao
import com.example.liftrix.data.local.entity.ChatHistoryEntity
import com.example.liftrix.data.local.entity.ChatPreferencesEntity
import com.example.liftrix.domain.model.chat.ChatMessage
import com.example.liftrix.domain.model.chat.ChatPreferences
import com.example.liftrix.domain.model.chat.MessageType
import com.example.liftrix.domain.model.chat.UsageLimits
import com.example.liftrix.domain.model.chat.WorkoutContext
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.ChatRepository
import com.example.liftrix.sync.SyncCoordinator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID
import java.util.Calendar
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
    private val syncCoordinator: SyncCoordinator
) : ChatRepository {
    
    override suspend fun saveMessage(
        userId: String,
        message: String,
        type: MessageType,
        conversationId: String,
        language: String,
        workoutContext: WorkoutContext?,
        tokenCount: Int?,
        processingTimeMs: Long?
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
        val entity = ChatHistoryEntity(
            id = UUID.randomUUID().toString(),
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
        
        chatHistoryDao.insertMessage(entity)
        
        // Trigger sync for chat history
        syncCoordinator.triggerImmediateSync(userId)
        
        entity.toDomainModel()
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
    
    override suspend fun checkUsageLimits(userId: String): LiftrixResult<UsageLimits> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "USAGE_CHECK_FAILED",
                errorMessage = "Failed to check usage limits",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        val prefs = chatPreferencesDao.getChatPreferences(userId).first()
            ?: ChatPreferencesEntity(userId = userId)
        
        // Calculate today's start timestamp
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        // Calculate month's start timestamp
        val monthStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        val todayCount = chatHistoryDao.getTodayMessageCount(userId, todayStart)
        val monthTokens = chatHistoryDao.getMonthlyTokenUsage(userId, monthStart) ?: 0
        
        UsageLimits(
            dailyMessagesRemaining = maxOf(0, prefs.maxMessagesPerDay - todayCount),
            monthlyTokensRemaining = maxOf(0, prefs.maxTokensPerMonth - monthTokens),
            isNearDailyLimit = todayCount >= (prefs.maxMessagesPerDay * 0.8).toInt(),
            isNearMonthlyLimit = monthTokens >= (prefs.maxTokensPerMonth * 0.8).toInt()
        )
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
        chatPreferencesDao.insertOrUpdatePreferences(entity)
        
        // Trigger sync for preferences
        syncCoordinator.triggerImmediateSync(preferences.userId)
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
        val prefs = chatPreferencesDao.getChatPreferences(userId).first()
            ?: ChatPreferencesEntity(userId = userId)
        
        val cutoffTimestamp = System.currentTimeMillis() - (prefs.autoClearDays * 24 * 60 * 60 * 1000L)
        val deletedCount = chatHistoryDao.deleteOldMessages(userId, cutoffTimestamp)
        
        Timber.d("Deleted $deletedCount old messages for user $userId")
        deletedCount
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
        val deletedCount = chatHistoryDao.deleteConversation(userId, conversationId)
        Timber.d("Deleted conversation $conversationId with $deletedCount messages")
        deletedCount
    }
    
    override suspend fun getHourlyTokenUsage(userId: String): Int {
        val hourAgo = System.currentTimeMillis() - 3600000 // 1 hour ago
        return chatHistoryDao.getTokenUsageSince(userId, hourAgo) ?: 0
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
        isSynced = false,
        syncVersion = 0,
        updatedAt = updatedAt
    )
}