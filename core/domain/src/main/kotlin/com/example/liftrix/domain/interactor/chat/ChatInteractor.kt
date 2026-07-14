package com.example.liftrix.domain.interactor.chat

import com.example.liftrix.domain.model.chat.ChatMessage
import com.example.liftrix.domain.model.chat.ChatConversation
import com.example.liftrix.domain.model.chat.MessageType
import com.example.liftrix.domain.model.chat.ChatPreferences
import com.example.liftrix.domain.model.chat.UsageLimits
import com.example.liftrix.domain.model.chat.WorkoutContext
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.service.AIUsageStats
import com.example.liftrix.domain.service.RateLimitStatus
import com.example.liftrix.domain.usecase.chat.ChatOperationsUseCase
import com.example.liftrix.domain.usecase.chat.ExportFormat
import com.example.liftrix.domain.usecase.chat.SendChatMessageUseCase
import com.example.liftrix.domain.usecase.chat.UpdateChatPreferencesUseCase
import com.example.liftrix.domain.usecase.chat.CheckUsageLimitsUseCase
import com.example.liftrix.domain.usecase.chat.UsageValidationResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ChatInteractor @Inject constructor(
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val checkUsageLimitsUseCase: CheckUsageLimitsUseCase,
    private val chatOperationsUseCase: ChatOperationsUseCase,
    private val updateChatPreferencesUseCase: UpdateChatPreferencesUseCase
) {
    suspend fun sendMessage(
        userId: String,
        requestId: String,
        message: String,
        conversationId: String? = null,
        workoutContext: WorkoutContext? = null,
        language: String = "en"
    ): LiftrixResult<Pair<ChatMessage, ChatMessage>> =
        sendChatMessageUseCase(
            userId = userId,
            requestId = requestId,
            message = message,
            conversationId = conversationId,
            workoutContext = workoutContext,
            language = language
        )

    suspend fun usageLimits(userId: String): LiftrixResult<UsageLimits> =
        checkUsageLimitsUseCase.getUserUsageLimits(userId)

    suspend fun canSendMessage(userId: String): LiftrixResult<RateLimitStatus> =
        checkUsageLimitsUseCase.canSendMessage(userId)

    suspend fun usageStats(userId: String): LiftrixResult<AIUsageStats> =
        checkUsageLimitsUseCase.getUsageStats(userId)

    suspend fun validateAction(
        userId: String,
        actionType: String = "SEND_MESSAGE"
    ): LiftrixResult<UsageValidationResult> =
        checkUsageLimitsUseCase.validateAction(userId, actionType)

    suspend fun updatePreferences(preferences: ChatPreferences): LiftrixResult<Unit> =
        updateChatPreferencesUseCase(preferences)

    fun observeConversation(
        userId: String,
        conversationId: String
    ): Flow<LiftrixResult<List<ChatMessage>>> =
        chatOperationsUseCase.observeConversation(userId, conversationId)

    fun observeConversations(userId: String): Flow<LiftrixResult<List<ChatConversation>>> =
        chatOperationsUseCase.observeConversations(userId)

    suspend fun renameConversation(userId: String, conversationId: String, title: String): LiftrixResult<Unit> =
        chatOperationsUseCase.renameConversation(userId, conversationId, title)

    suspend fun deleteConversation(userId: String, conversationId: String): LiftrixResult<Int> =
        chatOperationsUseCase.deleteConversation(userId, conversationId)

    suspend fun recordMessage(
        messageId: String,
        userId: String,
        conversationId: String,
        content: String,
        type: MessageType,
        language: String = "en",
        titleSeed: String? = null
    ): LiftrixResult<ChatMessage> = chatOperationsUseCase.recordMessage(
        messageId, userId, conversationId, content, type, language, titleSeed
    )

    suspend fun recentMessages(
        userId: String,
        limit: Int = 20
    ): LiftrixResult<List<ChatMessage>> =
        chatOperationsUseCase.getRecentMessages(userId, limit)

    suspend fun exportHistory(format: ExportFormat = ExportFormat.JSON): LiftrixResult<String> =
        chatOperationsUseCase.exportHistory(format)

    suspend fun clearAllHistory(
        confirmationText: String,
        language: String = "en"
    ): LiftrixResult<Int> =
        chatOperationsUseCase.clearAllHistory(confirmationText, language)

    fun requiredConfirmationText(language: String = "en"): String =
        chatOperationsUseCase.getRequiredConfirmationText(language)
}
