package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.common.LiftrixResult

/**
 * Service interface for AI chat functionality using Firebase AI.
 * Provides intelligent workout assistance through Gemini 2.5 Flash Lite.
 */
interface AIChatService {
    
    /**
     * Generates an AI response to a user message.
     *
     * @param userId The user ID for rate limiting and context
     * @param message The user's message
     * @param conversationContext Context from the conversation including recent messages
     * @param language The language for the response (English or Romanian)
     * @param autoDetectLanguage Whether the service may override the preferred language based on the message
     * @return AI-generated response with metadata
     */
    suspend fun generateResponse(
        userId: String,
        message: String,
        conversationContext: ConversationContext,
        language: Language = Language.ENGLISH,
        autoDetectLanguage: Boolean = true
    ): LiftrixResult<AIResponse>
    
    /**
     * Validates a prompt for safety and appropriateness.
     *
     * @param message The message to validate
     * @param language The language of the message
     * @return Validation result with details
     */
    suspend fun validatePrompt(
        message: String,
        language: Language
    ): PromptValidation
    
    /**
     * Detects the language of a message.
     *
     * @param message The message to analyze
     * @return Detected language
     */
    suspend fun detectLanguage(message: String): Language
    
    /**
     * Checks the current rate limit status for a user.
     *
     * @param userId The user to check
     * @return Current rate limit status
     */
    suspend fun checkRateLimit(userId: String): RateLimitStatus
    
    /**
     * Gets the current status of the AI model.
     *
     * @return Model status information
     */
    suspend fun getModelStatus(): ModelStatus
}

/**
 * AI response with metadata.
 */
data class AIResponse(
    val content: String,
    val tokensUsed: Int,
    val processingTimeMs: Long,
    val modelVersion: String
)

/**
 * Conversation context for maintaining continuity.
 */
data class ConversationContext(
    val recentMessages: List<com.example.liftrix.domain.model.chat.ChatMessage>,
    val workoutContext: com.example.liftrix.domain.model.chat.WorkoutContext?,
    val userPreferences: String?,
    val includeWorkoutHistory: Boolean = true,
    val aiResponseStyle: String = "balanced",
    val includeExerciseFormTips: Boolean = true
)

/**
 * Result of prompt validation.
 */
data class PromptValidation(
    val isValid: Boolean,
    val reason: String? = null,
    val confidence: Float = 1.0f
)

/**
 * Rate limit status for a user.
 */
data class RateLimitStatus(
    val isLimited: Boolean,
    val reason: String? = null,
    val resetTime: Long? = null,
    val messagesRemaining: Int? = null,
    val tokensRemaining: Int? = null,
    val isNearLimit: Boolean = false
)

/**
 * Status of the AI model.
 */
data class ModelStatus(
    val isAvailable: Boolean,
    val modelName: String,
    val version: String,
    val responseTimeMs: Long? = null
)

/**
 * Abuse detection result.
 */
data class AbuseDetection(
    val isAbusive: Boolean,
    val type: AbuseType? = null,
    val action: AbuseAction? = null,
    val confidence: Float = 1.0f,
    val warning: String? = null
)

/**
 * Types of detected abuse.
 */
enum class AbuseType {
    JAILBREAK,
    RATE_ANOMALY,
    EXCESSIVE_INPUT,
    SPAM,
    NONSENSE
}

/**
 * Actions to take for detected abuse.
 */
enum class AbuseAction {
    BLOCK,
    REVIEW,
    COOLDOWN,
    THROTTLE,
    REJECT,
    TRUNCATE
}
