package com.example.liftrix.data.service

import com.example.liftrix.data.remote.config.RemoteConfigManager
import com.example.liftrix.domain.model.chat.ChatMessage
import com.example.liftrix.domain.model.chat.MessageType
import com.example.liftrix.domain.model.chat.WorkoutContext
import com.example.liftrix.domain.model.Workout
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
import com.example.liftrix.domain.service.RateLimitingServiceContract
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import kotlinx.coroutines.flow.first
import com.google.firebase.ai.Chat
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.content
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
    private val rateLimitingService: RateLimitingServiceContract,
    private val paidAiCallExecutor: PaidAiCallExecutor
) : AIChatService {
    
    companion object {
        private const val MODEL_NAME = "gemini-2.5-flash-lite"
        private const val MAX_OUTPUT_TOKENS = 1024
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
        language: Language,
        autoDetectLanguage: Boolean
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
                    errorMessage = throwable.message ?: "Invalid message",
                    analyticsContext = mapOf("user_id" to userId)
                )
                is PaidAiDisabledException -> LiftrixError.BusinessLogicError(
                    code = "AI_DISABLED",
                    errorMessage = throwable.message.orEmpty(),
                    analyticsContext = mapOf("user_id" to userId)
                )
                is PaidAiAccessDeniedException -> LiftrixError.BusinessLogicError(
                    code = "AI_ACCESS_DENIED",
                    errorMessage = throwable.message.orEmpty(),
                    analyticsContext = mapOf("operation" to "CHAT_RESPONSE")
                )
                is PaidAiControlUnavailableException -> LiftrixError.BusinessLogicError(
                    code = "AI_CONTROL_UNAVAILABLE",
                    errorMessage = "AI is temporarily unavailable while operator controls are refreshed. Please try again.",
                    isRecoverable = true,
                    analyticsContext = mapOf("operation" to "CHAT_RESPONSE")
                )
                is PaidAiTimeoutException -> LiftrixError.BusinessLogicError(
                    code = "AI_REQUEST_TIMEOUT",
                    errorMessage = "The AI request took too long. Please try again or use a saved workout.",
                    isRecoverable = true,
                    analyticsContext = mapOf("operation" to "CHAT_RESPONSE")
                )
                is PaidAiPolicyDeniedException -> LiftrixError.ValidationError(
                    field = "message",
                    violations = listOf(throwable.message.orEmpty()),
                    errorMessage = throwable.message.orEmpty(),
                    analyticsContext = mapOf("user_id" to userId)
                )
                is PaidAiRecoverableDenialException -> LiftrixError.BusinessLogicError(
                    code = "AI_REQUEST_THROTTLED",
                    errorMessage = throwable.message.orEmpty(),
                    isRecoverable = true,
                    analyticsContext = mapOf("user_id" to userId)
                )
                is DebugAppCheckTokenNotRegisteredException -> LiftrixError.BusinessLogicError(
                    code = "APP_CHECK_DEBUG_TOKEN_NOT_REGISTERED",
                    errorMessage = aiUnavailableForAppCheckMessage(),
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
                is AIResponseMaxTokensException -> LiftrixError.BusinessLogicError(
                    code = "AI_RESPONSE_MAX_TOKENS",
                    errorMessage = "The AI response was cut off because it was too long. Please ask for a shorter answer or try again.",
                    isRecoverable = true,
                    analyticsContext = mapOf("user_id" to userId)
                )
                else -> LiftrixError.NetworkError(
                    errorMessage = "Failed to get AI response. Please try again.",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        }
    ) {
        val effectiveMessage = message.trim()
        Timber.i(
            "[AI] AIChatService: generateResponse started messageChars=%d historyMessages=%d requestedLanguage=%s",
            effectiveMessage.length,
            conversationContext.recentMessages.size,
            language.code
        )

        // 1. Detect language if needed
        val detectedLanguage = if (autoDetectLanguage && language == Language.ENGLISH && containsRomanian(effectiveMessage)) {
            Language.ROMANIAN
        } else {
            language
        }
        
        // 2. Validate prompt
        val validation = validatePrompt(effectiveMessage, detectedLanguage)
        if (!validation.isValid) {
            Timber.w(
                "[AI] AIChatService: prompt validation failed reason=%s messageChars=%d",
                validation.reason ?: "unknown",
                effectiveMessage.length
            )
            throw ValidationException(validation.reason ?: "Invalid prompt")
        }
        Timber.d("[AI] AIChatService: prompt validation passed")

        // 3. Build product-specific context and model input. Paid guards run in the executor.
        val contextPrompt = buildContextPrompt(userId, conversationContext, detectedLanguage)
        val systemPrompt = getSystemPrompt(detectedLanguage)
        val generativeModel = Firebase.ai().generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig,
            systemInstruction = content { text(systemPrompt) }
        )
        val historyMessages = buildValidChatHistoryMessages(conversationContext.recentMessages)
        val chatHistory = historyMessages.map { chatMessage ->
            content(role = if (chatMessage.type == MessageType.USER) "user" else "model") {
                text(chatMessage.content)
            }
        }
        val fullMessage = if (contextPrompt.isNotEmpty()) {
            "$contextPrompt\n\nUser: $effectiveMessage"
        } else {
            effectiveMessage
        }
        val estimatedInputTokens = estimateTokens(
            systemPrompt + historyMessages.joinToString(separator = "") { it.content },
            fullMessage
        )
        val chatSession = generativeModel.startChat(chatHistory)
        val startTime = System.currentTimeMillis()
        val dispatchResult = paidAiCallExecutor.execute(
            request = PaidAiCallRequest(
                userId = userId,
                operation = PaidAiOperation.CHAT_RESPONSE,
                model = MODEL_NAME,
                abuseContent = effectiveMessage,
                estimatedInputTokens = estimatedInputTokens
            )
        ) {
            dispatchChatMessage(
                chat = chatSession,
                fullMessage = fullMessage,
                estimatedInputTokens = estimatedInputTokens
            )
        }
        val processingTime = System.currentTimeMillis() - startTime
        if (dispatchResult.responseText.isBlank()) {
            throw IllegalStateException("Firebase AI returned an empty chat response")
        }

        analyticsTracker.trackAIChatResponse(
            userId = userId,
            tokensUsed = dispatchResult.totalTokens,
            processingTimeMs = processingTime,
            modelVersion = MODEL_NAME,
            language = detectedLanguage.code,
            hasWorkoutContext = conversationContext.workoutContext != null
        )
        
        AIResponse(
            content = dispatchResult.responseText,
            tokensUsed = dispatchResult.totalTokens,
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
        
        if (!message.any { it.isLetterOrDigit() }) {
            return PromptValidation(
                isValid = false,
                reason = "Message must include text",
                confidence = 1.0f
            )
        }

        return PromptValidation(isValid = true)

/*
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
*/
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
    
    private suspend fun buildContextPrompt(
        userId: String,
        context: ConversationContext,
        language: Language
    ): String {
        val parts = mutableListOf<String>()
        
        // Add user preferences
        context.userPreferences?.let {
            val prefix = if (language == Language.ROMANIAN) "Preferințe utilizator:" else "User preferences:"
            parts.add("$prefix $it")
        }
        
        val responseStyleInstruction = when (context.aiResponseStyle) {
            "concise" -> if (language == Language.ROMANIAN) {
                "Stil raspuns: raspunde concis, cu pasi scurti si directi."
            } else {
                "Response style: answer concisely with short, direct steps."
            }
            "detailed" -> if (language == Language.ROMANIAN) {
                "Stil raspuns: ofera explicatii mai detaliate, exemple si pasi concreti."
            } else {
                "Response style: give more detailed explanations, examples, and concrete steps."
            }
            else -> if (language == Language.ROMANIAN) {
                "Stil raspuns: echilibreaza claritatea cu detaliile utile."
            } else {
                "Response style: balance clarity with useful detail."
            }
        }
        parts.add(responseStyleInstruction)

        if (!context.includeExerciseFormTips) {
            parts.add(
                if (language == Language.ROMANIAN) {
                    "Nu include sfaturi de tehnica pentru exercitii decat daca utilizatorul le cere explicit."
                } else {
                    "Do not include exercise form tips unless the user explicitly asks for them."
                }
            )
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

        if (context.includeWorkoutHistory) {
            val recentWorkouts = loadRecentWorkoutsForAiContext(userId)
            if (recentWorkouts.isNotEmpty()) {
                parts.add(formatRecentWorkoutsForPrompt(recentWorkouts, language))
            }
        }
        
        return parts.joinToString("\n")
    }

    private suspend fun loadRecentWorkoutsForAiContext(userId: String): List<Workout> {
        return try {
            workoutRepository.getRecentWorkouts(userId, limit = 3)
                .first()
        } catch (exception: Exception) {
            Timber.w(exception, "[AI] AIChatService: failed to load recent workout context")
            emptyList()
        }
    }

    private fun formatRecentWorkoutsForPrompt(workouts: List<Workout>, language: Language): String {
        val header = if (language == Language.ROMANIAN) {
            "Antrenamente recente salvate in aplicatie:"
        } else {
            "Recent workouts saved in the app:"
        }
        val instruction = if (language == Language.ROMANIAN) {
            "Foloseste doar aceste date concrete. Nu inventa date, tipuri sau exercitii; daca lipseste ceva, spune ca nu este disponibil in istoric."
        } else {
            "Use only these concrete facts. Do not invent dates, workout types, or exercises; if something is missing, say it is not available in history."
        }
        val workoutLines = workouts.mapIndexed { index, workout ->
            "${index + 1}. ${workout.toAiPromptSummary()}"
        }
        return (listOf(header, instruction) + workoutLines).joinToString("\n")
    }

    private fun Workout.toAiPromptSummary(): String {
        val duration = getDuration()
            ?.toMinutes()
            ?.takeIf { it > 0 }
            ?.let { "$it min" }
            ?: "duration unavailable"
        val categories = getExerciseCategories()
            .takeIf { it.isNotEmpty() }
            ?.joinToString { it.name.toDisplayWords() }
            ?: "focus unavailable"
        val exercisesSummary = exercises
            .sortedBy { it.orderIndex }
            .take(6)
            .joinToString("; ") { exercise ->
                val sets = exercise.sets.takeIf { it.isNotEmpty() }?.let { "${it.size} sets" }
                    ?: exercise.targetSets?.let { "$it target sets" }
                val reps = exercise.sets
                    .mapNotNull { it.reps?.count }
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(prefix = "reps ", limit = 3)
                    ?: exercise.targetReps?.let { "target reps $it" }
                val weight = exercise.sets
                    .mapNotNull { it.weight?.format() }
                    .takeIf { it.isNotEmpty() }
                    ?.distinct()
                    ?.joinToString(prefix = "weights ", limit = 3)
                    ?: exercise.targetWeight?.format()?.let { "target weight $it" }
                listOfNotNull(
                    exercise.libraryExercise.name,
                    sets,
                    reps,
                    weight
                ).joinToString(" - ")
            }
            .ifBlank { "no exercises recorded" }
        return "$date - $name, status ${status.name.toDisplayWords()}, focus $categories, $duration, exercises: $exercisesSummary"
    }

    private fun String.toDisplayWords(): String =
        lowercase()
            .split("_")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

    private fun buildValidChatHistoryMessages(messages: List<ChatMessage>): List<ChatMessage> {
        val sorted = messages
            .filter { it.content.isNotBlank() }
            .filter { it.type == MessageType.USER || it.type == MessageType.AI_RESPONSE }
            .sortedBy { it.createdAt }
            .takeLast(MAX_CONTEXT_MESSAGES)

        val alternating = mutableListOf<ChatMessage>()
        sorted.forEach { message ->
            if (alternating.isEmpty() && message.type != MessageType.USER) {
                Timber.w("[AI] AIChatService: dropping leading model history message id=${message.id}")
                return@forEach
            }
            if (alternating.lastOrNull()?.type == message.type) {
                Timber.w("[AI] AIChatService: dropping non-alternating history message id=${message.id} type=${message.type}")
                return@forEach
            }
            alternating.add(message)
        }

        if (alternating.lastOrNull()?.type == MessageType.USER) {
            val dropped = alternating.removeAt(alternating.lastIndex)
            Timber.w("[AI] AIChatService: dropping dangling user history message id=${dropped.id} before sending current request")
        }

        return alternating.takeLast(MAX_CONTEXT_MESSAGES)
    }

    private suspend fun dispatchChatMessage(
        chat: Chat,
        fullMessage: String,
        estimatedInputTokens: Int
    ): PaidAiCallResult<ChatDispatchResult> {
        Timber.i("[AI] AIChatService: Firebase AI dispatch payloadChars=%d", fullMessage.length)
        val response = try {
            chat.sendMessage(content { text(fullMessage) })
        } catch (error: ResponseStoppedException) {
            if (error.isMaxTokensStop()) throw AIResponseMaxTokensException(error)
            throw error
        }
        val responseText = response.text?.trim().orEmpty()
        val usage = response.usageMetadata
        val inputTokens = usage?.promptTokenCount ?: estimatedInputTokens
        val outputTokens = usage?.candidatesTokenCount ?: estimateTokens("", responseText)
        val totalTokens = usage?.totalTokenCount ?: inputTokens + outputTokens
        return PaidAiCallResult(
            value = ChatDispatchResult(responseText = responseText, totalTokens = totalTokens),
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens,
            category = if (responseText.isBlank()) {
                PaidAiCallResult.CATEGORY_EMPTY_RESPONSE
            } else {
                PaidAiCallResult.CATEGORY_MODEL_RESPONSE
            }
        )
    }
    
    private fun getSystemPrompt(language: Language): String {
        return when (language) {
            Language.ENGLISH -> """
                You are Liftrix Coach, a knowledgeable, supportive fitness assistant inside the Liftrix app.

                Your role is to help users train safely, build consistency, improve technique, and progress gradually. Give practical fitness guidance based on the user's goals, recent workouts, preferences, available equipment, apparent fitness level, and any stated limitations.

                Core response style:
                - Be concise, clear, and actionable.
                - Default to 2-4 sentences unless the user asks for a detailed plan.
                - Use simple language, not medical or academic jargon.
                - Prioritize safety, progressive overload, proper form, recovery, and injury prevention.
                - When helpful, give specific next steps: sets, reps, effort level, rest time, modifications, or technique cues.

                Personalization:
                - Consider the user's recent workouts, training history, stated goals, equipment, schedule, preferences, and recovery.
                - Adapt advice to beginner, intermediate, or advanced users.
                - If key information is missing, ask one brief clarifying question.
                - If the user needs immediate help, give a safe default recommendation first, then ask a follow-up.

                Workout guidance:
                - Encourage gradual progression rather than sudden increases in load, volume, or intensity.
                - Avoid recommending max-effort lifts, training to failure, extreme volume, or unsafe rapid weight-loss strategies unless clearly appropriate and framed safely.
                - Include form cues when discussing exercises.
                - Suggest regressions, substitutions, or lower-impact options when needed.
                - Remind users to warm up, use controlled technique, and stop if they experience sharp pain, dizziness, chest pain, or unusual symptoms.

                Injury and pain boundaries:
                - Do not diagnose injuries, medical conditions, or causes of pain.
                - If a user reports pain or injury, acknowledge it, suggest conservative exercise modifications, and recommend consulting a qualified healthcare professional for diagnosis or treatment.
                - For severe, sudden, worsening, neurological, chest-related, or unexplained symptoms, advise urgent medical care.
                - Never prescribe rehabilitation protocols as medical treatment.

                Nutrition boundaries:
                - Provide general nutrition education for fitness goals, such as protein intake, hydration, meal timing, whole foods, and calorie balance.
                - Do not prescribe medical diets or nutrition treatment for health conditions.
                - For diabetes, eating disorders, pregnancy, kidney disease, heart disease, gastrointestinal disease, or other medical conditions, recommend working with a registered dietitian or healthcare professional.

                Supplement boundaries:
                - Provide general supplement education only.
                - Do not tell users they personally need a supplement.
                - Mention that supplement quality, interactions, medical history, and medications matter.
                - Recommend consulting a healthcare professional before starting supplements, especially if the user has medical conditions, takes medication, is pregnant, or is under 18.

                Mental health and body image:
                - Avoid shame, fear-based language, or extreme body-transformation promises.
                - Encourage sustainable habits, consistency, recovery, and self-efficacy.
                - If a user expresses disordered eating, self-harm, or severe distress, respond supportively and recommend professional help.

                Non-fitness topics:
                - If the user asks about something unrelated to fitness, gently redirect:
                  "I'm focused on helping with your fitness journey. About your workouts, I can help with..."

                Answer format:
                - For quick advice: give the recommendation first, then one reason.
                - For workout adjustments: mention what to change, how much to change it, and what to monitor.
                - For exercise form: give 2-3 key cues and one common mistake to avoid.
                - For plans: include exercises, sets, reps, rest, effort level, and progression guidance.

                Safety disclaimer style:
                - Do not overuse disclaimers.
                - Include brief safety guidance only when relevant.
                - Never claim to replace a doctor, physical therapist, registered dietitian, or certified medical professional.

                Example tone:
                Supportive, direct, and practical - like a good coach who keeps the user safe while helping them make progress.
                When the user has workout history available, proactively reference it. For example: "Since you trained legs yesterday, keep today's lower-body work light and focus on mobility or upper body."
                Never give generic motivation alone. Every response should include at least one useful action, adjustment, cue, or next step.
            """.trimIndent()
            
            Language.ROMANIAN -> """
                Ești Liftrix Coach, un asistent de fitness informat și susținător în aplicația Liftrix.

                Rolul tău este să ajuți utilizatorii să se antreneze în siguranță, să construiască consecvență, să își îmbunătățească tehnica și să progreseze treptat. Oferă îndrumare practică de fitness pe baza obiectivelor utilizatorului, antrenamentelor recente, preferințelor, echipamentului disponibil, nivelului aparent de fitness și oricăror limitări menționate.

                Stil de răspuns:
                - Fii concis, clar și practic.
                - În mod implicit, răspunde în 2-4 propoziții, cu excepția cazului în care utilizatorul cere un plan detaliat.
                - Folosește limbaj simplu, nu jargon medical sau academic.
                - Prioritizează siguranța, supraîncărcarea progresivă, forma corectă, recuperarea și prevenirea accidentărilor.
                - Când este util, oferă pași concreți: seturi, repetări, nivel de efort, timp de pauză, modificări sau indicii de tehnică.

                Personalizare:
                - Ia în considerare antrenamentele recente, istoricul de antrenament, obiectivele declarate, echipamentul, programul, preferințele și recuperarea utilizatorului.
                - Adaptează sfaturile pentru utilizatori începători, intermediari sau avansați.
                - Dacă lipsesc informații esențiale, pune o singură întrebare scurtă de clarificare.
                - Dacă utilizatorul are nevoie de ajutor imediat, oferă mai întâi o recomandare implicită sigură, apoi pune o întrebare de urmărire.

                Îndrumare pentru antrenamente:
                - Încurajează progresul treptat, nu creșteri bruște de greutate, volum sau intensitate.
                - Evită să recomanzi ridicări la efort maxim, antrenament până la eșec, volum extrem sau strategii nesigure de slăbire rapidă, cu excepția cazului în care sunt clar potrivite și formulate în siguranță.
                - Include indicii de formă când discuți exerciții.
                - Sugerează regresii, substituții sau opțiuni cu impact redus când este nevoie.
                - Amintește utilizatorilor să se încălzească, să folosească tehnică controlată și să se oprească dacă apar durere ascuțită, amețeală, durere în piept sau simptome neobișnuite.

                Limite pentru accidentări și durere:
                - Nu diagnostica accidentări, afecțiuni medicale sau cauze ale durerii.
                - Dacă un utilizator raportează durere sau accidentare, recunoaște problema, sugerează modificări conservatoare ale exercițiilor și recomandă consultarea unui profesionist medical calificat pentru diagnostic sau tratament.
                - Pentru simptome severe, bruște, care se agravează, neurologice, legate de piept sau inexplicabile, recomandă îngrijire medicală urgentă.
                - Nu prescrie niciodată protocoale de recuperare ca tratament medical.

                Limite pentru nutriție:
                - Oferă educație generală despre nutriție pentru obiective de fitness, cum ar fi aportul de proteine, hidratarea, momentul meselor, alimente integrale și echilibrul caloric.
                - Nu prescrie diete medicale sau tratamente nutriționale pentru afecțiuni.
                - Pentru diabet, tulburări de alimentație, sarcină, boală renală, boală cardiacă, boală gastrointestinală sau alte afecțiuni medicale, recomandă colaborarea cu un dietetician autorizat sau un profesionist medical.

                Limite pentru suplimente:
                - Oferă doar educație generală despre suplimente.
                - Nu spune utilizatorilor că au personal nevoie de un supliment.
                - Menționează că sunt importante calitatea suplimentelor, interacțiunile, istoricul medical și medicamentele.
                - Recomandă consultarea unui profesionist medical înainte de a începe suplimente, mai ales dacă utilizatorul are afecțiuni medicale, ia medicamente, este însărcinată sau are sub 18 ani.

                Sănătate mentală și imagine corporală:
                - Evită rușinarea, limbajul bazat pe frică sau promisiunile extreme de transformare corporală.
                - Încurajează obiceiuri sustenabile, consecvență, recuperare și încredere în propriile acțiuni.
                - Dacă un utilizator exprimă alimentație dezordonată, autovătămare sau suferință severă, răspunde susținător și recomandă ajutor profesional.

                Subiecte non-fitness:
                - Dacă utilizatorul întreabă despre ceva fără legătură cu fitnessul, redirecționează blând:
                  "Sunt concentrat să te ajut în parcursul tău de fitness. Despre antrenamentele tale, te pot ajuta cu..."

                Formatul răspunsului:
                - Pentru sfaturi rapide: oferă mai întâi recomandarea, apoi un motiv.
                - Pentru ajustări de antrenament: menționează ce să schimbe, cu cât să schimbe și ce să monitorizeze.
                - Pentru forma exercițiilor: oferă 2-3 indicii cheie și o greșeală frecventă de evitat.
                - Pentru planuri: include exerciții, seturi, repetări, pauză, nivel de efort și îndrumare pentru progresie.

                Stilul recomandărilor de siguranță:
                - Nu folosi excesiv disclaimere.
                - Include îndrumare scurtă de siguranță doar când este relevant.
                - Nu pretinde niciodată că înlocuiești un medic, fizioterapeut, dietetician autorizat sau profesionist medical certificat.

                Exemplu de ton:
                Susținător, direct și practic - ca un antrenor bun care menține utilizatorul în siguranță în timp ce îl ajută să progreseze.
                Când istoricul de antrenament al utilizatorului este disponibil, fă referire proactiv la el. De exemplu: "Pentru că ieri ai antrenat picioarele, menține ușoară munca pentru partea inferioară azi și concentrează-te pe mobilitate sau partea superioară."
                Nu oferi niciodată doar motivație generică. Fiecare răspuns trebuie să includă cel puțin o acțiune utilă, ajustare, indiciu sau pas următor.
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

    private fun ResponseStoppedException.isMaxTokensStop(): Boolean =
        message.orEmpty().contains("MAX_TOKENS", ignoreCase = true)
    
    private data class ChatDispatchResult(
        val responseText: String,
        val totalTokens: Int
    )
}

class ValidationException(message: String) : Exception(message)
