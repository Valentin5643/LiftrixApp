package com.example.liftrix.data.service

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.AnalyticsTracker
import com.example.liftrix.domain.service.Language
import com.example.liftrix.domain.service.WorkoutProgramGenerationService
import com.example.liftrix.domain.service.WorkoutProgramJsonResponse
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.content
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutProgramGenerationServiceImpl @Inject constructor(
    private val analyticsTracker: AnalyticsTracker,
    private val paidAiCallExecutor: PaidAiCallExecutor
) : WorkoutProgramGenerationService {
    private val generationConfig = GenerationConfig.Builder()
        .setTemperature(TEMPERATURE)
        .setTopK(TOP_K)
        .setTopP(TOP_P)
        .setMaxOutputTokens(MAX_OUTPUT_TOKENS)
        .build()

    override suspend fun generateProgramJson(
        userId: String,
        userPrompt: String,
        systemPrompt: String,
        inputPayload: String,
        language: Language
    ): LiftrixResult<WorkoutProgramJsonResponse> = generateJson(
        userId = userId,
        userPrompt = userPrompt,
        systemPrompt = systemPrompt,
        requestPayload = inputPayload,
        language = language,
        stage = "generate"
    )

    override suspend fun repairProgramJson(
        userId: String,
        userPrompt: String,
        systemPrompt: String,
        inputPayload: String,
        invalidJson: String,
        repairInstruction: String,
        language: Language
    ): LiftrixResult<WorkoutProgramJsonResponse> {
        val repairPayload = """
            task:
            Repair the workout program JSON so it passes validation.

            original_input_payload:
            $inputPayload

            invalid_json:
            $invalidJson

            exact_validation_errors:
            $repairInstruction

            repair_rules:
            - Return JSON only. Do not return Markdown, comments, explanations, or code fences.
            - Change only invalid exercises or invalid fields required by the validation errors.
            - If validation errors exist, returning the same JSON is forbidden.
            - For BEGINNER level, every rep-based exercise must have reps_min >= 8 and reps_max <= 15.
            - Replace any non-beginner exercise with a beginner-compatible exercise_id from the provided exercise_catalog.
            - Use only exercise_ids from the provided exercise_catalog and copy catalog exercise_name, primary_muscle, and equipment exactly.
        """.trimIndent()

        return generateJson(
            userId = userId,
            userPrompt = userPrompt,
            systemPrompt = systemPrompt,
            requestPayload = repairPayload,
            language = language,
            stage = "repair",
            validateUserPrompt = false
        )
    }

    override suspend fun modifyProgramJson(
        userId: String,
        userPrompt: String,
        systemPrompt: String,
        inputPayload: String,
        language: Language
    ): LiftrixResult<WorkoutProgramJsonResponse> = generateJson(
        userId = userId,
        userPrompt = userPrompt,
        systemPrompt = systemPrompt,
        requestPayload = inputPayload,
        language = language,
        stage = "modify"
    )

    private suspend fun generateJson(
        userId: String,
        userPrompt: String,
        systemPrompt: String,
        requestPayload: String,
        language: Language,
        stage: String,
        validateUserPrompt: Boolean = true
    ): LiftrixResult<WorkoutProgramJsonResponse> = liftrixCatching(
        errorMapper = { throwable ->
            when (throwable) {
                is QuotaExceededException -> LiftrixError.BusinessLogicError(
                    code = "QUOTA_EXCEEDED",
                    errorMessage = "Daily AI generation limit reached. Please try again later.",
                    analyticsContext = mapOf("user_id" to userId, "stage" to stage)
                )
                is ValidationException -> LiftrixError.ValidationError(
                    field = "message",
                    violations = listOf(throwable.message ?: "Invalid workout generation prompt"),
                    analyticsContext = mapOf("user_id" to userId, "stage" to stage)
                )
                is DebugAppCheckTokenNotRegisteredException -> LiftrixError.BusinessLogicError(
                    code = "APP_CHECK_DEBUG_TOKEN_NOT_REGISTERED",
                    errorMessage = aiUnavailableForAppCheckMessage(),
                    analyticsContext = mapOf("user_id" to userId, "stage" to stage)
                )
                is AppCheckRateLimitedException -> LiftrixError.BusinessLogicError(
                    code = "APP_CHECK_RATE_LIMITED",
                    errorMessage = "AI security verification is temporarily rate limited. Please try again later.",
                    isRecoverable = true,
                    analyticsContext = mapOf("user_id" to userId, "stage" to stage)
                )
                is AppCheckUnavailableException -> LiftrixError.BusinessLogicError(
                    code = "APP_CHECK_UNAVAILABLE",
                    errorMessage = "AI security verification is temporarily unavailable. Please try again later.",
                    isRecoverable = true,
                    analyticsContext = mapOf("user_id" to userId, "stage" to stage)
                )
                is PaidAiDisabledException -> LiftrixError.BusinessLogicError(
                    code = "AI_DISABLED",
                    errorMessage = throwable.message.orEmpty(),
                    analyticsContext = mapOf("user_id" to userId, "stage" to stage)
                )
                is PaidAiPolicyDeniedException -> LiftrixError.ValidationError(
                    field = "message",
                    violations = listOf(throwable.message.orEmpty()),
                    errorMessage = throwable.message.orEmpty(),
                    analyticsContext = mapOf("user_id" to userId, "stage" to stage)
                )
                is PaidAiRecoverableDenialException -> LiftrixError.BusinessLogicError(
                    code = "AI_REQUEST_THROTTLED",
                    errorMessage = throwable.message.orEmpty(),
                    isRecoverable = true,
                    analyticsContext = mapOf("user_id" to userId, "stage" to stage)
                )
                is AIResponseMaxTokensException -> LiftrixError.BusinessLogicError(
                    code = "AI_WORKOUT_GENERATION_MAX_TOKENS",
                    errorMessage = "The AI workout response was cut off before it finished. Please try a shorter request or generate again.",
                    isRecoverable = true,
                    analyticsContext = mapOf("user_id" to userId, "stage" to stage)
                )
                else -> LiftrixError.NetworkError(
                    errorMessage = "Failed to generate workout program. Please try again.",
                    analyticsContext = mapOf("user_id" to userId, "stage" to stage)
                )
            }
        }
    ) {
        Timber.i("[AI] WorkoutProgramGenerationService: request started stage=$stage promptChars=${userPrompt.length} payloadChars=${requestPayload.length}")
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        validateRequest(userPrompt, language, validateUserPrompt)
        Timber.d("[AI] WorkoutProgramGenerationService: request validation succeeded stage=$stage")

        val generativeModel = Firebase.ai().generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig,
            systemInstruction = content { text(systemPrompt) }
        )

        val estimatedInputTokens = estimateTokens(systemPrompt + requestPayload, "")
        val startTime = System.currentTimeMillis()
        val dispatchResult = paidAiCallExecutor.execute(
            request = PaidAiCallRequest(
                userId = userId,
                operation = stage.toPaidOperation(),
                model = MODEL_NAME,
                abuseContent = userPrompt,
                estimatedInputTokens = estimatedInputTokens
            )
        ) {
            Timber.i(
                "[AI] WorkoutProgramGenerationService: Firebase AI dispatch stage=%s payloadChars=%d",
                stage,
                requestPayload.length
            )
            val response = try {
                generativeModel.generateContent(content { text(requestPayload) })
            } catch (exception: ResponseStoppedException) {
                if (exception.isMaxTokensStop()) throw AIResponseMaxTokensException(exception)
                throw exception
            }
            val responseText = response.text?.trim().orEmpty()
            val usage = response.usageMetadata
            val inputTokens = usage?.promptTokenCount ?: estimatedInputTokens
            val outputTokens = usage?.candidatesTokenCount ?: estimateTokens("", responseText)
            val totalTokens = usage?.totalTokenCount ?: inputTokens + outputTokens
            PaidAiCallResult(
                value = WorkoutDispatchResult(responseText, totalTokens),
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
        if (dispatchResult.responseText.isBlank()) {
            throw IllegalStateException("Firebase AI returned an empty workout program response")
        }

        val processingTime = System.currentTimeMillis() - startTime
        val tokensUsed = dispatchResult.totalTokens

        analyticsTracker.trackAIWorkoutGenerationResponse(
            userId = userId,
            tokensUsed = tokensUsed,
            processingTimeMs = processingTime,
            modelVersion = MODEL_NAME,
            language = language.code,
            stage = stage
        )

        val cleanedJson = stripCodeFence(dispatchResult.responseText)
        Timber.i("[AI] WorkoutProgramGenerationService: final JSON returned stage=$stage chars=${cleanedJson.length} tokens=$tokensUsed processingMs=$processingTime")

        WorkoutProgramJsonResponse(
            json = cleanedJson,
            tokensUsed = tokensUsed,
            processingTimeMs = processingTime,
            modelVersion = MODEL_NAME
        )
    }

    private suspend fun validateRequest(
        userPrompt: String,
        language: Language,
        validateUserPrompt: Boolean
    ) {
        if (validateUserPrompt) {
            if (userPrompt.isBlank()) {
                throw ValidationException("Message cannot be empty")
            }
            if (userPrompt.length > MAX_PROMPT_LENGTH) {
                throw ValidationException("Message is too long")
            }

        }

        if (language !in listOf(Language.ENGLISH, Language.ROMANIAN)) {
            throw ValidationException("Unsupported language")
        }
    }

    private fun stripCodeFence(text: String): String {
        val trimmed = text.trim()
        val fenced = Regex("""(?s)```\s*(?:json|JSON)?\s*(.*?)\s*```""")
            .find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        return extractJsonObject(fenced ?: trimmed)
    }

    private fun extractJsonObject(text: String): String {
        val first = text.indexOf('{')
        if (first < 0) return text.trim()

        var depth = 0
        var inString = false
        var escaped = false
        for (index in first until text.length) {
            val character = text[index]
            when (character) {
                '\\' -> if (inString) escaped = !escaped else escaped = false
                '"' -> if (!escaped) inString = !inString
                '{' -> if (!inString) depth++
                '}' -> if (!inString) {
                    depth--
                    if (depth == 0) return text.substring(first, index + 1).trim()
                }
            }
            if (character != '\\') escaped = false
        }
        return text.trim()
    }

    private fun estimateTokens(input: String, output: String): Int =
        (input.length + output.length) / 4

    private fun String.toPaidOperation(): PaidAiOperation = when (this) {
        "generate" -> PaidAiOperation.WORKOUT_GENERATE
        "repair" -> PaidAiOperation.WORKOUT_REPAIR
        "modify" -> PaidAiOperation.WORKOUT_MODIFY
        else -> error("Unsupported paid workout operation: $this")
    }

    private data class WorkoutDispatchResult(
        val responseText: String,
        val totalTokens: Int
    )

    companion object {
        private const val MODEL_NAME = "gemini-2.5-flash-lite"
        private const val MAX_OUTPUT_TOKENS = 8192
        private const val TEMPERATURE = 0.25f
        private const val TOP_K = 40
        private const val TOP_P = 0.9f
        private const val MAX_PROMPT_LENGTH = 2000
    }

    private fun ResponseStoppedException.isMaxTokensStop(): Boolean =
        message.orEmpty().contains("MAX_TOKENS", ignoreCase = true)
}
