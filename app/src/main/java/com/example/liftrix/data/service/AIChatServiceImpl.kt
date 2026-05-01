package com.example.liftrix.data.service

import com.example.liftrix.BuildConfig
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
import com.google.firebase.FirebaseException
import com.google.firebase.ai.ai
import com.google.firebase.appcheck.appCheck
import com.example.liftrix.LiftrixApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.google.firebase.ai.Chat
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.content
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import kotlin.random.Random
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
        private const val MONTHLY_USAGE_TAG = "MonthlyUsageDebug"
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
                is DebugAppCheckTokenNotRegisteredException -> LiftrixError.BusinessLogicError(
                    code = "APP_CHECK_DEBUG_TOKEN_NOT_REGISTERED",
                    errorMessage = throwable.message ?: "Debug App Check token is not registered.",
                    analyticsContext = mapOf("user_id" to userId)
                )
                is AppCheckRateLimitedException -> LiftrixError.BusinessLogicError(
                    code = "APP_CHECK_RATE_LIMITED",
                    errorMessage = "AI security verification is temporarily rate limited. Please try again later.",
                    analyticsContext = mapOf("user_id" to userId)
                )
                is AppCheckUnavailableException -> LiftrixError.BusinessLogicError(
                    code = "APP_CHECK_UNAVAILABLE",
                    errorMessage = "AI security verification is temporarily unavailable. Please try again later.",
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
        Timber.tag(MONTHLY_USAGE_TAG).d(
            "AI chat service rate limit result userId=%s isLimited=%s reason=%s dailyRemaining=%s monthlyRemaining=%s source=RateLimitingService",
            userId,
            rateLimit.isLimited,
            rateLimit.reason ?: "none",
            rateLimit.messagesRemaining?.toString() ?: "unknown",
            rateLimit.tokensRemaining?.toString() ?: "unknown"
        )
        if (rateLimit.isLimited) {
            if (rateLimit.tokensRemaining == 0) {
                Timber.tag(MONTHLY_USAGE_TAG).w(
                    "AI chat service blocked by monthly limit userId=%s reason=%s source=RateLimitingService",
                    userId,
                    rateLimit.reason ?: "unknown"
                )
            }
            throw QuotaExceededException("Rate limit exceeded: ${rateLimit.reason}")
        }
        
        // 5. Build context prompt
        val contextPrompt = buildContextPrompt(conversationContext, detectedLanguage)
        
        // 6. Generate response using Firebase AI with enhanced error handling
        val startTime = System.currentTimeMillis()
        
        val response = try {
            Timber.d("AIChatService: Initializing Firebase AI for user $userId")
            
            // CRITICAL: Wait for App Check initialization before making AI calls
            // Increased timeout to 30 seconds to handle slower networks and first-time attestation
            val isAppCheckReady = withTimeoutOrNull(30_000) { // 30 second timeout
                LiftrixApp.getInstance().isAppCheckInitialized.first { it }
                true
            } ?: false
            
            if (!isAppCheckReady) {
                Timber.e("AIChatService: App Check not ready after 30 seconds")
                throw AppCheckUnavailableException("App Check was not initialized before Firebase AI request.")
            }
            
            // Obtain App Check token with proper async handling and exponential backoff
            val appCheckToken = obtainAppCheckToken()
            
            if (appCheckToken == null) {
                val message = "Failed to obtain App Check token after retries. " +
                    "Debug builds require a registered App Check debug token; release builds require Play Integrity, package, and SHA setup."
                Timber.e("AIChatService: $message")
                throw AppCheckUnavailableException(message)
            } else {
                Timber.d("AIChatService: App Check token obtained successfully from cache path, expiresAt=${appCheckToken.expireTimeMillis}")
            }
            
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
            
            Timber.d("AIChatService: Creating chat session with ${chatHistory.size} context messages")
            
            // Create chat session
            val chat = generativeModel.startChat(chatHistory)
            
            // Send message with context
            val fullMessage = if (contextPrompt.isNotEmpty()) {
                "$contextPrompt\n\nUser: $message"
            } else {
                message
            }
            
            Timber.d("AIChatService: Sending message to Firebase AI (${fullMessage.length} chars)")
            
            chat.sendMessage(content { text(fullMessage) })
            
        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            
            Timber.e(e, "Firebase AI request failed for user $userId after ${processingTime}ms")
            
            // Track the specific Firebase AI error
            analyticsTracker.trackAIChatError(
                userId = userId,
                errorType = "FIREBASE_AI_ERROR",
                errorMessage = e.message ?: "Unknown Firebase AI error",
                modelVersion = MODEL_NAME,
                additionalProperties = mapOf(
                    "processing_time_ms" to processingTime,
                    "has_workout_context" to (conversationContext.workoutContext != null)
                )
            )
            
            // Map specific Firebase AI exceptions
            when (e) {
                is java.net.UnknownHostException,
                is java.net.SocketTimeoutException,
                is java.io.IOException -> {
                    Timber.e("Network error calling Firebase AI: ${e.message}")
                    throw Exception("Network connectivity issue. Please check your connection and try again.")
                }
                is DebugAppCheckTokenNotRegisteredException -> throw e
                is AppCheckRateLimitedException -> throw e
                is AppCheckUnavailableException -> throw e
                is SecurityException -> {
                    Timber.e("Firebase AI permissions error: ${e.message}")
                    throw Exception("AI service security configuration error. Verify Firebase App Check provider, package name, and SHA fingerprints.")
                }
                is IllegalStateException -> {
                    Timber.e("Firebase AI state error: ${e.message}")
                    throw Exception("AI service temporarily unavailable. Please try again in a moment.")
                }
                else -> {
                    Timber.e("Unexpected Firebase AI error: ${e.javaClass.simpleName} - ${e.message}")
                    
                    // Check for specific App Check token errors
                    val errorMessage = e.message?.lowercase() ?: ""
                    when {
                        errorMessage.contains("app check token is invalid") ||
                        errorMessage.contains("appcheck") -> {
                            Timber.e("App Check token validation failed - this indicates Firebase App Check is not properly configured")
                            throw Exception("AI service security validation failed. Register the debug token for debug builds or verify Play Integrity/SHA setup for release.")
                        }
                        e.javaClass.name.contains("firebase") || 
                        errorMessage.contains("firebase") -> {
                            throw Exception("AI service temporarily unavailable. Please try again in a moment.")
                        }
                        else -> {
                            throw Exception("Failed to get AI response. Please try again.")
                        }
                    }
                }
            }
        }
        
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
    
    /**
     * Obtains an App Check token using the SDK cache path.
     * 
     * @return AppCheckToken if successful, null if the request timed out
     */
    private suspend fun obtainAppCheckToken(): AppCheckToken? {
        Timber.d("AIChatService: Attempting cached App Check token retrieval")
        val tokenResult = withTimeoutOrNull(10_000) {
            try {
                Firebase.appCheck.getAppCheckToken(false).await()
            } catch (e: Exception) {
                handleAppCheckTokenFailure(e)
            }
        }

        if (tokenResult == null) {
            Timber.w("AIChatService: App Check token request timed out")
            delay(appCheckBackoffDelayMillis())
            return null
        }

        Timber.d("AIChatService: Successfully obtained App Check token via cached retrieval")
        return AppCheckToken(
            expireTimeMillis = tokenResult.expireTimeMillis
        )
    }

    private fun handleAppCheckTokenFailure(error: Exception): Nothing {
        val errorMsg = error.message.orEmpty()
        if (isDebugAppCheckTokenMissing(error)) {
            logDebugAppCheckTokenInstructions(error)
            throw DebugAppCheckTokenNotRegisteredException(error)
        }

        Timber.w(
            error,
            "AIChatService: Failed to get cached App Check token. type=%s, message=%s",
            error.javaClass.name,
            errorMsg
        )

        when {
            isAppCheckRateLimited(error) -> {
                Timber.e("AIChatService: App Check token request was rate limited by Firebase; not retrying in this chat request.")
                throw AppCheckRateLimitedException(error)
            }
            isAppCheckAttestationFailure(error) -> {
                logDebugAppCheckTokenInstructions(error)
                throw DebugAppCheckTokenNotRegisteredException(error)
            }
            else -> throw AppCheckUnavailableException("Failed to obtain App Check token.", error)
        }
    }

    private fun appCheckBackoffDelayMillis(): Long {
        val jitter = Random.nextLong(0L, 250L)
        return 1_000L + jitter
    }

    private fun isDebugAppCheckTokenMissing(error: Throwable): Boolean {
        return BuildConfig.DEBUG &&
            error is FirebaseException &&
            isAppCheckAttestationFailure(error)
    }

    private fun isAppCheckRateLimited(error: Throwable): Boolean =
        error is FirebaseException &&
            error.message.orEmpty().contains("too many attempts", ignoreCase = true)

    private fun isAppCheckAttestationFailure(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return error is FirebaseException &&
            (
                message.contains("403", ignoreCase = true) &&
                    message.contains("app attestation failed", ignoreCase = true)
                )
    }

    private fun logDebugAppCheckTokenInstructions(error: Throwable) {
        Timber.e(error, "Debug App Check token is not registered in Firebase Console for this Firebase app/project.")
        Timber.e("Find the generated token in Logcat by searching for:")
        Timber.e("Enter this debug secret into the allow list in the Firebase Console")
        Timber.e("Register it at Firebase Console > App Check > com.example.liftrix > Manage debug tokens.")
        Timber.e("Expected project: liftrix-390cf; package: com.example.liftrix; appId: 1:734273269747:android:39d5352dabb68d74202c86")
        Timber.e("After adding the token, uninstall/reinstall the debug app or clear app data and run again.")
    }
    
    /**
     * Data class to hold App Check token information.
     */
    private data class AppCheckToken(
        val expireTimeMillis: Long
    )
}

// Custom exceptions
class QuotaExceededException(message: String) : Exception(message)
class ValidationException(message: String) : Exception(message)
class DebugAppCheckTokenNotRegisteredException(
    cause: Throwable
) : Exception(
    "Debug App Check token is not registered in Firebase Console for com.example.liftrix in project liftrix-390cf. " +
        "Search Logcat for 'Enter this debug secret into the allow list in the Firebase Console', add that token under App Check > com.example.liftrix > Manage debug tokens, then clear app data or reinstall.",
    cause
)
class AppCheckRateLimitedException(
    cause: Throwable
) : Exception("Firebase App Check token exchange is rate limited. Wait before retrying.", cause)
class AppCheckUnavailableException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
