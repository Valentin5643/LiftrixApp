package com.example.liftrix.data.service

import com.example.liftrix.core.data.BuildConfig
import com.example.liftrix.data.local.dao.AiUsageDao
import com.example.liftrix.data.local.entity.AiUsageEntity
import com.example.liftrix.data.remote.config.RemoteConfigManager
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.service.AbuseAction
import com.example.liftrix.domain.service.AbusePreventionServiceContract
import com.example.liftrix.domain.service.RateLimitingServiceContract
import com.example.liftrix.domain.usecase.admin.CheckAdminPermissionsUseCase
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.appcheck.appCheck
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

enum class PaidAiOperation {
    CHAT_RESPONSE,
    WORKOUT_GENERATE,
    WORKOUT_REPAIR,
    WORKOUT_MODIFY
}

data class PaidAiCallRequest(
    val userId: String,
    val operation: PaidAiOperation,
    val model: String,
    val abuseContent: String,
    val estimatedInputTokens: Int
)

data class PaidAiCallResult<T>(
    val value: T,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val category: String = CATEGORY_MODEL_RESPONSE
) {
    companion object {
        const val CATEGORY_MODEL_RESPONSE = "MODEL_RESPONSE"
        const val CATEGORY_EMPTY_RESPONSE = "EMPTY_RESPONSE"
    }
}

/**
 * Single control plane for every paid Firebase AI dispatch.
 *
 * Guard evaluation, App Check, dispatch, and durable accounting are serialized by a fixed set of
 * user-keyed stripes. This prevents same-process quota races without retaining an unbounded user map.
 */
@Singleton
class PaidAiCallExecutor @Inject constructor(
    private val remoteConfig: RemoteConfigManager,
    private val abusePreventionService: AbusePreventionServiceContract,
    private val rateLimitingService: RateLimitingServiceContract,
    private val aiUsageDao: AiUsageDao,
    private val checkAdminPermissionsUseCase: CheckAdminPermissionsUseCase,
    private val authRepository: AuthRepository
) {
    private val stripes = Array(STRIPE_COUNT) { Mutex() }

    suspend fun <T> execute(
        request: PaidAiCallRequest,
        dispatch: suspend () -> PaidAiCallResult<T>
    ): T {
        val authenticatedUserId = try {
            authRepository.getCurrentUserId()?.value
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        if (authenticatedUserId == null || authenticatedUserId != request.userId) {
            throw PaidAiAccessDeniedException()
        }

        val verifiedRequest = request.copy(userId = authenticatedUserId)
        return stripeFor(authenticatedUserId).withLock {
            require(verifiedRequest.abuseContent.isNotBlank()) {
                "Abuse evaluation content is required"
            }

            val eventId = UUID.randomUUID().toString()
            val startedAt = System.currentTimeMillis()
            var dispatched = false

            try {
                checkPreconditions(verifiedRequest)
                obtainAppCheckToken()

                Timber.i(
                    "Paid AI dispatch eventId=%s operation=%s inputTokensEstimate=%d",
                    eventId,
                    verifiedRequest.operation.name,
                    verifiedRequest.estimatedInputTokens
                )
                dispatched = true
                val result = try {
                    withTimeout(PROVIDER_DISPATCH_TIMEOUT_MS) {
                        dispatch()
                    }
                } catch (error: TimeoutCancellationException) {
                    throw PaidAiTimeoutException(error)
                }
                recordOutcome(
                    eventId = eventId,
                    request = verifiedRequest,
                    inputTokens = result.inputTokens,
                    outputTokens = result.outputTokens,
                    totalTokens = result.totalTokens,
                    category = result.category
                )
                Timber.i(
                    "Paid AI completed eventId=%s operation=%s tokens=%d durationMs=%d category=%s",
                    eventId,
                    verifiedRequest.operation.name,
                    result.totalTokens,
                    System.currentTimeMillis() - startedAt,
                    result.category
                )
                result.value
            } catch (error: Exception) {
                if (dispatched) {
                    withContext(NonCancellable) {
                        recordOutcome(
                            eventId = eventId,
                            request = verifiedRequest,
                            inputTokens = verifiedRequest.estimatedInputTokens.coerceAtLeast(0),
                            outputTokens = 0,
                            totalTokens = verifiedRequest.estimatedInputTokens.coerceAtLeast(0),
                            category = error.toOutcomeCategory()
                        )
                    }
                    Timber.w(
                        error,
                        "Paid AI failed after dispatch eventId=%s operation=%s durationMs=%d",
                        eventId,
                        verifiedRequest.operation.name,
                        System.currentTimeMillis() - startedAt
                    )
                } else {
                    Timber.w(
                        "Paid AI rejected before dispatch eventId=%s operation=%s reason=%s",
                        eventId,
                        verifiedRequest.operation.name,
                        error.javaClass.simpleName
                    )
                }
                throw error
            }
        }
    }

    private suspend fun checkPreconditions(request: PaidAiCallRequest) {
        val isAdmin = checkAdminPermissionsUseCase(request.userId).getOrElse { false }
        if (!isAdmin) throw PaidAiAccessDeniedException()

        val readiness = withTimeoutOrNull(PAID_CONTROLS_READINESS_TIMEOUT_MS) {
            remoteConfig.ensurePaidAiControlsReady()
        } ?: throw PaidAiControlUnavailableException(
            "Paid AI control state refresh timed out."
        )
        readiness.getOrElse { error ->
            throw PaidAiControlUnavailableException(
                "Paid AI control state refresh failed.",
                error
            )
        }

        val isEnabled = remoteConfig.isAiChatEnabled().getOrElse { error ->
            throw PaidAiControlUnavailableException(
                "Paid AI control state could not be read.",
                error
            )
        }
        if (!isEnabled) throw PaidAiDisabledException()

        val abuse = abusePreventionService.detectAbuse(request.userId, request.abuseContent)
        if (abuse.isAbusive) {
            when (abuse.action) {
                AbuseAction.COOLDOWN,
                AbuseAction.THROTTLE -> throw PaidAiRecoverableDenialException(
                    abuse.warning ?: "Please wait before trying another AI request"
                )
                AbuseAction.BLOCK,
                AbuseAction.REVIEW,
                AbuseAction.REJECT,
                AbuseAction.TRUNCATE,
                null -> throw PaidAiPolicyDeniedException(
                    abuse.warning ?: "This request violates AI usage guidelines"
                )
            }
        }

        val limits = rateLimitingService.checkLimits(request.userId)
        if (limits.isLimited) {
            throw QuotaExceededException(limits.reason ?: "AI usage limit reached")
        }
    }

    private suspend fun obtainAppCheckToken() {
        val result = withTimeoutOrNull(APP_CHECK_TIMEOUT_MS) {
            try {
                Firebase.appCheck.getAppCheckToken(false).await()
            } catch (error: Exception) {
                mapAppCheckFailure(error)
            }
        } ?: throw AppCheckUnavailableException("App Check token request timed out.")

        if (result.token.isBlank()) {
            throw AppCheckUnavailableException("App Check returned an empty token.")
        }
    }

    private fun mapAppCheckFailure(error: Exception): Nothing {
        val message = error.message.orEmpty()
        when {
            BuildConfig.DEBUG && error.isAttestationFailure() ->
                throw DebugAppCheckTokenNotRegisteredException(error)
            error is FirebaseException && message.contains("too many attempts", ignoreCase = true) ->
                throw AppCheckRateLimitedException(error)
            else -> throw AppCheckUnavailableException("Failed to obtain App Check token.", error)
        }
    }

    private fun Throwable.isAttestationFailure(): Boolean =
        this is FirebaseException &&
            message.orEmpty().contains("403", ignoreCase = true) &&
            message.orEmpty().contains("app attestation failed", ignoreCase = true)

    private suspend fun recordOutcome(
        eventId: String,
        request: PaidAiCallRequest,
        inputTokens: Int,
        outputTokens: Int,
        totalTokens: Int,
        category: String
    ) {
        aiUsageDao.insert(
            AiUsageEntity(
                id = eventId,
                userId = request.userId,
                createdAt = System.currentTimeMillis(),
                operation = request.operation.name,
                model = request.model,
                inputTokens = inputTokens.coerceAtLeast(0),
                outputTokens = outputTokens.coerceAtLeast(0),
                totalTokens = totalTokens.coerceAtLeast(0),
                successCategory = category
            )
        )
    }

    private fun stripeFor(userId: String): Mutex =
        stripes[(userId.hashCode() and Int.MAX_VALUE) % stripes.size]

    private fun Throwable.toOutcomeCategory(): String = when (this) {
        is PaidAiTimeoutException -> "TIMEOUT"
        is AIResponseMaxTokensException -> "MAX_TOKENS"
        else -> "DISPATCH_ERROR"
    }

    private companion object {
        const val STRIPE_COUNT = 64
        const val PAID_CONTROLS_READINESS_TIMEOUT_MS = 10_000L
        const val APP_CHECK_TIMEOUT_MS = 10_000L
        const val PROVIDER_DISPATCH_TIMEOUT_MS = 30_000L
    }
}

class PaidAiDisabledException : Exception("AI features are temporarily disabled.")
class PaidAiAccessDeniedException : Exception(
    "AI access is limited to authorized competition administrators."
)
class PaidAiControlUnavailableException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
class PaidAiTimeoutException(cause: Throwable) :
    Exception("The AI provider did not respond before the request deadline.", cause)
class PaidAiPolicyDeniedException(message: String) : Exception(message)
class PaidAiRecoverableDenialException(message: String) : Exception(message)
class QuotaExceededException(message: String) : Exception(message)
class DebugAppCheckTokenNotRegisteredException(cause: Throwable) : Exception(
    "Firebase App Check rejected the debug provider token.",
    cause
)
class AppCheckRateLimitedException(cause: Throwable) :
    Exception("Firebase App Check token exchange is rate limited. Wait before retrying.", cause)
class AppCheckUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)
class AIResponseMaxTokensException(cause: Throwable) :
    Exception("Firebase AI response stopped because it reached max output tokens.", cause)

internal fun aiUnavailableForAppCheckMessage(): String =
    if (BuildConfig.DEBUG) {
        "AI is temporarily unavailable in this debug build. Register the debug App Check token in Firebase Console and try again."
    } else {
        "AI is temporarily unavailable in this build. Please try again later."
    }
