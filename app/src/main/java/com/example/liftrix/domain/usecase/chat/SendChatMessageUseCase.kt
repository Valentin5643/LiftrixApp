package com.example.liftrix.domain.usecase.chat

import com.example.liftrix.data.service.AbusePreventionService
import com.example.liftrix.data.service.RateLimitingService
import com.example.liftrix.domain.model.chat.ChatMessage
import com.example.liftrix.domain.model.chat.MessageType
import com.example.liftrix.domain.model.chat.WorkoutContext
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.error.withAnalyticsContext
import com.example.liftrix.domain.repository.ChatRepository
import com.example.liftrix.domain.service.AIChatService
import com.example.liftrix.domain.service.AbuseAction
import timber.log.Timber
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
    private val rateLimitingService: RateLimitingService,
    private val abusePreventionService: AbusePreventionService
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
        message: String,
        conversationId: String? = null,
        workoutContext: WorkoutContext? = null,
        language: String = "en"
    ): LiftrixResult<Pair<ChatMessage, ChatMessage>> = liftrixCatching(
        errorMapper = { throwable ->
            Timber.e(
                throwable,
                "[AI] SendChatMessageUseCase: failed userId=%s conversationId=%s messageChars=%d language=%s cause=%s",
                userId,
                conversationId ?: "generated",
                message.length,
                language,
                throwable.message ?: throwable.javaClass.simpleName
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
        val activeConversationId = conversationId ?: generateConversationId()
        
        Timber.i("[AI] SendChatMessageUseCase: sending message user=$userId conversation=$activeConversationId messageChars=${message.length} language=$language")
        
        // 1. Check rate limits
        val rateLimitStatus = rateLimitingService.checkLimits(userId)
        Timber.tag(MONTHLY_USAGE_TAG).d(
            "Send message rate limit result userId=%s isLimited=%s reason=%s dailyRemaining=%s monthlyRemaining=%s source=RateLimitingService",
            userId,
            rateLimitStatus.isLimited,
            rateLimitStatus.reason ?: "none",
            rateLimitStatus.messagesRemaining?.toString() ?: "unknown",
            rateLimitStatus.tokensRemaining?.toString() ?: "unknown"
        )
        if (rateLimitStatus.isLimited) {
            if (rateLimitStatus.tokensRemaining == 0) {
                Timber.tag(MONTHLY_USAGE_TAG).w(
                    "Send message blocked by monthly limit userId=%s reason=%s source=RateLimitingService",
                    userId,
                    rateLimitStatus.reason ?: "unknown"
                )
            }
            throw IllegalStateException(
                rateLimitStatus.reason ?: "Rate limit exceeded. Please try again later."
            )
        }
        
        // 2. Check for abuse
        val abuseDetection = abusePreventionService.detectAbuse(userId, message)
        if (abuseDetection.isAbusive) {
            when (abuseDetection.action) {
                AbuseAction.REJECT -> throw IllegalArgumentException(
                    abuseDetection.warning ?: "Message violates usage guidelines"
                )
                AbuseAction.COOLDOWN -> throw IllegalStateException(
                    abuseDetection.warning ?: "Please wait before sending another message"
                )
                AbuseAction.THROTTLE -> throw IllegalStateException(
                    abuseDetection.warning ?: "Please slow down"
                )
                else -> {
                    // For REVIEW or TRUNCATE, log but continue
                    Timber.w("Abuse detection flagged message: ${abuseDetection.warning}")
                }
            }
        }
        
        // 3. Load prior conversation context before saving the current user message.
        // Firebase AI chat history must contain completed prior turns, not the message
        // that is about to be sent again via sendMessage().
        val recentMessages = chatRepository.observeConversation(userId, activeConversationId)
            .first()
            .takeLast(10)
        Timber.i(
            "[AI] SendChatMessageUseCase: loaded prior conversation context conversation=%s messages=%d",
            activeConversationId,
            recentMessages.size
        )

        // 4. Get user chat preferences for personalization before the AI call
        val currentPreferences = chatRepository.observePreferences(userId).first()
        val userPreferences = currentPreferences?.userContextPrompt

        // 5. Save user message
        val userMessage = chatRepository.saveMessage(
            userId = userId,
            message = message,
            type = MessageType.USER,
            conversationId = activeConversationId,
            language = language,
            workoutContext = workoutContext
        ).getOrThrow()
        
        Timber.i("[AI] SendChatMessageUseCase: user message saved id=${userMessage.id}")
        
        // 6. Build conversation context
        val conversationContext = com.example.liftrix.domain.service.ConversationContext(
            recentMessages = recentMessages,
            workoutContext = workoutContext,
            userPreferences = userPreferences
        )
        
        // 7. Get AI response
        Timber.i("[AI] SendChatMessageUseCase: requesting AI response conversation=$activeConversationId priorMessages=${recentMessages.size}")
        val aiResponse = aiChatService.generateResponse(
            userId = userId,
            message = message,
            conversationContext = conversationContext,
            language = if (language == "ro") com.example.liftrix.domain.service.Language.ROMANIAN else com.example.liftrix.domain.service.Language.ENGLISH
        ).getOrThrow()
        
        Timber.i("[AI] SendChatMessageUseCase: AI response generated processingMs=${aiResponse.processingTimeMs} tokens=${aiResponse.tokensUsed}")
        
        // 7. Save AI response
        val assistantMessage = chatRepository.saveMessage(
            userId = userId,
            message = aiResponse.content,
            type = MessageType.AI_RESPONSE,
            conversationId = activeConversationId,
            language = language,
            workoutContext = workoutContext,
            tokenCount = aiResponse.tokensUsed,
            processingTimeMs = aiResponse.processingTimeMs
        ).getOrThrow()
        
        Timber.i("[AI] SendChatMessageUseCase: AI message saved id=${assistantMessage.id}")
        
        // Return both messages
        userMessage to assistantMessage
    }
    
    /**
     * Generates a unique conversation ID based on timestamp and user ID.
     */
    private fun generateConversationId(): String {
        val timestamp = System.currentTimeMillis()
        return "chat_${timestamp}_${(1000..9999).random()}"
    }
}
