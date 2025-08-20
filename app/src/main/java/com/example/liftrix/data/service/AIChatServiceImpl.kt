package com.example.liftrix.data.service

import com.example.liftrix.data.remote.config.RemoteConfigManager
import com.example.liftrix.domain.model.chat.ChatMessage
import com.example.liftrix.domain.model.chat.MessageType
import com.example.liftrix.domain.model.chat.WorkoutContext
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.ChatRepository
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.service.AIResponse
import com.example.liftrix.domain.service.AIChatService
import com.example.liftrix.domain.service.ConversationContext
import com.example.liftrix.domain.service.Language
import com.example.liftrix.domain.service.ModelStatus
import com.example.liftrix.domain.service.PromptValidation
import com.example.liftrix.domain.service.RateLimitStatus
import com.example.liftrix.domain.service.AnalyticsTracker
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.Chat
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.content
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of AIChatService using Firebase AI with Gemini 2.5 Flash Lite.
 * Provides cost-efficient, context-aware AI responses for workout guidance.
 */
@Singleton
class AIChatServiceImpl @Inject constructor(
    private val chatRepository: ChatRepository,
    private val workoutRepository: WorkoutRepository,
    private val analyticsTracker: AnalyticsTracker,
    private val remoteConfig: RemoteConfigManager,
    private val abusePreventionService: AbusePreventionService,
    private val rateLimitingService: RateLimitingService
) : AIChatService {
    
    companion object {
        private const val MODEL_NAME = "gemini-2.5-flash-lite"
        private const val MAX_OUTPUT_TOKENS = 500
        private const val TEMPERATURE = 0.7
        private const val TOP_K = 40
        private const val TOP_P = 0.95
        private const val MAX_CONTEXT_MESSAGES = 10
    }
    
    private val generationConfig = GenerationConfig.Builder()
        .setTemperature(TEMPERATURE.toFloat())
        .setTopK(TOP_K)
        .setTopP(TOP_P.toFloat())
        .setMaxOutputTokens(MAX_OUTPUT_TOKENS)
        .build()
    
    override suspend fun generateResponse(
        userId: String,
        message: String,
        conversationContext: ConversationContext,
        language: Language
    ): LiftrixResult<AIResponse> = liftrixCatching(
        errorMapper = { throwable ->
            when (throwable) {
                is QuotaExceededException -> LiftrixError.BusinessLogicError(
                    code = "QUOTA_EXCEEDED",
                    errorMessage = "Daily message limit reached. Resets at midnight.",
                    analyticsContext = mapOf("user_id" to userId)
                )
                is ValidationException -> LiftrixError.ValidationError(
                    field = "message",
                    violations = listOf(throwable.message ?: "Invalid message"),
                    analyticsContext = mapOf("user_id" to userId)
                )
                else -> LiftrixError.NetworkError(
                    errorMessage = "Failed to get AI response. Please try again.",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        }
    ) {
        // 1. Detect language if needed
        val detectedLanguage = if (language == Language.ENGLISH && containsRomanian(message)) {
            Language.ROMANIAN
        } else {
            language
        }
        
        // 2. Validate prompt
        val validation = validatePrompt(message, detectedLanguage)
        if (!validation.isValid) {
            throw ValidationException(validation.reason ?: "Invalid prompt")
        }
        
        // 3. Check for abuse patterns
        val abuseDetection = abusePreventionService.detectAbuse(userId, message)
        if (abuseDetection.isAbusive) {
            Timber.w("Abuse detected for user $userId: ${abuseDetection.type}")
            analyticsTracker.trackAbuseDetection(
                userId = userId,
                abuseType = abuseDetection.type?.name ?: "UNKNOWN",
                action = abuseDetection.action?.name ?: "UNKNOWN",
                confidence = abuseDetection.confidence,
                messageLength = message.length
            )
            
            when (abuseDetection.action) {
                com.example.liftrix.domain.service.AbuseAction.BLOCK,
                com.example.liftrix.domain.service.AbuseAction.REJECT -> {
                    throw ValidationException("Your message appears to violate our usage guidelines. Please rephrase and try again.")
                }
                com.example.liftrix.domain.service.AbuseAction.TRUNCATE -> {
                    // Truncate message to 500 characters
                    message.take(500)
                }
                else -> {
                    // Continue with warning logged
                }
            }
        }
        
        // 4. Check rate limits
        val rateLimit = checkRateLimit(userId)
        if (rateLimit.isLimited) {
            throw QuotaExceededException("Rate limit exceeded: ${rateLimit.reason}")
        }
        
        // 5. Build context prompt
        val contextPrompt = buildContextPrompt(conversationContext, detectedLanguage)
        
        // 6. Generate response using Firebase AI
        val startTime = System.currentTimeMillis()
        
        // Create model with language-specific system instruction
        val generativeModel = Firebase.ai().generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig,
            systemInstruction = content { text(getSystemPrompt(detectedLanguage)) }
        )
        
        // Build conversation history
        val chatHistory = conversationContext.recentMessages
            .takeLast(MAX_CONTEXT_MESSAGES)
            .map { msg ->
                content(role = if (msg.type == MessageType.USER) "user" else "model") {
                    text(msg.content)
                }
            }
        
        // Create chat session
        val chat = generativeModel.startChat(chatHistory)
        
        // Send message with context
        val fullMessage = if (contextPrompt.isNotEmpty()) {
            "$contextPrompt\n\nUser: $message"
        } else {
            message
        }
        
        val response = chat.sendMessage(content { text(fullMessage) })
        val processingTime = System.currentTimeMillis() - startTime
        
        // Extract response text and metadata
        val responseText = response.text ?: "I couldn't generate a response. Please try again."
        val tokensUsed = response.usageMetadata?.totalTokenCount ?: estimateTokens(message, responseText)
        
        // 7. Track usage and analytics
        analyticsTracker.trackAIChatResponse(
            userId = userId,
            tokensUsed = tokensUsed,
            processingTimeMs = processingTime,
            modelVersion = MODEL_NAME,
            language = detectedLanguage.code,
            hasWorkoutContext = conversationContext.workoutContext != null
        )
        
        AIResponse(
            content = responseText,
            tokensUsed = tokensUsed,
            processingTimeMs = processingTime,
            modelVersion = MODEL_NAME
        )
    }
    
    override suspend fun validatePrompt(
        message: String,
        language: Language
    ): PromptValidation {
        // Check message length
        if (message.isBlank()) {
            return PromptValidation(
                isValid = false,
                reason = if (language == Language.ROMANIAN) 
                    "Mesajul nu poate fi gol" 
                else 
                    "Message cannot be empty"
            )
        }
        
        if (message.length > 2000) {
            return PromptValidation(
                isValid = false,
                reason = if (language == Language.ROMANIAN)
                    "Mesajul este prea lung (max 2000 caractere)"
                else
                    "Message is too long (max 2000 characters)"
            )
        }
        
        // Check for minimum coherence (at least 2 words for non-greeting messages)
        val words = message.split("\\s+".toRegex())
        if (words.size < 2 && !isGreeting(message)) {
            return PromptValidation(
                isValid = false,
                reason = if (language == Language.ROMANIAN)
                    "Vă rugăm să furnizați mai multe detalii"
                else
                    "Please provide more details",
                confidence = 0.7f
            )
        }
        
        return PromptValidation(isValid = true)
    }
    
    override suspend fun detectLanguage(message: String): Language {
        return if (containsRomanian(message)) Language.ROMANIAN else Language.ENGLISH
    }
    
    override suspend fun checkRateLimit(userId: String): RateLimitStatus {
        return rateLimitingService.checkLimits(userId)
    }
    
    override suspend fun getModelStatus(): ModelStatus {
        return ModelStatus(
            isAvailable = true,
            modelName = MODEL_NAME,
            version = "2.5",
            responseTimeMs = remoteConfig.getLong("ai_avg_response_time_ms").getOrDefault(1500)
        )
    }
    
    private fun buildContextPrompt(context: ConversationContext, language: Language): String {
        val parts = mutableListOf<String>()
        
        // Add user preferences
        context.userPreferences?.let {
            val prefix = if (language == Language.ROMANIAN) "Preferințe utilizator:" else "User preferences:"
            parts.add("$prefix $it")
        }
        
        // Add workout context
        context.workoutContext?.let { workout ->
            if (language == Language.ROMANIAN) {
                parts.add("""
                    Context antrenament:
                    ${workout.exerciseName?.let { "Exercițiu curent: $it" } ?: ""}
                    ${workout.exerciseCategory?.let { "Categorie: $it" } ?: ""}
                    ${workout.muscleGroups.takeIf { it.isNotEmpty() }?.let { "Grupe musculare: ${it.joinToString()}" } ?: ""}
                    ${workout.workoutDuration?.let { "Durată: ${it / 60000} minute" } ?: ""}
                """.trimIndent())
            } else {
                parts.add("""
                    Workout context:
                    ${workout.exerciseName?.let { "Current exercise: $it" } ?: ""}
                    ${workout.exerciseCategory?.let { "Category: $it" } ?: ""}
                    ${workout.muscleGroups.takeIf { it.isNotEmpty() }?.let { "Muscle groups: ${it.joinToString()}" } ?: ""}
                    ${workout.workoutDuration?.let { "Duration: ${it / 60000} minutes" } ?: ""}
                """.trimIndent())
            }
        }
        
        return parts.joinToString("\n")
    }
    
    private fun getSystemPrompt(language: Language): String {
        return when (language) {
            Language.ENGLISH -> """
                You are a knowledgeable fitness coach for the Liftrix app. 
                
                Core principles:
                - Keep responses concise and actionable (prefer 2-3 sentences)
                - Focus on safe, progressive workout guidance
                - Consider injury prevention and proper form in all advice
                
                Boundaries:
                - For injuries: Acknowledge them, suggest modifications, but always recommend consulting healthcare professionals for diagnosis/treatment
                - For supplements: Provide general education but emphasize consulting healthcare providers for personal recommendations
                - For nutrition: Share general principles but refer to registered dietitians for medical conditions
                - Never diagnose medical conditions or prescribe treatments
                
                Context awareness:
                - Consider user's recent workouts and stated preferences
                - Adapt advice to user's apparent fitness level
                - If asked about non-fitness topics, gently redirect: "I'm focused on helping with your fitness journey. About your workouts..."
            """.trimIndent()
            
            Language.ROMANIAN -> """
                Ești un antrenor de fitness cu experiență pentru aplicația Liftrix.
                
                Principii de bază:
                - Păstrează răspunsurile concise și practice (preferabil 2-3 propoziții)
                - Concentrează-te pe îndrumări sigure și progresive pentru antrenament
                - Ia în considerare prevenirea accidentărilor și forma corectă în toate sfaturile
                
                Limite:
                - Pentru accidentări: Recunoaște-le, sugerează modificări, dar recomandă întotdeauna consultarea profesioniștilor din sănătate pentru diagnostic/tratament
                - Pentru suplimente: Oferă educație generală, dar subliniază consultarea furnizorilor de servicii medicale pentru recomandări personale
                - Pentru nutriție: Împărtășește principii generale, dar trimite la dieteticieni înregistrați pentru condiții medicale
                - Nu diagnostica niciodată condiții medicale sau nu prescrie tratamente
                
                Conștientizarea contextului:
                - Ia în considerare antrenamentele recente ale utilizatorului și preferințele declarate
                - Adaptează sfaturile la nivelul aparent de fitness al utilizatorului
                - Dacă ești întrebat despre subiecte non-fitness, redirecționează delicat: "Sunt concentrat să te ajut în călătoria ta de fitness. Despre antrenamentele tale..."
            """.trimIndent()
        }
    }
    
    private fun containsRomanian(text: String): Boolean {
        val romanianIndicators = listOf(
            "ă", "â", "î", "ș", "ț", // Romanian diacritics
            " și ", " sau ", " pentru ", " este ", " sunt ", // Common Romanian words
            "antrenament", "exerciți", "muschi", "greutate"
        )
        return romanianIndicators.any { text.lowercase().contains(it) }
    }
    
    private fun isGreeting(message: String): Boolean {
        val greetings = listOf(
            "hi", "hello", "hey", "salut", "bună", "buna", 
            "servus", "neața", "noroc", "yo", "hola"
        )
        return greetings.any { message.lowercase().startsWith(it) }
    }
    
    private fun estimateTokens(input: String, output: String): Int {
        // Rough estimation: ~1 token per 4 characters
        return (input.length + output.length) / 4
    }
}

// Custom exceptions
class QuotaExceededException(message: String) : Exception(message)
class ValidationException(message: String) : Exception(message)