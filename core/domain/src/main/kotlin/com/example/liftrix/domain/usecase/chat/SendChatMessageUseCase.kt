package com.example.liftrix.domain.usecase.chat

import com.example.liftrix.domain.model.chat.ChatMessage
import com.example.liftrix.domain.model.chat.MessageType
import com.example.liftrix.domain.model.chat.WorkoutContext
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.error.withAnalyticsContext
import com.example.liftrix.domain.repository.ChatRepository
import com.example.liftrix.domain.service.AIChatService
import com.example.liftrix.domain.service.RateLimitingServiceContract
import com.example.liftrix.domain.util.DomainLogger as Timber
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case for sending a chat message and getting an AI response.
 * Handles the complete flow of user input to AI response with proper error handling,
 * rate limiting, and abuse prevention.
 */
class SendChatMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val aiChatService: AIChatService,
    private val rateLimitingService: RateLimitingServiceContract,
    private val titlePolicy: ChatConversationTitlePolicy
) {
    private companion object {
        const val MONTHLY_USAGE_TAG = "MonthlyUsageDebug"
    }
    
    /**
     * Sends a user message and gets an AI response.
     * 
     * @param userId The authenticated user's ID
     * @param message The user's message
     * @param conversationId The conversation ID (optional, generates new if null)
     * @param workoutContext Optional workout context for better AI responses
     * @param language Language code (defaults to "en")
     * @return LiftrixResult containing both user and AI messages
     */
    suspend operator fun invoke(
        userId: String,
        requestId: String,
        message: String,
        conversationId: String? = null,
        workoutContext: WorkoutContext? = null,
        language: String = "en"
    ): LiftrixResult<Pair<ChatMessage, ChatMessage>> = liftrixCatching(
        errorMapper = { throwable ->
            Timber.e(
                throwable,
                "[AI] SendChatMessageUseCase: failed requestId=%s messageChars=%d language=%s causeType=%s",
                requestId,
                message.length,
                language,
                throwable.javaClass.simpleName
            )
            when (throwable) {
                is LiftrixError -> throwable.withAnalyticsContext(
                    mapOf(
                        "user_id" to userId,
                        "operation" to "SEND_CHAT_MESSAGE",
                        "conversation_id" to (conversationId ?: "generated"),
                        "message_chars" to message.length.toString(),
                        "source" to "SendChatMessageUseCase"
                    )
                )
                is IllegalArgumentException -> LiftrixError.ValidationError(
                    field = "message",
                    violations = listOf(throwable.message ?: "Invalid message format"),
                    errorMessage = throwable.message ?: "Invalid message format",
                    analyticsContext = mapOf(
                        "user_id" to userId,
                        "operation" to "SEND_CHAT_MESSAGE",
                        "error" to (throwable.message ?: "Validation failed")
                    )
                )
                else -> LiftrixError.BusinessLogicError(
                    code = "CHAT_MESSAGE_FAILED",
                    errorMessage = throwable.message ?: "Failed to send message. Please try again.",
                    isRecoverable = true, // Make error recoverable for retry
                    analyticsContext = mapOf(
                        "user_id" to userId,
                        "operation" to "SEND_CHAT_MESSAGE",
                        "error" to (throwable.message ?: "Unknown error")
                    )
                )
            }
        }
    ) {
        // Validate input
        if (message.isBlank()) {
            throw IllegalArgumentException("Message cannot be empty")
        }
        
        if (message.length > 2000) {
            throw IllegalArgumentException("Message too long (max 2000 characters)")
        }
        
        // Generate conversation ID if not provided
        require(requestId.isNotBlank()) { "Request ID cannot be empty" }
        val activeConversationId = conversationId ?: "chat_$requestId"
        
        Timber.i("[AI] SendChatMessageUseCase: sending requestId=$requestId messageChars=${message.length} language=$language")
        
        // 1. Check rate limits
        val rateLimitStatus = rateLimitingService.checkLimits(userId)
        Timber.tag(MONTHLY_USAGE_TAG).d(
            "Send message rate limit result requestId=%s isLimited=%s reason=%s dailyRemaining=%s monthlyRemaining=%s source=RateLimitingService",
            requestId,
            rateLimitStatus.isLimited,
            rateLimitStatus.reason ?: "none",
            rateLimitStatus.messagesRemaining?.toString() ?: "unknown",
            rateLimitStatus.tokensRemaining?.toString() ?: "unknown"
        )
        if (rateLimitStatus.isLimited) {
            if (rateLimitStatus.tokensRemaining == 0) {
                Timber.tag(MONTHLY_USAGE_TAG).w(
                    "Send message blocked by monthly limit requestId=%s reason=%s source=RateLimitingService",
                    requestId,
                    rateLimitStatus.reason ?: "unknown"
                )
            }
            throw IllegalStateException(
                rateLimitStatus.reason ?: "Rate limit exceeded. Please try again later."
            )
        }
        
        // 2. Get user chat preferences before persistence and AI personalization.
        // Abuse policy and the authoritative quota check run in PaidAiCallExecutor.
        val currentPreferences = chatRepository.observePreferences(userId).first()
        val shouldSaveConversation = currentPreferences?.conversationSaveEnabled
            ?: currentPreferences?.conversationHistoryEnabled
            ?: true

        // 3. Load prior conversation context before saving the current user message.
        // Firebase AI chat history must contain completed prior turns, not the message
        // that is about to be sent again via sendMessage().
        val recentMessages = if (shouldSaveConversation) {
            chatRepository.observeConversation(userId, activeConversationId)
                .first()
                .takeLast(10)
        } else {
            emptyList()
        }
        Timber.i(
            "[AI] SendChatMessageUseCase: loaded prior conversation context requestId=%s messages=%d",
            requestId,
            recentMessages.size
        )

        val userPreferences = currentPreferences?.userContextPrompt

        // 4. Save user message when conversation saving is enabled.
        val userMessage = if (shouldSaveConversation) {
            chatRepository.saveMessage(
                messageId = "chat-$requestId-user",
                userId = userId,
                message = message,
                type = MessageType.USER,
                conversationId = activeConversationId,
                language = language,
                workoutContext = workoutContext,
                titleSeed = titlePolicy.titleFor(message)
            ).getOrThrow()
        } else {
            localChatMessage(
                messageId = "chat-$requestId-user",
                userId = userId,
                conversationId = activeConversationId,
                type = MessageType.USER,
                language = language,
                content = message,
                workoutContext = workoutContext
            )
        }
        
        Timber.i("[AI] SendChatMessageUseCase: user message ready id=${userMessage.id} persisted=$shouldSaveConversation")
        
        // 5. Build conversation context
        val conversationContext = com.example.liftrix.domain.service.ConversationContext(
            recentMessages = recentMessages,
            workoutContext = workoutContext,
            userPreferences = userPreferences,
            includeWorkoutHistory = currentPreferences?.includeWorkoutHistory ?: true,
            aiResponseStyle = currentPreferences?.aiResponseStyle ?: "balanced",
            includeExerciseFormTips = currentPreferences?.includeExerciseFormTips ?: true
        )
        
        // 6. Get AI response
        Timber.i("[AI] SendChatMessageUseCase: requesting AI response requestId=$requestId priorMessages=${recentMessages.size}")
        val aiResponse = aiChatService.generateResponse(
            userId = userId,
            message = message,
            conversationContext = conversationContext,
            language = if (language == "ro") com.example.liftrix.domain.service.Language.ROMANIAN else com.example.liftrix.domain.service.Language.ENGLISH,
            autoDetectLanguage = currentPreferences?.autoDetectLanguage ?: true
        ).getOrThrow()
        
        Timber.i("[AI] SendChatMessageUseCase: AI response generated processingMs=${aiResponse.processingTimeMs} tokens=${aiResponse.tokensUsed}")
        
        // 7. Save AI response when conversation saving is enabled.
        val assistantMessage = if (shouldSaveConversation) {
            chatRepository.saveMessage(
                messageId = "chat-$requestId-assistant",
                userId = userId,
                message = aiResponse.content,
                type = MessageType.AI_RESPONSE,
                conversationId = activeConversationId,
                language = language,
                workoutContext = workoutContext,
                tokenCount = aiResponse.tokensUsed,
                processingTimeMs = aiResponse.processingTimeMs
            ).getOrThrow()
        } else {
            localChatMessage(
                messageId = "chat-$requestId-assistant",
                userId = userId,
                conversationId = activeConversationId,
                type = MessageType.AI_RESPONSE,
                language = language,
                content = aiResponse.content,
                workoutContext = workoutContext,
                tokenCount = aiResponse.tokensUsed,
                processingTimeMs = aiResponse.processingTimeMs
            )
        }
        
        Timber.i("[AI] SendChatMessageUseCase: AI message ready id=${assistantMessage.id} persisted=$shouldSaveConversation")
        
        // Return both messages
        userMessage to assistantMessage
    }
    
    /**
     * Generates a unique conversation ID based on timestamp and user ID.
     */
    private fun localChatMessage(
        messageId: String,
        userId: String,
        conversationId: String,
        type: MessageType,
        language: String,
        content: String,
        workoutContext: WorkoutContext? = null,
        tokenCount: Int? = null,
        processingTimeMs: Long? = null
    ): ChatMessage {
        return ChatMessage(
            id = messageId,
            userId = userId,
            conversationId = conversationId,
            type = type,
            language = language,
            content = content,
            workoutContext = workoutContext,
            tokenCount = tokenCount,
            processingTimeMs = processingTimeMs,
            createdAt = System.currentTimeMillis(),
            isSynced = false
        )
    }
}
