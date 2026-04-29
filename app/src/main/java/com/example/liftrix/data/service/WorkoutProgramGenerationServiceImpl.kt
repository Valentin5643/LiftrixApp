package com.example.liftrix.data.service

import com.example.liftrix.BuildConfig
import com.example.liftrix.LiftrixApp
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.AnalyticsTracker
import com.example.liftrix.domain.service.Language
import com.example.liftrix.domain.service.WorkoutProgramGenerationService
import com.example.liftrix.domain.service.WorkoutProgramJsonResponse
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.content
import com.google.firebase.appcheck.appCheck
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutProgramGenerationServiceImpl @Inject constructor(
    private val analyticsTracker: AnalyticsTracker,
    private val abusePreventionService: AbusePreventionService,
    private val rateLimitingService: RateLimitingService
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
            stage = "repair"
        )
    }

    private suspend fun generateJson(
        userId: String,
        userPrompt: String,
        systemPrompt: String,
        requestPayload: String,
        language: Language,
        stage: String
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
                    errorMessage = throwable.message ?: "Debug App Check token is not registered.",
                    analyticsContext = mapOf("user_id" to userId, "stage" to stage)
                )
                else -> LiftrixError.NetworkError(
                    errorMessage = "Failed to generate workout program. Please try again.",
                    analyticsContext = mapOf("user_id" to userId, "stage" to stage)
                )
            }
        }
    ) {
        Timber.i("WorkoutProgramGenerationService: request started stage=$stage user=$userId promptChars=${userPrompt.length} payloadChars=${requestPayload.length}")
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        validateRequest(userId, userPrompt, language)
        Timber.d("WorkoutProgramGenerationService: request validation succeeded stage=$stage")

        val startTime = System.currentTimeMillis()
        waitForAppCheck()
        val appCheckToken = obtainAppCheckTokenWithRetry()
            ?: throw IllegalStateException("AI service requires valid security tokens.")

        Timber.d("WorkoutProgramGenerationService: App Check token ready (${if (appCheckToken.fromCache) "cached" else "refreshed"})")
        Timber.i("WorkoutProgramGenerationService: Firebase AI call started stage=$stage model=$MODEL_NAME")

        val generativeModel = Firebase.ai().generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig,
            systemInstruction = content { text(systemPrompt) }
        )

        val response = generativeModel.generateContent(content { text(requestPayload) })
        val responseText = response.text?.trim().orEmpty()
        Timber.i("WorkoutProgramGenerationService: raw response received stage=$stage chars=${responseText.length}")
        logSanitizedRawResponse(responseText, stage)
        if (responseText.isBlank()) {
            throw IllegalStateException("Firebase AI returned an empty workout program response")
        }

        val processingTime = System.currentTimeMillis() - startTime
        val tokensUsed = response.usageMetadata?.totalTokenCount ?: estimateTokens(requestPayload, responseText)

        analyticsTracker.trackAIWorkoutGenerationResponse(
            userId = userId,
            tokensUsed = tokensUsed,
            processingTimeMs = processingTime,
            modelVersion = MODEL_NAME,
            language = language.code,
            stage = stage
        )

        val cleanedJson = stripCodeFence(responseText)
        Timber.i("WorkoutProgramGenerationService: final JSON returned stage=$stage chars=${cleanedJson.length} tokens=$tokensUsed processingMs=$processingTime")

        WorkoutProgramJsonResponse(
            json = cleanedJson,
            tokensUsed = tokensUsed,
            processingTimeMs = processingTime,
            modelVersion = MODEL_NAME
        )
    }

    private suspend fun validateRequest(userId: String, userPrompt: String, language: Language) {
        if (userPrompt.isBlank()) {
            throw ValidationException("Message cannot be empty")
        }
        if (userPrompt.length > MAX_PROMPT_LENGTH) {
            throw ValidationException("Message is too long")
        }

        val abuseDetection = abusePreventionService.detectAbuse(userId, userPrompt)
        if (abuseDetection.isAbusive) {
            throw ValidationException("Your message appears to violate our usage guidelines. Please rephrase and try again.")
        }

        val limits = rateLimitingService.checkLimits(userId)
        if (limits.isLimited) {
            throw QuotaExceededException("Rate limit exceeded: ${limits.reason}")
        }

        if (language !in listOf(Language.ENGLISH, Language.ROMANIAN)) {
            throw ValidationException("Unsupported language")
        }
    }

    private suspend fun waitForAppCheck() {
        val isReady = withTimeoutOrNull(APP_CHECK_READY_TIMEOUT_MS) {
            LiftrixApp.getInstance().isAppCheckInitialized.first { it }
            true
        } ?: false
        if (!isReady) {
            Timber.w("WorkoutProgramGenerationService: App Check was not ready before AI call")
        }
    }

    private suspend fun obtainAppCheckTokenWithRetry(): AppCheckToken? {
        var currentDelay = 1_000L
        repeat(APP_CHECK_MAX_RETRIES) { attempt ->
            if (attempt > 0) {
                delay(currentDelay)
                currentDelay = (currentDelay * 2).coerceAtMost(8_000L)
            }

            val tokenResult = withTimeoutOrNull(APP_CHECK_TOKEN_TIMEOUT_MS) {
                try {
                    Firebase.appCheck.getAppCheckToken(false).await()
                } catch (e: Exception) {
                    if (isDebugAppCheckTokenMissing(e)) {
                        logDebugAppCheckTokenInstructions(e)
                        throw DebugAppCheckTokenNotRegisteredException(e)
                    }
                    Timber.w(
                        e,
                        "WorkoutProgramGenerationService: Failed to obtain App Check token. type=%s, message=%s",
                        e.javaClass.name,
                        e.message.orEmpty()
                    )
                    null
                }
            }

            if (tokenResult != null) {
                return AppCheckToken(
                    token = tokenResult.token,
                    expireTimeMillis = tokenResult.expireTimeMillis,
                    fromCache = attempt == 0
                )
            }
        }
        return null
    }

    private fun isDebugAppCheckTokenMissing(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return BuildConfig.DEBUG &&
            error is FirebaseException &&
            message.contains("403", ignoreCase = true) &&
            message.contains("app attestation failed", ignoreCase = true)
    }

    private fun logDebugAppCheckTokenInstructions(error: Throwable) {
        Timber.e(error, "Debug App Check token is not registered in Firebase Console for this Firebase app/project.")
        Timber.e("Find the generated token in Logcat by searching for:")
        Timber.e("Enter this debug secret into the allow list in the Firebase Console")
        Timber.e("Register it at Firebase Console > App Check > com.example.liftrix > Manage debug tokens.")
        Timber.e("Expected project: liftrix-390cf; package: com.example.liftrix; appId: 1:734273269747:android:39d5352dabb68d74202c86")
        Timber.e("After adding the token, uninstall/reinstall the debug app or clear app data and run again.")
    }

    private fun stripCodeFence(text: String): String {
        val trimmed = text.trim()
        val fenced = Regex("""(?s)```\s*(?:json|JSON)?\s*(.*?)\s*```""")
            .find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (fenced != null) return fenced
        if (!trimmed.startsWith("```")) return extractJsonObject(trimmed)
        return trimmed
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun extractJsonObject(text: String): String {
        val first = text.indexOf('{')
        val last = text.lastIndexOf('}')
        return if (first >= 0 && last > first) text.substring(first, last + 1).trim() else text
    }

    private fun logSanitizedRawResponse(responseText: String, stage: String) {
        if (!BuildConfig.DEBUG) return
        val sanitized = responseText
            .replace(Regex("""(?i)(api[_-]?key|token|secret)["']?\s*[:=]\s*["'][^"']+["']"""), "$1:<redacted>")
            .take(4_000)
        Timber.d("WorkoutProgramGenerationService: sanitized raw response stage=$stage: $sanitized")
    }

    private fun estimateTokens(input: String, output: String): Int =
        (input.length + output.length) / 4

    private data class AppCheckToken(
        val token: String,
        val expireTimeMillis: Long,
        val fromCache: Boolean
    )

    companion object {
        private const val MODEL_NAME = "gemini-2.5-flash-lite"
        private const val MAX_OUTPUT_TOKENS = 4096
        private const val TEMPERATURE = 0.25f
        private const val TOP_K = 40
        private const val TOP_P = 0.9f
        private const val MAX_PROMPT_LENGTH = 2000
        private const val APP_CHECK_READY_TIMEOUT_MS = 30_000L
        private const val APP_CHECK_TOKEN_TIMEOUT_MS = 10_000L
        private const val APP_CHECK_MAX_RETRIES = 1
    }
}
